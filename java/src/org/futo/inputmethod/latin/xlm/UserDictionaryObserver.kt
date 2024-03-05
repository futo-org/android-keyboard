package org.futo.inputmethod.latin.xlm

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.UserDictionary
import android.database.Cursor
import android.util.Log

data class Word(val word: String, val frequency: Int)

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
        contentResolver.registerContentObserver(uri, true, contentObserver)
        updateWords()
    }

    fun getWords(): List<Word> = words

    private fun updateWords() {
        val projection = arrayOf(UserDictionary.Words.WORD, UserDictionary.Words.FREQUENCY)
        val cursor: Cursor? = contentResolver.query(uri, projection, null, null, null)

        words.clear()

        cursor?.use {
            val wordColumn = it.getColumnIndex(UserDictionary.Words.WORD)
            val frequencyColumn = it.getColumnIndex(UserDictionary.Words.FREQUENCY)

            while (it.moveToNext()) {
                val word = it.getString(wordColumn)
                val frequency = it.getInt(frequencyColumn)

                if(word.length < 64) {
                    words.add(Word(word, frequency))
                }
            }
        }

        words.sortByDescending { it.frequency }


        var approxNumTokens = 0
        var cutoffIndex = -1
        for(index in 0 until words.size) {
            approxNumTokens += words[index].word.length / 4
            if(approxNumTokens > 600) {
                cutoffIndex = index
                break
            }
        }

        if(cutoffIndex != -1) {
            Log.w("UserDictionaryObserver", "User Dictionary is being trimmed to $cutoffIndex due to reaching num token limit")
            words = words.subList(0, cutoffIndex)
        }
    }

    fun unregister() {
        contentResolver.unregisterContentObserver(contentObserver)
    }
}
