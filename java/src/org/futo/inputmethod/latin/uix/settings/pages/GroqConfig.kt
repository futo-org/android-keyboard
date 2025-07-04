package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.GROQ_API_KEY
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.SettingItem
import org.futo.inputmethod.latin.uix.settings.SettingTextField
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.voiceinput.shared.groq.GroqWhisperApi

@Composable
fun GroqConfigScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val apiKeyItem = useDataStore(GROQ_API_KEY)
    val testStatus = remember { mutableStateOf("") }

    ScrollableList {
        ScreenTitle(stringResource(R.string.groq_settings_title), showBack = true, navController)

        SettingTextField(
            title = stringResource(R.string.groq_settings_api_key),
            placeholder = "sk-...",
            field = GROQ_API_KEY
        )

        SettingItem(
            title = stringResource(R.string.groq_settings_test),
            subtitle = testStatus.value,
           onClick = {
                val testing = stringResource(R.string.groq_settings_testing)
                val successText = stringResource(R.string.groq_settings_success)
                val failureText = stringResource(R.string.groq_settings_failure)
                lifecycleOwner.lifecycleScope.launch {
                    testStatus.value = testing
                    val success = withContext(Dispatchers.IO) {
                        GroqWhisperApi.test(apiKeyItem.value)
                    }
                    testStatus.value = if(success) successText else failureText
                }
            }
        ) { }
    }
}
