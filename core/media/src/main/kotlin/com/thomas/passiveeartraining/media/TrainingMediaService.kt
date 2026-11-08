package com.thomas.passiveeartraining.media

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.thomas.passiveeartraining.training.MonotonicClock
import com.thomas.passiveeartraining.training.session.AudioPort
import com.thomas.passiveeartraining.training.session.AudioPortException
import com.thomas.passiveeartraining.training.session.DefaultSessionController
import com.thomas.passiveeartraining.training.session.ExerciseSource
import com.thomas.passiveeartraining.training.session.PauseReason
import com.thomas.passiveeartraining.training.session.SessionController
import com.thomas.passiveeartraining.training.session.SessionPhase
import com.thomas.passiveeartraining.training.session.SessionSnapshot
import com.thomas.passiveeartraining.training.session.StartRequest
import com.thomas.passiveeartraining.training.timer.TimerScheduler
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The platform permission the audio pipeline needs before it may produce sound.
 *
 * Implemented by the service over the audio-focus coordinator; kept as a media-layer interface so
 * the audio adapter can ask without knowing about Android focus or routes.
 */
interface PlaybackReadiness {
    /** Requests focus and route readiness now; false means the session must stay silent. */
    fun acquire(): Boolean

    fun release() = Unit

    /** True when a route change requires the output device to be reopened before the next sound. */
    fun needsOutputRecovery(): Boolean

    /** Clears the recovery flag once the output has been reopened. */
    fun outputRecovered()
}

/**
 * Metadata extras keys for the sleep-timer countdown.
 *
 * The service publishes them on the player metadata; a controller reads them to render the
 * remaining time without polling the session. Values are whole seconds, or absent when off.
 */
const val SLEEP_ACTIVE_EXTRA: String = "passiveeartraining.sleepActive"
const val SLEEP_REMAINING_MS_EXTRA: String = "passiveeartraining.sleepRemainingMs"
const val SLEEP_DRAINING_EXTRA: String = "passiveeartraining.sleepDraining"

/**
 * The consumed answer text, present only after its frame is consumed.
 *
 * Publishing it in metadata before consumption would leak the answer to notifications and lock
 * screens, so the service omits the key entirely until the snapshot carries a label.
 */
const val ANSWER_TEXT_EXTRA: String = "passiveeartraining.answerText"

/** The live session-state query a screen may send to this app's own session. */
const val SESSION_STATE_ACTION: String = "passiveeartraining.sessionState"

/** Custom command: request a mode switch after the current learning sequence. */
const val SESSION_SWITCH_ACTION: String = "passiveeartraining.switchMode"

/** Extra carrying the stable training-kind id of a switch destination. */
const val SWITCH_KIND_EXTRA: String = "kind"

/** State extras naming the audible and pending activations. */
const val STATE_ACTIVE_KIND: String = "activeKind"
const val STATE_PENDING_KIND: String = "pendingKind"
const val STATE_ACTIVE_REVISION: String = "activeRevision"
const val STATE_PENDING_REVISION: String = "pendingRevision"

/** A resolved switch destination: the request to activate and the saved revision acknowledged. */
data class SwitchDestination(val request: StartRequest, val revision: Long)

/** Bundle keys of the [SESSION_STATE_ACTION] reply. */
const val STATE_ACTIVE: String = "active"
const val STATE_PLAYING: String = "playing"
const val STATE_PAUSED: String = "paused"
const val STATE_PHASE: String = "phase"
const val STATE_ANSWER_TEXT: String = "answerText"
const val STATE_CURRENT_NOTES: String = "currentNotes"
const val STATE_SEED: String = "seed"
const val STATE_SLEEP_ACTIVE: String = "sleepActive"
const val STATE_SLEEP_REMAINING_MS: String = "sleepRemainingMs"
const val STATE_SLEEP_DRAINING: String = "sleepDraining"

/**
 * Creates the audio pipeline the service owns.
 *
 * The seam lets the service be built and tested without the native renderer, and makes a
 * construction failure an observable session error instead of a crash.
 */
interface AudioPipelineFactory {
    val sampleRate: Int

    fun create(
        context: Context,
        readiness: PlaybackReadiness,
        timerScheduler: com.thomas.passiveeartraining.training.timer.TimerScheduler
    ): SessionController

    companion object {
        /** A pipeline that reports itself unavailable; the app composes the real one. */
        fun unavailable(): AudioPipelineFactory = UnavailableAudioPipelineFactory
    }
}

/** Reports an unavailable audio pipeline as a session error rather than failing silently. */
object UnavailableAudioPipelineFactory : AudioPipelineFactory {
    override val sampleRate: Int = 48_000

    override fun create(
        context: Context,
        readiness: PlaybackReadiness,
        timerScheduler: com.thomas.passiveeartraining.training.timer.TimerScheduler
    ): SessionController = DefaultSessionController(
        source = FailingExerciseSource,
        audio = FailingAudioPort,
        clock = ZeroClock
    )
}

private object ZeroClock : MonotonicClock {
    override fun elapsed(): Duration = Duration.ZERO
}

private object FailingExerciseSource : ExerciseSource {
    override fun configure(request: StartRequest) =
        throw IllegalStateException("No exercise source is configured")

    override fun next() = throw IllegalStateException("No exercise source is configured")

    override fun compile(
        exercise: com.thomas.passiveeartraining.training.provider.ConcreteExercise,
        recipe: com.thomas.passiveeartraining.training.recipe.RecipeDefinition
    ): com.thomas.passiveeartraining.training.provider.CompiledExercise =
        throw IllegalStateException("No timeline compiler is configured")
}

private object FailingAudioPort : AudioPort {
    override fun configure(musicGain: Float, voiceGain: Float) = Unit

    override suspend fun prepare(
        identity: com.thomas.passiveeartraining.training.session.PlaybackIdentity,
        exercise: com.thomas.passiveeartraining.training.provider.ConcreteExercise,
        compiled: com.thomas.passiveeartraining.training.provider.CompiledExercise,
        settings: com.thomas.passiveeartraining.training.session.RenderSettings
    ): AudioPort.Prepared = throw AudioPortException("The audio pipeline is not configured")

    override suspend fun play(
        identity: com.thomas.passiveeartraining.training.session.PlaybackIdentity,
        startFrame: Long,
        boundaries: List<Long>,
        isCancelled: () -> Boolean,
        onConsumed: suspend (Long) -> Unit,
        continueAtBoundary: (Long) -> Boolean
    ): AudioPort.PlayOutcome = throw AudioPortException("The audio pipeline is not configured")

    override suspend fun requestPlaybackPermission(): Boolean = false
    override fun setMasterGain(gain: Float) = Unit
    override fun consumedFrames(): Long = 0L
    override fun flush() = Unit
    override fun release() = Unit
}

/**
 * The single playback owner.
 *
 * The service owns one session controller, one Media3 player, one MediaSession and the audio
 * lifetime. Screens connect through a MediaController, so disconnecting a screen never stops an
 * active session. Every player method and all state publication run on the application looper;
 * audio work is forwarded to the session's serialized scope.
 */
@UnstableApi
open class TrainingMediaService : MediaSessionService() {

    private var session: MediaSession? = null
    private var transport: TrainingTransport? = null
    private var player: TrainingPlayer? = null
    private var focus: AudioFocusCoordinator? = null
    private var wakeLock: WakeLockCoordinator? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observer: kotlinx.coroutines.Job? = null

    /** Set when a route change invalidated the output device; cleared after it is reopened. */
    @Volatile
    private var routeRecoveryNeeded = false

    private companion object {
        /** Name of the sleep-timer configuration command. */
        const val ACTION_SLEEP_TIMER = "passiveeartraining.sleepTimer"

        /** Name of the live session-state query used by this app's screen. */
        const val ACTION_SESSION_STATE = SESSION_STATE_ACTION

        /** Payload key: minutes, where zero means off. */
        const val KEY_SLEEP_MINUTES = "minutes"

        /** Three hours, matching the timer's own maximum. */
        const val MAX_SLEEP_MINUTES = 180

        /** Volume factor while another sound plays over this app; small enough to stay audible. */
        const val DUCK_GAIN = 0.3f
    }

    protected fun playbackResourcesHeld(): Pair<Boolean, Boolean> =
        (wakeLock?.isHeld() == true) to (focus?.holdsFocus() == true)

    /** Subclasses provide the audio pipeline; the default reports itself unavailable. */
    protected open fun createAudioPipelineFactory(): AudioPipelineFactory =
        AudioPipelineFactory.unavailable()

    /** The configuration a Start uses; null means the session only resumes. */
    protected open fun startRequestFactory(): suspend () -> StartRequest? = { null }

    /**
     * The remembered sleep-timer choice applied to a fresh Start, in minutes.
     *
     * A subclass returns its saved value so every start path (transport button, notification,
     * reconnect) arms the same timer without the screen having to repeat the command.
     */
    protected open fun startSleepMinutes(): Int = 0

    /**
     * Resolves a validated switch destination for a stable kind id.
     *
     * Null means the mode cannot be activated yet; the command fails without touching playback.
     */
    protected open suspend fun switchDestination(kindId: String): SwitchDestination? = null

    /**
     * Applies the current configuration to a running session.
     *
     * A subclass calls this after its settings change: the session keeps the audible item and
     * redraws the one ahead, so the next pair uses the new setup. While nothing is running this is
     * a no-op, because the next Start already reads the same configuration.
     */
    protected fun reconfigureActiveSession() {
        val active = transport ?: return
        when (active.snapshot.phase) {
            SessionPhase.IDLE, SessionPhase.COMPLETED, SessionPhase.ERROR -> return
            else -> Unit
        }
        serviceScope.launch {
            try {
                val request = startRequestFactory()() ?: return@launch
                val future = active.reconfigure(request)
                future.addListener({
                    try {
                        future.get()
                    } catch (failure: Exception) {
                        android.util.Log.e(
                            "TrainingMediaService",
                            "Live settings rejected",
                            failure
                        )
                    }
                }, { it.run() })
            } catch (
                cancelled: kotlinx.coroutines.CancellationException
            ) {
                throw cancelled
            } catch (
                failure: Exception
            ) {
                android.util.Log.e("TrainingMediaService", "Settings unavailable", failure)
            }
        }
    }

    /** Scheduler for the session's sleep timer; subclasses own a scope and cancel it on teardown. */
    protected open fun createTimerScheduler(): TimerScheduler = TimerScheduler { _, _ -> }

    override fun onCreate() {
        super.onCreate()
        val factory = createAudioPipelineFactory()
        val looper = Looper.getMainLooper()
        // Focus, route changes and the wake lock follow the session's phase, so acquire and release
        // cannot drift apart and every exit path releases its resources.
        val lock = WakeLockCoordinator(this)
        wakeLock = lock
        val coordinator = AudioFocusCoordinator(this) { interruption ->
            onInterruption(interruption)
        }
        focus = coordinator
        val readiness = object : PlaybackReadiness {
            override fun acquire(): Boolean = coordinator.request()
            override fun release() = coordinator.abandon()

            override fun needsOutputRecovery(): Boolean = routeRecoveryNeeded

            override fun outputRecovered() {
                routeRecoveryNeeded = false
            }
        }
        val activeTransport = TrainingTransport(
            controller = factory.create(
                applicationContext,
                readiness,
                createTimerScheduler()
            ),
            handler = Handler(looper),
            requestFactory = startRequestFactory(),
            startSleepMinutes = { startSleepMinutes() }
        )
        transport = activeTransport
        val activePlayer = TrainingPlayer(activeTransport, looper, factory.sampleRate)
        player = activePlayer
        observer = observeSession(activeTransport, lock, coordinator)
        session = MediaSession.Builder(this, activePlayer)
            .setSessionActivity(sessionActivityIntent())
            .setCallback(SleepTimerCallback())
            .build()
    }

    /**
     * Applies focus and power policy as the session changes.
     *
     * Focus is requested by the audio pipeline immediately before sound, so this observer only
     * releases it: a user pause, route change, completion or error abandons the request, while a
     * transient-loss pause keeps it so an explicit resume re-enters the same grant. The wake lock
     * follows the phase.
     */
    private fun observeSession(
        activeTransport: TrainingTransport,
        lock: WakeLockCoordinator,
        coordinator: AudioFocusCoordinator
    ): kotlinx.coroutines.Job = serviceScope.launch {
        activeTransport.state.collect { snapshot ->
            lock.apply(snapshot.phase)
            when (snapshot.phase) {
                SessionPhase.PAUSED -> {
                    if (snapshot.pauseReason != PauseReason.Focus) {
                        coordinator.abandon()
                    }
                }
                SessionPhase.COMPLETED, SessionPhase.ERROR,
                SessionPhase.IDLE
                -> coordinator.abandon()
                SessionPhase.PREPARING, SessionPhase.PLAYING, SessionPhase.STOPPING -> Unit
            }
        }
    }

    /** Maps a platform interruption onto the session through the shared policy. */
    protected fun onInterruption(interruption: Interruption) {
        val activeTransport = transport ?: return
        if (InterruptionPolicy.requiresOutputRecovery(interruption)) {
            // The device the output was opened on is gone; the next sound must reopen it.
            routeRecoveryNeeded = true
        }
        val decision = InterruptionPolicy.decide(
            interruption = interruption,
            playing = activeTransport.snapshot.phase == SessionPhase.PLAYING ||
                activeTransport.snapshot.phase == SessionPhase.PREPARING
        )
        when (decision) {
            InterruptionDecision.NONE -> Unit
            InterruptionDecision.DUCK -> activeTransport.setDuckGain(DUCK_GAIN)
            InterruptionDecision.UNDUCK -> activeTransport.setDuckGain(1f)
            InterruptionDecision.PAUSE, InterruptionDecision.PAUSE_AND_ABANDON_FOCUS -> {
                // The product has no pause: an interruption ends the session, and the next Start
                // begins a fresh one with a new seed.
                activeTransport.stop()
                focus?.abandon()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * A task swipe-away ends the session on the owner's request, so the sound stops instead of
     * continuing invisibly after the app was dismissed.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        runCatching {
            transport?.stop()
        }.onFailure { android.util.Log.w("TrainingMediaService", "Cleanup failed", it) }
        stopSelf()
    }

    /**
     * Releases in order: session writes, then the MediaSession, the player and the session scope.
     * Repeated destruction is safe because every step tolerates an absent resource.
     */
    override fun onDestroy() {
        // Every exit path releases focus, the wake lock and the observer before the session stops.
        runCatching {
            observer?.cancel()
        }.onFailure { android.util.Log.w("TrainingMediaService", "Cleanup failed", it) }
        observer = null
        serviceScope.cancel()
        runCatching {
            focus?.abandon()
        }.onFailure { android.util.Log.w("TrainingMediaService", "Cleanup failed", it) }
        focus = null
        runCatching {
            wakeLock?.apply(SessionPhase.IDLE)
        }.onFailure { android.util.Log.w("TrainingMediaService", "Cleanup failed", it) }
        wakeLock = null
        runCatching {
            transport?.stop()
        }.onFailure { android.util.Log.w("TrainingMediaService", "Cleanup failed", it) }
        runCatching {
            session?.release()
        }.onFailure { android.util.Log.w("TrainingMediaService", "Cleanup failed", it) }
        runCatching {
            player?.release()
        }.onFailure { android.util.Log.w("TrainingMediaService", "Cleanup failed", it) }
        runCatching {
            transport?.close()
        }.onFailure { android.util.Log.w("TrainingMediaService", "Cleanup failed", it) }
        session = null
        player = null
        transport = null
        super.onDestroy()
    }

    /**
     * Session callback for the app's own configuration commands.
     *
     * A custom command is configuration rather than transport, so anything that is not this
     * app's controller is refused. The payload is a single bounded integer, validated before
     * use, so a controller command can never carry a file path or a URL.
     */
    @UnstableApi
    private inner class SleepTimerCallback : MediaSession.Callback {

        /**
         * Negotiates the session commands this app's own controller may send.
         *
         * Media3 only delivers a custom command a controller was granted, so the sleep-timer action
         * is advertised here. Another app's controller is accepted without it, which is what keeps
         * configuration out of untrusted hands.
         */
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val accepted = super.onConnect(session, controller)
            if (controller.uid != android.os.Process.myUid()) {
                return accepted
            }
            val commands = androidx.media3.session.SessionCommands.Builder()
                .addSessionCommands(accepted.availableSessionCommands.commands)
                .add(
                    androidx.media3.session.SessionCommand(
                        ACTION_SLEEP_TIMER,
                        android.os.Bundle.EMPTY
                    )
                )
                .add(
                    androidx.media3.session.SessionCommand(
                        ACTION_SESSION_STATE,
                        android.os.Bundle.EMPTY
                    )
                )
                .add(
                    androidx.media3.session.SessionCommand(
                        SESSION_SWITCH_ACTION,
                        android.os.Bundle.EMPTY
                    )
                )
                .build()
            // Only the session commands are narrowed. The player's own advertised commands are left
            // to the player, so restricting configuration cannot accidentally remove transport.
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: androidx.media3.session.SessionCommand,
            args: android.os.Bundle
        ): com.google.common.util.concurrent.ListenableFuture<
            androidx.media3.session.SessionResult
            > {
            if (customCommand.customAction != ACTION_SLEEP_TIMER &&
                customCommand.customAction != ACTION_SESSION_STATE &&
                customCommand.customAction != SESSION_SWITCH_ACTION
            ) {
                return com.google.common.util.concurrent.Futures.immediateFuture(
                    androidx.media3.session.SessionResult(
                        androidx.media3.session.SessionResult.RESULT_ERROR_NOT_SUPPORTED
                    )
                )
            }
            if (controller.uid != android.os.Process.myUid()) {
                return com.google.common.util.concurrent.Futures.immediateFuture(
                    androidx.media3.session.SessionResult(
                        androidx.media3.session.SessionResult.RESULT_ERROR_PERMISSION_DENIED
                    )
                )
            }
            if (customCommand.customAction == ACTION_SESSION_STATE) {
                // A live snapshot for this app's screen: the answer is included only when its
                // frame has been consumed, so the same rule holds on every surface.
                val snapshot = transport?.snapshot
                val value = android.os.Bundle().apply {
                    if (snapshot == null) {
                        putBoolean(STATE_ACTIVE, false)
                    } else {
                        putString(STATE_PHASE, snapshot.phase.name)
                        putBoolean(
                            STATE_ACTIVE,
                            snapshot.phase != SessionPhase.IDLE &&
                                snapshot.phase != SessionPhase.COMPLETED &&
                                snapshot.phase != SessionPhase.ERROR
                        )
                        putBoolean(STATE_PLAYING, snapshot.phase == SessionPhase.PLAYING)
                        putBoolean(STATE_PAUSED, snapshot.phase == SessionPhase.PAUSED)
                        putString(STATE_ANSWER_TEXT, snapshot.answerLabel ?: "")
                        putIntArray(STATE_CURRENT_NOTES, snapshot.currentNotes.toIntArray())
                        snapshot.seed?.let { putLong(STATE_SEED, it) }
                        snapshot.activeKind?.let { putString(STATE_ACTIVE_KIND, it.id) }
                        snapshot.pendingKind?.let { putString(STATE_PENDING_KIND, it.id) }
                        snapshot.activeRevision?.let { putLong(STATE_ACTIVE_REVISION, it) }
                        snapshot.pendingRevision?.let { putLong(STATE_PENDING_REVISION, it) }
                        putBoolean(STATE_SLEEP_ACTIVE, snapshot.sleep.isEnabled)
                        snapshot.sleep.remaining?.let {
                            putLong(STATE_SLEEP_REMAINING_MS, it.inWholeMilliseconds)
                        }
                        putBoolean(STATE_SLEEP_DRAINING, snapshot.sleep.draining)
                    }
                }
                return com.google.common.util.concurrent.Futures.immediateFuture(
                    androidx.media3.session.SessionResult(
                        androidx.media3.session.SessionResult.RESULT_SUCCESS,
                        value
                    )
                )
            }
            if (customCommand.customAction == SESSION_SWITCH_ACTION) {
                return handleSwitchCommand(customCommand)
            }
            val minutes = customCommand.customExtras.getInt(KEY_SLEEP_MINUTES, -1)
            if (minutes !in 0..MAX_SLEEP_MINUTES) {
                return com.google.common.util.concurrent.Futures.immediateFuture(
                    androidx.media3.session.SessionResult(
                        androidx.media3.session.SessionResult.RESULT_ERROR_BAD_VALUE
                    )
                )
            }
            // The controller owns the timer: the command carries the full policy seam, so a
            // rejected or expired timer can never appear armed.
            val result = SettableFuture.create<androidx.media3.session.SessionResult>()
            val command = transport?.sleepTimer(minutes)
            if (command == null) {
                result.set(
                    androidx.media3.session.SessionResult(
                        androidx.media3.session.SessionResult.RESULT_ERROR_INVALID_STATE
                    )
                )
            } else {
                command.addListener({
                    try {
                        command.get()
                        result.set(
                            androidx.media3.session.SessionResult(
                                androidx.media3.session.SessionResult.RESULT_SUCCESS
                            )
                        )
                    } catch (failure: Exception) {
                        android.util.Log.e("TrainingMediaService", "Timer command failed", failure)
                        result.set(
                            androidx.media3.session.SessionResult(
                                androidx.media3.session.SessionResult.RESULT_ERROR_UNKNOWN
                            )
                        )
                    }
                }, { it.run() })
            }
            return result
        }
    }

    /**
     * Resolves the destination and sends the switch command.
     *
     * The resolution is asynchronous (settings IO); a null destination or a rejected command
     * produces a failed session result and leaves the audible session untouched.
     */
    private fun handleSwitchCommand(
        command: androidx.media3.session.SessionCommand
    ): com.google.common.util.concurrent.ListenableFuture<
        androidx.media3.session.SessionResult
        > {
        val result = com.google.common.util.concurrent.SettableFuture.create<
            androidx.media3.session.SessionResult
            >()
        val kindId = command.customExtras.getString(SWITCH_KIND_EXTRA)
        val active = transport
        if (kindId == null || active == null) {
            result.set(
                androidx.media3.session.SessionResult(
                    androidx.media3.session.SessionResult.RESULT_ERROR_INVALID_STATE
                )
            )
            return result
        }
        serviceScope.launch {
            try {
                val destination = switchDestination(kindId)
                if (destination == null) {
                    result.set(
                        androidx.media3.session.SessionResult(
                            androidx.media3.session.SessionResult.RESULT_ERROR_INVALID_STATE
                        )
                    )
                    return@launch
                }
                val switching = active.switchMode(destination.request, destination.revision)
                switching.addListener({
                    try {
                        switching.get()
                        result.set(
                            androidx.media3.session.SessionResult(
                                androidx.media3.session.SessionResult.RESULT_SUCCESS
                            )
                        )
                    } catch (failure: Exception) {
                        android.util.Log.e(
                            "TrainingMediaService",
                            "Switch command failed",
                            failure
                        )
                        result.set(
                            androidx.media3.session.SessionResult(
                                androidx.media3.session.SessionResult.RESULT_ERROR_UNKNOWN
                            )
                        )
                    }
                }, { it.run() })
            } catch (
                cancelled: kotlinx.coroutines.CancellationException
            ) {
                throw cancelled
            } catch (failure: Exception) {
                android.util.Log.e(
                    "TrainingMediaService",
                    "Switch destination unavailable",
                    failure
                )
                result.set(
                    androidx.media3.session.SessionResult(
                        androidx.media3.session.SessionResult.RESULT_ERROR_UNKNOWN
                    )
                )
            }
        }
        return result
    }

    private fun sessionActivityIntent(): PendingIntent {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent().setPackage(packageName)
        launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}

/**
 * Media3 player over the training session.
 *
 * Only the operations the product supports are advertised; seeking, speed changes and playlist
 * injection are absent by construction rather than rejected at runtime.
 */
@UnstableApi
class TrainingPlayer internal constructor(
    private val transport: TrainingTransport,
    applicationLooper: Looper,
    private val sampleRate: Int
) : SimpleBasePlayer(applicationLooper) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(applicationLooper)

    init {
        // Snapshots arrive from the session's scope; the player republishes them on its own looper.
        scope.launch {
            transport.state.collect { invalidateState() }
        }
    }

    /** The latest session snapshot, for the service's own decisions. */
    fun sessionSnapshot(): SessionSnapshot = transport.snapshot

    override fun getState(): State {
        val snapshot = transport.snapshot
        val projection = SessionPlayerMapping.project(snapshot, sampleRate)
        val builder = State.Builder()
            .setAvailableCommands(availableCommands(projection))
            .setPlaybackState(projection.playbackState)
            .setPlayWhenReady(
                projection.playWhenReady,
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
            )
            .setContentPositionMs(projection.positionMs)
            .setPlaylist(ImmutableList.of(mediaItemData(snapshot, projection)))
        projection.errorCode?.let { code ->
            builder.setPlayerError(
                PlaybackException(projection.errorMessage ?: "Playback failed", null, code)
            )
        }
        return builder.build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        val future = transport.playWhenReady(playWhenReady)
        // The session publishes the outcome; republishing here keeps the player state in step.
        future.addListener({ invalidateState() }, handler::post)
        return future
    }

    /**
     * Accepts Media3's prepare step without doing anything.
     *
     * The session has no separate prepare phase: Start validates assets, prepares the first item
     * and begins playback on its own worker. Advertising COMMAND_PREPARE without this override
     * makes Media3's play request fail with "Missing implementation to handle COMMAND_PREPARE".
     */
    override fun handlePrepare(): ListenableFuture<*> =
        com.google.common.util.concurrent.Futures.immediateVoidFuture()

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int
    ): ListenableFuture<*> = when (seekCommand) {
        Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> transport.next()
        Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ->
            transport.previous()
        else -> {
            // Seeking to a position and speed changes are not product operations, so they are not
            // advertised; reaching here means an unsupported request was made.
            val future = SettableFuture.create<Any>()
            future.setException(
                UnsupportedOperationException("Seeking to a position is not supported")
            )
            future
        }
    }

    override fun handleStop(): ListenableFuture<*> = transport.stop()

    override fun handleRelease(): ListenableFuture<*> {
        scope.cancel()
        return super.handleRelease()
    }

    /**
     * The single media item for the current exercise.
     *
     * The timeline is one generated exercise rather than a list, so the item carries the exercise
     * identity and the metadata every surface shows.
     */
    private fun mediaItemData(
        snapshot: SessionSnapshot,
        projection: PlayerStateProjection
    ): MediaItemData {
        val content = SessionPlayerMapping.notificationContent(snapshot)
        // The answer is never published before it is consumed, on any surface: screen, notification
        // and session metadata all come from this one policy.
        val timerExtras = android.os.Bundle().apply {
            putBoolean(SLEEP_ACTIVE_EXTRA, snapshot.sleep.isEnabled)
            putBoolean(SLEEP_DRAINING_EXTRA, snapshot.sleep.draining)
            // Whole seconds only, so the metadata changes once per second instead of on every tick.
            snapshot.sleep.remaining?.let {
                putLong(SLEEP_REMAINING_MS_EXTRA, (it.inWholeMilliseconds / 1000L) * 1000L)
            }
            // A consumed answer only; the key is absent until then.
            snapshot.answerLabel?.let { putString(ANSWER_TEXT_EXTRA, it) }
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(content.title)
            .setArtist(content.subtitle)
            .setExtras(timerExtras)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
        val item = MediaItem.Builder()
            .setMediaId("exercise:${snapshot.itemIndex}")
            .setMediaMetadata(metadata)
            .build()
        val builder = MediaItemData.Builder(item.mediaId)
            .setMediaItem(item)
            .setMediaMetadata(metadata)
            .setIsSeekable(false)
            .setIsDynamic(false)
        if (projection.totalDurationMs > 0L) {
            builder.setDurationUs(projection.totalDurationMs * 1_000L)
        }
        return builder.build()
    }

    private fun availableCommands(projection: PlayerStateProjection): Player.Commands {
        val builder = Player.Commands.Builder()
            .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_GET_METADATA)
            .add(Player.COMMAND_GET_TIMELINE)
            .add(Player.COMMAND_GET_TRACKS)
            .add(Player.COMMAND_GET_AUDIO_ATTRIBUTES)
            .add(Player.COMMAND_GET_VOLUME)
            .add(Player.COMMAND_PREPARE)
        // Play and pause are a single Media3 command, so it is advertised when either is available.
        if (projection.canPlay || projection.canPause) {
            builder.add(Player.COMMAND_PLAY_PAUSE)
        }
        if (projection.canSeekToNext) builder.add(Player.COMMAND_SEEK_TO_NEXT)
        if (projection.canSeekToPrevious) builder.add(Player.COMMAND_SEEK_TO_PREVIOUS)
        if (projection.canStop) builder.add(Player.COMMAND_STOP)
        return builder.build()
    }
}
