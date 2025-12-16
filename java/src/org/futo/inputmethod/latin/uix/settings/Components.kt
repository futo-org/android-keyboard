package org.futo.inputmethod.latin.uix.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.LocalKeyboardScheme
import org.futo.inputmethod.latin.uix.LocalNavController
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.theme.Typography
import kotlin.math.pow

@Composable
fun ScreenTitle(title: String, showBack: Boolean = false, navController: NavHostController? = LocalNavController.current ?: rememberNavController()) {
    val rowModifier = if(showBack) {
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Navigate back") {
                navController!!.navigateUp()
            }
    } else {
        Modifier.fillMaxWidth()
    }
    Row(modifier = rowModifier) {
        Spacer(modifier = Modifier.width(16.dp))

        if(showBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.align(CenterVertically))
            Spacer(modifier = Modifier.width(18.dp))
        }
        Text(title, style = Typography.Heading.Medium, modifier = Modifier
            .align(CenterVertically)
            .padding(0.dp, 16.dp))
    }
}

@Composable
fun ScreenTitleWithIcon(title: String, painter: Painter) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.width(16.dp))

        Icon(painter, contentDescription = null, modifier = Modifier.align(CenterVertically))
        Spacer(modifier = Modifier.width(18.dp))
        Text(title, style = Typography.Heading.Medium, modifier = Modifier
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
            style = Typography.Body.RegularMl,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
@Preview
fun WarningTip(text: String = "This is an example tip") {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp), shape = RoundedCornerShape(4.dp)
    ) {

        Text(
            buildAnnotatedString {
                appendInlineContent("icon")
                append(' ')
                append(text)
            },
            modifier = Modifier.padding(8.dp),
            style = Typography.Body.RegularMl,
            color = MaterialTheme.colorScheme.onErrorContainer,
            inlineContent = mapOf(
                "icon" to InlineTextContent(
                    Placeholder(
                        width = with(LocalDensity.current) { 24.dp.toPx().toSp() },
                        height = with(LocalDensity.current) { 24.dp.toPx().toSp() },
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                    )
                ){
                    Icon(Icons.Default.Warning, contentDescription = null)
                }
            ))
    }
}

@Composable
fun SpacedColumn(gap: Dp, modifier: Modifier = Modifier, horizontalAlignment: Alignment.Horizontal = Alignment.Start, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = horizontalAlignment) {
        content()
    }
}


@Composable
fun SettingItem(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    disabled: Boolean = false,
    modifier: Modifier = Modifier,
    subcontent: (@Composable () -> Unit)? = null,
    compact: Boolean = false,
    onSubmenuNavigate: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val textColor = when(LocalContentColor.current) {
        MaterialTheme.colorScheme.onPrimary,
        MaterialTheme.colorScheme.onSecondary,
        MaterialTheme.colorScheme.onTertiary -> LocalContentColor.current

        else -> MaterialTheme.colorScheme.onSurface
    }

    val subTextColor = when(textColor) {
        MaterialTheme.colorScheme.onPrimary,
        MaterialTheme.colorScheme.onSecondary,
        MaterialTheme.colorScheme.onTertiary -> textColor.copy(alpha = 0.6f)

        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(0.dp, if(compact) { 48.dp } else { 68.dp })
            .let {
                if(onClick != null && onSubmenuNavigate == null) {
                    it.clickable(enabled = !disabled, onClick = {
                        if (!disabled) {
                            onClick()
                        }
                    })
                } else if(onSubmenuNavigate != null) {
                    it.clickable(enabled = !disabled, onClick = {
                        if (!disabled) {
                            onSubmenuNavigate()
                        }
                    })
                } else {
                    it
                }
            }
            .height(intrinsicSize = IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1.0f).fillMaxHeight().padding(0.dp, 4.dp)) {
            Spacer(Modifier.width(4.dp))
            Spacer(Modifier.width(16.dp))
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

            Spacer(Modifier.width(12.dp))

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
                SpacedColumn(4.dp) {
                    Text(
                        title,
                        style = Typography.Heading.RegularMl,
                        color = textColor,
                        modifier = Modifier.heightIn(min = 24.dp)
                    )

                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = Typography.SmallMl,
                            color = subTextColor
                        )
                    } else if (subcontent != null) {
                        subcontent()
                    }
                }
            }
            if(onSubmenuNavigate != null) { Spacer(Modifier.width(8.dp)) }
        }

        if(onSubmenuNavigate != null) {
            VerticalDivider(
                Modifier.height(64.dp),
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            Spacer(Modifier.width(4.dp))
        }

        Row(Modifier.let {
            if(onSubmenuNavigate != null && onClick != null) {
                it.clickable(enabled = !disabled, onClick = {
                    if(!disabled) {
                        onClick()
                    }
                })
            } else {
                it
            }
        }.fillMaxHeight()) {
            if(onSubmenuNavigate != null) { Spacer(Modifier.width(8.dp)) }
            Box(modifier = Modifier.align(Alignment.CenterVertically), contentAlignment = Alignment.Center) {
                content()
            }

            Spacer(modifier = Modifier.width(12.dp))
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
fun SettingToggleRaw(
    title: String,
    enabled: Boolean,
    setValue: (Boolean) -> Unit,
    subtitle: String? = null,
    disabled: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    onSubmenuNavigate: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    SettingItem(
        title = title,
        subtitle = subtitle,
        onClick = {
            if (!disabled) {
                setValue(!enabled)
            }
        },
        icon = icon,
        modifier = Modifier.let {
            if(onSubmenuNavigate == null) {
                it.clearAndSetSemantics {
                    this.text = AnnotatedString("$title. ${subtitle ?: ""}")
                    this.role = Role.Switch
                    this.toggleableState = ToggleableState(enabled)
                }
            } else {
                it
            }
        },
        onSubmenuNavigate = onSubmenuNavigate,
        compact = compact
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
    icon: (@Composable () -> Unit)? = null,
    onSubmenuNavigate: (() -> Unit)? = null,
) {
    val (enabled, setValue) = dataStoreItem

    val subtitleValue = if (!enabled && disabledSubtitle != null) {
        disabledSubtitle
    } else {
        subtitle
    }

    SettingToggleRaw(title, enabled, { setValue(it) }, subtitleValue, disabled, icon, onSubmenuNavigate)
}

@Composable
fun SettingToggleDataStore(
    title: String,
    setting: SettingsKey<Boolean>,
    subtitle: String? = null,
    disabledSubtitle: String? = null,
    disabled: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    onSubmenuNavigate: (() -> Unit)? = null,
) {
    key(setting) {
        SettingToggleDataStoreItem(
            title,
            useDataStore(setting.key, setting.default),
            subtitle,
            disabledSubtitle,
            disabled,
            icon,
            onSubmenuNavigate
        )
    }
}

@Composable
fun SettingToggleSharedPrefs(
    title: String,
    key: String,
    default: Boolean,
    subtitle: String? = null,
    disabledSubtitle: String? = null,
    disabled: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    onSubmenuNavigate: (() -> Unit)? = null,
) {
    key(key) {
        SettingToggleDataStoreItem(
            title, useSharedPrefsBool(key, default), subtitle, disabledSubtitle, disabled, icon, onSubmenuNavigate
        )
    }
}

@Composable
fun<T> SettingRadio(
    title: String,
    options: List<T>,
    optionNames: List<String>,
    setting: DataStoreItem<T>,
    hints: List<@Composable () -> Unit>? = null,
) {
    ScreenTitle(title, showBack = false)
    Column {
        options.zip(optionNames).forEachIndexed { i, it ->
            SettingItem(title = it.second, onClick = { setting.setValue(it.first) }, icon = {
                RadioButton(selected = setting.value == it.first, onClick = null)
            }, modifier = Modifier.clearAndSetSemantics {
                this.text = AnnotatedString(it.second)
                this.role = Role.RadioButton
                this.selected = setting.value == it.first
            }) {
                hints?.getOrNull(i)?.let { it() }
            }
        }
    }
}

@Composable
fun<T: Number> SettingSliderForDataStoreItem(
    title: String,
    item: DataStoreItem<T>,
    default: T,
    range: ClosedFloatingPointRange<Float>,
    transform: (Float) -> T,
    indicator: (T) -> String = { it.toString() },
    hardRange: ClosedFloatingPointRange<Float> = range,
    power: Float = 1.0f,
    subtitle: String? = null,
    steps: Int = 0,
) {
    val context = LocalContext.current

    val (value, setValue) = item
    var virtualValue by remember { mutableFloatStateOf(value.toFloat().let {
        if(it == Float.POSITIVE_INFINITY || it == Float.NEGATIVE_INFINITY) {
            it
        } else {
            it.pow(1.0f / power)
        }
    }) }
    var isTextFieldVisible by remember { mutableStateOf(false) }
    var hasTextFieldFocusedYet by remember { mutableStateOf(false) }
    var textFieldValue by remember(value) {
        val s = value.toString()
        mutableStateOf(TextFieldValue(
            s,
            selection = TextRange(0, s.length)
        ))
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isTextFieldVisible) {
        if(isTextFieldVisible) focusRequester.requestFocus()
    }

    Column {
        ScreenTitle(title, showBack = false)
        if(subtitle != null) {
            Text(subtitle, style = Typography.Body.MediumMl, modifier = Modifier.padding(12.dp, 0.dp))
        }
        Row(modifier = Modifier.padding(16.dp, 0.dp)) {
            if (isTextFieldVisible) {
                val apply = {
                    if(isTextFieldVisible) {
                        val number = textFieldValue.text.trim().toFloatOrNull()
                        val newValue = if (number != null) {
                            transform(number.coerceIn(hardRange))
                        } else {
                            default
                        }

                        setValue(newValue)
                        virtualValue = newValue.toFloat().pow(1.0f / power)

                        isTextFieldVisible = false
                        textFieldValue = TextFieldValue()
                    }
                }

                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    modifier = Modifier
                        .weight(0.33f)
                        .align(CenterVertically)
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            if (it.isFocused) hasTextFieldFocusedYet = true
                            else if (!it.isFocused && hasTextFieldFocusedYet) apply()
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            apply()
                        }
                    ),
                    singleLine = true,
                    textStyle = Typography.SmallMl.copy(color = MaterialTheme.colorScheme.onBackground),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                )
            } else {
                Text(
                    text = indicator(value),
                    modifier = Modifier
                        .weight(0.33f)
                        .align(Alignment.CenterVertically)
                        .clickable {
                            hasTextFieldFocusedYet = false
                            isTextFieldVisible = true
                        },
                    style = Typography.SmallMl
                )
            }
            Slider(
                value = virtualValue,
                onValueChange = {
                    virtualValue = it
                    setValue(transform(it.pow(power))) },
                valueRange = range.start.pow(1.0f / power) .. range.endInclusive.pow(1.0f / power),
                enabled = !isTextFieldVisible,
                modifier = Modifier.weight(1.0f),
                steps = steps
            )
        }
    }
}



@Composable
fun<T: Number> SettingSlider(
    title: String,
    setting: SettingsKey<T>,
    range: ClosedFloatingPointRange<Float>,
    transform: (Float) -> T,
    indicator: (T) -> String = { it.toString() },
    hardRange: ClosedFloatingPointRange<Float> = range,
    power: Float = 1.0f,
    subtitle: String? = null,
    steps: Int = 0
) {
    SettingSliderForDataStoreItem(
        title = title,
        item = useDataStore(setting, blocking = true),
        default = setting.default,
        range = range,
        transform = transform,
        indicator = indicator,
        hardRange = hardRange,
        power = power,
        subtitle = subtitle,
        steps = steps
    )
}

@Composable
fun SettingSliderSharedPrefsInt(
    title: String,
    key: String,
    default: Int,
    range: ClosedFloatingPointRange<Float>,
    transform: (Float) -> Int,
    indicator: (Int) -> String = { it.toString() },
    hardRange: ClosedFloatingPointRange<Float> = range,
    power: Float = 1.0f,
    subtitle: String? = null,
    steps: Int = 0
) {
    SettingSliderForDataStoreItem(
        title = title,
        item = useSharedPrefsInt(key, default),
        default = default,
        range = range,
        transform = transform,
        indicator = indicator,
        hardRange = hardRange,
        power = power,
        subtitle = subtitle,
        steps = steps
    )
}

@Composable
fun ScrollableList(modifier: Modifier = Modifier, spacing: Dp = 0.dp, content: @Composable () -> Unit) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(spacing)
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
    Misc,
    ExternalLink,
    Mail
}

@Composable
fun NavigationItem(title: String, style: NavigationItemStyle, navigate: () -> Unit, icon: Painter? = null, subtitle: String? = null, compact: Boolean = false) {
    SettingItem(
        title = title,
        subtitle = subtitle,
        onClick = navigate,
        compact = compact,
        icon = {
            icon?.let {
                val circleColor = when(style) {
                    NavigationItemStyle.HomePrimary -> MaterialTheme.colorScheme.primaryContainer
                    NavigationItemStyle.HomeSecondary -> MaterialTheme.colorScheme.secondaryContainer
                    NavigationItemStyle.HomeTertiary -> MaterialTheme.colorScheme.tertiaryContainer

                    NavigationItemStyle.MiscNoArrow,
                    NavigationItemStyle.Misc,
                    NavigationItemStyle.ExternalLink,
                    NavigationItemStyle.Mail -> Color.Transparent
                }

                val iconColor = when(style) {
                    NavigationItemStyle.HomePrimary -> MaterialTheme.colorScheme.onPrimaryContainer
                    NavigationItemStyle.HomeSecondary -> MaterialTheme.colorScheme.onSecondaryContainer
                    NavigationItemStyle.HomeTertiary -> MaterialTheme.colorScheme.onTertiaryContainer

                    NavigationItemStyle.MiscNoArrow,
                    NavigationItemStyle.Mail,
                    NavigationItemStyle.ExternalLink,
                    NavigationItemStyle.Misc -> LocalContentColor.current.copy(alpha = 0.75f)
                }

                Canvas(modifier = Modifier.size(48.dp)) {
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
            NavigationItemStyle.Misc -> Icon(Icons.Default.ArrowForward, contentDescription = null)
            NavigationItemStyle.Mail -> Icon(Icons.Default.Send, contentDescription = null)
            NavigationItemStyle.ExternalLink -> Icon(painterResource(R.drawable.external_link), contentDescription = null)
            else -> {}
        }
    }
}

@Composable
fun SettingTextField(title: String, placeholder: String, field: SettingsKey<String>) {
    val context = LocalContext.current

    val personalDict = useDataStore(field)
    val textFieldValue = remember { mutableStateOf(context.getSettingBlocking(
        field.key, field.default)) }

    LaunchedEffect(textFieldValue.value) {
        personalDict.setValue(textFieldValue.value)
    }

    ScreenTitle(title)

    TextField(
        value = textFieldValue.value,
        onValueChange = {
            textFieldValue.value = it
        },
        placeholder = { Text(placeholder) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp, 4.dp),
    )
}

/*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun<T> DropDownPicker(
    label: String,
    options: List<T>,
    selection: T?,
    onSet: (T) -> Unit,
    getDisplayName: (T) -> String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            expanded = !expanded
        },
        modifier = modifier
    ) {
        TextField(
            readOnly = true,
            value = selection?.let(getDisplayName) ?: "None",
            onValueChange = { },
            label = if (label.isNotBlank()) {
                { Text(label) }
            } else {
                null
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded
                )
            },
            colors = ExposedDropdownMenuDefaults.textFieldColors(
                focusedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                focusedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                focusedIndicatorColor = MaterialTheme.colorScheme.onPrimaryContainer,
                focusedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            }
        ) {
            options.forEach { selectionOption ->
                DropdownMenuItem(
                    text = {
                        Text(getDisplayName(selectionOption))
                    },
                    onClick = {
                        onSet(selectionOption)
                        expanded = false
                    }
                )
            }
        }
    }
}*/

private val DropDownShape = RoundedCornerShape(12.dp)
@Composable
fun<T> DropDownPicker(
    options: List<T>,
    selection: T?,
    onSet: (T) -> Unit,
    getDisplayName: (T) -> String,
    scrollableOptions: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }


    SpacedColumn(4.dp, modifier = modifier.semantics {
        role = Role.DropdownList
    }) {
        Row(
            Modifier.fillMaxWidth().background(
                MaterialTheme.colorScheme.surfaceContainerHighest, DropDownShape
            ).border(
                if(expanded) { 2.dp } else { 1.dp },
                MaterialTheme.colorScheme.outline,
                DropDownShape
            ).heightIn(min = 44.dp).clip(DropDownShape).clickable {
                expanded = !expanded
            }.padding(16.dp).semantics {
                // TODO: Localization
                stateDescription = if(expanded) "Expanded" else "Collapsed"
                role = Role.DropdownList
            }
        ) {
            if(selection != null) {
                Text(
                    text = getDisplayName(selection),
                    style = Typography.Body.Regular,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1.0f)
                )
            } else {
                Spacer(Modifier.weight(1.0f))
            }

            RotatingChevronIcon(expanded, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
            val scrollState = rememberScrollState()
            Column(Modifier.let {
                if(scrollableOptions) {
                    it.verticalScroll(scrollState)
                } else {
                    it
                }
            }) {
                Spacer(Modifier.height(9.dp))
                Column(
                    Modifier.fillMaxWidth().background(
                        MaterialTheme.colorScheme.surfaceContainerHighest, DropDownShape
                    ).border(
                        1.dp,
                        MaterialTheme.colorScheme.outline,
                        DropDownShape
                    ).clip(DropDownShape)
                ) {
                    options.forEach {
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp).background(
                                if(selection == it) {
                                    LocalKeyboardScheme.current.onSurfaceTransparent
                                } else {
                                    Color.Transparent
                                }
                            ).clickable {
                                onSet(it)
                                expanded = false
                            }.padding(16.dp).semantics {
                                selected = selection == it
                                role = Role.DropdownList
                            }
                        ) {
                            Text(
                                getDisplayName(it),
                                style = Typography.Body.Regular,
                                color = if(selection == it) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.align(Alignment.CenterStart)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun<T> DropDownPickerSettingItem(
    label: String,
    options: List<T>,
    selection: T?,
    onSet: (T) -> Unit,
    getDisplayName: (T) -> String,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
) {
    SettingItem(
        title = label,
        icon = icon,
        subcontent = {
            DropDownPicker(options, selection, onSet, getDisplayName)
        },
        modifier = modifier
    ) {

    }
}

@Composable
fun RotatingChevronIcon(isExpanded: Boolean, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) -180f else 0f
    )

    Icon(
        painter = painterResource(R.drawable.chevron_down),
        contentDescription = null,
        modifier = modifier.rotate(rotation),
        tint = tint
    )
}

@Composable
fun PrimarySettingToggleDataStoreItem(
    title: String,
    dataStoreItem: DataStoreItem<Boolean>,
) {
    val (enabled, setValue) = dataStoreItem

    Box(Modifier.padding(24.dp)) {
        Surface(
            shape = RoundedCornerShape(48.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.clearAndSetSemantics {
                this.text = AnnotatedString(title)
                this.role = Role.Switch
                this.toggleableState = ToggleableState(enabled)
            },
            onClick = {
                setValue(!enabled)
            }
        ) {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = Typography.Heading.RegularMl,
                    modifier = Modifier.heightIn(min = 24.dp)
                )
                Spacer(Modifier.weight(1.0f))

                Switch(checked = enabled, onCheckedChange = null)
            }
        }
    }
}

@Preview
@Composable
fun PreviewPrimarySetting() {
    PrimarySettingToggleDataStoreItem(
        "Enable",
        dataStoreItem = DataStoreItem(false, { error("") })
    )
}