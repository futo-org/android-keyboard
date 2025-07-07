package org.futo.inputmethod.latin.uix.settings.pages

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.GROQ_REPLY_API_KEY
import org.futo.inputmethod.latin.uix.GROQ_REPLY_MODEL
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
    val apiKeyItem = useDataStore(GROQ_REPLY_API_KEY)
    val modelItem = useDataStore(GROQ_REPLY_MODEL)
    val testStatus = remember { mutableStateOf("") }
    val debugLog = remember { mutableStateOf("") }
    var testPrompt by rememberSaveable { mutableStateOf("") }
    val testResponse = remember { mutableStateOf("") }
    val isGenerating = remember { mutableStateOf(false) }

    fun logDebug(message: String) {
        Log.d("GroqConfigChat", message)
        debugLog.value = "${debugLog.value}\n$message"
    }
    
    @Composable
    fun DebugLogSection() {
        if (debugLog.value.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Debug Log",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(200.dp)
            ) {
                Text(
                    text = debugLog.value.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp)
                )
            }
        }
    }
    
    val modelOptions = remember { mutableStateOf(listOf("llama3-70b-8192")) }
    val isLoadingModels = remember { mutableStateOf(false) }
    val modelLoadError = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(apiKeyItem.value) {
        logDebug("API key changed: length=${apiKeyItem.value.length}, prefix=${apiKeyItem.value.take(4)}...")
        if (apiKeyItem.value.isNotBlank()) {
            isLoadingModels.value = true
            modelLoadError.value = null
            try {
                logDebug("Fetching available models...")
                val remote = withContext(Dispatchers.IO) {
                    GroqChatApi.availableModels(apiKeyItem.value)
                }
                if (!remote.isNullOrEmpty()) {
                    modelOptions.value = remote
                    logDebug("Models loaded: ${remote.joinToString(", ")}")
                    if (modelItem.value !in remote) {
                        logDebug("Current model ${modelItem.value} not in remote list, resetting to ${remote.first()}")
                        modelItem.setValue(remote.first())
                    }
                } else {
                    modelLoadError.value = "No models found. Check your API key."
                    logDebug("No models found for API key")
                }
            } catch (e: Exception) {
                modelLoadError.value = "Failed to load models: ${e.message ?: "Unknown error"}"
                logDebug("Model load error: ${e.javaClass.simpleName} - ${e.message ?: "Unknown error"}")
            } finally {
                isLoadingModels.value = false
                logDebug("Model loading completed")
            }
        } else {
            modelOptions.value = listOf("llama3-70b-8192")
            modelLoadError.value = null
            logDebug("API key blank, using default model: llama3-70b-8192")
        }
    }

    ScrollableList {
        ScreenTitle(stringResource(R.string.groq_reply_settings_title), showBack = true, navController)

        SettingTextField(
            title = stringResource(R.string.groq_reply_settings_api_key),
            placeholder = "Enter Groq API key",
            field = GROQ_REPLY_API_KEY
        )

        val modelLabel = when {
            isLoadingModels.value -> "${stringResource(R.string.groq_reply_settings_model)} (Loading...)"
            modelLoadError.value != null -> "${stringResource(R.string.groq_reply_settings_model)} (Error: ${modelLoadError.value})"
            else -> stringResource(R.string.groq_reply_settings_model)
        }

        DropDownPickerSettingItem(
            label = modelLabel,
            options = modelOptions.value,
            selection = modelItem.value,
            onSet = {
                logDebug("Model selected: $it")
                modelItem.setValue(it)
            },
            getDisplayName = { it }
        )

        val testing = stringResource(R.string.groq_settings_testing)
        val successText = stringResource(R.string.groq_settings_success)
        val failureText = stringResource(R.string.groq_settings_failure)

        SettingItem(
            title = stringResource(R.string.groq_settings_test),
            subtitle = testStatus.value,
            onClick = {
                logDebug("Testing API connection...")
                testStatus.value = testing
                lifecycleOwner.lifecycleScope.launch {
                    try {
                        val success = withContext(Dispatchers.IO) {
                            GroqChatApi.test(apiKeyItem.value)
                        }
                        val status = if (success) successText else failureText
                        logDebug("API test result: $status")
                        testStatus.value = status
                    } catch (e: Exception) {
                        val errorMsg = "$failureText: ${e.message ?: "Unknown error"}"
                        logDebug("API test failed: ${e.javaClass.simpleName} - ${e.message ?: "Unknown error"}")
                        testStatus.value = errorMsg
                    }
                }
            }
        ) { }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Test Reply Generation",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        OutlinedTextField(
            value = testPrompt,
            onValueChange = { testPrompt = it },
            label = { Text("Enter a test prompt") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
        
        Button(
            onClick = {
                if (testPrompt.isNotBlank() && !isGenerating.value) {
                    if (apiKeyItem.value.isBlank()) {
                        testResponse.value = "Error: API key is blank"
                        logDebug("Reply generation failed: API key is blank")
                        return@Button
                    }
                    isGenerating.value = true
                    testResponse.value = ""
                    logDebug("Starting reply generation: prompt='$testPrompt', model=${modelItem.value}, apiKeyLength=${apiKeyItem.value.length}")
                    
                    lifecycleOwner.lifecycleScope.launch {
                        try {
                            val response = withContext(Dispatchers.IO) {
                                GroqChatApi.chat(
                                    systemPrompt = "You are a helpful AI assistant.",
                                    userPrompt = testPrompt,
                                    apiKey = apiKeyItem.value,
                                    model = modelItem.value
                                )
                            }
                            if (response != null) {
                                testResponse.value = response
                                logDebug("Reply generation completed: response='${response.take(100)}...'")
                            } else {
                                testResponse.value = "Error: No response received from Groq API"
                                logDebug("Reply generation failed: No response received")
                            }
                        } catch (e: Exception) {
                            val errorMsg = "Error generating reply: ${e.message ?: "Unknown error"}"
                            testResponse.value = errorMsg
                            logDebug("Reply generation error: ${e.javaClass.simpleName} - ${e.message ?: "Unknown error"}")
                        } finally {
                            isGenerating.value = false
                            logDebug("Reply generation attempt finished")
                        }
                    }
                } else {
                    logDebug("Reply generation skipped: prompt='${testPrompt}', isGenerating=${isGenerating.value}")
                }
            },
            enabled = testPrompt.isNotBlank() && !isGenerating.value,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            if (isGenerating.value) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isGenerating.value) "Generating..." else "Generate Reply")
        }

        if (testResponse.value.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = testResponse.value,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        DebugLogSection()
    }
}