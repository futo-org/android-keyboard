/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.latin.utils;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.inputmethod.latin.utils.CsvUtils.CsvParseException;

import java.util.Arrays;

@SmallTest
public class CsvUtilsTests extends AndroidTestCase {
    public void testUnescape() {
        assertEquals("", CsvUtils.unescapeField(""));
        assertEquals("text", CsvUtils.unescapeField("text")); // text
        assertEquals("", CsvUtils.unescapeField("\"\"")); // ""
        assertEquals("\"", CsvUtils.unescapeField("\"\"\"\"")); // """" -> "
        assertEquals("text", CsvUtils.unescapeField("\"text\"")); // "text" -> text
        assertEquals("\"text", CsvUtils.unescapeField("\"\"\"text\"")); // """text" -> "text
        assertEquals("text\"", CsvUtils.unescapeField("\"text\"\"\"")); // "text""" -> text"
        assertEquals("te\"xt", CsvUtils.unescapeField("\"te\"\"xt\"")); // "te""xt" -> te"xt
        assertEquals("\"text\"",
                CsvUtils.unescapeField("\"\"\"text\"\"\"")); // """text""" -> "text"
        assertEquals("t\"e\"x\"t",
                CsvUtils.unescapeField("\"t\"\"e\"\"x\"\"t\"")); // "t""e""x""t" -> t"e"x"t
    }

    public void testUnescapeException() {
        try {
            final String text = CsvUtils.unescapeField("\""); // "
            fail("Unterminated quote: text=" + text);
        } catch (final CsvParseException success) {
            assertEquals("Unterminated quote", success.getMessage());
        }
        try {
            final String text = CsvUtils.unescapeField("\"\"\""); // """
            fail("Unterminated quote: text=" + text);
        } catch (final CsvParseException success) {
            assertEquals("Unterminated quote", success.getMessage());
        }
        try {
            final String text = CsvUtils.unescapeField("\"\"\"\"\""); // """""
            fail("Unterminated quote: text=" + text);
        } catch (final CsvParseException success) {
            assertEquals("Unterminated quote", success.getMessage());
        }
        try {
            final String text = CsvUtils.unescapeField("\"text"); // "text
            fail("Unterminated quote: text=" + text);
        } catch (final CsvParseException success) {
            assertEquals("Unterminated quote", success.getMessage());
        }
        try {
            final String text = CsvUtils.unescapeField("text\""); // text"
            fail("Raw quote in text: text=" + text);
        } catch (final CsvParseException success) {
            assertEquals("Raw quote in text", success.getMessage());
        }
        try {
            final String text = CsvUtils.unescapeField("te\"xt"); // te"xt
            fail("Raw quote in text: text=" + text);
        } catch (final CsvParseException success) {
            assertEquals("Raw quote in text", success.getMessage());
        }
        try {
            final String text = CsvUtils.unescapeField("\"\"text"); // ""text
            fail("Raw quote in quoted text: text=" + text);
        } catch (final CsvParseException success) {
            assertEquals("Raw quote in quoted text", success.getMessage());
        }
        try {
            final String text = CsvUtils.unescapeField("text\"\""); // text""
            fail("Escaped quote in text: text=" + text);
        } catch (final CsvParseException success)  {
            assertEquals("Escaped quote in text", success.getMessage());
        }
        try {
            final String text = CsvUtils.unescapeField("te\"\"xt"); // te""xt
            fail("Escaped quote in text: text=" + text);
        } catch (final CsvParseException success) {
            assertEquals("Escaped quote in text", success.getMessage());
        }
        try {
            final String text = CsvUtils.unescapeField("\"\"text\""); // ""text"
            fail("Raw quote in quoted text: text=" + text);
        } catch (final CsvParseException success) {
            assertEquals("Raw quote in quoted text", success.getMessage());
        }
        try {
            final String text = CsvUtils.unescapeField("\"text\"\""); // "text""
            fail("Unterminated quote: text=" + text);
        } catch (final CsvParseException success) {
            assertEquals("Unterminated quote", success.getMessage());
        }
        try {
            final String text = CsvUtils.unescapeField("\"te\"xt\""); // "te"xt"
            fail("Raw quote in quoted text: text=" + text);
        } catch (final CsvParseException success) {
            assertEquals("Raw quote in quoted text", success.getMessage());
        }
        try {
            final String text = CsvUtils.unescapeField("\"b,c"); // "b,c
            fail("Unterminated quote: text=" + text);
        } catch (final CsvParseException success) {
            assertEquals("Unterminated quote", success.getMessage());
        }
        try {
            final String text = CsvUtils.unescapeField("\",\"a\""); // ","a"
            fail("Raw quote in quoted text: text=" + text);
        } catch (final CsvParseException success) {
            assertEquals("Raw quote in quoted text", success.getMessage());
        }
    }

    private static <T> void assertArrayEquals(final T[] expected, final T[] actual) {
        if (expected == actual) {
            return;
        }
        if (expected == null || actual == null) {
            assertEquals(Arrays.toString(expected), Arrays.toString(actual));
            return;
        }
        if (expected.length != actual.length) {
            assertEquals("[length]", Arrays.toString(expected), Arrays.toString(actual));
            return;
        }
        for (int i = 0; i < expected.length; i++) {
            final T e = expected[i];
            final T a = actual[i];
            if (e == a) {
                continue;
            }
            assertEquals("["+i+"]", expected[i], actual[i]);
        }
    }

    public void testSplit() {
        assertArrayEquals(new String[]{""}, CsvUtils.split(""));
        assertArrayEquals(new String[]{"  "}, CsvUtils.split("  "));
        assertArrayEquals(new String[]{"text"}, CsvUtils.split("text"));
        assertArrayEquals(new String[]{" a b "}, CsvUtils.split(" a b "));

        assertArrayEquals(new String[]{"", ""}, CsvUtils.split(","));
        assertArrayEquals(new String[]{"", "", ""}, CsvUtils.split(",,"));
        assertArrayEquals(new String[]{" ", " "}, CsvUtils.split(" , "));
        assertArrayEquals(new String[]{" ", " ", " "}, CsvUtils.split(" , , "));
        assertArrayEquals(new String[]{"a", "b"}, CsvUtils.split("a,b"));
        assertArrayEquals(new String[]{" a ", " b "}, CsvUtils.split(" a , b "));

        assertArrayEquals(new String[]{"text"},
                CsvUtils.split("\"text\"")); // "text"
        assertArrayEquals(new String[]{" text "},
                CsvUtils.split("\" text \"")); // "_text_"

        assertArrayEquals(new String[]{""},
                CsvUtils.split("\"\"")); // ""
        assertArrayEquals(new String[]{"\""},
                CsvUtils.split("\"\"\"\"")); // """"
        assertArrayEquals(new String[]{"", ""},
                CsvUtils.split("\"\",\"\"")); // "",""
        assertArrayEquals(new String[]{"\",\""},
                CsvUtils.split("\"\"\",\"\"\"")); // ""","""
        assertArrayEquals(new String[]{"\"", "\""},
                CsvUtils.split("\"\"\"\",\"\"\"\"")); // """",""""
        assertArrayEquals(new String[]{"\"", "\",\""},
                CsvUtils.split("\"\"\"\",\"\"\",\"\"\"")); // """",""","""
        assertArrayEquals(new String[]{"\",\"", "\""},
                CsvUtils.split("\"\"\",\"\"\",\"\"\"\"")); // """,""",""""

        assertArrayEquals(new String[]{" a ", " b , c "},
                CsvUtils.split(" a ,\" b , c \"")); // _a_,"_b_,_c_"
        assertArrayEquals(new String[]{" a ", " b , c ", " d "},
                CsvUtils.split(" a ,\" b , c \", d ")); // _a_,"_b_,_c_",_d_
    }

    public void testSplitException() {
        try {
            final String[] fields = CsvUtils.split(" \"text\" "); // _"text"_
            fail("Raw quote in text: fields=" + Arrays.toString(fields));
        } catch (final CsvParseException success) {
            assertEquals("Raw quote in text", success.getMessage());
        }
        try {
            final String[] fields = CsvUtils.split(" \" text \" "); // _"_text_"_
            fail("Raw quote in text: fields=" + Arrays.toString(fields));
        } catch (final CsvParseException success) {
            assertEquals("Raw quote in text", success.getMessage());
        }

        try {
            final String[] fields = CsvUtils.split("a,\"b,"); // a,",b
            fail("Unterminated quote: fields=" + Arrays.toString(fields));
        } catch (final CsvParseException success) {
            assertEquals("Unterminated quote", success.getMessage());
        }
        try {
            final String[] fields = CsvUtils.split("a,\"\"\",b"); // a,""",b
            fail("Unterminated quote: fields=" + Arrays.toString(fields));
        } catch (final CsvParseException success) {
            assertEquals("Unterminated quote", success.getMessage());
        }
        try {
            final String[] fields = CsvUtils.split("a,\"\"\"\"\",b"); // a,""""",b
            fail("Unterminated quote: fields=" + Arrays.toString(fields));
        } catch (final CsvParseException success) {
            assertEquals("Unterminated quote", success.getMessage());
        }
        try {
            final String[] fields = CsvUtils.split("a,\"b,c"); // a,"b,c
            fail("Unterminated quote: fields=" + Arrays.toString(fields));
        } catch (final CsvParseException success) {
            assertEquals("Unterminated quote", success.getMessage());
        }
        try {
            final String[] fields = CsvUtils.split("a,\",\"b,c"); // a,","b,c
            fail("Raw quote in quoted text: fields=" + Arrays.toString(fields));
        } catch (final CsvParseException success) {
            assertEquals("Raw quote in quoted text", success.getMessage());
        }
        try {
            final String[] fields = CsvUtils.split("a,\",\"b\",\",c"); // a,","b",",c
            fail("Raw quote in quoted text: fields=" + Arrays.toString(fields));
        } catch (final CsvParseException success) {
            assertEquals("Raw quote in quoted text", success.getMessage());
        }
    }

    public void testSplitWithTrimSpaces() {
        final int trimSpaces = CsvUtils.SPLIT_FLAGS_TRIM_SPACES;
        assertArrayEquals(new String[]{""}, CsvUtils.split(trimSpaces, ""));
        assertArrayEquals(new String[]{""}, CsvUtils.split(trimSpaces, "  "));
        assertArrayEquals(new String[]{"text"}, CsvUtils.split(trimSpaces, "text"));
        assertArrayEquals(new String[]{"a b"}, CsvUtils.split(trimSpaces, " a b "));

        assertArrayEquals(new String[]{"", ""}, CsvUtils.split(trimSpaces, ","));
        assertArrayEquals(new String[]{"", "", ""}, CsvUtils.split(trimSpaces, ",,"));
        assertArrayEquals(new String[]{"", ""}, CsvUtils.split(trimSpaces, " , "));
        assertArrayEquals(new String[]{"", "", ""}, CsvUtils.split(trimSpaces, " , , "));
        assertArrayEquals(new String[]{"a", "b"}, CsvUtils.split(trimSpaces, "a,b"));
        assertArrayEquals(new String[]{"a", "b"}, CsvUtils.split(trimSpaces, " a , b "));

        assertArrayEquals(new String[]{"text"},
                CsvUtils.split(trimSpaces, "\"text\"")); // "text"
        assertArrayEquals(new String[]{"text"},
                CsvUtils.split(trimSpaces, " \"text\" ")); // _"text"_
        assertArrayEquals(new String[]{" text "},
                CsvUtils.split(trimSpaces, "\" text \"")); // "_text_"
        assertArrayEquals(new String[]{" text "},
                CsvUtils.split(trimSpaces, " \" text \" ")); // _"_text_"_
        assertArrayEquals(new String[]{"a", "b"},
                CsvUtils.split(trimSpaces, " \"a\" , \"b\" ")); // _"a"_,_"b"_

        assertArrayEquals(new String[]{""},
                CsvUtils.split(trimSpaces, " \"\" ")); // _""_
        assertArrayEquals(new String[]{"\""},
                CsvUtils.split(trimSpaces, " \"\"\"\" ")); // _""""_
        assertArrayEquals(new String[]{"", ""},
                CsvUtils.split(trimSpaces, " \"\" , \"\" ")); // _""_,_""_
        assertArrayEquals(new String[]{"\" , \""},
                CsvUtils.split(trimSpaces, " \"\"\" , \"\"\" ")); // _"""_,_"""_
        assertArrayEquals(new String[]{"\"", "\""},
                CsvUtils.split(trimSpaces, " \"\"\"\" , \"\"\"\" ")); // _""""_,_""""_
        assertArrayEquals(new String[]{"\"", "\" , \""},
                CsvUtils.split(trimSpaces, " \"\"\"\" , \"\"\" , \"\"\" ")); // _""""_,_"""_,_"""_
        assertArrayEquals(new String[]{"\" , \"", "\""},
                CsvUtils.split(trimSpaces, " \"\"\" , \"\"\" , \"\"\"\" ")); // _"""_,_"""_,_""""_

        assertArrayEquals(new String[]{"a", " b , c "},
                CsvUtils.split(trimSpaces, " a , \" b , c \" ")); // _a_,_"_b_,_c_"_
        assertArrayEquals(new String[]{"a", " b , c ", "d"},
                CsvUtils.split(trimSpaces, " a, \" b , c \" , d ")); // _a,_"_b_,_c_"_,_d_
    }

    public void testEscape() {
        assertEquals("", CsvUtils.escapeField("", false));
        assertEquals("plain", CsvUtils.escapeField("plain", false));
        assertEquals(" ", CsvUtils.escapeField(" ", false));
        assertEquals("  ", CsvUtils.escapeField("  ", false));
        assertEquals("a space", CsvUtils.escapeField("a space", false));
        assertEquals(" space-at-start", CsvUtils.escapeField(" space-at-start", false));
        assertEquals("space-at-end ", CsvUtils.escapeField("space-at-end ", false));
        assertEquals("a lot of spaces", CsvUtils.escapeField("a lot of spaces", false));
        assertEquals("\",\"", CsvUtils.escapeField(",", false));
        assertEquals("\",,\"", CsvUtils.escapeField(",,", false));
        assertEquals("\"a,comma\"", CsvUtils.escapeField("a,comma", false));
        assertEquals("\",comma-at-begin\"", CsvUtils.escapeField(",comma-at-begin", false));
        assertEquals("\"comma-at-end,\"", CsvUtils.escapeField("comma-at-end,", false));
        assertEquals("\",,a,lot,,,of,commas,,\"",
                CsvUtils.escapeField(",,a,lot,,,of,commas,,", false));
        assertEquals("\"a comma,and a space\"", CsvUtils.escapeField("a comma,and a space", false));
        assertEquals("\"\"\"\"", CsvUtils.escapeField("\"", false)); // " -> """"
        assertEquals("\"\"\"\"\"\"", CsvUtils.escapeField("\"\"", false)); // "" -> """"""
        assertEquals("\"\"\"\"\"\"\"\"", CsvUtils.escapeField("\"\"\"", false)); // """ -> """"""""
        assertEquals("\"\"\"text\"\"\"",
                CsvUtils.escapeField("\"text\"", false)); // "text" -> """text"""
        assertEquals("\"text has \"\" in middle\"",
                CsvUtils.escapeField("text has \" in middle", false));
        assertEquals("\"\"\"quote,at begin\"", CsvUtils.escapeField("\"quote,at begin", false));
        assertEquals("\"quote at,end\"\"\"", CsvUtils.escapeField("quote at,end\"", false));
        assertEquals("\"\"\"quote at begin\"", CsvUtils.escapeField("\"quote at begin", false));
        assertEquals("\"quote at end\"\"\"", CsvUtils.escapeField("quote at end\"", false));
    }

    public void testEscapeWithAlwaysQuoted() {
        assertEquals("\"\"", CsvUtils.escapeField("", true));
        assertEquals("\"plain\"", CsvUtils.escapeField("plain", true));
        assertEquals("\" \"", CsvUtils.escapeField(" ", true));
        assertEquals("\"  \"", CsvUtils.escapeField("  ", true));
        assertEquals("\"a space\"", CsvUtils.escapeField("a space", true));
        assertEquals("\" space-at-start\"", CsvUtils.escapeField(" space-at-start", true));
        assertEquals("\"space-at-end \"", CsvUtils.escapeField("space-at-end ", true));
        assertEquals("\"a lot of spaces\"", CsvUtils.escapeField("a lot of spaces", true));
        assertEquals("\",\"", CsvUtils.escapeField(",", true));
        assertEquals("\",,\"", CsvUtils.escapeField(",,", true));
        assertEquals("\"a,comma\"", CsvUtils.escapeField("a,comma", true));
        assertEquals("\",comma-at-begin\"", CsvUtils.escapeField(",comma-at-begin", true));
        assertEquals("\"comma-at-end,\"", CsvUtils.escapeField("comma-at-end,", true));
        assertEquals("\",,a,lot,,,of,commas,,\"",
                CsvUtils.escapeField(",,a,lot,,,of,commas,,", true));
        assertEquals("\"a comma,and a space\"", CsvUtils.escapeField("a comma,and a space", true));
        assertEquals("\"\"\"\"", CsvUtils.escapeField("\"", true)); // " -> """"
        assertEquals("\"\"\"\"\"\"", CsvUtils.escapeField("\"\"", true)); // "" -> """"""
        assertEquals("\"\"\"\"\"\"\"\"", CsvUtils.escapeField("\"\"\"", true)); // """ -> """"""""
        assertEquals("\"\"\"text\"\"\"",
                CsvUtils.escapeField("\"text\"", true)); // "text" -> """text"""
        assertEquals("\"text has \"\" in middle\"",
                CsvUtils.escapeField("text has \" in middle", true));
        assertEquals("\"\"\"quote,at begin\"", CsvUtils.escapeField("\"quote,at begin", true));
        assertEquals("\"quote at,end\"\"\"", CsvUtils.escapeField("quote at,end\"", true));
        assertEquals("\"\"\"quote at begin\"", CsvUtils.escapeField("\"quote at begin", true));
        assertEquals("\"quote at end\"\"\"", CsvUtils.escapeField("quote at end\"", true));
    }

    public void testJoinWithoutColumnPositions() {
        assertEquals("", CsvUtils.join());
        assertEquals("", CsvUtils.join(""));
        assertEquals(",", CsvUtils.join("", ""));

        assertEquals("text, text,text ",
                CsvUtils.join("text", " text", "text "));
        assertEquals("\"\"\"\",\"\"\"\"\"\",\"\"\"text\"\"\"",
                CsvUtils.join("\"", "\"\"", "\"text\""));
        assertEquals("a b,\"c,d\",\"e\"\"f\"",
                CsvUtils.join("a b", "c,d", "e\"f"));
    }

    public void testJoinWithoutColumnPositionsWithExtraSpace() {
        final int extraSpace = CsvUtils.JOIN_FLAGS_EXTRA_SPACE;
        assertEquals("", CsvUtils.join(extraSpace));
        assertEquals("", CsvUtils.join(extraSpace, ""));
        assertEquals(", ", CsvUtils.join(extraSpace, "", ""));

        assertEquals("text,  text, text ",
                CsvUtils.join(extraSpace, "text", " text", "text "));
        // ","","text" -> """","""""","""text"""
        assertEquals("\"\"\"\", \"\"\"\"\"\", \"\"\"text\"\"\"",
                CsvUtils.join(extraSpace, "\"", "\"\"", "\"text\""));
        assertEquals("a b, \"c,d\", \"e\"\"f\"",
                CsvUtils.join(extraSpace, "a b", "c,d", "e\"f"));
    }

    public void testJoinWithoutColumnPositionsWithExtraSpaceAndAlwaysQuoted() {
        final int extrSpaceAndQuoted =
                CsvUtils.JOIN_FLAGS_EXTRA_SPACE | CsvUtils.JOIN_FLAGS_ALWAYS_QUOTED;
        assertEquals("", CsvUtils.join(extrSpaceAndQuoted));
        assertEquals("\"\"", CsvUtils.join(extrSpaceAndQuoted, ""));
        assertEquals("\"\", \"\"", CsvUtils.join(extrSpaceAndQuoted, "", ""));

        assertEquals("\"text\", \" text\", \"text \"",
                CsvUtils.join(extrSpaceAndQuoted, "text", " text", "text "));
        // ","","text" -> """", """""", """text"""
        assertEquals("\"\"\"\", \"\"\"\"\"\", \"\"\"text\"\"\"",
                CsvUtils.join(extrSpaceAndQuoted, "\"", "\"\"", "\"text\""));
        assertEquals("\"a b\", \"c,d\", \"e\"\"f\"",
                CsvUtils.join(extrSpaceAndQuoted, "a b", "c,d", "e\"f"));
    }

    public void testJoinWithColumnPositions() {
        final int noFlags = CsvUtils.JOIN_FLAGS_NONE;
        assertEquals("", CsvUtils.join(noFlags, new int[]{}));
        assertEquals("   ", CsvUtils.join(noFlags, new int[]{3}, ""));
        assertEquals(" ,", CsvUtils.join(noFlags, new int[]{1}, "", ""));
        assertEquals(",  ", CsvUtils.join(noFlags, new int[]{0, 3}, "", ""));

        assertEquals("text,    text, text ",
                CsvUtils.join(noFlags, new int[]{0, 8, 15}, "text", " text", "text "));
        // ","","text" -> """",   """""","""text"""
        assertEquals("\"\"\"\",   \"\"\"\"\"\",\"\"\"text\"\"\"",
                CsvUtils.join(noFlags, new int[]{0, 8, 15}, "\"", "\"\"", "\"text\""));
        assertEquals("a b,    \"c,d\", \"e\"\"f\"",
                CsvUtils.join(noFlags, new int[]{0, 8, 15}, "a b", "c,d", "e\"f"));
    }

    public void testJoinWithColumnPositionsWithExtraSpace() {
        final int extraSpace = CsvUtils.JOIN_FLAGS_EXTRA_SPACE;
        assertEquals("", CsvUtils.join(extraSpace, new int[]{}));
        assertEquals("   ", CsvUtils.join(extraSpace, new int[]{3}, ""));
        assertEquals(" , ", CsvUtils.join(extraSpace, new int[]{1}, "", ""));
        assertEquals(",  ", CsvUtils.join(extraSpace, new int[]{0, 3}, "", ""));

        assertEquals("text,    text, text ",
                CsvUtils.join(extraSpace, new int[]{0, 8, 15}, "text", " text", "text "));
        // ","","text" -> """",   """""", """text"""
        assertEquals("\"\"\"\",   \"\"\"\"\"\", \"\"\"text\"\"\"",
                CsvUtils.join(extraSpace, new int[]{0, 8, 15}, "\"", "\"\"", "\"text\""));
        assertEquals("a b,    \"c,d\", \"e\"\"f\"",
                CsvUtils.join(extraSpace, new int[]{0, 8, 15}, "a b", "c,d", "e\"f"));
    }
}
