package com.thomas.passiveeartraining.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.thomas.passiveeartraining.music.SpellingPreference as MusicSpellingPreference
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.DegreeAnswerFormat
import com.thomas.passiveeartraining.training.context.ContextKind
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.ModeEdit
import com.thomas.passiveeartraining.training.settings.ModePayload
import com.thomas.passiveeartraining.training.settings.ModeWriteResult
import com.thomas.passiveeartraining.training.settings.ScaleDegreeSetup
import com.thomas.passiveeartraining.training.settings.SettingsDiagnostic
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory DataStore for the scale-degree persistence tests. */
private class ScaleDegreeStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
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

/** M8-T06: the scale-degree setup round-trips through the schema-2 preferences. */
class ScaleDegreeSettingsPersistenceTest {

    private fun repository(store: DataStore<Preferences> = ScaleDegreeStore()) =
        DataStoreSettingsRepository(store) to store

    private fun customSetup(): ScaleDegreeSetup = ScaleDegreeSetup.default().copy(
        keyValues = listOf(0, 9),
        tonalities = listOf(Tonality.NATURAL_MINOR),
        degrees = listOf(1, 3, 5),
        context = ContextKind.TONIC_DOMINANT_TONIC,
        contextGap = Duration.parse("750ms"),
        answerFormat = DegreeAnswerFormat.NOTE_NAME,
        spelling = MusicSpellingPreference.FLAT,
        minimumNote = 57,
        maximumNote = 84,
        mode = RecipeMode.EXPOSURE,
        replayCount = 2,
        noteDuration = Duration.parse("500ms"),
        melodicGap = Duration.parse("150ms"),
        stopPolicy = SessionStopPolicy.CompletedItems(15),
        seed = 7L,
        separatorCue = true
    )

    @Test
    fun aFreshStoreCarriesTheDefaultScaleDegreeSetup() = runTest {
        val (subject, _) = repository()
        val snapshot = subject.settings.first()
        assertEquals(ScaleDegreeSetup.default(), snapshot.settings.scaleDegreeSetup)
        assertTrue(snapshot.settings.modeSetups.containsKey(TrainingKind.SCALE_DEGREES))
        assertTrue(
            snapshot.settings.modeSetups.getValue(TrainingKind.SCALE_DEGREES).payload
                is ModePayload.ScaleDegrees
        )
        assertEquals(emptyList<SettingsDiagnostic>(), snapshot.diagnostics)
    }

    @Test
    fun aScaleDegreeSetupRoundTripsThroughTheRevisionCheckedWrite() = runTest {
        val (subject, store) = repository()
        val setup = customSetup()
        val revision = subject.settings.first().settings
            .modeSetups.getValue(TrainingKind.SCALE_DEGREES).revision
        val result = subject.updateMode(
            ModeEdit(TrainingKind.SCALE_DEGREES, revision) { ModePayload.ScaleDegrees(setup) }
        )
        assertTrue(
            "the scale-degree write must be accepted: $result",
            result is ModeWriteResult.Saved
        )

        val reread = DataStoreSettingsRepository(store).settings.first()
        assertEquals(setup, reread.settings.scaleDegreeSetup)
        assertEquals(
            "the context round-trips with the setup",
            ContextKind.TONIC_DOMINANT_TONIC,
            reread.settings.scaleDegreeSetup.context
        )
        assertEquals(
            "the context gap round-trips with the setup",
            Duration.parse("750ms"),
            reread.settings.scaleDegreeSetup.contextGap
        )
        assertEquals(
            "the stop policy round-trips with the setup",
            SessionStopPolicy.CompletedItems(15),
            reread.settings.scaleDegreeSetup.stopPolicy
        )
        assertEquals(
            "the seed round-trips with the setup",
            7L,
            reread.settings.scaleDegreeSetup.seed
        )
        assertTrue(reread.diagnostics.isEmpty())
    }

    @Test
    fun anUnusableStoredScaleDegreeSetupFallsBackAndIsReported() = runTest {
        val store = ScaleDegreeStore(
            mutablePreferencesOf(
                stringPreferencesKey("scale_degrees_degrees") to "1,42"
            )
        )
        val snapshot = DataStoreSettingsRepository(store).settings.first()
        assertEquals(ScaleDegreeSetup.default(), snapshot.settings.scaleDegreeSetup)
        assertTrue(
            snapshot.diagnostics.any {
                it is SettingsDiagnostic.InvalidValue && it.field == "scaleDegreeSetup"
            }
        )
    }

    @Test
    fun aStoredMovableDoAnswerFormatDecodesAsDegree() = runTest {
        val store = ScaleDegreeStore(
            mutablePreferencesOf(
                intPreferencesKey("schema_version") to 3,
                stringPreferencesKey("spelling") to "SOLFEGE",
                stringPreferencesKey("scale_degrees_answer_format") to "MOVABLE_DO"
            )
        )
        val snapshot = DataStoreSettingsRepository(store).settings.first()
        assertEquals(
            DegreeAnswerFormat.DEGREE,
            snapshot.settings.scaleDegreeSetup.answerFormat
        )
        assertTrue(
            "the schema-4 migration must not leave a diagnostic: ${snapshot.diagnostics}",
            snapshot.diagnostics.isEmpty()
        )
    }

    @Test
    fun aStoredDegreeAndSyllableAnswerFormatDecodesAsDegree() = runTest {
        val store = ScaleDegreeStore(
            mutablePreferencesOf(
                intPreferencesKey("schema_version") to 3,
                stringPreferencesKey("scale_degrees_answer_format") to "DEGREE_AND_SYLLABLE"
            )
        )
        val snapshot = DataStoreSettingsRepository(store).settings.first()
        assertEquals(
            DegreeAnswerFormat.DEGREE,
            snapshot.settings.scaleDegreeSetup.answerFormat
        )
        assertTrue(
            "the schema-4 migration must not leave a diagnostic: ${snapshot.diagnostics}",
            snapshot.diagnostics.isEmpty()
        )
    }

    @Test
    fun anUnknownStoredAnswerFormatStillFallsBackAndIsReported() = runTest {
        val store = ScaleDegreeStore(
            mutablePreferencesOf(
                intPreferencesKey("schema_version") to 3,
                stringPreferencesKey("scale_degrees_answer_format") to "FAHRENHEIT"
            )
        )
        val snapshot = DataStoreSettingsRepository(store).settings.first()
        assertEquals(
            DegreeAnswerFormat.DEGREE,
            snapshot.settings.scaleDegreeSetup.answerFormat
        )
        assertTrue(
            snapshot.diagnostics.any {
                it is SettingsDiagnostic.InvalidValue && it.field == "scaleDegreesAnswerFormat"
            }
        )
    }

    @Test
    fun aNewerScaleDegreeConfigVersionIsProtectedFromWrites() = runTest {
        val store = ScaleDegreeStore(
            mutablePreferencesOf(
                intPreferencesKey("scale_degrees_config_version") to 99
            )
        )
        val subject = DataStoreSettingsRepository(store)
        val snapshot = subject.settings.first()
        assertEquals(ScaleDegreeSetup.default(), snapshot.settings.scaleDegreeSetup)
        assertTrue(
            snapshot.diagnostics.any {
                it is SettingsDiagnostic.NewerModeConfig &&
                    it.kind == TrainingKind.SCALE_DEGREES
            }
        )
        val revision = snapshot.settings.modeSetups
            .getValue(TrainingKind.SCALE_DEGREES).revision
        val result = subject.updateMode(
            ModeEdit(TrainingKind.SCALE_DEGREES, revision) {
                ModePayload.ScaleDegrees(customSetup())
            }
        )
        assertTrue(
            "a protected payload must refuse writes: $result",
            result is ModeWriteResult.Rejected
        )
    }
}
