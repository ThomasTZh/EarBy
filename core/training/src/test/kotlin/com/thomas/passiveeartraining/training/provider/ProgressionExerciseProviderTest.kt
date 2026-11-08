package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.music.DiatonicChord
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.ProgressionVoicingMode
import com.thomas.passiveeartraining.music.SpellingPreference
import com.thomas.passiveeartraining.training.MapVoiceDurationCatalog
import com.thomas.passiveeartraining.training.ProgressionAnswerFormat
import com.thomas.passiveeartraining.training.ProgressionDefinition
import com.thomas.passiveeartraining.training.VoiceAssetId
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M9B: four-chord draws from the selected pool, the tonic opening, the fixed root and answers.
 */
class ProgressionExerciseProviderTest {

    private val provider = ProgressionExerciseProvider()

    private fun definition(
        chords: List<DiatonicChord> = DiatonicChord.TRIADS,
        fixedRoot: MidiNote? = null,
        answerFormat: ProgressionAnswerFormat = ProgressionAnswerFormat.ROMAN,
        minimumNote: MidiNote = MidiNote.of(48),
        maximumNote: MidiNote = MidiNote.of(84)
    ): ProgressionDefinition = ProgressionDefinition.of(
        chords = chords,
        fixedRoot = fixedRoot,
        voicing = ProgressionVoicingMode.ROOT_POSITION,
        allowedInversions = listOf(0, 1, 2, 3),
        answerFormat = answerFormat,
        spelling = SpellingPreference.SHARP,
        minimumNote = minimumNote,
        maximumNote = maximumNote
    )

    private fun catalog(excluded: VoiceAssetId? = null): MapVoiceDurationCatalog =
        MapVoiceDurationCatalog(
            VoiceVocabulary.entries
                .map { it.id }
                .filter { it != excluded }
                .associateWith { 900.milliseconds }
        )

    private fun exercises(
        definition: ProgressionDefinition,
        seed: Long = 1L,
        count: Int = 20
    ): List<ConcreteExercise> {
        val generator = provider.createGenerator(
            ProviderConfig.Progressions(definition),
            seed
        )
        return List(count) { generator.next() }
    }

    private fun labels(exercise: ConcreteExercise): List<String> =
        exercise.provenance.generatedProgression!!.sequenceLabel.split(" ")

    @Test
    fun everyExercisePlaysFourChordsAndOpensOnI() {
        val pool = DiatonicChord.TRIADS + DiatonicChord.I7
        val drawn = exercises(definition(chords = pool), seed = 7L, count = 60)
        drawn.forEach { exercise ->
            assertEquals(4, exercise.targetGroups.size)
            assertEquals(4, exercise.provenance.generatedProgression!!.chords.size)
            assertEquals(4, labels(exercise).size)
            assertEquals(
                "the opening chord is always the plain tonic",
                "I",
                labels(exercise).first()
            )
            assertEquals("the mode has no label cycle", null, exercise.cycle)
            assertEquals("progression.roman", exercise.categoryId)
        }
        assertTrue(
            "the selected seventh chords still appear in the following slots",
            drawn.any { "Imaj7" in labels(it) }
        )
        assertTrue(drawn.none { labels(it).first() == "Imaj7" })
    }

    @Test
    fun chordsStayInTheSelectedPoolAndMayRepeat() {
        val drawn = exercises(
            definition(chords = listOf(DiatonicChord.I, DiatonicChord.V)),
            seed = 11L,
            count = 80
        )
        drawn.forEach { exercise ->
            val names = labels(exercise)
            assertTrue(
                "every drawn chord is selected: $names",
                names.all { it == "I" || it == "V" }
            )
            assertEquals("the opening chord is the tonic", "I", names.first())
            val tail = names.drop(1)
            assertTrue(
                "three draws from two chords must repeat one: $names",
                tail.size != tail.toSet().size
            )
        }
    }

    @Test
    fun aSeventhOnlyPoolStillOpensOnThePlainTriad() {
        val drawn = exercises(
            definition(chords = listOf(DiatonicChord.I7, DiatonicChord.V7)),
            seed = 5L,
            count = 20
        )
        drawn.forEach { exercise ->
            val names = labels(exercise)
            assertEquals("the opening is the plain I triad", "I", names.first())
            assertTrue(
                "the following slots only use the selected sevenths: $names",
                names.drop(1).all { it in setOf("Imaj7", "V7") }
            )
            assertEquals(
                "the opening triad sounds three notes",
                3,
                exercise.targetGroups.first().notes.size
            )
        }
    }

    @Test
    fun seventhChordsSoundFourNotes() {
        val drawn = exercises(
            definition(
                chords = listOf(
                    DiatonicChord.I7,
                    DiatonicChord.II7,
                    DiatonicChord.V7
                )
            ),
            seed = 3L,
            count = 20
        )
        drawn.forEach { exercise ->
            assertEquals(
                "the fixed opening I is a triad",
                3,
                exercise.targetGroups.first().notes.size
            )
            exercise.targetGroups.drop(1).forEach { group ->
                assertEquals("every drawn seventh has four notes", 4, group.notes.size)
            }
        }
    }

    @Test
    fun aFixedRootPinsEveryExercise() {
        val definition = definition(
            chords = listOf(
                DiatonicChord.I,
                DiatonicChord.V7,
                DiatonicChord.VI,
                DiatonicChord.IV7
            ),
            fixedRoot = MidiNote.of(60)
        )
        exercises(definition, seed = 5L, count = 20).forEach { exercise ->
            val generated = exercise.provenance.generatedProgression!!
            assertEquals(60, generated.anchorMidi)
            assertEquals(0, generated.keyPitchClass)
            assertTrue(
                generated.chords.all { chord ->
                    chord.notes.all { it.value in 48..84 }
                }
            )
        }
    }

    @Test
    fun theAnswerMatchesTheDrawnSequence() {
        exercises(definition(), seed = 9L, count = 10).forEach { exercise ->
            val generated = exercise.provenance.generatedProgression!!
            assertEquals(generated.sequenceLabel, exercise.answer.display.label())
            assertEquals("four chords, two tokens each", 8, exercise.answer.voiceIds.size)
        }
        val motion = exercises(
            definition(answerFormat = ProgressionAnswerFormat.ROOT_MOTION),
            seed = 9L,
            count = 3
        ).first()
        assertEquals("three motions, two tokens each", 6, motion.answer.voiceIds.size)
        assertEquals("progression.root_motion", motion.categoryId)
        val functions = exercises(
            definition(answerFormat = ProgressionAnswerFormat.FUNCTIONS),
            seed = 9L,
            count = 3
        ).first()
        assertEquals(4, functions.answer.voiceIds.size)
        assertEquals("progression.functions", functions.categoryId)
        assertTrue(
            functions.provenance.generatedProgression!!.sequenceLabel
                .split(" ").all { it in setOf("tonic", "predominant", "dominant") }
        )
    }

    @Test
    fun aMissingClipIsReportedAgainstAnswers() {
        val config = ProviderConfig.Progressions(
            definition(chords = listOf(DiatonicChord.I, DiatonicChord.V7))
        )
        val validation = provider.validate(
            config,
            catalog(excluded = VoiceAssetId("chord.dominant_seventh"))
        )
        assertTrue(validation is ProviderValidation.Invalid)
        assertTrue(
            (validation as ProviderValidation.Invalid).issues.any { it.field == "answers" }
        )
    }

    @Test
    fun everyKeyWhoseChordsFitThePlayableRangeIsPresent() {
        val definition = definition(
            chords = listOf(DiatonicChord.I, DiatonicChord.V),
            minimumNote = MidiNote.of(60),
            maximumNote = MidiNote.of(74)
        )
        val validation = provider.validate(ProviderConfig.Progressions(definition), catalog())
        assertTrue(
            "the setup is valid: $validation",
            validation is ProviderValidation.Valid
        )
        val placements = definition.feasiblePlacements()
        assertTrue(placements.isNotEmpty())
        assertTrue(
            "C sharp fits its V chord inside A0-C8 and remains drawable",
            placements.any { it.keyValue == 1 }
        )
    }

    @Test
    fun theExampleWindowCoversFourChords() {
        assertEquals(
            4 * 700 + 3 * 100,
            ProgressionExerciseProvider.maximumExampleDuration(definition()).inWholeMilliseconds
        )
        val valid = provider.validate(
            ProviderConfig.Progressions(
                definition(chords = listOf(DiatonicChord.I7, DiatonicChord.V7))
            ),
            catalog()
        )
        assertTrue(valid is ProviderValidation.Valid)
        assertEquals(
            listOf("progression.roman"),
            (valid as ProviderValidation.Valid).categories.map { it.id }
        )
    }

    @Test
    fun aReconfigurationContinuesTheDrawStream() {
        val generator = provider.createGenerator(
            ProviderConfig.Progressions(definition()),
            4L
        )
        generator.next()
        generator.reconfigure(
            ProviderConfig.Progressions(
                definition(chords = listOf(DiatonicChord.I, DiatonicChord.V7))
            )
        )
        val next = generator.next()
        assertEquals(1, next.sequenceIndex)
        assertEquals("progression.roman", next.categoryId)
        assertEquals(4, labels(next).size)
    }
}
