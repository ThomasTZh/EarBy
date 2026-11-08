package com.thomas.passiveeartraining.training.session

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.training.MonotonicClock
import com.thomas.passiveeartraining.training.provider.CompiledExercise
import com.thomas.passiveeartraining.training.provider.ConcreteExercise
import com.thomas.passiveeartraining.training.provider.ProviderConfig
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.settings.LastSetup
import kotlin.time.Duration
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Controlled dispatchers exercise edits and history without real sleeps or polling. */
@kotlinx.coroutines.ExperimentalCoroutinesApi
class PlaybackIdentityTest {
    private class Port : AudioPort {
        data class Preparation(
            val exercise: ConcreteExercise,
            val compiled: CompiledExercise,
            val settings: RenderSettings
        )
        val items = linkedMapOf<PlaybackIdentity, Preparation>()
        var playing: PlaybackIdentity? = null
        var consumed = 0L
        override fun configure(musicGain: Float, voiceGain: Float) = Unit
        override suspend fun prepare(
            identity: PlaybackIdentity,
            exercise: ConcreteExercise,
            compiled: CompiledExercise,
            settings: RenderSettings
        ): AudioPort.Prepared {
            items[identity] = Preparation(exercise, compiled, settings)
            val answerFrame = compiled.timeline.voices.first().startOffset
                .inWholeMicroseconds * 48_000 / 1_000_000
            return AudioPort.Prepared(
                answerFrame,
                compiled.timeline.totalDuration.inWholeMicroseconds * 48_000 / 1_000_000
            )
        }
        override suspend fun play(
            identity: PlaybackIdentity,
            startFrame: Long,
            boundaries: List<Long>,
            isCancelled: () -> Boolean,
            onConsumed: suspend (Long) -> Unit,
            continueAtBoundary: (Long) -> Boolean
        ): AudioPort.PlayOutcome {
            playing = identity
            consumed = startFrame
            onConsumed(startFrame)
            // Yield cooperatively until navigation invalidates this playback.
            while (!isCancelled()) {
                kotlinx.coroutines.delay(1)
            }
            return AudioPort.PlayOutcome(0, consumed, false)
        }
        override suspend fun requestPlaybackPermission() = true
        override fun setMasterGain(gain: Float) = Unit
        override fun consumedFrames() = consumed
        override fun flush() = Unit
        override fun release() {
            playing = null
        }
    }

    @Test
    fun editsPreserveOriginalHistoryRecipeAndGainsDespiteCollidingGeneratorIndices() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val port = Port()
        val subject = DefaultSessionController(
            ConfiguredExerciseSource(),
            port,
            com.thomas.passiveeartraining.training.FakeMonotonicClock(),
            backgroundScope,
            playbackDispatcher = dispatcher,
            commandDispatcher = StandardTestDispatcher(testScheduler)
        )
        val initial = LastSetup.default().toStartRequest(7)
        try {
            subject.send(SessionCommand.Start(initial))
            runCurrent()
            val first = port.playing!!
            val original = port.items.getValue(first)
            val initialPolicy =
                (initial.setup.provider as ProviderConfig.Intervals).policy
            subject.send(
                SessionCommand.Reconfigure(
                    initial.copy(
                        setup = initial.setup.copy(
                            provider = ProviderConfig.Intervals(
                                initialPolicy.copy(fixedRoot = MidiNote.of(60))
                            ),
                            recipe = initial.setup.recipe.copy(mode = RecipeMode.EXPOSURE),
                            seed = 19
                        ),
                        musicGain = 0.4f,
                        voiceGain = 0.1f
                    )
                )
            )
            subject.send(SessionCommand.Next)
            testScheduler.advanceTimeBy(2)
            runCurrent()
            val edited = port.playing!!
            assertNotEquals(first, edited)
            assertEquals(
                "generator index restarts but playback identity does not",
                original.exercise.sequenceIndex,
                port.items.getValue(edited).exercise.sequenceIndex
            )
            assertEquals(
                Duration.ZERO,
                port.items.getValue(edited).compiled.timeline.voices.first().startOffset
            )
            subject.send(SessionCommand.Previous)
            testScheduler.advanceTimeBy(2)
            runCurrent()
            assertEquals(first, port.playing)
            assertEquals(original, port.items.getValue(first))
            subject.send(SessionCommand.Repeat)
            testScheduler.advanceTimeBy(2)
            runCurrent()
            assertEquals(original, port.items.getValue(first))
            subject.send(SessionCommand.Stop())
            subject.send(SessionCommand.Start(initial))
            testScheduler.advanceTimeBy(2)
            runCurrent()
            assertNotEquals(first.sessionGeneration, port.playing!!.sessionGeneration)
        } finally {
            subject.close()
        }
    }
}
