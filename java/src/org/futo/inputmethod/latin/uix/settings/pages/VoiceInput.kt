package org.futo.inputmethod.latin.uix.settings.pages

import android.content.Intent
import androidx.compose.runtime.Composable
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.AUDIO_FOCUS
import org.futo.inputmethod.latin.uix.CAN_EXPAND_SPACE
import org.futo.inputmethod.latin.uix.DICTATION_CAPITALIZATION
import org.futo.inputmethod.latin.uix.DICTATION_COMMANDS_ENABLED
import org.futo.inputmethod.latin.uix.DICTATION_CURRENCY
import org.futo.inputmethod.latin.uix.DICTATION_EMOTICONS
import org.futo.inputmethod.latin.uix.DICTATION_FORMATTING
import org.futo.inputmethod.latin.uix.DICTATION_IP_MARKS
import org.futo.inputmethod.latin.uix.DICTATION_MATH
import org.futo.inputmethod.latin.uix.DICTATION_PUNCTUATION
import org.futo.inputmethod.latin.uix.DICTATION_SYMBOLS
import org.futo.inputmethod.latin.uix.DISALLOW_SYMBOLS
import org.futo.inputmethod.latin.uix.ENABLE_SOUND
import org.futo.inputmethod.latin.uix.PREFER_BLUETOOTH
import org.futo.inputmethod.latin.uix.USE_PERSONAL_DICT
import org.futo.inputmethod.latin.uix.USE_SYSTEM_VOICE_INPUT
import org.futo.inputmethod.latin.uix.USE_VAD_AUTOSTOP
import org.futo.inputmethod.latin.uix.VERBOSE_PROGRESS
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import org.futo.inputmethod.latin.uix.settings.userSettingNavigationItem
import org.futo.inputmethod.latin.uix.settings.userSettingToggleDataStore

private val visibilityCheckNotSystemVoiceInput = @Composable {
    useDataStoreValue(USE_SYSTEM_VOICE_INPUT) == false
}

val DictationCommandsMenu = UserSettingsMenu(
    title = R.string.dictation_commands_title,
    navPath = "dictationCommands", registerNavPath = true,
    settings = listOf(
        userSettingToggleDataStore(
            title = R.string.dictation_commands_title,
            subtitle = R.string.dictation_commands_subtitle,
            setting = DICTATION_COMMANDS_ENABLED
        ),

        userSettingToggleDataStore(
            title = R.string.dictation_formatting_title,
            subtitle = R.string.dictation_formatting_subtitle,
            setting = DICTATION_FORMATTING
        ),

        userSettingToggleDataStore(
            title = R.string.dictation_capitalization_title,
            subtitle = R.string.dictation_capitalization_subtitle,
            setting = DICTATION_CAPITALIZATION
        ),

        userSettingToggleDataStore(
            title = R.string.dictation_punctuation_title,
            subtitle = R.string.dictation_punctuation_subtitle,
            setting = DICTATION_PUNCTUATION
        ),

        userSettingToggleDataStore(
            title = R.string.dictation_symbols_title,
            subtitle = R.string.dictation_symbols_subtitle,
            setting = DICTATION_SYMBOLS
        ),

        userSettingToggleDataStore(
            title = R.string.dictation_math_title,
            subtitle = R.string.dictation_math_subtitle,
            setting = DICTATION_MATH
        ),

        userSettingToggleDataStore(
            title = R.string.dictation_currency_title,
            subtitle = R.string.dictation_currency_subtitle,
            setting = DICTATION_CURRENCY
        ),

        userSettingToggleDataStore(
            title = R.string.dictation_emoticons_title,
            subtitle = R.string.dictation_emoticons_subtitle,
            setting = DICTATION_EMOTICONS
        ),

        userSettingToggleDataStore(
            title = R.string.dictation_ip_marks_title,
            subtitle = R.string.dictation_ip_marks_subtitle,
            setting = DICTATION_IP_MARKS
        )
    )
)

val VoiceInputMenu = UserSettingsMenu(
    title = R.string.voice_input_settings_title,
    navPath = "voiceInput", registerNavPath = true,
    settings = listOf(
        userSettingToggleDataStore(
            title = R.string.voice_input_settings_disable_builtin_voice_input,
            subtitle = R.string.voice_input_settings_disable_builtin_voice_input_subtitle,
            setting = USE_SYSTEM_VOICE_INPUT
        ),

        //if(!systemVoiceInput.value) {
        userSettingToggleDataStore(
            title = R.string.voice_input_settings_indication_sounds,
            subtitle = R.string.voice_input_settings_indication_sounds_subtitle,
            setting = ENABLE_SOUND
        ).copy(visibilityCheck = visibilityCheckNotSystemVoiceInput),

        /*
        userSettingToggleDataStore(
            title = R.string.voice_input_settings_verbose_progress,
            subtitle = R.string.voice_input_settings_verbose_progress_subtitle,
            setting = VERBOSE_PROGRESS
        ).copy(visibilityCheck = visibilityCheckNotSystemVoiceInput),
         */

        userSettingToggleDataStore(
            title = R.string.voice_input_settings_use_personal_dict,
            subtitle = R.string.voice_input_settings_use_personal_dict_subtitle,
            setting = USE_PERSONAL_DICT
        ).copy(visibilityCheck = visibilityCheckNotSystemVoiceInput),

        userSettingToggleDataStore(
            title = R.string.voice_input_settings_use_bluetooth_mic,
            subtitle = R.string.voice_input_settings_use_bluetooth_mic_subtitle,
            setting = PREFER_BLUETOOTH
        ).copy(visibilityCheck = visibilityCheckNotSystemVoiceInput),

        userSettingToggleDataStore(
            title = R.string.voice_input_settings_audio_focus,
            subtitle = R.string.voice_input_settings_audio_focus_subtitle,
            setting = AUDIO_FOCUS
        ).copy(visibilityCheck = visibilityCheckNotSystemVoiceInput),

        userSettingToggleDataStore(
            title = R.string.voice_input_settings_suppress_symbols,
            setting = DISALLOW_SYMBOLS
        ).copy(visibilityCheck = visibilityCheckNotSystemVoiceInput),

        userSettingToggleDataStore(
            title = R.string.voice_input_settings_long_form,
            subtitle = R.string.voice_input_settings_long_form_subtitle,
            setting = CAN_EXPAND_SPACE
        ).copy(visibilityCheck = visibilityCheckNotSystemVoiceInput),

        userSettingToggleDataStore(
            title = R.string.voice_input_settings_autostop_vad,
            subtitle = R.string.voice_input_settings_autostop_vad_subtitle,
            setting = USE_VAD_AUTOSTOP
        ).copy(visibilityCheck = visibilityCheckNotSystemVoiceInput),

        userSettingNavigationItem(
            title = R.string.dictation_commands_title,
            subtitle = R.string.dictation_commands_subtitle,
            style = NavigationItemStyle.Misc,
            navigateTo = "dictationCommands"
        ).copy(visibilityCheck = visibilityCheckNotSystemVoiceInput),

        userSettingNavigationItem(
            title = R.string.voice_input_settings_change_models,
            subtitle = R.string.voice_input_settings_change_models_subtitle,
            style = NavigationItemStyle.Misc,
            navigateTo = "languages"
        ).copy(visibilityCheck = visibilityCheckNotSystemVoiceInput),
        //}
    )
)