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
import com.android.inputmethod.keyboard.layout.customizer.EastSlavicCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

/**
 * be_BY: Belarusian (Belarus)/east_slavic
 */
@SmallTest
public final class TestsBelarusianBY extends LayoutTestsBase {
    private static final Locale LOCALE = new Locale("be", "BY");
    private static final LayoutBase LAYOUT = new EastSlavic(new BelarusianBYCustomizer(LOCALE));

    @Override
    LayoutBase getLayout() { return LAYOUT; }

    private static class BelarusianBYCustomizer extends EastSlavicCustomizer {
        BelarusianBYCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey[] getDoubleQuoteMoreKeys() {
            return Symbols.DOUBLE_QUOTES_R9L;
        }

        @Override
        public ExpectedKey[] getSingleQuoteMoreKeys() {
            return Symbols.SINGLE_QUOTES_R9L;
        }

        @Override
        public ExpectedKeyboardBuilder setAccentedLetters(final ExpectedKeyboardBuilder builder) {
            return builder
                    // U+0435: "е" CYRILLIC SMALL LETTER IE
                    // U+0451: "ё" CYRILLIC SMALL LETTER IO
                    .setMoreKeysOf("\u0435", "\u0451")
                    // U+045E: "ў" CYRILLIC SMALL LETTER SHORT U
                    .replaceKeyOfLabel(EastSlavic.ROW1_9, key("\u045E", additionalMoreKey("9")))
                    // U+044B: "ы" CYRILLIC SMALL LETTER YERU
                    .replaceKeyOfLabel(EastSlavic.ROW2_2, "\u044B")
                    // U+044D: "э" CYRILLIC SMALL LETTER E
                    .replaceKeyOfLabel(EastSlavic.ROW2_11, "\u044D")
                    // U+0456: "і" CYRILLIC SMALL LETTER BYELORUSSIAN-UKRAINIAN I
                    .replaceKeyOfLabel(EastSlavic.ROW3_5, "\u0456")
                    // U+044C: "ь" CYRILLIC SMALL LETTER SOFT SIGN
                    // U+044A: "ъ" CYRILLIC SMALL LETTER HARD SIGN
                    .setMoreKeysOf("\u044C", "\u044A");
        }
    }
}
