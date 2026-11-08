package com.thomas.passiveeartraining.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.ContextualIntervalAnswerFormat
import com.thomas.passiveeartraining.training.context.ContextKind
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.settings.AnswerVisibility
import com.thomas.passiveeartraining.training.settings.ContextualIntervalSetup
import com.thomas.passiveeartraining.training.settings.LastSetup
import com.thomas.passiveeartraining.training.settings.ModeEdit
import com.thomas.passiveeartraining.training.settings.ModePayload
import com.thomas.passiveeartraining.training.settings.ModeWriteResult
import com.thomas.passiveeartraining.training.settings.SavedModeSetup
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
 * Host coverage for the intervals-in-context surfaces (M9B.2).
 *
 * These render the real screens with an in-memory repository, so the key chips and their
 * "no selection = all twelve keys" convention, the five M8 contexts, the thirteen-interval
 * checklist, the three presentations with their direction explanations, the answer formats,
 * the field-attached interval/presentation/range explanation and the play summary are asserted
 * without a device or a screenshot baseline.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class ContextualIntervalScreensHostTest {

    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        PassiveEarTrainingTheme(UserSettings.DEFAULTS) { content() }
    }

    @Test
    fun theContextualIntervalSetupScreenListsKeysTonalitiesContextsAndIntervals() {
        compose.setContent {
            theme {
                ContextualIntervalSetupScreen(
                    settingsRepository = ContextualHostSettingsRepository()
                )
            }
        }
        compose.onNodeWithText("Intervals in context setup").assertExists()

        // The twelve key tonics read with the sharp spelling the setup defaults to, and C is the
        // stored selection until the empty selection convention applies.
        compose.onNodeWithText("C").assertExists()
        compose.onNodeWithText("C\u266F").assertExists()
        compose.onNodeWithText("D\u266F").assertExists()
        compose.onNodeWithText("A\u266F").assertExists()
        compose.onNodeWithText("B").assertExists()
        compose.onNodeWithText(
            "Each key tonic is trained in every selected tonality that can realize it."
        ).assertExists()

        compose.onNodeWithText("Major").assertExists()
        compose.onNodeWithText("Natural minor").assertExists()

        // The five M8 contexts keep their scale-degree wording.
        compose.onNodeWithText("Tonic note").assertExists()
        compose.onNodeWithText("Tonic triad").assertExists()
        compose.onNodeWithText("Ascending octave scale").assertExists()
        compose.onNodeWithText("Tonic\u2013dominant\u2013tonic").assertExists()
        compose.onNodeWithText("Subdominant\u2013dominant\u2013tonic").assertExists()
        compose.onNodeWithText(
            "The root-position tonic triad (major or natural minor), then the target."
        ).assertExists()
        compose.onNodeWithText("Context gap").assertExists()

        // The full thirteen-interval checklist uses the existing names.
        compose.onNodeWithText("Unison").assertExists()
        compose.onNodeWithText("Minor second").assertExists()
        compose.onNodeWithText("Major second").assertExists()
        compose.onNodeWithText("Minor third").assertExists()
        compose.onNodeWithText("Major third").assertExists()
        compose.onNodeWithText("Perfect fourth").assertExists()
        compose.onNodeWithText("Tritone").assertExists()
        compose.onNodeWithText("Perfect fifth").assertExists()
        compose.onNodeWithText("Minor sixth").assertExists()
        compose.onNodeWithText("Major sixth").assertExists()
        compose.onNodeWithText("Minor seventh").assertExists()
        compose.onNodeWithText("Major seventh").assertExists()
        compose.onNodeWithText("Octave").assertExists()

        // Each presentation explains its direction; harmonic also explains its ordering.
        compose.onNodeWithText("Ascending").assertExists()
        compose.onNodeWithText("Descending").assertExists()
        compose.onNodeWithText("Harmonic").assertExists()
        compose.onNodeWithText(
            "The two target notes sound in order, lower first, then higher; a unison is the " +
                "same note twice."
        ).assertExists()

        // The three answer formats and the selected one's explanation.
        compose.onNodeWithText("Interval").assertExists()
        compose.onNodeWithText("Degree pair").assertExists()
        compose.onNodeWithText("Interval and degrees").assertExists()
        compose.onNodeWithText(
            "Speaks the interval phrase alone, for example \u201Cminor third\u201D."
        ).assertExists()

        compose.onNodeWithText("Lowest root note").assertExists()
        compose.onNodeWithText("Highest root note").assertExists()
        compose.onNodeWithText("Thinking time").assertExists()
        compose.onNodeWithText("Advanced").assertExists()
    }

    @Test
    fun theContextualIntervalSetupScreenExplainsAnEmptyIntervalSelectionAndTheEmptyKeyConvention() {
        compose.setContent {
            theme {
                ContextualIntervalSetupScreen(
                    settingsRepository = ContextualHostSettingsRepository()
                )
            }
        }
        // Deselecting the three initially selected intervals empties the selection.
        listOf("Minor third", "Major third", "Perfect fifth").forEach {
            compose.onNodeWithText(it).performScrollTo().performClick()
        }
        compose.onNodeWithText("Fix these to apply the changes").assertExists()
        compose.onNodeWithText("Intervals: Select at least one interval.").assertExists()

        // Deselecting the only stored key leaves the empty selection, which means all twelve keys.
        compose.onNodeWithText("C").performScrollTo().performClick()
        compose.onNodeWithText(
            "No key selected means all twelve keys are included."
        ).assertExists()

        // Deselecting every tonality is reported against the tonality field.
        compose.onNodeWithText("Major").performScrollTo().performClick()
        compose.onNodeWithText("Tonalities: Select at least one tonality.").assertExists()
    }

    @Test
    fun theContextualIntervalSetupScreenExplainsAnImpossiblePresentationAndRange() {
        // A descending octave cannot fit C4..E4: every candidate pair needs a note an octave
        // below its first note, which the range excludes; the provider must explain exactly the
        // key, presentation, interval and range that failed.
        val setup = ContextualIntervalSetup.default().copy(
            intervalIds = listOf("octave"),
            presentation = IntervalPresentation.DESCENDING,
            minimumNote = 105,
            maximumNote = 108
        )
        compose.setContent {
            theme {
                ContextualIntervalSetupScreen(
                    settingsRepository = ContextualHostSettingsRepository(settingsWith(setup))
                )
            }
        }
        compose.onNodeWithText("Fix these to apply the changes").assertExists()
        compose.onNodeWithText("105..108", substring = true).assertExists()
    }

    @Test
    fun theContextualIntervalsPlaySummaryShowsTheStoredSetupAndHidesTheAnswer() {
        val setup = ContextualIntervalSetup.default().copy(
            keyValues = listOf(0, 7),
            tonalities = listOf(Tonality.MAJOR, Tonality.NATURAL_MINOR),
            intervalIds = listOf("minor_third", "perfect_fifth"),
            presentation = IntervalPresentation.HARMONIC,
            context = ContextKind.TONIC_DOMINANT_TONIC,
            contextGap = Duration.parse("750ms"),
            answerFormat = ContextualIntervalAnswerFormat.DEGREE_PAIR
        )
        compose.setContent {
            theme {
                PlayContent(
                    state = PlaybackUiState(
                        connected = true,
                        playing = true,
                        active = true,
                        answerText = "one five"
                    ),
                    setup = LastSetup.default(),
                    contextualIntervalSetup = setup,
                    mode = TrainingKind.INTERVALS_IN_CONTEXT,
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
        compose.onNodeWithText("Intervals: Minor third, Perfect fifth").assertExists()
        compose.onNodeWithText("Context: tonic-dominant-tonic · gap 0.75 s").assertExists()
        compose.onNodeWithText(
            "Presentation: harmonic · answer format: degree pair"
        ).assertExists()
        compose.onNodeWithText("Range: C4 to C5 · thinking 2 s · repeats 0").assertExists()
        // Safe configuration is shown; the current exercise's own answer is never revealed.
        compose.onNodeWithText("Answer: one five").assertDoesNotExist()
    }
}

/** The default settings with one contextual-interval setup substituted. */
private fun settingsWith(setup: ContextualIntervalSetup): UserSettings = UserSettings.DEFAULTS.copy(
    modeSetups = UserSettings.DEFAULTS.modeSetups + (
        TrainingKind.INTERVALS_IN_CONTEXT to SavedModeSetup(
            1L,
            ModePayload.ContextualIntervals(setup)
        )
        )
)

/** In-memory settings for host rendering; the same revision contract as the real repository. */
private class ContextualHostSettingsRepository(initial: UserSettings = UserSettings.DEFAULTS) :
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
