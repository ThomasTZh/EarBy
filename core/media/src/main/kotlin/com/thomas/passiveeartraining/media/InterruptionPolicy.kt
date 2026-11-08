package com.thomas.passiveeartraining.media

/**
 * What the session should do when the platform or the user interrupts playback.
 *
 * The policy is pure so every branch is testable with virtual events and no device.
 */
enum class InterruptionDecision {
    /** Keep playing; nothing about the interruption requires stopping. */
    NONE,

    /** Lower the output volume while another sound plays over this app. */
    DUCK,

    /** Restore the output volume because focus returned. */
    UNDUCK,

    /** Pause, retain the position, and keep the audio focus request. */
    PAUSE,

    /** Pause, retain the position, and abandon the audio focus request. */
    PAUSE_AND_ABANDON_FOCUS
}

/** A reason playback was interrupted, as the platform reports it. */
sealed interface Interruption {
    /** Focus was lost permanently: another app now owns audio. */
    data object FocusLost : Interruption

    /** Focus was lost for a short time, for example during a notification sound. */
    data object FocusLostTransient : Interruption

    /** Focus will come back; the platform expects ducking while it is gone. */
    data object FocusDucked : Interruption

    /** Focus returned. */
    data object FocusGained : Interruption

    /** The output route became noisy because a headset or cable was removed. */
    data object BecomingNoisy : Interruption

    /** The audio device that was in use went away. */
    data class RouteLost(val deviceName: String?) : Interruption

    /** A new device became available and playback could continue on it. */
    data class RouteAdded(val deviceName: String?) : Interruption
}

/** Whether an interruption should silence playback. */
val Interruption.silencesPlayback: Boolean
    get() = this !is Interruption.FocusGained && this !is Interruption.RouteAdded

/**
 * The interruption policy.
 *
 * Initial policy from the work package: end the session on permanent loss, transient loss and duck
 * requests, preserving speech intelligibility. Focus returning never restarts playback on its own,
 * because the product has no resume: the user starts a new session explicitly. A noisy route or a
 * lost device ends the session before any fallback to the speaker.
 */
object InterruptionPolicy {

    /**
     * Decides what to do for [interruption] while the session is in [playing].
     *
     * A decision is only meaningful while sound is playing; an interruption arriving when the
     * session is already silent changes nothing and is reported as [InterruptionDecision.NONE] so
     * duplicate platform callbacks are idempotent.
     */
    fun decide(interruption: Interruption, playing: Boolean): InterruptionDecision {
        if (interruption is Interruption.FocusGained) {
            // Focus returning restores the volume this app reduced while it was ducked; it never
            // resumes playback on its own.
            return InterruptionDecision.UNDUCK
        }
        if (interruption is Interruption.RouteAdded) {
            return InterruptionDecision.NONE
        }
        if (!playing) {
            // Nothing is sounding, so there is nothing to silence and no focus to release.
            return InterruptionDecision.NONE
        }
        return when (interruption) {
            // A permanent loss means another app owns audio: release focus so it is not held while
            // this app is silent.
            Interruption.FocusLost -> InterruptionDecision.PAUSE_AND_ABANDON_FOCUS
            // A notification or short sound lowers this app's volume and lets it continue, like a
            // music player; the gain is restored when focus returns.
            Interruption.FocusLostTransient, Interruption.FocusDucked ->
                InterruptionDecision.DUCK
            // A route change pauses before any fallback to the speaker, and the output is stale.
            Interruption.BecomingNoisy, is Interruption.RouteLost ->
                InterruptionDecision.PAUSE_AND_ABANDON_FOCUS
            Interruption.FocusGained, is Interruption.RouteAdded -> InterruptionDecision.NONE
        }
    }

    /**
     * Whether the audio output must be rebuilt before playback continues.
     *
     * A route change invalidates the device the output was opened on; a plain focus loss does not.
     */
    fun requiresOutputRecovery(interruption: Interruption): Boolean =
        interruption is Interruption.BecomingNoisy || interruption is Interruption.RouteLost
}

/**
 * Whether the session should hold a wake lock.
 *
 * A wake lock is held while sound is being produced - including the intentional silences inside a
 * timeline - and while the session is stopping. It is never held to keep a paused timer alive.
 */
object WakeLockPolicy {

    /** True while the session phase requires the device to stay awake. */
    fun shouldHold(phase: com.thomas.passiveeartraining.training.session.SessionPhase): Boolean =
        when (phase) {
            com.thomas.passiveeartraining.training.session.SessionPhase.PLAYING,
            com.thomas.passiveeartraining.training.session.SessionPhase.PREPARING,
            com.thomas.passiveeartraining.training.session.SessionPhase.STOPPING
            -> true
            // A paused session tolerates losing foreground status and process lifetime, so holding a
            // wake lock for it would only drain the battery.
            com.thomas.passiveeartraining.training.session.SessionPhase.IDLE,
            com.thomas.passiveeartraining.training.session.SessionPhase.PAUSED,
            com.thomas.passiveeartraining.training.session.SessionPhase.COMPLETED,
            com.thomas.passiveeartraining.training.session.SessionPhase.ERROR
            -> false
        }
}
