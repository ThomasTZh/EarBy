package com.thomas.passiveeartraining.training.session

import com.thomas.passiveeartraining.training.IntervalDefinition
import com.thomas.passiveeartraining.training.provider.ModeSetup
import com.thomas.passiveeartraining.training.provider.ProviderConfig
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SequencePolicy
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Repetition guard for the historical `returningToTheAudibleModeCancelsTheDeparture` flake.
 *
 * The flake was a test-order bug, not a controller race: the prepare count was asserted *after*
 * the continuation, racing the worker's legitimate preparation of the next item. This class runs
 * the whole scenario many times in one JVM with the deterministic order (count while the first
 * item still plays) so any regression in the cancellation semantics fails loudly and quickly.
 */
class SwitchCancelRepetitionTest {

    private fun request(seed: Long?): StartRequest = StartRequest(
        setup = ModeSetup(
            provider = ProviderConfig.Intervals(SequencePolicy(IntervalDefinition.default())),
            recipe = RecipeDefinition(mode = RecipeMode.RECOGNITION, replayCount = 1),
            seed = seed,
            stopPolicy = SessionStopPolicy.UntilStopped
        )
    )

    private fun controller(port: SegmentPort, clock: SwitchClock): DefaultSessionController =
        DefaultSessionController(
            source = ConfiguredExerciseSource(
                voiceDurations = { com.thomas.passiveeartraining.training.BundledVoiceDurations }
            ),
            audio = port,
            clock = clock,
            sampleRate = 48_000
        )

    @Test
    fun returningToTheAudibleModeCancelsEveryTime() = runBlocking {
        repeat(50) { iteration ->
            val port = SegmentPort()
            val subject = controller(port, SwitchClock())
            try {
                val active = request(seed = 42L)
                subject.send(SessionCommand.Start(active))
                awaitSwitch("first play") { port.playCalls.size == 1 }
                val layout = port.layout(port.playCalls[0].identity)
                port.consume(layout.mandatoryEndFrame / 2)

                subject.send(SessionCommand.SwitchMode(request(seed = null), savedRevision = 7L))
                awaitSwitch("pending published") { subject.state.value.pendingKind != null }
                subject.send(SessionCommand.SwitchMode(active, savedRevision = 1L))
                awaitSwitch("departure cancelled") { subject.state.value.pendingKind == null }
                assertNull("iteration $iteration", subject.state.value.activeRevision)
                assertEquals(
                    "iteration $iteration: cancelled switch prepared a destination",
                    1,
                    port.preparedExercises.size
                )

                port.consume(layout.totalFrames)
                awaitSwitch("normal continuation") { subject.state.value.itemIndex == 1 }
                assertNull("iteration $iteration", subject.state.value.pendingKind)
                assertNull("iteration $iteration", subject.state.value.activeRevision)
            } finally {
                subject.close()
            }
        }
    }
}
