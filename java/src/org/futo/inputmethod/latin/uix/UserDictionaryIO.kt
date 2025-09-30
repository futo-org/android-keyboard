package org.futo.inputmethod.latin.uix

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.UserDictionary
import org.futo.inputmethod.latin.localeFromString

class UserDictionaryIO(val context: Context) {
    private val contentResolver = context.applicationContext.contentResolver
    private val uri: Uri = UserDictionary.Words.CONTENT_URI

    fun get(): List<PersonalWord> {
        val result = mutableListOf<PersonalWord>()
        val projection = arrayOf(UserDictionary.Words.WORD, UserDictionary.Words.FREQUENCY, UserDictionary.Words.LOCALE, UserDictionary.Words.APP_ID, UserDictionary.Words.SHORTCUT)
        val cursor: Cursor? = contentResolver.query(uri, projection, null, null, null)

        cursor?.use {
            val wordColumn = it.getColumnIndex(UserDictionary.Words.WORD)
            val frequencyColumn = it.getColumnIndex(UserDictionary.Words.FREQUENCY)
            val localeColumn = it.getColumnIndex(UserDictionary.Words.LOCALE)
            val appIdColumn = it.getColumnIndex(UserDictionary.Words.APP_ID)
            val shortcutColumn = it.getColumnIndex(UserDictionary.Words.SHORTCUT)

            while (it.moveToNext()) {
                val word = it.getString(wordColumn)
                val frequency = it.getInt(frequencyColumn)
                val locale = it.getString(localeColumn)
                val appId = it.getInt(appIdColumn)
                val shortcut = it.getString(shortcutColumn)
                result.add(PersonalWord(
                        word,
                        frequency,
                        locale,
                        appId,
                        shortcut
                    ))
            }
        }

        return result
    }

    fun put(from: List<PersonalWord>, clear: Boolean = false) {
        if(clear) {
            contentResolver.delete(uri, null, null)
        }

        val currWords = get().toSet()
        from.filter {
            !currWords.contains(it)
        }.forEach {
            UserDictionary.Words.addWord(
                context,
                it.word,
                it.frequency,
                it.shortcut,
                it.locale?.let { localeFromString(it) }
            )
        }
    }

    fun remove(words: List<PersonalWord>) {
        words.forEach {
            if(it.locale != null) {
                val where =
                    UserDictionary.Words.WORD + " = ? AND " + UserDictionary.Words.LOCALE + " = ?"
                val args = arrayOf(it.word, it.locale)
                contentResolver.delete(uri, where, args)
            } else {
                val where = UserDictionary.Words.WORD + "=?"
                val args = arrayOf(it.word)
                contentResolver.delete(uri, where, args)
            }
        }
    }
}