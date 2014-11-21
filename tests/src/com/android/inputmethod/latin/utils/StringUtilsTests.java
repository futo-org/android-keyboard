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

package com.android.inputmethod.latin.utils;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.utils.StringUtils;

import java.util.Locale;

@SmallTest
public class StringUtilsTests extends AndroidTestCase {
    private static final Locale US = Locale.US;
    private static final Locale GERMAN = Locale.GERMAN;
    private static final Locale TURKEY = new Locale("tr", "TR");
    private static final Locale GREECE = new Locale("el", "GR");

    private static void assert_toUpperCaseOfStringForLocale(final Locale locale,
            final String lowerCase, final String expected) {
        assertEquals(lowerCase + " in " + locale, expected,
                StringUtils.toUpperCaseOfStringForLocale(
                        lowerCase, true /* needsToUpperCase */, locale));
    }

    public void test_toUpperCaseOfStringForLocale() {
        assert_toUpperCaseOfStringForLocale(US, null, null);
        assert_toUpperCaseOfStringForLocale(US, "", "");
        assert_toUpperCaseOfStringForLocale(US, "aeiou", "AEIOU");
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
        assert_toUpperCaseOfStringForLocale(US,
                "\u00E0\u00E8\u00EE\u00F6\u016B\u00F1\u00E7",
                "\u00C0\u00C8\u00CE\u00D6\u016A\u00D1\u00C7");
        // U+00DF: "ß" LATIN SMALL LETTER SHARP S
        // U+015B: "ś" LATIN SMALL LETTER S WITH ACUTE
        // U+0161: "š" LATIN SMALL LETTER S WITH CARON
        // U+015A: "Ś" LATIN CAPITAL LETTER S WITH ACUTE
        // U+0160: "Š" LATIN CAPITAL LETTER S WITH CARONZ
        assert_toUpperCaseOfStringForLocale(GERMAN,
                "\u00DF\u015B\u0161",
                "SS\u015A\u0160");
        // U+0259: "ə" LATIN SMALL LETTER SCHWA
        // U+0069: "i" LATIN SMALL LETTER I
        // U+0131: "ı" LATIN SMALL LETTER DOTLESS I
        // U+018F: "Ə" LATIN SMALL LETTER SCHWA
        // U+0130: "İ" LATIN SMALL LETTER I WITH DOT ABOVE
        // U+0049: "I" LATIN SMALL LETTER I
        assert_toUpperCaseOfStringForLocale(TURKEY,
                "\u0259\u0069\u0131",
                "\u018F\u0130\u0049");
        // U+03C3: "σ" GREEK SMALL LETTER SIGMA
        // U+03C2: "ς" GREEK SMALL LETTER FINAL SIGMA
        // U+03A3: "Σ" GREEK CAPITAL LETTER SIGMA
        assert_toUpperCaseOfStringForLocale(GREECE,
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
        assert_toUpperCaseOfStringForLocale(GREECE,
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
        assert_toUpperCaseOfStringForLocale(GREECE,
                "\u03CA\u03CB\u0390\u03B0",
                "\u03AA\u03AB\u0399\u0308\u0301\u03A5\u0308\u0301");
    }

    private static void assert_toUpperCaseOfCodeForLocale(final Locale locale, final int lowerCase,
            final int expected) {
        assertEquals(lowerCase + " in " + locale, expected,
                StringUtils.toUpperCaseOfCodeForLocale(
                        lowerCase, true /* needsToUpperCase */, locale));
    }

    public void test_toUpperCaseOfCodeForLocale() {
        assert_toUpperCaseOfCodeForLocale(US, Constants.CODE_ENTER, Constants.CODE_ENTER);
        assert_toUpperCaseOfCodeForLocale(US, Constants.CODE_SPACE, Constants.CODE_SPACE);
        assert_toUpperCaseOfCodeForLocale(US, Constants.CODE_COMMA, Constants.CODE_COMMA);
        // U+0069: "i" LATIN SMALL LETTER I
        // U+0131: "ı" LATIN SMALL LETTER DOTLESS I
        // U+0130: "İ" LATIN SMALL LETTER I WITH DOT ABOVE
        // U+0049: "I" LATIN SMALL LETTER I
        assert_toUpperCaseOfCodeForLocale(US, 0x0069, 0x0049); // i -> I
        assert_toUpperCaseOfCodeForLocale(US, 0x0131, 0x0049); // ı -> I
        assert_toUpperCaseOfCodeForLocale(TURKEY, 0x0069, 0x0130); // i -> İ
        assert_toUpperCaseOfCodeForLocale(TURKEY, 0x0131, 0x0049); // ı -> I
        // U+00DF: "ß" LATIN SMALL LETTER SHARP S
        // The title case of "ß" is "SS".
        assert_toUpperCaseOfCodeForLocale(US, 0x00DF, Constants.CODE_UNSPECIFIED);
        // U+03AC: "ά" GREEK SMALL LETTER ALPHA WITH TONOS
        // U+0386: "Ά" GREEK CAPITAL LETTER ALPHA WITH TONOS
        assert_toUpperCaseOfCodeForLocale(GREECE, 0x03AC, 0x0386);
        // U+03CA: "ϊ" GREEK SMALL LETTER IOTA WITH DIALYTIKA
        // U+03AA: "Ϊ" GREEK CAPITAL LETTER IOTA WITH DIALYTIKA
        assert_toUpperCaseOfCodeForLocale(GREECE, 0x03CA, 0x03AA);
        // U+03B0: "ΰ" GREEK SMALL LETTER UPSILON WITH DIALYTIKA AND TONOS
        // The title case of "ΰ" is "\u03A5\u0308\u0301".
        assert_toUpperCaseOfCodeForLocale(GREECE, 0x03B0, Constants.CODE_UNSPECIFIED);
    }

    private static void assert_capitalizeFirstCodePoint(final Locale locale, final String text,
            final String expected) {
        assertEquals(text + " in " + locale, expected,
                StringUtils.capitalizeFirstCodePoint(text, locale));
    }

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
}
