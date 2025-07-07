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
import org.futo.inputmethod.latin.uix.GROQ_CHAT_MODEL
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.SettingItem
import org.futo.inputmethod.latin.uix.settings.SettingTextField
import org.futo.inputmethod.latin.uix.settings.DropDownPickerSettingItem
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.voiceinput.shared.groq.GroqChatApi

@Composable
fun GroqChatConfigScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val apiKeyItem = useDataStore(GROQ_API_KEY)
    val modelItem = useDataStore(GROQ_CHAT_MODEL)
    val testStatus = remember { mutableStateOf("") }
    val modelOptions = remember {
        mutableStateOf(listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant"))
    }

    LaunchedEffect(apiKeyItem.value) {
        if (apiKeyItem.value.isNotBlank()) {
            val remote = withContext(Dispatchers.IO) {
                GroqChatApi.availableModels(apiKeyItem.value)
            }
            if (!remote.isNullOrEmpty()) {
                modelOptions.value = remote
            }
        }
    }

    ScrollableList {
        ScreenTitle(stringResource(R.string.groq_settings_title), showBack = true, navController)

        SettingTextField(
            title = stringResource(R.string.groq_settings_api_key),
            placeholder = "sk-...",
            field = GROQ_API_KEY
        )

        DropDownPickerSettingItem(
            label = stringResource(R.string.groq_settings_model),
            options = modelOptions.value,
            selection = modelItem.value,
            onSet = { modelItem.setValue(it) },
            getDisplayName = { it }
        )

        val testing = stringResource(R.string.groq_settings_testing)
        val successText = stringResource(R.string.groq_settings_success)
        val failureText = stringResource(R.string.groq_settings_failure)

        SettingItem(
            title = stringResource(R.string.groq_settings_test),
            subtitle = testStatus.value,
            onClick = {
                lifecycleOwner.lifecycleScope.launch {
                    testStatus.value = testing
                    val success = withContext(Dispatchers.IO) {
                        GroqChatApi.test(apiKeyItem.value)
                    }
                    testStatus.value = if (success) successText else failureText
                }
            }
        ) { }
    }
}
