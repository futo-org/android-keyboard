package org.futo.voiceinput.shared.ml

import android.content.Context
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.model.Model
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.MappedByteBuffer

class WhisperEncoderXatn {
    private val model: Model

    constructor(context: Context, modelPath: String = "tiny-en-encoder-xatn.tflite", options: Model.Options = Model.Options.Builder().build()) {
        model = Model.createModel(context, modelPath, options)
    }

    constructor(modelBuffer: MappedByteBuffer, options: Model.Options = Model.Options.Builder().build()) {
        model = Model.createModel(modelBuffer, "", options)
    }


    fun process(audioFeatures: TensorBuffer): Outputs {
        val outputs = Outputs(model)
        model.run(arrayOf<Any>(audioFeatures.buffer), outputs.buffer)
        return outputs
    }

    fun close() {
        model.close()
    }

    inner class Outputs internal constructor(model: Model) {
        val crossAttention: TensorBuffer

        init {
            crossAttention =
                TensorBuffer.createFixedSize(model.getOutputTensorShape(0), DataType.FLOAT32)
        }

        internal val buffer: Map<Int, Any>
            get() {
                val outputs: MutableMap<Int, Any> = HashMap()
                outputs[0] = crossAttention.buffer
                return outputs
            }
    }
}