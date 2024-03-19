package org.futo.inputmethod.latin.uix

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey


val lastUsedEmoji = stringPreferencesKey("last_used_emoji")
val lastUsedColor = stringPreferencesKey("last_used_color")

object EmojiTracker {
    suspend fun Context.setLastUsedColor(color: String) {
        dataStore.edit {
            it[lastUsedColor] = color
        }
    }

    suspend fun Context.useEmoji(emoji: String) {
        dataStore.edit {
            val combined = emoji + "<|>" + (it[lastUsedEmoji] ?: "")
            it[lastUsedEmoji] = combined.split("<|>").take(128).joinToString("<|>")
        }
    }

    suspend fun Context.unuseEmoji(emoji: String) {
        dataStore.edit {
            val split = (it[lastUsedEmoji] ?: "").split("<|>")
            val idxToRemove = split.indexOfFirst { v -> v == emoji || v.trim() == emoji.trim() }
            it[lastUsedEmoji] = split.filterIndexed { i, _ -> i != idxToRemove}.joinToString("<|>")
        }
    }

    suspend fun Context.getRecentEmojis(): List<String> {
        return getSetting(lastUsedEmoji, "")
            .split("<|>")
            .filter { it.isNotBlank() }
            .distinct()
    }
}