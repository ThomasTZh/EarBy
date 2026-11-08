package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.MidiNote
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds

/**
 * Deterministic, pure compiler for one recognition exercise.
 *
 * Recognition order is example, thinking silence, spoken answer, final silence. Every duration
 * is validated before any arithmetic, so Kotlin's saturating `Duration` operations can never
 * turn an overflow into an accepted infinite timeline.
 */
class TimelineCompiler(
    private val recipe: RecognitionRecipe,
    private val voiceDurations: VoiceDurationCatalog
) {

    /** Compiles the concrete notes of [exercise] using its own presentation. */
    fun compile(exercise: IntervalExercise): Timeline = compile(
        root = exercise.root,
        target = exercise.target,
        presentation = exercise.presentation,
        answerVoiceId = VoiceAssetId(exercise.answerId)
    )

    /**
     * Compiles a recognition timeline.
     *
     * @throws InvalidTimelineException when a duration is nonfinite, negative, finer than one
     *   microsecond, or when the compiled exercise would exceed the 60-second cap.
     * @throws MissingVoiceDurationException when the answer clip has no measured duration.
     */
    fun compile(
        root: MidiNote,
        target: MidiNote,
        presentation: IntervalPresentation,
        answerVoiceId: VoiceAssetId
    ): Timeline {
        val noteDuration = recipe.noteDuration.requireWholeMicroseconds(
            "note duration",
            mustBePositive = true
        )
        val melodicGap = recipe.melodicGap.requireWholeMicroseconds(
            "melodic gap",
            mustBePositive = false
        )
        val thinkingSilence =
            recipe.thinkingSilence.requireWholeMicroseconds(
                "thinking silence",
                mustBePositive = false
            )
        val postAnswerSilence =
            recipe.postAnswerSilence.requireWholeMicroseconds(
                "post-answer silence",
                mustBePositive = false
            )
        val answerVoiceDuration = voiceDurations.durationOf(answerVoiceId)
            .requireWholeMicroseconds("voice duration for '$answerVoiceId'", mustBePositive = true)

        // Ascending C4 -> G4 and harmonic C4/G4 share these boundaries: a melodic example lasts
        // 2 x noteDuration + melodicGap, a harmonic example only noteDuration.
        val melodicStart = noteDuration + melodicGap
        val exampleEnd = when (presentation) {
            IntervalPresentation.ASCENDING, IntervalPresentation.DESCENDING ->
                melodicStart +
                    noteDuration
            IntervalPresentation.HARMONIC -> noteDuration
        }
        val answerStart = exampleEnd + thinkingSilence
        val end = answerStart + answerVoiceDuration + postAnswerSilence
        if (end > RecognitionRecipe.EXERCISE_CAP) {
            throw InvalidTimelineException(
                "Compiled exercise lasts $end, which exceeds the ${RecognitionRecipe.EXERCISE_CAP} cap"
            )
        }

        val notes = notesFor(root, target, presentation, noteDuration, melodicStart)
        val events: List<TimelineEvent> = notes +
            VoiceEvent(answerStart, answerVoiceDuration, answerVoiceId) +
            AnswerStartedEvent(answerStart) +
            ExerciseEndedEvent(end)

        return Timeline(
            events = events.sortedWith(
                compareBy(
                    { it.startOffset },
                    { it.orderRank },
                    { (it as? NoteEvent)?.note?.value ?: 0 }
                )
            ),
            totalDuration = end
        )
    }

    private fun notesFor(
        root: MidiNote,
        target: MidiNote,
        presentation: IntervalPresentation,
        noteDuration: Duration,
        melodicStart: Duration
    ): List<NoteEvent> = when (presentation) {
        IntervalPresentation.ASCENDING, IntervalPresentation.DESCENDING -> listOf(
            NoteEvent(Duration.ZERO, noteDuration, root),
            NoteEvent(melodicStart, noteDuration, target)
        )

        IntervalPresentation.HARMONIC -> {
            // Simultaneous notes start together and are sorted by sounding value; a unison
            // produces one audible note rather than two copies of the same pitch.
            if (target == root) {
                listOf(NoteEvent(Duration.ZERO, noteDuration, root))
            } else {
                val lower = if (root.value <= target.value) root else target
                val upper = if (root.value <= target.value) target else root
                listOf(
                    NoteEvent(Duration.ZERO, noteDuration, lower),
                    NoteEvent(Duration.ZERO, noteDuration, upper)
                )
            }
        }
    }

    private fun Duration.requireWholeMicroseconds(
        label: String,
        mustBePositive: Boolean
    ): Duration {
        if (!isFinite()) {
            throw InvalidTimelineException("$label must be finite but was $this")
        }
        if (isNegative()) {
            throw InvalidTimelineException("$label must not be negative but was $this")
        }
        if (inWholeNanoseconds % NANOS_PER_MICROSECOND != 0L) {
            throw InvalidTimelineException("$label must use whole microseconds but was $this")
        }
        if (mustBePositive && this <= Duration.ZERO) {
            throw InvalidTimelineException("$label must be positive but was $this")
        }
        return inWholeMicroseconds.microseconds
    }

    private companion object {
        const val NANOS_PER_MICROSECOND: Long = 1_000L
    }
}
