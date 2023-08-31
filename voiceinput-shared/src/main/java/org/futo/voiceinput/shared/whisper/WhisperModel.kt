package org.futo.voiceinput.shared.whisper

import android.content.Context
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.futo.voiceinput.shared.types.DecodedMetadata
import org.futo.voiceinput.shared.types.ModelInferenceSession
import org.futo.voiceinput.shared.types.ModelLoader
import org.futo.voiceinput.shared.types.PromptingStyle
import org.futo.voiceinput.shared.types.getLanguageFromWhisperString
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.model.Model
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

/**
 * This is necessary to synchronize so two threads don't try to use the same tensor at once,
 * free a model while it's in use, etc.
 */
@OptIn(DelicateCoroutinesApi::class)
private val inferenceContext = newSingleThreadContext("InferenceContext")

class WhisperModel(
    val context: Context,
    val loader: ModelLoader,
) {
    private var closed = false

    private class InferenceSession(
        val model: WhisperModel, val bannedTokens: IntArray
    ) : ModelInferenceSession {
        private var seqLen = 0

        private var xAtn: TensorBuffer? = null
        private val decodedTokens = mutableListOf(model.tokenizer.decodeStartToken)

        private fun decodeStep(forceOption: Int? = null): Int {
            if (xAtn == null) {
                throw IllegalStateException("melToFeatures must be called before starting decoding")
            }

            model.loadSeqLenInputId(seqLen, decodedTokens.last())

            val decoderOutputs = model.runDecoder(xAtn!!, model.cacheTensor)
            model.cacheTensor.loadBuffer(decoderOutputs.nextCache.buffer.duplicate())

            val selectedToken = if (forceOption != null) {
                forceOption
            } else {
                val logits = decoderOutputs.logits.floatArray

                for (i in bannedTokens) logits[i] -= 1024.0f

                logits.withIndex().maxByOrNull { it.value }?.index!!
            }
            decodedTokens.add(selectedToken)

            seqLen += 1

            return selectedToken
        }

        override suspend fun melToFeatures(mel: FloatArray) {
            withContext(inferenceContext) {
                if (this@InferenceSession.xAtn != null) {
                    throw IllegalStateException("melToFeatures must only be called once")
                }

                this@InferenceSession.xAtn = model.runEncoderAndGetXatn(mel)
            }
        }

        private var metadataDecoded: Boolean = false
        override suspend fun decodeMetadata(): DecodedMetadata {
            if (metadataDecoded) {
                throw IllegalStateException("decodeMetadata must only be called once")
            }

            metadataDecoded = true

            return withContext(inferenceContext) {
                when (model.loader.promptingStyle) {
                    // We only need <|notimestamps|>, then we can move on. There is no metadata.
                    PromptingStyle.SingleLanguageOnly -> {
                        decodeStep(model.tokenizer.noTimestampsToken)

                        DecodedMetadata(detectedLanguage = null)
                    }

                    PromptingStyle.LanguageTokenAndAction -> {
                        val languageToken = decodeStep()

                        val language =
                            getLanguageFromWhisperString(model.tokenizer.tokenToString(languageToken)!!)

                        decodeStep(model.tokenizer.transcribeToken)
                        decodeStep(model.tokenizer.noTimestampsToken)

                        DecodedMetadata(detectedLanguage = language)
                    }
                }
            }
        }

        var outputDecoded: Boolean = false
        override suspend fun decodeOutput(onPartialResult: (String) -> Unit): String {
            // decodeMetadata brings us to a state where we can run decodeStep in a loop until the end or limit.
            if (!metadataDecoded) {
                throw IllegalStateException("You must call decodeMetadata before starting to decode output")
            }

            if (outputDecoded) {
                throw IllegalStateException("Output has already been decoded, you cannot call decodeOutput again.")
            }

            outputDecoded = true

            var normalizedString = ""
            withContext(inferenceContext) {
                // TODO: We can prompt the model here to force Simplified Chinese, etc
                // ...

                // TODO: Discover the true limit from cacheTensor's shape
                val maxLimit = 256

                var finalString = ""
                while (seqLen < maxLimit) {
                    val nextToken = decodeStep()
                    if (nextToken == model.tokenizer.decodeEndToken) {
                        break
                    }

                    yield()

                    model.tokenizer.tokenToString(nextToken)?.let {
                        finalString += it
                    }

                    normalizedString = stringifyUnicode(finalString)

                    launch(Dispatchers.Main) {
                        onPartialResult(normalizedString)
                    }
                }
            }

            return normalizedString
        }
    }

    private val encoderModel: EncoderModel
    private val decoderModel: DecoderModel
    private val tokenizer: Tokenizer

    init {
        val cpuOption = Model.Options.Builder().setDevice(Model.Device.CPU).build()
        // NNAPI is disabled due to reported issues

        val (encoder, decoder) = loader.loadEncoderDecoder(context, cpuOption)

        this.encoderModel = encoder
        this.decoderModel = decoder
        this.tokenizer = loader.loadTokenizer(context)
    }

    private var bannedTokens: IntArray = intArrayOf(
        tokenizer.translateToken, tokenizer.noCaptionsToken
    )

    private var previousBannedTokenSettings: DecodingConfiguration? = null
    private fun updateBannedTokens(settings: DecodingConfiguration) {
        if (settings == previousBannedTokenSettings) return

        previousBannedTokenSettings = settings

        var bannedTokens = intArrayOf(
            tokenizer.translateToken, tokenizer.noCaptionsToken
        )

        if (settings.suppressSymbols) {
            bannedTokens += tokenizer.symbolTokens
        }

        if (settings.languages.isNotEmpty()) {
            bannedTokens += tokenizer.generateBannedLanguageList(settings.languages)
        }

        this.bannedTokens = bannedTokens
    }

    // Must be called within inferenceContext
    private fun runEncoderAndGetXatn(mel: FloatArray): TensorBuffer {
        if (closed) throw IllegalStateException("Cannot run session after model has been closed")
        audioFeatures.loadArray(mel)
        return encoderModel.process(audioFeatures).crossAttention
    }

    // Must be called within inferenceContext
    private fun runDecoder(
        xAtn: TensorBuffer, cache: TensorBuffer
    ): DecoderModel.Outputs {
        if (closed) throw IllegalStateException("Cannot run session after model has been closed")
        return decoderModel.process(
            crossAttention = xAtn, seqLen = seqLenTensor, cache = cache, inputIds = inputIdTensor
        )
    }

    // TODO: Ideally this should be shared between model instances as well.
    private val cacheTensor =
        TensorBuffer.createFixedSize(decoderModel.getCacheTensorShape(), DataType.FLOAT32)

    companion object {
        private val audioFeatures =
            TensorBuffer.createFixedSize(intArrayOf(1, 80, 3000), DataType.FLOAT32)
        private val seqLenTensor = TensorBuffer.createFixedSize(intArrayOf(1), DataType.FLOAT32)
        private val inputIdTensor = TensorBuffer.createFixedSize(intArrayOf(1, 1), DataType.FLOAT32)

        private val seqLenArray = FloatArray(1)
        private val inputIdsArray = FloatArray(1)
    }

    // Must be called within inferenceContext
    private fun loadSeqLenInputId(seqLen: Int, inputId: Int) {
        // TFLite has sketchy support for ints, so the model takes floats as input and casts them
        // back to int internally
        seqLenArray[0] = seqLen.toFloat()
        inputIdsArray[0] = inputId.toFloat()

        seqLenTensor.loadArray(seqLenArray)
        inputIdTensor.loadArray(inputIdsArray)
    }


    init {
        val shape = cacheTensor.shape
        val size = shape[0] * shape[1] * shape[2] * shape[3]
        cacheTensor.loadArray(FloatArray(size) { 0f })
    }

    fun startInferenceSession(settings: DecodingConfiguration): ModelInferenceSession {
        if (closed) throw IllegalStateException("Cannot start session after model has been closed")

        updateBannedTokens(settings)
        return InferenceSession(
            this, bannedTokens
        )
    }

    suspend fun close() {
        if (closed) return

        closed = true

        withContext(inferenceContext) {
            encoderModel.close()
            decoderModel.close()
        }
    }
}