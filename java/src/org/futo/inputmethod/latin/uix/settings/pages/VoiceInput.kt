package org.futo.inputmethod.latin.uix.settings.pages

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.DISALLOW_SYMBOLS
import org.futo.inputmethod.latin.uix.ENABLE_SOUND
import org.futo.inputmethod.latin.uix.ENGLISH_MODEL_INDEX
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.USE_SYSTEM_VOICE_INPUT
import org.futo.inputmethod.latin.uix.VERBOSE_PROGRESS
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.voiceinput.shared.ENGLISH_MODELS
import org.futo.voiceinput.shared.types.ModelLoader

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
                subtitle = "Display verbose information about model inference",
                setting = VERBOSE_PROGRESS
            )

            SettingToggleDataStore(
                title = "Suppress symbols",
                setting = DISALLOW_SYMBOLS
            )

            NavigationItem(
                title = "Languages",
                subtitle = "To change the model, visit Languages menu",
                style = NavigationItemStyle.Misc,
                navigate = { navController.navigate("languages") }
            )
        }
    }
}