package org.futo.inputmethod.latin.uix.utils

data class DictationSettings(
    val enabled: Boolean = true,
    val formatting: Boolean = true,
    val capitalization: Boolean = true,
    val punctuation: Boolean = true,
    val symbols: Boolean = true,
    val math: Boolean = true,
    val currency: Boolean = true,
    val emoticons: Boolean = true,
    val ipMarks: Boolean = true
)

/**
 * Dictation command processor for FUTO Keyboard voice input.
 *
 * Intercepts Whisper transcription output and replaces spoken command phrases
 * (e.g., "new line", "caps on", "dollar sign") with the corresponding characters
 * or formatting. Runs after [ModelOutputSanitizer] in the voice input pipeline.
 *
 * Each command category can be independently toggled via [DictationSettings].
 */
object DictationCommandProcessor {

    private enum class Spacing { NORMAL, NO_SPACE_BEFORE, NO_SPACE_AFTER, NO_SPACE_EITHER }

    private data class Replacement(val text: String, val spacing: Spacing = Spacing.NORMAL)

    // -- Formatting commands --
    private val formattingCommands = mapOf(
        "new line" to Replacement("\n", Spacing.NO_SPACE_EITHER),
        "new paragraph" to Replacement("\n\n", Spacing.NO_SPACE_EITHER),
        "tab key" to Replacement("\t", Spacing.NO_SPACE_EITHER)
    )

    // -- Punctuation & bracket commands --
    private val punctuationCommands = mapOf(
        "apostrophe" to Replacement("'", Spacing.NO_SPACE_BEFORE),
        "open square bracket" to Replacement("[", Spacing.NO_SPACE_AFTER),
        "close square bracket" to Replacement("]", Spacing.NO_SPACE_BEFORE),
        "open parenthesis" to Replacement("(", Spacing.NO_SPACE_AFTER),
        "close parenthesis" to Replacement(")", Spacing.NO_SPACE_BEFORE),
        "open brace" to Replacement("{", Spacing.NO_SPACE_AFTER),
        "close brace" to Replacement("}", Spacing.NO_SPACE_BEFORE),
        "open angle bracket" to Replacement("<", Spacing.NO_SPACE_AFTER),
        "close angle bracket" to Replacement(">", Spacing.NO_SPACE_BEFORE),
        "dash" to Replacement("\u2013", Spacing.NO_SPACE_BEFORE),       // en-dash –
        "ellipsis" to Replacement("\u2026", Spacing.NO_SPACE_BEFORE),   // …
        "hyphen" to Replacement("-", Spacing.NO_SPACE_BEFORE),
        "quote" to Replacement("\u201C", Spacing.NO_SPACE_AFTER),       // left double smart quote "
        "begin quote" to Replacement("\u201C", Spacing.NO_SPACE_AFTER),
        "end quote" to Replacement("\u201D", Spacing.NO_SPACE_BEFORE),  // right double smart quote "
        "begin single quote" to Replacement("\u2018", Spacing.NO_SPACE_AFTER), // '
        "end single quote" to Replacement("\u2019", Spacing.NO_SPACE_BEFORE),  // '
        "period" to Replacement(".", Spacing.NO_SPACE_BEFORE),
        "point" to Replacement(".", Spacing.NO_SPACE_BEFORE),
        "dot" to Replacement(".", Spacing.NO_SPACE_BEFORE),
        "full stop" to Replacement(".", Spacing.NO_SPACE_BEFORE),
        "comma" to Replacement(",", Spacing.NO_SPACE_BEFORE),
        "exclamation mark" to Replacement("!", Spacing.NO_SPACE_BEFORE),
        "exclamation point" to Replacement("!", Spacing.NO_SPACE_BEFORE),
        "exclamation" to Replacement("!", Spacing.NO_SPACE_BEFORE),
        "question mark" to Replacement("?", Spacing.NO_SPACE_BEFORE),
        "colon" to Replacement(":", Spacing.NO_SPACE_BEFORE),
        "semicolon" to Replacement(";", Spacing.NO_SPACE_BEFORE)
    )

    // -- Typography symbol commands --
    private val symbolCommands = mapOf(
        "ampersand" to Replacement("&"),
        "asterisk" to Replacement("*"),
        "at sign" to Replacement("@"),
        "backslash" to Replacement("\\"),
        "forward slash" to Replacement("/"),
        "caret" to Replacement("^"),
        "center dot" to Replacement("\u00B7"),       // ·
        "large center dot" to Replacement("\u25CF"),  // ●
        "degree sign" to Replacement("\u00B0"),       // °
        "hashtag" to Replacement("#"),
        "pound sign" to Replacement("#"),
        "percent sign" to Replacement("%"),
        "underscore" to Replacement("_"),
        "vertical bar" to Replacement("|")
    )

    // -- Math symbol commands --
    private val mathCommands = mapOf(
        "equal sign" to Replacement("="),
        "greater than sign" to Replacement(">"),
        "less than sign" to Replacement("<"),
        "minus sign" to Replacement("-"),
        "multiplication sign" to Replacement("\u00D7"), // ×
        "plus sign" to Replacement("+")
    )

    // -- Currency symbol commands --
    private val currencyCommands = mapOf(
        "dollar sign" to Replacement("$"),
        "cent sign" to Replacement("\u00A2", Spacing.NO_SPACE_BEFORE),  // ¢
        "pound sterling sign" to Replacement("\u00A3"),                  // £
        "euro sign" to Replacement("\u20AC"),                            // €
        "yen sign" to Replacement("\u00A5")                              // ¥
    )

    // -- Emoticon commands --
    private val emoticonCommands = mapOf(
        "smiley face" to Replacement(":-)"),
        "frowny face" to Replacement(":-("),
        "winky face" to Replacement(";-)"),
        "cross-eyed laughing face" to Replacement("XD")
    )

    // -- Intellectual property mark commands --
    private val ipMarkCommands = mapOf(
        "copyright sign" to Replacement("\u00A9"),  // ©
        "registered sign" to Replacement("\u00AE"), // ®
        "trademark sign" to Replacement("\u2122")   // ™
    )

    // -- Number word to digit mappings --
    private val numberWords = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19, "twenty" to 20, "thirty" to 30,
        "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70,
        "eighty" to 80, "ninety" to 90, "hundred" to 100, "thousand" to 1000
    )

    // -- Roman numeral mappings --
    private val romanNumerals = mapOf(
        1 to "I", 2 to "II", 3 to "III", 4 to "IV", 5 to "V",
        6 to "VI", 7 to "VII", 8 to "VIII", 9 to "IX", 10 to "X",
        11 to "XI", 12 to "XII", 13 to "XIII", 14 to "XIV", 15 to "XV",
        16 to "XVI", 17 to "XVII", 18 to "XVIII", 19 to "XIX", 20 to "XX",
        30 to "XXX", 40 to "XL", 50 to "L", 60 to "LX", 70 to "LXX",
        80 to "LXXX", 90 to "XC", 100 to "C", 1000 to "M"
    )

    private enum class CapsMode { NONE, TITLE_CASE, ALL_CAPS }

    private val whisperPunct = charArrayOf('.', ',', '!', '?', ';', ':')

    private fun String.stripTrailingPunct(): String = this.trimEnd(*whisperPunct)

    private fun stripTrailingPunct(sb: StringBuilder) {
        while (sb.isNotEmpty() && sb.last() in whisperPunct) {
            sb.deleteCharAt(sb.length - 1)
        }
    }

    /**
     * Replaces spoken command phrases with their corresponding characters or formatting.
     * Returns [text] unchanged if [DictationSettings.enabled] is false.
     */
    @JvmStatic
    fun process(text: String, settings: DictationSettings): String {
        if (!settings.enabled || text.isBlank()) return text

        val leadingSpace = text.takeWhile { it == ' ' }
        val trailingSpace = text.takeLastWhile { it == ' ' }
        val content = text.trim()
        if (content.isEmpty()) return text

        val activeCommands = buildActiveCommands(settings)

        val words = content.split(" ")
        val result = StringBuilder()
        var capsMode = CapsMode.NONE
        var allCapsNextWord = false
        var noSpaceMode = false
        var numeralNextWord = false
        var romanNumeralNextWord = false
        var suppressNextSpace = false
        var lastWasCommand = false
        var i = 0

        while (i < words.size) {
            val matchResult = tryMatchCommand(words, i, activeCommands, settings)

            if (matchResult != null) {
                if (matchResult.type != CommandType.NUMERAL &&
                    matchResult.type != CommandType.ROMAN_NUMERAL) {
                    numeralNextWord = false
                    romanNumeralNextWord = false
                }

                when (matchResult.type) {
                    CommandType.CAPS_ON -> {
                        capsMode = CapsMode.TITLE_CASE
                        i += matchResult.wordsConsumed
                        continue
                    }
                    CommandType.CAPS_OFF -> {
                        capsMode = CapsMode.NONE
                        i += matchResult.wordsConsumed
                        continue
                    }
                    CommandType.ALL_CAPS_NEXT -> {
                        allCapsNextWord = true
                        i += matchResult.wordsConsumed
                        continue
                    }
                    CommandType.ALL_CAPS_ON -> {
                        capsMode = CapsMode.ALL_CAPS
                        i += matchResult.wordsConsumed
                        continue
                    }
                    CommandType.ALL_CAPS_OFF -> {
                        capsMode = CapsMode.NONE
                        i += matchResult.wordsConsumed
                        continue
                    }
                    CommandType.NO_SPACE_ON -> {
                        noSpaceMode = true
                        i += matchResult.wordsConsumed
                        continue
                    }
                    CommandType.NO_SPACE_OFF -> {
                        noSpaceMode = false
                        i += matchResult.wordsConsumed
                        continue
                    }
                    CommandType.NUMERAL -> {
                        numeralNextWord = true
                        i += matchResult.wordsConsumed
                        continue
                    }
                    CommandType.ROMAN_NUMERAL -> {
                        romanNumeralNextWord = true
                        i += matchResult.wordsConsumed
                        continue
                    }
                    CommandType.REPLACEMENT -> {
                        val spacing = matchResult.spacing
                        val skipBefore = noSpaceMode ||
                            spacing == Spacing.NO_SPACE_BEFORE ||
                            spacing == Spacing.NO_SPACE_EITHER ||
                            suppressNextSpace
                        if (skipBefore && result.isNotEmpty() && !lastWasCommand) {
                            stripTrailingPunct(result)
                        }
                        if (result.isNotEmpty() && !skipBefore) {
                            result.append(" ")
                        }
                        result.append(matchResult.replacement)
                        suppressNextSpace = spacing == Spacing.NO_SPACE_AFTER ||
                            spacing == Spacing.NO_SPACE_EITHER
                        lastWasCommand = true
                        i += matchResult.wordsConsumed
                        continue
                    }
                }
            }

            var word = words[i]

            if (numeralNextWord && settings.formatting) {
                val num = numberWords[word.stripTrailingPunct().lowercase()]
                if (num != null) {
                    word = num.toString()
                }
                numeralNextWord = false
            } else if (romanNumeralNextWord && settings.formatting) {
                val num = numberWords[word.stripTrailingPunct().lowercase()]
                if (num != null) {
                    val roman = romanNumerals[num]
                    if (roman != null) {
                        word = roman
                    }
                }
                romanNumeralNextWord = false
            }

            word = when (capsMode) {
                CapsMode.TITLE_CASE -> {
                    allCapsNextWord = false
                    word.replaceFirstChar { it.uppercaseChar() }
                }
                CapsMode.ALL_CAPS -> {
                    allCapsNextWord = false
                    word.uppercase()
                }
                CapsMode.NONE -> {
                    if (allCapsNextWord) {
                        allCapsNextWord = false
                        word.uppercase()
                    } else {
                        word
                    }
                }
            }

            if (result.isNotEmpty() && !noSpaceMode && !suppressNextSpace) {
                result.append(" ")
            }
            suppressNextSpace = false
            lastWasCommand = false
            result.append(word)
            i++
        }

        val body = result.toString()

        val prefix = if (leadingSpace.isNotEmpty() && body.isNotEmpty() &&
            body[0] != '\n' && body[0] != '\t') leadingSpace else ""
        val suffix = if (trailingSpace.isNotEmpty() && body.isNotEmpty() &&
            body.last() != '\n' && body.last() != '\t') trailingSpace else ""

        return prefix + body + suffix
    }

    private data class CommandMatch(
        val type: CommandType,
        val replacement: String,
        val spacing: Spacing,
        val wordsConsumed: Int
    )

    private enum class CommandType {
        REPLACEMENT,
        CAPS_ON, CAPS_OFF,
        ALL_CAPS_NEXT, ALL_CAPS_ON, ALL_CAPS_OFF,
        NO_SPACE_ON, NO_SPACE_OFF,
        NUMERAL, ROMAN_NUMERAL
    }

    private fun buildActiveCommands(settings: DictationSettings): Map<String, Replacement> {
        val commands = mutableMapOf<String, Replacement>()
        if (settings.formatting) commands.putAll(formattingCommands)
        if (settings.punctuation) commands.putAll(punctuationCommands)
        if (settings.symbols) commands.putAll(symbolCommands)
        if (settings.math) commands.putAll(mathCommands)
        if (settings.currency) commands.putAll(currencyCommands)
        if (settings.emoticons) commands.putAll(emoticonCommands)
        if (settings.ipMarks) commands.putAll(ipMarkCommands)
        return commands
    }

    private fun tryMatchCommand(
        words: List<String>,
        startIndex: Int,
        activeCommands: Map<String, Replacement>,
        settings: DictationSettings
    ): CommandMatch? {
        if (settings.capitalization) {
            matchStatefulCommand(words, startIndex)?.let { return it }
        }
        if (settings.formatting) {
            matchFormattingStatefulCommand(words, startIndex)?.let { return it }
        }

        for (length in minOf(4, words.size - startIndex) downTo 2) {
            val phrase = words.subList(startIndex, startIndex + length)
                .joinToString(" ") { it.stripTrailingPunct().lowercase() }
            val replacement = activeCommands[phrase]
            if (replacement != null) {
                return CommandMatch(CommandType.REPLACEMENT, replacement.text, replacement.spacing, length)
            }
        }

        val singleWord = words[startIndex].stripTrailingPunct().lowercase()
        val replacement = activeCommands[singleWord]
        if (replacement != null) {
            return CommandMatch(CommandType.REPLACEMENT, replacement.text, replacement.spacing, 1)
        }

        return null
    }

    private fun matchStatefulCommand(words: List<String>, startIndex: Int): CommandMatch? {
        val remaining = words.size - startIndex
        val w0 = words[startIndex].stripTrailingPunct().lowercase()

        if (remaining >= 3) {
            val phrase3 = "$w0 ${words[startIndex + 1].stripTrailingPunct().lowercase()} ${words[startIndex + 2].stripTrailingPunct().lowercase()}"
            when (phrase3) {
                "all caps on" -> return CommandMatch(CommandType.ALL_CAPS_ON, "", Spacing.NORMAL, 3)
                "all caps off" -> return CommandMatch(CommandType.ALL_CAPS_OFF, "", Spacing.NORMAL, 3)
            }
        }

        if (remaining >= 2) {
            val phrase2 = "$w0 ${words[startIndex + 1].stripTrailingPunct().lowercase()}"
            when (phrase2) {
                "caps on" -> return CommandMatch(CommandType.CAPS_ON, "", Spacing.NORMAL, 2)
                "caps off" -> return CommandMatch(CommandType.CAPS_OFF, "", Spacing.NORMAL, 2)
                "all caps" -> return CommandMatch(CommandType.ALL_CAPS_NEXT, "", Spacing.NORMAL, 2)
            }
        }

        return null
    }

    private fun matchFormattingStatefulCommand(words: List<String>, startIndex: Int): CommandMatch? {
        val remaining = words.size - startIndex
        val w0 = words[startIndex].stripTrailingPunct().lowercase()

        if (remaining >= 3) {
            val phrase3 = "$w0 ${words[startIndex + 1].stripTrailingPunct().lowercase()} ${words[startIndex + 2].stripTrailingPunct().lowercase()}"
            when (phrase3) {
                "no space on" -> return CommandMatch(CommandType.NO_SPACE_ON, "", Spacing.NORMAL, 3)
                "no space off" -> return CommandMatch(CommandType.NO_SPACE_OFF, "", Spacing.NORMAL, 3)
            }
        }

        if (remaining >= 2 && w0 == "numeral") {
            return CommandMatch(CommandType.NUMERAL, "", Spacing.NORMAL, 1)
        }

        if (remaining >= 3) {
            val phrase2 = "$w0 ${words[startIndex + 1].stripTrailingPunct().lowercase()}"
            if (phrase2 == "roman numeral") {
                return CommandMatch(CommandType.ROMAN_NUMERAL, "", Spacing.NORMAL, 2)
            }
        }

        return null
    }
}
