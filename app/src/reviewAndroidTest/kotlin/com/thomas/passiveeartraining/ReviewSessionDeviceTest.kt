package com.thomas.passiveeartraining

import android.content.ComponentName
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.common.util.concurrent.ListenableFuture
import com.thomas.passiveeartraining.app.PassiveEarTrainingApp
import com.thomas.passiveeartraining.app.ReviewProbe
import com.thomas.passiveeartraining.media.ANSWER_TEXT_EXTRA
import com.thomas.passiveeartraining.media.Interruption
import com.thomas.passiveeartraining.media.SESSION_STATE_ACTION
import com.thomas.passiveeartraining.media.STATE_ANSWER_TEXT
import com.thomas.passiveeartraining.music.DiatonicChord
import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.ProgressionVoicingMode
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.NoteEvent
import com.thomas.passiveeartraining.training.ProgressionAnswerFormat
import com.thomas.passiveeartraining.training.ProgressionDefinition
import com.thomas.passiveeartraining.training.VoiceEvent
import com.thomas.passiveeartraining.training.context.ContextKind
import com.thomas.passiveeartraining.training.provider.CompiledExercise
import com.thomas.passiveeartraining.training.provider.ConcreteExercise
import com.thomas.passiveeartraining.training.provider.GroupRole
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.provider.VoiceVocabulary
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.session.SessionCommand as DomainCommand
import com.thomas.passiveeartraining.training.session.SessionPhase
import com.thomas.passiveeartraining.training.session.SessionSnapshot
import com.thomas.passiveeartraining.training.settings.AnswerVisibility
import com.thomas.passiveeartraining.training.settings.LastSetup
import com.thomas.passiveeartraining.training.settings.ModeEdit
import com.thomas.passiveeartraining.training.settings.ModePayload
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Main-thread Media3 operations with bounded off-thread waits. */
internal class ReviewClient : AutoCloseable {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val app = context.applicationContext as PassiveEarTrainingApp
    private val future: ListenableFuture<MediaController> = main {
        MediaController.Builder(
            context,
            SessionToken(
                context,
                ComponentName(context, app.playbackServiceClassName)
            )
        ).buildAsync()
    }
    val controller: MediaController = future.get(30, TimeUnit.SECONDS)

    fun <T> main(block: () -> T): T {
        val result = AtomicReference<T>()
        instrumentation.runOnMainSync { result.set(block()) }
        return result.get()
    }
    fun waitFor(detail: String, timeout: Long = 30_000, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            Thread.sleep(50)
        }
        assertTrue("Timed out: $detail; ${ReviewProbe.controller?.state?.value}", predicate())
    }
    fun start() {
        main { controller.play() }
        waitFor("playing") {
            ReviewProbe.controller?.state?.value?.phase == SessionPhase.PLAYING &&
                (ReviewProbe.controller?.state?.value?.consumedFrames ?: 0) > 0
        }
    }
    fun stop() {
        main { controller.stop() }
        waitFor("stopped") {
            ReviewProbe.controller?.state?.value?.phase in
                listOf(SessionPhase.IDLE, SessionPhase.COMPLETED)
        }
    }
    fun timer(minutes: Int) {
        val result = main {
            controller.sendCustomCommand(
                SessionCommand(
                    "passiveeartraining.sleepTimer",
                    Bundle().apply { putInt("minutes", minutes) }
                ),
                Bundle.EMPTY
            )
        }.get(10, TimeUnit.SECONDS)
        assertEquals(0, result.resultCode)
    }
    fun answer(): String? = main {
        controller.sendCustomCommand(
            SessionCommand(SESSION_STATE_ACTION, Bundle.EMPTY),
            Bundle.EMPTY
        )
    }.get(10, TimeUnit.SECONDS).extras.getString(STATE_ANSWER_TEXT)
    fun disconnect() {
        main { MediaController.releaseFuture(future) }
    }
    override fun close() {
        try {
            main { controller.stop() }
        } finally {
            main { MediaController.releaseFuture(future) }
        }
    }
}

@RunWith(AndroidJUnit4::class)
class ReviewSessionDeviceTest {
    private lateinit var client: ReviewClient
    private lateinit var activity: ActivityScenario<MainActivity>

    @Before fun prepare() {
        check(
            InstrumentationRegistry.getInstrumentation().targetContext.packageName.endsWith(
                ".review"
            )
        )
        // The service-only harness leaves the process at a bound-service state. Some OEM audio
        // policies deny focus there, so keep a real Activity resumed for the session tests.
        activity = ActivityScenario.launch(MainActivity::class.java)
        client = ReviewClient()
        client.stop()
        ReviewProbe.failure = null
        ReviewProbe.denyFocus = false
        runBlocking {
            client.app.settingsRepository.resetToDefaults()
            kotlinx.coroutines.withTimeout(5_000) {
                client.app.settings.first {
                    it.settings.selectedKind == TrainingKind.INTERVALS
                }
            }
        }
        client.timer(0)
    }

    @After fun cleanup() {
        ReviewProbe.failure = null
        ReviewProbe.denyFocus = false
        client.close()
        activity.close()
    }

    @Test fun hiddenAnswersStayOutOfSnapshotsQueriesAndMetadataThenToggleImmediately() {
        runBlocking {
            client.app.settingsRepository.update {
                it.copy(answerVisibility = AnswerVisibility.HIDDEN)
            }
        }
        client.start()
        client.waitFor("spoken answer position") {
            (ReviewProbe.controller?.state?.value?.consumedFrames ?: 0) > 200_000
        }
        assertFalse(ReviewProbe.controller!!.state.value.answerVisible)
        assertEquals(null, ReviewProbe.controller!!.state.value.answerLabel)
        assertTrue(client.answer().isNullOrEmpty())
        assertFalse(
            client.main {
                client.controller.mediaMetadata.extras
                    ?.containsKey(ANSWER_TEXT_EXTRA) == true
            }
        )
        runBlocking {
            client.app.settingsRepository.update {
                it.copy(answerVisibility = AnswerVisibility.SHOWN_WHEN_CONSUMED)
            }
        }
        client.waitFor("answer shown after preference edit") { !client.answer().isNullOrEmpty() }
        runBlocking {
            client.app.settingsRepository.update {
                it.copy(answerVisibility = AnswerVisibility.HIDDEN)
            }
        }
        client.waitFor("answer removed after preference edit", 3000) {
            client.answer().isNullOrEmpty()
        }
        assertEquals(null, ReviewProbe.controller!!.state.value.answerLabel)
    }

    @Test fun hiddenAnswersStayHiddenForAChordSession() {
        runBlocking {
            client.app.settingsRepository.update {
                it.copy(
                    answerVisibility = AnswerVisibility.HIDDEN,
                    selectedKind = TrainingKind.CHORDS
                )
            }
        }
        client.start()
        client.waitFor("chord answer position") {
            (ReviewProbe.controller?.state?.value?.consumedFrames ?: 0) > 200_000
        }
        assertFalse(ReviewProbe.controller!!.state.value.answerVisible)
        assertEquals(null, ReviewProbe.controller!!.state.value.answerLabel)
        assertTrue(client.answer().isNullOrEmpty())
        assertFalse(
            client.main {
                client.controller.mediaMetadata.extras
                    ?.containsKey(ANSWER_TEXT_EXTRA) == true
            }
        )
        runBlocking {
            client.app.settingsRepository.update {
                it.copy(answerVisibility = AnswerVisibility.SHOWN_WHEN_CONSUMED)
            }
        }
        client.waitFor("chord answer shown after preference edit") {
            !client.answer().isNullOrEmpty()
        }
        runBlocking {
            client.app.settingsRepository.update {
                it.copy(answerVisibility = AnswerVisibility.HIDDEN)
            }
        }
        client.waitFor("chord answer removed after preference edit", 3000) {
            client.answer().isNullOrEmpty()
        }
        assertEquals(null, ReviewProbe.controller!!.state.value.answerLabel)
    }

    @Test fun hiddenAnswersStayHiddenForAScaleSession() {
        runBlocking {
            client.app.settingsRepository.update {
                it.copy(
                    answerVisibility = AnswerVisibility.HIDDEN,
                    selectedKind = TrainingKind.SCALES
                )
            }
        }
        client.start()
        client.waitFor("scale answer position") {
            // Past the answer frame of the default major/natural-minor item (example 6.3 s plus
            // the 2 s thinking pause), where a visible answer would already have been published.
            (ReviewProbe.controller?.state?.value?.consumedFrames ?: 0) > 420_000
        }
        assertFalse(ReviewProbe.controller!!.state.value.answerVisible)
        assertEquals(null, ReviewProbe.controller!!.state.value.answerLabel)
        assertTrue(client.answer().isNullOrEmpty())
        assertFalse(
            client.main {
                client.controller.mediaMetadata.extras
                    ?.containsKey(ANSWER_TEXT_EXTRA) == true
            }
        )
        runBlocking {
            client.app.settingsRepository.update {
                it.copy(answerVisibility = AnswerVisibility.SHOWN_WHEN_CONSUMED)
            }
        }
        client.waitFor("scale answer shown after preference edit") {
            !client.answer().isNullOrEmpty()
        }
        runBlocking {
            client.app.settingsRepository.update {
                it.copy(answerVisibility = AnswerVisibility.HIDDEN)
            }
        }
        client.waitFor("scale answer removed after preference edit", 3000) {
            client.answer().isNullOrEmpty()
        }
        assertEquals(null, ReviewProbe.controller!!.state.value.answerLabel)
    }

    @Test fun hiddenAnswersStayHiddenForANoteSession() {
        runBlocking {
            client.app.settingsRepository.update {
                it.copy(
                    answerVisibility = AnswerVisibility.HIDDEN,
                    selectedKind = TrainingKind.NOTE_IDENTIFICATION
                )
            }
        }
        client.start()
        client.waitFor("note answer position") {
            // Past the answer frame of the default note item (example 0.7 s plus the 2 s
            // thinking pause), where a visible answer would already have been published.
            (ReviewProbe.controller?.state?.value?.consumedFrames ?: 0) > 200_000
        }
        assertFalse(ReviewProbe.controller!!.state.value.answerVisible)
        assertEquals(null, ReviewProbe.controller!!.state.value.answerLabel)
        assertTrue(client.answer().isNullOrEmpty())
        assertFalse(
            client.main {
                client.controller.mediaMetadata.extras
                    ?.containsKey(ANSWER_TEXT_EXTRA) == true
            }
        )
        runBlocking {
            client.app.settingsRepository.update {
                it.copy(answerVisibility = AnswerVisibility.SHOWN_WHEN_CONSUMED)
            }
        }
        client.waitFor("note answer shown after preference edit") {
            !client.answer().isNullOrEmpty()
        }
        runBlocking {
            client.app.settingsRepository.update {
                it.copy(answerVisibility = AnswerVisibility.HIDDEN)
            }
        }
        client.waitFor("note answer removed after preference edit", 3000) {
            client.answer().isNullOrEmpty()
        }
        assertEquals(null, ReviewProbe.controller!!.state.value.answerLabel)
    }

    @Test fun hiddenAnswersStayHiddenForADegreeSession() {
        runBlocking {
            client.app.settingsRepository.update {
                it.copy(
                    answerVisibility = AnswerVisibility.HIDDEN,
                    selectedKind = TrainingKind.SCALE_DEGREES
                )
            }
        }
        client.waitFor("degree kind active in settings") {
            client.app.settings.value.settings.selectedKind == TrainingKind.SCALE_DEGREES
        }
        // Stop first so a session left over from an earlier test cannot answer the play command
        // with its old intervals snapshot. The following play then starts from the stored degree
        // selection.
        client.stop()
        client.start()
        client.waitFor("degree session running") {
            ReviewProbe.controller!!.state.value.activeKind == TrainingKind.SCALE_DEGREES
        }
        client.waitFor("degree answer position") {
            // Past the answer frame of the default degree item (tonic triad 0.7 s, 0.5 s context
            // gap, target 0.7 s and the 2 s thinking pause), where a visible answer would already
            // have been published.
            (ReviewProbe.controller?.state?.value?.consumedFrames ?: 0) > 200_000
        }
        assertFalse(ReviewProbe.controller!!.state.value.answerVisible)
        assertEquals(null, ReviewProbe.controller!!.state.value.answerLabel)
        assertTrue(client.answer().isNullOrEmpty())
        assertFalse(
            client.main {
                client.controller.mediaMetadata.extras
                    ?.containsKey(ANSWER_TEXT_EXTRA) == true
            }
        )
        runBlocking {
            client.app.settingsRepository.update {
                it.copy(answerVisibility = AnswerVisibility.SHOWN_WHEN_CONSUMED)
            }
        }
        client.waitFor("degree answer shown after preference edit") {
            !client.answer().isNullOrEmpty()
        }
        runBlocking {
            client.app.settingsRepository.update {
                it.copy(answerVisibility = AnswerVisibility.HIDDEN)
            }
        }
        client.waitFor("degree answer removed after preference edit", 3000) {
            client.answer().isNullOrEmpty()
        }
        assertEquals(null, ReviewProbe.controller!!.state.value.answerLabel)
    }

    @Test fun theSleepTimerRemembersTheLastChoiceAcrossSessions() {
        ReviewClient().use { client ->
            runBlocking {
                client.app.settingsRepository.update { it.copy(sleepTimerMinutes = 15) }
            }
            client.waitFor("the timer choice is observed") {
                client.app.settings.value.settings.sleepTimerMinutes == 15
            }
            client.start()
            client.waitFor("the remembered timer armed") {
                val remaining = ReviewProbe.controller!!.state.value.sleep.remaining
                remaining != null && remaining.inWholeMinutes in 14..15
            }
            client.stop()

            runBlocking {
                client.app.settingsRepository.update { it.copy(sleepTimerMinutes = 0) }
            }
            client.waitFor("the off choice is observed") {
                client.app.settings.value.settings.sleepTimerMinutes == 0
            }
            client.start()
            client.waitFor("the off choice armed no timer") {
                ReviewProbe.controller!!.state.value.sleep.remaining == null
            }
            client.stop()

            // A timer armed before the Start must survive when the stored choice is Off,
            // because the remembered value never cancels an explicit arm.
            client.timer(15)
            client.start()
            client.waitFor("the explicit timer survives the start") {
                val remaining = ReviewProbe.controller!!.state.value.sleep.remaining
                remaining != null && remaining.inWholeMinutes in 14..15
            }
            client.stop()
        }
    }

    @Test fun theDegreeContextTargetAndAnswerArriveInPhases() {
        runBlocking {
            client.app.settingsRepository.selectKind(TrainingKind.SCALE_DEGREES)
        }
        client.waitFor("degree kind active in settings") {
            client.app.settings.value.settings.selectedKind == TrainingKind.SCALE_DEGREES
        }
        // Stop first so a session left over from an earlier test cannot answer the play command
        // with its old intervals snapshot. The following play then starts from the stored degree
        // selection.
        client.stop()
        client.start()
        client.waitFor("degree session running") {
            ReviewProbe.controller!!.state.value.activeKind == TrainingKind.SCALE_DEGREES
        }
        val item = currentDegreeItem() ?: error("the running item must exist")
        val exercise = itemExercise(item)
        val provenance = exercise.provenance.generatedScaleDegree
            ?: error("the running item must be a generated scale degree")
        val compiled = itemCompiled(item)
        assertTrue(
            "every context kind sounds before the target",
            provenance.contextValues.isNotEmpty()
        )

        // Provenance plus the compiled timeline prove the phases: every context note is scheduled
        // before the target, the context gap separates them, and the spoken answer only starts
        // after the target note has ended.
        val noteEvents = compiled.timeline.events
            .filterIsInstance<NoteEvent>()
            .sortedBy { it.startOffset }
        val target = noteEvents.last()
        assertEquals(
            "the last scheduled note is the target degree",
            provenance.targetMidi,
            target.note.value
        )
        val contextNotes = noteEvents.filter { it.startOffset < target.startOffset }
        assertEquals(
            "the complete context sounds before the target",
            exercise.contextGroups.flatMap { group -> group.notes.map { it.value } },
            contextNotes.map { it.note.value }
        )
        val contextEnd = contextNotes.maxOf { it.startOffset + it.duration }
        val setup = client.app.settings.value.settings.scaleDegreeSetup
        assertEquals(
            "the configured context gap separates context and target",
            setup.contextGap,
            target.startOffset - contextEnd
        )
        val targetEnd = target.startOffset + target.duration
        val firstVoice = compiled.timeline.events
            .filterIsInstance<VoiceEvent>()
            .minBy { it.startOffset }
        assertTrue(
            "the answer clip only starts after the target note ends",
            firstVoice.startOffset >= targetEnd
        )

        // The controller state before the answer frame: nothing is revealed while the context and
        // target are the only audio.
        val early = ReviewProbe.controller!!.state.value
        assertFalse("the answer cannot be visible during the context", early.answerVisible)
        assertEquals(null, early.answerLabel)

        // The compiled answer frame is exactly the first voice event's frame (the renderer's
        // half-up conversion), so the state flips only once that frame is consumed.
        val expectedAnswerFrame =
            (firstVoice.startOffset.inWholeMicroseconds * REVIEW_SAMPLE_RATE + 500_000L) /
                1_000_000L
        assertEquals(expectedAnswerFrame, itemAnswerStartFrame(item))
        client.waitFor("degree answer consumed") {
            val state = ReviewProbe.controller!!.state.value
            state.answerVisible && state.answerLabel != null
        }
        assertEquals(
            "the revealed label is the target degree's own number",
            VoiceVocabulary.degreeNumberEntry(provenance.degree).spokenText,
            ReviewProbe.controller!!.state.value.answerLabel
        )
    }

    @Test fun anAssociationDegreeUnitFinishesBeforeASwitchCommits() {
        runBlocking {
            val repository = client.app.settingsRepository
            val revision = repository.settings.first().settings
                .modeSetups.getValue(TrainingKind.SCALE_DEGREES).revision
            repository.updateMode(
                ModeEdit(TrainingKind.SCALE_DEGREES, revision) { existing ->
                    val current = existing.payload as ModePayload.ScaleDegrees
                    ModePayload.ScaleDegrees(
                        current.setup.copy(
                            degrees = listOf(5),
                            context = ContextKind.TONIC_NOTE,
                            contextGap = kotlin.time.Duration.parse("200ms"),
                            mode = RecipeMode.ASSOCIATION,
                            thinkingSilence = kotlin.time.Duration.parse("500ms")
                        )
                    )
                }
            )
            repository.update { it.copy(selectedKind = TrainingKind.SCALE_DEGREES) }
        }
        client.waitFor("degree kind active in settings") {
            client.app.settings.value.settings.selectedKind == TrainingKind.SCALE_DEGREES
        }
        // Stop first so a session left over from an earlier test cannot answer the play command
        // with its old intervals snapshot. The following play then starts from the stored degree
        // selection.
        client.stop()
        client.start()
        client.waitFor("degrees playing in association mode") {
            val state = ReviewProbe.controller!!.state.value
            state.phase == SessionPhase.PLAYING &&
                state.activeKind == TrainingKind.SCALE_DEGREES &&
                state.consumedFrames > 0
        }
        val before = ReviewProbe.controller!!.state.value

        // Resolve the destination exactly as the service does: from the saved interval payload.
        val snapshot = client.app.settings.value
        val intervalPayload = snapshot.settings.modeSetups
            .getValue(TrainingKind.INTERVALS).payload as ModePayload.Intervals
        val destination = intervalPayload.setup.toStartRequest(
            seed = intervalPayload.setup.seed,
            musicGain = snapshot.settings.musicGain,
            voiceGain = snapshot.settings.voiceGain
        )
        val intervalsRevision = snapshot.settings.modeSetups
            .getValue(TrainingKind.INTERVALS).revision
        runBlocking {
            ReviewProbe.controller!!.send(DomainCommand.SwitchMode(destination, intervalsRevision))
        }
        client.waitFor("intervals switch pending") {
            ReviewProbe.controller!!.state.value.pendingKind == TrainingKind.INTERVALS
        }
        assertEquals(
            "the degree item stays audible while the switch waits",
            TrainingKind.SCALE_DEGREES,
            ReviewProbe.controller!!.state.value.activeKind
        )
        client.waitFor("intervals active at the boundary") {
            val state = ReviewProbe.controller!!.state.value
            state.activeKind == TrainingKind.INTERVALS && state.phase == SessionPhase.PLAYING
        }
        assertLastCommittedDegreeWasHeardInFull(before)
    }

    @Test fun hiddenAnswersStayHiddenForAProgressionSession() {
        runBlocking {
            client.app.settingsRepository.update {
                it.copy(
                    answerVisibility = AnswerVisibility.HIDDEN,
                    selectedKind = TrainingKind.CHORD_PROGRESSIONS
                )
            }
        }
        client.waitFor("progression kind active in settings") {
            client.app.settings.value.settings.selectedKind == TrainingKind.CHORD_PROGRESSIONS
        }
        client.start()
        client.waitFor("progression session running") {
            ReviewProbe.controller!!.state.value.activeKind == TrainingKind.CHORD_PROGRESSIONS
        }
        client.waitFor("progression answer position") {
            // Past the longest default progression example (four chords: 3.1 s) and its 2 s
            // thinking pause, where a visible answer would already have been published.
            (ReviewProbe.controller?.state?.value?.consumedFrames ?: 0) > 300_000
        }
        assertFalse(ReviewProbe.controller!!.state.value.answerVisible)
        assertEquals(null, ReviewProbe.controller!!.state.value.answerLabel)
        assertTrue(client.answer().isNullOrEmpty())
        assertFalse(
            client.main {
                client.controller.mediaMetadata.extras
                    ?.containsKey(ANSWER_TEXT_EXTRA) == true
            }
        )
        runBlocking {
            client.app.settingsRepository.update {
                it.copy(answerVisibility = AnswerVisibility.SHOWN_WHEN_CONSUMED)
            }
        }
        client.waitFor("progression answer shown after preference edit") {
            !client.answer().isNullOrEmpty()
        }
        runBlocking {
            client.app.settingsRepository.update {
                it.copy(answerVisibility = AnswerVisibility.HIDDEN)
            }
        }
        client.waitFor("progression answer removed after preference edit", 3000) {
            client.answer().isNullOrEmpty()
        }
        assertEquals(null, ReviewProbe.controller!!.state.value.answerLabel)
    }

    @Test fun progressionRecipesPlayTheCompleteProgression() {
        // One chord pool in C keeps every recipe short enough for a bounded wait while still
        // exercising the complete four-chord unit, including Association's second example.
        for (mode in RecipeMode.entries) {
            runBlocking {
                val repository = client.app.settingsRepository
                val revision = repository.settings.first().settings
                    .modeSetups.getValue(TrainingKind.CHORD_PROGRESSIONS).revision
                repository.updateMode(
                    ModeEdit(TrainingKind.CHORD_PROGRESSIONS, revision) { existing ->
                        val current = existing.payload as ModePayload.Progressions
                        ModePayload.Progressions(
                            current.setup.copy(
                                chordIds = listOf(
                                    DiatonicChord.I.id,
                                    DiatonicChord.II.id,
                                    DiatonicChord.V.id
                                ),
                                voicing = ProgressionVoicingMode.ROOT_POSITION,
                                answerFormat = ProgressionAnswerFormat.ROMAN,
                                mode = mode,
                                thinkingSilence = kotlin.time.Duration.parse("500ms")
                            )
                        )
                    }
                )
                repository.update { it.copy(selectedKind = TrainingKind.CHORD_PROGRESSIONS) }
            }
            client.waitFor("$mode progression selected") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.CHORD_PROGRESSIONS
            }
            // Let the service observe the edited recipe mode before the Start builds the request;
            // without this the first item can still be compiled from the previous mode.
            Thread.sleep(300)
            client.start()
            client.waitFor("$mode progression playing") {
                val state = ReviewProbe.controller!!.state.value
                state.phase == SessionPhase.PLAYING &&
                    state.activeKind == TrainingKind.CHORD_PROGRESSIONS &&
                    state.consumedFrames > 0
            }

            // The running item is one complete progression: every drawn chord sounds as one
            // full TARGET triad, never a split or truncated chord.
            val exercise = currentProgressionExercise()
            val progression = exercise.provenance.generatedProgression
                ?: error("the running item must be a generated progression")
            assertEquals(
                "$mode must schedule one group per drawn chord",
                ProgressionDefinition.CHORD_COUNT,
                exercise.groups.size
            )
            assertTrue(
                "$mode must sound every chord as one complete TARGET triad",
                exercise.groups.all { it.role == GroupRole.TARGET && it.notes.size == 3 }
            )
            assertEquals(ProgressionDefinition.CHORD_COUNT, progression.chords.size)

            // The unit runs to its boundary and the next complete progression begins, which
            // proves the whole recipe (for Association the second example) is playable.
            val firstIndex = ReviewProbe.controller!!.state.value.itemIndex
            client.waitFor("$mode progression unit completes", 60_000) {
                val state = ReviewProbe.controller!!.state.value
                state.itemIndex > firstIndex && state.phase == SessionPhase.PLAYING
            }
            assertEquals(
                TrainingKind.CHORD_PROGRESSIONS,
                currentProgressionExercise().kind
            )
            client.stop()
        }
    }

    @Test fun aLiveProgressionEditNeverSplitsTheAudibleProgression() {
        runBlocking {
            val repository = client.app.settingsRepository
            val revision = repository.settings.first().settings
                .modeSetups.getValue(TrainingKind.CHORD_PROGRESSIONS).revision
            repository.updateMode(
                ModeEdit(TrainingKind.CHORD_PROGRESSIONS, revision) { existing ->
                    val current = existing.payload as ModePayload.Progressions
                    ModePayload.Progressions(
                        current.setup.copy(
                            chordIds = listOf(
                                DiatonicChord.I.id,
                                DiatonicChord.II.id,
                                DiatonicChord.V.id
                            ),
                            answerFormat = ProgressionAnswerFormat.ROMAN,
                            thinkingSilence = kotlin.time.Duration.parse("500ms")
                        )
                    )
                }
            )
            repository.update { it.copy(selectedKind = TrainingKind.CHORD_PROGRESSIONS) }
        }
        client.waitFor("progression kind active in settings") {
            client.app.settings.value.settings.selectedKind == TrainingKind.CHORD_PROGRESSIONS
        }
        client.start()
        client.waitFor("progression playing") {
            val state = ReviewProbe.controller!!.state.value
            state.phase == SessionPhase.PLAYING &&
                state.activeKind == TrainingKind.CHORD_PROGRESSIONS &&
                state.consumedFrames > 0
        }
        val index = ReviewProbe.controller!!.state.value.itemIndex
        val audible = currentProgressionExercise()

        // A live setup edit is an autosave of the same form; it must not touch the audible
        // progression, which is one indivisible exercise.
        runBlocking {
            val repository = client.app.settingsRepository
            val revision = repository.settings.first().settings
                .modeSetups.getValue(TrainingKind.CHORD_PROGRESSIONS).revision
            repository.updateMode(
                ModeEdit(TrainingKind.CHORD_PROGRESSIONS, revision) { existing ->
                    val current = existing.payload as ModePayload.Progressions
                    ModePayload.Progressions(
                        current.setup.copy(
                            chordIds = listOf(
                                DiatonicChord.I.id,
                                DiatonicChord.IV.id,
                                DiatonicChord.V7.id
                            )
                        )
                    )
                }
            )
        }
        Thread.sleep(300)
        assertEquals(
            "the audible progression keeps playing as one item",
            index,
            ReviewProbe.controller!!.state.value.itemIndex
        )
        assertEquals(
            "the audible progression is still the same complete exercise",
            audible,
            currentProgressionExercise()
        )

        // The edit applies from the next progression, which is again one complete exercise.
        client.waitFor("the edited chord pool starts with the next progression", 60_000) {
            val state = ReviewProbe.controller!!.state.value
            state.itemIndex > index && state.phase == SessionPhase.PLAYING
        }
        val edited = currentProgressionExercise().provenance.generatedProgression!!
        assertTrue(
            "the next progression draws only from the edited pool: ${edited.sequenceLabel}",
            edited.sequenceLabel.split(" ").all { it in setOf("I", "IV", "V7") }
        )
        client.stop()
    }

    @Test fun anAssociationProgressionUnitFinishesBeforeASwitchCommits() {
        runBlocking {
            val repository = client.app.settingsRepository
            val revision = repository.settings.first().settings
                .modeSetups.getValue(TrainingKind.CHORD_PROGRESSIONS).revision
            repository.updateMode(
                ModeEdit(TrainingKind.CHORD_PROGRESSIONS, revision) { existing ->
                    val current = existing.payload as ModePayload.Progressions
                    ModePayload.Progressions(
                        current.setup.copy(
                            chordIds = listOf(
                                DiatonicChord.I.id,
                                DiatonicChord.II.id,
                                DiatonicChord.V.id
                            ),
                            thinkingSilence = kotlin.time.Duration.parse("500ms"),
                            mode = RecipeMode.ASSOCIATION
                        )
                    )
                }
            )
            repository.update { it.copy(selectedKind = TrainingKind.CHORD_PROGRESSIONS) }
        }
        client.waitFor("progression kind active in settings") {
            client.app.settings.value.settings.selectedKind == TrainingKind.CHORD_PROGRESSIONS
        }
        client.start()
        client.waitFor("progression playing in association mode") {
            val state = ReviewProbe.controller!!.state.value
            state.phase == SessionPhase.PLAYING &&
                state.activeKind == TrainingKind.CHORD_PROGRESSIONS &&
                state.consumedFrames > 0
        }
        val before = ReviewProbe.controller!!.state.value

        // Resolve the destination exactly as the service does: from the saved interval payload.
        val snapshot = client.app.settings.value
        val intervalPayload = snapshot.settings.modeSetups
            .getValue(TrainingKind.INTERVALS).payload as ModePayload.Intervals
        val destination = intervalPayload.setup.toStartRequest(
            seed = intervalPayload.setup.seed,
            musicGain = snapshot.settings.musicGain,
            voiceGain = snapshot.settings.voiceGain
        )
        val intervalsRevision = snapshot.settings.modeSetups
            .getValue(TrainingKind.INTERVALS).revision
        runBlocking {
            ReviewProbe.controller!!.send(DomainCommand.SwitchMode(destination, intervalsRevision))
        }
        client.waitFor("intervals switch pending") {
            ReviewProbe.controller!!.state.value.pendingKind == TrainingKind.INTERVALS
        }
        assertEquals(
            "the progression stays audible while the switch waits",
            TrainingKind.CHORD_PROGRESSIONS,
            ReviewProbe.controller!!.state.value.activeKind
        )
        client.waitFor("intervals active at the boundary") {
            val state = ReviewProbe.controller!!.state.value
            state.activeKind == TrainingKind.INTERVALS && state.phase == SessionPhase.PLAYING
        }
        assertLastCommittedProgressionWasHeardInFull(before)
    }

    @Test fun hiddenAnswersStayHiddenForAContextualIntervalSession() {
        runBlocking {
            client.app.settingsRepository.update {
                it.copy(
                    answerVisibility = AnswerVisibility.HIDDEN,
                    selectedKind = TrainingKind.INTERVALS_IN_CONTEXT
                )
            }
        }
        client.waitFor("contextual-interval kind active in settings") {
            client.app.settings.value.settings.selectedKind ==
                TrainingKind.INTERVALS_IN_CONTEXT
        }
        client.start()
        client.waitFor("contextual-interval session running") {
            ReviewProbe.controller!!.state.value.activeKind ==
                TrainingKind.INTERVALS_IN_CONTEXT
        }
        client.waitFor("contextual-interval answer position") {
            // Past the default example (tonic triad 0.7 s, 0.5 s context gap, both ascending
            // target notes) and the 2 s thinking pause, where a visible answer would already
            // have been published.
            (ReviewProbe.controller?.state?.value?.consumedFrames ?: 0) > 260_000
        }
        assertFalse(ReviewProbe.controller!!.state.value.answerVisible)
        assertEquals(null, ReviewProbe.controller!!.state.value.answerLabel)
        assertTrue(client.answer().isNullOrEmpty())
        assertFalse(
            client.main {
                client.controller.mediaMetadata.extras
                    ?.containsKey(ANSWER_TEXT_EXTRA) == true
            }
        )
        runBlocking {
            client.app.settingsRepository.update {
                it.copy(answerVisibility = AnswerVisibility.SHOWN_WHEN_CONSUMED)
            }
        }
        client.waitFor("contextual-interval answer shown after preference edit") {
            !client.answer().isNullOrEmpty()
        }
        runBlocking {
            client.app.settingsRepository.update {
                it.copy(answerVisibility = AnswerVisibility.HIDDEN)
            }
        }
        client.waitFor("contextual-interval answer removed after preference edit", 3000) {
            client.answer().isNullOrEmpty()
        }
        assertEquals(null, ReviewProbe.controller!!.state.value.answerLabel)
    }

    @Test fun contextualIntervalRecipesPlayTheCompleteContextAndTargets() {
        // One interval in C with the tonic-triad context keeps every recipe short enough for a
        // bounded wait while still exercising the complete context-plus-targets unit, including
        // Association's second example, which must repeat the complete context.
        for (mode in RecipeMode.entries) {
            runBlocking {
                val repository = client.app.settingsRepository
                val revision = repository.settings.first().settings
                    .modeSetups.getValue(TrainingKind.INTERVALS_IN_CONTEXT).revision
                repository.updateMode(
                    ModeEdit(TrainingKind.INTERVALS_IN_CONTEXT, revision) { existing ->
                        val current = existing.payload as ModePayload.ContextualIntervals
                        ModePayload.ContextualIntervals(
                            current.setup.copy(
                                keyValues = listOf(0),
                                tonalities = listOf(Tonality.MAJOR),
                                intervalIds = listOf("major_third"),
                                presentation = IntervalPresentation.ASCENDING,
                                context = ContextKind.TONIC_TRIAD,
                                mode = mode,
                                thinkingSilence = kotlin.time.Duration.parse("500ms")
                            )
                        )
                    }
                )
                repository.update {
                    it.copy(selectedKind = TrainingKind.INTERVALS_IN_CONTEXT)
                }
            }
            client.waitFor("$mode contextual-interval kind active") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.INTERVALS_IN_CONTEXT
            }
            // Let the service observe the edited recipe mode before the Start builds the request;
            // without this the first item can still be compiled from the previous mode.
            Thread.sleep(300)
            client.start()
            client.waitFor("$mode contextual intervals playing") {
                val state = ReviewProbe.controller!!.state.value
                state.phase == SessionPhase.PLAYING &&
                    state.activeKind == TrainingKind.INTERVALS_IN_CONTEXT &&
                    state.consumedFrames > 0
            }

            // The running item is one complete unit: the M8 context groups, then both target
            // notes in presented order, never a split or context-free fragment.
            val exercise = currentContextualIntervalExercise()
            val generated = exercise.provenance.generatedContextualInterval
                ?: error("the running item must be a generated contextual interval")
            assertEquals("major_third", generated.intervalId)
            assertTrue(
                "$mode must sound the complete context before the targets",
                exercise.contextGroups.isNotEmpty()
            )
            assertEquals(
                "$mode must schedule both target notes",
                listOf(generated.firstTargetMidi, generated.secondTargetMidi),
                exercise.targetGroups.map { group -> group.notes.single().value }
            )

            // Association repeats the example after the answer; the timeline must contain the
            // complete context a second time, not a bare target pair.
            val compiled = itemCompiled(
                currentContextualIntervalItem() ?: error("the running item must exist")
            )
            val noteEvents = compiled.timeline.events.filterIsInstance<NoteEvent>()
            val contextStarts = noteEvents
                .groupBy { it.startOffset }
                .filterValues { events ->
                    events.map { it.note.value }.toSet() == generated.contextValues.toSet()
                }
                .keys
            assertEquals(
                "$mode must repeat the complete context in every Example",
                if (mode == RecipeMode.ASSOCIATION) 2 else 1,
                contextStarts.size
            )

            // The unit runs to its boundary and the next complete unit begins, which proves the
            // whole recipe (for Association the second example) is playable.
            val firstIndex = ReviewProbe.controller!!.state.value.itemIndex
            client.waitFor("$mode contextual-interval unit completes", 60_000) {
                val state = ReviewProbe.controller!!.state.value
                state.itemIndex > firstIndex && state.phase == SessionPhase.PLAYING
            }
            assertEquals(
                TrainingKind.INTERVALS_IN_CONTEXT,
                currentContextualIntervalExercise().kind
            )
            client.stop()
        }
    }

    @Test fun aLiveContextualIntervalEditNeverSplitsTheAudibleUnit() {
        runBlocking {
            val repository = client.app.settingsRepository
            val revision = repository.settings.first().settings
                .modeSetups.getValue(TrainingKind.INTERVALS_IN_CONTEXT).revision
            repository.updateMode(
                ModeEdit(TrainingKind.INTERVALS_IN_CONTEXT, revision) { existing ->
                    val current = existing.payload as ModePayload.ContextualIntervals
                    ModePayload.ContextualIntervals(
                        current.setup.copy(
                            keyValues = listOf(0),
                            intervalIds = listOf("major_third"),
                            presentation = IntervalPresentation.ASCENDING,
                            context = ContextKind.TONIC_TRIAD,
                            thinkingSilence = kotlin.time.Duration.parse("500ms")
                        )
                    )
                }
            )
            repository.update { it.copy(selectedKind = TrainingKind.INTERVALS_IN_CONTEXT) }
        }
        client.waitFor("contextual-interval kind active in settings") {
            client.app.settings.value.settings.selectedKind ==
                TrainingKind.INTERVALS_IN_CONTEXT
        }
        client.start()
        client.waitFor("contextual intervals playing") {
            val state = ReviewProbe.controller!!.state.value
            state.phase == SessionPhase.PLAYING &&
                state.activeKind == TrainingKind.INTERVALS_IN_CONTEXT &&
                state.consumedFrames > 0
        }
        val index = ReviewProbe.controller!!.state.value.itemIndex
        val audible = currentContextualIntervalExercise()

        // A live setup edit is an autosave of the same form; it must not touch the audible unit,
        // which is one indivisible exercise: context, gap and both targets.
        runBlocking {
            val repository = client.app.settingsRepository
            val revision = repository.settings.first().settings
                .modeSetups.getValue(TrainingKind.INTERVALS_IN_CONTEXT).revision
            repository.updateMode(
                ModeEdit(TrainingKind.INTERVALS_IN_CONTEXT, revision) { existing ->
                    val current = existing.payload as ModePayload.ContextualIntervals
                    ModePayload.ContextualIntervals(
                        current.setup.copy(intervalIds = listOf("perfect_fifth"))
                    )
                }
            )
        }
        Thread.sleep(300)
        assertEquals(
            "the audible unit keeps playing as one item",
            index,
            ReviewProbe.controller!!.state.value.itemIndex
        )
        assertEquals(
            "the audible unit is still the same complete exercise",
            audible,
            currentContextualIntervalExercise()
        )

        // The edit applies from the next exercise, which is again one complete unit.
        client.waitFor("the edited interval starts with the next exercise", 60_000) {
            val state = ReviewProbe.controller!!.state.value
            state.itemIndex > index && state.phase == SessionPhase.PLAYING
        }
        assertEquals(
            "perfect_fifth",
            currentContextualIntervalExercise().provenance.generatedContextualInterval!!
                .intervalId
        )
        client.stop()
    }

    @Test fun anAssociationContextualIntervalUnitFinishesBeforeASwitchCommits() {
        runBlocking {
            val repository = client.app.settingsRepository
            val revision = repository.settings.first().settings
                .modeSetups.getValue(TrainingKind.INTERVALS_IN_CONTEXT).revision
            repository.updateMode(
                ModeEdit(TrainingKind.INTERVALS_IN_CONTEXT, revision) { existing ->
                    val current = existing.payload as ModePayload.ContextualIntervals
                    ModePayload.ContextualIntervals(
                        current.setup.copy(
                            keyValues = listOf(0),
                            intervalIds = listOf("major_third"),
                            context = ContextKind.TONIC_TRIAD,
                            thinkingSilence = kotlin.time.Duration.parse("500ms"),
                            mode = RecipeMode.ASSOCIATION
                        )
                    )
                }
            )
            repository.update { it.copy(selectedKind = TrainingKind.INTERVALS_IN_CONTEXT) }
        }
        client.waitFor("contextual-interval kind active in settings") {
            client.app.settings.value.settings.selectedKind ==
                TrainingKind.INTERVALS_IN_CONTEXT
        }
        client.start()
        client.waitFor("contextual intervals playing in association mode") {
            val state = ReviewProbe.controller!!.state.value
            state.phase == SessionPhase.PLAYING &&
                state.activeKind == TrainingKind.INTERVALS_IN_CONTEXT &&
                state.consumedFrames > 0
        }
        val before = ReviewProbe.controller!!.state.value

        // Resolve the destination exactly as the service does: from the saved interval payload.
        val snapshot = client.app.settings.value
        val intervalPayload = snapshot.settings.modeSetups
            .getValue(TrainingKind.INTERVALS).payload as ModePayload.Intervals
        val destination = intervalPayload.setup.toStartRequest(
            seed = intervalPayload.setup.seed,
            musicGain = snapshot.settings.musicGain,
            voiceGain = snapshot.settings.voiceGain
        )
        val intervalsRevision = snapshot.settings.modeSetups
            .getValue(TrainingKind.INTERVALS).revision
        runBlocking {
            ReviewProbe.controller!!.send(DomainCommand.SwitchMode(destination, intervalsRevision))
        }
        client.waitFor("intervals switch pending") {
            ReviewProbe.controller!!.state.value.pendingKind == TrainingKind.INTERVALS
        }
        assertEquals(
            "the contextual-interval unit stays audible while the switch waits",
            TrainingKind.INTERVALS_IN_CONTEXT,
            ReviewProbe.controller!!.state.value.activeKind
        )
        client.waitFor("intervals active at the boundary") {
            val state = ReviewProbe.controller!!.state.value
            state.activeKind == TrainingKind.INTERVALS && state.phase == SessionPhase.PLAYING
        }
        assertLastCommittedContextualIntervalWasHeardInFull(before)
    }

    @Test fun navigationEditsAndRapidStopStartKeepTheCurrentPairAndBoundHistory() {
        client.start()
        val before = ReviewProbe.controller!!.state.value
        runBlocking {
            val repository = client.app.settingsRepository
            val revision = repository.settings.first().settings
                .modeSetups.getValue(TrainingKind.INTERVALS).revision
            repository.updateMode(
                ModeEdit(TrainingKind.INTERVALS, revision) { existing ->
                    val current = existing.payload as ModePayload.Intervals
                    ModePayload.Intervals(
                        current.setup.copy(
                            thinkingSilence = kotlin.time.Duration.parse("1s"),
                            seed = 123,
                            stopPolicy = SessionStopPolicy.UntilStopped
                        )
                    )
                }
            )
        }
        Thread.sleep(250)
        assertEquals(before.itemIndex, ReviewProbe.controller!!.state.value.itemIndex)
        repeat(25) {
            client.main { client.controller.seekToNext() }
            Thread.sleep(100)
        }
        client.waitFor("navigation settled") {
            ReviewProbe.controller!!.state.value.itemIndex >= 20
        }
        val controller = ReviewProbe.controller!!
        val field = controller.javaClass.getDeclaredField("history").apply { isAccessible = true }
        assertTrue((field.get(controller) as Collection<*>).size <= 20)
        val retained = ReviewProbe.port!!.retention()
        assertTrue(retained.entries <= 2)
        assertTrue(retained.bytes <= 48L * 1024 * 1024)
        client.main { client.controller.seekToPrevious() }
        Thread.sleep(100)
        runBlocking { ReviewProbe.controller!!.send(DomainCommand.Repeat) }
        repeat(5) {
            client.stop()
            client.start()
        }
        client.stop()
        assertFalse(ReviewProbe.port!!.retention().rendererOpen)
        assertFalse(ReviewProbe.port!!.retention().outputOpen)
    }

    @Test fun failuresAtEverySessionBoundaryReleaseResourcesAndAllowAnExplicitNewStart() {
        for (failure in listOf("generation", "compilation", "rendering", "output")) {
            ReviewProbe.failure = failure
            client.main { client.controller.play() }
            client.waitFor("injected $failure error") {
                ReviewProbe.controller!!.state.value.phase ==
                    SessionPhase.ERROR
            }
            assertTrue(ReviewProbe.controller!!.state.value.error!!.detail.contains("injected"))
            assertFalse(ReviewProbe.port!!.retention().rendererOpen)
            assertFalse(ReviewProbe.port!!.retention().outputOpen)
            ReviewProbe.failure = null
            client.start()
            client.stop()
        }
    }

    @Test fun focusDenialAndInjectedRoutePolicyNeverRestartByThemselves() {
        ReviewProbe.denyFocus = true
        client.main { client.controller.play() }
        client.waitFor("focus denied") {
            ReviewProbe.controller!!.state.value.phase ==
                SessionPhase.PAUSED
        }
        assertFalse(ReviewProbe.port!!.retention().outputOpen)
        ReviewProbe.denyFocus = false
        client.start()
        client.main { ReviewProbe.service!!.inject(Interruption.FocusDucked) }
        Thread.sleep(200)
        assertEquals(SessionPhase.PLAYING, ReviewProbe.controller!!.state.value.phase)
        client.main { ReviewProbe.service!!.inject(Interruption.FocusGained) }
        client.main { ReviewProbe.service!!.inject(Interruption.BecomingNoisy) }
        client.waitFor("injected route stops") {
            ReviewProbe.controller!!.state.value.phase ==
                SessionPhase.COMPLETED
        }
        client.main { ReviewProbe.service!!.inject(Interruption.RouteAdded("test route")) }
        Thread.sleep(300)
        assertEquals(SessionPhase.COMPLETED, ReviewProbe.controller!!.state.value.phase)
    }

    @Test fun aRealTestOwnedFocusRequesterDucksAndStopsTheSession() {
        val manager = client.context.getSystemService(android.media.AudioManager::class.java)
        fun request(gain: Int) = android.media.AudioFocusRequest.Builder(gain)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder().setUsage(
                    android.media.AudioAttributes.USAGE_MEDIA
                )
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build()
            )
            .setOnAudioFocusChangeListener({
            }, android.os.Handler(android.os.Looper.getMainLooper())).build()
        client.start()
        val duck = request(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        try {
            assertEquals(
                android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
                manager.requestAudioFocus(duck)
            )
            Thread.sleep(300)
            assertEquals(SessionPhase.PLAYING, ReviewProbe.controller!!.state.value.phase)
        } finally {
            manager.abandonAudioFocusRequest(duck)
        }
        val loss = request(android.media.AudioManager.AUDIOFOCUS_GAIN)
        try {
            assertEquals(
                android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
                manager.requestAudioFocus(loss)
            )
            client.waitFor("real focus loss stops") {
                ReviewProbe.controller!!.state.value.phase ==
                    SessionPhase.COMPLETED
            }
        } finally {
            manager.abandonAudioFocusRequest(loss)
        }
        Thread.sleep(300)
        assertEquals(SessionPhase.COMPLETED, ReviewProbe.controller!!.state.value.phase)
    }

    @Test fun melodicDictationRecipesPlayTheCompleteContextAndPhrase() {
        // One four-note tonic-to-tonic phrase in C keeps every recipe short enough for a bounded
        // wait while still exercising the complete context-plus-phrase unit, including
        // Association's second example, which must repeat the complete context.
        for (mode in RecipeMode.entries) {
            runBlocking {
                val repository = client.app.settingsRepository
                val revision = repository.settings.first().settings
                    .modeSetups.getValue(TrainingKind.MELODIC_DICTATION).revision
                repository.updateMode(
                    ModeEdit(TrainingKind.MELODIC_DICTATION, revision) { existing ->
                        val current = existing.payload as ModePayload.MelodicDictation
                        ModePayload.MelodicDictation(
                            current.setup.copy(
                                keyValues = listOf(0),
                                tonalities = listOf(Tonality.MAJOR),
                                context = ContextKind.TONIC_TRIAD,
                                contextGap = kotlin.time.Duration.parse("200ms"),
                                phraseLength = 4,
                                startDegree = 1,
                                endDegree = 1,
                                allowedSteps = (-3..3).toList(),
                                maximumLeap = 3,
                                mode = mode,
                                thinkingSilence = kotlin.time.Duration.parse("500ms"),
                                noteDuration = kotlin.time.Duration.parse("300ms"),
                                melodicGap = kotlin.time.Duration.parse("100ms")
                            )
                        )
                    }
                )
                repository.update {
                    it.copy(selectedKind = TrainingKind.MELODIC_DICTATION)
                }
            }
            client.waitFor("$mode melodic dictation selected") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.MELODIC_DICTATION
            }
            // Let the service observe the edited mode before the Start builds the request;
            // without this the new item can still be compiled from the previous mode.
            Thread.sleep(300)
            client.start()
            client.waitFor("$mode melodic dictation playing") {
                val state = ReviewProbe.controller!!.state.value
                state.phase == SessionPhase.PLAYING &&
                    state.activeKind == TrainingKind.MELODIC_DICTATION &&
                    state.consumedFrames > 0
            }

            // The running item is one complete unit: the M8 context groups, then the whole
            // phrase in played order, never a split or context-free fragment.
            val exercise = currentMelodicDictationExercise()
            val generated = exercise.provenance.generatedMelody
                ?: error("the running item must be a generated melody")
            assertTrue(
                "$mode must sound the complete context before the phrase",
                exercise.contextGroups.isNotEmpty()
            )
            assertEquals(
                "$mode must schedule one TARGET group per phrase note",
                generated.notes.map { note -> note.midi },
                exercise.targetGroups.map { group -> group.notes.single().value }
            )
            assertEquals(
                "$mode must schedule a complete phrase",
                generated.notes.size,
                generated.steps.size + 1
            )

            // Association repeats the example after the answer; the timeline must contain the
            // complete context a second time, not a bare phrase.
            val compiled = itemCompiled(
                currentMelodicDictationItem() ?: error("the running item must exist")
            )
            val noteEvents = compiled.timeline.events.filterIsInstance<NoteEvent>()
            val contextStarts = noteEvents
                .groupBy { it.startOffset }
                .filterValues { events ->
                    events.map { it.note.value }.toSet() == generated.contextNotes.toSet()
                }
                .keys
            assertEquals(
                "$mode must repeat the complete context in every Example",
                if (mode == RecipeMode.ASSOCIATION) 2 else 1,
                contextStarts.size
            )

            // The unit runs to its boundary and the next complete unit begins, which proves the
            // whole recipe (for Association the second example) is playable.
            val firstIndex = ReviewProbe.controller!!.state.value.itemIndex
            client.waitFor("$mode melodic dictation unit completes", 60_000) {
                val state = ReviewProbe.controller!!.state.value
                state.itemIndex > firstIndex && state.phase == SessionPhase.PLAYING
            }
            assertEquals(
                TrainingKind.MELODIC_DICTATION,
                currentMelodicDictationExercise().kind
            )
            client.stop()
        }
    }

    @Test fun aMelodicDictationSwitchRequestedDuringThePhraseOrAnswerKeepsTheWholeUnit() {
        runBlocking {
            val repository = client.app.settingsRepository
            val revision = repository.settings.first().settings
                .modeSetups.getValue(TrainingKind.MELODIC_DICTATION).revision
            repository.updateMode(
                ModeEdit(TrainingKind.MELODIC_DICTATION, revision) { existing ->
                    val current = existing.payload as ModePayload.MelodicDictation
                    ModePayload.MelodicDictation(
                        current.setup.copy(
                            keyValues = listOf(0),
                            tonalities = listOf(Tonality.MAJOR),
                            context = ContextKind.TONIC_TRIAD,
                            contextGap = kotlin.time.Duration.parse("200ms"),
                            phraseLength = 6,
                            startDegree = 1,
                            endDegree = 1,
                            allowedSteps = (-3..3).toList(),
                            maximumLeap = 3,
                            mode = RecipeMode.ASSOCIATION,
                            thinkingSilence = kotlin.time.Duration.parse("500ms"),
                            noteDuration = kotlin.time.Duration.parse("300ms"),
                            melodicGap = kotlin.time.Duration.parse("100ms")
                        )
                    )
                }
            )
            repository.update { it.copy(selectedKind = TrainingKind.MELODIC_DICTATION) }
        }
        client.waitFor("melodic dictation kind active in settings") {
            client.app.settings.value.settings.selectedKind ==
                TrainingKind.MELODIC_DICTATION
        }
        client.start()
        client.waitFor("melodic dictation playing in association mode") {
            val state = ReviewProbe.controller!!.state.value
            state.phase == SessionPhase.PLAYING &&
                state.activeKind == TrainingKind.MELODIC_DICTATION &&
                state.consumedFrames > 0
        }

        // First switch requested during the example/melody phase, right after the item started.
        var before = ReviewProbe.controller!!.state.value
        client.requestIntervalsSwitch()
        client.waitFor("intervals switch pending") {
            ReviewProbe.controller!!.state.value.pendingKind == TrainingKind.INTERVALS
        }
        assertEquals(
            "the melody stays audible while the switch waits",
            TrainingKind.MELODIC_DICTATION,
            ReviewProbe.controller!!.state.value.activeKind
        )
        client.waitFor("intervals active at the boundary") {
            val state = ReviewProbe.controller!!.state.value
            state.activeKind == TrainingKind.INTERVALS && state.phase == SessionPhase.PLAYING
        }
        assertLastCommittedMelodicDictationWasHeardInFull(before)

        // Back to melodic dictation; this time the switch is requested while the spoken answer
        // is audible. The whole unit - context, melody and answer - must still finish first.
        client.requestMelodicDictationSwitch()
        client.waitFor("melodic dictation active again") {
            val state = ReviewProbe.controller!!.state.value
            state.activeKind == TrainingKind.MELODIC_DICTATION &&
                state.phase == SessionPhase.PLAYING
        }
        val melodyIndex = ReviewProbe.controller!!.state.value.itemIndex
        client.waitFor("melodic answer consumed") {
            val state = ReviewProbe.controller!!.state.value
            state.answerVisible && state.answerLabel != null && state.itemIndex == melodyIndex
        }
        before = ReviewProbe.controller!!.state.value
        client.requestIntervalsSwitch()
        client.waitFor("intervals switch pending again") {
            ReviewProbe.controller!!.state.value.pendingKind == TrainingKind.INTERVALS
        }
        assertEquals(
            "the answer stays audible while the switch waits",
            TrainingKind.MELODIC_DICTATION,
            ReviewProbe.controller!!.state.value.activeKind
        )
        client.waitFor("intervals active at the boundary again") {
            val state = ReviewProbe.controller!!.state.value
            state.activeKind == TrainingKind.INTERVALS && state.phase == SessionPhase.PLAYING
        }
        assertLastCommittedMelodicDictationWasHeardInFull(before)
    }
}

/** Asks the running session to switch to interval recognition, exactly as the service aims. */
private fun ReviewClient.requestIntervalsSwitch() {
    val snapshot = app.settings.value
    val intervalPayload = snapshot.settings.modeSetups
        .getValue(TrainingKind.INTERVALS).payload as ModePayload.Intervals
    val destination = intervalPayload.setup.toStartRequest(
        seed = intervalPayload.setup.seed,
        musicGain = snapshot.settings.musicGain,
        voiceGain = snapshot.settings.voiceGain
    )
    val intervalsRevision = snapshot.settings.modeSetups
        .getValue(TrainingKind.INTERVALS).revision
    runBlocking {
        ReviewProbe.controller!!.send(DomainCommand.SwitchMode(destination, intervalsRevision))
    }
}

/** Asks the running session to switch back to the saved melodic-dictation setup. */
private fun ReviewClient.requestMelodicDictationSwitch() {
    val snapshot = app.settings.value
    val melodyPayload = snapshot.settings.modeSetups
        .getValue(TrainingKind.MELODIC_DICTATION).payload as ModePayload.MelodicDictation
    val destination = melodyPayload.setup.toStartRequest(
        seed = melodyPayload.setup.seed,
        musicGain = snapshot.settings.musicGain,
        voiceGain = snapshot.settings.voiceGain
    )
    val revision = snapshot.settings.modeSetups
        .getValue(TrainingKind.MELODIC_DICTATION).revision
    runBlocking {
        ReviewProbe.controller!!.send(DomainCommand.SwitchMode(destination, revision))
    }
}

/** The controller's current item, or null; review tests inspect the concrete exercise. */
private fun currentDegreeItem(): Any? {
    val controller = ReviewProbe.controller ?: return null
    val currentField = controller.javaClass.getDeclaredField("current").apply {
        isAccessible = true
    }
    return currentField.get(controller)
}

/** The concrete exercise of one controller item. */
private fun itemExercise(item: Any): ConcreteExercise = item.javaClass.getDeclaredField("exercise")
    .apply { isAccessible = true }
    .get(item) as ConcreteExercise

/** The compiled timeline of one controller item. */
private fun itemCompiled(item: Any): CompiledExercise = item.javaClass.getDeclaredField("compiled")
    .apply { isAccessible = true }
    .get(item) as CompiledExercise

/** The device frame the rendered item starts its spoken answer at. */
private fun itemAnswerStartFrame(item: Any): Long =
    item.javaClass.getDeclaredField("answerStartFrame")
        .apply { isAccessible = true }
        .getLong(item)

/**
 * Asserts the switch waited for the whole degree learning unit it interrupted.
 *
 * In Association mode the mandatory unit is Example-Thinking-Answer-Gap-Example, so the frames
 * consumed between [before] and the commit must cover the second example as well; the controller's
 * private history retains the committed item and its rendered mandatory boundary.
 */
private fun assertLastCommittedDegreeWasHeardInFull(before: SessionSnapshot) {
    val controller = ReviewProbe.controller ?: error("no session controller")
    val historyField = controller.javaClass.getDeclaredField("history").apply {
        isAccessible = true
    }
    val lastItem = (historyField.get(controller) as Collection<*>).last()!!
    val exercise = lastItem.javaClass.getDeclaredField("exercise")
        .apply { isAccessible = true }
        .get(lastItem) as ConcreteExercise
    assertEquals(
        "the switch must wait for a scale-degree item",
        TrainingKind.SCALE_DEGREES,
        exercise.kind
    )
    val mandatoryEnd = lastItem.javaClass.getDeclaredField("mandatoryEndFrame")
        .apply { isAccessible = true }.getLong(lastItem)
    val after = controller.state.value
    val traversed = before.consumedFrames + (after.playedDuration - before.playedDuration)
        .inWholeMicroseconds * REVIEW_SAMPLE_RATE / 1_000_000L
    // The played-duration projection rounds to whole microseconds, so allow two frames.
    assertTrue(
        "the association unit must finish before the switch commits " +
            "(traversed=$traversed, mandatoryEnd=$mandatoryEnd)",
        traversed >= mandatoryEnd - 2
    )
}

/** The review audio pipeline is fixed to 48 kHz mono. */
private const val REVIEW_SAMPLE_RATE = 48_000L

/** The exercise of the controller's current item, required to be a progression item. */
private fun currentProgressionExercise(): ConcreteExercise {
    val controller = ReviewProbe.controller ?: error("no session controller")
    val currentField = controller.javaClass.getDeclaredField("current").apply {
        isAccessible = true
    }
    val item = currentField.get(controller) ?: error("no current progression item")
    return itemExercise(item)
}

/**
 * Asserts the switch waited for one complete chord progression and its mandatory unit.
 *
 * The controller records the committed item in its private history and only commits at a
 * mandatory-unit boundary at the earliest; the frames consumed between [before] and the commit must
 * therefore cover that unit, and the committed item must be one whole progression: every template
 * chord present as a single complete TARGET triad, never a chord split across the boundary.
 */
private fun assertLastCommittedProgressionWasHeardInFull(before: SessionSnapshot) {
    val controller = ReviewProbe.controller ?: error("no session controller")
    val historyField = controller.javaClass.getDeclaredField("history").apply {
        isAccessible = true
    }
    val lastItem = (historyField.get(controller) as Collection<*>).last()!!
    val exercise = itemExercise(lastItem)
    assertEquals(
        "the switch must wait for a chord-progression item",
        TrainingKind.CHORD_PROGRESSIONS,
        exercise.kind
    )
    val progression = exercise.provenance.generatedProgression
        ?: error("the committed item must be a generated progression")
    assertEquals(
        "the committed unit is one complete progression",
        ProgressionDefinition.CHORD_COUNT,
        exercise.groups.size
    )
    assertTrue(
        "every committed chord is one complete TARGET triad",
        exercise.groups.all { it.role == GroupRole.TARGET && it.notes.size == 3 }
    )
    assertEquals(ProgressionDefinition.CHORD_COUNT, progression.chords.size)
    val mandatoryEnd = lastItem.javaClass.getDeclaredField("mandatoryEndFrame")
        .apply { isAccessible = true }.getLong(lastItem)
    val after = controller.state.value
    val traversed = before.consumedFrames + (after.playedDuration - before.playedDuration)
        .inWholeMicroseconds * REVIEW_SAMPLE_RATE / 1_000_000L
    // The played-duration projection rounds to whole microseconds, so allow two frames.
    assertTrue(
        "the complete progression unit must finish before the switch commits " +
            "(traversed=$traversed, mandatoryEnd=$mandatoryEnd)",
        traversed >= mandatoryEnd - 2
    )
}

/** The exercise of the controller's current item, required to be a contextual-interval unit. */
private fun currentContextualIntervalExercise(): ConcreteExercise {
    val controller = ReviewProbe.controller ?: error("no session controller")
    val currentField = controller.javaClass.getDeclaredField("current").apply {
        isAccessible = true
    }
    val item = currentField.get(controller) ?: error("no current contextual-interval item")
    return itemExercise(item)
}

/** The controller's current item, or null; used when a helper needs the compiled timeline. */
private fun currentContextualIntervalItem(): Any? {
    val controller = ReviewProbe.controller ?: return null
    val currentField = controller.javaClass.getDeclaredField("current").apply {
        isAccessible = true
    }
    return currentField.get(controller)
}

/**
 * Asserts the switch waited for one complete contextual-interval unit and its mandatory frames.
 *
 * The committed item must be the whole unit: the M8 context groups, the context gap and both
 * target notes in presented order. The frames consumed between [before] and the commit must cover
 * the controller's recorded mandatory boundary, so the switch can never split the unit.
 */
private fun assertLastCommittedContextualIntervalWasHeardInFull(before: SessionSnapshot) {
    val controller = ReviewProbe.controller ?: error("no session controller")
    val historyField = controller.javaClass.getDeclaredField("history").apply {
        isAccessible = true
    }
    val lastItem = (historyField.get(controller) as Collection<*>).last()!!
    val exercise = itemExercise(lastItem)
    assertEquals(
        "the switch must wait for an intervals-in-context item",
        TrainingKind.INTERVALS_IN_CONTEXT,
        exercise.kind
    )
    val generated = exercise.provenance.generatedContextualInterval
        ?: error("the committed item must be a generated contextual interval")
    assertTrue(
        "the committed unit must contain the complete context",
        exercise.contextGroups.isNotEmpty()
    )
    assertEquals(
        "the committed unit must schedule both target notes in presented order",
        listOf(generated.firstTargetMidi, generated.secondTargetMidi),
        exercise.targetGroups.map { group -> group.notes.single().value }
    )
    val mandatoryEnd = lastItem.javaClass.getDeclaredField("mandatoryEndFrame")
        .apply { isAccessible = true }.getLong(lastItem)
    val after = controller.state.value
    val traversed = before.consumedFrames + (after.playedDuration - before.playedDuration)
        .inWholeMicroseconds * REVIEW_SAMPLE_RATE / 1_000_000L
    // The played-duration projection rounds to whole microseconds, so allow two frames.
    assertTrue(
        "the complete context and both target notes must finish before the switch commits " +
            "(traversed=$traversed, mandatoryEnd=$mandatoryEnd)",
        traversed >= mandatoryEnd - 2
    )
}

/** The exercise of the controller's current item, required to be a melodic-dictation unit. */
private fun currentMelodicDictationExercise(): ConcreteExercise {
    val controller = ReviewProbe.controller ?: error("no session controller")
    val currentField = controller.javaClass.getDeclaredField("current").apply {
        isAccessible = true
    }
    val item = currentField.get(controller) ?: error("no current melodic-dictation item")
    return itemExercise(item)
}

/** The controller's current item, or null; used when a helper needs the compiled timeline. */
private fun currentMelodicDictationItem(): Any? {
    val controller = ReviewProbe.controller ?: return null
    val currentField = controller.javaClass.getDeclaredField("current").apply {
        isAccessible = true
    }
    return currentField.get(controller)
}

/**
 * Asserts the switch waited for one complete melodic-dictation unit and its mandatory frames.
 *
 * The committed item must be the whole unit: the M8 context groups, the complete phrase in
 * played order and the spoken answer (and in Association mode the second full example). The
 * frames consumed between [before] and the commit must cover the controller's recorded mandatory
 * boundary, so the switch can never split the unit.
 */
private fun assertLastCommittedMelodicDictationWasHeardInFull(before: SessionSnapshot) {
    val controller = ReviewProbe.controller ?: error("no session controller")
    val historyField = controller.javaClass.getDeclaredField("history").apply {
        isAccessible = true
    }
    val lastItem = (historyField.get(controller) as Collection<*>).last()!!
    val exercise = itemExercise(lastItem)
    assertEquals(
        "the switch must wait for a melodic-dictation item",
        TrainingKind.MELODIC_DICTATION,
        exercise.kind
    )
    val generated = exercise.provenance.generatedMelody
        ?: error("the committed item must be a generated melody")
    assertTrue(
        "the committed unit must contain the complete context",
        exercise.contextGroups.isNotEmpty()
    )
    assertEquals(
        "the committed unit must schedule the complete phrase in played order",
        generated.notes.map { note -> note.midi },
        exercise.targetGroups.map { group -> group.notes.single().value }
    )
    assertEquals(
        "the committed unit must contain a complete phrase",
        generated.notes.size,
        generated.steps.size + 1
    )
    val mandatoryEnd = lastItem.javaClass.getDeclaredField("mandatoryEndFrame")
        .apply { isAccessible = true }.getLong(lastItem)
    val after = controller.state.value
    val traversed = before.consumedFrames + (after.playedDuration - before.playedDuration)
        .inWholeMicroseconds * REVIEW_SAMPLE_RATE / 1_000_000L
    // The played-duration projection rounds to whole microseconds, so allow two frames.
    assertTrue(
        "the complete context, melody and answer must finish before the switch commits " +
            "(traversed=$traversed, mandatoryEnd=$mandatoryEnd)",
        traversed >= mandatoryEnd - 2
    )
}
