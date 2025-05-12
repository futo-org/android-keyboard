package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.AndroidTextInput
import org.futo.inputmethod.latin.uix.DebugOnly
import org.futo.inputmethod.latin.uix.HiddenKeysSetting
import org.futo.inputmethod.latin.uix.OldStyleActionsBar
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.UixManagerInstanceForDebug
import org.futo.inputmethod.latin.uix.getPreferencesDataStoreFile
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingTextField
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.SettingToggleRaw
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.updates.DISABLE_UPDATE_REMINDER
import org.futo.inputmethod.updates.dismissedMigrateUpdateNotice
import java.io.File
import kotlin.system.exitProcess


val IS_DEVELOPER = SettingsKey(booleanPreferencesKey("isDeveloperMode"), false)

val TMP_PAYMENT_URL = SettingsKey(stringPreferencesKey("temporaryPaymentUrl"), BuildConfig.PAYMENT_URL)

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

@Preview(showBackground = true)
@Composable
fun DeveloperScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val scope = LocalLifecycleOwner.current

    ScrollableList {
        ScreenTitle("Developer", showBack = true, navController)

        SettingToggleDataStore(title = "Developer mode", setting = IS_DEVELOPER)

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

        SettingTextField("Payment URL", "https://example.com", TMP_PAYMENT_URL)


        ScreenTitle(title = "Here be dragons")
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
                            File(context.filesDir, "clipboard.json").outputStream().use {
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