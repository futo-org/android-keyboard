package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.HiddenKeysSetting
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStore


val IS_DEVELOPER = booleanPreferencesKey("isDeveloperMode")

@Preview(showBackground = true)
@Composable
fun DeveloperScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val scope = LocalLifecycleOwner.current

    ScrollableList {
        ScreenTitle("Developer", showBack = true, navController)

        SettingToggleDataStore(
            title = "Touch typing mode",
            subtitle = "Hides all keys. Touch typists only! Recommended to disable emoji key and enable key borders",
            setting = SettingsKey(HiddenKeysSetting, false)
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
    }
}