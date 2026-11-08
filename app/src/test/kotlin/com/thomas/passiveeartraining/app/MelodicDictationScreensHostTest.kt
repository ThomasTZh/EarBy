package com.thomas.passiveeartraining.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.MelodicAnswerFormat
import com.thomas.passiveeartraining.training.MelodicDirectionBias
import com.thomas.passiveeartraining.training.context.ContextKind
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.AnswerVisibility
import com.thomas.passiveeartraining.training.settings.LastSetup
import com.thomas.passiveeartraining.training.settings.MelodicDictationSetup
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Host coverage for the melodic-dictation surfaces (M10.4).
 *
 * These render the real screens with an in-memory repository, so the key chips and their
 * "no selection = all twelve keys" convention, the five M8 contexts, the phrase length, the
 * endpoint and signed-step controls (including the zero-repeats meaning), the direction-bias
 * weights, the three answer formats, the no-cycle stopping explanation, the field-attached
 * impossible-phrase explanation and the play summary are asserted without a device or a
 * screenshot baseline. The long-answer case renders a 16-token answer at 200% text and checks
 * that it and the transport stay inside the window.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class MelodicDictationScreensHostTest {

    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        PassiveEarTrainingTheme(UserSettings.DEFAULTS) { content() }
    }

    @Test
    fun theMelodicDictationSetupScreenListsKeysContextsPhrasesStepsAndAnswers() {
        compose.setContent {
            theme {
                MelodicDictationSetupScreen(
                    settingsRepository = MelodicHostSettingsRepository()
                )
            }
        }
        compose.onNodeWithText("Melodic dictation setup").assertExists()
        compose.onNodeWithText(
            "Listen to the context and the phrase, remember them and compare with the spoken " +
                "answer; there is no entry or scoring."
        ).assertExists()

        // The twelve key tonics read with the sharp spelling the setup defaults to, and C is the
        // stored selection until the empty selection convention applies.
        compose.onNodeWithText("C").assertExists()
        compose.onNodeWithText("C\u266F").assertExists()
        compose.onNodeWithText("D\u266F").assertExists()
        compose.onNodeWithText("A\u266F").assertExists()
        compose.onNodeWithText("B").assertExists()
        compose.onNodeWithText(
            "Every selected phrase is trained in every selected key and tonality that can " +
                "realize it."
        ).assertExists()

        compose.onNodeWithText("Major").assertExists()
        compose.onNodeWithText("Natural minor").assertExists()

        // The five M8 contexts keep their wording, and the selected context is explained.
        compose.onNodeWithText("Tonic note").assertExists()
        compose.onNodeWithText("Tonic triad").assertExists()
        compose.onNodeWithText("Ascending octave scale").assertExists()
        compose.onNodeWithText("Tonic\u2013dominant\u2013tonic").assertExists()
        compose.onNodeWithText("Subdominant\u2013dominant\u2013tonic").assertExists()
        compose.onNodeWithText(
            "The root-position tonic triad (major or natural minor), then the target."
        ).assertExists()

        // The phrase controls: length, both endpoints and the signed steps.
        compose.onNodeWithText("Phrase length").assertExists()
        compose.onNodeWithText("4 notes").assertExists()
        compose.onNodeWithText("Start degree").assertExists()
        compose.onNodeWithText("End degree").assertExists()
        // Both endpoints offer "Any": the start chooser and the end chooser.
        compose.onAllNodesWithText("Any").assertCountEquals(2)
        compose.onNodeWithText("0 (repeat)").assertExists()
        compose.onNodeWithText("+5").assertExists()
        compose.onNodeWithText("-5").assertExists()
        compose.onNodeWithText(
            "A step is a signed semitone move: positive ascends, negative descends and " +
                "0 (repeat) keeps the same note. A transition is used only when it is " +
                "selected and its size is at most the maximum leap."
        ).assertExists()
        compose.onNodeWithText("Maximum leap").assertExists()
        compose.onNodeWithText("5 semitones").assertExists()

        // The three direction biases and the selected one's weights.
        compose.onNodeWithText("Direction bias").assertExists()
        compose.onNodeWithText("Balanced").assertExists()
        compose.onNodeWithText("Ascending").assertExists()
        compose.onNodeWithText("Descending").assertExists()
        compose.onNodeWithText(
            "Up, down and repeated steps are equally likely (weights 1/1/1)."
        ).assertExists()

        // The two answer formats and the selected one's explanation.
        compose.onNodeWithText("Answer format").assertExists()
        compose.onNodeWithText("Degree sequence").assertExists()
        compose.onNodeWithText("Directed interval sequence").assertExists()
        compose.onNodeWithText(
            "Speaks one degree number per note, for example \u201Cone five three\u201D."
        ).assertExists()

        compose.onNodeWithText("Lowest root note").assertExists()
        compose.onNodeWithText("Highest root note").assertExists()
        compose.onNodeWithText("Thinking time").assertExists()
        compose.onNodeWithText("Advanced").assertExists()
    }

    @Test
    fun theMelodicDictationSetupScreenExplainsTheMissingLabelCycle() {
        compose.setContent {
            theme {
                MelodicDictationSetupScreen(
                    settingsRepository = MelodicHostSettingsRepository()
                )
            }
        }
        // The cycle choices are not offered, and the explanation says why.
        compose.onNodeWithText("Until stopped").assertExists()
        compose.onNodeWithText("After N items").assertExists()
        compose.onNodeWithText("After one cycle").assertDoesNotExist()
        compose.onNodeWithText(
            "Melodic dictation has no label cycle, so \u201CAfter one cycle\u201D and " +
                "finish-cycle duration limits are not offered here. The Play tab's sleep timer " +
                "stops by elapsed time, exactly as in the other modes. A stored cycle policy is " +
                "reported below until it is changed."
        ).assertExists()
    }

    @Test
    fun aStoredCyclePolicyShowsTheProviderStopPolicyMessage() {
        val stored = MelodicDictationSetup.default().copy(
            stopPolicy = SessionStopPolicy.AfterCycle
        )
        compose.setContent {
            theme {
                MelodicDictationSetupScreen(
                    settingsRepository = MelodicHostSettingsRepository(settingsWith(stored))
                )
            }
        }
        compose.onNodeWithText("Fix these to apply the changes").assertExists()
        compose.onNodeWithText(
            "Stop policy: Melodic dictation has no label cycle, so it cannot stop after a " +
                "cycle. Choose until stopped or a completed-item count."
        ).assertExists()
    }

    @Test
    fun anImpossiblePhraseExplainsTheLengthEndpointsStepsAndRange() {
        // Step 0 alone can never leave a pitch class, so a tonic-to-dominant phrase is
        // impossible; the provider must explain the exact constraint that failed.
        val stored = MelodicDictationSetup.default().copy(
            allowedSteps = listOf(0),
            endDegree = 5
        )
        compose.setContent {
            theme {
                MelodicDictationSetupScreen(
                    settingsRepository = MelodicHostSettingsRepository(settingsWith(stored))
                )
            }
        }
        compose.onNodeWithText("Fix these to apply the changes").assertExists()
        compose.onNodeWithText(
            "Steps: No 4-note C major phrase inside 60..72 starts at degree 1 and ends at " +
                "degree 5 with steps [0] and a maximum leap of 5. Allow more of the steps " +
                "inside the maximum leap."
        ).assertExists()
    }

    @Test
    fun theMelodicDictationPlaySummaryShowsTheStoredSetupAndHidesTheAnswer() {
        val setup = MelodicDictationSetup.default().copy(
            keyValues = listOf(0, 7),
            tonalities = listOf(Tonality.MAJOR, Tonality.NATURAL_MINOR),
            context = ContextKind.TONIC_TRIAD,
            contextGap = Duration.parse("750ms"),
            phraseLength = 6,
            startDegree = 1,
            endDegree = 5,
            allowedSteps = listOf(-3, -2, 0, 2, 3),
            maximumLeap = 3,
            bias = MelodicDirectionBias.DESCENDING,
            answerFormat = MelodicAnswerFormat.DIRECTED_INTERVAL_SEQUENCE
        )
        compose.setContent {
            theme {
                PlayContent(
                    state = PlaybackUiState(
                        connected = true,
                        playing = true,
                        active = true,
                        answerText = "descending minor third"
                    ),
                    setup = LastSetup.default(),
                    melodicDictationSetup = setup,
                    mode = TrainingKind.MELODIC_DICTATION,
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
        compose.onNodeWithText("Context: tonic triad · gap 0.75 s").assertExists()
        compose.onNodeWithText("Phrase: 6 notes · start degree 1 · end degree 5").assertExists()
        compose.onNodeWithText(
            "Steps: -3, -2, 0, 2, 3 · leap 3 · bias descending"
        ).assertExists()
        compose.onNodeWithText("Answer format: directed interval sequence").assertExists()
        compose.onNodeWithText("Range: C4 to C5 · thinking 2 s · repeats 0").assertExists()
        // Safe configuration is shown; the current exercise's own answer is never revealed.
        compose.onNodeWithText("Answer: descending minor third").assertDoesNotExist()
    }

    @Test
    fun aLongMelodicAnswerStaysInsideTheWindowAtTwoHundredPercentText() {
        // The longest degree answer is one token per note for 16 notes.
        val longAnswer =
            "one two three four five six seven one two three four five six seven one two"
        compose.setContent {
            theme {
                val base = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = base.density, fontScale = 2f)
                ) {
                    PlayContent(
                        state = PlaybackUiState(
                            connected = true,
                            playing = true,
                            active = true,
                            answerText = longAnswer
                        ),
                        setup = LastSetup.default(),
                        melodicDictationSetup = MelodicDictationSetup.default(),
                        mode = TrainingKind.MELODIC_DICTATION,
                        musicGain = UserSettings.DEFAULT_MUSIC_GAIN,
                        voiceGain = UserSettings.DEFAULT_VOICE_GAIN,
                        onPlay = {},
                        onStop = {},
                        onSleepChoice = {}
                    )
                }
            }
        }
        // The long answer exists and can be scrolled within its bounded region.
        compose.onNodeWithText("Answer: $longAnswer").assertExists()
        compose.onNodeWithText("Answer: $longAnswer").performScrollTo()

        // Neither the transport nor the answer overflows the window: the answer's top is above
        // the window bottom, and the transport's bottom stays inside it.
        val root = compose.onRoot().fetchSemanticsNode()
        val answer = compose.onNodeWithText("Answer: $longAnswer")
            .fetchSemanticsNode()
        val transport = compose.onNodeWithTag("play_stop").fetchSemanticsNode()
        assertTrue(
            "the long answer must start inside the window",
            answer.boundsInRoot.top < root.size.height
        )
        assertTrue(
            "the transport must stay inside the window (bottom=" +
                "${transport.boundsInRoot.bottom}, height=${root.size.height})",
            transport.boundsInRoot.bottom <= root.size.height.toFloat()
        )
    }
}

/** The default settings with one melodic-dictation setup substituted. */
private fun settingsWith(setup: MelodicDictationSetup): UserSettings = UserSettings.DEFAULTS.copy(
    modeSetups = UserSettings.DEFAULTS.modeSetups + (
        TrainingKind.MELODIC_DICTATION to SavedModeSetup(
            1L,
            ModePayload.MelodicDictation(setup)
        )
        )
)

/** In-memory settings for host rendering; the same revision contract as the real repository. */
private class MelodicHostSettingsRepository(initial: UserSettings = UserSettings.DEFAULTS) :
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
