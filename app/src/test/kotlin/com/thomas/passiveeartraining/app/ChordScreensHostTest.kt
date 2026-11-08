package com.thomas.passiveeartraining.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.thomas.passiveeartraining.music.ChordPresentation
import com.thomas.passiveeartraining.training.ChordAnswerMode
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.settings.AnswerVisibility
import com.thomas.passiveeartraining.training.settings.ChordSetup
import com.thomas.passiveeartraining.training.settings.LastSetup
import com.thomas.passiveeartraining.training.settings.ModeEdit
import com.thomas.passiveeartraining.training.settings.ModePayload
import com.thomas.passiveeartraining.training.settings.ModeWriteResult
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
 * Host coverage for the chords surfaces (M5.3).
 *
 * These render the real screens with an in-memory repository, so the disabled inversion state, its
 * explanation and the chords play summary are asserted without a device or a screenshot baseline.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class ChordScreensHostTest {

    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        PassiveEarTrainingTheme(UserSettings.DEFAULTS) { content() }
    }

    @Test
    fun theChordsSetupScreenDisablesThirdInversionForTriads() {
        compose.setContent {
            theme {
                ChordSetupScreen(settingsRepository = ChordHostSettingsRepository())
            }
        }
        compose.onNodeWithText("Chord setup").assertExists()
        compose.onNodeWithText("major").assertExists()
        compose.onNodeWithText("major seventh").assertExists()
        compose.onNodeWithText("root position").assertExists()
        compose.onNodeWithText("third inversion").assertIsNotEnabled()
        compose.onNodeWithText(
            "Third inversion is unavailable: no selected quality is a seventh chord."
        ).assertExists()
    }

    @Test
    fun theChordsSetupScreenExplainsAnEmptyQualitySelection() {
        compose.setContent {
            theme {
                ChordSetupScreen(settingsRepository = ChordHostSettingsRepository())
            }
        }
        compose.onNodeWithText("major").performClick()
        compose.onNodeWithText("minor").performClick()
        compose.onNodeWithText("Fix these to apply the changes").assertExists()
        compose.onNodeWithText("Qualities: Select at least one chord quality.").assertExists()
    }

    @Test
    fun theChordsPlaySummaryShowsTheStoredSetupAndHidesTheAnswer() {
        val setup = ChordSetup.default().copy(
            qualityIds = listOf("major_seventh", "minor_seventh"),
            inversions = listOf(0, 1, 2, 3),
            rootValues = listOf(0, 7),
            fixedRoot = 60,
            presentation = ChordPresentation.ARPEGGIO,
            answerMode = ChordAnswerMode.QUALITY_AND_INVERSION
        )
        compose.setContent {
            theme {
                PlayContent(
                    state = PlaybackUiState(
                        connected = true,
                        playing = true,
                        active = true,
                        answerText = "major seventh root position"
                    ),
                    setup = LastSetup.default(),
                    chordSetup = setup,
                    mode = TrainingKind.CHORDS,
                    musicGain = UserSettings.DEFAULT_MUSIC_GAIN,
                    voiceGain = UserSettings.DEFAULT_VOICE_GAIN,
                    answerVisibility = AnswerVisibility.HIDDEN,
                    onPlay = {},
                    onStop = {},
                    onSleepChoice = {}
                )
            }
        }
        compose.onNodeWithText("Qualities: major seventh, minor seventh").assertExists()
        compose.onNodeWithText(
            "Inversions: root position, first inversion, second inversion, third inversion"
        ).assertExists()
        compose.onNodeWithText("Answer format: quality + inversion").assertExists()
        compose.onNodeWithText(
            "Presentation: ascending arpeggio · mode: recognition"
        ).assertExists()
        compose.onNodeWithText("Roots: C, G").assertExists()
        compose.onNodeWithText("Range: C3 to C6 · thinking 2 s · repeats 0").assertExists()
        compose.onNodeWithText("Fixed root: C4").assertExists()
        compose.onNodeWithText("Answer: major seventh root position").assertDoesNotExist()
    }
}

/** In-memory settings for host rendering; the same revision contract as the real repository. */
private class ChordHostSettingsRepository : SettingsRepository {
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
