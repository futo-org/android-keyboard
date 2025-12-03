package org.futo.inputmethod.latin.utils

import java.io.ByteArrayOutputStream
import java.io.InputStream

fun InputStream.readAllBytesCompat(): ByteArray {
    val buffer = ByteArrayOutputStream()
    val data = ByteArray(4096)
    var nRead: Int
    while (this.read(data, 0, data.size).also { nRead = it } != -1) {
        buffer.write(data, 0, nRead)
    }
    return buffer.toByteArray()
}