package org.futo.inputmethod.latin.xlm

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class HistoryLogForTraining(
    val key: String, // (committedNgramCtx + word), used for unlearning

    val priorContext: String,
    val ngramContext: String,
    val misspelledWord: String?, // null if word was not misspelled but was chosen prediction
    val committedWord: String,

    val importance: Int, // 0 if autocorrected, 1 if manually selected, 3 if third option,

    val locale: String,

    val timeStamp: Long
)

fun saveHistoryLogBackup(context: Context, log: List<HistoryLogForTraining>) {
    val json = Json.encodeToString(log)

    val file = File(context.cacheDir, "historyLog.json")
    file.writeText(json)
}

fun loadHistoryLogBackup(context: Context, to: MutableList<HistoryLogForTraining>) {
    try {
        val file = File(context.cacheDir, "historyLog.json")
        if(file.exists()) {
            val reader = file.bufferedReader()
            val inputString = reader.use { it.readText() }

            val data = Json.decodeFromString<List<HistoryLogForTraining>>(inputString)

            to.clear()
            to.addAll(data)
        }
    } catch(e: Exception) {
        e.printStackTrace()
    }
}