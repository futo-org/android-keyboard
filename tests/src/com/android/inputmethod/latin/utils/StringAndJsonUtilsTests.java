/*
 * Copyright (C) 2012 The Android Open Source Project
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
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;

import com.android.inputmethod.latin.Constants;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@SmallTest
public class StringAndJsonUtilsTests extends AndroidTestCase {
    public void testContainsInArray() {
        assertFalse("empty array", StringUtils.containsInArray("key", new String[0]));
        assertFalse("not in 1 element", StringUtils.containsInArray("key", new String[] {
                "key1"
        }));
        assertFalse("not in 2 elements", StringUtils.containsInArray("key", new String[] {
                "key1", "key2"
        }));

        assertTrue("in 1 element", StringUtils.containsInArray("key", new String[] {
                "key"
        }));
        assertTrue("in 2 elements", StringUtils.containsInArray("key", new String[] {
                "key1", "key"
        }));
    }

    public void testContainsInCommaSplittableText() {
        assertFalse("null", StringUtils.containsInCommaSplittableText("key", null));
        assertFalse("empty", StringUtils.containsInCommaSplittableText("key", ""));
        assertFalse("not in 1 element",
                StringUtils.containsInCommaSplittableText("key", "key1"));
        assertFalse("not in 2 elements",
                StringUtils.containsInCommaSplittableText("key", "key1,key2"));

        assertTrue("in 1 element", StringUtils.containsInCommaSplittableText("key", "key"));
        assertTrue("in 2 elements", StringUtils.containsInCommaSplittableText("key", "key1,key"));
    }

    public void testRemoveFromCommaSplittableTextIfExists() {
        assertEquals("null", "", StringUtils.removeFromCommaSplittableTextIfExists("key", null));
        assertEquals("empty", "", StringUtils.removeFromCommaSplittableTextIfExists("key", ""));

        assertEquals("not in 1 element", "key1",
                StringUtils.removeFromCommaSplittableTextIfExists("key", "key1"));
        assertEquals("not in 2 elements", "key1,key2",
                StringUtils.removeFromCommaSplittableTextIfExists("key", "key1,key2"));

        assertEquals("in 1 element", "",
                StringUtils.removeFromCommaSplittableTextIfExists("key", "key"));
        assertEquals("in 2 elements at position 1", "key2",
                StringUtils.removeFromCommaSplittableTextIfExists("key", "key,key2"));
        assertEquals("in 2 elements at position 2", "key1",
                StringUtils.removeFromCommaSplittableTextIfExists("key", "key1,key"));
        assertEquals("in 3 elements at position 2", "key1,key3",
                StringUtils.removeFromCommaSplittableTextIfExists("key", "key1,key,key3"));

        assertEquals("in 3 elements at position 1,2,3", "",
                StringUtils.removeFromCommaSplittableTextIfExists("key", "key,key,key"));
        assertEquals("in 5 elements at position 2,4", "key1,key3,key5",
                StringUtils.removeFromCommaSplittableTextIfExists(
                        "key", "key1,key,key3,key,key5"));
    }


    public void testCapitalizeFirstCodePoint() {
        assertEquals("SSaa",
                StringUtils.capitalizeFirstCodePoint("ßaa", Locale.GERMAN));
        assertEquals("Aßa",
                StringUtils.capitalizeFirstCodePoint("aßa", Locale.GERMAN));
        assertEquals("Iab",
                StringUtils.capitalizeFirstCodePoint("iab", Locale.ENGLISH));
        assertEquals("CAmElCaSe",
                StringUtils.capitalizeFirstCodePoint("cAmElCaSe", Locale.ENGLISH));
        assertEquals("İab",
                StringUtils.capitalizeFirstCodePoint("iab", new Locale("tr")));
        assertEquals("AİB",
                StringUtils.capitalizeFirstCodePoint("AİB", new Locale("tr")));
        assertEquals("A",
                StringUtils.capitalizeFirstCodePoint("a", Locale.ENGLISH));
        assertEquals("A",
                StringUtils.capitalizeFirstCodePoint("A", Locale.ENGLISH));
    }

    public void testCapitalizeFirstAndDowncaseRest() {
        assertEquals("SSaa",
                StringUtils.capitalizeFirstAndDowncaseRest("ßaa", Locale.GERMAN));
        assertEquals("Aßa",
                StringUtils.capitalizeFirstAndDowncaseRest("aßa", Locale.GERMAN));
        assertEquals("Iab",
                StringUtils.capitalizeFirstAndDowncaseRest("iab", Locale.ENGLISH));
        assertEquals("Camelcase",
                StringUtils.capitalizeFirstAndDowncaseRest("cAmElCaSe", Locale.ENGLISH));
        assertEquals("İab",
                StringUtils.capitalizeFirstAndDowncaseRest("iab", new Locale("tr")));
        assertEquals("Aib",
                StringUtils.capitalizeFirstAndDowncaseRest("AİB", new Locale("tr")));
        assertEquals("A",
                StringUtils.capitalizeFirstAndDowncaseRest("a", Locale.ENGLISH));
        assertEquals("A",
                StringUtils.capitalizeFirstAndDowncaseRest("A", Locale.ENGLISH));
    }

    public void testGetCapitalizationType() {
        assertEquals(StringUtils.CAPITALIZE_NONE,
                StringUtils.getCapitalizationType("capitalize"));
        assertEquals(StringUtils.CAPITALIZE_NONE,
                StringUtils.getCapitalizationType("cApITalize"));
        assertEquals(StringUtils.CAPITALIZE_NONE,
                StringUtils.getCapitalizationType("capitalizE"));
        assertEquals(StringUtils.CAPITALIZE_NONE,
                StringUtils.getCapitalizationType("__c a piu$@tali56ze"));
        assertEquals(StringUtils.CAPITALIZE_FIRST,
                StringUtils.getCapitalizationType("A__c a piu$@tali56ze"));
        assertEquals(StringUtils.CAPITALIZE_FIRST,
                StringUtils.getCapitalizationType("Capitalize"));
        assertEquals(StringUtils.CAPITALIZE_FIRST,
                StringUtils.getCapitalizationType("     Capitalize"));
        assertEquals(StringUtils.CAPITALIZE_ALL,
                StringUtils.getCapitalizationType("CAPITALIZE"));
        assertEquals(StringUtils.CAPITALIZE_ALL,
                StringUtils.getCapitalizationType("  PI26LIE"));
        assertEquals(StringUtils.CAPITALIZE_NONE,
                StringUtils.getCapitalizationType(""));
    }

    public void testIsIdenticalAfterUpcaseIsIdenticalAfterDowncase() {
        assertFalse(StringUtils.isIdenticalAfterUpcase("capitalize"));
        assertTrue(StringUtils.isIdenticalAfterDowncase("capitalize"));
        assertFalse(StringUtils.isIdenticalAfterUpcase("cApITalize"));
        assertFalse(StringUtils.isIdenticalAfterDowncase("cApITalize"));
        assertFalse(StringUtils.isIdenticalAfterUpcase("capitalizE"));
        assertFalse(StringUtils.isIdenticalAfterDowncase("capitalizE"));
        assertFalse(StringUtils.isIdenticalAfterUpcase("__c a piu$@tali56ze"));
        assertTrue(StringUtils.isIdenticalAfterDowncase("__c a piu$@tali56ze"));
        assertFalse(StringUtils.isIdenticalAfterUpcase("A__c a piu$@tali56ze"));
        assertFalse(StringUtils.isIdenticalAfterDowncase("A__c a piu$@tali56ze"));
        assertFalse(StringUtils.isIdenticalAfterUpcase("Capitalize"));
        assertFalse(StringUtils.isIdenticalAfterDowncase("Capitalize"));
        assertFalse(StringUtils.isIdenticalAfterUpcase("     Capitalize"));
        assertFalse(StringUtils.isIdenticalAfterDowncase("     Capitalize"));
        assertTrue(StringUtils.isIdenticalAfterUpcase("CAPITALIZE"));
        assertFalse(StringUtils.isIdenticalAfterDowncase("CAPITALIZE"));
        assertTrue(StringUtils.isIdenticalAfterUpcase("  PI26LIE"));
        assertFalse(StringUtils.isIdenticalAfterDowncase("  PI26LIE"));
        assertTrue(StringUtils.isIdenticalAfterUpcase(""));
        assertTrue(StringUtils.isIdenticalAfterDowncase(""));
    }

    private static void checkCapitalize(final String src, final String dst,
            final int[] sortedSeparators, final Locale locale) {
        assertEquals(dst, StringUtils.capitalizeEachWord(src, sortedSeparators, locale));
        assert(src.equals(dst)
                == StringUtils.isIdenticalAfterCapitalizeEachWord(src, sortedSeparators));
    }

    private static final int[] SPACE = { Constants.CODE_SPACE };
    private static final int[] SPACE_PERIOD = StringUtils.toSortedCodePointArray(" .");
    private static final int[] SENTENCE_SEPARATORS =
            StringUtils.toSortedCodePointArray(" \n.!?*()&");
    private static final int[] WORD_SEPARATORS = StringUtils.toSortedCodePointArray(" \n.!?*,();&");

    public void testCapitalizeEachWord() {
        checkCapitalize("", "", SPACE, Locale.ENGLISH);
        checkCapitalize("test", "Test", SPACE, Locale.ENGLISH);
        checkCapitalize("    test", "    Test", SPACE, Locale.ENGLISH);
        checkCapitalize("Test", "Test", SPACE, Locale.ENGLISH);
        checkCapitalize("    Test", "    Test", SPACE, Locale.ENGLISH);
        checkCapitalize(".Test", ".test", SPACE, Locale.ENGLISH);
        checkCapitalize(".Test", ".Test", SPACE_PERIOD, Locale.ENGLISH);
        checkCapitalize("test and retest", "Test And Retest", SPACE_PERIOD, Locale.ENGLISH);
        checkCapitalize("Test and retest", "Test And Retest", SPACE_PERIOD, Locale.ENGLISH);
        checkCapitalize("Test And Retest", "Test And Retest", SPACE_PERIOD, Locale.ENGLISH);
        checkCapitalize("Test And.Retest  ", "Test And.Retest  ", SPACE_PERIOD, Locale.ENGLISH);
        checkCapitalize("Test And.retest  ", "Test And.Retest  ", SPACE_PERIOD, Locale.ENGLISH);
        checkCapitalize("Test And.retest  ", "Test And.retest  ", SPACE, Locale.ENGLISH);
        checkCapitalize("Test And.Retest  ", "Test And.retest  ", SPACE, Locale.ENGLISH);
        checkCapitalize("test and ietest", "Test And İetest", SPACE_PERIOD, new Locale("tr"));
        checkCapitalize("test and ietest", "Test And Ietest", SPACE_PERIOD, Locale.ENGLISH);
        checkCapitalize("Test&Retest", "Test&Retest", SENTENCE_SEPARATORS, Locale.ENGLISH);
        checkCapitalize("Test&retest", "Test&Retest", SENTENCE_SEPARATORS, Locale.ENGLISH);
        checkCapitalize("test&Retest", "Test&Retest", SENTENCE_SEPARATORS, Locale.ENGLISH);
        checkCapitalize("rest\nrecreation! And in the end...",
                "Rest\nRecreation! And In The End...", WORD_SEPARATORS, Locale.ENGLISH);
        checkCapitalize("lorem ipsum dolor sit amet", "Lorem Ipsum Dolor Sit Amet",
                WORD_SEPARATORS, Locale.ENGLISH);
        checkCapitalize("Lorem!Ipsum (Dolor) Sit * Amet", "Lorem!Ipsum (Dolor) Sit * Amet",
                WORD_SEPARATORS, Locale.ENGLISH);
        checkCapitalize("Lorem!Ipsum (dolor) Sit * Amet", "Lorem!Ipsum (Dolor) Sit * Amet",
                WORD_SEPARATORS, Locale.ENGLISH);
    }

    public void testLooksLikeURL() {
        assertTrue(StringUtils.lastPartLooksLikeURL("http://www.google."));
        assertFalse(StringUtils.lastPartLooksLikeURL("word wo"));
        assertTrue(StringUtils.lastPartLooksLikeURL("/etc/foo"));
        assertFalse(StringUtils.lastPartLooksLikeURL("left/right"));
        assertTrue(StringUtils.lastPartLooksLikeURL("www.goo"));
        assertTrue(StringUtils.lastPartLooksLikeURL("www."));
        assertFalse(StringUtils.lastPartLooksLikeURL("U.S.A"));
        assertFalse(StringUtils.lastPartLooksLikeURL("U.S.A."));
        assertTrue(StringUtils.lastPartLooksLikeURL("rtsp://foo."));
        assertTrue(StringUtils.lastPartLooksLikeURL("://"));
        assertFalse(StringUtils.lastPartLooksLikeURL("abc/"));
        assertTrue(StringUtils.lastPartLooksLikeURL("abc.def/ghi"));
        assertFalse(StringUtils.lastPartLooksLikeURL("abc.def"));
        // TODO: ideally this would not look like a URL, but to keep down the complexity of the
        // code for now True is acceptable.
        assertTrue(StringUtils.lastPartLooksLikeURL("abc./def"));
        // TODO: ideally this would not look like a URL, but to keep down the complexity of the
        // code for now True is acceptable.
        assertTrue(StringUtils.lastPartLooksLikeURL(".abc/def"));
    }

    public void testHexStringUtils() {
        final byte[] bytes = new byte[] { (byte)0x01, (byte)0x11, (byte)0x22, (byte)0x33,
                (byte)0x55, (byte)0x88, (byte)0xEE };
        final String bytesStr = StringUtils.byteArrayToHexString(bytes);
        final byte[] bytes2 = StringUtils.hexStringToByteArray(bytesStr);
        for (int i = 0; i < bytes.length; ++i) {
            assertTrue(bytes[i] == bytes2[i]);
        }
        final String bytesStr2 = StringUtils.byteArrayToHexString(bytes2);
        assertTrue(bytesStr.equals(bytesStr2));
    }

    public void testJsonUtils() {
        final Object[] objs = new Object[] { 1, "aaa", "bbb", 3 };
        final List<Object> objArray = Arrays.asList(objs);
        final String str = JsonUtils.listToJsonStr(objArray);
        final List<Object> newObjArray = JsonUtils.jsonStrToList(str);
        for (int i = 0; i < objs.length; ++i) {
            assertEquals(objs[i], newObjArray.get(i));
        }
    }

    public void testToCodePointArray() {
        final String STR_WITH_SUPPLEMENTARY_CHAR = "abcde\uD861\uDED7fgh\u0000\u2002\u2003\u3000xx";
        final int[] EXPECTED_RESULT = new int[] { 'a', 'b', 'c', 'd', 'e', 0x286D7, 'f', 'g', 'h',
                0, 0x2002, 0x2003, 0x3000, 'x', 'x'};
        final int[] codePointArray = StringUtils.toCodePointArray(STR_WITH_SUPPLEMENTARY_CHAR, 0,
                STR_WITH_SUPPLEMENTARY_CHAR.length());
        assertEquals("toCodePointArray, size matches", codePointArray.length,
                EXPECTED_RESULT.length);
        for (int i = 0; i < EXPECTED_RESULT.length; ++i) {
            assertEquals("toCodePointArray position " + i, codePointArray[i], EXPECTED_RESULT[i]);
        }
    }

    public void testCopyCodePointsAndReturnCodePointCount() {
        final String STR_WITH_SUPPLEMENTARY_CHAR = "AbcDE\uD861\uDED7fGh\u0000\u2002\u3000あx";
        final int[] EXPECTED_RESULT = new int[] { 'A', 'b', 'c', 'D', 'E', 0x286D7,
                'f', 'G', 'h', 0, 0x2002, 0x3000, 'あ', 'x'};
        final int[] EXPECTED_RESULT_DOWNCASE = new int[] { 'a', 'b', 'c', 'd', 'e', 0x286D7,
                'f', 'g', 'h', 0, 0x2002, 0x3000, 'あ', 'x'};

        int[] codePointArray = new int[50];
        int codePointCount = StringUtils.copyCodePointsAndReturnCodePointCount(codePointArray,
                STR_WITH_SUPPLEMENTARY_CHAR, 0,
                STR_WITH_SUPPLEMENTARY_CHAR.length(), false /* downCase */);
        assertEquals("copyCodePointsAndReturnCodePointCount, size matches", codePointCount,
                EXPECTED_RESULT.length);
        for (int i = 0; i < codePointCount; ++i) {
            assertEquals("copyCodePointsAndReturnCodePointCount position " + i, codePointArray[i],
                    EXPECTED_RESULT[i]);
        }

        codePointCount = StringUtils.copyCodePointsAndReturnCodePointCount(codePointArray,
                STR_WITH_SUPPLEMENTARY_CHAR, 0,
                STR_WITH_SUPPLEMENTARY_CHAR.length(), true /* downCase */);
        assertEquals("copyCodePointsAndReturnCodePointCount downcase, size matches", codePointCount,
                EXPECTED_RESULT_DOWNCASE.length);
        for (int i = 0; i < codePointCount; ++i) {
            assertEquals("copyCodePointsAndReturnCodePointCount position " + i, codePointArray[i],
                    EXPECTED_RESULT_DOWNCASE[i]);
        }

        final int JAVA_CHAR_COUNT = 8;
        final int CODEPOINT_COUNT = 7;
        codePointCount = StringUtils.copyCodePointsAndReturnCodePointCount(codePointArray,
                STR_WITH_SUPPLEMENTARY_CHAR, 0, JAVA_CHAR_COUNT, false /* downCase */);
        assertEquals("copyCodePointsAndReturnCodePointCount, size matches", codePointCount,
                CODEPOINT_COUNT);
        for (int i = 0; i < codePointCount; ++i) {
            assertEquals("copyCodePointsAndReturnCodePointCount position " + i, codePointArray[i],
                    EXPECTED_RESULT[i]);
        }

        boolean exceptionHappened = false;
        codePointArray = new int[5];
        try {
            codePointCount = StringUtils.copyCodePointsAndReturnCodePointCount(codePointArray,
                    STR_WITH_SUPPLEMENTARY_CHAR, 0, JAVA_CHAR_COUNT, false /* downCase */);
        } catch (ArrayIndexOutOfBoundsException e) {
            exceptionHappened = true;
        }
        assertTrue("copyCodePointsAndReturnCodePointCount throws when array is too small",
                exceptionHappened);
    }

    public void testGetTrailingSingleQuotesCount() {
        assertEquals(0, StringUtils.getTrailingSingleQuotesCount(""));
        assertEquals(1, StringUtils.getTrailingSingleQuotesCount("'"));
        assertEquals(5, StringUtils.getTrailingSingleQuotesCount("'''''"));
        assertEquals(0, StringUtils.getTrailingSingleQuotesCount("a"));
        assertEquals(0, StringUtils.getTrailingSingleQuotesCount("'this"));
        assertEquals(1, StringUtils.getTrailingSingleQuotesCount("'word'"));
        assertEquals(0, StringUtils.getTrailingSingleQuotesCount("I'm"));
    }

    private static void assertSpanCount(final int expectedCount, final CharSequence cs) {
        final int actualCount;
        if (cs instanceof Spanned) {
            final Spanned spanned = (Spanned) cs;
            actualCount = spanned.getSpans(0, spanned.length(), Object.class).length;
        } else {
            actualCount = 0;
        }
        assertEquals(expectedCount, actualCount);
    }

    private static void assertSpan(final CharSequence cs, final Object expectedSpan,
            final int expectedStart, final int expectedEnd, final int expectedFlags) {
        assertTrue(cs instanceof Spanned);
        final Spanned spanned = (Spanned) cs;
        final Object[] actualSpans = spanned.getSpans(0, spanned.length(), Object.class);
        for (Object actualSpan : actualSpans) {
            if (actualSpan == expectedSpan) {
                final int actualStart = spanned.getSpanStart(actualSpan);
                final int actualEnd = spanned.getSpanEnd(actualSpan);
                final int actualFlags = spanned.getSpanFlags(actualSpan);
                assertEquals(expectedStart, actualStart);
                assertEquals(expectedEnd, actualEnd);
                assertEquals(expectedFlags, actualFlags);
                return;
            }
        }
        assertTrue(false);
    }

    public void testSplitCharSequenceWithSpan() {
        // text:  " a bcd efg hij  "
        // span1:  ^^^^^^^
        // span2:  ^^^^^
        // span3:              ^
        final SpannableString spannableString = new SpannableString(" a bcd efg hij  ");
        final Object span1 = new Object();
        final Object span2 = new Object();
        final Object span3 = new Object();
        final int SPAN1_FLAGS = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        final int SPAN2_FLAGS = Spanned.SPAN_EXCLUSIVE_INCLUSIVE;
        final int SPAN3_FLAGS = Spanned.SPAN_INCLUSIVE_INCLUSIVE;
        spannableString.setSpan(span1, 0, 7, SPAN1_FLAGS);
        spannableString.setSpan(span2, 0, 5, SPAN2_FLAGS);
        spannableString.setSpan(span3, 12, 13, SPAN3_FLAGS);
        final CharSequence[] charSequencesFromSpanned = StringUtils.split(
                spannableString, " ", true /* preserveTrailingEmptySegmengs */);
        final CharSequence[] charSequencesFromString = StringUtils.split(
                spannableString.toString(), " ", true /* preserveTrailingEmptySegmengs */);


        assertEquals(7, charSequencesFromString.length);
        assertEquals(7, charSequencesFromSpanned.length);

        // text:  ""
        // span1: ^
        // span2: ^
        // span3:
        assertEquals("", charSequencesFromString[0].toString());
        assertSpanCount(0, charSequencesFromString[0]);
        assertEquals("", charSequencesFromSpanned[0].toString());
        assertSpanCount(2, charSequencesFromSpanned[0]);
        assertSpan(charSequencesFromSpanned[0], span1, 0, 0, SPAN1_FLAGS);
        assertSpan(charSequencesFromSpanned[0], span2, 0, 0, SPAN2_FLAGS);

        // text:  "a"
        // span1:  ^
        // span2:  ^
        // span3:
        assertEquals("a", charSequencesFromString[1].toString());
        assertSpanCount(0, charSequencesFromString[1]);
        assertEquals("a", charSequencesFromSpanned[1].toString());
        assertSpanCount(2, charSequencesFromSpanned[1]);
        assertSpan(charSequencesFromSpanned[1], span1, 0, 1, SPAN1_FLAGS);
        assertSpan(charSequencesFromSpanned[1], span2, 0, 1, SPAN2_FLAGS);

        // text:  "bcd"
        // span1:  ^^^
        // span2:  ^^
        // span3:
        assertEquals("bcd", charSequencesFromString[2].toString());
        assertSpanCount(0, charSequencesFromString[2]);
        assertEquals("bcd", charSequencesFromSpanned[2].toString());
        assertSpanCount(2, charSequencesFromSpanned[2]);
        assertSpan(charSequencesFromSpanned[2], span1, 0, 3, SPAN1_FLAGS);
        assertSpan(charSequencesFromSpanned[2], span2, 0, 2, SPAN2_FLAGS);

        // text:  "efg"
        // span1:
        // span2:
        // span3:
        assertEquals("efg", charSequencesFromString[3].toString());
        assertSpanCount(0, charSequencesFromString[3]);
        assertEquals("efg", charSequencesFromSpanned[3].toString());
        assertSpanCount(0, charSequencesFromSpanned[3]);

        // text:  "hij"
        // span1:
        // span2:
        // span3:   ^
        assertEquals("hij", charSequencesFromString[4].toString());
        assertSpanCount(0, charSequencesFromString[4]);
        assertEquals("hij", charSequencesFromSpanned[4].toString());
        assertSpanCount(1, charSequencesFromSpanned[4]);
        assertSpan(charSequencesFromSpanned[4], span3, 1, 2, SPAN3_FLAGS);

        // text:  ""
        // span1:
        // span2:
        // span3:
        assertEquals("", charSequencesFromString[5].toString());
        assertSpanCount(0, charSequencesFromString[5]);
        assertEquals("", charSequencesFromSpanned[5].toString());
        assertSpanCount(0, charSequencesFromSpanned[5]);

        // text:  ""
        // span1:
        // span2:
        // span3:
        assertEquals("", charSequencesFromString[6].toString());
        assertSpanCount(0, charSequencesFromString[6]);
        assertEquals("", charSequencesFromSpanned[6].toString());
        assertSpanCount(0, charSequencesFromSpanned[6]);
    }

    public void testSplitCharSequencePreserveTrailingEmptySegmengs() {
        assertEquals(1, StringUtils.split("", " ",
                false /* preserveTrailingEmptySegmengs */).length);
        assertEquals(1, StringUtils.split(new SpannedString(""), " ",
                false /* preserveTrailingEmptySegmengs */).length);

        assertEquals(1, StringUtils.split("", " ",
                true /* preserveTrailingEmptySegmengs */).length);
        assertEquals(1, StringUtils.split(new SpannedString(""), " ",
                true /* preserveTrailingEmptySegmengs */).length);

        assertEquals(0, StringUtils.split(" ", " ",
                false /* preserveTrailingEmptySegmengs */).length);
        assertEquals(0, StringUtils.split(new SpannedString(" "), " ",
                false /* preserveTrailingEmptySegmengs */).length);

        assertEquals(2, StringUtils.split(" ", " ",
                true /* preserveTrailingEmptySegmengs */).length);
        assertEquals(2, StringUtils.split(new SpannedString(" "), " ",
                true /* preserveTrailingEmptySegmengs */).length);

        assertEquals(3, StringUtils.split("a b c  ", " ",
                false /* preserveTrailingEmptySegmengs */).length);
        assertEquals(3, StringUtils.split(new SpannedString("a b c  "), " ",
                false /* preserveTrailingEmptySegmengs */).length);

        assertEquals(5, StringUtils.split("a b c  ", " ",
                true /* preserveTrailingEmptySegmengs */).length);
        assertEquals(5, StringUtils.split(new SpannedString("a b c  "), " ",
                true /* preserveTrailingEmptySegmengs */).length);

        assertEquals(6, StringUtils.split("a     b ", " ",
                false /* preserveTrailingEmptySegmengs */).length);
        assertEquals(6, StringUtils.split(new SpannedString("a     b "), " ",
                false /* preserveTrailingEmptySegmengs */).length);

        assertEquals(7, StringUtils.split("a     b ", " ",
                true /* preserveTrailingEmptySegmengs */).length);
        assertEquals(7, StringUtils.split(new SpannedString("a     b "), " ",
                true /* preserveTrailingEmptySegmengs */).length);
    }
}
