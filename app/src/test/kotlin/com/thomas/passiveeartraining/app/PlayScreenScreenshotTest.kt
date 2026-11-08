package com.thomas.passiveeartraining.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.thomas.passiveeartraining.training.settings.LastSetup
import com.thomas.passiveeartraining.training.settings.UserSettings
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Play-tab screenshots for the UI regression harness.
 *
 * The screen is rendered from fake immutable state, so no service, ViewModel or device is needed.
 * Images are written to local evidence for human review; they are not baselines. Baselines are
 * established only by the explicit recording task after that review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class PlayScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * Where the images are written.
     *
     * The default is the reviewed baseline folder; a review run redirects it to a candidates
     * folder with -Pear.screenshots.dir=... so a first or new image can be approved before it
     * becomes a baseline. -Pear.screenshots.theme=dark renders the dark review set instead.
     */
    private val screenshotsDir = File(
        System.getProperty("ear.screenshots.dir") ?: "src/test/screenshots"
    )

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        PassiveEarTrainingTheme(reviewCaptureSettings()) { ReviewCaptureSurface { content() } }
    }

    @Test
    fun theIdlePlayTabRenders() {
        compose.setContent {
            theme {
                PlayContent(
                    state = PlaybackUiState(connected = true),
                    setup = LastSetup.default(),
                    musicGain = UserSettings.DEFAULT_MUSIC_GAIN,
                    voiceGain = UserSettings.DEFAULT_VOICE_GAIN,
                    onPlay = {},
                    onStop = {},
                    onSleepChoice = {}
                )
            }
        }
        compose.onNodeWithTag("play_stop").assertExists()
        compose.onRoot().captureRoboImage(File(screenshotsDir, "play-idle.png"))
    }

    @Test
    fun aConsumedAnswerIsShownWhilePlaying() {
        compose.setContent {
            theme {
                PlayContent(
                    state = PlaybackUiState(
                        connected = true,
                        playing = true,
                        active = true,
                        answerText = "Major third",
                        sleepTimerMinutes = 5,
                        sleepTimerRemainingMs = 299_000
                    ),
                    setup = LastSetup.default(),
                    musicGain = UserSettings.DEFAULT_MUSIC_GAIN,
                    voiceGain = UserSettings.DEFAULT_VOICE_GAIN,
                    onPlay = {},
                    onStop = {},
                    onSleepChoice = {}
                )
            }
        }
        compose.onNodeWithText("Answer: Major third").assertExists()
        compose.onRoot().captureRoboImage(File(screenshotsDir, "play-answer.png"))
    }

    @Test
    fun theCurrentNotesAppearInTheSetupCard() {
        compose.setContent {
            theme {
                PlayContent(
                    state = PlaybackUiState(
                        connected = true,
                        playing = true,
                        active = true,
                        currentNotes = listOf(60, 64, 67)
                    ),
                    setup = LastSetup.default(),
                    musicGain = UserSettings.DEFAULT_MUSIC_GAIN,
                    voiceGain = UserSettings.DEFAULT_VOICE_GAIN,
                    onPlay = {},
                    onStop = {},
                    onSleepChoice = {}
                )
            }
        }
        compose.onNodeWithText("Notes: C4, E4, G4").assertExists()
    }

    @Test
    fun thePlayTabRendersAtTwoHundredPercentText() {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = 2f)
            ) {
                theme {
                    PlayContent(
                        state = PlaybackUiState(connected = true),
                        setup = LastSetup.default(),
                        musicGain = UserSettings.DEFAULT_MUSIC_GAIN,
                        voiceGain = UserSettings.DEFAULT_VOICE_GAIN,
                        onPlay = {},
                        onStop = {},
                        onSleepChoice = {}
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage(File(screenshotsDir, "play-large-text.png"))
    }

    @Test
    fun theSleepTimerPickerDialogRenders() {
        compose.setContent {
            theme {
                PlayContent(
                    state = PlaybackUiState(connected = true),
                    setup = LastSetup.default(),
                    musicGain = UserSettings.DEFAULT_MUSIC_GAIN,
                    voiceGain = UserSettings.DEFAULT_VOICE_GAIN,
                    onPlay = {},
                    onStop = {},
                    onSleepChoice = {}
                )
            }
        }
        compose.onNodeWithTag("sleep_picker").performClick()
        compose.onNodeWithText("15 min").assertExists()
        compose.onNodeWithText("OK").assertExists()
        compose.onRoot().captureRoboImage(File(screenshotsDir, "play-sleep-picker.png"))
    }
}
