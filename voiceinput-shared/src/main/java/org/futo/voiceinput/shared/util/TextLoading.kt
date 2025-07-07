package org.futo.voiceinput.shared.util

import android.content.Context
import android.content.res.Resources
import java.io.File

@Throws(Resources.NotFoundException::class)
fun loadTextFromResource(context: Context, resourceId: Int): String {
    val resources = context.resources

    val input = resources.openRawResource(resourceId)
    return input.bufferedReader().readText()
}

fun loadTextFromFile(file: File): String {
    return file.readText()
}

fun normalizeTranscription(text: String): String {
    val collapsed = text.replace(Regex("\\s+"), " ").trim()
    if (collapsed.isEmpty()) return collapsed
    val words = collapsed.split(" ")
    val builder = StringBuilder()
    var previous: String? = null
    for (word in words) {
        if (word != previous) {
            if (builder.isNotEmpty()) builder.append(' ')
            builder.append(word)
        }
        previous = word
    }
    return builder.toString()
}
