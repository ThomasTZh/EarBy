package com.thomas.passiveeartraining.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M9B: finite voicing for triads and seventh chords, including a mixed pool.
 */
class ProgressionVoicingTest {

    private val cMajor = Key(SpelledPitch.of(Letter.C, 0, 4), Tonality.MAJOR)
    private val wide = 48..84

    private fun steps(vararg chords: DiatonicChord): List<ProgressionStep> = chords.map { it.step }

    @Test
    fun rootPositionPlacesEveryChordAtItsDiatonicRoot() {
        val chords = ProgressionVoicing.rootPosition(
            steps(DiatonicChord.I, DiatonicChord.IV, DiatonicChord.V, DiatonicChord.I),
            cMajor,
            MidiNote.of(60),
            wide
        )
        requireNotNull(chords)
        assertEquals(listOf(60, 64, 67), chords[0].notes.map { it.value })
        assertEquals(listOf(65, 69, 72), chords[1].notes.map { it.value })
        assertEquals(listOf(67, 71, 74), chords[2].notes.map { it.value })
        assertEquals(listOf(60, 64, 67), chords[3].notes.map { it.value })
        assertEquals(
            "the theoretical roots stay diatonic",
            listOf(60, 65, 67, 60),
            chords.map { it.theoreticalRoot.midiValue }
        )
    }

    @Test
    fun rootPositionSeventhChordsSoundFourNotes() {
        val chords = ProgressionVoicing.rootPosition(
            steps(DiatonicChord.I7, DiatonicChord.V7),
            cMajor,
            MidiNote.of(60),
            wide
        )
        requireNotNull(chords)
        assertEquals(listOf(60, 64, 67, 71), chords[0].notes.map { it.value })
        assertEquals(listOf(67, 71, 74, 77), chords[1].notes.map { it.value })
        assertTrue(chords.all { it.notes.size == 4 })
    }

    @Test
    fun rootPositionFailsWhenAChordLeavesTheRange() {
        assertNull(
            "the seventh chord cannot fit a two-note window",
            ProgressionVoicing.rootPosition(
                steps(DiatonicChord.I7),
                cMajor,
                MidiNote.of(60),
                60..62
            )
        )
    }

    @Test
    fun nearestInversionFitsTheRangeAndKeepsTheRoots() {
        val chords = ProgressionVoicing.realize(
            steps(DiatonicChord.I, DiatonicChord.IV, DiatonicChord.V, DiatonicChord.I),
            cMajor,
            MidiNote.of(60),
            ProgressionVoicingMode.NEAREST_INVERSION,
            listOf(0, 1, 2),
            wide
        )
        requireNotNull(chords)
        assertEquals(4, chords.size)
        assertEquals("the first chord is anchored in root position", 0, chords[0].inversion)
        assertEquals(
            "the theoretical roots keep their diatonic classes",
            listOf(0, 5, 7, 0),
            chords.map { it.theoreticalRoot.midiValue.mod(12) }
        )
        assertTrue(chords.all { chord -> chord.notes.all { it.value in wide } })
    }

    @Test
    fun nearestInversionHandlesMixedTriadsAndSevenths() {
        val chords = ProgressionVoicing.realize(
            steps(DiatonicChord.I, DiatonicChord.V7, DiatonicChord.VI, DiatonicChord.IV7),
            cMajor,
            MidiNote.of(60),
            ProgressionVoicingMode.NEAREST_INVERSION,
            listOf(0, 1, 2, 3),
            wide
        )
        requireNotNull(chords)
        assertEquals(listOf(3, 4, 3, 4), chords.map { it.notes.size })
        assertTrue(chords.all { chord -> chord.notes.all { it.value in wide } })
    }

    @Test
    fun thirdInversionAppliesOnlyToSevenths() {
        assertTrue(
            "a triad has no third inversion",
            ProgressionVoicing.candidatesFor(
                DiatonicChord.I.step,
                cMajor,
                listOf(3),
                wide
            ).isEmpty()
        )
        val sevenths = ProgressionVoicing.candidatesFor(
            DiatonicChord.I7.step,
            cMajor,
            listOf(3),
            wide
        )
        assertTrue(sevenths.isNotEmpty())
        assertTrue(sevenths.all { it.inversion == 3 })
    }

    @Test
    fun theNearestSequenceMovesNoMoreThanRootPosition() {
        val sequence = steps(DiatonicChord.I, DiatonicChord.IV, DiatonicChord.V, DiatonicChord.I)
        val root = ProgressionVoicing.rootPosition(sequence, cMajor, MidiNote.of(60), wide)
            ?: error("root position must fit")
        val nearest = ProgressionVoicing.realize(
            sequence,
            cMajor,
            MidiNote.of(60),
            ProgressionVoicingMode.NEAREST_INVERSION,
            listOf(0, 1, 2),
            wide
        ) ?: error("the nearest search must fit")

        fun movement(chords: List<ChordVoicing>): Int = chords.zipWithNext { a, b ->
            a.notes.indices.sumOf { index ->
                kotlin.math.abs(a.notes[index].value - b.notes[index].value)
            }
        }.sum()

        assertTrue(
            "the search never exceeds the root-position total",
            movement(nearest) <= movement(root)
        )
    }
}
