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
import com.android.inputmethod.keyboard.layout.SouthSlavic;
import com.android.inputmethod.keyboard.layout.Symbols;
import com.android.inputmethod.keyboard.layout.customizer.SouthSlavicLayoutCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

/**
 * mk: Macedonian/south_slavic
 */
@SmallTest
public final class TestsMacedonian extends LayoutTestsBase {
    private static final Locale LOCALE = new Locale("mk");
    private static final LayoutBase LAYOUT = new SouthSlavic(new MacedonianCustomizer(LOCALE));

    @Override
    LayoutBase getLayout() { return LAYOUT; }

    private static class MacedonianCustomizer extends SouthSlavicLayoutCustomizer {
        MacedonianCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey[] getDoubleQuoteMoreKeys() { return Symbols.DOUBLE_QUOTES_R9L; }

        @Override
        public ExpectedKey[] getSingleQuoteMoreKeys() { return Symbols.SINGLE_QUOTES_R9L; }

        @Override
        public ExpectedKeyboardBuilder setAccentedLetters(final ExpectedKeyboardBuilder builder) {
            return builder
                    // U+0435: "е" CYRILLIC SMALL LETTER IE
                    // U+0450: "ѐ" CYRILLIC SMALL LETTER IE WITH GRAVE
                    .setMoreKeysOf("\u0435", "\u0450")
                    // U+0455: "ѕ" CYRILLIC SMALL LETTER DZE
                    .replaceKeyOfLabel(SouthSlavic.ROW1_6, key("\u0455", additionalMoreKey("6")))
                    // U+0438: "и" CYRILLIC SMALL LETTER I
                    // U+045D: "ѝ" CYRILLIC SMALL LETTER I WITH GRAVE
                    .setMoreKeysOf("\u0438", "\u045D")
                    // U+045C: "ќ" CYRILLIC SMALL LETTER KJE
                    .replaceKeyOfLabel(SouthSlavic.ROW2_11, "\u045C")
                    // U+0437: "з" CYRILLIC SMALL LETTER ZE
                    .replaceKeyOfLabel(SouthSlavic.ROW3_1, "\u0437")
                    // U+0453: "ѓ" CYRILLIC SMALL LETTER GJE
                    .replaceKeyOfLabel(SouthSlavic.ROW3_8, "\u0453");
        }
    }
}
