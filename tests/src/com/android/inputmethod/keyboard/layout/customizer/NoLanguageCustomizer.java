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

public class NoLanguageCustomizer extends LayoutCustomizer {
    public NoLanguageCustomizer(final Locale locale) { super(locale); }

    @Override
    public ExpectedKeyboardBuilder setAccentedLetters(final ExpectedKeyboardBuilder builder) {
        return builder
                // U+0175: "ŵ" LATIN SMALL LETTER W WITH CIRCUMFLEX
                .setMoreKeysOf("w", "\u0175")
                // U+00E8: "è" LATIN SMALL LETTER E WITH GRAVE
                // U+00E9: "é" LATIN SMALL LETTER E WITH ACUTE
                // U+00EA: "ê" LATIN SMALL LETTER E WITH CIRCUMFLEX
                // U+00EB: "ë" LATIN SMALL LETTER E WITH DIAERESIS
                // U+0113: "ē" LATIN SMALL LETTER E WITH MACRON
                // U+0115: "ĕ" LATIN SMALL LETTER E WITH BREVE
                // U+0117: "ė" LATIN SMALL LETTER E WITH DOT ABOVE
                // U+0119: "ę" LATIN SMALL LETTER E WITH OGONEK
                // U+011B: "ě" LATIN SMALL LETTER E WITH CARON
                .setMoreKeysOf("e",
                        "\u00E8", "\u00E9", "\u00EA", "\u00EB", "\u0113", "\u0115", "\u0117",
                        "\u0119", "\u011B")
                // U+0155: "ŕ" LATIN SMALL LETTER R WITH ACUTE
                // U+0157: "ŗ" LATIN SMALL LETTER R WITH CEDILLA
                // U+0159: "ř" LATIN SMALL LETTER R WITH CARON
                .setMoreKeysOf("r", "\u0155", "\u0157", "\u0159")
                // U+00FE: "þ" LATIN SMALL LETTER THORN
                // U+0163: "ţ" LATIN SMALL LETTER T WITH CEDILLA
                // U+0165: "ť" LATIN SMALL LETTER T WITH CARON
                // U+0167: "ŧ" LATIN SMALL LETTER T WITH STROKE
                .setMoreKeysOf("t", "\u00FE", "\u0163", "\u0165", "\u0167")
                // U+00FD: "ý" LATIN SMALL LETTER Y WITH ACUTE
                // U+0177: "ŷ" LATIN SMALL LETTER Y WITH CIRCUMFLEX
                // U+00FF: "ÿ" LATIN SMALL LETTER Y WITH DIAERESIS
                // U+0133: "ĳ" LATIN SMALL LIGATURE IJ
                .setMoreKeysOf("y", "\u00FD", "\u0177", "\u00FF", "\u0133")
                // U+00F9: "ù" LATIN SMALL LETTER U WITH GRAVE
                // U+00FA: "ú" LATIN SMALL LETTER U WITH ACUTE
                // U+00FB: "û" LATIN SMALL LETTER U WITH CIRCUMFLEX
                // U+00FC: "ü" LATIN SMALL LETTER U WITH DIAERESIS
                // U+0169: "ũ" LATIN SMALL LETTER U WITH TILDE
                // U+016B: "ū" LATIN SMALL LETTER U WITH MACRON
                // U+016D: "ŭ" LATIN SMALL LETTER U WITH BREVE
                // U+016F: "ů" LATIN SMALL LETTER U WITH RING ABOVE
                // U+0171: "ű" LATIN SMALL LETTER U WITH DOUBLE ACUTE
                // U+0173: "ų" LATIN SMALL LETTER U WITH OGONEK
                .setMoreKeysOf("u",
                        "\u00F9", "\u00FA", "\u00FB", "\u00FC", "\u0169", "\u016B", "\u016D",
                        "\u016F", "\u0171", "\u0173")
                // U+00EC: "ì" LATIN SMALL LETTER I WITH GRAVE
                // U+00ED: "í" LATIN SMALL LETTER I WITH ACUTE
                // U+00EE: "î" LATIN SMALL LETTER I WITH CIRCUMFLEX
                // U+00EF: "ï" LATIN SMALL LETTER I WITH DIAERESIS
                // U+0129: "ĩ" LATIN SMALL LETTER I WITH TILDE
                // U+012B: "ī" LATIN SMALL LETTER I WITH MACRON
                // U+012D: "ĭ" LATIN SMALL LETTER I WITH BREVE
                // U+012F: "į" LATIN SMALL LETTER I WITH OGONEK
                // U+0131: "ı" LATIN SMALL LETTER DOTLESS I
                // U+0133: "ĳ" LATIN SMALL LIGATURE IJ
                .setMoreKeysOf("i",
                        "\u00EC", "\u00ED", "\u00EE", "\u00EF", "\u0129", "\u012B", "\u012D",
                        "\u012F", "\u0131", "\u0133")
                // U+00F2: "ò" LATIN SMALL LETTER O WITH GRAVE
                // U+00F3: "ó" LATIN SMALL LETTER O WITH ACUTE
                // U+00F4: "ô" LATIN SMALL LETTER O WITH CIRCUMFLEX
                // U+00F5: "õ" LATIN SMALL LETTER O WITH TILDE
                // U+00F6: "ö" LATIN SMALL LETTER O WITH DIAERESIS
                // U+00F8: "ø" LATIN SMALL LETTER O WITH STROKE
                // U+014D: "ō" LATIN SMALL LETTER O WITH MACRON
                // U+014F: "ŏ" LATIN SMALL LETTER O WITH BREVE
                // U+0151: "ő" LATIN SMALL LETTER O WITH DOUBLE ACUTE
                // U+0153: "œ" LATIN SMALL LIGATURE OE
                // U+00BA: "º" MASCULINE ORDINAL INDICATOR
                .setMoreKeysOf("o",
                        "\u00F2", "\u00F3", "\u00F4", "\u00F5", "\u00F6", "\u00F8", "\u014D",
                        "\u014F", "\u0151", "\u0153", "\u00BA")
                // U+00E0: "à" LATIN SMALL LETTER A WITH GRAVE
                // U+00E1: "á" LATIN SMALL LETTER A WITH ACUTE
                // U+00E2: "â" LATIN SMALL LETTER A WITH CIRCUMFLEX
                // U+00E3: "ã" LATIN SMALL LETTER A WITH TILDE
                // U+00E4: "ä" LATIN SMALL LETTER A WITH DIAERESIS
                // U+00E5: "å" LATIN SMALL LETTER A WITH RING ABOVE
                // U+00E6: "æ" LATIN SMALL LETTER AE
                // U+0101: "ā" LATIN SMALL LETTER A WITH MACRON
                // U+0103: "ă" LATIN SMALL LETTER A WITH BREVE
                // U+0105: "ą" LATIN SMALL LETTER A WITH OGONEK
                // U+00AA: "ª" FEMININE ORDINAL INDICATOR
                .setMoreKeysOf("a",
                        "\u00E0", "\u00E1", "\u00E2", "\u00E3", "\u00E4", "\u00E5", "\u00E6",
                        "\u0101", "\u0103", "\u0105", "\u00AA")
                // U+00DF: "ß" LATIN SMALL LETTER SHARP S
                // U+015B: "ś" LATIN SMALL LETTER S WITH ACUTE
                // U+015D: "ŝ" LATIN SMALL LETTER S WITH CIRCUMFLEX
                // U+015F: "ş" LATIN SMALL LETTER S WITH CEDILLA
                // U+0161: "š" LATIN SMALL LETTER S WITH CARON
                // U+017F: "ſ" LATIN SMALL LETTER LONG S
                .setMoreKeysOf("s", "\u00DF", "\u015B", "\u015D", "\u015F", "\u0161", "\u017F")
                // U+010F: "ď" LATIN SMALL LETTER D WITH CARON
                // U+0111: "đ" LATIN SMALL LETTER D WITH STROKE
                // U+00F0: "ð" LATIN SMALL LETTER ETH
                .setMoreKeysOf("d", "\u010F", "\u0111", "\u00F0")
                // U+011D: "ĝ" LATIN SMALL LETTER G WITH CIRCUMFLEX
                // U+011F: "ğ" LATIN SMALL LETTER G WITH BREVE
                // U+0121: "ġ" LATIN SMALL LETTER G WITH DOT ABOVE
                // U+0123: "ģ" LATIN SMALL LETTER G WITH CEDILLA
                .setMoreKeysOf("g", "\u011D", "\u011F", "\u0121", "\u0123")
                // U+0125: "ĥ" LATIN SMALL LETTER H WITH CIRCUMFLEX
                .setMoreKeysOf("h", "\u0125")
                // U+0135: "ĵ" LATIN SMALL LETTER J WITH CIRCUMFLEX
                .setMoreKeysOf("j", "\u0135")
                // U+0137: "ķ" LATIN SMALL LETTER K WITH CEDILLA
                // U+0138: "ĸ" LATIN SMALL LETTER KRA
                .setMoreKeysOf("k", "\u0137", "\u0138")
                // U+013A: "ĺ" LATIN SMALL LETTER L WITH ACUTE
                // U+013C: "ļ" LATIN SMALL LETTER L WITH CEDILLA
                // U+013E: "ľ" LATIN SMALL LETTER L WITH CARON
                // U+0140: "ŀ" LATIN SMALL LETTER L WITH MIDDLE DOT
                // U+0142: "ł" LATIN SMALL LETTER L WITH STROKE
                .setMoreKeysOf("l", "\u013A", "\u013C", "\u013E", "\u0140", "\u0142")
                // U+017A: "ź" LATIN SMALL LETTER Z WITH ACUTE
                // U+017C: "ż" LATIN SMALL LETTER Z WITH DOT ABOVE
                // U+017E: "ž" LATIN SMALL LETTER Z WITH CARON
                .setMoreKeysOf("z", "\u017A", "\u017C", "\u017E")
                // U+00E7: "ç" LATIN SMALL LETTER C WITH CEDILLA
                // U+0107: "ć" LATIN SMALL LETTER C WITH ACUTE
                // U+0109: "ĉ" LATIN SMALL LETTER C WITH CIRCUMFLEX
                // U+010B: "ċ" LATIN SMALL LETTER C WITH DOT ABOVE
                // U+010D: "č" LATIN SMALL LETTER C WITH CARON
                .setMoreKeysOf("c", "\u00E7", "\u0107", "\u0109", "\u010B", "\u010D")
                // U+00F1: "ñ" LATIN SMALL LETTER N WITH TILDE
                // U+0144: "ń" LATIN SMALL LETTER N WITH ACUTE
                // U+0146: "ņ" LATIN SMALL LETTER N WITH CEDILLA
                // U+0148: "ň" LATIN SMALL LETTER N WITH CARON
                // U+0149: "ŉ" LATIN SMALL LETTER N PRECEDED BY APOSTROPHE
                // U+014B: "ŋ" LATIN SMALL LETTER ENG
                .setMoreKeysOf("n", "\u00F1", "\u0144", "\u0146", "\u0148", "\u0149", "\u014B");
    }
}
