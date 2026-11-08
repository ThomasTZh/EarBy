package com.thomas.passiveeartraining.media

import android.os.Handler
import com.google.common.util.concurrent.AbstractFuture
import com.google.common.util.concurrent.ListenableFuture
import com.thomas.passiveeartraining.training.session.CommandResult
import com.thomas.passiveeartraining.training.session.SessionCommand
import com.thomas.passiveeartraining.training.session.SessionController
import com.thomas.passiveeartraining.training.session.SessionPhase
import com.thomas.passiveeartraining.training.session.SessionSnapshot
import com.thomas.passiveeartraining.training.session.StartRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Forwards transport commands to the session and reports their outcome.
 *
 * Commands run on the session's own scope, so Media3's application looper is never blocked on
 * native audio work. Results are delivered back on the player's looper, which keeps state publication
 * single-threaded.
 */
class TrainingTransport(
    private val controller: SessionController,
    private val handler: Handler,
    private val requestFactory: suspend () -> StartRequest?,
    /** The remembered sleep-timer choice in minutes; zero means no timer. */
    private val startSleepMinutes: () -> Int = { 0 }
) {
    private val commandMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Session state, observed by the player so it republishes when it changes. */
    val state: StateFlow<SessionSnapshot> get() = controller.state

    /** Current snapshot, read whenever the player publishes state. */
    val snapshot: SessionSnapshot get() = controller.state.value

    /** Begins a session or ends one; there is no pause in this product. */
    fun playWhenReady(playWhenReady: Boolean): ListenableFuture<Unit> {
        if (!playWhenReady) {
            return send(SessionCommand.Stop())
        }
        return submit {
            when (snapshot.phase) {
                SessionPhase.PAUSED -> {
                    controller.send(SessionCommand.Stop())
                    val request = requestFactory() ?: error("No saved setup is available")
                    armRememberedSleepTimer()
                    controller.send(SessionCommand.Start(request))
                }
                SessionPhase.IDLE, SessionPhase.COMPLETED, SessionPhase.ERROR -> {
                    val request = requestFactory() ?: error("No saved setup is available")
                    armRememberedSleepTimer()
                    controller.send(SessionCommand.Start(request))
                }
                else -> CommandResult.Applied(snapshot)
            }
        }
    }

    /**
     * Arms the stored sleep-timer choice for the coming Start.
     *
     * Only a positive value is sent: a stored Off must not cancel a timer that the caller armed
     * explicitly just before starting (for example the endurance harness or a future schedule).
     */
    private suspend fun armRememberedSleepTimer() {
        val minutes = startSleepMinutes()
        if (minutes > 0) {
            controller.send(SessionCommand.SleepTimer(minutes))
        }
    }

    fun next(): ListenableFuture<Unit> = send(SessionCommand.Next)

    fun previous(): ListenableFuture<Unit> = send(SessionCommand.Previous)

    fun repeatItem(): ListenableFuture<Unit> = send(SessionCommand.Repeat)

    /** Arms or cancels the session's sleep timer; the controller stores the policy. */
    fun sleepTimer(minutes: Int): ListenableFuture<Unit> = send(SessionCommand.SleepTimer(minutes))

    /** Applies a platform duck to the output volume; 1 restores the product gain. */
    fun setDuckGain(gain: Float): ListenableFuture<Unit> = send(SessionCommand.DuckGain(gain))

    /** Applies edited settings to a running session; the next pair uses them. */
    fun reconfigure(request: StartRequest): ListenableFuture<Unit> =
        send(SessionCommand.Reconfigure(request))

    /** Requests a mode switch that commits at the next safe boundary (M3). */
    fun switchMode(request: StartRequest, revision: Long): ListenableFuture<Unit> =
        send(SessionCommand.SwitchMode(request, revision))

    fun stop(): ListenableFuture<Unit> = send(SessionCommand.Stop())

    /** Closes the session and the scope this transport owns. */
    fun close() {
        try {
            controller.close()
        } finally {
            scope.cancel()
        }
    }

    private fun send(command: SessionCommand): ListenableFuture<Unit> = submit {
        controller.send(command)
    }

    private fun submit(action: suspend () -> CommandResult): ListenableFuture<Unit> {
        val future = CommandFuture.create<Unit>()
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val result = commandMutex.withLock { action() }
                handler.post {
                    when (result) {
                        is CommandResult.Applied -> {
                            val failure = result.snapshot.error
                            if (failure != null) {
                                future.setException(
                                    IllegalStateException(failure.detail)
                                )
                            } else {
                                future.set(Unit)
                            }
                        }
                        is CommandResult.Unavailable -> future.setException(
                            IllegalStateException(result.reason.detail)
                        )
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                future.cancel(false)
                throw cancelled
            } catch (failure: Exception) {
                handler.post { future.setException(failure) }
            }
        }
        job.invokeOnCompletion { if (it != null) future.cancel(false) }
        return future
    }

    private fun completed(): ListenableFuture<Unit> =
        CommandFuture.create<Unit>().apply { set(Unit) }
}

/**
 * A future completed by the transport.
 *
 * Guava's `AbstractFuture` decides completion itself, so the transport only reports the outcome;
 * listeners registered by Media3 run on the player's looper because it adds them there.
 */
typealias CommandFuture = com.google.common.util.concurrent.SettableFuture<Unit>
