package com.thomas.passiveeartraining.audio

/**
 * Kotlin ownership wrapper around one native renderer.
 *
 * Exactly one instance owns one native synth. Every method is expected to run on the single
 * worker that owns this renderer; the class itself is not thread safe, and there is no finalizer:
 * [close] is the only release path, it is idempotent, and any call afterwards fails with
 * [RendererClosedException] instead of touching a dangling native handle.
 */
class MusicRenderer private constructor(private var handle: Long, val format: RenderFormat) :
    AutoCloseable {

    /** True once [close] has run. */
    val isClosed: Boolean get() = handle == 0L

    /** Loads a SoundFont and selects bank/program. Fails with [AudioAssetException]. */
    fun loadSoundFont(path: String, program: ProgramSelection = ProgramSelection.PIANO) {
        requireOpen()
        require(path.isNotBlank()) { "SoundFont path must not be blank" }
        try {
            NativeRenderer.nativeLoadSoundFont(handle, path, program.bank, program.program)
        } catch (error: NativeAudioException) {
            throw AudioAssetException("Failed to load SoundFont '$path': ${error.message}", error)
        }
    }

    /** Applies one [RenderRequest]: reset, program, gain and polyphony. */
    fun prepare(request: RenderRequest) {
        requireOpen()
        NativeRenderer.nativeSetGain(handle, request.gain)
        NativeRenderer.nativeSetPolyphony(handle, request.polyphony)
        NativeRenderer.nativeSelectProgram(handle, request.program.bank, request.program.program)
    }

    /** Resets the synth so the next render cannot leak a previous exercise's notes or tails. */
    fun reset() {
        requireOpen()
        NativeRenderer.nativeSystemReset(handle)
    }

    /** Starts one note. */
    fun noteOn(note: RenderNote) {
        requireOpen()
        NativeRenderer.nativeNoteOn(handle, note.midiNote, note.velocity)
    }

    /** Releases one note. */
    fun noteOff(midiNote: Int) {
        requireOpen()
        NativeRenderer.nativeNoteOff(handle, midiNote)
    }

    /** Silences every sounding voice. */
    fun allNotesOff() {
        requireOpen()
        NativeRenderer.nativeAllNotesOff(handle)
    }

    /**
     * Renders up to [RenderRequest.MAX_BLOCK_FRAMES] frames of interleaved float PCM.
     *
     * [into] must hold at least `frames * format.channels` floats. Returns the number of frames
     * written, which is always [frames] for a successful call.
     */
    fun render(into: FloatArray, frames: Int): Int {
        requireOpen()
        require(frames in 1..RenderRequest.MAX_BLOCK_FRAMES) {
            "Frames must be in 1..${RenderRequest.MAX_BLOCK_FRAMES} but was $frames"
        }
        require(into.size >= frames * format.channels) {
            "Buffer holds ${into.size} floats but ${frames * format.channels} are required"
        }
        NativeRenderer.nativeRender(handle, into, frames)
        return frames
    }

    override fun close() {
        val current = handle
        handle = 0L
        if (current != 0L) {
            NativeRenderer.nativeClose(current)
        }
    }

    private fun requireOpen() {
        if (handle == 0L) {
            throw RendererClosedException("Renderer is closed")
        }
    }

    companion object {
        /**
         * Loads the native library once per process.
         *
         * Exposed as [ensureLoaded] because the WAV helpers use the same library and can be called
         * before any renderer exists.
         */
        private val libraryLoaded: Result<Unit> = runCatching { System.loadLibrary("ear_audio") }

        /** Ensures the native library is loaded, failing with a clear message if it is absent. */
        fun ensureLoaded() {
            libraryLoaded.getOrElse { error ->
                throw NativeAudioException("Native audio library 'ear_audio' is unavailable", error)
            }
        }

        /**
         * Creates a renderer for [format].
         *
         * @throws NativeAudioException when the format is unsupported or the native library is unavailable.
         */
        fun create(format: RenderFormat = RenderFormat.DEFAULT): MusicRenderer {
            ensureLoaded()
            val handle = NativeRenderer.nativeCreate(format.sampleRate, format.channels)
            if (handle == 0L) {
                throw NativeAudioException("Native renderer creation returned no handle")
            }
            return MusicRenderer(handle, format)
        }
    }
}

/** Direct JNI surface; internal so callers use [MusicRenderer]. */
internal object NativeRenderer {
    external fun nativeCreate(sampleRate: Int, channels: Int): Long
    external fun nativeLoadSoundFont(handle: Long, path: String, bank: Int, program: Int)
    external fun nativeSetGain(handle: Long, gain: Float)
    external fun nativeSetPolyphony(handle: Long, polyphony: Int)
    external fun nativeSelectProgram(handle: Long, bank: Int, program: Int)
    external fun nativeNoteOn(handle: Long, midiNote: Int, velocity: Int)
    external fun nativeNoteOff(handle: Long, midiNote: Int)
    external fun nativeAllNotesOff(handle: Long)
    external fun nativeSystemReset(handle: Long)
    external fun nativeRender(handle: Long, output: FloatArray, frames: Int)
    external fun nativeClose(handle: Long): Long
}

/** WAV helpers backed by miniaudio; used by the asset catalog and the offline render harness. */
object NativeAudioFiles {

    /**
     * The WAV helpers use the same native library as the renderer, so it must be loaded before any
     * of these calls: they can be reached without a renderer ever being created.
     */
    private fun ensureLibrary() = MusicRenderer.ensureLoaded()

    /** Format information of a WAV file: sample rate, channels and exact frame count. */
    data class WavInfo(val sampleRate: Int, val channels: Int, val frameCount: Long)

    /** Reads WAV format information without decoding samples. */
    fun wavInfo(path: String): WavInfo {
        ensureLibrary()
        val values = checkNotNull(nativeWavInfo(path)) { "No WAV information returned for $path" }
        require(values.size == 3) { "Unexpected WAV info payload of ${values.size} values" }
        return WavInfo(values[0].toInt(), values[1].toInt(), values[2])
    }

    /**
     * Decodes a WAV file to mono float samples at [targetSampleRate], converting channels and
     * rate through miniaudio. [capacity] bounds the accepted input length.
     */
    fun decodeMono(path: String, targetSampleRate: Int, capacity: Int): FloatArray {
        ensureLibrary()
        require(capacity in 1..384_000) { "Voice capacity must be in 1..384000" }
        require(targetSampleRate == 44_100 || targetSampleRate == 48_000)
        val buffer = FloatArray(capacity)
        val produced = nativeDecodeMono(path, targetSampleRate, buffer)
        require(produced > 0) { "Decoded no frames from $path" }
        return buffer.copyOf(produced)
    }

    /** Writes interleaved float samples to a WAV file; used by the host render harness. */
    fun writeWav(path: String, samples: FloatArray, channels: Int, sampleRate: Int, frames: Int) {
        ensureLibrary()
        require(channels in 1..2) { "WAV channels must be mono or stereo" }
        require(sampleRate == 44_100 || sampleRate == 48_000)
        require(frames in 1..sampleRate * 60) { "WAV duration must be at most 60 seconds" }
        val required = frames.toLong() * channels
        require(samples.size >= required) { "Buffer holds ${samples.size}; requires $required" }
        nativeWriteWav(path, samples, channels, sampleRate, frames)
    }

    external fun nativeWavInfo(path: String): LongArray?
    external fun nativeDecodeMono(path: String, targetSampleRate: Int, output: FloatArray): Int
    external fun nativeWriteWav(
        path: String,
        samples: FloatArray,
        channels: Int,
        sampleRate: Int,
        frames: Int
    )
}
