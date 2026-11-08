package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.DiatonicChord
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.ProgressionVoicingMode
import com.thomas.passiveeartraining.music.SpellingPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M9B: the chord-pool definition, its answer formats, the fixed root and its validation.
 */
class ProgressionDefinitionTest {

    private fun definition(
        chords: List<DiatonicChord> = DiatonicChord.TRIADS,
        fixedRoot: MidiNote? = null,
        voicing: ProgressionVoicingMode = ProgressionVoicingMode.ROOT_POSITION,
        inversions: List<Int> = listOf(0, 1, 2, 3),
        answerFormat: ProgressionAnswerFormat = ProgressionAnswerFormat.ROMAN,
        minimumNote: MidiNote = MidiNote.of(48),
        maximumNote: MidiNote = MidiNote.of(84)
    ): ProgressionDefinition = ProgressionDefinition.of(
        chords = chords,
        fixedRoot = fixedRoot,
        voicing = voicing,
        allowedInversions = inversions,
        answerFormat = answerFormat,
        spelling = SpellingPreference.SHARP,
        minimumNote = minimumNote,
        maximumNote = maximumNote
    )

    private fun sequence(vararg chords: DiatonicChord) = chords.map { it.step }

    private fun tokens(format: ProgressionAnswerFormat, vararg chords: DiatonicChord) =
        ProgressionDefinition.answerTokens(format, sequence(*chords)).map { it.value }

    @Test
    fun theDefaultUsesTheSevenTriadsInEveryKey() {
        val definition = ProgressionDefinition.default()
        assertEquals(DiatonicChord.TRIADS, definition.chords)
        assertEquals(null, definition.fixedRoot)
        assertEquals(listOf(0, 1, 2, 3), definition.allowedInversions)
        assertEquals(ProgressionAnswerFormat.ROMAN, definition.answerFormat)
        assertEquals(
            "every key is used when no fixed root pins one",
            12,
            definition.effectiveKeyValues.size
        )
        assertEquals(
            "the opening I is always required",
            DiatonicChord.I,
            definition.requiredChords.first()
        )
        assertTrue(definition.feasiblePlacements().isNotEmpty())
    }

    @Test
    fun aPoolWithoutIStillOpensOnI() {
        val definition = definition(chords = listOf(DiatonicChord.II, DiatonicChord.V))
        assertTrue(
            "the opening I is not part of the selection but is always required",
            definition.requiredChords.contains(DiatonicChord.I)
        )
        assertFalse(definition.chords.contains(DiatonicChord.I))
        assertTrue(definition.feasiblePlacements().isNotEmpty())
    }

    @Test
    fun anEmptyPoolIsRejected() {
        val failure = assertThrows(InvalidProgressionDefinitionException::class.java) {
            definition(chords = emptyList())
        }
        assertEquals("chords", failure.field)
    }

    @Test
    fun unusableInversionsAreRejected() {
        assertEquals(
            "inversions",
            assertThrows(InvalidProgressionDefinitionException::class.java) {
                definition(inversions = emptyList())
            }.field
        )
        assertEquals(
            "inversions",
            assertThrows(InvalidProgressionDefinitionException::class.java) {
                definition(inversions = listOf(0, 4))
            }.field
        )
    }

    @Test
    fun anInvertedRangeIsRejected() {
        assertEquals(
            "range",
            assertThrows(InvalidProgressionDefinitionException::class.java) {
                definition(minimumNote = MidiNote.of(72), maximumNote = MidiNote.of(60))
            }.field
        )
    }

    @Test
    fun aFixedRootMustBeInsideTheRange() {
        assertEquals(
            "fixedRoot",
            assertThrows(InvalidProgressionDefinitionException::class.java) {
                definition(fixedRoot = MidiNote.of(90))
            }.field
        )
    }

    @Test
    fun aFixedRootPinsTheKeyAndAnchor() {
        val definition = definition(fixedRoot = MidiNote.of(60))
        assertEquals(
            "the fixed root's class decides the key",
            listOf(0),
            definition.effectiveKeyValues
        )
        assertEquals(
            listOf(ProgressionPlacement(0, MidiNote.of(60))),
            definition.feasiblePlacements()
        )
    }

    @Test
    fun aPoolThatNoPlacementRealizesIsRejected() {
        val failure = assertThrows(InvalidProgressionDefinitionException::class.java) {
            definition(
                chords = listOf(DiatonicChord.I7, DiatonicChord.VII7),
                minimumNote = MidiNote.of(105),
                maximumNote = MidiNote.of(108)
            )
        }
        assertEquals("range", failure.field)
    }

    @Test
    fun everyPlacementRealizesTheWholePool() {
        val definition = definition(
            chords = listOf(DiatonicChord.I, DiatonicChord.IV, DiatonicChord.V),
            minimumNote = MidiNote.of(60),
            maximumNote = MidiNote.of(74)
        )
        val placements = definition.feasiblePlacements()
        assertTrue(placements.isNotEmpty())
        placements.forEach { placement ->
            assertTrue(
                "placement $placement must realize the pool",
                definition.chordsFor(definition.steps, placement.keyValue, placement.anchor) != null
            )
        }
    }

    @Test
    fun romanAnswersNameEveryDegreeAndQuality() {
        assertEquals(
            listOf(
                "degree.one",
                "chord.major",
                "degree.two",
                "chord.minor",
                "degree.five",
                "chord.dominant_seventh",
                "degree.one",
                "chord.major"
            ),
            tokens(
                ProgressionAnswerFormat.ROMAN,
                DiatonicChord.I,
                DiatonicChord.II,
                DiatonicChord.V7,
                DiatonicChord.I
            )
        )
        assertEquals(
            "I ii V7 I",
            ProgressionDefinition.displayLabel(
                ProgressionAnswerFormat.ROMAN,
                sequence(DiatonicChord.I, DiatonicChord.II, DiatonicChord.V7, DiatonicChord.I)
            )
        )
    }

    @Test
    fun rootMotionAnswersCoverEveryAdjacentPair() {
        assertEquals(
            listOf(
                "connector.ascending",
                "interval.perfect_fourth",
                "connector.ascending",
                "interval.major_second",
                "connector.descending",
                "interval.perfect_fifth"
            ),
            tokens(
                ProgressionAnswerFormat.ROOT_MOTION,
                DiatonicChord.I,
                DiatonicChord.IV,
                DiatonicChord.V,
                DiatonicChord.I
            )
        )
        assertEquals(
            "ascending perfect fourth ascending major second descending perfect fifth",
            ProgressionDefinition.displayLabel(
                ProgressionAnswerFormat.ROOT_MOTION,
                sequence(DiatonicChord.I, DiatonicChord.IV, DiatonicChord.V, DiatonicChord.I)
            )
        )
    }

    @Test
    fun functionAnswersNameEveryChord() {
        assertEquals(
            listOf("harmony.tonic", "harmony.predominant", "harmony.dominant", "harmony.tonic"),
            tokens(
                ProgressionAnswerFormat.FUNCTIONS,
                DiatonicChord.I,
                DiatonicChord.IV,
                DiatonicChord.V,
                DiatonicChord.I
            )
        )
        assertEquals(
            "tonic predominant dominant tonic",
            ProgressionDefinition.displayLabel(
                ProgressionAnswerFormat.FUNCTIONS,
                sequence(DiatonicChord.I, DiatonicChord.IV, DiatonicChord.V, DiatonicChord.I)
            )
        )
    }

    @Test
    fun possibleTokensCoverTheSelectedPoolOnly() {
        val definition = definition()
        val roman = definition.possibleTokens(ProgressionAnswerFormat.ROMAN)
        assertTrue(roman.contains(VoiceAssetId("degree.seven")))
        assertTrue(roman.contains(VoiceAssetId("chord.diminished")))
        assertTrue(
            "a seventh clip is not required when no seventh is selected",
            !roman.contains(VoiceAssetId("chord.dominant_seventh"))
        )
        val seventhDefinition = definition(chords = listOf(DiatonicChord.I7, DiatonicChord.V7))
        assertTrue(
            seventhDefinition.possibleTokens(ProgressionAnswerFormat.ROMAN)
                .contains(VoiceAssetId("chord.dominant_seventh"))
        )
        val noTonic = definition(chords = listOf(DiatonicChord.II, DiatonicChord.V))
        assertTrue(
            "the fixed opening I contributes its tokens even when it is not selected",
            noTonic.possibleTokens(ProgressionAnswerFormat.ROMAN)
                .containsAll(
                    listOf(VoiceAssetId("degree.one"), VoiceAssetId("chord.major"))
                )
        )
    }

    @Test
    fun theExampleIsAlwaysFourChords() {
        val definition = definition()
        assertEquals(
            4 * 700 + 3 * 100,
            definition.maximumExampleDuration().inWholeMilliseconds
        )
    }

    @Test
    fun theAnswerTokenCountFollowsTheFormat() {
        assertEquals(8, ProgressionDefinition.answerTokenCount(ProgressionAnswerFormat.ROMAN))
        assertEquals(
            6,
            ProgressionDefinition.answerTokenCount(ProgressionAnswerFormat.ROOT_MOTION)
        )
        assertEquals(
            4,
            ProgressionDefinition.answerTokenCount(ProgressionAnswerFormat.FUNCTIONS)
        )
    }
}
