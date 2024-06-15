package org.futo.inputmethod.latin.uix.theme.selector

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.uix.KeyBordersSetting
import org.futo.inputmethod.latin.uix.KeyHintsSetting
import org.futo.inputmethod.latin.uix.KeyboardBottomOffsetSetting
import org.futo.inputmethod.latin.uix.KeyboardHeightMultiplierSetting
import org.futo.inputmethod.latin.uix.THEME_KEY
import org.futo.inputmethod.latin.uix.settings.SettingSlider
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.ThemeOptionKeys
import org.futo.inputmethod.latin.uix.theme.ThemeOptions
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.latin.uix.theme.UixThemeWrapper
import org.futo.inputmethod.latin.uix.theme.presets.AMOLEDDarkPurple
import org.futo.inputmethod.latin.uix.theme.presets.ClassicMaterialDark
import org.futo.inputmethod.latin.uix.theme.presets.DynamicDarkTheme
import org.futo.inputmethod.latin.uix.theme.presets.DynamicLightTheme
import org.futo.inputmethod.latin.uix.theme.presets.DynamicSystemTheme
import org.futo.inputmethod.latin.uix.theme.presets.VoiceInputTheme
import kotlin.math.roundToInt

@Composable
fun ThemePreview(theme: ThemeOption, isSelected: Boolean = false, overrideName: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit = { }) {
    if(theme == DynamicSystemTheme) return DynamicThemePreview(isSelected, onClick)

    val context = LocalContext.current
    val colors = remember { theme.obtainColors(context) }

    val currColors = MaterialTheme.colorScheme

    val borderWidth = if (isSelected) {
        4.dp
    } else {
        Dp.Hairline
    }

    val borderColor = if (isSelected) {
        currColors.inversePrimary
    } else {
        currColors.outline
    }

    val textColor = colors.onBackground

    // TODO: These have to be manually kept same as those in BasicThemeProvider
    val spacebarColor = colors.outline.copy(alpha = 0.33f)
    val actionColor = colors.primary

    val keyboardShape = RoundedCornerShape(8.dp)

    val previewModifier = if(LocalInspectionMode.current) {
        modifier.width(172.dp)
    } else {
        modifier
    }

    Surface(
        modifier = previewModifier
            .padding(12.dp)
            .height(128.dp)
            .border(borderWidth, borderColor, keyboardShape)
            .clickable { onClick() },
        color = colors.surface,
        shape = keyboardShape
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Theme name and action bar
            Text(
                text = overrideName ?: stringResource(theme.name),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .background(colors.background)
                    .fillMaxWidth()
                    .padding(4.dp),
                color = textColor,
                style = Typography.labelSmall
            )

            // Keyboard contents
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                // Spacebar
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(18.dp)
                        .align(Alignment.BottomCenter),
                    color = spacebarColor,
                    shape = RoundedCornerShape(12.dp)
                ) { }

                // Enter key
                Surface(
                    modifier = Modifier
                        .width(24.dp)
                        .height(18.dp)
                        .align(Alignment.BottomEnd)
                        .padding(0.dp, 1.dp),
                    color = actionColor,
                    shape = RoundedCornerShape(4.dp)
                ) { }
            }
        }
    }
}

// Special case to demonstrate the light and dark mode
@Preview
@Composable
fun DynamicThemePreview(isSelected: Boolean = false, onClick: () -> Unit = { }) {
    Box {
        ThemePreview(
            DynamicLightTheme,
            isSelected = isSelected,
            onClick = onClick,
            overrideName = stringResource(DynamicSystemTheme.name),
            modifier = Modifier.clip(GenericShape { size, _ ->
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width * 0.66f, 0f)
                    lineTo(size.width * 0.33f, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                addPath(path)
            })
        )
        ThemePreview(
            DynamicDarkTheme,
            isSelected = isSelected,
            onClick = onClick,
            overrideName = stringResource(DynamicSystemTheme.name),
            modifier = Modifier.clip(GenericShape { size, _ ->
                val path = Path().apply {
                    moveTo(size.width * 0.66f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    lineTo(size.width * 0.33f, size.height)
                    close()
                }
                addPath(path)
            })
        )
    }
}

@Composable
fun AddCustomThemeButton(onClick: () -> Unit = { }) {
    val context = LocalContext.current
    val currColors = MaterialTheme.colorScheme

    val keyboardShape = RoundedCornerShape(8.dp)

    Surface(
        modifier = Modifier
            .padding(12.dp)
            .width(172.dp)
            .height(128.dp)
            .clickable { onClick() },
        color = currColors.surfaceVariant,
        shape = keyboardShape
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Icon(
                Icons.Default.Add, contentDescription = "", modifier = Modifier
                    .size(48.dp)
                    .align(
                        Alignment.Center
                    )
            )
        }
    }
}

@Composable
fun ThemePicker(onSelected: (ThemeOption) -> Unit) {
    val context = LocalContext.current

    val currentTheme = useDataStore(THEME_KEY.key, "").value

    val isInspecting = LocalInspectionMode.current
    val availableThemeOptions = remember {
        ThemeOptionKeys.mapNotNull { key ->
            ThemeOptions[key]?.let { Pair(key, it) }
        }.filter {
            it.second.available(context)
        }.filter {
            when (isInspecting) {
                true -> !it.second.dynamic
                else -> true
            }
        }
    }


    val originalDirection = LocalLayoutDirection.current

    Column {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            LazyVerticalGrid(
                modifier = Modifier.fillMaxWidth(),
                columns = GridCells.Adaptive(minSize = 172.dp),
                horizontalArrangement = if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
                    Arrangement.End
                } else {
                    Arrangement.Start
                }
            ) {
                items(availableThemeOptions.count()) {
                    val themeOption = availableThemeOptions[it].second

                    ThemePreview(themeOption, isSelected = themeOption.key == currentTheme) {
                        onSelected(themeOption)
                    }
                }

                item {
                    AddCustomThemeButton {
                        // TODO: Custom themes
                        val toast = Toast.makeText(
                            context,
                            "Custom themes coming eventually",
                            Toast.LENGTH_SHORT
                        )
                        toast.show()
                    }
                }

                item(span = { GridItemSpan(maxCurrentLineSpan) }) { }

                item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                    CompositionLocalProvider(LocalLayoutDirection provides originalDirection) {
                        SettingToggleDataStore(
                            title = "Key borders",
                            setting = KeyBordersSetting
                        )
                    }
                }
                item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                    CompositionLocalProvider(LocalLayoutDirection provides originalDirection) {
                        SettingToggleDataStore(
                            title = "Show symbol hints",
                            setting = KeyHintsSetting
                        )
                    }
                }

                item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                    CompositionLocalProvider(LocalLayoutDirection provides originalDirection) {
                        SettingSlider(
                            title = "Keyboard Height",
                            setting = KeyboardHeightMultiplierSetting,
                            range = 0.33f..1.75f, transform = { it },
                            indicator = { "${(it * 100.0f).roundToInt()}%" },
                            steps = 16
                        )
                    }
                }
                item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                    CompositionLocalProvider(LocalLayoutDirection provides originalDirection) {
                        SettingSlider(
                            title = "Keyboard Offset",
                            setting = KeyboardBottomOffsetSetting,
                            range = 0.0f..50.0f,
                            hardRange = 0.0f..250.0f,
                            transform = { it },
                            indicator = { "${String.format("%.1f", it)} dp" },
                            steps = 9
                        )
                    }
                }
            }
        }
    }
}


@Preview
@Composable
private fun ThemePickerPreview() {
    Column {
        UixThemeWrapper(VoiceInputTheme.obtainColors(LocalContext.current)) {
            Surface(
                color = MaterialTheme.colorScheme.background
            ) {
                ThemePicker {}
            }
        }
        UixThemeWrapper(ClassicMaterialDark.obtainColors(LocalContext.current)) {
            Surface(
                color = MaterialTheme.colorScheme.background
            ) {
                ThemePicker {}
            }
        }
        UixThemeWrapper(AMOLEDDarkPurple.obtainColors(LocalContext.current)) {
            Surface(
                color = MaterialTheme.colorScheme.background
            ) {
                ThemePicker {}
            }
        }
    }
}