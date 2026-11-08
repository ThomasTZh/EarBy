package com.thomas.passiveeartraining.app

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The EarBy brand palette.
 *
 * Owner decision 2026-10-14: the light theme colour is #89E2C5 (a mint/teal green); the dark
 * theme colour keeps the same hue and saturation and lowers the lightness. The palette derives
 * every role, including the neutral surfaces, from that one hue so no default Material lavender
 * or wallpaper colour leaks into the app.
 */
private const val BRAND_HUE = 160.45f
private const val BRAND_SATURATION = 0.605f

/** Lightness of the requested light anchor; the dark anchor is the same hue and saturation. */
private const val LIGHT_LIGHTNESS = 0.712f

/** The dark anchor: same hue and saturation, darker (roughly a third darker than the light one). */
private const val DARK_LIGHTNESS = 0.45f

private val LightPrimary: Color = Color.hsl(BRAND_HUE, BRAND_SATURATION, LIGHT_LIGHTNESS)
private val DarkPrimary: Color = Color.hsl(BRAND_HUE, BRAND_SATURATION, DARK_LIGHTNESS)

/** A neutral surface tone: the same hue at low saturation, so greys read as mint-tinted. */
private fun neutral(lightness: Float, saturation: Float = 0.08f): Color =
    Color.hsl(BRAND_HUE, saturation, lightness)

/** The outlined-button colours that pair dark control text with the theme's outline. */
@Composable
internal fun darkOutlinedButtonColors(): ButtonColors =
    ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)

/** The text-button colours that pair the dark control text with the theme's surfaces. */
@Composable
internal fun darkTextButtonColors(): ButtonColors =
    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)

/** The light scheme: the requested colour as primary, with mint-tinted neutrals. */
internal fun brandLightColorScheme(): ColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.hsl(BRAND_HUE, BRAND_SATURATION, 0.12f),
    primaryContainer = Color.hsl(BRAND_HUE, BRAND_SATURATION, 0.88f),
    onPrimaryContainer = Color.hsl(BRAND_HUE, BRAND_SATURATION, 0.16f),
    inversePrimary = DarkPrimary,
    secondary = Color.hsl(BRAND_HUE, 0.30f, 0.35f),
    onSecondary = Color.hsl(BRAND_HUE, 0.20f, 0.98f),
    secondaryContainer = Color.hsl(BRAND_HUE, 0.35f, 0.86f),
    onSecondaryContainer = Color.hsl(BRAND_HUE, 0.35f, 0.15f),
    tertiary = Color.hsl(BRAND_HUE, 0.35f, 0.30f),
    onTertiary = Color.hsl(BRAND_HUE, 0.20f, 0.98f),
    background = neutral(0.985f, 0.06f),
    onBackground = neutral(0.10f, 0.10f),
    surface = neutral(0.985f, 0.06f),
    onSurface = neutral(0.10f, 0.10f),
    surfaceVariant = neutral(0.92f, 0.12f),
    onSurfaceVariant = neutral(0.30f),
    surfaceContainerLowest = neutral(1.00f, 0.06f),
    surfaceContainerLow = neutral(0.965f, 0.07f),
    surfaceContainer = neutral(0.945f),
    surfaceContainerHigh = neutral(0.925f, 0.09f),
    surfaceContainerHighest = neutral(0.905f, 0.10f),
    surfaceDim = neutral(0.87f),
    surfaceBright = neutral(0.98f, 0.06f),
    outline = neutral(0.50f, 0.06f),
    outlineVariant = neutral(0.80f),
    inverseSurface = neutral(0.20f),
    inverseOnSurface = neutral(0.95f, 0.05f),
    surfaceTint = LightPrimary
)

/** The dark scheme: the darker same-hue anchor as primary, with mint-tinted dark neutrals. */
internal fun brandDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color.hsl(BRAND_HUE, BRAND_SATURATION, 0.12f),
    primaryContainer = Color.hsl(BRAND_HUE, 0.45f, 0.22f),
    onPrimaryContainer = Color.hsl(BRAND_HUE, 0.50f, 0.88f),
    inversePrimary = LightPrimary,
    secondary = Color.hsl(BRAND_HUE, 0.22f, 0.78f),
    onSecondary = Color.hsl(BRAND_HUE, 0.25f, 0.18f),
    secondaryContainer = Color.hsl(BRAND_HUE, 0.28f, 0.28f),
    onSecondaryContainer = Color.hsl(BRAND_HUE, 0.35f, 0.90f),
    tertiary = Color.hsl(BRAND_HUE, 0.30f, 0.80f),
    onTertiary = Color.hsl(BRAND_HUE, 0.30f, 0.18f),
    background = neutral(0.055f),
    onBackground = neutral(0.90f, 0.06f),
    surface = neutral(0.055f),
    onSurface = neutral(0.90f, 0.06f),
    surfaceVariant = neutral(0.15f, 0.10f),
    onSurfaceVariant = neutral(0.78f, 0.07f),
    surfaceContainerLowest = neutral(0.04f),
    surfaceContainerLow = neutral(0.075f, 0.09f),
    surfaceContainer = neutral(0.095f, 0.09f),
    surfaceContainerHigh = neutral(0.115f, 0.10f),
    surfaceContainerHighest = neutral(0.135f, 0.10f),
    surfaceDim = neutral(0.055f),
    surfaceBright = neutral(0.25f),
    outline = neutral(0.55f, 0.06f),
    outlineVariant = neutral(0.30f, 0.07f),
    inverseSurface = neutral(0.90f, 0.06f),
    inverseOnSurface = neutral(0.12f, 0.10f),
    surfaceTint = DarkPrimary
)
