package org.futo.inputmethod.latin.uix.actions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.GROQ_REPLY_API_KEY
import org.futo.inputmethod.latin.uix.AI_REPLY_PROMPT
import org.futo.inputmethod.latin.uix.GROQ_REPLY_MODEL
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.settings.useDataStore
import androidx.compose.material3.TextField
import androidx.compose.runtime.LaunchedEffect
import org.futo.voiceinput.shared.groq.GroqChatApi
import android.util.Log
import org.futo.inputmethod.latin.uix.utils.latestClipboardText

private const val DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant that writes concise replies."

private class AiReplyWindow(
    val manager: KeyboardManagerForAction,
    val text: String
) : ActionWindow() {
    @Composable
    override fun windowName(): String = stringResource(R.string.action_ai_reply_title)

    @Composable
    override fun WindowContents(keyboardShown: Boolean) {
        val context = LocalContext.current
        val reply = remember { mutableStateOf<String?>(null) }
        val promptItem = useDataStore(AI_REPLY_PROMPT)
        val promptText = remember { mutableStateOf(promptItem.value) }
        val coroutineScope = rememberCoroutineScope()
        val isLoading = remember { mutableStateOf(false) }
        val scrollState = rememberScrollState()
        
        LaunchedEffect(promptText.value) { promptItem.setValue(promptText.value) }
        
        // Calculate max height based on keyboard state
        val maxHeight = if (keyboardShown) 0.5f else 0.7f
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Text content with max height and scrolling
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(text)
                Spacer(modifier = Modifier.height(8.dp))
                reply.value?.let { 
                    Text(it) 
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            // Fixed height prompt input
            TextField(
                value = promptText.value,
                onValueChange = { promptText.value = it },
                placeholder = { Text(stringResource(R.string.ai_reply_prompt_placeholder)) },
                modifier = Modifier.fillMaxWidth()
            )
            
            // Button row for Generate and Insert actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Generate button
                Button(
                    onClick = {
                        val apiKey = context.getSetting(GROQ_REPLY_API_KEY)
                        val model = context.getSetting(GROQ_REPLY_MODEL)
                        
                        if (apiKey.isBlank()) {
                            Toast.makeText(
                                context, 
                                context.getString(R.string.groq_api_key_required), 
                                Toast.LENGTH_LONG
                            ).show()
                            return@Button
                        }
                        
                        isLoading.value = true
                        reply.value = null
                        
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                withContext(Dispatchers.Main) { reply.value = "" }
                                val systemPrompt = DEFAULT_SYSTEM_PROMPT
                                val userPrompt = buildString {
                                    if (promptText.value.isNotBlank()) append(promptText.value).append('\n')
                                    append(text)
                                }
                                
                                Log.d("AiReplyAction", "Starting chat completion with model: $model")
                                val response = GroqChatApi.chat(
                                    systemPrompt = systemPrompt,
                                    userPrompt = userPrompt,
                                    apiKey = apiKey,
                                    model = model
                                )
                                
                                response?.let { generatedText ->
                                    withContext(Dispatchers.Main) {
                                        reply.value = generatedText
                                        Log.d("AiReplyAction", "Successfully generated reply")
                                    }
                                } ?: run {
                                    throw Exception("No response received from Groq API")
                                }
                            } catch (t: Throwable) {
                                Log.e("AiReplyAction", "Error generating reply", t)
                                withContext(Dispatchers.Main) {
                                    val errorMsg = context.getString(R.string.ai_reply_error, t.message ?: context.getString(R.string.unknown_error))
                                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                }
                            } finally {
                                withContext(Dispatchers.Main) {
                                    isLoading.value = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading.value
                ) {
                    if (isLoading.value) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Generating")
                    } else {
                        Text("Generate")
                    }
                }
                
                // Insert button (only shown when there's a reply)
                reply.value?.let { r ->
                    Button(
                        onClick = { 
                            manager.typeText(r)
                            manager.closeActionWindow() 
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Insert")
                    }
                } ?: Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

object AiReplyActionHolder { var pendingText: String = "" }

val AiReplyAction = Action(
    icon = R.drawable.text_prediction,
    name = R.string.action_ai_reply_title,
    simplePressImpl = null,
    windowImpl = { manager, _ ->
        var text = AiReplyActionHolder.pendingText
        if (text.isBlank()) {
            text = latestClipboardText(manager.getContext()) ?: ""
        }
        if (text.isBlank()) {
            text = ""
        }
        AiReplyActionHolder.pendingText = ""
        AiReplyWindow(manager, text)
    }
)
