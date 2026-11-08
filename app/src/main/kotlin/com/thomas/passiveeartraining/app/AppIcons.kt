package com.thomas.passiveeartraining.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The app's own vector icons.
 *
 * A font glyph can render at different optical sizes on different devices; the bottom bar uses
 * these vectors so every tab is exactly the same size everywhere.
 */
internal object AppIcons {
    /** An eighth note, used by the Play tab at the same 24 dp size as its siblings. */
    val MusicNote: ImageVector by lazy {
        ImageVector.Builder(
            name = "MusicNote",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Note head.
                moveTo(8.4f, 20.0f)
                curveTo(5.9f, 20.0f, 4.0f, 18.4f, 4.0f, 16.4f)
                curveTo(4.0f, 14.4f, 5.9f, 12.8f, 8.4f, 12.8f)
                curveTo(10.9f, 12.8f, 12.8f, 14.4f, 12.8f, 16.4f)
                curveTo(12.8f, 18.4f, 10.9f, 20.0f, 8.4f, 20.0f)
                close()
                // Stem.
                moveTo(11.2f, 4.0f)
                lineTo(12.8f, 4.0f)
                lineTo(12.8f, 16.0f)
                lineTo(11.2f, 16.0f)
                close()
                // Flag.
                moveTo(12.8f, 4.4f)
                curveTo(16.4f, 5.6f, 18.4f, 8.2f, 17.8f, 11.8f)
                curveTo(17.0f, 9.6f, 15.4f, 8.2f, 12.8f, 7.4f)
                close()
            }
        }.build()
    }
}
