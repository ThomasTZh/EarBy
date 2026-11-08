package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.MidiNote
import kotlin.time.Duration

/** One event of a compiled exercise, positioned on a relative timeline that starts at zero. */
sealed interface TimelineEvent {
    /** Offset from the start of the exercise. */
    val startOffset: Duration

    /** Length of the audible window, or `null` for a boundary marker. */
    val duration: Duration?

    /** End offset of the half-open window `[startOffset, endOffset)`; equal to the start for markers. */
    val endOffset: Duration

    /** Stable ordering rank: notes first, then the voice, then markers. */
    val orderRank: Int
}

/** An audible note. Simultaneous harmonic notes share [startOffset]. */
data class NoteEvent(
    override val startOffset: Duration,
    override val duration: Duration,
    val note: MidiNote,
    /** True for the fixed two-note separator cue, which is rendered from its own clip. */
    val separator: Boolean = false
) : TimelineEvent {
    override val endOffset: Duration get() = startOffset + duration
    override val orderRank: Int get() = 0

    override fun toString(): String = "NoteEvent($note @$startOffset for $duration)"
}

/** The spoken answer clip, referenced by semantic asset id only. */
data class VoiceEvent(
    override val startOffset: Duration,
    override val duration: Duration,
    val voiceId: VoiceAssetId
) : TimelineEvent {
    override val endOffset: Duration get() = startOffset + duration
    override val orderRank: Int get() = 1

    override fun toString(): String = "VoiceEvent($voiceId @$startOffset for $duration)"
}

/** The answer becomes visible only when this frame is consumed; it is never a wake-up timer. */
data class AnswerStartedEvent(override val startOffset: Duration) : TimelineEvent {
    override val duration: Duration? get() = null
    override val endOffset: Duration get() = startOffset
    override val orderRank: Int get() = 2

    override fun toString(): String = "AnswerStartedEvent(@$startOffset)"
}

/** The end boundary of the exercise; it has no following duration. */
data class ExerciseEndedEvent(override val startOffset: Duration) : TimelineEvent {
    override val duration: Duration? get() = null
    override val endOffset: Duration get() = startOffset
    override val orderRank: Int get() = 3

    override fun toString(): String = "ExerciseEndedEvent(@$startOffset)"
}

/**
 * Immutable compiled exercise.
 *
 * Silence is a gap between events rather than a scheduled event, and every window is half-open:
 * consumers process a window that ends at frame `n` before starting a window that begins there.
 */
class Timeline internal constructor(val events: List<TimelineEvent>, val totalDuration: Duration) {
    /** Events in stable play order. */
    val orderedEvents: List<TimelineEvent> = events

    val notes: List<NoteEvent> get() = events.filterIsInstance<NoteEvent>()
    val voices: List<VoiceEvent> get() = events.filterIsInstance<VoiceEvent>()

    override fun equals(other: Any?): Boolean = other is Timeline &&
        events == other.events &&
        totalDuration == other.totalDuration

    override fun hashCode(): Int = 31 * events.hashCode() + totalDuration.hashCode()

    override fun toString(): String = "Timeline(total=$totalDuration, events=$events)"
}

/** Raised when recipe timing, voice durations or the exercise cap are not satisfiable. */
class InvalidTimelineException(message: String) : IllegalArgumentException(message)
