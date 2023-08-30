package org.futo.voiceinput.shared

import org.futo.voiceinput.shared.types.ModelBuiltInAsset
import org.futo.voiceinput.shared.types.ModelDownloadable
import org.futo.voiceinput.shared.types.ModelLoader
import org.futo.voiceinput.shared.types.PromptingStyle


val ENGLISH_MODELS: List<ModelLoader> = listOf(
    ModelBuiltInAsset(
        name = R.string.tiny_en_name,
        promptingStyle = PromptingStyle.SingleLanguageOnly,

        encoderFile = "tiny-en-encoder-xatn.tflite",
        decoderFile = "tiny-en-decoder.tflite",
        vocabRawAsset = R.raw.tinyenvocab
    ),

    ModelDownloadable(
        name = R.string.base_en_name,
        promptingStyle = PromptingStyle.SingleLanguageOnly,

        encoderFile = "base.en-encoder-xatn.tflite",
        decoderFile = "base.en-decoder.tflite",
        vocabFile = "base.en-vocab.json"
    )
)

val MULTILINGUAL_MODELS: List<ModelLoader> = listOf(
    ModelDownloadable(
        name = R.string.tiny_name,
        promptingStyle = PromptingStyle.LanguageTokenAndAction,

        // The actual model is just the tiny model (non-en),
        // there is actually no Whisper model named tiny.multi
        encoderFile = "tiny-multi-encoder-xatn.tflite",
        decoderFile = "tiny-multi-decoder.tflite",
        vocabFile = "tiny-multi-vocab.json"
    ),
    ModelDownloadable(
        name = R.string.base_name,
        promptingStyle = PromptingStyle.LanguageTokenAndAction,

        encoderFile = "base-encoder-xatn.tflite",
        decoderFile = "base-decoder.tflite",
        vocabFile = "base-vocab.json"
    ),
    ModelDownloadable(
        name = R.string.small_name,
        promptingStyle = PromptingStyle.LanguageTokenAndAction,

        encoderFile = "small-encoder-xatn.tflite",
        decoderFile = "small-decoder.tflite",
        vocabFile = "small-vocab.json"
    ),
)