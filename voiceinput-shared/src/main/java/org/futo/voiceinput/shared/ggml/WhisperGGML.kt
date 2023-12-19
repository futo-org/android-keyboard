package org.futo.voiceinput.shared.ggml

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import java.nio.Buffer

@OptIn(DelicateCoroutinesApi::class)
val inferenceContext = newSingleThreadContext("whisper-ggml-inference")

class WhisperGGML(
    buffer: Buffer
) {
    private var handle: Long = 0L
    init {
        handle = openFromBufferNative(buffer)

        if(handle == 0L) {
            throw IllegalArgumentException("The Whisper model could not be loaded from the given buffer")
        }
    }

    suspend fun infer(samples: FloatArray): String = withContext(inferenceContext) {
        return@withContext inferNative(handle, samples, "")
    }

    external fun openNative(path: String): Long
    external fun openFromBufferNative(buffer: Buffer): Long
    external fun inferNative(handle: Long, samples: FloatArray, prompt: String): String
    external fun closeNative(handle: Long)
}