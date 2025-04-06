package org.futo.voiceinput.shared.whisper

import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

class MultiModelRunnerTest {
    private val modelManager = mock(ModelManager::class.java)
    private val runner = MultiModelRunner(modelManager)

    private data class TestCase(
            val before: String,
            val input: String,
            val after: String,
            val expected: String
    )

    private fun runCases(testCases: List<TestCase>) {
        testCases.forEach { (before, input, after, expected) ->
            assertEquals(
                    "Failed for before='$before', input='$input', after='$after'",
                    before + expected + after,
                    before + runner.sanitizeResult(
                            input,
                            TextContext(before, after)
                    ) + after
            )
        }
    }

    @Test
    fun `sanitizeResult handles null context`() {
        assertEquals("test", runner.sanitizeResult("test", null))
        assertEquals("", runner.sanitizeResult("", null))
        assertEquals("  ", runner.sanitizeResult("  ", null))
    }

    @Test
    fun `sanitizeResult handles blank input`() {
        val context = TextContext("before", "after")
        assertEquals("", runner.sanitizeResult("", context))
        assertEquals("", runner.sanitizeResult("   ", context))
    }

    @Test
    fun `sanitizeResult handles capitalization`() {
        runCases(listOf(
            TestCase("", "hello world", "", "Hello world"),
            TestCase(" ", "hello world", "", "Hello world"),
            TestCase(" ", "Hello world", "", "Hello world"),
            TestCase("First sentence.", "hello world", "", " Hello world"),
            TestCase("xt ", "Hello world", "", "hello world"),
            TestCase("t  ", "hello", " world", "hello"),
            TestCase("Some (text)", "Hello world", "", " hello world"),
            TestCase("First line\n", "hello world", "", "Hello world"),
        ))
    }

    @Test
    fun `sanitizeResult handles acronyms`() {
        runCases(listOf(
            TestCase("Some text that ", "AI approves of.", "", "AI approves of."),
        ))
    }

    @Test
    fun `sanitizeResult handles spacing with surrounding text`() {
        runCases(listOf(
            TestCase("Some", "text", "here", " text "),
            TestCase("Some ", "text", "here", "text "),
            TestCase("Some", "text", " here", " text"),
            TestCase("Some ", "text", " here", "text"),
            TestCase("", "text", "here", "Text "),
            TestCase("Some", "text", "", " text")
        ))
    }

    @Test
    fun `sanitizeResult handles punctuation`() {
        runCases(listOf(
            TestCase("Some", "text", ", and", " text"),
            TestCase("Some", "text ", ". And", " text"),
            TestCase("Some", " text.", "? Yes", " text"),
            TestCase("Some", " text.", " here", " text"),
            TestCase("", "Text.", "", "Text."),
            TestCase("Some", " text here.", "", " text here.")
        ))
    }

    @Test
    fun `sanitizeResult handles brackets`() {
        runCases(listOf(
            TestCase("Some (", " text", "", "text"),
            TestCase("Some (", "text ", "", "text"),
            TestCase("(Some)", "text", "", " text"),
            TestCase("(Some)", " text", "", " text"),
            TestCase("Some", " text ", ")", " text"),
            TestCase("Some", "text", "(", " text "),
            TestCase("Some", "More text ", "]", " more text")
        ))
    }

    @Test
    fun `sanitizeResult preserves internal spacing`() {
        val context = TextContext("Before", "after")
        assertEquals(" hello   world ", runner.sanitizeResult("hello   world", context))
    }

    @Test
    fun `sanitizeResult handles ellipsis`() {
        assertEquals(
                " hello world ",
                runner.sanitizeResult("Hello world...", TextContext("Before", "after"))
        )
    }
}
