package org.futo.inputmethod.latin.xlm

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import org.futo.inputmethod.annotations.ExternallyReferenced

@OptIn(DelicateCoroutinesApi::class)
val TrainingContext = newSingleThreadContext("AdapterTrainingContext")

class InadequateDataException() : Exception("Inadequate Training Data")

class AdapterTrainer(
    baseModelPath: String,
    checkpointCachePath: String,
    outputModelPath: String,
    weight: Float,
    examples: List<String>,
    val lossFlow: MutableSharedFlow<Float>?,
    val progressFlow: MutableSharedFlow<Float>?
) {
    private external fun openNative(baseModelPath: String, loraCachePath: String, outputModelPath: String, weight: Float): Long
    private external fun closeNative(handle: Long)
    private external fun addExample(handle: Long, example: String)
    private external fun train(handle: Long) // Long-running function

    private var handle: Long = 0L
    private fun isHandleValid() = handle != 0L

    @ExternallyReferenced
    private fun emitProgress(progress: Float) {
        progressFlow?.tryEmit(progress)
    }

    @ExternallyReferenced
    private fun emitLoss(loss: Float) {
        lossFlow?.tryEmit(loss)
    }

    init {
        handle = openNative(baseModelPath, checkpointCachePath, outputModelPath, weight)
        if(!isHandleValid()) {
            throw IllegalArgumentException("Failed to initialize AdapterTrainer with given parameters")
        }

        var numAdded = 0
        examples.forEach {
            if(it.isNotBlank()) {
                addExample(handle, it.trim() + " ")
                numAdded += 1
            }
        }

        if(numAdded == 0) {
            closeNative(handle)
            throw InadequateDataException()
        }
    }

    fun close() {
        closeNative(handle)
        handle = 0
    }

    suspend fun train() = withContext(TrainingContext) {
        if(!isHandleValid()) throw IllegalStateException("Attempting to train with null handle")
        train(handle)
    }
}

class AdapterTrainerBuilder(val baseModelPath: String, val checkpointPath: String, val outputModelPath: String) {
    private val examples = mutableListOf<String>()
    fun addExamples(newExamples: List<String>) {
        examples.addAll(newExamples)
    }

    private var lossFlow: MutableSharedFlow<Float>? = null
    fun setLossFlow(flow: MutableSharedFlow<Float>) {
        lossFlow = flow
    }

    private var progressFlow: MutableSharedFlow<Float>? = null
    fun setProgressFlow(flow: MutableSharedFlow<Float>) {
        progressFlow = flow
    }

    private var weight = 1.0f;
    fun setWeight(weight: Float) {
        this.weight = weight;
    }

    fun loadAndPrepare(): AdapterTrainer {
        return AdapterTrainer(baseModelPath, checkpointPath, outputModelPath, weight, examples, lossFlow = lossFlow, progressFlow = progressFlow)
    }
}