@file:Suppress("LocalVariableName")

package org.futo.inputmethod.latin.uix

import android.os.Build
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.theme.DarkColorScheme


// TODO: For now, this calls CODE_SHORTCUT. In the future, we will want to
// make this a window
val VoiceInputAction = Action(
    icon = R.drawable.mic_fill,
    name = "Voice Input",
    simplePressImpl = {
        it.triggerSystemVoiceInput()
    },
    windowImpl = null
)

val ThemeAction = Action(
    icon = R.drawable.eye,
    name = "Theme Switcher",
    simplePressImpl = null,
    windowImpl = object : ActionWindow {
        @Composable
        override fun windowName(): String {
            return "Theme Switcher"
        }

        @Composable
        override fun WindowContents(manager: KeyboardManagerForAction) {
            val context = LocalContext.current
            LazyColumn(modifier = Modifier.padding(8.dp, 0.dp).fillMaxWidth()) {
                item {
                    Button(onClick = {
                        manager.updateTheme(DarkColorScheme)
                    }) {
                        Text("Default voice input theme")
                    }
                }

                item {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Button(onClick = {
                            manager.updateTheme(dynamicLightColorScheme(context))
                        }) {
                            Text("Dynamic light color scheme")
                        }

                        Button(onClick = {
                            manager.updateTheme(dynamicDarkColorScheme(context))
                        }) {
                            Text("Dynamic dark color scheme")
                        }
                    }
                }

                item {
                    Button(onClick = {
                        val md_theme_light_primary = Color(0xFF6750A4)
                        val md_theme_light_onPrimary = Color(0xFFFFFFFF)
                        val md_theme_light_primaryContainer = Color(0xFFEADDFF)
                        val md_theme_light_onPrimaryContainer = Color(0xFF21005D)
                        val md_theme_light_secondary = Color(0xFF625B71)
                        val md_theme_light_onSecondary = Color(0xFFFFFFFF)
                        val md_theme_light_secondaryContainer = Color(0xFFE8DEF8)
                        val md_theme_light_onSecondaryContainer = Color(0xFF1D192B)
                        val md_theme_light_tertiary = Color(0xFF7D5260)
                        val md_theme_light_onTertiary = Color(0xFFFFFFFF)
                        val md_theme_light_tertiaryContainer = Color(0xFFFFD8E4)
                        val md_theme_light_onTertiaryContainer = Color(0xFF31111D)
                        val md_theme_light_error = Color(0xFFB3261E)
                        val md_theme_light_onError = Color(0xFFFFFFFF)
                        val md_theme_light_errorContainer = Color(0xFFF9DEDC)
                        val md_theme_light_onErrorContainer = Color(0xFF410E0B)
                        val md_theme_light_outline = Color(0xFF79747E)
                        val md_theme_light_background = Color(0xFFFFFBFE)
                        val md_theme_light_onBackground = Color(0xFF1C1B1F)
                        val md_theme_light_surface = Color(0xFFFFFBFE)
                        val md_theme_light_onSurface = Color(0xFF1C1B1F)
                        val md_theme_light_surfaceVariant = Color(0xFFE7E0EC)
                        val md_theme_light_onSurfaceVariant = Color(0xFF49454F)
                        val md_theme_light_inverseSurface = Color(0xFF313033)
                        val md_theme_light_inverseOnSurface = Color(0xFFF4EFF4)
                        val md_theme_light_inversePrimary = Color(0xFFD0BCFF)
                        val md_theme_light_shadow = Color(0xFF000000)
                        val md_theme_light_surfaceTint = Color(0xFF6750A4)
                        val md_theme_light_outlineVariant = Color(0xFFCAC4D0)
                        val md_theme_light_scrim = Color(0xFF000000)

                        manager.updateTheme(
                            lightColorScheme(
                                primary = md_theme_light_primary,
                                onPrimary = md_theme_light_onPrimary,
                                primaryContainer = md_theme_light_primaryContainer,
                                onPrimaryContainer = md_theme_light_onPrimaryContainer,
                                secondary = md_theme_light_secondary,
                                onSecondary = md_theme_light_onSecondary,
                                secondaryContainer = md_theme_light_secondaryContainer,
                                onSecondaryContainer = md_theme_light_onSecondaryContainer,
                                tertiary = md_theme_light_tertiary,
                                onTertiary = md_theme_light_onTertiary,
                                tertiaryContainer = md_theme_light_tertiaryContainer,
                                onTertiaryContainer = md_theme_light_onTertiaryContainer,
                                error = md_theme_light_error,
                                onError = md_theme_light_onError,
                                errorContainer = md_theme_light_errorContainer,
                                onErrorContainer = md_theme_light_onErrorContainer,
                                outline = md_theme_light_outline,
                                background = md_theme_light_background,
                                onBackground = md_theme_light_onBackground,
                                surface = md_theme_light_surface,
                                onSurface = md_theme_light_onSurface,
                                surfaceVariant = md_theme_light_surfaceVariant,
                                onSurfaceVariant = md_theme_light_onSurfaceVariant,
                                inverseSurface = md_theme_light_inverseSurface,
                                inverseOnSurface = md_theme_light_inverseOnSurface,
                                inversePrimary = md_theme_light_inversePrimary,
                                surfaceTint = md_theme_light_surfaceTint,
                                outlineVariant = md_theme_light_outlineVariant,
                                scrim = md_theme_light_scrim,
                            )
                        )
                    }) {
                        Text("Some random light theme")
                    }

                    Button(onClick = {
                        val md_theme_dark_primary = Color(0xFFD0BCFF)
                        val md_theme_dark_onPrimary = Color(0xFF381E72)
                        val md_theme_dark_primaryContainer = Color(0xFF4F378B)
                        val md_theme_dark_onPrimaryContainer = Color(0xFFEADDFF)
                        val md_theme_dark_secondary = Color(0xFFCCC2DC)
                        val md_theme_dark_onSecondary = Color(0xFF332D41)
                        val md_theme_dark_secondaryContainer = Color(0xFF4A4458)
                        val md_theme_dark_onSecondaryContainer = Color(0xFFE8DEF8)
                        val md_theme_dark_tertiary = Color(0xFFEFB8C8)
                        val md_theme_dark_onTertiary = Color(0xFF492532)
                        val md_theme_dark_tertiaryContainer = Color(0xFF633B48)
                        val md_theme_dark_onTertiaryContainer = Color(0xFFFFD8E4)
                        val md_theme_dark_error = Color(0xFFF2B8B5)
                        val md_theme_dark_onError = Color(0xFF601410)
                        val md_theme_dark_errorContainer = Color(0xFF8C1D18)
                        val md_theme_dark_onErrorContainer = Color(0xFFF9DEDC)
                        val md_theme_dark_outline = Color(0xFF938F99)
                        val md_theme_dark_background = Color(0xFF1C1B1F)
                        val md_theme_dark_onBackground = Color(0xFFE6E1E5)
                        val md_theme_dark_surface = Color(0xFF1C1B1F)
                        val md_theme_dark_onSurface = Color(0xFFE6E1E5)
                        val md_theme_dark_surfaceVariant = Color(0xFF49454F)
                        val md_theme_dark_onSurfaceVariant = Color(0xFFCAC4D0)
                        val md_theme_dark_inverseSurface = Color(0xFFE6E1E5)
                        val md_theme_dark_inverseOnSurface = Color(0xFF313033)
                        val md_theme_dark_inversePrimary = Color(0xFF6750A4)
                        val md_theme_dark_shadow = Color(0xFF000000)
                        val md_theme_dark_surfaceTint = Color(0xFFD0BCFF)
                        val md_theme_dark_outlineVariant = Color(0xFF49454F)
                        val md_theme_dark_scrim = Color(0xFF000000)

                        manager.updateTheme(
                            darkColorScheme(
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
                        )
                    }) {
                        Text("Some random dark theme")
                    }


                    Button(onClick = {
                        val md_theme_dark_primary = Color(0xFFD0BCFF)
                        val md_theme_dark_onPrimary = Color(0xFF381E72)
                        val md_theme_dark_primaryContainer = Color(0xFF4F378B)
                        val md_theme_dark_onPrimaryContainer = Color(0xFFEADDFF)
                        val md_theme_dark_secondary = Color(0xFFCCC2DC)
                        val md_theme_dark_onSecondary = Color(0xFF332D41)
                        val md_theme_dark_secondaryContainer = Color(0xFF4A4458)
                        val md_theme_dark_onSecondaryContainer = Color(0xFFE8DEF8)
                        val md_theme_dark_tertiary = Color(0xFFEFB8C8)
                        val md_theme_dark_onTertiary = Color(0xFF492532)
                        val md_theme_dark_tertiaryContainer = Color(0xFF633B48)
                        val md_theme_dark_onTertiaryContainer = Color(0xFFFFD8E4)
                        val md_theme_dark_error = Color(0xFFF2B8B5)
                        val md_theme_dark_onError = Color(0xFF601410)
                        val md_theme_dark_errorContainer = Color(0xFF8C1D18)
                        val md_theme_dark_onErrorContainer = Color(0xFFF9DEDC)
                        val md_theme_dark_outline = Color(0xFF938F99)
                        val md_theme_dark_background = Color(0xFF000000)
                        val md_theme_dark_onBackground = Color(0xFFE6E1E5)
                        val md_theme_dark_surface = Color(0xFF000000)
                        val md_theme_dark_onSurface = Color(0xFFE6E1E5)
                        val md_theme_dark_surfaceVariant = Color(0xFF49454F)
                        val md_theme_dark_onSurfaceVariant = Color(0xFFCAC4D0)
                        val md_theme_dark_inverseSurface = Color(0xFFE6E1E5)
                        val md_theme_dark_inverseOnSurface = Color(0xFF313033)
                        val md_theme_dark_inversePrimary = Color(0xFF6750A4)
                        val md_theme_dark_shadow = Color(0xFF000000)
                        val md_theme_dark_surfaceTint = Color(0xFFD0BCFF)
                        val md_theme_dark_outlineVariant = Color(0xFF49454F)
                        val md_theme_dark_scrim = Color(0xFF000000)

                        manager.updateTheme(
                            darkColorScheme(
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
                        )
                    }) {
                        Text("AMOLED dark")
                    }
                }
            }
        }
    }
)
