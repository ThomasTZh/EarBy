package com.thomas.passiveeartraining.training.session

import com.thomas.passiveeartraining.training.provider.CompiledExercise
import com.thomas.passiveeartraining.training.provider.ConcreteExercise
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition

/**
 * The audio operations a session needs, expressed without Android.
 *
 * The Android adapter implements this over the rendered PCM cache and the device output; tests
 * implement it with a fake so every command and state combination is checkable with no real sleeps.
 */
/** Playback identity is independent of generator indices and survives navigation unchanged. */
data class PlaybackIdentity(val sessionGeneration: Long, val itemOrdinal: Long)

/** Immutable synthesis gains captured when a concrete item is drawn. */
data class RenderSettings(val musicGain: Float, val voiceGain: Float)

interface AudioPort {
    /** Accepts only items belonging to this session until release; stale preparations are refused. */
    fun beginSession(generation: Long) = Unit

    /** Result of preparing one item, including the switching boundaries in frames. */
    data class Prepared(
        /** Frame the answer starts at, taken from the compiled timeline. */
        val answerStartFrame: Long,
        /** Total frames of the item, including its silences. */
        val totalFrames: Long,
        /** Absolute frame where the mandatory learning unit ends; a switch may commit there. */
        val mandatoryEndFrame: Long = totalFrames,
        /** Optional replay units as half-open `start until endExclusive` frame ranges. */
        val replayFrames: List<LongRange> = emptyList(),
        /** Absolute frame where the trailing silence or separator cue begins. */
        val trailingStartFrame: Long = totalFrames
    )

    /** How one playback call ended. */
    data class PlayOutcome(
        /** Frames handed to the device during this call. */
        val submittedFrames: Long,
        /** Item frames the device actually consumed by the end of this call. */
        val consumedFrames: Long,
        /** True only when the whole item timeline was consumed; submission alone is not enough. */
        val completed: Boolean,
        /**
         * Set when submission stopped because a unit boundary was not authorized; the consumed
         * position is at (or within the call's wait for) that boundary.
         */
        val stoppedAtBoundary: Long? = null
    )

    /**
     * Validates an accepted gain configuration; preparations carry their own immutable settings.
     *
     * A live edit changes later items only; retained items always use their captured settings.
     */
    fun configure(musicGain: Float, voiceGain: Float)

    /**
     * Ensures the concrete [exercise] is rendered into the bounded PCM cache and returns its layout.
     *
     * Idempotent: an item whose PCM is already resident is returned from the cache without
     * re-rendering. The cache holds only the current and next items; an evicted item is rendered
     * again on the next call, which is what keeps a selected identity exact.
     *
     * @throws AudioPortException when an asset is missing or the renderer fails.
     */
    suspend fun prepare(
        identity: PlaybackIdentity,
        exercise: ConcreteExercise,
        compiled: CompiledExercise,
        settings: RenderSettings
    ): Prepared

    /**
     * Plays the prepared [exercise] from [startFrame] up to [endFrameExclusive].
     *
     * The call submits only that segment and returns once the device has **consumed** it, or as
     * soon as [isCancelled] turns true, whichever comes first. Submitting every frame is not
     * completion: the output buffers ahead, so this method waits for the device to report the
     * segment tail consumed. A caller that needs to decide at a boundary passes the boundary as
     * [endFrameExclusive] and calls again from the returned position; the next segment of a
     * compiled timeline always begins with scheduled silence, so the submission pause cannot cut a
     * sounding note (M3.2).
     *
     * While the segment plays, [onConsumed] receives the item's consumed frame position
     * monotonically; it runs on the playback worker, so a caller must confine its own state to its
     * own dispatcher.
     *
     * A cancelled call leaves the queued audio flushed by the owner through [flush]; the retained
     * position for a later resume is the last value passed to [onConsumed].
     */
    suspend fun play(
        identity: PlaybackIdentity,
        startFrame: Long,
        /**
         * Sorted unit ends inside the item, each `> startFrame`; the caller authorizes crossing
         * each boundary through [continueAtBoundary]. An empty list plays to the item end.
         */
        boundaries: List<Long>,
        isCancelled: () -> Boolean,
        onConsumed: suspend (Long) -> Unit,
        /**
         * Consulted synchronously when submission reaches a unit boundary: true continues into the
         * next unit, false stops submission at the boundary. It must not block; a controller reads
         * a volatile pending-switch reference here.
         */
        continueAtBoundary: (Long) -> Boolean = { true }
    ): PlayOutcome

    /**
     * Requests permission to produce sound before any frame is submitted.
     *
     * The Android adapter asks the owner for audio focus and route readiness. Returning false means
     * the session stays silent and pauses with a recoverable reason; the call happens before the
     * writer is opened, so a denied request submits no PCM.
     */
    suspend fun requestPlaybackPermission(): Boolean

    /**
     * Applies a master gain to the output without re-rendering.
     *
     * Used only by the sleep timer's fade; 1.0 restores the product gain exactly.
     */
    fun setMasterGain(gain: Float)

    /**
     * Frames the device has consumed for the item of the most recent [play] call.
     *
     * Used only for accounting at a pause/stop boundary; live updates arrive through [play]'s
     * callback so the UI does not have to poll.
     */
    fun consumedFrames(): Long

    /**
     * Silences the output and drops whatever is queued, retaining the consumed position.
     *
     * A navigation command calls this to end the current item; the position for the next item then
     * starts from zero because the port reports per-item consumption.
     */
    fun flush()

    /** Releases the output; used on stop and on failure. */
    fun release()
}

/** Raised by an [AudioPort] implementation when preparation or playback fails. */
class AudioPortException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Deterministic exercise supply for a session.
 *
 * The session draws one item ahead and reuses retained definitions for navigation and repeat, so a
 * navigation command never changes what the generator would produce next. A source is bound to one
 * provider kind per accepted Start; the controller resolves the provider through a registry before
 * configuring (M1.4).
 */
interface ExerciseSource {
    /**
     * Applies the accepted Start before the first item is drawn.
     *
     * The controller calls this once per accepted Start, so a source can re-point its generator and
     * timeline compiler at the new definition, seed and recipe. A source that is asked for an item
     * before being configured must fail rather than guess a configuration. A fresh Start always
     * begins the seed's sequence from its start, so a recorded seed replays exactly.
     */
    fun configure(request: StartRequest)

    /**
     * Applies an edited configuration to a session that is already running.
     *
     * The default re-points the source exactly as a Start would; a source whose sequence is
     * stream-based overrides this to continue the running stream instead of replaying it. The
     * controller calls this for a live edit, never for an accepted Start.
     */
    fun reconfigure(request: StartRequest) {
        configure(request)
    }

    /** Produces the next concrete exercise; called only when a new item is genuinely needed. */
    fun next(): ConcreteExercise

    /** Compiles the timeline for [exercise] under the session's configuration. */
    fun compile(exercise: ConcreteExercise, recipe: RecipeDefinition): CompiledExercise
}
