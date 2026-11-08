package com.thomas.passiveeartraining.app

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.thomas.passiveeartraining.audio.RenderFormat
import com.thomas.passiveeartraining.media.AudioPipelineFactory
import com.thomas.passiveeartraining.media.Interruption
import com.thomas.passiveeartraining.media.PlaybackReadiness
import com.thomas.passiveeartraining.training.BundledVoiceDurations
import com.thomas.passiveeartraining.training.provider.CompiledExercise
import com.thomas.passiveeartraining.training.provider.ConcreteExercise
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.session.AudioPort
import com.thomas.passiveeartraining.training.session.AudioPortException
import com.thomas.passiveeartraining.training.session.ConfiguredExerciseSource
import com.thomas.passiveeartraining.training.session.DefaultSessionController
import com.thomas.passiveeartraining.training.session.ExerciseSource
import com.thomas.passiveeartraining.training.session.PlaybackIdentity
import com.thomas.passiveeartraining.training.session.RenderSettings
import com.thomas.passiveeartraining.training.session.SessionController
import com.thomas.passiveeartraining.training.timer.TimerScheduler

/** Review-only hooks are absent from the regular/debug/release APKs. */
object ReviewProbe {
    @Volatile var controller: SessionController? = null

    @Volatile var port: RendererAudioPort? = null

    @Volatile var service: ReviewPlaybackService? = null

    @Volatile var fontScale = 1f

    @Volatile var denyFocus = false

    @Volatile var failure: String? = null
}

class ReviewApplication : PassiveEarTrainingApp() {
    override fun activityBaseContext(base: Context): Context = base.createConfigurationContext(
        android.content.res.Configuration(base.resources.configuration).apply {
            fontScale = ReviewProbe.fontScale
        }
    )
    override val playbackServiceClassName: String get() = ReviewPlaybackService::class.java.name
}

@androidx.annotation.OptIn(UnstableApi::class)
class ReviewPlaybackService : TrainingPlaybackService() {
    override fun onCreate() {
        ReviewProbe.service = this
        super.onCreate()
    }
    override fun onDestroy() {
        try {
            super.onDestroy()
        } finally {
            ReviewProbe.service = null
        }
    }
    fun resourcesHeld() = playbackResourcesHeld()
    fun inject(interruption: Interruption) = onInterruption(interruption)

    override fun createAudioPipelineFactory(): AudioPipelineFactory =
        object : AudioPipelineFactory {
            override val sampleRate = 48_000
            override fun create(
                context: Context,
                readiness: PlaybackReadiness,
                timerScheduler: TimerScheduler
            ): SessionController {
                val permission = object : PlaybackReadiness by readiness {
                    override fun acquire() = !ReviewProbe.denyFocus && readiness.acquire()
                }
                val realPort = RendererAudioPort(context, RenderFormat.MONO_48K, permission)
                ReviewProbe.port = realPort
                val realSource =
                    ConfiguredExerciseSource(voiceDurations = { BundledVoiceDurations })
                val source = object : ExerciseSource by realSource {
                    override fun next(): ConcreteExercise {
                        if (ReviewProbe.failure == "generation") {
                            error("injected generation failure")
                        }
                        return realSource.next()
                    }
                    override fun compile(
                        exercise: ConcreteExercise,
                        recipe: RecipeDefinition
                    ): CompiledExercise {
                        if (ReviewProbe.failure == "compilation") {
                            error("injected compilation failure")
                        }
                        return realSource.compile(exercise, recipe)
                    }
                }
                val audio = object : AudioPort by realPort {
                    override suspend fun prepare(
                        identity: PlaybackIdentity,
                        exercise: ConcreteExercise,
                        compiled: CompiledExercise,
                        settings: RenderSettings
                    ): AudioPort.Prepared {
                        if (ReviewProbe.failure == "rendering") {
                            throw AudioPortException("injected render failure")
                        }
                        return realPort.prepare(identity, exercise, compiled, settings)
                    }
                    override suspend fun play(
                        identity: PlaybackIdentity,
                        startFrame: Long,
                        boundaries: List<Long>,
                        isCancelled: () -> Boolean,
                        onConsumed: suspend (Long) -> Unit,
                        continueAtBoundary: (Long) -> Boolean
                    ): AudioPort.PlayOutcome {
                        if (ReviewProbe.failure == "output") {
                            throw AudioPortException("injected output failure")
                        }
                        return realPort.play(
                            identity,
                            startFrame,
                            boundaries,
                            isCancelled,
                            onConsumed,
                            continueAtBoundary
                        )
                    }
                }
                return DefaultSessionController(
                    source,
                    audio,
                    SystemMonotonicClock,
                    timerScheduler = timerScheduler
                ).also { ReviewProbe.controller = it }
            }
        }
}
