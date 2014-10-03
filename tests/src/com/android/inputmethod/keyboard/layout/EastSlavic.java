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

public final class EastSlavic extends LayoutBase {
    private static final String LAYOUT_NAME = "east_slavic";

    public EastSlavic(final LayoutCustomizer customizer) {
        super(customizer, Symbols.class, SymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    @Override
    ExpectedKey[][] getCommonAlphabetLayout(final boolean isPhone) { return ALPHABET_COMMON; }

    public static final String ROW1_9 = "ROW1_9";
    public static final String ROW2_2 = "ROW2_2";
    public static final String ROW2_11 = "ROW2_11";
    public static final String ROW3_5 = "ROW3_5";

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+0443: "у" CYRILLIC SMALL LETTER U
                    key("\u0439", additionalMoreKey("1")),
                    // U+0446: "ц" CYRILLIC SMALL LETTER TSE
                    key("\u0446", additionalMoreKey("2")),
                    // U+0439: "й" CYRILLIC SMALL LETTER SHORT I
                    key("\u0443", additionalMoreKey("3")),
                    // U+043A: "к" CYRILLIC SMALL LETTER KA
                    key("\u043A", additionalMoreKey("4")),
                    // U+0435: "е" CYRILLIC SMALL LETTER IE
                    key("\u0435", additionalMoreKey("5")),
                    // U+043D: "н" CYRILLIC SMALL LETTER EN
                    key("\u043D", additionalMoreKey("6")),
                    // U+0433: "г" CYRILLIC SMALL LETTER GHE
                    key("\u0433", additionalMoreKey("7")),
                    // U+0448: "ш" CYRILLIC SMALL LETTER SHA
                    key("\u0448", additionalMoreKey("8")),
                    key(ROW1_9, additionalMoreKey("9")),
                    // U+0437: "з" CYRILLIC SMALL LETTER ZE
                    key("\u0437", additionalMoreKey("0")),
                    // U+0445: "х" CYRILLIC SMALL LETTER HA
                    "\u0445")
            .setKeysOfRow(2,
                    // U+0444: "ф" CYRILLIC SMALL LETTER EF
                    // U+0432: "в" CYRILLIC SMALL LETTER VE
                    // U+0430: "а" CYRILLIC SMALL LETTER A
                    // U+043F: "п" CYRILLIC SMALL LETTER PE
                    // U+0440: "р" CYRILLIC SMALL LETTER ER
                    // U+043E: "о" CYRILLIC SMALL LETTER O
                    // U+043B: "л" CYRILLIC SMALL LETTER EL
                    // U+0434: "д" CYRILLIC SMALL LETTER DE
                    // U+0436: "ж" CYRILLIC SMALL LETTER ZHE
                    "\u0444", ROW2_2, "\u0432", "\u0430", "\u043F", "\u0440", "\u043E", "\u043B",
                    "\u0434", "\u0436", ROW2_11)
            .setKeysOfRow(3,
                    // U+044F: "я" CYRILLIC SMALL LETTER YA
                    // U+0447: "ч" CYRILLIC SMALL LETTER CHE
                    // U+0441: "с" CYRILLIC SMALL LETTER ES
                    // U+043C: "м" CYRILLIC SMALL LETTER EM
                    // U+0442: "т" CYRILLIC SMALL LETTER TE
                    // U+044C: "ь" CYRILLIC SMALL LETTER SOFT SIGN
                    // U+0431: "б" CYRILLIC SMALL LETTER BE
                    // U+044E: "ю" CYRILLIC SMALL LETTER YU
                    "\u044F", "\u0447", "\u0441", "\u043C", ROW3_5, "\u0442", "\u044C", "\u0431",
                    "\u044E")
            .build();
}
