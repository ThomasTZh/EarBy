package com.thomas.passiveeartraining.audio.pcm

import com.thomas.passiveeartraining.training.NoteEvent
import com.thomas.passiveeartraining.training.Timeline
import com.thomas.passiveeartraining.training.VoiceEvent
import java.io.File

/**
 * The audio operations the offline renderer needs.
 *
 * Keeping this narrow means the render scheduling can be tested with a fake, and the native
 * renderer stays the only implementation that touches JNI.
 */
interface PcmRenderer {
    val format: PcmFormat

    /** Resets synth state so a new exercise cannot leak a previous one's notes or tails. */
    fun reset()

    fun noteOn(midiNote: Int, velocity: Int)

    fun noteOff(midiNote: Int)

    fun allNotesOff()

    /**
     * Announces that scheduling for the exercise is about to begin at [frame].
     *
     * The native adapter does not need this; it exists so a test double can observe the exact
     * boundary a note was scheduled at rather than inferring it from render calls.
     */
    fun schedulingFrame(frame: Long) {
        // Default: the renderer keeps no per-frame state, so nothing to do.
    }

    /** Renders exactly [frames] frames of interleaved float PCM into [output]. */
    fun render(output: FloatArray, frames: Int)

    /** Decodes a voice clip to mono float samples at the output sample rate. */
    fun decodeVoice(path: File, targetSampleRate: Int, capacityFrames: Int): FloatArray
}

/** Output format shared by the renderer, the cache and the device output. */
data class PcmFormat(val sampleRate: Int, val channels: Int) {
    init {
        require(sampleRate == 44_100 || sampleRate == 48_000) {
            "Supported sample rates are 44100 and 48000 but was $sampleRate"
        }
        require(channels in 1..2) { "Channels must be 1 or 2 but was $channels" }
    }
}

/** Raised when a timeline or an asset cannot be rendered for the output format. */
class RenderFailureException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/**
 * Renders one compiled exercise into a bounded PCM buffer.
 *
 * The conversion is frame-exact: every event boundary is converted independently with half-up
 * rounding, notes are turned on and off at those frame boundaries, and the music is rendered in
 * blocks of at most [maxBlockFrames]. Voice clips are decoded to their measured frame count and
 * mixed into their own windows afterwards, so music and speech share one frame clock instead of
 * running two independent players.
 */
class ExerciseRenderer(
    private val renderer: PcmRenderer,
    private val maxBlockFrames: Int = DEFAULT_BLOCK_FRAMES,
    private val noteVelocity: Int = DEFAULT_VELOCITY,
    private val voiceGain: Float = DEFAULT_VOICE_GAIN,
    /**
     * Uniform final-mix trim. It raises every rendered sample by the same factor, so per-note and
     * per-mode levels stay consistent; no compression or limiting is applied.
     */
    private val outputTrim: Float = 1f
) {
    init {
        require(maxBlockFrames in 1..4096) {
            "Block size must be in 1..4096 but was $maxBlockFrames"
        }
        require(noteVelocity in 1..127) { "Velocity must be in 1..127 but was $noteVelocity" }
        require(voiceGain >= 0f && voiceGain <= MAX_VOICE_GAIN) {
            "Voice gain must be in 0..$MAX_VOICE_GAIN but was $voiceGain"
        }
        require(outputTrim >= 0f && outputTrim <= MAX_OUTPUT_TRIM) {
            "Output trim must be in 0..$MAX_OUTPUT_TRIM but was $outputTrim"
        }
    }

    /**
     * Converts [timeline] to frames and validates it against the exercise cap.
     *
     * @throws FrameConversionException when the timeline cannot be represented at this format.
     */
    fun toFrameTimeline(timeline: Timeline): FrameTimeline {
        val rate = renderer.format.sampleRate
        val total = FrameConverter.framesAt(timeline.totalDuration, rate)
        val windows = mutableListOf<FrameWindow>()
        var previousEnd = 0L
        var previousStart = -1L
        for (event in timeline.orderedEvents) {
            val duration = event.duration ?: continue
            val start = FrameConverter.framesAt(event.startOffset, rate)
            val frames = FrameConverter.frameCount(duration, rate)
            val note = (event as? NoteEvent)?.note?.value
            val voice = (event as? VoiceEvent)?.voiceId?.value
            val separator = (event as? NoteEvent)?.separator == true
            if (start < previousEnd && start != previousStart) {
                // Harmonic notes share a start frame; any other overlap would double-schedule.
                throw FrameConversionException(
                    "Event $event starts at frame $start inside the previous window " +
                        "ending at $previousEnd"
                )
            }
            windows +=
                FrameWindow(
                    startFrame = start,
                    frameCount = frames,
                    note = note,
                    voiceId = voice,
                    separator = separator
                )
            previousEnd = maxOf(previousEnd, start + frames)
            previousStart = start
        }
        val seconds = total.toDouble() / rate
        if (seconds > BoundedPcmCache.MAX_EXERCISE_SECONDS) {
            throw FrameConversionException(
                "Exercise is ${"%.3f".format(seconds)} s, above the " +
                    "${BoundedPcmCache.MAX_EXERCISE_SECONDS} s cap"
            )
        }
        return FrameTimeline(rate, renderer.format.channels, total, windows.toList())
    }

    /**
     * Renders [timeline] into a new buffer.
     *
     * @param voiceFiles voice clip file per semantic asset id; a missing entry fails the render
     *   instead of silently omitting the answer.
     * @param separatorFile pre-rendered original separator clip. When null, separator notes are
     *   rendered by the main instrument for compatibility with diagnostics and tests.
     * @param separatorGain music gain applied to the separator clip.
     */
    fun render(
        identity: String,
        sequenceIndex: Int,
        timeline: Timeline,
        voiceFiles: Map<String, File>,
        separatorFile: File? = null,
        separatorGain: Float = 1f
    ): RenderedExercise {
        require(separatorGain >= 0f && separatorGain <= MAX_VOICE_GAIN) {
            "Separator gain must be in 0..$MAX_VOICE_GAIN but was $separatorGain"
        }
        val frameTimeline = toFrameTimeline(timeline)
        val channels = renderer.format.channels
        val exercise = RenderedExercise(
            identity = identity,
            sequenceIndex = sequenceIndex,
            sampleRate = renderer.format.sampleRate,
            channels = channels,
            totalFrames = frameTimeline.totalFrames,
            timeline = frameTimeline
        )
        val allNotes = frameTimeline.windows.filter { it.note != null }
        val useSeparatorClip = separatorFile != null && allNotes.any { it.separator }
        val separatorNotes = if (useSeparatorClip) allNotes.filter { it.separator } else emptyList()
        val notes = if (useSeparatorClip) allNotes.filterNot { it.separator } else allNotes
        val voices = frameTimeline.windows.filter { it.voiceId != null }

        renderer.reset()

        // Rendering is boundary-driven: the loop stops at every event boundary and inside those
        // segments never runs longer than one block. That keeps every note start and end
        // frame-exact while still bounding how much audio any single render call produces.
        val boundaries = (
            notes.map { it.startFrame } + notes.map { it.endFrame } +
                listOf(frameTimeline.totalFrames)
            )
            .filter { it in 0..frameTimeline.totalFrames }
            .distinct()
            .sorted()
        val activeNotes = mutableMapOf<Int, Long>()
        val block = FloatArray(maxBlockFrames * channels)
        var frame = 0L
        for (boundary in boundaries) {
            while (frame < boundary) {
                val frames = minOf(maxBlockFrames.toLong(), boundary - frame).toInt()
                val blockEnd = frame + frames

                renderer.schedulingFrame(frame)
                // Note ends are processed before note starts, so a window ending on this frame
                // frees its voice before a new note begins there.
                activeNotes.filterValues { it <= frame }.keys.toList().forEach { note ->
                    renderer.noteOff(note)
                    activeNotes.remove(note)
                }
                notes.filter { it.startFrame == frame }.forEach { window ->
                    val note = window.note ?: return@forEach
                    renderer.noteOn(note, noteVelocity)
                    activeNotes[note] = window.endFrame
                }

                // Render only where a note is sounding. The synth is reset at the start of the
                // exercise, so a block with no active voice is silent by definition and skipping
                // it keeps long silent gaps free of synthesizer work.
                if (activeNotes.isNotEmpty()) {
                    renderer.render(block, frames)
                    System.arraycopy(
                        block,
                        0,
                        exercise.samples,
                        (frame * channels).toInt(),
                        frames * channels
                    )
                }
                frame = blockEnd
            }
        }
        activeNotes.filterValues { it <= frame }.keys.toList().forEach { note ->
            renderer.noteOff(note)
            activeNotes.remove(note)
        }
        renderer.allNotesOff()

        // The synth keeps sounding through the sample's own release, which would bleed into the
        // following gap. Audible note windows therefore end with a short final envelope so a
        // specified silence really is silent, as the architecture requires. A note whose end frame
        // is also the start of the next event is different: its release tail is intentionally kept
        // by clearReleaseTails, and fading the window to zero first makes the tail jump back up at
        // the boundary. That jump is the click heard between back-to-back notes and chords.
        val eventStarts = (notes.map { it.startFrame } + voices.map { it.startFrame }).toSet()
        // A chord is several notes with the same start frame. Apply the onset softening once per
        // unique start, not once per note, so the attack of a four-note chord is not multiplied.
        notes.map { it.startFrame }.distinct().forEach { start ->
            applyOnsetEnvelope(exercise, start)
        }
        // Likewise, apply the final envelope once per unique chord window. The earlier per-note
        // application made a four-note chord fade about four times too steeply.
        notes.distinctBy { it.startFrame to it.endFrame }.forEach { window ->
            if (window.endFrame !in eventStarts) {
                applyFinalEnvelope(exercise, window)
            }
        }
        clearReleaseTails(exercise, allNotes, voices)
        voices.forEach { window -> mixVoice(exercise, window, voiceFiles) }
        if (useSeparatorClip && separatorNotes.isNotEmpty()) {
            mixSeparator(exercise, separatorNotes, separatorFile!!, separatorGain)
        }
        if (outputTrim != 1f) {
            // One uniform final-mix factor: no per-note normalization and no compression.
            for (index in exercise.samples.indices) {
                exercise.samples[index] *= outputTrim
            }
        }
        return exercise
    }

    /**
     * Mixes the original two-note separator clip at the first separator note. The clip already
     * contains its own envelope, so the Osiris note envelopes are not applied to it.
     */
    private fun mixSeparator(
        exercise: RenderedExercise,
        separatorNotes: List<FrameWindow>,
        separatorFile: File,
        gain: Float
    ) {
        val clip = decodedVoices.getOrPut(SEPARATOR_DECODE_KEY) {
            if (!separatorFile.isFile) {
                throw RenderFailureException("Separator clip does not exist: ${separatorFile.path}")
            }
            renderer.decodeVoice(
                separatorFile,
                renderer.format.sampleRate,
                MAX_VOICE_FRAMES
            )
        }
        val start = separatorNotes.minOf { it.startFrame }
        val frames = minOf(clip.size.toLong(), exercise.totalFrames - start)
        if (frames <= 0L) return
        val channels = exercise.channels
        for (frame in 0 until frames) {
            val value = clip[frame.toInt()] * gain
            for (channel in 0 until channels) {
                val index = ((start + frame) * channels + channel).toInt()
                exercise.samples[index] += value
            }
        }
    }

    /**
     * Softens the first [ONSET_ENVELOPE_MILLISECONDS] of every unique note start with a
     * raised-cosine ramp. A single note's attack is usually inaudible; several samples starting on
     * the same frame sum their attack transient into a click. The ramp leaves the scheduled start
     * frame unchanged.
     */
    private fun applyOnsetEnvelope(exercise: RenderedExercise, startFrame: Long) {
        val rate = exercise.sampleRate
        val fadeFrames = (rate.toLong() * ONSET_ENVELOPE_MILLISECONDS / 1000L)
            .toInt()
            .coerceAtLeast(1)
        val channels = exercise.channels
        val start = maxOf(0L, startFrame)
        val end = minOf(startFrame + fadeFrames, exercise.totalFrames)
        for (frame in start until end) {
            val position = (frame - startFrame).toFloat() / fadeFrames.toFloat()
            val gain = 0.5f * (1f - kotlin.math.cos(Math.PI.toFloat() * position))
            for (channel in 0 until channels) {
                val index = (frame * channels + channel).toInt()
                exercise.samples[index] = exercise.samples[index] * gain
            }
        }
    }

    /**
     * Fades the last [FINAL_ENVELOPE_MILLISECONDS] of a note window to zero when the window ends
     * before a gap. A window whose end coincides with the next event start is left untouched: its
     * release tail continues into that event without a zero-to-release discontinuity.
     *
     * The envelope is part of the note's own scheduled window, so the following gap contains exact
     * silence and the note does not click when it stops.
     */
    private fun applyFinalEnvelope(exercise: RenderedExercise, window: FrameWindow) {
        val rate = exercise.sampleRate
        val fadeFrames = (rate.toLong() * FINAL_ENVELOPE_MILLISECONDS / 1000L)
            .toInt()
            .coerceAtLeast(1)
        val channels = exercise.channels
        val start = maxOf(0L, window.endFrame - fadeFrames)
        val end = minOf(window.endFrame, exercise.totalFrames)
        for (frame in start until end) {
            val remaining = (window.endFrame - frame).toFloat() / fadeFrames.toFloat()
            // Raised-cosine, not linear: the slope is zero at both ends, so the fade itself does
            // not introduce a step or a slope discontinuity on low notes.
            val gain = 0.5f * (1f + kotlin.math.cos(Math.PI.toFloat() * (1f - remaining)))
            for (channel in 0 until channels) {
                val index = (frame * channels + channel).toInt()
                exercise.samples[index] = exercise.samples[index] * gain
            }
        }
    }

    /**
     * Clears whatever the instrument keeps sounding after a note window ends.
     *
     * A SoundFont piano decays for hundreds of milliseconds after note-off, which would bleed into
     * a following gap and make the timeline's specified silence untrue. The rendered tail is
     * therefore removed between a note window and the next audible event; the short envelope keeps
     * the cut click-free and inaudible on a piano tone. Silence in the gaps is a contract of this
     * product, so it wins over the sample's natural decay.
     */
    private fun clearReleaseTails(
        exercise: RenderedExercise,
        notes: List<FrameWindow>,
        voices: List<FrameWindow>
    ) {
        val eventStarts = (notes.map { it.startFrame } + voices.map { it.startFrame }).sorted()
        val channels = exercise.channels
        for (window in notes) {
            // A note that starts exactly when this window ends is the next event, not part of this
            // window's release: clearing from `>=` leaves that note untouched. Looking only for a
            // start strictly after the end would erase the whole next note when the two are
            // back-to-back, which is what the pair separator exposed.
            val nextStart = eventStarts.firstOrNull { it >= window.endFrame }
                ?: exercise.totalFrames
            val to = minOf(nextStart, exercise.totalFrames)
            for (frame in window.endFrame until to) {
                for (channel in 0 until channels) {
                    exercise.samples[(frame * channels + channel).toInt()] = 0f
                }
            }
        }
    }

    private fun mixVoice(
        exercise: RenderedExercise,
        window: FrameWindow,
        voiceFiles: Map<String, File>
    ) {
        val voiceId = window.voiceId ?: return
        val samples = decodedVoices.getOrPut(voiceId) {
            val file = voiceFiles[voiceId]
                ?: throw RenderFailureException("No voice clip was provided for '$voiceId'")
            if (!file.isFile) {
                throw RenderFailureException(
                    "Voice clip for '$voiceId' does not exist: ${file.path}"
                )
            }
            renderer.decodeVoice(file, renderer.format.sampleRate, MAX_VOICE_FRAMES)
        }
        val channels = renderer.format.channels
        val end = minOf(window.endFrame, exercise.totalFrames)
        for (frame in window.startFrame until end) {
            val source = (frame - window.startFrame).toInt()
            // The answer clip is scaled by the voice gain: the measured balance between the
            // normalised speech (about -19 LUFS) and a rendered piano note needs it, and the two
            // gains are the documented product controls.
            val value = samples.getOrElse(source) { 0f } * voiceGain
            for (channel in 0 until channels) {
                val index = (frame * channels + channel).toInt()
                exercise.samples[index] += value
            }
        }
    }

    /** Decoded voice clips for the exercise currently being rendered. */
    private val decodedVoices = mutableMapOf<String, FloatArray>()

    companion object {
        const val DEFAULT_BLOCK_FRAMES: Int = 1024
        const val DEFAULT_VELOCITY: Int = 96

        /** One voice clip is at most 8 s; longer files are rejected rather than truncated. */
        const val MAX_VOICE_FRAMES: Int = 48_000 * 8

        /**
         * Default gain applied to the spoken answer relative to the example.
         *
         * Measured on the packaged assets: the normalised voice clip sits about 12 dB above a
         * rendered piano note, which the first phone listening review heard as "voice too loud or
         * piano too low". One quarter (-12 dB) brings the two into the same range while keeping the
         * words clearly intelligible.
         */
        const val DEFAULT_VOICE_GAIN: Float = 0.25f

        /** Upper bound of the voice gain control. */
        const val MAX_VOICE_GAIN: Float = 4f

        /** Upper bound of the uniform final-mix trim. */
        const val MAX_OUTPUT_TRIM: Float = 2f

        /**
         * Length of the final envelope applied to every audible note window.
         *
         * The earlier 5 ms linear fade was applied once per chord note, so a four-note chord got
         * a fade four times steeper. The longer raised-cosine fade is applied once per unique
         * chord window; it still ends exactly at the scheduled note boundary.
         */
        const val FINAL_ENVELOPE_MILLISECONDS: Int = 20

        /**
         * Length of the onset ramp applied once per unique note start.
         *
         * Several samples starting together otherwise sum their first milliseconds into a click.
         * The ramp is short enough not to soften the piano attack audibly.
         */
        const val ONSET_ENVELOPE_MILLISECONDS: Int = 5

        /** Private decode-map key for the pre-rendered separator clip. */
        private const val SEPARATOR_DECODE_KEY = "\u0000separator"
    }
}
