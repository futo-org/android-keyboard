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

package org.futo.inputmethod.keyboard.layout.tests;

import android.test.suitebuilder.annotation.SmallTest;

import org.futo.inputmethod.keyboard.layout.LayoutBase;
import org.futo.inputmethod.keyboard.layout.Qwerty;
import org.futo.inputmethod.keyboard.layout.customizer.LayoutCustomizer;
import org.futo.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

/**
 * af: TestsAfrikaans/qwerty
 */
@SmallTest
public final class TestsAfrikaans extends LayoutTestsBase {
    private static final Locale LOCALE = new Locale("af");
    private static final LayoutBase LAYOUT = new Qwerty(new AfrikaansCustomizer(LOCALE));

    @Override
    LayoutBase getLayout() { return LAYOUT; }

    private static class AfrikaansCustomizer extends LayoutCustomizer {
        AfrikaansCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKeyboardBuilder setAccentedLetters(final ExpectedKeyboardBuilder builder) {
            return builder
                    // U+00E9: "é" LATIN SMALL LETTER E WITH ACUTE
                    // U+00E8: "è" LATIN SMALL LETTER E WITH GRAVE
                    // U+00EA: "ê" LATIN SMALL LETTER E WITH CIRCUMFLEX
                    // U+00EB: "ë" LATIN SMALL LETTER E WITH DIAERESIS
                    .setMoreKeysOf("e", "\u00E9", "\u00E8", "\u00EA", "\u00EB")
                    // U+00FA: "ú" LATIN SMALL LETTER U WITH ACUTE
                    // U+00FB: "û" LATIN SMALL LETTER U WITH CIRCUMFLEX
                    .setMoreKeysOf("u", "\u00FA", "\u00FB")
                    // U+00FD: "ý" LATIN SMALL LETTER Y WITH ACUTE
                    .setMoreKeysOf("y", "\u00FD")
                    // U+00ED: "í" LATIN SMALL LETTER I WITH ACUTE
                    // U+00EC: "ì" LATIN SMALL LETTER I WITH GRAVE
                    // U+00EF: "ï" LATIN SMALL LETTER I WITH DIAERESIS
                    // U+00EE: "î" LATIN SMALL LETTER I WITH CIRCUMFLEX
                    .setMoreKeysOf("i", "\u00ED", "\u00EC", "\u00EF", "\u00EE")
                    // U+00F3: "ó" LATIN SMALL LETTER O WITH ACUTE
                    // U+00F4: "ô" LATIN SMALL LETTER O WITH CIRCUMFLEX
                    .setMoreKeysOf("o", "\u00F3", "\u00F4")
                    // U+00E1: "á" LATIN SMALL LETTER A WITH ACUTE
                    .setMoreKeysOf("a", "\u00E1");
        }
    }
}
