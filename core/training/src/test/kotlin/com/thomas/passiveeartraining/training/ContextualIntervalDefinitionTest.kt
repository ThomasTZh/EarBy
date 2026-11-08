package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.SimpleInterval
import com.thomas.passiveeartraining.music.SpellingPreference
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.context.ContextKind
import com.thomas.passiveeartraining.training.provider.GroupRole
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M9B.1/M9B-T01..T03: the contextual-interval definition's defaults, validation, scale/degree
 * helpers, finite pair enumeration and answer token sequences.
 *
 * Every musical expectation is stated independently of the enumeration code: C major's scale,
 * A natural minor's lowered seventh, the diatonic intervals of the C4..C5 range and the three
 * answer formats' exact token sequences.
 */
class ContextualIntervalDefinitionTest {

    private val minorThird = SimpleInterval.of(SimpleInterval.MINOR_THIRD)
    private val majorThird = SimpleInterval.of(SimpleInterval.MAJOR_THIRD)
    private val perfectFifth = SimpleInterval.of(SimpleInterval.PERFECT_FIFTH)
    private val octave = SimpleInterval.of(SimpleInterval.OCTAVE)
    private val unison = SimpleInterval.of(SimpleInterval.UNISON)

    private fun definition(
        keys: List<Int> = listOf(0),
        tonalities: List<Tonality> = listOf(Tonality.MAJOR),
        intervals: List<String> = listOf("minor_third", "major_third", "perfect_fifth"),
        presentation: IntervalPresentation = IntervalPresentation.ASCENDING,
        context: ContextKind = ContextKind.TONIC_TRIAD,
        gap: Duration = 500.milliseconds,
        answerFormat: ContextualIntervalAnswerFormat = ContextualIntervalAnswerFormat.INTERVAL,
        spelling: SpellingPreference = SpellingPreference.SHARP,
        minimum: Int = 60,
        maximum: Int = 72
    ): ContextualIntervalDefinition = ContextualIntervalDefinition.of(
        keyValues = keys,
        tonalities = tonalities,
        intervalIds = intervals,
        presentation = presentation,
        context = context,
        contextGap = gap,
        answerFormat = answerFormat,
        spelling = spelling,
        minimumNote = MidiNote.of(minimum),
        maximumNote = MidiNote.of(maximum)
    )

    private fun pairs(
        definition: ContextualIntervalDefinition,
        interval: SimpleInterval,
        presentation: IntervalPresentation = definition.presentation
    ): List<String> = definition.eligiblePairs(0, Tonality.MAJOR, interval, presentation)
        .map { pair ->
            "${pair.first.value}-${pair.second.value}:" +
                "${pair.firstDegree}/${pair.secondDegree}"
        }

    @Test
    fun theDefaultIsCmajorTonicTriadThirdsAndFifthAscendingWithIntervalAnswers() {
        val definition = ContextualIntervalDefinition.default()
        assertEquals(listOf(0), definition.keyValues)
        assertEquals(listOf(Tonality.MAJOR), definition.tonalities)
        assertEquals(
            listOf("minor_third", "major_third", "perfect_fifth"),
            definition.intervalIds
        )
        assertEquals(
            listOf(
                SimpleInterval.MINOR_THIRD,
                SimpleInterval.MAJOR_THIRD,
                SimpleInterval.PERFECT_FIFTH
            ),
            definition.intervals.map { it.semitones }
        )
        assertEquals(IntervalPresentation.ASCENDING, definition.presentation)
        assertEquals(ContextKind.TONIC_TRIAD, definition.context)
        assertEquals(500.milliseconds, definition.contextGap)
        assertEquals(ContextualIntervalAnswerFormat.INTERVAL, definition.answerFormat)
        assertEquals(SpellingPreference.SHARP, definition.spelling)
        assertEquals(60, definition.minimumNoteValue)
        assertEquals(72, definition.maximumNoteValue)
        assertEquals((60..72), definition.noteRange)
        assertEquals(listOf(0), definition.effectiveKeyValues)
        assertEquals(
            "C major 1..7",
            listOf(0, 2, 4, 5, 7, 9, 11),
            definition.scalePitchClasses(0, Tonality.MAJOR)
        )
        assertEquals("C major", definition.keyDisplayName(0, Tonality.MAJOR))
    }

    @Test
    fun normalizationDeduplicatesAndOrdersKeysTonalitiesAndIntervals() {
        val definition = definition(
            keys = listOf(7, 0, 7, 9),
            tonalities = listOf(Tonality.NATURAL_MINOR, Tonality.MAJOR, Tonality.MAJOR),
            intervals = listOf("perfect_fifth", "minor_third", "perfect_fifth", "octave")
        )
        assertEquals(listOf(0, 7, 9), definition.keyValues)
        assertEquals(listOf(Tonality.MAJOR, Tonality.NATURAL_MINOR), definition.tonalities)
        assertEquals(listOf("minor_third", "perfect_fifth", "octave"), definition.intervalIds)
        assertEquals(
            "an empty key selection means all twelve key tonics",
            (0..11).toList(),
            definition(keys = emptyList()).effectiveKeyValues
        )
    }

    @Test
    fun everyInvalidFieldIsRejectedWithItsOwnFieldHint() {
        val keys = assertThrows(InvalidContextualIntervalDefinitionException::class.java) {
            definition(keys = listOf(12))
        }
        assertEquals("keys", keys.field)

        val tonalities = assertThrows(InvalidContextualIntervalDefinitionException::class.java) {
            definition(tonalities = emptyList())
        }
        assertEquals("tonalities", tonalities.field)

        val emptyIntervals =
            assertThrows(InvalidContextualIntervalDefinitionException::class.java) {
                definition(intervals = emptyList())
            }
        assertEquals("intervals", emptyIntervals.field)

        val unknownInterval =
            assertThrows(InvalidContextualIntervalDefinitionException::class.java) {
                definition(intervals = listOf("quint"))
            }
        assertEquals("intervals", unknownInterval.field)
        assertTrue(unknownInterval.message!!, unknownInterval.message!!.contains("quint"))

        val range = assertThrows(InvalidContextualIntervalDefinitionException::class.java) {
            definition(minimum = 72, maximum = 60)
        }
        assertEquals("range", range.field)

        val negativeGap = assertThrows(InvalidContextualIntervalDefinitionException::class.java) {
            definition(gap = (-1).milliseconds)
        }
        assertEquals("contextGap", negativeGap.field)

        val longGap = assertThrows(InvalidContextualIntervalDefinitionException::class.java) {
            definition(gap = 30.001.seconds)
        }
        assertEquals("contextGap", longGap.field)
    }

    @Test
    fun theScaleAndDegreeHelpersFollowTheTonalityOffsets() {
        val definition = ContextualIntervalDefinition.default()
        assertEquals(
            "A natural minor keeps the lowered seventh",
            listOf(9, 11, 0, 2, 4, 5, 7),
            definition.scalePitchClasses(9, Tonality.NATURAL_MINOR)
        )
        assertEquals(1, definition.degreeOf(0, Tonality.MAJOR, 0))
        assertEquals(3, definition.degreeOf(0, Tonality.MAJOR, 4))
        assertEquals(7, definition.degreeOf(0, Tonality.MAJOR, 11))
        assertNull(
            "F sharp is not a C major scale degree",
            definition.degreeOf(0, Tonality.MAJOR, 6)
        )
        assertEquals(7, definition.degreeOf(9, Tonality.NATURAL_MINOR, 7))
        assertNull(
            "G sharp is never a natural-minor target degree",
            definition.degreeOfNote(9, Tonality.NATURAL_MINOR, MidiNote.of(80))
        )
    }

    @Test
    fun theTargetPoolIsTheScaleNotesAcrossThePlayableRange() {
        val definition = ContextualIntervalDefinition.default()
        val targets = definition.targetNotesFor(0, Tonality.MAJOR).map { it.value }
        assertTrue(targets.isNotEmpty())
        assertTrue(targets.all { it in 21..108 })
        assertTrue(targets.all { it.mod(12) in setOf(0, 2, 4, 5, 7, 9, 11) })

        val minor = definition(
            keys = listOf(9),
            tonalities = listOf(Tonality.NATURAL_MINOR),
            minimum = 69,
            maximum = 84
        )
        val minorTargets = minor.targetNotesFor(9, Tonality.NATURAL_MINOR).map { it.value }
        assertTrue(minorTargets.isNotEmpty())
        assertTrue(minorTargets.all { it in 21..108 })
        assertTrue(minorTargets.none { it.mod(12) == 8 })
    }

    @Test
    fun ascendingPairsAddTheDistanceAndKeepThePresentedOrder() {
        val definition = ContextualIntervalDefinition.default()
        val fifthPairs = definition.eligiblePairs(0, Tonality.MAJOR, perfectFifth)
        assertTrue(fifthPairs.isNotEmpty())
        assertTrue(fifthPairs.all { it.second.value - it.first.value == 7 })
        assertTrue(fifthPairs.all { it.first.value in 21..108 && it.second.value in 21..108 })
        assertTrue(fifthPairs.any { it.first.value == 60 && it.second.value == 67 })
    }

    @Test
    fun descendingPairsReverseThePresentedOrderButKeepTheDegrees() {
        val definition = definition(presentation = IntervalPresentation.DESCENDING)
        val fifthPairs = definition.eligiblePairs(0, Tonality.MAJOR, perfectFifth)
        assertTrue(fifthPairs.isNotEmpty())
        assertTrue(fifthPairs.all { it.first.value - it.second.value == 7 })
        assertTrue(fifthPairs.all { it.first.value in 21..108 && it.second.value in 21..108 })
    }

    @Test
    fun harmonicPairsStoreLowToHighForEveryPresentationDistance() {
        val harmonic = definition(presentation = IntervalPresentation.HARMONIC)
        assertEquals(
            pairs(definition(presentation = IntervalPresentation.ASCENDING), perfectFifth),
            pairs(harmonic, perfectFifth)
        )
        harmonic.eligiblePairs(0, Tonality.MAJOR, perfectFifth).forEach { pair ->
            assertTrue(
                "a harmonic pair sounds low then high",
                pair.first.value < pair.second.value
            )
        }
    }

    @Test
    fun unisonEnumeratesOneEqualPairPerTargetNoteInEveryPresentation() {
        for (presentation in IntervalPresentation.entries) {
            val definition = definition(presentation = presentation)
            val expectedCount = definition.targetNotesFor(0, Tonality.MAJOR).size
            val actual = definition.eligiblePairs(0, Tonality.MAJOR, unison)
            assertEquals(expectedCount, actual.size)
            assertTrue(actual.all { it.first == it.second })
        }
    }

    @Test
    fun octavePairsShareTheDegreeWithADifferentMidiValue() {
        val ascending = ContextualIntervalDefinition.default()
        val octavePairs = ascending.eligiblePairs(0, Tonality.MAJOR, octave)
        assertTrue(octavePairs.isNotEmpty())
        assertTrue(octavePairs.all { it.second.value - it.first.value == 12 })
        assertTrue(octavePairs.all { it.firstDegree == it.secondDegree })

        val descending = definition(presentation = IntervalPresentation.DESCENDING)
        val descendingPairs = descending.eligiblePairs(0, Tonality.MAJOR, octave)
        assertTrue(descendingPairs.isNotEmpty())
        assertTrue(descendingPairs.all { it.first.value - it.second.value == 12 })
    }

    @Test
    fun everyEnumeratedPairMatchesItsSelectedIntervalAndDirection() {
        for (presentation in IntervalPresentation.entries) {
            for (interval in listOf(unison, minorThird, majorThird, perfectFifth, octave)) {
                val definition = definition(presentation = presentation, maximum = 84)
                definition.eligiblePairs(0, Tonality.MAJOR, interval).forEach { pair ->
                    assertEquals(
                        "$presentation $interval must keep the selected semitone distance",
                        interval.semitones,
                        abs(pair.second.value - pair.first.value)
                    )
                    when (presentation) {
                        IntervalPresentation.ASCENDING -> assertTrue(
                            pair.first.value < pair.second.value ||
                                (interval == unison && pair.first == pair.second)
                        )

                        IntervalPresentation.DESCENDING -> assertTrue(
                            pair.first.value > pair.second.value ||
                                (interval == unison && pair.first == pair.second)
                        )

                        IntervalPresentation.HARMONIC -> assertFalse(
                            pair.first.value > pair.second.value
                        )
                    }
                }
            }
        }
    }

    @Test
    fun theThreeAnswerFormatsSpeakTheirExactTokenSequence() {
        val definition = ContextualIntervalDefinition.default()
        val intervalFormat = definition(
            answerFormat = ContextualIntervalAnswerFormat.INTERVAL
        )
        assertEquals(
            listOf("interval.perfect_fifth"),
            intervalFormat.answerTokensFor(perfectFifth, 1, 5).map { it.value }
        )
        assertEquals("Perfect fifth", intervalFormat.answerTextFor(perfectFifth, 1, 5))

        val degreePair = definition(answerFormat = ContextualIntervalAnswerFormat.DEGREE_PAIR)
        assertEquals(
            "presented order, not sorted degrees",
            listOf("degree.five", "degree.one"),
            degreePair.answerTokensFor(perfectFifth, 5, 1).map { it.value }
        )
        assertEquals("five one", degreePair.answerTextFor(perfectFifth, 5, 1))
        assertEquals(
            "a unison keeps both degrees",
            listOf("degree.two", "degree.two"),
            degreePair.answerTokensFor(unison, 2, 2).map { it.value }
        )

        val both = definition(
            answerFormat = ContextualIntervalAnswerFormat.INTERVAL_AND_DEGREES
        )
        assertEquals(
            listOf("interval.minor_third", "degree.six", "degree.one"),
            both.answerTokensFor(minorThird, 6, 1).map { it.value }
        )
        assertEquals("Minor third six one", both.answerTextFor(minorThird, 6, 1))
    }

    @Test
    fun eligibleConstructionsOnlyAdmitPlayableContexts() {
        val default = ContextualIntervalDefinition.default()
        val anchors = default.eligibleConstructions(0, Tonality.MAJOR).map { it.anchor.value }
        assertEquals(listOf(60, 72), anchors)
        default.eligibleConstructions(0, Tonality.MAJOR).forEach { construction ->
            assertTrue(
                "a construction carries only CONTEXT groups",
                construction.groups.all { it.role == GroupRole.CONTEXT }
            )
            assertTrue(construction.soundingValues.all { it in 21..108 })
        }

        val wide = definition(maximum = 79)
        assertTrue(wide.eligibleConstructions(0, Tonality.MAJOR).isNotEmpty())

        val tiny = definition(minimum = 60, maximum = 60)
        assertTrue(tiny.eligibleConstructions(0, Tonality.MAJOR).isNotEmpty())
        assertTrue(tiny.eligiblePairs(0, Tonality.MAJOR, minorThird).isNotEmpty())
        assertTrue(tiny.supports(0, Tonality.MAJOR, minorThird))
    }

    @Test
    fun spellingChangesTheWrittenKeyButNeverTheSoundingPairs() {
        val sharp = definition(spelling = SpellingPreference.SHARP)
        val flat = definition(spelling = SpellingPreference.FLAT)
        assertEquals(
            "pitch class 8 in the flat reading is A flat major",
            "A flat major",
            flat.keyDisplayName(8, Tonality.MAJOR)
        )
        assertEquals(
            "pitch class 8 in the sharp reading is G sharp major",
            "G sharp major",
            sharp.keyDisplayName(8, Tonality.MAJOR)
        )
        assertEquals(
            "spelling never changes which pairs exist",
            pairs(sharp, perfectFifth),
            pairs(flat, perfectFifth)
        )
    }
}
