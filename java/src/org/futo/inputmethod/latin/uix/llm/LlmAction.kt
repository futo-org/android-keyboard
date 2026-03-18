package org.futo.inputmethod.latin.uix.llm

import android.view.inputmethod.ExtractedTextRequest
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.getSetting

val LlmRewriteAction = Action(
    icon = R.drawable.sparkle,
    name = R.string.action_llm_rewrite_title,
    simplePressImpl = null,
    windowImpl = { manager, _ ->
        LlmActionWindow(manager)
    }
)

private class LlmActionWindow(
    private val manager: KeyboardManagerForAction
) : ActionWindow() {

    private val isLoading: MutableState<Boolean> = mutableStateOf(false)
    private val undoText: MutableState<String?> = mutableStateOf(null)
    private var currentJob: Job? = null

    @Composable
    override fun windowName(): String = stringResource(R.string.llm_prompt_picker_title)

    @Composable
    override fun WindowContents(keyboardShown: Boolean) {
        val context = LocalContext.current
        val prompts = remember { context.loadPrompts() }
        val loading = isLoading.value
        val undo = undoText.value

        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.llm_rewriting_text),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = {
                        currentJob?.cancel()
                        isLoading.value = false
                    }) {
                        Text(stringResource(R.string.llm_cancel))
                    }
                }
            }
        } else if (undo != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                TextButton(onClick = {
                    performUndo()
                }) {
                    Text(stringResource(R.string.llm_undo_rewrite))
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(prompts) { prompt ->
                    PromptItem(prompt) {
                        executePrompt(prompt)
                    }
                }
            }
        }
    }

    @Composable
    private fun PromptItem(prompt: LlmPrompt, onClick: () -> Unit) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = prompt.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    private fun executePrompt(prompt: LlmPrompt) {
        val context = manager.getContext()
        val latinIME = manager.getLatinIMEForDebug()
        val ic = latinIME.currentInputConnection
        if (ic == null) {
            Toast.makeText(context, context.getString(R.string.llm_no_text), Toast.LENGTH_SHORT).show()
            return
        }

        val selectedText = ic.getSelectedText(0)?.toString()
        val hasSelection = !selectedText.isNullOrEmpty()

        val textToRewrite: String
        if (hasSelection) {
            textToRewrite = selectedText!!
        } else {
            val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
            textToRewrite = extracted?.text?.toString() ?: ""
        }

        if (textToRewrite.isBlank()) {
            Toast.makeText(context, context.getString(R.string.llm_no_text), Toast.LENGTH_SHORT).show()
            return
        }

        val baseUrl = context.getSetting(LLM_BACKEND_URL.key, LLM_BACKEND_URL.default)
        val apiKey = context.getSetting(LLM_API_KEY.key, LLM_API_KEY.default)
        val defaultSystemPrompt = context.getSetting(LLM_DEFAULT_SYSTEM_PROMPT.key, LLM_DEFAULT_SYSTEM_PROMPT.default)

        val systemPrompt = prompt.systemPrompt.ifBlank { defaultSystemPrompt }
        val client = LlmApiClient(baseUrl, apiKey.ifBlank { null })

        isLoading.value = true

        currentJob = manager.getLifecycleScope().launch {
            val response = client.rewriteText(
                systemPrompt = systemPrompt,
                userPrompt = prompt.userPromptTemplate,
                inputText = textToRewrite,
                maxTokens = prompt.maxTokens,
                temperature = prompt.temperature
            )

            isLoading.value = false

            if (response.success && response.rewrittenText != null) {
                undoText.value = textToRewrite

                val currentIc = latinIME.currentInputConnection ?: return@launch
                if (hasSelection) {
                    currentIc.commitText(response.rewrittenText, 1)
                } else {
                    currentIc.performContextMenuAction(android.R.id.selectAll)
                    currentIc.commitText(response.rewrittenText, 1)
                }
            } else {
                val errorMsg = when {
                    response.error?.contains("timeout", ignoreCase = true) == true ||
                    response.error?.contains("timed out", ignoreCase = true) == true ->
                        context.getString(R.string.llm_error_timeout)
                    response.error?.contains("connect", ignoreCase = true) == true ||
                    response.error?.contains("resolve", ignoreCase = true) == true ||
                    response.error?.contains("unreachable", ignoreCase = true) == true ->
                        context.getString(R.string.llm_error_connection)
                    else ->
                        context.getString(R.string.llm_error_generic, response.error ?: "Unknown")
                }
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun performUndo() {
        val originalText = undoText.value ?: return
        val latinIME = manager.getLatinIMEForDebug()
        val ic = latinIME.currentInputConnection ?: return

        ic.performContextMenuAction(android.R.id.selectAll)
        ic.commitText(originalText, 1)
        undoText.value = null
    }
}
