package com.thomas.passiveeartraining.training.recipe

import kotlin.time.Duration as TimeDuration

/**
 * How a session ends.
 *
 * Duration, count and cycle finishes are one selected policy rather than independent timers, so two
 * completion rules can never contradict each other. A duration finish can additionally drain through
 * the current cycle, which is bounded so an overrun is predictable.
 */
sealed interface SessionStopPolicy {
    /** Play until the user stops. */
    data object UntilStopped : SessionStopPolicy

    /** Stop when the duration expires. */
    data class Duration(
        val limit: TimeDuration,
        val finish: DurationFinish = DurationFinish.IMMEDIATE
    ) : SessionStopPolicy {
        init {
            require(limit > TimeDuration.ZERO) {
                "A duration limit must be positive but was $limit"
            }
        }
    }

    /** Stop after a number of completed traversals. */
    data class CompletedItems(val count: Int) : SessionStopPolicy {
        init {
            require(count in 1..MAX_ITEMS) {
                "A completed-item count is 1..$MAX_ITEMS but was $count"
            }
        }
    }

    /** Stop after the current cycle ends. */
    data object AfterCycle : SessionStopPolicy

    companion object {
        /** Most completed traversals a count policy may request. */
        const val MAX_ITEMS: Int = 10_000

        /** Longest drain the product allows for a finish-cycle or finish-current configuration. */
        val MAX_DRAIN: TimeDuration = TimeDuration.parse("15m")
    }
}

/** What a duration limit does when it expires. */
enum class DurationFinish {
    /** Stop at the deadline. */
    IMMEDIATE,

    /** Let the item that is audible at the deadline finish. */
    FINISH_CURRENT_ITEM,

    /** Finish the cycle the generator is currently in. */
    FINISH_CYCLE
}

/** Why a session ended. */
sealed interface EndingCause {
    /** The user stopped it. */
    data object UserStopped : EndingCause

    /** The duration limit was reached. */
    data class DurationReached(val drained: Boolean) : EndingCause

    /** The requested number of completed items was reached. */
    data class CompletedItemsReached(val count: Int) : EndingCause

    /** The cycle that was current when the session ended finished. */
    data object CycleFinished : EndingCause

    /** The user navigation stopped because the value earlier cannot be produced. */
    data object Failed : EndingCause
}

/**
 * Tracks whether a session should keep playing.
 *
 * It is pure: command handlers feed it what happened and it answers whether to continue, so the
 * rules are testable without playback. A skip or an incomplete playback never counts as a completed
 * traversal, and a drain is bounded so a stalled item cannot extend a session indefinitely.
 */
class SessionStopTracker(
    private val policy: SessionStopPolicy,
    private val clock: com.thomas.passiveeartraining.training.MonotonicClock,
    /** Length of the current cycle's remaining items, if the owner can measure it. */
    private val remainingCycleItems: () -> Int = { 0 },
    /** Worst-case duration of one remaining item, used to reject an oversized drain. */
    private val worstCaseItemDuration: () -> TimeDuration = { TimeDuration.ZERO }
) {
    private var completedItems = 0
    private var deadline: TimeDuration? = null
    private var draining = false
    private var drainDeadline: TimeDuration? = null
    private var ended: EndingCause? = null
    private var cycleStartIndex = 0

    /** Completed traversals so far. */
    val completed: Int get() = completedItems

    /** True while the session is finishing items it was already in rather than starting new work. */
    val isDraining: Boolean get() = draining

    /** The cause once the session has ended, or null while it is still running. */
    val endingCause: EndingCause? get() = ended

    /**
     * Rejects a configuration whose worst-case drain exceeds the bound.
     *
     * Called before a finish-cycle session starts so an overrun is reported up front rather than
     * discovered fifteen minutes later.
     */
    fun validateDrain() {
        if (policy !is SessionStopPolicy.Duration) return
        if (policy.finish == DurationFinish.IMMEDIATE) return
        val worstCase = worstCaseItemDuration() * remainingCycleItems().coerceAtLeast(0)
        if (worstCase > SessionStopPolicy.MAX_DRAIN) {
            throw RecipeValidationException(
                "Finishing the cycle could overrun by " +
                    "${worstCase.inWholeMinutes} minutes, above the " +
                    "${SessionStopPolicy.MAX_DRAIN.inWholeMinutes}-minute bound"
            )
        }
    }

    /** Called when a Start is accepted, so the deadline comes from the accepted Start. */
    fun started() {
        if (policy is SessionStopPolicy.Duration) {
            deadline = clock.elapsed() + policy.limit
        }
    }

    /**
     * Records that an item began playing.
     *
     * A traversal only counts once it completes, so a skip cannot inflate the count.
     */
    fun itemStarted(index: Int) {
        if (index == cycleStartIndex) return
        cycleStartIndex = index
    }

    /**
     * Records that the current item finished its full traversal, including its final silence.
     *
     * @return whether the session should stop now.
     */
    fun itemCompleted(index: Int): Boolean {
        completedItems += 1
        if (policy is SessionStopPolicy.CompletedItems && completedItems >= policy.count) {
            ended = EndingCause.CompletedItemsReached(completedItems)
            return true
        }
        if (draining && drainDeadline != null && clock.elapsed() >= drainDeadline!!) {
            ended = EndingCause.DurationReached(drained = true)
            return true
        }
        if (ended is EndingCause.DurationReached) {
            // A drain that has ended must not receive another item.
            return true
        }
        return false
    }

    /**
     * Records that the current item was skipped or interrupted.
     *
     * Neither counts as a completed traversal.
     */
    fun itemSkipped() {
        // Deliberately no counter change: a skip is not a completed traversal.
    }

    /**
     * Evaluates the duration limit.
     *
     * @param currentItemPlaying whether an item is audible right now; a limit reached while nothing
     *   is playing ends the session immediately, because there is nothing to finish.
     */
    fun evaluateDuration(currentItemPlaying: Boolean): Boolean {
        val active = deadline ?: return draining
        if (clock.elapsed() < active) return false
        if (ended is EndingCause.DurationReached) return true
        if (!currentItemPlaying) {
            ended = EndingCause.DurationReached(drained = false)
            deadline = null
            return true
        }
        // Only a duration policy has a finish mode; the deadline exists only for one, so this can
        // only be reached with a duration policy.
        val activePolicy = policy as? SessionStopPolicy.Duration ?: return true
        return when (activePolicy.finish) {
            DurationFinish.IMMEDIATE -> {
                ended = EndingCause.DurationReached(drained = false)
                deadline = null
                true
            }
            DurationFinish.FINISH_CURRENT_ITEM -> {
                draining = true
                // Grace is bounded by the longest item the product allows, so one item cannot extend
                // the session beyond it.
                drainDeadline = clock.elapsed() + MAX_ITEM_GRACE
                false
            }
            DurationFinish.FINISH_CYCLE -> {
                draining = true
                drainDeadline = clock.elapsed() + SessionStopPolicy.MAX_DRAIN
                false
            }
        }
    }

    /** Records that the drain finished because the cycle or the item ended. */
    fun drainFinished() {
        if (!draining) return
        draining = false
        drainDeadline = null
        ended = EndingCause.DurationReached(drained = true)
    }

    /**
     * Whether navigation is available while draining.
     *
     * Previous and Repeat are unavailable because either could extend a drain indefinitely; Next may
     * skip forward and Stop is always available.
     */
    fun allowsPrevious(): Boolean = !draining

    fun allowsRepeat(): Boolean = !draining

    fun allowsNext(): Boolean = true

    companion object {
        /**
         * Longest grace for finishing the item that is audible at the deadline.
         *
         * The product caps an exercise at 60 seconds, so this is the true upper bound.
         */
        val MAX_ITEM_GRACE: TimeDuration = TimeDuration.parse("60s")
    }
}
