package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.training.Timeline
import kotlin.time.Duration

/** A half-open span `[start, endExclusive)` on a compiled timeline. */
data class OpenRange(val start: Duration, val endExclusive: Duration) {
    init {
        require(endExclusive >= start) { "A range end must not precede its start: $this" }
    }

    /** The width of the span. */
    val duration: Duration get() = endExclusive - start

    /** True when [position] lies inside the half-open span. */
    fun contains(position: Duration): Boolean = position >= start && position < endExclusive
}

/**
 * One compiled exercise: the timeline plus the switching boundaries a session needs (SC2).
 *
 * All offsets are on the same relative timeline as [timeline], starting at zero. The mandatory
 * learning unit ends at [mandatoryEnd]; each optional replay is its own range; the trailing
 * silence or separator cue starts at [trailingStart]. Frame conversion happens in the audio
 * adapter, which owns the sample rate (M3.2).
 */
data class CompiledExercise(
    val timeline: Timeline,
    /** Offset of the composed answer's first token. */
    val answerStart: Duration,
    /** End of the mandatory learning unit; a switch may cut the timeline here. */
    val mandatoryEnd: Duration,
    /** Each optional replay unit, including its leading gap. */
    val replays: List<OpenRange>,
    /** Start of the trailing silence or separator cue after the mandatory unit and replays. */
    val trailingStart: Duration
) {
    /** Total compiled length; identical to the timeline's own total. */
    val total: Duration get() = timeline.totalDuration
}
