package com.thomas.passiveeartraining.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.thomas.passiveeartraining.music.ChordPresentation
import com.thomas.passiveeartraining.training.ChordAnswerMode
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.ChordSetup
import com.thomas.passiveeartraining.training.settings.ModeEdit
import com.thomas.passiveeartraining.training.settings.ModePayload
import com.thomas.passiveeartraining.training.settings.ModeWriteResult
import com.thomas.passiveeartraining.training.settings.SettingsDiagnostic
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory DataStore for the chord persistence tests. */
private class ChordStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
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

/** M5-T07: the chord setup round-trips through the schema-2 preferences. */
class ChordSettingsPersistenceTest {

    private fun repository(store: DataStore<Preferences> = ChordStore()) =
        DataStoreSettingsRepository(store) to store

    private fun customSetup(): ChordSetup = ChordSetup.default().copy(
        qualityIds = listOf("major_seventh", "minor_seventh"),
        inversions = listOf(0, 1, 2, 3),
        rootValues = listOf(0, 2, 7),
        presentation = ChordPresentation.ARPEGGIO,
        answerMode = ChordAnswerMode.QUALITY_AND_INVERSION,
        mode = RecipeMode.EXPOSURE,
        replayCount = 2,
        noteDuration = Duration.parse("500ms"),
        melodicGap = Duration.parse("150ms"),
        stopPolicy = SessionStopPolicy.CompletedItems(15),
        seed = 7L,
        separatorCue = true
    )

    @Test
    fun aFreshStoreCarriesTheDefaultChordSetup() = runTest {
        val (subject, _) = repository()
        val snapshot = subject.settings.first()
        assertEquals(ChordSetup.default(), snapshot.settings.chordSetup)
        assertTrue(snapshot.settings.modeSetups.containsKey(TrainingKind.CHORDS))
        assertTrue(
            snapshot.settings.modeSetups.getValue(TrainingKind.CHORDS).payload
                is ModePayload.Chords
        )
        assertEquals(emptyList<SettingsDiagnostic>(), snapshot.diagnostics)
    }

    @Test
    fun aChordSetupRoundTripsThroughTheRevisionCheckedWrite() = runTest {
        val (subject, store) = repository()
        val setup = customSetup()
        val revision = subject.settings.first().settings
            .modeSetups.getValue(TrainingKind.CHORDS).revision
        val result = subject.updateMode(
            ModeEdit(TrainingKind.CHORDS, revision) { ModePayload.Chords(setup) }
        )
        assertTrue("the chord write must be accepted: $result", result is ModeWriteResult.Saved)

        val reread = DataStoreSettingsRepository(store).settings.first()
        assertEquals(setup, reread.settings.chordSetup)
        assertTrue(reread.diagnostics.isEmpty())
    }

    @Test
    fun anUnusableStoredChordSetupFallsBackAndIsReported() = runTest {
        val store = ChordStore(
            mutablePreferencesOf(
                androidx.datastore.preferences.core.stringPreferencesKey("chords_qualities") to
                    "bogus_quality"
            )
        )
        val snapshot = DataStoreSettingsRepository(store).settings.first()
        assertEquals(ChordSetup.default(), snapshot.settings.chordSetup)
        assertTrue(
            snapshot.diagnostics.any {
                it is SettingsDiagnostic.InvalidValue && it.field == "chordSetup"
            }
        )
    }

    @Test
    fun aNewerChordConfigVersionIsProtectedFromWrites() = runTest {
        val store = ChordStore(
            mutablePreferencesOf(
                intPreferencesKey("chords_config_version") to 99
            )
        )
        val subject = DataStoreSettingsRepository(store)
        val snapshot = subject.settings.first()
        assertEquals(ChordSetup.default(), snapshot.settings.chordSetup)
        assertTrue(
            snapshot.diagnostics.any {
                it is SettingsDiagnostic.NewerModeConfig && it.kind == TrainingKind.CHORDS
            }
        )
        val revision = snapshot.settings.modeSetups.getValue(TrainingKind.CHORDS).revision
        val result = subject.updateMode(
            ModeEdit(TrainingKind.CHORDS, revision) { ModePayload.Chords(customSetup()) }
        )
        assertTrue(
            "a protected payload must refuse writes: $result",
            result is ModeWriteResult.Rejected
        )
    }
}
