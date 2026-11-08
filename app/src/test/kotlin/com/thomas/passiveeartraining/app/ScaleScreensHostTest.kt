package com.thomas.passiveeartraining.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.thomas.passiveeartraining.music.ScaleDirection
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.settings.AnswerVisibility
import com.thomas.passiveeartraining.training.settings.LastSetup
import com.thomas.passiveeartraining.training.settings.ModeEdit
import com.thomas.passiveeartraining.training.settings.ModeWriteResult
import com.thomas.passiveeartraining.training.settings.SavedModeSetup
import com.thomas.passiveeartraining.training.settings.ScaleSetup
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
 * Host coverage for the scales surfaces (M6.2/M6.3).
 *
 * These render the real screens with an in-memory repository, so the checklist aliases, the
 * empty-selection explanation and the scales play summary are asserted without a device or a
 * screenshot baseline.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class ScaleScreensHostTest {

    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        PassiveEarTrainingTheme(UserSettings.DEFAULTS) { content() }
    }

    @Test
    fun theScalesSetupScreenListsEveryCanonicalFormulaWithItsAlias() {
        compose.setContent {
            theme {
                ScaleSetupScreen(settingsRepository = ScaleHostSettingsRepository())
            }
        }
        compose.onNodeWithText("Scale setup").assertExists()
        compose.onNodeWithText("major (Ionian)").assertExists()
        compose.onNodeWithText("natural minor (Aeolian)").assertExists()
        compose.onNodeWithText("jazz melodic minor").assertExists()
        compose.onNodeWithText("whole tone").assertExists()
        compose.onNodeWithText("Ascending").assertExists()
        compose.onNodeWithText("Descending").assertExists()
        compose.onNodeWithText("Both").assertExists()
        // No fragment toggle and no "coming soon" subcontrol remains in the completed screen.
        compose.onNodeWithText("fragments", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Soon", substring = true).assertDoesNotExist()
    }

    @Test
    fun theScalesSetupScreenExplainsAnEmptyScaleSelection() {
        compose.setContent {
            theme {
                ScaleSetupScreen(settingsRepository = ScaleHostSettingsRepository())
            }
        }
        compose.onNodeWithText("major (Ionian)").performClick()
        compose.onNodeWithText("natural minor (Aeolian)").performClick()
        compose.onNodeWithText("Fix these to apply the changes").assertExists()
        compose.onNodeWithText("Scales: Select at least one scale.").assertExists()
    }

    @Test
    fun theScalesPlaySummaryShowsTheStoredSetupAndHidesTheAnswer() {
        val setup = ScaleSetup.default().copy(
            scaleIds = listOf("major", "jazz_melodic_minor"),
            direction = ScaleDirection.BOTH,
            rootValues = listOf(0, 7),
            fixedRoot = 60
        )
        compose.setContent {
            theme {
                PlayContent(
                    state = PlaybackUiState(
                        connected = true,
                        playing = true,
                        active = true,
                        answerText = "jazz melodic minor"
                    ),
                    setup = LastSetup.default(),
                    scaleSetup = setup,
                    mode = TrainingKind.SCALES,
                    musicGain = UserSettings.DEFAULT_MUSIC_GAIN,
                    voiceGain = UserSettings.DEFAULT_VOICE_GAIN,
                    answerVisibility = AnswerVisibility.HIDDEN,
                    onPlay = {},
                    onStop = {},
                    onSleepChoice = {}
                )
            }
        }
        compose.onNodeWithText("Scales: major, jazz melodic minor").assertExists()
        compose.onNodeWithText("Direction: both").assertExists()
        compose.onNodeWithText("Roots: C, G").assertExists()
        compose.onNodeWithText("Range: C3 to C6 · thinking 2 s · repeats 0").assertExists()
        compose.onNodeWithText("Fixed root: C4").assertExists()
        compose.onNodeWithText("Answer: jazz melodic minor").assertDoesNotExist()
    }
}

/** In-memory settings for host rendering; the same revision contract as the real repository. */
private class ScaleHostSettingsRepository : SettingsRepository {
    private val state = MutableStateFlow(SettingsSnapshot(UserSettings.DEFAULTS))
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
