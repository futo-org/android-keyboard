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

public final class BulgarianBds extends LayoutBase {
    private static final String LAYOUT_NAME = "bulgarian_bds";

    public BulgarianBds(final Locale locale) {
        super(new BulgarianBdsCustomizer(locale), Symbols.class, SymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    private static class BulgarianBdsCustomizer extends EastSlavicCustomizer {
        BulgarianBdsCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey[] getDoubleQuoteMoreKeys() { return Symbols.DOUBLE_QUOTES_R9L; }
    }

    @Override
    ExpectedKey[][] getCommonAlphabetLayout(final boolean isPhone) { return ALPHABET_COMMON; }

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+0443: "у" CYRILLIC SMALL LETTER U
                    key("\u0443", moreKey("1")),
                    // U+0435: "е" CYRILLIC SMALL LETTER IE
                    key("\u0435", moreKey("2")),
                    // U+0438: "и" CYRILLIC SMALL LETTER I
                    // U+045D: "ѝ" CYRILLIC SMALL LETTER I WITH GRAVE
                    key("\u0438", joinMoreKeys("3", "\u045D")),
                    // U+0448: "ш" CYRILLIC SMALL LETTER SHA
                    key("\u0448", moreKey("4")),
                    // U+0449: "щ" CYRILLIC SMALL LETTER SHCHA
                    key("\u0449", moreKey("5")),
                    // U+043A: "к" CYRILLIC SMALL LETTER KA
                    key("\u043A", moreKey("6")),
                    // U+0441: "с" CYRILLIC SMALL LETTER ES
                    key("\u0441", moreKey("7")),
                    // U+0434: "д" CYRILLIC SMALL LETTER DE
                    key("\u0434", moreKey("8")),
                    // U+0437: "з" CYRILLIC SMALL LETTER ZE
                    key("\u0437", moreKey("9")),
                    // U+0446: "ц" CYRILLIC SMALL LETTER TSE
                    key("\u0446", moreKey("0")),
                    // U+0431: "б" CYRILLIC SMALL LETTER BE
                    "\u0431")
            .setKeysOfRow(2,
                    // U+044C: "ь" CYRILLIC SMALL LETTER SOFT SIGN
                    // U+044F: "я" CYRILLIC SMALL LETTER YA
                    // U+0430: "а" CYRILLIC SMALL LETTER A
                    // U+043E: "о" CYRILLIC SMALL LETTER O
                    // U+0436: "ж" CYRILLIC SMALL LETTER ZHE
                    // U+0433: "г" CYRILLIC SMALL LETTER GHE
                    // U+0442: "т" CYRILLIC SMALL LETTER TE
                    // U+043D: "н" CYRILLIC SMALL LETTER EN
                    // U+0432: "в" CYRILLIC SMALL LETTER VE
                    // U+043C: "м" CYRILLIC SMALL LETTER EM
                    // U+0447: "ч" CYRILLIC SMALL LETTER CHE
                    "\u044C", "\u044F", "\u0430", "\u043E", "\u0436", "\u0433", "\u0442", "\u043D",
                    "\u0432", "\u043C", "\u0447")
            .setKeysOfRow(3,
                    // U+044E: "ю" CYRILLIC SMALL LETTER YU
                    // U+0439: "й" CYRILLIC SMALL LETTER SHORT I
                    // U+044A: "ъ" CYRILLIC SMALL LETTER HARD SIGN
                    // U+044D: "э" CYRILLIC SMALL LETTER E
                    // U+0444: "ф" CYRILLIC SMALL LETTER EF
                    // U+0445: "х" CYRILLIC SMALL LETTER HA
                    // U+043F: "п" CYRILLIC SMALL LETTER PE
                    // U+0440: "р" CYRILLIC SMALL LETTER ER
                    // U+043B: "л" CYRILLIC SMALL LETTER EL
                    "\u044E", "\u0439", "\u044A", "\u044D", "\u0444", "\u0445", "\u043F", "\u0440",
                    "\u043B")
            .build();
}
