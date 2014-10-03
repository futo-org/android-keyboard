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

import com.android.inputmethod.keyboard.layout.Colemak;
import com.android.inputmethod.keyboard.layout.LayoutBase;
import com.android.inputmethod.keyboard.layout.customizer.NorwegianCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

/**
 * nb: Norwegian Bokmål/colemak
 */
@SmallTest
public final class TestsNorwegianColemak extends LayoutTestsBase {
    private static final Locale LOCALE = new Locale("nb");
    private static final LayoutBase LAYOUT = new Colemak(new NorwegianColemakCustomizer(LOCALE));

    @Override
    LayoutBase getLayout() { return LAYOUT; }

    private static class NorwegianColemakCustomizer extends NorwegianCustomizer {
        NorwegianColemakCustomizer(final Locale locale) { super(locale); }

        @Override
        protected void setNordicKeys(final ExpectedKeyboardBuilder builder) {
            // Colemak layout doesn't have Nordic keys.
        }

        @Override
        protected void setMoreKeysOfA(final ExpectedKeyboardBuilder builder) {
            builder
                    // U+00E5: "å" LATIN SMALL LETTER A WITH RING ABOVE
                    // U+00F6: "ö" LATIN SMALL LETTER O WITH DIAERESIS
                    // U+00E4: "ä" LATIN SMALL LETTER A WITH DIAERESIS
                    // U+00E0: "à" LATIN SMALL LETTER A WITH GRAVE
                    // U+00E1: "á" LATIN SMALL LETTER A WITH ACUTE
                    // U+00E2: "â" LATIN SMALL LETTER A WITH CIRCUMFLEX
                    // U+00E3: "ã" LATIN SMALL LETTER A WITH TILDE
                    // U+0101: "ā" LATIN SMALL LETTER A WITH MACRON
                    .setMoreKeysOf("a", "\u00E5", "\u00E6", "\u00E4", "\u00E0", "\u00E1", "\u00E2",
                            "\u00E3", "\u0101");
        }

        @Override
        protected void setMoreKeysOfO(final ExpectedKeyboardBuilder builder) {
            builder
                    // U+00F8: "ø" LATIN SMALL LETTER O WITH STROKE
                    // U+00F6: "ö" LATIN SMALL LETTER O WITH DIAERESIS
                    // U+00F6: "ö" LATIN SMALL LETTER O WITH DIAERESIS
                    // U+00F4: "ô" LATIN SMALL LETTER O WITH CIRCUMFLEX
                    // U+00F2: "ò" LATIN SMALL LETTER O WITH GRAVE
                    // U+00F3: "ó" LATIN SMALL LETTER O WITH ACUTE
                    // U+00F5: "õ" LATIN SMALL LETTER O WITH TILDE
                    // U+0153: "œ" LATIN SMALL LIGATURE OE
                    // U+014D: "ō" LATIN SMALL LETTER O WITH MACRON
                    .setMoreKeysOf("o", "\u00F8", "\u00F6", "\u00F4", "\u00F2", "\u00F3", "\u00F5",
                            "\u0153", "\u014D");
        }
    }
}
