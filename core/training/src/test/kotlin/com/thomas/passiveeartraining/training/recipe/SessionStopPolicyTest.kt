package com.thomas.passiveeartraining.training.recipe

import com.thomas.passiveeartraining.training.MonotonicClock
import kotlin.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private class VirtualClock(private var now: Duration = Duration.ZERO) : MonotonicClock {
    override fun elapsed(): Duration = now

    fun advance(delta: Duration) {
        now += delta
    }
}

class SessionStopPolicyTest {

    private fun countPolicy(count: Int, clock: VirtualClock = VirtualClock()) =
        SessionStopTracker(SessionStopPolicy.CompletedItems(count), clock) to clock

    private fun durationPolicy(
        limit: String,
        finish: DurationFinish,
        clock: VirtualClock = VirtualClock(),
        remainingItems: Int = 0,
        worstCase: String = "6s"
    ) = SessionStopTracker(
        policy = SessionStopPolicy.Duration(Duration.parse(limit), finish),
        clock = clock,
        remainingCycleItems = { remainingItems },
        worstCaseItemDuration = { Duration.parse(worstCase) }
    ) to clock

    @Test
    fun aCountPolicyStopsAfterTheRequestedCompletedItems() {
        val (tracker, _) = countPolicy(3)
        tracker.started()
        assertFalse("the first item is not the last", tracker.itemCompleted(0))
        assertFalse(tracker.itemCompleted(1))
        assertTrue("the third completed item ends the session", tracker.itemCompleted(2))
        assertEquals(3, tracker.completed)
        assertEquals(EndingCause.CompletedItemsReached(3), tracker.endingCause)
    }

    @Test
    fun aSkippedItemDoesNotCountAsCompleted() {
        // A skip is not a completed traversal, so the count can only be reached by listening through.
        val (tracker, _) = countPolicy(2)
        tracker.started()
        tracker.itemSkipped()
        tracker.itemSkipped()
        assertEquals("skips must not advance the count", 0, tracker.completed)
        assertFalse(tracker.itemCompleted(0))
        assertTrue(tracker.itemCompleted(1))
        assertEquals(2, tracker.completed)
    }

    @Test
    fun aRepeatedItemCountsAgainWhenFullyPlayed() {
        // Repeating a full item is a completed traversal, so it advances the count.
        val (tracker, _) = countPolicy(2)
        assertFalse(tracker.itemCompleted(0))
        assertTrue("the same item played again completes the count", tracker.itemCompleted(0))
    }

    @Test
    fun countBoundsAreEnforced() {
        assertThrows(IllegalArgumentException::class.java) { SessionStopPolicy.CompletedItems(0) }
        assertThrows(IllegalArgumentException::class.java) {
            SessionStopPolicy.CompletedItems(10_001)
        }
        SessionStopPolicy.CompletedItems(10_000)
    }

    @Test
    fun aDurationLimitStopsImmediatelyByDefault() {
        val (tracker, clock) = durationPolicy("5m", DurationFinish.IMMEDIATE)
        tracker.started()
        clock.advance(Duration.parse("4m59s"))
        assertFalse(
            "before the deadline nothing stops",
            tracker.evaluateDuration(currentItemPlaying = true)
        )
        clock.advance(Duration.parse("1s"))
        assertTrue(
            "at the deadline an immediate finish stops",
            tracker.evaluateDuration(currentItemPlaying = true)
        )
        assertEquals(EndingCause.DurationReached(drained = false), tracker.endingCause)
    }

    @Test
    fun finishCurrentItemDrainsOneItemAndThenStops() {
        val (tracker, clock) = durationPolicy("1m", DurationFinish.FINISH_CURRENT_ITEM)
        tracker.started()
        clock.advance(Duration.parse("1m"))
        assertFalse(
            "the audible item is allowed to finish",
            tracker.evaluateDuration(currentItemPlaying = true)
        )
        assertTrue("the session is draining", tracker.isDraining)
        assertFalse("the count is unaffected by the drain", tracker.itemCompleted(0))
        tracker.drainFinished()
        assertEquals(EndingCause.DurationReached(drained = true), tracker.endingCause)
        assertFalse("the drain is over", tracker.isDraining)
    }

    @Test
    fun aLimitReachedWithNothingPlayingStopsAtOnce() {
        // There is no audible item to finish, so waiting would only keep a silent session alive.
        val (tracker, clock) = durationPolicy("1m", DurationFinish.FINISH_CURRENT_ITEM)
        tracker.started()
        clock.advance(Duration.parse("1m"))
        assertTrue(
            "nothing is playing, so it stops now",
            tracker.evaluateDuration(currentItemPlaying = false)
        )
        assertEquals(EndingCause.DurationReached(drained = false), tracker.endingCause)
    }

    @Test
    fun aDrainCannotOutlastItsBound() {
        val (tracker, clock) = durationPolicy("1m", DurationFinish.FINISH_CURRENT_ITEM)
        tracker.started()
        clock.advance(Duration.parse("1m"))
        tracker.evaluateDuration(currentItemPlaying = true)
        // The grace is the longest item the product allows; an item that overruns it ends the
        // session rather than extending it.
        clock.advance(SessionStopTracker.MAX_ITEM_GRACE + Duration.parse("1s"))
        assertTrue("a stalled item must not extend the session", tracker.itemCompleted(0))
        assertEquals(EndingCause.DurationReached(drained = true), tracker.endingCause)
    }

    @Test
    fun anOversizedCycleDrainIsRejectedUpFront() {
        // Twelve remaining items of six seconds each is 72 seconds, which is fine; the bound is
        // fifteen minutes, so a cycle that could overrun it is refused before the session starts.
        val (acceptable, _) = durationPolicy("1m", DurationFinish.FINISH_CYCLE, remainingItems = 12)
        acceptable.validateDrain()

        val (oversized, _) = durationPolicy(
            limit = "1m",
            finish = DurationFinish.FINISH_CYCLE,
            remainingItems = 200,
            worstCase = "10s"
        )
        val error =
            assertThrows(RecipeValidationException::class.java) { oversized.validateDrain() }
        assertTrue("the failure must name the bound", error.message!!.contains("bound"))
    }

    @Test
    fun navigationIsRestrictedOnlyWhileDraining() {
        val (draining, clock) = durationPolicy("1m", DurationFinish.FINISH_CYCLE)
        draining.started()
        clock.advance(Duration.parse("1m"))
        draining.evaluateDuration(currentItemPlaying = true)
        assertFalse(
            "Previous could extend a drain, so it is unavailable",
            draining.allowsPrevious()
        )
        assertFalse("Repeat could extend a drain, so it is unavailable", draining.allowsRepeat())
        assertTrue("Next may skip forward while draining", draining.allowsNext())

        val (running, _) = durationPolicy("10m", DurationFinish.FINISH_CYCLE)
        running.started()
        assertTrue("navigation is available before a drain", running.allowsPrevious())
        assertTrue(running.allowsRepeat())
    }

    @Test
    fun untilStoppedAndAfterCycleHaveNoDeadline() {
        val untilStopped = SessionStopTracker(SessionStopPolicy.UntilStopped, VirtualClock())
        untilStopped.started()
        assertFalse(untilStopped.evaluateDuration(currentItemPlaying = true))
        assertEquals(
            "nothing ends an until-stopped session on its own",
            null,
            untilStopped.endingCause
        )

        val afterCycle = SessionStopTracker(SessionStopPolicy.AfterCycle, VirtualClock())
        afterCycle.started()
        assertFalse(afterCycle.evaluateDuration(currentItemPlaying = true))
        assertTrue(
            "cycle-based stopping imposes no navigation restriction",
            afterCycle.allowsPrevious()
        )
    }
}
