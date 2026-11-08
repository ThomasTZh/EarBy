package com.thomas.passiveeartraining.data.preset

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The v1 schema, frozen.
 *
 * Editing this fixture is how a developer declares a schema change: the current create statements
 * must not change unless a new migration is added. When v2 arrives, add its statements to
 * [PresetSchema.MIGRATIONS] and upgrade this fixture in a test that runs v1 data through the new
 * step.
 */
internal val FROZEN_V1: List<String> = listOf(
    """
    CREATE TABLE preset_entity (
        id TEXT NOT NULL,
        name TEXT NOT NULL,
        created_at_epoch_ms INTEGER NOT NULL,
        updated_at_epoch_ms INTEGER NOT NULL,
        built_in INTEGER NOT NULL DEFAULT 0,
        minimum_note INTEGER NOT NULL,
        maximum_note INTEGER NOT NULL,
        presentation TEXT NOT NULL,
        mode TEXT NOT NULL,
        thinking_silence_ms INTEGER NOT NULL,
        replay_count INTEGER NOT NULL,
        seed_decimal TEXT,
        music_gain REAL NOT NULL,
        voice_gain REAL NOT NULL,
        stop_kind TEXT NOT NULL,
        stop_duration_ms INTEGER,
        stop_duration_finish TEXT,
        stop_count INTEGER,
        PRIMARY KEY (id)
    )
    """.trimIndent(),
    """
    CREATE TABLE preset_interval (
        preset_id TEXT NOT NULL,
        position INTEGER NOT NULL,
        interval_id TEXT NOT NULL,
        PRIMARY KEY (preset_id, position),
        FOREIGN KEY (preset_id) REFERENCES preset_entity (id) ON DELETE CASCADE
    )
    """.trimIndent(),
    """
    CREATE TABLE preset_root (
        preset_id TEXT NOT NULL,
        position INTEGER NOT NULL,
        root_value INTEGER NOT NULL,
        PRIMARY KEY (preset_id, position),
        FOREIGN KEY (preset_id) REFERENCES preset_entity (id) ON DELETE CASCADE
    )
    """.trimIndent()
)

/**
 * The v2 step, frozen.
 *
 * A v2 database is a v1 database that has run these exact statements. When v3 was added, the v2
 * shape was frozen here so the production step cannot be edited silently.
 */
internal val FROZEN_V2_MIGRATION: List<String> = listOf(
    "ALTER TABLE preset_entity ADD COLUMN note_duration_ms INTEGER NOT NULL DEFAULT 700",
    "ALTER TABLE preset_entity ADD COLUMN answer_gap_ms INTEGER NOT NULL DEFAULT 600",
    "ALTER TABLE preset_entity ADD COLUMN repeat_gap_ms INTEGER NOT NULL DEFAULT 400"
)

/** Parses a stored payload for field-level assertions. */
internal fun payloadFields(payload: String): kotlinx.serialization.json.JsonObject =
    kotlinx.serialization.json.Json.parseToJsonElement(payload)
        .jsonObject

/** Inserts one complete v1-shaped row, for upgrade tests. */
internal fun JdbcPresetDatabase.insertPreservedRow() {
    execute(
        "INSERT INTO preset_entity (" +
            "id, name, created_at_epoch_ms, updated_at_epoch_ms, built_in, " +
            "minimum_note, maximum_note, presentation, mode, thinking_silence_ms, " +
            "replay_count, seed_decimal, music_gain, voice_gain, stop_kind) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        listOf(
            "11111111-2222-3333-4444-555555555555",
            "Preserved",
            1L,
            2L,
            0,
            60,
            72,
            "ASCENDING",
            "RECOGNITION",
            2000,
            0,
            null,
            1.0,
            0.25,
            "UNTIL_STOPPED"
        )
    )
    execute(
        "INSERT INTO preset_interval (preset_id, position, interval_id) VALUES (?, ?, ?)",
        listOf("11111111-2222-3333-4444-555555555555", 0, "minor_third")
    )
}

class PresetSchemaTest {

    @Test
    fun theFirstMigrationIsTheFrozenV1Schema() {
        assertEquals(
            "Changing a released schema statement requires a new migration, not a silent edit",
            FROZEN_V1,
            PresetSchema.MIGRATIONS[1]
        )
    }

    @Test
    fun everyVersionBetweenOneAndCurrentHasAMigration() {
        (1..PresetSchema.CURRENT_VERSION).forEach { version ->
            assertTrue(
                "The schema claims version $version but has no migration for it",
                PresetSchema.MIGRATIONS.containsKey(version) ||
                    PresetSchema.TRANSFORMS.containsKey(version)
            )
        }
    }

    @Test
    fun migratingFromScratchReachesTheCurrentVersion() {
        JdbcPresetDatabase.inMemory().use { database ->
            database.migrate()
            assertEquals(PresetSchema.CURRENT_VERSION, database.userVersion())
        }
    }

    @Test
    fun migratingIsIdempotent() {
        JdbcPresetDatabase.inMemory().use { database ->
            database.migrate()
            database.migrate()
            assertEquals(PresetSchema.CURRENT_VERSION, database.userVersion())
        }
    }

    @Test
    fun aFrozenV1DatabaseKeepsItsDataWhenTheCurrentSchemaIsApplied() {
        JdbcPresetDatabase.inMemory().use { database ->
            FROZEN_V1.forEach(database::execute)
            database.setUserVersion(1)
            database.insertPreservedRow()

            database.migrate()

            assertEquals(PresetSchema.CURRENT_VERSION, database.userVersion())
            val row = database
                .query(
                    "SELECT name, kind, slot_number, payload, payload_version " +
                        "FROM preset_payload WHERE name = 'Preserved'"
                )
                .single()
            assertEquals("Preserved", row.stringColumn("name"))
            assertEquals("intervals", row.stringColumn("kind"))
            assertNull(
                "a legacy custom keeps a null slot number",
                row.nullableLongColumn("slot_number")
            )
            assertEquals(1L, row.longColumn("payload_version"))
            val payload = payloadFields(row.stringColumn("payload"))
            assertEquals(
                "an existing row keeps the reviewed note length",
                700L,
                payload.getValue("note_duration_ms").jsonPrimitive.long
            )
            assertEquals(600L, payload.getValue("answer_gap_ms").jsonPrimitive.long)
            assertEquals(400L, payload.getValue("repeat_gap_ms").jsonPrimitive.long)
            assertTrue(
                "the ordered interval list survives",
                payload.getValue("interval_ids").jsonArray
                    .map { it.jsonPrimitive.content } == listOf("minor_third")
            )
        }
    }

    @Test
    fun theSecondMigrationIsTheFrozenV2Step() {
        assertEquals(
            "Changing a released schema statement requires a new migration, not a silent edit",
            FROZEN_V2_MIGRATION,
            PresetSchema.MIGRATIONS[2]
        )
    }

    @Test
    fun aFrozenV2DatabaseKeepsItsDataWhenTheCurrentSchemaIsApplied() {
        JdbcPresetDatabase.inMemory().use { database ->
            FROZEN_V1.forEach(database::execute)
            database.setUserVersion(1)
            database.insertPreservedRow()
            FROZEN_V2_MIGRATION.forEach(database::execute)
            database.setUserVersion(2)

            database.migrate()

            assertEquals(PresetSchema.CURRENT_VERSION, database.userVersion())
            val row = database
                .query(
                    "SELECT name, payload FROM preset_payload WHERE name = 'Preserved'"
                )
                .single()
            assertEquals("Preserved", row.stringColumn("name"))
            val payload = payloadFields(row.stringColumn("payload"))
            assertEquals(700L, payload.getValue("note_duration_ms").jsonPrimitive.long)
            assertFalse("a v2 row draws its root", payload.contains("fixed_root"))
        }
    }

    @Test
    fun aMigratedDatabaseHasTheSameShapeAsAFreshOne() {
        JdbcPresetDatabase.inMemory().use { fresh ->
            JdbcPresetDatabase.inMemory().use { fromV1 ->
                JdbcPresetDatabase.inMemory().use { fromV2 ->
                    FROZEN_V1.forEach(fromV1::execute)
                    fromV1.setUserVersion(1)

                    FROZEN_V1.forEach(fromV2::execute)
                    fromV2.setUserVersion(1)
                    FROZEN_V2_MIGRATION.forEach(fromV2::execute)
                    fromV2.setUserVersion(2)

                    fresh.migrate()
                    fromV1.migrate()
                    fromV2.migrate()

                    assertEquals(schemaOf(fresh), schemaOf(fromV1))
                    assertEquals(schemaOf(fresh), schemaOf(fromV2))
                }
            }
        }
    }

    @Test
    fun aNewerStoredSchemaIsRefusedRatherThanRecreated() {
        JdbcPresetDatabase.inMemory().use { database ->
            database.setUserVersion(PresetSchema.CURRENT_VERSION + 1)
            assertThrows(IllegalStateException::class.java) { database.migrate() }
        }
    }

    @Test
    fun aDowngradeHasNoMigrationPath() {
        assertThrows(IllegalArgumentException::class.java) {
            PresetSchema.statementsToUpgrade(fromVersion = 2, toVersion = 1)
        }
    }

    /** The shape every table declares, independent of the order rows were inserted. */
    private fun schemaOf(database: JdbcPresetDatabase): List<String> = database.query(
        "SELECT type, name, sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' " +
            "ORDER BY name"
    ).map { row ->
        "${row.stringColumn("type")}|${row.stringColumn("name")}|" +
            row.stringColumn("sql")
    }

    @Test
    fun theVersionThirteenMigrationRemovesTheOldIntervalWalkStarter() {
        JdbcPresetDatabase.inMemory().use { database ->
            database.migrate()
            database.setUserVersion(12)
            database.execute(
                "INSERT INTO preset_payload (" +
                    "id, kind, slot_number, name, payload, payload_version, is_empty, built_in, " +
                    "created_at_epoch_ms, updated_at_epoch_ms" +
                    ") VALUES ('builtin:interval_walk:starter:v1', 'interval_walk', NULL, " +
                    "'Ascending thirds and fifths', '{}', 1, 0, 1, 0, 0)"
            )
            database.migrate()
            assertEquals(PresetSchema.CURRENT_VERSION, database.userVersion())
            assertTrue(
                "the old four-step starter must be removed so the current one can be seeded",
                database.query(
                    "SELECT id FROM preset_payload " +
                        "WHERE id = 'builtin:interval_walk:starter:v1'"
                ).isEmpty()
            )
        }
    }

    @Test
    fun aFailedVersionNineMigrationRollsBackEveryPhaseAndRetrySucceeds() {
        JdbcPresetDatabase.inMemory().use { database ->
            FROZEN_V1.forEach(database::execute)
            database.setUserVersion(1)
            database.insertPreservedRow()
            val failing = FailingMigrationDatabase(database)
            failing.failOnSql = "INSERT INTO preset_payload"

            assertThrows(IllegalStateException::class.java) { failing.migrate() }

            assertEquals("the stored version is untouched", 1, database.userVersion())
            assertTrue(
                "the old tables survive the rollback",
                database.query("SELECT * FROM preset_entity").isNotEmpty()
            )
            assertTrue(
                "the new table is rolled back with everything else",
                database.query(
                    "SELECT name FROM sqlite_master WHERE name = 'preset_payload'"
                ).isEmpty()
            )

            // Retrying the same migration succeeds once the fault is gone.
            failing.failOnSql = null
            failing.migrate()
            assertEquals(PresetSchema.CURRENT_VERSION, database.userVersion())
            val preserved = database.query(
                "SELECT payload FROM preset_payload WHERE name = 'Preserved'"
            ).single()
            assertTrue(preserved.stringColumn("payload").contains("minor_third"))
            assertEquals(
                "one converted row plus forty-five slots",
                46,
                database.query("SELECT COUNT(*) AS count FROM preset_payload")
                    .single().intColumn("count")
            )
        }
    }
}

/** A driver whose migration can be failed at a chosen statement, to prove rollback. */
internal class FailingMigrationDatabase(private val delegate: JdbcPresetDatabase) : PresetDatabase {
    var failOnSql: String? = null

    override fun migrate() {
        val current = delegate.userVersion()
        check(current <= PresetSchema.CURRENT_VERSION)
        if (current == PresetSchema.CURRENT_VERSION) return
        transaction {
            PresetSchema.migrationsToUpgrade(current, PresetSchema.CURRENT_VERSION)
                .forEach { migration ->
                    when (migration) {
                        is PresetMigration.Statements ->
                            migration.statements.forEach { execute(it) }
                        is PresetMigration.Transform -> migration.transform.invoke(this)
                    }
                }
            delegate.setUserVersion(PresetSchema.CURRENT_VERSION)
        }
    }

    override fun execute(sql: String, bind: List<Any?>) {
        if (failOnSql?.let { sql.startsWith(it) } == true) {
            failOnSql = null
            throw IllegalStateException("Simulated migration failure")
        }
        delegate.execute(sql, bind)
    }

    override fun executeUpdate(sql: String, bind: List<Any?>): Int =
        delegate.executeUpdate(sql, bind)

    override fun query(sql: String, bind: List<Any?>): List<Map<String, Any?>> =
        delegate.query(sql, bind)

    override fun <T> transaction(work: () -> T): T = delegate.transaction(work)
}
