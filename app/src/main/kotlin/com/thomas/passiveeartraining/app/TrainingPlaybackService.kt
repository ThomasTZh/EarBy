package com.thomas.passiveeartraining.app

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.thomas.passiveeartraining.audio.RenderFormat
import com.thomas.passiveeartraining.audio.RenderRequest
import com.thomas.passiveeartraining.audio.pcm.AssetPreparer
import com.thomas.passiveeartraining.audio.pcm.AudioTrackOutput
import com.thomas.passiveeartraining.audio.pcm.ExerciseOutputWriter
import com.thomas.passiveeartraining.audio.pcm.ExerciseRenderer
import com.thomas.passiveeartraining.audio.pcm.NativePcmRenderer
import com.thomas.passiveeartraining.audio.pcm.PcmFormat
import com.thomas.passiveeartraining.audio.pcm.RenderedExercise
import com.thomas.passiveeartraining.media.AudioPipelineFactory
import com.thomas.passiveeartraining.media.PlaybackReadiness
import com.thomas.passiveeartraining.media.SwitchDestination
import com.thomas.passiveeartraining.media.TrainingMediaService
import com.thomas.passiveeartraining.training.BundledVoiceDurations
import com.thomas.passiveeartraining.training.MapVoiceDurationCatalog
import com.thomas.passiveeartraining.training.MonotonicClock
import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.VoiceDurationCatalog
import com.thomas.passiveeartraining.training.provider.CompiledExercise
import com.thomas.passiveeartraining.training.provider.ConcreteExercise
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.session.AudioPort
import com.thomas.passiveeartraining.training.session.AudioPortException
import com.thomas.passiveeartraining.training.session.ConfiguredExerciseSource
import com.thomas.passiveeartraining.training.session.DefaultSessionController
import com.thomas.passiveeartraining.training.session.PlaybackIdentity
import com.thomas.passiveeartraining.training.session.RenderSettings
import com.thomas.passiveeartraining.training.session.SessionController
import com.thomas.passiveeartraining.training.session.StartRequest
import com.thomas.passiveeartraining.training.settings.ModePayload
import com.thomas.passiveeartraining.training.settings.SettingsDiagnostic
import com.thomas.passiveeartraining.training.settings.UserSettings
import com.thomas.passiveeartraining.training.timer.CoroutineTimerScheduler
import com.thomas.passiveeartraining.training.timer.TimerScheduler
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * The packaged playback service.
 *
 * It is the only owner of the session, the player, the MediaSession and the audio lifetime, and it
 * composes the real audio pipeline: the bundled SoundFont and voice pack, the native renderer and
 * the device output. A screen disconnecting never stops an active session.
 */
@OptIn(UnstableApi::class)
open class TrainingPlaybackService : TrainingMediaService() {

    /**
     * The service-owned scope for the timer's ticks.
     *
     * It is cancelled on destruction, so no tick can survive the service even if a pending render
     * or drain still holds the session.
     */
    private val timerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun createAudioPipelineFactory(): AudioPipelineFactory =
        object : AudioPipelineFactory {
            private val delegate = RendererAudioPipelineFactory(this@TrainingPlaybackService)
            override val sampleRate: Int get() = delegate.sampleRate

            override fun create(
                context: Context,
                readiness: PlaybackReadiness,
                timerScheduler: TimerScheduler
            ): SessionController = delegate.create(context, readiness, timerScheduler)
                .also { sessionController = it }
        }

    override fun createTimerScheduler(): TimerScheduler = CoroutineTimerScheduler(timerScope)

    /**
     * The controller the base service built.
     *
     * It lets a settings edit tell an audible same-kind edit from a kind selection that the
     * switch command owns: the former reconfigures live, the latter must not replace the audible
     * kind before its boundary (M3).
     */
    @Volatile
    private var sessionController: SessionController? = null

    override fun onCreate() {
        super.onCreate()
        observeSettingsForReconfigure()
    }

    /**
     * Applies each autosaved setup edit to a running session.
     *
     * The screen writes every edit to the settings store; this collector turns each saved snapshot
     * into a session reconfigure, so a changed setting reaches the next pair without leaving the
     * setup page. The controller ignores a request equal to the active one, which makes the
     * repeated snapshots of unrelated edits free no-ops. A kind selection is a switch, not an
     * edit: the switch command commits it at the item boundary, so this collector stays silent
     * until the audible kind catches up.
     */
    private fun observeSettingsForReconfigure() {
        val app = applicationContext as PassiveEarTrainingApp
        timerScope.launch {
            app.settings.collect { snapshot ->
                val audible = sessionController?.state?.value?.activeKind
                val selected = snapshot.settings.selectedKind
                if (audible == null || audible == selected) {
                    reconfigureActiveSession()
                }
            }
        }
    }

    override fun onDestroy() {
        timerScope.cancel()
        super.onDestroy()
    }

    override fun startSleepMinutes(): Int =
        (applicationContext as PassiveEarTrainingApp).settings.value.settings.sleepTimerMinutes

    override fun startRequestFactory(): suspend () -> StartRequest = {
        // Await the first stored snapshot before constructing a Start from the saved setup.
        val snapshot = (applicationContext as PassiveEarTrainingApp).awaitSettings()
        require(
            snapshot.diagnostics.none {
                it is SettingsDiagnostic.NewerSchema ||
                    it is SettingsDiagnostic.NewerModeConfig ||
                    it is SettingsDiagnostic.Unreadable
            }
        ) { "Stored settings need attention before playback" }
        val settings = snapshot.settings
        val saved = settings.modeSetups[settings.selectedKind]
            ?: throw IllegalStateException(
                "The selected learning mode has no saved setup yet."
            )
        // The payload's own subtype decides the dispatch: each implemented kind builds the
        // Start request its provider expects, with the same seed/gain/visibility handling.
        saved.payload.toStartRequest(settings)
    }

    /** Resolves a validated switch destination from the saved settings. */
    override suspend fun switchDestination(kindId: String): SwitchDestination? {
        val snapshot = (applicationContext as PassiveEarTrainingApp).awaitSettings()
        if (snapshot.diagnostics.any {
                it is SettingsDiagnostic.NewerSchema ||
                    it is SettingsDiagnostic.NewerModeConfig ||
                    it is SettingsDiagnostic.Unreadable
            }
        ) {
            return null
        }
        val kind = TrainingKind.ofIdOrNull(kindId) ?: return null
        val saved = snapshot.settings.modeSetups[kind] ?: return null
        return SwitchDestination(
            request = saved.payload.toStartRequest(snapshot.settings),
            revision = saved.revision
        )
    }
}

/**
 * Builds the Session Start one saved mode payload describes.
 *
 * Every implemented kind uses the same seed, gain and answer-visibility handling; the payload's
 * subtype is the dispatch, so adding a kind without its provider and screens cannot compile here
 * by accident.
 */
private fun ModePayload.toStartRequest(settings: UserSettings): StartRequest {
    val request = when (this) {
        is ModePayload.Intervals -> setup.toStartRequest(
            // A stored seed reproduces its sequence; without one the session draws exactly one
            // fresh value and publishes it.
            seed = setup.seed,
            musicGain = settings.musicGain,
            voiceGain = settings.voiceGain
        )
        is ModePayload.IntervalWalk -> setup.toStartRequest(
            // A walk answers with interval phrases and direction connectors only.
            seed = setup.seed,
            musicGain = settings.musicGain,
            voiceGain = settings.voiceGain
        )
        is ModePayload.Chords -> setup.toStartRequest(
            seed = setup.seed,
            musicGain = settings.musicGain,
            voiceGain = settings.voiceGain
        )
        is ModePayload.Scales -> setup.toStartRequest(
            seed = setup.seed,
            musicGain = settings.musicGain,
            voiceGain = settings.voiceGain
        )
        is ModePayload.Notes -> setup.toStartRequest(
            // Note answers always speak scientific names; the setup's own accidental style
            // selects the sharp or flat chromatic table.
            seed = setup.seed,
            musicGain = settings.musicGain,
            voiceGain = settings.voiceGain
        )
        is ModePayload.ScaleDegrees -> setup.toStartRequest(
            // Exactly as in the note mode, the setup's own spelling selects the chromatic
            // key/answer spelling.
            seed = setup.seed,
            musicGain = settings.musicGain,
            voiceGain = settings.voiceGain
        )
        is ModePayload.Progressions -> setup.toStartRequest(
            // Progression answers never speak note names, so the setup alone builds the request.
            seed = setup.seed,
            musicGain = settings.musicGain,
            voiceGain = settings.voiceGain
        )
        is ModePayload.ContextualIntervals -> setup.toStartRequest(
            // Contextual-interval answers never speak note names either, so the setup alone
            // builds the request.
            seed = setup.seed,
            musicGain = settings.musicGain,
            voiceGain = settings.voiceGain
        )
        is ModePayload.MelodicDictation -> setup.toStartRequest(
            // Melodic-dictation answers never speak note names: the degree and directed-interval
            // formats compose only degree, direction and interval tokens, so the setup alone
            // builds the request.
            seed = setup.seed,
            musicGain = settings.musicGain,
            voiceGain = settings.voiceGain
        )
    }
    return request.copy(answerVisibility = settings.answerVisibility)
}

/**
 * Builds the session's exercise supply and audio port from the packaged assets.
 *
 * Staging happens once, lazily, on the worker that first needs it: the SoundFont is large and must
 * not be copied on the service's main thread.
 */
class RendererAudioPipelineFactory(
    private val context: Context,
    private val format: RenderFormat = RenderFormat.MONO_48K
) : AudioPipelineFactory {

    override val sampleRate: Int = format.sampleRate

    override fun create(
        context: Context,
        readiness: PlaybackReadiness,
        timerScheduler: TimerScheduler
    ): SessionController {
        val port = RendererAudioPort(this.context, format, readiness)
        val source = ConfiguredExerciseSource(voiceDurations = { BundledVoiceDurations })
        return DefaultSessionController(
            source = source,
            audio = port,
            clock = SystemMonotonicClock,
            sampleRate = format.sampleRate,
            timerScheduler = timerScheduler
        )
    }
}

/** Monotonic clock backed by the platform's elapsed realtime, which includes time asleep. */
object SystemMonotonicClock : MonotonicClock {
    override fun elapsed(): Duration =
        Duration.parse("${android.os.SystemClock.elapsedRealtimeNanos()}ns")
}

/**
 * The audio port over the native renderer and the device output.
 *
 * Rendering keeps a bounded cache of the current and next concrete exercises, keyed by the
 * exercise identity, so a replayed or navigated-to item uses exactly the PCM that was prepared.
 * Playback returns only after the device has consumed the whole item; submission alone is not
 * completion.
 */
class RendererAudioPort(
    private val context: Context,
    private val format: RenderFormat,
    private val readiness: PlaybackReadiness = object : PlaybackReadiness {
        override fun acquire(): Boolean = true
        override fun needsOutputRecovery(): Boolean = false
        override fun outputRecovered() = Unit
    }
) : AudioPort {

    private val pcmFormat = PcmFormat(format.sampleRate, format.channels)
    private var renderer: com.thomas.passiveeartraining.audio.MusicRenderer? = null

    @Volatile private var output: AudioTrackOutput? = null

    @Volatile private var writer: ExerciseOutputWriter? = null
    private var voiceFiles: Map<String, File> = emptyMap()

    /** Original MuseScore General two-note separator, independent of the main instrument font. */
    private var separatorFile: File? = null

    private val activeGeneration = AtomicLong(-1L)
    private val cache = LinkedHashMap<PlaybackIdentity, RenderedExercise>()
    private val cacheLock = Any()

    @Volatile private var masterGain = 1f

    override fun beginSession(generation: Long) {
        activeGeneration.set(generation)
    }

    /**
     * Serialises renderer preparation/release against rendering; never held during a drain wait.
     */
    private val renderLock = Any()

    /** Item frames the device has consumed for the most recent play call. */
    @Volatile
    private var itemConsumed = 0L

    override fun configure(musicGain: Float, voiceGain: Float) {
        require(musicGain in 0f..RenderRequest.MAX_GAIN)
        require(voiceGain in 0f..ExerciseRenderer.MAX_VOICE_GAIN)
    }

    private fun stageSeparator() {
        if (separatorFile != null) return
        separatorFile = AssetPreparer.stage(
            context,
            SEPARATOR_ASSET,
            SEPARATOR_BYTES,
            SEPARATOR_SHA
        )
    }

    private fun stageVoices() {
        if (voiceFiles.isNotEmpty()) return
        val staged = AssetPreparer.stageVoicePack(context, VOICE_MANIFEST)
        staged.forEach { (id, file) ->
            val info = com.thomas.passiveeartraining.audio.NativeAudioFiles.wavInfo(
                file.absolutePath
            )
            val measured = Duration.parse("${info.frameCount * 1_000_000L / info.sampleRate}us")
            require(measured == BundledVoiceDurations.durationOf(VoiceAssetId(id))) {
                "Voice duration differs from bundled metadata: $id"
            }
        }
        voiceFiles = staged
    }

    /** Frame the answer starts at, from the compiled timeline of the prepared item. */
    private fun answerStartFrame(timeline: com.thomas.passiveeartraining.training.Timeline): Long {
        val answer = timeline.events
            .filterIsInstance<com.thomas.passiveeartraining.training.VoiceEvent>()
            .minByOrNull { it.startOffset } ?: return 0L
        return (answer.startOffset.inWholeMicroseconds * pcmFormat.sampleRate + 500_000L) /
            1_000_000L
    }

    override suspend fun prepare(
        identity: PlaybackIdentity,
        exercise: ConcreteExercise,
        compiled: CompiledExercise,
        settings: RenderSettings
    ): AudioPort.Prepared {
        val timeline = compiled.timeline
        return synchronized(renderLock) {
            if (activeGeneration.get() != identity.sessionGeneration) {
                throw AudioPortException("Stale preparation: $identity")
            }
            synchronized(cacheLock) { cache[identity] }?.let {
                return preparedLayout(compiled, it)
            }
            stageVoices()
            stageSeparator()
            val engine = ensureEngine(settings)
            val requiredBytes = com.thomas.passiveeartraining.audio.pcm.FrameConverter.framesAt(
                timeline.totalDuration,
                pcmFormat.sampleRate
            ) * pcmFormat.channels * 4L
            synchronized(cacheLock) {
                while (cache.isNotEmpty() && (
                        cache.size >= CACHE_ITEMS ||
                            cache.values.sumOf { it.byteCount } + requiredBytes > 48L * 1024 * 1024
                        )
                ) {
                    cache.remove(cache.keys.first())
                }
                require(requiredBytes <= 48L * 1024 * 1024) { "PCM budget exceeded" }
            }
            renderer!!.prepare(RenderRequest(gain = settings.musicGain))
            val missing = timeline.voices.map {
                it.voiceId.value
            }.filterNot { voiceFiles.containsKey(it) }
            if (missing.isNotEmpty()) {
                throw AudioPortException(
                    "Missing voice clip(s): ${missing.joinToString()}"
                )
            }
            val rendered = engine.render(
                identity.toString(),
                exercise.sequenceIndex,
                timeline,
                voiceFiles,
                separatorFile,
                settings.musicGain
            )
            if (activeGeneration.get() != identity.sessionGeneration) {
                throw AudioPortException("Discarded stale render: $identity")
            }
            synchronized(cacheLock) {
                while (cache.size >= CACHE_ITEMS) cache.remove(cache.keys.first())
                val bytes = cache.values.sumOf { it.byteCount } + rendered.byteCount
                if (bytes > 48L * 1024 * 1024) {
                    throw AudioPortException("PCM budget exceeded: $bytes")
                }
                cache[identity] = rendered
            }
            preparedLayout(compiled, rendered)
        }
    }

    /**
     * Converts the compiled switching boundaries into item frames with the same half-up rule the
     * renderer uses, so a boundary never exceeds the rendered item.
     */
    private fun preparedLayout(
        compiled: CompiledExercise,
        rendered: RenderedExercise
    ): AudioPort.Prepared {
        fun frames(duration: Duration): Long =
            com.thomas.passiveeartraining.audio.pcm.FrameConverter.framesAt(
                duration,
                pcmFormat.sampleRate
            ).coerceIn(0L, rendered.totalFrames)
        val mandatoryEnd = frames(compiled.mandatoryEnd)
            .coerceAtLeast(0L)
        val trailingStart = frames(compiled.trailingStart)
        return AudioPort.Prepared(
            answerStartFrame = answerStartFrame(compiled.timeline),
            totalFrames = rendered.totalFrames,
            mandatoryEndFrame = mandatoryEnd.coerceAtMost(rendered.totalFrames),
            replayFrames = compiled.replays.map { range ->
                frames(range.start) until frames(range.endExclusive)
            },
            trailingStartFrame = trailingStart.coerceAtMost(rendered.totalFrames)
        )
    }

    override suspend fun requestPlaybackPermission(): Boolean {
        val generation = activeGeneration.get()
        if (generation < 0) return false
        val acquired = readiness.acquire()
        if (activeGeneration.get() != generation) {
            readiness.release()
            return false
        }
        if (acquired && (output == null || readiness.needsOutputRecovery())) {
            // The route changed; the next sound must be written to a freshly opened device.
            reopenOutput()
            readiness.outputRecovered()
        }
        return acquired
    }

    override fun setMasterGain(gain: Float) {
        masterGain = gain
        writer?.setMasterGain(gain)
    }

    /** Drops the stale device stream and opens a new one, keeping the renderer and PCM cache. */
    private fun reopenOutput() {
        synchronized(renderLock) {
            writer?.requestPause()
            output?.stop()
            val device = AudioTrackOutput(pcmFormat)
            output = device
            writer =
                ExerciseOutputWriter(device, ExerciseRenderer.DEFAULT_BLOCK_FRAMES).also {
                    it.setMasterGain(masterGain)
                }
        }
    }

    override suspend fun play(
        identity: PlaybackIdentity,
        startFrame: Long,
        boundaries: List<Long>,
        isCancelled: () -> Boolean,
        onConsumed: suspend (Long) -> Unit,
        continueAtBoundary: (Long) -> Boolean
    ): AudioPort.PlayOutcome {
        val rendered = synchronized(cacheLock) { cache[identity] }
            ?: throw AudioPortException("Nothing has been prepared for $identity")
        val activeWriter = writer ?: throw AudioPortException("The output is not open")
        val activeOutput = output ?: throw AudioPortException("The output is not open")
        require(startFrame in 0..rendered.totalFrames) {
            "Start frame must be in 0..${rendered.totalFrames} but was $startFrame"
        }
        require(
            boundaries == boundaries.sorted() &&
                boundaries.all { it in startFrame..rendered.totalFrames }
        ) {
            "Boundaries must be sorted inside $startFrame..${rendered.totalFrames}: $boundaries"
        }
        val authorizedEnd = boundaries.lastOrNull { it > startFrame } ?: rendered.totalFrames
        if (isCancelled()) return AudioPort.PlayOutcome(0L, startFrame, false)
        activeOutput.start(preBufferFrames = ExerciseRenderer.DEFAULT_BLOCK_FRAMES)
        val deviceBase = activeOutput.playedFrames()
        itemConsumed = startFrame
        onConsumed(startFrame)

        // The writer blocks while it queues blocks to a full device buffer, so the position must
        // be reported from a parallel worker: otherwise an answer that sits in the middle of
        // the item would only be announced when the whole item had already been submitted (late
        // in recognition, where the answer follows the example and the thinking silence).
        val submitted = kotlinx.coroutines.coroutineScope {
            val reporter = launch(Dispatchers.Default) {
                var last = startFrame
                while (isActive) {
                    val consumed = itemPosition(activeOutput, deviceBase, startFrame, rendered)
                    if (consumed > last) {
                        last = consumed
                        itemConsumed = consumed
                        onConsumed(consumed)
                    }
                    delay(PROGRESS_POLL_MILLIS)
                }
            }
            try {
                activeWriter.play(
                    rendered,
                    startFrame,
                    endFrameExclusive = authorizedEnd,
                    boundaries = boundaries,
                    shouldContinueAtBoundary = continueAtBoundary,
                    isCancelled = isCancelled
                )
            } finally {
                reporter.cancelAndJoin()
            }
        }

        // The writer has submitted everything it can; wait for the device to consume the stop
        // point: the unauthorized boundary, or the end of the authorized segment.
        val stopBoundary = activeWriter.stoppedAtBoundary
        val waitUntil = stopBoundary ?: authorizedEnd
        var last = itemPosition(activeOutput, deviceBase, startFrame, rendered)
            .coerceAtLeast(itemConsumed)
        while (true) {
            val consumed = itemPosition(activeOutput, deviceBase, startFrame, rendered)
                .coerceIn(last, rendered.totalFrames)
            if (consumed != last) {
                last = consumed
                itemConsumed = consumed
                onConsumed(consumed)
            }
            if (last >= waitUntil || isCancelled()) break
            delay(PROGRESS_POLL_MILLIS)
        }
        itemConsumed = last
        val completed = !isCancelled() && last >= rendered.totalFrames
        return AudioPort.PlayOutcome(
            submittedFrames = submitted,
            consumedFrames = last,
            completed = completed,
            stoppedAtBoundary = stopBoundary
        )
    }

    /** Item position from the device's consumed frames relative to the start of this play call. */
    private fun itemPosition(
        activeOutput: AudioTrackOutput,
        deviceBase: Long,
        startFrame: Long,
        rendered: RenderedExercise
    ): Long {
        val deviceFrames = (activeOutput.playedFrames() - deviceBase).coerceAtLeast(0L)
        return (startFrame + deviceFrames).coerceIn(startFrame, rendered.totalFrames)
    }

    override fun consumedFrames(): Long = itemConsumed

    internal data class Retention(
        val entries: Int,
        val bytes: Long,
        val outputOpen: Boolean,
        val rendererOpen: Boolean
    )
    internal fun retention(): Retention = synchronized(renderLock) {
        synchronized(cacheLock) {
            Retention(
                cache.size,
                cache.values.sumOf { it.byteCount },
                output != null,
                renderer != null
            )
        }
    }

    override fun flush() {
        // The writer drops the queue but first plays its 8 ms anti-click fade from the cached PCM,
        // so Stop, Pause, focus loss, navigation and service teardown all cut at exact silence.
        writer?.requestPause()
    }

    override fun release() {
        activeGeneration.set(-1L)
        synchronized(renderLock) {
            var failure: Exception? = null
            fun attempt(action: () -> Unit) {
                try {
                    action()
                } catch (error: Exception) {
                    if (failure == null) failure = error else failure!!.addSuppressed(error)
                }
            }
            attempt { writer?.requestPause() }
            attempt { output?.stop() }
            attempt { renderer?.close() }
            output = null
            renderer = null
            writer = null
            synchronized(cacheLock) { cache.clear() }
            itemConsumed = 0L
            failure?.let { throw it }
        }
    }

    /** Creates only the renderer; device output is opened after focus is granted. */
    private fun ensureEngine(settings: RenderSettings): ExerciseRenderer {
        var active = renderer
        if (active == null) {
            val created = com.thomas.passiveeartraining.audio.MusicRenderer.create(format)
            try {
                val descriptor = kotlinx.serialization.json.Json.parseToJsonElement(
                    context.assets.open("soundfont/piano.json").bufferedReader().use {
                        it.readText()
                    }
                ).jsonObject
                // The sample player is the only engine; stage its EARP1 pack. The SoundFont is a
                // tracked generator input under synthNotes/ and is not packaged.
                val pack = descriptor.getValue("samplePack").jsonObject
                val font = AssetPreparer.stage(
                    context,
                    SAMPLE_PACK_ASSET,
                    pack.getValue("bytes").jsonPrimitive.long,
                    pack.getValue("sha256").jsonPrimitive.content
                )
                created.loadSoundFont(
                    font.absolutePath,
                    com.thomas.passiveeartraining.audio.ProgramSelection(PIANO_BANK, PIANO_PROGRAM)
                )
            } catch (failure: Exception) {
                try {
                    created.close()
                } catch (
                    cleanup: Exception
                ) {
                    failure.addSuppressed(cleanup)
                }
                throw failure
            }
            renderer = created
            active = created
        }
        return ExerciseRenderer(
            NativePcmRenderer(active),
            ExerciseRenderer.DEFAULT_BLOCK_FRAMES,
            voiceGain = settings.voiceGain,
            // +1.06 dB: the loudest current note peaks at 0.835, so 0.943 stays below the 0.95
            // clean target. This is a uniform trim, not compression; no per-note level change.
            outputTrim = 1.13f
        )
    }

    private companion object {
        const val SAMPLE_PACK_ASSET = "soundfont/earby-string-pack.bin"
        const val VOICE_MANIFEST = "voice/voice-pack.json"
        const val SEPARATOR_ASSET = "separator/original-separator.wav"
        const val SEPARATOR_BYTES = 30_798L
        const val SEPARATOR_SHA =
            "ed935306de57c8466d7d4a096817624d945ac0858771251adbc2d741c8ac96a6"
        const val PIANO_BANK = 0
        const val PIANO_PROGRAM = 0

        /** Current and next only, matching the roadmap's bounded PCM budget. */
        const val CACHE_ITEMS = 2

        /** How often the device position is sampled while waiting for the item to drain. */
        const val PROGRESS_POLL_MILLIS = 50L
    }
}
