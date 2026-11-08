package com.thomas.passiveeartraining.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.thomas.passiveeartraining.music.SpellingPreference as MusicSpellingPreference
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.ModeEdit
import com.thomas.passiveeartraining.training.settings.ModePayload
import com.thomas.passiveeartraining.training.settings.ModeWriteResult
import com.thomas.passiveeartraining.training.settings.NoteSetup
import com.thomas.passiveeartraining.training.settings.SettingsDiagnostic
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory DataStore for the note persistence tests. */
private class NoteStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences
    ): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

/** M7-T06: the note setup round-trips through the schema-2 preferences. */
class NoteSettingsPersistenceTest {

    private fun repository(store: DataStore<Preferences> = NoteStore()) =
        DataStoreSettingsRepository(store) to store

    private fun customSetup(): NoteSetup = NoteSetup.default().copy(
        pitchClassValues = listOf(0, 1, 7),
        accidental = MusicSpellingPreference.FLAT,
        speakOctave = true,
        mode = RecipeMode.EXPOSURE,
        replayCount = 2,
        noteDuration = Duration.parse("500ms"),
        melodicGap = Duration.parse("150ms"),
        stopPolicy = SessionStopPolicy.CompletedItems(15),
        seed = 7L,
        separatorCue = true
    )

    @Test
    fun aFreshStoreCarriesTheDefaultNoteSetup() = runTest {
        val (subject, _) = repository()
        val snapshot = subject.settings.first()
        assertEquals(NoteSetup.default(), snapshot.settings.noteSetup)
        assertTrue(snapshot.settings.modeSetups.containsKey(TrainingKind.NOTE_IDENTIFICATION))
        assertTrue(
            snapshot.settings.modeSetups.getValue(TrainingKind.NOTE_IDENTIFICATION).payload
                is ModePayload.Notes
        )
        assertEquals(emptyList<SettingsDiagnostic>(), snapshot.diagnostics)
    }

    @Test
    fun aNoteSetupRoundTripsThroughTheRevisionCheckedWrite() = runTest {
        val (subject, store) = repository()
        val setup = customSetup()
        val revision = subject.settings.first().settings
            .modeSetups.getValue(TrainingKind.NOTE_IDENTIFICATION).revision
        val result = subject.updateMode(
            ModeEdit(TrainingKind.NOTE_IDENTIFICATION, revision) { ModePayload.Notes(setup) }
        )
        assertTrue("the note write must be accepted: $result", result is ModeWriteResult.Saved)

        val reread = DataStoreSettingsRepository(store).settings.first()
        assertEquals(setup, reread.settings.noteSetup)
        assertEquals(
            "the stop policy round-trips with the setup",
            SessionStopPolicy.CompletedItems(15),
            reread.settings.noteSetup.stopPolicy
        )
        assertEquals("the seed round-trips with the setup", 7L, reread.settings.noteSetup.seed)
        assertTrue(reread.diagnostics.isEmpty())
    }

    @Test
    fun anUnusableStoredNoteSetupFallsBackAndIsReported() = runTest {
        val store = NoteStore(
            mutablePreferencesOf(
                stringPreferencesKey("notes_pitch_classes") to "0,42"
            )
        )
        val snapshot = DataStoreSettingsRepository(store).settings.first()
        assertEquals(NoteSetup.default(), snapshot.settings.noteSetup)
        assertTrue(
            snapshot.diagnostics.any {
                it is SettingsDiagnostic.InvalidValue && it.field == "noteSetup"
            }
        )
    }

    @Test
    fun aStoredSpellingKeyIsIgnoredAfterTheSchemaFourRemoval() = runTest {
        val store = NoteStore(
            mutablePreferencesOf(
                stringPreferencesKey("spelling") to "SOLFEGE",
                intPreferencesKey("schema_version") to 3,
                stringPreferencesKey("notes_pitch_classes") to "1",
                intPreferencesKey("notes_fixed_octave") to 4
            )
        )
        val snapshot = DataStoreSettingsRepository(store).settings.first()
        assertEquals(listOf(1), snapshot.settings.noteSetup.pitchClassValues)
        assertTrue(
            "the removed spelling and fixed-octave keys must not produce a diagnostic: " +
                "${snapshot.diagnostics}",
            snapshot.diagnostics.isEmpty()
        )
    }

    @Test
    fun aNewerNoteConfigVersionIsProtectedFromWrites() = runTest {
        val store = NoteStore(
            mutablePreferencesOf(
                intPreferencesKey("notes_config_version") to 99
            )
        )
        val subject = DataStoreSettingsRepository(store)
        val snapshot = subject.settings.first()
        assertEquals(NoteSetup.default(), snapshot.settings.noteSetup)
        assertTrue(
            snapshot.diagnostics.any {
                it is SettingsDiagnostic.NewerModeConfig &&
                    it.kind == TrainingKind.NOTE_IDENTIFICATION
            }
        )
        val revision = snapshot.settings.modeSetups
            .getValue(TrainingKind.NOTE_IDENTIFICATION).revision
        val result = subject.updateMode(
            ModeEdit(TrainingKind.NOTE_IDENTIFICATION, revision) {
                ModePayload.Notes(customSetup())
            }
        )
        assertTrue(
            "a protected payload must refuse writes: $result",
            result is ModeWriteResult.Rejected
        )
    }
}
