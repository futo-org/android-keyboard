package org.futo.voiceinput.shared.types

import org.futo.voiceinput.shared.whisper.stringifyUnicode

// Based on https://github.com/openai/whisper/blob/248b6cb124225dd263bb9bd32d060b6517e067f8/whisper/tokenizer.py#L236
private val SYMBOLS = "#()*+/:;<=>@[\\]^_`{|}~「」『』".chunked(1) + listOf(
    "<<",
    ">>",
    "<<<",
    ">>>",
    "--",
    "---",
    "-(",
    "-[",
    "('",
    "(\"",
    "((",
    "))",
    "(((",
    ")))",
    "[[",
    "]]",
    "{{",
    "}}",
    "♪♪",
    "♪♪♪"
)

private val SYMBOLS_WITH_SPACE = SYMBOLS.map { " $it" } + listOf(" -", " '")

private val MISCELLANEOUS_SYMBOLS = "♩♪♫♬♭♮♯".toSet()

private fun isSymbolToken(token: String): Boolean {
    val normalizedToken = stringifyUnicode(token)
    return SYMBOLS.contains(normalizedToken) || SYMBOLS_WITH_SPACE.contains(normalizedToken) || normalizedToken.toSet()
        .intersect(MISCELLANEOUS_SYMBOLS).isNotEmpty()
}

fun getSymbolTokens(tokenToId: Map<String, Int>): IntArray {
    return tokenToId.filterKeys { isSymbolToken(it) }.values.toIntArray()
}