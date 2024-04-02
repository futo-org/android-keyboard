package org.futo.inputmethod.latin.uix.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.theme.Typography

@Composable
fun ScreenTitle(title: String, showBack: Boolean = false, navController: NavHostController = rememberNavController()) {
    val rowModifier = if(showBack) {
        Modifier
            .fillMaxWidth()
            .clickable { navController.navigateUp() }
    } else {
        Modifier.fillMaxWidth()
    }
    Row(modifier = rowModifier) {
        Spacer(modifier = Modifier.width(16.dp))

        if(showBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.align(CenterVertically))
            Spacer(modifier = Modifier.width(18.dp))
        }
        Text(title, style = Typography.titleLarge, modifier = Modifier
            .align(CenterVertically)
            .padding(0.dp, 16.dp))
    }
}

@Composable
@Preview
fun Tip(text: String = "This is an example tip") {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp), shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text,
            modifier = Modifier.padding(8.dp),
            style = Typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}


@Composable
fun SettingItem(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    disabled: Boolean = false,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(0.dp, 68.dp)
            .clickable(enabled = !disabled && onClick != null, onClick = {
                if (!disabled && onClick != null) {
                    onClick()
                }
            })
            .padding(0.dp, 4.dp, 0.dp, 4.dp)
    ) {
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier
                .width(48.dp)
                .align(Alignment.CenterVertically)
        ) {
            Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                if (icon != null) {
                    icon()
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically)
                .alpha(
                    if (disabled) {
                        0.5f
                    } else {
                        1.0f
                    }
                )
        ) {
            Column {
                Text(title, style = Typography.bodyLarge)

                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = Typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
        Box(modifier = Modifier.align(Alignment.CenterVertically)) {
            content()
        }

        Spacer(modifier = Modifier.width(12.dp))
    }
}

@Composable
fun SettingToggleRaw(
    title: String,
    enabled: Boolean,
    setValue: (Boolean) -> Unit,
    subtitle: String? = null,
    disabled: Boolean = false,
    icon: (@Composable () -> Unit)? = null
) {
    SettingItem(
        title = title,
        subtitle = subtitle,
        onClick = {
            if (!disabled) {
                setValue(!enabled)
            }
        },
        icon = icon
    ) {
        Switch(checked = enabled, onCheckedChange = {
            if (!disabled) {
                setValue(!enabled)
            }
        }, enabled = !disabled)
    }
}

@Composable
fun SettingToggleDataStoreItem(
    title: String,
    dataStoreItem: DataStoreItem<Boolean>,
    subtitle: String? = null,
    disabledSubtitle: String? = null,
    disabled: Boolean = false,
    icon: (@Composable () -> Unit)? = null
) {
    val (enabled, setValue) = dataStoreItem

    val subtitleValue = if (!enabled && disabledSubtitle != null) {
        disabledSubtitle
    } else {
        subtitle
    }

    SettingToggleRaw(title, enabled, { setValue(it) }, subtitleValue, disabled, icon)
}

@Composable
fun SettingToggleDataStore(
    title: String,
    setting: SettingsKey<Boolean>,
    subtitle: String? = null,
    disabledSubtitle: String? = null,
    disabled: Boolean = false,
    icon: (@Composable () -> Unit)? = null
) {
    SettingToggleDataStoreItem(
        title, useDataStore(setting.key, setting.default), subtitle, disabledSubtitle, disabled, icon)
}

@Composable
fun SettingToggleSharedPrefs(
    title: String,
    key: String,
    default: Boolean,
    subtitle: String? = null,
    disabledSubtitle: String? = null,
    disabled: Boolean = false,
    icon: (@Composable () -> Unit)? = null
) {
    SettingToggleDataStoreItem(
        title, useSharedPrefsBool(key, default), subtitle, disabledSubtitle, disabled, icon)
}

@Composable
fun<T> SettingRadio(
    title: String,
    options: List<T>,
    optionNames: List<String>,
    setting: SettingsKey<T>,
) {
    val (value, setValue) = useDataStore(key = setting.key, default = setting.default)

    ScreenTitle(title, showBack = false)
    Column {
        options.zip(optionNames).forEach {
            SettingItem(title = it.second, onClick = { setValue(it.first) }, icon = {
                RadioButton(selected = value == it.first, onClick = null)
            }) {
                
            }
        }
    }
}

@Composable
fun ScrollableList(content: @Composable () -> Unit) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        content()
    }
}

@Composable
fun SettingListLazy(content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
    ) {
        content()
    }
}


enum class NavigationItemStyle {
    HomePrimary,
    HomeSecondary,
    HomeTertiary,
    MiscNoArrow,
    Misc
}

@Composable
fun NavigationItem(title: String, style: NavigationItemStyle, navigate: () -> Unit, icon: Painter? = null, subtitle: String? = null) {
    SettingItem(
        title = title,
        subtitle = subtitle,
        onClick = navigate,
        icon = {
            icon?.let {
                val circleColor = when(style) {
                    NavigationItemStyle.HomePrimary -> MaterialTheme.colorScheme.primaryContainer
                    NavigationItemStyle.HomeSecondary -> MaterialTheme.colorScheme.secondaryContainer
                    NavigationItemStyle.HomeTertiary -> MaterialTheme.colorScheme.tertiaryContainer
                    NavigationItemStyle.MiscNoArrow -> Color.Transparent
                    NavigationItemStyle.Misc -> Color.Transparent
                }

                val iconColor = when(style) {
                    NavigationItemStyle.HomePrimary -> MaterialTheme.colorScheme.onPrimaryContainer
                    NavigationItemStyle.HomeSecondary -> MaterialTheme.colorScheme.onSecondaryContainer
                    NavigationItemStyle.HomeTertiary -> MaterialTheme.colorScheme.onTertiaryContainer
                    NavigationItemStyle.MiscNoArrow -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                    NavigationItemStyle.Misc -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(circleColor, this.size.maxDimension / 2.4f)
                    translate(
                        left = this.size.width / 2.0f - icon.intrinsicSize.width / 2.0f,
                        top = this.size.height / 2.0f - icon.intrinsicSize.height / 2.0f
                    ) {
                        with(icon) {
                            draw(icon.intrinsicSize, colorFilter = ColorFilter.tint(iconColor))
                        }
                    }
                }
            }
        }
    ) {
        when(style) {
            NavigationItemStyle.Misc -> Icon(Icons.Default.ArrowForward, contentDescription = "Go")
            else -> {}
        }
    }
}