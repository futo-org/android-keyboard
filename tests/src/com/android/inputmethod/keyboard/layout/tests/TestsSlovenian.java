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
import com.android.inputmethod.keyboard.layout.Qwerty;
import com.android.inputmethod.keyboard.layout.Symbols;
import com.android.inputmethod.keyboard.layout.customizer.EuroCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

/**
 * sl: Slovenian/qwerty
 */
@SmallTest
public final class TestsSlovenian extends LayoutTestsBase {
    private static final Locale LOCALE = new Locale("sl");
    private static final LayoutBase LAYOUT = new Qwerty(new SlovenianCustomizer(LOCALE));

    @Override
    LayoutBase getLayout() { return LAYOUT; }

    private static class SlovenianCustomizer extends EuroCustomizer {
        SlovenianCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey[] getDoubleQuoteMoreKeys() { return Symbols.DOUBLE_QUOTES_R9L; }

        @Override
        public ExpectedKey[] getSingleQuoteMoreKeys() { return Symbols.SINGLE_QUOTES_R9L; }

        @Override
        public ExpectedKey[] getDoubleAngleQuoteKeys() { return Symbols.DOUBLE_ANGLE_QUOTES_RL; }

        @Override
        public ExpectedKey[] getSingleAngleQuoteKeys() { return Symbols.SINGLE_ANGLE_QUOTES_RL; }

        @Override
        public ExpectedKeyboardBuilder setAccentedLetters(final ExpectedKeyboardBuilder builder) {
            return builder
                    // U+0161: "š" LATIN SMALL LETTER S WITH CARON
                    .setMoreKeysOf("s", "\u0161")
                    // U+0111: "đ" LATIN SMALL LETTER D WITH STROKE
                    .setMoreKeysOf("d", "\u0111")
                    // U+017E: "ž" LATIN SMALL LETTER Z WITH CARON
                    .setMoreKeysOf("z", "\u017E")
                    // U+010D: "č" LATIN SMALL LETTER C WITH CARON
                    // U+0107: "ć" LATIN SMALL LETTER C WITH ACUTE
                    .setMoreKeysOf("c", "\u010D", "\u0107");
        }
    }
}
