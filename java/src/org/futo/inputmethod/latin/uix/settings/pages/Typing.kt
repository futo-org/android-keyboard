package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.SHOW_EMOJI_SUGGESTIONS
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.SettingToggleSharedPrefs

@Preview
@Composable
fun TypingScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    ScrollableList {
        ScreenTitle("Typing Preferences", showBack = true, navController)

        SettingToggleDataStore(
            title = "Emoji Suggestions",
            subtitle = "Suggest emojis while you're typing",
            setting = SHOW_EMOJI_SUGGESTIONS
        )
        SettingToggleSharedPrefs(
            title = stringResource(R.string.auto_cap),
            subtitle = stringResource(R.string.auto_cap_summary),
            key = Settings.PREF_AUTO_CAP,
            default = true
        )
        SettingToggleSharedPrefs(
            title = stringResource(R.string.use_double_space_period),
            subtitle = stringResource(R.string.use_double_space_period_summary),
            key = Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD,
            default = true
        )
        SettingToggleSharedPrefs(
            title = stringResource(R.string.vibrate_on_keypress),
            key = Settings.PREF_VIBRATE_ON,
            default = booleanResource(R.bool.config_default_vibration_enabled)
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
            title = stringResource(R.string.show_language_switch_key),
            subtitle = stringResource(R.string.show_language_switch_key_summary),
            key = Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY,
            default = false
        )

        // TODO: SeekBarDialogPreference pref_vibration_duration_settings etc
    }
}