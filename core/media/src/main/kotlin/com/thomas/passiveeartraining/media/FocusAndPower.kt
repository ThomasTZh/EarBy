package com.thomas.passiveeartraining.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import androidx.media3.common.util.UnstableApi

/**
 * Owns the audio focus request and the noisy-route receiver.
 *
 * Focus is requested before sound and released on pause, stop and error. The request is kept across
 * transient loss and ducking so playback resumes into the same grant, and abandoned on permanent
 * loss so a silent app never holds focus.
 */
@UnstableApi
class AudioFocusCoordinator(
    private val context: Context,
    private val onInterruption: (Interruption) -> Unit
) {
    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var focusRequest: AudioFocusRequest? = null

    @Volatile
    private var hasFocus = false
    private var noisyRegistered = false

    /**
     * The focus listener.
     *
     * Every callback is mapped to an [Interruption] and passed to the policy owner, which decides
     * what happens; this class never changes playback itself.
     */
    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        val interruption = when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // A permanent loss means this app no longer holds focus; a later request must ask
                // the platform again instead of trusting a stale flag.
                hasFocus = false
                Interruption.FocusLost
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> Interruption.FocusLostTransient
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> Interruption.FocusDucked
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                Interruption.FocusGained
            }
            else -> null
        }
        interruption?.let(onInterruption)
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                onInterruption(Interruption.BecomingNoisy)
            }
        }
    }

    /**
     * Requests audio focus before sound starts.
     *
     * Idempotent while this app holds focus, so item boundaries and resumes do not churn the
     * request. A transient loss or duck keeps the grant, so the next request is a no-op there too.
     *
     * @return true when focus was granted. A denial leaves the caller silent with a recoverable
     *   reason rather than playing over another app.
     */
    fun request(): Boolean {
        if (hasFocus) return true
        val manager = audioManager ?: return false
        val request = focusRequest ?: buildRequest().also { focusRequest = it }
        val result = manager.requestAudioFocus(request)
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (hasFocus) {
            registerNoisyReceiver()
        }
        return hasFocus
    }

    /** Releases focus and stops listening for route changes. */
    fun abandon() {
        hasFocus = false
        runCatching {
            focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        }.onFailure { android.util.Log.w("AudioFocusCoordinator", "Focus release failed", it) }
        unregisterNoisyReceiver()
    }

    /** True while this app holds focus. */
    fun holdsFocus(): Boolean = hasFocus

    /** Builds the request with media/music attributes so the platform reports ducking correctly. */
    private fun buildRequest(): AudioFocusRequest {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val builder = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(listener)
            // This app handles ducking itself by lowering the output volume, so the platform may
            // send a can-duck request instead of pausing playback for a notification.
            .setWillPauseWhenDucked(false)
        // Android 12 and later can fade the ducked app back in; requesting it keeps speech
        // intelligible without a manual gain change.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAcceptsDelayedFocusGain(false)
        }
        return builder.build()
    }

    private fun registerNoisyReceiver() {
        if (noisyRegistered) return
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(noisyReceiver, filter)
        }
        noisyRegistered = true
    }

    private fun unregisterNoisyReceiver() {
        if (!noisyRegistered) return
        runCatching { context.unregisterReceiver(noisyReceiver) }
            .onFailure {
                android.util.Log.w("AudioFocusCoordinator", "Receiver release failed", it)
            }
        noisyRegistered = false
    }
}

/**
 * Holds a partial wake lock while sound is being produced.
 *
 * Acquire and release go through [apply], which is driven by session phases, so they cannot drift
 * apart: the lock is held while playing, preparing or stopping and released on pause, completion,
 * error and destruction.
 */
@UnstableApi
class WakeLockCoordinator(context: Context) {

    private val powerManager: PowerManager? =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var renewedAt = 0L

    /** Matches the policy: the lock follows the phase rather than a separate flag. */
    fun apply(phase: com.thomas.passiveeartraining.training.session.SessionPhase) {
        if (WakeLockPolicy.shouldHold(phase)) {
            acquire()
        } else {
            release()
        }
    }

    /** True while the lock is held; used by tests and by the service's own bookkeeping. */
    fun isHeld(): Boolean = wakeLock?.isHeld == true

    private fun acquire() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (isHeld() && now - renewedAt < WAKE_LOCK_TIMEOUT_MS / 2) return
        val manager = powerManager ?: return
        val lock = wakeLock ?: manager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG
        ).apply {
            setReferenceCounted(false)
        }.also { wakeLock = it }
        lock.acquire(WAKE_LOCK_TIMEOUT_MS)
        renewedAt = now
    }

    private fun release() {
        val lock = wakeLock ?: return
        if (lock.isHeld) {
            runCatching { lock.release() }
                .onFailure { android.util.Log.w("WakeLockCoordinator", "Wake release failed", it) }
        }
    }

    private companion object {
        const val WAKE_LOCK_TAG = "passiveeartraining:playback"

        /**
         * Upper bound on the lock.
         *
         * A timeout means a bug or a crash cannot leave the device awake indefinitely; the session
         * releases the lock long before this in normal use.
         */
        const val WAKE_LOCK_TIMEOUT_MS = 30L * 60L * 1000L
    }
}
