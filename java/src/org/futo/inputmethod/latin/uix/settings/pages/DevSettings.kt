package org.futo.inputmethod.latin.uix.settings.pages

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
import org.futo.inputmethod.latin.uix.actions.clipboardFile
import org.futo.inputmethod.latin.uix.getPreferencesDataStoreFile
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingTextField
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.SettingToggleRaw
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
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



private fun triggerImportTheme(context: Context) {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/zip"
    }
    (context as Activity).startActivityForResult(intent, IMPORT_SETTINGS_REQUEST)
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
        SettingToggleDataStore(
            "Use expandable suggestions UI for all languages",
            UseExpandableSuggestionsForGeneralIME,
        )

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