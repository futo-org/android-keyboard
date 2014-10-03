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

package com.android.inputmethod.keyboard.layout.customizer;

import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

/**
 * Turkic languages layout customizer.
 */
public class TurkicCustomizer extends LayoutCustomizer {
    public TurkicCustomizer(final Locale locale) { super(locale); }

    @Override
    public ExpectedKeyboardBuilder setAccentedLetters(final ExpectedKeyboardBuilder builder) {
        return builder
                // U+0259: "ə" LATIN SMALL LETTER SCHWA
                // U+00E9: "é" LATIN SMALL LETTER E WITH ACUTE
                .setMoreKeysOf("e", "\u0259", "\u00E9")
                // U+00FD: "ý" LATIN SMALL LETTER Y WITH ACUTE
                .setMoreKeysOf("y", "\u00FD")
                // U+00FC: "ü" LATIN SMALL LETTER U WITH DIAERESIS
                // U+00FB: "û" LATIN SMALL LETTER U WITH CIRCUMFLEX
                // U+00F9: "ù" LATIN SMALL LETTER U WITH GRAVE
                // U+00FA: "ú" LATIN SMALL LETTER U WITH ACUTE
                // U+016B: "ū" LATIN SMALL LETTER U WITH MACRON
                .setMoreKeysOf("u", "\u00FC", "\u00FB", "\u00F9", "\u00FA", "\u016B")
                // U+0131: "ı" LATIN SMALL LETTER DOTLESS I
                // U+00EE: "î" LATIN SMALL LETTER I WITH CIRCUMFLEX
                // U+00EF: "ï" LATIN SMALL LETTER I WITH DIAERESIS
                // U+00EC: "ì" LATIN SMALL LETTER I WITH GRAVE
                // U+00ED: "í" LATIN SMALL LETTER I WITH ACUTE
                // U+012F: "į" LATIN SMALL LETTER I WITH OGONEK
                // U+012B: "ī" LATIN SMALL LETTER I WITH MACRON
                .setMoreKeysOf("i",
                        "\u0131", "\u00EE", "\u00EF", "\u00EC", "\u00ED", "\u012F", "\u012B")
                // U+00F6: "ö" LATIN SMALL LETTER O WITH DIAERESIS
                // U+00F4: "ô" LATIN SMALL LETTER O WITH CIRCUMFLEX
                // U+0153: "œ" LATIN SMALL LIGATURE OE
                // U+00F2: "ò" LATIN SMALL LETTER O WITH GRAVE
                // U+00F3: "ó" LATIN SMALL LETTER O WITH ACUTE
                // U+00F5: "õ" LATIN SMALL LETTER O WITH TILDE
                // U+00F8: "ø" LATIN SMALL LETTER O WITH STROKE
                // U+014D: "ō" LATIN SMALL LETTER O WITH MACRON
                .setMoreKeysOf("o",
                        "\u00F6", "\u00F4", "\u0153", "\u00F2", "\u00F3", "\u00F5", "\u00F8",
                        "\u014D")
                // U+00E2: "â" LATIN SMALL LETTER A WITH CIRCUMFLEX
                // U+00E4: "ä" LATIN SMALL LETTER A WITH DIAERESIS
                // U+00E1: "á" LATIN SMALL LETTER A WITH ACUTE
                .setMoreKeysOf("a", "\u00E2", "\u00E4", "\u00E1")
                // U+015F: "ş" LATIN SMALL LETTER S WITH CEDILLA
                // U+00DF: "ß" LATIN SMALL LETTER SHARP S
                // U+015B: "ś" LATIN SMALL LETTER S WITH ACUTE
                // U+0161: "š" LATIN SMALL LETTER S WITH CARON
                .setMoreKeysOf("s", "\u015F", "\u00DF", "\u015B", "\u0161")
                // U+011F: "ğ" LATIN SMALL LETTER G WITH BREVE
                .setMoreKeysOf("g", "\u011F")
                // U+017E: "ž" LATIN SMALL LETTER Z WITH CARON
                .setMoreKeysOf("z", "\u017E")
                // U+00E7: "ç" LATIN SMALL LETTER C WITH CEDILLA
                // U+0107: "ć" LATIN SMALL LETTER C WITH ACUTE
                // U+010D: "č" LATIN SMALL LETTER C WITH CARON
                .setMoreKeysOf("c", "\u00E7", "\u0107", "\u010D")
                // U+0148: "ň" LATIN SMALL LETTER N WITH CARON
                // U+00F1: "ñ" LATIN SMALL LETTER N WITH TILDE
                .setMoreKeysOf("n", "\u0148", "\u00F1");
    }
}
