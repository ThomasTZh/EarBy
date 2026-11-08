package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.Letter
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.music.SpellingPreference
import com.thomas.passiveeartraining.training.provider.VoiceVocabulary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** M7.1/M7-T01..T03: note definition normalization, spelling projections and feasibility. */
class NoteDefinitionTest {

    private fun definition(
        pitchClasses: List<Int> = (0..11).toList(),
        accidental: SpellingPreference = SpellingPreference.SHARP,
        speakOctave: Boolean = false,
        minimumNote: MidiNote = MidiNote.of(60),
        maximumNote: MidiNote = MidiNote.of(72)
    ): NoteDefinition = NoteDefinition.of(
        pitchClassValues = pitchClasses,
        accidental = accidental,
        speakOctave = speakOctave,
        minimumNote = minimumNote,
        maximumNote = maximumNote
    )

    private fun invalidFieldOf(block: () -> Unit): String? {
        try {
            block()
        } catch (exception: InvalidNoteDefinitionException) {
            return exception.field
        }
        fail("the definition must be rejected")
        return null
    }

    @Test
    fun theDefaultSelectsEveryClassWithSharpSpellingAndNoOctaveSpeech() {
        val default = NoteDefinition.default()
        assertEquals(PitchClass.ALL, default.selectedPitchClasses)
        assertEquals((0..11).toList(), default.pitchClassValues)
        assertEquals(SpellingPreference.SHARP, default.accidental)
        assertEquals(false, default.speakOctave)
        assertEquals(60, default.minimumNoteValue)
        assertEquals(72, default.maximumNoteValue)
        assertEquals(60..72, default.noteRange)
    }

    @Test
    fun emptyOrUnknownSelectionsAreRejectedOnThePitchClassesField() {
        assertEquals("pitchClasses", invalidFieldOf { definition(pitchClasses = emptyList()) })
        assertEquals("pitchClasses", invalidFieldOf { definition(pitchClasses = listOf(0, 12)) })
        assertEquals("pitchClasses", invalidFieldOf { definition(pitchClasses = listOf(-1)) })
    }

    @Test
    fun pitchClassesAreSortedAndDeduplicated() {
        assertEquals(
            listOf(0, 3, 7, 11),
            definition(pitchClasses = listOf(11, 7, 0, 7, 3)).pitchClassValues
        )
    }

    @Test
    fun anInvertedRangeIsRejectedOnTheRangeField() {
        assertEquals(
            "range",
            invalidFieldOf {
                definition(minimumNote = MidiNote.of(72), maximumNote = MidiNote.of(60))
            }
        )
    }

    @Test
    fun theDocumentedMidiMappingsHold() {
        assertEquals(60, MidiNote.of(PitchClass.of(0), 4).value)
        assertEquals(69, MidiNote.of(PitchClass.of(9), 4).value)
        assertEquals(0, MidiNote.of(PitchClass.of(0), -1).value)
        assertEquals(127, MidiNote.of(PitchClass.of(7), 9).value)

        val fullRange = definition(
            minimumNote = MidiNote.of(0),
            maximumNote = MidiNote.of(127)
        )
        assertEquals(
            listOf(0, 12, 24, 36, 48, 60, 72, 84, 96, 108, 120),
            fullRange.notesFor(PitchClass.of(0)).map { it.value }
        )
        assertEquals(127, fullRange.notesFor(PitchClass.of(7)).maxOf { it.value })
    }

    @Test
    fun scientificNamingFollowsTheSelectedSharpOrFlatTable() {
        val sharp = definition(accidental = SpellingPreference.SHARP)
        assertEquals(NoteName.Scientific(Letter.C, 1), sharp.nameOf(PitchClass.of(1)))
        assertEquals(NoteName.Scientific(Letter.F, 1), sharp.nameOf(PitchClass.of(6)))
        assertEquals("note.c_sharp", sharp.nameToken(PitchClass.of(1)).value)
        assertEquals("C sharp", sharp.nameOf(PitchClass.of(1)).entry.spokenText)

        val flat = definition(accidental = SpellingPreference.FLAT)
        assertEquals(NoteName.Scientific(Letter.D, -1), flat.nameOf(PitchClass.of(1)))
        assertEquals(NoteName.Scientific(Letter.G, -1), flat.nameOf(PitchClass.of(6)))
        assertEquals("note.d_flat", flat.nameToken(PitchClass.of(1)).value)
        assertEquals("D flat", flat.nameOf(PitchClass.of(1)).entry.spokenText)

        // Naturals are identical in both tables and never carry an accidental.
        assertEquals(sharp.nameOf(PitchClass.of(5)), flat.nameOf(PitchClass.of(5)))
        assertEquals("note.f", flat.nameToken(PitchClass.of(5)).value)
        assertEquals("note.b", flat.nameToken(PitchClass.of(11)).value)
    }

    @Test
    fun everySelectedClassProjectsUnderEveryAccidentalStyle() {
        for (accidental in SpellingPreference.entries) {
            val definition = definition(accidental = accidental)
            definition.selectedPitchClasses.forEach { pitchClass ->
                val entry = definition.nameOf(pitchClass).entry
                assertTrue(
                    "unexpected namespace for $accidental: ${entry.id.value}",
                    entry.id.value.startsWith("note.")
                )
                assertTrue(entry.spokenText.isNotBlank())
                assertEquals(
                    "the projection must be a declared vocabulary entry",
                    entry,
                    VoiceVocabulary.ofIdOrNull(entry.id)
                )
            }
        }
    }

    @Test
    fun feasibleNotesAreTheRangeNotesInNumericOrder() {
        val default = NoteDefinition.default()
        assertEquals(listOf(60, 72), default.notesFor(PitchClass.of(0)).map { it.value })
        assertEquals(listOf(61), default.notesFor(PitchClass.of(1)).map { it.value })
        assertEquals(listOf(71), default.notesFor(PitchClass.of(11)).map { it.value })
        assertTrue(
            "every reachable note sounds inside the range",
            default.reachableNotes.all { it.value in default.noteRange }
        )
        assertEquals(
            "reachable notes are one ascending sequence",
            default.reachableNotes.map { it.value }.sorted(),
            default.reachableNotes.map { it.value }
        )
    }

    @Test
    fun answerTokensFollowTheOctaveSpeechChoice() {
        val silent = definition()
        assertEquals(
            listOf("note.c_sharp"),
            silent.answerTokensFor(MidiNote.of(61)).map { it.value }
        )
        assertEquals("C sharp", silent.answerTextFor(MidiNote.of(61)))

        val speaking = definition(speakOctave = true)
        assertEquals(
            listOf("note.c_sharp", "note.octave.4"),
            speaking.answerTokensFor(MidiNote.of(61)).map { it.value }
        )
        assertEquals("C sharp four", speaking.answerTextFor(MidiNote.of(61)))
    }

    @Test
    fun normalizedDefinitionsCompareByTheirMusicalContent() {
        val left = definition(pitchClasses = listOf(7, 0, 0), speakOctave = true)
        val right = definition(pitchClasses = listOf(0, 7), speakOctave = true)
        assertEquals(left, right)
        assertEquals(left.hashCode(), right.hashCode())
        assertTrue(left != definition(pitchClasses = listOf(0, 7), speakOctave = false))
        assertTrue(
            left !=
                definition(
                    pitchClasses = listOf(0, 7),
                    speakOctave = true,
                    accidental = SpellingPreference.FLAT
                )
        )
    }
}
