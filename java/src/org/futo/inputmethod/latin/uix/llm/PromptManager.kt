package org.futo.inputmethod.latin.uix.llm

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.setSettingBlocking
import java.util.UUID

@Serializable
data class LlmPrompt(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val systemPrompt: String,
    val userPromptTemplate: String,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1024,
    val sortOrder: Int = 0
)

val LLM_BACKEND_URL = SettingsKey(
    stringPreferencesKey("llm_backend_url"),
    "http://enterprise.arpa:5001"
)

val LLM_API_KEY = SettingsKey(
    stringPreferencesKey("llm_api_key"),
    ""
)

val LLM_DEFAULT_SYSTEM_PROMPT = SettingsKey(
    stringPreferencesKey("llm_default_system_prompt"),
    "You are a text rewriting assistant. You receive user text and a rewriting instruction. Output ONLY the rewritten text with no preamble, explanation, or markdown formatting."
)

val LLM_PROMPTS_JSON = SettingsKey(
    stringPreferencesKey("llm_prompts_json"),
    ""
)

private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

fun getDefaultPrompts(): List<LlmPrompt> = listOf(
    LlmPrompt(
        id = "default_fix_grammar",
        name = "Fix Grammar",
        systemPrompt = "You are a text editor. Fix grammar, spelling, and punctuation. Output only the corrected text.",
        userPromptTemplate = "Fix the grammar and spelling in this text:\n\n{{TEXT}}",
        sortOrder = 0
    ),
    LlmPrompt(
        id = "default_make_formal",
        name = "Make Formal",
        systemPrompt = "You are a professional writing assistant. Rewrite text in a formal, professional tone. Output only the rewritten text.",
        userPromptTemplate = "Rewrite this text in a formal, professional tone:\n\n{{TEXT}}",
        sortOrder = 1
    ),
    LlmPrompt(
        id = "default_make_casual",
        name = "Make Casual",
        systemPrompt = "You are a friendly writing assistant. Rewrite text in a casual, conversational tone. Output only the rewritten text.",
        userPromptTemplate = "Rewrite this text in a casual, friendly tone:\n\n{{TEXT}}",
        sortOrder = 2
    ),
    LlmPrompt(
        id = "default_make_concise",
        name = "Make Concise",
        systemPrompt = "You are an editor focused on brevity. Shorten the text while preserving meaning. Output only the shortened text.",
        userPromptTemplate = "Make this text more concise while preserving its meaning:\n\n{{TEXT}}",
        sortOrder = 3
    ),
    LlmPrompt(
        id = "default_expand",
        name = "Expand",
        systemPrompt = "You are a writing assistant. Elaborate on the text with more detail and explanation. Output only the expanded text.",
        userPromptTemplate = "Expand this text with more detail:\n\n{{TEXT}}",
        sortOrder = 4
    ),
    LlmPrompt(
        id = "default_translate_spanish",
        name = "Translate to Spanish",
        systemPrompt = "You are a translator. Translate the text to Spanish. Output only the translation.",
        userPromptTemplate = "Translate this text to Spanish:\n\n{{TEXT}}",
        sortOrder = 5
    ),
    LlmPrompt(
        id = "default_eli5",
        name = "ELI5",
        systemPrompt = "You are an explainer. Rewrite the text so a 5-year-old could understand it. Output only the simplified text.",
        userPromptTemplate = "Rewrite this text so it's simple enough for a 5-year-old to understand:\n\n{{TEXT}}",
        sortOrder = 6
    )
)

fun Context.loadPrompts(): List<LlmPrompt> {
    val stored = getSetting(LLM_PROMPTS_JSON.key, LLM_PROMPTS_JSON.default)
    if (stored.isBlank()) return getDefaultPrompts()
    return try {
        json.decodeFromString<List<LlmPrompt>>(stored)
    } catch (e: Exception) {
        getDefaultPrompts()
    }
}

fun Context.savePrompts(prompts: List<LlmPrompt>) {
    setSettingBlocking(LLM_PROMPTS_JSON.key, json.encodeToString(prompts))
}

fun Context.savePrompt(prompt: LlmPrompt) {
    val prompts = loadPrompts().toMutableList()
    val idx = prompts.indexOfFirst { it.id == prompt.id }
    if (idx >= 0) {
        prompts[idx] = prompt
    } else {
        prompts.add(prompt.copy(sortOrder = prompts.size))
    }
    savePrompts(prompts)
}

fun Context.deletePrompt(id: String) {
    val prompts = loadPrompts().toMutableList()
    prompts.removeAll { it.id == id }
    savePrompts(prompts)
}

fun Context.resetPromptsToDefaults() {
    savePrompts(getDefaultPrompts())
}
