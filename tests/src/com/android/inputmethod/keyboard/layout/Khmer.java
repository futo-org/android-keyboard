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
 * The Khmer alphabet keyboard.
 */
public final class Khmer extends LayoutBase {
    private static final String LAYOUT_NAME = "khmer";

    public Khmer(final LayoutCustomizer customizer) {
        super(customizer, Symbols.class, SymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    public static class KhmerCustomizer extends LayoutCustomizer {
        public KhmerCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey getAlphabetKey() { return KHMER_ALPHABET_KEY; }

        @Override
        public ExpectedKey getCurrencyKey() { return CURRENCY_DOLLAR_WITH_RIEL; }

        @Override
        public ExpectedKey[] getRightShiftKeys(final boolean isPhone) { return EMPTY_KEYS; }

        // U+1780: "ក" KHMER LETTER KA
        // U+1781: "ខ" KHMER LETTER KHA
        // U+1782: "គ" KHMER LETTER KO
        private static final ExpectedKey KHMER_ALPHABET_KEY = key(
                "\u1780\u1781\u1782", Constants.CODE_SWITCH_ALPHA_SYMBOL);

        // U+17DB: "៛" KHMER CURRENCY SYMBOL RIEL
        private static final ExpectedKey CURRENCY_DOLLAR_WITH_RIEL = key(Symbols.DOLLAR_SIGN,
                moreKey("\u17DB"), Symbols.CENT_SIGN, Symbols.POUND_SIGN, Symbols.EURO_SIGN,
                Symbols.YEN_SIGN, Symbols.PESO_SIGN);
    }

    @Override
    ExpectedKey[][] getCommonAlphabetLayout(final boolean isPhone) {
        if (isPhone) {
            return ALPHABET_COMMON;
        }
        final ExpectedKeyboardBuilder builder = new ExpectedKeyboardBuilder(ALPHABET_COMMON);
        builder.addKeysOnTheRightOfRow(4, EXCLAMATION_AND_QUESTION_MARKS);
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

    // Helper method to create alphabet layout by adding special function keys.
    @Override
    ExpectedKeyboardBuilder convertCommonLayoutToKeyboard(final ExpectedKeyboardBuilder builder,
            final boolean isPhone) {
        final LayoutCustomizer customizer = getCustomizer();
        final ExpectedKey[] spacebar = joinKeys(
                customizer.getKeysLeftToSpacebar(isPhone),
                customizer.getSpaceKeys(isPhone),
                customizer.getKeysRightToSpacebar(isPhone));
        builder.setKeysOfRow(5, spacebar);
        if (isPhone) {
            builder.addKeysOnTheRightOfRow(4, DELETE_KEY)
                    .addKeysOnTheLeftOfRow(5, customizer.getSymbolsKey())
                    .addKeysOnTheRightOfRow(5, key(ENTER_KEY, EMOJI_KEY));
        } else {
            builder.addKeysOnTheRightOfRow(1, DELETE_KEY)
                    .addKeysOnTheRightOfRow(3, ENTER_KEY)
                    .addKeysOnTheLeftOfRow(5, customizer.getSymbolsKey(), SETTINGS_KEY)
                    .addKeysOnTheRightOfRow(5, EMOJI_KEY);
        }
        builder.addKeysOnTheLeftOfRow(4, customizer.getLeftShiftKeys(isPhone))
                .addKeysOnTheRightOfRow(4, customizer.getRightShiftKeys(isPhone));
        return builder;
    }

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+17E1: "១" KHMER DIGIT ONE
                    // U+17F1: "៱" KHMER SYMBOL LEK ATTAK MUOY
                    key("\u17E1", joinMoreKeys("1", "\u17F1")),
                    // U+17E2: "២" KHMER DIGIT TWO
                    // U+17F2: "៲" KHMER SYMBOL LEK ATTAK PII
                    key("\u17E2", joinMoreKeys("2", "\u17F2")),
                    // U+17E3: "៣" KHMER DIGIT THREE
                    // U+17F3: "៳" KHMER SYMBOL LEK ATTAK BEI
                    key("\u17E3", joinMoreKeys("3", "\u17F3")),
                    // U+17E4: "៤" KHMER DIGIT FOUR
                    // U+17F4: "៴" KHMER SYMBOL LEK ATTAK BUON
                    key("\u17E4", joinMoreKeys("4", "\u17F4")),
                    // U+17E5: "៥" KHMER DIGIT FIVE
                    // U+17F5: "៵" KHMER SYMBOL LEK ATTAK PRAM
                    key("\u17E5", joinMoreKeys("5", "\u17F5")),
                    // U+17E6: "៦" KHMER DIGIT SIX
                    // U+17F6: "៶" KHMER SYMBOL LEK ATTAK PRAM-MUOY
                    key("\u17E6", joinMoreKeys("6", "\u17F6")),
                    // U+17E7: "៧" KHMER DIGIT SEVEN
                    // U+17F7: "៷" KHMER SYMBOL LEK ATTAK PRAM-PII
                    key("\u17E7", joinMoreKeys("7", "\u17F7")),
                    // U+17E8: "៨" KHMER DIGIT EIGHT
                    // U+17F8: "៸" KHMER SYMBOL LEK ATTAK PRAM-BEI
                    key("\u17E8", joinMoreKeys("8", "\u17F8")),
                    // U+17E9: "៩" KHMER DIGIT NINE
                    // U+17F9: "៹" KHMER SYMBOL LEK ATTAK PRAM-BUON
                    key("\u17E9", joinMoreKeys("9", "\u17F9")),
                    // U+17E0: "០" KHMER DIGIT ZERO
                    // U+17F0: "៰" KHMER SYMBOL LEK ATTAK SON
                    key("\u17E0", joinMoreKeys("0", "\u17F0")),
                    // U+17A5: "ឥ" KHMER INDEPENDENT VOWEL QI
                    // U+17A6: "ឦ" KHMER INDEPENDENT VOWEL QII
                    key("\u17A5", moreKey("\u17A6")),
                    // U+17B2: "ឲ" KHMER INDEPENDENT VOWEL QOO TYPE TWO
                    // U+17B1: "ឱ" KHMER INDEPENDENT VOWEL QOO TYPE ONE
                    key("\u17B2", moreKey("\u17B1")))
            .setKeysOfRow(2,
                    // U+1786: "ឆ" KHMER LETTER CHA
                    key("\u1786"),
                    // U+17B9: "ឹ" KHMER VOWEL SIGN Y
                    key("\u17B9"),
                    // U+17C1: "េ" KHMER VOWEL SIGN E
                    key("\u17C1"),
                    // U+179A: "រ" KHMER LETTER RO
                    key("\u179A"),
                    // U+178F: "ត" KHMER LETTER TA
                    key("\u178F"),
                    // U+1799: "យ" KHMER LETTER YO
                    key("\u1799"),
                    // U+17BB: "ុ" KHMER VOWEL SIGN U
                    key("\u17BB"),
                    // U+17B7: "ិ" KHMER VOWEL SIGN I
                    key("\u17B7"),
                    // U+17C4: "ោ" KHMER VOWEL SIGN OO
                    key("\u17C4"),
                    // U+1795: "ផ" KHMER LETTER PHA
                    key("\u1795"),
                    // U+17C0: "ៀ" KHMER VOWEL SIGN IE
                    key("\u17C0"),
                    // U+17AA: "ឪ" KHMER INDEPENDENT VOWEL QUUV
                    // U+17A7: "ឧ" KHMER INDEPENDENT VOWEL QU
                    // U+17B1: "ឱ" KHMER INDEPENDENT VOWEL QOO TYPE ONE
                    // U+17B3: "ឳ" KHMER INDEPENDENT VOWEL QAU
                    // U+17A9: "ឩ" KHMER INDEPENDENT VOWEL QUU
                    // U+17A8: "ឨ" KHMER INDEPENDENT VOWEL QUK
                    key("\u17AA", joinMoreKeys("\u17A7", "\u17B1", "\u17B3", "\u17A9", "\u17A8")))
            .setKeysOfRow(3,
                    // U+17B6: "ា" KHMER VOWEL SIGN AA
                    key("\u17B6"),
                    // U+179F: "ស" KHMER LETTER SA
                    key("\u179F"),
                    // U+178A: "ដ" KHMER LETTER DA
                    key("\u178A"),
                    // U+1790: "ថ" KHMER LETTER THA
                    key("\u1790"),
                    // U+1784: "ង" KHMER LETTER NGO
                    key("\u1784"),
                    // U+17A0: "ហ" KHMER LETTER HA
                    key("\u17A0"),
                    // U+17D2: "្" KHMER SIGN COENG
                    key("\u17D2"),
                    // U+1780: "ក" KHMER LETTER KA
                    key("\u1780"),
                    // U+179B: "ល" KHMER LETTER LO
                    key("\u179B"),
                    // U+17BE: "ើ" KHMER VOWEL SIGN OE
                    key("\u17BE"),
                    // U+17CB: "់" KHMER SIGN BANTOC
                    key("\u17CB"),
                    // U+17AE: "ឮ" KHMER INDEPENDENT VOWEL LYY
                    // U+17AD: "ឭ" KHMER INDEPENDENT VOWEL LY
                    // U+17B0: "ឰ" KHMER INDEPENDENT VOWEL QAI
                    key("\u17AE", joinMoreKeys("\u17AD", "\u17B0")))
            .setLabelsOfRow(4,
                    // U+178B: "ឋ" KHMER LETTER TTHA
                    // U+1781: "ខ" KHMER LETTER KHA
                    // U+1785: "ច" KHMER LETTER CA
                    // U+179C: "វ" KHMER LETTER VO
                    // U+1794: "ប" KHMER LETTER BA
                    // U+1793: "ន" KHMER LETTER NO
                    // U+1798: "ម" KHMER LETTER MO
                    // U+17BB/U+17C6: "ុំ" KHMER VOWEL SIGN U/KHMER SIGN NIKAHIT
                    // U+17D4: "។" KHMER SIGN KHAN
                    // U+17CA: "៊" KHMER SIGN TRIISAP
                    "\u178B", "\u1781", "\u1785", "\u179C", "\u1794", "\u1793", "\u1798",
                    "\u17BB\u17C6", "\u17D4", "\u17CA")
            .build();

    private static final ExpectedKey[][] ALPHABET_SHIFTED_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    key("!", ZWJ_KEY),
                    // U+17D7: "ៗ" KHMER SIGN LEK TOO
                    key("\u17D7", ZWNJ_KEY),
                    // U+17D1: "៑" KHMER SIGN VIRIAM
                    key("\"", moreKey("\u17D1")),
                    // U+17DB: "៛" KHMER CURRENCY SYMBOL RIEL
                    key("\u17DB", joinMoreKeys(Symbols.DOLLAR_SIGN, Symbols.EURO_SIGN)),
                    // U+17D6: "៖" KHMER SIGN CAMNUC PII KUUH
                    key("%", moreKey("\u17D6")),
                    // U+17CD: "៍" KHMER SIGN TOANDAKHIAT
                    // U+17D9: "៙" KHMER SIGN PHNAEK MUAN
                    key("\u17CD", moreKey("\u17D9")),
                    // U+17D0: "័" KHMER SIGN SAMYOK SANNYA
                    // U+17DA: "៚" KHMER SIGN KOOMUUT
                    key("\u17D0", moreKey("\u17DA")),
                    // U+17CF: "៏" KHMER SIGN AHSDA
                    key("\u17CF", moreKey("*")),
                    // U+00AB: "«" LEFT-POINTING DOUBLE ANGLE QUOTATION MARK
                    key("(", joinMoreKeys("{", "\u00AB")),
                    // U+00BB: "»" RIGHT-POINTING DOUBLE ANGLE QUOTATION MARK
                    key(")", joinMoreKeys("}", "\u00BB")),
                    // U+17CC: "៌" KHMER SIGN ROBAT
                    // U+00D7: "×" MULTIPLICATION SIGN
                    key("\u17CC", moreKey("\u00D7")),
                    // U+17CE: "៎" KHMER SIGN KAKABAT
                    key("\u17CE"))
            .setKeysOfRow(2,
                    // U+1788: "ឈ" KHMER LETTER CHO
                    // U+17DC: "ៜ" KHMER SIGN AVAKRAHASANYA
                    key("\u1788", moreKey("\u17DC")),
                    // U+17BA: "ឺ" KHMER VOWEL SIGN YY
                    // U+17DD: "៝" KHMER SIGN ATTHACAN
                    key("\u17BA", moreKey("\u17DD")),
                    // U+17C2: "ែ" KHMER VOWEL SIGN AE
                    key("\u17C2"),
                    // U+17AC: "ឬ" KHMER INDEPENDENT VOWEL RYY
                    // U+17AB: "ឫ" KHMER INDEPENDENT VOWEL RY
                    key("\u17AC", moreKey("\u17AB")),
                    // U+1791: "ទ" KHMER LETTER TO
                    key("\u1791"),
                    // U+17BD: "ួ" KHMER VOWEL SIGN UA
                    key("\u17BD"),
                    // U+17BC: "ូ" KHMER VOWEL SIGN UU
                    key("\u17BC"),
                    // U+17B8: "ី" KHMER VOWEL SIGN II
                    key("\u17B8"),
                    // U+17C5: "ៅ" KHMER VOWEL SIGN AU
                    key("\u17C5"),
                    // U+1797: "ភ" KHMER LETTER PHO
                    key("\u1797"),
                    // U+17BF: "ឿ" KHMER VOWEL SIGN YA
                    key("\u17BF"),
                    // U+17B0: "ឰ" KHMER INDEPENDENT VOWEL QAI
                    key("\u17B0"))
            .setKeysOfRow(3,
                    // U+17B6/U+17C6: "ាំ" KHMER VOWEL SIGN AA/KHMER SIGN NIKAHIT
                    key("\u17B6\u17C6"),
                    // U+17C3: "ៃ" KHMER VOWEL SIGN AI
                    key("\u17C3"),
                    // U+178C: "ឌ" KHMER LETTER DO
                    key("\u178C"),
                    // U+1792: "ធ" KHMER LETTER THO
                    key("\u1792"),
                    // U+17A2: "អ" KHMER LETTER QAE
                    key("\u17A2"),
                    // U+17C7: "ះ" KHMER SIGN REAHMUK
                    // U+17C8: "ៈ" KHMER SIGN YUUKALEAPINTU
                    key("\u17C7", moreKey("\u17C8")),
                    // U+1789: "ញ" KHMER LETTER NYO
                    key("\u1789"),
                    // U+1782: "គ" KHMER LETTER KO
                    // U+179D: "ឝ" KHMER LETTER SHA
                    key("\u1782", moreKey("\u179D")),
                    // U+17A1: "ឡ" KHMER LETTER LA
                    key("\u17A1"),
                    // U+17C4/U+17C7: "ោះ" KHMER VOWEL SIGN OO/KHMER SIGN REAHMUK
                    key("\u17C4\u17C7"),
                    // U+17C9: "៉" KHMER SIGN MUUSIKATOAN
                    key("\u17C9"),
                    // U+17AF: "ឯ" KHMER INDEPENDENT VOWEL QE
                    key("\u17AF"))
            .setKeysOfRow(4,
                    // U+178D: "ឍ" KHMER LETTER TTHO
                    key("\u178D"),
                    // U+1783: "ឃ" KHMER LETTER KHO
                    key("\u1783"),
                    // U+1787: "ជ" KHMER LETTER CO
                    key("\u1787"),
                    // U+17C1/U+17C7: "េះ" KHMER VOWEL SIGN E/KHMER SIGN REAHMUK
                    key("\u17C1\u17C7"),
                    // U+1796: "ព" KHMER LETTER PO
                    // U+179E: "ឞ" KHMER LETTER SSO
                    key("\u1796", moreKey("\u179E")),
                    // U+178E: "ណ" KHMER LETTER NNO
                    key("\u178E"),
                    // U+17C6: "ំ" KHMER SIGN NIKAHIT
                    key("\u17C6"),
                    // U+17BB/U+17C7: "ុះ" KHMER VOWEL SIGN U/KHMER SIGN REAHMUK
                    key("\u17BB\u17C7"),
                    // U+17D5: "៕" KHMER SIGN BARIYOOSAN
                    key("\u17D5"),
                    key("?"))
            .build();
}
