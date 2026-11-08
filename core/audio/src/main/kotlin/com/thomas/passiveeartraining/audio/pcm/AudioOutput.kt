package com.thomas.passiveeartraining.audio.pcm

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.cos
import kotlin.time.Duration

/**
 * Device output contract.
 *
 * Implemented by Android's AudioTrack in the app and by a fake in tests. The PCM pipeline owns
 * *what* to play; this port owns only the device stream, so scheduling and position logic stay
 * testable off-device.
 */
interface AudioOutput {
    val format: PcmFormat

    /**
     * Ensures a playing stream exists at [format] and buffers about [preBufferFrames] of silence.
     *
     * Starting is explicit and idempotent: a second call while playing does nothing.
     */
    fun start(preBufferFrames: Int)

    /**
     * Queues interleaved float PCM starting at element [offset] (not frame index).
     *
     * Returns the frames actually accepted, which may be fewer than requested. A caller that
     * retries a short write must advance [offset] by `accepted * channels`, otherwise it would
     * re-send audio that was already queued.
     */
    fun write(samples: FloatArray, offset: Int, frames: Int): Int

    /** Pauses and leaves queued audio in place. */
    fun pause()

    /** Clears queued audio and resets the position. Used when resuming from cached PCM. */
    fun flush()

    /** Stops playback and releases the device stream. Also used on error. */
    fun stop()

    /** Estimated frames the device has actually played since the stream started. */
    fun playedFrames(): Long

    /** Buffer capacity of the stream in frames, or 0 when nothing is playing. */
    fun bufferCapacityFrames(): Int

    /** Number of underruns observed since the stream started. */
    fun underrunCount(): Int

    /** True while the stream is playing. */
    fun isPlaying(): Boolean

    /** Sample rate the device actually accepted, which may differ from the request. */
    fun acceptedSampleRate(): Int
}

/** Result of one output session, recorded for diagnostics and the device report. */
data class OutputMetrics(
    val requestedSampleRate: Int,
    val acceptedSampleRate: Int,
    val channels: Int,
    val bufferCapacityFrames: Int,
    val framesWritten: Long,
    val framesPlayed: Long,
    val underrunCount: Int,
    val shortWrites: Int
)

/** Raised when the device stream cannot be created or has been lost. */
class AudioOutputException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/**
 * Writes one cached exercise to an output stream with cancellation, position tracking and
 * bounded queueing.
 *
 * The write loop always runs on the caller's worker (the media service's output worker) and never
 * blocks the caller for the whole exercise: it stops as soon as [isCancelled] becomes true, and it
 * honours short writes by retrying the remainder. Positions are tracked separately:
 *
 * - **submitted**: frames handed to the device;
 * - **played**: frames the device reports as consumed, clamped to what was submitted.
 */
class ExerciseOutputWriter(
    private val output: AudioOutput,
    private val blockFrames: Int = DEFAULT_BLOCK_FRAMES,
    /** Test hook: disable only where a test asserts exact PCM values of the submitted blocks. */
    private val antiClickFade: Boolean = true
) {
    init {
        require(blockFrames in 64..8192) { "Block size must be in 64..8192 but was $blockFrames" }
    }

    private var submittedFrames = 0L
    private var shortWrites = 0

    /** Set when submission stopped because a boundary was not authorized. */
    @Volatile
    private var stopBoundary: Long? = null

    /**
     * Set when playback should stop short of the end of the exercise.
     *
     * The write loop blocks on the device, so it cannot be interrupted from outside without a flag
     * it checks between blocks. Without this, a pause press queued behind the loop and only took
     * effect once the exercise had finished, which is what the reviewer heard.
     */
    private val pauseRequested = AtomicBoolean(false)
    private val writing = AtomicBoolean(false)

    /** Exercise and segment origin used to build the anti-click fade block at a sudden stop. */
    @Volatile
    private var fadeExercise: RenderedExercise? = null

    @Volatile
    private var segmentStartFrame = 0L

    @Volatile
    private var segmentDeviceBase = 0L

    /** Thread running the write loop, so a pause from inside the loop does not wait on itself. */
    @Volatile
    private var writerThread: Thread? = null

    /** Exactly one of the write loop and an off-thread pause plays the fade-out. */
    private val pauseFadeHandled = AtomicBoolean(false)

    /**
     * Master gain applied to every block and to the anti-click fade block.
     *
     * Used by the sleep timer's fade (and the interruption duck); 1.0 leaves the rendered PCM
     * untouched exactly.
     */
    @Volatile
    var masterGain: Float = 1f
        private set

    /** Applies [gain] to every later block; bounded to 0..1 so a fade can never amplify. */
    fun setMasterGain(gain: Float) {
        require(gain in 0f..1f) { "Master gain must be in 0..1 but was $gain" }
        masterGain = gain
    }

    val submitted: Long get() = submittedFrames
    val shortWriteCount: Int get() = shortWrites

    /** The boundary submission stopped at, or null when the segment was submitted in full. */
    val stoppedAtBoundary: Long? get() = stopBoundary

    /**
     * Plays [exercise] from [startFrame].
     *
     * @param isCancelled polled before every write; a cancelled call leaves the stream running and
     *   returns the frames written so far so the caller can resume at the retained position.
     * @return frames submitted during this call.
     */
    fun play(
        exercise: RenderedExercise,
        startFrame: Long,
        endFrameExclusive: Long = exercise.totalFrames,
        boundaries: List<Long> = emptyList(),
        shouldContinueAtBoundary: (Long) -> Boolean = { true },
        isCancelled: () -> Boolean = { false }
    ): Long {
        require(startFrame in 0..exercise.totalFrames) {
            "Start frame must be in 0..${exercise.totalFrames} but was $startFrame"
        }
        require(endFrameExclusive in startFrame..exercise.totalFrames) {
            "End frame must be in $startFrame..${exercise.totalFrames} but was $endFrameExclusive"
        }
        require(
            boundaries == boundaries.sorted() &&
                boundaries.all { it in startFrame..exercise.totalFrames }
        ) {
            "Boundaries must be sorted and inside the played segment: $boundaries"
        }
        val relevantBoundaries = boundaries.filter { it > startFrame }
        pauseRequested.set(false)
        pauseFadeHandled.set(false)
        stopBoundary = null
        fadeExercise = exercise
        segmentStartFrame = startFrame
        segmentDeviceBase = output.playedFrames()
        writerThread = Thread.currentThread()
        writing.set(true)
        var hadFailure = false
        try {
            val channels = exercise.channels
            val block = FloatArray(blockFrames * channels)
            var frame = startFrame
            var written = 0L
            while (frame < endFrameExclusive) {
                if (isCancelled() || pauseRequested.get()) {
                    break
                }
                val boundary = relevantBoundaries.firstOrNull { it > frame }
                val limit = boundary ?: endFrameExclusive
                val frames = minOf(blockFrames.toLong(), limit - frame).toInt()
                val offset = (frame * channels).toInt()
                System.arraycopy(exercise.samples, offset, block, 0, frames * channels)
                val gain = masterGain
                val fadeInFrames = if (antiClickFade) {
                    fadeFrames(FADE_IN_MILLISECONDS, exercise.sampleRate)
                } else {
                    0
                }
                if (gain != 1f || fadeInFrames > 0) {
                    // Master gain and the 2 ms onset ramp scale only the submitted block; the
                    // cached PCM and the user's own gains stay untouched.
                    for (frameIndex in 0 until frames) {
                        val offsetFromStart = frame + frameIndex - startFrame
                        val ramp = if (offsetFromStart < fadeInFrames) {
                            fadeInGain(offsetFromStart.toInt(), fadeInFrames)
                        } else {
                            1f
                        }
                        val applied = gain * ramp
                        if (applied == 1f) continue
                        val base = frameIndex * channels
                        for (channel in 0 until channels) {
                            block[base + channel] *= applied
                        }
                    }
                }
                var accepted = 0
                while (accepted < frames) {
                    if (isCancelled() || pauseRequested.get()) {
                        break
                    }
                    // The retry continues after the frames the device already took; restarting at the
                    // start of the block would queue the same audio twice.
                    val chunk = output.write(block, accepted * channels, frames - accepted)
                    if (chunk <= 0) {
                        // A zero-length write means the device made no room; stop rather than spin.
                        throw AudioOutputException(
                            "Audio output accepted no frames at frame ${frame + accepted}"
                        )
                    }
                    if (chunk < frames - accepted) {
                        shortWrites += 1
                    }
                    accepted += chunk
                }
                written += accepted
                frame += accepted
                if (accepted < frames) {
                    break
                }
                if (boundary != null && frame == boundary &&
                    !shouldContinueAtBoundary(boundary)
                ) {
                    stopBoundary = boundary
                    break
                }
            }
            submittedFrames += written
            return written
        } catch (failure: Throwable) {
            hadFailure = true
            throw failure
        } finally {
            writing.set(false)
            writerThread = null
            // A device failure is already propagating; do not let the fade mask it.
            if (!hadFailure && antiClickFade && pauseRequested.get() &&
                pauseFadeHandled.compareAndSet(false, true)
            ) {
                performAntiClickStop()
            }
        }
    }

    /**
     * Asks the write loop to stop after the current block and silences the queued audio.
     *
     * A sudden pause/stop used to drop the queue at full amplitude, which clicks. The queue is still
     * dropped, but an 8 ms raised-cosine fade block is played from the cached PCM at the position
     * the device had reached first, so the cut happens at exact zero. The position the device has
     * already consumed is retained, so [play] can continue from it.
     *
     * The fade runs exactly once: on the write thread when pause is requested from inside the loop,
     * otherwise on the caller after the loop has left the device alone.
     */
    fun requestPause() {
        pauseRequested.set(true)
        // A pause before playback has started must not flush: there is nothing queued yet and doing
        // so reset the device position, which is what left the UI waiting forever.
        if (!output.isPlaying()) {
            return
        }
        if (!antiClickFade) {
            output.flush()
            return
        }
        if (writerThread === Thread.currentThread()) {
            // The loop's finally block plays the fade after it stops writing.
            return
        }
        awaitWriteStop()
        if (pauseFadeHandled.compareAndSet(false, true)) {
            performAntiClickStop()
        }
    }

    /** Waits (bounded) for the write loop to leave the device alone before touching the queue. */
    private fun awaitWriteStop() {
        val deadline = System.nanoTime() + WRITE_STOP_TIMEOUT_MILLIS * 1_000_000L
        while (writing.get() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(1)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }

    /**
     * Plays the anti-click fade-out from the cached PCM at the device's current position.
     *
     * Queueing the fade after a flush (rather than mutating queued audio) keeps the cached PCM and
     * the note-window envelopes untouched. The device is left paused with an empty queue.
     */
    private fun performAntiClickStop() {
        if (writing.get()) {
            // A paused write loop that did not finish in time: never write concurrently.
            output.flush()
            return
        }
        if (!output.isPlaying()) {
            return
        }
        val exercise = fadeExercise
        if (exercise == null) {
            output.flush()
            return
        }
        val position = currentItemFrame(exercise)
        val frames = fadeFrames(FADE_OUT_MILLISECONDS, exercise.sampleRate)
        val channels = exercise.channels
        val block = FloatArray(frames * channels)
        val master = masterGain
        for (index in 0 until frames) {
            val applied = fadeOutGain(index, frames) * master
            val sourceFrame = position + index
            for (channel in 0 until channels) {
                val source = sourceFrame * channels + channel
                if (source < exercise.samples.size) {
                    block[index * channels + channel] = exercise.samples[source.toInt()] * applied
                }
            }
        }
        try {
            output.flush()
            output.start(preBufferFrames = frames)
            var written = 0
            while (written < frames) {
                val chunk = output.write(block, written * channels, frames - written)
                if (chunk <= 0) break
                written += chunk
            }
            if (written == frames) {
                // The fade must actually reach the device; ending at zero is what removes the
                // click, so wait for its last frame before the final flush.
                val target = output.playedFrames() + frames
                val deadline = System.nanoTime() + FADE_PLAYBACK_TIMEOUT_MILLIS * 1_000_000L
                while (output.isPlaying() && output.playedFrames() < target &&
                    System.nanoTime() < deadline
                ) {
                    try {
                        Thread.sleep(1)
                    } catch (interrupted: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
        } catch (failure: Exception) {
            // The fade is best-effort: a route change or device loss must still silence the
            // queue, which the final flush below does. The owning release path reports the loss.
        } finally {
            output.flush()
        }
    }

    /** Cached item frame at the device's playback head for the most recent [play] segment. */
    private fun currentItemFrame(exercise: RenderedExercise): Long {
        val played = (output.playedFrames() - segmentDeviceBase).coerceAtLeast(0L)
        return (segmentStartFrame + played).coerceIn(0L, exercise.totalFrames)
    }

    /** Retained position in frames, for continuing after a pause. */
    fun retainedFrame(): Long = playedFrames()

    /** True while the write loop is active, including the blocks it is waiting to submit. */
    fun isWriting(): Boolean = writing.get()

    /** Consumed frames reported by the device, clamped to the submitted range. */
    fun playedFrames(): Long = output.playedFrames().coerceIn(0L, submittedFrames)

    /** Fraction of the submitted audio the device has played, in 0..1. */
    fun progress(submittedTotal: Long): Float {
        if (submittedTotal <= 0L) {
            // Nothing was submitted, so there is no progress to report; never divide by zero.
            return 0f
        }
        return (playedFrames().toDouble() / submittedTotal.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    fun metrics(): OutputMetrics = OutputMetrics(
        requestedSampleRate = output.format.sampleRate,
        acceptedSampleRate = output.acceptedSampleRate(),
        channels = output.format.channels,
        bufferCapacityFrames = output.bufferCapacityFrames(),
        framesWritten = submittedFrames,
        framesPlayed = output.playedFrames(),
        underrunCount = output.underrunCount(),
        shortWrites = shortWrites
    )

    /** Resets the position accounting; used when a new exercise starts. */
    fun resetCounters() {
        submittedFrames = 0L
        shortWrites = 0
        pauseRequested.set(false)
    }

    companion object {
        const val DEFAULT_BLOCK_FRAMES: Int = 1024

        /** Fade-out after a sudden pause/stop: long enough to remove the click, short enough to feel immediate. */
        const val FADE_OUT_MILLISECONDS: Int = 8

        /** Onset ramp for Start/Resume: a mid-note resume would otherwise begin on a non-zero sample. */
        const val FADE_IN_MILLISECONDS: Int = 2

        /** Bounded wait for the write loop to stop touching the device before the fade is played. */
        const val WRITE_STOP_TIMEOUT_MILLIS: Int = 100

        /** Bounded wait for the fade block to reach the device before the queue is silenced. */
        const val FADE_PLAYBACK_TIMEOUT_MILLIS: Int = 100

        /** Frames a ramp of [milliseconds] occupies at [sampleRate], at least one. */
        fun fadeFrames(milliseconds: Int, sampleRate: Int): Int {
            require(milliseconds >= 0) { "Fade length must not be negative but was $milliseconds" }
            require(sampleRate > 0) { "Sample rate must be positive but was $sampleRate" }
            return (sampleRate.toLong() * milliseconds / 1_000L).toInt().coerceAtLeast(1)
        }

        /** Raised-cosine onset gain for sample [index] of a [frames]-frame ramp: 0 -> 1. */
        fun fadeInGain(index: Int, frames: Int): Float {
            require(frames > 0) { "Fade frames must be positive but was $frames" }
            if (index >= frames) return 1f
            val clamped = index.coerceAtLeast(0)
            return (0.5 * (1.0 - cos(PI * clamped.toDouble() / frames))).toFloat()
        }

        /** Raised-cosine anti-click gain for sample [index] of a [frames]-frame fade: 1 -> 0. */
        fun fadeOutGain(index: Int, frames: Int): Float {
            require(frames > 0) { "Fade frames must be positive but was $frames" }
            if (index < 0) return 1f
            if (index >= frames) return 0f
            return (0.5 * (1.0 + cos(PI * (index + 1).toDouble() / frames))).toFloat()
        }

        /**
         * Onset ramp applied when resuming cached PCM mid-note.
         *
         * A hard start at a non-zero sample value clicks; a short ramp removes that without
         * changing the retained position.
         */
        const val RESUME_RAMP_MILLISECONDS: Int = 5

        /** Applies the resume ramp in place over [frames] starting at [startFrame]. */
        fun applyRamp(exercise: RenderedExercise, startFrame: Long, sampleRate: Int) {
            val rampFrames = (sampleRate.toLong() * RESUME_RAMP_MILLISECONDS / 1000L)
                .toInt()
                .coerceAtLeast(1)
            val channels = exercise.channels
            val last = minOf(startFrame + rampFrames, exercise.totalFrames)
            for (frame in startFrame until last) {
                val gain = ((frame - startFrame).toFloat() / rampFrames.toFloat()).coerceIn(0f, 1f)
                for (channel in 0 until channels) {
                    val index = (frame * channels + channel).toInt()
                    exercise.samples[index] = exercise.samples[index] * gain
                }
            }
        }
    }
}

/** Elapsed-time source used for output timing decisions; kept separate from the engine clock. */
interface OutputClock {
    fun elapsed(): Duration
}

/** Test clock that only moves forward. */
class SteppingOutputClock(private var now: Duration = Duration.ZERO) : OutputClock {
    override fun elapsed(): Duration = now

    fun advance(delta: Duration) {
        require(!delta.isNegative()) { "A monotonic clock cannot move backwards" }
        now += delta
    }
}
