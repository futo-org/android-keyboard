package org.futo.voiceinput.shared.groq

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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

    fun transcribe(samples: FloatArray, apiKey: String): String? {
        if(apiKey.isBlank()) return null
        return try {
            val wav = floatArrayToWav(samples)
            val boundary = "----VoiceBoundary${System.currentTimeMillis()}"
            val url = URL("https://api.groq.com/openai/v1/audio/transcriptions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
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
            writeString("whisper-large-v3\r\n")
            writeString("--$boundary--\r\n")
            out.flush()
            out.close()
            if(conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val resp = conn.inputStream.readBytes().toString(Charsets.UTF_8)
            val parsed = json.decodeFromString(TranscriptionResponse.serializer(), resp)
            parsed.text
        } catch(_: Exception) {
            null
        }
    }

    fun test(apiKey: String): Boolean {
        if(apiKey.isBlank()) return false
        return try {
            val url = URL("https://api.groq.com/openai/v1/models")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.inputStream.use { it.readBytes() }
            conn.responseCode == HttpURLConnection.HTTP_OK
        } catch(_: Exception) {
            false
        }
    }
}
