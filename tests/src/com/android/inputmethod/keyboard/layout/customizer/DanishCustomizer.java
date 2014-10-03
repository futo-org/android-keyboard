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

import com.android.inputmethod.keyboard.layout.Nordic;
import com.android.inputmethod.keyboard.layout.Symbols;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

public class DanishCustomizer extends EuroCustomizer {
    public DanishCustomizer(final Locale locale) { super(locale); }

    @Override
    public ExpectedKey[] getDoubleQuoteMoreKeys() { return Symbols.DOUBLE_QUOTES_R9L; }

    @Override
    public ExpectedKey[] getSingleQuoteMoreKeys() { return Symbols.SINGLE_QUOTES_R9L; }

    @Override
    public ExpectedKey[] getDoubleAngleQuoteKeys() { return Symbols.DOUBLE_ANGLE_QUOTES_RL; }

    @Override
    public ExpectedKey[] getSingleAngleQuoteKeys() { return Symbols.SINGLE_ANGLE_QUOTES_RL; }

    protected void setNordicKeys(final ExpectedKeyboardBuilder builder) {
        builder
                // U+00E5: "å" LATIN SMALL LETTER A WITH RING ABOVE
                .replaceKeyOfLabel(Nordic.ROW1_11, "\u00E5")
                // U+00E6: "æ" LATIN SMALL LETTER AE
                // U+00E4: "ä" LATIN SMALL LETTER A WITH DIAERESIS
                .replaceKeyOfLabel(Nordic.ROW2_10, "\u00E6")
                .setMoreKeysOf("\u00E6", "\u00E4")
                // U+00F8: "ø" LATIN SMALL LETTER O WITH STROKE
                // U+00F6: "ö" LATIN SMALL LETTER O WITH DIAERESIS
                .replaceKeyOfLabel(Nordic.ROW2_11, "\u00F8")
                .setMoreKeysOf("\u00F8", "\u00F6");
    }

    protected void setMoreKeysOfA(final ExpectedKeyboardBuilder builder) {
        builder
                // U+00E1: "á" LATIN SMALL LETTER A WITH ACUTE
                // U+00E4: "ä" LATIN SMALL LETTER A WITH DIAERESIS
                // U+00E0: "à" LATIN SMALL LETTER A WITH GRAVE
                // U+00E2: "â" LATIN SMALL LETTER A WITH CIRCUMFLEX
                // U+00E3: "ã" LATIN SMALL LETTER A WITH TILDE
                // U+0101: "ā" LATIN SMALL LETTER A WITH MACRON
                .setMoreKeysOf("a", "\u00E1", "\u00E4", "\u00E0", "\u00E2", "\u00E3", "\u0101");
    }

    protected void setMoreKeysOfO(final ExpectedKeyboardBuilder builder) {
        builder
                // U+00F6: "ö" LATIN SMALL LETTER O WITH DIAERESIS
                // U+00F3: "ó" LATIN SMALL LETTER O WITH ACUTE
                // U+00F4: "ô" LATIN SMALL LETTER O WITH CIRCUMFLEX
                // U+00F2: "ò" LATIN SMALL LETTER O WITH GRAVE
                // U+00F5: "õ" LATIN SMALL LETTER O WITH TILDE
                // U+0153: "œ" LATIN SMALL LIGATURE OE
                // U+014D: "ō" LATIN SMALL LETTER O WITH MACRON
                .setMoreKeysOf("o", "\u00F6", "\u00F3", "\u00F4", "\u00F2", "\u00F5", "\u0153",
                        "\u014D");
    }

    @Override
    public ExpectedKeyboardBuilder setAccentedLetters(final ExpectedKeyboardBuilder builder) {
        setNordicKeys(builder);
        setMoreKeysOfA(builder);
        setMoreKeysOfO(builder);
        return builder
                // U+00E9: "é" LATIN SMALL LETTER E WITH ACUTE
                // U+00EB: "ë" LATIN SMALL LETTER E WITH DIAERESIS
                .setMoreKeysOf("e", "\u00E9", "\u00EB")
                // U+00FD: "ý" LATIN SMALL LETTER Y WITH ACUTE
                // U+00FF: "ÿ" LATIN SMALL LETTER Y WITH DIAERESIS
                .setMoreKeysOf("y", "\u00FD", "\u00FF")
                // U+00FA: "ú" LATIN SMALL LETTER U WITH ACUTE
                // U+00FC: "ü" LATIN SMALL LETTER U WITH DIAERESIS
                // U+00FB: "û" LATIN SMALL LETTER U WITH CIRCUMFLEX
                // U+00F9: "ù" LATIN SMALL LETTER U WITH GRAVE
                // U+016B: "ū" LATIN SMALL LETTER U WITH MACRON
                .setMoreKeysOf("u", "\u00FA", "\u00FC", "\u00FB", "\u00F9", "\u016B")
                // U+00ED: "í" LATIN SMALL LETTER I WITH ACUTE
                // U+00EF: "ï" LATIN SMALL LETTER I WITH DIAERESIS
                .setMoreKeysOf("i", "\u00ED", "\u00EF")
                // U+00DF: "ß" LATIN SMALL LETTER SHARP S
                // U+015B: "ś" LATIN SMALL LETTER S WITH ACUTE
                // U+0161: "š" LATIN SMALL LETTER S WITH CARON
                .setMoreKeysOf("s", "\u00DF", "\u015B", "\u0161")
                // U+00F0: "ð" LATIN SMALL LETTER ETH
                .setMoreKeysOf("d", "\u00F0")
                // U+0142: "ł" LATIN SMALL LETTER L WITH STROKE
                .setMoreKeysOf("l", "\u0142")
                // U+00F1: "ñ" LATIN SMALL LETTER N WITH TILDE
                // U+0144: "ń" LATIN SMALL LETTER N WITH ACUTE
                .setMoreKeysOf("n", "\u00F1", "\u0144");
    }
}
