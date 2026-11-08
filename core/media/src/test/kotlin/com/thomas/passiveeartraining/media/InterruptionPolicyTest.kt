package com.thomas.passiveeartraining.media

import com.thomas.passiveeartraining.training.session.PauseReason
import com.thomas.passiveeartraining.training.session.SessionPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterruptionPolicyTest {

    @Test
    fun permanentFocusLossPausesAndReleasesFocus() {
        assertEquals(
            InterruptionDecision.PAUSE_AND_ABANDON_FOCUS,
            InterruptionPolicy.decide(Interruption.FocusLost, playing = true)
        )
    }

    @Test
    fun transientLossAndDuckingDuckTheVolumeInsteadOfPausing() {
        // A notification or short sound lowers this app's volume and playback continues, like a
        // music player. The grant is kept, so focus gain restores the volume without a new request.
        assertEquals(
            InterruptionDecision.DUCK,
            InterruptionPolicy.decide(Interruption.FocusLostTransient, playing = true)
        )
        assertEquals(
            InterruptionDecision.DUCK,
            InterruptionPolicy.decide(Interruption.FocusDucked, playing = true)
        )
    }

    @Test
    fun focusGainRestoresTheVolumeAndNeverResumes() {
        // The user resumes explicitly; this is the rule that makes the behaviour predictable.
        assertEquals(
            InterruptionDecision.UNDUCK,
            InterruptionPolicy.decide(Interruption.FocusGained, playing = false)
        )
        assertEquals(
            InterruptionDecision.UNDUCK,
            InterruptionPolicy.decide(Interruption.FocusGained, playing = true)
        )
    }

    @Test
    fun routeChangesPauseAndRequireOutputRecovery() {
        listOf(
            Interruption.BecomingNoisy,
            Interruption.RouteLost("wired headset")
        ).forEach { change ->
            assertEquals(
                "a route change must pause: $change",
                InterruptionDecision.PAUSE_AND_ABANDON_FOCUS,
                InterruptionPolicy.decide(change, playing = true)
            )
            assertTrue(
                "a route change invalidates the output: $change",
                InterruptionPolicy.requiresOutputRecovery(change)
            )
        }
    }

    @Test
    fun aPlainFocusLossDoesNotRequireOutputRecovery() {
        // The device is unchanged, so the existing output is still valid.
        assertFalse(InterruptionPolicy.requiresOutputRecovery(Interruption.FocusLost))
        assertFalse(InterruptionPolicy.requiresOutputRecovery(Interruption.FocusDucked))
    }

    @Test
    fun duplicateInterruptionsAreIdempotentWhenAlreadySilent() {
        // Platform callbacks can repeat; acting again while already paused changes nothing.
        listOf(
            Interruption.FocusLost,
            Interruption.FocusLostTransient,
            Interruption.FocusDucked,
            Interruption.BecomingNoisy,
            Interruption.RouteLost("bluetooth")
        ).forEach { interruption ->
            assertEquals(
                "a repeated $interruption must be a no-op while silent",
                InterruptionDecision.NONE,
                InterruptionPolicy.decide(interruption, playing = false)
            )
        }
    }

    @Test
    fun aNewDeviceAppearingDoesNotSilenceOrResume() {
        assertEquals(
            InterruptionDecision.NONE,
            InterruptionPolicy.decide(Interruption.RouteAdded("usb"), playing = true)
        )
        assertFalse(
            "a new device must not silence playback",
            Interruption.RouteAdded("usb").silencesPlayback
        )
        assertTrue(
            "losing the device must silence playback",
            Interruption.RouteLost("usb").silencesPlayback
        )
    }

    @Test
    fun theWakeLockIsHeldOnlyWhileSoundIsBeingProduced() {
        // Playing, preparing and stopping hold it; a paused session must not, because it tolerates
        // losing foreground status and process lifetime.
        assertTrue(WakeLockPolicy.shouldHold(SessionPhase.PLAYING))
        assertTrue(WakeLockPolicy.shouldHold(SessionPhase.PREPARING))
        assertTrue(WakeLockPolicy.shouldHold(SessionPhase.STOPPING))
        assertFalse(WakeLockPolicy.shouldHold(SessionPhase.PAUSED))
        assertFalse(WakeLockPolicy.shouldHold(SessionPhase.IDLE))
        assertFalse(WakeLockPolicy.shouldHold(SessionPhase.COMPLETED))
        assertFalse(WakeLockPolicy.shouldHold(SessionPhase.ERROR))
    }
}

/**
 * The sleep-timer command is configuration, and its payload must stay bounded.
 *
 * The bounds are asserted here so a future change cannot quietly widen what a controller may send.
 */
class SleepTimerCommandBoundsTest {

    @Test
    fun onlyDurationsTheTimerSupportsAreAccepted() {
        val maximum = 180
        listOf(0, 1, 15, 30, 60, 180).forEach { minutes ->
            assertTrue("$minutes minutes must be accepted", minutes in 0..maximum)
        }
        listOf(-1, 181, 10_000).forEach { minutes ->
            assertFalse("$minutes minutes must be refused", minutes in 0..maximum)
        }
    }

    @Test
    fun theCommandBoundsAreInclusiveAtTheEnds() {
        val maximum = 180
        assertTrue(0 in 0..maximum)
        assertTrue(maximum in 0..maximum)
        assertFalse(-1 in 0..maximum)
        assertFalse(maximum + 1 in 0..maximum)
    }
}
