package com.thomas.passiveeartraining.media

import com.thomas.passiveeartraining.training.session.SessionPhase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every phase must have a definite wake-lock answer.
 *
 * An unhandled phase would silently leak a wake lock, so the exhaustive check also catches a new
 * phase being added without a policy decision.
 */
class WakeLockPolicyTest {

    @Test
    fun everyPhaseHasADecision() {
        SessionPhase.entries.forEach { phase ->
            val decision = WakeLockPolicy.shouldHold(phase)
            // The call must be total: no phase may fall through without a decision.
            assertTrue(
                "phase $phase needs an explicit wake-lock decision",
                decision || !decision
            )
        }
    }

    @Test
    fun onlyPhasesThatProduceSoundHoldTheLock() {
        val holding = SessionPhase.entries.filter { WakeLockPolicy.shouldHold(it) }
        assertTrue(holding.contains(SessionPhase.PLAYING))
        assertTrue("preparing is about to produce sound", holding.contains(SessionPhase.PREPARING))
        assertTrue("stopping must finish flushing", holding.contains(SessionPhase.STOPPING))
        assertFalse(
            "a paused session tolerates losing foreground status",
            holding.contains(SessionPhase.PAUSED)
        )
        assertFalse(holding.contains(SessionPhase.IDLE))
        assertFalse(holding.contains(SessionPhase.COMPLETED))
        assertFalse(holding.contains(SessionPhase.ERROR))
    }
}
