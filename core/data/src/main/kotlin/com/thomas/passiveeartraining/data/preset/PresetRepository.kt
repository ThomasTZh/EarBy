package com.thomas.passiveeartraining.data.preset

import com.thomas.passiveeartraining.training.settings.UserSettings
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes the user's presets.
 *
 * Presets are domain configuration rather than live playback: no position, timer deadline or queue
 * state is stored. Built-in starters are immutable and are copied before they are edited, so a
 * preset operation can never alter what a user did not create. Every operation works on the
 * configuration's own kind, and reads stay kind-scoped, so one mode's presets can never leak into
 * another's list (SC7/M5).
 */
interface PresetRepository {
    /**
     * The intervals presets the current presets screen observes: immutable starters first, then
     * editable slots. A later kind's presets are read through its own kind-scoped operations.
     */
    val presets: Flow<List<Preset>>

    /** Reloads the list; called when a screen starts observing. */
    suspend fun refresh()

    /**
     * Records that [id] was applied just now; the saved timestamp stays as it was.
     *
     * The Recent sort follows this moment, so applying a preset - a built-in starter included -
     * moves it to the front without touching what the user saved or renamed.
     */
    suspend fun markUsed(id: PresetId)

    /** Creates an editable preset with a fresh identity, scoped to the configuration's kind. */
    suspend fun create(
        name: String,
        configuration: PresetConfiguration,
        seed: Long? = null,
        musicGain: Float = UserSettings.DEFAULT_MUSIC_GAIN,
        voiceGain: Float = UserSettings.DEFAULT_VOICE_GAIN
    ): Preset

    /** Replaces an editable preset's configuration; identity and creation time are kept. */
    suspend fun update(preset: Preset): Preset

    /** Renames an editable preset. */
    suspend fun rename(id: PresetId, name: String): Preset

    /** Copies a preset into an editable one with a fresh identity. */
    suspend fun duplicate(id: PresetId, name: String? = null): Preset

    /** Deletes an editable preset. */
    suspend fun delete(id: PresetId)

    /**
     * Empties an editable preset or slot, keeping its identity and name.
     *
     * A slot becomes an unfilled slot again, exactly as a fresh store leaves it; a custom preset
     * keeps its row but loses its configuration. Built-in starters and identities this build
     * cannot read are refused, like every other edit.
     */
    suspend fun clear(id: PresetId): Preset

    /**
     * Checks an imported document without changing the store.
     *
     * Each preset gets its own result, so one unusable entry does not hide the usable ones. A
     * file-level problem (malformed, oversized, newer schema) throws [PresetValidationException];
     * a per-preset problem is reported on that entry.
     */
    suspend fun previewImport(text: String): ImportPreview

    /**
     * Stores the accepted presets from a preview, atomically.
     *
     * An entry whose id already exists is imported under a new identity unless its id is listed in
     * [replaceExistingIds], which is the explicit request to overwrite. Every write of the batch is
     * one transaction: a failure or an over-limit batch changes nothing.
     */
    suspend fun commitImport(
        preview: ImportPreview,
        replaceExistingIds: Set<PresetId> = emptySet()
    ): ImportResult

    /** Writes an export document holding exactly the selected presets. */
    suspend fun export(ids: Collection<PresetId>): String
}

/** One preset parsed from an imported document, and how it relates to this store. */
sealed interface ImportEntry {
    /** Zero-based position in the file, so a screen can point at the row it means. */
    val index: Int

    /** The document's name; for a rejected entry, the best label available. */
    val name: String

    /** A valid preset that can be committed. */
    data class Ready(
        override val index: Int,
        val document: PresetDocument,
        /** Why the document's own id cannot simply be kept, or null when it can. */
        val conflict: ImportConflict?
    ) : ImportEntry {
        override val name: String get() = document.name
    }

    /** A preset that cannot be imported, with the reason to show beside it. */
    data class Rejected(override val index: Int, override val name: String, val reason: String) :
        ImportEntry
}

/** Why an imported preset's id cannot be used as-is. */
enum class ImportConflict {
    /** A preset with this id is already stored. */
    EXISTING_ID,

    /** An earlier preset in the same file already claims this id. */
    DUPLICATE_IN_FILE
}

/** The parsed result of one import document, before anything is stored. */
data class ImportPreview(val entries: List<ImportEntry>) {
    /** Entries that can be committed. */
    val ready: List<ImportEntry.Ready> get() = entries.filterIsInstance<ImportEntry.Ready>()

    /** Entries that cannot be committed. */
    val rejected: List<ImportEntry.Rejected> get() =
        entries.filterIsInstance<ImportEntry.Rejected>()

    /** True when at least one ready entry needs a decision about its identity. */
    val hasConflicts: Boolean get() = ready.any { it.conflict != null }
}

/** What a commit stored, so a screen can report it. */
data class ImportResult(
    /** Presets stored under an identity that did not previously exist in this store. */
    val imported: List<Preset>,
    /** Presets whose existing identity was overwritten on explicit request. */
    val replaced: List<Preset>
)

/** Raised when an operation names a preset this store does not hold. */
class PresetNotFoundException(val presetId: PresetId) :
    NoSuchElementException("No preset with id $presetId")

/** Raised when a caller tries to change a built-in starter. */
class ImmutablePresetException(val presetId: PresetId, operation: String) :
    IllegalStateException("The built-in preset $presetId cannot be $operation")

/** Raised when the editable preset store is full, before anything is written. */
class PresetLimitException(limit: Int) :
    IllegalStateException("The preset store holds at most $limit editable presets")
