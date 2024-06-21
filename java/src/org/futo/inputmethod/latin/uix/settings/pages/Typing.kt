package org.futo.inputmethod.latin.uix.settings.pages

import android.preference.PreferenceManager
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.settings.Settings.PREF_VIBRATION_DURATION_SETTINGS
import org.futo.inputmethod.latin.uix.SHOW_EMOJI_SUGGESTIONS
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.actions.AllActions
import org.futo.inputmethod.latin.uix.actions.ClipboardHistoryEnabled
import org.futo.inputmethod.latin.uix.settings.DropDownPicker
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingItem
import org.futo.inputmethod.latin.uix.settings.SettingSlider
import org.futo.inputmethod.latin.uix.settings.SettingSliderSharedPrefsInt
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.SettingToggleSharedPrefs
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.useSharedPrefsInt
import kotlin.math.roundToInt

val vibrationDurationSetting = SettingsKey(
    intPreferencesKey("vibration_duration"),
    -1
)

@Preview
@Composable
fun TypingScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val (vibration, _) = useDataStore(key = vibrationDurationSetting.key, default = vibrationDurationSetting.default)

    LaunchedEffect(vibration) {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        withContext(Dispatchers.Main) {
            sharedPrefs.edit {
                putInt(PREF_VIBRATION_DURATION_SETTINGS, vibration)
            }
        }
    }

    ScrollableList {
        ScreenTitle("Typing Preferences", showBack = true, navController)

        SettingToggleDataStore(
            title = "Emoji Suggestions",
            subtitle = "Suggest emojis while you're typing",
            setting = SHOW_EMOJI_SUGGESTIONS
        )

        SettingToggleSharedPrefs(
            title = "Swipe typing",
            key = Settings.PREF_GESTURE_INPUT,
            default = true
        )

        SettingToggleSharedPrefs(
            title = "Number row",
            key = Settings.PREF_ENABLE_NUMBER_ROW,
            default = false
        )

        SettingToggleDataStore(
            title = "Clipboard History",
            setting = ClipboardHistoryEnabled
        )

        SettingToggleSharedPrefs(
            title = "Action key enabled",
            subtitle = "Show the action key on the bottom row",
            key = Settings.PREF_SHOW_ACTION_KEY,
            default = true
        )

        val emojiKey = useSharedPrefsInt(key = Settings.PREF_ACTION_KEY_ID, default = 0)
        SettingItem(title = "Action key") {
            DropDownPicker(
                label = "",
                options = AllActions,
                selection = AllActions[emojiKey.value],
                onSet = {
                    emojiKey.setValue(AllActions.indexOf(it))
                },
                getDisplayName = {
                    context.getString(it.name)
                },
                modifier = Modifier.width(180.dp)
            )
        }

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

        SettingSliderSharedPrefsInt(
            title = "Long Press Duration",
            key = Settings.PREF_KEY_LONGPRESS_TIMEOUT,
            default = 300,
            range = 100.0f .. 700.0f,
            hardRange = 25.0f .. 1200.0f,
            transform = { it.roundToInt() },
            indicator = { "$it ms" },
            steps = 23
        )
    }
}