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

import com.android.inputmethod.keyboard.layout.customizer.EastSlavicCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

public final class Mongolian extends LayoutBase {
    private static final String LAYOUT_NAME = "mongolian";

    public Mongolian(final Locale locale) {
        super(new MongolianCustomizer(locale), Symbols.class, SymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    private static class MongolianCustomizer extends EastSlavicCustomizer {
        MongolianCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey getCurrencyKey() { return CURRENCY_TUGRIK; }

        @Override
        public ExpectedKey[] getOtherCurrencyKeys() {
            return SymbolsShifted.CURRENCIES_OTHER_GENERIC;
        }

        // U+20AE: "₮" TUGRIK SIGN
        private static final ExpectedKey CURRENCY_TUGRIK = key("\u20AE",
                Symbols.CURRENCY_GENERIC_MORE_KEYS);
    }

    @Override
    ExpectedKey[][] getCommonAlphabetLayout(final boolean isPhone) { return ALPHABET_COMMON; }

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+0444: "ф" CYRILLIC SMALL LETTER EF
                    key("\u0444", moreKey("1")),
                    // U+0446: "ц" CYRILLIC SMALL LETTER TSE
                    key("\u0446", moreKey("2")),
                    // U+0443: "у" CYRILLIC SMALL LETTER U
                    key("\u0443", moreKey("3")),
                    // U+0436: "ж" CYRILLIC SMALL LETTER ZHE
                    key("\u0436", moreKey("4")),
                    // U+044D: "э" CYRILLIC SMALL LETTER E
                    key("\u044D", moreKey("5")),
                    // U+043D: "н" CYRILLIC SMALL LETTER EN
                    key("\u043D", moreKey("6")),
                    // U+0433: "г" CYRILLIC SMALL LETTER GHE
                    key("\u0433", moreKey("7")),
                    // U+0448: "ш" CYRILLIC SMALL LETTER SHA
                    // U+0449: "щ" CYRILLIC SMALL LETTER SHCHA
                    key("\u0448", joinMoreKeys("8", "\u0449")),
                    // U+04AF: "ү" CYRILLIC SMALL LETTER STRAIGHT U
                    key("\u04AF", moreKey("9")),
                    // U+0437: "з" CYRILLIC SMALL LETTER ZE
                    key("\u0437", moreKey("0")),
                    // U+043A: "к" CYRILLIC SMALL LETTER KA
                    "\u043A")
            .setKeysOfRow(2,
                    // U+0439: "й" CYRILLIC SMALL LETTER SHORT I
                    // U+044B: "ы" CYRILLIC SMALL LETTER YERU
                    // U+0431: "б" CYRILLIC SMALL LETTER BE
                    // U+04E9: "ө" CYRILLIC SMALL LETTER BARRED O
                    // U+0430: "а" CYRILLIC SMALL LETTER A
                    // U+0445: "х" CYRILLIC SMALL LETTER HA
                    // U+0440: "р" CYRILLIC SMALL LETTER ER
                    // U+043E: "о" CYRILLIC SMALL LETTER O
                    // U+043B: "л" CYRILLIC SMALL LETTER EL
                    // U+0434: "д" CYRILLIC SMALL LETTER DE
                    // U+043F: "п" CYRILLIC SMALL LETTER PE
                    "\u0439", "\u044B", "\u0431", "\u04E9", "\u0430", "\u0445", "\u0440", "\u043E",
                    "\u043B", "\u0434", "\u043F")
            .setKeysOfRow(3,
                    // U+044F: "я" CYRILLIC SMALL LETTER YA
                    // U+0447: "ч" CYRILLIC SMALL LETTER CHE
                    "\u044F", "\u0447",
                    // U+0451: "ё" CYRILLIC SMALL LETTER IO
                    // U+0435: "е" CYRILLIC SMALL LETTER IE
                    key("\u0451", moreKey("\u0435")),
                    // U+0441: "с" CYRILLIC SMALL LETTER ES
                    // U+043C: "м" CYRILLIC SMALL LETTER EM
                    // U+0438: "и" CYRILLIC SMALL LETTER I
                    // U+0442: "т" CYRILLIC SMALL LETTER TE
                    "\u0441", "\u043C", "\u0438", "\u0442",
                    // U+044C: "ь" CYRILLIC SMALL LETTER SOFT SIGN
                    // U+044A: "ъ" CYRILLIC SMALL LETTER HARD SIGN
                    key("\u044C", moreKey("\u044A")),
                    // U+0432: "в" CYRILLIC SMALL LETTER VE
                    // U+044E: "ю" CYRILLIC SMALL LETTER YU
                    key("\u0432", moreKey("\u044E")))
            .build();
}
