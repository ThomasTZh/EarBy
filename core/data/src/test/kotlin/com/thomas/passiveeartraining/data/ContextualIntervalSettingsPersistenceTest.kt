package com.thomas.passiveeartraining.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.SpellingPreference as MusicSpellingPreference
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.ContextualIntervalAnswerFormat
import com.thomas.passiveeartraining.training.context.ContextKind
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.ContextualIntervalSetup
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

/** In-memory DataStore for the intervals-in-context persistence tests. */
private class ContextualIntervalStore(initial: Preferences = emptyPreferences()) :
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

/** M9B-T05: the intervals-in-context setup round-trips through the schema-2 preferences. */
class ContextualIntervalSettingsPersistenceTest {

    private fun repository(store: DataStore<Preferences> = ContextualIntervalStore()) =
        DataStoreSettingsRepository(store) to store

    private fun customSetup(): ContextualIntervalSetup = ContextualIntervalSetup.default().copy(
        keyValues = listOf(0, 9),
        tonalities = listOf(Tonality.NATURAL_MINOR),
        intervalIds = listOf("minor_third", "perfect_fifth"),
        presentation = IntervalPresentation.DESCENDING,
        context = ContextKind.TONIC_DOMINANT_TONIC,
        contextGap = Duration.parse("750ms"),
        answerFormat = ContextualIntervalAnswerFormat.DEGREE_PAIR,
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
    fun aFreshStoreCarriesTheDefaultContextualIntervalSetup() = runTest {
        val (subject, _) = repository()
        val snapshot = subject.settings.first()
        assertEquals(ContextualIntervalSetup.default(), snapshot.settings.contextualIntervalSetup)
        assertTrue(snapshot.settings.modeSetups.containsKey(TrainingKind.INTERVALS_IN_CONTEXT))
        assertTrue(
            snapshot.settings.modeSetups.getValue(TrainingKind.INTERVALS_IN_CONTEXT).payload
                is ModePayload.ContextualIntervals
        )
        assertEquals(emptyList<SettingsDiagnostic>(), snapshot.diagnostics)
    }

    @Test
    fun aContextualIntervalSetupRoundTripsThroughTheRevisionCheckedWrite() = runTest {
        val (subject, store) = repository()
        val setup = customSetup()
        val revision = subject.settings.first().settings
            .modeSetups.getValue(TrainingKind.INTERVALS_IN_CONTEXT).revision
        val result = subject.updateMode(
            ModeEdit(TrainingKind.INTERVALS_IN_CONTEXT, revision) {
                ModePayload.ContextualIntervals(setup)
            }
        )
        assertTrue(
            "the intervals-in-context write must be accepted: $result",
            result is ModeWriteResult.Saved
        )

        val reread = DataStoreSettingsRepository(store).settings.first()
        assertEquals(setup, reread.settings.contextualIntervalSetup)
        assertEquals(
            "the tonalities round-trip with the setup",
            listOf(Tonality.NATURAL_MINOR),
            reread.settings.contextualIntervalSetup.tonalities
        )
        assertEquals(
            "the interval selection round-trips with the setup",
            listOf("minor_third", "perfect_fifth"),
            reread.settings.contextualIntervalSetup.intervalIds
        )
        assertEquals(
            "the presentation round-trips with the setup",
            IntervalPresentation.DESCENDING,
            reread.settings.contextualIntervalSetup.presentation
        )
        assertEquals(
            "the context round-trips with the setup",
            ContextKind.TONIC_DOMINANT_TONIC,
            reread.settings.contextualIntervalSetup.context
        )
        assertEquals(
            "the context gap round-trips with the setup",
            Duration.parse("750ms"),
            reread.settings.contextualIntervalSetup.contextGap
        )
        assertEquals(
            "the answer format round-trips with the setup",
            ContextualIntervalAnswerFormat.DEGREE_PAIR,
            reread.settings.contextualIntervalSetup.answerFormat
        )
        assertEquals(
            "the stop policy round-trips with the setup",
            SessionStopPolicy.CompletedItems(15),
            reread.settings.contextualIntervalSetup.stopPolicy
        )
        assertEquals(
            "the seed round-trips with the setup",
            7L,
            reread.settings.contextualIntervalSetup.seed
        )
        assertTrue(reread.diagnostics.isEmpty())
    }

    @Test
    fun anUnusableStoredContextualIntervalSetupFallsBackAndIsReported() = runTest {
        val store = ContextualIntervalStore(
            mutablePreferencesOf(
                intPreferencesKey("contextual_intervals_min_note") to 84,
                intPreferencesKey("contextual_intervals_max_note") to 48
            )
        )
        val snapshot = DataStoreSettingsRepository(store).settings.first()
        assertEquals(ContextualIntervalSetup.default(), snapshot.settings.contextualIntervalSetup)
        assertTrue(
            snapshot.diagnostics.any {
                it is SettingsDiagnostic.InvalidValue && it.field == "contextualIntervalSetup"
            }
        )
    }

    @Test
    fun aNewerContextualIntervalConfigVersionIsProtectedFromWrites() = runTest {
        val store = ContextualIntervalStore(
            mutablePreferencesOf(
                intPreferencesKey("contextual_intervals_config_version") to 99
            )
        )
        val subject = DataStoreSettingsRepository(store)
        val snapshot = subject.settings.first()
        assertEquals(ContextualIntervalSetup.default(), snapshot.settings.contextualIntervalSetup)
        assertTrue(
            snapshot.diagnostics.any {
                it is SettingsDiagnostic.NewerModeConfig &&
                    it.kind == TrainingKind.INTERVALS_IN_CONTEXT
            }
        )
        val revision = snapshot.settings.modeSetups
            .getValue(TrainingKind.INTERVALS_IN_CONTEXT).revision
        val result = subject.updateMode(
            ModeEdit(TrainingKind.INTERVALS_IN_CONTEXT, revision) {
                ModePayload.ContextualIntervals(customSetup())
            }
        )
        assertTrue(
            "a protected payload must refuse writes: $result",
            result is ModeWriteResult.Rejected
        )
    }
}
