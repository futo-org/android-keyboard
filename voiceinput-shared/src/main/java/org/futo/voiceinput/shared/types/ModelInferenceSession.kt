package org.futo.voiceinput.shared.types

data class DecodedMetadata(
    val detectedLanguage: Language? // Some models do not support language decoding
)

interface ModelInferenceSession {
    suspend fun melToFeatures(mel: FloatArray)

    suspend fun decodeMetadata(): DecodedMetadata

    suspend fun decodeOutput(onPartialResult: (String) -> Unit): String
}