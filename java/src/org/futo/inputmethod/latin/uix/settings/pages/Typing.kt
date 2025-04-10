package org.futo.inputmethod.latin.uix.settings.pages

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextInputSession
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.settings.LongPressKey
import org.futo.inputmethod.latin.settings.LongPressKeyLayoutSetting
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.settings.Settings.PREF_VIBRATION_DURATION_SETTINGS
import org.futo.inputmethod.latin.settings.description
import org.futo.inputmethod.latin.settings.name
import org.futo.inputmethod.latin.settings.toEncodedString
import org.futo.inputmethod.latin.settings.toLongPressKeyLayoutItems
import org.futo.inputmethod.latin.uix.AndroidTextInput
import org.futo.inputmethod.latin.uix.KeyHintsSetting
import org.futo.inputmethod.latin.uix.PreferenceUtils
import org.futo.inputmethod.latin.uix.SHOW_EMOJI_SUGGESTIONS
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.actions.ActionsEditor
import org.futo.inputmethod.latin.uix.actions.ClipboardHistoryEnabled
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.setSettingBlocking
import org.futo.inputmethod.latin.uix.settings.DataStoreItem
import org.futo.inputmethod.latin.uix.settings.DropDownPickerSettingItem
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingItem
import org.futo.inputmethod.latin.uix.settings.SettingListLazy
import org.futo.inputmethod.latin.uix.settings.SettingRadio
import org.futo.inputmethod.latin.uix.settings.SettingSlider
import org.futo.inputmethod.latin.uix.settings.SettingSliderSharedPrefsInt
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.SettingToggleSharedPrefs
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.useSharedPrefsInt
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.v2keyboard.KeyboardSettings
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sign

val vibrationDurationSetting = SettingsKey(
    intPreferencesKey("vibration_duration"),
    -1
)

val ActionBarDisplayedSetting = SettingsKey(
    booleanPreferencesKey("enable_action_bar"),
    true
)

val InlineAutofillSetting = SettingsKey(
    booleanPreferencesKey("inline_autofill"),
    true
)

fun NavGraphBuilder.addTypingNavigation(
    navController: NavHostController
) {
    composable("typing") { TypingScreen(navController) }
    composable("resize") { ResizeScreen(navController) }
    composable("longPress") { LongPressScreen(navController) }
    composable("actionEdit") { ActionEditorScreen(navController) }
}

@Preview(showBackground = true)
@Composable
fun ActionEditorScreen(navController: NavHostController = rememberNavController()) {
    Column {
        ScreenTitle(stringResource(R.string.action_editor_title), showBack = true, navController)
        ActionsEditor { }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(showBackground = true)
@Composable
fun ResizeScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
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
            NavigationItem(
                stringResource(R.string.size_settings_reset),
                subtitle = stringResource(R.string.size_settings_reset_subtitle),
                style = NavigationItemStyle.Misc,
                icon = painterResource(R.drawable.close),
                navigate = {
                    KeyboardSettings.values.forEach {
                        context.setSettingBlocking(it.key, it.default)
                    }
                }
            )

            AndroidTextInput(allowPredictions = false, customOptions = setOf("org.futo.inputmethod.latin.ResizeMode"), autoshow = false)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LazyItemScope.DraggableSettingItem(idx: Int, item: LongPressKey, moveItem: (LongPressKey, Int) -> Unit, disable: (LongPressKey) -> Unit, dragIcon: @Composable () -> Unit) {
    val context = LocalContext.current
    val dragging = remember { mutableStateOf(false) }
    val offset = remember { mutableFloatStateOf(0.0f) }
    val height = remember { mutableIntStateOf(1) }

    SettingItem(
        title = "${idx+1}. " + item.name(context),
        subtitle = item.description(context),
        icon = dragIcon,
        modifier = Modifier
            .onSizeChanged { size -> height.intValue = size.height }
            .pointerInput(Unit) {
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
                        if (offset.floatValue.absoluteValue > height.intValue) {
                            val direction = offset.floatValue.sign.toInt()
                            moveItem(
                                item,
                                direction
                            )
                            offset.floatValue -= height.intValue * direction
                        }

                    }
                )
            }
            .let { modifier ->
                if (!dragging.value) {
                    modifier.animateItemPlacement()
                } else {
                    modifier
                        .zIndex(10.0f)
                        .graphicsLayer {
                            clip = false
                            translationX = 0.0f
                            translationY = offset.floatValue
                        }
                }
            }
    ) {
        IconButton(onClick = { disable(item) }) {
            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.morekey_settings_disable))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.longPressKeyLayoutEditor(context: Context, setting: DataStoreItem<String>) {
    item {
        ScreenTitle(title = stringResource(R.string.morekey_settings_layout))
    }

    item {
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

    item {
        Text(stringResource(R.string.morekey_settings_active), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
    itemsIndexed(items, key = { i, v -> v.ordinal }) { i, v ->
        DraggableSettingItem(idx = i, item = v, moveItem = moveItem, disable = disable, dragIcon = dragIcon)
    }

    item {
        Text(stringResource(R.string.morekey_settings_inactive), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
    items(LongPressKey.entries.filter { !items.contains(it) }, key = { it.ordinal }) {
        SettingItem(
            title = it.name(context),
            subtitle = it.description(context),
            modifier = Modifier.animateItemPlacement()
        ) {
            IconButton(onClick = { enable(it) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.morekey_settings_enable))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LongPressScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val setting = useDataStore(LongPressKeyLayoutSetting, blocking = true)
    SettingListLazy {
        item {
            ScreenTitle(stringResource(R.string.morekey_settings_keys), showBack = true, navController)
        }

        item {
            SettingToggleDataStore(
                title = stringResource(R.string.morekey_settings_show_hints),
                subtitle = stringResource(R.string.morekey_settings_show_hints_subtitle),
                setting = KeyHintsSetting
            )
        }

        longPressKeyLayoutEditor(
            context = context,
            setting = setting,
        )

        item {
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
        }

        item {
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
        }

        item {
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
    }
}

@Composable
private fun AutoSpacesSetting() {
    val altSpacesMode = useSharedPrefsInt(Settings.PREF_ALT_SPACES_MODE, Settings.DEFAULT_ALT_SPACES_MODE)
    val autoSpaceModes = mapOf(
        Settings.SPACES_MODE_ALL to stringResource(R.string.typing_settings_auto_space_mode_auto),
        Settings.SPACES_MODE_SUGGESTIONS to stringResource(R.string.typing_settings_auto_space_mode_suggestions),
        Settings.SPACES_MODE_LEGACY to stringResource(R.string.typing_settings_auto_space_mode_legacy)
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

@Preview(showBackground = true)
@Composable
fun TypingScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val (vibration, _) = useDataStore(key = vibrationDurationSetting.key, default = vibrationDurationSetting.default)

    LaunchedEffect(vibration) {
        val sharedPrefs = PreferenceUtils.getDefaultSharedPreferences(context)
        withContext(Dispatchers.Main) {
            sharedPrefs.edit {
                putInt(PREF_VIBRATION_DURATION_SETTINGS, vibration)
            }
        }
    }

    ScrollableList {
        ScreenTitle(stringResource(R.string.keyboard_settings_title), showBack = true, navController)

        NavigationItem(
            title = stringResource(R.string.size_settings_title),
            subtitle = stringResource(R.string.size_settings_subtitle),
            style = NavigationItemStyle.Misc,
            navigate = { navController.navigate("resize") },
            icon = painterResource(id = R.drawable.maximize)
        )

        SettingToggleSharedPrefs(
            title = stringResource(R.string.keyboard_settings_show_number_row),
            subtitle = stringResource(R.string.keyboard_settings_show_number_row_subtitle),
            key = Settings.PREF_ENABLE_NUMBER_ROW,
            default = false,
            icon = { Text("123", style = Typography.Body.MediumMl, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)) }
        )

        SettingToggleSharedPrefs(
            title = stringResource(R.string.keyboard_settings_show_arrow_row),
            subtitle = stringResource(R.string.keyboard_settings_show_arrow_row_subtitle),
            key = Settings.PREF_ENABLE_ARROW_ROW,
            default = false,
            icon = {
                Icon(painterResource(id = R.drawable.direction_arrows), contentDescription = null)
            }
        )

        NavigationItem(
            title = stringResource(R.string.morekey_settings_title),
            subtitle = stringResource(R.string.morekey_settings_subtitle),
            style = NavigationItemStyle.Misc,
            navigate = { navController.navigate("longPress") },
            icon = painterResource(id = R.drawable.arrow_up)
        )

        NavigationItem(
            title = stringResource(R.string.keyboard_settings_extra_layouts),
            subtitle = stringResource(R.string.keyboard_settings_extra_layouts_subtitle),
            style = NavigationItemStyle.Misc,
            navigate = { navController.navigate("languages") },
            icon = painterResource(id = R.drawable.keyboard)
        )

        NavigationItem(
            title = stringResource(R.string.action_editor_title),
            subtitle = stringResource(R.string.action_editor_subtitle),
            style = NavigationItemStyle.Misc,
            navigate = { navController.navigate("actionEdit") },
            icon = painterResource(id = R.drawable.smile)
        )

        SettingToggleDataStore(
            title = stringResource(R.string.keyboard_settings_show_suggestion_row),
            subtitle = stringResource(R.string.keyboard_settings_show_suggestion_row_subtitle),
            setting = ActionBarDisplayedSetting,
            icon = {
                Icon(painterResource(id = R.drawable.more_horizontal), contentDescription = null)
            }
        )

        if(useDataStore(ActionBarDisplayedSetting).value) {
            SettingToggleDataStore(
                title = stringResource(R.string.keyboard_settings_inline_autofill),
                subtitle = stringResource(R.string.keyboard_settings_inline_autofill_subtitle),
                setting = InlineAutofillSetting
            )
        }

        ScreenTitle(title = stringResource(R.string.typing_settings_title))

        AutoSpacesSetting()

        SettingToggleSharedPrefs(
            title = stringResource(R.string.typing_settings_swipe),
            subtitle = stringResource(R.string.typing_settings_swipe_subtitle),
            key = Settings.PREF_GESTURE_INPUT,
            default = true,
            icon = {
                Icon(painterResource(id = R.drawable.swipe_icon), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
            }
        )

        SettingToggleDataStore(
            title = stringResource(R.string.typing_settings_suggest_emojis),
            subtitle = stringResource(R.string.typing_settings_suggest_emojis_subtitle),
            setting = SHOW_EMOJI_SUGGESTIONS,
            icon = {
                Icon(painterResource(id = R.drawable.smile), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
            }
        )

        SettingToggleSharedPrefs(
            title = stringResource(R.string.auto_correction),
            subtitle = stringResource(R.string.auto_correction_summary),
            key = Settings.PREF_AUTO_CORRECTION,
            default = true,
            icon = {
                Icon(painterResource(id = R.drawable.icon_spellcheck), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
            }
        )

        SettingToggleSharedPrefs(
            title = stringResource(R.string.auto_cap),
            subtitle = stringResource(R.string.auto_cap_summary),
            key = Settings.PREF_AUTO_CAP,
            default = true,
            icon = {
                Text("Aa", style = Typography.Body.MediumMl, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
            }
        )

        SettingToggleSharedPrefs(
            title = stringResource(R.string.use_double_space_period),
            subtitle = stringResource(R.string.use_double_space_period_summary),
            key = Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD,
            default = true,
            icon = {
                Text(".", style = Typography.Body.MediumMl, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
            }
        )

        SettingToggleSharedPrefs(
            title = stringResource(R.string.sound_on_keypress),
            key = Settings.PREF_SOUND_ON,
            default = booleanResource(R.bool.config_default_sound_enabled)
        )
        SettingToggleSharedPrefs(
            title = stringResource(R.string.popup_on_keypress),
            key = Settings.PREF_POPUP_ON,
            default = booleanResource(R.bool.config_default_key_preview_popup)
        )

        SettingToggleSharedPrefs(
            title = stringResource(R.string.vibrate_on_keypress),
            key = Settings.PREF_VIBRATE_ON,
            default = booleanResource(R.bool.config_default_vibration_enabled)
        )

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

        SettingToggleDataStore(
            title = stringResource(R.string.typing_settings_enable_clipboard_history),
            setting = ClipboardHistoryEnabled
        )
    }
}

