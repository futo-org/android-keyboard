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

import com.android.inputmethod.keyboard.layout.LayoutBase;
import com.android.inputmethod.keyboard.layout.Qwertz;
import com.android.inputmethod.keyboard.layout.Symbols;
import com.android.inputmethod.keyboard.layout.customizer.LayoutCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

/**
 * hr: Croatian/qwertz
 */
@SmallTest
public final class TestsCroatian extends LayoutTestsBase {
    private static final Locale LOCALE = new Locale("hr");
    private static final LayoutBase LAYOUT = new Qwertz(new CroatianCustomizer(LOCALE));

    @Override
    LayoutBase getLayout() { return LAYOUT; }

    private static class CroatianCustomizer extends LayoutCustomizer {
        CroatianCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey[] getDoubleQuoteMoreKeys() { return Symbols.DOUBLE_QUOTES_L9R; }

        @Override
        public ExpectedKey[] getSingleQuoteMoreKeys() { return Symbols.SINGLE_QUOTES_L9R; }

        @Override
        public ExpectedKey[] getDoubleAngleQuoteKeys() { return Symbols.DOUBLE_ANGLE_QUOTES_RL; }

        @Override
        public ExpectedKey[] getSingleAngleQuoteKeys() { return Symbols.SINGLE_ANGLE_QUOTES_RL; }

        @Override
        public ExpectedKeyboardBuilder setAccentedLetters(final ExpectedKeyboardBuilder builder) {
            return builder
                    // U+017E: "ž" LATIN SMALL LETTER Z WITH CARON
                    // U+017A: "ź" LATIN SMALL LETTER Z WITH ACUTE
                    // U+017C: "ż" LATIN SMALL LETTER Z WITH DOT ABOVE
                    .setMoreKeysOf("z", "\u017E", "\u017A", "\u017C")
                    // U+0161: "š" LATIN SMALL LETTER S WITH CARON
                    // U+015B: "ś" LATIN SMALL LETTER S WITH ACUTE
                    // U+00DF: "ß" LATIN SMALL LETTER SHARP S
                    .setMoreKeysOf("s", "\u0161", "\u015B", "\u00DF")
                    // U+0111: "đ" LATIN SMALL LETTER D WITH STROKE
                    .setMoreKeysOf("d", "\u0111")
                    // U+010D: "č" LATIN SMALL LETTER C WITH CARON
                    // U+0107: "ć" LATIN SMALL LETTER C WITH ACUTE
                    // U+00E7: "ç" LATIN SMALL LETTER C WITH CEDILLA
                    .setMoreKeysOf("c", "\u010D", "\u0107", "\u00E7")
                    // U+00F1: "ñ" LATIN SMALL LETTER N WITH TILDE
                    // U+0144: "ń" LATIN SMALL LETTER N WITH ACUTE
                    .setMoreKeysOf("n", "\u00F1", "\u0144");
        }
    }
}
