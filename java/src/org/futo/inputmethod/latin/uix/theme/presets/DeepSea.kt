package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview


private val primaryLight = Color(0xFF4062D3)
private val onPrimaryLight = Color(0xFFFFFFFF)
private val primaryContainerLight = Color(0xFF3847FF)
private val onPrimaryContainerLight = Color(0xFFFFFFFF)
private val secondaryLight = Color(0xFF476083)
private val onSecondaryLight = Color(0xFFFFFFFF)
private val secondaryContainerLight = Color(0xFF869FC5)
private val onSecondaryContainerLight = Color(0xFF001025)
private val tertiaryLight = Color(0xFF006494)
private val onTertiaryLight = Color(0xFFFFFFFF)
private val tertiaryContainerLight = Color(0xFF5CA4D8)
private val onTertiaryContainerLight = Color(0xFF00111D)
private val errorLight = Color(0xFFBA1A1A)
private val onErrorLight = Color(0xFFFFFFFF)
private val errorContainerLight = Color(0xFFFFDAD6)
private val onErrorContainerLight = Color(0xFF410002)
private val backgroundLight = Color(0xFFDBE8FF)
private val onBackgroundLight = Color(0xFF1A1B26)
private val surfaceLight = Color(0xFFDBE8FF)
private val onSurfaceLight = Color(0xFF1B1B22)
private val surfaceVariantLight = Color(0xFFE1E0F7)
private val onSurfaceVariantLight = Color(0xFF444557)
private val outlineLight = Color(0xFF757589)
private val outlineVariantLight = Color(0xFFC5C5DA)
private val scrimLight = Color(0xFF000000)
private val inverseSurfaceLight = Color(0xFF303038)
private val inverseOnSurfaceLight = Color(0xFFF2EFFA)
private val inversePrimaryLight = Color(0xFFBEC2FF)
private val surfaceDimLight = Color(0xFFDBD9E3)
private val surfaceBrightLight = Color(0xFFFBF8FF)
private val surfaceContainerLowestLight = Color(0xFFFFFFFF)
private val surfaceContainerLowLight = Color(0xFFF5F2FD)
private val surfaceContainerLight = Color(0xFFEFECF7)
private val surfaceContainerHighLight = Color(0xFFE9E7F1)
private val surfaceContainerHighestLight = Color(0xFFE3E1EC)

private val primaryDark = Color(0xFFBEC2FF)
private val onPrimaryDark = Color(0xFF000CA5)
private val primaryContainerDark = Color(0xFF0016F5)
private val onPrimaryContainerDark = Color(0xFFE7E7FF)
private val secondaryDark = Color(0xFFAFC8F1)
private val onSecondaryDark = Color(0xFF163152)
private val secondaryContainerDark = Color(0xFF5D769A)
private val onSecondaryContainerDark = Color(0xFFFFFFFF)
private val tertiaryDark = Color(0xFF8ECDFF)
private val onTertiaryDark = Color(0xFF00344F)
private val tertiaryContainerDark = Color(0xFF2C7BAC)
private val onTertiaryContainerDark = Color(0xFFFFFFFF)
private val errorDark = Color(0xFFFFB4AB)
private val onErrorDark = Color(0xFF690005)
private val errorContainerDark = Color(0xFF93000A)
private val onErrorContainerDark = Color(0xFFFFDAD6)
private val backgroundDark = Color(0xFF12132D)
private val onBackgroundDark = Color(0xFFE3E1F0)
private val surfaceDark = Color(0xFF12132A)
private val onSurfaceDark = Color(0xFFE3E1EC)
private val surfaceVariantDark = Color(0xFF444557)
private val onSurfaceVariantDark = Color(0xFFC5C5DA)
private val outlineDark = Color(0xFF8F8FA3)
private val outlineVariantDark = Color(0xFF444557)
private val scrimDark = Color(0xFF000000)
private val inverseSurfaceDark = Color(0xFFE3E1EC)
private val inverseOnSurfaceDark = Color(0xFF303038)
private val inversePrimaryDark = Color(0xFF2F3EFF)
private val surfaceDimDark = Color(0xFF12131A)
private val surfaceBrightDark = Color(0xFF383841)
private val surfaceContainerLowestDark = Color(0xFF0D0E15)
private val surfaceContainerLowDark = Color(0xFF1B1B22)
private val surfaceContainerDark = Color(0xFF1F1F26)
private val surfaceContainerHighDark = Color(0xFF292931)
private val surfaceContainerHighestDark = Color(0xFF34343C)

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

private val darkScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)

val DeepSeaLight = ThemeOption(
    dynamic = false,
    key = "DeepSeaLight",
    name = R.string.deep_sea_light_theme_name,
    available = { true }
) {
    lightScheme
}

val DeepSeaDark = ThemeOption(
    dynamic = false,
    key = "DeepSeaDark",
    name = R.string.deep_sea_dark_theme_name,
    available = { true }
) {
    darkScheme
}

@Composable
@Preview
private fun PreviewThemeLight() {
    ThemePreview(DeepSeaLight)
}

@Composable
@Preview
private fun PreviewThemeDark() {
    ThemePreview(DeepSeaDark)
}