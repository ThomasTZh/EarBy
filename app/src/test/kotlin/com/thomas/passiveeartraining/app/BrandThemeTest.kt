package com.thomas.passiveeartraining.app

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner decision of 2026-10-14: #89E2C5 in the light theme, and a darker colour with the
 * same hue and saturation in the dark theme.
 */
class BrandThemeTest {

    @Test
    fun theLightPrimaryIsTheRequestedColour() {
        val primary = brandLightColorScheme().primary
        assertEquals(0x89 / 255f, primary.red, 0.002f)
        assertEquals(0xE2 / 255f, primary.green, 0.002f)
        assertEquals(0xC5 / 255f, primary.blue, 0.002f)
    }

    @Test
    fun theDarkPrimaryKeepsTheHueAndSaturationAndIsDarker() {
        val light = toHsl(brandLightColorScheme().primary)
        val dark = toHsl(brandDarkColorScheme().primary)
        assertTrue("the hue must match (${light.h} vs ${dark.h})", abs(light.h - dark.h) < 0.5f)
        assertTrue(
            "the saturation must match (${light.s} vs ${dark.s})",
            abs(light.s - dark.s) < 0.01f
        )
        assertTrue("the dark anchor must be darker", dark.l < light.l)
    }

    private data class Hsl(val h: Float, val s: Float, val l: Float)

    private fun toHsl(color: Color): Hsl {
        val max = maxOf(color.red, color.green, color.blue)
        val min = minOf(color.red, color.green, color.blue)
        val l = (max + min) / 2f
        if (max == min) return Hsl(0f, 0f, l)
        val delta = max - min
        val s = if (l > 0.5f) delta / (2f - max - min) else delta / (max + min)
        val h = when (max) {
            color.red -> (color.green - color.blue) / delta +
                (if (color.green < color.blue) 6f else 0f)
            color.green -> (color.blue - color.red) / delta + 2f
            else -> (color.red - color.green) / delta + 4f
        } * 60f
        return Hsl(h, s, l)
    }
}
