package org.futo.voiceinput.shared.ml

import android.content.Context
import android.os.Build
import kotlinx.coroutines.yield
import org.futo.voiceinput.shared.AudioFeatureExtraction
import org.futo.voiceinput.shared.ModelData
import org.futo.voiceinput.shared.toDoubleArray
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.model.Model
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


@Throws(IOException::class)
private fun Context.tryOpenDownloadedModel(pathStr: String): MappedByteBuffer {
    val fis = File(this.filesDir, pathStr).inputStream()
    val channel = fis.channel

    return channel.map(
        FileChannel.MapMode.READ_ONLY,
        0, channel.size()
    ).load()
}

enum class RunState {
    ExtractingFeatures,
    ProcessingEncoder,
    StartedDecoding,
    SwitchingModel
}

data class LoadedModels(
    val encoderModel: WhisperEncoderXatn,
    val decoderModel: WhisperDecoder,
    val tokenizer: WhisperTokenizer
)

fun initModelsWithOptions(context: Context, model: ModelData, encoderOptions: Model.Options, decoderOptions: Model.Options): LoadedModels {
    return if(model.is_builtin_asset) {
        val encoderModel = WhisperEncoderXatn(context, model.encoder_xatn_file, encoderOptions)
        val decoderModel = WhisperDecoder(context, model.decoder_file, decoderOptions)
        val tokenizer = WhisperTokenizer(context, model.vocab_raw_asset!!)

        LoadedModels(encoderModel, decoderModel, tokenizer)
    } else {
        val encoderModel = WhisperEncoderXatn(context.tryOpenDownloadedModel(model.encoder_xatn_file), encoderOptions)
        val decoderModel = WhisperDecoder(context.tryOpenDownloadedModel(model.decoder_file), decoderOptions)
        val tokenizer = WhisperTokenizer(File(context.filesDir, model.vocab_file))

        LoadedModels(encoderModel, decoderModel, tokenizer)
    }
}

class DecodingEnglishException : Throwable()


class WhisperModel(context: Context, model: ModelData, private val suppressNonSpeech: Boolean, languages: Set<String>? = null) {
    private val encoderModel: WhisperEncoderXatn
    private val decoderModel: WhisperDecoder
    private val tokenizer: WhisperTokenizer

    private val bannedTokens: IntArray
    private val decodeStartToken: Int
    private val decodeEndToken: Int
    private val translateToken: Int
    private val noCaptionsToken: Int

    private val startOfLanguages: Int
    private val englishLanguage: Int
    private val endOfLanguages: Int

    companion object {
        val extractor = AudioFeatureExtraction(
            chunkLength = 30,
            featureSize = 80,
            hopLength = 160,
            nFFT = 400,
            paddingValue = 0.0,
            samplingRate = 16000
        )

        private val emptyResults: Set<String>
        init {
            val emptyResults = mutableListOf(
                "you",
                "(bell dings)",
                "(blank audio)",
                "(beep)",
                "(bell)",
                "(music)",
                "(music playing)"
            )

            emptyResults += emptyResults.map { it.replace("(", "[").replace(")", "]") }
            emptyResults += emptyResults.map { it.replace(" ", "_") }

            Companion.emptyResults = emptyResults.toHashSet()
        }
    }

    init {
        val cpuOption = Model.Options.Builder().setDevice(Model.Device.CPU).build()

        val nnApiOption = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Model.Options.Builder().setDevice(Model.Device.NNAPI).build()
        } else {
            cpuOption
        }

        val (encoderModel, decoderModel, tokenizer) = try {
            initModelsWithOptions(context, model, nnApiOption, cpuOption)
        } catch (e: Exception) {
            e.printStackTrace()
            initModelsWithOptions(context, model, cpuOption, cpuOption)
        }

        this.encoderModel = encoderModel
        this.decoderModel = decoderModel
        this.tokenizer = tokenizer


        decodeStartToken = stringToToken("<|startoftranscript|>")!!
        decodeEndToken = stringToToken("<|endoftext|>")!!
        translateToken = stringToToken("<|translate|>")!!
        noCaptionsToken = stringToToken("<|nocaptions|>")!!

        startOfLanguages = stringToToken("<|en|>")!!
        englishLanguage = stringToToken("<|en|>")!!
        endOfLanguages = stringToToken("<|su|>")!!

        // Based on https://github.com/openai/whisper/blob/248b6cb124225dd263bb9bd32d060b6517e067f8/whisper/tokenizer.py#L236
        val symbols = "#()*+/:;<=>@[\\]^_`{|}~「」『』".chunked(1) + listOf("<<", ">>", "<<<", ">>>", "--", "---", "-(", "-[", "('", "(\"", "((", "))", "(((", ")))", "[[", "]]", "{{", "}}", "♪♪", "♪♪♪")

        val symbolsWithSpace = symbols.map { " $it" } + listOf(" -", " '")

        val miscellaneous = "♩♪♫♬♭♮♯".toSet()

        val isBannedChar = { token: String ->
            if(suppressNonSpeech) {
                val normalizedToken = makeStringUnicode(token)
                symbols.contains(normalizedToken) || symbolsWithSpace.contains(normalizedToken)
                        || normalizedToken.toSet().intersect(miscellaneous).isNotEmpty()
            } else {
                false
            }
        }

        var bannedTokens = tokenizer.tokenToId.filterKeys { isBannedChar(it) }.values.toIntArray()
        bannedTokens += listOf(translateToken, noCaptionsToken)

        if(languages != null) {
            val permittedLanguages = languages.map {
                stringToToken("<|$it|>")!!
            }.toHashSet()

            // Ban other languages
            bannedTokens += tokenizer.tokenToId.filterValues {
                (it >= startOfLanguages) && (it <= endOfLanguages) && (!permittedLanguages.contains(it))
            }.values.toIntArray()
        }

        this.bannedTokens = bannedTokens
    }

    private fun stringToToken(string: String): Int? {
        return tokenizer.stringToToken(string)
    }

    private fun tokenToString(token: Int): String? {
        return tokenizer.tokenToString(token)
    }

    private fun makeStringUnicode(string: String): String {
        return tokenizer.makeStringUnicode(string).trim()
    }

    private fun runEncoderAndGetXatn(audioFeatures: TensorBuffer): TensorBuffer {
        return encoderModel.process(audioFeatures).crossAttention
    }

    private fun runDecoder(
        xAtn: TensorBuffer,
        seqLen: TensorBuffer,
        cache: TensorBuffer,
        inputId: TensorBuffer
    ): WhisperDecoder.Outputs {
        return decoderModel.process(crossAttention = xAtn, seqLen = seqLen, cache = cache, inputIds = inputId)
    }

    private val audioFeatures = TensorBuffer.createFixedSize(intArrayOf(1, 80, 3000), DataType.FLOAT32)
    private val seqLenTensor = TensorBuffer.createFixedSize(intArrayOf(1), DataType.FLOAT32)
    private val cacheTensor = TensorBuffer.createFixedSize(decoderModel.getCacheTensorShape(), DataType.FLOAT32)
    private val inputIdTensor = TensorBuffer.createFixedSize(intArrayOf(1, 1), DataType.FLOAT32)

    init {
        val shape = cacheTensor.shape
        val size = shape[0] * shape[1] * shape[2] * shape[3]
        cacheTensor.loadArray(FloatArray(size) { 0f } )
    }

    suspend fun run(
        mel: FloatArray,
        onStatusUpdate: (RunState) -> Unit,
        onPartialDecode: (String) -> Unit,
        bailOnEnglish: Boolean
    ): String {
        onStatusUpdate(RunState.ProcessingEncoder)

        audioFeatures.loadArray(mel)

        yield()
        val xAtn = runEncoderAndGetXatn(audioFeatures)

        onStatusUpdate(RunState.StartedDecoding)

        val seqLenArray = FloatArray(1)
        val inputIdsArray = FloatArray(1)

        var fullString = ""
        var previousToken = decodeStartToken
        for (seqLen in 0 until 256) {
            yield()

            seqLenArray[0] = seqLen.toFloat()
            inputIdsArray[0] = previousToken.toFloat()

            seqLenTensor.loadArray(seqLenArray)
            inputIdTensor.loadArray(inputIdsArray)

            val decoderOutputs = runDecoder(xAtn, seqLenTensor, cacheTensor, inputIdTensor)
            cacheTensor.loadBuffer(decoderOutputs.nextCache.buffer.duplicate())

            val logits = decoderOutputs.logits.floatArray

            for(i in bannedTokens) logits[i] -= 1024.0f

            val selectedToken = logits.withIndex().maxByOrNull { it.value }?.index!!
            if(selectedToken == decodeEndToken) break

            val tokenAsString = tokenToString(selectedToken) ?: break

            if((selectedToken >= startOfLanguages) && (selectedToken <= endOfLanguages)){
                println("Language detected: $tokenAsString")
                if((selectedToken == englishLanguage) && bailOnEnglish) {
                    onStatusUpdate(RunState.SwitchingModel)
                    throw DecodingEnglishException()
                }
            }

            fullString += tokenAsString.run {
                if (this.startsWith("<|")) {
                    ""
                } else {
                    this
                }
            }

            previousToken = selectedToken

            yield()
            if(fullString.isNotEmpty())
                onPartialDecode(makeStringUnicode(fullString))
        }


        val fullStringNormalized = makeStringUnicode(fullString).lowercase().trim()

        if(emptyResults.contains(fullStringNormalized)) {
            fullString = ""
        }

        return makeStringUnicode(fullString)
    }

    fun close() {
        encoderModel.close()
        decoderModel.close()
    }

    protected fun finalize() {
        close()
    }
}


class WhisperModelWrapper(
    val context: Context,
    val primaryModel: ModelData,
    val fallbackEnglishModel: ModelData?,
    val suppressNonSpeech: Boolean,
    val languages: Set<String>? = null
) {
    private val primary: WhisperModel = WhisperModel(context, primaryModel, suppressNonSpeech, languages)
    private val fallback: WhisperModel? = fallbackEnglishModel?.let { WhisperModel(context, it, suppressNonSpeech) }

    init {
        if(primaryModel == fallbackEnglishModel) {
            throw IllegalArgumentException("Fallback model must be unique from the primary model")
        }
    }

    suspend fun run(
        samples: FloatArray,
        onStatusUpdate: (RunState) -> Unit,
        onPartialDecode: (String) -> Unit
    ): String {
        onStatusUpdate(RunState.ExtractingFeatures)
        val mel = WhisperModel.extractor.melSpectrogram(samples.toDoubleArray())

        return try {
            primary.run(mel, onStatusUpdate, onPartialDecode, fallback != null)
        } catch(e: DecodingEnglishException) {
            fallback!!.run(
                mel,
                {
                    if(it != RunState.ProcessingEncoder) {
                        onStatusUpdate(it)
                    }
                },
                onPartialDecode,
                false
            )
        }
    }

    fun close() {
        primary.close()
        fallback?.close()
    }
}