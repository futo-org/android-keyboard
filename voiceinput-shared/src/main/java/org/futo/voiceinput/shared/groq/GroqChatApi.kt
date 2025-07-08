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
        if (apiKey.isBlank()) {
            DebugLogger.log("Groq chat failed: API key is blank")
            return null
        }
        DebugLogger.log("Groq chat start: model=$model, apiKeyLength=${apiKey.length}, apiKeyPrefix=${apiKey.take(4)}...")
        return try {
            val req = ChatRequest(model, listOf(ChatMessage("system", systemPrompt), ChatMessage("user", userPrompt)))
            DebugLogger.log("Groq chat request body: ${json.encodeToString(req).take(200)}")
            val url = URL("https://api.groq.com/openai/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(json.encodeToString(req).toByteArray()) }
            DebugLogger.log("Groq chat sending request...")
            val responseCode = conn.responseCode
            DebugLogger.log("Groq chat response code: $responseCode")
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorResponse = try {
                    conn.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: "No error details"
                } catch (e: Exception) {
                    "Failed to read error: ${e.message}"
                }
                DebugLogger.log("Groq chat failed: code=$responseCode, error=$errorResponse")
                return null
            }
            val resp = conn.inputStream.readBytes().toString(Charsets.UTF_8)
            DebugLogger.log("Groq chat raw response length: ${resp.length}")
            DebugLogger.log("Groq chat raw response preview: ${resp.take(200)}")
            val parsed = json.decodeFromString<ChatResponse>(resp)
            val content = parsed.choices.firstOrNull()?.message?.content
            DebugLogger.log("Groq chat response content: ${content?.take(100) ?: "No content"}")
            content
        } catch (e: Exception) {
            DebugLogger.log("Groq chat error: ${e.javaClass.simpleName} - ${e.message ?: "Unknown error"}")
            e.printStackTrace()
            null
        } finally {
            DebugLogger.log("Groq chat request completed")
        }
    }

    fun test(apiKey: String): Boolean {
        if (apiKey.isBlank()) {
            DebugLogger.log("Groq chat test failed: API key is blank")
            return false
        }
        DebugLogger.log("Groq chat test start: apiKeyLength=${apiKey.length}, apiKeyPrefix=${apiKey.take(4)}...")
        return try {
            val url = URL("https://api.groq.com/openai/v1/models")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            DebugLogger.log("Groq chat test sending request...")
            conn.inputStream.use { it.readBytes() }
            val ok = conn.responseCode == HttpURLConnection.HTTP_OK
            DebugLogger.log("Groq chat test result: code=${conn.responseCode}, success=$ok")
            ok
        } catch (e: Exception) {
            DebugLogger.log("Groq chat test error: ${e.javaClass.simpleName} - ${e.message ?: "Unknown error"}")
            false
        }
    }

    fun availableModels(apiKey: String): List<String>? {
        if (apiKey.isBlank()) {
            DebugLogger.log("Groq models fetch failed: API key is blank")
            return null
        }
        DebugLogger.log("Groq models fetch start: apiKeyLength=${apiKey.length}, apiKeyPrefix=${apiKey.take(4)}...")
        return try {
            val url = URL("https://api.groq.com/openai/v1/models")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            DebugLogger.log("Groq models connecting...")
            val responseCode = conn.responseCode
            DebugLogger.log("Groq models response code: $responseCode")
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorResponse = try {
                    conn.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: "No error details"
                } catch (e: Exception) {
                    "Failed to read error: ${e.message}"
                }
                DebugLogger.log("Groq models failed: code=$responseCode, error=$errorResponse")
                return null
            }
            val resp = conn.inputStream.readBytes().toString(Charsets.UTF_8)
            DebugLogger.log("Groq models raw response length: ${resp.length}")
            DebugLogger.log("Groq models raw response preview: ${resp.take(200)}")
            val parsed = json.decodeFromString<ModelsResponse>(resp)
            val allModels = parsed.data.map { it.id }
            val chatModels = allModels.filter { modelId ->
                !modelId.lowercase().contains("whisper")
            }.distinct().sorted()
            DebugLogger.log("Groq models parsed successfully: ${allModels.size} total models, ${chatModels.size} chat models found")
            DebugLogger.log("All models: ${allModels.joinToString(", ")}")
            DebugLogger.log("Chat models: ${chatModels.joinToString(", ")}")
            return chatModels.ifEmpty {
                DebugLogger.log("No chat models found after filtering, returning all non-whisper models")
                allModels.filter { !it.lowercase().contains("whisper") }
            }
        } catch (e: Exception) {
            DebugLogger.log("Groq models error: ${e.javaClass.simpleName} - ${e.message ?: "Unknown error"}")
            e.printStackTrace()
            null
        }
    }
}