package org.futo.inputmethod.latin.xlm

import android.content.Context
import org.futo.inputmethod.latin.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

val TOKENIZER_RESOURCE = R.raw.ml3_tokenizer
val BASE_MODEL_RESOURCE = R.raw.ml4_1_f16

object ModelPaths {
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
        File(context.cacheDir, "tokenizer-$TOKENIZER_RESOURCE.model").delete()
        File(context.cacheDir, "model-$BASE_MODEL_RESOURCE.gguf").delete()
    }

    fun getTokenizer(context: Context): String {
        return copyResourceToCache(context, TOKENIZER_RESOURCE, "tokenizer-$TOKENIZER_RESOURCE.model")
    }

    fun getBaseModel(context: Context): String {
        return copyResourceToCache(context, BASE_MODEL_RESOURCE, "model-$BASE_MODEL_RESOURCE.gguf")
    }

    private fun getFinetunedModelFile(context: Context): File = File(context.filesDir, "trained.gguf")

    fun getFinetunedModelOutput(context: Context): String {
        return getFinetunedModelFile(context).absolutePath
    }

    fun getPrimaryModel(context: Context): String {
        // Prefer fine-tuned model
        if(getFinetunedModelFile(context).exists()) {
            return getFinetunedModelFile(context).absolutePath
        }

        // If it doesn't exist, use the base
        println("Model ${getFinetunedModelFile(context)} doesn't exist, so falling back to base!")
        return getBaseModel(context)
    }
}