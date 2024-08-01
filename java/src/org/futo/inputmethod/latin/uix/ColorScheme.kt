package org.futo.inputmethod.latin.uix

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

val LocalKeyboardScheme = staticCompositionLocalOf {
    wrapColorScheme(lightColorScheme())
}

data class ExtraColors(
    val backgroundContainer: Color,
    val backgroundContainerDim: Color,
    val onBackgroundContainer: Color,
    val outlineBright: Color,
    val outlineDim: Color,
)

data class KeyboardColorScheme(
    val base: ColorScheme,
    val extended: ExtraColors
) {
    // Base colors
    val primary: Color
        get() = base.primary
    val onPrimary: Color
        get() = base.onPrimary
    val primaryContainer: Color
        get() = base.primaryContainer
    val onPrimaryContainer: Color
        get() = base.onPrimaryContainer
    val inversePrimary: Color
        get() = base.inversePrimary
    val secondary: Color
        get() = base.secondary
    val onSecondary: Color
        get() = base.onSecondary
    val secondaryContainer: Color
        get() = base.secondaryContainer
    val onSecondaryContainer: Color
        get() = base.onSecondaryContainer
    val tertiary: Color
        get() = base.tertiary
    val onTertiary: Color
        get() = base.onTertiary
    val tertiaryContainer: Color
        get() = base.tertiaryContainer
    val onTertiaryContainer: Color
        get() = base.onTertiaryContainer
    val background: Color
        get() = base.background
    val onBackground: Color
        get() = base.onBackground
    val surface: Color
        get() = base.surface
    val onSurface: Color
        get() = base.onSurface
    val surfaceVariant: Color
        get() = base.surfaceVariant
    val onSurfaceVariant: Color
        get() = base.onSurfaceVariant
    val surfaceTint: Color
        get() = base.surfaceTint
    val inverseSurface: Color
        get() = base.inverseSurface
    val inverseOnSurface: Color
        get() = base.inverseOnSurface
    val error: Color
        get() = base.error
    val onError: Color
        get() = base.onError
    val errorContainer: Color
        get() = base.errorContainer
    val onErrorContainer: Color
        get() = base.onErrorContainer
    val outline: Color
        get() = base.outline
    val outlineVariant: Color
        get() = base.outlineVariant
    val scrim: Color
        get() = base.scrim
    val surfaceBright: Color
        get() = base.surfaceBright
    val surfaceDim: Color
        get() = base.surfaceDim
    val surfaceContainer: Color
        get() = base.surfaceContainer
    val surfaceContainerHigh: Color
        get() = base.surfaceContainerHigh
    val surfaceContainerHighest: Color
        get() = base.surfaceContainerHighest
    val surfaceContainerLow: Color
        get() = base.surfaceContainerLow
    val surfaceContainerLowest: Color
        get() = base.surfaceContainerLowest

    // Extended colors
    val backgroundContainer: Color
        get() = extended.backgroundContainer
    val backgroundContainerDim: Color
        get() = extended.backgroundContainerDim
    val onBackgroundContainer: Color
        get() = extended.onBackgroundContainer
    val outlineBright: Color
        get() = extended.outlineBright
    val outlineDim: Color
        get() = extended.outlineDim
}

fun wrapColorScheme(base: ColorScheme): KeyboardColorScheme =
    KeyboardColorScheme(
        base,
        generateExtraColorsAutomatically(base)
    )

private fun generateExtraColorsAutomatically(base: ColorScheme): ExtraColors {
    val ratio = 1.5f
    val background = base.background.toArgb()

    var backgroundContainer = adjustColorBrightnessForContrast(background, background, 1.5f)
    if(backgroundContainer == background) {
        // May happen if the color is already 100% white
        backgroundContainer = adjustColorBrightnessForContrast(background, background, 1.0f / (ratio / 2.0f + 0.5f))
    }

    val backgroundContainerDim = adjustColorBrightnessForContrast(background, background, ratio / 2.0f + 0.5f, adjustSaturation = true)

    val onBackgroundContainer = base.onBackground

    val outlineDim = base.outline.copy(alpha = 0.5f)
    val outlineBright = base.outline.copy(alpha = 1.0f)

    return ExtraColors(
        backgroundContainer = Color(backgroundContainer),
        backgroundContainerDim = Color(backgroundContainerDim),
        onBackgroundContainer = onBackgroundContainer,
        outlineDim = outlineDim,
        outlineBright = outlineBright
    )
}

private fun adjustColorBrightnessForContrast(bgColor: Int, fgColor: Int, desiredContrast: Float, adjustSaturation: Boolean = false): Int {
    // Convert RGB colors to HSL
    val bgHSL = FloatArray(3)
    ColorUtils.colorToHSL(bgColor, bgHSL)
    val fgHSL = FloatArray(3)
    ColorUtils.colorToHSL(fgColor, fgHSL)

    // Estimate the adjustment needed in lightness to achieve the desired contrast
    // This is a simplified approach and may not be perfectly accurate
    val lightnessAdjustment = (desiredContrast - 1) / 10.0f // Simplified and heuristic-based adjustment

    // Adjust the background color's lightness
    bgHSL[2] = bgHSL[2] + lightnessAdjustment
    bgHSL[2] = bgHSL[2].coerceIn(0f, 1f) // Ensure the lightness stays within valid range

    if(adjustSaturation) {
        bgHSL[1] = (bgHSL[1] + lightnessAdjustment).coerceIn(0f, 1f)
    }

    // Convert back to RGB and return the adjusted color
    return ColorUtils.HSLToColor(bgHSL)
}