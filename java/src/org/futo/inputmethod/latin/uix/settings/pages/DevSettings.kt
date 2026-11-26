package org.futo.inputmethod.latin.uix.settings.pages

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.engine.general.UseExpandableSuggestionsForGeneralIME
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.CrashLoggingApplication
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.SwipeDecoderDictionary
import org.futo.inputmethod.latin.SwipeLanguageModelSetting
import org.futo.inputmethod.latin.SwipeSpecialDecoderSetting
import org.futo.inputmethod.latin.TextInputAlternativeIC
import org.futo.inputmethod.latin.TextInputAlternativeICComposing
import org.futo.inputmethod.latin.TextInputBufferedIC
import org.futo.inputmethod.latin.VoiceInputAlternativeIC
import org.futo.inputmethod.latin.VoiceInputAlternativeICComposing
import org.futo.inputmethod.latin.uix.AndroidTextInput
import org.futo.inputmethod.latin.uix.DebugOnly
import org.futo.inputmethod.latin.uix.HiddenKeysSetting
import org.futo.inputmethod.latin.uix.IMPORT_SETTINGS_REQUEST
import org.futo.inputmethod.latin.uix.OldStyleActionsBar
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.UixManagerInstanceForDebug
import org.futo.inputmethod.latin.uix.actions.BugViewerAction
import org.futo.inputmethod.latin.uix.actions.BugViewerState
import org.futo.inputmethod.latin.uix.actions.clipboard.clipboardFile
import org.futo.inputmethod.latin.uix.findActivity
import org.futo.inputmethod.latin.uix.getPreferencesDataStoreFile
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.SettingToggleRaw
import org.futo.inputmethod.latin.uix.settings.SettingsMenus
import org.futo.inputmethod.latin.uix.settings.VoiceInputSwitchActivity
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import org.futo.inputmethod.latin.uix.theme.TonalPalette
import org.futo.inputmethod.latin.uix.theme.dynamicTonalPalette
import org.futo.inputmethod.latin.uix.theme.serialization.AlphaOrder
import org.futo.inputmethod.latin.uix.theme.serialization.argbLongToHexColorString
import org.futo.inputmethod.latin.xlm.AllowTransformerOnNonQWERTYLayouts
import org.futo.inputmethod.updates.DISABLE_UPDATE_REMINDER
import org.futo.inputmethod.updates.dismissedMigrateUpdateNotice
import kotlin.system.exitProcess


val IS_DEVELOPER = SettingsKey(booleanPreferencesKey("isDeveloperMode"), false)

@OptIn(DebugOnly::class)
@Composable
fun DevKeyboardScreen(navController: NavHostController = rememberNavController()) {
    Box {
        ScrollableList {
            ScreenTitle("Keyboard screen", showBack = true, navController)

            AndroidTextInput()
        }
        UixManagerInstanceForDebug?.Content()
    }
}


private val ColTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 8.sp,
    lineHeight = 8.sp
)

@Composable
private fun ShowColor(col: Color?, name: String) {
    val colHex = col?.let { argbLongToHexColorString(it.toArgb().toLong(), AlphaOrder.RGBA) } ?: "?"
    Box(
        Modifier.fillMaxWidth().height(18.dp)
            .background(col ?: Color.Red)
    ) {
        Text(
            "${name} ${colHex}",
            modifier = Modifier.align(Alignment.Center),
            style = ColTextStyle,
            color = if ((col ?: Color.Red).luminance() > 0.6f) Color.Black else Color.White
        )
    }
}
@Composable
private fun RowScope.TonesList(palette: TonalPalette, name: String) {
    Column(Modifier.weight(1.0f)) {
        listOf(100, 99, 98, 96, 95, 94, 92, 90, 87, 80, 70, 60, 50, 40, 30, 20, 10, 0).forEach {
            val col = palette.resolve("${name}${it}")
            ShowColor(col, "${name}${it}")
        }
    }
}

@Composable
fun DevPaletteScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val palette = remember { dynamicTonalPalette(context) }
    ScrollableList {
        ScreenTitle("Tonal palette", showBack = true, navController)
        Row {
            TonesList(palette, "primary")
            TonesList(palette, "secondary")
            TonesList(palette, "tertiary")
        }
        Row {
            TonesList(palette, "neutral")
            TonesList(palette, "neutralVariant")
            TonesList(palette, "error")
        }
        VerticalGrid(items=palette.otherDynamicColors.keys.toList(), columns=4) {
            ShowColor(palette.otherDynamicColors[it], it)
        }
        Button(onClick = {
            val json = buildString {
                append("{")
                listOf("primary", "secondary", "tertiary", "neutral", "neutralVariant", "error").forEach { name ->
                    listOf(100, 99, 98, 96, 95, 94, 92, 90, 87, 80, 70, 60, 50, 40, 30, 20, 10, 0).forEach { tone ->
                        val col = palette.resolve("$name$tone")
                        append("\n    \"system_$name$tone\": \"${argbLongToHexColorString(col!!.toArgb().toLong(), AlphaOrder.RGBA)}\",")
                    }
                }

                palette.otherDynamicColors.forEach { (name, col) ->
                    append("\n    \"system_$name\": \"${argbLongToHexColorString(col.toArgb().toLong(), AlphaOrder.RGBA)}\",")
                }

                deleteCharAt(length - 1) // trailing comma
                append("\n}")
            }

            context.copyToClipboard(json)
        }) {
            Text("Copy all to clipboard")
        }
    }
}



private fun triggerImportTheme(context: Context) {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/zip"
    }
    findActivity(context)!!.startActivityForResult(intent, IMPORT_SETTINGS_REQUEST)
}

var DevAutoAcceptThemeImport = false

@OptIn(DebugOnly::class)
@Composable
fun DevThemeImportScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        DevAutoAcceptThemeImport = true
        onDispose {
            DevAutoAcceptThemeImport = false
        }
    }
    Box {
        ScrollableList {
            ScreenTitle("Theme development", showBack = true, navController)
            Text("Push the theme zip file via adb and tap button below to re-import it")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    triggerImportTheme(context)
                    BugViewerState.clearBugs()
                }) { Text("Reimport") }
                Button(onClick = {
                    UixManagerInstanceForDebug?.onActionActivated(BugViewerAction)
                }) { Text("Bugviewer") }
            }

            AndroidTextInput()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DeveloperScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = LocalLifecycleOwner.current

    ScrollableList {
        ScreenTitle("Developer", showBack = true, navController)

        SettingToggleDataStore(title = "Developer mode", setting = IS_DEVELOPER)

        CrashLoggingApplication.CopyLogsOption()

        SettingToggleDataStore(title = "Disable all update reminders", setting = DISABLE_UPDATE_REMINDER)
        
        SettingToggleDataStore(
            title = "Touch typing mode",
            subtitle = "Hides all keys. Touch typists only! Recommended to disable emoji key and enable key borders",
            setting = HiddenKeysSetting
        )

        SettingToggleDataStore(title = "Dismissed migration notice", setting = dismissedMigrateUpdateNotice)

        SettingToggleDataStore(title = "Old action bar", setting = OldStyleActionsBar)

        NavigationItem(
            title = "Text edit variations",
            style = NavigationItemStyle.Misc,
            navigate = { navController.navigate("devtextedit") }
        )
        NavigationItem(
            title = "Layout list",
            style = NavigationItemStyle.Misc,
            navigate = { navController.navigate("devlayouts") }
        )
        NavigationItem(
            title = "Custom layouts",
            style = NavigationItemStyle.Misc,
            navigate = { navController.navigate("devlayouteditor") }
        )
        NavigationItem(
            title = "Theme dev utility",
            style = NavigationItemStyle.Misc,
            navigate = { navController.navigate("devtheme") }
        )
        NavigationItem(
            title = "Dynamic palette",
            style = NavigationItemStyle.Misc,
            navigate = { navController.navigate("dynamicpalette") }
        )

        SettingToggleDataStore(
            title = "Allow transformer models on non QWERTY layouts",
            setting = AllowTransformerOnNonQWERTYLayouts
        )


        ScreenTitle("Text input debug")
        SettingToggleDataStore(
            title = "Text input alt. composition",
            setting = TextInputAlternativeIC
        )
        SettingToggleDataStore(
            title = "Use buffering",
            setting = TextInputBufferedIC,
            disabled = useDataStoreValue(TextInputAlternativeIC) == false
        )
        SettingToggleDataStore(
            title = "Use setComposingRegion",
            setting = TextInputAlternativeICComposing,
            disabled = useDataStoreValue(TextInputAlternativeIC) == false
        )

        NavigationItem(
            title = "Buggy text edit variations",
            style = NavigationItemStyle.Misc,
            navigate = { navController.navigate("devbuggytextedit") }
        )

        ScreenTitle("Voice input debug")
        SettingToggleDataStore(
            title = "Voice input alt. composition",
            setting = VoiceInputAlternativeIC
        )

        SettingToggleDataStore(
            title = "Use setComposingRegion",
            setting = VoiceInputAlternativeICComposing,
            disabled = useDataStoreValue(VoiceInputAlternativeIC) == false
        )

        ScreenTitle("Swipe debug")
        SettingToggleDataStore(
            title = "Special decoder",
            subtitle = "default = yes",
            setting = SwipeSpecialDecoderSetting
        )
        SettingToggleDataStore(
            title = "Language model",
            subtitle = "default = yes",
            setting = SwipeLanguageModelSetting
        )
        NavigationItem(
            title = "Activate swipe debug logging for 5 minutes",
            subtitle = "Swipes and text will be logged to logcat",
            style = NavigationItemStyle.MiscNoArrow,
            navigate = {
                SwipeDecoderDictionary.debugLogUntil = System.currentTimeMillis() + 5L * 60L * 1000L
            }
        )

        ScreenTitle(title = "Payment stuff")

        SettingToggleDataStore(title = "Is paid", setting = IS_ALREADY_PAID)
        SettingToggleDataStore(title = "Is payment pending", setting = IS_PAYMENT_PENDING)
        SettingToggleDataStore(title = "Has seen paid notice", setting = HAS_SEEN_PAID_NOTICE)
        SettingToggleDataStore(title = "Force show notice", setting = FORCE_SHOW_NOTICE)

        val reminder = useDataStore(NOTICE_REMINDER_TIME)
        val currTime = System.currentTimeMillis() / 1000L

        val subtitleValue = if (reminder.value > currTime) {
            val diffDays = (reminder.value - currTime) / 60.0 / 60.0 / 24.0
            "Reminding in ${"%.2f".format(diffDays)} days"
        } else {
            "Reminder unset"
        }
        SettingToggleRaw(
            "Reminder Time",
            reminder.value > currTime,
            {
                if (!it) {
                    reminder.setValue(0L)
                }
            },
            subtitleValue,
            reminder.value <= currTime,
            { }
        )

        val licenseKey = useDataStore(EXT_LICENSE_KEY)
        SettingToggleRaw(
            "Ext License Key",
            licenseKey.value != EXT_LICENSE_KEY.default,
            {
                if(!it) {
                    licenseKey.setValue(EXT_LICENSE_KEY.default)
                }
            },
            licenseKey.value,
            licenseKey.value == EXT_LICENSE_KEY.default,
            { }
        )

        ScreenTitle(title = "Here be dragons")
        val context = LocalContext.current
        NavigationItem(
            title = "Show voice input switcher",
            style = NavigationItemStyle.MiscNoArrow,
            navigate = {
                val intent = Intent(context, VoiceInputSwitchActivity::class.java)
                intent.putExtra("targetPackage", context.packageName)
                intent.putExtra("mode", "popup")
                context.startActivity(intent)
            }
        )

        NavigationItem(
            title = "Copy settings menu layout to clipboard",
            style = NavigationItemStyle.Misc,
            navigate = {
                val s = SettingsMenus.flatMap { it.settings }.filter { it.name != 0 }.map {
                    resources.getString(it.name) + (it.subtitle?.let {
                        ": " + resources.getString(it)
                    } ?: "") + if(!it.appearsInSearch) { " (hidden)" } else ""
                }.joinToString("\n")
                context.copyToClipboard(s)
            }
        )

        SettingToggleDataStore(
            "Use expandable suggestions UI for all languages",
            UseExpandableSuggestionsForGeneralIME,
        )

        if(!BuildConfig.IS_PLAYSTORE_BUILD) {
            NavigationItem(
                title = "Crash the app",
                style = NavigationItemStyle.MiscNoArrow,
                navigate = {
                    scope.lifecycleScope.launch {
                        withContext(Dispatchers.Default) {
                            delay(300L)
                            throw RuntimeException("User requested app to crash :3")
                        }
                    }
                },
                icon = painterResource(id = R.drawable.close)
            )

            if(BuildConfig.DEBUG) {
                NavigationItem(
                    title = "Corrupt the settings, the clipboard, and exit the app",
                    style = NavigationItemStyle.MiscNoArrow,
                    navigate = {
                        scope.lifecycleScope.launch {
                            withContext(Dispatchers.Default) {
                                delay(300L)

                                context.getPreferencesDataStoreFile().outputStream().use {
                                    it.write(0)
                                }
                                context.clipboardFile.outputStream().use {
                                    it.write(0)
                                }

                                exitProcess(1)
                            }
                        }
                    }
                )
            }

            NavigationItem(
                title = "Inline Keyboard",
                subtitle = "This can break everything, force stop or crash the app to fix",
                style = NavigationItemStyle.Misc,
                navigate = { navController.navigate("devkeyboard") }
            )

        }
    }
}