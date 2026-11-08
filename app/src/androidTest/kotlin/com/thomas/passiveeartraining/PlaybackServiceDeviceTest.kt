package com.thomas.passiveeartraining

import android.content.ComponentName
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import com.thomas.passiveeartraining.app.PassiveEarTrainingApp
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device proof that the media service is the playback owner.
 *
 * It connects through a MediaController exactly as the screen does, so it verifies the published
 * service, the session binding and the advertised transport surface on real hardware. Whether the
 * audio sounds right stays a human listening item.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackServiceDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Brings the app to the foreground before a playback cycle.
     *
     * The ASUS build denies audio focus to a background process, so a controller-only test would
     * otherwise measure a focus denial instead of the output path.
     */
    private fun launchApp() {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        Thread.sleep(2_000)
    }

    /**
     * Connects a controller on the application thread.
     *
     * Media3's controller verifies it is used from that thread, so both the connection and the
     * release happen there; only the waiting is done off it.
     */
    private fun connect(): Pair<MediaController, ListenableFuture<MediaController>> {
        val futureRef = AtomicReference<ListenableFuture<MediaController>>()
        val controllerRef = AtomicReference<MediaController>()
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val serviceName = (context.applicationContext as PassiveEarTrainingApp)
                .playbackServiceClassName
            val token = SessionToken(context, ComponentName(context, serviceName))
            val future = MediaController.Builder(context, token).buildAsync()
            futureRef.set(future)
            future.addListener(
                {
                    controllerRef.set(runCatching { future.get() }.getOrNull())
                    latch.countDown()
                },
                { command -> command.run() }
            )
        }
        assertTrue("the controller must connect within 30 s", latch.await(30, TimeUnit.SECONDS))
        val controller = controllerRef.get()
        assertTrue("the controller must be usable", controller != null)
        return controller to futureRef.get()
    }

    /** Runs a read on the application thread, which Media3 requires for every controller access. */
    private fun <T> onApplicationThread(block: () -> T): T {
        val result = AtomicReference<T>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result.set(block()) }
        return result.get()
    }

    private fun release(future: ListenableFuture<MediaController>) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            MediaController.releaseFuture(future)
        }
    }

    @Test
    fun aControllerConnectsToThePackagedServiceAndReportsItsSurface() {
        val (controller, future) = connect()
        try {
            val surface = onApplicationThread {
                Triple(
                    controller.playbackState,
                    controller.availableCommands,
                    controller.mediaMetadata
                )
            }
            val (state, commands, _) = surface
            assertTrue(
                "the controller must expose a player state",
                state == Player.STATE_IDLE || state == Player.STATE_BUFFERING ||
                    state == Player.STATE_READY || state == Player.STATE_ENDED
            )
            assertTrue(
                "play/pause must be advertised",
                commands.contains(Player.COMMAND_PLAY_PAUSE)
            )
            // Unsupported operations must be absent rather than rejected at runtime.
            assertTrue(
                "seeking to a position must not be advertised",
                !commands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            )
            assertTrue(
                "setting a playback speed must not be advertised",
                !commands.contains(Player.COMMAND_SET_SPEED_AND_PITCH)
            )
            assertTrue(
                "injecting a playlist must not be advertised",
                !commands.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS)
            )
        } finally {
            release(future)
        }
    }

    @Test
    fun aPlayRequestStartsTheSessionWithoutFailingOnPrepare() {
        val (controller, future) = connect()
        try {
            // The bug this pins: a player that advertises COMMAND_PREPARE but does not handle
            // prepare() fails Media3's play path, and Start silently never happens (no audio).
            onApplicationThread { controller.play() }
            var state = Player.STATE_IDLE
            val becameActive = (1..50).any {
                Thread.sleep(100)
                state = onApplicationThread { controller.playbackState }
                state != Player.STATE_IDLE
            }
            assertTrue(
                "a play request must move the session out of idle, not fail silently",
                becameActive
            )
        } finally {
            onApplicationThread { controller.stop() }
            Thread.sleep(500)
            release(future)
        }
    }

    @Test
    fun fiveStartStopCyclesStopWithoutAPlayerError() {
        launchApp()
        val (controller, future) = connect()
        try {
            repeat(5) { cycle ->
                onApplicationThread { controller.play() }
                assertTrue(
                    "cycle ${cycle + 1} must reach a sounding state",
                    waitForState(controller) { it == Player.STATE_READY }
                )
                // Let the interval sound so the stop cuts through a note, not through silence.
                Thread.sleep(500)
                onApplicationThread { controller.stop() }
                assertTrue(
                    "cycle ${cycle + 1} must leave playback",
                    waitForState(controller) {
                        it == Player.STATE_ENDED || it == Player.STATE_IDLE
                    }
                )
                assertTrue(
                    "cycle ${cycle + 1} must not report a player error",
                    onApplicationThread { controller.playerError } == null
                )
            }
        } finally {
            onApplicationThread { controller.stop() }
            Thread.sleep(500)
            release(future)
        }
    }

    @Test
    fun fivePauseResumeCyclesKeepTheSessionUsable() {
        launchApp()
        val (controller, future) = connect()
        try {
            onApplicationThread { controller.play() }
            assertTrue(
                "the session must start before the pause cycles",
                waitForState(controller) { it == Player.STATE_READY }
            )
            repeat(5) { cycle ->
                Thread.sleep(300)
                // Media3 pause maps to the product's Stop (there is no user pause), so this
                // exercises the abrupt-stop fade-out and the following fresh Start fade-in.
                onApplicationThread { controller.pause() }
                assertTrue(
                    "cycle ${cycle + 1} must leave playback cleanly",
                    (1..100).any {
                        Thread.sleep(100)
                        // Media3 pause is the product's Stop here: the state may become ENDED,
                        // IDLE or PAUSED; what matters is that output stopped and no error exists.
                        onApplicationThread { !controller.isPlaying }
                    }
                )
                onApplicationThread { controller.play() }
                assertTrue(
                    "cycle ${cycle + 1} must resume",
                    (1..50).any {
                        Thread.sleep(100)
                        onApplicationThread { controller.isPlaying }
                    }
                )
                assertTrue(
                    "cycle ${cycle + 1} must not report a player error",
                    onApplicationThread { controller.playerError } == null
                )
            }
        } finally {
            onApplicationThread { controller.stop() }
            Thread.sleep(500)
            release(future)
        }
    }

    private fun waitForState(controller: MediaController, predicate: (Int) -> Boolean): Boolean =
        (1..300).any {
            // The first Start stages the 21 MB instrument asset and renders the first item; give it
            // up to 30 s on a cold install before treating a still-buffering session as a failure.
            Thread.sleep(100)
            predicate(onApplicationThread { controller.playbackState })
        }

    @Test
    fun metadataNeverRevealsTheAnswerBeforeItIsConsumed() {
        val (controller, future) = connect()
        try {
            val metadata = onApplicationThread { controller.mediaMetadata }
            val text = listOfNotNull(
                metadata.title,
                metadata.artist,
                metadata.description
            ).joinToString(" ")
            val answerWords = listOf(
                "minor second", "major second", "minor third", "major third", "perfect fourth",
                "tritone", "perfect fifth", "minor sixth", "major sixth", "minor seventh",
                "major seventh", "octave", "unison"
            )
            answerWords.forEach { answer ->
                assertTrue(
                    "metadata must not name an answer before it is consumed: found '$answer'",
                    !text.lowercase().contains(answer)
                )
            }
        } finally {
            release(future)
        }
    }
}
