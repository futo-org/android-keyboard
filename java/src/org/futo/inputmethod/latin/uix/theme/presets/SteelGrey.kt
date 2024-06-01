package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview

private val primaryLightHighContrast = Color(0xFF212223)
private val onPrimaryLightHighContrast = Color(0xFFFFFFFF)
private val primaryContainerLightHighContrast = Color(0xFF424343)
private val onPrimaryContainerLightHighContrast = Color(0xFFFFFFFF)
private val secondaryLightHighContrast = Color(0xFF222222)
private val onSecondaryLightHighContrast = Color(0xFFFFFFFF)
private val secondaryContainerLightHighContrast = Color(0xFF434343)
private val onSecondaryContainerLightHighContrast = Color(0xFFFFFFFF)
private val tertiaryLightHighContrast = Color(0xFF242223)
private val onTertiaryLightHighContrast = Color(0xFFFFFFFF)
private val tertiaryContainerLightHighContrast = Color(0xFF454244)
private val onTertiaryContainerLightHighContrast = Color(0xFFFFFFFF)
private val errorLightHighContrast = Color(0xFF4E0002)
private val onErrorLightHighContrast = Color(0xFFFFFFFF)
private val errorContainerLightHighContrast = Color(0xFF8C0009)
private val onErrorContainerLightHighContrast = Color(0xFFFFFFFF)
private val backgroundLightHighContrast = Color(0xFFA8A8A8)
private val onBackgroundLightHighContrast = Color(0xFF1C1B1B)
private val surfaceLightHighContrast = Color(0xFFBBBBBB)
private val onSurfaceLightHighContrast = Color(0xFF000000)
private val surfaceVariantLightHighContrast = Color(0xFFE0E3E3)
private val onSurfaceVariantLightHighContrast = Color(0xFF212525)
private val outlineLightHighContrast = Color(0xFF404344)
private val outlineVariantLightHighContrast = Color(0xFF404344)
private val scrimLightHighContrast = Color(0xFF000000)
private val inverseSurfaceLightHighContrast = Color(0xFF313030)
private val inverseOnSurfaceLightHighContrast = Color(0xFFFFFFFF)
private val inversePrimaryLightHighContrast = Color(0xFFECECEC)
private val surfaceDimLightHighContrast = Color(0xFFDDD9D9)
private val surfaceBrightLightHighContrast = Color(0xFFFCF8F8)
private val surfaceContainerLowestLightHighContrast = Color(0xFFFFFFFF)
private val surfaceContainerLowLightHighContrast = Color(0xFFF7F3F2)
private val surfaceContainerLightHighContrast = Color(0xFFF1EDEC)
private val surfaceContainerHighLightHighContrast = Color(0xFFEBE7E7)
private val surfaceContainerHighestLightHighContrast = Color(0xFFE5E2E1)

private val highContrastLightColorScheme = lightColorScheme(
    primary = primaryLightHighContrast,
    onPrimary = onPrimaryLightHighContrast,
    primaryContainer = primaryContainerLightHighContrast,
    onPrimaryContainer = onPrimaryContainerLightHighContrast,
    secondary = secondaryLightHighContrast,
    onSecondary = onSecondaryLightHighContrast,
    secondaryContainer = secondaryContainerLightHighContrast,
    onSecondaryContainer = onSecondaryContainerLightHighContrast,
    tertiary = tertiaryLightHighContrast,
    onTertiary = onTertiaryLightHighContrast,
    tertiaryContainer = tertiaryContainerLightHighContrast,
    onTertiaryContainer = onTertiaryContainerLightHighContrast,
    error = errorLightHighContrast,
    onError = onErrorLightHighContrast,
    errorContainer = errorContainerLightHighContrast,
    onErrorContainer = onErrorContainerLightHighContrast,
    background = backgroundLightHighContrast,
    onBackground = onBackgroundLightHighContrast,
    surface = surfaceLightHighContrast,
    onSurface = onSurfaceLightHighContrast,
    surfaceVariant = surfaceVariantLightHighContrast,
    onSurfaceVariant = onSurfaceVariantLightHighContrast,
    outline = outlineLightHighContrast,
    outlineVariant = outlineVariantLightHighContrast,
    scrim = scrimLightHighContrast,
    inverseSurface = inverseSurfaceLightHighContrast,
    inverseOnSurface = inverseOnSurfaceLightHighContrast,
    inversePrimary = inversePrimaryLightHighContrast,
    surfaceDim = surfaceDimLightHighContrast,
    surfaceBright = surfaceBrightLightHighContrast,
    surfaceContainerLowest = surfaceContainerLowestLightHighContrast,
    surfaceContainerLow = surfaceContainerLowLightHighContrast,
    surfaceContainer = surfaceContainerLightHighContrast,
    surfaceContainerHigh = surfaceContainerHighLightHighContrast,
    surfaceContainerHighest = surfaceContainerHighestLightHighContrast,
)


val SteelGray = ThemeOption(
    dynamic = false,
    key = "SteelGray",
    name = R.string.steel_gray_theme_name,
    available = { true }
) {
    highContrastLightColorScheme
}

@Composable
@Preview
private fun PreviewTheme() {
    ThemePreview(SteelGray)
}