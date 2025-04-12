package org.futo.inputmethod.latin.uix.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.futo.voiceinput.shared.ggml.BailLanguageException
import org.futo.voiceinput.shared.ggml.DecodingMode
import org.futo.voiceinput.shared.ggml.InferenceCancelledException
import org.futo.voiceinput.shared.types.InferenceState
import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.types.ModelInferenceCallback
import org.futo.voiceinput.shared.types.ModelLoader
import org.futo.voiceinput.shared.types.getLanguageFromWhisperString
import org.futo.voiceinput.shared.types.toWhisperString
import org.futo.inputmethod.latin.RichInputMethodManager;

data class TextContext(
    val beforeCursor: CharSequence?,
    val afterCursor: CharSequence?
)

object ModelOutputSanitizer {
    private fun Char.isClosingBracket(): Boolean {
        return this in setOf(')', ']', '}', '>')
    }

    private fun Char.isOpeningBracket(): Boolean {
        return this in setOf('(', '[', '{', '<')
    }

    private fun Char.isPunctuation(): Boolean {
        return this in setOf('.', ',', '!', '?', ':', ';')
    }

    private fun String.endsWithWhitespaceOrNewline(): Boolean {
        return this.isNotEmpty() && this.last().isWhitespace()
    }

    private fun String.startsWithWhitespaceOrNewline(): Boolean {
        return this.isNotEmpty() && this.first().isWhitespace()
    }

    @JvmStatic
    fun sanitize(result: String, textContext: TextContext?): String {
        if (textContext == null) {
            return result
        }

        val locale = RichInputMethodManager.getInstance().getCurrentSubtypeLocale()

        var trimmed = result.trim()
        if (trimmed.isEmpty()) {
            return ""
        }

        val before = (textContext.beforeCursor?.toString() ?: "")
            .split("\n")
            .lastOrNull() ?: ""
        val after = (textContext.afterCursor?.toString() ?: "")
            .split("\n")
            .firstOrNull() ?: ""

        // Whisper tends to generate ellipsis at the end of phrases/sentences more often than appropriate. Which isn't so bad if it's at the very end but frustrating when inserting something inside of a sentence.
        if (trimmed.endsWith("...") && !after.isEmpty()) {
            trimmed = trimmed.dropLast(3)
        }

        // Punctuation
        if (trimmed.last().isPunctuation() && !after.isEmpty()) {
            trimmed = trimmed.dropLast(1)
        }

        // Capitalization
        val beforeTrimmed = before.trimEnd()
        val needsCapitalization = beforeTrimmed.isEmpty() || beforeTrimmed.matches(Regex(".*[.:?!]$"))
        val isAcronym = trimmed.length >= 2 &&
            trimmed[0].isUpperCase() &&
            trimmed[1].isUpperCase()
        val isEnglishI = locale.language == "en" && ((trimmed.length == 1 && trimmed.first() == 'I') || trimmed.matches(Regex("^I\\s")))
        if (needsCapitalization && trimmed.first().isLowerCase()) {
            trimmed = trimmed.replaceFirstChar { it.titlecase(locale) }
        } else if (!beforeTrimmed.isEmpty() && trimmed.first().isUpperCase() && !isAcronym && !isEnglishI) {
            trimmed = trimmed.replaceFirstChar { it.lowercase(locale) }
        }

        // Leading and trailing spaces
        val needsLeadingSpace = before.isNotEmpty() && !before.endsWithWhitespaceOrNewline() &&
            !before.last().isOpeningBracket() &&
            before.last() != '—'
        val needsTrailingSpace = after.isNotEmpty() &&
            !after.startsWithWhitespaceOrNewline() &&
            !after.first().isPunctuation() &&
            !after.first().isClosingBracket() &&
            after.first() != '—'

        val prefix = if (needsLeadingSpace) " " else ""
        val suffix = if (needsTrailingSpace) " " else ""

        return prefix + trimmed + suffix
    }
}