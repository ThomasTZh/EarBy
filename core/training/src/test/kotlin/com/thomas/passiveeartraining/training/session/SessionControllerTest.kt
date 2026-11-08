package com.thomas.passiveeartraining.training.session

import com.thomas.passiveeartraining.training.IntervalDefinition
import com.thomas.passiveeartraining.training.MapVoiceDurationCatalog
import com.thomas.passiveeartraining.training.MonotonicClock
import com.thomas.passiveeartraining.training.RecognitionRecipe
import com.thomas.passiveeartraining.training.SeededIntervalGenerator
import com.thomas.passiveeartraining.training.SeededRandomSource
import com.thomas.passiveeartraining.training.Timeline
import com.thomas.passiveeartraining.training.TimelineCompiler
import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.provider.AnswerDisplay
import com.thomas.passiveeartraining.training.provider.CompiledExercise
import com.thomas.passiveeartraining.training.provider.ConcreteExercise
import com.thomas.passiveeartraining.training.recipe.EndingCause
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SequencePolicy
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.AnswerVisibility
import com.thomas.passiveeartraining.training.timer.SleepExpiry
import com.thomas.passiveeartraining.training.timer.SleepPolicy
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Virtual clock: time only moves when a test moves it, so no test sleeps. */
private class VirtualClock(private var now: Duration = Duration.ZERO) : MonotonicClock {
    override fun elapsed(): Duration = now

    fun advance(delta: Duration) {
        now += delta
    }
}

/** Waits for a condition driven by the real playback worker, with a bounded real timeout. */
private suspend fun waitFor(detail: String, check: () -> Boolean) {
    withContext(Dispatchers.Default) {
        // The worker uses real dispatchers. A shared CI runner can briefly starve it; 15 s keeps
        // the test bounded without turning a scheduling delay into a failure.
        withTimeout(15_000) {
            while (!check()) {
                yield()
            }
        }
    }
}

/**
 * Fake audio port with a controllable consumed position and a faithful one-current/next PCM cache.
 *
 * Completion is explicit: [play] suspends until the test calls [finishItem] or cancels the call.
 * That makes "submitted" and "consumed" genuinely different in tests, which is the property the
 * continuation rule depends on. [autoComplete] switches to immediate consumption for tests that
 * only need a session to move along.
 */
private class FakeAudioPort(private val sampleRate: Int = 48_000) : AudioPort {
    private class CachedEntry(val identity: String, val prepared: AudioPort.Prepared)

    /** Prepared identities in preparation order, so cache eviction is observable. */
    val preparedIdentities = java.util.concurrent.ConcurrentLinkedQueue<String>()

    @Volatile
    var preparedCount = 0
        private set

    @Volatile
    var playCount = 0
        private set

    @Volatile
    var flushCount = 0
        private set

    @Volatile
    var releaseCount = 0
        private set

    @Volatile
    var lastStartFrame = -1L
        private set

    @Volatile
    var lastPlayedIdentity: String? = null
        private set
    var failOnPrepare: String? = null
    var failOnPlay: String? = null
    var failOnFlush: Boolean = false
    var autoComplete: Boolean = false

    /** When true, [requestPlaybackPermission] refuses and no PCM may be submitted. */
    @Volatile
    var denyPlaybackPermission: Boolean = false

    /** How often permission was requested; every audible start must ask. */
    @Volatile
    var permissionRequests: Int = 0
        private set

    /** The last master gain the session applied; null means no fade command arrived. */
    @Volatile
    var lastMasterGain: Float? = null
        private set

    /** The last gain configuration the session applied, so a Start can be asserted. */
    var lastMusicGain: Float? = null
        private set
    var lastVoiceGain: Float? = null
        private set

    override fun configure(musicGain: Float, voiceGain: Float) {
        lastMusicGain = musicGain
        lastVoiceGain = voiceGain
        synchronized(cache) { cache.clear() }
    }

    private val cache = LinkedHashMap<String, CachedEntry>()

    @Volatile
    private var consumed = 0L
    private var answerStart = 0L
    private var total = 0L

    /** Completed by [finishItem] to end the suspended write. */
    @Volatile
    private var finishSignal: CompletableDeferred<Unit>? = null

    private fun answerStartFrame(timeline: Timeline): Long {
        val answer = timeline.events
            .filterIsInstance<com.thomas.passiveeartraining.training.VoiceEvent>()
            .minByOrNull { it.startOffset }
        return answer?.startOffset?.inWholeMicroseconds?.let { it * sampleRate / 1_000_000L } ?: 0L
    }

    override suspend fun prepare(
        identity: PlaybackIdentity,
        exercise: ConcreteExercise,
        compiled: CompiledExercise,
        settings: RenderSettings
    ): AudioPort.Prepared {
        val timeline = compiled.timeline
        failOnPrepare?.let { throw AudioPortException(it) }
        val cacheIdentity = "exercise:${identity.itemOrdinal}"
        synchronized(cache) {
            cache[cacheIdentity]?.let {
                // A cached identity is returned without re-rendering; still record the request so
                // tests can distinguish a cache hit from a render.
                preparedIdentities.add(cacheIdentity)
                return it.prepared
            }
        }
        preparedCount += 1
        val frames = timeline.totalDuration.inWholeMicroseconds * sampleRate / 1_000_000L

        // The same half-up frame rule the production adapter uses, so boundary tests exercise the
        // real unit ends instead of a degenerate single unit.
        fun boundaryFrames(duration: kotlin.time.Duration): Long =
            duration.inWholeMicroseconds * sampleRate / 1_000_000L
        val prepared = AudioPort.Prepared(
            answerStartFrame = answerStartFrame(timeline),
            totalFrames = frames,
            mandatoryEndFrame = boundaryFrames(compiled.mandatoryEnd).coerceAtMost(frames),
            replayFrames = compiled.replays.map { range ->
                boundaryFrames(range.start) until boundaryFrames(range.endExclusive)
            },
            trailingStartFrame = boundaryFrames(compiled.trailingStart).coerceAtMost(frames)
        )
        val entry = CachedEntry(cacheIdentity, prepared)
        synchronized(cache) {
            cache[cacheIdentity] = entry
            while (cache.size > 2) {
                cache.remove(cache.keys.first())
            }
        }
        preparedIdentities.add(cacheIdentity)
        return prepared
    }

    override suspend fun play(
        identity: PlaybackIdentity,
        startFrame: Long,
        boundaries: List<Long>,
        isCancelled: () -> Boolean,
        onConsumed: suspend (Long) -> Unit,
        continueAtBoundary: (Long) -> Boolean
    ): AudioPort.PlayOutcome {
        val cacheIdentity = "exercise:${identity.itemOrdinal}"
        failOnPlay?.let { throw AudioPortException(it) }
        val entry = synchronized(cache) { cache[cacheIdentity] }
            ?: throw AudioPortException("Nothing has been prepared for $identity")
        // The furthest frame this call may submit: the first boundary the controller refuses, or
        // the last authorized boundary.
        val stopAt = boundaries.filter { it > startFrame }.firstOrNull { !continueAtBoundary(it) }
            ?: boundaries.lastOrNull { it > startFrame }
            ?: entry.prepared.totalFrames
        // Publish the state the caller will observe before playCount makes the call visible: a test
        // that waits for the call must never race these assignments, and a completion signalled
        // immediately after must find its signal already installed.
        answerStart = entry.prepared.answerStartFrame
        total = entry.prepared.totalFrames
        lastPlayedIdentity = cacheIdentity
        lastStartFrame = startFrame
        consumed = startFrame
        val done = CompletableDeferred<Unit>()
        finishSignal = done
        val initial = consumed
        playCount += 1
        onConsumed(initial)
        if (autoComplete) {
            consumed = stopAt
            onConsumed(consumed)
            finishSignal = null
            return AudioPort.PlayOutcome(
                submittedFrames = (stopAt - startFrame).coerceAtLeast(0L),
                consumedFrames = consumed,
                completed = consumed >= total,
                stoppedAtBoundary = stopAt.takeIf { it < total }
            )
        }
        var reported = initial
        var stoppedAt: Long? = null
        while (!done.isCompleted && !isCancelled()) {
            val now = consumed
            if (now != reported) {
                reported = now
                onConsumed(now)
            }
            // A boundary the controller refused stops submission once its frames are consumed.
            val refused = boundaries
                .filter { it > startFrame && it <= consumed }
                .lastOrNull { !continueAtBoundary(it) }
            if (refused != null) {
                stoppedAt = refused
                consumed = refused
                break
            }
            delay(2)
        }
        finishSignal = null
        val cancelled = isCancelled()
        val boundaryStop = if (!done.isCompleted && !cancelled) stoppedAt else null
        val position = consumed
        onConsumed(position)
        return AudioPort.PlayOutcome(
            submittedFrames = (stopAt - startFrame).coerceAtLeast(0L),
            consumedFrames = position,
            completed = !cancelled && position >= total,
            stoppedAtBoundary = boundaryStop
        )
    }

    override fun consumedFrames(): Long = consumed

    override suspend fun requestPlaybackPermission(): Boolean {
        permissionRequests += 1
        return !denyPlaybackPermission
    }

    override fun setMasterGain(gain: Float) {
        lastMasterGain = gain
    }

    override fun flush() {
        flushCount += 1
        if (failOnFlush) error("injected flush failure")
        // A production flush silences the queued audio and resets the device position; the item
        // position retained for a resume lives in the controller, not in the device.
        consumed = 0L
    }

    override fun release() {
        releaseCount += 1
        synchronized(cache) { cache.clear() }
        finishSignal?.complete(Unit)
    }

    /** States that the device has consumed up to [frame] of the current item. */
    fun consume(frame: Long) {
        consumed = frame.coerceAtMost(total)
    }

    /** Marks the whole item consumed and lets the suspended write return as completed. */
    fun finishItem() {
        consumed = total
        finishSignal?.complete(Unit)
    }

    fun totalFrames(): Long = total

    fun answerFrame(): Long = answerStart
}

/** Deterministic exercise source that counts how often randomness is consumed. */
private class CountingSource(
    private val definition: IntervalDefinition = IntervalDefinition.default(),
    seed: Long = 42L
) : ExerciseSource {
    private val delegate = ConfiguredExerciseSource(
        voiceDurations = {
            MapVoiceDurationCatalog(
                definition.selectedIntervalIds.associate { id ->
                    VoiceAssetId("interval.$id") to Duration.parse("900ms")
                }
            )
        }
    )

    /** The most recent request the controller handed over, for configuration assertions. */
    var configuredRequest: StartRequest? = null
        private set

    var draws = 0
        private set

    /** The concrete exercise indexes drawn, in draw order. */
    val drawnIndexes = mutableListOf<Int>()
    private val drawn = mutableMapOf<Long, ConcreteExercise>()

    override fun configure(request: StartRequest) {
        configuredRequest = request
        delegate.configure(request)
    }

    override fun reconfigure(request: StartRequest) {
        configuredRequest = request
        delegate.reconfigure(request)
    }

    var failOnDraw: Int? = null
    var failOnTimeline = false

    override fun next(): ConcreteExercise {
        if (failOnDraw == draws + 1) error("generation failed")
        draws += 1
        val exercise = delegate.next()
        drawn[exercise.sequenceIndex.toLong()] = exercise
        drawnIndexes.add(exercise.sequenceIndex)
        return exercise
    }

    override fun compile(exercise: ConcreteExercise, recipe: RecipeDefinition): CompiledExercise {
        if (failOnTimeline) error("compilation failed")
        return delegate.compile(exercise, recipe)
    }

    fun exercise(index: Int): ConcreteExercise =
        drawn[index.toLong()] ?: error("exercise $index has not been drawn")
}

private fun startRequest(
    seed: Long = 42L,
    stopPolicy: SessionStopPolicy = SessionStopPolicy.UntilStopped
) = StartRequest(
    setup = com.thomas.passiveeartraining.training.provider.ModeSetup(
        provider = com.thomas.passiveeartraining.training.provider.ProviderConfig.Intervals(
            SequencePolicy(IntervalDefinition.default())
        ),
        recipe = RecipeDefinition.of(RecipeMode.RECOGNITION),
        seed = seed,
        stopPolicy = stopPolicy
    )
)

class SessionControllerTest {

    private fun controller(
        audio: FakeAudioPort = FakeAudioPort(),
        source: CountingSource = CountingSource()
    ): Triple<DefaultSessionController, FakeAudioPort, CountingSource> {
        val subject = DefaultSessionController(
            source = source,
            audio = audio,
            clock = VirtualClock(),
            sampleRate = 48_000
        )
        return Triple(subject, audio, source)
    }

    /** Starts a session and waits until the first item is actually playing. */
    private suspend fun DefaultSessionController.startAndPlay(audio: FakeAudioPort) {
        send(SessionCommand.Start(startRequest()))
        waitFor("first item should reach the device") { audio.playCount >= 1 }
    }

    /**
     * Moves the session to [frame] of the current item.
     *
     * The position is read from the device, so it is stated and then booked with a pause, exactly
     * as it is in production.
     */
    private suspend fun DefaultSessionController.advanceTo(audio: FakeAudioPort, frame: Long) {
        audio.consume(frame)
        send(SessionCommand.Pause())
    }

    @Test
    fun explicitStopAfterErrorClearsTheOldFailure() = runTest {
        val (subject, audio) = controller()
        try {
            audio.failOnPlay = "injected output failure"
            subject.send(SessionCommand.Start(startRequest()))
            waitFor("output error") { subject.state.value.phase == SessionPhase.ERROR }
            audio.failOnPlay = null
            subject.send(SessionCommand.Stop())
            assertEquals(null, subject.state.value.error)
            subject.startAndPlay(audio)
        } finally {
            subject.close()
        }
    }

    @Test
    fun stopReportsCleanupFailureAndStillReleasesTheOutput() = runTest {
        val (subject, audio) = controller()
        try {
            subject.startAndPlay(audio)
            audio.failOnFlush = true
            subject.send(SessionCommand.Stop())
            assertEquals(SessionPhase.ERROR, subject.state.value.phase)
            assertTrue(subject.state.value.error!!.detail.contains("injected flush failure"))
            assertTrue(audio.releaseCount > 0)
        } finally {
            subject.close()
        }
    }

    @Test
    fun hidingAnAlreadyConsumedAnswerRemovesItFromThePublishedSnapshot() = runTest {
        val (subject, audio, _) = controller()
        try {
            subject.startAndPlay(audio)
            audio.consume(audio.answerFrame())
            waitFor("answer consumed") { subject.state.value.answerVisible }
            subject.send(
                SessionCommand.Reconfigure(
                    startRequest().copy(
                        answerVisibility = AnswerVisibility.HIDDEN
                    )
                )
            )
            assertFalse(subject.state.value.answerVisible)
            assertEquals(null, subject.state.value.answerLabel)
        } finally {
            subject.close()
        }
    }

    @Test
    fun compilationFailureIsObservableAndReleasesAudio() = runTest {
        val source = CountingSource().apply { failOnTimeline = true }
        val (subject, audio, _) = controller(source = source)
        try {
            subject.send(SessionCommand.Start(startRequest()))
            waitFor("compilation error is published") {
                subject.state.value.phase ==
                    SessionPhase.ERROR
            }
            assertTrue(audio.releaseCount > 0)
            assertFalse(subject.state.value.answerVisible)
        } finally {
            subject.close()
        }
    }

    @Test
    fun navigationGenerationFailureIsObservableAndReleasesAudio() = runTest {
        val source = CountingSource()
        val (subject, audio, _) = controller(source = source)
        try {
            subject.startAndPlay(audio)
            subject.send(SessionCommand.Pause())
            source.failOnDraw = source.draws + 1
            subject.send(SessionCommand.Next)
            assertEquals(SessionPhase.ERROR, subject.state.value.phase)
            assertTrue(audio.releaseCount > 0)
        } finally {
            subject.close()
        }
    }

    @Test
    fun liveEditAfterPreviousDiscardsAllPreviouslyQueuedFutureItems() = runTest {
        val (subject, audio, source) = controller()
        try {
            subject.startAndPlay(audio)
            subject.send(SessionCommand.Pause())
            subject.send(SessionCommand.Next)
            subject.send(SessionCommand.Next)
            subject.send(SessionCommand.Previous)
            val before = source.draws
            subject.send(SessionCommand.Reconfigure(startRequest().copy(musicGain = 0.4f)))
            assertEquals(
                "an edit draws a new follower despite retained forward path",
                before + 1,
                source.draws
            )
        } finally {
            subject.close()
        }
    }

    @Test
    fun startPreparesAndPlaysTheFirstItem() = runTest {
        val (subject, audio, source) = controller()
        val result = subject.send(SessionCommand.Start(startRequest()))
        assertTrue(result is CommandResult.Applied)
        waitFor("first item should reach the device") { audio.playCount >= 1 }
        val state = subject.state.value
        assertEquals("start failed: ${state.error}", SessionPhase.PLAYING, state.phase)
        assertEquals(0, state.itemIndex)
        assertEquals(1, audio.preparedCount)
        assertEquals(1, audio.playCount)
        assertEquals("the first item is drawn with exactly one follower", 2, source.draws)
        assertEquals(audio.totalFrames(), state.totalFrames)
        assertTrue(state.controls.canPause)
        assertTrue(state.controls.canStop)
        assertFalse(state.controls.canStart)
        subject.close()
    }

    @Test
    fun theAcceptedStartConfiguresTheSourceAndOutputBeforeAnyItem() = runTest {
        val (subject, audio, source) = controller()
        val request = StartRequest(
            setup = com.thomas.passiveeartraining.training.provider.ModeSetup(
                provider = com.thomas.passiveeartraining.training.provider.ProviderConfig.Intervals(
                    SequencePolicy(IntervalDefinition.default())
                ),
                recipe = RecipeDefinition.of(RecipeMode.EXPOSURE),
                seed = 1234L
            ),
            musicGain = 0.5f,
            voiceGain = 0.125f
        )

        val result = subject.send(SessionCommand.Start(request))

        assertTrue(result is CommandResult.Applied)
        assertEquals(
            "the source receives the exact accepted request",
            request,
            source.configuredRequest
        )
        assertEquals(0.5f, audio.lastMusicGain!!, 0f)
        assertEquals(0.125f, audio.lastVoiceGain!!, 0f)
        subject.close()
    }

    @Test
    fun reconfigureAppliesTheEditToTheNextPairOnly() = runTest {
        val (subject, audio, source) = controller()
        subject.startAndPlay(audio)
        val drawsBefore = source.draws
        val audibleIdentity = audio.lastPlayedIdentity

        // The edit keeps the running seed but changes the recipe and both gains.
        val edited = startRequest().copy(
            setup = startRequest().setup.copy(
                recipe = RecipeDefinition.of(RecipeMode.EXPOSURE),
                seed = null
            ),
            musicGain = 0.5f,
            voiceGain = 0.125f
        )
        val result = subject.send(SessionCommand.Reconfigure(edited))

        assertTrue("a running session accepts an edit", result is CommandResult.Applied)
        assertEquals("the running seed is kept", 42L, subject.state.value.seed)
        assertEquals(
            "the source is re-pointed with the resolved seed",
            42L,
            source.configuredRequest!!.setup.seed
        )
        assertEquals(0.5f, audio.lastMusicGain!!, 0f)
        assertEquals(0.125f, audio.lastVoiceGain!!, 0f)
        assertEquals(
            "the audible item is not replaced or restarted",
            audibleIdentity,
            audio.lastPlayedIdentity
        )

        // Finishing the audible pair draws its replacement under the edited setup, plus a follower.
        audio.finishItem()
        waitFor("the second pair starts") { audio.playCount == 2 }
        assertEquals(
            "the discarded follower is redrawn under the edit and a new one drawn ahead",
            drawsBefore + 2,
            source.draws
        )
        subject.close()
    }

    @Test
    fun anEditEqualToTheActiveStartChangesNothing() = runTest {
        val (subject, audio, source) = controller()
        subject.startAndPlay(audio)
        val drawsBefore = source.draws
        val before = subject.state.value

        val result = subject.send(SessionCommand.Reconfigure(startRequest()))

        assertTrue(result is CommandResult.Applied)
        assertEquals("the follower is not discarded", drawsBefore, source.draws)
        assertEquals("the snapshot is unchanged", before, subject.state.value)
        subject.close()
    }

    @Test
    fun anEditWithoutASessionIsReportedRatherThanApplying() = runTest {
        val (subject, _, source) = controller()

        val result = subject.send(SessionCommand.Reconfigure(startRequest()))

        assertTrue(
            "an idle session must explain the rejected edit",
            result is CommandResult.Unavailable
        )
        assertEquals("no configuration reaches the source", 0, source.draws)
        subject.close()
    }

    @Test
    fun aStopPolicyEditReplacesTheRunningPolicy() = runTest {
        val (subject, audio, _) = controller()
        subject.startAndPlay(audio)

        val edited = startRequest(stopPolicy = SessionStopPolicy.Duration(Duration.parse("10m")))
        val result = subject.send(SessionCommand.Reconfigure(edited))

        assertTrue(result is CommandResult.Applied)
        assertTrue(
            "the edited duration arms the timer from now",
            subject.state.value.sleep.isEnabled
        )
        subject.close()
    }

    @Test
    fun aCompletedItemAdvancesAutomaticallyToTheNextConcreteItem() = runTest {
        val (subject, audio, source) = controller()
        subject.startAndPlay(audio)
        val firstIdentity = audio.lastPlayedIdentity

        // Full consumption of the first item - not its submission - is what advances the session.
        audio.finishItem()
        waitFor("the second item should start automatically") { audio.playCount >= 2 }
        assertEquals(1, subject.state.value.itemIndex)
        assertTrue(
            "the next item is a different concrete identity",
            audio.lastPlayedIdentity != firstIdentity
        )
        assertEquals("one-ahead plus the follower", 3, source.draws)
        assertTrue(
            "the second item was not navigated by a command",
            subject.state.value.phase == SessionPhase.PLAYING
        )
        subject.close()
    }

    @Test
    fun afterCycleStopsAtTheFirstCycleBoundary() = runTest {
        val (subject, audio, _) = controller()
        subject.send(
            SessionCommand.Start(startRequest(stopPolicy = SessionStopPolicy.AfterCycle))
        )
        waitFor("first item plays") { audio.playCount >= 1 }
        // The default definition has three intervals, so one cycle is three items.
        repeat(3) { completed ->
            audio.finishItem()
            if (completed < 2) {
                waitFor("item ${completed + 2} plays") { audio.playCount >= completed + 2 }
            }
        }
        waitFor("the session stops after the cycle") {
            subject.state.value.phase == SessionPhase.COMPLETED
        }
        assertEquals(EndingCause.CycleFinished, subject.state.value.endingCause)
        assertEquals("no sound starts after completion", 3, audio.playCount)
        subject.close()
    }

    @Test
    fun completedItemsStopsAfterTheRequestedCount() = runTest {
        val (subject, audio, _) = controller()
        val request = startRequest(stopPolicy = SessionStopPolicy.CompletedItems(2))
        subject.send(SessionCommand.Start(request))
        waitFor("first item plays") { audio.playCount >= 1 }
        audio.finishItem()
        waitFor("second item plays") { audio.playCount >= 2 }
        audio.finishItem()
        waitFor("the session stops after two completed items") {
            subject.state.value.phase == SessionPhase.COMPLETED
        }
        assertEquals(EndingCause.CompletedItemsReached(2), subject.state.value.endingCause)
        assertEquals("no sound starts after completion", 2, audio.playCount)
        subject.close()
    }

    @Test
    fun aStopPolicyEditDuringPlaybackStopsAtACycleBoundary() = runTest {
        val (subject, audio, _) = controller()
        subject.startAndPlay(audio)
        val edited = startRequest(stopPolicy = SessionStopPolicy.AfterCycle)
        assertTrue(subject.send(SessionCommand.Reconfigure(edited)) is CommandResult.Applied)

        // Finish items until the edited policy ends the session; bounded so a regression cannot
        // hang the suite. The redrawn follower may shorten the running cycle, so the exact count
        // is not asserted, only that a cycle boundary does stop the session.
        var finishes = 0
        while (subject.state.value.phase != SessionPhase.COMPLETED && finishes < 8) {
            audio.finishItem()
            finishes += 1
            waitFor("finish $finishes settles") {
                subject.state.value.phase == SessionPhase.COMPLETED ||
                    audio.playCount >= finishes + 1
            }
        }
        assertEquals(
            "the edited After-one-cycle policy must end the session",
            SessionPhase.COMPLETED,
            subject.state.value.phase
        )
        assertEquals(EndingCause.CycleFinished, subject.state.value.endingCause)
        assertTrue("the boundary arrived within a cycle", finishes in 1..3)
        // No sound may start after the completion.
        withContext(Dispatchers.Default) { delay(120) }
        assertEquals("no sound starts after completion", finishes, audio.playCount)
        subject.close()
    }

    @Test
    fun aCompletedItemsEditDuringPlaybackStopsAfterTheCount() = runTest {
        val (subject, audio, _) = controller()
        subject.startAndPlay(audio)
        val edited = startRequest(stopPolicy = SessionStopPolicy.CompletedItems(2))
        assertTrue(subject.send(SessionCommand.Reconfigure(edited)) is CommandResult.Applied)

        audio.finishItem()
        waitFor("second item plays") { audio.playCount >= 2 }
        audio.finishItem()
        waitFor("the edited count stops the session") {
            subject.state.value.phase == SessionPhase.COMPLETED
        }
        assertEquals(EndingCause.CompletedItemsReached(2), subject.state.value.endingCause)
        subject.close()
    }

    @Test
    fun severalItemsContinueFromOneStartAndKeepDistinctIdentities() = runTest {
        val (subject, audio, source) = controller()
        subject.startAndPlay(audio)
        val identities = mutableListOf(audio.lastPlayedIdentity)

        repeat(4) { completed ->
            audio.finishItem()
            waitFor("item ${completed + 1} should start") { audio.playCount >= completed + 2 }
            identities.add(audio.lastPlayedIdentity)
        }
        assertEquals("five distinct items played", 5, identities.toSet().size)
        assertEquals(4, subject.state.value.itemIndex)
        assertEquals("each forward item is drawn exactly once", 6, source.draws)
        subject.close()
    }

    @Test
    fun submissionAloneDoesNotAdvanceUntilTheDeviceConsumes() = runTest {
        val (subject, audio, _) = controller()
        subject.startAndPlay(audio)
        val total = audio.totalFrames()
        assertTrue(total > 0L)

        // The write has been submitted in full, but the device has consumed nothing yet.
        audio.consume(total)
        waitFor("the live position reaches the snapshot") {
            subject.state.value.consumedFrames == total
        }
        assertEquals(
            "no automatic advance before consumption is signalled",
            0,
            subject.state.value.itemIndex
        )
        assertEquals(1, audio.playCount)

        audio.finishItem()
        waitFor("consumption completion advances") { audio.playCount >= 2 }
        assertEquals(1, subject.state.value.itemIndex)
        subject.close()
    }

    @Test
    fun startWhileActiveIsRejectedUntilStopped() = runTest {
        val (subject, audio, source) = controller()
        subject.startAndPlay(audio)
        assertEquals(2, source.draws)
        val rejected = subject.send(SessionCommand.Start(startRequest(seed = 7L)))
        assertTrue(rejected is CommandResult.Unavailable)
        assertEquals("AlreadyActive", (rejected as CommandResult.Unavailable).reason.detail)
        assertEquals("a rejected Start must not draw randomness", 2, source.draws)

        subject.send(SessionCommand.Stop())
        assertEquals(SessionPhase.COMPLETED, subject.state.value.phase)
        assertTrue(
            subject.send(SessionCommand.Start(startRequest(seed = 7L))) is CommandResult.Applied
        )
        waitFor("the new session plays") { audio.playCount == 2 }
        assertEquals("a new Start draws its own first item and follower", 4, source.draws)
        subject.close()
    }

    @Test
    fun pauseRetainsThePositionAndResumeContinuesFromIt() = runTest {
        val (subject, audio, _) = controller()
        subject.startAndPlay(audio)
        audio.consume(20_000L)

        val paused = subject.send(SessionCommand.Pause())
        assertTrue(paused is CommandResult.Applied)
        assertEquals(SessionPhase.PAUSED, subject.state.value.phase)
        assertEquals(PauseReason.User, subject.state.value.pauseReason)
        assertEquals(20_000L, subject.state.value.consumedFrames)
        assertTrue("a pause flushes queued audio", audio.flushCount >= 1)
        assertTrue(subject.state.value.controls.canResume)

        subject.send(SessionCommand.Resume)
        waitFor("resume restarts the write") { audio.playCount >= 2 }
        assertEquals(SessionPhase.PLAYING, subject.state.value.phase)
        assertEquals("resume continues from the retained frame", 20_000L, audio.lastStartFrame)
        subject.close()
    }

    @Test
    fun duplicatePauseAndResumeAreIdempotent() = runTest {
        val (subject, audio, _) = controller()
        subject.startAndPlay(audio)
        assertEquals(1, audio.playCount)

        subject.send(SessionCommand.Pause())
        val again = subject.send(SessionCommand.Pause())
        assertTrue("a second pause is accepted, not a failure", again is CommandResult.Applied)
        assertEquals("a duplicate pause must not start playback", 1, audio.playCount)

        subject.send(SessionCommand.Resume)
        waitFor("resume starts playback") { audio.playCount == 2 }
        val extraResume = subject.send(SessionCommand.Resume)
        assertTrue(extraResume is CommandResult.Unavailable)
        assertEquals("resume while playing must not duplicate playback", 2, audio.playCount)
        subject.close()
    }

    @Test
    fun pauseBeforeStartIsUnavailableAndChangesNothing() = runTest {
        val (subject, audio, _) = controller()
        val result = subject.send(SessionCommand.Pause())
        assertTrue(result is CommandResult.Unavailable)
        assertEquals(SessionPhase.IDLE, subject.state.value.phase)
        assertEquals(0, audio.playCount)
        subject.close()
    }

    @Test
    fun pausedNavigationNeverStartsSound() = runTest {
        val (subject, audio, _) = controller()
        subject.startAndPlay(audio)
        subject.send(SessionCommand.Pause())
        val plays = audio.playCount

        subject.send(SessionCommand.Next)
        subject.send(SessionCommand.Repeat)
        subject.send(SessionCommand.Previous)
        withContext(Dispatchers.Default) { delay(20) }

        assertEquals("paused navigation must not call the output", plays, audio.playCount)
        assertEquals(SessionPhase.PAUSED, subject.state.value.phase)
        subject.close()
    }

    @Test
    fun nextAdvancesAndRepeatReplaysWithoutDrawing() = runTest {
        val (subject, audio, source) = controller()
        subject.startAndPlay(audio)
        val drawsAfterStart = source.draws

        subject.send(SessionCommand.Next)
        waitFor("Next starts the following item") { audio.playCount == 2 }
        assertEquals(1, subject.state.value.itemIndex)
        assertEquals(
            "Next uses the drawn-ahead item and draws its follower",
            drawsAfterStart + 1,
            source.draws
        )

        val drawsBeforeRepeat = source.draws
        val playsBeforeRepeat = audio.playCount
        subject.send(SessionCommand.Repeat)
        waitFor("Repeat restarts the write") { audio.playCount > playsBeforeRepeat }
        assertEquals("Repeat must not consume randomness", drawsBeforeRepeat, source.draws)
        assertEquals("Repeat stays on the same item", 1, subject.state.value.itemIndex)
        assertEquals("Repeat restarts the item", 0L, subject.state.value.consumedFrames)

        // A second press while the restart is running supersedes it rather than drawing anything.
        subject.send(SessionCommand.Repeat)
        withContext(Dispatchers.Default) { delay(50) }
        assertEquals("a superseding Repeat must not draw", drawsBeforeRepeat, source.draws)
        assertEquals(1, subject.state.value.itemIndex)
        subject.close()
    }

    @Test
    fun previousStepsBackWhenPastTheThresholdAndRestartsOtherwise() = runTest {
        val (subject, audio, source) = controller()
        subject.startAndPlay(audio)
        subject.send(SessionCommand.Next)
        waitFor("second item plays") { subject.state.value.itemIndex == 1 && audio.playCount == 2 }
        val draws = source.draws
        assertTrue("the second item was drawn", draws >= 2)

        // An early position: Previous selects the retained earlier item.
        subject.send(SessionCommand.Pause())
        waitFor("paused at the item start") {
            subject.state.value.phase == SessionPhase.PAUSED &&
                subject.state.value.consumedFrames == 0L
        }
        subject.send(SessionCommand.Previous)
        waitFor("stepped back to the first item") { subject.state.value.itemIndex == 0 }
        assertEquals("an early Previous steps back", 0, subject.state.value.itemIndex)
        assertEquals("stepping back must not draw", draws, source.draws)

        // A late position restarts the current item instead of stepping back further.
        subject.send(SessionCommand.Resume)
        waitFor("resumed") { subject.state.value.phase == SessionPhase.PLAYING }
        subject.advanceTo(audio, 5L * 48_000L)
        waitFor("paused at five seconds") {
            subject.state.value.phase == SessionPhase.PAUSED &&
                subject.state.value.consumedFrames == 5L * 48_000L
        }
        val plays = audio.playCount
        subject.send(SessionCommand.Previous)
        waitFor("the restart is selected without sound") {
            subject.state.value.itemIndex == 0 && subject.state.value.consumedFrames == 0L
        }
        assertEquals("a paused Previous never starts sound", plays, audio.playCount)
        subject.send(SessionCommand.Resume)
        waitFor("resume starts the restarted write") { audio.playCount > plays }
        assertEquals("a late Previous restarts the same item", 0, subject.state.value.itemIndex)
        assertEquals("restarting must not draw", draws, source.draws)
        subject.close()
    }

    @Test
    fun backwardThenForwardTraversesHistoryWithoutRedrawing() = runTest {
        val (subject, audio, source) = controller()
        subject.startAndPlay(audio)
        subject.send(SessionCommand.Next)
        waitFor("second item plays") { subject.state.value.itemIndex == 1 && audio.playCount == 2 }
        subject.send(SessionCommand.Next)
        waitFor("third item plays") { subject.state.value.itemIndex == 2 && audio.playCount == 3 }
        val drawsAfterThree = source.draws

        // An early position steps backwards without drawing; forward restores the same items.
        subject.send(SessionCommand.Pause())
        waitFor("paused") { subject.state.value.phase == SessionPhase.PAUSED }
        subject.send(SessionCommand.Previous)
        waitFor("back at item 1") { subject.state.value.itemIndex == 1 }
        subject.send(SessionCommand.Previous)
        waitFor("back at item 0") { subject.state.value.itemIndex == 0 }
        assertEquals("backward navigation must not draw", drawsAfterThree, source.draws)

        subject.send(SessionCommand.Next)
        waitFor("forward to item 1") { subject.state.value.itemIndex == 1 }
        subject.send(SessionCommand.Next)
        waitFor("forward to item 2") { subject.state.value.itemIndex == 2 }
        assertEquals("forward traversal must not redraw", drawsAfterThree, source.draws)
        assertEquals("paused navigation stayed silent", 3, audio.playCount)
        subject.close()
    }

    @Test
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    fun aHistoryItemWhosePcmWasEvictedIsRenderedAgainWithTheSameIdentity() = runTest {
        val audio = FakeAudioPort()
        val source = CountingSource()
        val subject = DefaultSessionController(
            source,
            audio,
            VirtualClock(),
            backgroundScope,
            playbackDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
            commandDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        )
        suspend fun command(command: SessionCommand) {
            subject.send(command)
            testScheduler.advanceTimeBy(3)
            runCurrent()
        }
        try {
            command(SessionCommand.Start(startRequest()))
            val firstIdentity = audio.lastPlayedIdentity
            for (index in 1..3) {
                command(SessionCommand.Next)
                assertEquals(
                    "each item actually renders before navigating",
                    index,
                    subject.state.value.itemIndex
                )
                assertEquals("exercise:$index", audio.lastPlayedIdentity)
            }
            command(SessionCommand.Pause())
            repeat(3) { command(SessionCommand.Previous) }
            assertEquals(0, subject.state.value.itemIndex)
            command(SessionCommand.Resume)
            assertEquals(firstIdentity, audio.lastPlayedIdentity)
            assertTrue(audio.preparedIdentities.count { it == firstIdentity } >= 2)
            assertEquals("no new randomness during navigation", 5, source.draws)
        } finally {
            subject.close()
        }
    }

    @Test
    fun theGeneratedSequenceIsUnchangedByNavigation() = runTest {
        // Draw a session with a known seed, navigate, then compare the drawn indexes with a fresh
        // generator draw order: navigation must never consume randomness.
        val (subject, audio, source) = controller()
        subject.startAndPlay(audio)
        subject.send(SessionCommand.Next)
        waitFor("item 1") { subject.state.value.itemIndex == 1 }
        subject.send(SessionCommand.Next)
        waitFor("item 2") { subject.state.value.itemIndex == 2 }
        val drawsAfterNavigation = source.draws
        subject.send(SessionCommand.Pause())
        waitFor("paused") { subject.state.value.phase == SessionPhase.PAUSED }
        subject.send(SessionCommand.Previous)
        waitFor("back at 1") { subject.state.value.itemIndex == 1 }
        subject.send(SessionCommand.Next)
        waitFor("forward at 2") { subject.state.value.itemIndex == 2 }
        assertEquals("navigation must not draw again", drawsAfterNavigation, source.draws)

        val direct = SeededIntervalGenerator(IntervalDefinition.default(), SeededRandomSource(42L))
        val expectedPrefix = (0 until drawsAfterNavigation).map { direct.next().sequenceIndex }
        assertEquals(
            "the concrete items are the seeded sequence",
            expectedPrefix,
            source.drawnIndexes.toList()
        )
        subject.close()
    }

    @Test
    fun answerVisibilityFollowsConsumedAudioWhilePlaying() = runTest {
        val (subject, audio, source) = controller()
        subject.startAndPlay(audio)
        val answerFrame = audio.answerFrame()
        assertTrue("the fixture must have an answer frame", answerFrame > 0L)
        assertFalse(
            "the answer must stay hidden while its frame has not been consumed",
            subject.state.value.answerVisible
        )
        assertTrue(
            "an unconsumed answer label must not be published",
            subject.state.value.answerLabel == null
        )

        audio.consume(answerFrame)
        waitFor("the answer marker reaches the snapshot while audio is active") {
            subject.state.value.answerVisible
        }
        assertEquals(
            "the consumed answer carries the concrete interval name",
            source.exercise(0).answer.display.label(),
            subject.state.value.answerLabel
        )
        assertEquals(SessionPhase.PLAYING, subject.state.value.phase)
        subject.close()
    }

    @Test
    fun playedDurationGrowsFromLiveConsumption() = runTest {
        val (subject, audio, _) = controller()
        subject.startAndPlay(audio)
        val total = audio.totalFrames()
        assertTrue(total > 0L)
        audio.consume(total / 2)
        waitFor("half the item reports a played duration") {
            subject.state.value.playedDuration > Duration.ZERO
        }
        val half = subject.state.value.playedDuration
        audio.consume(total)
        waitFor("full consumption reports more duration") {
            subject.state.value.playedDuration > half
        }
        subject.close()
    }

    @Test
    fun currentNotesArePublishedWhilePlayingAndClearedOnStop() = runTest {
        val (subject, audio, _) = controller()
        subject.startAndPlay(audio)
        waitFor("the current notes are published") {
            subject.state.value.currentNotes.isNotEmpty()
        }
        val notes = subject.state.value.currentNotes
        assertTrue("notes are valid MIDI values", notes.all { it in 0..127 })
        subject.send(SessionCommand.Stop())
        assertEquals(SessionPhase.COMPLETED, subject.state.value.phase)
        assertTrue(
            "stopping clears the debug note line",
            subject.state.value.currentNotes.isEmpty()
        )
        subject.close()
    }

    @Test
    fun stopCompletesTheSessionAndReleasesOutput() = runTest {
        val (subject, audio, _) = controller()
        subject.startAndPlay(audio)
        val result = subject.send(SessionCommand.Stop())
        assertTrue(result is CommandResult.Applied)
        assertEquals(SessionPhase.COMPLETED, subject.state.value.phase)
        assertEquals(PauseReason.Stopped, subject.state.value.pauseReason)
        assertTrue("stop releases the output", audio.releaseCount >= 1)
        assertFalse("a completed session offers no pause", subject.state.value.controls.canPause)
        assertTrue("a completed session offers a new Start", subject.state.value.controls.canStart)
        subject.close()
    }

    @Test
    fun stopDuringPreparationWinsOverTheLateRender() = runTest {
        val (subject, audio, source) = controller()
        subject.send(SessionCommand.Start(startRequest()))
        // Stop immediately, before the worker is likely to have reached the device.
        val stopped = subject.send(SessionCommand.Stop())
        assertTrue(stopped is CommandResult.Applied)
        waitFor("the release is observed") { audio.releaseCount >= 1 }
        withContext(Dispatchers.Default) { delay(50) }
        assertEquals("a stopped session must not advance", 0, subject.state.value.itemIndex)
        assertEquals(SessionPhase.COMPLETED, subject.state.value.phase)
        assertEquals(2, source.draws)
        subject.close()
    }

    @Test
    fun aPreparationFailureEntersErrorWithoutRetrying() = runTest {
        val audio = FakeAudioPort()
        audio.failOnPrepare = "answer clip missing"
        val (subject, _, _) = controller(audio = audio)
        subject.send(SessionCommand.Start(startRequest()))
        waitFor("the failure reaches the snapshot") {
            subject.state.value.phase ==
                SessionPhase.ERROR
        }
        val state = subject.state.value
        assertEquals(SessionError.ErrorCode.OUTPUT_FAILURE, state.error?.code)
        assertTrue(state.error!!.detail.contains("answer clip missing"))
        withContext(Dispatchers.Default) { delay(30) }
        assertEquals("a failure must not retry on its own", 0, audio.playCount)
        assertTrue("an error state offers an explicit Start", state.controls.canStart)
        subject.close()
    }

    @Test
    fun everyCommandIsAcceptedOrExplainedInEveryPhase() = runTest {
        // A caller must never be left guessing: each command either applies or reports why not.
        val (subject, audio, _) = controller()
        val commands = listOf(
            SessionCommand.Pause(),
            SessionCommand.Resume,
            SessionCommand.Next,
            SessionCommand.Previous,
            SessionCommand.Repeat,
            SessionCommand.Start(startRequest()),
            SessionCommand.Reconfigure(startRequest()),
            SessionCommand.Pause(),
            SessionCommand.Resume,
            SessionCommand.Stop(),
            SessionCommand.Resume,
            SessionCommand.Stop()
        )
        commands.forEach { command ->
            val outcome = subject.send(command)
            when (outcome) {
                is CommandResult.Applied -> Unit
                is CommandResult.Unavailable -> assertTrue(outcome.reason.detail.isNotBlank())
            }
        }
        waitFor("the last Stop releases the worker") {
            subject.state.value.phase ==
                SessionPhase.COMPLETED
        }
        assertEquals(SessionPhase.COMPLETED, subject.state.value.phase)
        subject.close()
    }

    @Test
    fun cumulativePlayedDurationOnlyGrows() = runTest {
        val (subject, audio, _) = controller()
        subject.startAndPlay(audio)
        audio.consume(40_000L)
        waitFor("consumed audio produces played duration") {
            subject.state.value.playedDuration > Duration.ZERO
        }
        subject.send(SessionCommand.Pause())
        val afterFirst = subject.state.value.playedDuration

        subject.send(SessionCommand.Resume)
        waitFor("resume plays") { audio.playCount >= 2 }
        audio.consume(80_000L)
        waitFor("more consumed audio") { subject.state.value.playedDuration > afterFirst }
        val afterSecond = subject.state.value.playedDuration
        subject.send(SessionCommand.Pause())

        // Navigation resets the item position but must not subtract from the cumulative duration.
        subject.send(SessionCommand.Next)
        assertTrue(
            "navigation must not reduce the cumulative played duration",
            subject.state.value.playedDuration >= afterSecond
        )
        subject.close()
    }

    @Test
    fun aStoppedSessionDoesNotResumeImplicitly() = runTest {
        val (subject, audio, _) = controller()
        subject.startAndPlay(audio)
        subject.send(SessionCommand.Stop())
        val plays = audio.playCount
        val result = subject.send(SessionCommand.Resume)
        assertTrue(result is CommandResult.Unavailable)
        assertEquals("a completed session never resumes implicitly", plays, audio.playCount)
        assertEquals(SessionPhase.COMPLETED, subject.state.value.phase)
        subject.close()
    }

    @Test
    fun aCommandAfterCloseIsReportedRatherThanThrowing() = runTest {
        // A released session can still receive a late command - a disconnecting screen, or a
        // service being destroyed - and that must be an unavailable result, never a crash.
        val (subject, audio, _) = controller()
        subject.startAndPlay(audio)
        subject.close()
        val outcome = subject.send(SessionCommand.Pause())
        assertTrue("a closed session reports unavailability", outcome is CommandResult.Unavailable)
        assertTrue((outcome as CommandResult.Unavailable).reason.detail.contains("closed"))
    }

    @Test
    fun separateSessionsCarryDistinctIdentities() = runTest {
        val (subject, audio, _) = controller()
        subject.startAndPlay(audio)
        val first = subject.state.value.session
        subject.send(SessionCommand.Stop())
        subject.send(SessionCommand.Start(startRequest(seed = 9L)))
        waitFor("the new session plays") { audio.playCount >= 2 }
        assertNotEquals("each session carries its own identity", first, subject.state.value.session)
        subject.close()
    }

    @Test
    fun pausingAtDifferentPointsInAnItemRetainsTheSamePosition() = runTest {
        // The session position is consumed audio, so it does not depend on which timeline event is
        // sounding; pausing in a note, a voice or a gap behaves identically.
        val (subject, audio, _) = controller()
        subject.startAndPlay(audio)
        val total = audio.totalFrames()
        assertTrue(total > 0L)
        listOf(1_000L, total / 2, total - 1_000L).forEach { frame ->
            audio.consume(frame)
            subject.send(SessionCommand.Pause())
            assertEquals(
                "pause at $frame retains that frame",
                frame,
                subject.state.value.consumedFrames
            )
            assertEquals(SessionPhase.PAUSED, subject.state.value.phase)
            val playsBefore = audio.playCount
            subject.send(SessionCommand.Resume)
            waitFor("resume starts another write") { audio.playCount > playsBefore }
            assertEquals(SessionPhase.PLAYING, subject.state.value.phase)
        }
        subject.close()
    }

    @Test
    fun aWriteFailureEntersErrorRatherThanStoppingSilently() = runTest {
        val audio = FakeAudioPort()
        audio.failOnPlay = "output lost"
        val (subject, _, _) = controller(audio = audio)
        subject.send(SessionCommand.Start(startRequest()))
        waitFor("the write failure reaches the snapshot") {
            subject.state.value.phase == SessionPhase.ERROR
        }
        assertEquals(SessionError.ErrorCode.OUTPUT_FAILURE, subject.state.value.error?.code)
        assertTrue(subject.state.value.error!!.detail.contains("output lost"))
        subject.close()
    }

    @Test
    fun liveCallbacksFromAnOldWorkerCannotActOnANewSession() = runTest {
        val (subject, audio, _) = controller()
        subject.startAndPlay(audio)
        // Stop invalidates the worker, then a new session starts. A late callback from the first
        // worker must not change the new session's position or item.
        subject.send(SessionCommand.Stop())
        subject.send(SessionCommand.Start(startRequest(seed = 7L)))
        waitFor("new session plays") { audio.playCount >= 2 }
        val index = subject.state.value.itemIndex
        withContext(Dispatchers.Default) { delay(30) }
        assertEquals(
            "a stale worker must not move the new session",
            index,
            subject.state.value.itemIndex
        )
        assertEquals(SessionPhase.PLAYING, subject.state.value.phase)
        subject.close()
    }

    @Test
    fun closingWhileAudioIsActiveReleasesWithoutWaitingForTheWorker() = runTest {
        val (subject, audio, _) = controller()
        subject.startAndPlay(audio)
        // The write is suspended in the fake; close must still return and release the output.
        subject.close()
        assertTrue("close releases the output", audio.releaseCount >= 1)
        val afterClose = subject.send(SessionCommand.Pause())
        assertTrue(
            "a closed session reports unavailability instead of hanging on the worker",
            afterClose is CommandResult.Unavailable
        )
    }

    // ---------------------------------------------------------------------------------------------
    // WP3.3a: focus/readiness before sound
    // ---------------------------------------------------------------------------------------------

    @Test
    fun deniedPlaybackPermissionKeepsTheOutputSilentAndPauses() = runTest {
        val audio = FakeAudioPort()
        audio.denyPlaybackPermission = true
        val (subject, _, _) = controller(audio = audio)
        subject.send(SessionCommand.Start(startRequest()))
        waitFor("the denial reaches the snapshot") {
            subject.state.value.phase == SessionPhase.PAUSED &&
                subject.state.value.pauseReason == PauseReason.Focus
        }
        withContext(Dispatchers.Default) { delay(50) }
        assertEquals("a denied request must submit no PCM", 0, audio.playCount)
        assertTrue("the denial is requested, not assumed", audio.permissionRequests >= 1)

        // An explicit resume after the platform grants permission plays normally.
        audio.denyPlaybackPermission = false
        subject.send(SessionCommand.Resume)
        waitFor("the granted resume plays") { audio.playCount == 1 }
        assertEquals(SessionPhase.PLAYING, subject.state.value.phase)
        subject.close()
    }

    @Test
    fun everyAudibleStartAsksForPermissionAgainWithoutOpeningADuplicateOutput() = runTest {
        val (subject, audio, _) = controller()
        subject.startAndPlay(audio)
        assertEquals(1, audio.permissionRequests)

        subject.send(SessionCommand.Next)
        waitFor("the next item asks again") { audio.permissionRequests == 2 }
        subject.send(SessionCommand.Repeat)
        waitFor("the repeat asks again") { audio.permissionRequests == 3 }
        subject.send(SessionCommand.Pause())
        subject.send(SessionCommand.Resume)
        waitFor("the resume asks again") { audio.permissionRequests == 4 }
        assertEquals("no overlapping output was opened", 4, audio.playCount)
        subject.close()
    }

    // ---------------------------------------------------------------------------------------------
    // WP3.4a: the timer actually stops playback
    // ---------------------------------------------------------------------------------------------

    private class TimerController {
        val clock = VirtualClock()
        val scheduler = com.thomas.passiveeartraining.training.timer.ManualTimerScheduler()
        lateinit var subject: DefaultSessionController
        lateinit var audio: FakeAudioPort
        lateinit var source: CountingSource

        fun build(): TimerController {
            audio = FakeAudioPort()
            source = CountingSource()
            subject = DefaultSessionController(
                source = source,
                audio = audio,
                clock = clock,
                sampleRate = 48_000,
                timerScheduler = scheduler
            )
            return this
        }

        /** Advances the clock and runs one round of scheduled ticks. */
        fun tick(delta: Duration) {
            clock.advance(delta)
            scheduler.runAll()
        }
    }

    @Test
    fun theDurationTimerStopsPlaybackAtTheDeadline() = runTest {
        val harness = TimerController().build()
        val subject = harness.subject
        subject.send(SessionCommand.Start(startRequest()))
        subject.send(SessionCommand.SleepTimer(1))
        waitFor("first item plays") { harness.audio.playCount >= 1 }
        assertTrue("the armed timer is observable", subject.state.value.sleep.isEnabled)

        harness.tick(Duration.parse("30s"))
        withContext(Dispatchers.Default) { delay(50) }
        assertEquals(
            "mid-deadline playback continues",
            SessionPhase.PLAYING,
            subject.state.value.phase
        )

        harness.tick(Duration.parse("31s"))
        waitFor("the deadline completes the session") {
            subject.state.value.phase == SessionPhase.COMPLETED
        }
        assertTrue(
            "the completion reason is the timer",
            subject.state.value.pauseReason is PauseReason.TimerExpired
        )
        val playsAfterStop = harness.audio.playCount
        harness.tick(Duration.parse("10s"))
        withContext(Dispatchers.Default) { delay(50) }
        assertEquals("no audio reappears after expiry", playsAfterStop, harness.audio.playCount)
        subject.close()
    }

    @Test
    fun finishCurrentDrainsTheAudibleItemThenStopsWithoutGeneratingTheNext() = runTest {
        val harness = TimerController().build()
        val subject = harness.subject
        subject.send(
            SessionCommand.Start(
                startRequest(
                    stopPolicy = com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
                        .Duration(
                            limit = Duration.parse("1m"),
                            finish = com.thomas.passiveeartraining.training.recipe.DurationFinish
                                .FINISH_CURRENT_ITEM
                        )
                )
            )
        )
        waitFor("first item plays") { harness.audio.playCount >= 1 }

        harness.tick(Duration.parse("61s"))
        waitFor("the drain is observable") { subject.state.value.sleep.draining }
        assertEquals(
            "the audible item keeps playing",
            SessionPhase.PLAYING,
            subject.state.value.phase
        )
        assertEquals("no next item is generated during drain", 1, harness.audio.playCount)

        // The current item now finishes; the session ends instead of advancing.
        harness.audio.finishItem()
        waitFor("the drained session completes") {
            subject.state.value.phase == SessionPhase.COMPLETED
        }
        assertEquals(
            "the ending cause records the drained finish",
            com.thomas.passiveeartraining.training.recipe.EndingCause.DurationReached(
                drained = true
            ),
            subject.state.value.endingCause
        )
        assertEquals("no next item was played", 1, harness.audio.playCount)
        subject.close()
    }

    @Test
    fun expiryWhilePausedStaysSilentAndProhibitsResume() = runTest {
        val harness = TimerController().build()
        val subject = harness.subject
        subject.send(SessionCommand.Start(startRequest()))
        subject.send(SessionCommand.SleepTimer(1))
        waitFor("first item plays") { harness.audio.playCount >= 1 }
        val plays = harness.audio.playCount
        subject.send(SessionCommand.Pause())

        harness.tick(Duration.parse("61s"))
        waitFor("a paused expiry completes silently") {
            subject.state.value.phase == SessionPhase.COMPLETED
        }
        val refused = subject.send(SessionCommand.Resume)
        assertTrue("resume after expiry is unavailable", refused is CommandResult.Unavailable)
        withContext(Dispatchers.Default) { delay(30) }
        assertEquals("no sound after expiry", plays, harness.audio.playCount)
        subject.close()
    }

    @Test
    fun replacingTheTimerStartsANewDeadlineFromTheReplacement() = runTest {
        val harness = TimerController().build()
        val subject = harness.subject
        subject.send(SessionCommand.Start(startRequest()))
        subject.send(SessionCommand.SleepTimer(1))
        waitFor("first item plays") { harness.audio.playCount >= 1 }

        harness.tick(Duration.parse("30s"))
        subject.send(SessionCommand.SleepTimer(2))
        // The original deadline would have passed here; the replacement must not have expired.
        harness.tick(Duration.parse("31s"))
        withContext(Dispatchers.Default) { delay(50) }
        assertEquals(
            "the replacement replaced the deadline",
            SessionPhase.PLAYING,
            subject.state.value.phase
        )

        // The replacement deadline is 2 minutes from t=30s, so it passes at t=150s.
        harness.tick(Duration.parse("90s"))
        waitFor("the replacement deadline acts") {
            subject.state.value.phase == SessionPhase.COMPLETED
        }
        subject.close()
    }

    @Test
    fun duckAndTimerFadeMultiplyAndCancelIndependently() = runTest {
        val harness = TimerController().build()
        val subject = harness.subject
        try {
            subject.send(SessionCommand.Start(startRequest()))
            subject.send(SessionCommand.SleepTimer(1, 30))
            waitFor("playing") { harness.audio.playCount > 0 }
            subject.send(SessionCommand.DuckGain(0.3f))
            harness.tick(Duration.parse("45s"))
            waitFor("timer tick") { subject.state.value.sleep.remaining == Duration.parse("15s") }
            assertEquals(0.15f, harness.audio.lastMasterGain!!, 0.001f)
            subject.send(SessionCommand.DuckGain(1f))
            assertEquals(0.5f, harness.audio.lastMasterGain!!, 0.001f)
            subject.send(SessionCommand.DuckGain(0.3f))
            subject.send(SessionCommand.SleepTimer(0))
            assertEquals(0.3f, harness.audio.lastMasterGain!!, 0.001f)
        } finally {
            subject.close()
        }
    }

    @Test
    fun cancellingTheTimerRestoresTheProductGain() = runTest {
        val harness = TimerController().build()
        val subject = harness.subject
        subject.send(SessionCommand.Start(startRequest()))
        subject.send(SessionCommand.SleepTimer(minutes = 1, fadeSeconds = 30))
        waitFor("first item plays") { harness.audio.playCount >= 1 }

        harness.tick(Duration.parse("45s"))
        waitFor("the fade reaches the output") {
            val gain = harness.audio.lastMasterGain
            gain != null && gain < 1f
        }
        subject.send(SessionCommand.SleepTimer(0))
        assertEquals("cancel restores the exact product gain", 1f, harness.audio.lastMasterGain)

        // The previous deadline no longer acts.
        harness.tick(Duration.parse("30s"))
        withContext(Dispatchers.Default) { delay(50) }
        assertEquals(SessionPhase.PLAYING, subject.state.value.phase)
        subject.close()
    }

    @Test
    fun aLateDeadlineIsHonouredBeforeTheNextSoundEvenWithoutTicks() = runTest {
        val harness = TimerController().build()
        val subject = harness.subject
        subject.send(SessionCommand.Start(startRequest()))
        subject.send(SessionCommand.SleepTimer(1))
        waitFor("first item plays") { harness.audio.playCount >= 1 }

        // No tick runs: the clock jumps while the first item is still finishing.
        harness.clock.advance(Duration.parse("2m"))
        harness.audio.finishItem()
        waitFor("the pre-sound check stops the session") {
            subject.state.value.phase == SessionPhase.COMPLETED
        }
        assertEquals("no next item's PCM was submitted", 1, harness.audio.playCount)
        assertTrue(
            "the stop is attributed to the timer",
            subject.state.value.pauseReason is PauseReason.TimerExpired
        )
        subject.close()
    }
}
