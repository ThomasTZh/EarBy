package com.thomas.passiveeartraining

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.thomas.passiveeartraining.app.ReviewProbe
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.session.SessionPhase
import com.thomas.passiveeartraining.training.settings.ModeEdit
import com.thomas.passiveeartraining.training.settings.ModePayload
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** External driver owns cleanup because force-stop terminates the instrumentation process. */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ReviewProcessDeviceTest {
    @Test fun startForExternalForceStop() {
        val client = ReviewClient()
        client.context.startActivity(
            client.context.packageManager.getLaunchIntentForPackage(client.context.packageName)!!
        )
        Thread.sleep(500)
        client.stop()
        runBlocking {
            val repository = client.app.settingsRepository
            val revision = repository.settings.first().settings
                .modeSetups.getValue(TrainingKind.INTERVALS).revision
            repository.updateMode(
                ModeEdit(TrainingKind.INTERVALS, revision) { existing ->
                    val current = existing.payload as ModePayload.Intervals
                    ModePayload.Intervals(current.setup.copy(seed = 776655))
                }
            )
        }
        client.start()
        client.disconnect()
    }

    @Test fun relaunchRestoresTheSetupAndStaysStopped() {
        ReviewClient().use { client ->
            client.context.startActivity(
                client.context.packageManager.getLaunchIntentForPackage(
                    client.context.packageName
                )!!
            )
            Thread.sleep(1000)
            assertEquals(
                776655L,
                runBlocking {
                    client.app.settingsRepository.settings.first().settings.lastSetup.seed
                }
            )
            assertTrue(
                ReviewProbe.controller!!.state.value.phase in
                    listOf(SessionPhase.IDLE, SessionPhase.COMPLETED)
            )
        }
    }
}
