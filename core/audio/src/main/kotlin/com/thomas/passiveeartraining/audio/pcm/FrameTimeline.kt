package com.thomas.passiveeartraining.audio.pcm

import kotlin.time.Duration

/**
 * Converts timeline offsets to sample frames.
 *
 * The domain works in whole microseconds; the renderer works in frames. Absolute offsets are
 * converted one by one with integer arithmetic and half-up rounding, never by adding up
 * per-segment rounded deltas, so a long timeline cannot accumulate drift. The multiplication is
 * range-checked before it happens, because a saturated conversion would silently move an event.
 */
object FrameConverter {

    /** Frames for an absolute offset at [sampleRate], rounded half up. */
    fun framesAt(offset: Duration, sampleRate: Int): Long {
        require(sampleRate > 0) { "Sample rate must be positive but was $sampleRate" }
        val micros = offset.inWholeMicroseconds
        require(micros >= 0) { "Offset must not be negative but was $offset" }
        // frames = micros * rate / 1_000_000 with half-up rounding on non-negative values.
        val scaled = multiplyExact(micros, sampleRate.toLong())
        return (scaled + MICROS_PER_SECOND / 2) / MICROS_PER_SECOND
    }

    /**
     * Converts a duration to a frame count, half up.
     *
     * A positive audible duration always yields at least one frame: a window shorter than a frame
     * would otherwise become silent, turning a scheduled note into a gap.
     */
    fun frameCount(duration: Duration, sampleRate: Int): Long {
        require(sampleRate > 0) { "Sample rate must be positive but was $sampleRate" }
        val micros = duration.inWholeMicroseconds
        require(micros >= 0) { "Duration must not be negative but was $duration" }
        val frames =
            (multiplyExact(micros, sampleRate.toLong()) + MICROS_PER_SECOND / 2) / MICROS_PER_SECOND
        return if (micros > 0 && frames == 0L) 1L else frames
    }

    /**
     * Multiplies two non-negative values, failing instead of overflowing.
     *
     * Kotlin's `Long` arithmetic wraps silently; a wrapped conversion would place an event at a
     * nonsense frame, so it is an error here.
     */
    private fun multiplyExact(left: Long, right: Long): Long {
        if (left == 0L || right == 0L) return 0L
        val product = left * right
        if (product / right != left) {
            throw IllegalArgumentException("Frame conversion overflow: $left x $right")
        }
        return product
    }

    const val MICROS_PER_SECOND: Long = 1_000_000L
}

/** Raised when a timeline cannot be represented at the requested output format. */
class FrameConversionException(message: String) : IllegalArgumentException(message)

/** One audible window of an exercise, already converted to absolute frames. */
data class FrameWindow(
    val startFrame: Long,
    val frameCount: Long,
    val note: Int?,
    val voiceId: String?,
    val separator: Boolean = false
) {
    init {
        require(startFrame >= 0) { "Start frame must not be negative but was $startFrame" }
        require(frameCount > 0) { "Frame count must be positive but was $frameCount" }
        require(note != null || voiceId != null) { "A window must identify a note or a voice clip" }
    }

    val endFrame: Long get() = startFrame + frameCount
}

/**
 * Timeline converted to output frames, with the frame boundaries the offline analysis compares
 * against.
 */
class FrameTimeline(
    val sampleRate: Int,
    val channels: Int,
    val totalFrames: Long,
    val windows: List<FrameWindow>
) {
    init {
        require(totalFrames >= 0) { "Total frames must not be negative but was $totalFrames" }
        var previousEnd = 0L
        var previousStart = -1L
        windows.forEach { window ->
            require(window.endFrame <= totalFrames) {
                "Window $window ends after the exercise (${window.endFrame} > $totalFrames)"
            }
            // Windows are half-open: a window may start exactly where the previous one ended.
            // Harmonic notes are the one legitimate overlap: two different pitches share their
            // start frame, and the renderer schedules them together. Every other overlap would
            // double-schedule audio.
            require(window.startFrame >= previousEnd || window.startFrame == previousStart) {
                "Window $window overlaps the previous window which ends at $previousEnd"
            }
            previousEnd = maxOf(previousEnd, window.endFrame)
            previousStart = window.startFrame
        }
    }

    /** Length of the whole exercise in seconds at its own sample rate. */
    val seconds: Double get() = totalFrames.toDouble() / sampleRate.toDouble()

    /** Total bytes of interleaved float PCM for this timeline. */
    fun byteCount(): Long = totalFrames * channels.toLong() * FLOAT_BYTES

    companion object {
        const val FLOAT_BYTES: Long = 4L
    }
}
