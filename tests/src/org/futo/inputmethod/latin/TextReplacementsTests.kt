package org.futo.inputmethod.latin

import junit.framework.TestCase

class TextReplacementsTests : TestCase() {
    private val replacements = listOf(
        TextReplacement("omw", "On my way!"),
        TextReplacement("en", "English"),
        TextReplacement("@@", "example@example.com"),
        TextReplacement("addr", "Line one\nLine two")
    )

    private val trie = ReverseTrie(replacements)

    /** Matches as the input path does: the whole buffer ends at the cursor. */
    private fun match(text: String, separatorTyped: Boolean): TextReplacement? =
        trie.findEndingAt(text, text.length) {
            if (separatorTyped) !it.expandsImmediately() else it.expandsImmediately()
        }

    fun testExpandsShortcutTerminatedBySeparator() {
        assertEquals("On my way!", match("omw", true)?.replacement)
        assertEquals("On my way!", match("I'm omw", true)?.replacement)
    }

    fun testDoesNotExpandWithoutSeparator() {
        assertNull(match("omw", false))
    }

    fun testRequiresWordBoundaryBeforeShortcut() {
        // "then" must not become "thEnglish"
        assertNull(match("then", true))
        assertEquals("English", match("en", true)?.replacement)
        assertEquals("English", match("speaks en", true)?.replacement)
        assertEquals("English", match("(en", true)?.replacement)
    }

    fun testSymbolOnlyShortcutExpandsImmediately() {
        assertEquals("example@example.com", match("@@", false)?.replacement)
        assertEquals("example@example.com", match("mail me at @@", false)?.replacement)
        // No word boundary is required before a symbol-only shortcut
        assertEquals("example@example.com", match("foo@@", false)?.replacement)
    }

    fun testSymbolOnlyShortcutDoesNotWaitForSeparator() {
        assertNull(match("@@", true))
    }

    fun testMatchingIgnoresCase() {
        // Auto-capitalization turns a shortcut at the start of a sentence into "Omw"
        assertEquals("On my way!", match("Omw", true)?.replacement)
        assertEquals("On my way!", match("OMW", true)?.replacement)
        // The replacement is committed as entered, not recased to match the shortcut
        assertEquals("English", match("EN", true)?.replacement)
        // The word boundary rule still applies regardless of case
        assertNull(match("THEN", true))
    }

    fun testMultilineReplacement() {
        assertEquals("Line one\nLine two", match("addr", true)?.replacement)
    }

    fun testNoMatch() {
        assertNull(match("hello", true))
        assertNull(match("", true))
    }

    fun testLongestShortcutWins() {
        val trie = ReverseTrie(
            listOf(
                TextReplacement("br", "brother"),
                TextReplacement("bbr", "big brother")
            )
        )
        val result = trie.findEndingAt("bbr", 3) { true }
        assertEquals("big brother", result?.replacement)
    }

    fun testLookbehindLengthCoversLongestShortcutPlusBoundary() {
        assertEquals(5, trie.maxShortcutLength + 1)
    }

    fun testEmptyEntriesAreIgnored() {
        val trie = ReverseTrie(
            listOf(
                TextReplacement("", "nothing"),
                TextReplacement("x", "")
            )
        )
        assertNull(trie.findEndingAt("x", 1) { true })
    }

    fun testRoundTripEncoding() {
        val encoded = TextReplacements.encode(replacements)
        assertEquals(replacements, TextReplacements.decode(encoded))
    }

    fun testDecodeOfGarbageDoesNotThrow() {
        assertEquals(emptyList<TextReplacement>(), TextReplacements.decode("not json"))
        assertEquals(emptyList<TextReplacement>(), TextReplacements.decode(""))
    }
}
