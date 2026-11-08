package com.thomas.passiveeartraining.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.music.SpellingPreference as MusicSpellingPreference
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.settings.AnswerVisibility
import com.thomas.passiveeartraining.training.settings.LastSetup
import com.thomas.passiveeartraining.training.settings.ModeEdit
import com.thomas.passiveeartraining.training.settings.ModeWriteResult
import com.thomas.passiveeartraining.training.settings.NoteSetup
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
 * Host coverage for the note-identification surfaces (M7.2/M7.3).
 *
 * These render the real screens with an in-memory repository, so the twelve canonical classes,
 * the accidental styles, the empty-selection explanation and the notes play summary are asserted
 * without a device or a screenshot baseline.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class NoteScreensHostTest {

    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        PassiveEarTrainingTheme(UserSettings.DEFAULTS) { content() }
    }

    @Test
    fun theNoteSetupScreenListsEveryPitchClassAndTheAccidentalStyles() {
        compose.setContent {
            theme {
                NoteSetupScreen(settingsRepository = NoteHostSettingsRepository())
            }
        }
        compose.onNodeWithText("Note setup").assertExists()
        compose.onNodeWithText("C").assertExists()
        compose.onNodeWithText("C\u266F").assertExists()
        compose.onNodeWithText("D\u266F").assertExists()
        compose.onNodeWithText("A\u266F").assertExists()
        compose.onNodeWithText("B").assertExists()
        compose.onNodeWithText("Sharp").assertExists()
        compose.onNodeWithText("Flat").assertExists()
        compose.onNodeWithText("Speak the octave").assertExists()
        compose.onNodeWithText("Lowest note").assertExists()
        compose.onNodeWithText("Highest note").assertExists()
        compose.onNodeWithText("Fix the octave").assertDoesNotExist()
    }

    @Test
    fun theNoteSetupScreenExplainsAnEmptyPitchClassSelection() {
        compose.setContent {
            theme {
                NoteSetupScreen(settingsRepository = NoteHostSettingsRepository())
            }
        }
        (0 until PitchClass.COUNT)
            .map { pitchClassLetterLabel(it) }
            .forEach { compose.onNodeWithText(it).performClick() }
        compose.onNodeWithText("Fix these to apply the changes").assertExists()
        compose.onNodeWithText("Note classes: Select at least one note class.").assertExists()
    }

    @Test
    fun theNotesPlaySummaryShowsTheStoredSetupAndHidesTheAnswer() {
        val setup = NoteSetup.default().copy(
            pitchClassValues = listOf(0, 1, 4, 7),
            accidental = MusicSpellingPreference.FLAT,
            speakOctave = true
        )
        compose.setContent {
            theme {
                PlayContent(
                    state = PlaybackUiState(
                        connected = true,
                        playing = true,
                        active = true,
                        answerText = "D flat 4"
                    ),
                    setup = LastSetup.default(),
                    noteSetup = setup,
                    mode = TrainingKind.NOTE_IDENTIFICATION,
                    musicGain = UserSettings.DEFAULT_MUSIC_GAIN,
                    voiceGain = UserSettings.DEFAULT_VOICE_GAIN,
                    answerVisibility = AnswerVisibility.HIDDEN,
                    onPlay = {},
                    onStop = {},
                    onSleepChoice = {}
                )
            }
        }
        compose.onNodeWithText("Notes: C, C\u266F, E, G").assertExists()
        compose.onNodeWithText("Range: C4 to C5 · thinking 2 s · repeats 0").assertExists()
        compose.onNodeWithText("Accidentals: flat · octave speech: on").assertExists()
        compose.onNodeWithText("Answer: D flat 4").assertDoesNotExist()
    }
}

/** In-memory settings for host rendering; the same revision contract as the real repository. */
private class NoteHostSettingsRepository : SettingsRepository {
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
