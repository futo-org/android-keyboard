package org.futo.inputmethod.latin.xlm

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSetting
import java.io.File
import java.io.FileOutputStream


val BASE_MODEL_RESOURCE = R.raw.ml4_v3mixing_m
val BASE_MODEL_NAME = "ml4_v3mixing_m"

val MODEL_OPTION_KEY = SettingsKey(
    stringSetPreferencesKey("lmModelsByLanguage"),
    setOf("en:$BASE_MODEL_NAME")
)

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
)

class ModelInfoLoader(
    val path: File,
    val name: String,
) {
    fun loadDetails(): ModelInfo {
        return loadNative(path.absolutePath)
    }

    external fun loadNative(path: String): ModelInfo
}

object ModelPaths {
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
            throw IllegalArgumentException("Model with that name already exists, refusing to replace")
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
                throw IllegalArgumentException("File does not appear to be a GGUF file")
            }


            file.outputStream().use { outputStream ->
                while (read != -1) {
                    outputStream.write(bytes, 0, read)
                    read = inputStream.read(bytes)
                }
            }
        }

        // Should attempt to load metadata here and check if it can even load

        return file
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