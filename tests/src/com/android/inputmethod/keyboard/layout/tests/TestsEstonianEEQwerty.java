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
import com.android.inputmethod.keyboard.layout.customizer.EstonianEECustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

/**
 * et_EE: Estonian (Estonia)/qwerty
 */
@SmallTest
public final class TestsEstonianEEQwerty extends LayoutTestsBase {
    private static final Locale LOCALE = new Locale("et", "EE");
    private static final LayoutBase LAYOUT = new Qwerty(new EstonianEEQwertyCustomizer(LOCALE));

    @Override
    LayoutBase getLayout() { return LAYOUT; }

    private static class EstonianEEQwertyCustomizer extends EstonianEECustomizer {
        EstonianEEQwertyCustomizer(final Locale locale) { super(locale); }

        @Override
        protected void setNordicKeys(final ExpectedKeyboardBuilder builder) {
            // QWERTY layout doesn't have Nordic keys.
        }

        @Override
        protected void setMoreKeysOfA(final ExpectedKeyboardBuilder builder) {
            builder
                    // U+00E4: "ä" LATIN SMALL LETTER A WITH DIAERESIS
                    // U+0101: "ā" LATIN SMALL LETTER A WITH MACRON
                    // U+00E0: "à" LATIN SMALL LETTER A WITH GRAVE
                    // U+00E1: "á" LATIN SMALL LETTER A WITH ACUTE
                    // U+00E2: "â" LATIN SMALL LETTER A WITH CIRCUMFLEX
                    // U+00E3: "ã" LATIN SMALL LETTER A WITH TILDE
                    // U+00E5: "å" LATIN SMALL LETTER A WITH RING ABOVE
                    // U+00E6: "æ" LATIN SMALL LETTER AE
                    // U+0105: "ą" LATIN SMALL LETTER A WITH OGONEK
                    .setMoreKeysOf("a", "\u00E4", "\u0101", "\u00E0", "\u00E1", "\u00E2", "\u00E3",
                            "\u00E5", "\u00E6", "\u0105");
        }

        @Override
        protected void setMoreKeysOfI(final ExpectedKeyboardBuilder builder, final int elementId) {
            // TODO: The upper-case letter of "ı" in Estonian locale is "I". It should be omitted
            // from the more keys of "I".
            builder
                    // U+012B: "ī" LATIN SMALL LETTER I WITH MACRON
                    // U+00EC: "ì" LATIN SMALL LETTER I WITH GRAVE
                    // U+012F: "į" LATIN SMALL LETTER I WITH OGONEK
                    // U+00ED: "í" LATIN SMALL LETTER I WITH ACUTE
                    // U+00EE: "î" LATIN SMALL LETTER I WITH CIRCUMFLEX
                    // U+00EF: "ï" LATIN SMALL LETTER I WITH DIAERESIS
                    // U+0131: "ı" LATIN SMALL LETTER DOTLESS I
                    .setMoreKeysOf("i",
                            "\u012B", "\u00EC", "\u012F", "\u00ED", "\u00EE", "\u00EF", "\u0131");
        }

        @Override
        protected void setMoreKeysOfO(final ExpectedKeyboardBuilder builder) {
            builder
                    // U+00F6: "ö" LATIN SMALL LETTER O WITH DIAERESIS
                    // U+00F5: "õ" LATIN SMALL LETTER O WITH TILDE
                    // U+00F2: "ò" LATIN SMALL LETTER O WITH GRAVE
                    // U+00F3: "ó" LATIN SMALL LETTER O WITH ACUTE
                    // U+00F4: "ô" LATIN SMALL LETTER O WITH CIRCUMFLEX
                    // U+0153: "œ" LATIN SMALL LIGATURE OE
                    // U+0151: "ő" LATIN SMALL LETTER O WITH DOUBLE ACUTE
                    // U+00F8: "ø" LATIN SMALL LETTER O WITH STROKE
                    .setMoreKeysOf("o", "\u00F6", "\u00F5", "\u00F2", "\u00F3", "\u00F4", "\u0153",
                            "\u0151", "\u00F8");
        }

        @Override
        protected void setMoreKeysOfU(final ExpectedKeyboardBuilder builder) {
            builder
                    // U+00FC: "ü" LATIN SMALL LETTER U WITH DIAERESIS
                    // U+016B: "ū" LATIN SMALL LETTER U WITH MACRON
                    // U+0173: "ų" LATIN SMALL LETTER U WITH OGONEK
                    // U+00F9: "ù" LATIN SMALL LETTER U WITH GRAVE
                    // U+00FA: "ú" LATIN SMALL LETTER U WITH ACUTE
                    // U+00FB: "û" LATIN SMALL LETTER U WITH CIRCUMFLEX
                    // U+016F: "ů" LATIN SMALL LETTER U WITH RING ABOVE
                    // U+0171: "ű" LATIN SMALL LETTER U WITH DOUBLE ACUTE
                    .setMoreKeysOf("u", "\u00FC", "\u016B", "\u0173", "\u00F9", "\u00FA", "\u00FB",
                            "\u016F", "\u0171");
        }
    }
}
