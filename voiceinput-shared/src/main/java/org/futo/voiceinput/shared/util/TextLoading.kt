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
