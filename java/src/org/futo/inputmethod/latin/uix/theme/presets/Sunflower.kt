package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview

private val primaryLight = Color(0xFF6D5E0F)
private val onPrimaryLight = Color(0xFFFFFFFF)
private val primaryContainerLight = Color(0xFFF8E287)
private val onPrimaryContainerLight = Color(0xFF221B00)
private val secondaryLight = Color(0xFF6E5E0E)
private val onSecondaryLight = Color(0xFFFFFFFF)
private val secondaryContainerLight = Color(0xFFF9E287)
private val onSecondaryContainerLight = Color(0xFF221B00)
private val tertiaryLight = Color(0xFF6E5D0E)
private val onTertiaryLight = Color(0xFFFFFFFF)
private val tertiaryContainerLight = Color(0xFFFAE287)
private val onTertiaryContainerLight = Color(0xFF221B00)
private val errorLight = Color(0xFFBA1A1A)
private val onErrorLight = Color(0xFFFFFFFF)
private val errorContainerLight = Color(0xFFFFDAD6)
private val onErrorContainerLight = Color(0xFF410002)
private val backgroundLight = Color(0xFFFFF9EE)
private val onBackgroundLight = Color(0xFF1E1B13)
private val surfaceLight = Color(0xFFFFF9ED)
private val onSurfaceLight = Color(0xFF1E1C13)
private val surfaceVariantLight = Color(0xFFEAE2D0)
private val onSurfaceVariantLight = Color(0xFF4B4739)
private val outlineLight = Color(0xFF7C7767)
private val outlineVariantLight = Color(0xFFCDC6B4)
private val scrimLight = Color(0xFF000000)
private val inverseSurfaceLight = Color(0xFF333027)
private val inverseOnSurfaceLight = Color(0xFFF7F0E2)
private val inversePrimaryLight = Color(0xFFDBC66E)
private val surfaceDimLight = Color(0xFFE0D9CC)
private val surfaceBrightLight = Color(0xFFFFF9ED)
private val surfaceContainerLowestLight = Color(0xFFFFFFFF)
private val surfaceContainerLowLight = Color(0xFFFAF3E5)
private val surfaceContainerLight = Color(0xFFF4EDDF)
private val surfaceContainerHighLight = Color(0xFFEEE8DA)
private val surfaceContainerHighestLight = Color(0xFFE8E2D4)

private val lightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)


val Sunflower = ThemeOption(
    dynamic = false,
    key = "Sunflower",
    name = R.string.sunflower_theme_name,
    available = { true }
) {
    lightScheme
}

@Composable
@Preview
private fun PreviewThemeLight() {
    ThemePreview(Sunflower)
}




