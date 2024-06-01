package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview

private val primaryLight = Color(0xFFEC5EB4)
private val onPrimaryLight = Color(0xFFFFFFFF)
private val primaryContainerLight = Color(0xFFFFA5F5)
private val onPrimaryContainerLight = Color(0xFF610062)
private val secondaryLight = Color(0xFF7A5175)
private val onSecondaryLight = Color(0xFFFFFFFF)
private val secondaryContainerLight = Color(0xFFFFD1F6)
private val onSecondaryContainerLight = Color(0xFF5F395B)
private val tertiaryLight = Color(0xFF904D1B)
private val onTertiaryLight = Color(0xFFFFFFFF)
private val tertiaryContainerLight = Color(0xFFFFB383)
private val onTertiaryContainerLight = Color(0xFF582700)
private val errorLight = Color(0xFFBA1A1A)
private val onErrorLight = Color(0xFFFFFFFF)
private val errorContainerLight = Color(0xFFFFDAD6)
private val onErrorContainerLight = Color(0xFF410002)
private val backgroundLight = Color(0xFFFFECF9)
private val onBackgroundLight = Color(0xFF21191F)
private val surfaceLight = Color(0xFFFFECF9)
private val onSurfaceLight = Color(0xFF21191F)
private val surfaceVariantLight = Color(0xFFF2DDEB)
private val onSurfaceVariantLight = Color(0xFF51434E)
private val outlineLight = Color(0xFF83727E)
private val outlineVariantLight = Color(0xFFD5C1CE)
private val scrimLight = Color(0xFF000000)
private val inverseSurfaceLight = Color(0xFF362E34)
private val inverseOnSurfaceLight = Color(0xFFFCEDF5)
private val inversePrimaryLight = Color(0xFFFFAAF5)
private val surfaceDimLight = Color(0xFFE4D6DE)
private val surfaceBrightLight = Color(0xFFFFF7F9)
private val surfaceContainerLowestLight = Color(0xFFFFFFFF)
private val surfaceContainerLowLight = Color(0xFFFFF0F8)
private val surfaceContainerLight = Color(0xFFF9EAF2)
private val surfaceContainerHighLight = Color(0xFFF3E4ED)
private val surfaceContainerHighestLight = Color(0xFFEDDFE7)


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


val CottonCandy = ThemeOption(
    dynamic = false,
    key = "CottonCandy",
    name = R.string.cotton_candy_theme_name,
    available = { true }
) {
    lightScheme
}

@Composable
@Preview
private fun PreviewThemeLight() {
    ThemePreview(CottonCandy)
}




