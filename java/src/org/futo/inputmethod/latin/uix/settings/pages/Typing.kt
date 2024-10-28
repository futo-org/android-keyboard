package org.futo.inputmethod.latin.uix.settings.pages

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
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
        ScreenTitle("Edit Actions", showBack = true, navController)
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
            ScreenTitle("Resize Keyboard", showBack = true, navController)

            PaymentSurface(
                isPrimary = false,
            ) {
                PaymentSurfaceHeading(title = "Tip")

                Text(
                    buildAnnotatedString {
                        append("You can access this anywhere with the new Keyboard Modes action: ")
                        appendInlineContent("icon")
                        appendLine()
                        appendLine()
                        append("Tap the \"Resize Keyboard\" button to resize.")
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

            NavigationItem(
                "Reset size settings",
                subtitle = "Tap to reset all sizes and modes for both portrait and landscape to default",
                style = NavigationItemStyle.Misc,
                icon = painterResource(R.drawable.close),
                navigate = {
                    KeyboardSettings.values.forEach {
                        context.setSettingBlocking(it.key, it.default)
                    }
                }
            )

            AndroidTextInput(allowPredictions = false, customOptions = setOf("org.futo.inputmethod.latin.ResizeMode"))
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
            Icon(Icons.Default.Clear, contentDescription = "Remove")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.longPressKeyLayoutEditor(context: Context, setting: DataStoreItem<String>) {
    item {
        ScreenTitle(title = "Layout of long-press keys")
    }

    item {
        Button(onClick = {
            setting.setValue(LongPressKeyLayoutSetting.default)
        }) {
            Text("Reset to default")
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
        Text("Active", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
    itemsIndexed(items, key = { i, v -> v.ordinal }) { i, v ->
        DraggableSettingItem(idx = i, item = v, moveItem = moveItem, disable = disable, dragIcon = dragIcon)
    }

    item {
        Text("Inactive", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
    items(LongPressKey.entries.filter { !items.contains(it) }, key = { it.ordinal }) {
        SettingItem(
            title = it.name(context),
            subtitle = it.description(context),
            modifier = Modifier.animateItemPlacement()
        ) {
            IconButton(onClick = { enable(it) }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
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
            ScreenTitle("Long-Press Keys", showBack = true, navController)
        }

        item {
            SettingToggleDataStore(
                title = "Show hints",
                subtitle = "Display a small hint on each key, showing the primary long-press key",
                setting = KeyHintsSetting
            )
        }

        longPressKeyLayoutEditor(
            context = context,
            setting = setting,
        )

        item {
            SettingSliderSharedPrefsInt(
                title = "Long Press Duration",
                subtitle = "How long a key needs to be pressed to be considered a long-press",
                key = Settings.PREF_KEY_LONGPRESS_TIMEOUT,
                default = 300,
                range = 100.0f..700.0f,
                hardRange = 25.0f..1200.0f,
                transform = { it.roundToInt() },
                indicator = { "$it ms" },
                steps = 23
            )
        }

        item {
            SettingRadio(
                title = "Backspace Behavior when holding/swiping",
                options = listOf(
                    Settings.BACKSPACE_MODE_CHARACTERS,
                    Settings.BACKSPACE_MODE_WORDS
                ),
                optionNames = listOf(
                    "Delete characters",
                    "Delete entire words"
                ),
                setting = useSharedPrefsInt(
                    key = Settings.PREF_BACKSPACE_MODE,
                    default = Settings.BACKSPACE_MODE_CHARACTERS
                )
            )
        }

        item {
            SettingRadio(
                title = "Spacebar Behavior",
                options = listOf(
                    Settings.SPACEBAR_MODE_SWIPE_CURSOR,
                    Settings.SPACEBAR_MODE_SWIPE_LANGUAGE,
                    Settings.SPACEBAR_MODE_SWIPE_CURSOR_ONLY
                ),
                optionNames = listOf(
                    "Swiping moves cursor, long-pressing switches language",
                    "Swiping changes language, long-pressing moves cursor",
                    "Swiping and long-pressing only moves cursor"
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
        Settings.SPACES_MODE_ALL to "Automatically insert spaces after punctuation or after inserting suggestions",
        Settings.SPACES_MODE_SUGGESTIONS to "Automatically insert spaces only after inserting suggestions",
        Settings.SPACES_MODE_LEGACY to "Do not automatically insert any spaces (legacy mode)"
    )
    DropDownPickerSettingItem(
        label = "Automatic spaces mode (new)",
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
        ScreenTitle("Keyboard", showBack = true, navController)

        NavigationItem(
            title = "Resize Keyboard",
            subtitle = "Change the height and offset of the keyboard",
            style = NavigationItemStyle.Misc,
            navigate = { navController.navigate("resize") },
            icon = painterResource(id = R.drawable.maximize)
        )

        SettingToggleSharedPrefs(
            title = "Show Number Row",
            subtitle = "When active, the number row is shown on top of the keyboard on supported layouts",
            key = Settings.PREF_ENABLE_NUMBER_ROW,
            default = false,
            icon = { Text("123", style = Typography.Body.MediumMl, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)) }
        )

        SettingToggleSharedPrefs(
            title = "Show Arrow Keys",
            subtitle = "When active, the arrow keys row is shown on the bottom of the keyboard",
            key = Settings.PREF_ENABLE_ARROW_ROW,
            default = false,
            icon = {
                Icon(painterResource(id = R.drawable.direction_arrows), contentDescription = null)
            }
        )

        NavigationItem(
            title = "Long-Press Keys & Spacebar",
            subtitle = "Configure long-press duration, how to order letters/symbols, and behavior of spacebar and delete key.",
            style = NavigationItemStyle.Misc,
            navigate = { navController.navigate("longPress") },
            icon = painterResource(id = R.drawable.arrow_up)
        )

        NavigationItem(
            title = "Additional Layouts",
            subtitle = "Configure additional layouts in the languages screen",
            style = NavigationItemStyle.Misc,
            navigate = { navController.navigate("languages") },
            icon = painterResource(id = R.drawable.keyboard)
        )

        NavigationItem(
            title = "Edit Actions",
            subtitle = "Edit favorite actions, pinned actions, and the action key next to the spacebar",
            style = NavigationItemStyle.Misc,
            navigate = { navController.navigate("actionEdit") },
            icon = painterResource(id = R.drawable.smile)
        )

        SettingToggleDataStore(
            title = "Show action/suggestions bar",
            subtitle = "Show the bar containing suggestions. Recommended to keep enabled",
            setting = ActionBarDisplayedSetting,
            icon = {
                Icon(painterResource(id = R.drawable.more_horizontal), contentDescription = null)
            }
        )

        ScreenTitle(title = "Typing preferences")

        AutoSpacesSetting()

        SettingToggleSharedPrefs(
            title = "Swipe Typing (alpha)",
            subtitle = "Allow swiping from key to key to write words.",
            key = Settings.PREF_GESTURE_INPUT,
            default = true,
            icon = {
                Icon(painterResource(id = R.drawable.swipe_icon), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
            }
        )

        SettingToggleDataStore(
            title = "Emoji Suggestions",
            subtitle = "Suggest emojis while you're typing",
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
            title = "Vibration",
            setting = vibrationDurationSetting,
            range = -1.0f .. 100.0f,
            hardRange = -1.0f .. 2000.0f,
            transform = { it.roundToInt() },
            indicator = {
                if(it == -1) {
                    "Default"
                } else {
                    "$it ms"
                }
            }
        )

        SettingToggleDataStore(
            title = "Clipboard History",
            setting = ClipboardHistoryEnabled
        )
    }
}

