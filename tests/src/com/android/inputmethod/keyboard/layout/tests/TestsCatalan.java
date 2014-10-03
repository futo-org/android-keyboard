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
import com.android.inputmethod.keyboard.layout.Spanish;
import com.android.inputmethod.keyboard.layout.customizer.EuroCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

/**
 * ca: Catalan/spanish
 */
@SmallTest
public class TestsCatalan extends LayoutTestsBase {
    private static final Locale LOCALE = new Locale("ca");
    private static final LayoutBase LAYOUT = new Spanish(new CatalanCustomizer(LOCALE));

    @Override
    LayoutBase getLayout() { return LAYOUT; }

    private static class CatalanCustomizer extends EuroCustomizer {
        CatalanCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey[] getPunctuationMoreKeys(final boolean isPhone) {
            return isPhone ? CATALAN_PHONE_PUNCTUATION_MORE_KEYS
                    : CATALAN_TABLET_PUNCTUATION_MORE_KEYS;
        }

        // U+00B7: "·" MIDDLE DOT
        private static final ExpectedKey[] CATALAN_PHONE_PUNCTUATION_MORE_KEYS = joinKeys(
                ",", "?", "!", "\u00B7", "#", ")", "(", "/", ";",
                "'", "@", ":", "-", "\"", "+", "%", "&");

        private static final ExpectedKey[] CATALAN_TABLET_PUNCTUATION_MORE_KEYS = joinKeys(
                ",", "'", "\u00B7", "#", ")", "(", "/", ";",
                "@", ":", "-", "\"", "+", "%", "&");

        @Override
        public ExpectedKeyboardBuilder setAccentedLetters(final ExpectedKeyboardBuilder builder) {
            return builder
                    // U+00E8: "è" LATIN SMALL LETTER E WITH GRAVE
                    // U+00E9: "é" LATIN SMALL LETTER E WITH ACUTE
                    // U+00EB: "ë" LATIN SMALL LETTER E WITH DIAERESIS
                    // U+00EA: "ê" LATIN SMALL LETTER E WITH CIRCUMFLEX
                    // U+0119: "ę" LATIN SMALL LETTER E WITH OGONEK
                    // U+0117: "ė" LATIN SMALL LETTER E WITH DOT ABOVE
                    // U+0113: "ē" LATIN SMALL LETTER E WITH MACRON
                    .setMoreKeysOf("e",
                            "\u00E8", "\u00E9", "\u00EB", "\u00EA", "\u0119", "\u0117", "\u0113")
                    // U+00FA: "ú" LATIN SMALL LETTER U WITH ACUTE
                    // U+00FC: "ü" LATIN SMALL LETTER U WITH DIAERESIS
                    // U+00F9: "ù" LATIN SMALL LETTER U WITH GRAVE
                    // U+00FB: "û" LATIN SMALL LETTER U WITH CIRCUMFLEX
                    // U+016B: "ū" LATIN SMALL LETTER U WITH MACRON
                    .setMoreKeysOf("u", "\u00FA", "\u00FC", "\u00F9", "\u00FB", "\u016B")
                    // U+00ED: "í" LATIN SMALL LETTER I WITH ACUTE
                    // U+00EF: "ï" LATIN SMALL LETTER I WITH DIAERESIS
                    // U+00EC: "ì" LATIN SMALL LETTER I WITH GRAVE
                    // U+00EE: "î" LATIN SMALL LETTER I WITH CIRCUMFLEX
                    // U+012F: "į" LATIN SMALL LETTER I WITH OGONEK
                    // U+012B: "ī" LATIN SMALL LETTER I WITH MACRON
                    .setMoreKeysOf("i", "\u00ED", "\u00EF", "\u00EC", "\u00EE", "\u012F", "\u012B")
                    // U+00F2: "ò" LATIN SMALL LETTER O WITH GRAVE
                    // U+00F3: "ó" LATIN SMALL LETTER O WITH ACUTE
                    // U+00F6: "ö" LATIN SMALL LETTER O WITH DIAERESIS
                    // U+00F4: "ô" LATIN SMALL LETTER O WITH CIRCUMFLEX
                    // U+00F5: "õ" LATIN SMALL LETTER O WITH TILDE
                    // U+00F8: "ø" LATIN SMALL LETTER O WITH STROKE
                    // U+0153: "œ" LATIN SMALL LIGATURE OE
                    // U+014D: "ō" LATIN SMALL LETTER O WITH MACRON
                    // U+00BA: "º" MASCULINE ORDINAL INDICATOR
                    .setMoreKeysOf("o",
                            "\u00F2", "\u00F3", "\u00F6", "\u00F4", "\u00F5", "\u00F8", "\u0153",
                            "\u014D", "\u00BA")
                    // U+00E0: "à" LATIN SMALL LETTER A WITH GRAVE
                    // U+00E1: "á" LATIN SMALL LETTER A WITH ACUTE
                    // U+00E4: "ä" LATIN SMALL LETTER A WITH DIAERESIS
                    // U+00E2: "â" LATIN SMALL LETTER A WITH CIRCUMFLEX
                    // U+00E3: "ã" LATIN SMALL LETTER A WITH TILDE
                    // U+00E5: "å" LATIN SMALL LETTER A WITH RING ABOVE
                    // U+0105: "ą" LATIN SMALL LETTER A WITH OGONEK
                    // U+00E6: "æ" LATIN SMALL LETTER AE
                    // U+0101: "ā" LATIN SMALL LETTER A WITH MACRON
                    // U+00AA: "ª" FEMININE ORDINAL INDICATOR
                    .setMoreKeysOf("a",
                            "\u00E0", "\u00E1", "\u00E4", "\u00E2", "\u00E3", "\u00E5", "\u0105",
                            "\u00E6", "\u0101", "\u00AA")
                    // U+00B7: "·" MIDDLE DOT
                    // U+0142: "ł" LATIN SMALL LETTER L WITH STROKE
                    .setMoreKeysOf("l", "l\u00B7l", "\u0142")
                    // U+00E7: "ç" LATIN SMALL LETTER C WITH CEDILLA
                    .replaceKeyOfLabel(Spanish.ROW2_10, "\u00E7")
                    // U+00E7: "ç" LATIN SMALL LETTER C WITH CEDILLA
                    // U+0107: "ć" LATIN SMALL LETTER C WITH ACUTE
                    // U+010D: "č" LATIN SMALL LETTER C WITH CARON
                    .setMoreKeysOf("c", "\u00E7", "\u0107", "\u010D")
                    // U+00F1: "ñ" LATIN SMALL LETTER N WITH TILDE
                    // U+0144: "ń" LATIN SMALL LETTER N WITH ACUTE
                    .setMoreKeysOf("n", "\u00F1", "\u0144");
        }
    }
}
