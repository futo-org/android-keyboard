package org.futo.inputmethod.latin.uix.llm.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.llm.LLM_API_KEY
import org.futo.inputmethod.latin.uix.llm.LLM_BACKEND_URL
import org.futo.inputmethod.latin.uix.llm.LLM_DEFAULT_SYSTEM_PROMPT
import org.futo.inputmethod.latin.uix.llm.LlmApiClient
import org.futo.inputmethod.latin.uix.llm.LlmPrompt
import org.futo.inputmethod.latin.uix.llm.deletePrompt
import org.futo.inputmethod.latin.uix.llm.loadPrompts
import org.futo.inputmethod.latin.uix.llm.resetPromptsToDefaults
import org.futo.inputmethod.latin.uix.llm.savePrompt
import org.futo.inputmethod.latin.uix.llm.savePrompts
import org.futo.inputmethod.latin.uix.LocalNavController
import org.futo.inputmethod.latin.uix.settings.BottomSpacer
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.useDataStore
import java.util.UUID

@Composable
fun LlmSettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val backendUrl = useDataStore(LLM_BACKEND_URL)
    val apiKey = useDataStore(LLM_API_KEY)
    val systemPrompt = useDataStore(LLM_DEFAULT_SYSTEM_PROMPT)

    var testingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    ScrollableList {
        ScreenTitle(stringResource(R.string.llm_settings_title), showBack = true)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                stringResource(R.string.llm_settings_network_note),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = backendUrl.value,
            onValueChange = { backendUrl.setValue(it) },
            label = { Text(stringResource(R.string.llm_settings_backend_url)) },
            placeholder = { Text(stringResource(R.string.llm_settings_backend_url_subtitle)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        OutlinedTextField(
            value = apiKey.value,
            onValueChange = { apiKey.setValue(it) },
            label = { Text(stringResource(R.string.llm_settings_api_key)) },
            placeholder = { Text(stringResource(R.string.llm_settings_api_key_subtitle)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    testingConnection = true
                    testResult = null
                    val client = LlmApiClient(
                        backendUrl.value,
                        apiKey.value.ifBlank { null }
                    )
                    scope.launch {
                        val result = client.testConnection()
                        testingConnection = false
                        testResult = if (result.isSuccess) {
                            context.getString(R.string.llm_settings_connection_success)
                        } else {
                            context.getString(
                                R.string.llm_settings_connection_failed,
                                result.exceptionOrNull()?.message ?: "Unknown error"
                            )
                        }
                    }
                },
                enabled = !testingConnection
            ) {
                if (testingConnection) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.llm_settings_testing_connection))
                } else {
                    Text(stringResource(R.string.llm_settings_test_connection))
                }
            }
        }

        testResult?.let { result ->
            Text(
                result,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = if (result.contains("success", ignoreCase = true))
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = systemPrompt.value,
            onValueChange = { systemPrompt.setValue(it) },
            label = { Text(stringResource(R.string.llm_settings_default_system_prompt)) },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        val navController = LocalNavController.current
        NavigationItem(
            title = stringResource(R.string.llm_settings_manage_prompts),
            subtitle = stringResource(R.string.llm_settings_manage_prompts_subtitle),
            style = NavigationItemStyle.HomePrimary,
            icon = painterResource(R.drawable.sparkle),
            navigate = { navController?.navigate("llmPrompts") }
        )

        BottomSpacer()
    }
}

@Composable
fun PromptManagerScreen() {
    val context = LocalContext.current
    var prompts by remember { mutableStateOf(context.loadPrompts()) }
    var editingPrompt by remember { mutableStateOf<LlmPrompt?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    if (editingPrompt != null) {
        PromptEditorDialog(
            prompt = editingPrompt!!,
            onSave = { saved ->
                context.savePrompt(saved)
                prompts = context.loadPrompts()
                editingPrompt = null
            },
            onDelete = if (editingPrompt!!.id.startsWith("new_")) null else { {
                context.deletePrompt(editingPrompt!!.id)
                prompts = context.loadPrompts()
                editingPrompt = null
            } },
            onDismiss = { editingPrompt = null }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.llm_settings_reset_prompts)) },
            confirmButton = {
                TextButton(onClick = {
                    context.resetPromptsToDefaults()
                    prompts = context.loadPrompts()
                    showResetDialog = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.llm_cancel))
                }
            }
        )
    }

    Column {
        ScreenTitle(stringResource(R.string.llm_settings_manage_prompts), showBack = true)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Button(onClick = {
                editingPrompt = LlmPrompt(
                    id = "new_${UUID.randomUUID()}",
                    name = "",
                    systemPrompt = "",
                    userPromptTemplate = "{{TEXT}}"
                )
            }) {
                Text(stringResource(R.string.llm_prompt_editor_add))
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = { showResetDialog = true }) {
                Text(stringResource(R.string.llm_settings_reset_prompts))
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(prompts) { _, prompt ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 3.dp)
                        .clickable { editingPrompt = prompt },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                prompt.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                prompt.userPromptTemplate.take(60),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = {
                            context.deletePrompt(prompt.id)
                            prompts = context.loadPrompts()
                        }) {
                            Icon(
                                painterResource(R.drawable.delete),
                                contentDescription = stringResource(R.string.llm_prompt_editor_delete)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PromptEditorDialog(
    prompt: LlmPrompt,
    onSave: (LlmPrompt) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(prompt.name) }
    var systemPrompt by remember { mutableStateOf(prompt.systemPrompt) }
    var userTemplate by remember { mutableStateOf(prompt.userPromptTemplate) }
    var temperature by remember { mutableStateOf(prompt.temperature) }
    var maxTokens by remember { mutableStateOf(prompt.maxTokens.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.llm_prompt_editor_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.llm_prompt_editor_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text(stringResource(R.string.llm_prompt_editor_system_prompt)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = userTemplate,
                    onValueChange = { userTemplate = it },
                    label = { Text(stringResource(R.string.llm_prompt_editor_user_template)) },
                    placeholder = { Text(stringResource(R.string.llm_prompt_editor_user_template_hint)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${stringResource(R.string.llm_settings_temperature)}: ${"%.1f".format(temperature)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0f..2f,
                    steps = 19
                )
                OutlinedTextField(
                    value = maxTokens,
                    onValueChange = { maxTokens = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.llm_settings_max_tokens)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            prompt.copy(
                                name = name,
                                systemPrompt = systemPrompt,
                                userPromptTemplate = userTemplate,
                                temperature = temperature,
                                maxTokens = maxTokens.toIntOrNull() ?: 1024
                            )
                        )
                    }
                }
            ) {
                Text(stringResource(R.string.llm_prompt_editor_save))
            }
        },
        dismissButton = {
            Row {
                onDelete?.let {
                    TextButton(onClick = it) {
                        Text(
                            stringResource(R.string.llm_prompt_editor_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.llm_cancel))
                }
            }
        }
    )
}
