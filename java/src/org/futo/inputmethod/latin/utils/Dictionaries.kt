package org.futo.inputmethod.latin.utils

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.content.res.Resources.NotFoundException
import android.util.Log
import org.futo.inputmethod.latin.AssetFileAddress
import org.futo.inputmethod.latin.BundleHelper
import org.futo.inputmethod.latin.R
import java.io.File
import java.io.IOException
import java.util.Locale

object Dictionaries {
    enum class DictionaryKind(val candidateNameGenerator: (Locale) -> List<String>) {
        BinaryDictionary({ listOf(
            "main_" + it.toString().lowercase(),
            "main_" + it.language.lowercase())
        }),

        Mozc({
            if(it.language == "ja") listOf("builtin_mozc_data") else emptyList()
        }),

        Any({ locale ->
            DictionaryKind.entries.filter { it != Any }.flatMap { it.candidateNameGenerator(locale) }
        })
    }

    fun getDictionaryIfExists(context: Context, locale: Locale?, kind: DictionaryKind): AssetFileAddress? {
        if(locale == null) return null

        return kind.candidateNameGenerator(locale).firstNotNullOfOrNull {
            BundleHelper.obtainSplitAssetFileDescriptor(context, it, locale)
        }
    }

    fun getFallbackDictionary(context: Context): AssetFileAddress? {
        var afd: AssetFileDescriptor? = null
        try {
            val resId: Int = R.raw.main
            if (0 == resId) return null
            afd = context.resources.openRawResourceFd(resId)
            if (afd == null) {
                Log.e("Dictionaries", "Found the resource but it is compressed. resId=" + resId)
                return null
            }
            val sourceDir = context.getApplicationInfo().sourceDir

            val packagePath = File(sourceDir)
            if (!packagePath.isFile()) {
                Log.e("Dictionaries", "sourceDir is not a file: " + sourceDir)
                return null
            }

            return AssetFileAddress(sourceDir, afd.startOffset, afd.length)
        } catch (e: NotFoundException) {
            Log.e("Dictionaries", "Could not find the resource")
            return null
        } finally {
            if (afd != null) {
                try {
                    afd.close()
                } catch (e: IOException) {
                    /* IOException on close ? What am I supposed to do ? */
                }
            }
        }
    }
}