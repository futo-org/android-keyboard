package org.futo.inputmethod.latin.uix.settings.pages

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.AUDIO_FOCUS
import org.futo.inputmethod.latin.uix.DISALLOW_SYMBOLS
import org.futo.inputmethod.latin.uix.ENABLE_SOUND
import org.futo.inputmethod.latin.uix.PREFER_BLUETOOTH
import org.futo.inputmethod.latin.uix.USE_SYSTEM_VOICE_INPUT
import org.futo.inputmethod.latin.uix.VERBOSE_PROGRESS
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStore

@Preview
@Composable
fun VoiceInputScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val systemVoiceInput = useDataStore(key = USE_SYSTEM_VOICE_INPUT.key, default = USE_SYSTEM_VOICE_INPUT.default)
    ScrollableList {
        ScreenTitle("Voice Input", showBack = true, navController)

        SettingToggleDataStore(
            title = "Disable built-in voice input",
            subtitle = "Use voice input provided by external app",
            setting = USE_SYSTEM_VOICE_INPUT
        )

        if(!systemVoiceInput.value) {
            NavigationItem(
                title = stringResource(R.string.edit_personal_dictionary),
                style = NavigationItemStyle.HomePrimary,
                icon = painterResource(id = R.drawable.book),
                navigate = {
                    val intent = Intent("android.settings.USER_DICTIONARY_SETTINGS")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
            )

            SettingToggleDataStore(
                title = "Indication sounds",
                subtitle = "Play sounds on start and cancel",
                setting = ENABLE_SOUND
            )

            SettingToggleDataStore(
                title = "Verbose progress",
                subtitle = "Display verbose information such as mic being used",
                setting = VERBOSE_PROGRESS
            )

            SettingToggleDataStore(
                title = "Prefer Bluetooth Mic",
                subtitle = "There may be extra delay to recording starting as Bluetooth SCO connection must be negotiated",
                setting = PREFER_BLUETOOTH
            )


            SettingToggleDataStore(
                title = "Audio Focus",
                subtitle = "Pause videos/music when voice input is activated",
                setting = AUDIO_FOCUS
            )

            SettingToggleDataStore(
                title = "Suppress symbols",
                setting = DISALLOW_SYMBOLS
            )

            NavigationItem(
                title = "Models",
                subtitle = "To change the models, visit Languages & Models menu",
                style = NavigationItemStyle.Misc,
                navigate = { navController.navigate("languages") }
            )
        }
    }
}