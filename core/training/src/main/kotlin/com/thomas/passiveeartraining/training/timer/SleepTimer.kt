package com.thomas.passiveeartraining.training.timer

import com.thomas.passiveeartraining.training.MonotonicClock
import kotlin.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Timer configuration.
 *
 * A duration of zero means off. The fade is clamped to the duration, and the default is immediate
 * expiry with no fade, so a timer that is merely enabled cannot change how the product sounds.
 */
data class SleepPolicy(
    val duration: Duration = Duration.ZERO,
    val expiry: SleepExpiry = SleepExpiry.IMMEDIATE,
    val fadeDuration: Duration = Duration.ZERO
) {
    init {
        require(duration >= Duration.ZERO) {
            "A sleep duration cannot be negative but was $duration"
        }
        require(duration <= MAX_DURATION) {
            "A sleep duration is at most $MAX_DURATION but was $duration"
        }
        require(fadeDuration >= Duration.ZERO) {
            "A fade cannot be negative but was $fadeDuration"
        }
    }

    /** Whether the timer is enabled. */
    val isEnabled: Boolean get() = duration > Duration.ZERO

    /** The fade actually applied: clamped to the configured duration and to the allowed maximum. */
    val effectiveFade: Duration get() = minOf(fadeDuration, duration, MAX_FADE)

    /** Whether the current exercise is allowed to finish before playback stops. */
    val finishesCurrentExercise: Boolean get() = expiry == SleepExpiry.FINISH_CURRENT_EXERCISE

    companion object {
        /** One minute, the smallest useful setting. */
        val MIN_DURATION: Duration = Duration.parse("1m")

        /** Three hours, the largest setting the work package allows. */
        val MAX_DURATION: Duration = Duration.parse("180m")

        /** Longest fade the work package allows. */
        val MAX_FADE: Duration = Duration.parse("60s")

        /** The timer is off. */
        val OFF: SleepPolicy = SleepPolicy()
    }
}

/** What happens when the deadline passes. */
enum class SleepExpiry {
    /** Stop as soon as the deadline passes. */
    IMMEDIATE,

    /** Let the exercise that is audible at the deadline finish, then stop. */
    FINISH_CURRENT_EXERCISE
}

/** The session state a timer decision needs, kept free of Android types. */
enum class SleepSessionPhase {
    IDLE,
    PREPARING,
    PLAYING,
    PAUSED,
    COMPLETED,
    ERROR
}

/** Why a session stopped, as the timer sees it. */
sealed interface StopReason {
    /** The deadline passed and playback stopped. */
    data class TimerExpired(val policy: SleepPolicy, val finishedCurrentExercise: Boolean) :
        StopReason

    /** The user or the platform stopped playback. */
    data object Other : StopReason
}

/** What the timer asks the owner to do. */
sealed interface SleepTimerEffect {
    /** Nothing to do. */
    data object None : SleepTimerEffect

    /** Apply this gain to the output master gain; the product gain is unchanged underneath. */
    data class FadeTo(val gain: Float) : SleepTimerEffect

    /** Stop now: cancel writes and flush. */
    data class StopNow(val reason: StopReason) : SleepTimerEffect

    /**
     * Stop after the current exercise finishes; no next item is generated.
     *
     * [gain] carries an optional fade to apply while the final item drains; null leaves the gain
     * untouched.
     */
    data class DrainCurrent(
        val reason: StopReason,
        val graceDeadline: Duration,
        val gain: Float? = null
    ) : SleepTimerEffect
}

/** Observable timer state, for the timer control and the session's own reporting. */
data class SleepTimerState(
    val policy: SleepPolicy = SleepPolicy.OFF,
    val deadline: Duration? = null,
    val remaining: Duration? = null,
    val draining: Boolean = false,
    val expired: Boolean = false
) {
    val isEnabled: Boolean get() = policy.isEnabled
}

/**
 * The duration sleep timer.
 *
 * Deadlines are measured from an accepted Start using elapsed realtime, so device sleep counts and
 * wall-clock changes are irrelevant. The timer holds no wake lock and needs no alarm: a session that
 * is already silent stays silent, and the deadline is re-checked before every command and before any
 * sound rather than relying on a callback arriving on time.
 */
class SleepTimer(private val clock: MonotonicClock, private val scheduler: TimerScheduler) {
    private val mutableState = MutableStateFlow(SleepTimerState())

    /** Observable state; the control renders this and the owner reads deadlines from it. */
    val state: StateFlow<SleepTimerState> = mutableState.asStateFlow()

    private var policy: SleepPolicy = SleepPolicy.OFF
    private var deadline: Duration? = null
    private var drainDeadline: Duration? = null
    private var expired = false
    private var expiredAt: Duration? = null

    /**
     * Arms the timer from an accepted Start or replaces an active one.
     *
     * Each call starts a new deadline, so replacing an active timer never inherits the old one.
     */
    fun arm(newPolicy: SleepPolicy) {
        policy = newPolicy
        expired = false
        expiredAt = null
        drainDeadline = null
        deadline = if (newPolicy.isEnabled) clock.elapsed() + newPolicy.duration else null
        publish()
    }

    /** Cancels the timer and restores the unfaded product gain. */
    fun cancel(): SleepTimerEffect {
        policy = SleepPolicy.OFF
        deadline = null
        drainDeadline = null
        expired = false
        expiredAt = null
        publish()
        return SleepTimerEffect.FadeTo(1f)
    }

    /**
     * Evaluates the timer for the current phase.
     *
     * Called before every command and before any sound, so a deferred callback cannot let audio
     * reappear after the deadline.
     */
    fun evaluate(phase: SleepSessionPhase): SleepTimerEffect {
        if (expired) {
            val activeDrain = drainDeadline
            if (activeDrain != null) {
                // Still draining: keep asking for the current item, and keep applying the drain fade
                // so the final item fades out even if the item outlasts the fade window.
                return SleepTimerEffect.DrainCurrent(
                    reason = expiryReason(finishedCurrentExercise = true),
                    graceDeadline = activeDrain,
                    gain = drainFadeGain()
                )
            }
            // Already expired and stopped: the session must stay silent, so every later check
            // reports the stop again rather than falling silent about it.
            return SleepTimerEffect.StopNow(expiryReason(finishedCurrentExercise = false))
        }
        val activeDeadline = deadline ?: return SleepTimerEffect.None
        val now = clock.elapsed()
        if (now < activeDeadline) {
            publish()
            return fadeEffect(now, activeDeadline)
        }
        expired = true
        expiredAt = now
        return when {
            // A timer that expires while nothing is audible completes immediately and prohibits
            // resume; only an exercise that is actually playing may finish.
            phase != SleepSessionPhase.PLAYING -> {
                deadline = null
                publish()
                SleepTimerEffect.StopNow(expiryReason(finishedCurrentExercise = false))
            }
            policy.finishesCurrentExercise -> {
                // Grace is bounded by the longest exercise the product allows, so drain can never
                // extend playback indefinitely.
                drainDeadline = now + MAX_GRACE
                publish()
                SleepTimerEffect.DrainCurrent(
                    reason = expiryReason(finishedCurrentExercise = true),
                    graceDeadline = drainDeadline!!,
                    gain = drainFadeGain()
                )
            }
            else -> {
                deadline = null
                publish()
                SleepTimerEffect.StopNow(expiryReason(finishedCurrentExercise = false))
            }
        }
    }

    /**
     * Fades during the after-expiry drain window.
     *
     * The drain fade starts at expiry (unlike the immediate fade, which ends at the deadline) so a
     * finishing exercise fades out over its last seconds rather than being muted for the whole drain.
     */
    private fun drainFadeGain(): Float? {
        val fade = policy.effectiveFade
        if (fade <= Duration.ZERO) return null
        val start = expiredAt ?: return null
        val elapsedInFade = clock.elapsed() - start
        if (elapsedInFade <= Duration.ZERO) return 1f
        val fraction = (
            elapsedInFade.inWholeMicroseconds.toDouble() /
                fade.inWholeMicroseconds.toDouble()
            )
            .coerceIn(0.0, 1.0)
        return (1.0 - fraction).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Reports the fade gain for [now].
     *
     * Fading is only a master-gain change, so cancelling the timer restores the product gain exactly.
     */
    private fun fadeEffect(now: Duration, activeDeadline: Duration): SleepTimerEffect {
        val fade = policy.effectiveFade
        if (fade <= Duration.ZERO) {
            return SleepTimerEffect.None
        }
        val fadeStart = activeDeadline - fade
        if (now <= fadeStart) {
            return SleepTimerEffect.None
        }
        val elapsedInFade = now - fadeStart
        val fraction = (
            elapsedInFade.inWholeMicroseconds.toDouble() /
                fade.inWholeMicroseconds.toDouble()
            )
            .coerceIn(0.0, 1.0)
        // Multiplicative from 1.0 down to 0.0, so the master gain never exceeds the product gain.
        val gain = (1.0 - fraction).toFloat().coerceIn(0f, 1f)
        return SleepTimerEffect.FadeTo(gain)
    }

    /**
     * Reports whether the drain window has finished.
     *
     * The owner calls this when the current exercise ends; a drain that overruns its grace is
     * stopped by the owner as well, so a stalled item cannot extend the session.
     */
    fun drainComplete(): SleepTimerEffect {
        if (drainDeadline == null) {
            return SleepTimerEffect.None
        }
        drainDeadline = null
        deadline = null
        publish()
        return SleepTimerEffect.StopNow(expiryReason(finishedCurrentExercise = true))
    }

    /** True while the session should not generate or play another exercise. */
    fun isDraining(): Boolean = drainDeadline != null

    private fun expiryReason(finishedCurrentExercise: Boolean): StopReason =
        StopReason.TimerExpired(policy, finishedCurrentExercise)

    private fun publish() {
        val now = clock.elapsed()
        val activeDeadline = deadline
        mutableState.value = SleepTimerState(
            policy = policy,
            deadline = activeDeadline,
            remaining = activeDeadline?.let { if (it > now) it - now else Duration.ZERO },
            draining = drainDeadline != null,
            expired = expired
        )
    }

    /** Starts a periodic re-evaluation so a fade advances and the display counts down. */
    fun startTicking(interval: Duration, onTick: () -> Unit) {
        if (!policy.isEnabled) return
        scheduler.schedule(interval) {
            onTick()
            if (policy.isEnabled) {
                startTicking(interval, onTick)
            }
        }
    }

    private companion object {
        /**
         * Longest grace for a finishing exercise.
         *
         * The product caps an exercise at 60 seconds, so this is the true upper bound on drain.
         */
        val MAX_GRACE: Duration = Duration.parse("60s")
    }
}

/** Schedules a delayed callback; the Android adapter uses the service's own scope. */
fun interface TimerScheduler {
    fun schedule(delay: Duration, action: () -> Unit)
}

/** Scheduler used by tests: nothing happens until the virtual clock is advanced. */
class ManualTimerScheduler : TimerScheduler {
    private val scheduled = mutableListOf<Pair<Duration, () -> Unit>>()

    override fun schedule(delay: Duration, action: () -> Unit) {
        scheduled += delay to action
    }

    /** Runs every scheduled action once, in order. */
    fun runAll() {
        val actions = scheduled.toList()
        scheduled.clear()
        actions.forEach { (_, action) -> action() }
    }

    fun pendingCount(): Int = scheduled.size
}

/** Scheduler backed by a coroutine delay; used on Android through the service's scope. */
class CoroutineTimerScheduler(private val scope: kotlinx.coroutines.CoroutineScope) :
    TimerScheduler {
    override fun schedule(delay: Duration, action: () -> Unit) {
        scope.launch {
            delay(delay)
            action()
        }
    }
}
