package com.thomas.passiveeartraining

import android.app.ActivityManager
import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thomas.passiveeartraining.app.ReviewProbe
import com.thomas.passiveeartraining.training.session.SessionPhase
import com.thomas.passiveeartraining.training.session.SessionSnapshot
import com.thomas.passiveeartraining.training.settings.SettingsSnapshot
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewReliabilityDeviceTest {
    @Test fun taskRemovalStopsPlaybackAndRelaunchDoesNotRestartIt() {
        ReviewClient().use { client ->
            client.context.startActivity(
                client.context.packageManager.getLaunchIntentForPackage(
                    client.context.packageName
                )!!
            )
            Thread.sleep(500)
            client.start()
            val port = ReviewProbe.port!!
            val manager = client.context.getSystemService(ActivityManager::class.java)
            val tasks = manager.appTasks
            assertTrue("review app owns a task", tasks.isNotEmpty())
            client.main { tasks.forEach { it.finishAndRemoveTask() } }
            client.waitFor("task removal stops the output") { !port.retention().outputOpen }
            assertFalse(port.retention().rendererOpen)
        }
        ReviewClient().use { fresh ->
            fresh.context.startActivity(
                fresh.context.packageManager.getLaunchIntentForPackage(fresh.context.packageName)!!
            )
            Thread.sleep(500)
            assertTrue(
                ReviewProbe.controller!!.state.value.phase in
                    listOf(SessionPhase.IDLE, SessionPhase.COMPLETED)
            )
        }
    }

    @Test fun releasingTheControllerAndRebindingKeepsTheStoppedSessionStopped() {
        ReviewProbe.failure = null
        ReviewProbe.denyFocus = false
        val original = ReviewClient()
        var disconnected = false
        lateinit var stopped: SessionSnapshot
        lateinit var settingsBefore: SettingsSnapshot
        try {
            original.stop()
            stopped = ReviewProbe.controller!!.state.value
            assertTrue(
                "the test must start from a stopped session",
                stopped.phase in listOf(SessionPhase.IDLE, SessionPhase.COMPLETED)
            )
            settingsBefore = original.app.settings.value

            // Releasing the last controller must not touch the service session, its stopped
            // output or the stored settings.
            original.disconnect()
            disconnected = true
            original.waitFor("the stopped output is released after the disconnect") {
                !ReviewProbe.port!!.retention().outputOpen &&
                    !ReviewProbe.port!!.retention().rendererOpen
            }
            Thread.sleep(500)
            assertEquals(
                "the disconnected controller must not allow a silent restart",
                stopped.phase,
                ReviewProbe.controller!!.state.value.phase
            )
            assertEquals(
                "the session state must not move while no controller is attached",
                stopped,
                ReviewProbe.controller!!.state.value
            )
            assertEquals(
                "the settings must not change across a controller disconnect",
                settingsBefore,
                original.app.settings.value
            )
        } finally {
            if (!disconnected) original.close()
        }

        // A rebuilt controller must see the same stopped session and the same released output;
        // the rebind itself starts nothing.
        ReviewClient().use { rebound ->
            rebound.waitFor("the rebuilt controller sees the stopped session") {
                val state = ReviewProbe.controller?.state?.value
                state != null && state.phase in
                    listOf(SessionPhase.IDLE, SessionPhase.COMPLETED)
            }
            Thread.sleep(300)
            assertEquals(
                "the rebind must see the same stopped state",
                stopped,
                ReviewProbe.controller!!.state.value
            )
            assertFalse(ReviewProbe.port!!.retention().rendererOpen)
            assertFalse(ReviewProbe.port!!.retention().outputOpen)
            assertEquals(
                "the rebind must not change the settings",
                settingsBefore,
                rebound.app.settings.value
            )
        }
    }

    @Test fun memoryStabilizesAcrossRepeatedWarmStartStopAndErrorCycles() {
        ReviewClient().use { client ->
            client.stop()
            repeat(2) {
                client.start()
                client.stop()
            }
            val pss = mutableListOf<Int>()
            val native = mutableListOf<Long>()
            val evidence = File(client.context.filesDir, "review-cycles.tsv")
            evidence.writeText("cycle\tpssKiB\tnativeAllocatedBytes\n")
            try {
                repeat(10) { cycle ->
                    client.start()
                    client.stop()
                    if (cycle % 2 == 0) {
                        ReviewProbe.failure = "output"
                        client.main { client.controller.play() }
                        client.waitFor("injected output failure") {
                            ReviewProbe.controller!!.state.value.phase ==
                                SessionPhase.ERROR
                        }
                        ReviewProbe.failure = null
                        client.stop()
                    }
                    Runtime.getRuntime().gc()
                    Thread.sleep(300)
                    val memory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
                    pss += memory.totalPss
                    native += Debug.getNativeHeapAllocatedSize()
                    evidence.appendText("$cycle\t${pss.last()}\t${native.last()}\n")
                    assertFalse(ReviewProbe.port!!.retention().rendererOpen)
                    assertFalse(ReviewProbe.port!!.retention().outputOpen)
                }
                assertTrue(
                    "stopped PSS range stays within 64 MiB after warmup: $pss",
                    pss.max() - pss.min() <= 64 * 1024
                )
                assertTrue(
                    "stopped native allocated range stays within 48 MiB: $native",
                    native.max() - native.min() <= 48L * 1024 * 1024
                )
            } finally {
                ReviewProbe.failure = null
                client.stop()
            }
        }
    }
}
