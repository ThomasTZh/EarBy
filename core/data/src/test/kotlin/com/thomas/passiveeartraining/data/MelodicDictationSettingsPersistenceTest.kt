package com.thomas.passiveeartraining.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.SpellingPreference as MusicSpellingPreference
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.MelodicAnswerFormat
import com.thomas.passiveeartraining.training.MelodicDirectionBias
import com.thomas.passiveeartraining.training.context.ContextKind
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.MelodicDictationSetup
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory DataStore for the melodic-dictation persistence tests. */
private class MelodicDictationStore(initial: Preferences = emptyPreferences()) :
    DataStore<Preferences> {
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

/** M10: the melodic-dictation setup round-trips through the schema-2 preferences. */
class MelodicDictationSettingsPersistenceTest {

    private fun repository(store: DataStore<Preferences> = MelodicDictationStore()) =
        DataStoreSettingsRepository(store) to store

    private fun customSetup(): MelodicDictationSetup = MelodicDictationSetup.default().copy(
        keyValues = listOf(0, 9),
        tonalities = listOf(Tonality.NATURAL_MINOR),
        context = ContextKind.TONIC_TRIAD,
        contextGap = Duration.parse("750ms"),
        minimumNote = MidiNote.of(57),
        maximumNote = MidiNote.of(84),
        phraseLength = 5,
        startDegree = 1,
        endDegree = null,
        allowedSteps = listOf(-3, 0, 2),
        maximumLeap = 3,
        bias = MelodicDirectionBias.DESCENDING,
        answerFormat = MelodicAnswerFormat.DIRECTED_INTERVAL_SEQUENCE,
        spelling = MusicSpellingPreference.FLAT,
        mode = RecipeMode.EXPOSURE,
        replayCount = 2,
        noteDuration = Duration.parse("500ms"),
        melodicGap = Duration.parse("150ms"),
        stopPolicy = SessionStopPolicy.CompletedItems(15),
        seed = 7L,
        separatorCue = true
    )

    @Test
    fun aFreshStoreCarriesTheDefaultMelodicDictationSetup() = runTest {
        val (subject, _) = repository()
        val snapshot = subject.settings.first()
        assertEquals(MelodicDictationSetup.default(), snapshot.settings.melodicDictationSetup)
        assertTrue(snapshot.settings.modeSetups.containsKey(TrainingKind.MELODIC_DICTATION))
        assertTrue(
            snapshot.settings.modeSetups.getValue(TrainingKind.MELODIC_DICTATION).payload
                is ModePayload.MelodicDictation
        )
        assertEquals(emptyList<SettingsDiagnostic>(), snapshot.diagnostics)
    }

    @Test
    fun aMelodicDictationSetupRoundTripsThroughTheRevisionCheckedWrite() = runTest {
        val (subject, store) = repository()
        val setup = customSetup()
        val revision = subject.settings.first().settings
            .modeSetups.getValue(TrainingKind.MELODIC_DICTATION).revision
        val result = subject.updateMode(
            ModeEdit(TrainingKind.MELODIC_DICTATION, revision) {
                ModePayload.MelodicDictation(setup)
            }
        )
        assertTrue(
            "the melodic-dictation write must be accepted: $result",
            result is ModeWriteResult.Saved
        )

        val reread = DataStoreSettingsRepository(store).settings.first()
        assertEquals(setup, reread.settings.melodicDictationSetup)
        assertEquals(
            "the tonalities round-trip with the setup",
            listOf(Tonality.NATURAL_MINOR),
            reread.settings.melodicDictationSetup.tonalities
        )
        assertEquals(
            "the signed steps round-trip with the setup",
            listOf(-3, 0, 2),
            reread.settings.melodicDictationSetup.allowedSteps
        )
        assertEquals(
            "the maximum leap round-trips with the setup",
            3,
            reread.settings.melodicDictationSetup.maximumLeap
        )
        assertEquals(
            "the direction bias round-trips with the setup",
            MelodicDirectionBias.DESCENDING,
            reread.settings.melodicDictationSetup.bias
        )
        assertEquals(
            "the start degree round-trips with the setup",
            1,
            reread.settings.melodicDictationSetup.startDegree
        )
        assertNull(
            "the any-degree end choice round-trips as null rather than the default",
            reread.settings.melodicDictationSetup.endDegree
        )
        assertEquals(
            "the answer format round-trips with the setup",
            MelodicAnswerFormat.DIRECTED_INTERVAL_SEQUENCE,
            reread.settings.melodicDictationSetup.answerFormat
        )
        assertEquals(
            "the stop policy round-trips with the setup",
            SessionStopPolicy.CompletedItems(15),
            reread.settings.melodicDictationSetup.stopPolicy
        )
        assertEquals(
            "the seed round-trips with the setup",
            7L,
            reread.settings.melodicDictationSetup.seed
        )
        assertTrue(reread.diagnostics.isEmpty())
    }

    @Test
    fun aStoredMovableDoSequenceAnswerFormatDecodesAsDegreeSequence() = runTest {
        val store = MelodicDictationStore(
            mutablePreferencesOf(
                intPreferencesKey("schema_version") to 3,
                stringPreferencesKey("melodic_dictation_answer_format") to "MOVABLE_DO_SEQUENCE"
            )
        )
        val snapshot = DataStoreSettingsRepository(store).settings.first()
        assertEquals(
            MelodicAnswerFormat.DEGREE_SEQUENCE,
            snapshot.settings.melodicDictationSetup.answerFormat
        )
        assertTrue(
            "the schema-4 migration must not leave a diagnostic: ${snapshot.diagnostics}",
            snapshot.diagnostics.isEmpty()
        )
    }

    @Test
    fun anUnusableStoredMelodicDictationSetupFallsBackAndIsReported() = runTest {
        val store = MelodicDictationStore(
            mutablePreferencesOf(
                intPreferencesKey("melodic_dictation_min_note") to 84,
                intPreferencesKey("melodic_dictation_max_note") to 48
            )
        )
        val snapshot = DataStoreSettingsRepository(store).settings.first()
        assertEquals(MelodicDictationSetup.default(), snapshot.settings.melodicDictationSetup)
        assertTrue(
            snapshot.diagnostics.any {
                it is SettingsDiagnostic.InvalidValue && it.field == "melodicDictationSetup"
            }
        )
    }

    @Test
    fun aStoredCycleStopPolicyFallsBackWithTheStopExplanation() = runTest {
        val store = MelodicDictationStore(
            mutablePreferencesOf(
                stringPreferencesKey("melodic_dictation_stop_kind") to "AFTER_CYCLE"
            )
        )
        val snapshot = DataStoreSettingsRepository(store).settings.first()
        assertEquals(MelodicDictationSetup.default(), snapshot.settings.melodicDictationSetup)
        val diagnostic = snapshot.diagnostics.single {
            it is SettingsDiagnostic.InvalidValue && it.field == "melodicDictationSetup"
        } as SettingsDiagnostic.InvalidValue
        assertTrue(
            "the fallback must carry the provider's cycle explanation: $diagnostic",
            diagnostic.detail.contains("no label cycle")
        )
    }

    @Test
    fun aNewerMelodicDictationConfigVersionIsProtectedFromWrites() = runTest {
        val store = MelodicDictationStore(
            mutablePreferencesOf(
                intPreferencesKey("melodic_dictation_config_version") to 99
            )
        )
        val subject = DataStoreSettingsRepository(store)
        val snapshot = subject.settings.first()
        assertEquals(MelodicDictationSetup.default(), snapshot.settings.melodicDictationSetup)
        assertTrue(
            snapshot.diagnostics.any {
                it is SettingsDiagnostic.NewerModeConfig &&
                    it.kind == TrainingKind.MELODIC_DICTATION
            }
        )
        val revision = snapshot.settings.modeSetups
            .getValue(TrainingKind.MELODIC_DICTATION).revision
        val result = subject.updateMode(
            ModeEdit(TrainingKind.MELODIC_DICTATION, revision) {
                ModePayload.MelodicDictation(customSetup())
            }
        )
        assertTrue(
            "a protected payload must refuse writes: $result",
            result is ModeWriteResult.Rejected
        )
    }
}
