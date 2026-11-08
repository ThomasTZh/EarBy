package com.thomas.passiveeartraining.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Independent expectation table, deliberately written out instead of derived from production code. */
private val EXPECTED_INTERVALS = listOf(
    Triple(0, "unison", "Unison"),
    Triple(1, "minor_second", "Minor second"),
    Triple(2, "major_second", "Major second"),
    Triple(3, "minor_third", "Minor third"),
    Triple(4, "major_third", "Major third"),
    Triple(5, "perfect_fourth", "Perfect fourth"),
    Triple(6, "tritone", "Tritone"),
    Triple(7, "perfect_fifth", "Perfect fifth"),
    Triple(8, "minor_sixth", "Minor sixth"),
    Triple(9, "major_sixth", "Major sixth"),
    Triple(10, "minor_seventh", "Minor seventh"),
    Triple(11, "major_seventh", "Major seventh"),
    Triple(12, "octave", "Octave")
)

private fun expectedPitchClass(midiValue: Int): Int = ((midiValue % 12) + 12) % 12

private fun expectedOctave(midiValue: Int): Int = Math.floorDiv(midiValue, 12) - 1

private fun sharpName(pitchClass: Int): String = listOf(
    "C", "C\u266F", "D", "D\u266F", "E", "F", "F\u266F", "G", "G\u266F", "A", "A\u266F", "B"
)[pitchClass]

private fun flatName(pitchClass: Int): String = listOf(
    "C", "D\u266D", "D", "E\u266D", "E", "F", "G\u266D", "G", "A\u266D", "A", "B\u266D", "B"
)[pitchClass]

class MidiNoteTest {

    @Test
    fun everyRepresentableMidiValueRoundTripsThroughPitchClassAndOctave() {
        for (value in 0..127) {
            val note = MidiNote.of(value)
            assertEquals(value, note.value)
            assertEquals(expectedPitchClass(value), note.pitchClass.value)
            assertEquals(expectedOctave(value), note.octave)
            assertEquals(
                "note $value must rebuild from its own class and octave",
                note,
                MidiNote.of(note.pitchClass, note.octave)
            )
        }
    }

    @Test
    fun referencePitchesMatchScientificPitchNotation() {
        assertEquals(60, MidiNote.C4)
        assertEquals(69, MidiNote.A4)
        assertEquals(PitchClass.of(0), MidiNote.of(60).pitchClass)
        assertEquals(4, MidiNote.of(60).octave)
        assertEquals(PitchClass.of(9), MidiNote.of(69).pitchClass)
        assertEquals(4, MidiNote.of(69).octave)
        assertEquals("C4", MidiNote.of(60).displayName())
        assertEquals("A4", MidiNote.of(69).displayName())
        assertEquals(-1, MidiNote.of(0).octave)
        assertEquals("C-1", MidiNote.of(0).displayName())
        assertEquals(9, MidiNote.of(127).octave)
        assertEquals("G9", MidiNote.of(127).displayName())
    }

    @Test
    fun b3TransposedByOneSemitoneIsC4() {
        val b3 = MidiNote.of(59)
        assertEquals("B3", b3.displayName())
        assertEquals(MidiNote.of(60), b3.requireTransposedBy(1))
        assertEquals("C4", b3.requireTransposedBy(1).displayName())
    }

    @Test
    fun transpositionIsCheckedOnBothEnds() {
        assertEquals(MidiNote.of(1), MidiNote.of(0).requireTransposedBy(1))
        assertEquals(MidiNote.of(126), MidiNote.of(127).requireTransposedBy(-1))
        assertNull(MidiNote.of(0).transposedBy(-1))
        assertNull(MidiNote.of(127).transposedBy(1))
        assertThrows(InvalidMidiNoteException::class.java) {
            MidiNote.of(0).requireTransposedBy(-1)
        }
        assertThrows(InvalidMidiNoteException::class.java) {
            MidiNote.of(127).requireTransposedBy(1)
        }
    }

    @Test
    fun transpositionUsesWidenedArithmeticAtIntegerExtremes() {
        assertNull(MidiNote.of(0).transposedBy(Int.MIN_VALUE))
        assertNull(MidiNote.of(127).transposedBy(Int.MAX_VALUE))
        assertNull(MidiNote.of(64).transposedBy(Int.MIN_VALUE))
        assertNull(MidiNote.of(64).transposedBy(Int.MAX_VALUE))
        assertEquals(MidiNote.of(127), MidiNote.of(0).requireTransposedBy(127))
        assertEquals(MidiNote.of(0), MidiNote.of(127).requireTransposedBy(-127))
    }

    @Test
    fun constructionRejectsValuesOutsideTheMidiRange() {
        assertThrows(InvalidMidiNoteException::class.java) { MidiNote.of(-1) }
        assertThrows(InvalidMidiNoteException::class.java) { MidiNote.of(128) }
        assertThrows(InvalidMidiNoteException::class.java) { MidiNote.of(Int.MIN_VALUE) }
        assertThrows(InvalidMidiNoteException::class.java) { MidiNote.of(Int.MAX_VALUE) }
        assertNull(MidiNote.ofOrNull(-1))
        assertNull(MidiNote.ofOrNull(128))
        assertEquals(MidiNote.of(127), MidiNote.ofOrNull(127))
    }

    @Test
    fun octaveNineIsOnlyPartiallyRepresentable() {
        assertEquals(MidiNote.of(120), MidiNote.of(PitchClass.of(0), 9))
        assertEquals(MidiNote.of(127), MidiNote.of(PitchClass.of(7), 9))
        assertNull(MidiNote.ofOrNull(PitchClass.of(8), 9))
        assertThrows(InvalidMidiNoteException::class.java) { MidiNote.of(PitchClass.of(8), 9) }
        assertThrows(InvalidMidiNoteException::class.java) { MidiNote.of(PitchClass.of(8), 10) }
        // Octave -1 holds C = 0 and C sharp = 1; other classes would be negative.
        assertEquals(MidiNote.of(0), MidiNote.of(PitchClass.of(0), -1))
        assertEquals(MidiNote.of(1), MidiNote.of(PitchClass.of(1), -1))
        assertNull(MidiNote.ofOrNull(PitchClass.of(0), -2))
        assertThrows(InvalidMidiNoteException::class.java) { MidiNote.of(PitchClass.of(0), -2) }
    }

    @Test
    fun spellingPreferenceChangesTextButNotSound() {
        val cSharp = MidiNote.of(61)
        assertEquals(61, cSharp.value)
        assertEquals("C\u266F4", cSharp.displayName(SpellingPreference.SHARP))
        assertEquals("D\u266D4", cSharp.displayName(SpellingPreference.FLAT))
        assertEquals(cSharp, MidiNote.of(61))
        assertEquals(cSharp.transposedBy(0), MidiNote.of(61))
    }
}

class PitchClassTest {

    @Test
    fun pitchClassesCoverTwelveSoundingClassesInCanonicalOrder() {
        assertEquals(12, PitchClass.ALL.size)
        assertEquals((0..11).toList(), PitchClass.ALL.map { it.value })
        for (value in 0..11) {
            assertEquals(value, PitchClass.of(value).value)
        }
    }

    @Test
    fun displayNamesUseTheRequestedEnharmonicSpelling() {
        for (value in 0..11) {
            assertEquals(
                sharpName(value),
                PitchClass.of(value).displayName(SpellingPreference.SHARP)
            )
            assertEquals(flatName(value), PitchClass.of(value).displayName(SpellingPreference.FLAT))
            assertEquals(sharpName(value), PitchClass.of(value).displayName())
        }
        assertEquals("D\u266D", PitchClass.of(1).displayName(SpellingPreference.FLAT))
        assertEquals("A\u266F", PitchClass.of(10).displayName(SpellingPreference.SHARP))
        assertEquals("B\u266D", PitchClass.of(10).displayName(SpellingPreference.FLAT))
    }

    @Test
    fun constructionRejectsValuesOutsideTwelveClasses() {
        assertThrows(InvalidMidiNoteException::class.java) { PitchClass.of(-1) }
        assertThrows(InvalidMidiNoteException::class.java) { PitchClass.of(12) }
    }

    @Test
    fun integerMidiValuesWrapIntoTheCorrectClass() {
        assertEquals(0, PitchClass.ofMidiValue(60).value)
        assertEquals(0, PitchClass.ofMidiValue(0).value)
        assertEquals(7, PitchClass.ofMidiValue(127).value)
        assertEquals(0, PitchClass.ofMidiValue(-12).value)
        assertEquals(4, PitchClass.ofMidiValue(-8).value)
        assertEquals(4, PitchClass.ofMidiValue(Int.MIN_VALUE).value)
        assertEquals(7, PitchClass.ofMidiValue(Int.MAX_VALUE).value)
    }
}

class SimpleIntervalTest {

    @Test
    fun theCompleteSimpleIntervalTableIsStable() {
        assertEquals(13, SimpleInterval.ALL.size)
        for ((semitones, id, answer) in EXPECTED_INTERVALS) {
            val interval = SimpleInterval.of(semitones)
            assertEquals(semitones, interval.semitones)
            assertEquals(id, interval.id)
            assertEquals(answer, interval.englishAnswer)
            assertEquals("interval.$id", interval.answerId)
            assertEquals(interval, SimpleInterval.ofId(id))
            assertEquals(interval, SimpleInterval.ALL[semitones])
        }
    }

    @Test
    fun namedSemitoneConstantsMatchTheTable() {
        assertEquals(SimpleInterval.of(SimpleInterval.UNISON), SimpleInterval.ofId("unison"))
        assertEquals(
            SimpleInterval.of(SimpleInterval.MINOR_THIRD),
            SimpleInterval.ofId("minor_third")
        )
        assertEquals(
            SimpleInterval.of(SimpleInterval.MAJOR_THIRD),
            SimpleInterval.ofId("major_third")
        )
        assertEquals(
            SimpleInterval.of(SimpleInterval.PERFECT_FIFTH),
            SimpleInterval.ofId("perfect_fifth")
        )
        assertEquals(SimpleInterval.of(SimpleInterval.OCTAVE), SimpleInterval.ofId("octave"))
    }

    @Test
    fun constructionRejectsUnsupportedSemitoneCountsAndIds() {
        assertThrows(InvalidIntervalException::class.java) { SimpleInterval.of(-1) }
        assertThrows(InvalidIntervalException::class.java) { SimpleInterval.of(13) }
        assertThrows(InvalidIntervalException::class.java) { SimpleInterval.ofId("compound_ninth") }
        assertThrows(InvalidIntervalException::class.java) { SimpleInterval.ofId("minor third") }
        assertNull(SimpleInterval.ofOrNull(13))
        assertNull(SimpleInterval.ofIdOrNull("major_tenth"))
    }

    @Test
    fun intervalsAreDirectionIndependentDistances() {
        val c4 = MidiNote.of(60)
        val g4 = MidiNote.of(67)
        assertEquals(SimpleInterval.ofId("perfect_fifth"), SimpleInterval.between(c4, g4))
        assertEquals(SimpleInterval.ofId("perfect_fifth"), SimpleInterval.between(g4, c4))
        assertEquals(SimpleInterval.ofId("unison"), SimpleInterval.between(c4, c4))
        assertEquals(
            SimpleInterval.ofId("octave"),
            SimpleInterval.between(MidiNote.of(60), MidiNote.of(72))
        )
        assertNull(SimpleInterval.between(MidiNote.of(0), MidiNote.of(13)))
        assertEquals(
            EXPECTED_INTERVALS.size,
            (0..12).count {
                SimpleInterval.between(MidiNote.of(60), MidiNote.of(60 + it)) !=
                    null
            }
        )
    }

    @Test
    fun ascendingPresentationPlaysRootThenTarget() {
        val notes = IntervalPresentation.ASCENDING.notes(
            MidiNote.of(60),
            SimpleInterval.ofId("perfect_fifth")
        )
        assertEquals(listOf(MidiNote.of(60), MidiNote.of(67)), notes)
        assertEquals(
            MidiNote.of(60),
            IntervalPresentation.ASCENDING.rootFor(
                MidiNote.of(60),
                SimpleInterval.ofId("perfect_fifth")
            )
        )
    }

    @Test
    fun descendingPresentationPlaysRootThenLowerTarget() {
        val interval = SimpleInterval.ofId("perfect_fifth")
        val notes = IntervalPresentation.DESCENDING.notes(MidiNote.of(67), interval)
        assertEquals(listOf(MidiNote.of(67), MidiNote.of(60)), notes)
        assertEquals(
            MidiNote.of(67),
            IntervalPresentation.DESCENDING.rootFor(MidiNote.of(60), interval)
        )
        // A descending unison is the same pitch twice rather than a rejection; both melodic
        // slots sound.
        assertEquals(
            listOf(MidiNote.of(0), MidiNote.of(0)),
            IntervalPresentation.DESCENDING.notes(MidiNote.of(0), SimpleInterval.ofId("unison"))
        )
        assertEquals(
            MidiNote.of(0),
            IntervalPresentation.DESCENDING.rootFor(MidiNote.of(0), SimpleInterval.ofId("unison"))
        )
        assertNull(
            IntervalPresentation.DESCENDING.notes(
                MidiNote.of(3),
                SimpleInterval.ofId("major_third")
            )
        )
    }

    @Test
    fun harmonicPresentationKeepsTheRootAsTheLowerNote() {
        val interval = SimpleInterval.ofId("major_third")
        assertEquals(
            listOf(MidiNote.of(60), MidiNote.of(64)),
            IntervalPresentation.HARMONIC.notes(MidiNote.of(60), interval)
        )
        assertEquals(
            MidiNote.of(60),
            IntervalPresentation.HARMONIC.rootFor(MidiNote.of(60), interval)
        )
        assertEquals(
            listOf(MidiNote.of(63)),
            IntervalPresentation.HARMONIC.notes(MidiNote.of(63), SimpleInterval.ofId("unison"))
        )
    }

    @Test
    fun unisonKeepsBothMelodicSlotsAndOneHarmonicNote() {
        val unison = SimpleInterval.ofId("unison")
        for (presentation in listOf(
            IntervalPresentation.ASCENDING,
            IntervalPresentation.DESCENDING
        )) {
            assertEquals(
                "a melodic unison sounds the same pitch twice",
                listOf(MidiNote.of(60), MidiNote.of(60)),
                presentation.notes(MidiNote.of(60), unison)
            )
            assertEquals(MidiNote.of(60), presentation.rootFor(MidiNote.of(60), unison))
        }
        assertEquals(
            "a harmonic unison stays one audible note",
            listOf(MidiNote.of(60)),
            IntervalPresentation.HARMONIC.notes(MidiNote.of(60), unison)
        )
        assertEquals(
            MidiNote.of(60),
            IntervalPresentation.HARMONIC.rootFor(MidiNote.of(60), unison)
        )
        assertEquals(0, unison.semitones)
    }

    @Test
    fun presentationsRejectExamplesOutsideTheMidiRange() {
        // Perfect fifth = 7 semitones; every expectation below uses that independent count.
        val fifth = SimpleInterval.ofId("perfect_fifth")
        assertTrue(IntervalPresentation.ASCENDING.isFeasible(MidiNote.of(0), fifth))
        assertTrue(!IntervalPresentation.ASCENDING.isFeasible(MidiNote.of(121), fifth))
        assertNull(IntervalPresentation.ASCENDING.notes(MidiNote.of(121), fifth))
        assertNull(IntervalPresentation.ASCENDING.notes(MidiNote.of(127), fifth))
        assertNull(IntervalPresentation.DESCENDING.notes(MidiNote.of(4), fifth))
        assertNull(
            IntervalPresentation.HARMONIC.notes(
                MidiNote.of(124),
                SimpleInterval.ofId("major_third")
            )
        )
        // A descending example has root = lowest note + interval, so only lowest notes whose root
        // stays in range have a representable root.
        for (lowest in 0..127) {
            val root = IntervalPresentation.DESCENDING.rootFor(MidiNote.of(lowest), fifth)
            if (lowest <= 120) {
                assertEquals(
                    "descending fifth above lowest note $lowest",
                    MidiNote.of(lowest + 7),
                    root
                )
                assertEquals(
                    listOf(MidiNote.of(lowest + 7), MidiNote.of(lowest)),
                    IntervalPresentation.DESCENDING.notes(root!!, fifth)
                )
            } else {
                assertNull("descending fifth above lowest note $lowest", root)
            }
        }
        // A descending major seventh above its lowest note 4 has root 15; the highest lowest note
        // that still has an in-range root is 116.
        assertEquals(
            MidiNote.of(15),
            IntervalPresentation.DESCENDING.rootFor(
                MidiNote.of(4),
                SimpleInterval.ofId("major_seventh")
            )
        )
        assertNull(
            IntervalPresentation.DESCENDING.rootFor(
                MidiNote.of(117),
                SimpleInterval.ofId("major_seventh")
            )
        )
        // A descending octave above its lowest note 0 has root 12; above 116 it would need 128.
        assertEquals(
            MidiNote.of(12),
            IntervalPresentation.DESCENDING.rootFor(MidiNote.of(0), SimpleInterval.ofId("octave"))
        )
        assertNull(
            IntervalPresentation.DESCENDING.rootFor(MidiNote.of(116), SimpleInterval.ofId("octave"))
        )
        assertEquals(
            MidiNote.of(0),
            IntervalPresentation.DESCENDING.rootFor(MidiNote.of(0), SimpleInterval.ofId("unison"))
        )
        assertEquals(
            MidiNote.of(11),
            IntervalPresentation.DESCENDING.rootFor(MidiNote.of(4), fifth)
        )
        assertEquals(
            listOf(MidiNote.of(11), MidiNote.of(4)),
            IntervalPresentation.DESCENDING.notes(MidiNote.of(11), fifth)
        )
    }

    @Test
    fun presentationOrderIsStableForPersistence() {
        assertEquals(
            listOf(
                IntervalPresentation.ASCENDING,
                IntervalPresentation.DESCENDING,
                IntervalPresentation.HARMONIC
            ),
            IntervalPresentation.entries.toList()
        )
        assertEquals(
            listOf("ASCENDING", "DESCENDING", "HARMONIC"),
            IntervalPresentation.entries.map { it.name }
        )
    }
}
