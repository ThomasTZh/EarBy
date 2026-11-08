package com.thomas.passiveeartraining.app

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.thomas.passiveeartraining.training.settings.ThemePreference
import com.thomas.passiveeartraining.training.settings.UserSettings

/**
 * Settings for host review captures.
 *
 * The default is the product default (the brand light theme). A QA run that re-captures the dark
 * review set passes `-Pear.screenshots.theme=dark`; the capture then renders with the same dark
 * scheme the app's theme preference selects. Captures are always review candidates, never
 * baselines, so neither value approves anything.
 */
internal fun reviewCaptureSettings(): UserSettings =
    if (System.getProperty("ear.screenshots.theme") == "dark") {
        UserSettings.DEFAULTS.copy(theme = ThemePreference.DARK)
    } else {
        UserSettings.DEFAULTS
    }

/**
 * The surface a review capture sits on.
 *
 * The light capture is the committed one, so it stays a pass-through and remains byte-identical.
 * The dark review artifact paints the theme's own background, matching the Scaffold color the app
 * provides, because the bare Robolectric window would otherwise stay white and misreport the dark
 * scheme.
 */
@Composable
internal fun ReviewCaptureSurface(content: @Composable () -> Unit) {
    if (System.getProperty("ear.screenshots.theme") == "dark") {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box { content() }
        }
    } else {
        content()
    }
}
