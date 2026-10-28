package org.futo.inputmethod.latin.uix.actions.emoji

import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.futo.inputmethod.latin.uix.DataStoreHelper
import org.futo.inputmethod.latin.uix.SettingsKey

val HIDDEN_EMOJIS = SettingsKey(
    key = stringSetPreferencesKey("hiddenEmojis"),
    default = emptySet()
)

private val emojiSkinToneModifiers = listOf(
    "\ud83c\udffb",
    "\ud83c\udffc",
    "\ud83c\udffd",
    "\ud83c\udffe",
    "\ud83c\udfff",
)

/**
 * Normalizes presentation and skin-tone variants so hiding one form hides the whole emoji family.
 * ZWJ sequences and gender variants remain distinct.
 */
fun normalizeEmojiForVisibility(emoji: String): String {
    var normalized = emoji.replace("\uFE0E", "").replace("\uFE0F", "")
    emojiSkinToneModifiers.forEach { modifier ->
        normalized = normalized.replace(modifier, "")
    }
    return normalized
}

fun parseEmojiSequenceForHiding(input: String, knownEmojis: Collection<String>): List<String>? {
    val normalizedInput = normalizeEmojiForVisibility(input.trim())
    if (normalizedInput.isEmpty()) return null

    val candidates = knownEmojis
        .mapNotNull { emoji ->
            val normalized = normalizeEmojiForVisibility(emoji)
            if (normalized.isEmpty()) null else emoji to normalized
        }
        .distinctBy { it.second }
        .sortedByDescending { it.second.length }

    val parsed = mutableListOf<String>()
    var offset = 0
    while (offset < normalizedInput.length) {
        val match = candidates.firstOrNull { (_, normalized) ->
            normalizedInput.startsWith(normalized, offset)
        } ?: return null

        parsed.add(match.first)
        offset += match.second.length
    }

    return parsed
}

fun normalizeHiddenEmojiSet(hiddenEmojis: Set<String>): Set<String> =
    hiddenEmojis.mapTo(mutableSetOf(), ::normalizeEmojiForVisibility)

fun isEmojiHidden(emoji: String, normalizedHiddenEmojis: Set<String>): Boolean =
    normalizeEmojiForVisibility(emoji) in normalizedHiddenEmojis

fun isEmojiHidden(emoji: String): Boolean =
    isEmojiHidden(
        emoji,
        normalizeHiddenEmojiSet(DataStoreHelper.getSetting(HIDDEN_EMOJIS))
    )
