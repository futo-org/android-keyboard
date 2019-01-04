/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.inputmethod.latin.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class StringUtilsTests {
    private static final Locale US = Locale.US;
    private static final Locale GERMAN = Locale.GERMAN;
    private static final Locale TURKEY = new Locale("tr", "TR");
    private static final Locale GREECE = new Locale("el", "GR");

    private static void assert_toTitleCaseOfKeyLabel(final Locale locale,
            final String lowerCase, final String expected) {
        assertEquals(lowerCase + " in " + locale, expected,
                StringUtils.toTitleCaseOfKeyLabel(lowerCase, locale));
    }

    @Test
    public void test_toTitleCaseOfKeyLabel() {
        assert_toTitleCaseOfKeyLabel(US, null, null);
        assert_toTitleCaseOfKeyLabel(US, "", "");
        assert_toTitleCaseOfKeyLabel(US, "aeiou", "AEIOU");
        // U+00E0: "à" LATIN SMALL LETTER A WITH GRAVE
        // U+00E8: "è" LATIN SMALL LETTER E WITH GRAVE
        // U+00EE: "î" LATIN SMALL LETTER I WITH CIRCUMFLEX
        // U+00F6: "ö" LATIN SMALL LETTER O WITH DIAERESIS
        // U+016B: "ū" LATIN SMALL LETTER U WITH MACRON
        // U+00F1: "ñ" LATIN SMALL LETTER N WITH TILDE
        // U+00E7: "ç" LATIN SMALL LETTER C WITH CEDILLA
        // U+00C0: "À" LATIN CAPITAL LETTER A WITH GRAVE
        // U+00C8: "È" LATIN CAPITAL LETTER E WITH GRAVE
        // U+00CE: "Î" LATIN CAPITAL LETTER I WITH CIRCUMFLEX
        // U+00D6: "Ö" LATIN CAPITAL LETTER O WITH DIAERESIS
        // U+016A: "Ū" LATIN CAPITAL LETTER U WITH MACRON
        // U+00D1: "Ñ" LATIN CAPITAL LETTER N WITH TILDE
        // U+00C7: "Ç" LATIN CAPITAL LETTER C WITH CEDILLA
        assert_toTitleCaseOfKeyLabel(US,
                "\u00E0\u00E8\u00EE\u00F6\u016B\u00F1\u00E7",
                "\u00C0\u00C8\u00CE\u00D6\u016A\u00D1\u00C7");
        // U+00DF: "ß" LATIN SMALL LETTER SHARP S
        // U+015B: "ś" LATIN SMALL LETTER S WITH ACUTE
        // U+0161: "š" LATIN SMALL LETTER S WITH CARON
        // U+015A: "Ś" LATIN CAPITAL LETTER S WITH ACUTE
        // U+0160: "Š" LATIN CAPITAL LETTER S WITH CARONZ
        assert_toTitleCaseOfKeyLabel(GERMAN,
                "\u00DF\u015B\u0161",
                "SS\u015A\u0160");
        // U+0259: "ə" LATIN SMALL LETTER SCHWA
        // U+0069: "i" LATIN SMALL LETTER I
        // U+0131: "ı" LATIN SMALL LETTER DOTLESS I
        // U+018F: "Ə" LATIN SMALL LETTER SCHWA
        // U+0130: "İ" LATIN SMALL LETTER I WITH DOT ABOVE
        // U+0049: "I" LATIN SMALL LETTER I
        assert_toTitleCaseOfKeyLabel(TURKEY,
                "\u0259\u0069\u0131",
                "\u018F\u0130\u0049");
        // U+03C3: "σ" GREEK SMALL LETTER SIGMA
        // U+03C2: "ς" GREEK SMALL LETTER FINAL SIGMA
        // U+03A3: "Σ" GREEK CAPITAL LETTER SIGMA
        assert_toTitleCaseOfKeyLabel(GREECE,
                "\u03C3\u03C2",
                "\u03A3\u03A3");
        // U+03AC: "ά" GREEK SMALL LETTER ALPHA WITH TONOS
        // U+03AD: "έ" GREEK SMALL LETTER EPSILON WITH TONOS
        // U+03AE: "ή" GREEK SMALL LETTER ETA WITH TONOS
        // U+03AF: "ί" GREEK SMALL LETTER IOTA WITH TONOS
        // U+03CC: "ό" GREEK SMALL LETTER OMICRON WITH TONOS
        // U+03CD: "ύ" GREEK SMALL LETTER UPSILON WITH TONOS
        // U+03CE: "ώ" GREEK SMALL LETTER OMEGA WITH TONOS
        // U+0386: "Ά" GREEK CAPITAL LETTER ALPHA WITH TONOS
        // U+0388: "Έ" GREEK CAPITAL LETTER EPSILON WITH TONOS
        // U+0389: "Ή" GREEK CAPITAL LETTER ETA WITH TONOS
        // U+038A: "Ί" GREEK CAPITAL LETTER IOTA WITH TONOS
        // U+038C: "Ό" GREEK CAPITAL LETTER OMICRON WITH TONOS
        // U+038E: "Ύ" GREEK CAPITAL LETTER UPSILON WITH TONOS
        // U+038F: "Ώ" GREEK CAPITAL LETTER OMEGA WITH TONOS
        assert_toTitleCaseOfKeyLabel(GREECE,
                "\u03AC\u03AD\u03AE\u03AF\u03CC\u03CD\u03CE",
                "\u0386\u0388\u0389\u038A\u038C\u038E\u038F");
        // U+03CA: "ϊ" GREEK SMALL LETTER IOTA WITH DIALYTIKA
        // U+03CB: "ϋ" GREEK SMALL LETTER UPSILON WITH DIALYTIKA
        // U+0390: "ΐ" GREEK SMALL LETTER IOTA WITH DIALYTIKA AND TONOS
        // U+03B0: "ΰ" GREEK SMALL LETTER UPSILON WITH DIALYTIKA AND TONOS
        // U+03AA: "Ϊ" GREEK CAPITAL LETTER IOTA WITH DIALYTIKA
        // U+03AB: "Ϋ" GREEK CAPITAL LETTER UPSILON WITH DIALYTIKA
        // U+0399: "Ι" GREEK CAPITAL LETTER IOTA
        // U+03A5: "Υ" GREEK CAPITAL LETTER UPSILON
        // U+0308: COMBINING DIAERESIS
        // U+0301: COMBINING GRAVE ACCENT
        assert_toTitleCaseOfKeyLabel(GREECE,
                "\u03CA\u03CB\u0390\u03B0",
                "\u03AA\u03AB\u0399\u0308\u0301\u03A5\u0308\u0301");
    }

    private static void assert_toTitleCaseOfKeyCode(final Locale locale, final int lowerCase,
            final int expected) {
        assertEquals(lowerCase + " in " + locale, expected,
                StringUtils.toTitleCaseOfKeyCode(lowerCase, locale));
    }

    @Test
    public void test_toTitleCaseOfKeyCode() {
        assert_toTitleCaseOfKeyCode(US, Constants.CODE_ENTER, Constants.CODE_ENTER);
        assert_toTitleCaseOfKeyCode(US, Constants.CODE_SPACE, Constants.CODE_SPACE);
        assert_toTitleCaseOfKeyCode(US, Constants.CODE_COMMA, Constants.CODE_COMMA);
        // U+0069: "i" LATIN SMALL LETTER I
        // U+0131: "ı" LATIN SMALL LETTER DOTLESS I
        // U+0130: "İ" LATIN SMALL LETTER I WITH DOT ABOVE
        // U+0049: "I" LATIN SMALL LETTER I
        assert_toTitleCaseOfKeyCode(US, 0x0069, 0x0049); // i -> I
        assert_toTitleCaseOfKeyCode(US, 0x0131, 0x0049); // ı -> I
        assert_toTitleCaseOfKeyCode(TURKEY, 0x0069, 0x0130); // i -> İ
        assert_toTitleCaseOfKeyCode(TURKEY, 0x0131, 0x0049); // ı -> I
        // U+00DF: "ß" LATIN SMALL LETTER SHARP S
        // The title case of "ß" is "SS".
        assert_toTitleCaseOfKeyCode(US, 0x00DF, Constants.CODE_UNSPECIFIED);
        // U+03AC: "ά" GREEK SMALL LETTER ALPHA WITH TONOS
        // U+0386: "Ά" GREEK CAPITAL LETTER ALPHA WITH TONOS
        assert_toTitleCaseOfKeyCode(GREECE, 0x03AC, 0x0386);
        // U+03CA: "ϊ" GREEK SMALL LETTER IOTA WITH DIALYTIKA
        // U+03AA: "Ϊ" GREEK CAPITAL LETTER IOTA WITH DIALYTIKA
        assert_toTitleCaseOfKeyCode(GREECE, 0x03CA, 0x03AA);
        // U+03B0: "ΰ" GREEK SMALL LETTER UPSILON WITH DIALYTIKA AND TONOS
        // The title case of "ΰ" is "\u03A5\u0308\u0301".
        assert_toTitleCaseOfKeyCode(GREECE, 0x03B0, Constants.CODE_UNSPECIFIED);
    }

    private static void assert_capitalizeFirstCodePoint(final Locale locale, final String text,
            final String expected) {
        assertEquals(text + " in " + locale, expected,
                StringUtils.capitalizeFirstCodePoint(text, locale));
    }

    @Test
    public void test_capitalizeFirstCodePoint() {
        assert_capitalizeFirstCodePoint(US, "", "");
        assert_capitalizeFirstCodePoint(US, "a", "A");
        assert_capitalizeFirstCodePoint(US, "à", "À");
        assert_capitalizeFirstCodePoint(US, "ß", "SS");
        assert_capitalizeFirstCodePoint(US, "text", "Text");
        assert_capitalizeFirstCodePoint(US, "iGoogle", "IGoogle");
        assert_capitalizeFirstCodePoint(TURKEY, "iyi", "İyi");
        assert_capitalizeFirstCodePoint(TURKEY, "ısırdı", "Isırdı");
        assert_capitalizeFirstCodePoint(GREECE, "ά", "Ά");
        assert_capitalizeFirstCodePoint(GREECE, "άνεση", "Άνεση");
    }

    private static void assert_capitalizeFirstAndDowncaseRest(final Locale locale,
            final String text, final String expected) {
        assertEquals(text + " in " + locale, expected,
                StringUtils.capitalizeFirstAndDowncaseRest(text, locale));
    }

    @Test
    public void test_capitalizeFirstAndDowncaseRest() {
        assert_capitalizeFirstAndDowncaseRest(US, "", "");
        assert_capitalizeFirstAndDowncaseRest(US, "a", "A");
        assert_capitalizeFirstAndDowncaseRest(US, "à", "À");
        assert_capitalizeFirstAndDowncaseRest(US, "ß", "SS");
        assert_capitalizeFirstAndDowncaseRest(US, "text", "Text");
        assert_capitalizeFirstAndDowncaseRest(US, "iGoogle", "Igoogle");
        assert_capitalizeFirstAndDowncaseRest(US, "invite", "Invite");
        assert_capitalizeFirstAndDowncaseRest(US, "INVITE", "Invite");
        assert_capitalizeFirstAndDowncaseRest(TURKEY, "iyi", "İyi");
        assert_capitalizeFirstAndDowncaseRest(TURKEY, "İYİ", "İyi");
        assert_capitalizeFirstAndDowncaseRest(TURKEY, "ısırdı", "Isırdı");
        assert_capitalizeFirstAndDowncaseRest(TURKEY, "ISIRDI", "Isırdı");
        assert_capitalizeFirstAndDowncaseRest(GREECE, "ά", "Ά");
        assert_capitalizeFirstAndDowncaseRest(GREECE, "άνεση", "Άνεση");
        assert_capitalizeFirstAndDowncaseRest(GREECE, "ΆΝΕΣΗ", "Άνεση");
    }

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
    public void testGetTrailingSingleQuotesCount() {
        assertEquals(0, StringUtils.getTrailingSingleQuotesCount(""));
        assertEquals(1, StringUtils.getTrailingSingleQuotesCount("'"));
        assertEquals(5, StringUtils.getTrailingSingleQuotesCount("'''''"));
        assertEquals(0, StringUtils.getTrailingSingleQuotesCount("a"));
        assertEquals(0, StringUtils.getTrailingSingleQuotesCount("'this"));
        assertEquals(1, StringUtils.getTrailingSingleQuotesCount("'word'"));
        assertEquals(0, StringUtils.getTrailingSingleQuotesCount("I'm"));
    }
}
