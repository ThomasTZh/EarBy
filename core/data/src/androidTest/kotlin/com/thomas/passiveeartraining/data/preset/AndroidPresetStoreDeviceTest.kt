package com.thomas.passiveeartraining.data.preset

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.LastSetup
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The preset store on the platform's own SQLite.
 *
 * The host tests run the same schema and statements through a JDBC engine; this proves the Android
 * adapter: `SQLiteOpenHelper` creation, the transaction boundary, cursor mapping and foreign-key
 * behaviour on a real device database.
 */
@RunWith(AndroidJUnit4::class)
class AndroidPresetStoreDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val fileName = "preset-store-device-test.db"

    @Before
    fun deleteOldDatabase() {
        context.deleteDatabase(fileName)
    }

    @After
    fun cleanUp() {
        context.deleteDatabase(fileName)
    }

    private fun repository(): SqlitePresetRepository = SqlitePresetRepository(context, fileName)

    @Test
    fun thePlatformDatabaseSeedsStartersAndRoundTripsAPreset() = runBlocking {
        val store = repository()
        store.refresh()
        assertEquals(PresetStarters.ALL.size + 5, store.presets.value.size)
        assertEquals(PresetStarters.ALL.size, store.presets.value.count { it.builtIn })
        assertEquals(5, store.presets.value.count { !it.builtIn })

        val created = store.create(
            name = "Device preset",
            configuration = PresetConfiguration.Intervals(
                LastSetup.default().copy(
                    stopPolicy = SessionStopPolicy.CompletedItems(5)
                )
            ),
            seed = 99L
        )

        // A second repository over the same file reads the write back, so persistence is by file
        // and not by a process-local cache.
        val reopened = repository()
        reopened.refresh()
        assertEquals(created, reopened.presets.value.first { it.id == created.id })
    }

    @Test
    fun startersAreNotSeededTwiceAcrossOpens() = runBlocking {
        repository().refresh()
        val second = repository()
        second.refresh()
        assertEquals(PresetStarters.ALL.size + 5, second.presets.value.size)
    }

    @Test
    fun importAndExportRoundTripThroughThePlatformDatabase() = runBlocking {
        val store = repository()
        val created = store.create(
            "Exported",
            PresetConfiguration.Intervals(LastSetup.default()),
            seed = 123L
        )
        val text = store.export(listOf(created.id))

        // "Clear app data": remove the file and open the store again, which seeds the starters.
        context.deleteDatabase(fileName)
        val fresh = repository()
        fresh.refresh()
        val preview = fresh.previewImport(text)
        assertTrue("the export must preview cleanly", preview.rejected.isEmpty())

        val result = fresh.commitImport(preview)

        assertEquals(1, result.imported.size)
        assertEquals(created.configuration, result.imported.single().configuration)
        assertEquals(123L, result.imported.single().seed)
    }

    @Test
    fun thePlatformDatabasePassesAChangeOfSchemaVersionWithoutDataLoss() = runBlocking {
        val store = repository()
        val created = store.create(
            "Survivor",
            PresetConfiguration.Intervals(LastSetup.default())
        )

        // Opening again runs migrate(), which for this build is a no-op at the current version.
        val reopened = repository()
        reopened.refresh()

        assertTrue(reopened.presets.value.any { it.id == created.id })
    }

    @Test
    fun aVersionEightDatabaseMigratesOnThePlatformWithEveryRowPreserved() = runBlocking {
        createVersionEightDatabaseWithOneCustom()

        val store = repository()
        store.refresh()

        // The legacy custom was adopted into the first interval slot; all five slots exist and the
        // other thirty-five slots belong to the later kinds.
        val slots = store.presets.value.filter { it.id in PresetSlots.IDS }
        assertEquals(5, slots.size)
        val adopted = slots.single { !it.empty }
        assertEquals("Legacy device custom", adopted.name)
        assertEquals(
            listOf("minor_third"),
            (adopted.configuration as PresetConfiguration.Intervals).setup.intervalIds
        )
        assertEquals(5, store.slotsOf(TrainingKind.CHORDS).size)

        val rawSlotCount = store.slotsOf(TrainingKind.INTERVALS).size
        assertEquals(5, rawSlotCount)
        val database = android.database.sqlite.SQLiteDatabase.openDatabase(
            context.getDatabasePath(fileName).absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY
        )
        database.use { db ->
            val cursor = db.rawQuery(
                "SELECT COUNT(*) FROM preset_payload WHERE slot_number IS NOT NULL",
                emptyArray()
            )
            cursor.use {
                it.moveToFirst()
                assertEquals(
                    "forty-five kind-scoped slots survive the platform migration",
                    45,
                    it.getInt(0)
                )
            }
        }
    }

    /** Builds a genuine version-8 platform database with one legacy custom, as an upgrade input. */
    private fun createVersionEightDatabaseWithOneCustom() {
        val file = context.getDatabasePath(fileName)
        file.parentFile?.mkdirs()
        val database = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null)
        database.use { db ->
            // Stop before version 8 so the custom exists when the adoption marker is written.
            PresetSchema.statementsToUpgrade(0, 7).forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO preset_entity (" +
                    "id, name, created_at_epoch_ms, updated_at_epoch_ms, built_in, " +
                    "minimum_note, maximum_note, presentation, mode, thinking_silence_ms, " +
                    "replay_count, music_gain, voice_gain, stop_kind, note_duration_ms, " +
                    "answer_gap_ms, repeat_gap_ms, melodic_gap_ms, final_silence_ms" +
                    ") VALUES ('legacy-device-1', 'Legacy device custom', 123, 456, 0, 60, 72, " +
                    "'ASCENDING', 'RECOGNITION', 2000, 0, 1.0, 0.25, 'UNTIL_STOPPED', 700, " +
                    "600, 400, 100, 2000)"
            )
            db.execSQL(
                "INSERT INTO preset_interval (preset_id, position, interval_id) " +
                    "VALUES ('legacy-device-1', 0, 'minor_third')"
            )
            PresetSchema.statementsToUpgrade(7, 8).forEach(db::execSQL)
            db.version = 8
        }
    }
}
