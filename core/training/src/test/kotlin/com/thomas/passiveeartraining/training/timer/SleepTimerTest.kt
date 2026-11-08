package com.thomas.passiveeartraining.training.timer

import com.thomas.passiveeartraining.training.MonotonicClock
import kotlin.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Virtual clock: time moves only when a test moves it, so no test sleeps. */
private class VirtualClock(private var now: Duration = Duration.ZERO) : MonotonicClock {
    override fun elapsed(): Duration = now

    fun advance(delta: Duration) {
        now += delta
    }

    /**
     * Simulates a wall-clock jump.
     *
     * The timer reads elapsed realtime, which includes device sleep, so time only ever moves
     * forward; a wall-clock change is invisible to it by construction.
     */
    fun advanceThroughSleep(delta: Duration) = advance(delta)
}

private fun policy(
    minutes: Int,
    expiry: SleepExpiry = SleepExpiry.IMMEDIATE,
    fadeSeconds: Int = 0
) = SleepPolicy(
    duration = Duration.parse("${minutes}m"),
    expiry = expiry,
    fadeDuration = Duration.parse("${fadeSeconds}s")
)

class SleepTimerTest {

    private fun timer(clock: VirtualClock = VirtualClock()) =
        Triple(SleepTimer(clock, ManualTimerScheduler()), clock, ManualTimerScheduler())

    @Test
    fun anUnarmedTimerNeverFires() {
        val (subject, clock, _) = timer()
        clock.advance(Duration.parse("3h"))
        assertEquals(SleepTimerEffect.None, subject.evaluate(SleepSessionPhase.PLAYING))
        assertFalse(subject.state.value.isEnabled)
    }

    @Test
    fun theDeadlineIsMeasuredFromArmingAndIncludesDeviceSleep() {
        val (subject, clock, _) = timer()
        subject.arm(policy(minutes = 30))
        clock.advance(Duration.parse("29m"))
        assertEquals(
            "just before the deadline nothing happens",
            SleepTimerEffect.None,
            subject.evaluate(SleepSessionPhase.PLAYING)
        )
        // Time spent asleep still counts: the deadline is elapsed realtime.
        clock.advanceThroughSleep(Duration.parse("1m"))
        val effect = subject.evaluate(SleepSessionPhase.PLAYING)
        assertTrue("exactly at the deadline it stops", effect is SleepTimerEffect.StopNow)
    }

    @Test
    fun immediateExpiryStopsAtTheDeadline() {
        val (subject, clock, _) = timer()
        subject.arm(policy(minutes = 1))
        clock.advance(Duration.parse("1m"))
        val effect = subject.evaluate(SleepSessionPhase.PLAYING)
        assertTrue(effect is SleepTimerEffect.StopNow)
        val reason = (effect as SleepTimerEffect.StopNow).reason as StopReason.TimerExpired
        assertFalse("immediate expiry does not finish the exercise", reason.finishedCurrentExercise)
    }

    @Test
    fun finishCurrentDrainsThenStops() {
        val (subject, clock, _) = timer()
        subject.arm(policy(minutes = 1, expiry = SleepExpiry.FINISH_CURRENT_EXERCISE))
        clock.advance(Duration.parse("1m"))
        val effect = subject.evaluate(SleepSessionPhase.PLAYING)
        assertTrue("the exercise is allowed to finish", effect is SleepTimerEffect.DrainCurrent)
        assertTrue(subject.isDraining())

        // Repeated evaluations while draining keep asking for the item, never stop it early.
        clock.advance(Duration.parse("10s"))
        val again = subject.evaluate(SleepSessionPhase.PLAYING)
        assertTrue("draining stays a drain", again is SleepTimerEffect.DrainCurrent)

        val stopped = subject.drainComplete()
        assertTrue(stopped is SleepTimerEffect.StopNow)
        assertFalse("draining ends after the exercise", subject.isDraining())
    }

    @Test
    fun aFinishingExerciseFadesAfterExpiryRatherThanBeingMutedForTheDrain() {
        val (subject, clock, _) = timer()
        subject.arm(
            policy(
                minutes = 1,
                expiry = SleepExpiry.FINISH_CURRENT_EXERCISE,
                fadeSeconds = 10
            )
        )
        clock.advance(Duration.parse("1m"))
        val atExpiry = subject.evaluate(SleepSessionPhase.PLAYING)
        assertTrue(atExpiry is SleepTimerEffect.DrainCurrent)
        assertEquals(
            "the drain starts at full gain",
            1f,
            (atExpiry as SleepTimerEffect.DrainCurrent).gain!!,
            0.001f
        )

        clock.advance(Duration.parse("5s"))
        val half = subject.evaluate(SleepSessionPhase.PLAYING)
        assertTrue(half is SleepTimerEffect.DrainCurrent)
        assertEquals(
            "halfway through the drain fade is about half gain",
            0.5f,
            (half as SleepTimerEffect.DrainCurrent).gain!!,
            0.05f
        )
    }

    @Test
    fun finishCurrentWithNothingPlayingCompletesImmediately() {
        // There is no audible item to finish, so waiting would only keep a silent session alive.
        val (subject, clock, _) = timer()
        subject.arm(policy(minutes = 1, expiry = SleepExpiry.FINISH_CURRENT_EXERCISE))
        clock.advance(Duration.parse("1m"))
        val effect = subject.evaluate(SleepSessionPhase.PAUSED)
        assertTrue("a paused session completes immediately", effect is SleepTimerEffect.StopNow)
    }

    @Test
    fun expiryWhilePausedOrPreparingCompletesAndProhibitsResume() {
        listOf(SleepSessionPhase.PAUSED, SleepSessionPhase.PREPARING).forEach { phase ->
            val (subject, clock, _) = timer()
            subject.arm(policy(minutes = 1))
            clock.advance(Duration.parse("2m"))
            val effect = subject.evaluate(phase)
            assertTrue("$phase must complete at expiry", effect is SleepTimerEffect.StopNow)
            // A re-check keeps the session silent: audio must never reappear after expiry.
            val again = subject.evaluate(SleepSessionPhase.PLAYING)
            assertTrue("$phase must stay silent after expiry", again is SleepTimerEffect.StopNow)
            assertTrue("expiry is sticky", subject.state.value.expired)
        }
    }

    @Test
    fun aFadeRunsFromTheConfiguredWindowToTheDeadline() {
        val (subject, clock, _) = timer()
        subject.arm(policy(minutes = 1, fadeSeconds = 10))
        clock.advance(Duration.parse("50s"))
        assertEquals(
            "before the fade window the gain is untouched",
            SleepTimerEffect.None,
            subject.evaluate(SleepSessionPhase.PLAYING)
        )

        clock.advance(Duration.parse("5s"))
        val half = subject.evaluate(SleepSessionPhase.PLAYING)
        assertTrue(half is SleepTimerEffect.FadeTo)
        assertEquals(
            "halfway through the fade is about half gain",
            0.5f,
            (half as SleepTimerEffect.FadeTo).gain,
            0.05f
        )

        clock.advance(Duration.parse("5s"))
        assertTrue(subject.evaluate(SleepSessionPhase.PLAYING) is SleepTimerEffect.StopNow)
    }

    @Test
    fun theFadeIsClampedToTheDuration() {
        // A 90 second fade with a 1 minute duration must not start before the timer does.
        val clamped = SleepPolicy(
            duration = Duration.parse("1m"),
            fadeDuration = Duration.parse("90s")
        )
        assertEquals(
            "the fade is clamped to the duration and to the allowed maximum",
            Duration.parse("1m"),
            clamped.effectiveFade
        )
        // A very long fade with a long duration is clamped to the 60 s maximum.
        val longFade = SleepPolicy(
            duration = Duration.parse("30m"),
            fadeDuration = Duration.parse("90s")
        )
        assertEquals(Duration.parse("60s"), longFade.effectiveFade)
    }

    @Test
    fun cancellingRestoresTheProductGain() {
        val (subject, clock, _) = timer()
        subject.arm(policy(minutes = 1, fadeSeconds = 30))
        clock.advance(Duration.parse("50s"))
        assertTrue(subject.evaluate(SleepSessionPhase.PLAYING) is SleepTimerEffect.FadeTo)

        val restored = subject.cancel()
        assertEquals(
            "cancelling restores the unfaded gain",
            SleepTimerEffect.FadeTo(1f),
            restored
        )
        assertFalse(subject.state.value.isEnabled)
        assertNull(subject.state.value.deadline)
    }

    @Test
    fun replacingAnActiveTimerStartsANewDeadline() {
        val (subject, clock, _) = timer()
        subject.arm(policy(minutes = 5))
        clock.advance(Duration.parse("4m"))
        subject.arm(policy(minutes = 30))
        clock.advance(Duration.parse("5m"))
        assertEquals(
            "the replaced deadline does not fire early",
            SleepTimerEffect.None,
            subject.evaluate(SleepSessionPhase.PLAYING)
        )
        clock.advance(Duration.parse("25m"))
        assertTrue(subject.evaluate(SleepSessionPhase.PLAYING) is SleepTimerEffect.StopNow)
    }

    @Test
    fun repeatedStopsAfterExpiryAreIdempotent() {
        val (subject, clock, _) = timer()
        subject.arm(policy(minutes = 1))
        clock.advance(Duration.parse("1m"))
        repeat(3) {
            assertTrue(
                "a repeated check stays a stop",
                subject.evaluate(SleepSessionPhase.PLAYING) is SleepTimerEffect.StopNow
            )
        }
    }

    @Test
    fun remainingTimeCountsDownAndStopsAtZero() {
        val (subject, clock, _) = timer()
        subject.arm(policy(minutes = 2))
        clock.advance(Duration.parse("30s"))
        subject.evaluate(SleepSessionPhase.PLAYING)
        assertEquals(Duration.parse("90s"), subject.state.value.remaining)
        clock.advance(Duration.parse("2m"))
        subject.evaluate(SleepSessionPhase.PLAYING)
        // After expiry the timer is disabled, so there is no remaining time to report at all.
        val remaining = subject.state.value.remaining
        assertTrue(
            "remaining is zero or absent after expiry, never negative",
            remaining == null || remaining == Duration.ZERO
        )
        assertTrue(subject.state.value.expired)
    }

    @Test
    fun thePolicyValidatesItsBounds() {
        // An off timer has no fade to apply, whatever fade is configured alongside it.
        assertEquals(Duration.ZERO, SleepPolicy(fadeDuration = Duration.parse("30s")).effectiveFade)
        assertThrows(IllegalArgumentException::class.java) {
            SleepPolicy(duration = Duration.parse("-1m"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SleepPolicy(duration = Duration.parse("181m"))
        }
        // A fade beyond the maximum is clamped rather than rejected, so it never breaks a session.
        val clampedFade = SleepPolicy(
            duration = Duration.parse("10m"),
            fadeDuration = Duration.parse("61s")
        )
        assertEquals(Duration.parse("60s"), clampedFade.effectiveFade)
        assertNotNull(SleepPolicy(duration = Duration.parse("180m")))
    }

    @Test
    fun tickingSchedulesRepeatedEvaluationsOnlyWhileEnabled() {
        val clock = VirtualClock()
        val scheduler = ManualTimerScheduler()
        val subject = SleepTimer(clock, scheduler)
        subject.startTicking(Duration.parse("1s")) { subject.evaluate(SleepSessionPhase.PLAYING) }
        assertEquals("an unarmed timer schedules nothing", 0, scheduler.pendingCount())

        subject.arm(policy(minutes = 1))
        subject.startTicking(Duration.parse("1s")) { subject.evaluate(SleepSessionPhase.PLAYING) }
        assertEquals(1, scheduler.pendingCount())
        scheduler.runAll()
        assertEquals("ticking continues while enabled", 1, scheduler.pendingCount())
    }
}
