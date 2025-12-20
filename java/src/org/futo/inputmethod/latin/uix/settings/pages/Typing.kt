package org.futo.inputmethod.latin.uix.settings.pages

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextInputSession
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import org.futo.inputmethod.accessibility.AccessibilityUtils
import org.futo.inputmethod.engine.IMESettingsMenu
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.settings.LongPressKey
import org.futo.inputmethod.latin.settings.LongPressKeyLayoutSetting
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.settings.Settings.PREF_KEYPRESS_SOUND_VOLUME
import org.futo.inputmethod.latin.settings.Settings.PREF_VIBRATION_DURATION_SETTINGS
import org.futo.inputmethod.latin.settings.description
import org.futo.inputmethod.latin.settings.name
import org.futo.inputmethod.latin.settings.toEncodedString
import org.futo.inputmethod.latin.settings.toLongPressKeyLayoutItems
import org.futo.inputmethod.latin.uix.AndroidTextInput
import org.futo.inputmethod.latin.uix.BasicThemeProvider
import org.futo.inputmethod.latin.uix.KeyHintsSetting
import org.futo.inputmethod.latin.uix.LocalKeyboardScheme
import org.futo.inputmethod.latin.uix.SHOW_EMOJI_SUGGESTIONS
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.setSettingBlocking
import org.futo.inputmethod.latin.uix.settings.BottomSpacer
import org.futo.inputmethod.latin.uix.settings.DataStoreItem
import org.futo.inputmethod.latin.uix.settings.DropDownPickerSettingItem
import org.futo.inputmethod.latin.uix.settings.LocalSharedPrefsCache
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.PrimarySettingToggleDataStoreItem
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingItem
import org.futo.inputmethod.latin.uix.settings.SettingRadio
import org.futo.inputmethod.latin.uix.settings.SettingSlider
import org.futo.inputmethod.latin.uix.settings.SettingSliderSharedPrefsInt
import org.futo.inputmethod.latin.uix.settings.SyncDataStoreToPreferencesFloat
import org.futo.inputmethod.latin.uix.settings.SyncDataStoreToPreferencesInt
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.settings.UserSetting
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.render
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.useSharedPrefsBool
import org.futo.inputmethod.latin.uix.settings.useSharedPrefsInt
import org.futo.inputmethod.latin.uix.settings.userSettingDecorationOnly
import org.futo.inputmethod.latin.uix.settings.userSettingNavigationItem
import org.futo.inputmethod.latin.uix.settings.userSettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.userSettingToggleSharedPrefs
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.v2keyboard.KeyboardSettings
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sign

val vibrationDurationSetting = SettingsKey(
    intPreferencesKey("vibration_duration"),
    -1
)

val keySoundVolumeSetting = SettingsKey(
    floatPreferencesKey("key_sound_volume"),
    0.0f
)

val ActionBarDisplayedSetting = SettingsKey(
    booleanPreferencesKey("enable_action_bar"),
    true
)

val InlineAutofillSetting = SettingsKey(
    booleanPreferencesKey("inline_autofill"),
    true
)

val ResizeMenuLite = UserSettingsMenu(
    title = R.string.size_settings_title,
    navPath = "resize", registerNavPath = false,
    settings = listOf(
        userSettingNavigationItem(
            title = R.string.size_settings_reset,
            subtitle = R.string.size_settings_reset_subtitle,
            style = NavigationItemStyle.Misc,
            icon = R.drawable.close,
            navigate = { nav ->
                KeyboardSettings.values.forEach {
                    nav.context.setSettingBlocking(it.key, it.default)
                }
            }
        )
    )
)

@OptIn(ExperimentalLayoutApi::class)
@Preview(showBackground = true)
@Composable
fun ResizeScreen(navController: NavHostController = rememberNavController()) {
    val textInputService = LocalTextInputService.current
    val session = remember { mutableStateOf<TextInputSession?>(null) }

    DisposableEffect(Unit) {
        session.value = textInputService?.startInput(
            TextFieldValue(""),
            imeOptions = ImeOptions.Default.copy(
                platformImeOptions = PlatformImeOptions(
                    privateImeOptions = "org.futo.inputmethod.latin.ResizeMode=1"
                )
            ),
            onEditCommand = { },
            onImeActionPerformed = { }
        )

        onDispose {
            textInputService?.stopInput(session.value ?: return@onDispose)
        }
    }

    Box {
        ScrollableList {
            ScreenTitle(stringResource(R.string.size_settings_title), showBack = true, navController)

            PaymentSurface(
                isPrimary = false,
            ) {
                PaymentSurfaceHeading(title = stringResource(R.string.settings_tip))

                Text(
                    buildAnnotatedString {
                        append(stringResource(R.string.size_settings_keyboard_modes_tip))
                        append(" ")
                        appendInlineContent("icon")
                        appendLine()
                        append(stringResource(R.string.size_settings_keyboard_modes_portrait_landscape_tip))
                        appendLine()
                        append(stringResource(R.string.size_settings_resize_tip))
                    },
                    style = Typography.Body.MediumMl,
                    color = LocalContentColor.current,
                    inlineContent = mapOf(
                        "icon" to InlineTextContent(
                            Placeholder(
                                width = with(LocalDensity.current) { 24.dp.toPx().toSp() },
                                height = with(LocalDensity.current) { 24.dp.toPx().toSp() },
                                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                            )
                        ){
                            Icon(painterResource(R.drawable.keyboard_gear), contentDescription = null)
                        }
                    ))
            }

            Spacer(Modifier.height(8.dp))
            ResizeMenuLite.render(showTitle = false)

            AndroidTextInput(allowPredictions = false, customOptions = setOf("org.futo.inputmethod.latin.ResizeMode"), autoshow = false)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DraggableSettingItem(idx: Int, item: LongPressKey, moveItem: (LongPressKey, Int) -> Unit, disable: (LongPressKey) -> Unit, dragIcon: @Composable () -> Unit, limits: IntRange) {
    val context = LocalContext.current
    val talkBackOn = remember {
        AccessibilityUtils.init(context)
        AccessibilityUtils.getInstance().isAccessibilityEnabled
    }

    val customActions = remember(idx, limits, item) {
        buildList {
            if (idx > limits.first) {
                add(
                    CustomAccessibilityAction(
                        context.getString(R.string.morekey_settings_move_kind_up)
                    ) {
                        moveItem(item, -1)
                        true
                    }
                )

                add(
                    CustomAccessibilityAction(
                        context.getString(R.string.morekey_settings_move_kind_up_to_top)
                    ) {
                        moveItem(item, -100)
                        true
                    }
                )
            }
            if (idx < limits.last) {
                add(
                    CustomAccessibilityAction(
                        context.getString(R.string.morekey_settings_move_kind_down)
                    ) {
                        moveItem(item, 1)
                        true
                    }
                )
                add(
                    CustomAccessibilityAction(
                        context.getString(R.string.morekey_settings_move_kind_down_to_bottom)
                    ) {
                        moveItem(item, 100)
                        true
                    }
                )
            }
            add(
                CustomAccessibilityAction(
                    context.getString(R.string.morekey_settings_disable)
                ) {
                    disable(item)
                    true
                }
            )
        }
    }

    val semantics = Modifier.clearAndSetSemantics {
        contentDescription = item.name(context)
        stateDescription = context.getString(
            R.string.morekey_settings_kind_position,
            (idx + 1).toString(),
            (limits.last + 1).toString()
        )

        if (talkBackOn) {
            this.customActions = customActions
        }
    }

    val dragging = remember { mutableStateOf(false) }
    val offset = remember { mutableFloatStateOf(0.0f) }
    val height = remember { mutableIntStateOf(1) }

    val pendingOffsetDiff = remember { mutableFloatStateOf(0.0f) }
    LaunchedEffect(idx, pendingOffsetDiff.floatValue) {
        if(pendingOffsetDiff.floatValue != 0.0f) {
            offset.floatValue += pendingOffsetDiff.floatValue
            pendingOffsetDiff.floatValue = 0.0f
        }
    }

    val shouldClampLower = (idx - 1) < limits.first
    val shouldClampUpper = (idx + 1) > limits.last

    SettingItem(
        title = "${idx+1}. " + item.name(context),
        subtitle = item.description(context),
        icon = {
            if(talkBackOn) {
                Column {
                    IconButton(onClick = { moveItem(item, -1) }) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.morekey_settings_move_kind_up)
                        )
                    }
                    IconButton(onClick = { moveItem(item, 1) }) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.morekey_settings_move_kind_down)
                        )
                    }
                }
            } else {
                Box(Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            dragging.value = true
                            offset.floatValue = 0.0f
                        },
                        onDragEnd = {
                            dragging.value = false
                            offset.floatValue = 0.0f
                        },
                        onDragCancel = {
                            dragging.value = false
                            offset.floatValue = 0.0f
                        },
                        onDrag = { change, dragAmount ->
                            offset.floatValue += dragAmount.y

                            if ((offset.floatValue + pendingOffsetDiff.floatValue).absoluteValue > height.intValue) {
                                val direction = offset.floatValue.sign.toInt()
                                moveItem(
                                    item,
                                    direction
                                )
                                pendingOffsetDiff.floatValue -= height.intValue * direction
                            }

                        }
                    )
                }) {
                    dragIcon()
                }
            }
        },
        modifier = semantics
            .onSizeChanged { size -> height.intValue = size.height }
            .let { modifier ->
                if (!dragging.value) {
                    modifier
                        .background(LocalKeyboardScheme.current.surfaceTint.copy(alpha = if(idx % 2 == 0) 0.02f else 0.06f))
                } else {
                    modifier
                        .zIndex(10.0f)
                        .graphicsLayer {
                            clip = false
                            translationX = 0.0f
                            translationY = offset.floatValue.let {
                                if (shouldClampLower && it < 0.0f) 0.0f
                                else if (shouldClampUpper && it > 0.0f) 0.0f
                                else it
                            }
                        }
                        .background(
                            LocalKeyboardScheme.current.surfaceTint.copy(alpha = 0.2f)
                                .compositeOver(LocalKeyboardScheme.current.background)
                        )
                }
            }
    ) {
        IconButton(onClick = { disable(item) }) {
            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.morekey_settings_disable))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LongPressKeyLayoutEditor(context: Context, setting: DataStoreItem<String>) {
    Row(Modifier.padding(16.dp)) {
        Text(stringResource(R.string.morekey_settings_layout), style = Typography.Heading.Medium, modifier = Modifier
            .align(CenterVertically)
            .weight(1.0f))

        Spacer(Modifier.width(4.dp))

        Button(onClick = {
            setting.setValue(LongPressKeyLayoutSetting.default)
        }) {
            Text(stringResource(R.string.morekey_settings_reset))
        }
    }


    val dragIcon: @Composable () -> Unit = {
        Icon(Icons.Default.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
    }

    val items = setting.value.toLongPressKeyLayoutItems()

    val moveItem: (item: LongPressKey, direction: Int) -> Unit = { item, direction ->
        val oldItems = context.getSettingBlocking(LongPressKeyLayoutSetting).toLongPressKeyLayoutItems()
        val oldIdx = oldItems.indexOf(item)

        val insertIdx = (oldIdx + direction).coerceAtLeast(0).coerceAtMost(oldItems.size - 1)

        val newItems = oldItems.filter { it != item }.toMutableList().apply {
            add(insertIdx, item)
        }.toEncodedString()

        setting.setValue(newItems)
    }

    val disable: (item : LongPressKey) -> Unit = { item ->
        val oldItems = context.getSettingBlocking(LongPressKeyLayoutSetting).toLongPressKeyLayoutItems()

        val newItems = oldItems.filter { it != item }.toEncodedString()

        setting.setValue(newItems)

    }

    val enable: (item : LongPressKey) -> Unit = { item ->
        val oldItems = context.getSettingBlocking(LongPressKeyLayoutSetting).toLongPressKeyLayoutItems()

        val newItems = oldItems.filter { it != item }.toMutableList().apply {
            add(item)
        }.toEncodedString()

        setting.setValue(newItems)
    }

    if(items.isNotEmpty()) {
        Text(
            stringResource(R.string.morekey_settings_active),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Column(Modifier.semantics {
            collectionInfo = CollectionInfo(
                rowCount = items.size,
                columnCount = 1
            )
            contentDescription = context.getString(R.string.morekey_settings_active)
        }) {
            items.forEachIndexed { i, v ->
                key(v.ordinal) {
                    DraggableSettingItem(
                        idx = i,
                        item = v,
                        moveItem = moveItem,
                        disable = disable,
                        dragIcon = dragIcon,
                        limits = items.indices
                    )
                }
            }
        }
    }

    val inactiveEntries = LongPressKey.entries.filter { !items.contains(it) }
    if(inactiveEntries.isNotEmpty()) {
        Text(
            stringResource(R.string.morekey_settings_inactive),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Column(Modifier.semantics {
            collectionInfo = CollectionInfo(
                rowCount = inactiveEntries.size,
                columnCount = 1
            )
            contentDescription = context.getString(R.string.morekey_settings_inactive)
        }) {
            inactiveEntries.forEach {
                SettingItem(
                    title = it.name(context),
                    subtitle = it.description(context),
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = it.name(context)

                        onClick(label = context.getString(R.string.morekey_settings_reactivate)) {
                            enable(it)
                            true
                        }
                    }
                ) {
                    IconButton(onClick = { enable(it) }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.morekey_settings_reactivate)
                        )
                    }
                }
            }
        }
    }
}

val LongPressMenu = UserSettingsMenu(
    title = R.string.morekey_settings_keys,
    navPath = "longPress", registerNavPath = true,
    settings = listOf(
        userSettingToggleDataStore(
            title = R.string.morekey_settings_show_hints,
            subtitle = R.string.morekey_settings_show_hints_subtitle,
            setting = KeyHintsSetting
        ).copy(searchTags = R.string.morekey_settings_show_hints_tags),

        // TODO: Might not work well for showing up in search
        UserSetting(name = R.string.morekey_settings_layout) {
            val context = LocalContext.current
            val setting = useDataStore(LongPressKeyLayoutSetting)
            LongPressKeyLayoutEditor(
                context = context,
                setting = setting,
            )
        },

        UserSetting(
            name = R.string.morekey_settings_duration,
            subtitle = R.string.morekey_settings_duration_subtitle,
        ) {
            val context = LocalContext.current
            SettingSliderSharedPrefsInt(
                title = stringResource(R.string.morekey_settings_duration),
                subtitle = stringResource(R.string.morekey_settings_duration_subtitle),
                key = Settings.PREF_KEY_LONGPRESS_TIMEOUT,
                default = 300,
                range = 100.0f..700.0f,
                hardRange = 25.0f..1200.0f,
                transform = { it.roundToInt() },
                indicator = { context.getString(R.string.abbreviation_unit_milliseconds, "$it") },
                steps = 23
            )
        },
        UserSetting(
            name = R.string.morekey_settings_backspace_behavior
        ) {
            SettingRadio(
                title = stringResource(R.string.morekey_settings_backspace_behavior),
                options = listOf(
                    Settings.BACKSPACE_MODE_CHARACTERS,
                    Settings.BACKSPACE_MODE_WORDS
                ),
                optionNames = listOf(
                    stringResource(R.string.morekey_settings_backspace_behavior_delete_chars),
                    stringResource(R.string.morekey_settings_backspace_behavior_delete_words)
                ),
                setting = useSharedPrefsInt(
                    key = Settings.PREF_BACKSPACE_MODE,
                    default = Settings.BACKSPACE_MODE_CHARACTERS
                )
            )
        },
        UserSetting(
            name = R.string.morekey_settings_space_behavior,
            searchTagList = listOf(
                R.string.morekey_settings_space_behavior_swipe_cursor,
                R.string.morekey_settings_space_behavior_swipe_lang,
                R.string.morekey_settings_space_behavior_only_cursor
            )
        ) {
            SettingRadio(
                title = stringResource(R.string.morekey_settings_space_behavior),
                options = listOf(
                    Settings.SPACEBAR_MODE_SWIPE_CURSOR,
                    Settings.SPACEBAR_MODE_SWIPE_LANGUAGE,
                    Settings.SPACEBAR_MODE_SWIPE_CURSOR_ONLY
                ),
                optionNames = listOf(
                    stringResource(R.string.morekey_settings_space_behavior_swipe_cursor),
                    stringResource(R.string.morekey_settings_space_behavior_swipe_lang),
                    stringResource(R.string.morekey_settings_space_behavior_only_cursor)
                ),
                setting = useSharedPrefsInt(
                    key = Settings.PREF_SPACEBAR_MODE,
                    default = Settings.SPACEBAR_MODE_SWIPE_CURSOR
                )
            )
        }
    )
)

@Composable
private fun AutoSpacesSetting() {
    val altSpacesMode = useSharedPrefsInt(Settings.PREF_ALT_SPACES_MODE, Settings.DEFAULT_ALT_SPACES_MODE)
    val autoSpaceModes = mapOf(
        Settings.SPACES_MODE_ALL to stringResource(R.string.typing_settings_auto_space_mode_auto2),
        Settings.SPACES_MODE_SUGGESTIONS to stringResource(R.string.typing_settings_auto_space_mode_suggestions2),
        Settings.SPACES_MODE_LEGACY to stringResource(R.string.typing_settings_auto_space_mode_legacy2),
        Settings.SPACES_MODE_NONE to stringResource(R.string.typing_settings_auto_space_mode_none2),
    )
    DropDownPickerSettingItem(
        label = stringResource(R.string.typing_settings_auto_space_mode),
        options = autoSpaceModes.keys.toList(),
        selection = altSpacesMode.value,
        onSet = {
            altSpacesMode.setValue(it)
        },
        getDisplayName = {
            autoSpaceModes[it] ?: "?"
        },
        icon = {
            Icon(painterResource(R.drawable.space), contentDescription = null)
        }
    )
}

val NumberRowSettingMenu = UserSettingsMenu(
    title = R.string.keyboard_settings_number_row_title,
    navPath = "numberRow", registerNavPath = true,
    settings = listOf(
        userSettingDecorationOnly {
            PrimarySettingToggleDataStoreItem(
                stringResource(R.string.keyboard_settings_show_number_row),
                useSharedPrefsBool(Settings.PREF_ENABLE_NUMBER_ROW, false)
            )
        },

        userSettingToggleSharedPrefs(
            R.string.keyboard_settings_number_row_dont_use_script_digits,
            default = {false},
            key = Settings.PREF_USE_WESTERN_NUMERALS,
        ).copy(visibilityCheck = {
            useSharedPrefsBool(Settings.PREF_ENABLE_NUMBER_ROW, false).value
        }),

        UserSetting(name = R.string.keyboard_settings_number_row_style, visibilityCheck = {
            useSharedPrefsBool(Settings.PREF_ENABLE_NUMBER_ROW, false).value
        }) {
            val context = LocalContext.current
            val scheme = LocalKeyboardScheme.current
            val provider = remember(scheme) {
                BasicThemeProvider(context, scheme)
            }
            val keySize = with(LocalDensity.current) {
                32.dp.toPx() to 48.dp.toPx()
            }
            val background = remember(provider) {
                provider.keyBackground.toBitmap(
                    width = keySize.first.toInt(),
                    height = keySize.second.toInt()
                ).asImageBitmap()
            }

            val measurer = rememberTextMeasurer()
            val textSizePx = background.height / 2f
            val textSizeSp = with(LocalDensity.current) { textSizePx.toSp() }
            val color = LocalKeyboardScheme.current.onKeyboardContainer

            val textLayoutResult = measurer.measure(
                text = "1",
                style = TextStyle(
                    fontSize = textSizeSp,
                    color = color,
                    textAlign = TextAlign.Center
                )
            )

            SettingRadio(
                title = stringResource(R.string.keyboard_settings_number_row_style),
                options = listOf(
                    Settings.NUMBER_ROW_MODE_DEFAULT,
                    Settings.NUMBER_ROW_MODE_CLASSIC
                ),
                optionNames = listOf(
                    stringResource(R.string.keyboard_settings_number_row_style_default),
                    stringResource(R.string.keyboard_settings_number_row_style_classic),
                ),
                setting = useSharedPrefsInt(
                    key = Settings.PREF_NUMBER_ROW_MODE,
                    default = Settings.NUMBER_ROW_MODE_DEFAULT
                ),
                hints = listOf(
                    {
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier.size(32.dp, 48.dp)
                        ) {
                            drawText(
                                textLayoutResult = textLayoutResult,
                                topLeft = Offset(
                                    x = background.width / 2.0f - textLayoutResult.size.width / 2.0f,
                                    y = background.height / 2.0f - textLayoutResult.size.height / 2.0f,
                                )
                            )
                        }
                    },
                    {
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier.size(32.dp, 48.dp)
                        ) {
                            drawImage(background)
                            drawText(
                                textLayoutResult = textLayoutResult,
                                topLeft = Offset(
                                    x = background.width / 2.0f - textLayoutResult.size.width / 2.0f,
                                    y = background.height / 2.0f - textLayoutResult.size.height / 2.0f,
                                )
                            )
                        }
                    },
                )
            )
        }
    )
)

val KeyboardSettingsMenu = UserSettingsMenu(
    title = R.string.keyboard_settings_title,
    navPath = "keyboard", registerNavPath = true,
    settings = listOf(
        userSettingNavigationItem(
            title = R.string.size_settings_title,
            subtitle = R.string.size_settings_subtitle2,
            style = NavigationItemStyle.Misc,
            navigateTo = "resize",
            icon = R.drawable.maximize
        ),
        userSettingToggleSharedPrefs(
            title = R.string.keyboard_settings_show_number_row,
            subtitle = R.string.keyboard_settings_show_number_row_subtitle,
            key = Settings.PREF_ENABLE_NUMBER_ROW,
            default = {false},
            icon = { Text("123", style = Typography.Body.MediumMl, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                modifier = Modifier.clearAndSetSemantics{}) },
            submenu = NumberRowSettingMenu.navPath
        ),
        userSettingToggleSharedPrefs(
            title = R.string.keyboard_settings_show_arrow_row,
            subtitle = R.string.keyboard_settings_show_arrow_row_subtitle,
            key = Settings.PREF_ENABLE_ARROW_ROW,
            default = {false},
            icon = {
                Icon(painterResource(id = R.drawable.direction_arrows), contentDescription = null)
            }
        ),
        userSettingNavigationItem(
            title = R.string.morekey_settings_title,
            subtitle = R.string.morekey_settings_subtitle,
            style = NavigationItemStyle.Misc,
            navigateTo = "longPress",
            icon = R.drawable.arrow_up
        ),
        userSettingToggleDataStore(
            title = R.string.keyboard_settings_show_suggestion_row,
            subtitle = R.string.keyboard_settings_show_suggestion_row_subtitle,
            setting = ActionBarDisplayedSetting,
            icon = {
                Icon(painterResource(id = R.drawable.more_horizontal), contentDescription = null)
            }
        ),
        userSettingToggleDataStore(
            title = R.string.keyboard_settings_inline_autofill,
            subtitle = R.string.keyboard_settings_inline_autofill_subtitle,
            setting = InlineAutofillSetting
        ),
        userSettingToggleSharedPrefs(
            title = R.string.keyboard_settings_period_key,
            subtitle = R.string.keyboard_settings_period_key_subtitle2,
            key = Settings.PREF_ENABLE_ALT_PERIOD_KEY,
            default = {false},
        ),
    )
)

val TypingSettingsMenu = UserSettingsMenu(
    title = R.string.typing_settings_title,
    navPath = "typing", registerNavPath = true,
    settings = listOf(
        UserSetting(
            name = R.string.typing_settings_auto_space_mode,
            component = {
                AutoSpacesSetting()
            }
        ),
        userSettingToggleSharedPrefs(
            title = R.string.typing_settings_swipe,
            subtitle = R.string.typing_settings_swipe_subtitle,
            key = Settings.PREF_GESTURE_INPUT,
            default = {true},
            icon = {
                Icon(painterResource(id = R.drawable.swipe_icon), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
            }
        ),
        userSettingToggleDataStore(
            title = R.string.typing_settings_suggest_emojis,
            subtitle = R.string.typing_settings_suggest_emojis_subtitle,
            setting = SHOW_EMOJI_SUGGESTIONS,
            icon = {
                Icon(painterResource(id = R.drawable.smile), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
            }
        ),
        userSettingToggleSharedPrefs(
            title = R.string.auto_cap,
            subtitle = R.string.auto_cap_summary,
            key = Settings.PREF_AUTO_CAP,
            default = {true},
            icon = {
                Text("Aa", style = Typography.Body.MediumMl, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
            }
        ),
        userSettingToggleSharedPrefs(
            title = R.string.use_double_space_period,
            subtitle = R.string.use_double_space_period_summary,
            key = Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD,
            default = {true},
            icon = {
                Text(".", style = Typography.Body.MediumMl, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
            }
        ),
        userSettingToggleSharedPrefs(
            title = R.string.typing_settings_delete_pasted_text_on_backspace,
            key = Settings.PREF_BACKSPACE_DELETE_INSERTED_TEXT,
            default = {true}
        ),
        userSettingToggleSharedPrefs(
            title = R.string.typing_settings_revert_correction_on_backspace,
            key = Settings.PREF_BACKSPACE_UNDO_AUTOCORRECT,
            default = {true}
        ),
        userSettingToggleSharedPrefs(
            title = R.string.popup_on_keypress,
            key = Settings.PREF_POPUP_ON,
            default = {booleanResource(R.bool.config_default_key_preview_popup)}
        ),
        userSettingToggleSharedPrefs(
            title = R.string.vibrate_on_keypress,
            key = Settings.PREF_VIBRATE_ON,
            default = {booleanResource(R.bool.config_default_vibration_enabled)}
        ),
        UserSetting(
            name = R.string.typing_settings_vibration_strength,
            visibilityCheck = {
                LocalSharedPrefsCache.current!!.currSharedPrefs.getBoolean(
                    Settings.PREF_VIBRATE_ON,
                    booleanResource(R.bool.config_default_vibration_enabled)
                )
            },
            component = {
                val context = LocalContext.current
                SyncDataStoreToPreferencesInt(vibrationDurationSetting, PREF_VIBRATION_DURATION_SETTINGS)

                SettingSlider(
                    title = stringResource(R.string.typing_settings_vibration_strength),
                    setting = vibrationDurationSetting,
                    range = -1.0f .. 100.0f,
                    hardRange = -1.0f .. 2000.0f,
                    transform = { it.roundToInt() },
                    indicator = {
                        if(it == -1) {
                            context.getString(R.string.typing_settings_vibration_strength_default)
                        } else {
                            context.getString(R.string.abbreviation_unit_milliseconds, "$it")
                        }
                    }
                )
            }
        ),
        userSettingToggleSharedPrefs(
            title = R.string.sound_on_keypress,
            key = Settings.PREF_SOUND_ON,
            default = {booleanResource(R.bool.config_default_sound_enabled)}
        ),
        UserSetting(
            name = R.string.typing_settings_keypress_sound_volume,
            visibilityCheck = {
                LocalSharedPrefsCache.current!!.currSharedPrefs.getBoolean(
                    Settings.PREF_SOUND_ON,
                    booleanResource(R.bool.config_default_sound_enabled)
                )
            },
            component = {
                val context = LocalContext.current
                SyncDataStoreToPreferencesFloat(keySoundVolumeSetting, PREF_KEYPRESS_SOUND_VOLUME)

                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val value = remember { mutableFloatStateOf(0.0f) }
                val ringerMode = remember { mutableStateOf(audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) }
                val firstPlayback = remember { mutableStateOf(false) }

                LaunchedEffect(value.floatValue) {
                    delay(100L) // debounce
                    if(firstPlayback.value == false) {
                        firstPlayback.value = true
                        return@LaunchedEffect
                    }
                    val volume = value.floatValue.let {
                        if(it == -1.0f) {
                            Settings.readDefaultKeypressSoundVolume(context.resources)
                        } else {
                            it
                        }
                    }

                    val shouldPlay = audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL
                    ringerMode.value = shouldPlay

                    if(shouldPlay) {
                        audioManager.playSoundEffect(
                            AudioManager.FX_KEYPRESS_STANDARD,
                            volume
                        )
                    }
                }

                if(!ringerMode.value) {
                    Tip(stringResource(R.string.typing_settings_keypress_sound_volume_ringer_mode_warning))
                }

                Tip(stringResource(R.string.typing_settings_keypress_sound_volume_vendor_warning))

                SettingSlider(
                    title = stringResource(R.string.typing_settings_keypress_sound_volume),
                    setting = keySoundVolumeSetting,
                    range = 0.0f .. 1.0f,
                    hardRange = 0.0f .. 1.0f,
                    transform = {
                        value.floatValue = it
                        if(it == 0.0f) {
                            -1.0f
                        } else {
                            it
                        }
                    },
                    indicator = {
                        if(it <= 0.0f) {
                            context.getString(R.string.typing_settings_keypress_sound_volume_default)
                        } else {
                            "${(it * 100.0f).roundToInt()}%"
                        }
                    }
                )
            }
        ),
    )
)

@Preview(showBackground = true)
@Composable
fun KeyboardAndTypingScreen(navController: NavHostController = rememberNavController()) {
    ScrollableList {
        if(IMESettingsMenu.visibilityCheck!!()) {
            ScreenTitle("", showBack = true, navController)
            IMESettingsMenu.render(showBack = false)
            ScreenTitle(
                stringResource(
                    KeyboardSettingsMenu.title
                ),
                showBack = false,
                navController
            )
        } else {
            ScreenTitle(
                stringResource(
                    KeyboardSettingsMenu.title
                ),
                showBack = true,
                navController
            )
        }

        KeyboardSettingsMenu.render(showBack = false, showTitle = false)
        TypingSettingsMenu.render(showBack = false)

        BottomSpacer()
    }
}