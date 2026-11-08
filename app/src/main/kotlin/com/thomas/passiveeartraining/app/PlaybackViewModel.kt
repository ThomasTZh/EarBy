package com.thomas.passiveeartraining.app

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.thomas.passiveeartraining.media.ANSWER_TEXT_EXTRA
import com.thomas.passiveeartraining.media.SESSION_STATE_ACTION
import com.thomas.passiveeartraining.media.SESSION_SWITCH_ACTION
import com.thomas.passiveeartraining.media.SLEEP_ACTIVE_EXTRA
import com.thomas.passiveeartraining.media.SLEEP_DRAINING_EXTRA
import com.thomas.passiveeartraining.media.SLEEP_REMAINING_MS_EXTRA
import com.thomas.passiveeartraining.media.STATE_ACTIVE
import com.thomas.passiveeartraining.media.STATE_ACTIVE_KIND
import com.thomas.passiveeartraining.media.STATE_ACTIVE_REVISION
import com.thomas.passiveeartraining.media.STATE_ANSWER_TEXT
import com.thomas.passiveeartraining.media.STATE_CURRENT_NOTES
import com.thomas.passiveeartraining.media.STATE_PAUSED
import com.thomas.passiveeartraining.media.STATE_PENDING_KIND
import com.thomas.passiveeartraining.media.STATE_PENDING_REVISION
import com.thomas.passiveeartraining.media.STATE_PLAYING
import com.thomas.passiveeartraining.media.STATE_SEED
import com.thomas.passiveeartraining.media.STATE_SLEEP_ACTIVE
import com.thomas.passiveeartraining.media.STATE_SLEEP_DRAINING
import com.thomas.passiveeartraining.media.STATE_SLEEP_REMAINING_MS
import com.thomas.passiveeartraining.media.SWITCH_KIND_EXTRA
import com.thomas.passiveeartraining.training.provider.TrainingKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** What the screens need to know about the media service connection. */
data class PlaybackUiState(
    val connected: Boolean = false,
    val playing: Boolean = false,
    /** True while a session exists that can be resumed or stopped. */
    val active: Boolean = false,
    /** True while the session is paused and can be resumed or stopped. */
    val paused: Boolean = false,
    val unavailable: Boolean = false,
    val error: String? = null,
    val sleepTimerMinutes: Int = 0,
    /** Remaining sleep time in milliseconds, or null when no timer is armed. */
    val sleepTimerRemainingMs: Long? = null,
    /** True while the expired timer lets the audible item finish. */
    val sleepTimerDraining: Boolean = false,
    /** The concrete answer, present only after its frame was consumed. */
    val answerText: String? = null,
    /** MIDI notes of the current item, for the Play debug line. */
    val currentNotes: List<Int> = emptyList(),
    /** The seed the accepted Start resolved, for reproducing the run. */
    val seed: Long? = null,
    /** The audible activation's kind, when a session is running. */
    val activeKind: TrainingKind? = null,
    /** The destination of a pending switch, or null. */
    val pendingKind: TrainingKind? = null,
    /** The saved revision of the audible activation, when it came from a switch. */
    val activeRevision: Long? = null,
    /** The saved revision of the pending destination. */
    val pendingRevision: Long? = null
)

/**
 * Owns the screen's MediaController connection.
 *
 * Playback itself belongs to the service, so this class never touches audio: it connects, forwards
 * transport presses and republishes the service's state. The connection lives in the ViewModel, so
 * a rotation reuses it but leaving the app does not keep an extra connection.
 */
class PlaybackViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var reconnectJob: kotlinx.coroutines.Job? = null
    private var cleared = false
    private var connection:
        com.google.common.util.concurrent.ListenableFuture<MediaController>? = null

    /**
     * Local countdown driver.
     *
     * The service publishes the remaining time with the session state, but Media3 may republish
     * metadata only when the item changes. The screen therefore ticks once per second from the
     * last received value and resynchronizes on every new update, so the display moves smoothly
     * without a polling loop against the service.
     */
    private var sleepTicker: kotlinx.coroutines.Job? = null
    private var sleepBaseRemainingMs: Long = 0L
    private var sleepBaseAtRealtimeMs: Long = 0L

    /** Live session-state polling; cancelled with the ViewModel. */
    private var statePoll: kotlinx.coroutines.Job? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publish(playing = isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            publish(playing = controller?.isPlaying == true)
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            // The service publishes the timer as metadata extras on every state change; the local
            // ticker keeps the countdown moving between those updates.
            val extras = mediaMetadata.extras
            val active = extras?.getBoolean(SLEEP_ACTIVE_EXTRA) == true
            val remaining = if (active) extras?.getLong(SLEEP_REMAINING_MS_EXTRA) else null
            _state.value = _state.value.copy(
                sleepTimerRemainingMs = remaining,
                sleepTimerDraining = active && extras?.getBoolean(SLEEP_DRAINING_EXTRA) == true,
                answerText = extras?.getString(ANSWER_TEXT_EXTRA)
            )
            if (remaining != null) {
                sleepBaseRemainingMs = remaining
                sleepBaseAtRealtimeMs = android.os.SystemClock.elapsedRealtime()
                startSleepTicker()
            } else {
                sleepTicker?.cancel()
                sleepTicker = null
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(error = error.message ?: "Playback failed")
        }
    }

    init {
        connect()
        // The sleep timer remembers its last choice; seed the chooser from the saved settings.
        viewModelScope.launch {
            getApplication<PassiveEarTrainingApp>().settings.collect { snapshot ->
                val saved = snapshot.settings.sleepTimerMinutes
                if (_state.value.sleepTimerMinutes != saved) {
                    _state.value = _state.value.copy(sleepTimerMinutes = saved)
                }
            }
        }
    }

    private fun connect() {
        if (cleared || reconnectJob?.isActive == true) return
        reconnectJob = viewModelScope.launch {
            var retryDelay = 1000L
            while (isActive && controller == null) {
                val context = getApplication<PassiveEarTrainingApp>().applicationContext
                val token = SessionToken(
                    context,
                    ComponentName(
                        context,
                        getApplication<PassiveEarTrainingApp>().playbackServiceClassName
                    )
                )
                val future = MediaController.Builder(context, token)
                    .setListener(object : MediaController.Listener {
                        override fun onDisconnected(disconnected: MediaController) {
                            if (controller !== disconnected || cleared) return
                            controller = null
                            statePoll?.cancel()
                            sleepTicker?.cancel()
                            connection?.let { MediaController.releaseFuture(it) }
                            connection = null
                            _state.value = PlaybackUiState(
                                unavailable = true,
                                error = "Playback disconnected; reconnecting"
                            )
                            connect()
                        }
                    }).buildAsync()
                connection = future
                try {
                    val connected = withTimeoutOrNull(15_000) { future.awaitCommandResult() }
                        ?: error("Playback connection timed out")
                    controller = connected
                    connected.addListener(listener)
                    _state.value = PlaybackUiState(connected = true, playing = connected.isPlaying)
                    startSessionStatePolling()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    MediaController.releaseFuture(future)
                    throw cancelled
                } catch (failure: Exception) {
                    MediaController.releaseFuture(future)
                    connection = null
                    _state.value = PlaybackUiState(
                        unavailable = true,
                        error = failure.message ?: "Playback connection failed"
                    )
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(15_000)
                }
            }
        }
    }

    /**
     * Polls the session snapshot through this app's own query command.
     *
     * Media3 republishes player metadata lazily, so a screen that waited for metadata would show
     * the answer only after the item changed. This query returns the live snapshot, which makes
     * the consumed answer appear within a fraction of a second of the voice starting.
     */
    private fun startSessionStatePolling() {
        statePoll?.cancel()
        statePoll = viewModelScope.launch {
            while (true) {
                try {
                    querySessionState()
                } catch (
                    cancelled: kotlinx.coroutines.CancellationException
                ) {
                    throw cancelled
                } catch (failure: Exception) {
                    _state.value =
                        _state.value.copy(error = failure.message ?: "Session query failed")
                }
                delay(STATE_POLL_MILLIS)
            }
        }
    }

    private suspend fun querySessionState() {
        val active = controller ?: return
        val future = withContext(Dispatchers.Main) {
            active.sendCustomCommand(
                SessionCommand(SESSION_STATE_ACTION, Bundle.EMPTY),
                Bundle.EMPTY
            )
        }
        val result = withTimeoutOrNull(5000) { future.awaitCommandResult() }
            ?: error("Session query timed out")
        check(result.resultCode == androidx.media3.session.SessionResult.RESULT_SUCCESS) {
            "Session query was refused"
        }
        val bundle = result.extras
        val sleepActive = bundle.getBoolean(STATE_SLEEP_ACTIVE)
        val remaining = if (sleepActive) bundle.getLong(STATE_SLEEP_REMAINING_MS) else null
        _state.value = _state.value.copy(
            playing = bundle.getBoolean(STATE_PLAYING),
            paused = bundle.getBoolean(STATE_PAUSED),
            active = bundle.getBoolean(STATE_ACTIVE),
            answerText = bundle.getString(STATE_ANSWER_TEXT)?.takeIf { it.isNotBlank() },
            currentNotes = bundle.getIntArray(STATE_CURRENT_NOTES)?.toList() ?: emptyList(),
            seed = if (bundle.containsKey(STATE_SEED)) bundle.getLong(STATE_SEED) else null,
            activeKind = bundle.getString(STATE_ACTIVE_KIND)?.let { TrainingKind.ofIdOrNull(it) },
            pendingKind = bundle.getString(STATE_PENDING_KIND)?.let { TrainingKind.ofIdOrNull(it) },
            activeRevision = if (bundle.containsKey(STATE_ACTIVE_REVISION)) {
                bundle.getLong(STATE_ACTIVE_REVISION)
            } else {
                null
            },
            pendingRevision = if (bundle.containsKey(STATE_PENDING_REVISION)) {
                bundle.getLong(STATE_PENDING_REVISION)
            } else {
                null
            },
            sleepTimerRemainingMs = remaining,
            sleepTimerDraining = sleepActive && bundle.getBoolean(STATE_SLEEP_DRAINING)
        )
        if (remaining != null) {
            sleepBaseRemainingMs = remaining
            sleepBaseAtRealtimeMs = android.os.SystemClock.elapsedRealtime()
            startSleepTicker()
        } else {
            sleepTicker?.cancel()
            sleepTicker = null
        }
    }

    /** Starts a fresh session from the saved setup; the service draws the seed. */
    fun start() {
        val active = controller ?: return
        active.play()
        _state.value = _state.value.copy(error = null)
    }

    /** Ends the session. There is no pause: a stopped session restarts fresh with a new seed. */
    fun stop() {
        controller?.stop()
        _state.value = _state.value.copy(error = null)
    }

    /**
     * Requests a switch to [kind] after the current learning sequence.
     *
     * The service resolves and validates the destination; a rejected request leaves the audible
     * session untouched and surfaces the failure on the play screen.
     */
    fun switchMode(kind: TrainingKind) {
        val active = controller ?: return
        val extras = Bundle().apply { putString(SWITCH_KIND_EXTRA, kind.id) }
        viewModelScope.launch {
            try {
                val result = active.sendCustomCommand(
                    SessionCommand(SESSION_SWITCH_ACTION, extras),
                    Bundle.EMPTY
                ).awaitCommandResult()
                if (result.resultCode != androidx.media3.session.SessionResult.RESULT_SUCCESS) {
                    _state.value = _state.value.copy(
                        error = "${kind.label} cannot be activated yet"
                    )
                } else {
                    _state.value = _state.value.copy(error = null)
                }
            } catch (
                cancelled: kotlinx.coroutines.CancellationException
            ) {
                throw cancelled
            } catch (failure: Exception) {
                _state.value = _state.value.copy(error = failure.message)
            }
        }
    }

    /** Arms or cancels the service-owned sleep timer; zero cancels. */
    fun setSleepTimer(minutes: Int) {
        val active = controller ?: return
        // Remember the choice for the next start as well as applying it to this session.
        val app = getApplication<PassiveEarTrainingApp>()
        viewModelScope.launch {
            app.settingsRepository.update { it.copy(sleepTimerMinutes = minutes) }
        }
        val extras = Bundle().apply { putInt(KEY_SLEEP_MINUTES, minutes) }
        viewModelScope.launch {
            try {
                val result = active.sendCustomCommand(
                    SessionCommand(ACTION_SLEEP_TIMER, extras),
                    Bundle.EMPTY
                ).awaitCommandResult()
                if (result.resultCode == androidx.media3.session.SessionResult.RESULT_SUCCESS) {
                    _state.value = _state.value.copy(sleepTimerMinutes = minutes, error = null)
                } else {
                    _state.value = _state.value.copy(error = "The sleep timer could not be changed")
                }
            } catch (
                cancelled: kotlinx.coroutines.CancellationException
            ) {
                throw cancelled
            } catch (
                failure: Exception
            ) {
                _state.value = _state.value.copy(error = failure.message)
            }
        }
    }

    /**
     * Starts the countdown driver if it is not already running.
     *
     * The service reports the authoritative remaining time on every state poll, and the screen
     * derives the display from that base between reports. Ticking faster than the display changes
     * removes the stutter a one-second ticker showed when its phase drifted from the reports.
     */
    private fun startSleepTicker() {
        if (sleepTicker?.isActive == true) return
        sleepTicker = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(SLEEP_TICK_MILLIS)
                val elapsed = android.os.SystemClock.elapsedRealtime() - sleepBaseAtRealtimeMs
                val remaining = (sleepBaseRemainingMs - elapsed).coerceAtLeast(0L)
                val shown = (_state.value.sleepTimerRemainingMs ?: 0L) / 1_000L
                if (remaining / 1_000L != shown) {
                    _state.value = _state.value.copy(sleepTimerRemainingMs = remaining)
                }
            }
        }
    }

    override fun onCleared() {
        cleared = true
        reconnectJob?.cancel()
        statePoll?.cancel()
        statePoll = null
        sleepTicker?.cancel()
        sleepTicker = null
        controller?.removeListener(listener)
        controller = null
        connection?.let { MediaController.releaseFuture(it) }
        connection = null
        super.onCleared()
    }

    private fun publish(playing: Boolean = _state.value.playing, error: String? = null) {
        _state.value = _state.value.copy(playing = playing, error = error)
    }

    private companion object {
        const val ACTION_SLEEP_TIMER = "passiveeartraining.sleepTimer"
        const val KEY_SLEEP_MINUTES = "minutes"

        /** Live-state poll interval: fast enough that a consumed answer appears with the voice. */
        const val STATE_POLL_MILLIS = 400L

        /** Countdown tick: faster than the displayed second so the clock never stutters. */
        const val SLEEP_TICK_MILLIS = 200L
    }
}
