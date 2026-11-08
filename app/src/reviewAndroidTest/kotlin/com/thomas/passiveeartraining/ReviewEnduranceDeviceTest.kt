package com.thomas.passiveeartraining

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.UiDevice
import com.thomas.passiveeartraining.app.ReviewProbe
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.session.SessionCommand as DomainCommand
import com.thomas.passiveeartraining.training.session.SessionPhase
import com.thomas.passiveeartraining.training.settings.ModePayload
import com.thomas.passiveeartraining.training.settings.UserSettings
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M11.4 real elapsed-time acceptance: no test-owned wake lock, no virtual deadline.
 *
 * Warmup: every provider is started and stopped once before the measured run and recorded in
 * `review-endurance-warmup.tsv`; it is not part of the 30 minutes.
 *
 * Measured run: a real 30-minute sleep deadline with the display off and a declared mode
 * schedule: the nine modes in three-minute slices, then progressions and melody alternating.
 * Every request and commit is recorded with its elapsed time in `review-endurance-switches.tsv`;
 * the same deadline must stay visible and never reset upward.
 *
 * Watchdog: 40 minutes (warmup <= 3, measured 30, terminal checks <= 7). The production sleep
 * deadline tolerance observed here stays within two seconds; the harness memory guard in
 * `scripts/review-results.py` needs 25 samples inside 180..1740 s spanning 24 minutes.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ReviewEnduranceDeviceTest {
    @Test(timeout = 40 * 60_000L)
    fun thirtyMinutesBackgroundWithScreenOffEndsAtTheSleepDeadline() {
        ReviewClient().use { client ->
            val device = UiDevice.getInstance(client.instrumentation)
            val power = client.context.getSystemService(android.os.PowerManager::class.java)
            val evidence = File(client.context.filesDir, "review-endurance.tsv")
            val warmupEvidence = File(client.context.filesDir, "review-endurance-warmup.tsv")
            val switchEvidence = File(client.context.filesDir, "review-endurance-switches.tsv")
            evidence.writeText(
                "elapsedMs\tphase\tplayedMs\tremainingMs\tpcmEntries\tpcmBytes\tinteractive\n"
            )
            warmupEvidence.writeText("mode\telapsedMs\tphase\n")
            switchEvidence.writeText("event\tmode\telapsedMs\tplayedMs\tremainingMs\n")
            client.stop()
            runBlocking { client.app.settingsRepository.resetToDefaults() }
            client.context.startActivity(
                client.context.packageManager.getLaunchIntentForPackage(
                    client.context.packageName
                )!!
            )
            Thread.sleep(1500)

            // Warmup: every provider path once, recorded separately from the measured run.
            for (kind in WARMUP_ORDER) {
                runBlocking {
                    client.app.settingsRepository.update { it.copy(selectedKind = kind) }
                }
                client.waitFor("$kind selected for warmup") {
                    client.app.settings.value.settings.selectedKind == kind
                }
                client.start()
                client.waitFor("$kind warmup playing", 30_000) {
                    val state = ReviewProbe.controller!!.state.value
                    state.phase == SessionPhase.PLAYING && state.consumedFrames > 0
                }
                client.stop()
                warmupEvidence.appendText(
                    "$kind\t${SystemClock.elapsedRealtime()}\tstopped\n"
                )
            }
            runBlocking {
                client.app.settingsRepository.update {
                    it.copy(selectedKind = TrainingKind.INTERVALS)
                }
            }
            client.waitFor("intervals selected for the measured run") {
                client.app.settings.value.settings.selectedKind == TrainingKind.INTERVALS
            }

            client.timer(30)
            val started = SystemClock.elapsedRealtime()
            var lastRemaining = 30 * 60_000L
            var lastPlayed = 0L
            var lastSample = 0L
            var nextSchedule = 0
            try {
                client.start()
                device.pressHome()
                device.sleep()
                client.waitFor("display off", 10_000) { !power.isInteractive }
                while (SystemClock.elapsedRealtime() - started < 30 * 60_000L) {
                    val elapsed = SystemClock.elapsedRealtime() - started
                    // Declared schedule: request the next mode when its slice starts.
                    if (nextSchedule < SCHEDULE.size && elapsed >= SCHEDULE[nextSchedule].first) {
                        val (_, kind) = SCHEDULE[nextSchedule]
                        val requestedElapsed = SystemClock.elapsedRealtime() - started
                        runBlocking {
                            client.app.settingsRepository.update { it.copy(selectedKind = kind) }
                        }
                        // The service switches on an explicit command, exactly as the app's
                        // drawer does; a settings edit alone does not reach the session.
                        val saved = client.app.settings.value
                        val destination = startRequestFor(saved.settings, kind)
                        val revision = saved.settings.modeSetups.getValue(kind).revision
                        runBlocking {
                            ReviewProbe.controller!!.send(
                                DomainCommand.SwitchMode(destination, revision)
                            )
                        }
                        val requested = ReviewProbe.controller!!.state.value
                        switchEvidence.appendText(
                            "requested\t$kind\t$requestedElapsed\t" +
                                "${requested.playedDuration.inWholeMilliseconds}\t" +
                                "${requested.sleep.remaining?.inWholeMilliseconds}\n"
                        )
                        client.waitFor("$kind pending", 15_000) {
                            val state = ReviewProbe.controller!!.state.value
                            // A switch requested exactly at a boundary may publish and commit in
                            // one step, so the active kind also satisfies the request.
                            state.pendingKind == kind || state.activeKind == kind
                        }
                        client.waitFor("$kind committed", 120_000) {
                            val state = ReviewProbe.controller!!.state.value
                            state.activeKind == kind && state.pendingKind == null
                        }
                        val committed = ReviewProbe.controller!!.state.value
                        switchEvidence.appendText(
                            "committed\t$kind\t${SystemClock.elapsedRealtime() - started}\t" +
                                "${committed.playedDuration.inWholeMilliseconds}\t" +
                                "${committed.sleep.remaining?.inWholeMilliseconds}\n"
                        )
                        assertTrue(
                            "the deadline stays visible after switching to $kind",
                            (committed.sleep.remaining?.inWholeMilliseconds ?: 0) > 0
                        )
                        assertNull("no pending mode after $kind committed", committed.pendingKind)
                        nextSchedule++
                    }

                    val snapshot = ReviewProbe.controller!!.state.value
                    val retention = ReviewProbe.port!!.retention()
                    if (elapsed - lastSample >= SAMPLE_INTERVAL_MS) {
                        evidence.appendText(
                            "$elapsed\t${snapshot.phase}\t" +
                                "${snapshot.playedDuration.inWholeMilliseconds}\t" +
                                "${snapshot.sleep.remaining?.inWholeMilliseconds}\t" +
                                "${retention.entries}\t${retention.bytes}\t" +
                                "${power.isInteractive}\n"
                        )
                        lastSample = elapsed
                        if (snapshot.phase == SessionPhase.PLAYING) {
                            val remaining = snapshot.sleep.remaining?.inWholeMilliseconds
                                ?: error("the measured run must keep its sleep deadline visible")
                            assertTrue(
                                "the deadline never resets upward ($lastRemaining -> $remaining)",
                                remaining <= lastRemaining + 2_000L
                            )
                            lastRemaining = remaining
                        }
                    }
                    assertFalse("display stays off", power.isInteractive)
                    assertTrue(
                        "consumed progress is monotonic",
                        snapshot.playedDuration.inWholeMilliseconds >= lastPlayed
                    )
                    if (elapsed >
                        10_000
                    ) {
                        assertTrue(
                            "background consumed progress advances",
                            snapshot.playedDuration.inWholeMilliseconds > lastPlayed
                        )
                    }
                    lastPlayed = snapshot.playedDuration.inWholeMilliseconds
                    assertTrue(retention.entries <= 2 && retention.bytes <= 48L * 1024 * 1024)
                    if (elapsed <
                        30 * 60_000L - 1000
                    ) {
                        // A committed mode switch prepares its first item, so PREPARING is a
                        // legitimate running state between switches; only stopped/error phases
                        // fail the measured run.
                        assertTrue(
                            "the session keeps running ($snapshot)",
                            snapshot.phase == SessionPhase.PLAYING ||
                                snapshot.phase == SessionPhase.PREPARING
                        )
                    }
                    Thread.sleep(minOf(10_000L, (30 * 60_000L - elapsed).coerceAtLeast(1)))
                }
                client.waitFor("real timer terminal state", 10_000) {
                    ReviewProbe.controller!!.state.value.phase ==
                        SessionPhase.COMPLETED
                }
                assertTrue(
                    "timer stops close to real deadline",
                    SystemClock.elapsedRealtime() - started <= 30 * 60_000L + 10_000
                )
                val terminal = ReviewProbe.controller!!.state.value
                assertNull("no pending mode at the terminal state", terminal.pendingKind)
                assertTrue(
                    "the deadline is released or expired at the terminal state",
                    (terminal.sleep.remaining?.inWholeMilliseconds ?: 0L) <= 0L
                )
                client.waitFor("released output, renderer, wake and focus", 10_000) {
                    val retention = ReviewProbe.port!!.retention()
                    !retention.outputOpen && !retention.rendererOpen &&
                        ReviewProbe.service!!.resourcesHeld() == (false to false)
                }
                val releasedPlayed = terminal.playedDuration.inWholeMilliseconds
                Thread.sleep(5000)
                val after = ReviewProbe.controller!!.state.value
                assertEquals(SessionPhase.COMPLETED, after.phase)
                assertEquals(
                    "no stale restart advances playback after the deadline",
                    releasedPlayed,
                    after.playedDuration.inWholeMilliseconds
                )
                evidence.appendText(
                    "PASS\t${SystemClock.elapsedRealtime() - started}\t" +
                        "terminal resources released; no pending mode; no stale restart\n"
                )
            } finally {
                client.stop()
                device.wakeUp()
            }
        }
    }

    private companion object {
        /** Every provider is warmed once; the measured run then starts on intervals. */
        val WARMUP_ORDER = listOf(
            TrainingKind.INTERVALS,
            TrainingKind.INTERVAL_WALK,
            TrainingKind.CHORDS,
            TrainingKind.SCALES,
            TrainingKind.NOTE_IDENTIFICATION,
            TrainingKind.SCALE_DEGREES,
            TrainingKind.CHORD_PROGRESSIONS,
            TrainingKind.INTERVALS_IN_CONTEXT,
            TrainingKind.MELODIC_DICTATION
        )

        /** The declared measured schedule: three-minute slices, then alternating. */
        val SCHEDULE: List<Pair<Long, TrainingKind>> = listOf(
            3 * 60_000L to TrainingKind.CHORDS,
            6 * 60_000L to TrainingKind.SCALES,
            9 * 60_000L to TrainingKind.NOTE_IDENTIFICATION,
            12 * 60_000L to TrainingKind.SCALE_DEGREES,
            15 * 60_000L to TrainingKind.CHORD_PROGRESSIONS,
            18 * 60_000L to TrainingKind.INTERVALS_IN_CONTEXT,
            21 * 60_000L to TrainingKind.MELODIC_DICTATION,
            24 * 60_000L to TrainingKind.INTERVAL_WALK,
            27 * 60_000L to TrainingKind.CHORD_PROGRESSIONS
        )

        /** One evidence sample per minute keeps the harness guard's cadence. */
        const val SAMPLE_INTERVAL_MS = 60_000L
    }
}

/**
 * Builds the destination Start request for [kind] from the saved settings, exactly as the app's
 * dispatch does before sending a switch command.
 */
private fun startRequestFor(settings: UserSettings, kind: TrainingKind) = when (
    val payload = settings.modeSetups.getValue(kind).payload
) {
    is ModePayload.Intervals -> payload.setup.toStartRequest(
        seed = payload.setup.seed,
        musicGain = settings.musicGain,
        voiceGain = settings.voiceGain
    )
    is ModePayload.IntervalWalk -> payload.setup.toStartRequest(
        seed = payload.setup.seed,
        musicGain = settings.musicGain,
        voiceGain = settings.voiceGain
    )
    is ModePayload.Chords -> payload.setup.toStartRequest(
        seed = payload.setup.seed,
        musicGain = settings.musicGain,
        voiceGain = settings.voiceGain
    )
    is ModePayload.Scales -> payload.setup.toStartRequest(
        seed = payload.setup.seed,
        musicGain = settings.musicGain,
        voiceGain = settings.voiceGain
    )
    is ModePayload.Notes -> payload.setup.toStartRequest(
        seed = payload.setup.seed,
        musicGain = settings.musicGain,
        voiceGain = settings.voiceGain
    )
    is ModePayload.ScaleDegrees -> payload.setup.toStartRequest(
        seed = payload.setup.seed,
        musicGain = settings.musicGain,
        voiceGain = settings.voiceGain
    )
    is ModePayload.Progressions -> payload.setup.toStartRequest(
        seed = payload.setup.seed,
        musicGain = settings.musicGain,
        voiceGain = settings.voiceGain
    )
    is ModePayload.ContextualIntervals -> payload.setup.toStartRequest(
        seed = payload.setup.seed,
        musicGain = settings.musicGain,
        voiceGain = settings.voiceGain
    )
    is ModePayload.MelodicDictation -> payload.setup.toStartRequest(
        seed = payload.setup.seed,
        musicGain = settings.musicGain,
        voiceGain = settings.voiceGain
    )
}
