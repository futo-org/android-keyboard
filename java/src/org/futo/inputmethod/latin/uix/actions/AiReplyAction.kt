package org.futo.inputmethod.latin.uix.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import org.futo.voiceinput.shared.groq.stream

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
        LaunchedEffect(promptText.value) { promptItem.setValue(promptText.value) }
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text)
            reply.value?.let { Text(it) }
            TextField(
                value = promptText.value,
                onValueChange = { promptText.value = it },
                placeholder = { Text(stringResource(R.string.ai_reply_prompt_placeholder)) },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                val apiKey = context.getSetting(GROQ_REPLY_API_KEY)
                val model = context.getSetting(GROQ_REPLY_MODEL)
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        withContext(Dispatchers.Main) { reply.value = "" }
                        val systemPrompt = DEFAULT_SYSTEM_PROMPT
                        val userPrompt = buildString {
                            if (promptText.value.isNotBlank()) append(promptText.value).append('\n')
                            append(text)
                        }
                        stream(apiKey, systemPrompt, userPrompt, model) { token ->
                            coroutineScope.launch(Dispatchers.Main) {
                                reply.value = (reply.value ?: "") + token
                            }
                        }
                    } catch (t: Throwable) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, t.message ?: "Unknown error", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_reply_generate))
            }
            reply.value?.let { r ->
                Button(onClick = { manager.typeText(r); manager.closeActionWindow() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ai_reply_insert))
                }
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
        val text = AiReplyActionHolder.pendingText
        AiReplyActionHolder.pendingText = ""
        AiReplyWindow(manager, text)
    }
)
