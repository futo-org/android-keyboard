package org.futo.voiceinput.shared.whisper

import android.content.Context
import org.futo.voiceinput.shared.ggml.WhisperGGML
import org.futo.voiceinput.shared.types.ModelLoader


class ModelManager(
    val context: Context,
    var useGpu: Boolean = false
) {
    private val loadedModels: HashMap<Any, WhisperGGML> = hashMapOf()

    fun obtainModel(model: ModelLoader): WhisperGGML {
        val key = "${model.key(context)}#${if(useGpu) "gpu" else "cpu"}"
        if (!loadedModels.contains(key)) {
            loadedModels[key] = model.loadGGML(context, useGpu)
        }

        return loadedModels[key]!!
    }

    fun cancelAll() {
        loadedModels.forEach {
            it.value.cancel()
        }
    }

    suspend fun cleanUp() {
        for (model in loadedModels.entries) {
            model.value.close()
        }

        loadedModels.clear()
    }
}
