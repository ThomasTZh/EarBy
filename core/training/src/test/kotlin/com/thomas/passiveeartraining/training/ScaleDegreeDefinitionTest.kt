package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.Letter
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.music.SpelledPitch
import com.thomas.passiveeartraining.music.SpellingPreference
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.context.ContextKind
import com.thomas.passiveeartraining.training.provider.GroupRole
import com.thomas.passiveeartraining.training.provider.VoiceVocabulary
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M8.1/M8-T01/T03: the normalized scale-degree configuration, its theoretical projections and
 * its answer-token formats.
 */
class ScaleDegreeDefinitionTest {

    private fun definition(
        keys: List<Int> = listOf(0),
        tonalities: List<Tonality> = listOf(Tonality.MAJOR),
        degrees: List<Int> = (1..7).toList(),
        context: ContextKind = ContextKind.TONIC_TRIAD,
        gap: Duration = 500.milliseconds,
        answerFormat: DegreeAnswerFormat = DegreeAnswerFormat.DEGREE,
        spelling: SpellingPreference = SpellingPreference.SHARP,
        minimum: MidiNote = MidiNote.of(60),
        maximum: MidiNote = MidiNote.of(72)
    ): ScaleDegreeDefinition = ScaleDegreeDefinition.of(
        keyValues = keys,
        tonalities = tonalities,
        degrees = degrees,
        context = context,
        contextGap = gap,
        answerFormat = answerFormat,
        spelling = spelling,
        minimumNote = minimum,
        maximumNote = maximum
    )

    @Test
    fun theDefaultDefinitionIsCmajorTonicTriad() {
        val default = ScaleDegreeDefinition.default()
        assertEquals(listOf(0), default.keyValues)
        assertEquals(listOf(Tonality.MAJOR), default.tonalities)
        assertEquals((1..7).toList(), default.degrees)
        assertEquals(ContextKind.TONIC_TRIAD, default.context)
        assertEquals(500.milliseconds, default.contextGap)
        assertEquals(DegreeAnswerFormat.DEGREE, default.answerFormat)
        assertEquals(SpellingPreference.SHARP, default.spelling)
        assertEquals(60, default.minimumNoteValue)
        assertEquals(72, default.maximumNoteValue)
        assertEquals(definition(), default)
        assertEquals(default, definition())
    }

    @Test
    fun anEmptyKeySelectionMeansEveryKeyTonic() {
        val all = definition(keys = emptyList())
        assertTrue(all.keyValues.isEmpty())
        assertEquals((0..11).toList(), all.effectiveKeyValues)
        assertEquals((0..11).toList(), definition(keys = (0..11).toList()).effectiveKeyValues)
        assertEquals(listOf(1, 7), definition(keys = listOf(7, 1, 7)).keyValues)
    }

    @Test
    fun selectionsAreDeduplicatedAndOrdered() {
        val normalized = definition(
            keys = listOf(7, 2, 2),
            tonalities = listOf(Tonality.NATURAL_MINOR, Tonality.MAJOR, Tonality.MAJOR),
            degrees = listOf(5, 1, 5)
        )
        assertEquals(listOf(2, 7), normalized.keyValues)
        assertEquals(listOf(Tonality.MAJOR, Tonality.NATURAL_MINOR), normalized.tonalities)
        assertEquals(listOf(1, 5), normalized.degrees)
    }

    @Test
    fun invalidSelectionsNameTheirSetupField() {
        assertEquals(
            "keys",
            assertThrows(InvalidScaleDegreeDefinitionException::class.java) {
                definition(keys = listOf(12))
            }.field
        )
        assertEquals(
            "keys",
            assertThrows(InvalidScaleDegreeDefinitionException::class.java) {
                definition(keys = listOf(-1))
            }.field
        )
        assertEquals(
            "tonalities",
            assertThrows(InvalidScaleDegreeDefinitionException::class.java) {
                definition(tonalities = emptyList())
            }.field
        )
        assertEquals(
            "degrees",
            assertThrows(InvalidScaleDegreeDefinitionException::class.java) {
                definition(degrees = emptyList())
            }.field
        )
        assertEquals(
            "degrees",
            assertThrows(InvalidScaleDegreeDefinitionException::class.java) {
                definition(degrees = listOf(0))
            }.field
        )
        assertEquals(
            "degrees",
            assertThrows(InvalidScaleDegreeDefinitionException::class.java) {
                definition(degrees = listOf(8))
            }.field
        )
        assertEquals(
            "contextGap",
            assertThrows(InvalidScaleDegreeDefinitionException::class.java) {
                definition(gap = (-1).milliseconds)
            }.field
        )
        assertEquals(
            "contextGap",
            assertThrows(InvalidScaleDegreeDefinitionException::class.java) {
                definition(gap = 30_001.milliseconds)
            }.field
        )
        assertEquals(
            "contextGap",
            assertThrows(InvalidScaleDegreeDefinitionException::class.java) {
                definition(gap = Duration.INFINITE)
            }.field
        )
        assertEquals(
            "range",
            assertThrows(InvalidScaleDegreeDefinitionException::class.java) {
                definition(minimum = MidiNote.of(73), maximum = MidiNote.of(72))
            }.field
        )
    }

    @Test
    fun keySpellingFollowsThePreference() {
        val sharp = definition(spelling = SpellingPreference.SHARP)
        assertEquals(SpelledPitch.of(Letter.C, 1, 4), sharp.spelledKeyTonic(1))
        val flat = definition(spelling = SpellingPreference.FLAT)
        assertEquals(SpelledPitch.of(Letter.D, -1, 4), flat.spelledKeyTonic(1))
        assertEquals("C sharp major", sharp.keyDisplayName(1, Tonality.MAJOR))
        assertEquals("D flat major", flat.keyDisplayName(1, Tonality.MAJOR))
        assertEquals("A natural minor", flat.keyDisplayName(9, Tonality.NATURAL_MINOR))
    }

    @Test
    fun degreeSpellingFollowsTheKeyLetterProgression() {
        val dMajor = definition(keys = listOf(2))
        val third = dMajor.spelledDegree(2, Tonality.MAJOR, 3)
        assertEquals(Letter.F, third.letter)
        assertEquals(1, third.accidental)
        assertEquals("D major degree 3 is F sharp", 66, third.midiValue)

        val fMajor = definition(keys = listOf(5), spelling = SpellingPreference.FLAT)
        val fourth = fMajor.spelledDegree(5, Tonality.MAJOR, 4)
        assertEquals(Letter.B, fourth.letter)
        assertEquals(-1, fourth.accidental)
        assertEquals("F major degree 4 is B flat", 70, fourth.midiValue)

        val gSharpMajor = definition(keys = listOf(8))
        val seventh = gSharpMajor.spelledDegree(8, Tonality.MAJOR, 7)
        assertEquals("G sharp major degree 7 is F double sharp", Letter.F, seventh.letter)
        assertEquals(2, seventh.accidental)
        assertEquals(79, seventh.midiValue)
    }

    @Test
    fun degreePitchClassesAreTheoreticalNotLastContextNotes() {
        val definition = definition()
        assertEquals(PitchClass.of(0), definition.degreePitchClass(0, Tonality.MAJOR, 1))
        assertEquals(PitchClass.of(11), definition.degreePitchClass(0, Tonality.MAJOR, 7))
        assertEquals(
            "natural minor degree 7 is G, the lowered seventh",
            PitchClass.of(7),
            definition.degreePitchClass(9, Tonality.NATURAL_MINOR, 7)
        )
        assertEquals(
            "natural minor degree 2 is B",
            PitchClass.of(11),
            definition.degreePitchClass(9, Tonality.NATURAL_MINOR, 2)
        )
        assertEquals(
            "natural minor degree 3 is the lowered third class",
            PitchClass.of(0),
            definition.degreePitchClass(9, Tonality.NATURAL_MINOR, 3)
        )
    }

    @Test
    fun answerTokensPerFormat() {
        val degree = 5
        val degreeAnswers = definition(degrees = listOf(degree))
        assertEquals(
            listOf("degree.five"),
            degreeAnswers.answerTokensFor(0, Tonality.MAJOR, degree).map { it.value }
        )
        assertEquals("five", degreeAnswers.answerTextFor(0, Tonality.MAJOR, degree))

        // F major degree 4 is A sharp or B flat; the flat reading uses the flat table.
        val sharpName = definition(
            keys = listOf(5),
            degrees = listOf(4),
            answerFormat = DegreeAnswerFormat.NOTE_NAME,
            spelling = SpellingPreference.SHARP
        )
        assertEquals(
            listOf("note.a_sharp"),
            sharpName.answerTokensFor(5, Tonality.MAJOR, 4).map { it.value }
        )
        assertEquals("A sharp", sharpName.answerTextFor(5, Tonality.MAJOR, 4))

        val flatName = definition(
            keys = listOf(5),
            degrees = listOf(4),
            answerFormat = DegreeAnswerFormat.NOTE_NAME,
            spelling = SpellingPreference.FLAT
        )
        assertEquals(
            listOf("note.b_flat"),
            flatName.answerTokensFor(5, Tonality.MAJOR, 4).map { it.value }
        )
        assertEquals("B flat", flatName.answerTextFor(5, Tonality.MAJOR, 4))
    }

    @Test
    fun noteNameAnswersNeverUseDoubleAccidentals() {
        for (spelling in SpellingPreference.entries) {
            val allNames = definition(
                keys = emptyList(),
                tonalities = Tonality.entries,
                answerFormat = DegreeAnswerFormat.NOTE_NAME,
                spelling = spelling,
                minimum = MidiNote.of(0),
                maximum = MidiNote.of(127)
            )
            for (keyValue in 0..11) {
                for (tonality in Tonality.entries) {
                    for (degree in 1..7) {
                        val tokens = allNames.answerTokensFor(keyValue, tonality, degree)
                        assertEquals(1, tokens.size)
                        val id = tokens.single().value
                        assertTrue(
                            "a note name must never need a double accidental: $id",
                            !id.contains("double")
                        )
                        assertTrue(
                            "the token must exist in the vocabulary: $id",
                            VoiceVocabulary.ofIdOrNull(tokens.single()) != null
                        )
                        assertTrue(id.startsWith("note."))
                    }
                }
            }
        }
    }

    @Test
    fun targetNotesAndConstructionsUseThePlayableRange() {
        val default = ScaleDegreeDefinition.default()
        assertEquals(
            "the definition enumerates every playable octave of the tonic",
            listOf(24, 36, 48, 60, 72, 84, 96, 108),
            default.targetNotesFor(0, Tonality.MAJOR, 1).map { it.value }
        )

        val tonicNote = definition(context = ContextKind.TONIC_NOTE)
        assertEquals(
            "both root-range anchors fit a one-note context",
            listOf(60, 72),
            tonicNote.eligibleConstructions(0, Tonality.MAJOR, 1).map { it.anchor.value }
        )

        val triad = definition(context = ContextKind.TONIC_TRIAD)
        assertEquals(
            "both root-range anchors can extend their context above the root",
            listOf(60, 72),
            triad.eligibleConstructions(0, Tonality.MAJOR, 1).map { it.anchor.value }
        )
        assertTrue(triad.supports(0, Tonality.MAJOR, 1))
        assertTrue(triad.supports(6, Tonality.MAJOR, 1))

        val defaultCadence = definition(context = ContextKind.TONIC_DOMINANT_TONIC)
        assertTrue(
            "I-V-I can extend above the root range",
            defaultCadence.supports(0, Tonality.MAJOR, 1)
        )
        val roomy = definition(
            context = ContextKind.TONIC_DOMINANT_TONIC,
            maximum = MidiNote.of(74)
        )
        val constructions = roomy.eligibleConstructions(0, Tonality.MAJOR, 1)
        assertTrue(constructions.isNotEmpty())
        assertTrue(
            "built constructions are context groups",
            constructions.all { construction ->
                construction.groups.all { it.role == GroupRole.CONTEXT }
            }
        )
    }

    @Test
    fun equalityTracksEveryField() {
        val base = definition()
        assertEquals(base, definition())
        assertNotEquals(base, definition(context = ContextKind.TONIC_NOTE))
        assertNotEquals(base, definition(gap = 600.milliseconds))
        assertNotEquals(base, definition(spelling = SpellingPreference.FLAT))
        assertNotEquals(base, definition(minimum = MidiNote.of(59)))
        assertNotEquals(
            base,
            definition(answerFormat = DegreeAnswerFormat.NOTE_NAME)
        )
    }
}
