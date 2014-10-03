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
import com.android.inputmethod.keyboard.layout.customizer.LayoutCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

/**
 * ro: Romanian/qwerty
 */
@SmallTest
public final class TestsRomanian extends LayoutTestsBase {
    private static final Locale LOCALE = new Locale("ro");
    private static final LayoutBase LAYOUT = new Qwerty(new RomanianCustomizer(LOCALE));

    @Override
    LayoutBase getLayout() { return LAYOUT; }

    private static class RomanianCustomizer extends LayoutCustomizer {
        RomanianCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey[] getDoubleQuoteMoreKeys() { return Symbols.DOUBLE_QUOTES_L9R; }

        @Override
        public ExpectedKey[] getSingleQuoteMoreKeys() { return Symbols.SINGLE_QUOTES_L9R; }

        @Override
        public ExpectedKeyboardBuilder setAccentedLetters(final ExpectedKeyboardBuilder builder) {
            return builder
                    // U+021B: "ț" LATIN SMALL LETTER T WITH COMMA BELOW
                    .setMoreKeysOf("t", "\u021B")
                    // U+00EE: "î" LATIN SMALL LETTER I WITH CIRCUMFLEX
                    // U+00EF: "ï" LATIN SMALL LETTER I WITH DIAERESIS
                    // U+00EC: "ì" LATIN SMALL LETTER I WITH GRAVE
                    // U+00ED: "í" LATIN SMALL LETTER I WITH ACUTE
                    // U+012F: "į" LATIN SMALL LETTER I WITH OGONEK
                    // U+012B: "ī" LATIN SMALL LETTER I WITH MACRON
                    .setMoreKeysOf("i", "\u00EE", "\u00EF", "\u00EC", "\u00ED", "\u012F", "\u012B")
                    // U+0103: "ă" LATIN SMALL LETTER A WITH BREVE
                    // U+00E2: "â" LATIN SMALL LETTER A WITH CIRCUMFLEX
                    // U+00E3: "ã" LATIN SMALL LETTER A WITH TILDE
                    // U+00E0: "à" LATIN SMALL LETTER A WITH GRAVE
                    // U+00E1: "á" LATIN SMALL LETTER A WITH ACUTE
                    // U+00E4: "ä" LATIN SMALL LETTER A WITH DIAERESIS
                    // U+00E6: "æ" LATIN SMALL LETTER AE
                    // U+00E5: "å" LATIN SMALL LETTER A WITH RING ABOVE
                    // U+0101: "ā" LATIN SMALL LETTER A WITH MACRON
                    .setMoreKeysOf("a",
                            "\u0103", "\u00E2", "\u00E3", "\u00E0", "\u00E1", "\u00E4", "\u00E6",
                            "\u00E5", "\u0101")
                    // U+0219: "ș" LATIN SMALL LETTER S WITH COMMA BELOW
                    // U+00DF: "ß" LATIN SMALL LETTER SHARP S
                    // U+015B: "ś" LATIN SMALL LETTER S WITH ACUTE
                    // U+0161: "š" LATIN SMALL LETTER S WITH CARON
                    .setMoreKeysOf("s", "\u0219", "\u00DF", "\u015B", "\u0161");
        }
    }
}
