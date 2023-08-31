package org.futo.voiceinput.shared.whisper

import android.content.Context
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.model.Model
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.MappedByteBuffer

class DecoderModel {
    companion object {
        /**
         * Load the model from a file in the context's assets (model built into the apk)
         */
        fun loadFromAssets(
            context: Context,
            modelPath: String,
            options: Model.Options = Model.Options.Builder().build()
        ): DecoderModel {
            return DecoderModel(context, modelPath, options)
        }

        /**
         * Load the model from a MappedByteBuffer, which can be created from any File
         */
        fun loadFromMappedBuffer(
            modelBuffer: MappedByteBuffer, options: Model.Options = Model.Options.Builder().build()
        ): DecoderModel {
            return DecoderModel(modelBuffer, options)
        }
    }

    private val model: Model

    private constructor(
        context: Context,
        modelPath: String,
        options: Model.Options = Model.Options.Builder().build()
    ) {
        model = Model.createModel(context, modelPath, options)
    }

    private constructor(
        modelBuffer: MappedByteBuffer, options: Model.Options = Model.Options.Builder().build()
    ) {
        model = Model.createModel(modelBuffer, "", options)
    }


    fun process(
        crossAttention: TensorBuffer,
        seqLen: TensorBuffer,
        cache: TensorBuffer,
        inputIds: TensorBuffer
    ): Outputs {
        val outputs = Outputs(model)
        model.run(
            arrayOf<Any>(crossAttention.buffer, seqLen.buffer, cache.buffer, inputIds.buffer),
            outputs.buffer
        )
        return outputs
    }

    fun close() {
        model.close()
    }

    fun getCacheTensorShape(): IntArray {
        return model.getOutputTensorShape(1)
    }

    inner class Outputs internal constructor(model: Model) {
        val logits: TensorBuffer
        val nextCache: TensorBuffer

        init {
            logits = TensorBuffer.createFixedSize(model.getOutputTensorShape(0), DataType.FLOAT32)
            nextCache =
                TensorBuffer.createFixedSize(model.getOutputTensorShape(1), DataType.FLOAT32)
        }

        internal val buffer: Map<Int, Any>
            get() {
                val outputs: MutableMap<Int, Any> = HashMap()
                outputs[0] = logits.buffer
                outputs[1] = nextCache.buffer
                return outputs
            }
    }
}