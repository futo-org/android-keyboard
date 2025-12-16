package org.futo.inputmethod.latin.uix

import androidx.annotation.FloatRange
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.core.math.MathUtils
import org.futo.inputmethod.latin.uix.theme.AdvancedThemeOptions
import kotlin.math.pow
import kotlin.math.roundToInt

val LocalKeyboardScheme = staticCompositionLocalOf {
   wrapLightColorScheme(lightColorScheme())
}

data class ExtraColors(
    val keyboardSurface: Color,
    val keyboardSurfaceDim: Color,
    val keyboardContainer: Color,
    val keyboardContainerVariant: Color,
    val onKeyboardContainer: Color,
    val keyboardPress: Color,
    val keyboardBackgroundGradient: Brush?,
    val primaryTransparent: Color,
    val onSurfaceTransparent: Color,
    val keyboardContainerPressed: Color,
    val onKeyboardContainerPressed: Color,

    val hintColor: Color?,
    val hintHiVis: Boolean,

    val navigationBarColor: Color? = null,
    val navigationBarColorForTransparency: Color? = null,
    val advancedThemeOptions: AdvancedThemeOptions
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
    val keyboardSurface: Color
        get() = extended.keyboardSurface
    val keyboardSurfaceDim: Color
        get() = extended.keyboardSurfaceDim
    val keyboardContainer: Color
        get() = extended.keyboardContainer
    val keyboardContainerVariant: Color
        get() = extended.keyboardContainerVariant
    val onKeyboardContainer: Color
        get() = extended.onKeyboardContainer
    val keyboardPress: Color
        get() = extended.keyboardPress
    val keyboardBackgroundGradient: Brush?
        get() = extended.keyboardBackgroundGradient

    val primaryTransparent: Color
        get() = extended.primaryTransparent
    val onSurfaceTransparent: Color
        get() = extended.onSurfaceTransparent

    val navigationBarColor: Color?
        get() = extended.navigationBarColor
    val navigationBarColorForTransparency: Color?
        get() = extended.navigationBarColorForTransparency

    val keyboardContainerPressed: Color
        get() = extended.keyboardContainerPressed
    val onKeyboardContainerPressed: Color
        get() = extended.onKeyboardContainerPressed
    val hintColor: Color?
        get() = extended.hintColor
    val hintHiVis: Boolean
        get() = extended.hintHiVis
}

fun extendedDarkColorScheme(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    onSecondary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
    tertiary: Color,
    onTertiary: Color,
    tertiaryContainer: Color,
    onTertiaryContainer: Color,
    error: Color,
    onError: Color,
    errorContainer: Color,
    onErrorContainer: Color,
    outline: Color,
    outlineVariant: Color,
    surface: Color,
    onSurface: Color,
    onSurfaceVariant: Color,
    surfaceContainerHighest: Color,
    shadow: Color = Color.Black,
    keyboardSurface: Color,
    keyboardSurfaceDim: Color = keyboardSurface,
    keyboardContainer: Color,
    keyboardContainerVariant: Color,
    onKeyboardContainer: Color,
    keyboardPress: Color,
    keyboardFade0: Color = surface,
    keyboardFade1: Color = surface,
    keyboardBackgroundGradient: Brush? = null,
    primaryTransparent: Color,
    onSurfaceTransparent: Color,
    navigationBarColor: Color? = null,
    navigationBarColorForTransparency: Color? = null,
    keyboardContainerPressed: Color = outline.copy(alpha = 0.33f),
    onKeyboardContainerPressed: Color = Color.Transparent,
    hintColor: Color? = null,
    hintHiVis: Boolean = false,
    keyboardBackgroundShader: String? = null
): KeyboardColorScheme =
    KeyboardColorScheme(
        darkColorScheme(
            primary                    = primary,
            onPrimary                  = onPrimary,
            primaryContainer           = primaryContainer,
            onPrimaryContainer         = onPrimaryContainer,
            secondary                  = secondary,
            onSecondary                = onSecondary,
            secondaryContainer         = secondaryContainer,
            onSecondaryContainer       = onSecondaryContainer,
            tertiary                   = tertiary,
            onTertiary                 = onTertiary,
            tertiaryContainer          = tertiaryContainer,
            onTertiaryContainer        = onTertiaryContainer,
            error                      = error,
            onError                    = onError,
            errorContainer             = errorContainer,
            onErrorContainer           = onErrorContainer,
            outline                    = outline,
            outlineVariant             = outlineVariant,
            surface                    = surface,
            onSurface                  = onSurface,
            onSurfaceVariant           = onSurfaceVariant,
            surfaceContainerHighest    = surfaceContainerHighest,
            background                 = surface,
            onBackground               = onSurface
        ),

        ExtraColors(
            keyboardSurface            = keyboardSurface,
            keyboardSurfaceDim         = keyboardSurfaceDim,
            keyboardContainer          = keyboardContainer,
            keyboardContainerVariant   = keyboardContainerVariant,
            onKeyboardContainer        = onKeyboardContainer,
            keyboardPress              = keyboardPress,
            keyboardBackgroundGradient = keyboardBackgroundGradient,
            primaryTransparent         = primaryTransparent,
            onSurfaceTransparent       = onSurfaceTransparent,
            navigationBarColor         = navigationBarColor,
            keyboardContainerPressed   = keyboardContainerPressed,
            onKeyboardContainerPressed = onKeyboardContainerPressed,
            hintColor = hintColor,
            hintHiVis = hintHiVis,
            navigationBarColorForTransparency = navigationBarColorForTransparency,
            advancedThemeOptions = AdvancedThemeOptions()
        )
    )


fun extendedLightColorScheme(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    onSecondary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
    tertiary: Color,
    onTertiary: Color,
    tertiaryContainer: Color,
    onTertiaryContainer: Color,
    error: Color,
    onError: Color,
    errorContainer: Color,
    onErrorContainer: Color,
    outline: Color,
    outlineVariant: Color,
    surface: Color,
    onSurface: Color,
    onSurfaceVariant: Color,
    surfaceContainerHighest: Color,
    shadow: Color = Color.Black,
    keyboardSurface: Color,
    keyboardSurfaceDim: Color = keyboardSurface,
    keyboardContainer: Color,
    keyboardContainerVariant: Color,
    onKeyboardContainer: Color,
    keyboardPress: Color,
    keyboardFade0: Color = surface,
    keyboardFade1: Color = surface,
    keyboardBackgroundGradient: Brush? = null,
    primaryTransparent: Color,
    onSurfaceTransparent: Color,
    navigationBarColor: Color? = null,
    navigationBarColorForTransparency: Color? = null,
    keyboardContainerPressed: Color = outline.copy(alpha = 0.33f),
    onKeyboardContainerPressed: Color = Color.Transparent,
    hintColor: Color? = null,
    hintHiVis: Boolean = false,
    keyboardBackgroundShader: String? = null
): KeyboardColorScheme =
    KeyboardColorScheme(
        lightColorScheme(
            primary                    = primary,
            onPrimary                  = onPrimary,
            primaryContainer           = primaryContainer,
            onPrimaryContainer         = onPrimaryContainer,
            secondary                  = secondary,
            onSecondary                = onSecondary,
            secondaryContainer         = secondaryContainer,
            onSecondaryContainer       = onSecondaryContainer,
            tertiary                   = tertiary,
            onTertiary                 = onTertiary,
            tertiaryContainer          = tertiaryContainer,
            onTertiaryContainer        = onTertiaryContainer,
            error                      = error,
            onError                    = onError,
            errorContainer             = errorContainer,
            onErrorContainer           = onErrorContainer,
            outline                    = outline,
            outlineVariant             = outlineVariant,
            surface                    = surface,
            onSurface                  = onSurface,
            onSurfaceVariant           = onSurfaceVariant,
            surfaceContainerHighest    = surfaceContainerHighest,
            background                 = surface,
            onBackground               = onSurface
        ),

        ExtraColors(
            keyboardSurface            = keyboardSurface,
            keyboardSurfaceDim     = keyboardSurfaceDim,
            keyboardContainer          = keyboardContainer,
            keyboardContainerVariant   = keyboardContainerVariant,
            onKeyboardContainer        = onKeyboardContainer,
            keyboardPress              = keyboardPress,
            keyboardBackgroundGradient = keyboardBackgroundGradient,
            primaryTransparent         = primaryTransparent,
            onSurfaceTransparent       = onSurfaceTransparent,
            navigationBarColor         = navigationBarColor,
            keyboardContainerPressed   = keyboardContainerPressed,
            onKeyboardContainerPressed = onKeyboardContainerPressed,
            hintColor = hintColor,
            hintHiVis = hintHiVis,
            navigationBarColorForTransparency = navigationBarColorForTransparency,
            advancedThemeOptions = AdvancedThemeOptions()
        )
    )


// Taken from androidx/compose/material3/DynamicTonalPalette.android.kt
// Copyright 2021 The Android Open Source Project, subject to Apache-2.0 license
internal fun Color.setLuminance(
    @FloatRange(from = 0.0, to = 100.0)
    newLuminance: Float
): Color {
    if ((newLuminance < 0.0001) or (newLuminance > 99.9999)) {
        // aRGBFromLstar() from monet ColorUtil.java
        val y = 100 * labInvf((newLuminance + 16) / 116)
        val component = delinearized(y)
        return Color(
            /* red = */component,
            /* green = */component,
            /* blue = */component,
        )
    }

    val sLAB = this.convert(ColorSpaces.CieLab)
    return Color(
        /* luminance = */newLuminance,
        /* a = */sLAB.component2(),
        /* b = */sLAB.component3(),
        colorSpace = ColorSpaces.CieLab
    ).convert(ColorSpaces.Srgb)
}

// Taken from androidx/compose/material3/DynamicTonalPalette.android.kt
// Copyright 2021 The Android Open Source Project, subject to Apache-2.0 license
/** Helper method from monet ColorUtils.java */
private fun labInvf(ft: Float): Float {
    val e = 216f / 24389f
    val kappa = 24389f / 27f
    val ft3 = ft * ft * ft
    return if (ft3 > e) {
        ft3
    } else {
        (116 * ft - 16) / kappa
    }
}

// Taken from androidx/compose/material3/DynamicTonalPalette.android.kt
// Copyright 2021 The Android Open Source Project, subject to Apache-2.0 license
/**
 * Helper method from monet ColorUtils.java
 *
 * Delinearizes an RGB component.
 *
 * @param rgbComponent 0.0 <= rgb_component <= 100.0, represents linear R/G/B channel
 * @return 0 <= output <= 255, color channel converted to regular RGB space
 */
private fun delinearized(rgbComponent: Float): Int {
    val normalized = rgbComponent / 100
    val delinearized = if (normalized <= 0.0031308) {
        normalized * 12.92
    } else {
        1.055 * normalized.toDouble().pow(1.0 / 2.4) - 0.055
    }
    return MathUtils.clamp((delinearized * 255.0).roundToInt(), 0, 255)
}


fun wrapDarkColorScheme(scheme: ColorScheme): KeyboardColorScheme {
    return KeyboardColorScheme(
        scheme,
        ExtraColors(
            keyboardSurface = scheme.surface,
            keyboardSurfaceDim = scheme.surfaceContainerLowest,
            keyboardContainer = scheme.surfaceContainerHigh,
            keyboardContainerVariant = scheme.surfaceContainerLow,
            onKeyboardContainer = scheme.onSurface,
            keyboardPress = scheme.inversePrimary,
            keyboardBackgroundGradient = null,
            primaryTransparent = scheme.primary.copy(alpha = 0.3f),
            onSurfaceTransparent = scheme.onSurface.copy(alpha = 0.1f),
            keyboardContainerPressed = scheme.outline.copy(alpha = 0.33f),
            onKeyboardContainerPressed = Color.Transparent,
            hintColor = null,
            hintHiVis = false,
            advancedThemeOptions = AdvancedThemeOptions()
        )
    )
}

fun wrapLightColorScheme(scheme: ColorScheme): KeyboardColorScheme {
    return KeyboardColorScheme(
        scheme,
        ExtraColors(
            keyboardSurface = scheme.surfaceContainerHigh,
            keyboardSurfaceDim = scheme.surfaceContainerHighest,
            keyboardContainer = scheme.surfaceContainerLowest,
            keyboardContainerVariant = scheme.surfaceContainerLow,
            onKeyboardContainer = scheme.onSurface,
            keyboardPress = scheme.inversePrimary,
            keyboardBackgroundGradient = null,
            primaryTransparent = scheme.primary.copy(alpha = 0.3f),
            onSurfaceTransparent = scheme.onSurface.copy(alpha = 0.1f),
            keyboardContainerPressed = scheme.outline.copy(alpha = 0.33f),
            onKeyboardContainerPressed = Color.Transparent,
            hintColor = null,
            hintHiVis = false,
            advancedThemeOptions = AdvancedThemeOptions()
        )
    )
}