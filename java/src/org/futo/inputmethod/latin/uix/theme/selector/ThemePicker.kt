package org.futo.inputmethod.latin.uix.theme.selector

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.KeyBordersSetting
import org.futo.inputmethod.latin.uix.KeyboardBackground
import org.futo.inputmethod.latin.uix.KeyboardColorScheme
import org.futo.inputmethod.latin.uix.THEME_KEY
import org.futo.inputmethod.latin.uix.setSetting
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.theme.CustomThemes
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.ThemeOptionKeys
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.latin.uix.theme.UixThemeWrapper
import org.futo.inputmethod.latin.uix.theme.getThemeOption
import org.futo.inputmethod.latin.uix.theme.presets.AMOLEDDarkPurple
import org.futo.inputmethod.latin.uix.theme.presets.ClassicMaterialDark
import org.futo.inputmethod.latin.uix.theme.presets.DefaultLightScheme
import org.futo.inputmethod.latin.uix.theme.presets.DynamicDarkTheme
import org.futo.inputmethod.latin.uix.theme.presets.DynamicLightTheme
import org.futo.inputmethod.latin.uix.theme.presets.DynamicSystemTheme
import org.futo.inputmethod.latin.uix.theme.presets.VoiceInputTheme

@Composable
fun ThemePreview(theme: ThemeOption, isSelected: Boolean = false, overrideName: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit = { }) {
    if(theme == DynamicSystemTheme) return DynamicThemePreview(isSelected, onClick)

    val context = LocalContext.current
    val colors = remember(theme) { theme.obtainColors(context) }

    return ThemePreview(
        colors = colors,
        name = overrideName ?: stringResource(theme.name),
        loading = false,
        isSelected = isSelected,
        modifier = modifier,
        onClick = onClick
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThemePreview(colors: KeyboardColorScheme, name: String, loading: Boolean, isSelected: Boolean = false, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, onClick: () -> Unit = { }) {
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

    val spacebarColor = colors.keyboardContainer
    val actionColor = colors.primary

    val keyboardShape = RoundedCornerShape(8.dp)

    val previewModifier = if(LocalInspectionMode.current) {
        modifier.width(172.dp)
    } else {
        modifier
    }

    Box(
        modifier = previewModifier
            .padding(12.dp)
            .height(128.dp)
            .border(borderWidth, borderColor, keyboardShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .clip(keyboardShape),
    ) {
        KeyboardBackground(colors, useThumbnail = true)
        Box(modifier = Modifier.fillMaxSize()) {
            // Theme name and action bar
            Text(
                text = name,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .background(colors.keyboardSurfaceDim.copy(
                        alpha = if(colors.extended.advancedThemeOptions.thumbnailImage == null) {
                            1.0f
                        } else {
                            0.4f
                        }
                    ))
                    .fillMaxWidth()
                    .padding(4.dp),
                color = textColor,
                style = Typography.SmallMl
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
                    shape = RoundedCornerShape(4.dp)
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


@Composable
fun CustomThemePreview(customThemeName: String, isSelected: Boolean, modifier: Modifier, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val context = LocalContext.current

    val loading = remember { mutableStateOf(true) }
    val scheme = remember { mutableStateOf<KeyboardColorScheme>(DefaultLightScheme.obtainColors(context)) }

    LaunchedEffect(customThemeName) {
        loading.value = true
        scheme.value = DefaultLightScheme.obtainColors(context)
        withContext(Dispatchers.Default) {
            scheme.value = CustomThemes.loadSchemeThumb(context, customThemeName)
        }
        loading.value = false
    }

    ThemePreview(
        colors = scheme.value,
        name = stringResource(R.string.theme_custom_named, customThemeName),
        loading = loading.value,
        isSelected = isSelected,
        modifier = modifier,
        onLongClick = onLongClick,
        onClick = onClick,
    )
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
fun AddCustomThemeButton(short: Boolean = false, onClick: () -> Unit = { }) {
    val currColors = MaterialTheme.colorScheme

    val keyboardShape = RoundedCornerShape(8.dp)

    Surface(
        modifier = Modifier
            .padding(12.dp)
            .width(172.dp)
            .height(if(short) 64.dp else 128.dp )
            .clickable { onClick() },
        color = currColors.surfaceVariant,
        shape = keyboardShape
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Icon(
                Icons.Default.Add, contentDescription = null, modifier = Modifier
                    .size(if(short) 32.dp else 48.dp)
                    .align(
                        Alignment.Center
                    )
            )
        }
    }
}

@Composable
fun ThemePicker(onDeleteCustomTheme: (String) -> Unit, onCustomTheme: () -> Unit) {
    val context = LocalContext.current

    val currentTheme = useDataStore(THEME_KEY.key, "").value

    val isInspecting = LocalInspectionMode.current
    val availableThemeOptions = remember {
        ThemeOptionKeys.mapNotNull { key ->
            getThemeOption(context, key)?.let { Pair(key, it) }
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

    val customThemes = remember(CustomThemes.updateCount.intValue) {
        CustomThemes.list(context)
    }

    val lifecycle = LocalLifecycleOwner.current
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
                item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                    ScreenTitle(stringResource(R.string.theme_settings_custom_themes))
                }

                items(customThemes.size) {
                    val name = customThemes[it]
                    CustomThemePreview(name, isSelected = currentTheme == "custom$name", modifier = Modifier, onLongClick = {
                        onDeleteCustomTheme(name)
                    }) {
                        lifecycle.lifecycleScope.launch {
                            context.setSetting(THEME_KEY, "custom$name")
                        }
                    }
                }

                item {
                    AddCustomThemeButton(customThemes.isEmpty()) {
                        onCustomTheme()
                    }
                }

                item(span = { GridItemSpan(maxCurrentLineSpan) }) { }
                item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                    ScreenTitle(stringResource(R.string.theme_settings_default_themes))
                }
                items(availableThemeOptions.size) {
                    val themeOption = availableThemeOptions[it].second

                    ThemePreview(themeOption, isSelected = themeOption.key == currentTheme) {
                        lifecycle.lifecycleScope.launch {
                            context.setSetting(THEME_KEY, themeOption.key)
                        }
                    }
                }


                item(span = { GridItemSpan(maxCurrentLineSpan) }) { }

                item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                    CompositionLocalProvider(LocalLayoutDirection provides originalDirection) {
                        SettingToggleDataStore(
                            title = stringResource(R.string.theme_settings_key_borders),
                            setting = KeyBordersSetting
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
                ThemePicker({},{})
            }
        }
        UixThemeWrapper(ClassicMaterialDark.obtainColors(LocalContext.current)) {
            Surface(
                color = MaterialTheme.colorScheme.background
            ) {
                ThemePicker({},{})
            }
        }
        UixThemeWrapper(AMOLEDDarkPurple.obtainColors(LocalContext.current)) {
            Surface(
                color = MaterialTheme.colorScheme.background
            ) {
                ThemePicker({},{})
            }
        }
    }
}