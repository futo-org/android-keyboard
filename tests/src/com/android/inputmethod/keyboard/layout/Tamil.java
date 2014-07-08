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

import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;
import com.android.inputmethod.latin.Constants;

import java.util.Locale;

/**
 * The Tamil keyboard.
 */
public final class Tamil extends LayoutBase {
    private static final String LAYOUT_NAME = "tamil";

    public Tamil(final LayoutCustomizer customizer) {
        super(customizer, Symbols.class, SymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    public static class TamilCustomizer extends LayoutCustomizer {
        public TamilCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey getAlphabetKey() { return TAMIL_ALPHABET_KEY; }

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

        // U+0BA4: "த" TAMIL LETTER TA
        // U+0BAE/U+0BBF: "மி" TAMIL LETTER MA/TAMIL VOWEL SIGN I
        // U+0BB4/U+0BCD: "ழ்" TAMIL LETTER LLLA/TAMIL SIGN VIRAMA
        private static final ExpectedKey TAMIL_ALPHABET_KEY = key(
                "\u0BA4\u0BAE\u0BBF\u0BB4\u0BCD", Constants.CODE_SWITCH_ALPHA_SYMBOL);

        // U+0BF9: "௹" TAMIL RUPEE SIGN
        private static final ExpectedKey CURRENCY_RUPEE = key("\u0BF9",
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
                    // U+0B94: "ஔ" TAMIL LETTER AU
                    key("\u0B94", moreKey("1")),
                    // U+0B90: "ஐ" TAMIL LETTER AI
                    key("\u0B90", moreKey("2")),
                    // U+0B86: "ஆ" TAMIL LETTER AA
                    key("\u0B86", moreKey("3")),
                    // U+0B88: "ஈ" TAMIL LETTER II
                    key("\u0B88", moreKey("4")),
                    // U+0B8A: "ஊ" TAMIL LETTER UU
                    key("\u0B8A", moreKey("5")),
                    // U+0BAE: "ம" TAMIL LETTER MA
                    key("\u0BAE", moreKey("6")),
                    // U+0BA9: "ன" TAMIL LETTER NNNA
                    key("\u0BA9", moreKey("7")),
                    // U+0BA8: "ந" TAMIL LETTER NA
                    key("\u0BA8", moreKey("8")),
                    // U+0B99: "ங" TAMIL LETTER NGA
                    key("\u0B99", moreKey("9")),
                    // U+0BA3: "ண" TAMIL LETTER NNA
                    key("\u0BA3", moreKey("0")),
                    // U+0B9E: "ஞ" TAMIL LETTER NYA
                    "\u0B9E")
            .setKeysOfRow(2,
                    // U+0B93: "ஓ" TAMIL LETTER OO
                    // U+0BD0: "ௐ" TAMIL OM
                    key("\u0B93", moreKey("\u0BD0")),
                    // U+0B8F: "ஏ" TAMIL LETTER EE
                    "\u0B8F",
                    // U+0B85: "அ" TAMIL LETTER A
                    // U+0B83: "ஃ" TAMIL SIGN VISARGA
                    key("\u0B85", moreKey("\u0B83")),
                    // U+0B87: "இ" TAMIL LETTER I
                    // U+0B89: "உ" TAMIL LETTER U
                    // U+0BB1: "ற" TAMIL LETTER RRA
                    // U+0BAA: "ப" TAMIL LETTER PA
                    "\u0B87", "\u0B89", "\u0BB1", "\u0BAA",
                    // U+0B95: "க" TAMIL LETTER KA
                    // U+0BB9: "ஹ" TAMIL LETTER HA
                    // U+0B95/U+0BCD/U+0BB7:
                    //     "க்ஷ" TAMIL LETTER KA/TAMIL SIGN VIRAMA/TAMIL LETTER SSA
                    key("\u0B95", joinMoreKeys("\u0BB9", "\u0B95\u0BCD\u0BB7")),
                    // U+0BA4: "த" TAMIL LETTER TA
                    "\u0BA4",
                    // U+0B9A: "ச" TAMIL LETTER CA
                    // U+0BB8: "ஸ" TAMIL LETTER SA
                    // U+0BB6/U+0BCD/U+0BB0/U+0BC0:
                    //     "ஶ்ரீ" TAMIL LETTER SHA/TAMIL SIGN VIRAMA/TAMIL LETTER RA
                    //          /TAMIL VOWEL SIGN II
                    key("\u0B9A", joinMoreKeys("\u0BB8", "\u0BB6\u0BCD\u0BB0\u0BC0")),
                    // U+0B9F: "ட" TAMIL LETTER TTA
                    "\u0B9F")
            .setKeysOfRow(3,
                    // U+0B92: "ஒ" TAMIL LETTER O
                    // U+0B8E: "எ" TAMIL LETTER E
                    // U+0BCD: "்" TAMIL SIGN VIRAMA
                    // U+0BB0: "ர" TAMIL LETTER RA
                    // U+0BB5: "வ" TAMIL LETTER VA
                    // U+0BB4: "ழ TAMIL LETTER LLLA
                    // U+0BB2: "ல" TAMIL LETTER LA
                    // U+0BB3: "ள" TAMIL LETTER LLA
                    // U+0BAF: "ய" TAMIL LETTER YA
                    "\u0B92", "\u0B8E", "\u0BCD", "\u0BB0", "\u0BB5", "\u0BB4", "\u0BB2", "\u0BB3",
                    "\u0BAF",
                    // U+0BB7: "ஷ" TAMIL LETTER SSA
                    // U+0B9C: "ஜ" TAMIL LETTER JA
                    key("\u0BB7", moreKey("\u0B9C")))
            .build();
}
