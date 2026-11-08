package com.thomas.passiveeartraining.data.preset

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteProgram
import android.database.sqlite.SQLiteStatement

/**
 * The preset database on the platform's SQLite.
 *
 * Creation and every upgrade run the shared [PresetSchema] statements, so the schema tested on the
 * host is the schema a device runs. A downgrade refuses instead of recreating the file: destructive
 * migration is never a fix for a missing migration.
 */
internal class AndroidPresetDatabase(context: Context, fileName: String) :
    SQLiteOpenHelper(context.applicationContext, fileName, null, PresetSchema.CURRENT_VERSION),
    PresetDatabase {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // Android leaves foreign keys off by default; the schema relies on them for integrity.
        db.setForeignKeyConstraintsEnabled(true)
    }

    /**
     * The database currently being created or upgraded.
     *
     * `onCreate`/`onUpgrade` receive the already-open database, and calling `writableDatabase`
     * again from inside them is a recursive open, which the platform refuses. Every driver method
     * therefore uses this handle while a migration is running and the normal handle otherwise.
     */
    private var migrationDatabase: SQLiteDatabase? = null

    private val database: SQLiteDatabase get() = migrationDatabase ?: writableDatabase

    override fun onCreate(db: SQLiteDatabase) {
        withMigrationDatabase(db) {
            applyMigrations(db, PresetSchema.migrationsToUpgrade(0, PresetSchema.CURRENT_VERSION))
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        withMigrationDatabase(db) {
            applyMigrations(db, PresetSchema.migrationsToUpgrade(oldVersion, newVersion))
        }
    }

    private fun <T> withMigrationDatabase(db: SQLiteDatabase, work: () -> T): T {
        migrationDatabase = db
        return try {
            work()
        } finally {
            migrationDatabase = null
        }
    }

    /** Runs each step in order; the framework already wraps this in one transaction. */
    private fun applyMigrations(db: SQLiteDatabase, migrations: List<PresetMigration>) {
        migrations.forEach { migration ->
            when (migration) {
                is PresetMigration.Statements -> migration.statements.forEach(db::execSQL)
                is PresetMigration.Transform -> migration.transform.invoke(this)
            }
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int): Unit =
        throw SQLiteException(
            "The stored preset schema is version $oldVersion, newer than this build's " +
                "${PresetSchema.CURRENT_VERSION}; refusing to migrate down"
        )

    /** Opening the helper runs `onCreate` or `onUpgrade`; this only forces the open. */
    override fun migrate() {
        writableDatabase
    }

    override fun execute(sql: String, bind: List<Any?>) {
        database.execSQL(sql, bind.toTypedArray())
    }

    override fun executeUpdate(sql: String, bind: List<Any?>): Int =
        database.compileStatement(sql).use { statement ->
            statement.bindAll(bind)
            statement.executeUpdateDelete()
        }

    override fun query(sql: String, bind: List<Any?>): List<Map<String, Any?>> =
        // rawQuery takes text arguments; affinity converts them for numeric comparisons, and every
        // parameter this store passes is compared against TEXT keys.
        database.rawQuery(sql, bind.map { it?.toString() }.toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toRow())
                }
            }
        }

    override fun <T> transaction(work: () -> T): T {
        val db = database
        db.beginTransaction()
        return try {
            work().also { db.setTransactionSuccessful() }
        } finally {
            db.endTransaction()
        }
    }

    private fun SQLiteStatement.bindAll(bind: List<Any?>) {
        bind.forEachIndexed { index, value -> this.bindOne(index + 1, value) }
    }

    private fun SQLiteProgram.bindOne(index: Int, value: Any?) {
        when (value) {
            null -> bindNull(index)
            is String -> bindString(index, value)
            is Boolean -> bindLong(index, if (value) 1L else 0L)
            is Int -> bindLong(index, value.toLong())
            is Long -> bindLong(index, value)
            is Float -> bindDouble(index, value.toDouble())
            is Double -> bindDouble(index, value)
            else -> throw IllegalArgumentException(
                "Cannot bind ${value::class.java.name} at index $index"
            )
        }
    }
}

/** Copies one cursor row into a map, preserving SQLite's own storage classes. */
internal fun Cursor.toRow(): Map<String, Any?> = buildMap {
    for (index in 0 until columnCount) {
        val value = when (getType(index)) {
            Cursor.FIELD_TYPE_NULL -> null
            Cursor.FIELD_TYPE_INTEGER -> getLong(index)
            Cursor.FIELD_TYPE_FLOAT -> getDouble(index)
            Cursor.FIELD_TYPE_STRING -> getString(index)
            Cursor.FIELD_TYPE_BLOB -> getBlob(index)
            else -> null
        }
        put(getColumnName(index), value)
    }
}
