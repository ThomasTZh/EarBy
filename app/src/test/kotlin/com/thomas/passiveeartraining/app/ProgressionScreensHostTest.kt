package com.thomas.passiveeartraining.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.thomas.passiveeartraining.music.DiatonicChord
import com.thomas.passiveeartraining.music.ProgressionVoicingMode
import com.thomas.passiveeartraining.music.SpellingPreference as MusicSpellingPreference
import com.thomas.passiveeartraining.training.ProgressionAnswerFormat
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.settings.AnswerVisibility
import com.thomas.passiveeartraining.training.settings.LastSetup
import com.thomas.passiveeartraining.training.settings.ModeEdit
import com.thomas.passiveeartraining.training.settings.ModePayload
import com.thomas.passiveeartraining.training.settings.ModeWriteResult
import com.thomas.passiveeartraining.training.settings.ProgressionSetup
import com.thomas.passiveeartraining.training.settings.SavedModeSetup
import com.thomas.passiveeartraining.training.settings.SettingsRepository
import com.thomas.passiveeartraining.training.settings.SettingsSnapshot
import com.thomas.passiveeartraining.training.settings.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Host coverage for the chord-progression surfaces (M9B).
 *
 * These render the real screens with an in-memory repository, so the fourteen-chord pool, the
 * removed key selection, the fixed-root switch, the voicing controls, the answer formats with
 * their explanations, the range explanation, the now-playable FUNCTIONS format since the harmony
 * clips were bundled, and the progression play summary are asserted without a device or a
 * screenshot baseline.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class ProgressionScreensHostTest {

    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        PassiveEarTrainingTheme(UserSettings.DEFAULTS) { content() }
    }

    @Test
    fun theProgressionSetupScreenListsChordsRootVoicingAndAnswerFormats() {
        compose.setContent {
            theme {
                ProgressionSetupScreen(settingsRepository = ProgressionHostSettingsRepository())
            }
        }
        compose.onNodeWithText("Chord progression setup").assertExists()

        // The fourteen-chord pool: seven triads and their seven seventh chords.
        compose.onNodeWithText("Chords").assertExists()
        compose.onNodeWithText("Triads").assertExists()
        compose.onNodeWithText("Seventh chords").assertExists()
        listOf("I", "ii", "iii", "IV", "V", "vi", "vii\u00B0").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        listOf("Imaj7", "ii7", "iii7", "IVmaj7", "V7", "vi7", "vii\u00F87").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        compose.onNodeWithText(
            "Every exercise opens on the tonic I. The next three chords are drawn from these " +
                "in random order; a chord may repeat."
        ).assertExists()

        // The fixed-root switch is offered; the picker only appears when it is on.
        compose.onNodeWithText("Root note").assertExists()
        compose.onNodeWithText("Fix the root note").assertExists()
        compose.onNodeWithText(
            "All twelve keys are used unless a fixed root pins one."
        ).assertExists()

        // Root position is the default; the nearest search only offers the inversion chips.
        compose.onNodeWithText("Root position").assertExists()
        compose.onNodeWithText("Nearest inversion").performScrollTo().performClick()
        compose.onNodeWithText("Allowed inversions").performScrollTo().assertExists()
        compose.onNodeWithText("Triads only use 0 to 2", substring = true)
            .performScrollTo().assertExists()

        compose.onNodeWithText("Roman sequence").performScrollTo().assertExists()
        compose.onNodeWithText("Root motion").performScrollTo().assertExists()
        compose.onNodeWithText("Harmonic functions").performScrollTo().assertExists()
        compose.onNodeWithText(
            "Speaks each chord's degree number and quality in order, for example " +
                "\u201Cone major, six minor\u201D."
        ).performScrollTo().assertExists()

        compose.onNodeWithText("Lowest root note").performScrollTo().assertExists()
        compose.onNodeWithText("Highest root note").performScrollTo().assertExists()
        compose.onNodeWithText("Thinking time").performScrollTo().assertExists()
        compose.onNodeWithText("Advanced").performScrollTo().assertExists()
    }

    @Test
    fun theProgressionSetupScreenExplainsAnEmptyChordSelection() {
        compose.setContent {
            theme {
                ProgressionSetupScreen(settingsRepository = ProgressionHostSettingsRepository())
            }
        }
        listOf("I", "ii", "iii", "IV", "V", "vi", "vii\u00B0").forEach {
            compose.onNodeWithText(it).performClick()
        }
        compose.onNodeWithText("Fix these to apply the changes").assertExists()
        compose.onNodeWithText("Chords: Select at least one chord.").assertExists()
    }

    @Test
    fun theProgressionSetupScreenExplainsAnEmptyInversionSelection() {
        val setup = ProgressionSetup.default().copy(
            voicing = ProgressionVoicingMode.NEAREST_INVERSION,
            allowedInversions = emptyList()
        )
        compose.setContent {
            theme {
                ProgressionSetupScreen(
                    settingsRepository = ProgressionHostSettingsRepository(settingsWith(setup))
                )
            }
        }
        compose.onNodeWithText("Allowed inversions").assertExists()
        compose.onNodeWithText("Fix these to apply the changes").assertExists()
        compose.onNodeWithText("Inversions: Select at least one inversion.").assertExists()
    }

    @Test
    fun theProgressionSetupScreenExplainsASelectionNoKeyCanRealize() {
        // A triad cannot fit a three-semitone window, so no key can realize the opening I.
        val setup = ProgressionSetup.default().copy(
            chordIds = listOf(DiatonicChord.I.id, DiatonicChord.V.id),
            minimumNote = 105,
            maximumNote = 108
        )
        compose.setContent {
            theme {
                ProgressionSetupScreen(
                    settingsRepository = ProgressionHostSettingsRepository(settingsWith(setup))
                )
            }
        }
        compose.onNodeWithText("Fix these to apply the changes").assertExists()
        compose.onNodeWithText(
            "105..108",
            substring = true
        ).performScrollTo().assertExists()
    }

    @Test
    fun theProgressionFunctionsFormatIsPlayableSinceTheClipsAreBundled() {
        // The owner accepted the three harmony.* clips on 2026-10-14; they are in the bundled
        // pack, so the FUNCTIONS format validates and the missing-clip report is gone.
        val setup = ProgressionSetup.default().copy(
            answerFormat = ProgressionAnswerFormat.FUNCTIONS
        )
        compose.setContent {
            theme {
                ProgressionSetupScreen(
                    settingsRepository = ProgressionHostSettingsRepository(settingsWith(setup))
                )
            }
        }
        compose.onNodeWithText("Fix these to apply the changes").assertDoesNotExist()
        compose.onAllNodesWithText("harmony.", substring = true).assertCountEquals(0)
    }

    @Test
    fun theProgressionsPlaySummaryShowsTheStoredSetupAndHidesTheAnswer() {
        val setup = ProgressionSetup.default().copy(
            chordIds = listOf(
                DiatonicChord.I.id,
                DiatonicChord.II.id,
                DiatonicChord.V7.id
            ),
            voicing = ProgressionVoicingMode.NEAREST_INVERSION,
            allowedInversions = listOf(0, 1, 2),
            answerFormat = ProgressionAnswerFormat.ROOT_MOTION,
            spelling = MusicSpellingPreference.FLAT,
            minimumNote = 48,
            maximumNote = 84
        )
        compose.setContent {
            theme {
                PlayContent(
                    state = PlaybackUiState(
                        connected = true,
                        playing = true,
                        active = true,
                        answerText = "descending perfect fifth"
                    ),
                    setup = LastSetup.default(),
                    progressionSetup = setup,
                    mode = TrainingKind.CHORD_PROGRESSIONS,
                    musicGain = UserSettings.DEFAULT_MUSIC_GAIN,
                    voiceGain = UserSettings.DEFAULT_VOICE_GAIN,
                    answerVisibility = AnswerVisibility.HIDDEN,
                    onPlay = {},
                    onStop = {},
                    onSleepChoice = {}
                )
            }
        }
        compose.onNodeWithText("Keys: all twelve").assertExists()
        compose.onNodeWithText("Chords: I, ii, V7").assertExists()
        compose.onNodeWithText(
            "Voicing: nearest inversion, allowed inversions 0, 1, 2"
        ).assertExists()
        compose.onNodeWithText("Answer format: root motion").assertExists()
        compose.onNodeWithText("Range: C3 to C6 · thinking 2 s · repeats 0").assertExists()
        compose.onNodeWithText("Answer: descending perfect fifth").assertDoesNotExist()
    }

    @Test
    fun aFixedRootReplacesTheKeyLineOnThePlaySummary() {
        val setup = ProgressionSetup.default().copy(fixedRoot = 60)
        compose.setContent {
            theme {
                PlayContent(
                    state = PlaybackUiState(connected = true),
                    setup = LastSetup.default(),
                    progressionSetup = setup,
                    mode = TrainingKind.CHORD_PROGRESSIONS,
                    musicGain = UserSettings.DEFAULT_MUSIC_GAIN,
                    voiceGain = UserSettings.DEFAULT_VOICE_GAIN,
                    answerVisibility = AnswerVisibility.HIDDEN,
                    onPlay = {},
                    onStop = {},
                    onSleepChoice = {}
                )
            }
        }
        compose.onNodeWithText("Fixed root: C4").assertExists()
        compose.onAllNodesWithText("Keys: all twelve").assertCountEquals(0)
    }
}

/** The default settings with one progression setup substituted. */
private fun settingsWith(setup: ProgressionSetup): UserSettings = UserSettings.DEFAULTS.copy(
    modeSetups = UserSettings.DEFAULTS.modeSetups + (
        TrainingKind.CHORD_PROGRESSIONS to SavedModeSetup(
            1L,
            ModePayload.Progressions(setup)
        )
        )
)

/** In-memory settings for host rendering; the same revision contract as the real repository. */
private class ProgressionHostSettingsRepository(initial: UserSettings = UserSettings.DEFAULTS) :
    SettingsRepository {
    private val state = MutableStateFlow(SettingsSnapshot(initial))
    override val settings: Flow<SettingsSnapshot> = state

    override suspend fun update(change: (UserSettings) -> UserSettings): UserSettings {
        val updated = change(state.value.settings)
        state.value = SettingsSnapshot(updated)
        return updated
    }

    override suspend fun updateMode(
        edit: ModeEdit,
        globalChange: ((UserSettings) -> UserSettings)?
    ): ModeWriteResult {
        val current = state.value
        val existing = current.settings.modeSetups[edit.kind]
            ?: return ModeWriteResult.Rejected("no setup")
        if (existing.revision != edit.expectedRevision) {
            return ModeWriteResult.Conflict(current)
        }
        val base = globalChange?.invoke(current.settings) ?: current.settings
        val updated = base.copy(
            modeSetups = base.modeSetups + (
                edit.kind to SavedModeSetup(existing.revision + 1, edit.change(existing))
                )
        )
        state.value = SettingsSnapshot(updated)
        return ModeWriteResult.Saved(SettingsSnapshot(updated))
    }

    override suspend fun saveAndSelect(
        edits: List<ModeEdit>,
        selectedKind: TrainingKind,
        globalChange: ((UserSettings) -> UserSettings)?
    ): ModeWriteResult {
        var snapshot = state.value
        for (edit in edits) {
            val result = updateMode(edit, globalChange)
            if (result !is ModeWriteResult.Saved) return result
            snapshot = result.snapshot
        }
        val updated = snapshot.settings.copy(selectedKind = selectedKind)
        state.value = SettingsSnapshot(updated)
        return ModeWriteResult.Saved(SettingsSnapshot(updated))
    }

    override suspend fun selectKind(kind: TrainingKind): ModeWriteResult {
        val updated = state.value.settings.copy(selectedKind = kind)
        state.value = SettingsSnapshot(updated)
        return ModeWriteResult.Saved(SettingsSnapshot(updated))
    }

    override suspend fun resetToDefaults(): UserSettings {
        state.value = SettingsSnapshot(UserSettings.DEFAULTS)
        return UserSettings.DEFAULTS
    }
}
