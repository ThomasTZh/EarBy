package com.thomas.passiveeartraining.training.session

import com.thomas.passiveeartraining.training.BundledVoiceDurations
import com.thomas.passiveeartraining.training.provider.ModeSetup
import com.thomas.passiveeartraining.training.provider.ProviderRegistry
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M11.2 switching matrix: every one of the 56 ordered pairs of distinct production kinds.
 *
 * Each pair runs through the real [DefaultSessionController] with the controllable
 * [SegmentPort]/[SwitchClock] harness from [SwitchModeTest]. The audible kind must keep playing its
 * mandatory unit, the destination must publish while pending, the commit must land on that exact
 * unit boundary, and the next item must be a distinct-identity exercise of the destination kind.
 * No placeholder or unregistered kind can ever be selected because the destination is always its
 * own production provider's default configuration.
 */
class OrderedSwitchPairMatrixTest {

    private fun request(kind: TrainingKind): StartRequest {
        val provider = requireNotNull(ProviderRegistry.PRODUCTION.providerFor(kind)) {
            "No production provider is registered for $kind"
        }
        return StartRequest(
            setup = ModeSetup(
                provider = provider.defaults(),
                recipe = RecipeDefinition(mode = RecipeMode.RECOGNITION, replayCount = 0),
                seed = null
            )
        )
    }

    private fun controller(port: SegmentPort): DefaultSessionController = DefaultSessionController(
        source = ConfiguredExerciseSource(voiceDurations = { BundledVoiceDurations }),
        audio = port,
        clock = SwitchClock(),
        sampleRate = 48_000
    )

    /** Drives one ordered pair and asserts the complete commit contract. */
    private suspend fun assertOrderedPair(from: TrainingKind, to: TrainingKind) {
        val label = "$from -> $to"
        val port = SegmentPort()
        val subject = controller(port)
        try {
            subject.send(SessionCommand.Start(request(from)))
            awaitSwitch("$label first play") { port.playCalls.size == 1 }
            val first = port.playCalls[0]
            val layout = port.layout(first.identity)
            assertTrue(
                "$label the first item needs a mandatory unit",
                layout.mandatoryEndFrame > 0L
            )
            port.consume(layout.mandatoryEndFrame / 2)

            val result = subject.send(
                SessionCommand.SwitchMode(request(to), savedRevision = 7L)
            )
            assertTrue("$label the switch must be accepted", result is CommandResult.Applied)
            awaitSwitch("$label pending publishes") { subject.state.value.pendingKind != null }
            assertEquals("$label pending destination", to, subject.state.value.pendingKind)
            assertEquals("$label pending revision", 7L, subject.state.value.pendingRevision)
            assertEquals(
                "$label the audible kind is unchanged while pending",
                from,
                subject.state.value.activeKind
            )

            port.consume(layout.mandatoryEndFrame)
            awaitSwitch("$label destination plays") { port.playCalls.size == 2 }

            assertNull("$label the commit clears pending", subject.state.value.pendingKind)
            assertEquals("$label committed kind", to, subject.state.value.activeKind)
            assertEquals("$label committed revision", 7L, subject.state.value.activeRevision)
            assertEquals(
                "$label the switch commits at the interrupted mandatory boundary",
                layout.mandatoryEndFrame,
                port.stops.single().second
            )
            assertEquals(
                "$label exactly the old and the destination item prepare",
                2,
                port.preparedExercises.size
            )
            val committed = port.preparedExercises[1]
            assertEquals(
                "$label the committed item is the destination exercise",
                to,
                committed.kind
            )
            assertEquals(
                "$label the committed exercise comes from the destination provider",
                to.id,
                committed.provenance.providerId
            )
            assertNotEquals(
                "$label the committed item has a distinct playback identity",
                first.identity,
                port.playCalls[1].identity
            )
            assertTrue(
                "$label item ordinals stay monotonic",
                port.playCalls[1].identity.itemOrdinal > first.identity.itemOrdinal
            )
            assertTrue(
                "$label no placeholder or unimplemented kind was selected",
                TrainingKind.ALL.contains(committed.kind) &&
                    ProviderRegistry.PRODUCTION.providerFor(committed.kind) != null
            )
        } finally {
            subject.close()
        }
    }

    @Test
    fun everyOrderedPairOfDistinctProductionKindsSwitchesAndCommits() = runTest {
        val kinds = ProviderRegistry.PRODUCTION.implementedKinds
        assertEquals(
            "every kind the shell knows has a production provider; no placeholder exists",
            TrainingKind.ALL.toSet(),
            kinds.toSet()
        )
        assertEquals("exactly the nine production kinds", 9, kinds.size)

        var pairs = 0
        for (from in kinds) {
            for (to in kinds) {
                if (from == to) continue
                pairs += 1
                assertOrderedPair(from, to)
            }
        }
        assertEquals("ordered distinct pairs are 9 x 8", 72, pairs)
    }
}
