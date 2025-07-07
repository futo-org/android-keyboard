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
import org.futo.inputmethod.latin.uix.GROQ_VOICE_API_KEY
import org.futo.inputmethod.latin.uix.GROQ_VOICE_MODEL
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.SettingItem
import org.futo.inputmethod.latin.uix.settings.SettingTextField
import org.futo.inputmethod.latin.uix.settings.DropDownPickerSettingItem
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.voiceinput.shared.groq.GroqWhisperApi

@Composable
fun GroqWhisperConfigScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val apiKeyItem = useDataStore(GROQ_VOICE_API_KEY)
    val modelItem = useDataStore(GROQ_VOICE_MODEL)
    val testStatus = remember { mutableStateOf("") }
    val modelOptions = remember { mutableStateOf(listOf("whisper-large-v3")) }
    val modelsLoading = remember { mutableStateOf(false) }
    val modelsError = remember { mutableStateOf<String?>(null) }

    // Load models when API key changes
    LaunchedEffect(apiKeyItem.value) {
        if (apiKeyItem.value.isNotBlank()) {
            modelsLoading.value = true
            modelsError.value = null
            
            try {
                val remote = withContext(Dispatchers.IO) {
                    GroqWhisperApi.availableModels(apiKeyItem.value)
                }
                
                if (!remote.isNullOrEmpty()) {
                    modelOptions.value = remote
                    modelsError.value = null
                    
                    // If current model is not in the list, reset to first available
                    if (modelItem.value !in remote) {
                        modelItem.setValue(remote.first())
                    }
                } else {
                    modelsError.value = "Failed to load models"
                    // Keep default model if loading fails
                }
            } catch (e: Exception) {
                modelsError.value = "Error loading models: ${e.message}"
            } finally {
                modelsLoading.value = false
            }
        } else {
            // Reset to default when API key is empty
            modelOptions.value = listOf("whisper-large-v3")
            modelsError.value = null
        }
    }

    ScrollableList {
        ScreenTitle(stringResource(R.string.groq_voice_settings_title), showBack = true, navController)

        SettingTextField(
            title = stringResource(R.string.groq_voice_settings_api_key),
            placeholder = "sk-...",
            field = GROQ_VOICE_API_KEY
        )

        // Models dropdown with loading and error states
        DropDownPickerSettingItem(
            label = stringResource(R.string.groq_voice_settings_model),
            options = modelOptions.value,
            selection = modelItem.value,
            onSet = { modelItem.setValue(it) },
            getDisplayName = { model ->
                when {
                    modelsLoading.value -> "$model (Loading...)"
                    modelsError.value != null -> "$model (Error loading models)"
                    else -> model
                }
            }
        )

        // Show model loading status
        if (modelsLoading.value) {
            SettingItem(
                title = "Loading Models...",
                subtitle = "Fetching available Whisper models",
                onClick = { }
            ) { }
        }

        // Show model error status
        modelsError.value?.let { error ->
            SettingItem(
                title = "Model Loading Error",
                subtitle = error,
                onClick = {
                    // Retry loading models
                    lifecycleOwner.lifecycleScope.launch {
                        if (apiKeyItem.value.isNotBlank()) {
                            modelsLoading.value = true
                            modelsError.value = null
                            
                            try {
                                val remote = withContext(Dispatchers.IO) {
                                    GroqWhisperApi.availableModels(apiKeyItem.value)
                                }
                                
                                if (!remote.isNullOrEmpty()) {
                                    modelOptions.value = remote
                                    modelsError.value = null
                                } else {
                                    modelsError.value = "Failed to load models"
                                }
                            } catch (e: Exception) {
                                modelsError.value = "Error loading models: ${e.message}"
                            } finally {
                                modelsLoading.value = false
                            }
                        }
                    }
                }
            ) { }
        }

        val testing = stringResource(R.string.groq_settings_testing)
        val successText = stringResource(R.string.groq_settings_success)
        val failureText = stringResource(R.string.groq_settings_failure)

        SettingItem(
            title = stringResource(R.string.groq_settings_test),
            subtitle = when {
                testStatus.value.isEmpty() -> "Test API connection"
                else -> testStatus.value
            },
            onClick = {
                if (apiKeyItem.value.isBlank()) {
                    testStatus.value = "Please enter API key first"
                    return@SettingItem
                }
                
                lifecycleOwner.lifecycleScope.launch {
                    testStatus.value = testing
                    try {
                        val success = withContext(Dispatchers.IO) {
                            GroqWhisperApi.test(apiKeyItem.value)
                        }
                        testStatus.value = if (success) successText else failureText
                    } catch (e: Exception) {
                        testStatus.value = "Test failed: ${e.message}"
                    }
                }
            }
        ) { }

        // Refresh models button
        SettingItem(
            title = "Refresh Models",
            subtitle = when {
                modelsLoading.value -> "Loading..."
                modelsError.value != null -> "Tap to retry"
                else -> "Reload available models"
            },
            onClick = {
                if (apiKeyItem.value.isBlank()) {
                    modelsError.value = "Please enter API key first"
                    return@SettingItem
                }
                
                lifecycleOwner.lifecycleScope.launch {
                    modelsLoading.value = true
                    modelsError.value = null
                    
                    try {
                        val remote = withContext(Dispatchers.IO) {
                            GroqWhisperApi.availableModels(apiKeyItem.value)
                        }
                        
                        if (!remote.isNullOrEmpty()) {
                            modelOptions.value = remote
                            modelsError.value = null
                        } else {
                            modelsError.value = "No models found"
                        }
                    } catch (e: Exception) {
                        modelsError.value = "Error: ${e.message}"
                    } finally {
                        modelsLoading.value = false
                    }
                }
            }
        ) { }
    }
}