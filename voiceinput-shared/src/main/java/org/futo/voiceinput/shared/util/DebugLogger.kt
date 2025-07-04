package org.futo.voiceinput.shared.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogger {
    private var logFile: File? = null

    fun init(context: Context) {
        logFile = File(context.cacheDir, "groq_debug.log")
    }

    fun log(message: String) {
        try {
            val file = logFile ?: return
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            file.appendText("$timestamp $message\n")
        } catch (_: Exception) {
        }
    }
}
