package org.futo.voiceinput.shared.groq

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.futo.voiceinput.shared.util.DebugLogger
import java.net.HttpURLConnection
import java.net.URL

object GroqChatApi {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ModelsResponse(val data: List<Model>)
    @Serializable
    private data class Model(val id: String)

    @Serializable
    private data class ChatMessage(val role: String, val content: String)
    @Serializable
    private data class ChatRequest(val model: String, val messages: List<ChatMessage>)
    @Serializable
    private data class ChatChoice(val message: ChatMessage)
    @Serializable
    private data class ChatResponse(val choices: List<ChatChoice>)

    fun chat(systemPrompt: String, userPrompt: String, apiKey: String, model: String): String? {
        if(apiKey.isBlank()) return null
        return try {
            DebugLogger.log("Groq chat start model=$model")
            val req = ChatRequest(model, listOf(ChatMessage("system", systemPrompt), ChatMessage("user", userPrompt)))
            val url = URL("https://api.groq.com/openai/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(json.encodeToString(req).toByteArray()) }
            if(conn.responseCode != HttpURLConnection.HTTP_OK) {
                DebugLogger.log("Groq chat failed code=${conn.responseCode}")
                return null
            }
            val resp = conn.inputStream.readBytes().toString(Charsets.UTF_8)
            val parsed = json.decodeFromString<ChatResponse>(resp)
            parsed.choices.firstOrNull()?.message?.content
        } catch(e: Exception) {
            DebugLogger.log("Groq chat error: ${e.message}")
            null
        }
    }

    fun test(apiKey: String): Boolean {
        if(apiKey.isBlank()) return false
        return try {
            DebugLogger.log("Groq chat test start")
            val url = URL("https://api.groq.com/openai/v1/models")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.inputStream.use { it.readBytes() }
            val ok = conn.responseCode == HttpURLConnection.HTTP_OK
            DebugLogger.log("Groq chat test result code=${conn.responseCode}")
            ok
        } catch(e: Exception) {
            DebugLogger.log("Groq chat test error: ${e.message}")
            false
        }
    }

    fun availableModels(apiKey: String): List<String>? {
        if(apiKey.isBlank()) return null
        return try {
            DebugLogger.log("Groq chat models fetch")
            val url = URL("https://api.groq.com/openai/v1/models")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val resp = conn.inputStream.readBytes().toString(Charsets.UTF_8)
            if(conn.responseCode != HttpURLConnection.HTTP_OK) {
                DebugLogger.log("Groq chat models failed code=${conn.responseCode}")
                return null
            }
            val parsed = json.decodeFromString<ModelsResponse>(resp)
            parsed.data.map { it.id }.filter { !it.startsWith("whisper-") }
        } catch(e: Exception) {
            DebugLogger.log("Groq chat models error: ${e.message}")
            null
        }
    }
}
