package org.futo.inputmethod.latin.uix.utils;

import android.view.inputmethod.InputMethodSubtype;

import androidx.test.InstrumentationRegistry;

import org.futo.inputmethod.latin.RichInputMethodManager;
import org.futo.inputmethod.latin.Subtypes;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ModelOutputSanitizerTest {
    private final InputMethodSubtype mockSubtypeEn = Subtypes.INSTANCE.makeSubtype("en_US", "qwerty");
    private final InputMethodSubtype mockSubtypeDe = Subtypes.INSTANCE.makeSubtype("de_DE", "qwertz");

    private static String sanitize(String input, TextContext context) {
        return ModelOutputSanitizer.sanitize(input, context);
    }

    private static class TestCase {
        final String before;
        final String input;
        final String after;
        final String expected;

        TestCase(String before, String input, String after, String expected) {
            this.before = before;
            this.input = input;
            this.after = after;
            this.expected = expected;
        }
    }

    private void runCases(List<TestCase> testCases) {
        for (TestCase testCase : testCases) {
            String before = testCase.before;
            String input = testCase.input;
            String after = testCase.after;
            String expected = testCase.expected;

            Assert.assertEquals(
                    "Failed for before='" + before + "', input='" + input + "', after='" + after + "'",
                    before + expected + after,
                    before + sanitize(
                            input,
                            new TextContext(before, after)
                    ) + after
            );
        }
    }

    @Before
    public void setUp() throws Exception {
        RichInputMethodManager.init(InstrumentationRegistry.getTargetContext());
        RichInputMethodManager.forceSubtype(mockSubtypeEn);
    }

    @Test
    public void testHandlesNullContext() {
        Assert.assertEquals("test", sanitize("test", null));
        Assert.assertEquals("", sanitize("", null));
        Assert.assertEquals("  ", sanitize("  ", null));
    }

    @Test
    public void testHandlesBlankInput() {
        TextContext context = new TextContext("before", "after");
        Assert.assertEquals("", sanitize("", context));
        Assert.assertEquals("", sanitize("   ", context));
    }

    @Test
    public void testHandlesCapitalization() {
        runCases(Arrays.asList(
            new TestCase("", "hello world", "", "Hello world"),
            new TestCase(" ", "hello world", "", "Hello world"),
            new TestCase(" ", "Hello world", "", "Hello world"),
            new TestCase("First sentence.", "hello world", "", " Hello world"),
            new TestCase("xt ", "Hello world", "", "hello world"),
            new TestCase("t  ", "hello", " world", "hello"),
            new TestCase("Some (text)", "Hello world", "", " hello world"),
            new TestCase("First line\n", "hello world", "", "Hello world"),
            new TestCase("Hello world,", "You", "say", " you "),
            new TestCase("Hello world,", "I", "say", " I "),
            new TestCase("Hello world", "Inside", "another world", " inside ")
        ));

        RichInputMethodManager.forceSubtype(mockSubtypeDe);
        runCases(Arrays.asList(
            new TestCase("Hallo,", "I", "bin...", " i ")
        ));
        RichInputMethodManager.forceSubtype(mockSubtypeEn);
    }

    @Test
    public void testHandlesAcronyms() {
        runCases(Arrays.asList(
            new TestCase("Some text that ", "AI approves of.", "", "AI approves of.")
        ));
    }

    @Test
    public void testHandlesSpacingWithSurroundingText() {
        runCases(Arrays.asList(
            new TestCase("Some", "text", "here", " text "),
            new TestCase("Some ", "text", "here", "text "),
            new TestCase("Some", "text", " here", " text"),
            new TestCase("Some ", "text", " here", "text"),
            new TestCase("", "text", "here", "Text "),
            new TestCase("Some", "text", "", " text")
        ));
    }

    @Test
    public void testHandlesPunctuation() {
        runCases(Arrays.asList(
            new TestCase("Some", "text", ", and", " text"),
            new TestCase("Some", "text ", ". And", " text"),
            new TestCase("Some", " text.", "? Yes", " text"),
            new TestCase("Some", " text.", " here", " text"),
            new TestCase("", "Text.", "", "Text."),
            new TestCase("Some", " text here.", "", " text here."),
            new TestCase("Some -", "text", "- here", " text "),
            new TestCase("Some—", "text", "—here", "text")
        ));
    }

    @Test
    public void testHandlesBrackets() {
        runCases(Arrays.asList(
            new TestCase("Some (", " text", "", "text"),
            new TestCase("Some (", "text ", "", "text"),
            new TestCase("(Some)", "text", "", " text"),
            new TestCase("(Some)", " text", "", " text"),
            new TestCase("Some", " text ", ")", " text"),
            new TestCase("Some", "text", "(", " text "),
            new TestCase("Some", "More text ", "]", " more text")
        ));
    }

    @Test
    public void testPreservesInternalSpacing() {
        TextContext context = new TextContext("Before", "after");
        Assert.assertEquals(" hello   world ", ModelOutputSanitizer.sanitize("hello   world", context));
    }

    @Test
    public void testHandlesEllipsis() {
        Assert.assertEquals(
                " hello world ",
                ModelOutputSanitizer.sanitize("Hello world...", new TextContext("Before", "after"))
        );
    }
}