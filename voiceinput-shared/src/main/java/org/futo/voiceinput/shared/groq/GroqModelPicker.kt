package org.futo.voiceinput.shared.groq

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private val client = OkHttpClient()

/** Returns a model that definitely exists. */
fun pickGroqModel(apiKey: String, preferredId: String = "llama3-70b-8192"): String {
    val req = Request.Builder()
        .url("https://api.groq.com/openai/v1/models")
        .addHeader("Authorization", "Bearer $apiKey")
        .build()

    client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return "llama-3.1-8b-instant"

        val data = JSONObject(resp.body!!.string()).getJSONArray("data")
        val ids = (0 until data.length()).map { data.getJSONObject(it).getString("id") }.toSet()

        return when {
            preferredId in ids -> preferredId
            "llama-3.3-70b-versatile" in ids -> "llama-3.3-70b-versatile"
            "llama-3.1-8b-instant" in ids -> "llama-3.1-8b-instant"
            else -> ids.first()
        }
    }
}

fun stream(apiKey: String, prompt: String, preferredId: String? = null, onToken: (String) -> Unit) {
    val model = pickGroqModel(apiKey, preferredId ?: "llama3-70b-8192")
    val reqBody = """
      {
        "model":"$model",
        "stream":true,
        "messages":[{"role":"user","content":"$prompt"}]
      }
    """.trimIndent()

    val request = Request.Builder()
        .url("https://api.groq.com/openai/v1/chat/completions")
        .addHeader("Authorization", "Bearer $apiKey")
        .addHeader("Accept", "text/event-stream")
        .post(reqBody.toRequestBody("application/json".toMediaType()))
        .build()

    client.newCall(request).execute().body!!.source().use { src ->
        while (!src.exhausted()) {
            val line = src.readUtf8Line() ?: continue
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload == "[DONE]") break

            val token = JSONObject(payload)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("delta")
                .optString("content")

            if (token.isNotEmpty()) onToken(token)
        }
    }
}
