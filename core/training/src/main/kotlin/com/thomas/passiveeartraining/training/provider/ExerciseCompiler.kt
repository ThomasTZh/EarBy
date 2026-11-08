package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.training.AnswerStartedEvent
import com.thomas.passiveeartraining.training.ExerciseEndedEvent
import com.thomas.passiveeartraining.training.NoteEvent
import com.thomas.passiveeartraining.training.Timeline
import com.thomas.passiveeartraining.training.TimelineEvent
import com.thomas.passiveeartraining.training.VoiceDurationCatalog
import com.thomas.passiveeartraining.training.VoiceEvent
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeStep
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import kotlin.time.Duration

/**
 * Compiles a concrete exercise under a learning recipe into one timeline plus switching
 * boundaries.
 *
 * Timing comes from the recipe, never from the provider: group lists describe a phrase shape
 * (sequential slots, simultaneous notes, rest slots), and the compiler schedules that shape with
 * the configured note length and sequential gap. Context and target sections are scheduled
 * independently with the context gap between them, which is how every later provider gets its
 * context-in-every-Example behavior without a second compiler (SC4/M1.3).
 */
class ExerciseCompiler {

    /**
     * Compiles [exercise] under [definition].
     *
     * @throws com.thomas.passiveeartraining.training.recipe.RecipeValidationException when the
     *   complete exercise exceeds the product cap.
     * @throws com.thomas.passiveeartraining.training.MissingVoiceDurationException when a composed
     *   answer token has no measured duration.
     */
    fun compile(
        exercise: ConcreteExercise,
        definition: RecipeDefinition,
        catalog: VoiceDurationCatalog
    ): CompiledExercise {
        val timing = definition.timing
        val steps = definition.steps
        val mandatoryStepCount = definition.mode.mandatoryStepCount

        // Optional replays are the (Gap, Example) pairs after the mandatory unit and before the
        // trailing silence/separator tail. Classifying first keeps boundary capture exact.
        val replayGapIndices = mutableSetOf<Int>()
        var scan = mandatoryStepCount
        while (scan + 1 < steps.size &&
            steps[scan] is RecipeStep.Gap &&
            steps[scan + 1] is RecipeStep.Example
        ) {
            replayGapIndices += scan
            scan += 2
        }
        val tailStartIndex = scan

        val events = mutableListOf<TimelineEvent>()
        var cursor = Duration.ZERO
        var answerStart: Duration? = null
        var mandatoryEnd: Duration? = null
        var trailingStart: Duration? = null
        val replays = mutableListOf<OpenRange>()
        var pendingReplayStart: Duration? = null
        var previousWasReplayGap = false

        steps.forEachIndexed { index, step ->
            if (index == tailStartIndex) trailingStart = cursor
            val stepStart = cursor
            when (step) {
                is RecipeStep.Example -> {
                    cursor += placeExample(exercise, timing, cursor, events)
                    if (previousWasReplayGap) {
                        val start = pendingReplayStart
                            ?: throw IllegalStateException("A replay example has no leading gap")
                        replays += OpenRange(start, cursor)
                        pendingReplayStart = null
                        previousWasReplayGap = false
                    }
                }
                is RecipeStep.Answer -> {
                    if (answerStart == null) answerStart = cursor
                    cursor += placeAnswer(exercise.answer, cursor, events, catalog)
                }
                is RecipeStep.Gap -> {
                    if (index in replayGapIndices) {
                        pendingReplayStart = stepStart
                        previousWasReplayGap = true
                    }
                    cursor += step.gap
                }
                RecipeStep.Thinking -> cursor += timing.thinkingSilence
                RecipeStep.FinalSilence -> cursor += timing.postAnswerSilence
                RecipeStep.Separator -> {
                    placeSeparator(cursor, events)
                    cursor += RecipeStep.Separator.NOTE_DURATION * 2 + RecipeStep.Separator.NOTE_GAP
                }
            }
            if (index == mandatoryStepCount - 1) mandatoryEnd = cursor
        }

        val total = cursor
        events += ExerciseEndedEvent(startOffset = total)
        val timeline = Timeline(events = events.sortedBy { it.startOffset }, totalDuration = total)
        val compiled = CompiledExercise(
            timeline = timeline,
            answerStart = answerStart ?: Duration.ZERO,
            mandatoryEnd = mandatoryEnd ?: total,
            replays = replays,
            trailingStart = trailingStart ?: total
        )
        if (total > com.thomas.passiveeartraining.training.RecognitionRecipe.EXERCISE_CAP) {
            throw com.thomas.passiveeartraining.training.recipe.RecipeValidationException(
                "${definition.mode} lasts ${total.inWholeMilliseconds} ms, above the " +
                    "${com.thomas.passiveeartraining.training.RecognitionRecipe.EXERCISE_CAP
                        .inWholeSeconds} s cap for one exercise"
            )
        }
        return compiled
    }

    /**
     * Total compiled length of [exercise] under [definition], without retaining the timeline.
     *
     * Used by validation so the number it checks is produced by the same code that compiles.
     */
    fun durationOf(
        exercise: ConcreteExercise,
        definition: RecipeDefinition,
        catalog: VoiceDurationCatalog
    ): Duration = compile(exercise, definition, catalog).total

    private fun placeExample(
        exercise: ConcreteExercise,
        timing: RecipeTiming,
        start: Duration,
        events: MutableList<TimelineEvent>
    ): Duration {
        var offset = start
        val context = exercise.contextGroups
        val target = exercise.targetGroups
        if (context.isNotEmpty()) {
            placeSection(context, timing, offset, events)
            offset += sectionDuration(context.size, timing) + timing.contextGap
        }
        placeSection(target, timing, offset, events)
        offset += sectionDuration(target.size, timing)
        return offset - start
    }

    private fun placeSection(
        groups: List<NoteGroup>,
        timing: RecipeTiming,
        start: Duration,
        events: MutableList<TimelineEvent>
    ) {
        val stride = stride(timing)
        groups.forEachIndexed { index, group ->
            val groupStart = start + stride * index
            group.notes.forEach { note ->
                events += NoteEvent(
                    startOffset = groupStart,
                    duration = timing.noteDuration,
                    note = note
                )
            }
        }
    }

    private fun placeAnswer(
        answer: AnswerDescriptor,
        start: Duration,
        events: MutableList<TimelineEvent>,
        catalog: VoiceDurationCatalog
    ): Duration {
        var offset = start
        answer.voiceIds.forEachIndexed { index, voiceId ->
            if (index > 0) offset += TOKEN_GAP
            if (index == 0) events += AnswerStartedEvent(offset)
            val duration = catalog.durationOf(voiceId)
            events += VoiceEvent(startOffset = offset, duration = duration, voiceId = voiceId)
            offset += duration
        }
        return offset - start
    }

    private fun placeSeparator(start: Duration, events: MutableList<TimelineEvent>) {
        val stride = RecipeStep.Separator.NOTE_DURATION + RecipeStep.Separator.NOTE_GAP
        RecipeStep.Separator.NOTE_VALUES.forEachIndexed { index, value ->
            events += NoteEvent(
                startOffset = start + stride * index,
                duration = RecipeStep.Separator.NOTE_DURATION,
                note = MidiNote.of(value),
                separator = true
            )
        }
    }

    private fun sectionDuration(count: Int, timing: RecipeTiming): Duration =
        if (count == 0) Duration.ZERO else stride(timing) * (count - 1) + timing.noteDuration

    private fun stride(timing: RecipeTiming): Duration = timing.noteDuration + timing.melodicGap

    companion object {
        /** Exact gap between composed answer tokens (SC4). */
        val TOKEN_GAP: Duration = Duration.parse("80ms")
    }
}
