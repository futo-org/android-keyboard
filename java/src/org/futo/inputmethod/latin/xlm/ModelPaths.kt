package org.futo.inputmethod.latin.xlm

import android.content.Context
import org.futo.inputmethod.latin.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files

val BASE_MODEL_RESOURCE = R.raw.ml4_v3mixing_m

data class ModelInfo(
    val name: String,
    val description: String,
    val author: String,
    val license: String,
    val features: List<String>,
    val languages: List<String>,
    val tokenizer_type: String,
    val finetune_count: Int
)

class ModelInfoLoader(
    val name: String,
) {
    fun loadDetails(): ModelInfo {
        TODO()
    }
}

object ModelPaths {
    fun getModels(context: Context): List<ModelInfoLoader> {
        val modelDirectory = File(context.filesDir, "transformer-models")
        TODO()
    }


    private fun copyResourceToCache(
        context: Context,
        resource: Int,
        filename: String
    ): String {
        val outputDir = context.cacheDir

        val outputFileTokenizer = File(
            outputDir,
            filename
        )

        if(outputFileTokenizer.exists()) {
            // May want to delete the file and overwrite it, if it's corrupted
            return outputFileTokenizer.absolutePath
        }

        val is_t = context.resources.openRawResource(resource)
        val os_t: OutputStream = FileOutputStream(outputFileTokenizer)

        var read = 0
        val bytes = ByteArray(1024)
        while (is_t.read(bytes).also { read = it } != -1) {
            os_t.write(bytes, 0, read)
        }
        os_t.flush()
        os_t.close()
        is_t.close()

        return outputFileTokenizer.absolutePath
    }

    fun clearCache(context: Context) {
        File(context.cacheDir, "model-$BASE_MODEL_RESOURCE.gguf").delete()
    }
}