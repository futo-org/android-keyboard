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
 * The Thai alphabet keyboard.
 */
public final class Thai extends LayoutBase {
    private static final String LAYOUT_NAME = "thai";

    public Thai(final Locale locale) {
        super(new ThaiCustomizer(locale), Symbols.class, SymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    private static class ThaiCustomizer extends LayoutCustomizer {
        ThaiCustomizer(final Locale locale) { super(locale); }

        @Override
        public int getNumberOfRows() { return 5; }

        @Override
        public ExpectedKey getAlphabetKey() { return THAI_ALPHABET_KEY; }

        @Override
        public ExpectedKey getCurrencyKey() { return CURRENCY_BAHT; }

        @Override
        public ExpectedKey[] getOtherCurrencyKeys() {
            return SymbolsShifted.CURRENCIES_OTHER_GENERIC;
        }

        @Override
        public ExpectedKey[] getRightShiftKeys(final boolean isPhone) { return EMPTY_KEYS; }

        // U+0E01: "ก" THAI CHARACTER KO KAI
        // U+0E02: "ข" THAI CHARACTER KHO KHAI
        // U+0E04: "ค" THAI CHARACTER KHO KHWAI
        private static final ExpectedKey THAI_ALPHABET_KEY = key(
                "\u0E01\u0E02\u0E04", Constants.CODE_SWITCH_ALPHA_SYMBOL);

        // U+0E3F: "฿" THAI CURRENCY SYMBOL BAHT
        private static final ExpectedKey CURRENCY_BAHT = key("\u0E3F",
                Symbols.CURRENCY_GENERIC_MORE_KEYS);
    }

    @Override
    ExpectedKey[][] getCommonAlphabetLayout(final boolean isPhone) {
        final ExpectedKeyboardBuilder builder = new ExpectedKeyboardBuilder(ALPHABET_COMMON);
        if (isPhone) {
            // U+0E03: "ฃ" THAI CHARACTER KHO KHUAT
            builder.addKeysOnTheRightOfRow(3, "\u0E03");
        } else {
            // U+0E03: "ฃ" THAI CHARACTER KHO KHUAT
            builder.addKeysOnTheRightOfRow(2, "\u0E03")
                    .addKeysOnTheRightOfRow(4, (Object[])EXCLAMATION_AND_QUESTION_MARKS);
        }
        return builder.build();
    }

    @Override
    public ExpectedKey[][] getCommonAlphabetShiftLayout(final boolean isPhone,
            final int elementId) {
        if (elementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED) {
            return getCommonAlphabetLayout(isPhone);
        }
        final ExpectedKeyboardBuilder builder = new ExpectedKeyboardBuilder(
                ALPHABET_SHIFTED_COMMON);
        if (isPhone) {
            // U+0E05: "ฅ" THAI CHARACTER KHO KHON
            builder.addKeysOnTheRightOfRow(3, "\u0E05");
        } else {
            // U+0E05: "ฅ" THAI CHARACTER KHO KHON
            builder.addKeysOnTheRightOfRow(2, "\u0E05");
        }
        return builder.build();
    }

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+0E45: "ๅ" THAI CHARACTER LAKKHANGYAO
                    "\u0E45",
                    // U+0E51: "๑" THAI DIGIT ONE
                    key("/", joinMoreKeys("1", "\u0E51")),
                    // U+0E52: "๒" THAI DIGIT TWO
                    key("_", joinMoreKeys("2", "\u0E52")),
                    // U+0E20: "ภ" THAI CHARACTER PHO SAMPHAO
                    // U+0E53: "๓" THAI DIGIT THREE
                    key("\u0E20", joinMoreKeys("3", "\u0E53")),
                    // U+0E16: "ถ" THAI CHARACTER THO THUNG
                    // U+0E54: "๔" THAI DIGIT FOUR
                    key("\u0E16", joinMoreKeys("4", "\u0E54")),
                    // U+0E38: " ุ" THAI CHARACTER SARA U
                    key(" \u0E38", "\u0E38"),
                    // U+0E36: " ึ" THAI CHARACTER SARA UE
                    key(" \u0E36", "\u0E36"),
                    // U+0E04: "ค" THAI CHARACTER KHO KHWAI
                    // U+0E55: "๕" THAI DIGIT FIVE
                    key("\u0E04", joinMoreKeys("5", "\u0E55")),
                    // U+0E15: "ต" THAI CHARACTER TO TAO
                    // U+0E56: "๖" THAI DIGIT SIX
                    key("\u0E15", joinMoreKeys("6", "\u0E56")),
                    // U+0E08: "จ" THAI CHARACTER CHO CHAN
                    // U+0E57: "๗" THAI DIGIT SEVEN
                    key("\u0E08", joinMoreKeys("7", "\u0E57")),
                    // U+0E02: "ข" THAI CHARACTER KHO KHAI
                    // U+0E58: "๘" THAI DIGIT EIGHT
                    key("\u0E02", joinMoreKeys("8", "\u0E58")),
                    // U+0E0A: "ช" THAI CHARACTER CHO CHANG
                    // U+0E59: "๙" THAI DIGIT NINE
                    key("\u0E0A", joinMoreKeys("9", "\u0E59")))
            .setKeysOfRow(2,
                    // U+0E46: "ๆ" THAI CHARACTER MAIYAMOK
                    // U+0E50: "๐" THAI DIGIT ZERO
                    key("\u0E46", joinMoreKeys("0", "\u0E50")),
                    // U+0E44: "ไ" THAI CHARACTER SARA AI MAIMALAI
                    // U+0E33: "ำ" THAI CHARACTER SARA AM
                    // U+0E1E: "พ" THAI CHARACTER PHO PHAN
                    // U+0E30: "ะ" THAI CHARACTER SARA A
                    "\u0E44", "\u0E33", "\u0E1E", "\u0E30",
                    // U+0E31: " ั" THAI CHARACTER MAI HAN-AKAT
                    key(" \u0E31", "\u0E31"),
                    // U+0E35: " ี" HAI CHARACTER SARA II
                    key(" \u0E35", "\u0E35"),
                    // U+0E23: "ร" THAI CHARACTER RO RUA
                    // U+0E19: "น" THAI CHARACTER NO NU
                    // U+0E22: "ย" THAI CHARACTER YO YAK
                    // U+0E1A: "บ" THAI CHARACTER BO BAIMAI
                    // U+0E25: "ล" THAI CHARACTER LO LING
                    "\u0E23", "\u0E19", "\u0E22", "\u0E1A", "\u0E25")
            .setKeysOfRow(3,
                    // U+0E1F: "ฟ" THAI CHARACTER FO FAN
                    // U+0E2B: "ห" THAI CHARACTER HO HIP
                    // U+0E01: "ก" THAI CHARACTER KO KAI
                    // U+0E14: "ด" THAI CHARACTER DO DEK
                    // U+0E40: "เ" THAI CHARACTER SARA E
                    "\u0E1F", "\u0E2B", "\u0E01", "\u0E14", "\u0E40",
                    // U+0E49: " ้" THAI CHARACTER MAI THO
                    key(" \u0E49", "\u0E49"),
                    // U+0E48: " ่" THAI CHARACTER MAI EK
                    key(" \u0E48", "\u0E48"),
                    // U+0E32: "า" THAI CHARACTER SARA AA
                    // U+0E2A: "ส" THAI CHARACTER SO SUA
                    // U+0E27: "ว" THAI CHARACTER WO WAEN
                    // U+0E07: "ง" THAI CHARACTER NGO NGU
                    "\u0E32", "\u0E2A", "\u0E27", "\u0E07")
            .setKeysOfRow(4,
                    // U+0E1C: "ผ" THAI CHARACTER PHO PHUNG
                    // U+0E1B: "ป" THAI CHARACTER PO PLA
                    // U+0E41: "แ" THAI CHARACTER SARA AE
                    // U+0E2D: "อ" THAI CHARACTER O ANG
                    "\u0E1C", "\u0E1B", "\u0E41", "\u0E2D",
                    // U+0E34: " ิ" THAI CHARACTER SARA I
                    key(" \u0E34", "\u0E34"),
                    // U+0E37: " ื" THAI CHARACTER SARA UEE
                    key(" \u0E37", "\u0E37"),
                    // U+0E17: "ท" THAI CHARACTER THO THAHAN
                    // U+0E21: "ม" THAI CHARACTER MO MA
                    // U+0E43: "ใ" THAI CHARACTER SARA AI MAIMUAN
                    // U+0E1D: "ฝ" THAI CHARACTER FO FA
                    "\u0E17", "\u0E21", "\u0E43", "\u0E1D")
            .build();

    private static final ExpectedKey[][] ALPHABET_SHIFTED_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+0E51: "๑" THAI DIGIT ONE
                    // U+0E52: "๒" THAI DIGIT TWO
                    // U+0E53: "๓" THAI DIGIT THREE
                    // U+0E54: "๔" THAI DIGIT FOUR
                    // U+0E39: " ู" THAI CHARACTER SARA UU
                    "+", "\u0E51", "\u0E52", "\u0E53", "\u0E54",
                    key(" \u0E39", "\u0E39"),
                    // U+0E3F: "฿" THAI CURRENCY SYMBOL BAHT
                    // U+0E55: "๕" THAI DIGIT FIVE
                    // U+0E56: "๖" THAI DIGIT SIX
                    // U+0E57: "๗" THAI DIGIT SEVEN
                    // U+0E58: "๘" THAI DIGIT EIGHT
                    // U+0E59: "๙" THAI DIGIT NINE
                    "\u0E3F", "\u0E55", "\u0E56", "\u0E57", "\u0E58", "\u0E59")
            .setKeysOfRow(2,
                    // U+0E50: "๐" THAI DIGIT ZERO
                    // U+0E0E: "ฎ" THAI CHARACTER DO CHADA
                    // U+0E11: "ฑ" THAI CHARACTER THO NANGMONTHO
                    // U+0E18: "ธ" THAI CHARACTER THO THONG
                    "\u0E50", "\"", "\u0E0E", "\u0E11", "\u0E18",
                    // U+0E4D: " ํ" THAI CHARACTER THANTHAKHAT
                    key(" \u0E4D", "\u0E4D"),
                    // U+0E4A: " ๊" THAI CHARACTER MAI TRI
                    key(" \u0E4A", "\u0E4A"),
                    // U+0E13: "ณ" THAI CHARACTER NO NEN
                    // U+0E2F: "ฯ" THAI CHARACTER PAIYANNOI
                    // U+0E0D: "ญ" THAI CHARACTER YO YING
                    // U+0E10: "ฐ" THAI CHARACTER THO THAN
                    "\u0E13", "\u0E2F", "\u0E0D", "\u0E10", ",")
            .setKeysOfRow(3,
                    // U+0E24: "ฤ" THAI CHARACTER RU
                    // U+0E06: "ฆ" THAI CHARACTER KHO RAKHANG
                    // U+0E0F: "ฏ" THAI CHARACTER TO PATAK
                    // U+0E42: "โ" THAI CHARACTER SARA O
                    // U+0E0C: "ฌ" THAI CHARACTER CHO CHOE
                    "\u0E24", "\u0E06", "\u0E0F", "\u0E42", "\u0E0C",
                    // U+0E47: " ็" THAI CHARACTER MAITAIKHU
                    key(" \u0E47", "\u0E47"),
                    // U+0E4B: " ๋" THAI CHARACTER MAI CHATTAWA
                    key(" \u0E4B", "\u0E4B"),
                    // U+0E29: "ษ" THAI CHARACTER SO RUSI
                    // U+0E28: "ศ" THAI CHARACTER SO SALA
                    // U+0E0B: "ซ" THAI CHARACTER SO SO
                    "\u0E29", "\u0E28", "\u0E0B", ".")
            .setKeysOfRow(4,
                    // U+0E09: "ฉ" THAI CHARACTER CHO CHING
                    // U+0E2E: "ฮ" THAI CHARACTER HO NOKHUK
                    "(", ")", "\u0E09", "\u0E2E",
                    // U+0E3A: " ฺ" THAI CHARACTER PHINTHU
                    key(" \u0E3A", "\u0E3A"),
                    // U+0E4C: " ์" THAI CHARACTER THANTHAKHAT
                    key(" \u0E4C", "\u0E4C"),
                    // U+0E12: "ฒ" THAI CHARACTER THO PHUTHAO
                    // U+0E2C: "ฬ" THAI CHARACTER LO CHULA
                    // U+0E26: "ฦ" THAI CHARACTER LU
                    "?", "\u0E12", "\u0E2C", "\u0E26")
            .build();
}
