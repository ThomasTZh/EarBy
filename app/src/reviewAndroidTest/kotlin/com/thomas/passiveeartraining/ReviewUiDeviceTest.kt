package com.thomas.passiveeartraining

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import com.thomas.passiveeartraining.app.ReviewProbe
import com.thomas.passiveeartraining.media.ANSWER_TEXT_EXTRA
import com.thomas.passiveeartraining.music.ScaleKind
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.ContextualIntervalAnswerFormat
import com.thomas.passiveeartraining.training.NoteDefinition
import com.thomas.passiveeartraining.training.context.ContextKind
import com.thomas.passiveeartraining.training.provider.ConcreteExercise
import com.thomas.passiveeartraining.training.provider.GeneratedContextualInterval
import com.thomas.passiveeartraining.training.provider.GeneratedMelody
import com.thomas.passiveeartraining.training.provider.GeneratedNote
import com.thomas.passiveeartraining.training.provider.GeneratedProgression
import com.thomas.passiveeartraining.training.provider.GeneratedScaleDegree
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.provider.VoiceVocabulary
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.session.SessionPhase
import com.thomas.passiveeartraining.training.session.SessionSnapshot
import com.thomas.passiveeartraining.training.settings.AnswerVisibility
import com.thomas.passiveeartraining.training.settings.ModeEdit
import com.thomas.passiveeartraining.training.settings.ModePayload
import java.io.File
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Compose semantics at isolated 150%/200% font settings, plus recreation and rebind. */
@RunWith(AndroidJUnit4::class)
class ReviewUiDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @org.junit.Before fun reset() {
        ReviewClient().use { client ->
            client.stop()
            kotlinx.coroutines.runBlocking {
                client.app.settingsRepository.resetToDefaults()
                // Reset returns when DataStore is written; wait until the app's settings collector
                // has also observed the default selection, so the next start cannot race an old
                // selected mode.
                kotlinx.coroutines.withTimeout(5_000) {
                    client.app.settings.first {
                        it.settings.selectedKind == TrainingKind.INTERVALS
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @After fun cleanup() {
        ReviewProbe.fontScale = 1f
        UiDevice.getInstance(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        ).unfreezeRotation()
        ReviewProbe.controller?.let {
            kotlinx.coroutines.runBlocking {
                it.send(com.thomas.passiveeartraining.training.session.SessionCommand.Stop())
            }
        }
    }

    @Test fun settingsAndHelpOpenFromTheDrawerWithTheirOwnTitlesAndTheAboutHeading() {
        // Settings carries its own top-bar title, not the audible mode's name.
        compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.waitForIdle()
        // The closed drawer stays composed off-screen, so the title is the visible match.
        val settingsRoot = compose.onRoot().fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(
            "the Settings title is visible",
            compose.onAllNodesWithText("Settings").fetchSemanticsNodes()
                .any { settingsRoot.contains(it.boundsInRoot.center) }
        )
        compose.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()

        // Help carries its own title; the page heading is "About EarBy".
        compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
        compose.onNodeWithText("Help").performClick()
        compose.waitForIdle()
        val helpRoot = compose.onRoot().fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(
            "the Help title is visible",
            compose.onAllNodesWithText("Help").fetchSemanticsNodes()
                .any { helpRoot.contains(it.boundsInRoot.center) }
        )
        compose.onNodeWithText("About EarBy").assertExists()
        compose.onNodeWithText("The nine modes").assertExists()
        // The full mode list is asserted by HelpScreenHostTest; here the unique page lines
        // avoid matching the closed drawer's off-screen mode items.
        compose.onNodeWithText("Transcribe a short melody you hear.").assertExists()
        compose.onNodeWithText(
            "EarBy ${com.thomas.passiveeartraining.BuildConfig.VERSION_NAME}",
            substring = true
        ).assertExists()
        // System back returns to the Play tab.
        compose.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()
        compose.onNodeWithText("Ready").assertExists()
    }

    @Test fun learningModeAndPresetEditsSurviveRecreationWithoutReplacingTheAudibleItem() {
        ReviewClient().use { client ->
            client.start()
            val session = ReviewProbe.controller!!.state.value.session
            val index = ReviewProbe.controller!!.state.value.itemIndex
            compose.onNodeWithText("Setup").performClick()
            compose.onNodeWithText("Exposure").performScrollTo().performClick()
            compose.onNodeWithText("Exposure").assertIsSelected()
            client.waitFor("learning mode saved") {
                client.app.settings.value.settings.lastSetup.mode ==
                    com.thomas.passiveeartraining.training.recipe.RecipeMode.EXPOSURE
            }
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("Exposure").performScrollTo().assertIsSelected()
            compose.onNodeWithText("Presets").performClick()
            compose.onAllNodesWithText("Use").onFirst().performScrollTo().performClick()
            compose.onNodeWithText("Play").performClick()
            org.junit.Assert.assertEquals(session, ReviewProbe.controller!!.state.value.session)
            org.junit.Assert.assertTrue(ReviewProbe.controller!!.state.value.itemIndex >= index)
            compose.onNodeWithContentDescription("Stop").assertIsDisplayed()
        }
    }

    @Test fun dialogsTabsAndTransportRemainReachableAtLargeText() {
        val device = UiDevice.getInstance(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        )
        for (scale in listOf(1.5f, 2f)) {
            ReviewProbe.fontScale = scale
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("play_stop").assertIsDisplayed()
            compose.onNodeWithContentDescription("Start").assertIsDisplayed()
            compose.onNodeWithTag("sleep_picker").performClick()
            compose.onNodeWithText("OK").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithText("Setup").performClick()
            compose.onNodeWithText("2.00 s").performScrollTo().performClick()
            compose.onNodeWithText("OK").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithText("Play").performClick()
            compose.onNodeWithTag("play_stop").assertIsDisplayed()
            device.takeScreenshot(File(compose.activity.filesDir, "review-ui-$scale.png"))
        }
    }

    @Test fun rotationHomeAndActivityRecreationRebindWithoutStartingANewSession() {
        ReviewClient().use { client ->
            client.start()
            val session = ReviewProbe.controller!!.state.value.session
            val device = UiDevice.getInstance(client.instrumentation)
            try {
                device.setOrientationLeft()
                Thread.sleep(500)
                device.setOrientationNatural()
                compose.activityRule.scenario.recreate()
                device.pressHome()
                Thread.sleep(750)
                client.context.startActivity(
                    client.context.packageManager.getLaunchIntentForPackage(
                        client.context.packageName
                    )!!
                )
                compose.waitForIdle()
                org.junit.Assert.assertEquals(session, ReviewProbe.controller!!.state.value.session)
                compose.onNodeWithContentDescription("Stop").assertIsDisplayed()
            } finally {
                device.unfreezeRotation()
            }
        }
    }

    @Test fun switchingBetweenIntervalsAndChordsCommitsAtTheItemBoundary() {
        ReviewClient().use { client ->
            // Start through the UI: the drawer's switch path only runs while the screen knows a
            // session is active, which is exactly the real user flow.
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Ready").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("intervals playing") {
                val state = ReviewProbe.controller!!.state.value
                state.phase == SessionPhase.PLAYING &&
                    state.activeKind ==
                    TrainingKind.INTERVALS &&
                    state.consumedFrames > 0
            }
            // The drawer's switch path only runs while the screen's own state reports an active
            // session; wait for the visible transport before opening the drawer.
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithContentDescription("Stop").fetchSemanticsNodes().isNotEmpty()
            }
            val intervalsGeneration = ReviewProbe.controller!!.state.value.session
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Chords") and hasClickAction()).performClick()
            // The drawer selection is remembered immediately; the audible session switches only
            // when the current item reaches its boundary (M3).
            client.waitFor("chords selected in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.CHORDS
            }
            client.waitFor("chords switch pending or refused") {
                val state = ReviewProbe.controller!!.state.value
                state.pendingKind ==
                    TrainingKind.CHORDS ||
                    compose.onAllNodesWithText("cannot be activated", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
            }
            org.junit.Assert.assertEquals(
                "the switch must become pending, not silently refused",
                TrainingKind.CHORDS,
                ReviewProbe.controller!!.state.value.pendingKind
            )
            org.junit.Assert.assertEquals(
                "the audible session is untouched while the switch waits",
                intervalsGeneration,
                ReviewProbe.controller!!.state.value.session
            )
            org.junit.Assert.assertEquals(
                TrainingKind.INTERVALS,
                ReviewProbe.controller!!.state.value.activeKind
            )
            client.waitFor("chords active at the boundary") {
                val state = ReviewProbe.controller!!.state.value
                state.activeKind ==
                    TrainingKind.CHORDS &&
                    state.phase == SessionPhase.PLAYING
            }
            kotlinx.coroutines.runBlocking {
                org.junit.Assert.assertEquals(
                    TrainingKind.CHORDS,
                    client.app.settings.value.settings.selectedKind
                )
            }

            // And back to intervals through the same boundary path.
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Interval recognition") and hasClickAction()).performClick()
            client.waitFor("intervals selected in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.INTERVALS
            }
            client.waitFor("intervals switch pending or refused") {
                val state = ReviewProbe.controller!!.state.value
                state.pendingKind ==
                    TrainingKind.INTERVALS ||
                    compose.onAllNodesWithText("cannot be activated", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
            }
            org.junit.Assert.assertEquals(
                "the return switch must become pending, not silently refused",
                TrainingKind.INTERVALS,
                ReviewProbe.controller!!.state.value.pendingKind
            )
            client.waitFor("intervals active at the boundary") {
                val state = ReviewProbe.controller!!.state.value
                state.activeKind ==
                    TrainingKind.INTERVALS &&
                    state.phase == SessionPhase.PLAYING
            }
            compose.onNodeWithContentDescription("Stop").assertIsDisplayed()
        }
    }

    @Test fun intervalWalkCanBeSelectedStartedAndStopped() {
        ReviewClient().use { client ->
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Interval walk") and hasClickAction()).performClick()
            compose.waitForIdle()
            client.waitFor("interval walk selected in settings") {
                client.app.settings.value.settings.selectedKind == TrainingKind.INTERVAL_WALK
            }
            compose.onNodeWithText("Direction: ascending · 3 steps").assertExists()
            compose.onNodeWithText("Setup").performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Interval walk setup")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Interval walk setup").assertExists()
            compose.onNodeWithText("Presets").performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Ascending thirds and fifths (starter)")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Ascending thirds and fifths (starter)")
                .performScrollTo().assertExists()
            compose.onNodeWithText("Play").performClick()

            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Ready").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("interval walk playing") {
                val state = ReviewProbe.controller!!.state.value
                state.phase == SessionPhase.PLAYING &&
                    state.activeKind == TrainingKind.INTERVAL_WALK &&
                    state.consumedFrames > 0
            }
            compose.onNodeWithContentDescription("Stop").assertIsDisplayed()
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("interval walk stopped") {
                ReviewProbe.controller!!.state.value.phase in
                    listOf(SessionPhase.IDLE, SessionPhase.COMPLETED)
            }
        }
    }

    @Test fun chordsCanBeSelectedStartedAndStopped() {
        ReviewClient().use { client ->
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Chords") and hasClickAction()).performClick()
            compose.waitForIdle()
            client.waitFor("chords selected in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.CHORDS
            }
            // The Play summary, the setup editor and the starter preset are the real chords
            // surfaces; none of them is a placeholder.
            compose.onNodeWithText("Qualities: major, minor").assertExists()
            compose.onNodeWithText("Inversions: root position").assertExists()
            compose.onNodeWithText("Setup").performClick()
            compose.onNodeWithText("Chord setup").assertExists()
            compose.onNodeWithText("Presets").performClick()
            compose.onNodeWithText(
                "Major and minor chords (starter)"
            ).performScrollTo().assertExists()
            compose.onNodeWithText("Play").performClick()

            // Start through the UI, verify a chord session actually runs, then Stop it again.
            // The transport is enabled once the screen's own controller has connected.
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Ready").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("chords playing") {
                val state = ReviewProbe.controller!!.state.value
                state.phase == SessionPhase.PLAYING &&
                    state.activeKind ==
                    TrainingKind.CHORDS &&
                    state.consumedFrames > 0
            }
            // The drawer's switch path waits for the screen's own active state; wait for the
            // visible transport before opening the drawer (the media callback precedes the poll).
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithContentDescription("Stop").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Stop").assertIsDisplayed()
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("chords stopped") {
                ReviewProbe.controller!!.state.value.phase in
                    listOf(SessionPhase.IDLE, SessionPhase.COMPLETED)
            }
        }
    }

    @Test fun scalesCanBeSelectedStartedAndStopped() {
        ReviewClient().use { client ->
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Scales") and hasClickAction()).performClick()
            compose.waitForIdle()
            client.waitFor("scales selected in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.SCALES
            }
            // The Play summary, the setup editor and the starter preset are the real scale
            // surfaces; none of them is a placeholder.
            compose.onNodeWithText("Scales: major, natural minor").assertExists()
            compose.onNodeWithText("Direction: ascending").assertExists()
            compose.onNodeWithText("Setup").performClick()
            compose.onNodeWithText("Scale setup").assertExists()
            compose.onNodeWithText("Presets").performClick()
            compose.onNodeWithText(
                "Major and natural minor scales (starter)"
            ).performScrollTo().assertExists()
            compose.onNodeWithText("Play").performClick()

            // Start through the UI, verify a scale session actually runs, then Stop it again.
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Ready").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("scales playing") {
                val state = ReviewProbe.controller!!.state.value
                state.phase == SessionPhase.PLAYING &&
                    state.activeKind ==
                    TrainingKind.SCALES &&
                    state.consumedFrames > 0
            }
            // The drawer's switch path waits for the screen's own active state; wait for the
            // visible transport before opening the drawer (the media callback precedes the poll).
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithContentDescription("Stop").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Stop").assertIsDisplayed()
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("scales stopped") {
                ReviewProbe.controller!!.state.value.phase in
                    listOf(SessionPhase.IDLE, SessionPhase.COMPLETED)
            }
        }
    }

    @Test fun notesCanBeSelectedStartedAndStopped() {
        ReviewClient().use { client ->
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Perfect pitch") and hasClickAction()).performClick()
            compose.waitForIdle()
            client.waitFor("notes selected in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.NOTE_IDENTIFICATION
            }
            // The Play summary, the setup editor and the starter preset are the real note
            // surfaces; none of them is a placeholder.
            compose.onNodeWithText("Accidentals: sharp · octave speech: off").assertExists()
            compose.onNodeWithText("Range: C4 to C5 · thinking 2 s · repeats 0").assertExists()
            compose.onNodeWithText("Setup").performClick()
            compose.onNodeWithText("Note setup").assertExists()
            compose.onNodeWithText("C\u266F").assertExists()
            compose.onNodeWithText("Presets").performClick()
            compose.onNodeWithText("All twelve notes (starter)").performScrollTo().assertExists()
            compose.onNodeWithText("Play").performClick()

            // Start through the UI, verify a note session actually runs, then Stop it again.
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Ready").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("notes playing") {
                val state = ReviewProbe.controller!!.state.value
                state.phase == SessionPhase.PLAYING &&
                    state.activeKind ==
                    TrainingKind.NOTE_IDENTIFICATION &&
                    state.consumedFrames > 0
            }
            // The drawer's switch path waits for the screen's own active state; wait for the
            // visible transport before opening the drawer (the media callback precedes the poll).
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithContentDescription("Stop").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Stop").assertIsDisplayed()
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("notes stopped") {
                ReviewProbe.controller!!.state.value.phase in
                    listOf(SessionPhase.IDLE, SessionPhase.COMPLETED)
            }
        }
    }

    @Test fun scaleDegreesCanBeSelectedStartedAndStopped() {
        ReviewClient().use { client ->
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Scale degrees") and hasClickAction()).performClick()
            compose.waitForIdle()
            client.waitFor("degrees selected in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.SCALE_DEGREES
            }
            // The Play summary, the setup editor and the starter preset are the real degree
            // surfaces; none of them is a placeholder.
            compose.onNodeWithText("Keys: C").assertExists()
            compose.onNodeWithText("Tonalities: major").assertExists()
            compose.onNodeWithText("Degrees: 1, 2, 3, 4, 5, 6, 7").assertExists()
            compose.onNodeWithText("Context: tonic triad · gap 0.50 s").assertExists()
            compose.onNodeWithText("Answer format: degree").assertExists()
            compose.onNodeWithText("Range: C4 to C5 · thinking 2 s · repeats 0").assertExists()
            compose.onNodeWithText("Setup").performClick()
            compose.onNodeWithText("Scale degree setup").assertExists()
            compose.onNodeWithText("C\u266F").assertExists()
            compose.onNodeWithText("Presets").performClick()
            compose.onNodeWithText(
                "C major scale degrees (starter)"
            ).performScrollTo().assertExists()
            compose.onNodeWithText("Play").performClick()

            // Start through the UI, verify a degree session actually runs, then Stop it again.
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Ready").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("degrees playing") {
                val state = ReviewProbe.controller!!.state.value
                state.phase == SessionPhase.PLAYING &&
                    state.activeKind ==
                    TrainingKind.SCALE_DEGREES &&
                    state.consumedFrames > 0
            }
            // The drawer's switch path waits for the screen's own active state; wait for the
            // visible transport before opening the drawer (the media callback precedes the poll).
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithContentDescription("Stop").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Stop").assertIsDisplayed()
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("degrees stopped") {
                ReviewProbe.controller!!.state.value.phase in
                    listOf(SessionPhase.IDLE, SessionPhase.COMPLETED)
            }
        }
    }

    @Test fun progressionsCanBeSelectedStartedAndStopped() {
        ReviewClient().use { client ->
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Chord progressions") and hasClickAction()).performClick()
            compose.waitForIdle()
            client.waitFor("progressions selected in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.CHORD_PROGRESSIONS
            }
            // The Play summary, the setup editor and the starter preset are the real progression
            // surfaces; none of them is a placeholder.
            compose.onNodeWithText("Keys: all twelve").assertExists()
            compose.onNodeWithText("Chords: I, ii, iii, IV, V, vi, vii\u00B0").assertExists()
            compose.onNodeWithText("Voicing: root position").assertExists()
            compose.onNodeWithText("Answer format: roman sequence").assertExists()
            compose.onNodeWithText("Range: C3 to C6 · thinking 2 s · repeats 0").assertExists()
            compose.onNodeWithText("Setup").performClick()
            compose.onNodeWithText("Chord progression setup").assertExists()
            compose.onNodeWithText("Nearest inversion").assertExists()
            compose.onNodeWithText("Presets").performClick()
            compose.onNodeWithText(
                "Major chord progressions (starter)"
            ).performScrollTo().assertExists()
            compose.onNodeWithText("Play").performClick()

            // Start through the UI, verify a progression session actually runs, then Stop it again.
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Ready").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("progressions playing") {
                val state = ReviewProbe.controller!!.state.value
                state.phase == SessionPhase.PLAYING &&
                    state.activeKind ==
                    TrainingKind.CHORD_PROGRESSIONS &&
                    state.consumedFrames > 0
            }
            // The drawer's switch path waits for the screen's own active state; wait for the
            // visible transport before opening the drawer (the media callback precedes the poll).
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithContentDescription("Stop").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Stop").assertIsDisplayed()
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("progressions stopped") {
                ReviewProbe.controller!!.state.value.phase in
                    listOf(SessionPhase.IDLE, SessionPhase.COMPLETED)
            }
        }
    }

    @Test fun contextualIntervalsCanBeSelectedStartedAndStopped() {
        ReviewClient().use { client ->
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Intervals in context") and hasClickAction()).performClick()
            compose.waitForIdle()
            client.waitFor("contextual intervals selected in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.INTERVALS_IN_CONTEXT
            }
            // The Play summary, the setup editor and the starter preset are the real
            // intervals-in-context surfaces; none of them is a placeholder. The summary names
            // only safe configuration, never the current exercise's own interval or pair.
            compose.onNodeWithText("Keys: C").assertExists()
            compose.onNodeWithText("Tonalities: major").assertExists()
            compose.onNodeWithText(
                "Intervals: Minor third, Major third, Perfect fifth"
            ).assertExists()
            compose.onNodeWithText("Context: tonic triad · gap 0.50 s").assertExists()
            compose.onNodeWithText(
                "Presentation: ascending · answer format: interval"
            ).assertExists()
            compose.onNodeWithText("Range: C4 to C5 · thinking 2 s · repeats 0").assertExists()
            compose.onNodeWithText("Setup").performClick()
            compose.onNodeWithText("Intervals in context setup").assertExists()
            compose.onNodeWithText("Ascending").assertExists()
            compose.onNodeWithText("Perfect fifth").assertExists()
            compose.onNodeWithText("Presets").performClick()
            compose.onNodeWithText(
                "Intervals over the tonic triad (starter)"
            ).performScrollTo().assertExists()
            compose.onNodeWithText("Play").performClick()

            // Start through the UI, verify a contextual-interval session actually runs, then
            // Stop it again.
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Ready").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("contextual intervals playing") {
                val state = ReviewProbe.controller!!.state.value
                state.phase == SessionPhase.PLAYING &&
                    state.activeKind ==
                    TrainingKind.INTERVALS_IN_CONTEXT &&
                    state.consumedFrames > 0
            }
            // The drawer's switch path waits for the screen's own active state; wait for the
            // visible transport before opening the drawer (the media callback precedes the poll).
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithContentDescription("Stop").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Stop").assertIsDisplayed()
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("contextual intervals stopped") {
                ReviewProbe.controller!!.state.value.phase in
                    listOf(SessionPhase.IDLE, SessionPhase.COMPLETED)
            }
        }
    }

    @Test fun switchingBetweenIntervalsAndDegreesCommitsAtTheItemBoundary() {
        ReviewClient().use { client ->
            // Start through the UI: the drawer's switch path only runs while the screen knows a
            // session is active, which is exactly the real user flow.
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Ready").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("intervals playing") {
                val state = ReviewProbe.controller!!.state.value
                state.phase == SessionPhase.PLAYING &&
                    state.activeKind ==
                    TrainingKind.INTERVALS &&
                    state.consumedFrames > 0
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithContentDescription("Stop").fetchSemanticsNodes().isNotEmpty()
            }
            val intervalsGeneration = ReviewProbe.controller!!.state.value.session
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Scale degrees") and hasClickAction()).performClick()
            // The drawer selection is remembered immediately; the audible session switches only
            // when the current item reaches its boundary (M3).
            client.waitFor("degrees selected in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.SCALE_DEGREES
            }
            client.waitFor("degrees switch pending or refused") {
                val state = ReviewProbe.controller!!.state.value
                state.pendingKind ==
                    TrainingKind.SCALE_DEGREES ||
                    compose.onAllNodesWithText("cannot be activated", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
            }
            org.junit.Assert.assertEquals(
                "the switch must become pending, not silently refused",
                TrainingKind.SCALE_DEGREES,
                ReviewProbe.controller!!.state.value.pendingKind
            )
            org.junit.Assert.assertEquals(
                "the audible session is untouched while the switch waits",
                intervalsGeneration,
                ReviewProbe.controller!!.state.value.session
            )
            org.junit.Assert.assertEquals(
                TrainingKind.INTERVALS,
                ReviewProbe.controller!!.state.value.activeKind
            )
            client.waitFor("degrees active at the boundary") {
                val state = ReviewProbe.controller!!.state.value
                state.activeKind ==
                    TrainingKind.SCALE_DEGREES &&
                    state.phase == SessionPhase.PLAYING
            }

            // And back to intervals through the same boundary path.
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Interval recognition") and hasClickAction()).performClick()
            client.waitFor("intervals selected in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.INTERVALS
            }
            client.waitFor("intervals switch pending or refused") {
                val state = ReviewProbe.controller!!.state.value
                state.pendingKind ==
                    TrainingKind.INTERVALS ||
                    compose.onAllNodesWithText("cannot be activated", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
            }
            org.junit.Assert.assertEquals(
                "the return switch must become pending, not silently refused",
                TrainingKind.INTERVALS,
                ReviewProbe.controller!!.state.value.pendingKind
            )
            client.waitFor("intervals active at the boundary") {
                val state = ReviewProbe.controller!!.state.value
                state.activeKind ==
                    TrainingKind.INTERVALS &&
                    state.phase == SessionPhase.PLAYING
            }
            compose.onNodeWithContentDescription("Stop").assertIsDisplayed()
        }
    }

    @Test fun switchingBetweenIntervalsAndProgressionsCommitsAtTheItemBoundary() {
        ReviewClient().use { client ->
            // Start through the UI: the drawer's switch path only runs while the screen knows a
            // session is active, which is exactly the real user flow.
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Ready").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("intervals playing") {
                val state = ReviewProbe.controller!!.state.value
                state.phase == SessionPhase.PLAYING &&
                    state.activeKind ==
                    TrainingKind.INTERVALS &&
                    state.consumedFrames > 0
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithContentDescription("Stop").fetchSemanticsNodes().isNotEmpty()
            }
            val intervalsGeneration = ReviewProbe.controller!!.state.value.session
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Chord progressions") and hasClickAction()).performClick()
            // The drawer selection is remembered immediately; the audible session switches only
            // when the current item reaches its boundary (M3).
            client.waitFor("progressions selected in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.CHORD_PROGRESSIONS
            }
            client.waitFor("progressions switch pending or refused") {
                val state = ReviewProbe.controller!!.state.value
                state.pendingKind ==
                    TrainingKind.CHORD_PROGRESSIONS ||
                    compose.onAllNodesWithText("cannot be activated", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
            }
            org.junit.Assert.assertEquals(
                "the switch must become pending, not silently refused",
                TrainingKind.CHORD_PROGRESSIONS,
                ReviewProbe.controller!!.state.value.pendingKind
            )
            org.junit.Assert.assertEquals(
                "the audible session is untouched while the switch waits",
                intervalsGeneration,
                ReviewProbe.controller!!.state.value.session
            )
            org.junit.Assert.assertEquals(
                TrainingKind.INTERVALS,
                ReviewProbe.controller!!.state.value.activeKind
            )
            client.waitFor("progressions active at the boundary") {
                val state = ReviewProbe.controller!!.state.value
                state.activeKind ==
                    TrainingKind.CHORD_PROGRESSIONS &&
                    state.phase == SessionPhase.PLAYING
            }
            val beforeProgression = ReviewProbe.controller!!.state.value

            // And back to intervals through the same boundary path.
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Interval recognition") and hasClickAction()).performClick()
            client.waitFor("intervals selected in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.INTERVALS
            }
            client.waitFor("intervals switch pending or refused") {
                val state = ReviewProbe.controller!!.state.value
                state.pendingKind ==
                    TrainingKind.INTERVALS ||
                    compose.onAllNodesWithText("cannot be activated", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
            }
            org.junit.Assert.assertEquals(
                "the return switch must become pending, not silently refused",
                TrainingKind.INTERVALS,
                ReviewProbe.controller!!.state.value.pendingKind
            )
            client.waitFor("intervals active at the boundary") {
                val state = ReviewProbe.controller!!.state.value
                state.activeKind ==
                    TrainingKind.INTERVALS &&
                    state.phase == SessionPhase.PLAYING
            }
            // The committed progression item is one whole progression, never a split chord.
            assertLastCommittedKindWasHeardInFull(
                beforeProgression,
                TrainingKind.CHORD_PROGRESSIONS
            )
            compose.onNodeWithContentDescription("Stop").assertIsDisplayed()
        }
    }

    @Test fun switchingBetweenProgressionsAndContextualIntervalsCommitsAtTheItemBoundary() {
        ReviewClient().use { client ->
            // Start in the new mode through the UI, then switch to chord progressions and back;
            // each pending switch must wait for the whole committed learning unit.
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Intervals in context") and hasClickAction()).performClick()
            client.waitFor("contextual intervals selected in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.INTERVALS_IN_CONTEXT
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Ready").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("contextual intervals playing") {
                val state = ReviewProbe.controller!!.state.value
                state.phase == SessionPhase.PLAYING &&
                    state.activeKind ==
                    TrainingKind.INTERVALS_IN_CONTEXT &&
                    state.consumedFrames > 0
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithContentDescription("Stop").fetchSemanticsNodes().isNotEmpty()
            }
            val contextualGeneration = ReviewProbe.controller!!.state.value.session
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Chord progressions") and hasClickAction()).performClick()
            client.waitFor("progressions selected in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.CHORD_PROGRESSIONS
            }
            client.waitFor("progressions switch pending") {
                ReviewProbe.controller!!.state.value.pendingKind ==
                    TrainingKind.CHORD_PROGRESSIONS
            }
            org.junit.Assert.assertEquals(
                "the audible session is untouched while the switch waits",
                contextualGeneration,
                ReviewProbe.controller!!.state.value.session
            )
            org.junit.Assert.assertEquals(
                TrainingKind.INTERVALS_IN_CONTEXT,
                ReviewProbe.controller!!.state.value.activeKind
            )
            client.waitFor("progressions active at the boundary") {
                val state = ReviewProbe.controller!!.state.value
                state.activeKind ==
                    TrainingKind.CHORD_PROGRESSIONS &&
                    state.phase == SessionPhase.PLAYING
            }

            // And back to intervals in context; the whole progression unit must finish first.
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Intervals in context") and hasClickAction()).performClick()
            client.waitFor("contextual intervals selected again") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.INTERVALS_IN_CONTEXT
            }
            client.waitFor("contextual intervals switch pending") {
                ReviewProbe.controller!!.state.value.pendingKind ==
                    TrainingKind.INTERVALS_IN_CONTEXT
            }
            val beforeContextual = ReviewProbe.controller!!.state.value
            client.waitFor("contextual intervals active at the boundary") {
                val state = ReviewProbe.controller!!.state.value
                state.activeKind ==
                    TrainingKind.INTERVALS_IN_CONTEXT &&
                    state.phase == SessionPhase.PLAYING
            }
            assertLastCommittedKindWasHeardInFull(
                beforeContextual,
                TrainingKind.CHORD_PROGRESSIONS
            )
            compose.onNodeWithContentDescription("Stop").assertIsDisplayed()
        }
    }

    @Test fun switchingBetweenNotesAndDegreesCommitsAtTheItemBoundary() {
        ReviewClient().use { client ->
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Perfect pitch") and hasClickAction()).performClick()
            client.waitFor("notes selected in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.NOTE_IDENTIFICATION
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Ready").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("notes playing") {
                val state = ReviewProbe.controller!!.state.value
                state.phase == SessionPhase.PLAYING &&
                    state.activeKind ==
                    TrainingKind.NOTE_IDENTIFICATION &&
                    state.consumedFrames > 0
            }
            val beforeNotes = ReviewProbe.controller!!.state.value

            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithContentDescription("Stop").fetchSemanticsNodes().isNotEmpty()
            }
            // The note item must finish in full before the degree switch commits.
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Scale degrees") and hasClickAction()).performClick()
            client.waitFor("degrees selected in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.SCALE_DEGREES
            }
            client.waitFor("degrees switch pending") {
                ReviewProbe.controller!!.state.value.pendingKind ==
                    TrainingKind.SCALE_DEGREES
            }
            org.junit.Assert.assertEquals(
                "the audible session is untouched while the switch waits",
                beforeNotes.session,
                ReviewProbe.controller!!.state.value.session
            )
            org.junit.Assert.assertEquals(
                TrainingKind.NOTE_IDENTIFICATION,
                ReviewProbe.controller!!.state.value.activeKind
            )
            client.waitFor("degrees active at the boundary") {
                val state = ReviewProbe.controller!!.state.value
                state.activeKind ==
                    TrainingKind.SCALE_DEGREES &&
                    state.phase == SessionPhase.PLAYING
            }
            assertLastCommittedKindWasHeardInFull(beforeNotes, TrainingKind.NOTE_IDENTIFICATION)

            // And back to notes; the whole degree example (context, target and answer) must have
            // been heard before the note item takes over.
            val beforeDegrees = ReviewProbe.controller!!.state.value
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Perfect pitch") and hasClickAction()).performClick()
            client.waitFor("notes selected again") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.NOTE_IDENTIFICATION
            }
            client.waitFor("notes switch pending again") {
                ReviewProbe.controller!!.state.value.pendingKind ==
                    TrainingKind.NOTE_IDENTIFICATION
            }
            client.waitFor("notes active at the boundary") {
                val state = ReviewProbe.controller!!.state.value
                state.activeKind ==
                    TrainingKind.NOTE_IDENTIFICATION &&
                    state.phase == SessionPhase.PLAYING
            }
            assertLastCommittedKindWasHeardInFull(beforeDegrees, TrainingKind.SCALE_DEGREES)
            compose.onNodeWithContentDescription("Stop").assertIsDisplayed()
        }
    }

    @Test fun switchingBetweenScalesAndIntervalsCommitsAtTheWholeTraversalBoundary() {
        ReviewClient().use { client ->
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Scales") and hasClickAction()).performClick()
            client.waitFor("scales selected in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.SCALES
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Ready").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("scales playing") {
                val state = ReviewProbe.controller!!.state.value
                state.phase == SessionPhase.PLAYING &&
                    state.activeKind ==
                    TrainingKind.SCALES &&
                    state.consumedFrames > 0
            }
            val before = ReviewProbe.controller!!.state.value

            // The drawer's switch path reads the screen's own active state; wait for the visible
            // transport before the switch click (the media callback precedes the state poll).
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithContentDescription("Stop").fetchSemanticsNodes().isNotEmpty()
            }
            // Switch to intervals while the scale is audible; the switch waits for the whole
            // traversal (and its spoken answer), never a turning tonic or intermediate note.
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Interval recognition") and hasClickAction()).performClick()
            client.waitFor("intervals selected in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.INTERVALS
            }
            client.waitFor("intervals switch pending") {
                ReviewProbe.controller!!.state.value.pendingKind ==
                    TrainingKind.INTERVALS
            }
            org.junit.Assert.assertEquals(
                "the audible session is untouched while the switch waits",
                before.session,
                ReviewProbe.controller!!.state.value.session
            )
            org.junit.Assert.assertEquals(
                TrainingKind.SCALES,
                ReviewProbe.controller!!.state.value.activeKind
            )
            client.waitFor("intervals active at the boundary") {
                val state = ReviewProbe.controller!!.state.value
                state.activeKind ==
                    TrainingKind.INTERVALS &&
                    state.phase == SessionPhase.PLAYING
            }
            assertLastCommittedKindWasHeardInFull(before, TrainingKind.SCALES)

            // And back to scales through the same boundary path.
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Scales") and hasClickAction()).performClick()
            client.waitFor("scales selected again") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.SCALES
            }
            client.waitFor("scales switch pending again") {
                ReviewProbe.controller!!.state.value.pendingKind ==
                    TrainingKind.SCALES
            }
            client.waitFor("scales active at the boundary") {
                val state = ReviewProbe.controller!!.state.value
                state.activeKind ==
                    TrainingKind.SCALES &&
                    state.phase == SessionPhase.PLAYING
            }
            compose.onNodeWithContentDescription("Stop").assertIsDisplayed()
        }
    }

    @Test fun switchingBetweenChordsAndScalesCommitsAtTheWholeTraversalBoundary() {
        ReviewClient().use { client ->
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Chords") and hasClickAction()).performClick()
            client.waitFor("chords selected in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.CHORDS
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Ready").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("chords playing") {
                val state = ReviewProbe.controller!!.state.value
                state.phase == SessionPhase.PLAYING &&
                    state.activeKind ==
                    TrainingKind.CHORDS &&
                    state.consumedFrames > 0
            }

            // The drawer's switch path reads the screen's own active state; wait for the visible
            // transport before the switch click (the media callback precedes the state poll).
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithContentDescription("Stop").fetchSemanticsNodes().isNotEmpty()
            }
            // Chords to scales.
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Scales") and hasClickAction()).performClick()
            client.waitFor("scales selected in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.SCALES
            }
            client.waitFor("scales switch pending") {
                ReviewProbe.controller!!.state.value.pendingKind ==
                    TrainingKind.SCALES
            }
            client.waitFor("scales active at the boundary") {
                val state = ReviewProbe.controller!!.state.value
                state.activeKind ==
                    TrainingKind.SCALES &&
                    state.phase == SessionPhase.PLAYING
            }

            // Back to chords while the scale is audible; the pending switch must wait for the
            // whole scale traversal before it commits.
            val before = ReviewProbe.controller!!.state.value
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Chords") and hasClickAction()).performClick()
            client.waitFor("chords selected again in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.CHORDS
            }
            client.waitFor("chords switch pending") {
                ReviewProbe.controller!!.state.value.pendingKind ==
                    TrainingKind.CHORDS
            }
            org.junit.Assert.assertEquals(
                "the scale stays audible while the switch waits",
                TrainingKind.SCALES,
                ReviewProbe.controller!!.state.value.activeKind
            )
            client.waitFor("chords active again at the boundary") {
                val state = ReviewProbe.controller!!.state.value
                state.activeKind ==
                    TrainingKind.CHORDS &&
                    state.phase == SessionPhase.PLAYING
            }
            assertLastCommittedKindWasHeardInFull(before, TrainingKind.SCALES)
            compose.onNodeWithContentDescription("Stop").assertIsDisplayed()
        }
    }

    @Test fun switchingBetweenIntervalsAndNotesCommitsAtTheItemBoundary() {
        ReviewClient().use { client ->
            // Start through the UI: the drawer's switch path only runs while the screen knows a
            // session is active, which is exactly the real user flow.
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Ready").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("intervals playing") {
                val state = ReviewProbe.controller!!.state.value
                state.phase == SessionPhase.PLAYING &&
                    state.activeKind ==
                    TrainingKind.INTERVALS &&
                    state.consumedFrames > 0
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithContentDescription("Stop").fetchSemanticsNodes().isNotEmpty()
            }
            val intervalsGeneration = ReviewProbe.controller!!.state.value.session
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Perfect pitch") and hasClickAction()).performClick()
            // The drawer selection is remembered immediately; the audible session switches only
            // when the current item reaches its boundary (M3).
            client.waitFor("notes selected in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.NOTE_IDENTIFICATION
            }
            client.waitFor("notes switch pending or refused") {
                val state = ReviewProbe.controller!!.state.value
                state.pendingKind ==
                    TrainingKind.NOTE_IDENTIFICATION ||
                    compose.onAllNodesWithText("cannot be activated", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
            }
            org.junit.Assert.assertEquals(
                "the switch must become pending, not silently refused",
                TrainingKind.NOTE_IDENTIFICATION,
                ReviewProbe.controller!!.state.value.pendingKind
            )
            org.junit.Assert.assertEquals(
                "the audible session is untouched while the switch waits",
                intervalsGeneration,
                ReviewProbe.controller!!.state.value.session
            )
            org.junit.Assert.assertEquals(
                TrainingKind.INTERVALS,
                ReviewProbe.controller!!.state.value.activeKind
            )
            client.waitFor("notes active at the boundary") {
                val state = ReviewProbe.controller!!.state.value
                state.activeKind ==
                    TrainingKind.NOTE_IDENTIFICATION &&
                    state.phase == SessionPhase.PLAYING
            }

            // And back to intervals through the same boundary path.
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Interval recognition") and hasClickAction()).performClick()
            client.waitFor("intervals selected in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.INTERVALS
            }
            client.waitFor("intervals switch pending or refused") {
                val state = ReviewProbe.controller!!.state.value
                state.pendingKind ==
                    TrainingKind.INTERVALS ||
                    compose.onAllNodesWithText("cannot be activated", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
            }
            org.junit.Assert.assertEquals(
                "the return switch must become pending, not silently refused",
                TrainingKind.INTERVALS,
                ReviewProbe.controller!!.state.value.pendingKind
            )
            client.waitFor("intervals active at the boundary") {
                val state = ReviewProbe.controller!!.state.value
                state.activeKind ==
                    TrainingKind.INTERVALS &&
                    state.phase == SessionPhase.PLAYING
            }
            compose.onNodeWithContentDescription("Stop").assertIsDisplayed()
        }
    }

    @Test fun switchingBetweenScalesAndNotesCommitsAtTheWholeTraversalBoundary() {
        ReviewClient().use { client ->
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Scales") and hasClickAction()).performClick()
            client.waitFor("scales selected in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.SCALES
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Ready").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("scales playing") {
                val state = ReviewProbe.controller!!.state.value
                state.phase == SessionPhase.PLAYING &&
                    state.activeKind ==
                    TrainingKind.SCALES &&
                    state.consumedFrames > 0
            }
            val before = ReviewProbe.controller!!.state.value

            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithContentDescription("Stop").fetchSemanticsNodes().isNotEmpty()
            }
            // Switch to notes while the scale is audible; the switch waits for the whole
            // traversal (and its spoken answer), never a turning tonic or intermediate note.
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Perfect pitch") and hasClickAction()).performClick()
            client.waitFor("notes selected in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.NOTE_IDENTIFICATION
            }
            client.waitFor("notes switch pending") {
                ReviewProbe.controller!!.state.value.pendingKind ==
                    TrainingKind.NOTE_IDENTIFICATION
            }
            org.junit.Assert.assertEquals(
                "the audible session is untouched while the switch waits",
                before.session,
                ReviewProbe.controller!!.state.value.session
            )
            org.junit.Assert.assertEquals(
                TrainingKind.SCALES,
                ReviewProbe.controller!!.state.value.activeKind
            )
            client.waitFor("notes active at the boundary") {
                val state = ReviewProbe.controller!!.state.value
                state.activeKind ==
                    TrainingKind.NOTE_IDENTIFICATION &&
                    state.phase == SessionPhase.PLAYING
            }
            assertLastCommittedKindWasHeardInFull(before, TrainingKind.SCALES)

            // And back to scales through the same boundary path.
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Scales") and hasClickAction()).performClick()
            client.waitFor("scales selected again") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.SCALES
            }
            client.waitFor("scales switch pending again") {
                ReviewProbe.controller!!.state.value.pendingKind ==
                    TrainingKind.SCALES
            }
            client.waitFor("scales active at the boundary") {
                val state = ReviewProbe.controller!!.state.value
                state.activeKind ==
                    TrainingKind.SCALES &&
                    state.phase == SessionPhase.PLAYING
            }
            compose.onNodeWithContentDescription("Stop").assertIsDisplayed()
        }
    }

    @Test fun hiddenScaleAnswersNeverReachThePlaySurface() {
        ReviewClient().use { client ->
            kotlinx.coroutines.runBlocking {
                client.app.settingsRepository.update {
                    it.copy(
                        answerVisibility =
                        AnswerVisibility.HIDDEN,
                        selectedKind =
                        TrainingKind.SCALES
                    )
                }
            }
            client.waitFor("scale kind active in settings") {
                client.app.settings.value.settings.selectedKind == TrainingKind.SCALES
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Ready").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("scale session running") {
                ReviewProbe.controller!!.state.value.activeKind == TrainingKind.SCALES
            }
            // Past the scale example and its 2 s thinking pause: the answer frame is consumed by
            // now, so a visible answer would already have been published on the surface.
            client.waitFor("scale answer position") {
                (ReviewProbe.controller?.state?.value?.consumedFrames ?: 0) > 420_000
            }
            compose.waitForIdle()
            val scaleId = currentScaleId() ?: error("the running item must be a generated scale")
            val spoken = VoiceVocabulary
                .scaleEntry(ScaleKind.ofIdOrNull(scaleId)!!)
                .spokenText
            org.junit.Assert.assertTrue(
                "a hidden answer must not appear on the Play surface",
                compose.onAllNodesWithText("Answer:", substring = true)
                    .fetchSemanticsNodes().isEmpty()
            )
            org.junit.Assert.assertTrue(
                "the generated scale name must not reach the Play surface before its answer",
                compose.onAllNodesWithText(spoken).fetchSemanticsNodes().isEmpty()
            )
        }
    }

    @Test fun hiddenNoteAnswersNeverReachThePlaySurface() {
        ReviewClient().use { client ->
            kotlinx.coroutines.runBlocking {
                client.app.settingsRepository.update {
                    it.copy(
                        answerVisibility =
                        AnswerVisibility.HIDDEN,
                        selectedKind =
                        TrainingKind.NOTE_IDENTIFICATION
                    )
                }
            }
            client.waitFor("note kind active in settings") {
                client.app.settings.value.settings.selectedKind == TrainingKind.NOTE_IDENTIFICATION
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Ready").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("note session running") {
                ReviewProbe.controller!!.state.value.activeKind == TrainingKind.NOTE_IDENTIFICATION
            }
            // Past the note example and its 2 s thinking pause: the answer frame is consumed by
            // now, so a visible answer would already have been published on the surface.
            client.waitFor("note answer position") {
                (ReviewProbe.controller?.state?.value?.consumedFrames ?: 0) > 200_000
            }
            compose.waitForIdle()
            val generated = currentGeneratedNote()
                ?: error("the running item must be a generated note")
            val spoken = NoteDefinition.answerTextFor(
                generated.midiNote,
                generated.accidental,
                generated.octaveSpoken
            )
            org.junit.Assert.assertTrue(
                "a hidden answer must not appear on the Play surface",
                compose.onAllNodesWithText("Answer:", substring = true)
                    .fetchSemanticsNodes().isEmpty()
            )
            org.junit.Assert.assertTrue(
                "the generated note must not reach the Play surface while it is hidden",
                compose.onAllNodesWithText(spoken).fetchSemanticsNodes().isEmpty()
            )
            org.junit.Assert.assertEquals(
                "the hidden answer is consumed while the session continues",
                SessionPhase.PLAYING,
                ReviewProbe.controller!!.state.value.phase
            )
        }
    }

    @Test fun hiddenDegreeAnswersNeverReachThePlaySurface() {
        ReviewClient().use { client ->
            kotlinx.coroutines.runBlocking {
                client.app.settingsRepository.update {
                    it.copy(
                        answerVisibility =
                        AnswerVisibility.HIDDEN,
                        selectedKind =
                        TrainingKind.SCALE_DEGREES
                    )
                }
            }
            client.waitFor("degree kind active in settings") {
                client.app.settings.value.settings.selectedKind == TrainingKind.SCALE_DEGREES
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Ready").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("degree session running") {
                ReviewProbe.controller!!.state.value.activeKind == TrainingKind.SCALE_DEGREES
            }
            // Past the degree context, gap, target and the 2 s thinking pause: the answer frame is
            // consumed by now, so a visible answer would already have been published.
            client.waitFor("degree answer position") {
                (ReviewProbe.controller?.state?.value?.consumedFrames ?: 0) > 200_000
            }
            compose.waitForIdle()
            val generated = currentGeneratedScaleDegree()
                ?: error("the running item must be a generated scale degree")
            val spoken = VoiceVocabulary.degreeNumberEntry(generated.degree).spokenText
            org.junit.Assert.assertFalse(
                "the hidden answer stays out of the session snapshot",
                ReviewProbe.controller!!.state.value.answerVisible
            )
            org.junit.Assert.assertEquals(
                null,
                ReviewProbe.controller!!.state.value.answerLabel
            )
            org.junit.Assert.assertTrue(
                "the state query must not name the hidden answer",
                client.answer().isNullOrEmpty()
            )
            org.junit.Assert.assertFalse(
                "the media metadata must not carry the hidden answer",
                client.main {
                    client.controller.mediaMetadata.extras
                        ?.containsKey(ANSWER_TEXT_EXTRA) == true
                }
            )
            org.junit.Assert.assertTrue(
                "a hidden answer must not appear on the Play surface",
                compose.onAllNodesWithText("Answer:", substring = true)
                    .fetchSemanticsNodes().isEmpty()
            )
            org.junit.Assert.assertTrue(
                "the generated degree must not reach the Play surface while it is hidden",
                compose.onAllNodesWithText(spoken).fetchSemanticsNodes().isEmpty()
            )
            org.junit.Assert.assertEquals(
                "the hidden answer is consumed while the session continues",
                SessionPhase.PLAYING,
                ReviewProbe.controller!!.state.value.phase
            )
        }
    }

    @Test fun hiddenProgressionAnswersNeverReachThePlaySurface() {
        ReviewClient().use { client ->
            kotlinx.coroutines.runBlocking {
                client.app.settingsRepository.update {
                    it.copy(
                        answerVisibility =
                        AnswerVisibility.HIDDEN,
                        selectedKind =
                        TrainingKind.CHORD_PROGRESSIONS
                    )
                }
            }
            client.waitFor("progression kind active in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.CHORD_PROGRESSIONS
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Ready").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("progression session running") {
                ReviewProbe.controller!!.state.value.activeKind ==
                    TrainingKind.CHORD_PROGRESSIONS
            }
            // Past the longest default progression example (four chords: 3.1 s) and its 2 s
            // thinking pause; the answer frame is consumed by now, so a visible answer would
            // already have been published on the surface.
            client.waitFor("progression answer position") {
                (ReviewProbe.controller?.state?.value?.consumedFrames ?: 0) > 300_000
            }
            compose.waitForIdle()
            val generated = currentGeneratedProgression()
                ?: error("the running item must be a generated progression")
            val spoken = generated.sequenceLabel
            org.junit.Assert.assertFalse(
                "the hidden answer stays out of the session snapshot",
                ReviewProbe.controller!!.state.value.answerVisible
            )
            org.junit.Assert.assertEquals(
                null,
                ReviewProbe.controller!!.state.value.answerLabel
            )
            org.junit.Assert.assertTrue(
                "the state query must not name the hidden answer",
                client.answer().isNullOrEmpty()
            )
            org.junit.Assert.assertFalse(
                "the media metadata must not carry the hidden answer",
                client.main {
                    client.controller.mediaMetadata.extras
                        ?.containsKey(ANSWER_TEXT_EXTRA) == true
                }
            )
            org.junit.Assert.assertTrue(
                "a hidden answer must not appear on the Play surface",
                compose.onAllNodesWithText("Answer:", substring = true)
                    .fetchSemanticsNodes().isEmpty()
            )
            org.junit.Assert.assertTrue(
                "the generated progression must not reach the Play surface while it is hidden",
                compose.onAllNodesWithText(spoken).fetchSemanticsNodes().isEmpty()
            )
            org.junit.Assert.assertEquals(
                "the hidden answer is consumed while the session continues",
                SessionPhase.PLAYING,
                ReviewProbe.controller!!.state.value.phase
            )
        }
    }

    @Test fun hiddenContextualIntervalAnswersNeverReachThePlaySurface() {
        ReviewClient().use { client ->
            kotlinx.coroutines.runBlocking {
                // Degree-pair answers are not safe configuration, so their two number tokens
                // must never appear on any surface while the answer is hidden.
                val repository = client.app.settingsRepository
                val revision = repository.settings.first().settings.modeSetups
                    .getValue(TrainingKind.INTERVALS_IN_CONTEXT).revision
                repository.updateMode(
                    ModeEdit(TrainingKind.INTERVALS_IN_CONTEXT, revision) { existing ->
                        val current = existing.payload as ModePayload.ContextualIntervals
                        ModePayload.ContextualIntervals(
                            current.setup.copy(
                                answerFormat = ContextualIntervalAnswerFormat.DEGREE_PAIR
                            )
                        )
                    }
                )
                repository.update {
                    it.copy(
                        answerVisibility =
                        AnswerVisibility.HIDDEN,
                        selectedKind =
                        TrainingKind.INTERVALS_IN_CONTEXT
                    )
                }
            }
            client.waitFor("contextual-interval kind active in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.INTERVALS_IN_CONTEXT
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Ready").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("contextual-interval session running") {
                ReviewProbe.controller!!.state.value.activeKind ==
                    TrainingKind.INTERVALS_IN_CONTEXT
            }
            // Past the default example and the 2 s thinking pause: the answer frame is consumed
            // by now, so a visible answer would already have been published on the surface.
            client.waitFor("contextual-interval answer position") {
                (ReviewProbe.controller?.state?.value?.consumedFrames ?: 0) > 260_000
            }
            compose.waitForIdle()
            val generated = currentGeneratedContextualInterval()
                ?: error("the running item must be a generated contextual interval")
            val first = VoiceVocabulary.degreeNumberEntry(generated.firstDegree).spokenText
            val second = VoiceVocabulary.degreeNumberEntry(generated.secondDegree).spokenText
            org.junit.Assert.assertFalse(
                "the hidden answer stays out of the session snapshot",
                ReviewProbe.controller!!.state.value.answerVisible
            )
            org.junit.Assert.assertEquals(
                null,
                ReviewProbe.controller!!.state.value.answerLabel
            )
            org.junit.Assert.assertTrue(
                "the state query must not name the hidden answer",
                client.answer().isNullOrEmpty()
            )
            org.junit.Assert.assertFalse(
                "the media metadata must not carry the hidden answer",
                client.main {
                    client.controller.mediaMetadata.extras
                        ?.containsKey(ANSWER_TEXT_EXTRA) == true
                }
            )
            org.junit.Assert.assertTrue(
                "a hidden answer must not appear on the Play surface",
                compose.onAllNodesWithText("Answer:", substring = true)
                    .fetchSemanticsNodes().isEmpty()
            )
            org.junit.Assert.assertTrue(
                "the first hidden degree token must not reach the Play surface",
                compose.onAllNodesWithText(first).fetchSemanticsNodes().isEmpty()
            )
            org.junit.Assert.assertTrue(
                "the second hidden degree token must not reach the Play surface",
                compose.onAllNodesWithText(second).fetchSemanticsNodes().isEmpty()
            )
            org.junit.Assert.assertEquals(
                "the hidden answer is consumed while the session continues",
                SessionPhase.PLAYING,
                ReviewProbe.controller!!.state.value.phase
            )
        }
    }

    @Test fun melodicDictationCanBeSelectedStartedAndStopped() {
        ReviewClient().use { client ->
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Melodic dictation") and hasClickAction()).performClick()
            compose.waitForIdle()
            client.waitFor("melodic dictation selected in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.MELODIC_DICTATION
            }
            // The Play summary, the setup editor and the starter preset are the real melodic
            // surfaces; the summary names only safe configuration, never the current melody or
            // its answer.
            compose.onNodeWithText("Keys: C").assertExists()
            compose.onNodeWithText("Tonalities: major").assertExists()
            compose.onNodeWithText("Context: tonic triad · gap 0.50 s").assertExists()
            compose.onNodeWithText(
                "Phrase: 4 notes · start degree 1 · end degree 1"
            ).assertExists()
            compose.onNodeWithText("Steps: -5 to 5 · leap 5 · bias balanced").assertExists()
            compose.onNodeWithText("Answer format: degree sequence").assertExists()
            compose.onNodeWithText("Range: C4 to C5 · thinking 2 s · repeats 0").assertExists()
            compose.onNodeWithText("Setup").performClick()
            compose.onNodeWithText("Melodic dictation setup").assertExists()
            compose.onNodeWithText("0 (repeat)").assertExists()
            compose.onNodeWithText("Directed interval sequence").assertExists()
            compose.onNodeWithText("Presets").performClick()
            compose.onNodeWithText(
                "Tonic-to-tonic four-note phrases (starter)"
            ).performScrollTo().assertExists()
            compose.onNodeWithText("Play").performClick()

            // Start through the UI, verify a melodic session actually runs, then Stop it again.
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Ready").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("melodic dictation playing") {
                val state = ReviewProbe.controller!!.state.value
                state.phase == SessionPhase.PLAYING &&
                    state.activeKind == TrainingKind.MELODIC_DICTATION &&
                    state.consumedFrames > 0
            }
            // The drawer's switch path waits for the screen's own active state; wait for the
            // visible transport before opening the drawer (the media callback precedes the poll).
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithContentDescription("Stop").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Stop").assertIsDisplayed()
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("melodic dictation stopped") {
                ReviewProbe.controller!!.state.value.phase in
                    listOf(SessionPhase.IDLE, SessionPhase.COMPLETED)
            }
        }
    }

    @Test fun switchingBetweenIntervalsAndMelodicDictationCommitsAtTheItemBoundary() {
        ReviewClient().use { client ->
            // Start in the new mode through the UI, then switch to interval recognition and back;
            // each pending switch must wait for the whole committed learning unit.
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Melodic dictation") and hasClickAction()).performClick()
            client.waitFor("melodic dictation selected in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.MELODIC_DICTATION
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Ready").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("melodic dictation playing") {
                val state = ReviewProbe.controller!!.state.value
                state.phase == SessionPhase.PLAYING &&
                    state.activeKind == TrainingKind.MELODIC_DICTATION &&
                    state.consumedFrames > 0
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithContentDescription("Stop").fetchSemanticsNodes().isNotEmpty()
            }
            val melodyGeneration = ReviewProbe.controller!!.state.value.session
            val beforeMelody = ReviewProbe.controller!!.state.value
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Interval recognition") and hasClickAction()).performClick()
            // The drawer selection is remembered immediately; the audible session switches only
            // when the current item reaches its boundary (M3).
            client.waitFor("intervals selected in settings") {
                client.app.settings.value.settings.selectedKind == TrainingKind.INTERVALS
            }
            client.waitFor("intervals switch pending or refused") {
                val state = ReviewProbe.controller!!.state.value
                state.pendingKind == TrainingKind.INTERVALS ||
                    compose.onAllNodesWithText("cannot be activated", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
            }
            org.junit.Assert.assertEquals(
                "the switch must become pending, not silently refused",
                TrainingKind.INTERVALS,
                ReviewProbe.controller!!.state.value.pendingKind
            )
            org.junit.Assert.assertEquals(
                "the audible session is untouched while the switch waits",
                melodyGeneration,
                ReviewProbe.controller!!.state.value.session
            )
            org.junit.Assert.assertEquals(
                TrainingKind.MELODIC_DICTATION,
                ReviewProbe.controller!!.state.value.activeKind
            )
            client.waitFor("intervals active at the boundary") {
                val state = ReviewProbe.controller!!.state.value
                state.activeKind == TrainingKind.INTERVALS &&
                    state.phase == SessionPhase.PLAYING
            }
            // The committed melodic item is one whole unit: the complete context, the phrase and
            // the spoken answer, never a split fragment.
            assertLastCommittedKindWasHeardInFull(beforeMelody, TrainingKind.MELODIC_DICTATION)

            // And back to melodic dictation through the same boundary path.
            compose.onNodeWithContentDescription("Open the mode sidebar").performClick()
            compose.onNode(hasText("Melodic dictation") and hasClickAction()).performClick()
            client.waitFor("melodic dictation selected again") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.MELODIC_DICTATION
            }
            client.waitFor("melodic dictation switch pending or refused") {
                val state = ReviewProbe.controller!!.state.value
                state.pendingKind == TrainingKind.MELODIC_DICTATION ||
                    compose.onAllNodesWithText("cannot be activated", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
            }
            org.junit.Assert.assertEquals(
                "the return switch must become pending, not silently refused",
                TrainingKind.MELODIC_DICTATION,
                ReviewProbe.controller!!.state.value.pendingKind
            )
            client.waitFor("melodic dictation active at the boundary") {
                val state = ReviewProbe.controller!!.state.value
                state.activeKind == TrainingKind.MELODIC_DICTATION &&
                    state.phase == SessionPhase.PLAYING
            }
            compose.onNodeWithContentDescription("Stop").assertIsDisplayed()
        }
    }

    @Test fun hiddenMelodicDictationAnswersNeverReachThePlaySurface() {
        ReviewClient().use { client ->
            kotlinx.coroutines.runBlocking {
                // Degree-sequence answers are the composed melody, so their tokens must never
                // appear on any surface while the answer is hidden.
                client.app.settingsRepository.update {
                    it.copy(
                        answerVisibility = AnswerVisibility.HIDDEN,
                        selectedKind = TrainingKind.MELODIC_DICTATION
                    )
                }
            }
            client.waitFor("melodic dictation kind active in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.MELODIC_DICTATION
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Ready").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("melodic dictation session running") {
                ReviewProbe.controller!!.state.value.activeKind ==
                    TrainingKind.MELODIC_DICTATION
            }
            // Past the default example (tonic triad, context gap and four phrase notes) and the
            // 2 s thinking pause: the answer frame is consumed by now, so a visible answer would
            // already have been published on the surface.
            client.waitFor("melodic dictation answer position") {
                (ReviewProbe.controller?.state?.value?.consumedFrames ?: 0) > 400_000
            }
            compose.waitForIdle()
            val generated = currentGeneratedMelody()
                ?: error("the running item must be a generated melody")
            val spoken = generated.notes.joinToString(" ") { note ->
                VoiceVocabulary.degreeNumberEntry(note.degree).spokenText
            }
            org.junit.Assert.assertFalse(
                "the hidden answer stays out of the session snapshot",
                ReviewProbe.controller!!.state.value.answerVisible
            )
            org.junit.Assert.assertEquals(
                null,
                ReviewProbe.controller!!.state.value.answerLabel
            )
            org.junit.Assert.assertTrue(
                "the state query must not name the hidden answer",
                client.answer().isNullOrEmpty()
            )
            org.junit.Assert.assertFalse(
                "the media metadata must not carry the hidden answer",
                client.main {
                    client.controller.mediaMetadata.extras
                        ?.containsKey(ANSWER_TEXT_EXTRA) == true
                }
            )
            org.junit.Assert.assertTrue(
                "a hidden answer must not appear on the Play surface",
                compose.onAllNodesWithText("Answer:", substring = true)
                    .fetchSemanticsNodes().isEmpty()
            )
            org.junit.Assert.assertTrue(
                "the generated melody must not reach the Play surface while it is hidden",
                compose.onAllNodesWithText(spoken).fetchSemanticsNodes().isEmpty()
            )
            org.junit.Assert.assertTrue(
                "the revealed answer line must not reach the Play surface while it is hidden",
                compose.onAllNodesWithText("Answer: $spoken").fetchSemanticsNodes().isEmpty()
            )
            org.junit.Assert.assertEquals(
                "the hidden answer is consumed while the session continues",
                SessionPhase.PLAYING,
                ReviewProbe.controller!!.state.value.phase
            )
        }
    }

    @Test fun melodicDictationLongAnswersStayInsideTheWindowAtLargeText() {
        ReviewClient().use { client ->
            kotlinx.coroutines.runBlocking {
                // A 16-note phrase makes the longest degree answer the mode can compose; the
                // short timings keep a full item inside the bounded waits.
                val repository = client.app.settingsRepository
                val revision = repository.settings.first().settings.modeSetups
                    .getValue(TrainingKind.MELODIC_DICTATION).revision
                repository.updateMode(
                    ModeEdit(TrainingKind.MELODIC_DICTATION, revision) { existing ->
                        val current = existing.payload as ModePayload.MelodicDictation
                        ModePayload.MelodicDictation(
                            current.setup.copy(
                                keyValues = listOf(0),
                                tonalities = listOf(Tonality.MAJOR),
                                context = ContextKind.TONIC_NOTE,
                                contextGap = kotlin.time.Duration.parse("0ms"),
                                phraseLength = 16,
                                startDegree = null,
                                endDegree = null,
                                allowedSteps = (-5..5).toList(),
                                maximumLeap = 5,
                                mode = RecipeMode.RECOGNITION,
                                thinkingSilence = kotlin.time.Duration.parse("500ms"),
                                noteDuration = kotlin.time.Duration.parse("300ms"),
                                melodicGap = kotlin.time.Duration.parse("0ms")
                            )
                        )
                    }
                )
                repository.update {
                    it.copy(
                        selectedKind = TrainingKind.MELODIC_DICTATION,
                        answerVisibility = AnswerVisibility.SHOWN_WHEN_CONSUMED
                    )
                }
            }
            client.waitFor("melodic dictation kind active in settings") {
                client.app.settings.value.settings.selectedKind ==
                    TrainingKind.MELODIC_DICTATION
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Ready").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("play_stop").performClick()
            client.waitFor("melodic dictation playing") {
                val state = ReviewProbe.controller!!.state.value
                state.phase == SessionPhase.PLAYING &&
                    state.activeKind == TrainingKind.MELODIC_DICTATION &&
                    state.consumedFrames > 0
            }
            for (scale in listOf(1.5f, 2f)) {
                ReviewProbe.fontScale = scale
                compose.activityRule.scenario.recreate()
                compose.waitForIdle()
                // Wait for a consumed answer while at this font scale; the session keeps
                // producing 16-note items.
                client.waitFor("long melodic answer consumed at ${scale}x") {
                    val state = ReviewProbe.controller!!.state.value
                    state.answerVisible && state.answerLabel != null
                }
                compose.waitUntil(timeoutMillis = 10_000) {
                    compose.onAllNodesWithText("Answer:", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
                }
                val answer = ReviewProbe.controller!!.state.value.answerLabel
                    ?: error("the long answer must be published")
                org.junit.Assert.assertTrue(
                    "the 16-note answer must carry one token per note",
                    answer.split(' ').size >= 16
                )
                // The long answer wraps and scrolls in its bounded region, and the transport
                // stays displayed: neither overflows the window at this font scale.
                compose.onNodeWithText("Answer: $answer").assertIsDisplayed()
                compose.onNodeWithTag("play_stop").assertIsDisplayed()
            }
        }
    }
}

/**
 * Asserts the pending switch waited for the whole learning unit it interrupted.
 *
 * The controller records the committed item in its private history and only commits at a
 * mandatory-unit boundary at the earliest; the frames consumed between [before] and the commit
 * must therefore cover that unit (for scales the complete traversal plus its spoken answer, for
 * scale degrees the context–target–answer example and any association repeat).
 */
private fun assertLastCommittedKindWasHeardInFull(before: SessionSnapshot, kind: TrainingKind) {
    val controller = ReviewProbe.controller ?: error("no session controller")
    val historyField = controller.javaClass.getDeclaredField("history").apply {
        isAccessible = true
    }
    val lastItem = (historyField.get(controller) as Collection<*>).last()!!
    val exerciseField = lastItem.javaClass.getDeclaredField("exercise")
        .apply { isAccessible = true }
    org.junit.Assert.assertEquals(
        "the switch must wait for a $kind item",
        kind,
        (exerciseField.get(lastItem) as ConcreteExercise).kind
    )
    val mandatoryEnd = lastItem.javaClass.getDeclaredField("mandatoryEndFrame")
        .apply { isAccessible = true }.getLong(lastItem)
    val after = controller.state.value
    val traversed = before.consumedFrames + (after.playedDuration - before.playedDuration)
        .inWholeMicroseconds * REVIEW_SAMPLE_RATE / 1_000_000L
    // The played-duration projection rounds to whole microseconds, so allow two frames.
    org.junit.Assert.assertTrue(
        "$kind traversal must finish before the switch commits " +
            "(traversed=$traversed, mandatoryEnd=$mandatoryEnd)",
        traversed >= mandatoryEnd - 2
    )
}

/** The canonical id of the scale the controller is currently playing, or null. */
private fun currentScaleId(): String? {
    val controller = ReviewProbe.controller ?: return null
    val currentField = controller.javaClass.getDeclaredField("current").apply {
        isAccessible = true
    }
    val item = currentField.get(controller) ?: return null
    val exerciseField = item.javaClass.getDeclaredField("exercise").apply { isAccessible = true }
    return (exerciseField.get(item) as ConcreteExercise).provenance.generatedScale?.scaleId
}

/** The generated note of the item the controller is currently playing, or null. */
private fun currentGeneratedNote(): GeneratedNote? {
    val controller = ReviewProbe.controller ?: return null
    val currentField = controller.javaClass.getDeclaredField("current").apply {
        isAccessible = true
    }
    val item = currentField.get(controller) ?: return null
    val exerciseField = item.javaClass.getDeclaredField("exercise").apply { isAccessible = true }
    return (exerciseField.get(item) as ConcreteExercise).provenance.generatedNote
}

/** The generated scale degree of the item the controller is currently playing, or null. */
private fun currentGeneratedScaleDegree(): GeneratedScaleDegree? {
    val controller = ReviewProbe.controller ?: return null
    val currentField = controller.javaClass.getDeclaredField("current").apply {
        isAccessible = true
    }
    val item = currentField.get(controller) ?: return null
    val exerciseField = item.javaClass.getDeclaredField("exercise").apply { isAccessible = true }
    return (exerciseField.get(item) as ConcreteExercise).provenance.generatedScaleDegree
}

/** The generated chord progression of the item the controller is currently playing, or null. */
private fun currentGeneratedProgression(): GeneratedProgression? {
    val controller = ReviewProbe.controller ?: return null
    val currentField = controller.javaClass.getDeclaredField("current").apply {
        isAccessible = true
    }
    val item = currentField.get(controller) ?: return null
    val exerciseField = item.javaClass.getDeclaredField("exercise").apply { isAccessible = true }
    return (exerciseField.get(item) as ConcreteExercise).provenance.generatedProgression
}

/** The generated contextual interval of the item the controller is currently playing, or null. */
private fun currentGeneratedContextualInterval(): GeneratedContextualInterval? {
    val controller = ReviewProbe.controller ?: return null
    val currentField = controller.javaClass.getDeclaredField("current").apply {
        isAccessible = true
    }
    val item = currentField.get(controller) ?: return null
    val exerciseField = item.javaClass.getDeclaredField("exercise").apply { isAccessible = true }
    return (exerciseField.get(item) as ConcreteExercise).provenance.generatedContextualInterval
}

/** The generated melody of the item the controller is currently playing, or null. */
private fun currentGeneratedMelody(): GeneratedMelody? {
    val controller = ReviewProbe.controller ?: return null
    val currentField = controller.javaClass.getDeclaredField("current").apply {
        isAccessible = true
    }
    val item = currentField.get(controller) ?: return null
    val exerciseField = item.javaClass.getDeclaredField("exercise").apply { isAccessible = true }
    return (exerciseField.get(item) as ConcreteExercise).provenance.generatedMelody
}

/** The review audio pipeline is fixed to 48 kHz mono. */
private const val REVIEW_SAMPLE_RATE = 48_000L
