package org.futo.inputmethod.latin.uix.settings.pages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.voiceinput.shared.groq.GroqWhisperApi

class GroqWhisperConfigViewModel : ViewModel() {
    private val _modelOptions = MutableStateFlow<List<String>>(emptyList())
    val modelOptions: StateFlow<List<String>> = _modelOptions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _testStatus = MutableStateFlow<String?>(null)
    val testStatus: StateFlow<String?> = _testStatus.asStateFlow()
    
    // Known Whisper models as fallback
    private val knownWhisperModels = listOf(
        "whisper-large-v3",
        "whisper-large-v2",
        "whisper-1"
    )

    /**
     * Fetches available models and updates state.
     */

     fun loadModels(apiKey: String, currentModel: String? = null) {
    if (apiKey.isBlank()) {
        _modelOptions.value = knownWhisperModels // Use fallback instead of empty
        return
    }
    
    viewModelScope.launch {
        _isLoading.value = true
        _errorMessage.value = null
        try {
            val remote = withContext(Dispatchers.IO) { 
                GroqWhisperApi.availableModels(apiKey)
            }
            
            if (remote.isNullOrEmpty()) {
                _errorMessage.value = "No models available from API. Using known models."
                _modelOptions.value = knownWhisperModels // Fallback to known models
            } else {
                _modelOptions.value = remote
                // Optional: Add current model if it's not in the remote list
                if (currentModel != null && currentModel !in remote) {
                    _modelOptions.value = remote + currentModel
                }
            }
        } catch (e: Exception) {
            _errorMessage.value = "Failed to load models: ${e.message}. Using known models."
            _modelOptions.value = knownWhisperModels // Fallback on error
        } finally {
            _isLoading.value = false
        }
    }
}

    fun testApiKey(apiKey: String) {
        if (apiKey.isBlank()) {
            _testStatus.value = "API key is required"
            return
        }
        
        viewModelScope.launch {
            _testStatus.value = "Testing..."
            try {
                val success = withContext(Dispatchers.IO) {
                    GroqWhisperApi.test(apiKey)
                }
                _testStatus.value = if (success) "Test successful!" else "Test failed. Check your API key."
            } catch (e: Exception) {
                _testStatus.value = "Test failed: ${e.message}"
            }
        }
    }
}
