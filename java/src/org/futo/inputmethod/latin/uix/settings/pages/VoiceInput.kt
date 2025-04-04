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
import org.futo.inputmethod.latin.uix.CAN_EXPAND_SPACE
import org.futo.inputmethod.latin.uix.DISALLOW_SYMBOLS
import org.futo.inputmethod.latin.uix.ENABLE_SOUND
import org.futo.inputmethod.latin.uix.PREFER_BLUETOOTH
import org.futo.inputmethod.latin.uix.USE_SYSTEM_VOICE_INPUT
import org.futo.inputmethod.latin.uix.USE_VAD_AUTOSTOP
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
        ScreenTitle(stringResource(R.string.voice_input_settings_title), showBack = true, navController)

        SettingToggleDataStore(
            title = stringResource(R.string.voice_input_settings_disable_builtin_voice_input),
            subtitle = stringResource(R.string.voice_input_settings_disable_builtin_voice_input_subtitle),
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
                title = stringResource(R.string.voice_input_settings_indication_sounds),
                subtitle = stringResource(R.string.voice_input_settings_indication_sounds_subtitle),
                setting = ENABLE_SOUND
            )

            SettingToggleDataStore(
                title = stringResource(R.string.voice_input_settings_verbose_progress),
                subtitle = stringResource(R.string.voice_input_settings_verbose_progress_subtitle),
                setting = VERBOSE_PROGRESS
            )

            SettingToggleDataStore(
                title = stringResource(R.string.voice_input_settings_use_bluetooth_mic),
                subtitle = stringResource(R.string.voice_input_settings_use_bluetooth_mic_subtitle),
                setting = PREFER_BLUETOOTH
            )

            SettingToggleDataStore(
                title = stringResource(R.string.voice_input_settings_audio_focus),
                subtitle = stringResource(R.string.voice_input_settings_audio_focus_subtitle),
                setting = AUDIO_FOCUS
            )

            SettingToggleDataStore(
                title = stringResource(R.string.voice_input_settings_suppress_symbols),
                setting = DISALLOW_SYMBOLS
            )

            SettingToggleDataStore(
                title = stringResource(R.string.voice_input_settings_long_form),
                subtitle = stringResource(R.string.voice_input_settings_long_form_subtitle),
                setting = CAN_EXPAND_SPACE
            )

            SettingToggleDataStore(
                title = stringResource(R.string.voice_input_settings_autostop_vad),
                subtitle = stringResource(R.string.voice_input_settings_autostop_vad_subtitle),
                setting = USE_VAD_AUTOSTOP
            )

            NavigationItem(
                title = stringResource(R.string.voice_input_settings_change_models),
                subtitle = stringResource(R.string.voice_input_settings_change_models_subtitle),
                style = NavigationItemStyle.Misc,
                navigate = { navController.navigate("languages") }
            )
        }
    }
}