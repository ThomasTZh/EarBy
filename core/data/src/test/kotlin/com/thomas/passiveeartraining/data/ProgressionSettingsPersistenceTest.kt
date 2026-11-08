package com.thomas.passiveeartraining.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.thomas.passiveeartraining.music.DiatonicChord
import com.thomas.passiveeartraining.music.ProgressionVoicingMode
import com.thomas.passiveeartraining.music.SpellingPreference as MusicSpellingPreference
import com.thomas.passiveeartraining.training.ProgressionAnswerFormat
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.ModeEdit
import com.thomas.passiveeartraining.training.settings.ModePayload
import com.thomas.passiveeartraining.training.settings.ModeWriteResult
import com.thomas.passiveeartraining.training.settings.ProgressionSetup
import com.thomas.passiveeartraining.training.settings.SettingsDiagnostic
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory DataStore for the chord-progression persistence tests. */
private class ProgressionStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
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

/** M9B: the chord-progression setup round-trips through the schema-2 preferences. */
class ProgressionSettingsPersistenceTest {

    private fun repository(store: DataStore<Preferences> = ProgressionStore()) =
        DataStoreSettingsRepository(store) to store

    private fun customSetup(): ProgressionSetup = ProgressionSetup.default().copy(
        chordIds = listOf(
            DiatonicChord.I.id,
            DiatonicChord.IV.id,
            DiatonicChord.V.id,
            DiatonicChord.V7.id
        ),
        voicing = ProgressionVoicingMode.NEAREST_INVERSION,
        allowedInversions = listOf(0, 1),
        answerFormat = ProgressionAnswerFormat.ROOT_MOTION,
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
    fun aFreshStoreCarriesTheDefaultProgressionSetup() = runTest {
        val (subject, _) = repository()
        val snapshot = subject.settings.first()
        assertEquals(ProgressionSetup.default(), snapshot.settings.progressionSetup)
        assertTrue(snapshot.settings.modeSetups.containsKey(TrainingKind.CHORD_PROGRESSIONS))
        assertTrue(
            snapshot.settings.modeSetups.getValue(TrainingKind.CHORD_PROGRESSIONS).payload
                is ModePayload.Progressions
        )
        assertEquals(emptyList<SettingsDiagnostic>(), snapshot.diagnostics)
    }

    @Test
    fun aProgressionSetupRoundTripsThroughTheRevisionCheckedWrite() = runTest {
        val (subject, store) = repository()
        val setup = customSetup()
        val revision = subject.settings.first().settings
            .modeSetups.getValue(TrainingKind.CHORD_PROGRESSIONS).revision
        val result = subject.updateMode(
            ModeEdit(TrainingKind.CHORD_PROGRESSIONS, revision) {
                ModePayload.Progressions(setup)
            }
        )
        assertTrue(
            "the progression write must be accepted: $result",
            result is ModeWriteResult.Saved
        )

        val reread = DataStoreSettingsRepository(store).settings.first()
        assertEquals(setup, reread.settings.progressionSetup)
        assertEquals(
            "the voicing round-trips with the setup",
            ProgressionVoicingMode.NEAREST_INVERSION,
            reread.settings.progressionSetup.voicing
        )
        assertEquals(
            "the allowed inversions round-trip with the setup",
            listOf(0, 1),
            reread.settings.progressionSetup.allowedInversions
        )
        assertEquals(
            "the answer format round-trips with the setup",
            ProgressionAnswerFormat.ROOT_MOTION,
            reread.settings.progressionSetup.answerFormat
        )
        assertEquals(
            "the stop policy round-trips with the setup",
            SessionStopPolicy.CompletedItems(15),
            reread.settings.progressionSetup.stopPolicy
        )
        assertEquals(
            "the seed round-trips with the setup",
            7L,
            reread.settings.progressionSetup.seed
        )
        assertEquals(
            "the chord pool round-trips with the setup",
            listOf(
                DiatonicChord.I.id,
                DiatonicChord.IV.id,
                DiatonicChord.V.id,
                DiatonicChord.V7.id
            ),
            reread.settings.progressionSetup.chordIds
        )
        assertTrue(reread.diagnostics.isEmpty())
    }

    @Test
    fun aFixedRootRoundTripsAndReplacesTheSelectedKey() = runTest {
        val (subject, store) = repository()
        val setup = customSetup().copy(fixedRoot = 60)
        val revision = subject.settings.first().settings
            .modeSetups.getValue(TrainingKind.CHORD_PROGRESSIONS).revision
        val result = subject.updateMode(
            ModeEdit(TrainingKind.CHORD_PROGRESSIONS, revision) {
                ModePayload.Progressions(setup)
            }
        )
        assertTrue(
            "the fixed-root write must be accepted: $result",
            result is ModeWriteResult.Saved
        )
        val reread = DataStoreSettingsRepository(store).settings.first()
        assertEquals(60, reread.settings.progressionSetup.fixedRoot)
        assertEquals(
            "the definition pins the fixed root's key",
            listOf(0),
            reread.settings.progressionSetup.toDefinition().effectiveKeyValues
        )
        assertTrue(reread.diagnostics.isEmpty())
    }

    @Test
    fun aStoredChordKeyWinsOverTheLegacyTemplateKey() = runTest {
        val store = ProgressionStore(
            mutablePreferencesOf(
                stringPreferencesKey("progressions_templates") to "major_i_iv_v_i",
                stringPreferencesKey("progressions_chords") to
                    "${DiatonicChord.I.id},${DiatonicChord.IV.id}"
            )
        )
        val snapshot = DataStoreSettingsRepository(store).settings.first()
        assertEquals(
            "the new chord key wins",
            listOf(DiatonicChord.I.id, DiatonicChord.IV.id),
            snapshot.settings.progressionSetup.chordIds
        )
        assertTrue(snapshot.diagnostics.isEmpty())
    }

    @Test
    fun aLegacyTemplateOnlyStoreReadsAsTheDefaultTriads() = runTest {
        val store = ProgressionStore(
            mutablePreferencesOf(
                stringPreferencesKey("progressions_templates") to "major_i_iv_v_i"
            )
        )
        val snapshot = DataStoreSettingsRepository(store).settings.first()
        assertEquals(
            DiatonicChord.TRIADS.map { it.id },
            snapshot.settings.progressionSetup.chordIds
        )
        assertTrue(snapshot.diagnostics.isEmpty())
    }

    @Test
    fun anUnusableStoredProgressionSetupFallsBackAndIsReported() = runTest {
        val store = ProgressionStore(
            mutablePreferencesOf(
                intPreferencesKey("progressions_min_note") to 84,
                intPreferencesKey("progressions_max_note") to 48
            )
        )
        val snapshot = DataStoreSettingsRepository(store).settings.first()
        assertEquals(ProgressionSetup.default(), snapshot.settings.progressionSetup)
        assertTrue(
            snapshot.diagnostics.any {
                it is SettingsDiagnostic.InvalidValue && it.field == "progressionSetup"
            }
        )
    }

    @Test
    fun aNewerProgressionConfigVersionIsProtectedFromWrites() = runTest {
        val store = ProgressionStore(
            mutablePreferencesOf(
                intPreferencesKey("progressions_config_version") to 99
            )
        )
        val subject = DataStoreSettingsRepository(store)
        val snapshot = subject.settings.first()
        assertEquals(ProgressionSetup.default(), snapshot.settings.progressionSetup)
        assertTrue(
            snapshot.diagnostics.any {
                it is SettingsDiagnostic.NewerModeConfig &&
                    it.kind == TrainingKind.CHORD_PROGRESSIONS
            }
        )
        val revision = snapshot.settings.modeSetups
            .getValue(TrainingKind.CHORD_PROGRESSIONS).revision
        val result = subject.updateMode(
            ModeEdit(TrainingKind.CHORD_PROGRESSIONS, revision) {
                ModePayload.Progressions(customSetup())
            }
        )
        assertTrue(
            "a protected payload must refuse writes: $result",
            result is ModeWriteResult.Rejected
        )
    }
}
