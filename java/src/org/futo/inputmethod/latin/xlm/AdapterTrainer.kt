package org.futo.inputmethod.latin.xlm

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext

@OptIn(DelicateCoroutinesApi::class)
val TrainingContext = newSingleThreadContext("AdapterTrainingContext")

class AdapterTrainer(baseModelPath: String, tokenizerPath: String, checkpointPath: String, examples: List<String>) {
    private external fun openNative(baseModelPath: String, tokenizerPath: String, outputPath: String): Long
    private external fun closeNative(handle: Long)
    private external fun addExample(handle: Long, example: String)
    private external fun train(handle: Long) // Long-running function

    private var handle: Long = 0L
    private fun isHandleValid() = handle != 0L

    init {
        handle = openNative(baseModelPath, tokenizerPath, checkpointPath)
        if(!isHandleValid()) {
            throw IllegalArgumentException("Failed to initialize AdapterTrainer with given parameters")
        }

        examples.forEach {
            if(it.isNotBlank()) {
                addExample(handle, it.trim())
            }
        }
    }

    suspend fun train() = withContext(TrainingContext) {
        if(!isHandleValid()) throw IllegalStateException("Attempting to train with null handle")
        train(handle)
    }
}

class AdapterTrainerBuilder(val baseModelPath: String, val tokenizerPath: String, val checkpointPath: String) {
    private val examples = mutableListOf<String>()
    fun addExamples(newExamples: List<String>) {
        examples.addAll(newExamples)
    }

    fun loadAndPrepare(): AdapterTrainer {
        return AdapterTrainer(baseModelPath, tokenizerPath, checkpointPath, examples)
    }
}