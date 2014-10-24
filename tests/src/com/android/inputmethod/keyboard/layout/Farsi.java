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

import com.android.inputmethod.keyboard.layout.Symbols.RtlSymbols;
import com.android.inputmethod.keyboard.layout.SymbolsShifted.RtlSymbolsShifted;
import com.android.inputmethod.keyboard.layout.customizer.LayoutCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;
import com.android.inputmethod.latin.common.Constants;

import java.util.Locale;

public final class Farsi extends LayoutBase {
    private static final String LAYOUT_NAME = "farsi";

    public Farsi(final Locale locale) {
        super(new FarsiCustomizer(locale), FarsiSymbols.class, FarsiSymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    private static class FarsiCustomizer extends LayoutCustomizer {
        FarsiCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey getAlphabetKey() { return FARSI_ALPHABET_KEY; }

        @Override
        public ExpectedKey getSymbolsKey() { return FARSI_SYMBOLS_KEY; }

        @Override
        public ExpectedKey getBackToSymbolsKey() { return FARSI_BACK_TO_SYMBOLS_KEY; }

        @Override
        public ExpectedKey getCurrencyKey() { return CURRENCY_RIAL; }

        @Override
        public ExpectedKey[] getOtherCurrencyKeys() {
            return SymbolsShifted.CURRENCIES_OTHER_GENERIC;
        }

        @Override
        public ExpectedKey[] getLeftShiftKeys(final boolean isPhone) {
            return EMPTY_KEYS;
        }

        @Override
        public ExpectedKey[] getRightShiftKeys(final boolean isPhone) {
            return EMPTY_KEYS;
        }

        @Override
        public ExpectedKey[] getKeysLeftToSpacebar(final boolean isPhone) {
            if (isPhone) {
                // U+060C: "،" ARABIC COMMA
                return joinKeys(key("\u060C", SETTINGS_KEY));
            }
            // U+060C: "،" ARABIC COMMA
            // U+061F: "؟" ARABIC QUESTION MARK
            // U+061B: "؛" ARABIC SEMICOLON
            return joinKeys(key("\u060C", joinMoreKeys(
                    ":", "!", "\u061F", "\u061B", "-", RtlSymbols.DOUBLE_ANGLE_QUOTES_LR_RTL,
                    SETTINGS_KEY)));
        }

        @Override
        public ExpectedKey[] getKeysRightToSpacebar(final boolean isPhone) {
            if (isPhone) {
                return super.getKeysRightToSpacebar(isPhone);
            }
            return joinKeys(key(".", getPunctuationMoreKeys(isPhone)));
        }

        @Override
        public ExpectedKey[] getSpaceKeys(final boolean isPhone) {
            return joinKeys(LANGUAGE_SWITCH_KEY, SPACE_KEY, key(ZWNJ_KEY, ZWJ_KEY));
        }

        @Override
        public ExpectedKey[] getPunctuationMoreKeys(final boolean isPhone) {
            return FARSI_DIACRITICS;
        }

        // U+0627: "ا" ARABIC LETTER ALEF
        // U+200C: ZERO WIDTH NON-JOINER
        // U+0628: "ب" ARABIC LETTER BEH
        // U+067E: "پ" ARABIC LETTER PEH
        private static final ExpectedKey FARSI_ALPHABET_KEY = key(
                "\u0627\u200C\u0628\u200C\u067E", Constants.CODE_SWITCH_ALPHA_SYMBOL);
        // U+06F3: "۳" EXTENDED ARABIC-INDIC DIGIT THREE
        // U+06F2: "۲" EXTENDED ARABIC-INDIC DIGIT TWO
        // U+06F1: "۱" EXTENDED ARABIC-INDIC DIGIT ONE
        // U+061F: "؟" ARABIC QUESTION MARK
        private static final ExpectedKey FARSI_SYMBOLS_KEY = key(
                "\u06F3\u06F2\u06F1\u061F", Constants.CODE_SWITCH_ALPHA_SYMBOL);
        private static final ExpectedKey FARSI_BACK_TO_SYMBOLS_KEY = key(
                "\u06F3\u06F2\u06F1\u061F", Constants.CODE_SHIFT);
        // U+FDFC: "﷼" RIAL SIGN
        private static final ExpectedKey CURRENCY_RIAL = key("\uFDFC",
                Symbols.CURRENCY_GENERIC_MORE_KEYS);
        private static final ExpectedKey[] FARSI_DIACRITICS = {
                // U+0655: "ٕ" ARABIC HAMZA BELOW
                // U+0652: "ْ" ARABIC SUKUN
                // U+0651: "ّ" ARABIC SHADDA
                // U+064C: "ٌ" ARABIC DAMMATAN
                // U+064D: "ٍ" ARABIC KASRATAN
                // U+064B: "ً" ARABIC FATHATAN
                // U+0654: "ٔ" ARABIC HAMZA ABOVE
                // U+0656: "ٖ" ARABIC SUBSCRIPT ALEF
                // U+0670: "ٰ" ARABIC LETTER SUPERSCRIPT ALEF
                // U+0653: "ٓ" ARABIC MADDAH ABOVE
                // U+064F: "ُ" ARABIC DAMMA
                // U+0650: "ِ" ARABIC KASRA
                // U+064E: "َ" ARABIC FATHA
                // U+0640: "ـ" ARABIC TATWEEL
                moreKey(" \u0655", "\u0655"), moreKey(" \u0652", "\u0652"),
                moreKey(" \u0651", "\u0651"), moreKey(" \u064C", "\u064C"),
                moreKey(" \u064D", "\u064D"), moreKey(" \u064B", "\u064B"),
                moreKey(" \u0654", "\u0654"), moreKey(" \u0656", "\u0656"),
                moreKey(" \u0670", "\u0670"), moreKey(" \u0653", "\u0653"),
                moreKey(" \u064F", "\u064F"), moreKey(" \u0650", "\u0650"),
                moreKey(" \u064E", "\u064E"), moreKey("\u0640\u0640\u0640", "\u0640")
        };
    }

    @Override
    ExpectedKey[][] getCommonAlphabetLayout(final boolean isPhone) {
        if (isPhone) {
            return ALPHABET_COMMON;
        }
        final ExpectedKeyboardBuilder builder = new ExpectedKeyboardBuilder(ALPHABET_COMMON);
        // U+0622: "آ" ARABIC LETTER ALEF WITH MADDA ABOVE
        builder.insertKeysAtRow(3, 10, "\u0622");
        return builder.build();
    }

    @Override
    ExpectedKey[][] getCommonAlphabetShiftLayout(final boolean isPhone, final int elementId) {
        return null;
    }

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+0636: "ض" ARABIC LETTER DAD
                    // U+06F1: "۱" EXTENDED ARABIC-INDIC DIGIT ONE
                    key("\u0636", joinMoreKeys("\u06F1", "1")),
                    // U+0635: "ص" ARABIC LETTER SAD
                    // U+06F2: "۲" EXTENDED ARABIC-INDIC DIGIT TWO
                    key("\u0635", joinMoreKeys("\u06F2", "2")),
                    // U+062B: "ث" ARABIC LETTER THEH
                    // U+06F3: "۳" EXTENDED ARABIC-INDIC DIGIT THREE
                    key("\u062B", joinMoreKeys("\u06F3", "3")),
                    // U+0642: "ق" ARABIC LETTER QAF
                    // U+06F4: "۴" EXTENDED ARABIC-INDIC DIGIT FOUR
                    key("\u0642", joinMoreKeys("\u06F4", "4")),
                    // U+0641: "ف" ARABIC LETTER FEH
                    // U+06F5: "۵" EXTENDED ARABIC-INDIC DIGIT FIVE
                    key("\u0641", joinMoreKeys("\u06F5", "5")),
                    // U+063A: "غ" ARABIC LETTER GHAIN
                    // U+06F6: "۶" EXTENDED ARABIC-INDIC DIGIT SIX
                    key("\u063A", joinMoreKeys("\u06F6", "6")),
                    // U+0639: "ع" ARABIC LETTER AIN
                    // U+06F7: "۷" EXTENDED ARABIC-INDIC DIGIT SEVEN
                    key("\u0639", joinMoreKeys("\u06F7", "7")),
                    // U+0647: "ه" ARABIC LETTER HEH
                    // U+FEEB: "ﻫ" ARABIC LETTER HEH INITIAL FORM
                    // U+0647/U+200D: ARABIC LETTER HEH + ZERO WIDTH JOINER
                    // U+0647/U+0654: ARABIC LETTER HEH + ARABIC HAMZA ABOVE
                    // U+0629: "ة" ARABIC LETTER TEH MARBUTA
                    // U+06F8: "۸" EXTENDED ARABIC-INDIC DIGIT EIGHT
                    key("\u0647", joinMoreKeys(moreKey("\uFEEB", "\u0647\u200D"), "\u0647\u0654",
                            "\u0629", "\u06F8", "8")),
                    // U+062E: "خ" ARABIC LETTER KHAH
                    // U+06F9: "۹" EXTENDED ARABIC-INDIC DIGIT NINE
                    key("\u062E", joinMoreKeys("\u06F9", "9")),
                    // U+062D: "ح" ARABIC LETTER HAH
                    // U+06F0: "۰" EXTENDED ARABIC-INDIC DIGIT ZERO
                    key("\u062D", joinMoreKeys("\u06F0", "0")),
                    // U+062C: "ج" ARABIC LETTER JEEM
                    "\u062C")
            .setKeysOfRow(2,
                    // U+0634: "ش" ARABIC LETTER SHEEN
                    // U+0633: "س" ARABIC LETTER SEEN
                    "\u0634", "\u0633",
                    // U+06CC: "ی" ARABIC LETTER FARSI YEH
                    // U+0626: "ئ" ARABIC LETTER YEH WITH HAMZA ABOVE
                    // U+064A: "ي" ARABIC LETTER YEH
                    // U+FBE8: "ﯨ" ARABIC LETTER UIGHUR KAZAKH KIRGHIZ ALEF MAKSURA INITIAL FORM
                    // U+0649: "ى" ARABIC LETTER ALEF MAKSURA
                    key("\u06CC", joinMoreKeys("\u0626", "\u064A", moreKey("\uFBE8", "\u0649"))),
                    // U+0628: "ب" ARABIC LETTER BEH
                    // U+0644: "ل" ARABIC LETTER LAM
                    "\u0628", "\u0644",
                    // U+0627: "ا" ARABIC LETTER ALEF
                    // U+0671: "ٱ" ARABIC LETTER ALEF WASLA
                    // U+0621: "ء" ARABIC LETTER HAMZA
                    // U+0622: "آ" ARABIC LETTER ALEF WITH MADDA ABOVE
                    // U+0623: "أ" ARABIC LETTER ALEF WITH HAMZA ABOVE
                    // U+0625: "إ" ARABIC LETTER ALEF WITH HAMZA BELOW
                    key("\u0627", joinMoreKeys("\u0671", "\u0621", "\u0622", "\u0623", "\u0625")),
                    // U+062A: "ت" ARABIC LETTER TEH
                    // U+0629: "ة": ARABIC LETTER TEH MARBUTA
                    key("\u062A", moreKey("\u0629")),
                    // U+0646: "ن" ARABIC LETTER NOON
                    // U+0645: "م" ARABIC LETTER MEEM
                    "\u0646", "\u0645",
                    // U+06A9: "ک" ARABIC LETTER KEHEH
                    // U+0643: "ك" ARABIC LETTER KAF
                    key("\u06A9", moreKey("\u0643")),
                    // U+06AF: "گ" ARABIC LETTER GAF
                    "\u06AF")
            .setKeysOfRow(3,
                    // U+0638: "ظ" ARABIC LETTER ZAH
                    // U+0637: "ط" ARABIC LETTER TAH
                    // U+0698: "ژ" ARABIC LETTER JEH
                    // U+0632: "ز" ARABIC LETTER ZAIN
                    // U+0631: "ر" ARABIC LETTER REH
                    // U+0630: "ذ" ARABIC LETTER THAL
                    // U+062F: "د" ARABIC LETTER DAL
                    // U+067E: "پ" ARABIC LETTER PEH
                    "\u0638", "\u0637", "\u0698", "\u0632", "\u0631", "\u0630", "\u062F", "\u067E",
                    // U+0648: "و" ARABIC LETTER WAW
                    // U+0624: "ؤ" ARABIC LETTER WAW WITH HAMZA ABOVE
                    key("\u0648", moreKey("\u0624")),
                    // U+0686: "چ" ARABIC LETTER TCHEH
                    "\u0686")
            .build();

    private static class FarsiSymbols extends RtlSymbols {
        public FarsiSymbols(final LayoutCustomizer customizer) {
            super(customizer);
        }

        @Override
        public ExpectedKey[][] getLayout(final boolean isPhone) {
            return new ExpectedKeyboardBuilder(super.getLayout(isPhone))
                    // U+06F1: "۱" EXTENDED ARABIC-INDIC DIGIT ONE
                    // U+00B9: "¹" SUPERSCRIPT ONE
                    // U+00BD: "½" VULGAR FRACTION ONE HALF
                    // U+2153: "⅓" VULGAR FRACTION ONE THIRD
                    // U+00BC: "¼" VULGAR FRACTION ONE QUARTER
                    // U+215B: "⅛" VULGAR FRACTION ONE EIGHTH
                    .replaceKeyOfLabel("1", key("\u06F1",
                            joinMoreKeys("1", "\u00B9", "\u00BD", "\u2153", "\u00BC", "\u215B")))
                    // U+06F2: "۲" EXTENDED ARABIC-INDIC DIGIT TWO
                    // U+00B2: "²" SUPERSCRIPT TWO
                    // U+2154: "⅔" VULGAR FRACTION TWO THIRDS
                    .replaceKeyOfLabel("2", key("\u06F2", joinMoreKeys("2", "\u00B2", "\u2154")))
                    // U+06F3: "۳" EXTENDED ARABIC-INDIC DIGIT THREE
                    // U+00B3: "³" SUPERSCRIPT THREE
                    // U+00BE: "¾" VULGAR FRACTION THREE QUARTERS
                    // U+215C: "⅜" VULGAR FRACTION THREE EIGHTHS
                    .replaceKeyOfLabel("3", key("\u06F3",
                            joinMoreKeys("3", "\u00B3", "\u00BE", "\u215C")))
                    // U+06F4: "۴" EXTENDED ARABIC-INDIC DIGIT FOUR
                    // U+2074: "⁴" SUPERSCRIPT FOUR
                    .replaceKeyOfLabel("4", key("\u06F4", joinMoreKeys("4", "\u2074")))
                    // U+06F5: "۵" EXTENDED ARABIC-INDIC DIGIT FIVE
                    // U+215D: "⅝" VULGAR FRACTION FIVE EIGHTHS
                    .replaceKeyOfLabel("5", key("\u06F5", joinMoreKeys("5", "\u215D")))
                    // U+06F6: "۶" EXTENDED ARABIC-INDIC DIGIT SIX
                    .replaceKeyOfLabel("6", key("\u06F6", moreKey("6")))
                    // U+06F7: "۷" EXTENDED ARABIC-INDIC DIGIT SEVEN
                    // U+215E: "⅞" VULGAR FRACTION SEVEN EIGHTHS
                    .replaceKeyOfLabel("7", key("\u06F7", joinMoreKeys("7", "\u215E")))
                    // U+06F8: "۸" EXTENDED ARABIC-INDIC DIGIT EIGHT
                    .replaceKeyOfLabel("8", key("\u06F8", moreKey("8")))
                    // U+06F9: "۹" EXTENDED ARABIC-INDIC DIGIT NINE
                    .replaceKeyOfLabel("9", key("\u06F9", moreKey("9")))
                    // U+066C: "٬" ARABIC THOUSANDS SEPARATOR
                    .replaceKeyOfLabel("@", key("\u066C", moreKey("@")))
                    // U+066B: "٫" ARABIC DECIMAL SEPARATOR
                    .replaceKeyOfLabel("#", key("\u066B", moreKey("#")))
                    // U+06F0: "۰" EXTENDED ARABIC-INDIC DIGIT ZERO
                    // U+066B: "٫" ARABIC DECIMAL SEPARATOR
                    // U+066C: "٬" ARABIC THOUSANDS SEPARATOR
                    // U+207F: "ⁿ" SUPERSCRIPT LATIN SMALL LETTER N
                    // U+2205: "∅" EMPTY SET
                    .replaceKeyOfLabel("0", key("\u06F0",
                            joinMoreKeys("0", "\u066B", "\u066C", "\u207F", "\u2205")))
                    // U+066A: "٪" ARABIC PERCENT SIGN
                    // U+2030: "‰" PER MILLE SIGN
                    .replaceKeyOfLabel("%", key("\u066A", joinMoreKeys("%", "\u2030")))
                    // U+00AB: "«" LEFT-POINTING DOUBLE ANGLE QUOTATION MARK
                    // U+2039: "‹" SINGLE LEFT-POINTING ANGLE QUOTATION MARK
                    // U+2264: "≤" LESS-THAN OR EQUAL TO
                    .replaceKeyOfLabel("\"", key("\u00AB", "\u00BB", joinMoreKeys(
                            DOUBLE_QUOTES_9LR, DOUBLE_ANGLE_QUOTES_LR_RTL)))
                    // U+00BB: "»" RIGHT-POINTING DOUBLE ANGLE QUOTATION MARK
                    // U+203A: "›" SINGLE RIGHT-POINTING ANGLE QUOTATION MARK
                    // U+2265: "≥" GREATER-THAN EQUAL TO
                    .replaceKeyOfLabel("'", key("\u00BB", "\u00AB", joinMoreKeys(
                            SINGLE_QUOTES_9LR, SINGLE_ANGLE_QUOTES_LR_RTL)))
                    // U+061B: "؛" ARABIC SEMICOLON
                    .replaceKeyOfLabel(";", key("\u061B", moreKey(";")))
                    // U+061F: "؟" ARABIC QUESTION MARK
                    // U+00BF: "¿" INVERTED QUESTION MARK
                    .replaceKeyOfLabel("?", key("\u061F", joinMoreKeys("?", "\u00BF")))
                    // U+060C: "،" ARABIC COMMA
                    .replaceKeyOfLabel(",", "\u060C")
                    // U+FD3E: "﴾" ORNATE LEFT PARENTHESIS
                    // U+FD3F: "﴿" ORNATE RIGHT PARENTHESIS
                    .replaceKeyOfLabel("(", key("(", ")",
                            moreKey("\uFD3E", "\uFD3F"), moreKey("<", ">"), moreKey("{", "}"),
                            moreKey("[", "]")))
                    // U+FD3F: "﴿" ORNATE RIGHT PARENTHESIS
                    // U+FD3E: "﴾" ORNATE LEFT PARENTHESIS
                    .replaceKeyOfLabel(")", key(")", "(",
                            moreKey("\uFD3F", "\uFD3E"), moreKey(">", "<"), moreKey("}", "{"),
                            moreKey("]", "[")))
                    // U+2605: "★" BLACK STAR
                    // U+066D: "٭" ARABIC FIVE POINTED STAR
                    .setMoreKeysOf("*", "\u2605", "\u066D")
                    .build();
        }
    }

    private static class FarsiSymbolsShifted extends RtlSymbolsShifted {
        public FarsiSymbolsShifted(final LayoutCustomizer customizer) {
            super(customizer);
        }

        @Override
        public ExpectedKey[][] getLayout(final boolean isPhone) {
            return new ExpectedKeyboardBuilder(super.getLayout(isPhone))
                    // U+2022: "•" BULLET
                    // U+266A: "♪" EIGHTH NOTE
                    .setMoreKeysOf("\u2022", "\u266A")
                    // U+060C: "،" ARABIC COMMA
                    .replaceKeyOfLabel(",", "\u060C")
                    // U+00AB: "«" LEFT-POINTING DOUBLE ANGLE QUOTATION MARK
                    // U+2039: "‹" SINGLE LEFT-POINTING ANGLE QUOTATION MARK
                    // U+2264: "≤" LESS-THAN OR EQUAL TO
                    .replaceKeyOfLabel("<", key("\u00AB", "\u00BB",
                            moreKey("\u2039", "\u203A"), moreKey("\u2264", "\u2265"),
                            moreKey("<", ">")))
                    // U+00BB: "»" RIGHT-POINTING DOUBLE ANGLE QUOTATION MARK
                    // U+203A: "›" SINGLE RIGHT-POINTING ANGLE QUOTATION MARK
                    // U+2265: "≥" GREATER-THAN EQUAL TO
                    .replaceKeyOfLabel(">", key("\u00BB", "\u00AB",
                            moreKey("\u203A", "\u2039"), moreKey("\u2265", "\u2264"),
                            moreKey(">", "<")))
                    .build();
        }
    }
}
