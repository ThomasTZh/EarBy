package com.thomas.passiveeartraining.training.session

import com.thomas.passiveeartraining.training.BundledVoiceDurations
import com.thomas.passiveeartraining.training.IntervalDefinition
import com.thomas.passiveeartraining.training.MapVoiceDurationCatalog
import com.thomas.passiveeartraining.training.MonotonicClock
import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.provider.CompiledExercise
import com.thomas.passiveeartraining.training.provider.ConcreteExercise
import com.thomas.passiveeartraining.training.provider.ModeSeedDerivation
import com.thomas.passiveeartraining.training.provider.ModeSetup
import com.thomas.passiveeartraining.training.provider.ProviderConfig
import com.thomas.passiveeartraining.training.provider.ProviderRegistry
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SequencePolicy
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Virtual clock: only a test moves time.
 *
 * Internal so the exhaustive session tests share one controllable clock with this harness.
 */
internal class SwitchClock(private var now: Duration = Duration.ZERO) : MonotonicClock {
    override fun elapsed(): Duration = now

    fun advance(delta: Duration) {
        now += delta
    }
}

/** Waits for a condition driven by the real playback worker, with a bounded real timeout. */
internal suspend fun awaitSwitch(detail: String, check: () -> Boolean) {
    withContext(Dispatchers.Default) {
        withTimeout(5_000) {
            while (!check()) {
                yield()
            }
        }
    }
}

/**
 * A port that records every prepared exercise and play segment and consumes only what a test
 * states. The boundary callback is consulted live, so a pending switch stops the segment at its
 * unit end exactly as the production adapter does.
 *
 * Internal so the exhaustive session tests reuse the same fake instead of a second copy.
 */
internal class SegmentPort(private val sampleRate: Int = 48_000) : AudioPort {
    data class PlayCall(
        val identity: PlaybackIdentity,
        val startFrame: Long,
        val authorizedEnd: Long
    )

    val preparedExercises = java.util.concurrent.CopyOnWriteArrayList<ConcreteExercise>()

    /** Prepared identities in preparation order, so an abandoned preparation is identifiable. */
    val preparedIdentities =
        java.util.concurrent.CopyOnWriteArrayList<PlaybackIdentity>()
    val playCalls = java.util.concurrent.CopyOnWriteArrayList<PlayCall>()
    val stops = java.util.concurrent.CopyOnWriteArrayList<Pair<PlaybackIdentity, Long>>()
    private val layouts =
        java.util.concurrent.ConcurrentHashMap<PlaybackIdentity, AudioPort.Prepared>()

    /**
     * One-shot barrier for the next [prepare] call.
     *
     * A test sets it to hold a destination's preparation open while it issues another command. The
     * prepare call takes and clears it before awaiting, so exactly one preparation blocks and every
     * later prepare is immediate.
     */
    @Volatile
    var prepareBarrier: CompletableDeferred<Unit>? = null

    @Volatile
    private var consumed = 0L

    @Volatile
    private var currentTotal = 0L

    @Volatile
    private var releaseSignal: CompletableDeferred<Unit>? = null

    override fun configure(musicGain: Float, voiceGain: Float) = Unit

    override suspend fun prepare(
        identity: PlaybackIdentity,
        exercise: ConcreteExercise,
        compiled: CompiledExercise,
        settings: RenderSettings
    ): AudioPort.Prepared {
        prepareBarrier?.let { barrier ->
            prepareBarrier = null
            barrier.await()
        }
        val frames = compiled.timeline.totalDuration.inWholeMicroseconds * sampleRate / 1_000_000L
        fun boundary(duration: Duration): Long =
            duration.inWholeMicroseconds * sampleRate / 1_000_000L
        val layout = AudioPort.Prepared(
            answerStartFrame = 0L,
            totalFrames = frames,
            mandatoryEndFrame = boundary(compiled.mandatoryEnd).coerceAtMost(frames),
            replayFrames = compiled.replays.map { range ->
                boundary(range.start) until boundary(range.endExclusive)
            },
            trailingStartFrame = boundary(compiled.trailingStart).coerceAtMost(frames)
        )
        layouts[identity] = layout
        preparedExercises += exercise
        preparedIdentities += identity
        return layout
    }

    override suspend fun play(
        identity: PlaybackIdentity,
        startFrame: Long,
        boundaries: List<Long>,
        isCancelled: () -> Boolean,
        onConsumed: suspend (Long) -> Unit,
        continueAtBoundary: (Long) -> Boolean
    ): AudioPort.PlayOutcome {
        val layout = layouts.getValue(identity)
        currentTotal = layout.totalFrames
        consumed = startFrame
        val unauthorized = boundaries.filter { it > startFrame }.firstOrNull {
            !continueAtBoundary(it)
        }
        val authorizedEnd = unauthorized
            ?: boundaries.lastOrNull { it > startFrame }
            ?: layout.totalFrames
        playCalls += PlayCall(identity, startFrame, authorizedEnd)
        onConsumed(consumed)
        val done = CompletableDeferred<Unit>()
        releaseSignal = done
        var stopped: Long? = null
        var lastCrossed = startFrame
        while (!done.isCompleted && !isCancelled()) {
            // A boundary is consulted exactly while it is crossed. A boundary the test jumped past
            // was authorized when it was crossed and can no longer stop the call; the next one can.
            val boundary = boundaries.firstOrNull { it > lastCrossed && it <= consumed }
            if (boundary != null) {
                if (boundary == consumed && !continueAtBoundary(boundary)) {
                    stopped = boundary
                    break
                }
                lastCrossed = boundary
                continue
            }
            // The production adapter returns once the authorized segment is consumed; so does
            // this fake.
            if (consumed >= layout.totalFrames) break
            delay(2)
        }
        releaseSignal = null
        val cancelled = isCancelled()
        if (!cancelled && stopped != null) stops += identity to stopped
        return AudioPort.PlayOutcome(
            submittedFrames = (consumed - startFrame).coerceAtLeast(0L),
            consumedFrames = consumed,
            completed = !cancelled && consumed >= layout.totalFrames,
            stoppedAtBoundary = if (!cancelled) stopped else null
        )
    }

    override suspend fun requestPlaybackPermission(): Boolean = true

    override fun setMasterGain(gain: Float) = Unit

    override fun consumedFrames(): Long = consumed

    override fun flush() {
        consumed = 0L
    }

    override fun release() {
        releaseSignal?.complete(Unit)
    }

    fun consume(frame: Long) {
        consumed = frame.coerceAtMost(currentTotal)
    }

    fun layout(identity: PlaybackIdentity): AudioPort.Prepared = layouts.getValue(identity)
}

/**
 * M3 — a requested switch preserves the audible learning sequence and commits at a safe boundary.
 */
class SwitchModeTest {

    private val catalog = MapVoiceDurationCatalog(
        IntervalDefinition.default().selectedIntervalIds.associate { id ->
            VoiceAssetId("interval.$id") to Duration.parse("900ms")
        }
    )

    private fun request(
        seed: Long?,
        replayCount: Int = 1,
        stopPolicy: SessionStopPolicy = SessionStopPolicy.UntilStopped
    ): StartRequest = StartRequest(
        setup = ModeSetup(
            provider = ProviderConfig.Intervals(
                SequencePolicy(IntervalDefinition.default())
            ),
            recipe = RecipeDefinition(
                mode = RecipeMode.RECOGNITION,
                replayCount = replayCount
            ),
            seed = seed,
            stopPolicy = stopPolicy
        )
    )

    private fun controller(port: SegmentPort, clock: SwitchClock): DefaultSessionController =
        DefaultSessionController(
            source = ConfiguredExerciseSource(voiceDurations = { catalog }),
            audio = port,
            clock = clock,
            sampleRate = 48_000
        )

    /** The production voice pack, for destinations that are not intervals. */
    private fun bundledController(
        port: SegmentPort,
        clock: SwitchClock = SwitchClock()
    ): DefaultSessionController = DefaultSessionController(
        source = ConfiguredExerciseSource(voiceDurations = { BundledVoiceDurations }),
        audio = port,
        clock = clock,
        sampleRate = 48_000
    )

    /** A recognition request for any registered production kind's defaults. */
    private fun requestFor(kind: TrainingKind, seed: Long?): StartRequest {
        val provider = requireNotNull(ProviderRegistry.PRODUCTION.providerFor(kind)) {
            "No production provider is registered for $kind"
        }
        return StartRequest(
            setup = ModeSetup(
                provider = provider.defaults(),
                recipe = RecipeDefinition(mode = RecipeMode.RECOGNITION, replayCount = 0),
                seed = seed
            )
        )
    }

    @Test
    fun aSwitchDuringTheMandatoryUnitCommitsAtItsEndWithTheDerivedSeed() = runTest {
        val port = SegmentPort()
        val subject = controller(port, SwitchClock())
        try {
            subject.send(SessionCommand.Start(request(seed = 42L)))
            awaitSwitch("first play") { port.playCalls.size == 1 }
            assertEquals(
                TrainingKind.INTERVALS,
                subject.state.value.activeKind
            )
            val first = port.playCalls[0]
            val layout = port.layout(first.identity)
            port.consume(layout.mandatoryEndFrame / 2)

            subject.send(SessionCommand.SwitchMode(request(seed = null), savedRevision = 7L))
            awaitSwitch("pending published") { subject.state.value.pendingKind != null }
            assertEquals(7L, subject.state.value.pendingRevision)

            port.consume(layout.mandatoryEndFrame)
            awaitSwitch("destination prepared") { port.preparedExercises.size == 2 }
            awaitSwitch("destination playing") { port.playCalls.size == 2 }

            assertNull(subject.state.value.pendingKind)
            assertEquals(7L, subject.state.value.activeRevision)
            assertEquals(
                "the mandatory unit's end is the commit boundary",
                layout.mandatoryEndFrame,
                port.stops.single().second
            )
            // Frozen vector: intervals, ordinal 1, label activation, parent seed 42.
            assertEquals(
                -5510101068104975162L,
                port.preparedExercises[1].provenance.seed
            )
            assertNotEquals(first.identity, port.playCalls[1].identity)
            assertTrue(
                "identity ordinals stay monotonic",
                port.playCalls[1].identity.itemOrdinal > first.identity.itemOrdinal
            )
        } finally {
            subject.close()
        }
    }

    @Test
    fun aSwitchDuringAStartedReplayFinishesTheReplayFirst() = runTest {
        val port = SegmentPort()
        val subject = controller(port, SwitchClock())
        try {
            subject.send(SessionCommand.Start(request(seed = 42L, replayCount = 1)))
            awaitSwitch("first play") { port.playCalls.size == 1 }
            val first = port.playCalls[0]
            val layout = port.layout(first.identity)
            val replay = layout.replayFrames.single()
            port.consume(replay.first + 10)
            awaitSwitch("tick") { port.consumedFrames() >= replay.first + 10 }

            subject.send(SessionCommand.SwitchMode(request(seed = null), savedRevision = 3L))
            awaitSwitch("pending published") { subject.state.value.pendingKind != null }
            port.consume(replay.last + 1)
            awaitSwitch("destination playing") { port.playCalls.size == 2 }

            assertEquals(
                "the started replay completes before the switch",
                replay.last + 1,
                port.stops.single().second
            )
            assertEquals(3L, subject.state.value.activeRevision)
        } finally {
            subject.close()
        }
    }

    @Test
    fun aSwitchWhilePausedCommitsOnResume() = runTest {
        val port = SegmentPort()
        val subject = controller(port, SwitchClock())
        try {
            subject.send(SessionCommand.Start(request(seed = 42L)))
            awaitSwitch("first play") { port.playCalls.size == 1 }
            val first = port.playCalls[0]
            val layout = port.layout(first.identity)
            port.consume(layout.mandatoryEndFrame / 2)
            subject.send(SessionCommand.Pause())
            awaitSwitch("paused") { subject.state.value.phase == SessionPhase.PAUSED }

            subject.send(SessionCommand.SwitchMode(request(seed = null), savedRevision = 5L))
            awaitSwitch("pending published") { subject.state.value.pendingKind != null }
            assertEquals(
                "a pause alone does not commit a switch",
                SessionPhase.PAUSED,
                subject.state.value.phase
            )

            subject.send(SessionCommand.Resume)
            awaitSwitch("resumed play") { port.playCalls.size == 2 }
            port.consume(layout.mandatoryEndFrame)
            awaitSwitch("destination committed") {
                subject.state.value.activeRevision == 5L
            }
            assertEquals(5L, subject.state.value.activeRevision)
        } finally {
            subject.close()
        }
    }

    @Test
    fun returningToTheAudibleModeCancelsTheDeparture() = runTest {
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
            assertNull(subject.state.value.activeRevision)

            // The cancelled departure must not have prepared a destination. While the first item
            // is still playing the worker cannot have prepared anything else, so this count is
            // deterministic here; asserting it after the continuation would race the legitimate
            // preparation of the next item (the historical flake, see
            // SwitchCancelRepetitionTest).
            assertEquals(
                "the cancelled switch prepared no destination exercise",
                1,
                port.preparedExercises.size
            )

            port.consume(layout.totalFrames)
            awaitSwitch("normal continuation") {
                subject.state.value.itemIndex == 1
            }
            assertNull(subject.state.value.pendingKind)
            assertNull(subject.state.value.activeRevision)
        } finally {
            subject.close()
        }
    }

    @Test
    fun navigationAndStopWhilePending() = runTest {
        val port = SegmentPort()
        val subject = controller(port, SwitchClock())
        try {
            subject.send(SessionCommand.Start(request(seed = 42L)))
            awaitSwitch("first play") { port.playCalls.size == 1 }
            val layout = port.layout(port.playCalls[0].identity)
            port.consume(layout.mandatoryEndFrame / 2)
            subject.send(SessionCommand.SwitchMode(request(seed = null), savedRevision = 7L))
            awaitSwitch("pending published") { subject.state.value.pendingKind != null }

            val next = subject.send(SessionCommand.Next)
            assertTrue(
                "navigation is refused while a switch is pending",
                next is CommandResult.Unavailable
            )

            subject.send(SessionCommand.Stop())
            awaitSwitch("stopped") { subject.state.value.phase == SessionPhase.COMPLETED }
            assertNull(subject.state.value.pendingKind)
            assertEquals(1, port.preparedExercises.size)
        } finally {
            subject.close()
        }
    }

    @Test
    fun anExpiredTimerBeatsTheCommit() = runTest {
        val port = SegmentPort()
        val clock = SwitchClock()
        val subject = controller(port, clock)
        try {
            subject.send(
                SessionCommand.Start(
                    request(
                        seed = 42L,
                        stopPolicy = SessionStopPolicy.Duration(
                            limit = Duration.parse("1m"),
                            finish = com.thomas.passiveeartraining.training.recipe.DurationFinish
                                .IMMEDIATE
                        )
                    )
                )
            )
            awaitSwitch("first play") { port.playCalls.size == 1 }
            val layout = port.layout(port.playCalls[0].identity)
            port.consume(layout.mandatoryEndFrame / 2)
            subject.send(SessionCommand.SwitchMode(request(seed = null), savedRevision = 7L))
            awaitSwitch("pending published") { subject.state.value.pendingKind != null }

            // The deadline passes while the switch waits for its boundary.
            clock.advance(Duration.parse("61s"))
            port.consume(layout.mandatoryEndFrame)
            awaitSwitch("timer ended the session") {
                subject.state.value.phase == SessionPhase.COMPLETED
            }
            assertNull(subject.state.value.pendingKind)
            assertEquals(
                "the destination was never activated",
                1,
                port.preparedExercises.size
            )
        } finally {
            subject.close()
        }
    }

    @Test
    fun aFixedDestinationSeedWinsOverTheDerivation() = runTest {
        val port = SegmentPort()
        val subject = controller(port, SwitchClock())
        try {
            subject.send(SessionCommand.Start(request(seed = 42L)))
            awaitSwitch("first play") { port.playCalls.size == 1 }
            val layout = port.layout(port.playCalls[0].identity)
            port.consume(layout.mandatoryEndFrame / 2)
            subject.send(SessionCommand.SwitchMode(request(seed = 99L), savedRevision = 2L))
            awaitSwitch("pending published") { subject.state.value.pendingKind != null }
            port.consume(layout.mandatoryEndFrame)
            awaitSwitch("destination prepared") { port.preparedExercises.size == 2 }
            assertEquals(99L, port.preparedExercises[1].provenance.seed)
        } finally {
            subject.close()
        }
    }

    /**
     * A switch that arrives while the destination is still inside `prepare` must invalidate that
     * preparation, not corrupt it: the newer request commits at once, the abandoned preparation
     * completes and is discarded, and only the newest destination ever reaches the output.
     */
    @Test
    fun aSwitchWhileTheDestinationIsPreparingInvalidatesItInsteadOfCorruptingOutput() = runTest {
        val port = SegmentPort()
        val subject = bundledController(port)
        try {
            subject.send(SessionCommand.Start(requestFor(TrainingKind.INTERVALS, 42L)))
            awaitSwitch("first play") { port.playCalls.size == 1 }
            val layout = port.layout(port.playCalls[0].identity)
            port.consume(layout.mandatoryEndFrame / 2)

            subject.send(
                SessionCommand.SwitchMode(
                    requestFor(TrainingKind.CHORDS, null),
                    savedRevision = 7L
                )
            )
            awaitSwitch("chords pending") {
                subject.state.value.pendingKind == TrainingKind.CHORDS
            }

            // Hold the destination's preparation open, then let the interrupted unit finish so the
            // pending switch commits and the chord preparation starts.
            val barrier = CompletableDeferred<Unit>()
            port.prepareBarrier = barrier
            port.consume(layout.mandatoryEndFrame)
            awaitSwitch("destination preparing") { port.prepareBarrier == null }
            assertEquals(SessionPhase.PREPARING, subject.state.value.phase)
            assertEquals(TrainingKind.CHORDS, subject.state.value.activeKind)
            assertNull(subject.state.value.pendingKind)

            // The newer request runs while the destination is still inside prepare. It selects the
            // newest destination and must not disturb the in-flight preparation.
            subject.send(
                SessionCommand.SwitchMode(
                    requestFor(TrainingKind.SCALES, null),
                    savedRevision = 9L
                )
            )
            awaitSwitch("scales committed") {
                subject.state.value.activeKind == TrainingKind.SCALES
            }
            assertEquals(9L, subject.state.value.activeRevision)
            assertNull(subject.state.value.pendingKind)
            assertEquals(
                "the abandoned preparation must not have reached the output",
                1,
                port.playCalls.size
            )
            assertEquals(
                "the abandoned preparation has not recorded a result yet",
                1,
                port.preparedExercises.size
            )

            barrier.complete(Unit)
            awaitSwitch("scales playing") { port.playCalls.size == 2 }
            awaitSwitch("every preparation finished") { port.preparedExercises.size == 3 }
            assertEquals(
                "the abandoned chord item is prepared while the newest destination plays",
                listOf(TrainingKind.INTERVALS, TrainingKind.CHORDS, TrainingKind.SCALES),
                port.preparedExercises.map { it.kind }
            )
            val abandoned = port.preparedIdentities[1]
            assertTrue(
                "the abandoned preparation must never play",
                port.playCalls.none { it.identity == abandoned }
            )
            assertEquals(port.preparedIdentities[2], port.playCalls[1].identity)
            assertNotEquals(port.playCalls[0].identity, port.playCalls[1].identity)
            assertEquals(
                "the interrupted unit still ends at its exact mandatory boundary",
                layout.mandatoryEndFrame,
                port.stops.single().second
            )
        } finally {
            subject.close()
        }
    }

    /**
     * A→B then A→C while B is still pending: only the latest valid destination may commit, and it
     * must consume the first activation ordinal exactly once.
     */
    @Test
    fun aSecondPendingSwitchSupersedesTheFirstAndOnlyTheLatestCommits() = runTest {
        val port = SegmentPort()
        val subject = bundledController(port)
        try {
            subject.send(SessionCommand.Start(requestFor(TrainingKind.INTERVALS, 42L)))
            awaitSwitch("first play") { port.playCalls.size == 1 }
            val layout = port.layout(port.playCalls[0].identity)
            port.consume(layout.mandatoryEndFrame / 2)

            subject.send(
                SessionCommand.SwitchMode(
                    requestFor(TrainingKind.CHORDS, null),
                    savedRevision = 3L
                )
            )
            awaitSwitch("chords pending") {
                subject.state.value.pendingKind == TrainingKind.CHORDS
            }
            subject.send(
                SessionCommand.SwitchMode(
                    requestFor(TrainingKind.SCALES, null),
                    savedRevision = 4L
                )
            )
            awaitSwitch("scales supersede chords") {
                subject.state.value.pendingKind == TrainingKind.SCALES
            }
            assertEquals(4L, subject.state.value.pendingRevision)
            assertEquals(TrainingKind.INTERVALS, subject.state.value.activeKind)

            port.consume(layout.mandatoryEndFrame)
            awaitSwitch("destination playing") { port.playCalls.size == 2 }

            assertNull(subject.state.value.pendingKind)
            assertEquals(TrainingKind.SCALES, subject.state.value.activeKind)
            assertEquals(4L, subject.state.value.activeRevision)
            assertEquals(
                "the superseded chord request is never even prepared",
                listOf(TrainingKind.INTERVALS, TrainingKind.SCALES),
                port.preparedExercises.map { it.kind }
            )
            assertEquals(layout.mandatoryEndFrame, port.stops.single().second)
            assertNotEquals(port.playCalls[0].identity, port.playCalls[1].identity)
            // Only the latest request may advance the activation ordinal: the destination's seed is
            // the ordinal-1 derivation for scales, never for the superseded chords.
            assertEquals(
                ModeSeedDerivation.derive(
                    42L,
                    TrainingKind.SCALES,
                    1L,
                    ModeSeedDerivation.ACTIVATION
                ),
                port.preparedExercises[1].provenance.seed
            )
        } finally {
            subject.close()
        }
    }

    /**
     * Repeat and Previous racing a pending switch: both are refused, the audible unit keeps its
     * consumed position, and the switch still commits exactly at the mandatory boundary.
     */
    @Test
    fun repeatAndPreviousRaceAPendingSwitchWithoutBreakingTheUnit() = runTest {
        val port = SegmentPort()
        val subject = controller(port, SwitchClock())
        try {
            subject.send(SessionCommand.Start(request(seed = 42L)))
            awaitSwitch("first play") { port.playCalls.size == 1 }
            val first = port.playCalls[0]
            val layout = port.layout(first.identity)
            port.consume(layout.mandatoryEndFrame / 2)
            val consumedBefore = port.consumedFrames()

            subject.send(SessionCommand.SwitchMode(request(seed = null), savedRevision = 7L))
            awaitSwitch("pending published") { subject.state.value.pendingKind != null }

            val repeat = subject.send(SessionCommand.Repeat)
            assertTrue(
                "Repeat must be refused while a switch is pending",
                repeat is CommandResult.Unavailable
            )
            val previous = subject.send(SessionCommand.Previous)
            assertTrue(
                "Previous must be refused while a switch is pending",
                previous is CommandResult.Unavailable
            )
            assertEquals("a refused navigation adds no playback", 1, port.playCalls.size)
            assertTrue("a refused navigation records no stop", port.stops.isEmpty())
            assertEquals(
                "the audible unit keeps its consumed position",
                consumedBefore,
                port.consumedFrames()
            )
            assertEquals(SessionPhase.PLAYING, subject.state.value.phase)
            assertEquals(7L, subject.state.value.pendingRevision)
            assertEquals(TrainingKind.INTERVALS, subject.state.value.activeKind)

            port.consume(layout.mandatoryEndFrame)
            awaitSwitch("destination playing") { port.playCalls.size == 2 }
            assertEquals(
                "the refusal must not move the commit boundary",
                layout.mandatoryEndFrame,
                port.stops.single().second
            )
            assertEquals(7L, subject.state.value.activeRevision)
            assertNotEquals(first.identity, port.playCalls[1].identity)
        } finally {
            subject.close()
        }
    }
}
