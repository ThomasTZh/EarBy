package com.thomas.passiveeartraining

import android.content.ComponentName
import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import com.thomas.passiveeartraining.app.PassiveEarTrainingApp
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The sleep-timer command as a controller sends it.
 *
 * Its payload is a single bounded integer, and a value outside the supported range is refused
 * rather than clamped silently, so a bug in a caller is visible instead of passing quietly.
 */
@RunWith(AndroidJUnit4::class)
class SleepTimerCommandTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

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
        return controllerRef.get() to futureRef.get()
    }

    private fun resultFor(controller: MediaController, minutes: Int): Int {
        val latch = CountDownLatch(1)
        val code = AtomicReference<Int>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val extras = Bundle().apply { putInt("minutes", minutes) }
            val future = controller.sendCustomCommand(
                SessionCommand("passiveeartraining.sleepTimer", extras),
                Bundle.EMPTY
            )
            future.addListener(
                {
                    code.set(runCatching { future.get().resultCode }.getOrDefault(-1))
                    latch.countDown()
                },
                { command -> command.run() }
            )
        }
        assertTrue("the command must answer within 10 s", latch.await(10, TimeUnit.SECONDS))
        return code.get()
    }

    @Test
    fun aSupportedDurationIsAcceptedAndAnUnsupportedOneIsRefused() {
        val (controller, future) = connect()
        try {
            assertEquals(
                "a supported duration is accepted",
                androidx.media3.session.SessionResult.RESULT_SUCCESS,
                resultFor(controller, 30)
            )
            assertEquals(
                "an off request is accepted",
                androidx.media3.session.SessionResult.RESULT_SUCCESS,
                resultFor(controller, 0)
            )
            assertEquals(
                "a value above the maximum is refused, not clamped",
                androidx.media3.session.SessionResult.RESULT_ERROR_BAD_VALUE,
                resultFor(controller, 10_000)
            )
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                MediaController.releaseFuture(future)
            }
        }
    }
}
