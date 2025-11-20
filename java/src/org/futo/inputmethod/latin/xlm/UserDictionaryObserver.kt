package org.futo.inputmethod.latin.xlm

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.UserDictionary
import android.database.Cursor
import android.util.Log
import org.futo.inputmethod.latin.Subtypes
import java.util.Locale

data class Word(val word: String, val frequency: Int, val locale: String?, val shortcut: String?)

class UserDictionaryObserver(context: Context) {
    private val contentResolver = context.applicationContext.contentResolver
    private val uri: Uri = UserDictionary.Words.CONTENT_URI
    private val handler = Handler(Looper.getMainLooper())
    private var words = mutableListOf<Word>()

    private val contentObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            updateWords()
        }
    }

    init {
        try {
            contentResolver.registerContentObserver(uri, true, contentObserver)
        } catch(_: Exception) { }
        updateWords()
    }

    fun getWords(locales: List<Locale>): List<Word> = words.filter {
        if(it.locale == null) {
            true
        } else {
            val locale = Subtypes.getLocale(it.locale)
            locales.any { it.language == locale.language }
        }
    }

    internal fun updateWords() {
        val projection = arrayOf(
            UserDictionary.Words.WORD,
            UserDictionary.Words.FREQUENCY,
            UserDictionary.Words.LOCALE,
            UserDictionary.Words.SHORTCUT)
        val cursor: Cursor? = contentResolver.query(uri, projection, null, null, null)

        words.clear()

        cursor?.use {
            val wordColumn = it.getColumnIndex(UserDictionary.Words.WORD)
            val frequencyColumn = it.getColumnIndex(UserDictionary.Words.FREQUENCY)
            val localeColumn = it.getColumnIndex(UserDictionary.Words.LOCALE)
            val shortcutColumn = it.getColumnIndex(UserDictionary.Words.SHORTCUT)

            while (it.moveToNext()) {
                val word = it.getString(wordColumn)
                val frequency = it.getInt(frequencyColumn)
                val locale = it.getString(localeColumn)
                val shortcut = it.getString(shortcutColumn)

                if(word.length < 64) {
                    words.add(Word(word, frequency, locale, shortcut))
                }
            }
        }

        words.sortByDescending { it.frequency }


        var approxNumTokens = 0
        var cutoffIndex = -1
        for(index in 0 until words.size) {
            approxNumTokens += (4+words[index].word.length) / 4
            if(approxNumTokens > 200) {
                cutoffIndex = index
                break
            }
        }

        if(cutoffIndex != -1) {
            Log.w("UserDictionaryObserver", "User Dictionary is being trimmed to $cutoffIndex / ${words.size} due to reaching num token limit")
            words = words.subList(0, cutoffIndex)
        }
    }

    fun unregister() {
        try {
            contentResolver.unregisterContentObserver(contentObserver)
        } catch(_: Exception) { }
    }
}
