package com.thomas.passiveeartraining.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M9B: the fourteen-chord diatonic pool, its Roman labels, functions and root motions.
 */
class ProgressionTest {

    @Test
    fun thePoolHasSevenTriadsAndSevenSevenths() {
        assertEquals("fourteen diatonic chords", 14, DiatonicChord.entries.size)
        assertEquals(7, DiatonicChord.TRIADS.size)
        assertEquals(7, DiatonicChord.SEVENTHS.size)
        assertTrue(
            "every degree appears once as a triad",
            DiatonicChord.TRIADS.map { it.degree }
                .toSet() == (1..7).toSet()
        )
        assertTrue(
            "every degree appears once as a seventh",
            DiatonicChord.SEVENTHS.map { it.degree }
                .toSet() == (1..7).toSet()
        )
        assertEquals(
            "the seventh qualities are the diatonic ones",
            listOf(
                ChordQuality.MAJOR_SEVENTH,
                ChordQuality.MINOR_SEVENTH,
                ChordQuality.MINOR_SEVENTH,
                ChordQuality.MAJOR_SEVENTH,
                ChordQuality.DOMINANT_SEVENTH,
                ChordQuality.MINOR_SEVENTH,
                ChordQuality.HALF_DIMINISHED
            ),
            DiatonicChord.SEVENTHS.map { it.quality }
        )
        assertEquals(
            "every id is unique",
            DiatonicChord.entries.size,
            DiatonicChord.entries.map { it.id }.toSet().size
        )
    }

    @Test
    fun romanNumeralsNameTriadsAndSevenths() {
        assertEquals("I", DiatonicChord.I.romanLabel)
        assertEquals("ii", DiatonicChord.II.romanLabel)
        assertEquals("iii", DiatonicChord.III.romanLabel)
        assertEquals("IV", DiatonicChord.IV.romanLabel)
        assertEquals("V", DiatonicChord.V.romanLabel)
        assertEquals("vi", DiatonicChord.VI.romanLabel)
        assertEquals("vii\u00B0", DiatonicChord.VII.romanLabel)
        assertEquals("Imaj7", DiatonicChord.I7.romanLabel)
        assertEquals("ii7", DiatonicChord.II7.romanLabel)
        assertEquals("IVmaj7", DiatonicChord.IV7.romanLabel)
        assertEquals("V7", DiatonicChord.V7.romanLabel)
        assertEquals("vii\u00F87", DiatonicChord.VII7.romanLabel)
    }

    @Test
    fun diatonicFunctionsFollowTheFixedMapping() {
        assertEquals(HarmonicFunction.TONIC, DiatonicChord.I.function)
        assertEquals(HarmonicFunction.TONIC, DiatonicChord.III.function)
        assertEquals(HarmonicFunction.TONIC, DiatonicChord.VI.function)
        assertEquals(HarmonicFunction.TONIC, DiatonicChord.I7.function)
        assertEquals(HarmonicFunction.PREDOMINANT, DiatonicChord.II.function)
        assertEquals(HarmonicFunction.PREDOMINANT, DiatonicChord.IV7.function)
        assertEquals(HarmonicFunction.DOMINANT, DiatonicChord.V.function)
        assertEquals(HarmonicFunction.DOMINANT, DiatonicChord.VII7.function)
    }

    @Test
    fun rootMotionsUseMajorDegrees() {
        val sequence = listOf(
            DiatonicChord.I.step,
            DiatonicChord.IV.step,
            DiatonicChord.V.step,
            DiatonicChord.I.step
        )
        assertEquals(
            listOf(
                "ascending perfect fourth",
                "ascending major second",
                "descending perfect fifth"
            ),
            ProgressionTheory.rootMotions(sequence).map { it.label }
        )
        assertEquals(
            "a repeated chord is a unison motion",
            RootMotionDirection.UNISON,
            RootMotion.betweenMajorDegrees(5, 5).direction
        )
        assertEquals(
            "the dominant resolves down a perfect fifth",
            "descending perfect fifth",
            RootMotion.betweenMajorDegrees(5, 1).label
        )
    }

    @Test
    fun theoreticalRootsFollowTheKeyLetter() {
        val cMajor = Key(SpelledPitch.of(Letter.C, 0, 4), Tonality.MAJOR)
        assertEquals(
            MidiNote.of(60),
            ProgressionTheory.rootMidi(DiatonicChord.I.step, Tonality.MAJOR, MidiNote.of(60))
        )
        assertEquals(
            MidiNote.of(65),
            ProgressionTheory.rootMidi(DiatonicChord.IV.step, Tonality.MAJOR, MidiNote.of(60))
        )
        assertEquals(
            "a seventh chord keeps the same root",
            ProgressionTheory.spelledRoot(DiatonicChord.V7.step, cMajor, MidiNote.of(60))
                ?.midiValue,
            67
        )
    }
}
