package org.futo.voiceinput.shared.whisper

import android.content.Context
import org.futo.voiceinput.shared.types.ModelLoader


class ModelManager(
    val context: Context
) {
    private val loadedModels: HashMap<ModelLoader, WhisperModel> = hashMapOf()

    fun obtainModel(model: ModelLoader): WhisperModel {
        if (!loadedModels.contains(model)) {
            loadedModels[model] = WhisperModel(context, model)
        }

        return loadedModels[model]!!
    }

    suspend fun cleanUp() {
        for (model in loadedModels.values) {
            model.close()
        }

        loadedModels.clear()
    }
}
