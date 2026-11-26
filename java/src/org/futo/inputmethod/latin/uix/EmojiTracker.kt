package org.futo.inputmethod.latin.uix

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import org.futo.inputmethod.latin.uix.actions.skinTones
import org.futo.inputmethod.latin.uix.actions.stripVariationSelector16


val lastUsedEmoji = stringPreferencesKey("last_used_emoji")
const val EmojiLimit = 32

object EmojiTracker {
    // Temporary for #2316 to fix recently used emojis
    private val patchEmoji = { em: String ->
        if (skinTones.filter { it.isNotEmpty() }.any { it in em })
            em.stripVariationSelector16()
        else em
    }

    suspend fun Context.useEmoji(emoji: String) {
        if(isDeviceLocked) return

        dataStore.edit { ds ->
            val combined = emoji + "<|>" + (ds[lastUsedEmoji] ?: "")
            ds[lastUsedEmoji] = combined.split("<|>").map(patchEmoji)
                .distinct().take(EmojiLimit).joinToString("<|>")
        }
    }

    suspend fun Context.getRecentEmojis(): List<String> {
        if(isDeviceLocked) return listOf()

        return getSetting(lastUsedEmoji, "")
            .split("<|>")
            .filter { it.isNotBlank() }
            .distinct()
    }

    suspend fun Context.resetRecentEmojis() {
        if(isDeviceLocked) return

        setSetting(lastUsedEmoji, "")
    }
}