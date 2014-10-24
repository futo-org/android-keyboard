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

import com.android.inputmethod.keyboard.layout.customizer.LayoutCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;
import com.android.inputmethod.latin.common.Constants;

import java.util.Locale;

/**
 * The Malayalam keyboard.
 */
public final class Malayalam extends LayoutBase {
    private static final String LAYOUT_NAME = "malayalam";

    public Malayalam(final Locale locale) {
        super(new MalayalamCustomizer(locale), Symbols.class, SymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    private static class MalayalamCustomizer extends LayoutCustomizer {
        MalayalamCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey getAlphabetKey() { return MALAYALAM_ALPHABET_KEY; }

        @Override
        public ExpectedKey getCurrencyKey() { return CURRENCY_RUPEE; }

        @Override
        public ExpectedKey[] getOtherCurrencyKeys() {
            return SymbolsShifted.CURRENCIES_OTHER_GENERIC;
        }

        @Override
        public ExpectedKey[] getLeftShiftKeys(final boolean isPhone) {
            return EMPTY_KEYS;
        }

        @Override
        public ExpectedKey[] getRightShiftKeys(final boolean isPhone) {
            return isPhone ? EMPTY_KEYS : EXCLAMATION_AND_QUESTION_MARKS;
        }

        // U+0D05: "അ" MALAYALAM LETTER A
        private static final ExpectedKey MALAYALAM_ALPHABET_KEY = key(
                "\u0D05", Constants.CODE_SWITCH_ALPHA_SYMBOL);

        // U+20B9: "₹" INDIAN RUPEE SIGN
        private static final ExpectedKey CURRENCY_RUPEE = key("\u20B9",
                Symbols.CURRENCY_GENERIC_MORE_KEYS);
    }

    @Override
    ExpectedKey[][] getCommonAlphabetLayout(boolean isPhone) { return ALPHABET_COMMON; }

    @Override
    ExpectedKey[][] getCommonAlphabetShiftLayout(boolean isPhone, final int elementId) {
        return null;
    }

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+0D4D: "്" MALAYALAM SIGN VIRAMA
                    // U+0D05: "അ" MALAYALAM LETTER A
                    key("\u0D4D", joinMoreKeys("\u0D05", "1")),
                    // U+0D3E: "ാ" MALAYALAM VOWEL SIGN AA
                    // U+0D06: "ആ" MALAYALAM LETTER AA
                    key("\u0D3E", joinMoreKeys("\u0D06", "2")),
                    // U+0D3F: "ി" MALAYALAM VOWEL SIGN I
                    // U+0D07: "ഇ" MALAYALAM LETTER I
                    key("\u0D3F", joinMoreKeys("\u0D07", "3")),
                    // U+0D40: "ീ" MALAYALAM VOWEL SIGN II
                    // U+0D08: "ഈ" MALAYALAM LETTER II
                    key("\u0D40", joinMoreKeys("\u0D08", "4")),
                    // U+0D41: "ു" MALAYALAM VOWEL SIGN U
                    // U+0D09: "ഉ" MALAYALAM LETTER U
                    key("\u0D41", joinMoreKeys("\u0D09", "5")),
                    // U+0D42: "ൂ" MALAYALAM VOWEL SIGN UU
                    // U+0D0A: "ഊ" MALAYALAM LETTER UU
                    key("\u0D42", joinMoreKeys("\u0D0A", "6")),
                    // U+0D43: "ൃ" MALAYALAM VOWEL SIGN VOCALIC R
                    // U+0D0B: "ഋ" MALAYALAM LETTER VOCALIC R
                    key("\u0D43", joinMoreKeys("\u0D0B", "7")),
                    // U+0D46: "െ" MALAYALAM VOWEL SIGN E
                    // U+0D0E: "എ" MALAYALAM LETTER E
                    // U+0D10: "ഐ" MALAYALAM LETTER AI
                    // U+0D48: "ൈ" MALAYALAM VOWEL SIGN AI
                    key("\u0D46", joinMoreKeys("\u0D0E", "\u0D10", "\u0D48", "8")),
                    // U+0D47: "േ" MALAYALAM VOWEL SIGN EE
                    // U+0D0F: "ഏ" MALAYALAM LETTER EE
                    key("\u0D47", joinMoreKeys("\u0D0F", "9")),
                    // U+0D4A: "ൊ" MALAYALAM VOWEL SIGN O
                    // U+0D12: "ഒ" MALAYALAM LETTER O
                    key("\u0D4A", joinMoreKeys("\u0D12", "0")),
                    // U+0D4B: "ോ" MALAYALAM VOWEL SIGN OO
                    // U+0D13: "ഓ" MALAYALAM LETTER OO
                    // U+0D14: "ഔ" MALAYALAM LETTER AU
                    // U+0D57: "ൗ" MALAYALAM AU LENGTH MARK
                    key("\u0D4B", joinMoreKeys("\u0D13", "\u0D14", "\u0D57")))
            .setKeysOfRow(2,
                    // U+0D15: "ക" MALAYALAM LETTER KA
                    // U+0D16: "ഖ" MALAYALAM LETTER KHA
                    key("\u0D15", moreKey("\u0D16")),
                    // U+0D17: "ഗ" MALAYALAM LETTER GA
                    // U+0D18: "ഘ" MALAYALAM LETTER GHA
                    key("\u0D17", moreKey("\u0D18")),
                    // U+0D19: "ങ" MALAYALAM LETTER NGA
                    // U+0D1E: "ഞ" MALAYALAM LETTER NYA
                    key("\u0D19", moreKey("\u0D1E")),
                    // U+0D1A: "ച" MALAYALAM LETTER CA
                    // U+0D1B: "ഛ" MALAYALAM LETTER CHA
                    key("\u0D1A", moreKey("\u0D1B")),
                    // U+0D1C: "ജ" MALAYALAM LETTER JA
                    // U+0D1D: "ഝ" MALAYALAM LETTER JHA
                    key("\u0D1C", moreKey("\u0D1D")),
                    // U+0D1F: "ട" MALAYALAM LETTER TTA
                    // U+0D20: "ഠ" MALAYALAM LETTER TTHA
                    key("\u0D1F", moreKey("\u0D20")),
                    // U+0D21: "ഡ" MALAYALAM LETTER DDA
                    // U+0D22: "ഢ" MALAYALAM LETTER DDHA
                    key("\u0D21", moreKey("\u0D22")),
                    // U+0D23: "ണ" MALAYALAM LETTER NNA
                    // U+0D7A: "ൺ" MALAYALAM LETTER CHILLU NN
                    key("\u0D23", moreKey("\u0D7A")),
                    // U+0D24: "ത" MALAYALAM LETTER TA
                    // U+0D25: "ഥ" MALAYALAM LETTER THA
                    key("\u0D24", moreKey("\u0D25")),
                    // U+0D26: "ദ" MALAYALAM LETTER DA
                    // U+0D27: "ധ" MALAYALAM LETTER DHA
                    key("\u0D26", moreKey("\u0D27")),
                    // U+0D28: "ഗന" MALAYALAM LETTER NA
                    // U+0D7B: "ൻ" MALAYALAM LETTER CHILLU N
                    key("\u0D28", moreKey("\u0D7B")))
            .setKeysOfRow(3,
                    // U+0D2A: "പ" MALAYALAM LETTER PA
                    // U+0D2B: "ഫ" MALAYALAM LETTER PHA
                    key("\u0D2A", moreKey("\u0D2B")),
                    // U+0D2C: "ബ" MALAYALAM LETTER BA
                    // U+0D2D: "ഭ" MALAYALAM LETTER BHA
                    key("\u0D2C", moreKey("\u0D2D")),
                    // U+0D2E: "മ" MALAYALAM LETTER MA
                    // U+0D02: "ം" MALAYALAM SIGN ANUSVARA
                    key("\u0D2E", moreKey("\u0D02")),
                    // U+0D2F: "യ" MALAYALAM LETTER YA
                    // U+0D4D/U+0D2F: "്യ" MALAYALAM SIGN VIRAMA/MALAYALAM LETTER YA
                    key("\u0D2F", moreKey("\u0D4D\u0D2F")),
                    // U+0D30: "ര" MALAYALAM LETTER RA
                    // U+0D4D/U+0D30: "്ര" MALAYALAM SIGN VIRAMA/MALAYALAM LETTER RA
                    // U+0D7C: "ർ" MALAYALAM LETTER CHILLU RR
                    // U+0D31: "റ" MALAYALAM LETTER RRA
                    key("\u0D30", joinMoreKeys("\u0D4D\u0D30", "\u0D7C", "\u0D31")),
                    // U+0D32: "ല" MALAYALAM LETTER LA
                    // U+0D7D: "ൽ" MALAYALAM LETTER CHILLU L
                    key("\u0D32", moreKey("\u0D7D")),
                    // U+0D35: "വ" MALAYALAM LETTER VA
                    // U+0D4D/U+0D35: "്വ" MALAYALAM SIGN VIRAMA/MALAYALAM LETTER VA
                    key("\u0D35", moreKey("\u0D4D\u0D35")),
                    // U+0D36: "ശ" MALAYALAM LETTER SHA
                    // U+0D37: "ഷ" MALAYALAM LETTER SSA
                    // U+0D38: "സ" MALAYALAM LETTER SA
                    key("\u0D36", joinMoreKeys("\u0D37", "\u0D38")),
                    // U+0D39: "ഹ" MALAYALAM LETTER HA
                    // U+0D03: "ഃ" MALAYALAM SIGN VISARGA
                    key("\u0D39", moreKey("\u0D03")),
                    // U+0D33: "ള" MALAYALAM LETTER LLA
                    // U+0D7E: "ൾ" MALAYALAM LETTER CHILLU LL
                    // U+0D34: "ഴ" MALAYALAM LETTER LLLA
                    key("\u0D33", joinMoreKeys("\u0D7E", "\u0D34")))
            .build();
}
