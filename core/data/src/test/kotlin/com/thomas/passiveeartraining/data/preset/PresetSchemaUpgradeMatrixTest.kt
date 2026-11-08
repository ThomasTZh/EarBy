package com.thomas.passiveeartraining.data.preset

import com.thomas.passiveeartraining.training.provider.TrainingKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M11.2 storage matrix: every intermediate stored schema version is carried to the current one by
 * the production steps, applied one at a time.
 *
 * For each starting version 1..8 the test builds a real database at that version, writes the
 * representative rows a user could have there (the frozen v1 fixture row plus rows inserted while
 * the store is at the starting version), then runs the remaining production steps one by one. After
 * every step it asserts that the stored version advanced and the rows survive; the final row lives
 * in the schema-9 payload table.
 *
 * What is and is not preserved per step:
 * - steps 2..5 and 7 add columns or seed rows; every existing value survives with schema defaults
 *   for the new columns;
 * - step 6 is the one deliberate data change: legacy `DEEP_REPETITION`/`ANSWER_FIRST_RECALL`
 *   modes are rewritten to `RECOGNITION`, and nothing else about the row changes;
 * - step 8 adds the `preset_meta` adoption marker without touching entity rows;
 * - step 9 converts every entity row into the typed payload table, preserving identity, name,
 *   timestamps, flags and the interval configuration (verified by the transform's own round trip).
 *
 * No new binary fixture is frozen: the v1 statements and v2 step shipped by [PresetSchemaTest] are
 * applied through the production [PresetSchema.migrationsToUpgrade] so this matrix cannot drift
 * from the released steps.
 */
class PresetSchemaUpgradeMatrixTest {

    @Test
    fun everyIntermediateVersionSurvivesEveryRemainingProductionStep() {
        for (start in 1..8) {
            JdbcPresetDatabase.inMemory().use { database ->
                buildVersion(database, start)
                insertRepresentativeRows(database, start)

                var version = start
                while (version < PresetSchema.CURRENT_VERSION) {
                    val target = version + 1
                    applyProductionStep(database, target)
                    database.setUserVersion(target)
                    version = target

                    assertEquals(
                        "stored version after the step that reaches $target (from $start)",
                        target,
                        database.userVersion()
                    )
                    assertRepresentativeRows(database, start, target)
                }
                assertEquals(PresetSchema.CURRENT_VERSION, database.userVersion())
                assertFinalPayloadTable(database, start)
            }
        }
    }

    @Test
    fun aFailureInAMiddleStepRollsBackEveryEarlierStepAndRetrySucceeds() {
        JdbcPresetDatabase.inMemory().use { database ->
            buildVersion(database, 1)
            val failing = FailingMigrationDatabase(database)
            // Version 7 is a middle step of nine: it disables the from-scratch fast path and proves
            // that even the columns and rows added by steps 2..6 roll back with it.
            failing.failOnSql = "ALTER TABLE preset_entity ADD COLUMN is_empty"

            assertThrows(IllegalStateException::class.java) { failing.migrate() }

            assertEquals("the stored version is untouched", 1, database.userVersion())
            assertTrue(
                "the pre-migration entity table survives",
                database.query("SELECT * FROM preset_entity").isNotEmpty()
            )
            val entitySql = database.query(
                "SELECT sql FROM sqlite_master WHERE name = 'preset_entity'"
            ).single().stringColumn("sql")
            assertTrue(
                "an earlier column migrates away with the whole transaction",
                !entitySql.contains("note_duration_ms")
            )
            assertTrue(
                "the failing column itself is absent",
                !entitySql.contains("separator_cue")
            )
            assertTrue(
                "the payload table is rolled back",
                database.query("SELECT name FROM sqlite_master WHERE name = 'preset_payload'")
                    .isEmpty()
            )
            assertTrue(
                "the meta table is rolled back",
                database.query("SELECT name FROM sqlite_master WHERE name = 'preset_meta'")
                    .isEmpty()
            )

            // Retrying the same migration succeeds once the fault is gone.
            failing.failOnSql = null
            failing.migrate()
            assertEquals(PresetSchema.CURRENT_VERSION, database.userVersion())
            val preserved = database.query(
                "SELECT payload FROM preset_payload WHERE name = 'Preserved'"
            ).single()
            assertTrue(preserved.stringColumn("payload").contains("minor_third"))
        }
    }

    /**
     * Creates a database at exactly [version] from the frozen v1 statements plus the production
     * steps that reach the requested version. Starting below version 9 means the old entity tables
     * still exist and can carry representative rows.
     */
    private fun buildVersion(database: JdbcPresetDatabase, version: Int) {
        FROZEN_V1.forEach(database::execute)
        database.setUserVersion(1)
        database.insertPreservedRow()
        for (target in 2..version) {
            applyProductionStep(database, target)
            database.setUserVersion(target)
            // The row written at version 1 is the "previously written" representative; every
            // production step on the way to the starting version must leave it intact.
            assertPreservedRowSurvives(database, target)
        }
        assertEquals(version, database.userVersion())
    }

    /** Asserts the frozen v1 fixture row still exists with its identity while the entity table lives. */
    private fun assertPreservedRowSurvives(database: JdbcPresetDatabase, version: Int) {
        val rows = database.query(
            "SELECT id, name, created_at_epoch_ms, updated_at_epoch_ms, mode FROM preset_entity"
        ).associateBy { it.stringColumn("id") }
        assertStoredIdentity(rows, PRESERVED_ID, "Preserved", 1L, 2L, version)
    }

    /**
     * Writes the rows a starting version could hold: the frozen v1 row comes from
     * [insertPreservedRow] in [buildVersion], a fully populated row is inserted while the store is
     * at [start], and a legacy deep-repetition row is added when step 6 can still rewrite it.
     */
    private fun insertRepresentativeRows(database: JdbcPresetDatabase, start: Int) {
        database.insertMatrixRow(start)
        if (start <= 5) {
            database.insertLegacyModeRow(start)
        }
    }

    /** Applies exactly one production step, statements or transform, exactly as `migrate()` does. */
    private fun applyProductionStep(database: JdbcPresetDatabase, target: Int) {
        val migrations = PresetSchema.migrationsToUpgrade(target - 1, target)
        if (target == 2) {
            // The version 2 step must be the frozen fixture step this matrix claims to run, not a
            // private copy that could drift from the released one.
            val statements = (migrations.single() as PresetMigration.Statements).statements
            assertEquals(
                "the v2 step is the frozen fixture, not a private copy",
                FROZEN_V2_MIGRATION,
                statements
            )
        }
        migrations.forEach { migration ->
            when (migration) {
                is PresetMigration.Statements -> migration.statements.forEach(database::execute)
                is PresetMigration.Transform -> migration.transform.invoke(database)
            }
        }
    }

    /** Asserts the representative rows are intact at [version], in whichever table owns them. */
    private fun assertRepresentativeRows(database: JdbcPresetDatabase, start: Int, version: Int) {
        val legacyExpected = start <= 5
        if (version < 9) {
            val rows = database.query(
                "SELECT id, name, created_at_epoch_ms, updated_at_epoch_ms, mode " +
                    "FROM preset_entity"
            ).associateBy { it.stringColumn("id") }
            assertStoredIdentity(rows, PRESERVED_ID, "Preserved", 1L, 2L, version)
            assertStoredIdentity(rows, MATRIX_ID, "Matrix", 10L, 11L, version)
            if (legacyExpected) {
                val legacy = assertStoredIdentity(
                    rows,
                    LEGACY_ID,
                    "Legacy",
                    20L,
                    21L,
                    version
                )
                assertEquals(
                    "step 6 is the one deliberate rewrite: the legacy mode becomes recognition",
                    if (version >= 6) "RECOGNITION" else "DEEP_REPETITION",
                    legacy.stringColumn("mode")
                )
            }
        } else {
            val rows = database.query(
                "SELECT id, kind, slot_number, name, payload, payload_version, is_empty, " +
                    "created_at_epoch_ms, updated_at_epoch_ms FROM preset_payload"
            ).associateBy { it.stringColumn("id") }
            assertPayloadIdentity(rows, PRESERVED_ID, "Preserved", 1L, 2L, "minor_third")
            assertPayloadIdentity(rows, MATRIX_ID, "Matrix", 10L, 11L, "major_third")
            if (legacyExpected) {
                assertPayloadIdentity(rows, LEGACY_ID, "Legacy", 20L, 21L, null)
            }
        }
    }

    private fun assertStoredIdentity(
        rows: Map<String, Map<String, Any?>>,
        id: String,
        name: String,
        createdAt: Long,
        updatedAt: Long,
        version: Int
    ): Map<String, Any?> {
        val row = rows[id] ?: error("row $id disappeared at version $version")
        assertEquals(name, row.stringColumn("name"))
        assertEquals(createdAt, row.longColumn("created_at_epoch_ms"))
        assertEquals(updatedAt, row.longColumn("updated_at_epoch_ms"))
        return row
    }

    private fun assertPayloadIdentity(
        rows: Map<String, Map<String, Any?>>,
        id: String,
        name: String,
        createdAt: Long,
        updatedAt: Long,
        expectedIntervalId: String?
    ) {
        val row = rows[id] ?: error("converted row $id disappeared")
        assertEquals("intervals", row.stringColumn("kind"))
        assertEquals(name, row.stringColumn("name"))
        assertEquals(createdAt, row.longColumn("created_at_epoch_ms"))
        assertEquals(updatedAt, row.longColumn("updated_at_epoch_ms"))
        assertNull(
            "a legacy custom keeps a null slot number",
            row.nullableLongColumn("slot_number")
        )
        assertEquals(0L, row.longColumn("is_empty"))
        assertEquals(1L, row.longColumn("payload_version"))
        if (expectedIntervalId != null) {
            assertTrue(
                "the ordered interval list survives the conversion",
                row.stringColumn("payload").contains(expectedIntervalId)
            )
        }
    }

    /** The schema-9 outcome: every custom converted and all forty slots seeded empty. */
    private fun assertFinalPayloadTable(database: JdbcPresetDatabase, start: Int) {
        val customs = if (start <= 5) 3 else 2
        val count = database.query("SELECT COUNT(*) AS count FROM preset_payload")
            .single()
            .intColumn("count")
        assertEquals(
            "converted customs plus the empty slots of every kind",
            customs + TrainingKind.ALL.size * PresetSlots.COUNT,
            count
        )
    }

    /**
     * The fully populated row written while the store is at [version].
     *
     * Only columns that already exist at that version are written; the remaining columns take the
     * schema defaults added by later migrations, which is exactly what a stored row written at that
     * version looks like.
     */
    private fun JdbcPresetDatabase.insertMatrixRow(version: Int) {
        val columns = mutableListOf(
            "id", "name", "created_at_epoch_ms", "updated_at_epoch_ms", "built_in",
            "minimum_note", "maximum_note", "presentation", "mode", "thinking_silence_ms",
            "replay_count", "seed_decimal", "music_gain", "voice_gain", "stop_kind"
        )
        val values = mutableListOf<Any?>(
            MATRIX_ID, "Matrix", 10L, 11L, 0,
            57, 84, "ASCENDING", "EXPOSURE", 1500,
            2, null, 0.8, 0.3, "UNTIL_STOPPED"
        )
        if (version >= 2) {
            columns.addAll(listOf("note_duration_ms", "answer_gap_ms", "repeat_gap_ms"))
            values.addAll(listOf<Any?>(850L, 150L, 250L))
        }
        if (version >= 3) {
            columns.add("fixed_root")
            values.add(60)
        }
        if (version >= 4) {
            columns.addAll(
                listOf(
                    "presentations",
                    "example_repeats",
                    "trailing_example_repeats",
                    "melodic_gap_ms",
                    "final_silence_ms"
                )
            )
            values.addAll(listOf<Any?>("ASCENDING,DESCENDING", 2, 1, 120L, 1800L))
        }
        if (version >= 5) {
            columns.add("separator_cue")
            values.add(1)
        }
        if (version >= 7) {
            columns.add("is_empty")
            values.add(0)
        }
        execute(
            "INSERT INTO preset_entity (${columns.joinToString(", ")}) " +
                "VALUES (${columns.joinToString(", ") { "?" }})",
            values
        )
        execute(
            "INSERT INTO preset_interval (preset_id, position, interval_id) VALUES (?, ?, ?)",
            listOf(MATRIX_ID, 0, "major_third")
        )
        execute(
            "INSERT INTO preset_root (preset_id, position, root_value) VALUES (?, ?, ?)",
            listOf(MATRIX_ID, 0, 0)
        )
    }

    /**
     * A row that step 6 must rewrite.
     *
     * It is only written when the starting version is at most 5: from version 6 on the legacy
     * modes are no longer readable, and a row that still used one would be rejected by the version
     * 9 conversion rather than silently repaired.
     */
    private fun JdbcPresetDatabase.insertLegacyModeRow(version: Int) {
        val columns = mutableListOf(
            "id", "name", "created_at_epoch_ms", "updated_at_epoch_ms", "built_in",
            "minimum_note", "maximum_note", "presentation", "mode", "thinking_silence_ms",
            "replay_count", "seed_decimal", "music_gain", "voice_gain", "stop_kind"
        )
        val values = mutableListOf<Any?>(
            LEGACY_ID, "Legacy", 20L, 21L, 0,
            60, 72, "ASCENDING", "DEEP_REPETITION", 2000,
            0, null, 1.0, 0.25, "UNTIL_STOPPED"
        )
        if (version >= 2) {
            columns.addAll(listOf("note_duration_ms", "answer_gap_ms", "repeat_gap_ms"))
            values.addAll(listOf<Any?>(700L, 600L, 400L))
        }
        if (version >= 4) {
            columns.addAll(
                listOf(
                    "presentations",
                    "example_repeats",
                    "trailing_example_repeats",
                    "melodic_gap_ms",
                    "final_silence_ms"
                )
            )
            values.addAll(listOf<Any?>(null, 1, 0, 100L, 2000L))
        }
        if (version >= 5) {
            columns.add("separator_cue")
            values.add(0)
        }
        execute(
            "INSERT INTO preset_entity (${columns.joinToString(", ")}) " +
                "VALUES (${columns.joinToString(", ") { "?" }})",
            values
        )
    }

    private companion object {
        const val PRESERVED_ID = "11111111-2222-3333-4444-555555555555"
        const val MATRIX_ID = "22222222-3333-4444-5555-666666666666"
        const val LEGACY_ID = "33333333-4444-5555-6666-777777777777"
    }
}
