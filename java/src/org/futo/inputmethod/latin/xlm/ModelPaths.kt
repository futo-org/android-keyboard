package org.futo.inputmethod.latin.xlm

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.Keep
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.MutableSharedFlow
import org.futo.inputmethod.annotations.ExternallyReferenced
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.setSetting
import java.io.File
import java.io.FileOutputStream


val BASE_MODEL_RESOURCE = R.raw.ml4_1_f16_meta_fixed
val BASE_MODEL_NAME = "ml4_1_f16_meta_fixed"

val MODEL_OPTION_KEY = SettingsKey(
    stringSetPreferencesKey("lmModelsByLanguage"),
    setOf("en:$BASE_MODEL_NAME")
)

@Keep
@ExternallyReferenced
data class ModelInfo(
    val name: String,
    val description: String,
    val author: String,
    val license: String,
    val features: List<String>,
    val languages: List<String>,
    val tokenizer_type: String,
    val finetune_count: Int,
    val path: String
) {
    fun toLoader(): ModelInfoLoader {
        return ModelInfoLoader(File(path), name)
    }
}

class ModelInfoLoader(
    val path: File,
    val name: String,
) {
    fun loadDetails(): ModelInfo? {
        return loadNative(path.absolutePath)
    }

    external fun loadNative(path: String): ModelInfo?
}

object ModelPaths {
    val modelOptionsUpdated = MutableSharedFlow<Unit>(replay = 0)

    fun exportModel(context: Context, uri: Uri, file: File) {
        context.contentResolver.openOutputStream(uri)!!.use { outputStream ->
            file.inputStream().use { inputStream ->
                var read = 0
                val bytes = ByteArray(1024)
                while (inputStream.read(bytes).also { read = it } != -1) {
                    outputStream.write(bytes, 0, read)
                }
            }
        }
    }


    private val supportedFeatures = setOf(
        "base_v1",
        "inverted_space",
        "xbu_char_autocorrect_v1",
        "lora_finetunable_v1",
        "xc0_swipe_typing_v1",
        "char_embed_mixing_v1",
        "experiment_linear_208_209_210",
    )

    fun importModel(context: Context, uri: Uri): File {
        val modelDirectory = getModelDirectory(context)

        val fileName = context.contentResolver.query(uri, null, null, null, null, null).use {
            if(it != null && it.moveToFirst()) {
                val colIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (colIdx != -1) {
                    it.getString(colIdx)
                } else {
                    null
                }
            } else {
                null
            }
        } ?: throw IllegalArgumentException("Model file data could not be obtained")


        val file = File(modelDirectory, fileName)
        if(file.exists()) {
            throw IllegalArgumentException("Model with the name \"${file.name}\" already exists, refusing to replace!")
        }

        if(file.extension != "gguf") {
            throw IllegalArgumentException("File's extension must equal 'gguf'")
        }

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            var read = 0
            val bytes = ByteArray(1024)

            read = inputStream.read(bytes)

            // Sanity check to make sure it's valid
            if(read < 4
                || bytes[0] != 'G'.code.toByte()
                || bytes[1] != 'G'.code.toByte()
                || bytes[2] != 'U'.code.toByte()
                || bytes[3] != 'F'.code.toByte()
            ) {
                throw IllegalArgumentException("File \"${file.name}\" does not appear to be a GGUF file")
            }

            file.outputStream().use { outputStream ->
                while (read != -1) {
                    outputStream.write(bytes, 0, read)
                    read = inputStream.read(bytes)
                }
            }
        }

        // Attempt to load metadata here and check if it can even load
        val details = ModelInfoLoader(
            name = file.nameWithoutExtension,
            path = file
        ).loadDetails()

        if(details == null) {
            file.delete()
            throw IllegalArgumentException("Failed to load metadata, file \"${file.name}\" may not be a valid GGUF file")
        }

        // Check that the model has any features at all
        if(details.features.isEmpty()) {
            file.delete()
            throw IllegalArgumentException("Model is a valid GGUF file, but does not support use as a keyboard language model (it lacks KeyboardLM metadata).\n\nIf you are a model creator: models must support specific features and prompt formats; arbitrary gguf models are unsupported at this time. Refer to the model creation documentation for more details.")
        }

        // Check that we support all features from this model
        val unsupportedFeatures = details.features.filter {
            !(supportedFeatures.contains(it) || it.startsWith("opt_") || it.startsWith("_"))
        }
        if(unsupportedFeatures.isNotEmpty()) {
            file.delete()
            throw IllegalArgumentException("Model has the following unknown features: [${unsupportedFeatures.joinToString(separator=", ")}]\nYou probably need to update FUTO Keyboard.")
        }

        return file
    }

    suspend fun signalReloadModels() {
        modelOptionsUpdated.emit(Unit)
    }

    suspend fun updateModelOption(context: Context, key: String, value: File) {
        if(!value.absolutePath.startsWith(context.filesDir.absolutePath)) {
            throw IllegalArgumentException("Model path ${value.absolutePath} does not start with filesDir path ${context.filesDir.absolutePath}")
        }

        val options = context.getSetting(MODEL_OPTION_KEY).filter {
            it.split(":", limit = 2)[0] != key
        }.toMutableSet()

        options.add("$key:${value.nameWithoutExtension}")

        context.setSetting(MODEL_OPTION_KEY, options)

        signalReloadModels()
    }

    suspend fun getModelOptions(context: Context): Map<String, ModelInfoLoader> {
        ensureDefaultModelExists(context)
        val modelDirectory = getModelDirectory(context)
        val options = context.getSetting(MODEL_OPTION_KEY)

        val modelOptionsByLanguage = hashMapOf<String, ModelInfoLoader>()
        options.forEach {
            val splits = it.split(":", limit = 2)
            val language = splits[0]
            val modelName = splits[1]

            // TODO: This assumes the extension is .gguf
            val modelFile = File(modelDirectory, "$modelName.gguf")
            if(modelFile.exists()) {
                modelOptionsByLanguage[language] = ModelInfoLoader(modelFile, modelName)
            } else {
                Log.e("ModelPaths", "Option for language $language set to $modelName, but could not find ${modelFile.absolutePath}")
            }
        }

        return modelOptionsByLanguage
    }

    fun getModelDirectory(context: Context): File {
        val modelDirectory = File(context.filesDir, "transformer-models")

        if(!modelDirectory.isDirectory){
            modelDirectory.mkdir()
        }

        return modelDirectory
    }

    fun ensureDefaultModelExists(context: Context) {
        val directory = getModelDirectory(context)

        val tgtFile = File(directory, "$BASE_MODEL_NAME.gguf")
        if(!tgtFile.isFile) {

            context.resources.openRawResource(BASE_MODEL_RESOURCE).use { inputStream ->
                FileOutputStream(tgtFile).use { outputStream ->
                    var read = 0
                    val bytes = ByteArray(1024)
                    while (inputStream.read(bytes).also { read = it } != -1) {
                        outputStream.write(bytes, 0, read)
                    }
                }
            }
        }
    }

    fun getModels(context: Context): List<ModelInfoLoader> {
        ensureDefaultModelExists(context)

        return getModelDirectory(context).listFiles()?.map {
            ModelInfoLoader(
                path = it,
                name = it.nameWithoutExtension
            )
        } ?: listOf()
    }
}