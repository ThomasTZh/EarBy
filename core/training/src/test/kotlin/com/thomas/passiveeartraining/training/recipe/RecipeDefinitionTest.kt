package com.thomas.passiveeartraining.training.recipe

import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.training.AnswerStartedEvent
import com.thomas.passiveeartraining.training.IntervalDefinition
import com.thomas.passiveeartraining.training.IntervalExercise
import com.thomas.passiveeartraining.training.MapVoiceDurationCatalog
import com.thomas.passiveeartraining.training.NoteEvent
import com.thomas.passiveeartraining.training.RecognitionRecipe
import com.thomas.passiveeartraining.training.SeededIntervalGenerator
import com.thomas.passiveeartraining.training.SeededRandomSource
import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.VoiceEvent
import kotlin.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private val CATALOG = MapVoiceDurationCatalog(
    IntervalDefinition.default().selectedIntervalIds.associate { id ->
        VoiceAssetId("interval.$id") to Duration.parse("900ms")
    }
)

private fun exercise(seed: Long = 42L): IntervalExercise =
    SeededIntervalGenerator(IntervalDefinition.default(), SeededRandomSource(seed)).next()

/** A concrete exercise whose interval is not a unison, so it has two distinct sounding notes. */
private fun nonUnisonExercise(
    presentation: IntervalPresentation = IntervalPresentation.ASCENDING
): IntervalExercise {
    val base = IntervalDefinition.default()
    val definition = IntervalDefinition.of(
        intervalIds = base.selectedIntervalIds,
        rootValues = base.rootValues,
        minimumNote = base.minimumNote,
        maximumNote = base.maximumNote,
        presentation = presentation
    )
    val generator = SeededIntervalGenerator(definition, SeededRandomSource(42L))
    return generateSequence { generator.next() }.first { it.interval.semitones != 0 }
}

class RecipeDefinitionTest {

    private val compiler = RecipeExerciseCompiler(CATALOG)

    @Test
    fun everyModeCompilesInsideTheCap() {
        RecipeMode.entries.forEach { mode ->
            val timeline = compiler.compile(exercise(), RecipeDefinition.of(mode))
            assertTrue(
                "$mode must stay inside the ${RecognitionRecipe.EXERCISE_CAP} cap",
                timeline.totalDuration <= RecognitionRecipe.EXERCISE_CAP
            )
            assertTrue("$mode must produce sound", timeline.notes.isNotEmpty())
        }
    }

    @Test
    fun theTemplatesMatchTheRoadmapOrder() {
        assertEquals(
            listOf(
                RecipeStep.Example::class,
                RecipeStep.Thinking::class,
                RecipeStep.Answer::class,
                RecipeStep.FinalSilence::class
            ),
            RecipeDefinition.of(RecipeMode.RECOGNITION).steps.map { it::class }
        )
        assertEquals(
            listOf(
                RecipeStep.Answer::class,
                RecipeStep.Gap::class,
                RecipeStep.Example::class,
                RecipeStep.FinalSilence::class
            ),
            RecipeDefinition.of(RecipeMode.EXPOSURE).steps.map { it::class }
        )
        assertEquals(
            listOf(
                RecipeStep.Example::class,
                RecipeStep.Thinking::class,
                RecipeStep.Answer::class,
                RecipeStep.Gap::class,
                RecipeStep.Example::class,
                RecipeStep.FinalSilence::class
            ),
            RecipeDefinition.of(RecipeMode.ASSOCIATION).steps.map { it::class }
        )
    }

    @Test
    fun theFinalSilenceOccursExactlyOncePerExercise() {
        RecipeMode.entries.forEach { mode ->
            val steps = RecipeDefinition(mode = mode, replayCount = 3).steps
            assertEquals(
                "$mode must have one final silence",
                1,
                steps.count { it is RecipeStep.FinalSilence }
            )
            assertTrue("$mode must end with it", steps.last() is RecipeStep.FinalSilence)
        }
    }

    @Test
    fun theAnswerIsSpokenExactlyOnceInEveryTemplate() {
        RecipeMode.entries.forEach { mode ->
            val timeline = compiler.compile(exercise(), RecipeDefinition.of(mode))
            assertEquals("$mode must speak the answer once", 1, timeline.voices.size)
        }
    }

    @Test
    fun onlyTemplatesThatPlaceExamplesFirstStartWithNotes() {
        val exposure = compiler.compile(exercise(), RecipeDefinition.of(RecipeMode.EXPOSURE))
        assertTrue(
            "exposure must start with the answer",
            exposure.orderedEvents.first { it is VoiceEvent || it is NoteEvent } is VoiceEvent
        )
        listOf(RecipeMode.RECOGNITION, RecipeMode.ASSOCIATION)
            .forEach { mode ->
                val timeline = compiler.compile(exercise(), RecipeDefinition.of(mode))
                assertTrue(
                    "$mode must start with an example",
                    timeline.orderedEvents.first {
                        it is VoiceEvent || it is NoteEvent
                    } is NoteEvent
                )
            }
    }

    @Test
    fun associationSpeaksTheAnswerAfterTheThinkingPause() {
        // The owner reported that the thinking-time control did nothing in association mode. The
        // voice must start after the example's two notes and the configured thinking silence.
        val thinking = Duration.parse("3s")
        val recipe = RecipeDefinition(
            mode = RecipeMode.ASSOCIATION,
            timing = RecipeTiming(
                thinkingSilence = thinking,
                // Distinct from the thinking value, so the assertion cannot pass on the wrong gap.
                answerGap = Duration.parse("1s")
            )
        )
        val timeline = compiler.compile(exercise(), recipe)
        val voice = timeline.voices.single()
        val example = RecipeStep.Example.duration(
            CATALOG,
            recipe.timing,
            exercise().presentation
        )
        assertEquals(
            "the answer starts after the example and the thinking pause",
            example + thinking,
            voice.startOffset
        )
    }

    @Test
    fun theSeparatorCueSitsInTheMiddleOfTheFinalSilence() {
        val plainRecipe = RecipeDefinition.of(RecipeMode.RECOGNITION)
        val cueRecipe = RecipeDefinition(mode = RecipeMode.RECOGNITION, separatorCue = true)
        val without = compiler.compile(exercise(), plainRecipe)
        val with = compiler.compile(exercise(), cueRecipe)
        val separatorNotes = with.notes.takeLast(2)
        assertEquals(
            "the two marker notes are the fixed high pair",
            RecipeStep.Separator.NOTE_VALUES,
            separatorNotes.map { it.note.value }
        )
        val half = plainRecipe.timing.postAnswerSilence / 2
        val bodyEnd = without.totalDuration - plainRecipe.timing.postAnswerSilence
        assertEquals(
            "the marker starts halfway through the final silence",
            bodyEnd + half,
            separatorNotes.first().startOffset
        )
        val markerEnd = separatorNotes.last().startOffset + RecipeStep.Separator.NOTE_DURATION
        assertEquals(
            "the other half of the silence follows the marker",
            half,
            with.totalDuration - markerEnd
        )
        assertEquals(
            "the cue adds exactly its own duration",
            without.totalDuration + RecipeStep.Separator.duration(
                CATALOG,
                cueRecipe.timing,
                exercise().presentation
            ),
            with.totalDuration
        )
    }

    @Test
    fun replayAddsExamplesWithoutAddingAnswersOrFinalSilences() {
        val base = compiler.compile(exercise(), RecipeDefinition.of(RecipeMode.RECOGNITION))
        val replayed = compiler.compile(
            exercise(),
            RecipeDefinition(mode = RecipeMode.RECOGNITION, replayCount = 4)
        )
        assertEquals("replay must not speak the answer again", 1, replayed.voices.size)
        assertTrue("replay must add examples", replayed.notes.size > base.notes.size)
        assertTrue("replay makes the exercise longer", replayed.totalDuration > base.totalDuration)
    }

    @Test
    fun associationRepeatsFollowTheAnswer() {
        val base = compiler.compile(exercise(), RecipeDefinition.of(RecipeMode.ASSOCIATION))
        val replayed = compiler.compile(
            exercise(),
            RecipeDefinition(mode = RecipeMode.ASSOCIATION, replayCount = 2)
        )
        assertEquals("repeats must not speak the answer again", 1, replayed.voices.size)
        assertEquals(
            "each repeat adds one example's notes",
            base.notes.size + 2 * exercise().notes.size,
            replayed.notes.size
        )
        assertTrue(
            "repeats make the exercise longer",
            replayed.totalDuration > base.totalDuration
        )
        // The extra examples follow the answer, so the last note comes after the voice ends.
        val voice = replayed.voices.single()
        assertTrue(
            "a repeated example must follow the answer",
            replayed.notes.maxOf { it.startOffset } > voice.startOffset
        )
    }

    @Test
    fun replayBoundsAreEnforced() {
        assertThrows(IllegalArgumentException::class.java) {
            RecipeDefinition(mode = RecipeMode.RECOGNITION, replayCount = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecipeDefinition(mode = RecipeMode.RECOGNITION, replayCount = 9)
        }
        RecipeDefinition(mode = RecipeMode.RECOGNITION, replayCount = 8)
    }

    @Test
    fun anOverlongRecipeIsRejectedWithItsMeasuredDuration() {
        // The longest replay the bounds allow still fits, so the cap is exercised with longer notes:
        // the failure then names both the measured length and the bound it broke.
        val longNotes = RecipeDefinition(
            mode = RecipeMode.RECOGNITION,
            timing = RecipeTiming(noteDuration = Duration.parse("20s")),
            replayCount = 4
        )
        val error = assertThrows(RecipeValidationException::class.java) {
            compiler.compile(exercise(), longNotes)
        }
        assertTrue("the failure must name the cap", error.message!!.contains("cap"))
        assertTrue("the failure must report the measured length", error.message!!.contains("ms"))

        // The maximum replay count the bounds allow is still inside the cap, which is what keeps the
        // product's longest recipe playable.
        val longest =
            RecipeDefinition(
                mode = RecipeMode.RECOGNITION,
                replayCount = RecipeDefinition.MAX_REPLAY
            )
        assertTrue(
            "the longest allowed replay must still fit",
            compiler.previewDuration(
                longest,
                exercise().answerId
            ) <= RecognitionRecipe.EXERCISE_CAP
        )
    }

    @Test
    fun thePreviewMatchesTheCompiledDuration() {
        RecipeMode.entries.forEach { mode ->
            val definition = RecipeDefinition(mode = mode, replayCount = 1)
            val compilable = exercise()
            val timeline = compiler.compile(compilable, definition)
            assertEquals(
                "$mode preview must match the compiled duration",
                timeline.totalDuration,
                compiler.previewDuration(definition, compilable.answerId)
            )
        }
    }

    @Test
    fun aMelodicExampleSeparatesItsNotesAndFitsInsideItsOwnStep() {
        val timeline = compiler.compile(
            nonUnisonExercise(),
            RecipeDefinition.of(RecipeMode.RECOGNITION)
        )
        val notes = timeline.notes
        assertEquals(2, notes.size)
        val noteDuration = Duration.parse("700ms")
        val melodicGap = Duration.parse("100ms")
        assertEquals(Duration.ZERO, notes[0].startOffset)
        assertEquals(
            "the second note follows the first after the melodic gap",
            noteDuration + melodicGap,
            notes[1].startOffset
        )
        val secondNoteEnd = notes[1].startOffset + notes[1].duration
        val answer = timeline.voices.single()
        assertTrue(
            "the thinking silence must start after the example has finished",
            answer.startOffset >= secondNoteEnd
        )
    }

    @Test
    fun aHarmonicExampleSoundsBothNotesTogether() {
        val timeline = compiler.compile(
            nonUnisonExercise(IntervalPresentation.HARMONIC),
            RecipeDefinition.of(RecipeMode.RECOGNITION)
        )
        val notes = timeline.notes
        assertEquals(2, notes.size)
        assertEquals(
            "a harmonic example has no melodic offset",
            notes[0].startOffset,
            notes[1].startOffset
        )
    }

    @Test
    fun everyTemplateEmitsExactlyOneAnswerMarkerAtItsAnswer() {
        RecipeMode.entries.forEach { mode ->
            val timeline = compiler.compile(exercise(), RecipeDefinition.of(mode))
            val markers = timeline.events.filterIsInstance<AnswerStartedEvent>()
            assertEquals("$mode must mark its answer exactly once", 1, markers.size)
            assertEquals(
                "$mode's marker is at the spoken answer",
                timeline.voices.single().startOffset,
                markers.single().startOffset
            )
        }
    }

    @Test
    fun mixedPresentationIsSortedAndVersionedSeparatelyFromV1() {
        val v1 = SequencePolicy(IntervalDefinition.default())
        assertEquals("the original draw order is version 1", 1, v1.generatorVersion)

        val v2 = SequencePolicy(
            definition = IntervalDefinition.default(),
            presentations = listOf(IntervalPresentation.ASCENDING, IntervalPresentation.DESCENDING),
            mixedPresentation = true
        )
        assertEquals("mixed presentation is version 2", 2, v2.generatorVersion)
        assertThrows(IllegalArgumentException::class.java) {
            SequencePolicy(
                definition = IntervalDefinition.default(),
                presentations = listOf(
                    IntervalPresentation.DESCENDING,
                    IntervalPresentation.ASCENDING
                ),
                mixedPresentation = true
            )
        }
    }

    @Test
    fun aFixedRootMustIntersectTheSelectedClassesAndRange() {
        val definition = IntervalDefinition.default()
        val inside = definition.noteRange.first { value ->
            definition.selectedRoots.any { it == MidiNote.of(value).pitchClass }
        }
        SequencePolicy(definition = definition, fixedRoot = MidiNote.of(inside))
        assertThrows(IllegalArgumentException::class.java) {
            SequencePolicy(
                definition = definition,
                fixedRoot = MidiNote.of(definition.noteRange.last + 1)
            )
        }
    }

    @Test
    fun anEmptyPresentationSetIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SequencePolicy(definition = IntervalDefinition.default(), presentations = emptyList())
        }
    }

    @Test
    fun recipesAreReproducibleForTheSameSeed() {
        val first = compiler.compile(exercise(7L), RecipeDefinition.of(RecipeMode.ASSOCIATION))
        val second = compiler.compile(exercise(7L), RecipeDefinition.of(RecipeMode.ASSOCIATION))
        assertEquals("the same seed compiles the same timeline", first, second)
        assertNotEquals(
            "a different seed changes the exercise",
            first,
            compiler.compile(exercise(8L), RecipeDefinition.of(RecipeMode.ASSOCIATION))
        )
    }
}
