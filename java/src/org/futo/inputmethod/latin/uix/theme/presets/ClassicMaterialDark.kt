package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview


private val md_theme_dark_primary = Color(0xFF80cbc4)
private val md_theme_dark_onPrimary = Color(0xFFFFFFFF)
private val md_theme_dark_primaryContainer = Color(0xFF34434B)
private val md_theme_dark_onPrimaryContainer = Color(0xFFF0FFFE)
private val md_theme_dark_secondary = Color(0xFF80cbc4)
private val md_theme_dark_onSecondary = Color(0xFFFFFFFF)
private val md_theme_dark_secondaryContainer = Color(0xFF416152)
private val md_theme_dark_onSecondaryContainer = Color(0xFFFFFFFF)
private val md_theme_dark_tertiary = Color(0xFF3582A2)
private val md_theme_dark_onTertiary = Color(0xFFFFFFFF)
private val md_theme_dark_tertiaryContainer = Color(0xFF17516D)
private val md_theme_dark_onTertiaryContainer = Color(0xFFBDE9FF)
private val md_theme_dark_error = Color(0xFFFFB4AB)
private val md_theme_dark_errorContainer = Color(0xFF93000A)
private val md_theme_dark_onError = Color(0xFF690005)
private val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
private val md_theme_dark_background = Color(0xFF21272b)
private val md_theme_dark_onBackground = Color(0xFFFFFFFF)
private val md_theme_dark_surface = Color(0xFF263238)
private val md_theme_dark_onSurface = Color(0xFFFFFFFF)
private val md_theme_dark_surfaceVariant = Color(0xFF3F4947)
private val md_theme_dark_onSurfaceVariant = Color(0xFFBEC9C7)
private val md_theme_dark_outline = Color(0xFF899391)
private val md_theme_dark_inverseOnSurface = Color(0xFF001F2A)
private val md_theme_dark_inverseSurface = Color(0xFFBFE9FF)
private val md_theme_dark_inversePrimary = Color(0xFF006A64)
private val md_theme_dark_shadow = Color(0xFF000000)
private val md_theme_dark_surfaceTint = Color(0xFF4FDBD1)
private val md_theme_dark_outlineVariant = Color(0xFF3F4947)
private val md_theme_dark_scrim = Color(0xFF000000)

private val colorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    outline = md_theme_dark_outline,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    inverseSurface = md_theme_dark_inverseSurface,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inversePrimary = md_theme_dark_inversePrimary,
    surfaceTint = md_theme_dark_surfaceTint,
    outlineVariant = md_theme_dark_outlineVariant,
    scrim = md_theme_dark_scrim,
)

val ClassicMaterialDark = ThemeOption(
    dynamic = false,
    key = "ClassicMaterialDark",
    name = R.string.classic_material_dark_theme_name,
    available = { true }
) {
    colorScheme
}

@Composable
@Preview
private fun PreviewTheme() {
    ThemePreview(ClassicMaterialDark)
}