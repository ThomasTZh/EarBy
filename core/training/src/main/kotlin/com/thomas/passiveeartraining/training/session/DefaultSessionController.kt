package com.thomas.passiveeartraining.training.session

import com.thomas.passiveeartraining.training.MonotonicClock
import com.thomas.passiveeartraining.training.provider.CompiledExercise
import com.thomas.passiveeartraining.training.provider.ConcreteExercise
import com.thomas.passiveeartraining.training.provider.ModeSeedDerivation
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.recipe.DurationFinish
import com.thomas.passiveeartraining.training.recipe.EndingCause
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.recipe.SessionStopTracker
import com.thomas.passiveeartraining.training.timer.SleepExpiry
import com.thomas.passiveeartraining.training.timer.SleepPolicy
import com.thomas.passiveeartraining.training.timer.SleepSessionPhase
import com.thomas.passiveeartraining.training.timer.SleepTimer
import com.thomas.passiveeartraining.training.timer.SleepTimerEffect
import com.thomas.passiveeartraining.training.timer.StopReason
import com.thomas.passiveeartraining.training.timer.TimerScheduler
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The session state machine.
 *
 * Every mutable state field is touched only on the single-threaded [commands] dispatcher: command
 * handlers and playback-progress events both serialise through `withContext(commands)`, so no two
 * mutations can interleave. Rendering and device writes never occupy that thread; they run on the
 * [playbackDispatcher], which is what keeps Pause responsive while an exercise is being rendered or
 * written.
 *
 * Queue rules:
 *
 * - exactly one concrete item is generated ahead;
 * - at most [MAX_HISTORY] concrete definitions are retained for navigating backwards;
 * - Repeat and backward navigation reuse retained definitions and draw no new randomness;
 * - navigating backwards then forwards traverses retained history before generating, so user
 *   navigation never changes the future generated sequence.
 *
 * Continuation rule: an item advances only after the device has consumed its whole timeline, never
 * when its frames have merely been submitted. A cancelled or partially consumed item pauses
 * instead.
 */
class DefaultSessionController(
    private val source: ExerciseSource,
    private val audio: AudioPort,
    private val clock: MonotonicClock,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val sampleRate: Int = 48_000,
    private val previousRestartThresholdFrames: Long = 3L * 48_000L,
    /** Worker for rendering and device writes; never the command thread. */
    private val playbackDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Schedules the sleep timer's ticks; the owner keeps the scope and cancels it on teardown. */
    private val timerScheduler: TimerScheduler = TimerScheduler { _, _ -> },
    /** Draws the one seed of a Start that did not carry one; injectable so tests are deterministic. */
    private val seedSource: () -> Long = { ThreadLocalRandom.current().nextLong() },
    private val commandDispatcher: CoroutineDispatcher? = null
) : SessionController {

    /**
     * The single-threaded dispatcher every state mutation runs on.
     *
     * One thread is what makes the state machine serialized; nothing else touches the session state.
     */
    private val commands: kotlinx.coroutines.CoroutineDispatcher =
        commandDispatcher ?: java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "session-commands")
        }.asCoroutineDispatcher()
    private val snapshot = MutableStateFlow(SessionSnapshot.IDLE)
    private val closed = AtomicBoolean(false)

    /**
     * Identifies the playback that is currently allowed to publish results.
     *
     * Every command that changes what should play bumps this token. A playback worker whose token is
     * stale returns without touching state, so a late render, a late progress callback or a late
     * write completion cannot act on a newer command.
     */
    private val playbackToken = AtomicLong(0L)
    private val playbackMutex = Mutex()
    private var itemOrdinal = 0L

    /** Identifies the active timer tick chain; bumping it stops older chains. */
    private var timerTickToken = 0L

    private var sessionId: SessionId? = null
    private var request: StartRequest? = null

    /** The audible activation's kind and acknowledged saved revision, for snapshots. */
    private var activeKind: TrainingKind? = null
    private var activeRevision: Long? = null

    /** Committed activations after the initial Start; the initial activation is ordinal 0. */
    private var activationOrdinal: Long = 0L

    /**
     * A validated destination waiting for a safe boundary.
     *
     * Read by the playback worker's boundary callback, written only on the command dispatcher, so
     * the reference is volatile rather than guarded by a lock the callback cannot take.
     */
    @Volatile
    private var pendingSwitch: PendingSwitch? = null

    private data class PendingSwitch(
        val request: StartRequest,
        val revision: Long,
        val kind: TrainingKind
    )
    private var generation = 0L

    /** Concrete items already played, newest last, bounded by [MAX_HISTORY]. */
    private val history = ArrayDeque<Item>()

    /** Items reached by navigating backwards, so moving forward restores them instead of drawing. */
    private val forward = ArrayDeque<Item>()

    private var current: Item? = null

    /** The one concrete item generated ahead of [current]. */
    private var next: Item? = null
    private var lastConsumed = 0L
    private var playedFrames = 0L
    private var answerVisible = false
    private var pauseReason: PauseReason? = null
    private var error: SessionError? = null
    private var phase = SessionPhase.IDLE

    /** The sleep timer of the active session; null while idle. */
    private var sleepTimer: SleepTimer? = null

    /** True while the timer has expired with finish-current: no next item may be generated. */
    @Volatile
    private var draining = false

    /** Why a stop-policy completion ended the session; null for a plain user stop. */
    private var endingCause: EndingCause? = null

    /** The chosen seed of the active session; published so the run can be reproduced. */
    private var sessionSeed: Long? = null

    /** The one selected stop policy tracker for the active session. */
    private var stopTracker: SessionStopTracker? = null

    /** True when a duration expiry must keep playing until the current cycle ends. */
    private var finishCycle = false
    private var fadeGain = 1f
    private var duckGain = 1f

    /** A timer armed before a session starts, applied to the next accepted Start. */
    private var pendingSleepMinutes = 0

    /** The pending timer's fade, applied with [pendingSleepMinutes]. */
    private var pendingSleepFadeSeconds = 0

    override val state: StateFlow<SessionSnapshot> = snapshot.asStateFlow()

    private class Item(
        val index: Int,
        val identity: PlaybackIdentity,
        val exercise: ConcreteExercise,
        val compiled: CompiledExercise,
        val settings: RenderSettings
    ) {
        var answerStartFrame = 0L
        var totalFrames = 0L
        var mandatoryEndFrame = 0L
        var replayFrames: List<LongRange> = emptyList()
        var trailingStartFrame = 0L
        var prepared = false
    }

    init {
        // Commands are serialized by confinement: the scope's dispatcher is single-threaded, so one
        // command runs at a time. Serialization therefore needs no channel, and releasing the
        // session cannot race an in-flight send.
        require(scope.coroutineContext[kotlinx.coroutines.Job] != null) {
            "The session scope needs a Job"
        }
        require(playbackDispatcher != commands) {
            "Playback must not run on the command thread"
        }
    }

    override suspend fun send(command: SessionCommand): CommandResult {
        // A command that arrives after release is reported as unavailable rather than throwing: a
        // disconnecting screen or a service being destroyed must never see a crash.
        if (closed.get()) {
            return closedResult()
        }
        return try {
            withContext(commands) {
                try {
                    process(command)
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    fail(failure)
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            if (closed.get()) closedResult() else throw cancelled
        }
    }

    private fun closedResult(): CommandResult = CommandResult.Unavailable(
        SessionError(SessionError.ErrorCode.UNAVAILABLE_COMMAND, "The session is closed")
    )

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        playbackToken.incrementAndGet()
        timerTickToken += 1
        try {
            audio.release()
        } finally {
            scope.cancel()
            if (commandDispatcher == null) (commands as? java.io.Closeable)?.close()
        }
    }

    /**
     * Processes one command.
     *
     * Runs on the single consumer. Command handlers never render and never wait for the device;
     * they change state and start or cancel the playback worker.
     */
    private suspend fun process(command: SessionCommand): CommandResult = when (command) {
        is SessionCommand.Start -> start(command.request)
        is SessionCommand.Pause -> pause(command.reason)
        SessionCommand.Resume -> resume()
        SessionCommand.Next -> next()
        SessionCommand.Previous -> previous()
        SessionCommand.Repeat -> repeat()
        is SessionCommand.Stop -> stop(command.reason)
        is SessionCommand.SleepTimer -> configureSleepTimer(command.minutes, command.fadeSeconds)
        is SessionCommand.DuckGain -> {
            duckGain = command.gain
            applyMasterGain()
            applied()
        }
        is SessionCommand.Reconfigure -> reconfigure(command.request)
        is SessionCommand.SwitchMode ->
            switchTo(command.request, command.savedRevision)
    }

    /**
     * Requests a mode switch (M3.1).
     *
     * Stopped sessions only acknowledge the selection; a preparing session with no frames
     * submitted activates immediately; a playing or paused session stores the destination and the
     * playback worker commits it at the next safe boundary. A destination of the audible kind is a
     * same-mode edit, not an activation.
     */
    private suspend fun switchTo(request: StartRequest, savedRevision: Long): CommandResult {
        when (phase) {
            SessionPhase.IDLE, SessionPhase.COMPLETED, SessionPhase.ERROR -> {
                // Nothing audible: the caller already persisted the selection; no audio starts.
                return applied()
            }
            SessionPhase.STOPPING -> return unavailable(
                SessionError.ErrorCode.UNAVAILABLE_COMMAND,
                "Cannot switch from $phase"
            )
            else -> Unit
        }
        val active = this.request
        val kind = request.setup.provider.kind
        if (active != null && request == active) {
            // A→A: cancel a pending departure and keep the running activation untouched.
            pendingSwitch = null
            publish()
            return applied()
        }
        // The service routes same-kind edits to Reconfigure; a SwitchMode is always an activation
        // request, so the controller never has to guess which one the caller meant.
        if (phase == SessionPhase.PREPARING) {
            val item = current ?: return applied()
            // No frames have been submitted for the old work; invalidate it and activate now.
            invalidatePlayback()
            audio.flush()
            pendingSwitch = PendingSwitch(request, savedRevision, kind)
            if (!commitPendingSwitch(playbackToken.get(), item, recordHistory = false)) {
                if (phase != SessionPhase.COMPLETED && phase != SessionPhase.ERROR) {
                    pendingSwitch = null
                }
                return applied()
            }
            launchPlayback(playbackToken.get())
            return applied()
        }
        pendingSwitch = PendingSwitch(request, savedRevision, kind)
        publish()
        return applied()
    }

    /**
     * Applies edited settings from the next item on.
     *
     * The audible item keeps the timeline and PCM it was prepared with; the one-ahead follower was
     * drawn under the previous configuration, so it is discarded and the transition after the
     * current item draws its replacement under the new one. Only a preparing, playing or paused
     * session accepts edits: while stopped there is nothing running that a later Start would not
     * already pick up.
     */
    private suspend fun reconfigure(request: StartRequest): CommandResult {
        val active = this.request ?: return unavailable(
            SessionError.ErrorCode.UNAVAILABLE_COMMAND,
            "No session"
        )
        if (phase != SessionPhase.PREPARING && phase != SessionPhase.PLAYING &&
            phase != SessionPhase.PAUSED
        ) {
            return unavailable(
                SessionError.ErrorCode.UNAVAILABLE_COMMAND,
                "Cannot reconfigure from $phase"
            )
        }
        // An edit without a seed keeps the running seed, so small changes neither draw fresh
        // randomness nor make the session irreproducible.
        val resolved = request.withResolvedSeed(
            request.setup.seed ?: requireNotNull(sessionSeed) {
                "A reconfiguring session has a resolved seed"
            }
        )
        if (resolved == active) {
            // Autosave republishes the unchanged setup, so an equal request must change nothing.
            return applied()
        }
        this.request = resolved
        sessionSeed = resolved.setup.seed
        // The source re-points at the new definition and recipe; a running generator continues
        // when the seed and the sequence policy did not change.
        source.reconfigure(resolved)
        audio.configure(resolved.musicGain, resolved.voiceGain)
        // The follower belongs to the previous configuration; redrawing it now keeps one item
        // ahead and keeps the cycle boundary exact for an edited After-one-cycle policy.
        next = null
        forward.clear()
        ensureOneAhead()
        if (resolved.setup.stopPolicy != active.setup.stopPolicy) {
            // Only a policy edit touches the tracker and the timer; every other edit leaves the
            // running deadlines and counted progress exactly where they were.
            stopTracker = SessionStopTracker(resolved.setup.stopPolicy, clock).also { it.started() }
            finishCycle = resolved.setup.stopPolicy is SessionStopPolicy.Duration &&
                (resolved.setup.stopPolicy as SessionStopPolicy.Duration).finish ==
                DurationFinish.FINISH_CYCLE
            val sleepPolicy = sleepPolicyFor(resolved.setup.stopPolicy)
            // The pending explicit timer is a one-shot arm: it is consumed here so it cannot
            // leak into a later session whose remembered choice is Off.
            pendingSleepMinutes = 0
            pendingSleepFadeSeconds = 0
            startSleepTimer(sleepPolicy)
        }
        publish()
        return applied()
    }

    private suspend fun start(request: StartRequest): CommandResult {
        if (phase == SessionPhase.PREPARING || phase == SessionPhase.PLAYING ||
            phase == SessionPhase.PAUSED
        ) {
            // Replacement needs an explicit Stop first, so a Start cannot silently abandon playback.
            return unavailable(SessionError.ErrorCode.UNAVAILABLE_COMMAND, "AlreadyActive")
        }
        return try {
            // The seed is resolved exactly once, here: a seeded request reproduces its sequence and
            // an unseeded one draws a fresh value that the snapshot publishes.
            val resolved = request.withResolvedSeed(request.setup.seed ?: seedSource())
            this.request = resolved
            sessionSeed = resolved.setup.seed
            // The accepted Start owns the configuration: the source re-points at its definition,
            // seed and recipe, and the output applies its gains, before any item is drawn.
            source.configure(resolved)
            audio.configure(resolved.musicGain, resolved.voiceGain)
            generation += 1
            sessionId = SessionId(generation)
            audio.beginSession(generation)
            activeKind = resolved.setup.provider.kind
            activeRevision = null
            activationOrdinal = 0L
            pendingSwitch = null
            history.clear()
            forward.clear()
            current = null
            next = null
            lastConsumed = 0L
            playedFrames = 0L
            answerVisible = false
            pauseReason = null
            error = null
            draining = false
            fadeGain = 1f
            duckGain = 1f
            applyMasterGain()
            endingCause = null
            finishCycle = resolved.setup.stopPolicy is SessionStopPolicy.Duration &&
                (resolved.setup.stopPolicy as SessionStopPolicy.Duration).finish ==
                DurationFinish.FINISH_CYCLE
            // The tracker counts completed traversals and drives count/cycle endings; the timer is
            // the single duration authority. Both derive from the one selected stop policy.
            stopTracker = SessionStopTracker(resolved.setup.stopPolicy, clock).also { it.started() }
            phase = SessionPhase.PREPARING
            // Drawing the first item and its one-ahead follower happens here, serialised with every
            // other queue operation: navigation must never change the generated sequence.
            current = drawItem(0)
            ensureOneAhead()
            val sleepPolicy = sleepPolicyFor(resolved.setup.stopPolicy)
            // One-shot arm: consume the pending explicit timer so a session whose remembered
            // choice is Off starts without a timer.
            pendingSleepMinutes = 0
            pendingSleepFadeSeconds = 0
            startSleepTimer(sleepPolicy)
            publish()
            val token = invalidatePlayback()
            launchPlayback(token)
            applied()
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            fail(failure)
        }
    }

    private suspend fun pause(reason: PauseReason): CommandResult {
        if (phase != SessionPhase.PLAYING && phase != SessionPhase.PAUSED) {
            return unavailable(
                SessionError.ErrorCode.UNAVAILABLE_COMMAND,
                "Cannot pause from $phase"
            )
        }
        if (phase == SessionPhase.PAUSED) {
            // A repeated pause is idempotent: it only re-silences, so no playback can be duplicated.
            audio.flush()
            return applied()
        }
        // Cancelling the worker before flushing makes a late completion callback harmless; the
        // worker's token no longer matches.
        invalidatePlayback()
        current?.let {
            accountConsumption(
                it,
                from = lastConsumed,
                to = maxOf(lastConsumed, audio.consumedFrames())
            )
        }
        audio.flush()
        phase = SessionPhase.PAUSED
        pauseReason = reason
        publish()
        return applied()
    }

    private fun pauseForFocus() {
        if (phase != SessionPhase.PLAYING && phase != SessionPhase.PREPARING) return
        // No PCM was submitted, so nothing needs silencing; the reason makes the denial observable.
        playbackToken.incrementAndGet()
        audio.flush()
        phase = SessionPhase.PAUSED
        pauseReason = PauseReason.Focus
        publish()
    }

    private fun resume(): CommandResult {
        val item = current ?: return unavailable(
            SessionError.ErrorCode.UNAVAILABLE_COMMAND,
            "Nothing prepared"
        )
        if (phase != SessionPhase.PAUSED) {
            return unavailable(
                SessionError.ErrorCode.UNAVAILABLE_COMMAND,
                "Cannot resume from $phase"
            )
        }
        // An expired deadline prohibits resume: the session completes silently instead.
        evaluateSleepTimer()?.let {
            stopForTimer(it)
            return applied()
        }
        // The retained concrete item is rendered again below if the bounded PCM cache no longer
        // holds it; resume never draws a replacement.
        phase = SessionPhase.PLAYING
        pauseReason = null
        publish()
        launchPlayback(invalidatePlayback())
        return applied()
    }

    private suspend fun next(): CommandResult = navigate(forward = true)

    private suspend fun previous(): CommandResult {
        val item = current ?: return unavailable(
            SessionError.ErrorCode.UNAVAILABLE_COMMAND,
            "Nothing prepared"
        )
        if (phase != SessionPhase.PLAYING && phase != SessionPhase.PAUSED) {
            return unavailable(
                SessionError.ErrorCode.UNAVAILABLE_COMMAND,
                "Cannot navigate from $phase"
            )
        }
        // The device position is authoritative, so it is read here rather than trusted from the
        // last command: a pause or a plain play may have advanced it since.
        accountConsumption(
            item,
            from = lastConsumed,
            to = maxOf(lastConsumed, audio.consumedFrames())
        )
        // A position within the first seconds restarts the item; beyond that it steps back.
        if (lastConsumed >= previousRestartThresholdFrames || history.isEmpty()) {
            return repeat()
        }
        val wasPaused = phase == SessionPhase.PAUSED
        invalidatePlayback()
        audio.flush()
        // The retained forward path keeps every concrete item that was already drawn ahead, so
        // moving forward restores the same sequence instead of redrawing. The drawn-ahead follower
        // goes onto the deque first, then the item being left, so the nearest item is on top.
        next?.let { forward.addLast(it) }
        forward.addLast(item)
        val earlier = history.removeLast()
        next = null
        current = earlier
        lastConsumed = 0L
        answerVisible = false
        phase = if (wasPaused) SessionPhase.PAUSED else SessionPhase.PLAYING
        pauseReason = if (wasPaused) PauseReason.User else null
        publish()
        if (!wasPaused) {
            launchPlayback(invalidatePlayback())
        }
        return applied()
    }

    /**
     * Moves forward to the retained forward item, the one generated ahead, or a fresh draw.
     *
     * A previously prepared history item is not proof its PCM remains resident; the playback
     * worker renders the selected concrete item again when it is not cached.
     */
    private suspend fun navigate(forward: Boolean): CommandResult {
        val item = current ?: return unavailable(
            SessionError.ErrorCode.UNAVAILABLE_COMMAND,
            "Nothing prepared"
        )
        if (phase != SessionPhase.PLAYING && phase != SessionPhase.PAUSED) {
            return unavailable(
                SessionError.ErrorCode.UNAVAILABLE_COMMAND,
                "Cannot navigate from $phase"
            )
        }
        if (pendingSwitch != null) {
            return unavailable(
                SessionError.ErrorCode.UNAVAILABLE_COMMAND,
                "A mode switch is pending"
            )
        }
        if (!forward) return previous()
        val wasPaused = phase == SessionPhase.PAUSED
        accountConsumption(
            item,
            from = lastConsumed,
            to = maxOf(lastConsumed, audio.consumedFrames())
        )
        invalidatePlayback()
        audio.flush()
        history.addLast(item)
        while (history.size > MAX_HISTORY) {
            history.removeFirst()
        }
        // A retained forward item is reused: navigation must not change the generated sequence.
        val upcoming = this.forward.removeLastOrNull()
            ?: next
            ?: drawItem(item.index + 1)
        next = null
        current = upcoming
        lastConsumed = 0L
        answerVisible = false
        phase = if (wasPaused) SessionPhase.PAUSED else SessionPhase.PLAYING
        pauseReason = if (wasPaused) PauseReason.User else null
        ensureOneAhead()
        publish()
        if (!wasPaused) {
            launchPlayback(invalidatePlayback())
        }
        return applied()
    }

    private suspend fun repeat(): CommandResult {
        val item = current ?: return unavailable(
            SessionError.ErrorCode.UNAVAILABLE_COMMAND,
            "Nothing prepared"
        )
        if (phase != SessionPhase.PLAYING && phase != SessionPhase.PAUSED) {
            return unavailable(
                SessionError.ErrorCode.UNAVAILABLE_COMMAND,
                "Cannot repeat from $phase"
            )
        }
        if (pendingSwitch != null) {
            return unavailable(
                SessionError.ErrorCode.UNAVAILABLE_COMMAND,
                "A mode switch is pending"
            )
        }
        val wasPaused = phase == SessionPhase.PAUSED
        accountConsumption(
            item,
            from = lastConsumed,
            to = maxOf(lastConsumed, audio.consumedFrames())
        )
        invalidatePlayback()
        audio.flush()
        // The same concrete definition is replayed; the generator is not consulted.
        lastConsumed = 0L
        answerVisible = false
        phase = if (wasPaused) SessionPhase.PAUSED else SessionPhase.PLAYING
        pauseReason = if (wasPaused) PauseReason.User else null
        publish()
        if (!wasPaused) {
            launchPlayback(invalidatePlayback())
        }
        return applied()
    }

    private fun stop(reason: PauseReason): CommandResult {
        // A Stop during Preparing wins: the token is bumped so a late render or write result cannot
        // publish itself onto a stopped session.
        playbackToken.incrementAndGet()
        timerTickToken += 1
        // The port stops any write cooperatively, then releases every device resource.
        val cleanupFailure = releaseResources()
        if (cleanupFailure != null) return fail(cleanupFailure)
        sleepTimer = null
        stopTracker = null
        draining = false
        finishCycle = false
        endingCause = null
        current = null
        next = null
        history.clear()
        forward.clear()
        pendingSwitch = null
        lastConsumed = 0L
        answerVisible = false
        error = null
        phase = SessionPhase.COMPLETED
        pauseReason = reason
        publish()
        return applied()
    }

    /**
     * Draws the one-ahead item when the queue is heading into fresh territory.
     *
     * While retained forward history exists, the next item is already concrete and nothing is drawn.
     */
    private fun ensureOneAhead() {
        if (next != null || forward.isNotEmpty()) return
        val active = current ?: return
        next = drawItem(active.index + 1)
    }

    private fun drawItem(index: Int): Item {
        val setup = requireNotNull(request)
        val exercise = source.next()
        return Item(
            index,
            PlaybackIdentity(generation, itemOrdinal++),
            exercise,
            source.compile(exercise, setup.setup.recipe),
            RenderSettings(setup.musicGain, setup.voiceGain)
        )
    }

    private fun invalidatePlayback(): Long = playbackToken.incrementAndGet()

    /**
     * Maps the one selected stop policy onto the timer.
     *
     * A pending explicit timer command wins over a saved duration, because it is the more recent
     * user decision. Count and cycle policies arm no timer at all.
     */
    private fun sleepPolicyFor(policy: SessionStopPolicy): SleepPolicy {
        if (pendingSleepMinutes > 0) {
            return SleepPolicy(
                duration = Duration.parse("${pendingSleepMinutes}m"),
                fadeDuration = Duration.parse("${pendingSleepFadeSeconds}s")
            )
        }
        val duration = policy as? SessionStopPolicy.Duration ?: return SleepPolicy.OFF
        return SleepPolicy(
            duration = duration.limit,
            expiry = when (duration.finish) {
                DurationFinish.IMMEDIATE -> SleepExpiry.IMMEDIATE
                DurationFinish.FINISH_CURRENT_ITEM, DurationFinish.FINISH_CYCLE ->
                    SleepExpiry.FINISH_CURRENT_EXERCISE
            }
        )
    }

    /**
     * Whether the completed [item] was the last of its cycle.
     *
     * The one-ahead follower is already concrete, so the boundary is exact: a follower in another
     * cycle (or no follower at all) means this cycle finished.
     */
    private fun cycleEndsAfter(item: Item): Boolean {
        val follower = next ?: forward.lastOrNull() ?: return true
        val currentCycle = item.exercise.cycle ?: return true
        val followerCycle = follower.exercise.cycle ?: return true
        return followerCycle.cycleIndex != currentCycle.cycleIndex
    }

    /** Stops the session because its selected stop policy was reached. */
    private fun stopForPolicy(cause: EndingCause?) {
        timerTickToken += 1
        playbackToken.incrementAndGet()
        val cleanupFailure = releaseResources()
        if (cleanupFailure != null) {
            fail(cleanupFailure)
            return
        }
        sleepTimer = null
        stopTracker = null
        draining = false
        finishCycle = false
        pendingSwitch = null
        endingCause = cause ?: EndingCause.UserStopped
        pauseReason = PauseReason.StopPolicyReached
        current = null
        next = null
        history.clear()
        forward.clear()
        lastConsumed = 0L
        answerVisible = false
        phase = SessionPhase.COMPLETED
        publish()
    }

    /** Creates the session's timer, resets the tick chain and publishes its initial state. */
    private fun startSleepTimer(policy: SleepPolicy) {
        sleepTimer?.cancel()
        setFadeGain(1f)
        draining = false
        val timer = SleepTimer(clock, timerScheduler)
        sleepTimer = timer
        timer.arm(policy)
        timerTickToken += 1
        if (policy.isEnabled) {
            val token = timerTickToken
            scheduleTimerTick(token)
        }
    }

    /**
     * Schedules the periodic evaluation that makes the deadline actually act.
     *
     * The chain is identified by a token so replacing or cancelling the timer stops every older
     * chain instead of accumulating ticks.
     */
    private fun scheduleTimerTick(token: Long) {
        timerScheduler.schedule(TIMER_TICK_INTERVAL) { enqueueTimerTick(token) }
    }

    private fun enqueueTimerTick(token: Long) {
        if (closed.get()) return
        scope.launch(playbackDispatcher) {
            withContext(commands) {
                if (closed.get() || token != timerTickToken ||
                    sleepTimer == null
                ) {
                    return@withContext
                }
                try {
                    evaluateSleepTimer()?.let { stopForTimer(it) }
                } catch (
                    cancelled: CancellationException
                ) {
                    throw cancelled
                } catch (failure: Exception) {
                    fail(failure)
                }
                if (token == timerTickToken &&
                    sleepTimer?.state?.value?.policy?.isEnabled == true
                ) {
                    scheduleTimerTick(token)
                }
            }
        }
    }

    private fun applyMasterGain() {
        audio.setMasterGain(fadeGain * duckGain)
    }
    private fun setFadeGain(gain: Float) {
        fadeGain = gain
        applyMasterGain()
    }

    /**
     * Evaluates the timer and applies its effect.
     *
     * @return the stop reason when the session must stop now, or null when it may continue.
     */
    private fun evaluateSleepTimer(): StopReason? {
        val timer = sleepTimer ?: return null
        return when (val effect = timer.evaluate(sleepPhase())) {
            SleepTimerEffect.None -> {
                publish()
                null
            }
            is SleepTimerEffect.FadeTo -> {
                setFadeGain(effect.gain)
                publish()
                null
            }
            is SleepTimerEffect.DrainCurrent -> {
                draining = true
                effect.gain?.let { setFadeGain(it) }
                publish()
                null
            }
            is SleepTimerEffect.StopNow -> effect.reason
        }
    }

    private fun sleepPhase(): SleepSessionPhase = when (phase) {
        SessionPhase.IDLE -> SleepSessionPhase.IDLE
        SessionPhase.PREPARING -> SleepSessionPhase.PREPARING
        SessionPhase.PLAYING -> SleepSessionPhase.PLAYING
        SessionPhase.PAUSED -> SleepSessionPhase.PAUSED
        SessionPhase.STOPPING, SessionPhase.COMPLETED -> SleepSessionPhase.COMPLETED
        SessionPhase.ERROR -> SleepSessionPhase.ERROR
    }

    /** Stops the session because the timer expired; keeps the session silent and not resumable. */
    private fun stopForTimer(reason: StopReason) {
        if (phase == SessionPhase.COMPLETED || phase == SessionPhase.ERROR) return
        timerTickToken += 1
        playbackToken.incrementAndGet()
        val cleanupFailure = releaseResources()
        if (cleanupFailure != null) {
            fail(cleanupFailure)
            return
        }
        sleepTimer = null
        stopTracker = null
        draining = false
        finishCycle = false
        pendingSwitch = null
        endingCause = when (reason) {
            is StopReason.TimerExpired -> EndingCause.DurationReached(
                reason.finishedCurrentExercise
            )
            StopReason.Other -> EndingCause.UserStopped
        }
        pauseReason = PauseReason.TimerExpired(
            (reason as? StopReason.TimerExpired)?.policy?.duration ?: Duration.ZERO
        )
        current = null
        next = null
        history.clear()
        forward.clear()
        lastConsumed = 0L
        answerVisible = false
        phase = SessionPhase.COMPLETED
        publish()
    }

    /** Applies a sleep-timer command; a positive value replaces the deadline from now. */
    private fun configureSleepTimer(minutes: Int, fadeSeconds: Int): CommandResult {
        pendingSleepMinutes = minutes
        pendingSleepFadeSeconds = fadeSeconds
        val timer = sleepTimer
        val sessionActive = timer != null && phase != SessionPhase.IDLE &&
            phase != SessionPhase.COMPLETED && phase != SessionPhase.ERROR
        if (sessionActive) {
            if (minutes == 0) {
                timerTickToken += 1
                setFadeGain(1f)
                timer!!.cancel()
                draining = false
            } else {
                timer!!.arm(
                    SleepPolicy(
                        duration = Duration.parse("${minutes}m"),
                        fadeDuration = Duration.parse("${fadeSeconds}s")
                    )
                )
                val token = ++timerTickToken
                scheduleTimerTick(token)
            }
        }
        publish()
        return applied()
    }

    /**
     * Starts the playback worker for the current concrete item and retained position.
     *
     * The worker runs off the command thread; it publishes through [withContext] on the command
     * dispatcher so all state mutation stays serialised.
     */
    private fun launchPlayback(token: Long) {
        scope.launch(playbackDispatcher) {
            playbackMutex.withLock {
                try {
                    runPlaybackLoop(token)
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    withContext(commands) { if (token == playbackToken.get()) fail(failure) }
                }
            }
        }
    }

    private suspend fun runPlaybackLoop(token: Long) {
        while (true) {
            val item = withContext(commands) {
                if (token != playbackToken.get() || closed.get()) null else current
            } ?: return
            val from = withContext(commands) {
                if (token != playbackToken.get()) null else lastConsumed
            } ?: return

            // A pending switch skips an optional unit or the trailing tail that has not started;
            // the destination activates without playing old-mode content that is still queued.
            val skipToSwitch = withContext(commands) {
                token == playbackToken.get() && !closed.get() &&
                    shouldSkipToSwitch(item, from)
            }
            if (skipToSwitch) {
                val committed = withContext(commands) { commitPendingSwitch(token, item) }
                if (!committed) return
                continue
            }

            val compiled = item.compiled
            val prepared = try {
                audio.prepare(item.identity, item.exercise, compiled, item.settings)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                withContext(commands) {
                    if (token == playbackToken.get()) fail(failure)
                }
                return
            }
            val stillCurrent = withContext(commands) {
                if (token != playbackToken.get()) {
                    false
                } else {
                    item.answerStartFrame = prepared.answerStartFrame
                    item.totalFrames = prepared.totalFrames
                    item.mandatoryEndFrame = prepared.mandatoryEndFrame
                    item.replayFrames = prepared.replayFrames
                    item.trailingStartFrame = prepared.trailingStartFrame
                    item.prepared = true
                    // Preparing is complete the moment the item is renderable; the write follows
                    // on this worker and the phase stays PLAYING until a command changes it.
                    if (phase == SessionPhase.PREPARING) {
                        phase = SessionPhase.PLAYING
                    }
                    // A traversal only counts once it completes; recording the start keeps the
                    // cycle boundary for a stop-after-cycle policy.
                    stopTracker?.itemStarted(item.index)
                    publish()
                    true
                }
            }
            if (!stillCurrent) return

            // The timer is checked immediately before any sound, so a deadline that passed while
            // rendering cannot let audio reappear after expiry.
            val sleepStop = withContext(commands) {
                if (token != playbackToken.get()) {
                    return@withContext null
                }
                evaluateSleepTimer()
            }
            if (sleepStop != null) {
                withContext(commands) { if (token == playbackToken.get()) stopForTimer(sleepStop) }
                return
            }
            if (token != playbackToken.get() || closed.get()) return

            // Focus, route and device readiness are confirmed before the writer is opened, so a
            // denied request submits no PCM at all. A late grant cannot act on a changed session.
            val permitted = try {
                audio.requestPlaybackPermission()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                withContext(commands) {
                    if (token == playbackToken.get()) fail(failure)
                }
                return
            }
            if (!permitted) {
                withContext(commands) {
                    if (token == playbackToken.get()) {
                        pauseForFocus()
                    }
                }
                return
            }
            if (token != playbackToken.get() || closed.get()) return

            val outcome = try {
                audio.play(
                    identity = item.identity,
                    startFrame = from,
                    boundaries = unitBoundaries(item, from),
                    isCancelled = { token != playbackToken.get() || closed.get() },
                    continueAtBoundary = { pendingSwitch == null || draining },
                    onConsumed = { consumed ->
                        // Live position and answer visibility while audio is active.
                        withContext(commands) {
                            if (token == playbackToken.get()) {
                                accountConsumption(
                                    item,
                                    from = lastConsumed,
                                    to = consumed
                                )
                                publish()
                            }
                        }
                    }
                )
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                withContext(commands) {
                    if (token == playbackToken.get()) fail(failure)
                }
                return
            }

            val shouldContinue = withContext(commands) {
                onPlaybackReturned(token, item, from, outcome)
            }
            if (!shouldContinue) return
        }
    }

    /**
     * Applies a finished playback call on the command thread.
     *
     * @return whether the worker should continue with the next activation or item.
     */
    private fun onPlaybackReturned(
        token: Long,
        item: Item,
        from: Long,
        outcome: AudioPort.PlayOutcome
    ): Boolean {
        if (token != playbackToken.get() || closed.get()) return false
        if (phase != SessionPhase.PLAYING) return false
        val fullyConsumed = outcome.completed && outcome.consumedFrames >= item.totalFrames
        if (fullyConsumed) {
            accountConsumption(item, from = lastConsumed, to = item.totalFrames)
            // The stop policy is consulted only at a full consumed boundary: a skip never counts.
            val tracker = stopTracker
            if (tracker != null) {
                val stopsNow = tracker.itemCompleted(item.index)
                if (stopsNow) {
                    stopForPolicy(tracker.endingCause)
                    return false
                }
                val policy = request?.setup?.stopPolicy
                if (policy == SessionStopPolicy.AfterCycle && cycleEndsAfter(item)) {
                    stopForPolicy(EndingCause.CycleFinished)
                    return false
                }
            }
            if (draining) {
                // A finish-current drain ends with the audible item unless the duration policy asked
                // to finish the whole cycle and that cycle is still running.
                if (!(finishCycle && !cycleEndsAfter(item))) {
                    val timer = sleepTimer
                    runCatching { timer?.drainComplete() }
                    stopForTimer(
                        StopReason.TimerExpired(
                            policy = timer?.state?.value?.policy ?: SleepPolicy.OFF,
                            finishedCurrentExercise = true
                        )
                    )
                    return false
                }
            }
            if (pendingSwitch != null) {
                // The old item was heard in full before any boundary could refuse; activate the
                // destination instead of drawing a new old-mode item.
                return commitPendingSwitch(token, item)
            }
            // The item is fully consumed: continue with the next concrete item.
            val upcoming = next
                ?: forward.removeLastOrNull()
                ?: drawItem(item.index + 1)
            history.addLast(item)
            while (history.size > MAX_HISTORY) {
                history.removeFirst()
            }
            next = null
            current = upcoming
            lastConsumed = 0L
            answerVisible = false
            ensureOneAhead()
            publish()
            return true
        }
        val stoppedAt = outcome.stoppedAtBoundary
        if (stoppedAt != null) {
            // Submission stopped because a unit boundary was not authorized: the unit in progress
            // was heard in full, and the switch commits now.
            accountConsumption(item, from = lastConsumed, to = stoppedAt)
            if (pendingSwitch != null) {
                return commitPendingSwitch(token, item)
            }
        }
        // An interrupted write without a pause command (for example a device short write) pauses
        // rather than silently dropping the remaining audio.
        accountConsumption(
            item,
            from = lastConsumed,
            to = maxOf(lastConsumed, outcome.consumedFrames)
        )
        phase = SessionPhase.PAUSED
        pauseReason = PauseReason.User
        publish()
        return false
    }

    /**
     * The unit ends the playback worker may submit across, in order.
     *
     * The mandatory unit, each optional replay and the trailing tail; the item end is always last.
     * Boundaries at or before the current position are omitted, so a resumed mid-unit position
     * still has its own unit end.
     */
    private fun unitBoundaries(item: Item, from: Long): List<Long> {
        val ends = mutableListOf<Long>()
        if (item.mandatoryEndFrame > from) ends += item.mandatoryEndFrame
        item.replayFrames.forEach { range ->
            val endExclusive = range.last + 1
            if (endExclusive > from) ends += endExclusive
        }
        if (item.totalFrames > from) ends += item.totalFrames
        return ends.distinct().sorted()
    }

    /**
     * True when a pending switch may activate without playing what is left.
     *
     * Only units that have not started are skipped: inside the mandatory unit the unit is finished
     * first, inside a replay the replay is finished, and inside the trailing tail the tail is
     * finished (cutting the separator cue mid-way would be audible).
     */
    private fun shouldSkipToSwitch(item: Item, from: Long): Boolean {
        if (pendingSwitch == null) return false
        if (from < item.mandatoryEndFrame) return false
        val nextReplay = item.replayFrames.firstOrNull { from < it.last + 1 }
        if (nextReplay != null) return from <= nextReplay.first
        return from <= item.trailingStartFrame && from < item.totalFrames
    }

    /**
     * Activates the pending destination on the command thread.
     *
     * The activation ordinal increments only here, a cancelled preparation never advances any
     * retained stream, and the destination's fixed seed wins over the derived activation seed
     * (SC3/D16). A finish-current drain owns the ending and keeps playing the old units.
     *
     * @return whether the playback worker should continue with the destination.
     */
    private fun commitPendingSwitch(
        token: Long,
        item: Item,
        recordHistory: Boolean = true
    ): Boolean {
        if (token != playbackToken.get() || closed.get()) return false
        val pending = pendingSwitch ?: return false
        if (draining) return true
        evaluateSleepTimer()?.let {
            stopForTimer(it)
            return false
        }
        val parentSeed = sessionSeed ?: return false
        val ordinal = activationOrdinal + 1L
        val seed = pending.request.setup.seed
            ?: ModeSeedDerivation.derive(
                parentSeed,
                pending.kind,
                ordinal,
                ModeSeedDerivation.ACTIVATION
            )
        val resolved = pending.request.withResolvedSeed(seed)
        if (recordHistory) {
            history.addLast(item)
            while (history.size > MAX_HISTORY) {
                history.removeFirst()
            }
        }
        forward.clear()
        next = null
        request = resolved
        return try {
            source.configure(resolved)
            activationOrdinal = ordinal
            activeKind = pending.kind
            activeRevision = pending.revision
            pendingSwitch = null
            answerVisible = false
            lastConsumed = 0L
            current = drawItem(item.index + 1)
            phase = SessionPhase.PREPARING
            publish()
            true
        } catch (failure: Exception) {
            fail(failure)
            false
        }
    }

    /**
     * Adds the frames consumed since [from] to the cumulative played duration and evaluates whether
     * the answer has started.
     *
     * Logical position is the device's consumed audio, so intentional timeline silence counts as
     * played time and analysis is never inferred from wall-clock time.
     */
    private fun accountConsumption(item: Item, from: Long, to: Long) {
        val consumed = to.coerceIn(0L, item.totalFrames)
        if (consumed > from) {
            playedFrames += consumed - from
        }
        // A later callback from the same item can never move the position backwards.
        lastConsumed = maxOf(lastConsumed, consumed).coerceAtMost(item.totalFrames)
        if (!answerVisible && lastConsumed >= item.answerStartFrame) {
            // Published only once its frame is consumed, never when queued or rendered.
            answerVisible = true
        }
    }

    /** Attempts every cleanup step and preserves failures for the observable error path. */
    private fun releaseResources(): Exception? {
        var failure: Exception? = null
        val operations: List<() -> Unit> = listOf(
            { audio.flush() },
            { audio.release() },
            { setFadeGain(1f) },
            {
                sleepTimer?.cancel()
                Unit
            }
        )
        for (operation in operations) {
            try {
                operation()
            } catch (caught: Exception) {
                val first = failure
                if (first ==
                    null
                ) {
                    failure = caught
                } else if (first !== caught) {
                    first.addSuppressed(caught)
                }
            }
        }
        if (failure is CancellationException) throw failure
        return failure
    }

    private fun fail(failure: Throwable): CommandResult {
        playbackToken.incrementAndGet()
        answerVisible = false
        current = null
        next = null
        history.clear()
        forward.clear()
        timerTickToken += 1
        releaseResources()?.let { if (it !== failure) failure.addSuppressed(it) }
        sleepTimer = null
        stopTracker = null
        draining = false
        finishCycle = false
        pendingSwitch = null
        phase = SessionPhase.ERROR
        val code = when (failure) {
            is AudioPortException -> SessionError.ErrorCode.OUTPUT_FAILURE
            else -> SessionError.ErrorCode.ASSET_UNAVAILABLE
        }
        error = SessionError(code, failure.message ?: failure::class.java.simpleName)
        pauseReason = null
        publish()
        return applied()
    }

    private fun unavailable(code: SessionError.ErrorCode, detail: String): CommandResult {
        // The snapshot is republished so a caller always sees the phase it was rejected in.
        publish()
        return CommandResult.Unavailable(SessionError(code, detail))
    }

    private fun applied(): CommandResult = CommandResult.Applied(snapshot.value)

    /** Publishes the current state, including the controls the UI may offer. */
    private fun publish() {
        val item = current
        snapshot.value = SessionSnapshot(
            session = sessionId,
            phase = phase,
            itemIndex = item?.index ?: 0,
            answerVisible = answerVisible && request?.answerVisibility !=
                com.thomas.passiveeartraining.training.settings.AnswerVisibility.HIDDEN,
            consumedFrames = lastConsumed,
            totalFrames = item?.totalFrames ?: 0L,
            playedDuration = Duration.parse("${playedFrames * 1_000_000L / sampleRate}us"),
            pauseReason = pauseReason,
            error = error,
            controls = controlsFor(item),
            hasNext = next != null || forward.isNotEmpty() || item != null,
            hasPrevious = history.isNotEmpty() || (item != null && lastConsumed > 0L),
            sleep =
            sleepTimer?.state?.value
                ?: com.thomas.passiveeartraining.training.timer.SleepTimerState(),
            endingCause = endingCause,
            seed = sessionSeed,
            activeKind = activeKind,
            pendingKind = pendingSwitch?.kind,
            activeRevision = activeRevision,
            pendingRevision = pendingSwitch?.revision,
            // The concrete answer is part of the snapshot only after its frame was consumed; the
            // hidden-answer rule is enforced here, not only at the rendering surface.
            answerLabel = if (answerVisible && request?.answerVisibility !=
                com.thomas.passiveeartraining.training.settings.AnswerVisibility.HIDDEN
            ) {
                current?.exercise?.answer?.display?.label()
            } else {
                null
            },
            // Owner-requested Play debug readout: the generated notes are visible while the
            // current item is playing. This intentionally sits outside the hidden-answer rule.
            currentNotes = if (phase == SessionPhase.PLAYING) {
                item?.exercise?.groups
                    ?.flatMap { group -> group.notes }
                    ?.map { note -> note.value }
                    ?.distinct()
                    ?: emptyList()
            } else {
                emptyList()
            }
        )
    }

    private fun controlsFor(item: Item?): AvailableControls = AvailableControls(
        canStart =
        phase == SessionPhase.IDLE || phase == SessionPhase.COMPLETED ||
            phase == SessionPhase.ERROR,
        canPause = phase == SessionPhase.PLAYING,
        canResume = phase == SessionPhase.PAUSED,
        canNext = item != null && phase != SessionPhase.PREPARING && phase != SessionPhase.STOPPING,
        canPrevious =
        item != null && phase != SessionPhase.PREPARING && phase != SessionPhase.STOPPING,
        canRepeat =
        item != null && phase != SessionPhase.PREPARING && phase != SessionPhase.STOPPING,
        canStop = phase != SessionPhase.IDLE && phase != SessionPhase.COMPLETED
    )

    companion object {
        /** Concrete definitions retained for backward navigation. */
        const val MAX_HISTORY: Int = 20

        /** How often the sleep timer is re-evaluated while a policy is active. */
        val TIMER_TICK_INTERVAL: Duration = Duration.parse("250ms")
    }
}
