package com.thomas.passiveeartraining.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** M4-T02: every chord/scale formula and inversion matches the frozen vocabulary. */
class ChordQualityTest {

    @Test
    fun everyQualityCarriesTheFrozenFormula() {
        val expected = mapOf(
            "major" to listOf(0, 4, 7),
            "minor" to listOf(0, 3, 7),
            "diminished" to listOf(0, 3, 6),
            "augmented" to listOf(0, 4, 8),
            "sus2" to listOf(0, 2, 7),
            "sus4" to listOf(0, 5, 7),
            "major_seventh" to listOf(0, 4, 7, 11),
            "minor_seventh" to listOf(0, 3, 7, 10),
            "dominant_seventh" to listOf(0, 4, 7, 10),
            "half_diminished" to listOf(0, 3, 6, 10),
            "diminished_seventh" to listOf(0, 3, 6, 9)
        )
        assertEquals(11, ChordQuality.entries.size)
        expected.forEach { (id, semitones) ->
            val quality = ChordQuality.ofIdOrNull(id) ?: error("missing $id")
            assertEquals(id, semitones, quality.semitones)
            assertEquals(
                "triads invert twice, sevenths three times",
                quality.memberCount - 1,
                quality.maximumInversion
            )
        }
    }

    @Test
    fun closedPositionVoicingsSortAscendingAndKeepSeventhsAsSevenths() {
        val cMajorSeventh = ChordVoicing.of(ChordQuality.MAJOR_SEVENTH, 0, MidiNote.of(60))
        assertEquals(listOf(60, 64, 67, 71), cMajorSeventh.notes.map { it.value })
        assertEquals(
            listOf("C4", "E4", "G4", "B4"),
            cMajorSeventh.spelledMembers().map { it.toString() }
        )

        val dMinorSeventh = ChordVoicing.of(ChordQuality.MINOR_SEVENTH, 0, MidiNote.of(62))
        assertEquals(
            listOf("D4", "F4", "A4", "C5"),
            dMinorSeventh.spelledMembers().map {
                it.toString()
            }
        )
    }

    @Test
    fun diminishedAndAugmentedSpellingsKeepTheirTheoreticalLetters() {
        // C diminished seventh needs a double-flat seventh (Bbb), never an A relabel.
        val cDim7 = ChordVoicing.of(ChordQuality.DIMINISHED_SEVENTH, 0, MidiNote.of(60))
        assertEquals(
            listOf("C4", "Eb4", "Gb4", "Bbb4"),
            cDim7.spelledMembers().map { it.toString() }
        )
        val cSharpDim7 = ChordVoicing.of(ChordQuality.DIMINISHED_SEVENTH, 0, MidiNote.of(61))
        assertEquals(
            listOf("C#4", "E4", "G4", "Bb4"),
            cSharpDim7.spelledMembers().map { it.toString() }
        )
        val cAugmented = ChordVoicing.of(ChordQuality.AUGMENTED, 0, MidiNote.of(60))
        assertEquals(listOf("C4", "E4", "G#4"), cAugmented.spelledMembers().map { it.toString() })
    }

    @Test
    fun inversionsRaiseTheFirstMembersAndChangeTheBass() {
        val root = MidiNote.of(60)
        val rootPosition = ChordVoicing.of(ChordQuality.MAJOR, 0, root)
        assertEquals(60, rootPosition.bass.value)
        assertEquals(60, rootPosition.theoreticalRoot.midiValue)

        val first = ChordVoicing.of(ChordQuality.MAJOR, 1, root)
        assertEquals(listOf(64, 67, 72), first.notes.map { it.value })
        assertEquals(64, first.bass.value)
        assertEquals("the theoretical root does not move", 60, first.theoreticalRoot.midiValue)

        val second = ChordVoicing.of(ChordQuality.MAJOR, 2, root)
        assertEquals(listOf(67, 72, 76), second.notes.map { it.value })
    }

    @Test
    fun seventhInversionsProduceTheDocumentedBasses() {
        val root = MidiNote.of(60)
        assertEquals(
            listOf(60, 64, 67, 71),
            ChordVoicing.of(ChordQuality.MAJOR_SEVENTH, 0, root).notes.map { it.value }
        )
        assertEquals(
            listOf(64, 67, 71, 72),
            ChordVoicing.of(ChordQuality.MAJOR_SEVENTH, 1, root).notes.map { it.value }
        )
        assertEquals(
            listOf(67, 71, 72, 76),
            ChordVoicing.of(ChordQuality.MAJOR_SEVENTH, 2, root).notes.map { it.value }
        )
        assertEquals(
            listOf(71, 72, 76, 79),
            ChordVoicing.of(ChordQuality.MAJOR_SEVENTH, 3, root).notes.map { it.value }
        )
    }

    @Test
    fun invalidInversionsAndOutOfRangeRootsFail() {
        assertThrows(IllegalArgumentException::class.java) {
            ChordVoicing.of(ChordQuality.MAJOR, 3, MidiNote.of(60))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChordVoicing.of(ChordQuality.MAJOR_SEVENTH, 0, MidiNote.of(125))
        }
    }

    @Test
    fun symmetricQualitiesProduceEquivalentPitchClassSetsAcrossInversions() {
        fun pitchClasses(quality: ChordQuality, inversion: Int): Set<Int> =
            ChordVoicing.of(quality, inversion, MidiNote.of(60))
                .notes.map { it.value.mod(12) }.toSet()
        assertEquals(
            pitchClasses(ChordQuality.AUGMENTED, 0),
            pitchClasses(ChordQuality.AUGMENTED, 1)
        )
        assertEquals(
            pitchClasses(ChordQuality.DIMINISHED_SEVENTH, 0),
            pitchClasses(ChordQuality.DIMINISHED_SEVENTH, 2)
        )
    }
}

/** M4-T02: the thirteen scale formulas and their traversals. */
class ScaleKindTest {

    @Test
    fun everyScaleCarriesTheFrozenFormula() {
        val expected = mapOf(
            "major" to listOf(0, 2, 4, 5, 7, 9, 11),
            "natural_minor" to listOf(0, 2, 3, 5, 7, 8, 10),
            "harmonic_minor" to listOf(0, 2, 3, 5, 7, 8, 11),
            "jazz_melodic_minor" to listOf(0, 2, 3, 5, 7, 9, 11),
            "dorian" to listOf(0, 2, 3, 5, 7, 9, 10),
            "phrygian" to listOf(0, 1, 3, 5, 7, 8, 10),
            "lydian" to listOf(0, 2, 4, 6, 7, 9, 11),
            "mixolydian" to listOf(0, 2, 4, 5, 7, 9, 10),
            "locrian" to listOf(0, 1, 3, 5, 6, 8, 10),
            "major_pentatonic" to listOf(0, 2, 4, 7, 9),
            "minor_pentatonic" to listOf(0, 3, 5, 7, 10),
            "minor_blues" to listOf(0, 3, 5, 6, 7, 10),
            "whole_tone" to listOf(0, 2, 4, 6, 8, 10)
        )
        assertEquals(13, ScaleKind.entries.size)
        expected.forEach { (id, semitones) ->
            val kind = ScaleKind.ofIdOrNull(id) ?: error("missing $id")
            assertEquals(id, semitones, kind.semitones)
        }
        assertEquals(ScaleKind.MAJOR, ScaleKind.ofIdOrNull("ionian"))
        assertEquals(ScaleKind.NATURAL_MINOR, ScaleKind.ofIdOrNull("aeolian"))
    }

    @Test
    fun aCompleteOctaveIncludesTheRepeatedTonic() {
        val c4 = SpelledPitch.of(Letter.C, 0, 4)
        val major = ScaleTraversal.pitches(ScaleKind.MAJOR, c4, ScaleDirection.ASCENDING)
        assertEquals(
            listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5"),
            major.map { it.toString() }
        )
        val descending = ScaleTraversal.pitches(ScaleKind.MAJOR, c4, ScaleDirection.DESCENDING)
        assertEquals(major.reversed().map { it.toString() }, descending.map { it.toString() })
        val both = ScaleTraversal.pitches(ScaleKind.MAJOR, c4, ScaleDirection.BOTH)
        assertEquals("the top tonic sounds exactly once", 15, both.size)
        assertEquals("C4", both.first().toString())
        assertEquals("C5", both[7].toString())
    }

    @Test
    fun pentatonicAndBluesSpellingsUseTheFrozenDegrees() {
        val c4 = SpelledPitch.of(Letter.C, 0, 4)
        assertEquals(
            listOf("C4", "D4", "E4", "G4", "A4", "C5"),
            ScaleTraversal.pitches(ScaleKind.MAJOR_PENTATONIC, c4, ScaleDirection.ASCENDING)
                .map { it.toString() }
        )
        assertEquals(
            listOf("C4", "Eb4", "F4", "Gb4", "G4", "Bb4", "C5"),
            ScaleTraversal.pitches(ScaleKind.MINOR_BLUES, c4, ScaleDirection.ASCENDING)
                .map { it.toString() }
        )
    }

    @Test
    fun wholeToneUsesTheExplicitSixLetterPattern() {
        val c4 = SpelledPitch.of(Letter.C, 0, 4)
        assertEquals(
            listOf("C4", "D4", "E4", "F#4", "G#4", "A#4", "C5"),
            ScaleTraversal.pitches(ScaleKind.WHOLE_TONE, c4, ScaleDirection.ASCENDING)
                .map { it.toString() }
        )
    }

    @Test
    fun allThirteenScalesTraverseFromEveryNaturalTonic() {
        val tonics = Letter.entries.map { SpelledPitch.of(it, 0, 4) }
        for (kind in ScaleKind.entries) {
            for (tonic in tonics) {
                val pitches = ScaleTraversal.pitches(kind, tonic, ScaleDirection.BOTH)
                assertTrue(
                    "$kind from $tonic must sound",
                    pitches.size >= kind.semitones.size + 1
                )
                val values = pitches.map { it.midiValue }
                val peak = values.indexOf(values.max())
                assertTrue(
                    "sounding values must ascend to the top tonic",
                    (0 until peak).all { values[it] <= values[it + 1] }
                )
                assertTrue(
                    "sounding values must descend after the top tonic",
                    (peak until values.lastIndex).all { values[it] >= values[it + 1] }
                )
            }
        }
    }
}
