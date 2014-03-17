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
import com.android.inputmethod.keyboard.layout.Hindi.HindiCustomizer;
import com.android.inputmethod.keyboard.layout.Hindi.HindiSymbols;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

/**
 * The nepali_romanized layout
 */
public final class NepaliRomanized extends LayoutBase {
    private static final String LAYOUT_NAME = "nepali_romanized";

    public NepaliRomanized(final LayoutCustomizer customizer) {
        super(customizer, HindiSymbols.class, SymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    public static class NepaliRomanizedCustomizer extends HindiCustomizer {
        public NepaliRomanizedCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey getCurrencyKey() { return CURRENCY_NEPALI; }

        @Override
        public ExpectedKey[] getSpaceKeys(final boolean isPhone) {
            return joinKeys(SPACE_KEY, key(ZWNJ_KEY, ZWJ_KEY));
        }

        // U+0930/U+0941/U+002E "रु." NEPALESE RUPEE SIGN
        private static final ExpectedKey CURRENCY_NEPALI = key("\u0930\u0941\u002E",
                Symbols.DOLLAR_SIGN, Symbols.CENT_SIGN, Symbols.EURO_SIGN, Symbols.POUND_SIGN,
                Symbols.YEN_SIGN, Symbols.PESO_SIGN);
    }

    @Override
    ExpectedKey[][] getCommonAlphabetLayout(boolean isPhone) { return ALPHABET_COMMON; }

    @Override
    ExpectedKey[][] getCommonAlphabetShiftLayout(boolean isPhone, final int elementId) {
        if (elementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED) {
            return getCommonAlphabetLayout(isPhone);
        }
        return ALPHABET_SHIFTED_COMMON;
    }

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+091F: "ट" DEVANAGARI LETTER TTA
                    // U+0967: "१" DEVANAGARI DIGIT ONE
                    // U+093C: "़" DEVANAGARI SIGN NUKTA
                    key("\u091F", joinMoreKeys("\u0967", "1", "\u093C")),
                    // U+094C: "ौ" DEVANAGARI VOWEL SIGN AU
                    // U+0968: "२" DEVANAGARI DIGIT TWO
                    key("\u094C", joinMoreKeys("\u0968", "2")),
                    // U+0947: "े" DEVANAGARI VOWEL SIGN E
                    // U+0969: "३" DEVANAGARI DIGIT THREE
                    key("\u0947", joinMoreKeys("\u0969", "3")),
                    // U+0930: "र" DEVANAGARI LETTER RA
                    // U+096A: "४" DEVANAGARI DIGIT FOUR
                    key("\u0930", joinMoreKeys("\u096A", "4")),
                    // U+0924: "त" DEVANAGARI LETTER TA
                    // U+096B: "५" DEVANAGARI DIGIT FIVE
                    key("\u0924", joinMoreKeys("\u096B", "5")),
                    // U+092F: "य" DEVANAGARI LETTER YA
                    // U+096C: "६" DEVANAGARI DIGIT SIX
                    key("\u092F", joinMoreKeys("\u096C", "6")),
                    // U+0941: "ु" DEVANAGARI VOWEL SIGN U
                    // U+096D: "७" DEVANAGARI DIGIT SEVEN
                    key("\u0941", joinMoreKeys("\u096D", "7")),
                    // U+093F: "ि" DEVANAGARI VOWEL SIGN I
                    // U+096E: "८" DEVANAGARI DIGIT EIGHT
                    key("\u093F", joinMoreKeys("\u096E", "8")),
                    // U+094B: "ो" DEVANAGARI VOWEL SIGN O
                    // U+096F: "९" DEVANAGARI DIGIT NINE
                    key("\u094B", joinMoreKeys("\u096F", "9")),
                    // U+092A: "प" DEVANAGARI LETTER PA
                    // U+0966: "०" DEVANAGARI DIGIT ZERO
                    key("\u092A", joinMoreKeys("\u0966", "0")),
                    // U+0907: "इ" DEVANAGARI LETTER I
                    key("\u0907"))
            // U+093E: "ा" DEVANAGARI VOWEL SIGN AA
            // U+0938: "स" DEVANAGARI LETTER SA
            // U+0926: "द" DEVANAGARI LETTER DA
            // U+0909: "उ" DEVANAGARI LETTER U
            // U+0917: "ग" DEVANAGARI LETTER GA
            // U+0939: "ह" DEVANAGARI LETTER HA
            // U+091C: "ज" DEVANAGARI LETTER JA
            // U+0915: "क" DEVANAGARI LETTER KA
            // U+0932: "ल" DEVANAGARI LETTER LA
            // U+090F: "ए" DEVANAGARI LETTER E
            // U+0950: "ॐ" DEVANAGARI OM
            .setLabelsOfRow(2,
                    "\u093E", "\u0938", "\u0926", "\u0909", "\u0917", "\u0939", "\u091C", "\u0915",
                    "\u0932", "\u090F", "\u0950")
            // U+0937: "ष" DEVANAGARI LETTER SSA
            // U+0921: "ड" DEVANAGARI LETTER DDA
            // U+091A: "च" DEVANAGARI LETTER CA
            // U+0935: "व" DEVANAGARI LETTER VA
            // U+092C: "ब" DEVANAGARI LETTER BHA
            // U+0928: "न" DEVANAGARI LETTER NA
            // U+092E: "म" DEVANAGARI LETTER MA
            // U+0964: "।" DEVANAGARI DANDA
            // U+094D: "्" DEVANAGARI SIGN VIRAMA
            .setLabelsOfRow(3,
                    "\u0937", "\u0921", "\u091A", "\u0935", "\u092C", "\u0928", "\u092E", "\u0964",
                    "\u094D")
            // U+0964: "।" DEVANAGARI DANDA
            // U+093D: "ऽ" DEVANAGARI SIGN AVAGRAHA
            .setMoreKeysOf("\u0964", "\u093D")
            .build();

    private static final ExpectedKey[][] ALPHABET_SHIFTED_COMMON = new ExpectedKeyboardBuilder()
            // U+0920: "ठ" DEVANAGARI LETTER TTHA
            // U+0914: "औ" DEVANAGARI LETTER AU
            // U+0948: "ै" DEVANAGARI VOWEL SIGN AI
            // U+0943: "ृ" DEVANAGARI VOWEL SIGN VOCALIC R
            // U+0925: "थ" DEVANAGARI LETTER THA
            // U+091E: "ञ" DEVANAGARI LETTER NYA
            // U+0942: "ू" DEVANAGARI VOWEL SIGN UU
            // U+0940: "ी" DEVANAGARI VOWEL SIGN II
            // U+0913: "ओ" DEVANAGARI LETTER O
            // U+092B: "फ" DEVANAGARI LETTER PHA
            // U+0908: "ई" DEVANAGARI LETTER II
            .setLabelsOfRow(1,
                    "\u0920", "\u0914", "\u0948", "\u0943", "\u0925", "\u091E", "\u0942", "\u0940",
                    "\u0913", "\u092B", "\u0908")
            // U+0906: "आ" DEVANAGARI LETTER AA
            // U+0936: "श" DEVANAGARI LETTER SHA
            // U+0927: "ध" DEVANAGARI LETTER DHA
            // U+090A: "ऊ" DEVANAGARI LETTER UU
            // U+0918: "घ" DEVANAGARI LETTER GHA
            // U+0905: "अ" DEVANAGARI LETTER A
            // U+091D: "झ" DEVANAGARI LETTER JHA
            // U+0916: "ख" DEVANAGARI LETTER KHA
            // U+0965: "॥" DEVANAGARI DOUBLE DANDA
            // U+0910: "ऐ" DEVANAGARI LETTER AI
            // U+0903: "ः" DEVANAGARI SIGN VISARGA
            .setLabelsOfRow(2,
                    "\u0906", "\u0936", "\u0927", "\u090A", "\u0918", "\u0905", "\u091D", "\u0916",
                    "\u0965", "\u0910", "\u0903")
            // U+090B: "ऋ" DEVANAGARI LETTER VOCALIC R
            // U+0922: "ढ" DEVANAGARI LETTER DDHA
            // U+091B: "छ" DEVANAGARI LETTER CHA
            // U+0901: "ँ" DEVANAGARI SIGN CANDRABINDU
            // U+092D: "भ" DEVANAGARI LETTER BHA
            // U+0923: "ण" DEVANAGARI LETTER NNA
            // U+0902: "ं" DEVANAGARI SIGN ANUSVARA
            // U+0919: "ङ" DEVANAGARI LETTER NGA
            // U+094D: "्" DEVANAGARI SIGN VIRAMA
            .setLabelsOfRow(3,
                    "\u090B", "\u0922", "\u091B", "\u0901", "\u092D", "\u0923", "\u0902", "\u0919",
                    "\u094D")
            .build();
}
