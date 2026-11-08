package com.thomas.passiveeartraining.training.recipe

import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.training.AnswerStartedEvent
import com.thomas.passiveeartraining.training.ExerciseEndedEvent
import com.thomas.passiveeartraining.training.IntervalExercise
import com.thomas.passiveeartraining.training.NoteEvent
import com.thomas.passiveeartraining.training.Timeline
import com.thomas.passiveeartraining.training.TimelineEvent
import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.VoiceDurationCatalog
import com.thomas.passiveeartraining.training.VoiceEvent
import kotlin.time.Duration

/**
 * Compiles a learning recipe into a timeline.
 *
 * Steps are placed in order with whole-microsecond precision, so a recipe is a schedule the session
 * can verify and preview rather than a sequence of one-off commands. The example layout comes from
 * the concrete exercise's presentation: a melodic example separates its notes by the recipe's
 * melodic gap, a harmonic one sounds them together. The final silence appears exactly once, at the
 * end, whichever template produced the steps.
 */
class RecipeExerciseCompiler(private val catalog: VoiceDurationCatalog) {
    /**
     * Compiles [exercise] under [definition].
     *
     * @throws RecipeValidationException when the recipe exceeds the one-exercise cap.
     */
    fun compile(exercise: IntervalExercise, definition: RecipeDefinition): Timeline {
        definition.validate(catalog, VoiceAssetId(exercise.answerId), exercise.presentation)
        val events = mutableListOf<TimelineEvent>()
        var cursor = Duration.ZERO

        definition.steps.forEach { step ->
            val duration = stepDuration(step, definition, exercise)
            when (step) {
                is RecipeStep.Example -> {
                    val noteDuration = definition.timing.noteDuration
                    val melodicStride = noteDuration + definition.timing.melodicGap
                    exercise.notes.forEachIndexed { index, note ->
                        // Harmonic notes share the start; melodic notes are separated by the
                        // stride. The exercise's own presentation decides, never the recipe.
                        val offset = if (exercise.presentation == IntervalPresentation.HARMONIC) {
                            cursor
                        } else {
                            cursor + melodicStride * index
                        }
                        events += NoteEvent(
                            startOffset = offset,
                            duration = noteDuration,
                            note = MidiNote.of(note.value)
                        )
                    }
                }
                is RecipeStep.Answer -> {
                    val voiceId =
                        step.voiceId(VoiceAssetId(exercise.answerId))
                            ?: VoiceAssetId(exercise.answerId)
                    // The marker and the voice event share one boundary: the answer begins there.
                    events += AnswerStartedEvent(cursor)
                    events +=
                        VoiceEvent(startOffset = cursor, duration = duration, voiceId = voiceId)
                }
                RecipeStep.Separator -> {
                    // Two fixed high notes announce a new pair; the marker is not the example.
                    val stride = RecipeStep.Separator.NOTE_DURATION +
                        RecipeStep.Separator.NOTE_GAP
                    RecipeStep.Separator.NOTE_VALUES.forEachIndexed { index, note ->
                        events += NoteEvent(
                            startOffset = cursor + stride * index,
                            duration = RecipeStep.Separator.NOTE_DURATION,
                            note = MidiNote.of(note),
                            separator = true
                        )
                    }
                }
                is RecipeStep.Thinking, is RecipeStep.Gap, RecipeStep.FinalSilence -> {
                    // Silences occupy timeline space and produce no event.
                }
            }
            cursor += duration
        }

        val total = cursor
        events += ExerciseEndedEvent(startOffset = total)
        return Timeline(
            events = events.sortedBy { it.startOffset },
            totalDuration = total
        )
    }

    /** The measured duration of one step; an answer uses its own clip length, never a placeholder. */
    private fun stepDuration(
        step: RecipeStep,
        definition: RecipeDefinition,
        exercise: IntervalExercise
    ): Duration = when (step) {
        is RecipeStep.Answer -> catalog.durationOf(VoiceAssetId(exercise.answerId))
        else -> step.duration(catalog, definition.timing, exercise.presentation)
    }

    /**
     * Previews the total duration without compiling, so a screen can show the cost of a recipe
     * before it is played, and validation can report the same number it measured.
     */
    fun previewDuration(
        definition: RecipeDefinition,
        answerId: String,
        presentation: IntervalPresentation = IntervalPresentation.ASCENDING
    ): Duration = definition.totalDuration(catalog, VoiceAssetId(answerId), presentation)
}
