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

package com.android.inputmethod.keyboard.layout.tests;

import android.test.suitebuilder.annotation.SmallTest;

import com.android.inputmethod.keyboard.layout.EastSlavic;
import com.android.inputmethod.keyboard.layout.LayoutBase;
import com.android.inputmethod.keyboard.layout.Symbols;
import com.android.inputmethod.keyboard.layout.SymbolsShifted;
import com.android.inputmethod.keyboard.layout.customizer.EastSlavicCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

/**
 * uk: Ukrainian/east_slavic
 */
@SmallTest
public final class TestsUkrainian extends LayoutTestsBase {
    private static final Locale LOCALE = new Locale("uk");
    private static final LayoutBase LAYOUT = new EastSlavic(new UkrainianCustomizer(LOCALE));

    @Override
    LayoutBase getLayout() { return LAYOUT; }

    private static class UkrainianCustomizer extends EastSlavicCustomizer {
        UkrainianCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey getCurrencyKey() { return CURRENCY_HRYVNIA; }

        @Override
        public ExpectedKey[] getOtherCurrencyKeys() {
            return SymbolsShifted.CURRENCIES_OTHER_GENERIC;
        }

        @Override
        public ExpectedKey[] getDoubleQuoteMoreKeys() { return Symbols.DOUBLE_QUOTES_R9L; }

        @Override
        public ExpectedKey[] getSingleQuoteMoreKeys() { return Symbols.SINGLE_QUOTES_R9L; }

        // U+20B4: "₴" HRYVNIA SIGN
        private static final ExpectedKey CURRENCY_HRYVNIA = key("\u20B4",
                Symbols.CURRENCY_GENERIC_MORE_KEYS);

        @Override
        public ExpectedKeyboardBuilder setAccentedLetters(final ExpectedKeyboardBuilder builder) {
            return builder
                    // U+0433: "г" CYRILLIC SMALL LETTER GHE
                    // U+0491: "ґ" CYRILLIC SMALL LETTER GHE WITH UPTURN
                    .setMoreKeysOf("\u0433", "\u0491")
                    // U+0449: "щ" CYRILLIC SMALL LETTER SHCHA
                    .replaceKeyOfLabel(EastSlavic.ROW1_9, key("\u0449", additionalMoreKey("9")))
                    // U+0456: "і" CYRILLIC SMALL LETTER BYELORUSSIAN-UKRAINIAN I
                    // U+0457: "ї" CYRILLIC SMALL LETTER YI
                    .replaceKeyOfLabel(EastSlavic.ROW2_2, key("\u0456", moreKey("\u0457")))
                    // U+0454: "є" CYRILLIC SMALL LETTER UKRAINIAN IE
                    .replaceKeyOfLabel(EastSlavic.ROW2_11, "\u0454")
                    // U+0438: "и" CYRILLIC SMALL LETTER I
                    .replaceKeyOfLabel(EastSlavic.ROW3_5, "\u0438")
                    // U+044C: "ь" CYRILLIC SMALL LETTER SOFT SIGN
                    // U+044A: "ъ" CYRILLIC SMALL LETTER HARD SIGN
                    .setMoreKeysOf("\u044C", "\u044A");
        }
    }
}
