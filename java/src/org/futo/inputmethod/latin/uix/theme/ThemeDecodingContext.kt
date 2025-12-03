package org.futo.inputmethod.latin.uix.theme

import android.content.Context
import androidx.datastore.core.Closeable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

interface ThemeDecodingContext: Closeable {
    val context: Context
    fun getFileBytes(path: String): ByteArray?

    // implementation-defined hash, could be as simple as hash of whole zip file + path
    fun getFileHash(path: String): String?

    override fun close()
}

@OptIn(ExperimentalEncodingApi::class)
fun ThemeDecodingContext.getFileBytesOrBase64(src: String): ByteArray? = when {
    src.startsWith("data:image") -> {
        val comma = src.indexOf(',')
        if (comma == -1) null
        else {
            val base64 = src.substring(comma + 1)
            Base64.Default.decode(base64)
        }
    }
    else -> getFileBytes(src)
}

@OptIn(ExperimentalEncodingApi::class)
fun ThemeDecodingContext.getFileHashOrBase64(src: String): String? = when {
    src.startsWith("data:image") -> {
        // Not a perfect hash
        src.hashCode().toString()
    }
    else -> getFileHash(src)
}