package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview

private val primaryDark = Color(0xFF67FB59)
private val onPrimaryDark = Color(0xFF003A02)
private val primaryContainerDark = Color(0xFF35CF31)
private val onPrimaryContainerDark = Color(0xFF003001)
private val secondaryDark = Color(0xFF8CD97C)
private val onSecondaryDark = Color(0xFF003A02)
private val secondaryContainerDark = Color(0xFF004B03)
private val onSecondaryContainerDark = Color(0xFF99E788)
private val tertiaryDark = Color(0xFF52F5F4)
private val onTertiaryDark = Color(0xFF003737)
private val tertiaryContainerDark = Color(0xFF00CAC9)
private val onTertiaryContainerDark = Color(0xFF002F2F)
private val errorDark = Color(0xFFFFB4AB)
private val onErrorDark = Color(0xFF690005)
private val errorContainerDark = Color(0xFF93000A)
private val onErrorContainerDark = Color(0xFFFFDAD6)
private val backgroundDark = Color(0xFF0D1A0B)
private val onBackgroundDark = Color(0xFFDCE6D4)
private val surfaceDark = Color(0xFF0D1A0B)
private val onSurfaceDark = Color(0xFFDCE5D5)
private val surfaceVariantDark = Color(0xFF3E4A39)
private val onSurfaceVariantDark = Color(0xFFBCCBB4)
private val outlineDark = Color(0xFF879580)
private val outlineVariantDark = Color(0xFF3E4A39)
private val scrimDark = Color(0xFF000000)
private val inverseSurfaceDark = Color(0xFFDCE5D5)
private val inverseOnSurfaceDark = Color(0xFF2B3327)
private val inversePrimaryDark = Color(0xFF006E08)
private val surfaceDimDark = Color(0xFF0E150C)
private val surfaceBrightDark = Color(0xFF333B30)
private val surfaceContainerLowestDark = Color(0xFF091007)
private val surfaceContainerLowDark = Color(0xFF161E13)
private val surfaceContainerDark = Color(0xFF1A2217)
private val surfaceContainerHighDark = Color(0xFF242C21)
private val surfaceContainerHighestDark = Color(0xFF2F372B)

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

val Emerald = ThemeOption(
    dynamic = false,
    key = "Emerald",
    name = R.string.emerald_theme_name,
    available = { true }
) {
    darkScheme
}

@Composable
@Preview
private fun PreviewThemeDark() {
    ThemePreview(Emerald)
}