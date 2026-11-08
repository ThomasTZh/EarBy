package com.thomas.passiveeartraining.audio.pcm

import com.thomas.passiveeartraining.audio.MusicRenderer
import com.thomas.passiveeartraining.audio.NativeAudioFiles
import com.thomas.passiveeartraining.audio.RenderNote
import java.io.File

/**
 * Adapter from the native renderer to the offline render contract.
 *
 * This is the only place where the PCM pipeline touches JNI; the scheduling logic above it is
 * plain Kotlin and is exercised by host unit tests.
 */
class NativePcmRenderer(private val musicRenderer: MusicRenderer) : PcmRenderer {

    override val format: PcmFormat = PcmFormat(
        sampleRate = musicRenderer.format.sampleRate,
        channels = musicRenderer.format.channels
    )

    override fun reset() = musicRenderer.reset()

    override fun noteOn(midiNote: Int, velocity: Int) =
        musicRenderer.noteOn(RenderNote(midiNote, velocity))

    override fun noteOff(midiNote: Int) = musicRenderer.noteOff(midiNote)

    override fun allNotesOff() = musicRenderer.allNotesOff()

    override fun render(output: FloatArray, frames: Int) {
        musicRenderer.render(output, frames)
    }

    override fun decodeVoice(path: File, targetSampleRate: Int, capacityFrames: Int): FloatArray =
        NativeAudioFiles.decodeMono(path.absolutePath, targetSampleRate, capacityFrames)
}
