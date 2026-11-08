package com.thomas.passiveeartraining.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.thomas.passiveeartraining.BuildConfig
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.settings.UserSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Help page: the purpose sentence, the start/learning sections, one entry per mode using the
 * shared summaries, the range and sound guidance, and the packed build version. No screenshot
 * capture here; the page is review evidence.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class HelpScreenHostTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theHelpPageExplainsTheAppAndListsEveryModeAndTheBuild() {
        compose.setContent {
            PassiveEarTrainingTheme(UserSettings.DEFAULTS) { HelpScreen() }
        }
        compose.onNodeWithText("About EarBy").assertExists()
        compose.onNodeWithText("passive ear training", substring = true).assertExists()
        compose.onNodeWithText("The nine modes").assertExists()
        compose.onNodeWithText("Privacy").assertExists()
        compose.onNodeWithText("How to use it").assertExists()
        compose.onNodeWithText("Three ways to listen").assertExists()
        compose.onNodeWithText("Ranges and answers").assertExists()
        compose.onNodeWithText("Presets and sound").assertExists()
        compose.onNodeWithText("Credits").assertExists()
        compose.onNodeWithText("hand-drawn by Dr Ham Hamster", substring = true).assertExists()
        compose.onNodeWithText("roots or key tonics only", substring = true).assertExists()
        compose.onNodeWithText("0 to 100 percent", substring = true).assertExists()
        TrainingKind.ALL.forEach { kind ->
            compose.onAllNodesWithText(kind.label).assertCountEquals(1)
            compose.onAllNodesWithText(kind.summary).assertCountEquals(1)
            compose.onAllNodesWithText(kind.audioDescription).assertCountEquals(1)
        }
        // The three recipes are explained in the introduction.
        compose.onNodeWithText("Recognition plays the example", substring = true).assertExists()
        compose.onNodeWithText("Exposure speaks the answer first", substring = true).assertExists()
        compose.onNodeWithText(
            "Association plays the example again",
            substring = true
        ).assertExists()
        compose.onNodeWithText("Build").assertExists()
        compose.onNodeWithText(
            "EarBy ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
        ).assertExists()
    }
}
