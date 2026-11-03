package org.futo.inputmethod.latin.uix.actions.emoji

import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class EmojiVisibilityTests {
    @Test
    fun presentationSelectorsNormalizeToSameEmoji() {
        assertEquals(
            normalizeEmojiForVisibility("❤"),
            normalizeEmojiForVisibility("❤️")
        )
    }

    @Test
    fun skinToneVariantsNormalizeToSameEmoji() {
        assertEquals(
            normalizeEmojiForVisibility("👍"),
            normalizeEmojiForVisibility("👍🏽")
        )
    }

    @Test
    fun hiddenEmojiMatchesPresentationAndSkinToneVariants() {
        val hidden = normalizeHiddenEmojiSet(setOf("👍", "❤️"))

        assertTrue(isEmojiHidden("👍🏿", hidden))
        assertTrue(isEmojiHidden("❤", hidden))
        assertFalse(isEmojiHidden("👎", hidden))
    }

    @Test
    fun distinctZwjSequencesStayDistinct() {
        assertNotEquals(
            normalizeEmojiForVisibility("👩‍⚕️"),
            normalizeEmojiForVisibility("👨‍⚕️")
        )
    }

    @Test
    fun adjacentEmojiInputSplitsIntoCatalogEntries() {
        val known = listOf("😀", "😃", "👍", "❤️")

        assertEquals(
            listOf("😀", "😃", "👍", "❤️"),
            parseEmojiSequenceForHiding("😀😃👍🏽❤️", known)
        )
    }

    @Test
    fun parserPrefersWholeZwjSequence() {
        val known = listOf("👩", "⚕️", "👩‍⚕️")

        assertEquals(
            listOf("👩‍⚕️"),
            parseEmojiSequenceForHiding("👩‍⚕️", known)
        )
    }

    @Test
    fun parserKeepsFlagSequencesTogether() {
        val known = listOf("🇺🇸", "🇨🇦")

        assertEquals(
            listOf("🇺🇸", "🇨🇦"),
            parseEmojiSequenceForHiding("🇺🇸🇨🇦", known)
        )
    }

    @Test
    fun parserRejectsUnmatchedText() {
        assertNull(
            parseEmojiSequenceForHiding("😀hello😃", listOf("😀", "😃"))
        )
    }
}
