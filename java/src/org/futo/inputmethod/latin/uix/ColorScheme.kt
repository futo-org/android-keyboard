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
            navigationBarColorForTransparency = navigationBarColorForTransparency,
            advancedThemeOptions = AdvancedThemeOptions()
        )
    )

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
            advancedThemeOptions = AdvancedThemeOptions()
        )
    )
}