package org.futo.voiceinput.shared.ggml

import androidx.annotation.Keep
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import java.nio.Buffer

@OptIn(DelicateCoroutinesApi::class)
val inferenceContext = newSingleThreadContext("whisper-ggml-inference")

enum class DecodingMode(val value: Int) {
    Greedy(0),
    BeamSearch5(5)
}

class BailLanguageException(val language: String): Exception()
class InferenceCancelledException : Exception()

@Keep
class WhisperGGML(
    modelBuffer: Buffer
) {
    private var handle: Long = 0L
    init {
        handle = openFromBufferNative(modelBuffer)

        if(handle == 0L) {
            throw IllegalArgumentException("The Whisper model could not be loaded from the given buffer")
        }
    }

    private var partialResultCallback: (String) -> Unit = { }

    @Keep
    private fun invokePartialResult(text: String) {
        partialResultCallback(text.trim())
    }

    // empty languages = autodetect any language
    // 1 language = will force that language
    // 2 or more languages = autodetect between those languages
    @Throws(BailLanguageException::class, InferenceCancelledException::class)
    suspend fun infer(
        samples: FloatArray,
        prompt: String,
        languages: Array<String>,
        bailLanguages: Array<String>,
        decodingMode: DecodingMode,
        suppressNonSpeechTokens: Boolean,
        partialResultCallback: (String) -> Unit
    ): String = withContext(inferenceContext) {
        if(handle == 0L) {
            throw IllegalStateException("WhisperGGML has already been closed, cannot infer")
        }
        this@WhisperGGML.partialResultCallback = partialResultCallback

        val result = inferNative(handle, samples, prompt, languages, bailLanguages, decodingMode.value, suppressNonSpeechTokens).trim()

        if(result.contains("<>CANCELLED<>")) {
            if(result.contains("flag")) {
                throw InferenceCancelledException()
            } else if(result.contains("lang=")) {
                val language = result.split("lang=")[1]
                throw BailLanguageException(language)
            } else {
                throw IllegalStateException("Cancelled for unknown reason")
            }

        } else {
            return@withContext result
        }
    }

    fun cancel() {
        if(handle == 0L) return
        cancelNative(handle)
    }

    suspend fun close() = withContext(inferenceContext) {
        if(handle != 0L) {
            closeNative(handle)
        }
        handle = 0L
    }

    private external fun openNative(path: String): Long
    private external fun openFromBufferNative(buffer: Buffer): Long
    private external fun inferNative(handle: Long, samples: FloatArray, prompt: String, languages: Array<String>, bailLanguages: Array<String>, decodingMode: Int, suppressNonSpeechTokens: Boolean): String
    private external fun cancelNative(handle: Long)
    private external fun closeNative(handle: Long)
}