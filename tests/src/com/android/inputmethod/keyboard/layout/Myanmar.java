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

package com.android.inputmethod.keyboard.layout;

import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;
import com.android.inputmethod.latin.Constants;

import java.util.Locale;

/**
 * The Myanmar alphabet keyboard.
 */
public final class Myanmar extends LayoutBase {
    private static final String LAYOUT_NAME = "myanmar";

    public Myanmar(final LayoutCustomizer customizer) {
        super(customizer, Symbols.class, SymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    public static class MyanmarCustomizer extends LayoutCustomizer {
        public MyanmarCustomizer(final Locale locale) { super(locale); }

        @Override
        public int getNumberOfRows() { return 5; }

        @Override
        public ExpectedKey getAlphabetKey() { return MYANMAR_ALPHABET_KEY; }

        @Override
        public ExpectedKey[] getRightShiftKeys(final boolean isPhone) {
            return isPhone ? EMPTY_KEYS : EXCLAMATION_AND_QUESTION_MARKS;
        }

        @Override
        public ExpectedKey[] getKeysLeftToSpacebar(final boolean isPhone) {
            // U+002C: "," COMMA
            // U+104A: "၊" MYANMAR SIGN LITTLE SECTION
            return isPhone ? joinKeys(key("\u002C", SETTINGS_KEY))
                    : joinKeys(key("\u104A", moreKey(","), SETTINGS_KEY));
        }

        @Override
        public ExpectedKey[] getKeysRightToSpacebar(final boolean isPhone) {
            // U+104B: "။" MYANMAR SIGN SECTION
            final ExpectedKey periodKey = key("\u104B", getPunctuationMoreKeys(isPhone));
            return joinKeys(periodKey);
        }

        @Override
        public ExpectedKey[] getPunctuationMoreKeys(final boolean isPhone) {
            return isPhone ? MYANMAR_PHONE_PUNCTUATION_MORE_KEYS
                    : MYANMAR_TABLET_PUNCTUATION_MORE_KEYS;
        }

        // U+1000: "က" MYANMAR LETTER KA
        // U+1001: "ခ" MYANMAR LETTER KHA
        // U+1002: "ဂ" MYANMAR LETTER GA
        private static final ExpectedKey MYANMAR_ALPHABET_KEY = key(
                "\u1000\u1001\u1002", Constants.CODE_SWITCH_ALPHA_SYMBOL);

        // U+104A: "၊" MYANMAR SIGN LITTLE SECTION
        // Punctuation more keys for phone form factor.
        private static final ExpectedKey[] MYANMAR_PHONE_PUNCTUATION_MORE_KEYS = joinKeys(
                "\u104A", ".", "?", "!", "#", ")", "(", "/", ";",
                "...", "'", "@", ":", "-", "\"", "+", "%", "&");
        // Punctuation more keys for tablet form factor.
        private static final ExpectedKey[] MYANMAR_TABLET_PUNCTUATION_MORE_KEYS = joinKeys(
                ".", "'", "#", ")", "(", "/", ";", "@",
                "...", ":", "-", "\"", "+", "%", "&");
    }

    @Override
    ExpectedKey[][] getCommonAlphabetLayout(final boolean isPhone) { return ALPHABET_COMMON; }

    @Override
    public ExpectedKey[][] getCommonAlphabetShiftLayout(final boolean isPhone,
            final int elementId) {
        if (elementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED) {
            return getCommonAlphabetLayout(isPhone);
        }
        return ALPHABET_SHIFTED_COMMON;
    }

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+1041: "၁" MYANMAR DIGIT ONE
                    key("\u1041", moreKey("1")),
                    // U+1042: "၂" MYANMAR DIGIT TWO
                    key("\u1042", moreKey("2")),
                    // U+1043: "၃" MYANMAR DIGIT THREE
                    key("\u1043", moreKey("3")),
                    // U+1044: "၄" MYANMAR DIGIT FOUR
                    key("\u1044", moreKey("4")),
                    // U+1045: "၅" MYANMAR DIGIT FIVE
                    key("\u1045", moreKey("5")),
                    // U+1046: "၆" MYANMAR DIGIT SIX
                    key("\u1046", moreKey("6")),
                    // U+1047: "၇" MYANMAR DIGIT SEVEN
                    key("\u1047", moreKey("7")),
                    // U+1048: "၈" MYANMAR DIGIT EIGHT
                    key("\u1048", moreKey("8")),
                    // U+1049: "၉" MYANMAR DIGIT NINE
                    key("\u1049", moreKey("9")),
                    // U+1040: "၀" MYANMAR DIGIT ZERO
                    key("\u1040", moreKey("0")))
            .setKeysOfRow(2,
                    // U+1006: "ဆ" MYANMAR LETTER CHA
                    // U+1039/U+1006: "္ဆ" MYANMAR SIGN VIRAMA/MYANMAR LETTER CHA
                    key("\u1006", moreKey("\u1039\u1006")),
                    // U+1010: "တ" MYANMAR LETTER TA
                    // U+1039/U+1010: "္တ" MYANMAR SIGN VIRAMA/MYANMAR LETTER TA
                    key("\u1010", moreKey("\u1039\u1010")),
                    // U+1014: "န" MYANMAR LETTER NA
                    // U+1039/U+1014: "္န" MYANMAR SIGN VIRAMA/MYANMAR LETTER NA
                    key("\u1014", moreKey("\u1039\u1014")),
                    // U+1019: "မ" MYANMAR LETTER MA
                    // U+1039/U+1019: "္မ" MYANMAR SIGN VIRAMA/MYANMAR LETTER MA
                    key("\u1019", moreKey("\u1039\u1019")),
                    // U+1021: "အ" MYANMAR LETTER A
                    // U+1015: "ပ" MYANMAR LETTER PA
                    "\u1021", "\u1015",
                    // U+1000: "က" MYANMAR LETTER KA
                    // U+1039/U+1000: "္က" MYANMAR SIGN VIRAMA/MYANMAR LETTER KA
                    key("\u1000", moreKey("\u1039\u1000")),
                    // U+1004: "င" MYANMAR LETTER NGA
                    // U+101E: "သ" MYANMAR LETTER SA
                    "\u1004", "\u101E",
                    // U+1005: "စ" MYANMAR LETTER CA
                    // U+1039/U+1005: "္စ" MYANMAR SIGN VIRAMA/MYANMAR LETTER CA
                    key("\u1005", moreKey("\u1039\u1005")))
            .setKeysOfRow(3,
                    // U+1031: "ေ" MYANMAR VOWEL SIGN E
                    // U+103B: "ျ" MYANMAR CONSONANT SIGN MEDIAL YA
                    // U+103C: "ြ" MYANMAR CONSONANT SIGN MEDIAL RA
                    "\u1031", "\u103B", "\u103C",
                    // U+103D: "ွ" MYANMAR CONSONANT SIGN MEDIAL WA
                    // U+103E: "ှ" MYANMAR CONSONANT SIGN MEDIAL HA
                    // U+103D/U+103E:
                    //     "ွှ" MYANMAR CONSONANT SIGN MEDIAL WA/MYANMAR CONSONANT SIGN MEDIAL HA
                    key("\u103D", joinMoreKeys("\u103E", "\u103D\u103E")),
                    // U+102D: "ိ" MYANMAR VOWEL SIGN I
                    // U+102E: "ီ" MYANMAR VOWEL SIGN II
                    key("\u102D", moreKey("\u102E")),
                    // U+102F: "ု" MYANMAR VOWEL SIGN U
                    // U+1030: "ူ" MYANMAR VOWEL SIGN UU
                    key("\u102F", moreKey("\u1030")),
                    // U+102C: "ာ" MYANMAR VOWEL SIGN AA
                    // U+102B: "ါ" MYANMAR VOWEL SIGN TALL AA
                    key("\u102C", moreKey("\u102B")),
                    // U+103A: "်" MYANMAR SIGN ASAT
                    // U+1032: "ဲ" MYANMAR VOWEL SIGN AI
                    key("\u103A", moreKey("\u1032")),
                    // U+1037: "့" MYANMAR SIGN DOT BELOW
                    // U+1036: "ံ" MYANMAR SIGN ANUSVARA
                    key("\u1037", moreKey("\u1036")),
                    // U+1038: "း" MYANMAR SIGN VISARGA
                    "\u1038")
            .setKeysOfRow(4,
                    // U+1016: "ဖ" MYANMAR LETTER PHA
                    "\u1016",
                    // U+1011: "ထ" MYANMAR LETTER THA
                    // U+1039/U+1011: "္ထ" MYANMAR SIGN VIRAMA/MYANMAR LETTER THA
                    key("\u1011", moreKey("\u1039\u1011")),
                    // U+1001: "ခ" MYANMAR LETTER KHA
                    // U+1039/U+1001: "္ခ" MYANMAR SIGN VIRAMA/MYANMAR LETTER KHA
                    key("\u1001", moreKey("\u1039\u1001")),
                    // U+101C: "လ" MYANMAR LETTER LA
                    // U+1039/U+101C: "္လ" MYANMAR SIGN VIRAMA/MYANMAR LETTER LA
                    key("\u101C", moreKey("\u1039\u101C")),
                    // U+1018: "ဘ" MYANMAR LETTER BHA
                    // U+1039/U+1018: "္ဘ" MYANMAR SIGN VIRAMA/MYANMAR LETTER BHA
                    key("\u1018", moreKey("\u1039\u1018")),
                    // U+100A: "ည" MYANMAR LETTER NNYA
                    // U+1009: "ဉ" MYANMAR LETTER NYA
                    key("\u100A", moreKey("\u1009")),
                    // U+101B: "ရ" MYANMAR LETTER RA
                    // U+101D: "ဝ" MYANMAR LETTER WA
                    "\u101B", "\u101D")
            .build();

    private static final ExpectedKey[][] ALPHABET_SHIFTED_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+1027: "ဧ" MYANMAR LETTER E
                    // U+104F: "၏" MYANMAR SYMBOL GENITIVE
                    // U+1024: "ဤ" MYANMAR LETTER II
                    // U+1023: "ဣ" MYANMAR LETTER I
                    // U+104E: "၎" MYANMAR SYMBOL AFOREMENTIONED
                    // U+1000/U+103B/U+1015/U+103A: "ကျပ်" MYANMAR LETTER KA
                    //     /MYANMAR CONSONANT SIGN MEDIAL YA/MYANMAR LETTER PA/MYANMAR SIGN ASAT
                    // U+1029: "ဩ" MYANMAR LETTER O
                    // U+102A: "ဪ" MYANMAR LETTER AU
                    // U+104D: "၍" MYANMAR SYMBOL COMPLETED
                    // U+104C: "၌" MYANMAR SYMBOL LOCATIVE
                    "\u1027", "\u104F", "\u1024", "\u1023", "\u104E", "\u1000\u103B\u1015\u103A",
                    "\u1029", "\u102A", "\u104D", "\u104C")
            .setKeysOfRow(2,
                    // U+1017: "ဗ" MYANMAR LETTER BA
                    // U+1039/U+1017: "္ဗ" MYANMAR SIGN VIRAMA/MYANMAR LETTER BA
                    key("\u1017", moreKey("\u1039\u1017")),
                    // U+1012: "ဒ" MYANMAR LETTER DA
                    // U+1039/U+1012: "္ဒ" MYANMAR SIGN VIRAMA/MYANMAR LETTER DA
                    key("\u1012", moreKey("\u1039\u1012")),
                    // U+1013: "ဓ" MYANMAR LETTER DHA
                    // U+1039/U+1013: "္ဓ" MYANMAR SIGN VIRAMA/MYANMAR LETTER DHA
                    key("\u1013", moreKey("\u1039\u1013")),
                    // U+1003: "ဃ" MYANMAR LETTER GHA
                    // U+100E: "ဎ" MYANMAR LETTER DDHA
                    // U+103F: "ဿ" MYANMAR LETTER GREAT SA
                    // U+100F: "ဏ" MYANMAR LETTER NNA
                    "\u1003", "\u100E", "\u103F", "\u100F",
                    // U+1008: "ဈ" MYANMAR LETTER JHA
                    // U+1039/U+1008: "္ဈ" MYANMAR SIGN VIRAMA/MYANMAR LETTER JHA
                    key("\u1008", moreKey("\u1039\u1008")),
                    // U+1007: "ဇ" MYANMAR LETTER JA
                    // U+1039/U+1007: "္ဇ" MYANMAR SIGN VIRAMA/MYANMAR LETTER JA
                    key("\u1007", moreKey("\u1039\u1007")),
                    // U+1002: "ဂ" MYANMAR LETTER GA
                    // U+1039/U+1002: "္ဂ" MYANMAR SIGN VIRAMA/MYANMAR LETTER GA
                    key("\u1002", moreKey("\u1039\u1002")))
            .setKeysOfRow(3,
                    // U+101A: "ယ" MYANMAR LETTER YA
                    // U+1039: "္" MYANMAR SIGN VIRAMA
                    // U+1004/U+103A/U+1039: "င်္င" MYANMAR LETTER NGA
                    //     /MYANMAR SIGN ASAT/MYANMAR SIGN VIRAMA
                    // U+103E: "ှ" MYANMAR CONSONANT SIGN MEDIAL HA
                    // U+102E: "ီ" MYANMAR VOWEL SIGN II
                    // U+1030: "ူ" MYANMAR VOWEL SIGN UU
                    // U+102B: "ါ" MYANMAR VOWEL SIGN TALL AA
                    // U+1032: "ဲ" MYANMAR VOWEL SIGN AI
                    // U+1036: "ံ" MYANMAR SIGN ANUSVARA
                    // U+101F: "ဟ" MYANMAR LETTER HA
                    "\u101A", "\u1039", "\u1004\u103A\u1039", "\u103E", "\u102E", "\u1030",
                    "\u102B", "\u1032", "\u1036", "\u101F")
            .setKeysOfRow(4,
                    // U+1025: "ဥ" MYANMAR LETTER U
                    // U+1026: "ဦ" MYANMAR LETTER UU
                    // U+100C: "ဌ" MYANMAR LETTER TTHA
                    // U+100B: "ဋ" MYANMAR LETTER TTA
                    // U+100D: "ဍ" MYANMAR LETTER DDA
                    // U+1020: "ဠ" MYANMAR LETTER LLA
                    // U+100B/U+1039/U+100C: "ဋ္ဌ" MYANMAR LETTER TTA
                    //     /MYANMAR SIGN VIRAMA/MYANMAR LETTER TTHA
                    "\u1025", "\u1026", "\u100C", "\u100B", "\u100D", "\u1020",
                    "\u100B\u1039\u100C",
                    // U+100F/U+1039/U+100D: "ဏ္ဍ" MYANMAR LETTER NNA
                    //     /MYANMAR SIGN VIRAMA/MYANMAR LETTER DDA
                    // U+100F/U+1039/U+100C: "ဏ္ဌ" MYANMAR LETTER NNA
                    //     /MYANMAR SIGN VIRAMA/MYANMAR LETTER TTHA
                    key("\u100F\u1039\u100D", moreKey("\u100F\u1039\u100C")))
            .build();
}
