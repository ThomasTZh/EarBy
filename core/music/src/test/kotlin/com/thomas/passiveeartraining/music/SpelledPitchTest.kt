package com.thomas.passiveeartraining.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** M4-T01: spelling conversion, theoretical letters and checked extremes. */
class SpelledPitchTest {

    @Test
    fun theDocumentedConversionsHold() {
        // B#3 = C4 and Cb4 = B3: the written octave follows the letter, never the sounding value.
        assertEquals(MidiNote.of(MidiNote.C4), SpelledPitch.of(Letter.B, 1, 3).toMidiNote())
        assertEquals(
            MidiNote.of(MidiNote.C4).value - 1,
            SpelledPitch.of(Letter.C, -1, 4).toMidiNote().value
        )
        assertEquals(60, SpelledPitch.of(Letter.C, 0, 4).midiValue)
        assertEquals(61, SpelledPitch.of(Letter.C, 1, 4).midiValue)
        assertEquals(59, SpelledPitch.of(Letter.C, -1, 4).midiValue)
    }

    @Test
    fun chromaticSpellingFollowsTheChosenTable() {
        val cSharp4 = SpelledPitch.fromMidi(MidiNote.of(61), SpellingPreference.SHARP)
        assertEquals(SpelledPitch.of(Letter.C, 1, 4), cSharp4)
        val dFlat4 = SpelledPitch.fromMidi(MidiNote.of(61), SpellingPreference.FLAT)
        assertEquals(SpelledPitch.of(Letter.D, -1, 4), dFlat4)
        assertEquals(
            SpelledPitch.of(Letter.C, 0, 4),
            SpelledPitch.fromMidi(MidiNote.of(60), SpellingPreference.FLAT)
        )
    }

    @Test
    fun accidentalBoundsAndMidiBoundsAreChecked() {
        assertThrows(IllegalArgumentException::class.java) {
            SpelledPitch.of(Letter.C, 3, 4)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SpelledPitch.of(Letter.C, -3, 4)
        }
        assertEquals(null, SpelledPitch.of(Letter.C, -1, -2).toMidiNoteOrNull())
        assertEquals(null, SpelledPitch.of(Letter.B, 1, 10).toMidiNoteOrNull())
    }

    @Test
    fun doubleAccidentalsAreSpelledWithoutLosingTheLetter() {
        // F##4 sounds G4 but keeps the F letter.
        val fDoubleSharp = SpelledPitch.of(Letter.F, 2, 4)
        assertEquals(MidiNote.of(67), fDoubleSharp.toMidiNote())
        assertEquals(Letter.F, fDoubleSharp.letter)
        assertEquals("F##4", fDoubleSharp.toString())
        assertEquals("Bbb3", SpelledPitch.of(Letter.B, -2, 3).toString())
    }
}

/** M4.1: key degrees follow the theoretical letter progression. */
class KeyTest {

    @Test
    fun cMajorDegreesAreNaturalLetters() {
        val key = Key(SpelledPitch.of(Letter.C, 0, 4), Tonality.MAJOR)
        val expected = listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4")
        assertEquals(expected, (1..7).map { key.degree(it).toString() })
        assertEquals(listOf(60, 62, 64, 65, 67, 69, 71), (1..7).map { key.degreeMidi(it) })
    }

    @Test
    fun sharpKeysUseTheoreticalSharps() {
        // F# major: E# is the leading tone, and the letters never repeat.
        val key = Key(SpelledPitch.of(Letter.F, 1, 4), Tonality.MAJOR)
        val names = (1..7).map { key.degree(it).toString() }
        assertEquals(listOf("F#4", "G#4", "A#4", "B4", "C#5", "D#5", "E#5"), names)
        assertEquals(
            "the leading tone keeps its E letter",
            Letter.E,
            key.degree(7).letter
        )
    }

    @Test
    fun flatKeysUseTheoreticalFlats() {
        val key = Key(SpelledPitch.of(Letter.D, -1, 4), Tonality.MAJOR)
        assertEquals(
            listOf("Db4", "Eb4", "F4", "Gb4", "Ab4", "Bb4", "C5"),
            (1..7).map { key.degree(it).toString() }
        )
    }

    @Test
    fun naturalMinorDegreesKeepTheirLoweredDegrees() {
        val key = Key(SpelledPitch.of(Letter.A, 0, 3), Tonality.NATURAL_MINOR)
        assertEquals(
            listOf("A3", "B3", "C4", "D4", "E4", "F4", "G4"),
            (1..7).map { key.degree(it).toString() }
        )
        assertEquals(listOf(57, 59, 60, 62, 64, 65, 67), (1..7).map { key.degreeMidi(it) })
    }

    @Test
    fun anUnrepresentableKeyFailsExplicitly() {
        // All twelve ordinary major keys fit within -2..2; a synthetic double-sharp tonic pushes
        // degree 2 past the third accidental, which must fail explicitly.
        val impossible = Key(SpelledPitch.of(Letter.B, 2, 3), Tonality.MAJOR)
        assertThrows(UnrepresentableSpellingException::class.java) {
            (1..7).forEach { impossible.degree(it) }
        }
    }

    @Test
    fun degreeLettersAdvanceDiatonicallyFromTheTonic() {
        val key = Key(SpelledPitch.of(Letter.E, -1, 4), Tonality.MAJOR)
        assertEquals(
            listOf(Letter.E, Letter.F, Letter.G, Letter.A, Letter.B, Letter.C, Letter.D),
            (1..7).map { key.letterForDegree(it) }
        )
    }
}
