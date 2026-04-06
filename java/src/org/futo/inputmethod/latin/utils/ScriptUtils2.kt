package org.futo.inputmethod.latin.utils

import java.util.Locale

data class MultiRange(val ranges: List<LongRange>) {
    val hugeRange = ranges.sortedBy { it.first }.let {
        it.first().first..it.last().last
    }

    fun isInRange(v: Int): Boolean {
        if(v !in hugeRange) return false
        return ranges.any { v in it }
    }
}

enum class IncompatibleScript(val range: MultiRange, val languages: List<String>) {
    CJK(
        MultiRange(listOf(
            // Source: https://stackoverflow.com/a/56311158
            0x1100L..0x11FFL, // Hangul Jamo
            0x2E80L..0x2EFFL, // Radicals Supplement
            0x2F00L..0x2FDFL, // Kangxi Radicals
            0x3000L..0x303FL, // CJK Symbols and Punctuation
            0x3040L..0x309FL, // Hiragana
            0x30A0L..0x30FFL, // Katakana
            0x3100L..0x312FL, // Bopomofo
            0x3130L..0x318FL, // Hangul Compatibility Jamo
            0x3190L..0x319FL, // Kanbun
            0x31A0L..0x31BFL, // Bopomofo Extended
            0x31C0L..0x31EFL, // CJK Strokes
            0x31F0L..0x31FFL, // Katakana Phonetic Extensions
            0x3200L..0x32FFL, // Enclosed CJK Letters and Months
            0x3300L..0x33FFL, // CJK Compatibility
            0x3400L..0x4DBFL, // CJK Unified Ideographs Extension A
            0x4E00L..0x9FEFL, // CJK Unified Ideographs
            0xA960L..0xA97FL, // Hangul Jamo Extended-A
            0xAC00L..0xD7A3L, // Hangul Syllables
            0xD7B0L..0xD7FFL, // Hangul Jamo Extended-B
            0xF900L..0xFAFFL, // CJK Compatibility Ideographs
            0xFE30L..0xFE4FL, // CJK Compatibility Forms
            0xFF00L..0xFFEFL, // Halfwidth and Fullwidth Forms
            0x1B000L..0x1B0FFL, // Kana Supplement
            0x1B100L..0x1B12FL, // Kana Extended-A
            0x1B130L..0x1B16FL, // Small Kana Extension
            0x1F200L..0x1F2FFL, // Enclosed Ideographic Supplement
            0x20000L..0x2A6DFL, // CJK Unified Ideographs Extension B
            0x2A700L..0x2B73FL, // CJK Unified Ideographs Extension C
            0x2B740L..0x2B81FL, // CJK Unified Ideographs Extension D
            0x2B820L..0x2CEAFL, // CJK Unified Ideographs Extension E
            0x2CEB0L..0x2EBEFL, // CJK Unified Ideographs Extension F
            0x2F800L..0x2FA1FL, // CJK Compatibility Ideographs Supplement
            0x30000L..0x3134FL, // CJK Unified Ideographs Extension G
        )),
        listOf("ja", "zh", "ko")
    ),
}



object ScriptUtils2 {
    /// Returns true if the given codepoint is definitely incompatible for the given locale
    /// Mainly to avoid mixing CJK letters with English letters
    @JvmStatic
    fun isLetterDefinitelyIncompatibleForLocale(c: Int, locale: Locale) =
        IncompatibleScript.entries.firstOrNull {
            it.range.isInRange(c)
        }?.languages?.contains(locale.language) == false
}