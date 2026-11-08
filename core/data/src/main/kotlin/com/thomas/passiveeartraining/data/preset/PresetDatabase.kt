package com.thomas.passiveeartraining.data.preset

import com.thomas.passiveeartraining.training.provider.TrainingKind

/**
 * The small SQL surface the preset store needs.
 *
 * Production maps this onto the platform's `android.database.sqlite`; the host tests map it onto a
 * real SQLite engine through JDBC. Both run the same [PresetSchema] statements, so the schema and
 * every migration are exercised on the host and on a device rather than only in one environment.
 */
internal interface PresetDatabase {
    /**
     * Creates or upgrades the schema and returns once it is at [PresetSchema.CURRENT_VERSION].
     *
     * Idempotent. A stored schema newer than this build refuses rather than being overwritten or
     * destructively recreated.
     */
    fun migrate()

    /** Runs a statement that returns no rows. */
    fun execute(sql: String, bind: List<Any?> = emptyList())

    /** Runs a statement and returns the number of affected rows. */
    fun executeUpdate(sql: String, bind: List<Any?> = emptyList()): Int

    /** Runs a query, returning every row as a column-name-to-value map. */
    fun query(sql: String, bind: List<Any?> = emptyList()): List<Map<String, Any?>>

    /**
     * Runs [work] as one transaction.
     *
     * A failure of any kind rolls every write back and propagates, so a failed import cannot leave
     * half of a batch behind.
     */
    fun <T> transaction(work: () -> T): T
}

/**
 * The preset schema, version 1.
 *
 * Migrations are hand-written statements keyed by the version they reach. Fresh creation is the
 * same code path as an upgrade from version 0, so it is covered by the migration tests too. A
 * future version adds one entry here and a test that upgrades a database created by the frozen
 * previous version; editing an already-issued migration is forbidden because an installed database
 * has already run it.
 */
internal object PresetSchema {

    /** Schema version this build creates and understands. */
    const val CURRENT_VERSION: Int = 13

    /**
     * Every migration step, keyed by the schema version it reaches.
     *
     * Statement lists are frozen once released: the JSON document and a user's installed database
     * may already depend on them.
     */
    val MIGRATIONS: Map<Int, List<String>> = mapOf(
        1 to listOf(
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
        ),
        2 to listOf(
            // The Stage 4 UI exposes note and gap lengths, so a preset must carry them. Existing
            // rows keep the reviewed defaults rather than becoming unusable.
            "ALTER TABLE preset_entity ADD COLUMN note_duration_ms INTEGER NOT NULL DEFAULT 700",
            "ALTER TABLE preset_entity ADD COLUMN answer_gap_ms INTEGER NOT NULL DEFAULT 600",
            "ALTER TABLE preset_entity ADD COLUMN repeat_gap_ms INTEGER NOT NULL DEFAULT 400"
        ),
        3 to listOf(
            // The advanced panel's optional fixed note; NULL means the root is drawn.
            "ALTER TABLE preset_entity ADD COLUMN fixed_root INTEGER"
        ),
        4 to listOf(
            // The complete saved configuration: mixed presentations, deep-repetition counts and
            // the remaining recipe timings. Existing rows keep the reviewed defaults.
            "ALTER TABLE preset_entity ADD COLUMN presentations TEXT",
            "ALTER TABLE preset_entity ADD COLUMN example_repeats INTEGER NOT NULL DEFAULT 1",
            "ALTER TABLE preset_entity ADD COLUMN trailing_example_repeats INTEGER NOT NULL " +
                "DEFAULT 0",
            "ALTER TABLE preset_entity ADD COLUMN melodic_gap_ms INTEGER NOT NULL DEFAULT 100",
            "ALTER TABLE preset_entity ADD COLUMN final_silence_ms INTEGER NOT NULL DEFAULT 2000"
        ),
        5 to listOf(
            // The optional pair separator cue before each exercise.
            "ALTER TABLE preset_entity ADD COLUMN separator_cue INTEGER NOT NULL DEFAULT 0"
        ),
        6 to listOf(
            // Deep repetition and answer-first recall were removed from the product. A stored
            // preset that used one of them loads as recognition instead of making the whole
            // preset library unreadable.
            "UPDATE preset_entity SET mode = 'RECOGNITION' " +
                "WHERE mode IN ('DEEP_REPETITION', 'ANSWER_FIRST_RECALL')"
        ),
        7 to listOf(
            // Five user slots replace free preset creation. They start empty; the user fills each
            // one from the current setup. `is_empty` keeps an unfilled slot distinct from a real
            // preset so the screen can hide its use action and mark it.
            "ALTER TABLE preset_entity ADD COLUMN is_empty INTEGER NOT NULL DEFAULT 0",
            slotInsert(1),
            slotInsert(2),
            slotInsert(3),
            slotInsert(4),
            slotInsert(5)
        ),
        8 to listOf(
            // One-shot marker: an upgrade that finds customs created before the slots existed
            // requests their adoption into empty slots. A fresh store with no customs leaves no
            // marker. This is a separate migration because version 7 was already released.
            "CREATE TABLE IF NOT EXISTS preset_meta (" +
                "key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)",
            "INSERT INTO preset_meta (key, value) " +
                "SELECT '${ADOPTION_KEY}', '1' WHERE EXISTS (" +
                "SELECT 1 FROM preset_entity WHERE built_in = 0 AND id NOT IN (" +
                PresetSlots.IDS.joinToString(", ") { "'${it.value}'" } +
                "))"
        ),
        10 to listOf(
            // The Recent sort follows the preset the user applied last, so the store keeps that
            // moment beside the saved timestamp. Existing rows have never been used.
            "ALTER TABLE preset_payload ADD COLUMN last_used_at_epoch_ms INTEGER NOT NULL DEFAULT 0"
        ),
        11 to listOf(
            // The interval starters were replaced by the owner's four reviewed defaults. Remove
            // the old built-in rows so the seeding below cannot show both sets; user presets are
            // never touched.
            "DELETE FROM preset_payload WHERE built_in = 1 AND kind = 'intervals'"
        ),
        12 to listOf(
            // Interval walk owns five empty slots. The kind is new, so an existing v11 store has
            // none; OR IGNORE also makes the step harmless for a fresh or v8 upgrade whose
            // migration 9 already created every current kind.
            walkSlotInsert(1),
            walkSlotInsert(2),
            walkSlotInsert(3),
            walkSlotInsert(4),
            walkSlotInsert(5)
        ),
        13 to listOf(
            // The interval-walk starter now has three steps. The starter row was seeded once by
            // older builds and is not rewritten by seedStarters, so remove the old built-in row;
            // the next open seeds the current three-step starter under the same fixed identity.
            "DELETE FROM preset_payload " +
                "WHERE id = 'builtin:interval_walk:starter:v1' AND built_in = 1"
        )
    )

    /**
     * Driver-level transformation steps, keyed by the version they reach.
     *
     * A version appears in exactly one of [MIGRATIONS] and [TRANSFORMS]. Transformations run inside
     * the caller's transaction and use only the [PresetDatabase] surface, so Android and the host
     * tests execute one shared implementation (M2.3).
     */
    val TRANSFORMS: Map<Int, PresetDatabase.() -> Unit> = mapOf(
        9 to { migratePresetSchemaToV9() }
    )

    /** Metadata key that asks once for pre-slot customs to be moved into empty slots. */
    const val ADOPTION_KEY: String = "adopt_legacy_customs"

    /**
     * The statement that creates one empty user slot.
     *
     * Every value that a slot needs before it is filled is written explicitly; the remaining
     * columns keep the schema defaults applied by earlier migrations.
     */
    private fun walkSlotInsert(index: Int): String {
        val kind = TrainingKind.INTERVAL_WALK
        val id = PresetSlots.slotIds(kind)[index - 1]
        return "INSERT OR IGNORE INTO preset_payload (" +
            "id, kind, slot_number, name, payload, payload_version, is_empty, built_in, " +
            "created_at_epoch_ms, updated_at_epoch_ms" +
            ") VALUES ('${id.value}', '${kind.id}', $index, '${PresetSlots.defaultName(index)}', " +
            "NULL, NULL, 1, 0, 0, 0)"
    }

    private fun slotInsert(index: Int): String = "INSERT INTO preset_entity (" +
        "id, name, created_at_epoch_ms, updated_at_epoch_ms, built_in, " +
        "minimum_note, maximum_note, presentation, mode, thinking_silence_ms, " +
        "replay_count, music_gain, voice_gain, stop_kind, is_empty" +
        ") VALUES (" +
        "'${PresetSlots.IDS[index - 1].value}', '${PresetSlots.defaultName(index)}', " +
        "0, 0, 0, 60, 72, 'ASCENDING', 'RECOGNITION', 2000, 0, 1.0, 0.25, " +
        "'UNTIL_STOPPED', 1)"

    /**
     * The statements that carry a database from [fromVersion] to [toVersion].
     *
     * @throws IllegalArgumentException for a downgrade or a version this build cannot reach.
     * @throws IllegalStateException when a version in the range has no migration, which a test
     *   asserts cannot happen for the versions this build claims to support.
     */
    fun statementsToUpgrade(fromVersion: Int, toVersion: Int): List<String> {
        require(fromVersion <= toVersion) {
            "Cannot downgrade the preset schema from $fromVersion to $toVersion"
        }
        require(toVersion <= CURRENT_VERSION) {
            "The preset schema at version $fromVersion is newer than this build ($CURRENT_VERSION)"
        }
        return (fromVersion + 1..toVersion).flatMap { target ->
            MIGRATIONS[target]
                ?: throw IllegalStateException(
                    "No statement migration reaches version $target; use migrationsToUpgrade"
                )
        }
    }

    /**
     * The ordered steps from [fromVersion] to [toVersion].
     *
     * Each version resolves to frozen statements or a transformation; a gap is a build error rather
     * than a silently skipped version.
     */
    fun migrationsToUpgrade(fromVersion: Int, toVersion: Int): List<PresetMigration> {
        require(fromVersion <= toVersion) {
            "Cannot downgrade the preset schema from $fromVersion to $toVersion"
        }
        require(toVersion <= CURRENT_VERSION) {
            "The preset schema at version $fromVersion is newer than this build ($CURRENT_VERSION)"
        }
        return (fromVersion + 1..toVersion).map { target ->
            val statements = MIGRATIONS[target]
            val transform = TRANSFORMS[target]
            when {
                statements != null && transform == null ->
                    PresetMigration.Statements(statements)
                transform != null && statements == null -> PresetMigration.Transform(transform)
                else -> throw IllegalStateException(
                    "No preset schema migration reaches version $target"
                )
            }
        }
    }
}

/** One ordered schema step: frozen statements or a driver-level transformation. */
internal sealed interface PresetMigration {
    /** SQL statements executed in order. */
    data class Statements(val statements: List<String>) : PresetMigration

    /** A transformation executed inside the caller's transaction. */
    data class Transform(val transform: PresetDatabase.() -> Unit) : PresetMigration
}

/** Reads a text column, failing loudly when the stored value is missing or of the wrong type. */
internal fun Map<String, Any?>.stringColumn(name: String): String {
    val value = this[name] ?: throw PresetStorageException("Column '$name' is missing or NULL")
    return value as? String
        ?: throw PresetStorageException("Column '$name' is not text: $value")
}

/** Reads a nullable text column. */
internal fun Map<String, Any?>.nullableStringColumn(name: String): String? {
    val value = this[name] ?: return null
    return value as? String
        ?: throw PresetStorageException("Column '$name' is not text: $value")
}

/** Reads an integer column; SQLite may hand it back as any numeric type. */
internal fun Map<String, Any?>.longColumn(name: String): Long {
    val value = this[name] ?: throw PresetStorageException("Column '$name' is missing or NULL")
    return (value as? Number)?.toLong()
        ?: throw PresetStorageException("Column '$name' is not an integer: $value")
}

/** Reads an integer column that fits an `Int`. */
internal fun Map<String, Any?>.intColumn(name: String): Int = longColumn(name).toInt()

/** Reads a nullable integer column. */
internal fun Map<String, Any?>.nullableLongColumn(name: String): Long? {
    val value = this[name] ?: return null
    return (value as? Number)?.toLong()
        ?: throw PresetStorageException("Column '$name' is not an integer: $value")
}

/** Reads a floating-point column. */
internal fun Map<String, Any?>.doubleColumn(name: String): Double {
    val value = this[name] ?: throw PresetStorageException("Column '$name' is missing or NULL")
    return (value as? Number)?.toDouble()
        ?: throw PresetStorageException("Column '$name' is not numeric: $value")
}

/** Raised when stored preset data cannot be represented, which means the database is corrupt. */
class PresetStorageException(message: String) : IllegalStateException(message)
