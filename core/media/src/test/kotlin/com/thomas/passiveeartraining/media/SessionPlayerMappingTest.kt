package com.thomas.passiveeartraining.media

import com.thomas.passiveeartraining.training.session.AvailableControls
import com.thomas.passiveeartraining.training.session.PauseReason
import com.thomas.passiveeartraining.training.session.SessionError
import com.thomas.passiveeartraining.training.session.SessionId
import com.thomas.passiveeartraining.training.session.SessionPhase
import com.thomas.passiveeartraining.training.session.SessionSnapshot
import kotlin.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Builds a snapshot with only the fields a test cares about spelled out. */
private fun snapshot(
    phase: SessionPhase,
    controls: AvailableControls = allControlsOff(),
    consumedFrames: Long = 0L,
    totalFrames: Long = 0L,
    answerVisible: Boolean = false,
    error: SessionError? = null,
    pauseReason: PauseReason? = null
): SessionSnapshot = SessionSnapshot(
    session = SessionId(1L),
    phase = phase,
    itemIndex = 0,
    answerVisible = answerVisible,
    consumedFrames = consumedFrames,
    totalFrames = totalFrames,
    playedDuration = Duration.ZERO,
    pauseReason = pauseReason,
    error = error,
    controls = controls,
    hasNext = true,
    hasPrevious = true
)

private fun allControlsOff() = AvailableControls(
    canStart = false,
    canPause = false,
    canResume = false,
    canNext = false,
    canPrevious = false,
    canRepeat = false,
    canStop = false
)

class SessionPlayerMappingTest {

    private val playable = AvailableControls(
        canStart = false,
        canPause = true,
        canResume = false,
        canNext = true,
        canPrevious = true,
        canRepeat = true,
        canStop = true
    )

    @Test
    fun preparingBuffersAndPlayingIsReady() {
        val preparing = SessionPlayerMapping.project(snapshot(SessionPhase.PREPARING), 48_000)
        assertEquals(SessionPlayerMapping.PlaybackStateCodes.BUFFERING, preparing.playbackState)
        assertFalse("preparing is not playing yet", preparing.isPlaying)
        assertEquals(
            "an unknown length is reported as unset",
            SessionPlayerMapping.TIME_UNSET,
            preparing.totalDurationMs
        )

        val playing = SessionPlayerMapping.project(snapshot(SessionPhase.PLAYING, playable), 48_000)
        assertEquals(SessionPlayerMapping.PlaybackStateCodes.READY, playing.playbackState)
        assertTrue(playing.playWhenReady)
        assertTrue(playing.isPlaying)
        assertNull("a healthy session reports no error", playing.errorCode)
    }

    @Test
    fun pausedIsReadyWithPlayWhenReadyFalse() {
        val paused = SessionPlayerMapping.project(snapshot(SessionPhase.PAUSED, playable), 48_000)
        assertEquals(SessionPlayerMapping.PlaybackStateCodes.READY, paused.playbackState)
        assertFalse("a paused item is ready but not playing", paused.playWhenReady)
        assertFalse(paused.isPlaying)
    }

    @Test
    fun completedEndsAndErrorGoesIdleWithAnException() {
        val completed = SessionPlayerMapping.project(snapshot(SessionPhase.COMPLETED), 48_000)
        assertEquals(SessionPlayerMapping.PlaybackStateCodes.ENDED, completed.playbackState)

        val failure = SessionError(SessionError.ErrorCode.OUTPUT_FAILURE, "AudioTrack failed")
        val error = SessionPlayerMapping.project(
            snapshot(SessionPhase.ERROR, error = failure),
            48_000
        )
        assertEquals(
            "a failed session is idle so Play is offered again",
            SessionPlayerMapping.PlaybackStateCodes.IDLE,
            error.playbackState
        )
        assertEquals(SessionPlayerMapping.ErrorCodes.AUDIO_TRACK_INIT_FAILED, error.errorCode)
        assertEquals("AudioTrack failed", error.errorMessage)
    }

    @Test
    fun durationAndPositionComeFromFrames() {
        val state = SessionPlayerMapping.project(
            snapshot(
                SessionPhase.PLAYING,
                playable,
                consumedFrames = 144_000L,
                totalFrames = 307_200L
            ),
            48_000
        )
        assertEquals("6.4 s consumed", 3_000L, state.positionMs)
        assertEquals("307200 frames at 48 kHz is 6.4 s", 6_400L, state.totalDurationMs)
        assertEquals(state.totalDurationMs, state.bufferedMs)
    }

    @Test
    fun onlySupportedCommandsAreAdvertised() {
        val state = SessionPlayerMapping.project(snapshot(SessionPhase.PLAYING, playable), 48_000)
        assertTrue(state.canPause)
        assertTrue(state.canSeekToNext)
        assertTrue(state.canSeekToPrevious)
        assertTrue(state.canRepeat)
        assertTrue(state.canStop)
        assertFalse(
            "there is no seek-to-position operation",
            state.canPlay == false && state.canStop == false
        )
    }

    @Test
    fun commandAvailabilitySurvivesQueueBoundaries() {
        // The queue is generated, so next and previous are offered even at a boundary; the snapshot
        // is what decides, and the mapping must not second-guess it.
        val atStart = SessionPlayerMapping.project(
            snapshot(SessionPhase.PLAYING, playable).copy(hasPrevious = false, hasNext = true),
            48_000
        )
        assertTrue("next stays available at the start", atStart.canSeekToNext)
        assertTrue("previous stays available at the start", atStart.canSeekToPrevious)
    }

    @Test
    fun notificationNeverRevealsAnUnconsumedAnswer() {
        val listening = SessionPlayerMapping.notificationContent(snapshot(SessionPhase.PLAYING))
        assertEquals("Interval recognition", listening.title)
        assertEquals("Listen", listening.subtitle)
        assertFalse(listening.showAnswer)

        val thinking = SessionPlayerMapping.notificationContent(
            snapshot(SessionPhase.PAUSED, playable)
        )
        assertEquals("Paused - Think", thinking.subtitle)
        assertFalse("a paused session must not reveal the answer", thinking.showAnswer)
    }

    @Test
    fun notificationShowsTheAnswerOnlyOnceConsumed() {
        val answered = SessionPlayerMapping.notificationContent(
            snapshot(SessionPhase.PLAYING, playable, answerVisible = true)
        )
        assertTrue(answered.showAnswer)
        assertEquals("Answer", answered.subtitle)
    }

    @Test
    fun everyPhaseProducesNotificationText() {
        SessionPhase.entries.forEach { phase ->
            val content = SessionPlayerMapping.notificationContent(snapshot(phase))
            assertTrue("$phase must have a title", content.title.isNotBlank())
            assertTrue("$phase must have a subtitle", content.subtitle.isNotBlank())
            if (phase != SessionPhase.PLAYING) {
                assertFalse("$phase must not reveal the answer", content.showAnswer)
            }
        }
    }

    @Test
    fun pauseReasonIsExplainedWhenPresent() {
        assertNull(SessionPlayerMapping.pauseReasonText(snapshot(SessionPhase.PLAYING)))
        assertTrue(
            SessionPlayerMapping.pauseReasonText(
                snapshot(SessionPhase.PAUSED, pauseReason = PauseReason.Focus)
            )!!
                .contains("focus")
        )
        assertTrue(
            SessionPlayerMapping.pauseReasonText(
                snapshot(SessionPhase.PAUSED, pauseReason = PauseReason.RouteChange)
            )!!
                .contains("device")
        )
    }

    @Test
    fun frameAndMillisecondConversionRoundTrips() {
        assertEquals(1_000L, SessionPlayerMapping.framesToMs(48_000L, 48_000))
        assertEquals(48_000L, SessionPlayerMapping.msToFrames(1_000L, 48_000))
        // Integer division truncates, so a reported position never runs ahead of the audio:
        // 479 frames is 9.98 ms and reports 9 ms, while exactly 480 frames reports 10 ms.
        assertEquals(9L, SessionPlayerMapping.framesToMs(479L, 48_000))
        assertEquals(10L, SessionPlayerMapping.framesToMs(480L, 48_000))
    }

    @Test
    fun anInvalidSampleRateIsRejected() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            SessionPlayerMapping.project(snapshot(SessionPhase.PLAYING), 0)
        }
    }
}
