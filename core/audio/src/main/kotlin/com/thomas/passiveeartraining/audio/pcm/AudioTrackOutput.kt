package com.thomas.passiveeartraining.audio.pcm

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * Android device output backed by one AudioTrack.
 *
 * Exactly one instance owns one AudioTrack, and the pipeline is the only writer. The buffer starts
 * from the platform minimum and grows only if the measured underrun count says it should; the
 * accepted sample rate is reported back because a device may not honour the request.
 */
class AudioTrackOutput(
    override val format: PcmFormat,
    private val requestedBufferMultiplier: Int = DEFAULT_BUFFER_MULTIPLIER
) : AudioOutput {

    @Volatile
    private var track: AudioTrack? = null
    private var acceptedRate: Int = format.sampleRate
    private var capacityFrames: Int = 0

    /**
     * Position retained across flushes.
     *
     * AudioTrack's own playback head restarts from zero after a flush, which would report a pause
     * at the wrong position and make the deferred pause wait forever. The consumed position is
     * therefore the head plus whatever was flushed before.
     */
    private val head = PlaybackHeadCounter()

    @Synchronized
    override fun start(preBufferFrames: Int) {
        if (track != null) {
            if (track?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                head.start()
                track?.play()
            }
            return
        }
        val channelMask = if (format.channels == 1) {
            AudioFormat.CHANNEL_OUT_MONO
        } else {
            AudioFormat.CHANNEL_OUT_STEREO
        }
        val encoding = AudioFormat.ENCODING_PCM_FLOAT
        val minBytes = AudioTrack.getMinBufferSize(format.sampleRate, channelMask, encoding)
        if (minBytes <= 0) {
            throw AudioOutputException(
                "AudioTrack rejected ${format.sampleRate} Hz / " +
                    "${format.channels} channel float output"
            )
        }
        val bufferBytes = maxOf(minBytes, preBufferFrames * format.channels * BYTES_PER_FLOAT) *
            requestedBufferMultiplier
        val created = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(format.sampleRate)
                    .setChannelMask(channelMask)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferBytes)
            .build()
        if (created.state != AudioTrack.STATE_INITIALIZED) {
            created.release()
            throw AudioOutputException(
                "AudioTrack failed to initialize " +
                    "(${format.sampleRate} Hz, ${format.channels} channel)"
            )
        }
        head.start()
        try {
            created.play()
        } catch (failure: Exception) {
            created.release()
            throw AudioOutputException("AudioTrack could not start", failure)
        }
        track = created
        acceptedRate = created.sampleRate
        capacityFrames = created.bufferSizeInFrames
    }

    override fun write(samples: FloatArray, offset: Int, frames: Int): Int {
        val current = track ?: throw AudioOutputException("Audio output has not been started")
        val elements = frames * format.channels
        val written = current.write(samples, offset, elements, AudioTrack.WRITE_BLOCKING)
        if (written < 0) {
            // A dead object means the route changed out from under us; the caller pauses and
            // recreates the stream on explicit resume.
            throw AudioOutputException("AudioTrack write failed with code $written")
        }
        return written / format.channels
    }

    override fun pause() {
        track?.takeIf { it.playState == AudioTrack.PLAYSTATE_PLAYING }?.pause()
    }

    @Synchronized
    override fun flush() {
        val current = track ?: return
        head.flush(current.playbackHeadPosition)
        current.pause()
        current.flush()
    }

    @Synchronized
    override fun stop() {
        val current = track ?: return
        track = null
        var failure: Exception? = null
        for (operation in listOf<() -> Unit>(
            { current.pause() },
            { current.flush() },
            { current.release() }
        )) {
            try {
                operation()
            } catch (caught: Exception) {
                val first = failure
                if (first == null) failure = caught else first.addSuppressed(caught)
            }
        }
        capacityFrames = 0
        head.reset()
        failure?.let { throw AudioOutputException("AudioTrack cleanup failed", it) }
    }

    @Synchronized
    override fun playedFrames(): Long = head.position(track?.playbackHeadPosition ?: 0)

    override fun bufferCapacityFrames(): Int = capacityFrames

    override fun underrunCount(): Int {
        // AudioTrack.underrunCount is available from API 24 and minSdk is 29, so no fallback is
        // needed; a released track reports zero.
        return track?.underrunCount ?: 0
    }

    override fun isPlaying(): Boolean = track?.playState == AudioTrack.PLAYSTATE_PLAYING

    override fun acceptedSampleRate(): Int = acceptedRate

    companion object {
        const val DEFAULT_BUFFER_MULTIPLIER: Int = 2
        const val BYTES_PER_FLOAT: Int = 4

        /** Audio focus attributes used when the platform asks the app for its category. */
        const val STREAM_ATTRIBUTE: Int = AudioManager.STREAM_MUSIC
    }
}
