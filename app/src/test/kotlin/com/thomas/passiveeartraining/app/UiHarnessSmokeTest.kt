package com.thomas.passiveeartraining.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Smoke test for the host UI harness.
 *
 * It proves that Robolectric can render a real Compose surface on the host and that Roborazzi can
 * capture it. The image is written under `build/`, never into the reviewed baseline folder, and the
 * capture is skipped while verifying: verification compares only the approved nine-image set, and a
 * missing scratch file must not fail a fresh checkout.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class UiHarnessSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    /** True while `verifyRoborazziDebug` compares the approved images. */
    private val verifying: Boolean get() = System.getProperty("roborazzi.test.verify") == "true"

    @Test
    fun aComposeSurfaceRendersAndCaptures() {
        compose.setContent {
            MaterialTheme {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Passive Ear Training")
                    Button(onClick = {}) { Text("Play") }
                }
            }
        }
        compose.onNodeWithText("Play").assertIsDisplayed()
        if (!verifying) {
            compose.onRoot().captureRoboImage(File("build/roborazzi/ui-harness-smoke.png"))
        }
    }
}
