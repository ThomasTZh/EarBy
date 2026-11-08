package com.thomas.passiveeartraining.data.preset

import android.content.Context
import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.recipe.DurationFinish
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.ChordSetup
import com.thomas.passiveeartraining.training.settings.ContextualIntervalSetup
import com.thomas.passiveeartraining.training.settings.IntervalWalkSetup
import com.thomas.passiveeartraining.training.settings.LastSetup
import com.thomas.passiveeartraining.training.settings.MelodicDictationSetup
import com.thomas.passiveeartraining.training.settings.NoteSetup
import com.thomas.passiveeartraining.training.settings.ProgressionSetup
import com.thomas.passiveeartraining.training.settings.ScaleDegreeSetup
import com.thomas.passiveeartraining.training.settings.ScaleSetup
import java.util.UUID
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The preset store on plain SQLite.
 *
 * All access is serialized through one mutex on a background dispatcher, so commands cannot
 * interleave and no statement runs on the main thread. A mutation and its reload are one unit: the
 * list a screen observes never shows a half-applied write. Schema creation and migration are the
 * shared [PresetSchema] statements, executed by [AndroidPresetDatabase] on a device and by a real
 * SQLite engine in the host tests.
 *
 * The clock and the identity generator are injected, so tests are deterministic and the store never
 * reaches for global state.
 */
class SqlitePresetRepository internal constructor(
    private val database: PresetDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val newId: () -> PresetId = { PresetId(UUID.randomUUID().toString()) },
    private val starters: List<Preset> = PresetStarters.ALL_KINDS
) : PresetRepository {

    /** Opens the app-private preset database. */
    constructor(context: Context, fileName: String = DEFAULT_FILE_NAME) : this(
        AndroidPresetDatabase(context, fileName)
    )

    private val gate = Mutex()
    private var opened = false
    private val state = MutableStateFlow<List<Preset>>(emptyList())

    override val presets: StateFlow<List<Preset>> = state.asStateFlow()

    override suspend fun refresh() {
        withStore { reload() }
    }

    override suspend fun markUsed(id: PresetId) {
        withStore {
            database.executeUpdate(
                "UPDATE preset_payload SET last_used_at_epoch_ms = ? WHERE id = ?",
                listOf(nowMs(), id.value)
            )
            reload()
        }
    }

    override suspend fun create(
        name: String,
        configuration: PresetConfiguration,
        seed: Long?,
        musicGain: Float,
        voiceGain: Float
    ): Preset = withStore {
        val timestamp = nowMs()
        val preset = Preset(
            id = newId(),
            name = name.trim(),
            createdAtEpochMs = timestamp,
            updatedAtEpochMs = timestamp,
            configuration = configuration,
            seed = seed,
            musicGain = musicGain,
            voiceGain = voiceGain
        )
        PresetDocumentCodec.validatePreset(preset)
        requireRoomFor(configuration.kind, 1)
        database.transaction { writePreset(preset, replaceExisting = false) }
        reload()
        preset
    }

    override suspend fun update(preset: Preset): Preset = withStore {
        val row = requireEditableRow(preset.id, "updated", preset.configuration.kind)
        val updated = preset.copy(
            name = preset.name.trim(),
            // A preset is not a live session: only its configuration moves, never its identity.
            createdAtEpochMs = row.longColumn("created_at_epoch_ms"),
            updatedAtEpochMs = nowMs(),
            // Saving never reorders the Recent list; the stored use time is kept.
            lastUsedAtEpochMs = row.longColumn("last_used_at_epoch_ms"),
            builtIn = false,
            // Writing a full configuration into a slot fills it; the flag only ever describes an
            // untouched slot, never a preset the user saved.
            empty = false
        )
        PresetDocumentCodec.validatePreset(updated)
        database.transaction { writePreset(updated, replaceExisting = true) }
        reload()
        updated
    }

    override suspend fun rename(id: PresetId, name: String): Preset = withStore {
        val row = requireEditableRow(id, "renamed")
        val trimmed = name.trim()
        PresetDocumentCodec.requireName(trimmed)
        database.executeUpdate(
            "UPDATE preset_payload SET name = ?, updated_at_epoch_ms = ? " +
                "WHERE id = ? AND built_in = 0 AND kind = ?",
            listOf(trimmed, nowMs(), id.value, row.stringColumn("kind"))
        )
        reload()
        loadOne(id) ?: throw PresetNotFoundException(id)
    }

    override suspend fun duplicate(id: PresetId, name: String?): Preset = withStore {
        val original = loadOne(id) ?: throw PresetNotFoundException(id)
        val timestamp = nowMs()
        val copy = original.copy(
            id = newId(),
            name = name?.trim() ?: copyNameFor(original.name),
            createdAtEpochMs = timestamp,
            updatedAtEpochMs = timestamp,
            // A copy has never been used, whatever the original's use time was.
            lastUsedAtEpochMs = 0,
            builtIn = false
        )
        PresetDocumentCodec.requireName(copy.name)
        requireRoomFor(copy.configuration.kind, 1)
        database.transaction { writePreset(copy, replaceExisting = false) }
        reload()
        copy
    }

    override suspend fun delete(id: PresetId) {
        withStore {
            val row = requireEditableRow(id, "deleted")
            val kind = kindOfRow(row, id)
            // A slot is a permanent place for a user's setup; it is refilled, never deleted.
            if (id in PresetSlots.slotIds(kind)) {
                throw ImmutablePresetException(id, "deleted")
            }
            database.transaction {
                val removed = database.executeUpdate(
                    "DELETE FROM preset_payload WHERE id = ? AND built_in = 0 AND kind = ?",
                    listOf(id.value, kind.id)
                )
                if (removed != 1) throw PresetNotFoundException(id)
            }
            reload()
        }
    }

    override suspend fun clear(id: PresetId): Preset = withStore {
        val row = requireEditableRow(id, "cleared")
        val kind = kindOfRow(row, id)
        // The row keeps its identity, name and slot number; only the configuration is gone, so a
        // cleared slot is exactly a fresh one again (M5).
        database.executeUpdate(
            "UPDATE preset_payload SET payload = NULL, payload_version = NULL, is_empty = 1, " +
                "updated_at_epoch_ms = ?, last_used_at_epoch_ms = 0 " +
                "WHERE id = ? AND built_in = 0 AND kind = ?",
            listOf(nowMs(), id.value, kind.id)
        )
        reload()
        loadOne(id) ?: throw PresetNotFoundException(id)
    }

    override suspend fun previewImport(text: String): ImportPreview = withStore {
        val documents = PresetDocumentCodec.decodeEnvelope(text)
        val storedIds = loadEveryPreset().mapTo(mutableSetOf()) { it.id.value }
        val seen = mutableSetOf<String>()
        val entries = documents.mapIndexed { index, document ->
            try {
                PresetDocumentCodec.validate(document)
                val conflict = when {
                    !seen.add(document.id) -> ImportConflict.DUPLICATE_IN_FILE
                    document.id in storedIds -> ImportConflict.EXISTING_ID
                    else -> null
                }
                ImportEntry.Ready(index, document, conflict)
            } catch (failure: PresetValidationException) {
                ImportEntry.Rejected(
                    index = index,
                    name = document.name,
                    reason = failure.message ?: "The preset is not valid"
                )
            }
        }
        ImportPreview(entries)
    }

    override suspend fun commitImport(
        preview: ImportPreview,
        replaceExistingIds: Set<PresetId>
    ): ImportResult = withStore {
        val stored = loadEveryPreset().associateBy { it.id }
        val seen = mutableSetOf<String>()
        val planned = preview.ready.map { entry ->
            val document = entry.document
            val requestedId = PresetId(document.id)
            val duplicateInFile = !seen.add(document.id)
            // An explicit replacement is only honoured for the first entry claiming that id; a
            // later duplicate in the same file would otherwise depend on file order.
            val replaces = !duplicateInFile &&
                requestedId in replaceExistingIds &&
                requestedId in stored
            val targetId = when {
                replaces -> requestedId
                duplicateInFile -> newId()
                requestedId in stored -> newId()
                else -> requestedId
            }
            Planned(document, targetId, replaces)
        }
        planned.filterNot { it.replaces }
            .groupingBy { kindOf(it.document) }
            .eachCount()
            .forEach { (kind, count) -> requireRoomFor(kind, count) }
        database.transaction {
            planned.forEach { plan ->
                val existing = stored[plan.targetId]
                val imported = PresetDocumentCodec.toPreset(plan.document, id = plan.targetId)
                val toWrite = if (plan.replaces && existing != null) {
                    // Replacing keeps when the preset first appeared and records when it changed.
                    imported.copy(
                        createdAtEpochMs = existing.createdAtEpochMs,
                        updatedAtEpochMs = nowMs()
                    )
                } else {
                    imported
                }
                writePreset(toWrite, replaceExisting = plan.replaces)
            }
        }
        reload()
        val saved = loadEveryPreset().associateBy { it.id }
        ImportResult(
            imported = planned.filterNot { it.replaces }.map { saved.getValue(it.targetId) },
            replaced = planned.filter { it.replaces }.map { saved.getValue(it.targetId) }
        )
    }

    override suspend fun export(ids: Collection<PresetId>): String = withStore {
        val wanted = ids.toSet()
        val stored = loadEveryPreset().associateBy { it.id }
        val missing = wanted.firstOrNull { it !in stored }
        if (missing != null) throw PresetNotFoundException(missing)
        PresetDocumentCodec.encode(wanted.map { stored.getValue(it) })
    }

    /** Runs [work] on the store's single lane, opening the database on first use. */
    private suspend fun <T> withStore(work: () -> T): T = gate.withLock {
        withContext(ioDispatcher) {
            openIfNeeded()
            work()
        }
    }

    /** Migrates and seeds once; an already-open store keeps its state. */
    private fun openIfNeeded() {
        if (opened) return
        database.migrate()
        database.transaction { seedStarters() }
        // The slot upgrade leaves a marker when customs created before the slots existed; move
        // them into empty slots once and clear the marker. A fresh store leaves none.
        database.transaction {
            if (takeAdoptionRequest()) adoptLegacyCustomsIntoSlots()
        }
        reload()
        opened = true
    }

    /**
     * Writes every starter once, one kind at a time.
     *
     * The write is keyed on the starter's fixed identity, so re-opening a store - or adding a
     * kind's group to an existing store - adds only what is absent and never rewrites a row a
     * migration owns (M5).
     */
    private fun seedStarters() {
        starters.groupBy { it.configuration.kind }.forEach { (_, kindStarters) ->
            kindStarters.forEach { starter ->
                if (!exists(starter.id)) {
                    writePreset(starter, replaceExisting = false)
                }
            }
        }
    }

    /** True when the schema upgrade asked for pre-slot customs to be adopted; clears the request. */
    private fun takeAdoptionRequest(): Boolean {
        val requested = database.query(
            "SELECT value FROM preset_meta WHERE key = ?",
            listOf(PresetSchema.ADOPTION_KEY)
        ).isNotEmpty()
        if (requested) {
            database.execute(
                "DELETE FROM preset_meta WHERE key = ?",
                listOf(PresetSchema.ADOPTION_KEY)
            )
        }
        return requested
    }

    /**
     * The five slots of [kind] in slot order.
     *
     * Kind-scoped so a screen or a test can never rename, clear or apply another mode's slot by
     * position or title; only the mode's own ids are returned (SC7/M2.4).
     */
    suspend fun slotsOf(kind: TrainingKind): List<Preset> = withStore { loadSlots(kind) }

    /**
     * The built-in starter of [kind], read from the store.
     *
     * The kind-aware screens show the stored row rather than the in-memory constant, so the same
     * last-used moment that orders the slots orders the starter as well.
     */
    suspend fun starterOf(kind: TrainingKind): Preset? = withStore {
        loadAll(kind).firstOrNull { it.builtIn }
    }

    /**
     * Reloads the intervals preset list the presets screen observes.
     *
     * The list stays intervals-scoped until another kind's screen exists; another kind's starters
     * and slots are read through [slotsOf] and never leak into this list, so adding a kind cannot
     * insert or reorder an entry here (SC7/M2.4, M5).
     */
    private fun reload() {
        state.value = loadAll(TrainingKind.INTERVALS)
    }

    /**
     * Moves customs created before the five slots existed into empty interval slots.
     *
     * The move keeps the old preset's name and configuration, so an upgrade never hides or drops a
     * user's preset. It is idempotent: once a custom has moved, its row is gone and there is nothing
     * left to adopt. A custom beyond the fifth slot is left untouched rather than deleted.
     */
    private fun adoptLegacyCustomsIntoSlots() {
        val rows = database.query(
            "SELECT * FROM preset_payload WHERE kind = ? AND built_in = 0",
            listOf(TrainingKind.INTERVALS.id)
        )
        val slotIds = PresetSlots.IDS.mapTo(mutableSetOf()) { it.value }
        val emptySlots = rows
            .filter { it.stringColumn("id") in slotIds && it.longColumn("is_empty") != 0L }
            .sortedBy { it.stringColumn("id") }
        if (emptySlots.isEmpty()) return
        val legacy = rows
            .filter { it.stringColumn("id") !in slotIds }
            .sortedWith(
                compareBy({ it.longColumn("created_at_epoch_ms") }, { it.stringColumn("id") })
            )
        legacy.zip(emptySlots).forEach { (old, slot) ->
            val oldPreset = decodeRow(old, TrainingKind.INTERVALS) ?: return@forEach
            val slotId = PresetId(slot.stringColumn("id"))
            writePreset(oldPreset.copy(id = slotId, empty = false), replaceExisting = true)
            database.executeUpdate(
                "DELETE FROM preset_payload WHERE id = ? AND built_in = 0",
                listOf(oldPreset.id.value)
            )
        }
    }

    private fun loadAll(kind: TrainingKind): List<Preset> = database.query(
        "SELECT * FROM preset_payload WHERE kind = ? " +
            "ORDER BY built_in DESC, name COLLATE NOCASE ASC, id ASC",
        listOf(kind.id)
    ).mapNotNull { row -> decodeRow(row, kind) }

    private fun loadSlots(kind: TrainingKind): List<Preset> = database.query(
        "SELECT * FROM preset_payload WHERE kind = ? AND slot_number IS NOT NULL " +
            "ORDER BY slot_number",
        listOf(kind.id)
    ).mapNotNull { row -> decodeRow(row, kind) }

    private fun loadOne(id: PresetId): Preset? = database.query(
        "SELECT * FROM preset_payload WHERE id = ?",
        listOf(id.value)
    ).firstOrNull()?.let { row ->
        val kind = TrainingKind.ofIdOrNull(row.stringColumn("kind")) ?: return null
        // A kind this build has no codec for is never exposed through identity-based operations;
        // its empty slots stay reachable only through slotsOf, exactly as before chords arrived.
        if (presetConfigVersionOrNull(kind) == null) return null
        decodeRow(row, kind)
    }

    /**
     * Every preset this build can read, whichever supported kind it belongs to.
     *
     * Import and export use identities, which are global: a preset id is the primary key, so a
     * conflict with any readable row must be visible. A row of a kind that has no codec yet stays
     * hidden rather than being guessed at or silently replaced.
     */
    private fun loadEveryPreset(): List<Preset> = SUPPORTED_KINDS.flatMap { kind -> loadAll(kind) }

    /**
     * Decodes one stored row.
     *
     * The row's kind selects its codec: a supported kind is decoded with its own payload and
     * configuration version, and a kind this build has no codec for exposes only its empty slot,
     * exactly as before chords arrived. An empty slot becomes a preset with its kind's default
     * configuration and `empty = true`. A payload written by a newer build is protected: the row
     * is not exposed and cannot be overwritten, rather than being guessed at.
     */
    private fun decodeRow(row: Map<String, Any?>, expectedKind: TrainingKind): Preset? {
        val id = PresetId(row.stringColumn("id"))
        if (row.stringColumn("kind") != expectedKind.id) return null
        val configVersion = presetConfigVersionOrNull(expectedKind)
        if (configVersion == null) {
            return if (row.longColumn("is_empty") != 0L &&
                row.nullableStringColumn("payload") == null
            ) {
                Preset(
                    id = id,
                    name = row.stringColumn("name"),
                    createdAtEpochMs = row.longColumn("created_at_epoch_ms"),
                    updatedAtEpochMs = row.longColumn("updated_at_epoch_ms"),
                    lastUsedAtEpochMs = row.longColumn("last_used_at_epoch_ms"),
                    // A kind without a typed codec still exposes its empty slot, as the generic
                    // table did before chords arrived; a filled one is refused below (SC7/M2.3).
                    configuration = PresetConfiguration.Intervals(LastSetup.default()),
                    builtIn = row.longColumn("built_in") != 0L,
                    empty = true
                )
            } else {
                null
            }
        }
        val version = row.nullableLongColumn("payload_version")?.toInt()
        if (version != null && version != configVersion) return null
        val builtIn = row.longColumn("built_in") != 0L
        val empty = row.longColumn("is_empty") != 0L
        val payload = row.nullableStringColumn("payload")
        if (empty) {
            if (payload != null) {
                throw PresetStorageException("Empty preset $id carries a payload")
            }
            return Preset(
                id = id,
                name = row.stringColumn("name"),
                createdAtEpochMs = row.longColumn("created_at_epoch_ms"),
                updatedAtEpochMs = row.longColumn("updated_at_epoch_ms"),
                lastUsedAtEpochMs = row.longColumn("last_used_at_epoch_ms"),
                configuration = defaultConfigurationFor(expectedKind),
                builtIn = builtIn,
                empty = true
            )
        }
        if (payload == null) throw PresetStorageException("Preset $id has no payload")
        return try {
            PresetDocumentCodec.storedPreset(
                id = id.value,
                name = row.stringColumn("name"),
                createdAtEpochMs = row.longColumn("created_at_epoch_ms"),
                updatedAtEpochMs = row.longColumn("updated_at_epoch_ms"),
                lastUsedAtEpochMs = row.longColumn("last_used_at_epoch_ms"),
                payloadJson = payload,
                builtIn = builtIn,
                kind = expectedKind,
                configVersion = version ?: configVersion
            )
        } catch (failure: PresetValidationException) {
            throw PresetStorageException("Preset $id cannot be read: ${failure.message}")
        }
    }

    /** The configuration an empty row of [kind] stands for. */
    private fun defaultConfigurationFor(kind: TrainingKind): PresetConfiguration = when (kind) {
        TrainingKind.INTERVAL_WALK -> PresetConfiguration.IntervalWalk(IntervalWalkSetup.default())
        TrainingKind.CHORDS -> PresetConfiguration.Chords(ChordSetup.default())
        TrainingKind.SCALES -> PresetConfiguration.Scales(ScaleSetup.default())
        TrainingKind.NOTE_IDENTIFICATION -> PresetConfiguration.Notes(NoteSetup.default())
        TrainingKind.SCALE_DEGREES -> PresetConfiguration.ScaleDegrees(ScaleDegreeSetup.default())
        TrainingKind.CHORD_PROGRESSIONS ->
            PresetConfiguration.Progressions(ProgressionSetup.default())
        TrainingKind.INTERVALS_IN_CONTEXT ->
            PresetConfiguration.ContextualIntervals(ContextualIntervalSetup.default())
        TrainingKind.MELODIC_DICTATION ->
            PresetConfiguration.MelodicDictation(MelodicDictationSetup.default())
        else -> PresetConfiguration.Intervals(LastSetup.default())
    }

    /**
     * The supported kind of one mutable row, refusing a kind this build cannot read.
     *
     * The row's own kind scopes the operation, so an identity can only ever be changed through the
     * codec that wrote it; a foreign row is reported as missing instead of being touched.
     */
    private fun kindOfRow(row: Map<String, Any?>, id: PresetId): TrainingKind {
        val kind = TrainingKind.ofIdOrNull(row.stringColumn("kind"))
        if (kind == null || presetConfigVersionOrNull(kind) == null) {
            throw PresetNotFoundException(id)
        }
        return kind
    }

    /** The kind one prepared import document describes; preview already refused unknown kinds. */
    private fun kindOf(document: PresetDocument): TrainingKind = when (document) {
        is PresetDocumentV1 -> TrainingKind.INTERVALS
        is PresetDocumentV2 -> TrainingKind.ofIdOrNull(document.kind)
            ?: throw PresetValidationException(
                "Preset '${document.name}' names an unknown training kind '${document.kind}'"
            )
    }

    /**
     * Writes one preset.
     *
     * @param replaceExisting true to overwrite this store's row; a built-in row is never overwritten
     *   because the update is restricted to `built_in = 0`.
     */
    private fun writePreset(preset: Preset, replaceExisting: Boolean) {
        val id = preset.id.value
        val kind = preset.configuration.kind
        val configVersion = presetConfigVersionOrNull(kind)
            ?: throw PresetStorageException(
                "Training kind '${kind.id}' has no stored configuration version"
            )
        val existing = database.query(
            "SELECT * FROM preset_payload WHERE id = ?",
            listOf(id)
        ).firstOrNull()
        if (existing != null && !replaceExisting) {
            throw PresetStorageException("Preset $id already exists")
        }
        if (existing != null) {
            val storedKind = existing.stringColumn("kind")
            val storedVersion = existing.nullableLongColumn("payload_version")?.toInt()
            if (storedKind != kind.id ||
                (storedVersion != null && storedVersion != configVersion)
            ) {
                throw ImmutablePresetException(preset.id, "overwritten")
            }
        }
        val payload = if (preset.empty) null else PresetDocumentCodec.payloadJson(preset)
        val slotNumber = existing?.nullableLongColumn("slot_number")
            ?: PresetSlots.slotNumberOf(kind, preset.id)
        if (existing != null) {
            val updated = database.executeUpdate(
                "UPDATE preset_payload SET name = ?, payload = ?, payload_version = ?, " +
                    "is_empty = ?, created_at_epoch_ms = ?, updated_at_epoch_ms = ? " +
                    "WHERE id = ? AND built_in = 0 AND kind = ?",
                listOf(
                    preset.name,
                    payload,
                    if (preset.empty) null else configVersion,
                    if (preset.empty) 1 else 0,
                    preset.createdAtEpochMs,
                    preset.updatedAtEpochMs,
                    id,
                    kind.id
                )
            )
            if (updated != 1) {
                throw ImmutablePresetException(preset.id, "overwritten")
            }
        } else {
            database.execute(
                "INSERT INTO preset_payload (" +
                    "id, kind, slot_number, name, payload, payload_version, is_empty, built_in, " +
                    "created_at_epoch_ms, updated_at_epoch_ms" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                listOf(
                    id,
                    kind.id,
                    slotNumber,
                    preset.name,
                    payload,
                    if (preset.empty) null else configVersion,
                    if (preset.empty) 1 else 0,
                    if (preset.builtIn) 1 else 0,
                    preset.createdAtEpochMs,
                    preset.updatedAtEpochMs
                )
            )
        }
    }

    /** True when any stored row uses this identity; ids are globally unique in the table. */
    private fun exists(id: PresetId): Boolean = database.query(
        "SELECT 1 FROM preset_payload WHERE id = ?",
        listOf(id.value)
    ).isNotEmpty()

    /**
     * Reads an editable row or fails with the reason the caller gave.
     *
     * The row's own kind scopes the operation: a missing identity, or one belonging to a kind this
     * build cannot read, is refused instead of being touched. [expectedKind] additionally guards an
     * update whose payload already names a kind.
     */
    private fun requireEditableRow(
        id: PresetId,
        verb: String,
        expectedKind: TrainingKind? = null
    ): Map<String, Any?> {
        val row = database.query(
            "SELECT * FROM preset_payload WHERE id = ?",
            listOf(id.value)
        ).firstOrNull() ?: throw PresetNotFoundException(id)
        val kind = kindOfRow(row, id)
        if (expectedKind != null && expectedKind != kind) throw PresetNotFoundException(id)
        if (row.longColumn("built_in") != 0L) {
            throw ImmutablePresetException(id, verb)
        }
        val version = row.nullableLongColumn("payload_version")?.toInt()
        if (version != null && version != presetConfigVersionOrNull(kind)) {
            throw ImmutablePresetException(id, verb)
        }
        return row
    }

    /**
     * Refuses a write that would carry one kind's editable preset count above its bound.
     *
     * Each kind owns its own slots, so its capacity is counted on its own; the forty slots of the
     * later kinds never consume the intervals library's room (M2.3/M5).
     */
    private fun requireRoomFor(kind: TrainingKind, newPresets: Int) {
        val current = database
            .query(
                "SELECT COUNT(*) AS count FROM preset_payload WHERE kind = ? AND built_in = 0",
                listOf(kind.id)
            )
            .first()
            .intColumn("count")
        if (current + newPresets > MAX_EDITABLE_PRESETS) {
            throw PresetLimitException(MAX_EDITABLE_PRESETS)
        }
    }

    private fun <T : Enum<T>> enumColumn(row: Map<String, Any?>, name: String, values: List<T>): T {
        val text = row.stringColumn(name)
        return values.firstOrNull { it.name == text }
            ?: throw PresetStorageException("Column '$name' has unknown value '$text'")
    }

    /** "Minor and major thirds" becomes "Minor and major thirds copy", within the name bound. */
    private fun copyNameFor(name: String): String {
        val suffix = " copy"
        return if (name.length + suffix.length <= PresetDocumentCodec.MAX_NAME_LENGTH) {
            name + suffix
        } else {
            name.take(PresetDocumentCodec.MAX_NAME_LENGTH - suffix.length).trimEnd() + suffix
        }
    }

    /** One planned import write, with the identity conflict already resolved. */
    private data class Planned(
        val document: PresetDocument,
        val targetId: PresetId,
        val replaces: Boolean
    )

    companion object {
        /** File name of the app-private preset database. */
        const val DEFAULT_FILE_NAME: String = "presets.db"

        /** Most editable presets a store holds per kind; built-in starters are not counted. */
        const val MAX_EDITABLE_PRESETS: Int = PresetDocumentCodec.MAX_PRESETS

        /**
         * The kinds this build reads and writes through a typed payload codec.
         *
         * A later milestone adds its kind to the codec's version map; until then its rows stay
         * protected, exactly as another mode's filled row behaved before chords arrived (M5).
         */
        private val SUPPORTED_KINDS: List<TrainingKind> =
            TrainingKind.ALL.filter { presetConfigVersionOrNull(it) != null }
    }
}
