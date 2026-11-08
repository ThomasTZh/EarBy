package com.thomas.passiveeartraining.audio

/**
 * Output format of one renderer instance.
 *
 * The requested format is negotiated once at construction; a format the platform cannot render
 * fails there rather than mid-session.
 */
data class RenderFormat(val sampleRate: Int, val channels: Int) {
    init {
        require(sampleRate > 0) { "Sample rate must be positive but was $sampleRate" }
        require(channels == 1 || channels == 2) { "Channels must be 1 or 2 but was $channels" }
        require(sampleRate == 44_100 || sampleRate == 48_000) {
            "Supported sample rates are 44100 and 48000 but was $sampleRate"
        }
    }

    companion object {
        val DEFAULT: RenderFormat = RenderFormat(sampleRate = 48_000, channels = 2)
        val MONO_48K: RenderFormat = RenderFormat(sampleRate = 48_000, channels = 1)
    }
}

/** Plain bank/program selection, matching the roadmap's bank 0 / program 0 piano default. */
data class ProgramSelection(val bank: Int = 0, val program: Int = 0) {
    init {
        require(bank in 0..128) { "Bank must be in 0..128 but was $bank" }
        require(program in 0..127) { "Program must be in 0..127 but was $program" }
    }

    companion object {
        val PIANO: ProgramSelection = ProgramSelection()
    }
}

/** One note request: MIDI value, velocity and an optional sounding duration in frames. */
data class RenderNote(val midiNote: Int, val velocity: Int = RenderRequest.DEFAULT_VELOCITY) {
    init {
        require(midiNote in 0..127) { "MIDI note must be in 0..127 but was $midiNote" }
        require(velocity in 1..127) { "Velocity must be in 1..127 but was $velocity" }
    }
}

/**
 * One independently renderable exercise.
 *
 * A render always starts from a reset synth state: program, gain, notes and effect tails are
 * cleared so a previous exercise cannot leak into this one.
 */
data class RenderRequest(
    val program: ProgramSelection = ProgramSelection.PIANO,
    val gain: Float = DEFAULT_GAIN,
    val polyphony: Int = DEFAULT_POLYPHONY
) {
    init {
        require(gain >= 0f && gain <= MAX_GAIN) { "Gain must be in 0..$MAX_GAIN but was $gain" }
        require(polyphony in 1..MAX_POLYPHONY) {
            "Polyphony must be in 1..$MAX_POLYPHONY but was $polyphony"
        }
    }

    companion object {
        /**
         * Default music gain. The piano listening review asked for more level twice: 0.5 -> 0.8
         * and then 1.25x that, so the default is 1.0. The slider shows this default at its middle;
         * the upper half is available headroom above the reviewed level, and very high settings can
         * distort on dense material.
         */
        const val DEFAULT_GAIN: Float = 1.0f
        const val DEFAULT_VELOCITY: Int = 96
        const val DEFAULT_POLYPHONY: Int = 32

        /** Highest gain the renderer accepts; the reviewed default sits at 25% of this scale. */
        const val MAX_GAIN: Float = 4.0f
        const val MAX_POLYPHONY: Int = 128

        /** Renders at most this many frames in one call; the cache is built from such blocks. */
        const val MAX_BLOCK_FRAMES: Int = 1024

        val DEFAULT: RenderRequest = RenderRequest()
    }
}
