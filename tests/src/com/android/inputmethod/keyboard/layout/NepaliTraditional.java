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
import com.android.inputmethod.keyboard.layout.Hindi.HindiSymbols;
import com.android.inputmethod.keyboard.layout.NepaliRomanized.NepaliRomanizedCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

/**
 * The nepali_traditional keyboard.
 */
public final class NepaliTraditional extends LayoutBase {
    private static final String LAYOUT_NAME = "nepali_traditional";

    public NepaliTraditional(final LayoutCustomizer customizer) {
        super(customizer, HindiSymbols.class, SymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    public static class NepaliTraditionalCustomizer extends NepaliRomanizedCustomizer {
        public NepaliTraditionalCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey[] getRightShiftKeys(final boolean isPhone) { return EMPTY_KEYS; }

        @Override
        public ExpectedKey[] getKeysRightToSpacebar(final boolean isPhone) {
            if (isPhone) {
                // U+094D: "्" DEVANAGARI SIGN VIRAMA
                return joinKeys(key("\u094D", PHONE_PUNCTUATION_MORE_KEYS));
            }
            return super.getKeysRightToSpacebar(isPhone);
        }
    }

    @Override
    ExpectedKey[][] getCommonAlphabetLayout(boolean isPhone) {
        final ExpectedKeyboardBuilder builder = new ExpectedKeyboardBuilder(ALPHABET_COMMON);
        if (isPhone) {
            builder.addKeysOnTheRightOfRow(3,
                    // U+0947: "े" DEVANAGARI VOWEL SIGN E
                    // U+0903: "ः‍" DEVANAGARI SIGN VISARGA
                    // U+093D: "ऽ" DEVANAGARI SIGN AVAGRAHA
                    key("\u0947", joinMoreKeys("\u0903", "\u093D")),
                    // U+0964: "।" DEVANAGARI DANDA
                    "\u0964",
                    // U+0930: "र" DEVANAGARI LETTER RA
                    // U+0930/U+0941: "रु" DEVANAGARI LETTER RA/DEVANAGARI VOWEL SIGN U
                    key("\u0930", moreKey("\u0930\u0941")));
        } else {
            builder.addKeysOnTheRightOfRow(3,
                    // U+0903: "ः" DEVANAGARI SIGN VISARGA
                    // U+093D: "ऽ" DEVANAGARI SIGN AVAGRAHA
                    key("\u0903", moreKey("\u093D")),
                    // U+0947: "े" DEVANAGARI VOWEL SIGN E
                    // U+0964: "।" DEVANAGARI DANDA
                    "\u0947", "\u0964",
                    // U+0930: "र" DEVANAGARI LETTER RA
                    key("\u0930", moreKey("!")),
                    // U+094D: "्" DEVANAGARI SIGN VIRAMA
                    key("\u094D", moreKey("?")));
        }
        return builder.build();
    }

    @Override
    ExpectedKey[][] getCommonAlphabetShiftLayout(boolean isPhone, final int elementId) {
        if (elementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED) {
            return getCommonAlphabetLayout(isPhone);
        }
        final ExpectedKeyboardBuilder builder = new ExpectedKeyboardBuilder(
                ALPHABET_SHIFTED_COMMON);
        if (isPhone) {
            builder.addKeysOnTheRightOfRow(3,
                    // U+0902: "ं" DEVANAGARI SIGN ANUSVARA
                    // U+0919: "ङ" DEVANAGARI LETTER NGA
                    "\u0902", "\u0919",
                    // U+0948: "ै" DEVANAGARI VOWEL SIGN AI
                    // U+0936/U+094D/U+0930:
                    //     "श्र" DEVANAGARI LETTER SHA/DEVANAGARI SIGN VIRAMA/DEVANAGARI LETTER RA
                    key("\u0948", moreKey("\u0936\u094D\u0930")));
        } else {
            builder.addKeysOnTheRightOfRow(3,
                    // U+0902: "ं" DEVANAGARI SIGN ANUSVARA
                    // U+0919: "ङ" DEVANAGARI LETTER NGA
                    "\u0902", "\u0919",
                    // U+0948: "ै" DEVANAGARI VOWEL SIGN AI
                    // U+0936/U+094D/U+0930:
                    //     "श्र" DEVANAGARI LETTER SHA/DEVANAGARI SIGN VIRAMA/DEVANAGARI LETTER RA
                    key("\u0948", moreKey("\u0936\u094D\u0930")),
                    // U+0930/U+0941: "रु" DEVANAGARI LETTER RA/DEVANAGARI VOWEL SIGN U
                    key("\u0930\u0941", moreKey("!")),
                    "?");
        }
        return builder.build();
    }

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+091F: "ट" DEVANAGARI LETTER TTA
                    // U+0967: "१" DEVANAGARI DIGIT ONE
                    key("\u091F", joinMoreKeys("\u0967", "1")),
                    // U+0927: "ध" DEVANAGARI LETTER DHA
                    // U+0968: "२" DEVANAGARI DIGIT TWO
                    key("\u0927", joinMoreKeys("\u0968", "2")),
                    // U+092D: "भ" DEVANAGARI LETTER BHA
                    // U+0969: "३" DEVANAGARI DIGIT THREE
                    key("\u092D", joinMoreKeys("\u0969", "3")),
                    // U+091A: "च" DEVANAGARI LETTER CA
                    // U+096A: "४" DEVANAGARI DIGIT FOUR
                    key("\u091A", joinMoreKeys("\u096A", "4")),
                    // U+0924: "त" DEVANAGARI LETTER TA
                    // U+096B: "५" DEVANAGARI DIGIT FIVE
                    key("\u0924", joinMoreKeys("\u096B", "5")),
                    // U+0925: "थ" DEVANAGARI LETTER THA
                    // U+096C: "६" DEVANAGARI DIGIT SIX
                    key("\u0925", joinMoreKeys("\u096C", "6")),
                    // U+0917: "ग" DEVANAGARI LETTER G
                    // U+096D: "७" DEVANAGARI DIGIT SEVEN
                    key("\u0917", joinMoreKeys("\u096D", "7")),
                    // U+0937: "ष" DEVANAGARI LETTER SSA
                    // U+096E: "८" DEVANAGARI DIGIT EIGHT
                    key("\u0937", joinMoreKeys("\u096E", "8")),
                    // U+092F: "य" DEVANAGARI LETTER YA
                    // U+096F: "९" DEVANAGARI DIGIT NINE
                    key("\u092F", joinMoreKeys("\u096F", "9")),
                    // U+0909: "उ" DEVANAGARI LETTER U
                    // U+0966: "०" DEVANAGARI DIGIT ZERO
                    key("\u0909", joinMoreKeys("\u0966", "0")),
                    // U+0907: "इ" DEVANAGARI LETTER I
                    // U+0914: "औ" DEVANAGARI LETTER AU
                    key("\u0907", moreKey("\u0914")))
            .setKeysOfRow(2,
                    // U+092C: "ब" DEVANAGARI LETTER BA
                    // U+0915: "क" DEVANAGARI LETTER KA
                    // U+092E: "म" DEVANAGARI LETTER MA
                    // U+093E: "ा" DEVANAGARI VOWEL SIGN AA
                    // U+0928: "न" DEVANAGARI LETTER NA
                    // U+091C: "ज" DEVANAGARI LETTER JA
                    // U+0935: "व" DEVANAGARI LETTER VA
                    // U+092A: "प" DEVANAGARI LETTER PA
                    // U+093F: "ि" DEVANAGARI VOWEL SIGN I
                    // U+0938: "स" DEVANAGARI LETTER SA
                    // U+0941: "ु" DEVANAGARI VOWEL SIGN U
                    "\u092C", "\u0915", "\u092E", "\u093E", "\u0928", "\u091C", "\u0935", "\u092A",
                    "\u093F", "\u0938", "\u0941")
            .setKeysOfRow(3,
                    // U+0936: "श" DEVANAGARI LETTER SHA
                    // U+0939: "ह" DEVANAGARI LETTER HA
                    // U+0905: "अ" DEVANAGARI LETTER A
                    // U+0916: "ख" DEVANAGARI LETTER KHA
                    // U+0926: "द" DEVANAGARI LETTER DA
                    // U+0932: "ल" DEVANAGARI LETTER LA
                    "\u0936", "\u0939", "\u0905", "\u0916", "\u0926", "\u0932")
            .build();

    private static final ExpectedKey[][] ALPHABET_SHIFTED_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+0924/U+094D/U+0924:
                    // "त्त" DEVANAGARI LETTER TA/DEVANAGARI SIGN VIRAMA/DEVANAGARI LETTER TA
                    // U+091E: "ञ" DEVANAGARI LETTER NYA
                    // U+091C/U+094D/U+091E: "ज्ञ" DEVANAGARI LETTER JA/DEVANAGARI SIGN
                    // VIRAMA/DEVANAGARI LETTER NYA
                    // U+0965: "॥" DEVANAGARI DOUBLE DANDA
                    key("\u0924\u094D\u0924",
                            joinMoreKeys("\u091E", "\u091C\u094D\u091E", "\u0965")),
                    // U+0921/U+094D/U+0922:
                    // "ड्ढ" DEVANAGARI LETTER DDA/DEVANAGARI SIGN VIRAMA/DEVANAGARI LETTER DDHA
                    // U+0908: "ई" DEVANAGARI LETTER II
                    key("\u0921\u094D\u0922", moreKey("\u0908")),
                    // U+0910: "ऐ" DEVANAGARI LETTER AI
                    // U+0918: "घ" DEVANAGARI LETTER GHA
                    key("\u0910", moreKey("\u0918")),
                    // U+0926/U+094D/U+0935:
                    // "द्व" DEVANAGARI LETTER DA/DEVANAGARI SIGN VIRAMA/DEVANAGARI LETTER VA
                    // U+0926/U+094D/U+0927:
                    // "द्ध" DEVANAGARI LETTER DA/DEVANAGARI SIGN VIRAMA/DEVANAGARI LETTER DHA
                    key("\u0926\u094D\u0935", moreKey("\u0926\u094D\u0927")),
                    // U+091F/U+094D/U+091F:
                    // "ट्ट" DEVANAGARI LETTER TTA/DEVANAGARI SIGN VIRAMA/DEVANAGARI LETTER TTA
                    // U+091B: "छ" DEVANAGARI LETTER CHA
                    key("\u091F\u094D\u091F", moreKey("\u091B")),
                    // U+0920/U+094D/U+0920:
                    // "ठ्ठ" DEVANAGARI LETTER TTHA/DEVANAGARI SIGN VIRAMA/DEVANAGARI LETTER TTHA
                    // U+091F: "ट" DEVANAGARI LETTER TTA
                    key("\u0920\u094D\u0920", moreKey("\u091F")),
                    // U+090A: "ऊ" DEVANAGARI LETTER UU
                    // U+0920: "ठ" DEVANAGARI LETTER TTHA
                    key("\u090A", moreKey("\u0920")),
                    // U+0915/U+094D/U+0937:
                    // "क्ष" DEVANAGARI LETTER KA/DEVANAGARI SIGN VIRAMA/DEVANAGARI LETTER SSA
                    // U+0921: "ड" DEVANAGARI LETTER DDA
                    key("\u0915\u094D\u0937", moreKey("\u0921")),
                    // U+0907: "इ" DEVANAGARI LETTER I
                    // U+0922: "ढ" DEVANAGARI LETTER DDHA
                    key("\u0907", moreKey("\u0922")),
                    // U+090F: "ए" DEVANAGARI LETTER E
                    // U+0923: "ण" DEVANAGARI LETTER NNA
                    key("\u090F", moreKey("\u0923")),
                    // U+0943: "ृ" DEVANAGARI VOWEL SIGN VOCALIC R
                    // U+0913: "ओ" DEVANAGARI LETTER O
                    key("\u0943", moreKey("\u0913")))
            .setKeysOfRow(2,
                    // U+0906: "आ" DEVANAGARI LETTER AA
                    // U+0919/U+094D: "ङ्" DEVANAGARI LETTER NGA/DEVANAGARI SIGN VIRAMA
                    // U+0921/U+094D/U+0921:
                    //     "ड्ड" DEVANAGARI LETTER DDA/DEVANAGARI SIGN VIRAMA/DEVANAGARI LETTER DDA
                    // U+0901: "ँ" DEVANAGARI SIGN CANDRABINDU
                    // U+0926/U+094D/U+0926:
                    //     "द्द" DEVANAGARI LETTER DA/DEVANAGARI SIGN VIRAMA/DEVANAGARI LETTER DA
                    // U+091D: "झ" DEVANAGARI LETTER JHA
                    // U+094B: "ो" DEVANAGARI VOWEL SIGN O
                    // U+092B: "फ" DEVANAGARI LETTER PHA
                    // U+0940: "ी" DEVANAGARI VOWEL SIGN II
                    // U+091F/U+094D/U+0920:
                    //     "ट्ठ" DEVANAGARI LETTER TTA/DEVANAGARI SIGN VIRAMA/DEVANAGARI LETTER TTHA
                    // U+0942: "ू" DEVANAGARI VOWEL SIGN UU
                    "\u0906", "\u0919\u094D", "\u0921\u094D\u0921", "\u0901", "\u0926\u094D\u0926",
                    "\u091D", "\u094B", "\u092B", "\u0940", "\u091F\u094D\u0920", "\u0942")
            .setKeysOfRow(3,
                    // U+0915/U+094D: "क्" DEVANAGARI LETTER KA/DEVANAGARI SIGN VIRAMA
                    // U+0939/U+094D/U+092E:
                    //     "ह्म" DEVANAGARI LETTER HA/DEVANAGARI SIGN VIRAMA/DEVANAGARI LETTER MA
                    // U+090B: "ऋ" DEVANAGARI LETTER VOCALIC R
                    // U+0950: "ॐ" DEVANAGARI OM
                    // U+094C: "ौ" DEVANAGARI VOWEL SIGN AU
                    // U+0926/U+094D/U+092F:
                    //     "द्य" DEVANAGARI LETTER DA/DEVANAGARI SIGN VIRAMA/DEVANAGARI LETTER YA
                    "\u0915\u094D", "\u0939\u094D\u092E", "\u090B", "\u0950", "\u094C",
                    "\u0926\u094D\u092F")
            .build();
}
