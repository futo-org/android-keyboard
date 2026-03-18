package org.futo.inputmethod.latin.uix.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class LlmApiClient(
    private val baseUrl: String,
    private val apiKey: String? = null,
    private val timeoutSeconds: Long = 30
) {
    @Serializable
    data class ChatMessage(val role: String, val content: String)

    @Serializable
    data class ChatRequest(
        val messages: List<ChatMessage>,
        val max_tokens: Int = 1024,
        val temperature: Float = 0.7f,
        val stream: Boolean = false
    )

    data class LlmResponse(
        val success: Boolean,
        val rewrittenText: String?,
        val error: String?
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .build()

    suspend fun rewriteText(
        systemPrompt: String,
        userPrompt: String,
        inputText: String,
        maxTokens: Int = 1024,
        temperature: Float = 0.7f
    ): LlmResponse = withContext(Dispatchers.IO) {
        try {
            val messages = listOf(
                ChatMessage("system", systemPrompt),
                ChatMessage("user", userPrompt.replace("{{TEXT}}", inputText))
            )

            val requestBody = json.encodeToString(
                ChatRequest(
                    messages = messages,
                    max_tokens = maxTokens,
                    temperature = temperature
                )
            )

            val url = "${baseUrl.trimEnd('/')}/v1/chat/completions"
            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestBody.toRequestBody("application/json".toMediaType()))

            apiKey?.let { key ->
                if (key.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $key")
                }
            }

            val response = client.newCall(requestBuilder.build()).execute()
            val body = response.body?.string()

            if (!response.isSuccessful) {
                return@withContext LlmResponse(
                    success = false,
                    rewrittenText = null,
                    error = "HTTP ${response.code}: ${body?.take(200) ?: "No response body"}"
                )
            }

            if (body == null) {
                return@withContext LlmResponse(
                    success = false,
                    rewrittenText = null,
                    error = "Empty response body"
                )
            }

            val jsonResponse = json.parseToJsonElement(body).jsonObject
            val content = jsonResponse["choices"]
                ?.jsonArray?.get(0)
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content

            if (content != null) {
                LlmResponse(success = true, rewrittenText = content, error = null)
            } else {
                LlmResponse(
                    success = false,
                    rewrittenText = null,
                    error = "Could not parse response"
                )
            }
        } catch (e: IOException) {
            LlmResponse(success = false, rewrittenText = null, error = e.message ?: "Network error")
        } catch (e: Exception) {
            LlmResponse(success = false, rewrittenText = null, error = e.message ?: "Unknown error")
        }
    }

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/v1/models"
            val requestBuilder = Request.Builder().url(url).get()

            apiKey?.let { key ->
                if (key.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $key")
                }
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                Result.success(response.body?.string() ?: "OK")
            } else {
                Result.failure(IOException("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
