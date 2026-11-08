package com.thomas.passiveeartraining.training

import kotlin.time.Duration

/**
 * Elapsed-time source for session work.
 *
 * The domain measures deadlines against this clock, never against wall-clock time, so device
 * clock changes cannot move a sleep deadline. [elapsed] must be non-decreasing; a source that
 * moves backwards is a programming error and must fail loudly rather than rewind a session.
 *
 * Stage 1 defines the port and a test fake only; the timeline compiler never reads a clock.
 */
interface MonotonicClock {
    /** Time since an arbitrary fixed origin, including time spent with the device asleep. */
    fun elapsed(): Duration
}

/** Raised when a monotonic clock would move backwards. */
class ClockWentBackwardsException(previous: Duration, requested: Duration) :
    IllegalStateException(
        "Monotonic clock moved backwards from $previous to $requested"
    )

/**
 * Test clock that only accepts forward movement.
 *
 * Production code uses an `elapsedRealtime` adapter added with the media service; this fake keeps
 * session tests deterministic and makes a backwards jump an explicit failure.
 */
class FakeMonotonicClock(start: Duration = Duration.ZERO) : MonotonicClock {
    private var current: Duration = start

    override fun elapsed(): Duration = current

    /** Advances the clock; [delta] must not be negative. */
    fun advanceBy(delta: Duration) {
        if (delta.isNegative()) {
            throw ClockWentBackwardsException(current, current + delta)
        }
        current += delta
    }

    /** Moves the clock to an absolute elapsed value; values behind [elapsed] are rejected. */
    fun moveTo(target: Duration) {
        if (target < current) {
            throw ClockWentBackwardsException(current, target)
        }
        current = target
    }
}
