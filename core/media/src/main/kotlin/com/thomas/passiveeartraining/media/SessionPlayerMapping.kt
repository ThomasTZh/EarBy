package com.thomas.passiveeartraining.media

import com.thomas.passiveeartraining.training.session.PauseReason
import com.thomas.passiveeartraining.training.session.SessionError
import com.thomas.passiveeartraining.training.session.SessionPhase
import com.thomas.passiveeartraining.training.session.SessionSnapshot

/**
 * The parts of Media3 player state a session snapshot determines.
 *
 * Kept as plain data so the mapping is testable without a device or a Media3 runtime; the service
 * applies these fields to its `SimpleBasePlayer`.
 */
data class PlayerStateProjection(
    val playbackState: Int,
    val playWhenReady: Boolean,
    val isPlaying: Boolean,
    val errorCode: Int?,
    val errorMessage: String?,
    val canPlay: Boolean,
    val canPause: Boolean,
    val canSeekToPrevious: Boolean,
    val canSeekToNext: Boolean,
    val canRepeat: Boolean,
    val canStop: Boolean,
    val totalDurationMs: Long,
    val positionMs: Long,
    val bufferedMs: Long
)

/** What the media notification and lock screen may show for the current item. */
data class NotificationContent(val title: String, val subtitle: String, val showAnswer: Boolean)

/**
 * Maps a session snapshot onto Media3 player state and onto the text surfaces.
 *
 * All of Media3's constants are expressed as plain Ints here so this mapping can be tested on the
 * host; the service uses [PlaybackStateCodes] to translate them.
 */
object SessionPlayerMapping {

    /** Playback state codes matching `androidx.media3.common.Player`'s constants. */
    object PlaybackStateCodes {
        const val IDLE = 1
        const val BUFFERING = 2
        const val READY = 3
        const val ENDED = 4
    }

    /** Playback error codes matching `androidx.media3.common.PlaybackException`'s constants. */
    object ErrorCodes {
        const val UNKNOWN = 1000
        const val PARSING_FAILED = 1002
        const val IO_UNSPECIFIED = 2000
        const val AUDIO_TRACK_INIT_FAILED = 5002
    }

    /** Duration reported when an item's frame layout is not known yet. */
    const val TIME_UNSET: Long = -9223372036854775807L

    /**
     * Projects [snapshot] onto player state.
     *
     * Preparing maps to buffering, Playing and Paused map to ready with `playWhenReady` telling
     * them apart, Completed maps to ended, and Error maps to idle with a playback exception. A
     * failed session is idle rather than ended so a controller offers Play again instead of a
     * finished item.
     */
    fun project(snapshot: SessionSnapshot, sampleRate: Int): PlayerStateProjection {
        require(sampleRate > 0) { "Sample rate must be positive but was $sampleRate" }
        val totalMs = if (snapshot.totalFrames > 0L) {
            framesToMs(snapshot.totalFrames, sampleRate)
        } else {
            TIME_UNSET
        }
        val positionMs = framesToMs(snapshot.consumedFrames, sampleRate)
        val state = when (snapshot.phase) {
            SessionPhase.IDLE -> PlaybackStateCodes.IDLE
            SessionPhase.PREPARING, SessionPhase.STOPPING -> PlaybackStateCodes.BUFFERING
            SessionPhase.PLAYING, SessionPhase.PAUSED -> PlaybackStateCodes.READY
            SessionPhase.COMPLETED -> PlaybackStateCodes.ENDED
            SessionPhase.ERROR -> PlaybackStateCodes.IDLE
        }
        val failure = snapshot.error
        return PlayerStateProjection(
            playbackState = state,
            playWhenReady = snapshot.phase == SessionPhase.PLAYING,
            isPlaying = snapshot.phase == SessionPhase.PLAYING,
            errorCode = failure?.let { errorCodeFor(it) },
            errorMessage = failure?.detail,
            // Only the operations the product supports are advertised: no seek, no speed, no
            // arbitrary playlist injection.
            canPlay = snapshot.controls.canStart || snapshot.controls.canResume,
            canPause = snapshot.controls.canPause,
            canSeekToPrevious = snapshot.controls.canPrevious,
            canSeekToNext = snapshot.controls.canNext,
            canRepeat = snapshot.controls.canRepeat,
            canStop = snapshot.controls.canStop,
            totalDurationMs = totalMs,
            positionMs = positionMs,
            bufferedMs = if (state == PlaybackStateCodes.READY) totalMs else 0L
        )
    }

    /** Frames to milliseconds, rounding down so a position never reports ahead of the audio. */
    fun framesToMs(frames: Long, sampleRate: Int): Long = frames * 1000L / sampleRate

    /** Milliseconds to frames, used when a controller reports a position back. */
    fun msToFrames(ms: Long, sampleRate: Int): Long = ms * sampleRate / 1000L

    /**
     * Text for the notification, lock screen and session metadata.
     *
     * The answer is revealed only once the session reports it visible, which happens when its frame
     * is consumed. Every surface uses this one policy, so no surface can leak an unconsumed answer.
     */
    fun notificationContent(snapshot: SessionSnapshot): NotificationContent {
        val title = "Interval recognition"
        val subtitle = when {
            snapshot.phase == SessionPhase.PREPARING -> "Preparing"
            snapshot.phase == SessionPhase.PAUSED && !snapshot.answerVisible -> "Paused - Think"
            snapshot.phase == SessionPhase.PAUSED -> "Paused"
            snapshot.phase == SessionPhase.COMPLETED -> "Finished"
            snapshot.phase == SessionPhase.ERROR -> "Stopped"
            snapshot.answerLabel != null -> "Answer: ${snapshot.answerLabel}"
            snapshot.answerVisible -> "Answer"
            else -> "Listen"
        }
        return NotificationContent(
            title = title,
            subtitle = subtitle,
            showAnswer = snapshot.answerVisible
        )
    }

    private fun errorCodeFor(failure: SessionError): Int = when (failure.code) {
        SessionError.ErrorCode.INVALID_CONFIGURATION -> ErrorCodes.PARSING_FAILED
        SessionError.ErrorCode.ASSET_UNAVAILABLE -> ErrorCodes.IO_UNSPECIFIED
        SessionError.ErrorCode.OUTPUT_FAILURE -> ErrorCodes.AUDIO_TRACK_INIT_FAILED
        SessionError.ErrorCode.UNAVAILABLE_COMMAND -> ErrorCodes.UNKNOWN
    }

    /** Human-readable pause reason for a snapshot, or null when it is not paused. */
    fun pauseReasonText(snapshot: SessionSnapshot): String? =
        when (val reason = snapshot.pauseReason) {
            null -> null
            PauseReason.User -> "paused by you"
            PauseReason.Focus -> "paused because another app took audio focus"
            PauseReason.RouteChange -> "paused because the audio device changed"
            is PauseReason.TimerExpired -> "paused by the sleep timer"
            PauseReason.StopPolicyReached -> "the selected stop policy was reached"
            PauseReason.Stopped -> "stopped"
            PauseReason.QueueExhausted -> "nothing left to play"
        }
}
