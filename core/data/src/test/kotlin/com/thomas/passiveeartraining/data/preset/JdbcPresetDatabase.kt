package com.thomas.passiveeartraining.data.preset

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.Types

/**
 * A real SQLite engine for host tests.
 *
 * The production store runs on `android.database.sqlite`; this adapter runs the same
 * [PresetSchema] statements and the same SQL through SQLite itself, so the schema, the migrations
 * and every statement are tested on the host rather than only on a phone. It is test-only: nothing
 * in the shipped app references it.
 */
internal class JdbcPresetDatabase private constructor(private val connection: Connection) :
    PresetDatabase,
    AutoCloseable {

    /** Reads the SQLite schema version, the equivalent of the helper's stored version. */
    fun userVersion(): Int = connection.createStatement().use { statement ->
        statement.executeQuery("PRAGMA user_version").use { rows ->
            rows.next()
            rows.getInt(1)
        }
    }

    /** Sets the stored schema version, so a test can simulate an older or newer database. */
    fun setUserVersion(version: Int) {
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA user_version = $version")
        }
    }

    override fun migrate() {
        val current = userVersion()
        check(current <= PresetSchema.CURRENT_VERSION) {
            "The stored preset schema is $current, newer than ${PresetSchema.CURRENT_VERSION}"
        }
        if (current == PresetSchema.CURRENT_VERSION) return
        transaction {
            PresetSchema.migrationsToUpgrade(current, PresetSchema.CURRENT_VERSION)
                .forEach { migration ->
                    when (migration) {
                        is PresetMigration.Statements -> migration.statements.forEach {
                            execute(it)
                        }
                        is PresetMigration.Transform -> migration.transform.invoke(this)
                    }
                }
            setUserVersion(PresetSchema.CURRENT_VERSION)
        }
    }

    override fun execute(sql: String, bind: List<Any?>) {
        connection.prepareStatement(sql).use { statement ->
            statement.bindAll(bind)
            statement.executeUpdate()
        }
    }

    override fun executeUpdate(sql: String, bind: List<Any?>): Int =
        connection.prepareStatement(sql).use { statement ->
            statement.bindAll(bind)
            statement.executeUpdate()
        }

    override fun query(sql: String, bind: List<Any?>): List<Map<String, Any?>> =
        connection.prepareStatement(sql).use { statement ->
            statement.bindAll(bind)
            statement.executeQuery().use { rows ->
                val metadata = rows.metaData
                buildList {
                    while (rows.next()) {
                        val row = HashMap<String, Any?>()
                        for (column in 1..metadata.columnCount) {
                            row[metadata.getColumnLabel(column)] = rows.getObject(column)
                        }
                        add(row)
                    }
                }
            }
        }

    override fun <T> transaction(work: () -> T): T {
        val previous = connection.autoCommit
        connection.autoCommit = false
        return try {
            work().also { connection.commit() }
        } catch (failure: Throwable) {
            connection.rollback()
            throw failure
        } finally {
            connection.autoCommit = previous
        }
    }

    override fun close() {
        connection.close()
    }

    private fun PreparedStatement.bindAll(bind: List<Any?>) {
        bind.forEachIndexed { index, value ->
            if (value == null) {
                setNull(index + 1, Types.NULL)
            } else {
                setObject(index + 1, value)
            }
        }
    }

    companion object {
        /** A fresh, empty database that lives only for the test. */
        fun inMemory(): JdbcPresetDatabase =
            JdbcPresetDatabase(DriverManager.getConnection("jdbc:sqlite::memory:"))
    }
}
