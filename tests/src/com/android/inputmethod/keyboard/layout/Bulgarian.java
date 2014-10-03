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
import com.android.inputmethod.keyboard.layout.customizer.LayoutCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

public final class Bulgarian extends LayoutBase {
    private static final String LAYOUT_NAME = "bulgarian";

    public Bulgarian(final Locale locale) {
        super(new BulgarianCustomizer(locale), Symbols.class, SymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    private static class BulgarianCustomizer extends LayoutCustomizer {
        private final EastSlavicCustomizer mEastSlavicCustomizer;

        BulgarianCustomizer(final Locale locale) {
            super(locale);
            mEastSlavicCustomizer = new EastSlavicCustomizer(locale);
        }

        @Override
        public ExpectedKey getAlphabetKey() {
            return mEastSlavicCustomizer.getAlphabetKey();
        }

        @Override
        public ExpectedKey[] getDoubleQuoteMoreKeys() { return Symbols.DOUBLE_QUOTES_R9L; }
    }

    @Override
    ExpectedKey[][] getCommonAlphabetLayout(final boolean isPhone) { return ALPHABET_COMMON; }

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+044F: "я" CYRILLIC SMALL LETTER YA
                    key("\u044F", moreKey("1")),
                    // U+0432: "в" CYRILLIC SMALL LETTER VE
                    key("\u0432", moreKey("2")),
                    // U+0435: "е" CYRILLIC SMALL LETTER IE
                    key("\u0435", moreKey("3")),
                    // U+0440: "р" CYRILLIC SMALL LETTER ER
                    key("\u0440", moreKey("4")),
                    // U+0442: "т" CYRILLIC SMALL LETTER TE
                    key("\u0442", moreKey("5")),
                    // U+044A: "ъ" CYRILLIC SMALL LETTER HARD SIGN
                    key("\u044A", moreKey("6")),
                    // U+0443: "у" CYRILLIC SMALL LETTER U
                    key("\u0443", moreKey("7")),
                    // U+0438: "и" CYRILLIC SMALL LETTER I
                    // U+045D: "ѝ" CYRILLIC SMALL LETTER I WITH GRAVE
                    key("\u0438", joinMoreKeys("8", "\u045D")),
                    // U+043E: "о" CYRILLIC SMALL LETTER O
                    key("\u043E", moreKey("9")),
                    // U+043F: "п" CYRILLIC SMALL LETTER PE
                    key("\u043F", moreKey("0")),
                    // U+0447: "ч" CYRILLIC SMALL LETTER CHE
                    "\u0447")
            .setKeysOfRow(2,
                    // U+0430: "а" CYRILLIC SMALL LETTER A
                    // U+0441: "с" CYRILLIC SMALL LETTER ES
                    // U+0434: "д" CYRILLIC SMALL LETTER DE
                    // U+0444: "ф" CYRILLIC SMALL LETTER EF
                    // U+0433: "г" CYRILLIC SMALL LETTER GHE
                    // U+0445: "х" CYRILLIC SMALL LETTER HA
                    // U+0439: "й" CYRILLIC SMALL LETTER SHORT I
                    // U+043A: "к" CYRILLIC SMALL LETTER KA
                    // U+043B: "л" CYRILLIC SMALL LETTER EL
                    // U+0448: "ш" CYRILLIC SMALL LETTER SHA
                    // U+0449: "щ" CYRILLIC SMALL LETTER SHCHA
                    "\u0430", "\u0441", "\u0434", "\u0444", "\u0433", "\u0445", "\u0439", "\u043A",
                    "\u043B", "\u0448", "\u0449")
            .setKeysOfRow(3,
                    // U+0437: "з" CYRILLIC SMALL LETTER ZE
                    // U+044C: "ь" CYRILLIC SMALL LETTER SOFT SIGN
                    // U+0446: "ц" CYRILLIC SMALL LETTER TSE
                    // U+0436: "ж" CYRILLIC SMALL LETTER ZHE
                    // U+0431: "б" CYRILLIC SMALL LETTER BE
                    // U+043D: "н" CYRILLIC SMALL LETTER EN
                    // U+043C: "м" CYRILLIC SMALL LETTER EM
                    // U+044E: "ю" CYRILLIC SMALL LETTER YU
                    "\u0437", "\u044C", "\u0446", "\u0436", "\u0431", "\u043D", "\u043C", "\u044E")
            .build();
}
