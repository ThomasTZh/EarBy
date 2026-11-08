package com.thomas.passiveeartraining.data.preset

import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.recipe.DurationFinish
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.INTERVALS_CONFIG_VERSION
import com.thomas.passiveeartraining.training.settings.LastSetup
import kotlin.time.Duration

/**
 * Schema 9's generic, kind-scoped payload table.
 *
 * The table stores one row per preset or slot: the stable kind id, an optional slot number (1..5),
 * the name, the typed payload as validated JSON, that payload's own version, and the empty/
 * built-in flags with the original timestamps. The constraints encode the invariants the store
 * relies on: slots are numbered 1..5 or absent, filled rows have a payload and its version, empty
 * rows have neither, and a starter is never empty (SC7/M2.3).
 */
internal val PRESET_PAYLOAD_TABLE_SQL: String = """
    CREATE TABLE preset_payload (
        id TEXT NOT NULL,
        kind TEXT NOT NULL,
        slot_number INTEGER,
        name TEXT NOT NULL,
        payload TEXT,
        payload_version INTEGER,
        is_empty INTEGER NOT NULL DEFAULT 0,
        built_in INTEGER NOT NULL DEFAULT 0,
        created_at_epoch_ms INTEGER NOT NULL,
        updated_at_epoch_ms INTEGER NOT NULL,
        PRIMARY KEY (id),
        UNIQUE (kind, slot_number),
        CHECK (slot_number IS NULL OR (slot_number BETWEEN 1 AND ${PresetSlots.COUNT})),
        CHECK ((payload IS NULL) = (payload_version IS NULL)),
        CHECK (is_empty = 0 OR payload IS NULL),
        CHECK (is_empty = 1 OR payload IS NOT NULL),
        CHECK (built_in = 0 OR is_empty = 0)
    )
""".trimIndent()

/**
 * Migrates a version-8 database to version 9, inside the caller's transaction.
 *
 * Every version-8 row is read first and converted in Kotlin, so the conversion shares one
 * implementation between Android and the JDBC tests. The five historical interval slots keep their
 * identities; the other thirty-five deterministic slots are created empty; legacy customs keep a
 * null slot number and the adoption marker in `preset_meta` is untouched. Converted payloads are
 * verified by decoding them back before the old tables are dropped, and any failure aborts the
 * surrounding transaction (SC7/M2.3).
 */
internal fun PresetDatabase.migratePresetSchemaToV9() {
    // 1. Read every version-8 row and its ordered lists before any schema change.
    val entities = query("SELECT * FROM preset_entity")
    val intervals = query(
        "SELECT preset_id, interval_id FROM preset_interval ORDER BY preset_id, position"
    ).groupBy({ it.stringColumn("preset_id") }, { it.stringColumn("interval_id") })
    val roots = query(
        "SELECT preset_id, root_value FROM preset_root ORDER BY preset_id, position"
    ).groupBy({ it.stringColumn("preset_id") }, { it.intColumn("root_value") })
    val converted = entities.map { row -> legacyPreset(row, intervals, roots) }

    val payloads = converted.associate { preset ->
        preset.id.value to if (preset.empty) {
            null
        } else {
            PresetDocumentCodec.intervalPayloadJson(preset)
        }
    }

    // 2. Create the new table.
    execute(PRESET_PAYLOAD_TABLE_SQL)

    // 3. Convert every row, preserving identity, name, flags and timestamps.
    converted.forEach { preset ->
        execute(
            "INSERT INTO preset_payload (" +
                "id, kind, slot_number, name, payload, payload_version, is_empty, built_in, " +
                "created_at_epoch_ms, updated_at_epoch_ms" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            listOf(
                preset.id.value,
                TrainingKind.INTERVALS.id,
                PresetSlots.slotNumber(preset.id),
                preset.name,
                payloads.getValue(preset.id.value),
                if (preset.empty) null else 1,
                if (preset.empty) 1 else 0,
                if (preset.builtIn) 1 else 0,
                preset.createdAtEpochMs,
                preset.updatedAtEpochMs
            )
        )
    }

    // The other thirty-five slots exist from the start so every kind owns five stable places.
    val futureKinds = TrainingKind.ALL.filter { it != TrainingKind.INTERVALS }
    futureKinds.forEach { kind ->
        PresetSlots.slotIds(kind).forEachIndexed { index, id ->
            execute(
                "INSERT INTO preset_payload (" +
                    "id, kind, slot_number, name, payload, payload_version, is_empty, built_in, " +
                    "created_at_epoch_ms, updated_at_epoch_ms" +
                    ") VALUES (?, ?, ?, ?, NULL, NULL, 1, 0, 0, 0)",
                listOf(id.value, kind.id, index + 1, PresetSlots.defaultName(index + 1))
            )
        }
    }

    // 4. Verify counts, payload presence and exact payload round trips before replacing anything.
    val expectedRows = converted.size + futureKinds.size * PresetSlots.COUNT
    val storedRows = query("SELECT COUNT(*) AS count FROM preset_payload")
        .first()
        .intColumn("count")
    check(storedRows == expectedRows) {
        "Migration 9 converted $storedRows rows; expected $expectedRows"
    }
    val missingPayloads = converted.count { preset ->
        preset.empty && payloads.getValue(preset.id.value) != null ||
            !preset.empty && payloads.getValue(preset.id.value) == null
    }
    check(missingPayloads == 0) { "Migration 9 produced $missingPayloads inconsistent payloads" }
    converted.filterNot { it.empty }.forEach { preset ->
        val restored = PresetDocumentCodec.storedPreset(
            id = preset.id.value,
            name = preset.name,
            createdAtEpochMs = preset.createdAtEpochMs,
            updatedAtEpochMs = preset.updatedAtEpochMs,
            payloadJson = requireNotNull(payloads.getValue(preset.id.value)),
            builtIn = preset.builtIn,
            kind = TrainingKind.INTERVALS,
            configVersion = INTERVALS_CONFIG_VERSION
        )
        check(restored == preset) {
            "Migration 9 changed preset ${preset.id}: $preset became $restored"
        }
    }
    val distinctSlots = query(
        "SELECT COUNT(*) AS count FROM (" +
            "SELECT DISTINCT kind, slot_number FROM preset_payload WHERE slot_number IS NOT NULL)"
    ).first().intColumn("count")
    check(distinctSlots == TrainingKind.ALL.size * PresetSlots.COUNT) {
        "Migration 9 produced $distinctSlots distinct slots"
    }

    // 5. Only now replace the old tables. `preset_meta` keeps the adoption marker.
    execute("DROP TABLE preset_interval")
    execute("DROP TABLE preset_root")
    execute("DROP TABLE preset_entity")
}

/** Converts one version-8 row into the domain preset, exactly as the old reader did. */
private fun legacyPreset(
    row: Map<String, Any?>,
    intervals: Map<String, List<String>>,
    roots: Map<String, List<Int>>
): Preset {
    val id = PresetId(row.stringColumn("id"))
    val setup = try {
        LastSetup(
            intervalIds = intervals[id.value].orEmpty(),
            rootValues = roots[id.value].orEmpty(),
            minimumNote = row.intColumn("minimum_note"),
            maximumNote = row.intColumn("maximum_note"),
            presentation = enumColumn(row, "presentation", IntervalPresentation.entries),
            mode = enumColumn(row, "mode", RecipeMode.entries),
            thinkingSilence = Duration.parse("${row.longColumn("thinking_silence_ms")}ms"),
            replayCount = row.intColumn("replay_count"),
            stopPolicy = legacyStopPolicy(row, id),
            noteDuration = Duration.parse("${row.longColumn("note_duration_ms")}ms"),
            answerGap = Duration.parse("${row.longColumn("answer_gap_ms")}ms"),
            repeatGap = Duration.parse("${row.longColumn("repeat_gap_ms")}ms"),
            fixedRoot = row.nullableLongColumn("fixed_root")?.toInt(),
            presentations = legacyPresentations(row, id),
            melodicGap = Duration.parse("${row.longColumn("melodic_gap_ms")}ms"),
            finalSilence = Duration.parse("${row.longColumn("final_silence_ms")}ms"),
            separatorCue = row.longColumn("separator_cue") != 0L
        )
    } catch (failure: IllegalArgumentException) {
        throw PresetStorageException("Preset $id cannot be read: ${failure.message}")
    }
    val seedText = row.nullableStringColumn("seed_decimal")
    return Preset(
        id = id,
        name = row.stringColumn("name"),
        createdAtEpochMs = row.longColumn("created_at_epoch_ms"),
        updatedAtEpochMs = row.longColumn("updated_at_epoch_ms"),
        configuration = PresetConfiguration.Intervals(setup),
        seed = when (seedText) {
            null -> null
            else -> seedText.toLongOrNull()
                ?: throw PresetStorageException("Preset $id has a non-numeric seed '$seedText'")
        },
        musicGain = row.doubleColumn("music_gain").toFloat(),
        voiceGain = row.doubleColumn("voice_gain").toFloat(),
        builtIn = row.longColumn("built_in") != 0L,
        empty = row.longColumn("is_empty") != 0L
    )
}

private fun legacyPresentations(row: Map<String, Any?>, id: PresetId): List<IntervalPresentation>? {
    val stored = row.nullableStringColumn("presentations") ?: return null
    val names = stored.split(",").filter { it.isNotBlank() }
    val values = names.map { name ->
        IntervalPresentation.entries.firstOrNull { it.name == name }
            ?: throw PresetStorageException("Preset $id has unknown presentation '$name'")
    }
    return values.takeIf { it.size > 1 }
}

private fun legacyStopPolicy(row: Map<String, Any?>, id: PresetId): SessionStopPolicy =
    when (val kind = row.stringColumn("stop_kind")) {
        STOP_KIND_UNTIL_STOPPED -> SessionStopPolicy.UntilStopped
        STOP_KIND_AFTER_CYCLE -> SessionStopPolicy.AfterCycle
        STOP_KIND_DURATION -> {
            val durationMs = row.nullableLongColumn("stop_duration_ms")
                ?: throw PresetStorageException("Preset $id has a duration stop without one")
            val finishName = row.nullableStringColumn("stop_duration_finish")
                ?: throw PresetStorageException(
                    "Preset $id has a duration stop without a finish"
                )
            val finish = DurationFinish.entries.firstOrNull { it.name == finishName }
                ?: throw PresetStorageException(
                    "Preset $id has unknown duration finish '$finishName'"
                )
            SessionStopPolicy.Duration(Duration.parse("${durationMs}ms"), finish)
        }
        STOP_KIND_COMPLETED_ITEMS -> {
            val count = row.nullableLongColumn("stop_count")
                ?: throw PresetStorageException("Preset $id has a count stop without a count")
            SessionStopPolicy.CompletedItems(count.toInt())
        }
        else -> throw PresetStorageException("Preset $id has unknown stop kind '$kind'")
    }

private fun <T : Enum<T>> enumColumn(row: Map<String, Any?>, name: String, values: List<T>): T {
    val text = row.stringColumn(name)
    return values.firstOrNull { it.name == text }
        ?: throw PresetStorageException("Column '$name' has unknown value '$text'")
}
