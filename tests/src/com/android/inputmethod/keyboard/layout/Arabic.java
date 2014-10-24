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

public final class Arabic extends LayoutBase {
    private static final String LAYOUT_NAME = "arabic";

    public Arabic(final Locale locale) {
        super(new ArabicCustomizer(locale), ArabicSymbols.class, ArabicSymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    private static class ArabicCustomizer extends LayoutCustomizer {
        ArabicCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey getAlphabetKey() { return ARABIC_ALPHABET_KEY; }

        @Override
        public ExpectedKey getSymbolsKey() { return ARABIC_SYMBOLS_KEY; }

        @Override
        public ExpectedKey getBackToSymbolsKey() { return ARABIC_BACK_TO_SYMBOLS_KEY; }

        @Override
        public ExpectedKey[] getDoubleAngleQuoteKeys() {
            return RtlSymbols.DOUBLE_ANGLE_QUOTES_LR_RTL;
        }

        @Override
        public ExpectedKey[] getSingleAngleQuoteKeys() {
            return RtlSymbols.SINGLE_ANGLE_QUOTES_LR_RTL;
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
                    ":", "!", "\u061F", "\u061B", "-", "\"", "'", SETTINGS_KEY)));
        }

        @Override
        public ExpectedKey[] getKeysRightToSpacebar(final boolean isPhone) {
            if (isPhone) {
                return super.getKeysRightToSpacebar(isPhone);
            }
            // U+060C: "،" ARABIC COMMA
            // U+061F: "؟" ARABIC QUESTION MARK
            // U+061B: "؛" ARABIC SEMICOLON
            return joinKeys(key(".", getPunctuationMoreKeys(isPhone)));
        }

        @Override
        public ExpectedKey[] getPunctuationMoreKeys(final boolean isPhone) {
            return ARABIC_DIACRITICS;
        }

        // U+0623: "أ" ARABIC LETTER ALEF WITH HAMZA ABOVE
        // U+200C: ZERO WIDTH NON-JOINER
        // U+0628: "ب" ARABIC LETTER BEH
        // U+062C: "ج" ARABIC LETTER JEEM
        private static final ExpectedKey ARABIC_ALPHABET_KEY = key(
                "\u0623\u200C\u0628\u200C\u062C", Constants.CODE_SWITCH_ALPHA_SYMBOL);
        // U+0663: "٣" ARABIC-INDIC DIGIT THREE
        // U+0662: "٢" ARABIC-INDIC DIGIT TWO
        // U+0661: "١" ARABIC-INDIC DIGIT ONE
        // U+061F: "؟" ARABIC QUESTION MARK
        private static final ExpectedKey ARABIC_SYMBOLS_KEY = key(
                "\u0663\u0662\u0661\u061F", Constants.CODE_SWITCH_ALPHA_SYMBOL);
        private static final ExpectedKey ARABIC_BACK_TO_SYMBOLS_KEY = key(
                "\u0663\u0662\u0661\u061F", Constants.CODE_SHIFT);

        private static final ExpectedKey[] ARABIC_DIACRITICS = {
                // U+0655: "ٕ" ARABIC HAMZA BELOW
                // U+0654: "ٔ" ARABIC HAMZA ABOVE
                // U+0652: "ْ" ARABIC SUKUN
                // U+064D: "ٍ" ARABIC KASRATAN
                // U+064C: "ٌ" ARABIC DAMMATAN
                // U+064B: "ً" ARABIC FATHATAN
                // U+0651: "ّ" ARABIC SHADDA
                // U+0656: "ٖ" ARABIC SUBSCRIPT ALEF
                // U+0670: "ٰ" ARABIC LETTER SUPERSCRIPT ALEF
                // U+0653: "ٓ" ARABIC MADDAH ABOVE
                // U+0650: "ِ" ARABIC KASRA
                // U+064F: "ُ" ARABIC DAMMA
                // U+064E: "َ" ARABIC FATHA
                // U+0640: "ـ" ARABIC TATWEEL
                moreKey(" \u0655", "\u0655"), moreKey(" \u0654", "\u0654"),
                moreKey(" \u0652", "\u0652"), moreKey(" \u064D", "\u064D"),
                moreKey(" \u064C", "\u064C"), moreKey(" \u064B", "\u064B"),
                moreKey(" \u0651", "\u0651"), moreKey(" \u0656", "\u0656"),
                moreKey(" \u0670", "\u0670"), moreKey(" \u0653", "\u0653"),
                moreKey(" \u0650", "\u0650"), moreKey(" \u064F", "\u064F"),
                moreKey(" \u064E", "\u064E"), moreKey("\u0640\u0640\u0640", "\u0640")
        };
    }

    @Override
    ExpectedKey[][] getCommonAlphabetLayout(final boolean isPhone) {
        if (isPhone) {
            return ALPHABET_COMMON;
        }
        final ExpectedKeyboardBuilder builder = new ExpectedKeyboardBuilder(ALPHABET_COMMON);
        // U+0626: "ئ" ARABIC LETTER YEH WITH HAMZA ABOVE
        builder.insertKeysAtRow(3, 2, "\u0626");
        return builder.build();
    }

    @Override
    ExpectedKey[][] getCommonAlphabetShiftLayout(final boolean isPhone, final int elementId) {
        return null;
    }

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+0636: "ض" ARABIC LETTER DAD
                    // U+0661: "١" ARABIC-INDIC DIGIT ONE
                    key("\u0636", joinMoreKeys("1", "\u0661")),
                    // U+0635: "ص" ARABIC LETTER SAD
                    // U+0662: "٢" ARABIC-INDIC DIGIT TWO
                    key("\u0635", joinMoreKeys("2", "\u0662")),
                    // U+062B: "ث" ARABIC LETTER THEH
                    // U+0663: "٣" ARABIC-INDIC DIGIT THREE
                    key("\u062B", joinMoreKeys("3", "\u0663")),
                    // U+0642: "ق" ARABIC LETTER QAF
                    // U+0664: "٤" ARABIC-INDIC DIGIT FOUR
                    // U+06A8: "ڨ" ARABIC LETTER QAF WITH THREE DOTS ABOVE
                    key("\u0642", joinMoreKeys("4", "\u0664", "\u06A8")),
                    // U+0641: "ف" ARABIC LETTER FEH
                    // U+0665: "٥" ARABIC-INDIC DIGIT FIVE
                    // U+06A4: "ڤ" ARABIC LETTER VEH
                    // U+06A2: "ڢ" ARABIC LETTER FEH WITH DOT MOVED BELOW
                    // U+06A5: "ڥ" ARABIC LETTER FEH WITH THREE DOTS BELOW
                    key("\u0641", joinMoreKeys("5", "\u0665", "\u06A4", "\u06A2", "\u06A5")),
                    // U+063A: "غ" ARABIC LETTER GHAIN
                    // U+0666: "٦" ARABIC-INDIC DIGIT SIX
                    key("\u063A", joinMoreKeys("6", "\u0666")),
                    // U+0639: "ع" ARABIC LETTER AIN
                    // U+0667: "٧" ARABIC-INDIC DIGIT SEVEN
                    key("\u0639", joinMoreKeys("7", "\u0667")),
                    // U+0647: "ه" ARABIC LETTER HEH
                    // U+0668: "٨" ARABIC-INDIC DIGIT EIGHT
                    // U+FEEB: "ﻫ" ARABIC LETTER HEH INITIAL FORM
                    // U+0647 U+200D: ARABIC LETTER HEH + ZERO WIDTH JOINER
                    key("\u0647", joinMoreKeys("8", "\u0668", moreKey("\uFEEB", "\u0647\u200D"))),
                    // U+062E: "خ" ARABIC LETTER KHAH
                    // U+0669: "٩" ARABIC-INDIC DIGIT NINE
                    key("\u062E", joinMoreKeys("9", "\u0669")),
                    // U+062D: "ح" ARABIC LETTER HAH
                    // U+0660: "٠" ARABIC-INDIC DIGIT ZERO
                    key("\u062D", joinMoreKeys("0", "\u0660")),
                    // U+062C: "ج" ARABIC LETTER JEEM
                    // U+0686: "چ" ARABIC LETTER TCHEH
                    key("\u062C", moreKey("\u0686")))
            .setKeysOfRow(2,
                    // U+0634: "ش" ARABIC LETTER SHEEN
                    // U+069C: "ڜ" ARABIC LETTER SEEN WITH THREE DOTS BELOW AND THREE DOTS ABOVE
                    key("\u0634", moreKey("\u069C")),
                    // U+0633: "س" ARABIC LETTER SEEN
                    "\u0633",
                    // U+064A: "ي" ARABIC LETTER YEH
                    // U+0626: "ئ" ARABIC LETTER YEH WITH HAMZA ABOVE
                    // U+0649: "ى" ARABIC LETTER ALEF MAKSURA
                    key("\u064A", joinMoreKeys("\u0626", "\u0649")),
                    // U+0628: "ب" ARABIC LETTER BEH
                    // U+067E: "پ" ARABIC LETTER PEH
                    key("\u0628", moreKey("\u067E")),
                    // U+0644: "ل" ARABIC LETTER LAM
                    // U+FEFB: "ﻻ" ARABIC LIGATURE LAM WITH ALEF ISOLATED FORM
                    // U+0627: "ا" ARABIC LETTER ALEF
                    // U+FEF7: "ﻷ" ARABIC LIGATURE LAM WITH ALEF WITH HAMZA ABOVE ISOLATED FORM
                    // U+0623: "أ" ARABIC LETTER ALEF WITH HAMZA ABOVE
                    // U+FEF9: "ﻹ" ARABIC LIGATURE LAM WITH ALEF WITH HAMZA BELOW ISOLATED FORM
                    // U+0625: "إ" ARABIC LETTER ALEF WITH HAMZA BELOW
                    // U+FEF5: "ﻵ" ARABIC LIGATURE LAM WITH ALEF WITH MADDA ABOVE ISOLATED FORM
                    // U+0622: "آ" ARABIC LETTER ALEF WITH MADDA ABOVE
                    key("\u0644",
                            moreKey("\uFEFB", "\u0644\u0627"), moreKey("\uFEF7", "\u0644\u0623"),
                            moreKey("\uFEF9", "\u0644\u0625"), moreKey("\uFEF5", "\u0644\u0622")),
                    // U+0627: "ا" ARABIC LETTER ALEF
                    // U+0622: "آ" ARABIC LETTER ALEF WITH MADDA ABOVE
                    // U+0621: "ء" ARABIC LETTER HAMZA
                    // U+0623: "أ" ARABIC LETTER ALEF WITH HAMZA ABOVE
                    // U+0625: "إ" ARABIC LETTER ALEF WITH HAMZA BELOW
                    // U+0671: "ٱ" ARABIC LETTER ALEF WASLA
                    key("\u0627", joinMoreKeys("\u0622", "\u0621", "\u0623", "\u0625", "\u0671")),
                    // U+062A: "ت" ARABIC LETTER TEH
                    // U+0646: "ن" ARABIC LETTER NOON
                    // U+0645: "م" ARABIC LETTER MEEM
                    "\u062A", "\u0646", "\u0645",
                    // U+0643: "ك" ARABIC LETTER KAF
                    // U+06AF: "گ" ARABIC LETTER GAF
                    // U+06A9: "ک" ARABIC LETTER KEHEH
                    key("\u0643", joinMoreKeys("\u06AF", "\u06A9")),
                    // U+0637: "ط" ARABIC LETTER TAH
                    "\u0637")
            .setKeysOfRow(3,
                    // U+0630: "ذ" ARABIC LETTER THAL
                    // U+0621: "ء" ARABIC LETTER HAMZA
                    // U+0624: "ؤ" ARABIC LETTER WAW WITH HAMZA ABOVE
                    // U+0631: "ر" ARABIC LETTER REH
                    "\u0630", "\u0621", "\u0624", "\u0631",
                    // U+0649: "ى" ARABIC LETTER ALEF MAKSURA
                    // U+0626: "ئ" ARABIC LETTER YEH WITH HAMZA ABOVE
                    key("\u0649", moreKey("\u0626")),
                    // U+0629: "ة" ARABIC LETTER TEH MARBUTA
                    // U+0648: "و" ARABIC LETTER WAW
                    "\u0629", "\u0648",
                    // U+0632: "ز" ARABIC LETTER ZAIN
                    // U+0698: "ژ" ARABIC LETTER JEH
                    key("\u0632", moreKey("\u0698")),
                    // U+0638: "ظ" ARABIC LETTER ZAH
                    // U+062F: "د" ARABIC LETTER DAL
                    "\u0638", "\u062F")
            .build();

    private static class ArabicSymbols extends RtlSymbols {
        public ArabicSymbols(final LayoutCustomizer customizer) {
            super(customizer);
        }

        @Override
        public ExpectedKey[][] getLayout(final boolean isPhone) {
            return new ExpectedKeyboardBuilder(super.getLayout(isPhone))
                    // U+0661: "١" ARABIC-INDIC DIGIT ONE
                    // U+00B9: "¹" SUPERSCRIPT ONE
                    // U+00BD: "½" VULGAR FRACTION ONE HALF
                    // U+2153: "⅓" VULGAR FRACTION ONE THIRD
                    // U+00BC: "¼" VULGAR FRACTION ONE QUARTER
                    // U+215B: "⅛" VULGAR FRACTION ONE EIGHTH
                    .replaceKeyOfLabel("1", key("\u0661",
                            joinMoreKeys("1", "\u00B9", "\u00BD", "\u2153", "\u00BC", "\u215B")))
                    // U+0662: "٢" ARABIC-INDIC DIGIT TWO
                    // U+00B2: "²" SUPERSCRIPT TWO
                    // U+2154: "⅔" VULGAR FRACTION TWO THIRDS
                    .replaceKeyOfLabel("2", key("\u0662", joinMoreKeys("2", "\u00B2", "\u2154")))
                    // U+0663: "٣" ARABIC-INDIC DIGIT THREE
                    // U+00B3: "³" SUPERSCRIPT THREE
                    // U+00BE: "¾" VULGAR FRACTION THREE QUARTERS
                    // U+215C: "⅜" VULGAR FRACTION THREE EIGHTHS
                    .replaceKeyOfLabel("3", key("\u0663",
                            joinMoreKeys("3", "\u00B3", "\u00BE", "\u215C")))
                    // U+0664: "٤" ARABIC-INDIC DIGIT FOUR
                    // U+2074: "⁴" SUPERSCRIPT FOUR
                    .replaceKeyOfLabel("4", key("\u0664", joinMoreKeys("4", "\u2074")))
                    // U+0665: "٥" ARABIC-INDIC DIGIT FIVE
                    // U+215D: "⅝" VULGAR FRACTION FIVE EIGHTHS
                    .replaceKeyOfLabel("5", key("\u0665", joinMoreKeys("5", "\u215D")))
                    // U+0666: "٦" ARABIC-INDIC DIGIT SIX
                    .replaceKeyOfLabel("6", key("\u0666", moreKey("6")))
                    // U+0667: "٧" ARABIC-INDIC DIGIT SEVEN
                    // U+215E: "⅞" VULGAR FRACTION SEVEN EIGHTHS
                    .replaceKeyOfLabel("7", key("\u0667", joinMoreKeys("7", "\u215E")))
                    // U+0668: "٨" ARABIC-INDIC DIGIT EIGHT
                    .replaceKeyOfLabel("8", key("\u0668", moreKey("8")))
                    // U+0669: "٩" ARABIC-INDIC DIGIT NINE
                    .replaceKeyOfLabel("9", key("\u0669", moreKey("9")))
                    // U+0660: "٠" ARABIC-INDIC DIGIT ZERO
                    // U+066B: "٫" ARABIC DECIMAL SEPARATOR
                    // U+066C: "٬" ARABIC THOUSANDS SEPARATOR
                    // U+207F: "ⁿ" SUPERSCRIPT LATIN SMALL LETTER N
                    // U+2205: "∅" EMPTY SET
                    .replaceKeyOfLabel("0", key("\u0660",
                            joinMoreKeys("0", "\u066B", "\u066C", "\u207F", "\u2205")))
                    // U+066A: "٪" ARABIC PERCENT SIGN
                    // U+2030: "‰" PER MILLE SIGN
                    .replaceKeyOfLabel("%", key("\u066A", joinMoreKeys("%", "\u2030")))
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

    private static class ArabicSymbolsShifted extends RtlSymbolsShifted {
        public ArabicSymbolsShifted(final LayoutCustomizer customizer) {
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
                    .build();
        }
    }
}
