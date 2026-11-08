package com.thomas.passiveeartraining.training.session

import com.thomas.passiveeartraining.training.recipe.EndingCause
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.SequencePolicy
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.UserSettings
import com.thomas.passiveeartraining.training.timer.SleepTimerState
import kotlin.time.Duration
import kotlinx.coroutines.flow.StateFlow

/**
 * Identifies one concrete session.
 *
 * Every asynchronous result carries the session that started it, so a late callback from a stopped
 * session can never act on a newer one.
 */
@JvmInline
value class SessionId(val value: Long)

/** Where a session is in its lifecycle. */
enum class SessionPhase {
    /** Nothing prepared; a Start is available. */
    IDLE,

    /** Validating assets and preparing the first item. */
    PREPARING,

    /** Playing the current item. */
    PLAYING,

    /** Stopped between commands with the retained position; a Resume continues from it. */
    PAUSED,

    /** Releasing resources after a Stop. */
    STOPPING,

    /** Finished by the user (Stop) or by running to the end of the queue. */
    COMPLETED,

    /** Failed; an explicit new Start is required and nothing resumes implicitly. */
    ERROR
}

/** Why a session is paused or completed. */
sealed interface PauseReason {
    /** The user pressed pause. */
    data object User : PauseReason

    /** Audio focus was lost to another application. */
    data object Focus : PauseReason

    /** The output device changed and playback cannot continue as it was. */
    data object RouteChange : PauseReason

    /** The duration timer reached its deadline. */
    data class TimerExpired(val elapsed: Duration) : PauseReason

    /** A count or cycle stop policy was reached. */
    data object StopPolicyReached : PauseReason

    /** The user stopped the session. */
    data object Stopped : PauseReason

    /** The queue ran out of items. */
    data object QueueExhausted : PauseReason
}

/** A failure that stopped the session. */
data class SessionError(val code: ErrorCode, val detail: String) {
    enum class ErrorCode {
        /** The configuration or a referenced asset was invalid. */
        INVALID_CONFIGURATION,

        /** An answer clip or the instrument could not be prepared. */
        ASSET_UNAVAILABLE,

        /** The audio output could not be opened or was lost. */
        OUTPUT_FAILURE,

        /** A command was not available in the current phase. */
        UNAVAILABLE_COMMAND
    }
}

/** Controls the UI may offer right now; the UI renders these instead of guessing. */
data class AvailableControls(
    val canStart: Boolean,
    val canPause: Boolean,
    val canResume: Boolean,
    val canNext: Boolean,
    val canPrevious: Boolean,
    val canRepeat: Boolean,
    val canStop: Boolean
)

/** What the session looks like at one moment. */
data class SessionSnapshot(
    val session: SessionId?,
    val phase: SessionPhase,
    val itemIndex: Int,
    val answerVisible: Boolean,
    val consumedFrames: Long,
    val totalFrames: Long,
    val playedDuration: Duration,
    val pauseReason: PauseReason?,
    val error: SessionError?,
    val controls: AvailableControls,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
    /** The sleep-timer policy in force and its observable deadline, if any. */
    val sleep: SleepTimerState = SleepTimerState(),
    /** Why a completed session ended, once a stop policy decided it. */
    val endingCause: EndingCause? = null,
    /** The seed the accepted Start resolved and recorded; null before a Start. */
    val seed: Long? = null,
    /** The kind of the audible activation, or null before the first accepted Start. */
    val activeKind: com.thomas.passiveeartraining.training.provider.TrainingKind? = null,
    /** The kind a committed switch is heading to, or null when no switch is pending. */
    val pendingKind: com.thomas.passiveeartraining.training.provider.TrainingKind? = null,
    /** The saved revision acknowledged for the audible activation, when it came from a switch. */
    val activeRevision: Long? = null,
    /** The saved revision of the pending destination, when a switch is waiting for a boundary. */
    val pendingRevision: Long? = null,
    /**
     * The concrete interval answer, present only once its frame has been consumed.
     *
     * Null before consumption, on navigation and on a fresh item, so no surface can leak an
     * unconsumed answer even by accident.
     */
    val answerLabel: String? = null,
    /**
     * MIDI notes of the current item's groups, exposed for the Play debug line.
     *
     * Empty outside PLAYING. This deliberately makes the generated notes visible on the Play
     * surface at the owner's request; it is a debug readout, not part of the answer contract.
     */
    val currentNotes: List<Int> = emptyList()
) {
    /** Position within the current item, in 0..1. */
    val itemProgress: Float
        get() = if (totalFrames <= 0L) {
            0f
        } else {
            (consumedFrames.toDouble() / totalFrames.toDouble()).toFloat().coerceIn(0f, 1f)
        }

    companion object {
        /** The initial state: idle with only Start available. */
        val IDLE: SessionSnapshot = SessionSnapshot(
            session = null,
            phase = SessionPhase.IDLE,
            itemIndex = 0,
            answerVisible = false,
            consumedFrames = 0L,
            totalFrames = 0L,
            playedDuration = Duration.ZERO,
            pauseReason = null,
            error = null,
            controls = AvailableControls(
                canStart = true,
                canPause = false,
                canResume = false,
                canNext = false,
                canPrevious = false,
                canRepeat = false,
                canStop = false
            ),
            hasNext = false,
            hasPrevious = false
        )
    }
}

/** The command surface. One command is processed at a time, in arrival order. */
sealed interface SessionCommand {
    /** Begin a session from the stopped or error state. */
    data class Start(val request: StartRequest) : SessionCommand

    /** Stop playback and retain the position. */
    data class Pause(val reason: PauseReason = PauseReason.User) : SessionCommand

    /** Continue from the retained position. */
    data object Resume : SessionCommand

    /** Flush the current item and move forward. */
    data object Next : SessionCommand

    /** Restart the current item, or move back when the position is early. */
    data object Previous : SessionCommand

    /** Restart the same concrete item without drawing new randomness. */
    data object Repeat : SessionCommand

    /** Stop the session and release its resources. */
    data class Stop(val reason: PauseReason = PauseReason.Stopped) : SessionCommand

    /**
     * Sets the sleep timer: a positive minute count arms a fresh deadline from now, zero cancels.
     *
     * The value is stored even when no session is active so the next accepted Start uses it.
     * [fadeSeconds] is the optional 0..[MAX_SLEEP_FADE_SECONDS] fade before the deadline.
     */
    data class SleepTimer(val minutes: Int, val fadeSeconds: Int = 0) : SessionCommand {
        init {
            require(minutes in 0..MAX_SLEEP_MINUTES) {
                "Sleep timer minutes are 0..$MAX_SLEEP_MINUTES but were $minutes"
            }
            require(fadeSeconds in 0..MAX_SLEEP_FADE_SECONDS) {
                "Sleep timer fade seconds are 0..$MAX_SLEEP_FADE_SECONDS but were $fadeSeconds"
            }
        }
    }

    /**
     * Lowers or restores the output volume for a platform duck.
     *
     * One restores the product gain exactly. This is internal transport, never exposed to another
     * app's controller.
     */
    data class DuckGain(val gain: Float) : SessionCommand {
        init {
            require(gain in 0f..1f) { "A duck gain is 0..1 but was $gain" }
        }
    }

    /**
     * Requests a mode switch after the current learning sequence.
     *
     * The service resolves and validates the destination's saved setup and passes the revision it
     * acknowledged; the controller keeps the audible unit and commits at the next safe boundary
     * (SC6/M3). The command carries a fully resolved StartRequest; its seed may still be null, in
     * which case the activation seed is derived from the session seed and the activation ordinal.
     */
    data class SwitchMode(val request: StartRequest, val savedRevision: Long) : SessionCommand

    /**
     * Applies edited settings to the next item of a running session.
     *
     * The audible item keeps its rendered timeline; the follower is redrawn and every later item
     * uses the new configuration. A command that carries the active request is a no-op.
     */
    data class Reconfigure(val request: StartRequest) : SessionCommand

    companion object {
        /** Three hours, matching the timer's own maximum. */
        const val MAX_SLEEP_MINUTES: Int = 180

        /** Longest fade the timer offers. */
        const val MAX_SLEEP_FADE_SECONDS: Int = 60
    }
}

/** Everything a session needs to begin. */
data class StartRequest(
    /** The provider-neutral musical setup: config, recipe, seed and stop policy. */
    val setup: com.thomas.passiveeartraining.training.provider.ModeSetup,
    /** Music gain applied to the rendered example, within the settings range. */
    val musicGain: Float = UserSettings.DEFAULT_MUSIC_GAIN,
    /** Voice gain applied to the spoken answer, within the settings range. */
    val voiceGain: Float = UserSettings.DEFAULT_VOICE_GAIN,
    val answerVisibility: com.thomas.passiveeartraining.training.settings.AnswerVisibility =
        com.thomas.passiveeartraining.training.settings.AnswerVisibility.SHOWN_WHEN_CONSUMED
) {
    init {
        require(musicGain in UserSettings.MIN_GAIN..UserSettings.MAX_GAIN) {
            "A music gain is ${UserSettings.MIN_GAIN}..${UserSettings.MAX_GAIN} but was $musicGain"
        }
        require(voiceGain in UserSettings.MIN_GAIN..UserSettings.MAX_GAIN) {
            "A voice gain is ${UserSettings.MIN_GAIN}..${UserSettings.MAX_GAIN} but was $voiceGain"
        }
    }

    /** A copy whose setup carries the resolved [resolved] seed. */
    fun withResolvedSeed(resolved: Long): StartRequest = copy(setup = setup.withSeed(resolved))
}

/** The outcome of one command, so a caller can report or log it without inspecting internal state. */
sealed interface CommandResult {
    /** The command was applied. */
    data class Applied(val snapshot: SessionSnapshot) : CommandResult

    /** The command is not available in the current phase and changed nothing. */
    data class Unavailable(val reason: SessionError) : CommandResult
}

/** The read-only view of a session that UI and services consume. */
interface SessionController {
    /** Current state; updates as commands are processed. */
    val state: StateFlow<SessionSnapshot>

    /** Sends a command and waits for it to be processed; commands never run concurrently. */
    suspend fun send(command: SessionCommand): CommandResult

    /** Releases the owned scope. After this the controller must not be used. */
    fun close()
}
