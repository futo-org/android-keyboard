package org.futo.inputmethod.latin.uix.settings.pages

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
import org.futo.inputmethod.latin.uix.HiddenKeysSetting
import org.futo.inputmethod.latin.uix.SettingsKey
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


val IS_DEVELOPER = SettingsKey(booleanPreferencesKey("isDeveloperMode"), false)

val TMP_PAYMENT_URL = SettingsKey(stringPreferencesKey("temporaryPaymentUrl"), BuildConfig.PAYMENT_URL)

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

        NavigationItem(
            title = "Text edit variations",
            style = NavigationItemStyle.Misc,
            navigate = { navController.navigate("devtextedit") }
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
    }
}