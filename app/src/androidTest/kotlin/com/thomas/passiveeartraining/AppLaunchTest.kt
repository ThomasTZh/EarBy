package com.thomas.passiveeartraining

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

/** The launcher opens the training screen and offers the transport controls. */
class AppLaunchTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun launcherDisplaysTheTrainingScreen() {
        // The screen is the session surface now: playback belongs to the media service, so the
        // shell shows the transport controls rather than the retired diagnostic proof.
        compose.onNodeWithTag("play_stop").assertIsDisplayed()
        compose.onNodeWithContentDescription("Start").assertIsDisplayed()
    }
}
