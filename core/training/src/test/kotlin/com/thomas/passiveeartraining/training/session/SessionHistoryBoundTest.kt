package com.thomas.passiveeartraining.training.session

import com.thomas.passiveeartraining.training.IntervalDefinition
import com.thomas.passiveeartraining.training.MapVoiceDurationCatalog
import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.provider.CompiledExercise
import com.thomas.passiveeartraining.training.provider.ModeSetup
import com.thomas.passiveeartraining.training.provider.ProviderConfig
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SequencePolicy
import kotlin.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M11.2 limit evidence: the controller's history stays bounded and retained items keep their
 * identity when replayed.
 *
 * The test drives more than twenty-five items through the real controller with the controllable
 * [SegmentPort]/[SwitchClock] harness, then reads the controller's private queue fields (the
 * reflection precedent from `app/src/reviewAndroidTest`) to prove:
 * - `history` holds at most [DefaultSessionController.MAX_HISTORY] items;
 * - the only retained concrete items are `history + forward + current + next`, i.e. the bound is
 *   20 + the two current entries, and nothing else in the controller or the exercise source holds
 *   compiled timelines;
 * - a history item replayed through Previous reuses its original playback identity and compiled
 *   exercise instead of drawing or compiling again.
 */
class SessionHistoryBoundTest {

    private val catalog = MapVoiceDurationCatalog(
        IntervalDefinition.default().selectedIntervalIds.associate { id ->
            VoiceAssetId("interval.$id") to Duration.parse("900ms")
        }
    )

    private fun request(seed: Long): StartRequest = StartRequest(
        setup = ModeSetup(
            provider = ProviderConfig.Intervals(SequencePolicy(IntervalDefinition.default())),
            recipe = RecipeDefinition(mode = RecipeMode.RECOGNITION, replayCount = 0),
            seed = seed
        )
    )

    private fun controller(port: SegmentPort): DefaultSessionController = DefaultSessionController(
        source = ConfiguredExerciseSource(voiceDurations = { catalog }),
        audio = port,
        clock = SwitchClock(),
        sampleRate = 48_000
    )

    /** Reads one private field; the session queues are deliberately not public diagnostics. */
    private fun controllerField(name: String): java.lang.reflect.Field =
        DefaultSessionController::class.java.getDeclaredField(name).apply { isAccessible = true }

    private fun itemField(item: Any, name: String): java.lang.reflect.Field =
        item.javaClass.getDeclaredField(name).apply { isAccessible = true }

    private fun itemIdentity(item: Any): PlaybackIdentity =
        itemField(item, "identity").get(item) as PlaybackIdentity

    private fun itemIndex(item: Any): Int = itemField(item, "index").get(item) as Int

    private fun itemCompiled(item: Any): CompiledExercise =
        itemField(item, "compiled").get(item) as CompiledExercise

    /** Every concrete item the controller can still reach, without duplicates. */
    private fun retainedItems(controller: DefaultSessionController): List<Any> {
        val history = controllerField("history").get(controller) as Collection<*>
        val forward = controllerField("forward").get(controller) as Collection<*>
        val current = controllerField("current").get(controller)
        val next = controllerField("next").get(controller)
        val retained = mutableListOf<Any?>()
        retained.addAll(history)
        retained.addAll(forward)
        if (current != null) retained.add(current)
        if (next != null) retained.add(next)
        return retained.filterNotNull()
    }

    @Test
    fun historyStaysBoundedAndRetainedItemsReplayWithTheirOriginalIdentity() = runTest {
        val port = SegmentPort()
        val subject = controller(port)
        try {
            subject.send(SessionCommand.Start(request(seed = 42L)))
            awaitSwitch("first play") { port.playCalls.size == 1 }

            // More than twenty-five forward steps: each Next reuses the drawn follower and draws a
            // new one, so the oldest entries leave the bounded history while playback keeps moving.
            val driven = 31
            repeat(driven) {
                val expected = port.playCalls.size + 1
                subject.send(SessionCommand.Next)
                awaitSwitch("driven item $expected plays") { port.playCalls.size == expected }
            }

            val history = controllerField("history").get(subject) as Collection<*>
            val forward = controllerField("forward").get(subject) as Collection<*>
            assertEquals(DefaultSessionController.MAX_HISTORY, history.size)
            assertTrue("plain Next navigation never fills the forward path", forward.isEmpty())

            val retained = retainedItems(subject)
            assertEquals(
                "history plus current and the drawn-ahead follower",
                DefaultSessionController.MAX_HISTORY + 2,
                retained.size
            )
            assertEquals(
                "every retained item is a distinct concrete item",
                retained.size,
                retained.toSet().size
            )
            // Interval draws may repeat, so equality would collapse different items; identity
            // proves that no compiled timeline object is shared between two retained items.
            val compiledIdentities = java.util.IdentityHashMap<Any, Boolean>()
            retained.forEach { compiledIdentities[itemCompiled(it)] = true }
            assertEquals(
                "no retained compiled timeline object is shared by two items",
                retained.size,
                compiledIdentities.size
            )

            // The bound is structural: the only collection fields are the two bounded queues, and
            // the exercise source caches no timelines of its own.
            val controllerCollections = DefaultSessionController::class.java.declaredFields
                .filter { Collection::class.java.isAssignableFrom(it.type) }
                .map { it.name }
                .sorted()
            assertEquals(
                "the queues are the only item-holding collections",
                listOf("forward", "history"),
                controllerCollections
            )
            val sourceCollections = ConfiguredExerciseSource::class.java.declaredFields
                .filter { Collection::class.java.isAssignableFrom(it.type) }
                .map { it.name }
            assertTrue(
                "the exercise source must not retain a global timeline cache",
                sourceCollections.isEmpty()
            )

            // The first driven items were evicted: history now starts later than item zero.
            val oldest = history.first()!!
            val oldestIndex = itemIndex(oldest)
            val oldestIdentity = itemIdentity(oldest)
            val oldestCompiled = itemCompiled(oldest)
            assertTrue("earlier items must already be evicted", oldestIndex > 0)
            assertTrue(
                "the retained item was played when it was fresh",
                port.playCalls.any { it.identity == oldestIdentity }
            )
            val highestPlayedOrdinal = port.playCalls.maxOf { it.identity.itemOrdinal }

            // Walk back exactly to the oldest retained history item and play it again.
            val playedBeforeBack = port.playCalls.size
            val stepsBack = history.size
            repeat(stepsBack) {
                val expected = port.playCalls.size + 1
                subject.send(SessionCommand.Previous)
                awaitSwitch("replayed history item $expected plays") {
                    port.playCalls.size == expected
                }
            }
            val current = controllerField("current").get(subject)!!
            assertEquals(
                "Previous reuses the retained concrete item",
                oldestIdentity,
                itemIdentity(current)
            )
            assertEquals(
                "the replayed compiled exercise is the retained one, not a recompile",
                oldestCompiled,
                itemCompiled(current)
            )
            assertEquals(
                "the replay carries the original playback identity",
                oldestIdentity,
                port.playCalls.last().identity
            )
            assertEquals(
                "navigation draws no new item",
                highestPlayedOrdinal,
                port.playCalls.maxOf { it.identity.itemOrdinal }
            )
            assertEquals(
                "the retained set stays inside the same bound after replaying",
                DefaultSessionController.MAX_HISTORY + 2,
                retainedItems(subject).size
            )

            // One more Previous at the oldest retained item repeats it: the items that no longer
            // fit in the bounded history are unreachable, so replay keeps the retained identity
            // instead of resurrecting an evicted one.
            val beforeRepeat = port.playCalls.size
            subject.send(SessionCommand.Previous)
            awaitSwitch("the oldest retained item repeats") {
                port.playCalls.size == beforeRepeat + 1
            }
            assertEquals(
                "the earliest retained identity repeats; an evicted item stays gone",
                oldestIdentity,
                port.playCalls.last().identity
            )
            assertTrue(
                "no evicted identity can reappear after the bounded window",
                port.playCalls.drop(playedBeforeBack)
                    .none { it.identity.itemOrdinal < oldestIdentity.itemOrdinal }
            )
        } finally {
            subject.close()
        }
    }
}
