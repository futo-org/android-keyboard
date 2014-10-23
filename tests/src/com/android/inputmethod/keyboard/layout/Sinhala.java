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

package com.android.inputmethod.keyboard.layout;

import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.layout.customizer.LayoutCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;
import com.android.inputmethod.latin.common.Constants;

import java.util.Locale;

/**
 * The Sinhala keyboard.
 */
public final class Sinhala extends LayoutBase {
    private static final String LAYOUT_NAME = "sinhala";

    public Sinhala(final Locale locale) {
        super(new SinhalaCustomizer(locale), Symbols.class, SymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    private static class SinhalaCustomizer extends LayoutCustomizer {
        SinhalaCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey getAlphabetKey() { return SINHALA_ALPHABET_KEY; }

        @Override
        public ExpectedKey getCurrencyKey() { return CURRENCY_RUPEE; }

        @Override
        public ExpectedKey[] getOtherCurrencyKeys() {
            return SymbolsShifted.CURRENCIES_OTHER_GENERIC;
        }

        @Override
        public ExpectedKey[] getRightShiftKeys(final boolean isPhone) {
            return isPhone ? EMPTY_KEYS : EXCLAMATION_AND_QUESTION_MARKS;
        }

        // U+0D85: "අ" SINHALA LETTER AYANNA
        // U+0D86: "ආ" SINHALA LETTER AAYANNA
        private static final ExpectedKey SINHALA_ALPHABET_KEY = key(
                "\u0D85,\u0D86", Constants.CODE_SWITCH_ALPHA_SYMBOL);

        // U+0DBB/U+0DD4: "රු" SINHALA LETTER RAYANNA/SINHALA VOWEL SIGN KETTI PAA-PILLA
        private static final ExpectedKey CURRENCY_RUPEE = key("\u0DBB\u0DD4",
                Symbols.CURRENCY_GENERIC_MORE_KEYS);
    }

    @Override
    ExpectedKey[][] getCommonAlphabetLayout(boolean isPhone) { return ALPHABET_COMMON; }

    @Override
    ExpectedKey[][] getCommonAlphabetShiftLayout(boolean isPhone, final int elementId) {
        if (elementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED) {
            return ALPHABET_COMMON;
        }
        return ALPHABET_SHIFTED_COMMON;
    }

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+0DD4: "ු" SINHALA VOWEL SIGN KETTI PAA-PILLA
                    key("\u0DD4", moreKey("1")),
                    // U+0D85: "අ" SINHALA LETTER AYANNA
                    key("\u0D85", moreKey("2")),
                    // U+0DD0: "ැ" SINHALA VOWEL SIGN KETTI AEDA-PILLA
                    key("\u0DD0", moreKey("3")),
                    // U+0DBB: "ර" SINHALA LETTER RAYANNA
                    key("\u0DBB", moreKey("4")),
                    // U+0D91: "එ" SINHALA LETTER EYANNA
                    key("\u0D91", moreKey("5")),
                    // U+0DC4: "හ" SINHALA LETTER HAYANNA
                    key("\u0DC4", moreKey("6")),
                    // U+0DB8: "ම" SINHALA LETTER MAYANNA
                    key("\u0DB8", moreKey("7")),
                    // U+0DC3: "ස" SINHALA LETTER DANTAJA SAYANNA
                    key("\u0DC3", moreKey("8")),
                    // U+0DAF: "ද" SINHALA LETTER ALPAPRAANA DAYANNA
                    // U+0DB3: "ඳ" SINHALA LETTER SANYAKA DAYANNA
                    key("\u0DAF", joinMoreKeys("9", "\u0DB3")),
                    // U+0DA0: "ච" SINHALA LETTER ALPAPRAANA CAYANNA
                    key("\u0DA0", moreKey("0")),
                    // U+0DA4: "ඤ" SINHALA LETTER TAALUJA NAASIKYAYA
                    // U+0DF4: "෴" SINHALA PUNCTUATION KUNDDALIYA
                    key("\u0DA4", moreKey("\u0DF4")))
            .setKeysOfRow(2,
                    // U+0DCA: "්" SINHALA SIGN AL-LAKUNA
                    // U+0DD2: "ි" SINHALA VOWEL SIGN KETTI IS-PILLA
                    // U+0DCF: "ා" SINHALA VOWEL SIGN AELA-PILLA
                    // U+0DD9: "ෙ" SINHALA VOWEL SIGN KOMBUVA
                    // U+0DA7: "ට" SINHALA LETTER ALPAPRAANA TTAYANNA
                    // U+0DBA: "ය" SINHALA LETTER YAYANNA
                    // U+0DC0: "ව" SINHALA LETTER VAYANNA
                    // U+0DB1: "න" SINHALA LETTER DANTAJA NAYANNA
                    // U+0D9A: "ක" SINHALA LETTER ALPAPRAANA KAYANNA
                    // U+0DAD: "ත" SINHALA LETTER ALPAPRAANA TAYANNA
                    // U+0D8F: "ඏ" SINHALA LETTER ILUYANNA
                    "\u0DCA", "\u0DD2", "\u0DCF", "\u0DD9", "\u0DA7", "\u0DBA", "\u0DC0", "\u0DB1",
                    "\u0D9A", "\u0DAD", "\u0D8F")
            .setKeysOfRow(3,
                    // U+0D82: "ං" SINHALA SIGN ANUSVARAYA
                    // U+0D83: "ඃ" SINHALA SIGN VISARGAYA
                    key("\u0D82", moreKey("\u0D83")),
                    // U+0DA2: "ජ" SINHALA LETTER ALPAPRAANA JAYANNA
                    // U+0DA6: "ඦ" SINHALA LETTER SANYAKA JAYANNA
                    key("\u0DA2", moreKey("\u0DA6")),
                    // U+0DA9: "ඩ" SINHALA LETTER ALPAPRAANA DDAYANNA
                    // U+0DAC: "ඬ" SINHALA LETTER SANYAKA DDAYANNA
                    key("\u0DA9", moreKey("\u0DAC")),
                    // U+0D89: "ඉ" SINHALA LETTER IYANNA
                    // U+0DB6: "බ" SINHALA LETTER ALPAPRAANA BAYANNA
                    // U+0DB4: "ප" SINHALA LETTER ALPAPRAANA PAYANNA
                    // U+0DBD: "ල" SINHALA LETTER DANTAJA LAYANNA
                    "\u0D89", "\u0DB6", "\u0DB4", "\u0DBD",
                    // U+0D9C: "ග" SINHALA LETTER ALPAPRAANA GAYANNA
                    // U+0D9F: "ඟ" SINHALA LETTER SANYAKA GAYANNA
                    key("\u0D9C", moreKey("\u0D9F")),
                    // U+0DF3: "ෳ" SINHALA VOWEL SIGN DIGA GAYANUKITTA
                    "\u0DF3")
            .build();

    private static final ExpectedKey[][] ALPHABET_SHIFTED_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+0DD6: "ූ" SINHALA VOWEL SIGN DIGA PAA-PILLA
                    // U+0D8B: "උ" SINHALA LETTER UYANNA
                    // U+0DD1: "ෑ" SINHALA VOWEL SIGN DIGA AEDA-PILLA
                    // U+0D8D: "ඍ" SINHALA LETTER IRUYANNA
                    // U+0D94: "ඔ" SINHALA LETTER OYANNA
                    // U+0DC1: "ශ" SINHALA LETTER TAALUJA SAYANNA
                    // U+0DB9: "ඹ" SINHALA LETTER AMBA BAYANNA
                    // U+0DC2: "ෂ" SINHALA LETTER MUURDHAJA SAYANNA
                    // U+0DB0: "ධ" SINHALA LETTER MAHAAPRAANA DAYANNA
                    // U+0DA1: "ඡ" SINHALA LETTER MAHAAPRAANA CAYANNA
                    "\u0DD6", "\u0D8B", "\u0DD1", "\u0D8D", "\u0D94", "\u0DC1", "\u0DB9", "\u0DC2",
                    "\u0DB0", "\u0DA1",
                    // U+0DA5: "ඥ" SINHALA LETTER TAALUJA SANYOOGA NAAKSIKYAYA
                    // U+0DF4: "෴" SINHALA PUNCTUATION KUNDDALIYA
                    key("\u0DA5", moreKey("\u0DF4")))
            .setKeysOfRow(2,
                    // U+0DDF: "ෟ" SINHALA VOWEL SIGN GAYANUKITTA
                    // U+0DD3: "ී" SINHALA VOWEL SIGN DIGA IS-PILLA
                    // U+0DD8: "ෘ" SINHALA VOWEL SIGN GAETTA-PILLA
                    // U+0DC6: "ෆ" SINHALA LETTER FAYANNA
                    // U+0DA8: "ඨ" SINHALA LETTER MAHAAPRAANA TTAYANNA
                    // U+0DCA/U+200D/U+0DBA:
                    //     "්‍ය" SINHALA SIGN AL-LAKUNA/ZERO WIDTH JOINER/SINHALA LETTER YAYANNA
                    // U+0DC5/U+0DD4:
                    //     "ළු" SINHALA LETTER MUURDHAJA LAYANNA/SINHALA VOWEL SIGN KETTI PAA-PILLA
                    // U+0DAB: "ණ" SINHALA LETTER MUURDHAJA NAYANNA
                    // U+0D9B: "ඛ" SINHALA LETTER MAHAAPRAANA KAYANNA
                    // U+0DAE: "ථ" SINHALA LETTER MAHAAPRAANA TAYANNA
                    // U+0DCA/U+200D/U+0DBB:
                    //     "්‍ර" SINHALA SIGN AL-LAKUNA/ZERO WIDTH JOINER/SINHALA LETTER RAYANNA
                    "\u0DDF", "\u0DD3", "\u0DD8", "\u0DC6", "\u0DA8", "\u0DCA\u200D\u0DBA",
                    "\u0DC5\u0DD4", "\u0DAB", "\u0D9B", "\u0DAE", "\u0DCA\u200D\u0DBB")
            .setKeysOfRow(3,
                    // U+0D9E: "ඞ" SINHALA LETTER KANTAJA NAASIKYAYA
                    // U+0DA3: "ඣ" SINHALA LETTER MAHAAPRAANA JAYANNA
                    // U+0DAA: "ඪ" SINHALA LETTER MAHAAPRAANA DDAYANNA
                    // U+0D8A: "ඊ" SINHALA LETTER IIYANNA
                    // U+0DB7: "භ" SINHALA LETTER MAHAAPRAANA BAYANNA
                    // U+0DB5: "ඵ" SINHALA LETTER MAHAAPRAANA PAYANNA
                    // U+0DC5: "ළ" SINHALA LETTER MUURDHAJA LAYANNA
                    // U+0D9D: "ඝ" SINHALA LETTER MAHAAPRAANA GAYANNA
                    // U+0DBB/U+0DCA/U+200D:
                    //     "ර්‍" SINHALA LETTER RAYANNA/SINHALA SIGN AL-LAKUNA/ZERO WIDTH JOINER
                    "\u0d9E", "\u0DA3", "\u0DAA", "\u0D8A", "\u0DB7", "\u0DB5", "\u0DC5", "\u0D9D",
                    "\u0DBB\u0DCA\u200D")
            .build();
}
