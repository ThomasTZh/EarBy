package com.thomas.passiveeartraining.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.settings.AnswerVisibility
import com.thomas.passiveeartraining.training.settings.ChordSetup
import com.thomas.passiveeartraining.training.settings.ContextualIntervalSetup
import com.thomas.passiveeartraining.training.settings.LastSetup
import com.thomas.passiveeartraining.training.settings.MelodicDictationSetup
import com.thomas.passiveeartraining.training.settings.NoteSetup
import com.thomas.passiveeartraining.training.settings.ProgressionSetup
import com.thomas.passiveeartraining.training.settings.ScaleDegreeSetup
import com.thomas.passiveeartraining.training.settings.ScaleSetup
import com.thomas.passiveeartraining.training.settings.UserSettings
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Host coverage for the hidden-answer contract of the Play surface (M11.2 Visibility row).
 *
 * Each of the eight kinds renders the real [PlayContent] with a hidden answer whose frame has
 * already been consumed and walks the whole Compose semantics tree: no visible text, content
 * description or state description anywhere may contain the answer string. With the stored
 * preference flipped to [AnswerVisibility.SHOWN_WHEN_CONSUMED] the same state must show the answer
 * exactly once, which proves the hidden case is the preference and not a missing value. The
 * per-mode setup summaries are asserted in both phases so the page is not merely blank, and no
 * screenshot is captured here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class HiddenAnswerSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun hiddenIntervalAnswersStayOutOfTheWholeSemanticsTreeUntilConsumed() {
        assertHiddenThenConsumed(
            kind = TrainingKind.INTERVALS,
            answer = "Major sixth ascending",
            summaryLines = listOf(
                "Intervals: Minor third, Major third, Perfect fifth",
                "Presentation: ascending · mode: recognition"
            )
        )
    }

    @Test
    fun hiddenChordAnswersStayOutOfTheWholeSemanticsTreeUntilConsumed() {
        assertHiddenThenConsumed(
            kind = TrainingKind.CHORDS,
            answer = "Major seventh second inversion",
            summaryLines = listOf(
                "Qualities: major, minor",
                "Inversions: root position"
            )
        )
    }

    @Test
    fun hiddenScaleAnswersStayOutOfTheWholeSemanticsTreeUntilConsumed() {
        assertHiddenThenConsumed(
            kind = TrainingKind.SCALES,
            answer = "D natural minor descending",
            summaryLines = listOf(
                "Scales: major, natural minor",
                "Direction: ascending"
            )
        )
    }

    @Test
    fun hiddenNoteAnswersStayOutOfTheWholeSemanticsTreeUntilConsumed() {
        assertHiddenThenConsumed(
            kind = TrainingKind.NOTE_IDENTIFICATION,
            answer = "E flat 4",
            summaryLines = listOf(
                "Notes: C, C\u266F, D, D\u266F, E, F, F\u266F, G, G\u266F, A, A\u266F, B",
                "Accidentals: sharp · octave speech: off"
            )
        )
    }

    @Test
    fun hiddenDegreeAnswersStayOutOfTheWholeSemanticsTreeUntilConsumed() {
        assertHiddenThenConsumed(
            kind = TrainingKind.SCALE_DEGREES,
            answer = "Degree 7 in G major",
            summaryLines = listOf(
                "Degrees: 1, 2, 3, 4, 5, 6, 7",
                "Context: tonic triad · gap 0.50 s"
            )
        )
    }

    @Test
    fun hiddenProgressionAnswersStayOutOfTheWholeSemanticsTreeUntilConsumed() {
        assertHiddenThenConsumed(
            kind = TrainingKind.CHORD_PROGRESSIONS,
            answer = "I\u2013vi\u2013IV\u2013V in C major",
            summaryLines = listOf(
                "Chords: I, ii, iii, IV, V, vi, vii\u00B0",
                "Voicing: root position"
            )
        )
    }

    @Test
    fun hiddenContextualIntervalAnswersStayOutOfTheWholeSemanticsTreeUntilConsumed() {
        assertHiddenThenConsumed(
            kind = TrainingKind.INTERVALS_IN_CONTEXT,
            answer = "Major third descending in C major",
            summaryLines = listOf(
                "Intervals: Minor third, Major third, Perfect fifth",
                "Presentation: ascending · answer format: interval"
            )
        )
    }

    @Test
    fun hiddenMelodicDictationAnswersStayOutOfTheWholeSemanticsTreeUntilConsumed() {
        assertHiddenThenConsumed(
            kind = TrainingKind.MELODIC_DICTATION,
            answer = "one five seven two",
            summaryLines = listOf(
                "Phrase: 4 notes · start degree 1 · end degree 1",
                "Steps: -5 to 5 · leap 5 · bias balanced"
            )
        )
    }

    /**
     * Renders one kind twice: first with [AnswerVisibility.HIDDEN] and a consumed answer, then with
     * the answer shown. [summaryLines] must render in both phases, and the answer string must never
     * appear anywhere in the first phase but exactly once in the second.
     */
    private fun assertHiddenThenConsumed(
        kind: TrainingKind,
        answer: String,
        summaryLines: List<String>
    ) {
        var visibility by mutableStateOf(AnswerVisibility.HIDDEN)
        compose.setContent {
            PassiveEarTrainingTheme(UserSettings.DEFAULTS) {
                ModePlaySurface(kind = kind, answer = answer, visibility = visibility)
            }
        }

        summaryLines.forEach { compose.onNodeWithText(it).assertExists() }
        // The live line and the transport's own description prove the surfaces under test are
        // rendered, so the absence walk below cannot pass vacuously.
        compose.onNodeWithText("Playing").assertExists()
        compose.onNodeWithContentDescription("Stop").assertExists()
        assertAnswerAbsentFromTheWholeSemanticsTree(answer)

        visibility = AnswerVisibility.SHOWN_WHEN_CONSUMED
        compose.waitForIdle()
        compose.onAllNodesWithText("Answer: $answer", substring = true).assertCountEquals(1)
        summaryLines.forEach { compose.onNodeWithText(it).assertExists() }
    }

    /**
     * The hidden answer must be absent from every Compose semantics surface.
     *
     * The visible-text walk, the explicit content/state-description walk and the count-zero matcher
     * cover the merged and unmerged trees, so a merged accessibility label cannot hide it either.
     */
    private fun assertAnswerAbsentFromTheWholeSemanticsTree(answer: String) {
        compose.onAllNodesWithText(answer, substring = true, useUnmergedTree = true)
            .assertCountEquals(0)

        val descriptionNodes = compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
            useUnmergedTree = true
        ).fetchSemanticsNodes()
        var sawTransportDescription = false
        descriptionNodes.forEach { node ->
            val descriptions =
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            sawTransportDescription = sawTransportDescription ||
                descriptions.any { it.contains("Stop") }
            assertTrue(
                "a content description must not contain the hidden answer: $descriptions",
                descriptions.none { it.contains(answer) }
            )
        }
        assertTrue(
            "the content-description walk must see the rendered transport description",
            sawTransportDescription
        )
        compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription),
            useUnmergedTree = true
        ).fetchSemanticsNodes().forEach { node ->
            val description = node.config.getOrNull(SemanticsProperties.StateDescription)
            assertTrue(
                "a state description must not contain the hidden answer: $description",
                description == null || !description.contains(answer)
            )
        }

        val leak = SemanticsMatcher("a semantics label contains the hidden answer") { node ->
            val labels = buildList {
                addAll(node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty())
                node.config.getOrNull(SemanticsProperties.StateDescription)?.let(::add)
            }
            labels.any { it.contains(answer) }
        }
        compose.onAllNodes(leak, useUnmergedTree = true).assertCountEquals(0)
    }
}

/**
 * The stateless Play tab for one kind, with every setup left at its stored default.
 *
 * The answer is present in the state exactly as the service publishes it after its frame was
 * consumed; only [visibility] decides whether the page renders it.
 */
@Composable
private fun ModePlaySurface(kind: TrainingKind, answer: String, visibility: AnswerVisibility) {
    PlayContent(
        state = PlaybackUiState(
            connected = true,
            playing = true,
            active = true,
            answerText = answer
        ),
        setup = LastSetup.default(),
        chordSetup = ChordSetup.default(),
        scaleSetup = ScaleSetup.default(),
        noteSetup = NoteSetup.default(),
        degreeSetup = ScaleDegreeSetup.default(),
        progressionSetup = ProgressionSetup.default(),
        contextualIntervalSetup = ContextualIntervalSetup.default(),
        melodicDictationSetup = MelodicDictationSetup.default(),
        mode = kind,
        musicGain = UserSettings.DEFAULT_MUSIC_GAIN,
        voiceGain = UserSettings.DEFAULT_VOICE_GAIN,
        answerVisibility = visibility,
        onPlay = {},
        onStop = {},
        onSleepChoice = {}
    )
}
