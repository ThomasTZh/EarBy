package com.thomas.passiveeartraining.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.DegreeAnswerFormat
import com.thomas.passiveeartraining.training.context.ContextKind
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.settings.AnswerVisibility
import com.thomas.passiveeartraining.training.settings.LastSetup
import com.thomas.passiveeartraining.training.settings.ModeEdit
import com.thomas.passiveeartraining.training.settings.ModePayload
import com.thomas.passiveeartraining.training.settings.ModeWriteResult
import com.thomas.passiveeartraining.training.settings.SavedModeSetup
import com.thomas.passiveeartraining.training.settings.ScaleDegreeSetup
import com.thomas.passiveeartraining.training.settings.SettingsRepository
import com.thomas.passiveeartraining.training.settings.SettingsSnapshot
import com.thomas.passiveeartraining.training.settings.UserSettings
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Host coverage for the scale-degree surfaces (M8.3).
 *
 * These render the real screens with an in-memory repository, so the key chips and their
 * "no selection = all twelve keys" convention, the five context choices, the answer formats, the
 * field-attached context/range explanation and the scale-degree play summary are asserted without
 * a device or a screenshot baseline.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class ScaleDegreeScreensHostTest {

    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        PassiveEarTrainingTheme(UserSettings.DEFAULTS) { content() }
    }

    @Test
    fun theDegreeSetupScreenListsKeysTonalitiesDegreesAndContexts() {
        compose.setContent {
            theme {
                ScaleDegreeSetupScreen(settingsRepository = DegreeHostSettingsRepository())
            }
        }
        compose.onNodeWithText("Scale degree setup").assertExists()

        // The twelve key tonics read with the sharp spelling the setup defaults to, and the empty
        // selection convention is stated in words.
        compose.onNodeWithText("C").assertExists()
        compose.onNodeWithText("C\u266F").assertExists()
        compose.onNodeWithText("D\u266F").assertExists()
        compose.onNodeWithText("A\u266F").assertExists()
        compose.onNodeWithText("B").assertExists()

        compose.onNodeWithText("Major").assertExists()
        compose.onNodeWithText("Natural minor").assertExists()
        compose.onNodeWithText("Target degrees").assertExists()
        (1..7).forEach { compose.onNodeWithText(it.toString()).assertExists() }

        compose.onNodeWithText("Tonic note").assertExists()
        compose.onNodeWithText("Tonic triad").assertExists()
        compose.onNodeWithText("Ascending octave scale").assertExists()
        compose.onNodeWithText("Tonic\u2013dominant\u2013tonic").assertExists()
        compose.onNodeWithText("Subdominant\u2013dominant\u2013tonic").assertExists()
        // The selected context explains itself, including the major V of the minor cadences.
        compose.onNodeWithText(
            "The root-position tonic triad (major or natural minor), then the target."
        ).assertExists()
        compose.onNodeWithText("Context gap").assertExists()

        compose.onNodeWithText("Degree").assertExists()
        compose.onNodeWithText("Note name").assertExists()
        compose.onNodeWithText("Lowest root note").assertExists()
        compose.onNodeWithText("Highest root note").assertExists()
    }

    @Test
    fun theDegreeSetupScreenExplainsAnEmptyDegreeSelectionAndTheEmptyKeyConvention() {
        compose.setContent {
            theme {
                ScaleDegreeSetupScreen(settingsRepository = DegreeHostSettingsRepository())
            }
        }
        (1..7).map { it.toString() }
            .forEach { compose.onNodeWithText(it).performClick() }
        compose.onNodeWithText("Fix these to apply the changes").assertExists()
        compose.onNodeWithText("Degrees: Select at least one target degree.").assertExists()

        // Deselecting the only stored key leaves the empty selection, which means all twelve keys.
        compose.onNodeWithText("C").performClick()
        compose.onNodeWithText("No key selected means all twelve keys are included.").assertExists()
    }

    @Test
    fun theDegreeSetupScreenExplainsAnImpossibleContextAndRange() {
        // A complete ascending octave cannot fit C4..E4; the stored setup is still a valid draft,
        // and the provider must attach the key/context/range explanation to the range field.
        val setup = ScaleDegreeSetup.default().copy(
            context = ContextKind.ASCENDING_SCALE,
            degrees = listOf(1),
            minimumNote = 105,
            maximumNote = 108
        )
        val settings = UserSettings.DEFAULTS.copy(
            modeSetups = UserSettings.DEFAULTS.modeSetups + (
                TrainingKind.SCALE_DEGREES to SavedModeSetup(1L, ModePayload.ScaleDegrees(setup))
                )
        )
        compose.setContent {
            theme {
                ScaleDegreeSetupScreen(
                    settingsRepository = DegreeHostSettingsRepository(settings)
                )
            }
        }
        compose.onNodeWithText("Fix these to apply the changes").assertExists()
        compose.onNodeWithText(
            "Range: No C major ascending scale context fits the selected range 105..108",
            substring = true
        ).assertExists()
    }

    @Test
    fun theDegreesPlaySummaryShowsTheStoredSetupAndHidesTheAnswer() {
        val setup = ScaleDegreeSetup.default().copy(
            keyValues = listOf(0, 7),
            tonalities = listOf(Tonality.MAJOR, Tonality.NATURAL_MINOR),
            degrees = listOf(1, 5, 7),
            context = ContextKind.TONIC_DOMINANT_TONIC,
            contextGap = Duration.parse("750ms"),
            answerFormat = DegreeAnswerFormat.NOTE_NAME
        )
        compose.setContent {
            theme {
                PlayContent(
                    state = PlaybackUiState(
                        connected = true,
                        playing = true,
                        active = true,
                        answerText = "F4"
                    ),
                    setup = LastSetup.default(),
                    degreeSetup = setup,
                    mode = TrainingKind.SCALE_DEGREES,
                    musicGain = UserSettings.DEFAULT_MUSIC_GAIN,
                    voiceGain = UserSettings.DEFAULT_VOICE_GAIN,
                    answerVisibility = AnswerVisibility.HIDDEN,
                    onPlay = {},
                    onStop = {},
                    onSleepChoice = {}
                )
            }
        }
        compose.onNodeWithText("Keys: C, G").assertExists()
        compose.onNodeWithText("Tonalities: major, natural minor").assertExists()
        compose.onNodeWithText("Degrees: 1, 5, 7").assertExists()
        compose.onNodeWithText("Context: tonic-dominant-tonic · gap 0.75 s").assertExists()
        compose.onNodeWithText("Answer format: note name").assertExists()
        compose.onNodeWithText("Range: C4 to C5 · thinking 2 s · repeats 0").assertExists()
        compose.onNodeWithText("Answer: F4").assertDoesNotExist()
    }
}

/** In-memory settings for host rendering; the same revision contract as the real repository. */
private class DegreeHostSettingsRepository(initial: UserSettings = UserSettings.DEFAULTS) :
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
