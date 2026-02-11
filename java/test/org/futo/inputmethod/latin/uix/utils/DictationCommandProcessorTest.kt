package org.futo.inputmethod.latin.uix.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class DictationCommandProcessorTest {

    private val allEnabled = DictationSettings()
    private val allDisabled = DictationSettings(enabled = false)

    private fun process(text: String, settings: DictationSettings = allEnabled): String {
        return DictationCommandProcessor.process(text, settings)
    }

    // -- Master toggle --

    @Test
    fun testMasterToggleOff_returnsUnchanged() {
        assertEquals("hello new line world", process("hello new line world", allDisabled))
    }

    @Test
    fun testEmptyInput_returnsEmpty() {
        assertEquals("", process(""))
        assertEquals("  ", process("  "))
    }

    @Test
    fun testPlainText_passesThrough() {
        assertEquals("hello world", process("hello world"))
    }

    // -- Formatting commands --

    @Test
    fun testNewLine() {
        assertEquals("hello\nworld", process("hello new line world"))
    }

    @Test
    fun testNewParagraph() {
        assertEquals("hello\n\nworld", process("hello new paragraph world"))
    }

    @Test
    fun testTabKey() {
        assertEquals("hello\tworld", process("hello tab key world"))
    }

    @Test
    fun testNewLineAtStart() {
        assertEquals("\nhello", process("new line hello"))
    }

    @Test
    fun testNewLineAtEnd() {
        assertEquals("hello\n", process("hello new line"))
    }

    // -- Capitalization commands --

    @Test
    fun testCapsOn_titleCase() {
        assertEquals("Hello World", process("caps on hello world"))
    }

    @Test
    fun testCapsOnOff() {
        assertEquals("Hello World back to normal", process("caps on hello world caps off back to normal"))
    }

    @Test
    fun testAllCaps_singleWord() {
        assertEquals("HELLO world", process("all caps hello world"))
    }

    @Test
    fun testAllCapsOn_multipleWords() {
        assertEquals("HELLO WORLD", process("all caps on hello world"))
    }

    @Test
    fun testAllCapsOnOff() {
        assertEquals("HELLO WORLD back to normal", process("all caps on hello world all caps off back to normal"))
    }

    @Test
    fun testCapsOnDoesNotAffectNextSentenceAfterOff() {
        assertEquals("Big small", process("caps on big caps off small"))
    }

    // -- Spacing commands --

    @Test
    fun testNoSpaceOn() {
        assertEquals("helloworld", process("no space on hello world"))
    }

    @Test
    fun testNoSpaceOnOff() {
        assertEquals("helloworld back to normal", process("no space on hello world no space off back to normal"))
    }

    // -- Numeral commands --

    @Test
    fun testNumeral() {
        assertEquals("5", process("numeral five"))
    }

    @Test
    fun testNumeralInContext() {
        assertEquals("I have 3 cats", process("I have numeral three cats"))
    }

    @Test
    fun testRomanNumeral() {
        assertEquals("Chapter V", process("caps on chapter caps off roman numeral five"))
    }

    @Test
    fun testNumeralUnknownWord_passesThrough() {
        assertEquals("banana", process("numeral banana"))
    }

    // -- Punctuation commands --

    @Test
    fun testApostrophe() {
        assertEquals("don't", process("don no space on apostrophe t"))
    }

    @Test
    fun testBrackets() {
        assertEquals("hello [world]", process("hello open square bracket world close square bracket"))
    }

    @Test
    fun testParentheses() {
        assertEquals("hello (world)", process("hello open parenthesis world close parenthesis"))
    }

    @Test
    fun testBraces() {
        assertEquals("hello {world}", process("hello open brace world close brace"))
    }

    @Test
    fun testAngleBrackets() {
        assertEquals("hello <world>", process("hello open angle bracket world close angle bracket"))
    }

    @Test
    fun testClosingBracketsNoSpaceBefore() {
        // Closing brackets/parens should attach to the preceding word
        assertEquals("(hello)", process("open parenthesis hello close parenthesis"))
        assertEquals("[hello]", process("open square bracket hello close square bracket"))
        assertEquals("{hello}", process("open brace hello close brace"))
        assertEquals("<hello>", process("open angle bracket hello close angle bracket"))
    }

    @Test
    fun testSmartQuotes() {
        // end quote attaches to preceding word (no space before)
        assertEquals("he said \u201Chello\u201D", process("he said begin quote hello end quote"))
    }

    @Test
    fun testSmartSingleQuotes() {
        assertEquals("he said \u2018hello\u2019", process("he said begin single quote hello end single quote"))
    }

    @Test
    fun testDash() {
        assertEquals("hello\u2013world", process("hello no space on dash world"))
    }

    @Test
    fun testEllipsis() {
        assertEquals("hello\u2026", process("hello ellipsis"))
    }

    @Test
    fun testHyphen() {
        assertEquals("well-known", process("well no space on hyphen known"))
    }

    @Test
    fun testPeriodFallback() {
        assertEquals("hello.", process("hello period"))
    }

    @Test
    fun testCommaFallback() {
        assertEquals("hello,", process("hello comma"))
    }

    @Test
    fun testQuestionMarkFallback() {
        assertEquals("hello?", process("hello question mark"))
    }

    @Test
    fun testExclamationMarkFallback() {
        assertEquals("hello!", process("hello exclamation mark"))
    }

    @Test
    fun testColonFallback() {
        assertEquals("hello:", process("hello colon"))
    }

    @Test
    fun testSemicolonFallback() {
        assertEquals("hello;", process("hello semicolon"))
    }

    // -- Symbol commands --

    @Test
    fun testAmpersand() {
        assertEquals("rock & roll", process("rock ampersand roll"))
    }

    @Test
    fun testAsterisk() {
        assertEquals("hello * world", process("hello asterisk world"))
    }

    @Test
    fun testAtSign() {
        assertEquals("user @ domain", process("user at sign domain"))
    }

    @Test
    fun testBackslash() {
        assertEquals("path \\ file", process("path backslash file"))
    }

    @Test
    fun testForwardSlash() {
        assertEquals("path / file", process("path forward slash file"))
    }

    @Test
    fun testHashtag() {
        assertEquals("# trending", process("hashtag trending"))
    }

    @Test
    fun testPercentSign() {
        assertEquals("100 %", process("100 percent sign"))
    }

    @Test
    fun testUnderscore() {
        assertEquals("snake _ case", process("snake underscore case"))
    }

    @Test
    fun testVerticalBar() {
        assertEquals("a | b", process("a vertical bar b"))
    }

    @Test
    fun testDegreeSign() {
        assertEquals("72 \u00B0", process("72 degree sign"))
    }

    @Test
    fun testCaret() {
        assertEquals("x ^", process("x caret"))
    }

    // -- Math commands --

    @Test
    fun testEqualSign() {
        assertEquals("x = 5", process("x equal sign 5"))
    }

    @Test
    fun testPlusSign() {
        assertEquals("2 + 3", process("2 plus sign 3"))
    }

    @Test
    fun testMinusSign() {
        assertEquals("5 - 2", process("5 minus sign 2"))
    }

    @Test
    fun testMultiplicationSign() {
        assertEquals("3 \u00D7 4", process("3 multiplication sign 4"))
    }

    @Test
    fun testGreaterThanSign() {
        assertEquals("5 > 3", process("5 greater than sign 3"))
    }

    @Test
    fun testLessThanSign() {
        assertEquals("3 < 5", process("3 less than sign 5"))
    }

    // -- Currency commands --

    @Test
    fun testDollarSign() {
        assertEquals("$ 100", process("dollar sign 100"))
    }

    @Test
    fun testCentSign() {
        assertEquals("50\u00A2", process("50 cent sign"))
    }

    @Test
    fun testPoundSterlingSign() {
        assertEquals("\u00A3 50", process("pound sterling sign 50"))
    }

    @Test
    fun testEuroSign() {
        assertEquals("\u20AC 100", process("euro sign 100"))
    }

    @Test
    fun testYenSign() {
        assertEquals("\u00A5 1000", process("yen sign 1000"))
    }

    // -- Emoticon commands --

    @Test
    fun testSmileyFace() {
        assertEquals("hello :-)", process("hello smiley face"))
    }

    @Test
    fun testFrownyFace() {
        assertEquals(":-(", process("frowny face"))
    }

    @Test
    fun testWinkyFace() {
        assertEquals(";-)", process("winky face"))
    }

    @Test
    fun testCrossEyedLaughingFace() {
        assertEquals("XD", process("cross-eyed laughing face"))
    }

    // -- IP mark commands --

    @Test
    fun testCopyrightSign() {
        assertEquals("\u00A9 2024", process("copyright sign 2024"))
    }

    @Test
    fun testRegisteredSign() {
        assertEquals("Brand \u00AE", process("Brand registered sign"))
    }

    @Test
    fun testTrademarkSign() {
        assertEquals("Name \u2122", process("Name trademark sign"))
    }

    // -- Category toggle tests --

    @Test
    fun testFormattingDisabled_passesThrough() {
        val settings = DictationSettings(formatting = false)
        assertEquals("hello new line world", process("hello new line world", settings))
    }

    @Test
    fun testCapitalizationDisabled_passesThrough() {
        val settings = DictationSettings(capitalization = false)
        assertEquals("hello caps on world", process("hello caps on world", settings))
    }

    @Test
    fun testPunctuationDisabled_passesThrough() {
        val settings = DictationSettings(punctuation = false)
        assertEquals("hello open parenthesis world", process("hello open parenthesis world", settings))
    }

    @Test
    fun testSymbolsDisabled_passesThrough() {
        val settings = DictationSettings(symbols = false)
        assertEquals("hello ampersand world", process("hello ampersand world", settings))
    }

    @Test
    fun testMathDisabled_passesThrough() {
        val settings = DictationSettings(math = false)
        assertEquals("hello equal sign world", process("hello equal sign world", settings))
    }

    @Test
    fun testCurrencyDisabled_passesThrough() {
        val settings = DictationSettings(currency = false)
        assertEquals("hello dollar sign world", process("hello dollar sign world", settings))
    }

    @Test
    fun testEmoticonsDisabled_passesThrough() {
        val settings = DictationSettings(emoticons = false)
        assertEquals("hello smiley face world", process("hello smiley face world", settings))
    }

    @Test
    fun testIpMarksDisabled_passesThrough() {
        val settings = DictationSettings(ipMarks = false)
        assertEquals("hello copyright sign world", process("hello copyright sign world", settings))
    }

    // -- Mixed / complex scenarios --

    @Test
    fun testMixedCommands() {
        assertEquals(
            "Dear Sir,\nI have $ 100.",
            process("caps on dear sir caps off comma new line I have dollar sign 100 period")
        )
    }

    @Test
    fun testAllCapsWithSymbols() {
        assertEquals("HELLO @ world", process("all caps hello at sign world"))
    }

    @Test
    fun testNoSpaceWithSymbols() {
        assertEquals("user@domain", process("no space on user at sign domain"))
    }

    @Test
    fun testMultipleNewLines() {
        assertEquals("a\nb\nc", process("a new line b new line c"))
    }

    @Test
    fun testCapsModeResetByOff() {
        assertEquals("Hello world test", process("caps on hello caps off world test"))
    }

    @Test
    fun testNumeralFollowedByCommand() {
        assertEquals("5\n", process("numeral five new line"))
    }

    @Test
    fun testCaseInsensitiveMatching() {
        assertEquals("hello\nworld", process("hello New Line world"))
    }

    @Test
    fun testSingleWordInput() {
        assertEquals("hello", process("hello"))
    }

    @Test
    fun testCommandOnly() {
        assertEquals("\n", process("new line"))
    }

    @Test
    fun testConsecutiveCommands() {
        assertEquals("\n\n", process("new line new line"))
    }

    @Test
    fun testFormattingDoesNotAddExtraSpaceAfterNewline() {
        // After a newline, the next word should not have a leading space
        assertEquals("hello\nworld", process("hello new line world"))
    }

    // -- Additional punctuation aliases --

    @Test
    fun testPointFallback() {
        assertEquals("hello.", process("hello point"))
    }

    @Test
    fun testDotFallback() {
        assertEquals("hello.", process("hello dot"))
    }

    @Test
    fun testFullStopFallback() {
        assertEquals("hello.", process("hello full stop"))
    }

    // -- Additional symbol coverage --

    @Test
    fun testCenterDot() {
        assertEquals("hello \u00B7 world", process("hello center dot world"))
    }

    @Test
    fun testLargeCenterDot() {
        assertEquals("hello \u25CF world", process("hello large center dot world"))
    }

    @Test
    fun testPoundSign() {
        assertEquals("# 5", process("pound sign 5"))
    }

    // -- Edge cases for dangling state flags --

    @Test
    fun testNumeralAtEnd_passesThrough() {
        // "numeral" as last word should pass through (no following word to convert)
        assertEquals("numeral", process("numeral"))
    }

    @Test
    fun testRomanNumeralAtEnd_passesThrough() {
        // "roman numeral" as last words should pass through
        assertEquals("roman numeral", process("roman numeral"))
    }

    @Test
    fun testNumeralFollowedByCommand_resetsFlag() {
        // "numeral" then a command — the numeral flag should not leak to later words
        assertEquals("\nfive", process("numeral new line five"))
    }

    @Test
    fun testAllCapsNextConsumedInTitleCaseMode() {
        // "all caps" then "caps on" — the allCapsNextWord flag should be consumed, not leak
        assertEquals("Hello World", process("all caps caps on hello world"))
    }

    // -- Punctuation attaches to preceding word --

    @Test
    fun testPunctuationNoSpaceBefore() {
        assertEquals("hello. world", process("hello period world"))
        assertEquals("hello, world", process("hello comma world"))
        assertEquals("hello! world", process("hello exclamation mark world"))
        assertEquals("hello? world", process("hello question mark world"))
        assertEquals("hello: world", process("hello colon world"))
        assertEquals("hello; world", process("hello semicolon world"))
    }

    @Test
    fun testEllipsisNoSpaceBefore() {
        assertEquals("hello\u2026 world", process("hello ellipsis world"))
    }

    @Test
    fun testDashNoSpaceBefore() {
        assertEquals("hello\u2013 world", process("hello dash world"))
    }

    // -- Whisper auto-punctuation tolerance --

    @Test
    fun testWhisperCommaOnCommandWord() {
        // Whisper may output "hello, new line, world" — commas should not break matching
        assertEquals("hello\nworld", process("hello, new line, world"))
    }

    @Test
    fun testWhisperPeriodOnCommandWord() {
        // Whisper may output "hello. New line. World."
        assertEquals("hello\nworld.", process("hello. new line. world."))
    }

    @Test
    fun testWhisperPunctOnSingleWordCommand() {
        // "ampersand," should still match ampersand
        assertEquals("hello & world", process("hello ampersand, world"))
    }

    @Test
    fun testWhisperPunctOnMultiWordCommand() {
        // "question mark" has NO_SPACE_BEFORE — attaches to preceding word
        assertEquals("hello? world", process("hello question mark, world"))
    }

    @Test
    fun testWhisperPunctBeforeNewLine() {
        // Comma before "new line" should be stripped from output
        assertEquals("hello\nworld", process("hello, new line world"))
    }

    @Test
    fun testWhisperPunctOnStatefulCommands() {
        assertEquals("Hello World", process("caps on, hello world"))
    }

    // -- Cursor-context whitespace preservation --

    @Test
    fun testLeadingSpacePreserved() {
        // Sanitizer adds leading space for cursor context — should be preserved
        assertEquals(" hello world", process(" hello world"))
    }

    @Test
    fun testLeadingSpaceSuppressedBeforeNewline() {
        // Leading space makes no sense before a newline
        assertEquals("\nhello", process(" new line hello"))
    }

    @Test
    fun testTrailingSpacePreserved() {
        assertEquals("hello world ", process("hello world "))
    }

    @Test
    fun testTrailingSpaceSuppressedAfterNewline() {
        assertEquals("hello\n", process("hello new line "))
    }
}
