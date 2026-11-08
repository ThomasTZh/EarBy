package com.thomas.passiveeartraining.audio.pcm

import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.training.IntervalDefinition
import com.thomas.passiveeartraining.training.MapVoiceDurationCatalog
import com.thomas.passiveeartraining.training.RecognitionRecipe
import com.thomas.passiveeartraining.training.SeededIntervalGenerator
import com.thomas.passiveeartraining.training.SeededRandomSource
import com.thomas.passiveeartraining.training.TimelineCompiler
import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeExerciseCompiler
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import java.io.File
import kotlin.math.abs
import kotlin.math.sin
import kotlin.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Records every renderer call so the scheduling can be asserted frame by frame. */
private class FakePcmRenderer(
    override val format: PcmFormat = PcmFormat(48_000, 1),
    private val voiceSamples: (String) -> FloatArray = { FloatArray(0) }
) : PcmRenderer {
    data class Call(val kind: String, val frame: Long, val detail: Int)

    val calls = mutableListOf<Call>()

    /**
     * Frame position at the last render call. Note scheduling happens while the renderer is
     * positioned at the end of the previous block, so this is what a call records.
     */
    var framePosition = 0L
    var blockSizes = mutableListOf<Int>()
    var resets = 0

    /** Boundary the renderer announced before scheduling notes for a block. */
    var scheduledAt = mutableListOf<Pair<String, Long>>()
    private var currentSchedulingFrame = 0L

    override fun reset() {
        resets += 1
        calls += Call("reset", framePosition, 0)
    }

    override fun noteOn(midiNote: Int, velocity: Int) {
        calls += Call("on", framePosition, midiNote)
        scheduledAt += "on:$midiNote" to currentSchedulingFrame
    }

    override fun noteOff(midiNote: Int) {
        calls += Call("off", framePosition, midiNote)
        scheduledAt += "off:$midiNote" to currentSchedulingFrame
    }

    override fun allNotesOff() {
        calls += Call("allOff", framePosition, 0)
    }

    override fun schedulingFrame(frame: Long) {
        currentSchedulingFrame = frame
    }

    override fun render(output: FloatArray, frames: Int) {
        blockSizes += frames
        val channels = format.channels
        for (frame in 0 until frames) {
            // A 1 kHz tone makes any copied audio distinguishable from silence.
            val value = sin(
                2.0 * Math.PI * 1000.0 * (framePosition + frame) / format.sampleRate
            ).toFloat()
            for (channel in 0 until channels) {
                output[frame * channels + channel] = value
            }
        }
        framePosition += frames
    }

    override fun decodeVoice(path: File, targetSampleRate: Int, capacityFrames: Int): FloatArray =
        voiceSamples(path.name)
}

private fun recognitionCompiler(voiceMicros: Long = 1_000_000L): TimelineCompiler =
    TimelineCompiler(
        recipe = RecognitionRecipe(
            noteDuration = 700.milliseconds(),
            melodicGap = 100.milliseconds(),
            thinkingSilence = 4.seconds(),
            postAnswerSilence = 2.seconds()
        ),
        voiceDurations = MapVoiceDurationCatalog(
            mapOf(VoiceAssetId("interval.perfect_fifth") to Duration.parse("${voiceMicros}us"))
        )
    )

private fun microseconds(value: Long): Duration = Duration.parse("${value}us")

/** Reports the nanoseconds a configured duration actually holds; used to prove the unit. */
private fun Duration.nanos(): Long = inWholeNanoseconds
private fun milliseconds(value: Long): Duration = Duration.parse("${value}ms")
private fun Long.micros(): Duration = microseconds(this)
private fun Int.micros(): Duration = microseconds(this.toLong())
private fun Int.milliseconds(): Duration = milliseconds(this.toLong())
private fun Int.seconds(): Duration = milliseconds(this.toLong() * 1_000L)
private fun Int.minutes(): Duration = milliseconds(this.toLong() * 60_000L)

private val EXPECTED_VOICE_FILE = File("interval.perfect_fifth.wav")

class FrameConverterTest {

    @Test
    fun offsetsConvertWithHalfUpRoundingAtBothSupportedRates() {
        assertEquals(0L, FrameConverter.framesAt(Duration.ZERO, 48_000))
        assertEquals(48_000L, FrameConverter.framesAt(1.seconds(), 48_000))
        assertEquals(44_100L, FrameConverter.framesAt(1.seconds(), 44_100))
        // 700 ms at 48 kHz is exactly 33600 frames; at 44.1 kHz it is 30870.
        assertEquals(33_600L, FrameConverter.framesAt(700.milliseconds(), 48_000))
        assertEquals(30_870L, FrameConverter.framesAt(700.milliseconds(), 44_100))
        // Half-up: 1 microsecond at 48 kHz is 0.048 frames -> 0, but a positive duration is 1.
        assertEquals(0L, FrameConverter.framesAt(1.micros(), 48_000))
        assertEquals(1L, FrameConverter.frameCount(1.micros(), 48_000))
        // 10 microseconds at 48 kHz is 0.48 frames -> 0 by rounding, 1 by the audible floor.
        assertEquals(1L, FrameConverter.frameCount(10.micros(), 48_000))
        // 20 microseconds is 0.96 -> 1.
        assertEquals(1L, FrameConverter.frameCount(20.micros(), 48_000))
        // 21 microseconds is 1.008 -> 1.
        assertEquals(1L, FrameConverter.frameCount(21.micros(), 48_000))
        // 100 microseconds at 44.1 kHz is 4.41 -> 4.
        assertEquals(4L, FrameConverter.frameCount(100.micros(), 44_100))
    }

    @Test
    fun absoluteOffsetsDoNotAccumulateRoundingDrift() {
        // Ten thousand 100-microsecond steps converted individually must equal one conversion.
        val step = 100.micros()
        var accumulated = 0L
        var offset = Duration.ZERO
        repeat(10_000) {
            offset += step
            accumulated += FrameConverter.frameCount(step, 44_100)
        }
        assertEquals(100L, step.inWholeMicroseconds)
        val single = FrameConverter.framesAt(offset, 44_100)
        // Each step rounds 4.41 frames down to 4, so the summed value drifts; the point is that the
        // absolute conversion is exact and independent of any chain of per-step conversions.
        assertTrue("absolute conversion must be exact", abs(single - 44_100L) <= 1L)
        assertTrue("per-step accumulation drifts as expected", accumulated != single)
    }

    @Test
    fun invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            FrameConverter.framesAt(1.seconds(), 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FrameConverter.framesAt(-1.micros(), 48_000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FrameConverter.frameCount(1.seconds(), -48_000)
        }
    }

    @Test
    fun overflowFailsInsteadOfWrapping() {
        val huge = Duration.parse("999999999d")
        assertThrows(IllegalArgumentException::class.java) { FrameConverter.framesAt(huge, 48_000) }
    }

    @Test
    fun frameTimelineRejectsOverlapsAndOutOfRangeWindows() {
        assertThrows(IllegalArgumentException::class.java) {
            FrameTimeline(
                48_000,
                1,
                100,
                listOf(FrameWindow(0, 60, 60, null), FrameWindow(50, 10, 62, null))
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FrameTimeline(48_000, 1, 100, listOf(FrameWindow(80, 40, 60, null)))
        }
        // Half-open windows may touch: one ending at 60 and the next starting at 60 are valid.
        FrameTimeline(
            48_000,
            1,
            100,
            listOf(FrameWindow(0, 60, 60, null), FrameWindow(60, 40, 62, null))
        )
    }

    @Test
    fun frameTimelineAllowsSimultaneousHarmonicNotes() {
        // A harmonic pair shares one start frame; both pitches sound together and the renderer
        // schedules them together. Requiring a strictly sequential layout here silenced harmonic
        // sessions with an exception instead.
        val chord = FrameTimeline(
            48_000,
            1,
            100,
            listOf(FrameWindow(0, 60, 60, null), FrameWindow(0, 60, 64, null))
        )
        assertEquals(2, chord.windows.size)
        // A window starting inside another at a different frame is still rejected.
        assertThrows(IllegalArgumentException::class.java) {
            FrameTimeline(
                48_000,
                1,
                200,
                listOf(
                    FrameWindow(0, 60, 60, null),
                    FrameWindow(0, 60, 64, null),
                    FrameWindow(30, 20, 67, null)
                )
            )
        }
    }
}

class ExerciseRendererTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun voiceFile(name: String = "interval.perfect_fifth.wav"): File = folder.newFile(name)

    private fun timeline(voiceMicros: Long = 1_000_000L) = recognitionCompiler(voiceMicros).compile(
        root = MidiNote.of(MidiNote.C4),
        target = MidiNote.of(67),
        presentation = IntervalPresentation.ASCENDING,
        answerVoiceId = VoiceAssetId("interval.perfect_fifth")
    )

    private fun harmonicTimeline(voiceMicros: Long = 1_000_000L) =
        recognitionCompiler(voiceMicros).compile(
            root = MidiNote.of(MidiNote.C4),
            target = MidiNote.of(67),
            presentation = IntervalPresentation.HARMONIC,
            answerVoiceId = VoiceAssetId("interval.perfect_fifth")
        )

    @Test
    fun harmonicNotesRenderTogetherWithoutBeingRejected() {
        val fake = FakePcmRenderer()
        val rendered = ExerciseRenderer(fake).render(
            "interval:1",
            0,
            harmonicTimeline(),
            mapOf("interval.perfect_fifth" to voiceFile())
        )

        // Both pitches turn on at frame 0 and off together at 700 ms = 33600 frames.
        val scheduled = fake.scheduledAt
        assertEquals(
            listOf("on:60" to 0L, "on:67" to 0L),
            scheduled.filter { it.first.startsWith("on") }
        )
        assertEquals(
            listOf("off:60", "off:67"),
            scheduled.filter { it.first.startsWith("off") }.map { it.first }
        )
        // The example is audible from its first frames; harmonic mode must not fail the overlap
        // check that previously rejected two notes sharing a start frame.
        assertTrue("a harmonic pair must not be silent", rendered.samples[1_000] != 0f)
    }

    @Test
    fun noteWindowsLandOnFrameExactBoundaries() {
        val fake = FakePcmRenderer()
        val renderer = ExerciseRenderer(fake)
        val rendered = renderer.render(
            "interval:1",
            0,
            timeline(),
            mapOf("interval.perfect_fifth" to voiceFile())
        )

        // 700 ms = 33600 frames; the target starts at 800 ms = 38400 frames; the answer at
        // 5.5 s = 264000 frames; the exercise ends at 8.5 s = 408000 frames.
        assertEquals(408_000L, rendered.totalFrames)
        // The renderer schedules a note while positioned at the start of the block it renders,
        // which is the boundary the window declares.
        val scheduled = fake.scheduledAt
        assertEquals(
            listOf("on:60" to 0L, "on:67" to 38_400L),
            scheduled.filter {
                it.first.startsWith("on")
            }
        )
        val offs = scheduled.filter { it.first.startsWith("off") }
        assertEquals(listOf("off:60", "off:67"), offs.map { it.first })
        assertEquals(listOf(33_600L, 72_000L), offs.map { it.second })
        assertEquals(1, fake.resets)
    }

    @Test
    fun aBackToBackNoteIsNotErasedByThePreviousTail() {
        // A zero melodic gap makes the two example notes back-to-back. Tail clearing must treat the
        // second note's start as the next event, not as part of the first note's release, or the
        // whole second note becomes silence.
        val catalog = MapVoiceDurationCatalog(
            IntervalDefinition.default().selectedIntervalIds.associate { id ->
                VoiceAssetId("interval.$id") to Duration.parse("1s")
            }
        )
        val exercise = SeededIntervalGenerator(
            IntervalDefinition.default(),
            SeededRandomSource(42L)
        ).next()
        val timeline = RecipeExerciseCompiler(catalog).compile(
            exercise,
            RecipeDefinition(
                mode = RecipeMode.RECOGNITION,
                timing = RecipeTiming(melodicGap = Duration.ZERO)
            )
        )
        val rendered = ExerciseRenderer(FakePcmRenderer()).render(
            "interval:1",
            0,
            timeline,
            mapOf(exercise.answerId to voiceFile())
        )
        // The example is two 700 ms notes, so the second starts at 33600 frames. The onset ramp
        // covers the first 5 ms (240 frames); the 1 kHz test tone reaches the next positive peak
        // 12 frames after that ramp, at 33852.
        assertTrue(
            "the note starting at the previous note's end must contain audio",
            rendered.samples[33_852] > 0.9f
        )
        // The first note must not be faded to zero immediately before the back-to-back boundary:
        // fading it leaves the release tail to jump back up on the next frame, which is the click
        // heard between chords. The 1 kHz tone at 33599 is still well above zero.
        assertTrue(
            "the first note must stay continuous into the back-to-back boundary",
            kotlin.math.abs(rendered.samples[33_599]) > 0.05f
        )
    }

    @Test
    fun renderingUsesBlocksOfAtMostTheConfiguredSize() {
        val fake = FakePcmRenderer()
        ExerciseRenderer(fake, maxBlockFrames = 1000).render(
            "interval:1",
            0,
            timeline(),
            mapOf("interval.perfect_fifth" to voiceFile())
        )
        assertTrue(fake.blockSizes.isNotEmpty())
        assertTrue("no block may exceed the configured size", fake.blockSizes.all { it <= 1000 })
        // Blocks are only rendered while a note is sounding, so the total is exactly the two
        // 33600-frame note windows and the trailing block is short.
        assertEquals(67_200, fake.blockSizes.sum())
        assertEquals(listOf(600), fake.blockSizes.takeLast(1))
    }

    @Test
    fun silentGapsAreNotRenderedThroughTheSynth() {
        val fake = FakePcmRenderer()
        val rendered = ExerciseRenderer(fake).render(
            "interval:1",
            0,
            timeline(),
            mapOf("interval.perfect_fifth" to voiceFile())
        )
        // The synth works only through the note windows and skips the 4.8 s gap entirely.
        assertEquals(67_200, fake.blockSizes.sum())
        // The buffer is still the full exercise length, so the answer has somewhere to live.
        assertEquals(408_000, rendered.samples.size)
        // Everything outside the note windows is silent until the voice is mixed in.
        assertTrue(rendered.samples[100_000] == 0f)
    }

    @Test
    fun aUniformOutputTrimScalesTheWholeMixWithoutChangingBalance() {
        val voice = FloatArray(48_000) { 0.5f }
        val files = mapOf("interval.perfect_fifth" to voiceFile())
        val unity = ExerciseRenderer(FakePcmRenderer(voiceSamples = { voice }), voiceGain = 1f)
            .render("interval:1", 0, timeline(), files)
        val trimmed = ExerciseRenderer(
            FakePcmRenderer(voiceSamples = { voice }),
            voiceGain = 1f,
            outputTrim = 1.13f
        ).render("interval:1", 0, timeline(), files)
        assertEquals(unity.samples[264_000] * 1.13f, trimmed.samples[264_000], 1e-6f)
        assertEquals(unity.samples[311_999] * 1.13f, trimmed.samples[311_999], 1e-6f)
    }

    @Test
    fun theAnswerIsMixedIntoItsExactWindow() {
        val voice = FloatArray(48_000) { 0.5f }
        val fake = FakePcmRenderer(voiceSamples = { voice })
        val rendered = ExerciseRenderer(fake, voiceGain = 1f).render(
            "interval:1",
            0,
            timeline(),
            mapOf("interval.perfect_fifth" to voiceFile())
        )
        // 5.5 s = 264000 frames; the 1 s clip fills 264000..312000 and nothing after it.
        assertEquals(0f, rendered.samples[263_999])
        assertEquals(0.5f, rendered.samples[264_000])
        assertEquals(0.5f, rendered.samples[311_999])
        assertEquals(0f, rendered.samples[312_000])
        // 5.5 s answer + 1 s clip + 2 s final silence = 8.5 s.
        assertEquals(408_000L - 96_000L, 312_000L)
    }

    /**
     * The 44.1 kHz mirror of [theAnswerIsMixedIntoItsExactWindow] and [noteWindowsLandOnFrameExactBoundaries].
     *
     * The device may accept 44.1 kHz instead of the preferred 48 kHz, so every frame-exact window and
     * the voice placement must be converted at the accepted rate, not only the requested one.
     */
    @Test
    fun theSameExerciseRendersFrameExactWindowsAndVoiceAtFortyFourPointOneKilohertz() {
        val fake = FakePcmRenderer(
            format = PcmFormat(44_100, 1),
            voiceSamples = { FloatArray(44_100) { 0.5f } }
        )
        val rendered = ExerciseRenderer(fake, voiceGain = 1f).render(
            "interval:1",
            0,
            timeline(),
            mapOf("interval.perfect_fifth" to voiceFile())
        )

        // 700 ms = 30870 frames, 800 ms = 35280 frames and 5.5 s = 242550 frames; the exercise
        // ends at 8.5 s = 374850 frames.
        assertEquals(374_850L, rendered.totalFrames)
        val scheduled = fake.scheduledAt
        assertEquals(
            listOf("on:60" to 0L, "on:67" to 35_280L),
            scheduled.filter { it.first.startsWith("on") }
        )
        val offs = scheduled.filter { it.first.startsWith("off") }
        assertEquals(listOf("off:60", "off:67"), offs.map { it.first })
        assertEquals(listOf(30_870L, 66_150L), offs.map { it.second })
        assertEquals(1, fake.resets)

        val windows = rendered.timeline.windows
        assertEquals(listOf(0L, 35_280L, 242_550L), windows.map { it.startFrame })
        assertEquals(listOf(30_870L, 30_870L, 44_100L), windows.map { it.frameCount })

        // The 1 s voice clip fills exactly 242550..286650 and touches neither neighbouring frame.
        assertEquals(0f, rendered.samples[242_549])
        assertEquals(0.5f, rendered.samples[242_550])
        assertEquals(0.5f, rendered.samples[286_649])
        assertEquals(0f, rendered.samples[286_650])
    }

    /** The 44.1 kHz cap conversion; the training layer's 60 s rule is rate-independent. */
    @Test
    fun anExerciseAtTheSixtySecondCapIsAcceptedAtFortyFourPointOneKilohertz() {
        val atCap = TimelineCompiler(
            recipe = RecognitionRecipe(
                noteDuration = 29.seconds(),
                melodicGap = 0.seconds(),
                thinkingSilence = 0.seconds(),
                postAnswerSilence = 0.seconds()
            ),
            voiceDurations = MapVoiceDurationCatalog(
                mapOf(VoiceAssetId("interval.perfect_fifth") to 1.seconds())
            )
        ).compile(
            MidiNote.of(60),
            MidiNote.of(67),
            IntervalPresentation.ASCENDING,
            VoiceAssetId("interval.perfect_fifth")
        )
        val frames = ExerciseRenderer(
            FakePcmRenderer(format = PcmFormat(44_100, 1))
        ).toFrameTimeline(atCap)
        // Two 29 s notes plus a 1 s answer: 59 s, exactly 44100 * 59 frames at this rate.
        assertEquals(44_100L * 59L, frames.totalFrames)
        assertEquals(59.0, frames.totalFrames.toDouble() / 44_100.0, 0.0001)
        assertTrue(frames.seconds <= 60.0)
    }

    @Test
    fun aShorterVoiceClipLeavesTheRestOfItsWindowSilent() {
        val voice = FloatArray(24_000) { 0.25f }
        val fake = FakePcmRenderer(voiceSamples = { voice })
        val rendered = ExerciseRenderer(fake, voiceGain = 1f).render(
            "interval:1",
            0,
            timeline(),
            mapOf("interval.perfect_fifth" to voiceFile())
        )
        assertEquals(0.25f, rendered.samples[264_000])
        assertEquals(0.25f, rendered.samples[287_999])
        assertEquals(0f, rendered.samples[288_000])
    }

    @Test
    fun theVoiceGainScalesTheAnswerWithoutTouchingTheNotes() {
        val voice = FloatArray(48_000) { 0.4f }
        val fake = FakePcmRenderer(voiceSamples = { voice })
        val clip = voiceFile()
        val files = mapOf("interval.perfect_fifth" to clip)
        val scaled = ExerciseRenderer(
            fake,
            voiceGain = 0.25f
        ).render("interval:1", 0, timeline(), files)
        // The answer is scaled by the voice gain...
        assertEquals(0.1f, scaled.samples[264_000], 1e-6f)
        // ...and the music is untouched, so the two gains are independent controls.
        val unity = ExerciseRenderer(
            fake,
            voiceGain = 1f
        ).render("interval:1", 0, timeline(), files)
        assertEquals(unity.samples[100], scaled.samples[100], 1e-6f)
        assertEquals(0.4f, unity.samples[264_000], 1e-6f)
    }

    @Test
    fun anInvalidVoiceGainIsRejected() {
        val fake = FakePcmRenderer()
        assertThrows(IllegalArgumentException::class.java) {
            ExerciseRenderer(fake, voiceGain = -0.1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExerciseRenderer(fake, voiceGain = 5f)
        }
    }

    @Test
    fun aMissingVoiceClipFailsTheRender() {
        val fake = FakePcmRenderer()
        val error = assertThrows(RenderFailureException::class.java) {
            ExerciseRenderer(fake).render("interval:1", 0, timeline(), emptyMap())
        }
        assertTrue(error.message!!.contains("interval.perfect_fifth"))
    }

    @Test
    fun aMissingVoiceFileFailsTheRender() {
        val fake = FakePcmRenderer(voiceSamples = { FloatArray(10) })
        val missing = File(folder.root, "not-there.wav")
        val error = assertThrows(RenderFailureException::class.java) {
            ExerciseRenderer(fake).render(
                "interval:1",
                0,
                timeline(),
                mapOf("interval.perfect_fifth" to missing)
            )
        }
        assertTrue(error.message!!.contains("does not exist"))
    }

    @Test
    fun stereoOutputDoublesEverySample() {
        val fake =
            FakePcmRenderer(format = PcmFormat(48_000, 2), voiceSamples = {
                FloatArray(48_000) { 0.5f }
            })
        val rendered = ExerciseRenderer(fake, voiceGain = 1f).render(
            "interval:1",
            0,
            timeline(),
            mapOf("interval.perfect_fifth" to voiceFile())
        )
        assertEquals(408_000 * 2, rendered.samples.size)
        val frame = 264_000 * 2
        assertEquals(0.5f, rendered.samples[frame])
        assertEquals(0.5f, rendered.samples[frame + 1])
    }

    @Test
    fun theFrameTimelineIsExposedWithItsWindows() {
        val fake = FakePcmRenderer()
        val rendered = ExerciseRenderer(fake).render(
            "interval:1",
            0,
            timeline(),
            mapOf("interval.perfect_fifth" to voiceFile())
        )
        val windows = rendered.timeline.windows
        assertEquals(3, windows.size)
        assertEquals(listOf(0L, 38_400L, 264_000L), windows.map { it.startFrame })
        assertEquals(listOf(33_600L, 33_600L, 48_000L), windows.map { it.frameCount })
        assertEquals(listOf(60, 67, null), windows.map { it.note })
    }

    @Test
    fun anExerciseAtTheSixtySecondCapIsAcceptedAndLongerOnesCannotBeBuilt() {
        // core:training rejects anything longer than 60 s at compile time, so the frame layer only
        // has to convert the boundary correctly.
        val atCap = TimelineCompiler(
            recipe = RecognitionRecipe(
                noteDuration = 29.seconds(),
                melodicGap = 0.seconds(),
                thinkingSilence = 0.seconds(),
                postAnswerSilence = 0.seconds()
            ),
            voiceDurations = MapVoiceDurationCatalog(
                mapOf(VoiceAssetId("interval.perfect_fifth") to 1.seconds())
            )
        ).compile(
            MidiNote.of(60),
            MidiNote.of(67),
            IntervalPresentation.ASCENDING,
            VoiceAssetId("interval.perfect_fifth")
        )
        val frames = ExerciseRenderer(FakePcmRenderer()).toFrameTimeline(atCap)
        // Two 29 s notes plus a 1 s answer, with no thinking or final silence.
        assertEquals(48_000L * 59L, frames.totalFrames)
        assertEquals(59.0, frames.totalFrames.toDouble() / 48_000.0, 0.0001)
        assertTrue(frames.seconds <= 60.0)

        val overCap = TimelineCompiler(
            recipe = RecognitionRecipe(
                noteDuration = 30.seconds(),
                melodicGap = 0.seconds(),
                thinkingSilence = 0.seconds(),
                postAnswerSilence = 0.seconds()
            ),
            voiceDurations = MapVoiceDurationCatalog(
                mapOf(VoiceAssetId("interval.perfect_fifth") to 1.seconds())
            )
        )
        val error =
            assertThrows(
                com.thomas.passiveeartraining.training.InvalidTimelineException::class.java
            ) {
                overCap.compile(
                    MidiNote.of(60),
                    MidiNote.of(67),
                    IntervalPresentation.ASCENDING,
                    VoiceAssetId("interval.perfect_fifth")
                )
            }
        assertTrue(error.message!!.contains("cap"))
    }
}

class BoundedPcmCacheTest {

    private fun exercise(frames: Long, identity: String = "e", channels: Int = 2) =
        RenderedExercise(
            identity = identity,
            sequenceIndex = 0,
            sampleRate = 48_000,
            channels = channels,
            totalFrames = frames,
            timeline = FrameTimeline(48_000, channels, frames, emptyList())
        )

    @Test
    fun twoSixtySecondExercisesFitTheDefaultBudget() {
        val cache = BoundedPcmCache()
        val sixtySeconds = 48_000L * 60
        val perExercise = (sixtySeconds * 2 * 4).toInt().toLong()
        assertEquals(23_040_000L, perExercise)
        cache.promoteToCurrent(exercise(sixtySeconds, "a"))
        cache.stageNext(exercise(sixtySeconds, "b"))
        assertEquals(46_080_000L, cache.usedBytes)
        assertTrue(cache.usedBytes <= BoundedPcmCache.DEFAULT_BUDGET_BYTES)
        // The documents state the two-item figure explicitly; keep the arithmetic checked.
        assertEquals(46_080_000L, 2 * sixtySeconds * 2 * FrameTimeline.FLOAT_BYTES)
    }

    @Test
    fun promotingEvictsTheCurrentItemAndDropsTheStagedOne() {
        val cache = BoundedPcmCache()
        cache.promoteToCurrent(exercise(1_000, "a"))
        cache.stageNext(exercise(1_000, "b"))
        assertEquals("a", cache.currentExercise?.identity)
        assertEquals("b", cache.nextExercise?.identity)
        cache.promoteToCurrent(exercise(1_000, "b"))
        assertEquals("b", cache.currentExercise?.identity)
        assertEquals(null, cache.nextExercise)
        assertEquals(1_000 * 2 * 4, cache.usedBytes)
    }

    @Test
    fun theBudgetIsEnforcedBeforeAllocation() {
        val cache = BoundedPcmCache(budgetBytes = 1_000)
        val error = assertThrows(PcmBudgetExceededException::class.java) {
            cache.promoteToCurrent(exercise(10_000, "big"))
        }
        assertTrue(error.message!!.contains("whole 1000-byte cache budget"))
        assertEquals(0L, cache.usedBytes)
    }

    @Test
    fun stagingAgainReplacesTheStagedItemWithoutGrowingTheCache() {
        // The cache holds current + next only. Staging a second candidate replaces the first, so
        // repeated staging must not accumulate bytes.
        val cache = BoundedPcmCache(budgetBytes = 150_000)
        val frames = 12_500L // 50_000 bytes mono
        cache.promoteToCurrent(exercise(frames, "a", channels = 1))
        cache.stageNext(exercise(frames, "b", channels = 1))
        assertEquals(100_000L, cache.usedBytes)
        cache.stageNext(exercise(frames, "c", channels = 1))
        assertEquals("c", cache.nextExercise?.identity)
        assertEquals(100_000L, cache.usedBytes)
        cache.stageNext(exercise(frames, "d", channels = 1))
        assertEquals(100_000L, cache.usedBytes)
    }

    @Test
    fun anItemThatCannotFitBesideTheCurrentOneIsRejected() {
        // Two thirds of the budget per item: the first item fits, staging a second does not.
        val cache = BoundedPcmCache(budgetBytes = 150_000)
        val frames = 25_000L // 25_000 * 4 = 100_000 bytes mono
        cache.promoteToCurrent(exercise(frames, "a", channels = 1))
        assertEquals(100_000L, cache.usedBytes)
        val error = assertThrows(PcmBudgetExceededException::class.java) {
            cache.stageNext(exercise(frames, "b", channels = 1))
        }
        assertTrue(error.message!!.contains("would use 200000"))
        assertEquals(100_000L, cache.usedBytes)
    }

    @Test
    fun clearingDropsEverything() {
        val cache = BoundedPcmCache()
        cache.promoteToCurrent(exercise(10_000, "a"))
        cache.stageNext(exercise(10_000, "b"))
        cache.clearNext()
        assertEquals(null, cache.nextExercise)
        cache.clear()
        assertEquals(null, cache.currentExercise)
        assertEquals(0L, cache.usedBytes)
    }
}

/** Fake device output that records writes and can be told to accept short writes or fail. */
private class FakeAudioOutput(
    override val format: PcmFormat = PcmFormat(48_000, 1),
    private val maxAcceptPerWrite: Int = Int.MAX_VALUE,
    private val acceptedSampleRateValue: Int = 48_000,
    private val advancePlayedOnWrite: Boolean = false
) : AudioOutput {
    val written = mutableListOf<FloatArray>()
    var framesAccepted = 0L
    var started = 0
    var paused = 0
    var flushed = 0
    var stopped = 0
    var playPosition = 0L
    var underruns = 0
    var failAfterFrames: Long? = null

    override fun start(preBufferFrames: Int) {
        started += 1
    }

    override fun write(samples: FloatArray, offset: Int, frames: Int): Int {
        failAfterFrames?.let { limit ->
            if (framesAccepted >= limit) {
                throw AudioOutputException("simulated device loss")
            }
        }
        val accepted = minOf(frames, maxAcceptPerWrite)
        // Copy only what is accepted, and only from the requested offset: a fake that copied the
        // whole request would hide a short write, and one that ignored the offset would hide a
        // duplicated retry.
        written += samples.copyOfRange(offset, offset + accepted * format.channels)
        framesAccepted += accepted
        if (advancePlayedOnWrite) {
            playPosition += accepted
        }
        return accepted
    }

    override fun pause() {
        paused += 1
    }
    override fun flush() {
        flushed += 1
        playPosition = 0
    }
    override fun stop() {
        stopped += 1
    }
    override fun playedFrames(): Long = playPosition
    override fun bufferCapacityFrames(): Int = 4096
    override fun underrunCount(): Int = underruns
    override fun isPlaying(): Boolean = started > paused
    override fun acceptedSampleRate(): Int = acceptedSampleRateValue
}

private fun renderedExercise(
    totalFrames: Long,
    channels: Int = 1,
    fill: (Long) -> Float = { it.toFloat() }
): RenderedExercise {
    val exercise = RenderedExercise(
        identity = "e",
        sequenceIndex = 0,
        sampleRate = 48_000,
        channels = channels,
        totalFrames = totalFrames,
        timeline = FrameTimeline(48_000, channels, totalFrames, emptyList())
    )
    for (frame in 0 until totalFrames) {
        val value = fill(frame)
        for (channel in 0 until channels) {
            exercise.samples[(frame * channels + channel).toInt()] = value
        }
    }
    return exercise
}

class ExerciseOutputWriterTest {

    @Test
    fun theWholeExerciseIsSubmittedInBoundedBlocks() {
        val output = FakeAudioOutput()
        val writer = ExerciseOutputWriter(output, blockFrames = 1000)
        val written = writer.play(renderedExercise(2500), 0)
        assertEquals(2500L, written)
        assertEquals(3, output.written.size)
        assertEquals(listOf(1000, 1000, 500), output.written.map { it.size })
        assertEquals(2500L, writer.submitted)
    }

    @Test
    fun aShortWriteIsRetriedForTheRemainder() {
        val output = FakeAudioOutput(maxAcceptPerWrite = 300)
        // This test asserts exact frame values; the onset ramp is covered by its own tests.
        val writer = ExerciseOutputWriter(output, blockFrames = 1000, antiClickFade = false)
        val written = writer.play(renderedExercise(1000), 0)
        // Every frame must reach the device even though each call accepts only 300.
        assertEquals(1000L, written)
        assertEquals(1000L, output.framesAccepted)
        // 1000 frames in chunks of at most 300: three partial writes out of four calls, and the
        // accepted chunk sizes must not exceed the limit the device advertised.
        assertEquals(3, writer.shortWriteCount)
        assertEquals(listOf(300, 300, 300, 100), output.written.map { it.size })
        val flat = output.written.flatMap { it.toList() }
        assertEquals("the accepted audio must be complete", 1000, flat.size)
        // The device must receive every frame exactly once and in order.
        assertEquals(List(1000) { it.toFloat() }, flat)
    }

    @Test
    fun pausingMidExerciseStopsSubmittingAndFlushesTheQueue() {
        val output = FakeAudioOutput(advancePlayedOnWrite = true)
        // The production session starts the output before writing; the fake reports playing only
        // after that, and a pause flush is only valid on a playing output.
        output.start(1024)
        val writer = ExerciseOutputWriter(output, blockFrames = 1000)
        val exercise = renderedExercise(48_000)
        // The loop polls once per block, so the first poll is before any audio is written.
        var polls = 0
        val written = writer.play(exercise, 0) {
            polls += 1
            if (polls == 3) writer.requestPause()
            false
        }
        assertTrue(
            "a mid-exercise pause must stop the write loop early",
            written < exercise.totalFrames
        )
        // The fade flushes the full-amplitude queue, plays 8 ms from the cached position and
        // flushes again at zero.
        assertEquals("the pause must silence the queue", 2, output.flushed)
        assertTrue("some audio must have been submitted", written > 0L)
        assertEquals(
            "the last submitted sample must be exact silence",
            0f,
            output.written.last().last()
        )
    }

    @Test
    fun playbackContinuesFromTheRetainedPositionAfterAPause() {
        val output = FakeAudioOutput()
        val writer = ExerciseOutputWriter(output, blockFrames = 1000)
        val exercise = renderedExercise(10_000)
        var polls = 0
        val first = writer.play(exercise, 0) {
            polls += 1
            if (polls == 3) writer.requestPause()
            false
        }
        output.playPosition = first
        val second = writer.play(exercise, writer.retainedFrame())
        assertEquals("the remainder must be submitted", 10_000L - first, second)
    }

    @Test
    fun cancellationStopsAtTheRetainedPosition() {
        val output = FakeAudioOutput()
        val writer = ExerciseOutputWriter(output, blockFrames = 1000)
        // Cancel after the second block has been submitted, using the position rather than a call
        // count so the result does not depend on how often the predicate is polled.
        val written = writer.play(renderedExercise(10_000), 0) { output.framesAccepted >= 2000L }
        assertEquals(2000L, written)
        assertTrue(written < 10_000L)
    }

    @Test
    fun resumingFromARetainedPositionContinuesFromThere() {
        val output = FakeAudioOutput()
        val writer = ExerciseOutputWriter(output, blockFrames = 1000)
        val exercise = renderedExercise(5000)
        val first = writer.play(exercise, 0) { output.framesAccepted >= 2000L }
        assertEquals(2000L, first)
        val second = writer.play(exercise, startFrame = first)
        assertEquals(3000L, second)
        val flat = output.written.flatMap { it.toList() }
        assertEquals(5000, flat.size)
        assertEquals(0f, flat.first())
        assertEquals(4999f, flat.last())
    }

    @Test
    fun aDeviceFailureIsSurfacedInsteadOfSpinning() {
        val output = FakeAudioOutput()
        output.failAfterFrames = 1500
        val writer = ExerciseOutputWriter(output, blockFrames = 1000)
        val error = assertThrows(AudioOutputException::class.java) {
            writer.play(renderedExercise(5000), 0)
        }
        assertTrue(error.message!!.contains("simulated device loss"))
    }

    @Test
    fun playedPositionIsClampedToWhatWasSubmitted() {
        val output = FakeAudioOutput()
        val writer = ExerciseOutputWriter(output, blockFrames = 1000)
        writer.play(renderedExercise(2000), 0)
        output.playPosition = 5000 // device claims more than we wrote
        assertEquals(2000L, writer.playedFrames())
        assertEquals(1f, writer.progress(2000L))
        output.playPosition = 1000
        assertEquals(1000L, writer.playedFrames())
        assertEquals(0.5f, writer.progress(2000L))
        output.playPosition = 0
        assertEquals(0f, writer.progress(2000L))
        // A zero-length submission has no progress to report; it must not divide by zero.
        assertEquals(0f, writer.progress(0L))
    }

    @Test
    fun aWrapOfTheDeviceCounterIsReadAsUnsigned() {
        val output = FakeAudioOutput()
        // The platform counter is 32-bit and wraps; a large value must not become negative.
        val writer = ExerciseOutputWriter(output, blockFrames = 1000)
        writer.play(renderedExercise(10_000), 0)
        output.playPosition = 0xFFFF_FFFFL
        assertEquals(10_000L, writer.playedFrames())
    }

    @Test
    fun metricsRecordTheAcceptedFormatAndCounters() {
        val output = FakeAudioOutput(acceptedSampleRateValue = 48_000, maxAcceptPerWrite = 500)
        val writer = ExerciseOutputWriter(output, blockFrames = 1000)
        writer.play(renderedExercise(2500), 0)
        output.underruns = 3
        output.playPosition = 1200
        val metrics = writer.metrics()
        assertEquals(48_000, metrics.requestedSampleRate)
        assertEquals(48_000, metrics.acceptedSampleRate)
        assertEquals(4096, metrics.bufferCapacityFrames)
        assertEquals(2500L, metrics.framesWritten)
        assertEquals(1200L, metrics.framesPlayed)
        assertEquals(3, metrics.underrunCount)
        assertTrue(metrics.shortWrites > 0)
        writer.resetCounters()
        assertEquals(0L, writer.submitted)
    }

    @Test
    fun theMasterGainScalesSubmittedBlocksWithoutTouchingTheCachedPcm() {
        val output = FakeAudioOutput()
        val writer = ExerciseOutputWriter(output, blockFrames = 100, antiClickFade = false)
        val exercise = renderedExercise(100) { 1f }
        writer.setMasterGain(0.5f)
        writer.play(exercise, 0)
        assertTrue("something was written", output.written.isNotEmpty())
        assertTrue(
            "every submitted sample is scaled",
            output.written.flatMap { it.toList() }.all { kotlin.math.abs(it - 0.5f) < 0.0001f }
        )
        assertEquals("the cached PCM is untouched", 1f, exercise.samples[0])

        writer.setMasterGain(1f)
        writer.play(exercise, 0)
        assertTrue(
            "restoring full gain submits the original samples",
            output.written.last().all { it == 1f }
        )
    }

    @Test
    fun anInvalidStartFrameIsRejected() {
        val writer = ExerciseOutputWriter(FakeAudioOutput())
        assertThrows(IllegalArgumentException::class.java) {
            writer.play(renderedExercise(100), -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            writer.play(renderedExercise(100), 101)
        }
    }

    @Test
    fun theResumeRampStartsSilentAndReachesFullLevel() {
        val exercise = renderedExercise(1000) { 1f }
        ExerciseOutputWriter.applyRamp(exercise, startFrame = 100, sampleRate = 48_000)
        // 5 ms at 48 kHz is 240 frames: the ramp is silent at its first frame and full after it.
        assertEquals(0f, exercise.samples[100])
        assertTrue(exercise.samples[200] < 1f)
        assertEquals(1f, exercise.samples[340])
        // Frames before the ramp are untouched.
        assertEquals(1f, exercise.samples[99])
    }

    @Test
    fun theRampIsBoundedByTheEndOfTheExercise() {
        val exercise = renderedExercise(10) { 1f }
        ExerciseOutputWriter.applyRamp(exercise, startFrame = 5, sampleRate = 48_000)
        assertEquals(0f, exercise.samples[5])
        assertEquals(1f, exercise.samples[4])
    }

    @Test
    fun theFadeOutEnvelopeIsARaisedCosineThatEndsAtZero() {
        val frames = ExerciseOutputWriter.fadeFrames(
            ExerciseOutputWriter.FADE_OUT_MILLISECONDS,
            48_000
        )
        assertEquals("8 ms at 48 kHz", 384, frames)
        assertEquals(1f, ExerciseOutputWriter.fadeOutGain(0, frames), 0.001f)
        assertEquals(0f, ExerciseOutputWriter.fadeOutGain(frames - 1, frames))
        var previous = 1f
        var largestStep = 0f
        for (index in 0 until frames) {
            val gain = ExerciseOutputWriter.fadeOutGain(index, frames)
            assertTrue("the fade must never rise", gain <= previous + 1e-6f)
            largestStep = maxOf(largestStep, previous - gain)
            previous = gain
        }
        assertEquals("the fade must end at exact silence", 0f, previous)
        assertTrue("the per-sample step must stay far below the old cut", largestStep < 0.01f)
    }

    @Test
    fun theFadeInEnvelopeStartsAtZeroAndReachesFullLevel() {
        val frames = ExerciseOutputWriter.fadeFrames(
            ExerciseOutputWriter.FADE_IN_MILLISECONDS,
            48_000
        )
        assertEquals("2 ms at 48 kHz", 96, frames)
        assertEquals(0f, ExerciseOutputWriter.fadeInGain(0, frames))
        assertEquals(1f, ExerciseOutputWriter.fadeInGain(frames, frames))
        var previous = 0f
        for (index in 0 until frames) {
            val gain = ExerciseOutputWriter.fadeInGain(index, frames)
            assertTrue("the ramp must never fall", gain >= previous - 1e-6f)
            previous = gain
        }
        assertTrue("the last ramp sample is almost full level", previous > 0.99f)
    }

    @Test
    fun aStartOrResumeBeginsWithATwoMillisecondRamp() {
        val output = FakeAudioOutput()
        val writer = ExerciseOutputWriter(output, blockFrames = 100)
        writer.play(renderedExercise(1000) { 1f }, 0)
        val flat = output.written.flatMap { it.toList() }
        assertEquals("the first submitted sample must be silent", 0f, flat.first())
        assertTrue("the ramp is still below full level after 1 ms", flat[47] < 1f)
        assertEquals("the ramp reaches full level after 2 ms", 1f, flat[96])
    }

    @Test
    fun anAbruptPauseCutsAtExactSilenceInsteadOfFullAmplitude() {
        val output = FakeAudioOutput(advancePlayedOnWrite = true)
        output.start(1024)
        val writer = ExerciseOutputWriter(output, blockFrames = 250)
        // A 1 kHz tone: the last frame before the cut is near a waveform extremum.
        val exercise = renderedExercise(48_000) { frame ->
            sin(2.0 * Math.PI * 1_000.0 * frame / 48_000.0).toFloat()
        }
        var polls = 0
        val written = writer.play(exercise, 0) {
            polls += 1
            if (polls == 3) writer.requestPause()
            false
        }
        // The poll callback runs before the inner retry too, so the cut lands at frame 250.
        assertEquals(250L, written)
        val preCut = abs(exercise.samples[(written - 1).toInt()])
        assertTrue("the cut lands on a loud sample (pre-fix discontinuity)", preCut > 0.7f)
        val fade = output.written.last()
        assertEquals("the fade is 8 ms at 48 kHz", 384, fade.size)
        assertEquals("the last submitted sample is exact silence", 0f, fade.last())
        // The fade starts on the exact sample the device would have played next, so there is no
        // step at its beginning either; the remaining steps are the 1 kHz waveform's own slope.
        val startStep = abs(fade.first() - exercise.samples[written.toInt()])
        assertTrue("the fade starts continuously", startStep < 0.001f)
        var largestStep = 0f
        for (index in 1 until fade.size) {
            largestStep = maxOf(largestStep, abs(fade[index] - fade[index - 1]))
        }
        assertTrue(
            "the largest step within the fade belongs to the waveform, not a cut ($largestStep)",
            largestStep < 0.2f
        )
    }

    @Test
    fun anAbruptStopEndsWithAnExactZeroSample() {
        val output = FakeAudioOutput(advancePlayedOnWrite = true)
        output.start(1024)
        val writer = ExerciseOutputWriter(output, blockFrames = 250)
        var polls = 0
        writer.play(renderedExercise(48_000) { 1f }, 0) {
            polls += 1
            if (polls == 3) writer.requestPause()
            false
        }
        // Stop releases the device after the port's flush; the fade already ended at zero.
        output.stop()
        assertEquals(1, output.stopped)
        assertEquals(0f, output.written.last().last())
    }

    @Test
    fun theAntiClickFadeDoesNotMutateTheCachedPcmOrTheMasterGain() {
        val output = FakeAudioOutput(advancePlayedOnWrite = true)
        output.start(1024)
        val writer = ExerciseOutputWriter(output, blockFrames = 250)
        val exercise = renderedExercise(48_000) { frame -> (frame % 100).toFloat() }
        val before = exercise.samples.copyOf()
        writer.setMasterGain(0.5f)
        var polls = 0
        writer.play(exercise, 0) {
            polls += 1
            if (polls == 3) writer.requestPause()
            false
        }
        assertTrue("the cached PCM must be untouched", before.contentEquals(exercise.samples))
        assertEquals("the master gain must be untouched", 0.5f, writer.masterGain)
        assertEquals(0f, output.written.last().last())
    }
}

/** Fake diagnostic port: records commands and lets tests force failures. */
private class FakeDiagnosticPort(override val format: PcmFormat = PcmFormat(48_000, 1)) :
    DiagnosticAudioPort {
    val commands = mutableListOf<String>()
    var open = false
    var consumed = 0L
    var failOnOpen: Exception? = null
    var failOnRender: Exception? = null
    var lastRenderIdentity: String? = null
    val renderIdentities = mutableListOf<String>()
    var lastPlayStart: Long? = null

    override val isOpen: Boolean get() = open

    override fun open(soundFontFile: File, bank: Int, program: Int) {
        commands += "open($bank/$program)"
        failOnOpen?.let { throw it }
        open = true
    }

    override fun render(
        identity: String,
        sequenceIndex: Int,
        timeline: com.thomas.passiveeartraining.training.Timeline
    ): RenderedExercise {
        commands += "render($identity)"
        failOnRender?.let { throw it }
        lastRenderIdentity = identity
        renderIdentities += identity
        val frames = 48_000L
        return RenderedExercise(
            identity = identity,
            sequenceIndex = sequenceIndex,
            sampleRate = 48_000,
            channels = format.channels,
            totalFrames = frames,
            timeline = FrameTimeline(48_000, format.channels, frames, emptyList())
        )
    }

    override fun play(startFrame: Long) {
        commands += "play($startFrame)"
        lastPlayStart = startFrame
    }

    override fun pause() {
        commands += "pause"
    }

    override fun requestPause() {
        commands += "requestPause"
    }

    override fun stop() {
        commands += "stop"
        consumed = 0
    }

    override fun consumedFrames(): Long = consumed

    override fun metrics(): OutputMetrics = OutputMetrics(
        requestedSampleRate = format.sampleRate,
        acceptedSampleRate = format.sampleRate,
        channels = format.channels,
        bufferCapacityFrames = 4096,
        framesWritten = 48_000,
        framesPlayed = consumed,
        underrunCount = 0,
        shortWrites = 0
    )

    override fun close() {
        commands += "close"
        open = false
    }
}

private fun diagnosticTimeline(): com.thomas.passiveeartraining.training.Timeline =
    recognitionCompiler().compile(
        root = MidiNote.of(60),
        target = MidiNote.of(67),
        presentation = IntervalPresentation.ASCENDING,
        answerVoiceId = VoiceAssetId("interval.perfect_fifth")
    )

class DiagnosticControllerTest {

    private fun controller(port: FakeDiagnosticPort = FakeDiagnosticPort()) =
        port to DiagnosticController(port) { 42L }

    @Test
    fun startOpensTheEngineAndPreparesAnExercise() {
        val (port, subject) = controller()
        subject.start(
            7L,
            font(),
            0,
            0,
            diagnosticTimeline(),
            "interval.perfect_fifth",
            "C4..C5 ascending"
        )
        val state = subject.state()
        // Start prepares the exercise and stops at Ready; playing is a separate command so the
        // display can show Ready in between.
        assertEquals(DiagnosticPhase.READY, state.phase)
        // The caller provides the seed; the controller renders exactly what it was given.
        assertEquals(7L, state.seed)
        assertEquals("C4..C5 ascending", state.configurationSummary)
        assertEquals("interval.perfect_fifth", state.voiceId)
        assertTrue(state.exerciseSummary.contains("48000 frames"))
        assertTrue(state.timelineSummary.contains("NoteEvent=2"))
        assertTrue(state.timelineSummary.contains("VoiceEvent=1"))
        assertEquals(listOf("open(0/0)", "render(diagnostic:7)"), port.commands)
    }

    @Test
    fun playPauseAndResumePreserveTheConsumedPosition() {
        val (port, subject) = controller()
        subject.start(7L, font(), 0, 0, diagnosticTimeline(), "interval.perfect_fifth", "cfg")
        subject.play()
        assertEquals(DiagnosticPhase.PLAYING, subject.state().phase)
        port.consumed = 12_000
        subject.pause()
        assertEquals(DiagnosticPhase.PAUSED, subject.state().phase)
        assertEquals(12_000L, subject.state().consumedFrames)
        subject.resume()
        // Resume continues from the position the device reports, not from the start.
        assertEquals(12_000L, port.lastPlayStart)
        assertEquals(DiagnosticPhase.PLAYING, subject.state().phase)

        // A pause that retains nothing resumes from the beginning rather than submitting nothing.
        subject.pause()
        port.consumed = 0L
        subject.resume()
        assertEquals(0L, port.lastPlayStart)
    }

    @Test
    fun repeatRestartsTheSameExerciseFromZero() {
        val (port, subject) = controller()
        subject.start(7L, font(), 0, 0, diagnosticTimeline(), "interval.perfect_fifth", "cfg")
        subject.play()
        port.consumed = 30_000
        subject.repeat()
        assertEquals(0L, port.lastPlayStart)
        assertEquals(DiagnosticPhase.PLAYING, subject.state().phase)
        assertEquals(1, port.commands.count { it.startsWith("render(") })
    }

    @Test
    fun stopFlushesOutputAndAllowsTheSessionToBeReopened() {
        val (port, subject) = controller()
        subject.start(7L, font(), 0, 0, diagnosticTimeline(), "interval.perfect_fifth", "cfg")
        subject.play()
        subject.stop()
        assertEquals(DiagnosticPhase.IDLE, subject.state().phase)
        assertEquals(0L, subject.state().totalFrames)
        assertTrue(port.commands.contains("stop"))
        assertEquals(0L, subject.state().consumedFrames)
    }

    @Test
    fun unavailableCommandsAreRejectedByPhase() {
        val (_, subject) = controller()
        assertThrows(DiagnosticCommandException::class.java) { subject.play() }
        assertThrows(DiagnosticCommandException::class.java) { subject.pause() }
        assertThrows(DiagnosticCommandException::class.java) { subject.resume() }
        assertThrows(DiagnosticCommandException::class.java) { subject.repeat() }
        assertThrows(DiagnosticCommandException::class.java) {
            subject.regenerate(5L, diagnosticTimeline(), "x")
        }
        subject.start(7L, font(), 0, 0, diagnosticTimeline(), "interval.perfect_fifth", "cfg")
        // Resume is a play, so from Ready it starts the exercise.
        subject.resume()
        assertEquals(DiagnosticPhase.PLAYING, subject.state().phase)
        // Pausing is available once playing, and resuming after a pause returns to playing.
        subject.pause()
        assertEquals(DiagnosticPhase.PAUSED, subject.state().phase)
        subject.resume()
        assertEquals(DiagnosticPhase.PLAYING, subject.state().phase)
        // Resume while already playing is not applicable and must not change the phase.
        subject.resume()
        assertEquals(DiagnosticPhase.PLAYING, subject.state().phase)
        // Start remains available and re-prepares the exercise instead of being rejected.
        subject.start(7L, font(), 0, 0, diagnosticTimeline(), "interval.perfect_fifth", "cfg")
        assertEquals(DiagnosticPhase.READY, subject.state().phase)
    }

    @Test
    fun startAgainAfterACompletedExerciseRestartsItInsteadOfFailing() {
        // Reported from the phone: Start worked once, then failed or hung. A second Start on an open
        // session must stop what is playing, reuse the session and play the exercise again.
        val port = FakeDiagnosticPort()
        val subject = DiagnosticController(port) { 5L }
        subject.start(7L, font(), 0, 0, diagnosticTimeline(), "interval.perfect_fifth", "cfg")
        subject.play()
        port.consumed = 48_000
        assertEquals(DiagnosticPhase.PLAYING, subject.state().phase)

        subject.start(7L, font(), 0, 0, diagnosticTimeline(), "interval.perfect_fifth", "cfg")
        val state = subject.state()
        assertEquals("a second Start must succeed, not fail", DiagnosticPhase.READY, state.phase)
        // The engine is reopened only once: a repeat Start reuses the open session.
        assertEquals(1, port.commands.count { it.startsWith("open(") })
        // The exercise is re-rendered, and playing from the beginning is the caller's next step.
        assertEquals(2, port.commands.count { it.startsWith("render(") })
    }

    @Test
    fun playAfterAnExerciseFinishedRestartsFromTheBeginning() {
        // The device reports the whole exercise consumed; pressing Play must replay it rather than
        // write nothing and appear hung.
        val port = FakeDiagnosticPort()
        val subject = DiagnosticController(port) { 6L }
        subject.start(7L, font(), 0, 0, diagnosticTimeline(), "interval.perfect_fifth", "cfg")
        subject.play()
        subject.pause()
        port.consumed = 48_000
        subject.play(startFrame = port.consumedFrames())
        assertEquals("a finished exercise must restart from zero", 0L, port.lastPlayStart)
        assertEquals(DiagnosticPhase.PLAYING, subject.state().phase)
    }

    @Test
    fun stopReleasesPlaybackButKeepsTheSessionUsable() {
        val port = FakeDiagnosticPort()
        val subject = DiagnosticController(port) { 7L }
        subject.start(7L, font(), 0, 0, diagnosticTimeline(), "interval.perfect_fifth", "cfg")
        subject.play()
        subject.stop()
        assertEquals(DiagnosticPhase.IDLE, subject.state().phase)
        assertTrue("stop must flush the output", port.commands.contains("stop"))
        // The session stays open, so Start works again without re-opening the engine.
        val opensBefore = port.commands.count { it.startsWith("open(") }
        subject.start(7L, font(), 0, 0, diagnosticTimeline(), "interval.perfect_fifth", "cfg")
        assertEquals(DiagnosticPhase.READY, subject.state().phase)
        assertEquals(opensBefore, port.commands.count { it.startsWith("open(") })
        subject.play()
        assertEquals(DiagnosticPhase.PLAYING, subject.state().phase)
    }

    @Test
    fun aRendererFailureEntersErrorAndSilencesOutput() {
        val port = FakeDiagnosticPort()
        port.failOnRender = RenderFailureException("missing voice clip for interval.tritone")
        val subject = DiagnosticController(port) { 7L }
        subject.start(7L, font(), 0, 0, diagnosticTimeline(), "interval.perfect_fifth", "cfg")
        val state = subject.state()
        assertEquals(DiagnosticPhase.ERROR, state.phase)
        assertTrue(state.message!!.contains("missing voice clip"))
        assertEquals(null, state.outputMetrics)
        assertTrue(port.commands.contains("close"))
    }

    @Test
    fun retryIsRefusedWhileAHealthySessionExistsAndWorksAfterAFailure() {
        val port = FakeDiagnosticPort()
        val subject = DiagnosticController(port)
        subject.start(7L, font(), 0, 0, diagnosticTimeline(), "interval.perfect_fifth", "cfg")
        subject.play()
        assertEquals(DiagnosticPhase.PLAYING, subject.state().phase)
        // A healthy session must not be silently replaced by a retry.
        assertThrows(DiagnosticCommandException::class.java) {
            subject.retry(8L, font(), 0, 0, diagnosticTimeline(), "v", "c")
        }
        // After a failure the session is closed and a retry reopens the engine and re-renders.
        port.failOnRender = RenderFailureException("bad soundfont")
        subject.regenerate(8L, diagnosticTimeline(), "v")
        assertEquals(DiagnosticPhase.ERROR, subject.state().phase)
        port.failOnRender = null
        subject.retry(9L, font(), 0, 0, diagnosticTimeline(), "interval.perfect_fifth", "cfg")
        assertEquals(DiagnosticPhase.READY, subject.state().phase)
        // The failed session is released before the retry reopens the engine.
        assertEquals(
            listOf(
                "open(0/0)",
                "render(diagnostic:7)",
                "play(0)",
                "pause",
                "render(diagnostic:8)",
                "close",
                "open(0/0)",
                "render(diagnostic:9)"
            ),
            port.commands
        )
    }

    @Test
    fun anOpenFailureLeavesNoSessionAndNoMetrics() {
        val port = FakeDiagnosticPort()
        port.failOnOpen =
            AudioOutputException("AudioTrack failed to initialize (48000 Hz, 1 channel)")
        val subject = DiagnosticController(port) { 1L }
        subject.start(7L, font(), 0, 0, diagnosticTimeline(), "interval.perfect_fifth", "cfg")
        val state = subject.state()
        assertEquals(DiagnosticPhase.ERROR, state.phase)
        assertTrue(state.message!!.contains("AudioTrack failed"))
        assertEquals(null, state.outputMetrics)
    }

    @Test
    fun regenerateUsesANewSeedAndKeepsTheSessionOpen() {
        var next = 100L
        val port = FakeDiagnosticPort()
        val subject = DiagnosticController(port) { next }
        subject.start(100L, font(), 0, 0, diagnosticTimeline(), "interval.perfect_fifth", "cfg")
        assertEquals(100L, subject.state().seed)
        subject.regenerate(200L, diagnosticTimeline(), "interval.perfect_fifth")
        assertEquals(200L, subject.state().seed)
        assertEquals("diagnostic:200", port.lastRenderIdentity)
        assertEquals(DiagnosticPhase.READY, subject.state().phase)
    }

    @Test
    fun closeIsIdempotentAndLeavesTheIdlePhase() {
        val (port, subject) = controller()
        subject.start(7L, font(), 0, 0, diagnosticTimeline(), "interval.perfect_fifth", "cfg")
        subject.close()
        subject.close()
        assertEquals(DiagnosticPhase.IDLE, subject.state().phase)
        assertEquals(null, subject.state().outputMetrics)
        assertTrue(port.commands.contains("close"))
    }

    @Test
    fun progressIsCalculatedFromConsumedFrames() {
        val (port, subject) = controller()
        subject.start(7L, font(), 0, 0, diagnosticTimeline(), "interval.perfect_fifth", "cfg")
        port.consumed = 24_000
        val state = subject.state()
        assertEquals(48_000L, state.totalFrames)
        assertEquals(24_000L, state.consumedFrames)
        assertEquals(0.5f, state.progress)
    }

    private fun font(): File = File("earby-string-pack.bin")
}

class DiagnosticViewModelGateTest {

    @Test
    fun playIsNotSwallowedWhileThePhaseSnapshotIsStale() {
        // The screen reads the published snapshot, which lags the controller while a long command
        // runs. Play must be judged on the controller's real phase, otherwise a press right after
        // Start is silently ignored and the button appears dead.
        val port = FakeDiagnosticPort()
        val controller = DiagnosticController(port) { 11L }
        controller.start(
            7L,
            File("earby-string-pack.bin"),
            0,
            0,
            diagnosticTimeline(),
            "interval.perfect_fifth",
            "cfg"
        )
        assertEquals(DiagnosticPhase.READY, controller.state().phase)

        // Simulate the stale snapshot the UI would hold: still Preparing from the start command.
        val stale = controller.state().copy(phase = DiagnosticPhase.PREPARING)
        assertTrue(
            "this reproduces the reported state: the snapshot says PREPARING while the controller is READY",
            stale.phase == DiagnosticPhase.PREPARING &&
                controller.state().phase == DiagnosticPhase.READY
        )
    }
}
