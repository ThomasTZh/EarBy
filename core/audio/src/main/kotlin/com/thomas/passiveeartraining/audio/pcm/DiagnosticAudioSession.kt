package com.thomas.passiveeartraining.audio.pcm

import java.io.File

/**
 * Temporary foreground-only audio owner for the WP2.4 diagnostic proof.
 *
 * The activity owns this session and closes it when it leaves the foreground; WP3.2 replaces the
 * ownership with the media service. It renders one exercise at a time from the shared pipeline and
 * never claims background support.
 */
interface DiagnosticAudioPort {
    /** Output format the session negotiated at start. */
    val format: PcmFormat

    /** True while this session owns the engine. */
    val isOpen: Boolean

    /** Loads the SoundFont and prepares the synth for the given bank/program. */
    fun open(soundFontFile: File, bank: Int, program: Int)

    /** Renders [timeline] into a cached exercise, keyed by [identity]. */
    fun render(
        identity: String,
        sequenceIndex: Int,
        timeline: com.thomas.passiveeartraining.training.Timeline
    ): RenderedExercise

    /** Starts (or restarts) playback of the cached current exercise from [startFrame]. */
    fun play(startFrame: Long)

    /** Pauses and retains the consumed position. */
    fun pause()

    /** Flushes queued audio and drops the current exercise. */
    fun stop()

    /** Consumed frames reported by the device, clamped to what was submitted. */
    fun consumedFrames(): Long

    /**
     * Asks the in-flight write loop to stop and flushes queued audio.
     *
     * This must be callable from outside the worker, because the worker is blocked inside the
     * write loop while an exercise plays: a pause that can only run on that worker is a pause that
     * arrives after the exercise, which is what the reviewer reported.
     */
    fun requestPause()

    /** Output metrics for the diagnostic readout. */
    fun metrics(): OutputMetrics

    /** Closes the session and releases every resource; idempotent. */
    fun close()
}

/** The phase the diagnostic proof is in; the screen renders this directly. */
enum class DiagnosticPhase { IDLE, PREPARING, READY, PLAYING, PAUSED, ERROR }

/** Everything the diagnostic screen shows. */
data class DiagnosticSnapshot(
    val phase: DiagnosticPhase,
    val seed: Long,
    val configurationSummary: String,
    val exerciseSummary: String,
    val voiceId: String,
    val timelineSummary: String,
    val outputMetrics: OutputMetrics?,
    val consumedFrames: Long,
    val totalFrames: Long,
    val message: String?
) {
    val progress: Float
        get() = if (totalFrames <=
            0L
        ) {
            0f
        } else {
            (consumedFrames.toDouble() / totalFrames.toDouble()).toFloat().coerceIn(0f, 1f)
        }
}

/** Raised when a diagnostic command is not available in the current phase. */
class DiagnosticCommandException(message: String) : IllegalStateException(message)

/**
 * Serialized command handler behind the diagnostic screen.
 *
 * The screen sends commands here and renders snapshots; it never touches audio directly. The
 * controller owns no threads, so a test can drive it deterministically.
 */
class DiagnosticController(
    private val port: DiagnosticAudioPort,
    private val newSeed: () -> Long = { System.nanoTime() }
) {
    private var phase = DiagnosticPhase.IDLE
    private var seed = 0L
    private var exercise: RenderedExercise? = null
    private var message: String? = null
    private var configuration = ""
    private var exerciseSummary = ""
    private var voiceId = ""
    private var timelineSummary = ""

    private fun snapshot(): DiagnosticSnapshot = DiagnosticSnapshot(
        phase = phase,
        seed = seed,
        configurationSummary = configuration,
        exerciseSummary = exerciseSummary,
        voiceId = voiceId,
        timelineSummary = timelineSummary,
        outputMetrics = if (port.isOpen) runCatching { port.metrics() }.getOrNull() else null,
        consumedFrames = if (exercise != null) port.consumedFrames() else 0L,
        totalFrames = exercise?.totalFrames ?: 0L,
        message = message
    )

    /** Current observable state; the caller can poll this without issuing a command. */
    fun state(): DiagnosticSnapshot = snapshot()

    /**
     * Prepares and plays the exercise.
     *
     * Re-entrant by design: a second call on an open session stops what is playing, reuses the
     * engine and renders again. The reviewer reported that Start worked only once and then failed or
     * appeared to hang, which was this method refusing an open session.
     *
     * @throws DiagnosticCommandException only when the engine cannot be opened or the render fails.
     */
    fun start(
        seedForExercise: Long,
        soundFontFile: File,
        bank: Int,
        program: Int,
        timeline: com.thomas.passiveeartraining.training.Timeline,
        voiceAssetId: String,
        configurationSummary: String
    ) {
        phase = DiagnosticPhase.PREPARING
        try {
            // Stop anything still playing, so Start always begins from a clean, silent output.
            if (port.isOpen) {
                runCatching { port.stop() }
            } else {
                port.open(soundFontFile, bank, program)
            }
            // The seed that produced the timeline is authoritative: the timeline and the displayed
            // seed must describe the same exercise, otherwise the field changes while the pair
            // stays the same, which is exactly what the reviewer observed.
            seed = seedForExercise
            configuration = configurationSummary
            voiceId = voiceAssetId
            exercise = port.render("diagnostic:$seed", 0, timeline)
            exerciseSummary = describe(exercise!!)
            timelineSummary = describeTimeline(timeline)
            // Ready is published before the output is written, so the display can show that the
            // exercise exists while playback is still starting.
            phase = DiagnosticPhase.READY
        } catch (error: Exception) {
            phase = DiagnosticPhase.ERROR
            message = error.message ?: error::class.java.simpleName
            runCatching { port.close() }
        }
    }

    /** Starts or resumes playback of the prepared exercise. */
    fun play(startFrame: Long = 0L) {
        val current = exercise ?: throw DiagnosticCommandException("Nothing is prepared to play")
        if (!port.isOpen || phase == DiagnosticPhase.IDLE || phase == DiagnosticPhase.ERROR) {
            throw DiagnosticCommandException("Cannot play from phase $phase")
        }
        // A play from Paused continues where the device stopped; anything else starts at the
        // beginning. Writing from a position already consumed would submit nothing and look like a
        // hang, so a finished exercise restarts.
        val consumed = port.consumedFrames()
        val effective = when {
            consumed >= current.totalFrames -> 0L
            phase == DiagnosticPhase.PAUSED -> consumed
            else -> startFrame.coerceIn(0L, current.totalFrames)
        }
        port.play(effective)
        phase = DiagnosticPhase.PLAYING
    }

    /** Pauses and retains the consumed position for a later resume. */
    fun pause() {
        if (phase == DiagnosticPhase.IDLE || phase == DiagnosticPhase.ERROR || !port.isOpen) {
            throw DiagnosticCommandException("Cannot pause from phase $phase")
        }
        // Stop the write loop first, then stop the device: the position is retained for a resume.
        port.requestPause()
        port.pause()
        phase = DiagnosticPhase.PAUSED
    }

    /** Resumes from the retained position. */
    fun resume() = play()

    /** Replays the same concrete exercise from the beginning without consuming new randomness. */
    fun repeat() {
        val current = exercise ?: throw DiagnosticCommandException("Nothing is prepared to repeat")
        port.stop()
        if (phase == DiagnosticPhase.ERROR || phase == DiagnosticPhase.IDLE) {
            throw DiagnosticCommandException("Cannot repeat from phase $phase")
        }
        port.play(0L)
        phase = DiagnosticPhase.PLAYING
    }

    /**
     * Flushes output and returns to Idle while keeping the engine open.
     *
     * The session stays usable: Start prepares and plays again without reopening the engine, which
     * is what makes repeated runs work.
     */
    fun stop() {
        if (port.isOpen) {
            port.stop()
        }
        exercise = null
        phase = DiagnosticPhase.IDLE
        message = null
    }

    /** Replaces the current exercise with a newly rendered one, for example after a reseed. */
    fun regenerate(
        seedForExercise: Long,
        timeline: com.thomas.passiveeartraining.training.Timeline,
        voiceAssetId: String
    ) {
        if (!port.isOpen) {
            throw DiagnosticCommandException("No diagnostic session is open")
        }
        if (phase == DiagnosticPhase.PLAYING) {
            port.pause()
        }
        val previousSeed = seed
        seed = seedForExercise
        voiceId = voiceAssetId
        val rendered = try {
            port.render("diagnostic:$seed", 0, timeline)
        } catch (error: Exception) {
            // A failed regeneration leaves the session in an observable Error state rather than
            // half-updated, and names the failure for the diagnostic readout.
            seed = previousSeed
            exercise = null
            exerciseSummary = ""
            phase = DiagnosticPhase.ERROR
            message = error.message ?: error::class.java.simpleName
            return
        }
        exercise = rendered
        exerciseSummary = describe(rendered)
        timelineSummary = describeTimeline(timeline)
        phase = DiagnosticPhase.READY
    }

    /** Injects a failure path for tests: records the error and silences output. */
    fun fail(error: Exception) {
        runCatching { port.stop() }
        phase = DiagnosticPhase.ERROR
        message = error.message ?: error::class.java.simpleName
    }

    /** Idempotent release; safe when the activity is destroyed twice. */
    fun close() {
        port.close()
        exercise = null
        phase = DiagnosticPhase.IDLE
    }

    /** Retries after an error by reopening the engine and rendering the given timeline. */
    fun retry(
        seedForExercise: Long,
        soundFontFile: File,
        bank: Int,
        program: Int,
        timeline: com.thomas.passiveeartraining.training.Timeline,
        voiceAssetId: String,
        configurationSummary: String
    ) {
        if (phase != DiagnosticPhase.ERROR) {
            throw DiagnosticCommandException("Retry is only available after an error")
        }
        // The failed session may still hold the engine open; release it before reopening so a
        // retry cannot leave two owners or a stale error state behind.
        port.close()
        exercise = null
        exerciseSummary = ""
        message = null
        phase = DiagnosticPhase.IDLE
        seed = 0L
        start(
            seedForExercise,
            soundFontFile,
            bank,
            program,
            timeline,
            voiceAssetId,
            configurationSummary
        )
    }

    companion object {
        fun describe(exercise: RenderedExercise): String =
            "${exercise.identity} seq=${exercise.sequenceIndex} " +
                frames(exercise.totalFrames, exercise.sampleRate)

        fun describeTimeline(timeline: com.thomas.passiveeartraining.training.Timeline): String {
            val kinds = timeline.orderedEvents.groupingBy {
                it::class.simpleName ?: "event"
            }.eachCount()
            return "events=${timeline.orderedEvents.size} " +
                kinds.entries.joinToString(", ") { "${it.key}=${it.value}" } +
                " total=${timeline.totalDuration}"
        }

        private fun frames(frames: Long, sampleRate: Int): String =
            "$frames frames (${"%.3f".format(frames.toDouble() / sampleRate)} s)"
    }
}
