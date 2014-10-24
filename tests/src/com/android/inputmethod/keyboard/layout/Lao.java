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
import com.android.inputmethod.keyboard.layout.customizer.LayoutCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;
import com.android.inputmethod.latin.common.Constants;

import java.util.Locale;

/**
 * The Khmer alphabet keyboard.
 */
public final class Lao extends LayoutBase {
    private static final String LAYOUT_NAME = "lao";

    public Lao(final Locale locale) {
        super(new LaoCustomizer(locale), Symbols.class, SymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    private static class LaoCustomizer extends LayoutCustomizer {
        LaoCustomizer(final Locale locale) { super(locale); }

        @Override
        public int getNumberOfRows() { return 5; }

        @Override
        public ExpectedKey getAlphabetKey() { return LAO_ALPHABET_KEY; }

        @Override
        public ExpectedKey getCurrencyKey() { return CURRENCY_KIP; }

        @Override
        public ExpectedKey[] getOtherCurrencyKeys() {
            return SymbolsShifted.CURRENCIES_OTHER_GENERIC;
        }

        @Override
        public ExpectedKey[] getRightShiftKeys(final boolean isPhone) { return EMPTY_KEYS; }

        // U+0E81: "ກ" LAO LETTER KO
        // U+0E82: "ຂ" LAO LETTER KHO SUNG
        // U+0E84: "ຄ" LAO LETTER KHO TAM
        private static final ExpectedKey LAO_ALPHABET_KEY = key(
                "\u0E81\u0E82\u0E84", Constants.CODE_SWITCH_ALPHA_SYMBOL);

        // U+20AD: "₭" KIP SIGN
        private static final ExpectedKey CURRENCY_KIP = key("\u20AD",
                Symbols.CURRENCY_GENERIC_MORE_KEYS);
    }

    @Override
    ExpectedKey[][] getCommonAlphabetLayout(final boolean isPhone) {
        if (isPhone) {
            return ALPHABET_COMMON;
        }
        final ExpectedKeyboardBuilder builder = new ExpectedKeyboardBuilder(ALPHABET_COMMON);
        builder.addKeysOnTheRightOfRow(4, (Object[])EXCLAMATION_AND_QUESTION_MARKS);
        return builder.build();
    }

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
                    // U+0EA2: "ຢ" LAO LETTER YO
                    // U+0ED1: "໑" LAO DIGIT ONE
                    key("\u0EA2", joinMoreKeys("1", "\u0ED1")),
                    // U+0E9F: "ຟ" LAO LETTER FO SUNG
                    // U+0ED2: "໒" LAO DIGIT TWO
                    key("\u0E9F", joinMoreKeys("2", "\u0ED2")),
                    // U+0EC2: "ໂ" LAO VOWEL SIGN O
                    // U+0ED3: "໓" LAO DIGIT THREE
                    key("\u0EC2", joinMoreKeys("3", "\u0ED3")),
                    // U+0E96: "ຖ" LAO LETTER THO SUNG
                    // U+0ED4: "໔" LAO DIGIT FOUR
                    key("\u0E96", joinMoreKeys("4", "\u0ED4")),
                    // U+0EB8: "ຸ" LAO VOWEL SIGN U
                    // U+0EB9: "ູ" LAO VOWEL SIGN UU
                    "\u0EB8", "\u0EB9",
                    // U+0E84: "ຄ" LAO LETTER KHO TAM
                    // U+0ED5: "໕" LAO DIGIT FIVE
                    key("\u0E84", joinMoreKeys("5", "\u0ED5")),
                    // U+0E95: "ຕ" LAO LETTER TO
                    // U+0ED6: "໖" LAO DIGIT SIX
                    key("\u0E95", joinMoreKeys("6", "\u0ED6")),
                    // U+0E88: "ຈ" LAO LETTER CO
                    // U+0ED7: "໗" LAO DIGIT SEVEN
                    key("\u0E88", joinMoreKeys("7", "\u0ED7")),
                    // U+0E82: "ຂ" LAO LETTER KHO SUNG
                    // U+0ED8: "໘" LAO DIGIT EIGHT
                    key("\u0E82", joinMoreKeys("8", "\u0ED8")),
                    // U+0E8A: "ຊ" LAO LETTER SO TAM
                    // U+0ED9: "໙" LAO DIGIT NINE
                    key("\u0E8A", joinMoreKeys("9", "\u0ED9")),
                    // U+0ECD: "ໍ" LAO NIGGAHITA
                    "\u0ECD")
            .setKeysOfRow(2,
                    // U+0EBB: "ົ" LAO VOWEL SIGN MAI KON
                    "\u0EBB",
                    // U+0EC4: "ໄ" LAO VOWEL SIGN AI
                    // U+0ED0: "໐" LAO DIGIT ZERO
                    key("\u0EC4", joinMoreKeys("0", "\u0ED0")),
                    // U+0EB3: "ຳ" LAO VOWEL SIGN AM
                    // U+0E9E: "ພ" LAO LETTER PHO TAM
                    // U+0EB0: "ະ" LAO VOWEL SIGN A
                    // U+0EB4: "ິ" LAO VOWEL SIGN I
                    // U+0EB5: "ີ" LAO VOWEL SIGN II
                    // U+0EAE: "ຮ" LAO LETTER HO TAM
                    // U+0E99: "ນ" LAO LETTER NO
                    // U+0E8D: "ຍ" LAO LETTER NYO
                    // U+0E9A: "ບ" LAO LETTER BO
                    // U+0EA5: "ລ" LAO LETTER LO LOOT
                    "\u0EB3", "\u0E9E", "\u0EB0", "\u0EB4", "\u0EB5", "\u0EAE", "\u0E99", "\u0E8D",
                    "\u0E9A", "\u0EA5")
            .setKeysOfRow(3,
                    // U+0EB1: "ັ" LAO VOWEL SIGN MAI KAN
                    // U+0EAB: "ຫ" LAO LETTER HO SUNG
                    // U+0E81: "ກ" LAO LETTER KO
                    // U+0E94: "ດ" LAO LETTER DO
                    // U+0EC0: "ເ" LAO VOWEL SIGN E
                    // U+0EC9: "້" LAO TONE MAI THO
                    // U+0EC8: "່" LAO TONE MAI EK
                    // U+0EB2: "າ" LAO VOWEL SIGN AA
                    // U+0EAA: "ສ" LAO LETTER SO SUNG
                    // U+0EA7: "ວ" LAO LETTER WO
                    // U+0E87: "ງ" LAO LETTER NGO
                    // U+201C: "“" LEFT DOUBLE QUOTATION MARK
                    "\u0EB1", "\u0EAB", "\u0E81", "\u0E94", "\u0EC0", "\u0EC9", "\u0EC8", "\u0EB2",
                    "\u0EAA", "\u0EA7", "\u0E87", "\u201C")
            .setKeysOfRow(4,
                    // U+0E9C: "ຜ" LAO LETTER PHO SUNG
                    // U+0E9B: "ປ" LAO LETTER PO
                    // U+0EC1: "ແ" LAO VOWEL SIGN EI
                    // U+0EAD: "ອ" LAO LETTER O
                    // U+0EB6: "ຶ" LAO VOWEL SIGN Y
                    // U+0EB7: "ື" LAO VOWEL SIGN YY
                    // U+0E97: "ທ" LAO LETTER THO TAM
                    // U+0EA1: "ມ" LAO LETTER MO
                    // U+0EC3: "ໃ" LAO VOWEL SIGN AY
                    // U+0E9D: "ຝ" LAO LETTER FO TAM
                    "\u0E9C", "\u0E9B", "\u0EC1", "\u0EAD", "\u0EB6", "\u0EB7", "\u0E97", "\u0EA1",
                    "\u0EC3", "\u0E9D")
            .build();

    private static final ExpectedKey[][] ALPHABET_SHIFTED_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+0ED1: "໑" LAO DIGIT ONE
                    // U+0ED2: "໒" LAO DIGIT TWO
                    // U+0ED3: "໓" LAO DIGIT THREE
                    // U+0ED4: "໔" LAO DIGIT FOUR
                    // U+0ECC: "໌" LAO CANCELLATION MARK
                    // U+0EBC: "ຼ" LAO SEMIVOWEL SIGN LO
                    // U+0ED5: "໕" LAO DIGIT FIVE
                    // U+0ED6: "໖" LAO DIGIT SIX
                    // U+0ED7: "໗" LAO DIGIT SEVEN
                    // U+0ED8: "໘" LAO DIGIT EIGHT
                    // U+0ED9: "໙" LAO DIGIT NINE
                    // U+0ECD/U+0EC8: "ໍ່" LAO NIGGAHITA/LAO TONE MAI EK
                    "\u0ED1", "\u0ED2", "\u0ED3", "\u0ED4", "\u0ECC", "\u0EBC", "\u0ED5", "\u0ED6",
                    "\u0ED7", "\u0ED8", "\u0ED9", "\u0ECD\u0EC8")
            .setKeysOfRow(2,
                    // U+0EBB/U+0EC9: "" LAO VOWEL SIGN MAI KON/LAO TONE MAI THO
                    // U+0ED0: "໐" LAO DIGIT ZERO
                    // U+0EB3/U+0EC9: "ຳ້" LAO VOWEL SIGN AM/LAO TONE MAI THO
                    // U+0EB4/U+0EC9: "ິ້" LAO VOWEL SIGN I/LAO TONE MAI THO
                    // U+0EB5/U+0EC9: "ີ້" LAO VOWEL SIGN II/LAO TONE MAI THO
                    // U+0EA3: "ຣ" LAO LETTER LO LING
                    // U+0EDC: "ໜ" LAO HO NO
                    // U+0EBD: "ຽ" LAO SEMIVOWEL SIGN NYO
                    // U+0EAB/U+0EBC: "" LAO LETTER HO SUNG/LAO SEMIVOWEL SIGN LO
                    // U+201D: "”" RIGHT DOUBLE QUOTATION MARK
                    "\u0EBB\u0EC9", "\u0ED0", "\u0EB3\u0EC9", "_", "+", "\u0EB4\u0EC9",
                    "\u0EB5\u0EC9", "\u0EA3", "\u0EDC", "\u0EBD", "\u0EAB\u0EBC", "\u201D")
            .setKeysOfRow(3,
                    // U+0EB1/U+0EC9: "ັ້" LAO VOWEL SIGN MAI KAN/LAO TONE MAI THO
                    // U+0ECA: "໊" LAO TONE MAI TI
                    // U+0ECB: "໋" LAO TONE MAI CATAWA
                    // U+201C: "“" LEFT DOUBLE QUOTATION MARK
                    "\u0EB1\u0EC9", ";", ".", ",", ":", "\u0ECA", "\u0ECB", "!", "?", "%", "=",
                    "\u201C")
            .setKeysOfRow(4,
                    // U+20AD: "₭" KIP SIGN
                    // U+0EAF: "ຯ" LAO ELLIPSIS
                    // U+0EB6/U+0EC9: "ຶ້" LAO VOWEL SIGN Y/LAO TONE MAI THO
                    // U+0EB7/U+0EC9: "ື້" LAO VOWEL SIGN YY/LAO TONE MAI THO
                    // U+0EC6: "ໆ" LAO KO LA
                    // U+0EDD: "ໝ" LAO HO MO
                    "\u20AD", "(", "\u0EAF", "@", "\u0EB6\u0EC9", "\u0EB7\u0EC9", "\u0EC6",
                    "\u0EDD", "$", ")")
            .build();
}
