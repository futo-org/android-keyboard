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

public final class SouthSlavic extends LayoutBase {
    private static final String LAYOUT_NAME = "south_slavic";

    public SouthSlavic(final LayoutCustomizer customizer) {
        super(customizer, Symbols.class, SymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    @Override
    ExpectedKey[][] getCommonAlphabetLayout(final boolean isPhone) { return ALPHABET_COMMON; }

    public static final String ROW1_6 = "ROW1_6";
    public static final String ROW2_11 = "ROW2_11";
    public static final String ROW3_1 = "ROW3_1";
    public static final String ROW3_8 = "ROW3_8";

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+0459: "љ" CYRILLIC SMALL LETTER LJE
                    key("\u0459", additionalMoreKey("1")),
                    // U+045A: "њ" CYRILLIC SMALL LETTER NJE
                    key("\u045A", additionalMoreKey("2")),
                    // U+0435: "е" CYRILLIC SMALL LETTER IE
                    key("\u0435", additionalMoreKey("3")),
                    // U+0440: "р" CYRILLIC SMALL LETTER ER
                    key("\u0440", additionalMoreKey("4")),
                    // U+0442: "т" CYRILLIC SMALL LETTER TE
                    key("\u0442", additionalMoreKey("5")),
                    key(ROW1_6, additionalMoreKey("6")),
                    // U+0443: "у" CYRILLIC SMALL LETTER U
                    key("\u0443", additionalMoreKey("7")),
                    // U+0438: "и" CYRILLIC SMALL LETTER I
                    key("\u0438", additionalMoreKey("8")),
                    // U+043E: "о" CYRILLIC SMALL LETTER O
                    key("\u043E", additionalMoreKey("9")),
                    // U+043F: "п" CYRILLIC SMALL LETTER PE
                    key("\u043F", additionalMoreKey("0")),
                    // U+0448: "ш" CYRILLIC SMALL LETTER SHA
                    "\u0448")
            .setKeysOfRow(2,
                    // U+0430: "а" CYRILLIC SMALL LETTER A
                    // U+0441: "с" CYRILLIC SMALL LETTER ES
                    // U+0434: "д" CYRILLIC SMALL LETTER DE
                    // U+0444: "ф" CYRILLIC SMALL LETTER EF
                    // U+0433: "г" CYRILLIC SMALL LETTER GHE
                    // U+0445: "х" CYRILLIC SMALL LETTER HA
                    // U+0458: "ј" CYRILLIC SMALL LETTER JE
                    // U+043A: "к" CYRILLIC SMALL LETTER KA
                    // U+043B: "л" CYRILLIC SMALL LETTER EL
                    // U+0447: "ч" CYRILLIC SMALL LETTER CHE
                    "\u0430", "\u0441", "\u0434", "\u0444", "\u0433", "\u0445", "\u0458", "\u043A",
                    "\u043B", "\u0447", ROW2_11)
            .setKeysOfRow(3,
                    // U+045F: "џ" CYRILLIC SMALL LETTER DZHE
                    // U+0446: "ц" CYRILLIC SMALL LETTER TSE
                    // U+0432: "в" CYRILLIC SMALL LETTER VE
                    // U+0431: "б" CYRILLIC SMALL LETTER BE
                    // U+043D: "н" CYRILLIC SMALL LETTER EN
                    // U+043C: "м" CYRILLIC SMALL LETTER EM
                    // U+0436: "ж" CYRILLIC SMALL LETTER ZHE
                    ROW3_1, "\u045F", "\u0446", "\u0432", "\u0431", "\u043D", "\u043C", ROW3_8,
                    "\u0436")
            .build();
}
