package org.futo.inputmethod.latin

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.futo.inputmethod.latin.uix.TEXT_REPLACEMENTS
import org.futo.inputmethod.latin.uix.dataStore
import org.futo.inputmethod.latin.uix.getSettingFlow

@Serializable
data class TextReplacement(
    val shortcut: String,
    val replacement: String,

    /**
     * When true the shortcut is expanded without asking. When false the replacement is only
     * offered as a suggestion. Only auto-replacement is implemented for now, entries with this
     * set to false are stored but never expanded.
     */
    val autoReplace: Boolean = true
)

/**
 * A shortcut which contains no word code points (e.g. "@@") can't be terminated by a separator,
 * because the separator would be part of the shortcut as far as the user is concerned. Those are
 * expanded as soon as they're typed. Shortcuts containing word code points wait for a separator,
 * otherwise typing "ends" would expand the "en" shortcut halfway through the word.
 */
internal fun TextReplacement.expandsImmediately(): Boolean =
    shortcut.isNotEmpty() && shortcut.none { Character.isLetterOrDigit(it) }

/** Locale-independent, so that a Turkish "I" doesn't fold differently than the stored shortcut. */
internal fun foldCase(c: Char): Char = Character.toLowerCase(Character.toUpperCase(c))

/**
 * Shortcuts are matched by walking backwards from the cursor, so the trie is keyed on the reversed
 * shortcut. Matching returns the longest shortcut ending at the given position.
 *
 * Matching ignores case. Without this a shortcut typed at the start of a sentence would never
 * match, because auto-capitalization turns "omw" into "Omw" before it reaches us. The replacement
 * is always committed exactly as the user entered it.
 */
internal class ReverseTrie(replacements: List<TextReplacement>) {
    private class Node {
        val children = HashMap<Char, Node>()
        var value: TextReplacement? = null
    }

    private val root = Node()

    val maxShortcutLength: Int = replacements.maxOfOrNull { it.shortcut.length } ?: 0

    init {
        replacements.forEach { replacement ->
            if (replacement.shortcut.isEmpty() || replacement.replacement.isEmpty()) return@forEach

            var node = root
            for (i in replacement.shortcut.indices.reversed()) {
                node = node.children.getOrPut(foldCase(replacement.shortcut[i])) { Node() }
            }
            // Last entry wins on duplicate shortcuts
            node.value = replacement
        }
    }

    /**
     * Finds the longest shortcut which ends at [endIndex] (exclusive) in [text].
     *
     * @param filter restricts which entries are eligible, used to skip entries which shouldn't
     *               expand yet given the character that was just typed.
     */
    fun findEndingAt(
        text: CharSequence,
        endIndex: Int,
        filter: (TextReplacement) -> Boolean
    ): TextReplacement? {
        var node = root
        var best: TextReplacement? = null

        var i = endIndex - 1
        while (i >= 0) {
            node = node.children[foldCase(text[i])] ?: break

            val value = node.value
            if (value != null && filter(value) && hasWordBoundaryBefore(text, i, value)) {
                best = value
            }

            i--
        }

        return best
    }

    /**
     * A shortcut starting with a word code point must not be preceded by one, so that "en" doesn't
     * turn "then" into "thEnglish". Shortcuts starting with a symbol have no such requirement --
     * "@@" is expected to work regardless of what precedes it.
     */
    private fun hasWordBoundaryBefore(
        text: CharSequence,
        startIndex: Int,
        replacement: TextReplacement
    ): Boolean {
        if (!Character.isLetterOrDigit(replacement.shortcut[0])) return true
        if (startIndex == 0) return true
        return !Character.isLetterOrDigit(text[startIndex - 1])
    }
}

/**
 * Holds the user's text replacements in memory so the input path can match against them without
 * touching disk. Mirrors [SuggestionBlacklist] in how it's initialized and kept up to date.
 */
class TextReplacements(val context: Context, val lifecycleScope: LifecycleCoroutineScope) {
    @Volatile
    private var trie: ReverseTrie = ReverseTrie(emptyList())

    @Volatile
    var replacements: List<TextReplacement> = emptyList()
        private set

    fun init() {
        lifecycleScope.launch {
            context.getSettingFlow(TEXT_REPLACEMENTS).collect { value ->
                replacements = decode(value)
                trie = ReverseTrie(replacements.filter { it.autoReplace })
            }
        }
    }

    fun isEmpty(): Boolean = trie.maxShortcutLength == 0

    /**
     * How much text before the cursor needs to be fetched to be able to match anything, including
     * the character preceding the shortcut for the word boundary check.
     */
    fun getLookbehindLength(): Int = trie.maxShortcutLength + 1

    /**
     * Finds the replacement to apply for text ending at [endIndex] (exclusive).
     *
     * @param separatorTyped whether the character that triggered this lookup was a word separator.
     *                       Shortcuts containing word code points only expand on a separator.
     */
    fun findMatch(
        textBeforeCursor: CharSequence,
        endIndex: Int,
        separatorTyped: Boolean
    ): TextReplacement? = trie.findEndingAt(textBeforeCursor, endIndex) {
        if (separatorTyped) !it.expandsImmediately() else it.expandsImmediately()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        @JvmStatic
        fun decode(value: String): List<TextReplacement> = if (value.isEmpty()) {
            emptyList()
        } else {
            try {
                json.decodeFromString<List<TextReplacement>>(value)
            } catch (_: Exception) {
                emptyList()
            }
        }

        @JvmStatic
        fun encode(value: List<TextReplacement>): String = json.encodeToString(value)

        suspend fun setReplacements(context: Context, value: List<TextReplacement>) {
            context.dataStore.edit {
                it[TEXT_REPLACEMENTS.key] = encode(value)
            }
        }
    }
}
