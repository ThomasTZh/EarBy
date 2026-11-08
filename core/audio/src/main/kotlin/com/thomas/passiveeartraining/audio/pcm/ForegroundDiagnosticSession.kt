package com.thomas.passiveeartraining.audio.pcm

import com.thomas.passiveeartraining.audio.MusicRenderer
import com.thomas.passiveeartraining.audio.RenderFormat
import com.thomas.passiveeartraining.training.Timeline
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * Foreground-only implementation of the diagnostic audio session.
 *
 * One worker thread owns the engine, the cache and the output. Every public method hops onto that
 * worker so the screen can call from any thread without touching JNI, and the whole session is
 * released by [close]. WP3.2 replaces this owner with the media service.
 */
class ForegroundDiagnosticSession(
    private val renderFormat: RenderFormat = RenderFormat.DEFAULT,
    private val voiceFiles: Map<String, File> = emptyMap(),
    private val blockFrames: Int = ExerciseRenderer.DEFAULT_BLOCK_FRAMES
) : DiagnosticAudioPort {

    private val worker = Executors.newSingleThreadExecutor(
        ThreadFactory { runnable -> Thread(runnable, "diagnostic-audio") }
    )

    private val pcmFormat = PcmFormat(renderFormat.sampleRate, renderFormat.channels)
    private var renderer: MusicRenderer? = null
    private var pcmRenderer: PcmRenderer? = null
    private var output: AudioOutput? = null
    private var writer: ExerciseOutputWriter? = null
    private var exerciseRenderer: ExerciseRenderer? = null
    private var cached: RenderedExercise? = null
    private var open = false

    override val format: PcmFormat get() = pcmFormat

    override val isOpen: Boolean get() = open

    override fun open(soundFontFile: File, bank: Int, program: Int) {
        onWorker {
            require(!open) { "Diagnostic session is already open" }
            val created = MusicRenderer.create(renderFormat)
            try {
                created.loadSoundFont(
                    soundFontFile.absolutePath,
                    com.thomas.passiveeartraining.audio.ProgramSelection(bank, program)
                )
                created.prepare(
                    com.thomas.passiveeartraining.audio.RenderRequest(
                        program = com.thomas.passiveeartraining.audio.ProgramSelection(
                            bank,
                            program
                        )
                    )
                )
            } catch (error: Exception) {
                created.close()
                throw error
            }
            val device = AudioTrackOutput(pcmFormat)
            device.start(preBufferFrames = blockFrames)
            val adapter = NativePcmRenderer(created)
            renderer = created
            pcmRenderer = adapter
            output = device
            writer = ExerciseOutputWriter(device, blockFrames)
            exerciseRenderer = ExerciseRenderer(adapter, blockFrames)
            open = true
        }
    }

    override fun render(
        identity: String,
        sequenceIndex: Int,
        timeline: Timeline
    ): RenderedExercise {
        val rendered = onWorker {
            val engine =
                exerciseRenderer ?: throw AudioOutputException("Diagnostic session is not open")
            // Verified before playing: a missing clip fails with its semantic id instead of playing
            // an exercise with no answer.
            val missing = timeline.voices.map {
                it.voiceId.value
            }.filterNot { voiceFiles.containsKey(it) }
            if (missing.isNotEmpty()) {
                throw RenderFailureException("Missing voice clip(s) for: ${missing.joinToString()}")
            }
            engine.render(identity, sequenceIndex, timeline, voiceFiles).also { cached = it }
        }
        return rendered
    }

    override fun play(startFrame: Long) {
        onWorker {
            val device = output ?: throw AudioOutputException("Diagnostic session is not open")
            val current = cached ?: throw RenderFailureException("Nothing has been rendered yet")
            val activeWriter =
                writer ?: throw AudioOutputException("Diagnostic session is not open")
            if (startFrame > 0L) {
                // A resumed position can land inside a note; the short ramp removes the click.
                ExerciseOutputWriter.applyRamp(current, startFrame, pcmFormat.sampleRate)
            }
            device.start(preBufferFrames = blockFrames)
            activeWriter.play(current, startFrame)
        }
    }

    /**
     * Stops playback and keeps the position.
     *
     * The request is raised before the worker is joined, because the write loop blocks on the
     * device: without this the pause could only take effect once the whole exercise had been
     * written, which is what the reviewer heard.
     */
    override fun pause() {
        writer?.requestPause()
        onWorker {
            output?.pause()
        }
    }

    override fun requestPause() {
        // Deliberately off the worker: the worker is blocked in the write loop while the pair
        // plays, so only a direct call can stop it before the exercise ends.
        writer?.requestPause()
    }

    /** Frames the device has consumed, which is the position a later resume continues from. */
    fun retainedFrame(): Long = onWorker { writer?.retainedFrame() ?: 0L }

    /** True while the write loop is still active; off-worker so a sampler can poll it. */
    fun isWriting(): Boolean = writer?.isWriting() ?: false

    override fun stop() {
        onWorker {
            output?.flush()
            cached = null
        }
    }

    override fun consumedFrames(): Long = onWorker { writer?.playedFrames() ?: 0L }

    override fun metrics(): OutputMetrics = onWorker {
        writer?.metrics() ?: OutputMetrics(
            requestedSampleRate = pcmFormat.sampleRate,
            acceptedSampleRate = pcmFormat.sampleRate,
            channels = pcmFormat.channels,
            bufferCapacityFrames = 0,
            framesWritten = 0,
            framesPlayed = 0,
            underrunCount = 0,
            shortWrites = 0
        )
    }

    override fun close() {
        onWorker {
            // A diagnostics close can land while audio is still queued; fade it like any stop.
            runCatching { writer?.requestPause() }
            runCatching { output?.stop() }
            runCatching { renderer?.close() }
            output = null
            renderer = null
            pcmRenderer = null
            writer = null
            exerciseRenderer = null
            cached = null
            open = false
        }
    }

    /** Runs [block] on the audio worker and rethrows its failure to the caller. */
    private fun <T> onWorker(block: () -> T): T {
        val task = worker.submit(block)
        return try {
            task.get()
        } catch (error: java.util.concurrent.ExecutionException) {
            val cause = error.cause
            when (cause) {
                is RuntimeException -> throw cause
                is Error -> throw cause
                null -> throw AudioOutputException("Audio worker failed")
                else -> throw AudioOutputException(
                    cause.message ?: cause::class.java.simpleName,
                    cause
                )
            }
        }
    }
}
