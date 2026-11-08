package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.SimpleInterval
import com.thomas.passiveeartraining.music.SpelledPitch
import com.thomas.passiveeartraining.music.SpellingPreference
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.context.ContextKind
import com.thomas.passiveeartraining.training.provider.MelodyNote
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M10.1/M10-T01..T02: the melodic-dictation definition's defaults, validation, scale-node
 * enumeration, graph edges, interval helper and answer token sequences.
 *
 * Every musical expectation is stated independently of the enumeration code: C major's scale,
 * A natural minor's lowered seventh, F# major's theoretical E sharp, the signed step rule and the
 * answer formats' exact token sequences.
 */
class MelodicDictationDefinitionTest {

    private fun definition(
        keys: List<Int> = listOf(0),
        tonalities: List<Tonality> = listOf(Tonality.MAJOR),
        context: ContextKind = ContextKind.TONIC_TRIAD,
        gap: Duration = 500.milliseconds,
        minimum: Int = 60,
        maximum: Int = 72,
        length: Int = 4,
        start: Int? = 1,
        end: Int? = 1,
        steps: List<Int> = (-5..5).toList(),
        leap: Int = 5,
        bias: MelodicDirectionBias = MelodicDirectionBias.BALANCED,
        answerFormat: MelodicAnswerFormat = MelodicAnswerFormat.DEGREE_SEQUENCE,
        spelling: SpellingPreference = SpellingPreference.SHARP
    ): MelodicDictationDefinition = MelodicDictationDefinition.of(
        keyValues = keys,
        tonalities = tonalities,
        context = context,
        contextGap = gap,
        minimumNote = MidiNote.of(minimum),
        maximumNote = MidiNote.of(maximum),
        phraseLength = length,
        startDegree = start,
        endDegree = end,
        allowedSteps = steps,
        maximumLeap = leap,
        bias = bias,
        answerFormat = answerFormat,
        spelling = spelling
    )

    @Test
    fun theDefaultIsCmajorTonicToTonicBalanceWithDegreeAnswers() {
        val default = MelodicDictationDefinition.default()
        assertEquals(listOf(0), default.keyValues)
        assertEquals(listOf(Tonality.MAJOR), default.tonalities)
        assertEquals(ContextKind.TONIC_TRIAD, default.context)
        assertEquals(500.milliseconds, default.contextGap)
        assertEquals(60, default.minimumNoteValue)
        assertEquals(72, default.maximumNoteValue)
        assertEquals(60..72, default.noteRange)
        assertEquals(4, default.phraseLength)
        assertEquals(1, default.startDegree)
        assertEquals(1, default.endDegree)
        assertEquals((-5..5).toList(), default.allowedSteps)
        assertEquals(5, default.maximumLeap)
        assertEquals(MelodicDirectionBias.BALANCED, default.bias)
        assertEquals(MelodicAnswerFormat.DEGREE_SEQUENCE, default.answerFormat)
        assertEquals(SpellingPreference.SHARP, default.spelling)
        assertEquals(listOf(0), default.effectiveKeyValues)
        assertEquals(
            "C major 1..7",
            listOf(0, 2, 4, 5, 7, 9, 11),
            default.scalePitchClasses(0, Tonality.MAJOR)
        )
        assertEquals(
            "A natural minor keeps the lowered seventh",
            listOf(9, 11, 0, 2, 4, 5, 7),
            default.scalePitchClasses(9, Tonality.NATURAL_MINOR)
        )
        assertEquals("C major", default.keyDisplayName(0, Tonality.MAJOR))
        assertTrue(
            "the default graph has notes to draw from",
            default.graphFor(0, Tonality.MAJOR).isFeasible
        )
    }

    @Test
    fun normalizationDeduplicatesAndOrdersKeysTonalitiesAndSteps() {
        val normalized = definition(
            keys = listOf(7, 0, 7, 9),
            tonalities = listOf(Tonality.NATURAL_MINOR, Tonality.MAJOR, Tonality.MAJOR),
            steps = listOf(5, -2, 0, -2)
        )
        assertEquals(listOf(0, 7, 9), normalized.keyValues)
        assertEquals(listOf(Tonality.MAJOR, Tonality.NATURAL_MINOR), normalized.tonalities)
        assertEquals(listOf(-2, 0, 5), normalized.allowedSteps)
        assertEquals(
            "an empty key selection means all twelve key tonics",
            (0..11).toList(),
            definition(keys = emptyList()).effectiveKeyValues
        )
    }

    @Test
    fun everyInvalidFieldIsRejectedWithItsOwnFieldHint() {
        val keys = assertThrows(InvalidMelodicDictationDefinitionException::class.java) {
            definition(keys = listOf(12))
        }
        assertEquals("keys", keys.field)

        val tonalities = assertThrows(InvalidMelodicDictationDefinitionException::class.java) {
            definition(tonalities = emptyList())
        }
        assertEquals("tonalities", tonalities.field)

        val short = assertThrows(InvalidMelodicDictationDefinitionException::class.java) {
            definition(length = 1)
        }
        assertEquals("length", short.field)

        val long = assertThrows(InvalidMelodicDictationDefinitionException::class.java) {
            definition(length = 17)
        }
        assertEquals("length", long.field)

        val start = assertThrows(InvalidMelodicDictationDefinitionException::class.java) {
            definition(start = 0)
        }
        assertEquals("endpoints", start.field)

        val end = assertThrows(InvalidMelodicDictationDefinitionException::class.java) {
            definition(end = 8)
        }
        assertEquals("endpoints", end.field)

        val emptySteps = assertThrows(InvalidMelodicDictationDefinitionException::class.java) {
            definition(steps = emptyList())
        }
        assertEquals("steps", emptySteps.field)

        val wideStep = assertThrows(InvalidMelodicDictationDefinitionException::class.java) {
            definition(steps = listOf(13))
        }
        assertEquals("steps", wideStep.field)
        assertTrue(wideStep.message!!, wideStep.message!!.contains("13"))

        val negativeLeap = assertThrows(InvalidMelodicDictationDefinitionException::class.java) {
            definition(leap = -1)
        }
        assertEquals("leap", negativeLeap.field)

        val wideLeap = assertThrows(InvalidMelodicDictationDefinitionException::class.java) {
            definition(leap = 13)
        }
        assertEquals("leap", wideLeap.field)

        val negativeGap = assertThrows(InvalidMelodicDictationDefinitionException::class.java) {
            definition(gap = (-1).milliseconds)
        }
        assertEquals("contextGap", negativeGap.field)

        val longGap = assertThrows(InvalidMelodicDictationDefinitionException::class.java) {
            definition(gap = 30.001.seconds)
        }
        assertEquals("contextGap", longGap.field)

        val range = assertThrows(InvalidMelodicDictationDefinitionException::class.java) {
            definition(minimum = 72, maximum = 60)
        }
        assertEquals("range", range.field)
    }

    @Test
    fun theScaleNodeEnumerationCoversThePlayableRange() {
        val default = MelodicDictationDefinition.default()
        val nodes = default.scaleNodes(0, Tonality.MAJOR)
        assertTrue(nodes.all { it.value in 21..108 })
        assertTrue(nodes.any { it.value == 60 && it.degree == 1 })
        assertTrue(nodes.any { it.value == 72 && it.degree == 1 })
        assertEquals(7, nodes.map { it.degree }.distinct().size)

        val minor = definition(
            keys = listOf(9),
            tonalities = listOf(Tonality.NATURAL_MINOR),
            minimum = 57,
            maximum = 69
        )
        val minorNodes = minor.scaleNodes(9, Tonality.NATURAL_MINOR)
        assertTrue(minorNodes.any { it.value == 57 && it.degree == 1 })
        assertTrue(minorNodes.none { it.value.mod(12) == 8 })
        assertTrue(minorNodes.all { it.value in 21..108 })
        assertTrue(
            "a natural-minor node is never the raised leading tone",
            definition(
                keys = listOf(9),
                tonalities = listOf(Tonality.NATURAL_MINOR),
                context = ContextKind.TONIC_DOMINANT_TONIC,
                minimum = 57,
                maximum = 69
            ).scaleNodes(9, Tonality.NATURAL_MINOR).none { it.value == 68 }
        )
        assertNull(
            "G sharp is never a natural-minor degree",
            definition(keys = listOf(9)).degreeOf(9, Tonality.NATURAL_MINOR, 8)
        )
        assertEquals(7, default.degreeOf(0, Tonality.MAJOR, 11))
        assertNull("F sharp is not a C major degree", default.degreeOf(0, Tonality.MAJOR, 6))
    }

    @Test
    fun aSharpKeySpellsItsTheoreticalDegreesIncludingDoubleSharpLetters() {
        val fSharp = definition(keys = listOf(6), minimum = 60, maximum = 72)
        val nodes = fSharp.scaleNodes(6, Tonality.MAJOR)
        val expected = mapOf(61 to 5, 63 to 6, 65 to 7, 66 to 1, 68 to 2, 70 to 3, 71 to 4)
        expected.forEach { (value, degree) ->
            val node = nodes.single { it.value == value }
            assertEquals(degree, node.degree)
        }
        val eSharp = nodes.single { it.value == 65 }
        assertEquals(com.thomas.passiveeartraining.music.Letter.E, eSharp.spelled.letter)
        assertEquals(1, eSharp.spelled.accidental)
        assertNotEquals(
            "the chromatic F natural is not the F# major seventh",
            SpelledPitch.of(com.thomas.passiveeartraining.music.Letter.F, 0, 4),
            eSharp.spelled
        )
    }

    @Test
    fun graphEdgesFollowTheSignedStepsTheLeapAndTheScale() {
        val default = MelodicDictationDefinition.default()
        val graph = default.graphFor(0, Tonality.MAJOR)
        assertTrue(
            "C4 continues by the scale steps inside the leap",
            graph.outgoingNodes(nodeAt(graph, 60)).map { it.value }
                .containsAll(listOf(60, 62, 64, 65))
        )
        assertTrue(
            "C5 continues by the scale steps inside the leap",
            graph.outgoingNodes(nodeAt(graph, 72)).map { it.value }
                .containsAll(listOf(67, 69, 71, 72))
        )
        graph.nodes.forEach { node ->
            graph.outgoingNodes(node).forEach { target ->
                val step = target.value - node.value
                assertTrue(
                    "every edge is a signed effective step: $node -> $target",
                    step in graph.transitions
                )
                assertTrue(
                    "every edge respects the maximum leap",
                    kotlin.math.abs(step) <= default.maximumLeap
                )
                assertTrue(
                    "every edge target is a scale node",
                    default.degreeOfNote(0, Tonality.MAJOR, MidiNote.of(target.value)) != null
                )
            }
        }
    }

    @Test
    fun theMaximumLeapCutsStepsExactlyAtItsBoundary() {
        val leapFive = definition(steps = (-7..7).toList(), leap = 5)
        val leapSix = definition(steps = (-7..7).toList(), leap = 6)
        assertEquals(
            "the effective steps are the allowed steps inside the leap",
            (-5..5).toList(),
            leapFive.effectiveSteps
        )
        val graphFive = leapFive.graphFor(0, Tonality.MAJOR)
        val graphSix = leapSix.graphFor(0, Tonality.MAJOR)
        val f4Five = nodeAt(graphFive, 65)
        val f4Six = nodeAt(graphSix, 65)
        assertFalse(
            "F4 to B4 is six semitones and stays out at a leap of five",
            graphFive.outgoingNodes(f4Five).any { it.value == 71 }
        )
        assertTrue(
            "F4 to B4 is an edge at a leap of exactly six",
            graphSix.outgoingNodes(f4Six).any { it.value == 71 }
        )
        assertTrue(
            "F4 to A4 is five semitones and is an edge at a leap of five",
            graphFive.outgoingNodes(f4Five).any { it.value == 69 }
        )
    }

    @Test
    fun zeroEdgesExistOnlyWhenZeroIsExplicitlyAllowed() {
        val withZero = definition()
        val withoutZero = definition(length = 2, steps = (-5..5).filter { it != 0 })
        assertTrue("a selected zero repeats a note", 0 in withZero.effectiveSteps)
        assertTrue(
            "C4 repeats itself only when zero is selected",
            withZero.graphFor(0, Tonality.MAJOR)
                .outgoingNodes(nodeAt(withZero.graphFor(0, Tonality.MAJOR), 60))
                .any { it.value == 60 }
        )
        assertFalse("zero is not part of this transition set", 0 in withoutZero.effectiveSteps)
        assertFalse(
            "no node repeats itself when zero is disabled",
            withoutZero.graphFor(0, Tonality.MAJOR).nodes.any { node ->
                withoutZero.graphFor(0, Tonality.MAJOR)
                    .outgoingNodes(node)
                    .any { it.value == node.value }
            }
        )
        assertNull(
            "a degree-1 to degree-1 phrase of two notes needs zero or an octave",
            withoutZero.graphFor(0, Tonality.MAJOR).eligibleStarts.firstOrNull()
        )
        assertTrue(
            "the graph really has no eligible start without zero or an octave",
            withoutZero.graphFor(0, Tonality.MAJOR).eligibleStarts.isEmpty()
        )
    }

    @Test
    fun aSignedStepIsTheTargetMinusTheSource() {
        val graph = MelodicDictationDefinition.default().graphFor(0, Tonality.MAJOR)
        assertEquals(5, nodeAt(graph, 60).stepTo(nodeAt(graph, 65)))
        assertEquals(-5, nodeAt(graph, 65).stepTo(nodeAt(graph, 60)))
        assertEquals(0, nodeAt(graph, 64).stepTo(nodeAt(graph, 64)))
    }

    @Test
    fun theIntervalPhraseMapsZeroToUnisonAndTwelveToOctave() {
        assertEquals(
            SimpleInterval.UNISON,
            MelodicDictationDefinition.intervalForStep(0).semitones
        )
        assertEquals(
            SimpleInterval.OCTAVE,
            MelodicDictationDefinition.intervalForStep(12).semitones
        )
        assertEquals(
            SimpleInterval.OCTAVE,
            MelodicDictationDefinition.intervalForStep(-12).semitones
        )
        assertEquals(
            SimpleInterval.PERFECT_FOURTH,
            MelodicDictationDefinition.intervalForStep(5).semitones
        )
        assertEquals(
            SimpleInterval.PERFECT_FOURTH,
            MelodicDictationDefinition.intervalForStep(-5).semitones
        )
    }

    @Test
    fun theAnswerFormatsSpeakTheirExactTokenSequence() {
        val notes = listOf(
            MelodyNote(midi = 60, degree = 1),
            MelodyNote(midi = 64, degree = 3),
            MelodyNote(midi = 67, degree = 5)
        )
        val steps = listOf(4, 3)
        val degreeFormat = definition(answerFormat = MelodicAnswerFormat.DEGREE_SEQUENCE)
        assertEquals(
            listOf("degree.one", "degree.three", "degree.five"),
            degreeFormat.answerTokensFor(notes, steps).map { it.value }
        )
        assertEquals(
            "one three five",
            degreeFormat.answerTextFor(notes, steps)
        )

        val directed = definition(
            answerFormat = MelodicAnswerFormat.DIRECTED_INTERVAL_SEQUENCE
        )
        assertEquals(
            "a zero step speaks the unison phrase alone, without a false direction",
            listOf(
                "connector.ascending",
                "interval.major_third",
                "interval.unison",
                "connector.descending",
                "interval.perfect_fifth"
            ),
            directed.answerTokensFor(notes, listOf(4, 0, -7)).map { it.value }
        )
        assertEquals(
            "ascending Major third Unison descending Perfect fifth",
            directed.answerTextFor(notes, listOf(4, 0, -7))
        )
        assertEquals(
            "an upward octave names its direction and the octave phrase",
            listOf("connector.ascending", "interval.octave"),
            MelodicDictationDefinition.intervalTokensForStep(12).map { it.value }
        )
        assertEquals(
            "a downward octave names its direction and the octave phrase",
            listOf("connector.descending", "interval.octave"),
            MelodicDictationDefinition.intervalTokensForStep(-12).map { it.value }
        )
        assertEquals(
            "zero names the unison connector only for the direction helper",
            "connector.unison",
            MelodicDictationDefinition.directionConnectorEntry(0).id.value
        )
    }

    @Test
    fun theBiasWeightsMapUpDownAndRepeatTransitions() {
        assertEquals(1, MelodicDirectionBias.BALANCED.upWeight)
        assertEquals(1, MelodicDirectionBias.BALANCED.downWeight)
        assertEquals(1, MelodicDirectionBias.BALANCED.repeatWeight)
        assertEquals(3, MelodicDirectionBias.ASCENDING.upWeight)
        assertEquals(1, MelodicDirectionBias.ASCENDING.downWeight)
        assertEquals(1, MelodicDirectionBias.ASCENDING.repeatWeight)
        assertEquals(1, MelodicDirectionBias.DESCENDING.upWeight)
        assertEquals(3, MelodicDirectionBias.DESCENDING.downWeight)
        assertEquals(1, MelodicDirectionBias.DESCENDING.repeatWeight)
        listOf(
            MelodicDirectionBias.BALANCED,
            MelodicDirectionBias.ASCENDING,
            MelodicDirectionBias.DESCENDING
        ).forEach { bias ->
            assertEquals(bias.upWeight, bias.weightOf(5))
            assertEquals(bias.downWeight, bias.weightOf(-5))
            assertEquals(bias.repeatWeight, bias.weightOf(0))
        }
    }

    @Test
    fun spellingChangesTheWrittenKeyButNeverTheSoundingPhrase() {
        val sharp = definition(keys = listOf(8), spelling = SpellingPreference.SHARP)
        val flat = definition(keys = listOf(8), spelling = SpellingPreference.FLAT)
        assertEquals("G sharp major", sharp.keyDisplayName(8, Tonality.MAJOR))
        assertEquals("A flat major", flat.keyDisplayName(8, Tonality.MAJOR))
        assertEquals(
            "spelling never changes which phrase notes exist",
            sharp.graphFor(8, Tonality.MAJOR).nodes.map { it.value },
            flat.graphFor(8, Tonality.MAJOR).nodes.map { it.value }
        )
    }

    @Test
    fun nullEndpointsReadAsAnyDegreeAndTonicIsOne() {
        val anyEndpoints = definition(start = null, end = null, minimum = 60, maximum = 64)
        assertNull(anyEndpoints.startDegree)
        assertNull(anyEndpoints.endDegree)
        val default = MelodicDictationDefinition.default()
        assertEquals(1, default.startDegree)
        assertEquals(1, default.endDegree)
    }

    private fun nodeAt(graph: MelodyGraph, value: Int): MelodyNode =
        graph.nodes.single { it.value == value }
}
