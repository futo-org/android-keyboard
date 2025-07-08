package org.futo.voiceinput.shared.groq

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.futo.voiceinput.shared.util.DebugLogger
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

object GroqWhisperApi {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class TranscriptionResponse(val text: String)
    @Serializable
    private data class ModelsResponse(val data: List<Model>)
    @Serializable
    data class Model(val id: String)

    private fun floatArrayToWav(samples: FloatArray): ByteArray {
        val pcm = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (f in samples) {
            val v = (f.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt()
            pcm.putShort(v.toShort())
        }
        val pcmData = pcm.array()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + pcmData.size)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(1)
        header.putInt(16000)
        header.putInt(16000 * 2)
        header.putShort(2)
        header.putShort(16)
        header.put("data".toByteArray())
        header.putInt(pcmData.size)
        return header.array() + pcmData
    }

    fun transcribe(samples: FloatArray, apiKey: String, model: String): String? {
        if(apiKey.isBlank()) return null
        return try {
            DebugLogger.log("Groq transcribe start model=$model, samples=${samples.size}")
            val wav = floatArrayToWav(samples)
            DebugLogger.log("Groq transcribe WAV size=${wav.size} bytes")
            
            val boundary = "----VoiceBoundary${System.currentTimeMillis()}"
            val url = URL("https://api.groq.com/openai/v1/audio/transcriptions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 30000  // Increased timeout for file upload
            conn.readTimeout = 30000     // Increased timeout for response
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            
            val out = DataOutputStream(conn.outputStream)
            fun writeString(s: String) = out.writeBytes(s)
            writeString("--$boundary\r\n")
            writeString("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n")
            writeString("Content-Type: audio/wav\r\n\r\n")
            out.write(wav)
            writeString("\r\n")
            writeString("--$boundary\r\n")
            writeString("Content-Disposition: form-data; name=\"model\"\r\n\r\n")
            writeString("$model\r\n")
            writeString("--$boundary--\r\n")
            out.flush()
            out.close()
            
            DebugLogger.log("Groq transcribe request sent, waiting for response...")
            val responseCode = conn.responseCode
            DebugLogger.log("Groq transcribe response code: $responseCode")
            
            if(responseCode != HttpURLConnection.HTTP_OK) {
                // Try to read error response
                val errorResponse = try {
                    conn.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: "No error details"
                } catch(e: Exception) {
                    "Failed to read error: ${e.message}"
                }
                DebugLogger.log("Groq transcribe failed code=$responseCode, error=$errorResponse")
                return null
            }
            
            val resp = conn.inputStream.readBytes().toString(Charsets.UTF_8)
            DebugLogger.log("Groq transcribe response length: ${resp.length}")
            DebugLogger.log("Groq transcribe response preview: ${resp.take(200)}")
            
            val parsed = json.decodeFromString<TranscriptionResponse>(resp)
            DebugLogger.log("Groq transcribe success: '${parsed.text.take(100)}...'")
            parsed.text
        } catch(e: Exception) {
            DebugLogger.log("Groq transcribe error: ${e.javaClass.simpleName} - ${e.message}")
            e.printStackTrace()
            null
        }
    }

    fun test(apiKey: String): Boolean {
        if(apiKey.isBlank()) return false
        return try {
            DebugLogger.log("Groq test start")
            val url = URL("https://api.groq.com/openai/v1/models")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 10000  // Increased timeout
            conn.readTimeout = 10000     // Increased timeout
            
            DebugLogger.log("Groq test connecting...")
            val responseCode = conn.responseCode
            DebugLogger.log("Groq test response code: $responseCode")
            
            if(responseCode != HttpURLConnection.HTTP_OK) {
                val errorResponse = try {
                    conn.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: "No error details"
                } catch(e: Exception) {
                    "Failed to read error: ${e.message}"
                }
                DebugLogger.log("Groq test failed: $errorResponse")
            }
            
            conn.inputStream.use { it.readBytes() }
            val ok = responseCode == HttpURLConnection.HTTP_OK
            DebugLogger.log("Groq test result: $ok")
            ok
        } catch(e: Exception) {
            DebugLogger.log("Groq test error: ${e.javaClass.simpleName} - ${e.message}")
            e.printStackTrace()
            false
        }
    }
        
    fun availableModels(apiKey: String, maxRetries: Int = 3): List<String>? {
        if (apiKey.isBlank()) {
            DebugLogger.log("Groq Whisper models fetch failed: API key is blank")
            return null
        }
        return try {
            DebugLogger.log("Groq Whisper models fetch start")
            val url = URL("https://api.groq.com/openai/v1/models")
            var attempt = 0
            while (attempt < maxRetries) {
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                DebugLogger.log("Groq Whisper models connecting... attempt ${attempt + 1}")
                val responseCode = conn.responseCode
                DebugLogger.log("Groq Whisper models response code: $responseCode")
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val resp = conn.inputStream.readBytes().toString(Charsets.UTF_8)
                    DebugLogger.log("Groq Whisper models raw response length: ${resp.length}")
                    DebugLogger.log("Groq Whisper models raw response preview: ${resp.take(200)}")
                    val parsed = json.decodeFromString<ModelsResponse>(resp)
                    val allModels = parsed.data.map { it.id }
                    val whisperModels = allModels.filter { it.lowercase().contains("whisper") }
                    DebugLogger.log("All models: ${allModels.joinToString(", ")}")
                    DebugLogger.log("Whisper models: ${whisperModels.joinToString(", ")}")
                    return if (whisperModels.isEmpty()) {
                        DebugLogger.log("No whisper models found, returning all models")
                        allModels.ifEmpty { listOf("whisper-large-v3") } // Fallback
                    } else {
                        whisperModels
                    }
                } else if (responseCode == 429) {
                    attempt++
                    if (attempt < maxRetries) {
                        Thread.sleep(1000L shl attempt) // Exponential backoff
                        continue
                    }
                    val errorResponse = conn.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: "No error details"
                    DebugLogger.log("Groq Whisper models failed code=$responseCode, error=$errorResponse")
                    return null
                } else {
                    val errorResponse = conn.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: "No error details"
                    DebugLogger.log("Groq Whisper models failed code=$responseCode, error=$errorResponse")
                    return null
                }
            }
            return null
        } catch (e: Exception) {
            DebugLogger.log("Groq Whisper models error: ${e.javaClass.simpleName} - ${e.message}")
            e.printStackTrace()
            null
        }
    }
}