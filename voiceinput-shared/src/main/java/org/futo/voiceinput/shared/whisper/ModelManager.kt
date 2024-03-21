package org.futo.voiceinput.shared.whisper

import android.content.Context
import org.futo.voiceinput.shared.ggml.WhisperGGML
import org.futo.voiceinput.shared.types.ModelLoader


class ModelManager(
    val context: Context
) {
    private val loadedModels: HashMap<ModelLoader, WhisperGGML> = hashMapOf()

    fun obtainModel(model: ModelLoader): WhisperGGML {
        if (!loadedModels.contains(model)) {
            loadedModels[model] = model.loadGGML(context)
        }

        return loadedModels[model]!!
    }

    fun cancelAll() {
        loadedModels.forEach {
            it.value.cancel()
        }
    }

    suspend fun cleanUp() {
        for (model in loadedModels.values) {
            model.close()
        }

        loadedModels.clear()
    }
}
