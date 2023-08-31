package org.futo.voiceinput.shared.types

import android.content.Context
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import org.futo.voiceinput.shared.whisper.DecoderModel
import org.futo.voiceinput.shared.whisper.EncoderModel
import org.futo.voiceinput.shared.whisper.Tokenizer
import org.tensorflow.lite.support.model.Model
import java.io.File
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class EncoderDecoder(
    val encoder: EncoderModel,
    val decoder: DecoderModel
)

enum class PromptingStyle {
    // <|startoftranscript|><|notimestamps|> Text goes here.<|endoftext|>
    SingleLanguageOnly,

    // <|startoftranscript|><|en|><|transcribe|><|notimestamps|> Text goes here.<|endoftext|>
    LanguageTokenAndAction,
}

// Maybe add `val languages: Set<Language>`
interface ModelLoader {
    @get:StringRes
    val name: Int
    val promptingStyle: PromptingStyle

    fun exists(context: Context): Boolean
    fun getRequiredDownloadList(context: Context): List<String>

    fun loadEncoder(context: Context, options: Model.Options): EncoderModel
    fun loadDecoder(context: Context, options: Model.Options): DecoderModel
    fun loadTokenizer(context: Context): Tokenizer

    fun loadEncoderDecoder(context: Context, options: Model.Options): EncoderDecoder {
        return EncoderDecoder(
            encoder = loadEncoder(context, options),
            decoder = loadDecoder(context, options),
        )
    }
}

internal class ModelBuiltInAsset(
    override val name: Int,
    override val promptingStyle: PromptingStyle,

    val encoderFile: String,
    val decoderFile: String,
    @RawRes val vocabRawAsset: Int
) : ModelLoader {
    override fun exists(context: Context): Boolean {
        return true
    }

    override fun getRequiredDownloadList(context: Context): List<String> {
        return listOf()
    }

    override fun loadEncoder(context: Context, options: Model.Options): EncoderModel {
        return EncoderModel.loadFromAssets(context, encoderFile, options)
    }

    override fun loadDecoder(context: Context, options: Model.Options): DecoderModel {
        return DecoderModel.loadFromAssets(context, decoderFile, options)
    }

    override fun loadTokenizer(context: Context): Tokenizer {
        return Tokenizer(context, vocabRawAsset)
    }
}

@Throws(IOException::class)
private fun Context.tryOpenDownloadedModel(pathStr: String): MappedByteBuffer {
    val fis = File(this.filesDir, pathStr).inputStream()
    val channel = fis.channel

    return channel.map(
        FileChannel.MapMode.READ_ONLY,
        0, channel.size()
    ).load()
}

internal class ModelDownloadable(
    override val name: Int,
    override val promptingStyle: PromptingStyle,

    val encoderFile: String,
    val decoderFile: String,
    val vocabFile: String
) : ModelLoader {
    override fun exists(context: Context): Boolean {
        return getRequiredDownloadList(context).isEmpty()
    }

    override fun getRequiredDownloadList(context: Context): List<String> {
        return listOf(encoderFile, decoderFile, vocabFile).filter {
            !File(context.filesDir, it).exists()
        }
    }

    override fun loadEncoder(context: Context, options: Model.Options): EncoderModel {
        return EncoderModel.loadFromMappedBuffer(
            context.tryOpenDownloadedModel(encoderFile),
            options
        )
    }

    override fun loadDecoder(context: Context, options: Model.Options): DecoderModel {
        return DecoderModel.loadFromMappedBuffer(
            context.tryOpenDownloadedModel(decoderFile),
            options
        )
    }

    override fun loadTokenizer(context: Context): Tokenizer {
        return Tokenizer(
            File(context.filesDir, vocabFile)
        )
    }
}
