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

import com.android.inputmethod.keyboard.layout.customizer.LayoutCustomizer;
import com.android.inputmethod.keyboard.layout.expected.AbstractLayoutBase;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

/**
 * The symbols keyboard layout.
 */
public class Symbols extends AbstractLayoutBase {
    private final LayoutCustomizer mCustomizer;

    public Symbols(final LayoutCustomizer customizer) {
        mCustomizer = customizer;
    }

    public ExpectedKey[][] getLayout(final boolean isPhone) {
        final ExpectedKeyboardBuilder builder = new ExpectedKeyboardBuilder(SYMBOLS_COMMON);
        final LayoutCustomizer customizer = mCustomizer;
        builder.replaceKeyOfLabel(CURRENCY, customizer.getCurrencyKey());
        builder.replaceKeyOfLabel(DOUBLE_QUOTE, key("\"", joinMoreKeys(
                customizer.getDoubleQuoteMoreKeys(), customizer.getDoubleAngleQuoteKeys())));
        builder.replaceKeyOfLabel(SINGLE_QUOTE, key("'", joinMoreKeys(
                customizer.getSingleQuoteMoreKeys(), customizer.getSingleAngleQuoteKeys())));
        if (isPhone) {
            builder.addKeysOnTheLeftOfRow(3, customizer.getSymbolsShiftKey(isPhone))
                    .addKeysOnTheRightOfRow(3, DELETE_KEY)
                    .addKeysOnTheLeftOfRow(4, customizer.getAlphabetKey())
                    .addKeysOnTheRightOfRow(4, customizer.getEnterKey(isPhone));
        } else {
            // Tablet symbols keyboard has extra two keys at the left edge of the 3rd row.
            builder.addKeysOnTheLeftOfRow(3, (Object[])joinKeys("\\", "="));
            builder.addKeysOnTheRightOfRow(1, DELETE_KEY)
                    .addKeysOnTheRightOfRow(2, customizer.getEnterKey(isPhone))
                    .addKeysOnTheLeftOfRow(3, customizer.getSymbolsShiftKey(isPhone))
                    .addKeysOnTheRightOfRow(3, customizer.getSymbolsShiftKey(isPhone))
                    .addKeysOnTheLeftOfRow(4, customizer.getAlphabetKey())
                    .addKeysOnTheRightOfRow(4, customizer.getEmojiKey(isPhone));
        }
        return builder.build();
    }

    // Variations of the "currency" key on the 2nd row.
    public static final String CURRENCY = "CURRENCY";
    // U+00A2: "¢" CENT SIGN
    // U+00A3: "£" POUND SIGN
    // U+00A5: "¥" YEN SIGN
    // U+20AC: "€" EURO SIGN
    // U+20B1: "₱" PESO SIGN
    public static final ExpectedKey DOLLAR_SIGN = key("$");
    public static final ExpectedKey CENT_SIGN = key("\u00A2");
    public static final ExpectedKey POUND_SIGN = key("\u00A3");
    public static final ExpectedKey YEN_SIGN = key("\u00A5");
    public static final ExpectedKey EURO_SIGN = key("\u20AC");
    public static final ExpectedKey PESO_SIGN = key("\u20B1");
    public static final ExpectedKey CURRENCY_DOLLAR = key("$",
            CENT_SIGN, POUND_SIGN, EURO_SIGN, YEN_SIGN, PESO_SIGN);
    public static final ExpectedKey CURRENCY_EURO = key("\u20AC",
            CENT_SIGN, POUND_SIGN, DOLLAR_SIGN, YEN_SIGN, PESO_SIGN);
    public static final ExpectedKey[] CURRENCY_GENERIC_MORE_KEYS = joinMoreKeys(
            Symbols.DOLLAR_SIGN, Symbols.CENT_SIGN, Symbols.EURO_SIGN, Symbols.POUND_SIGN,
            Symbols.YEN_SIGN, Symbols.PESO_SIGN);

    // Variations of the "double quote" key's "more keys" on the 3rd row.
    public static final String DOUBLE_QUOTE = "DOUBLE_QUOTE";
    // U+201C: "“" LEFT DOUBLE QUOTATION MARK
    // U+201D: "”" RIGHT DOUBLE QUOTATION MARK
    // U+201E: "„" DOUBLE LOW-9 QUOTATION MARK
    private static final ExpectedKey DQUOTE_LEFT = key("\u201C");
    private static final ExpectedKey DQUOTE_RIGHT = key("\u201D");
    private static final ExpectedKey DQUOTE_LOW9 = key("\u201E");
    public static ExpectedKey[] DOUBLE_QUOTES_9LR = { DQUOTE_LOW9, DQUOTE_LEFT, DQUOTE_RIGHT };
    public static ExpectedKey[] DOUBLE_QUOTES_R9L = { DQUOTE_RIGHT, DQUOTE_LOW9, DQUOTE_LEFT };
    public static ExpectedKey[] DOUBLE_QUOTES_L9R = { DQUOTE_LEFT, DQUOTE_LOW9, DQUOTE_RIGHT };
    public static ExpectedKey[] DOUBLE_QUOTES_LR9 = { DQUOTE_LEFT, DQUOTE_RIGHT, DQUOTE_LOW9 };
    // U+00AB: "«" LEFT-POINTING DOUBLE ANGLE QUOTATION MARK
    // U+00BB: "»" RIGHT-POINTING DOUBLE ANGLE QUOTATION MARK
    private static final ExpectedKey DAQUOTE_LEFT = key("\u00AB");
    private static final ExpectedKey DAQUOTE_RIGHT = key("\u00BB");
    public static ExpectedKey[] DOUBLE_ANGLE_QUOTES_LR = { DAQUOTE_LEFT, DAQUOTE_RIGHT };
    public static ExpectedKey[] DOUBLE_ANGLE_QUOTES_RL = { DAQUOTE_RIGHT, DAQUOTE_LEFT };

    // Variations of the "single quote" key's "more keys" on the 3rd row.
    public static final String SINGLE_QUOTE = "SINGLE_QUOTE";
    // U+2018: "‘" LEFT SINGLE QUOTATION MARK
    // U+2019: "’" RIGHT SINGLE QUOTATION MARK
    // U+201A: "‚" SINGLE LOW-9 QUOTATION MARK
    private static final ExpectedKey SQUOTE_LEFT = key("\u2018");
    private static final ExpectedKey SQUOTE_RIGHT = key("\u2019");
    private static final ExpectedKey SQUOTE_LOW9 = key("\u201A");
    public static ExpectedKey[] SINGLE_QUOTES_9LR = { SQUOTE_LOW9, SQUOTE_LEFT, SQUOTE_RIGHT };
    public static ExpectedKey[] SINGLE_QUOTES_R9L = { SQUOTE_RIGHT, SQUOTE_LOW9, SQUOTE_LEFT };
    public static ExpectedKey[] SINGLE_QUOTES_L9R = { SQUOTE_LEFT, SQUOTE_LOW9, SQUOTE_RIGHT };
    public static ExpectedKey[] SINGLE_QUOTES_LR9 = { SQUOTE_LEFT, SQUOTE_RIGHT, SQUOTE_LOW9 };
    // U+2039: "‹" SINGLE LEFT-POINTING ANGLE QUOTATION MARK
    // U+203A: "›" SINGLE RIGHT-POINTING ANGLE QUOTATION MARK
    private static final ExpectedKey SAQUOTE_LEFT = key("\u2039");
    private static final ExpectedKey SAQUOTE_RIGHT = key("\u203A");
    public static ExpectedKey[] SINGLE_ANGLE_QUOTES_LR = { SAQUOTE_LEFT, SAQUOTE_RIGHT };
    public static ExpectedKey[] SINGLE_ANGLE_QUOTES_RL = { SAQUOTE_RIGHT, SAQUOTE_LEFT };

    // Common symbols keyboard layout.
    private static final ExpectedKey[][] SYMBOLS_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+00B9: "¹" SUPERSCRIPT ONE
                    // U+00BD: "½" VULGAR FRACTION ONE HALF
                    // U+2153: "⅓" VULGAR FRACTION ONE THIRD
                    // U+00BC: "¼" VULGAR FRACTION ONE QUARTER
                    // U+215B: "⅛" VULGAR FRACTION ONE EIGHTH
                    key("1", joinMoreKeys("\u00B9", "\u00BD", "\u2153", "\u00BC", "\u215B")),
                    // U+00B2: "²" SUPERSCRIPT TWO
                    // U+2154: "⅔" VULGAR FRACTION TWO THIRDS
                    key("2", joinMoreKeys("\u00B2", "\u2154")),
                    // U+00B3: "³" SUPERSCRIPT THREE
                    // U+00BE: "¾" VULGAR FRACTION THREE QUARTERS
                    // U+215C: "⅜" VULGAR FRACTION THREE EIGHTHS
                    key("3", joinMoreKeys("\u00B3", "\u00BE", "\u215C")),
                    // U+2074: "⁴" SUPERSCRIPT FOUR
                    key("4", moreKey("\u2074")),
                    // U+215D: "⅝" VULGAR FRACTION FIVE EIGHTHS
                    key("5", moreKey("\u215D")),
                    "6",
                    // U+215E: "⅞" VULGAR FRACTION SEVEN EIGHTHS
                    key("7", moreKey("\u215E")),
                    "8", "9",
                    // U+207F: "ⁿ" SUPERSCRIPT LATIN SMALL LETTER N
                    // U+2205: "∅" EMPTY SET
                    key("0", joinMoreKeys("\u207F", "\u2205")))
            .setKeysOfRow(2,
                    key("@"), key("#"), key(CURRENCY),
                    // U+2030: "‰" PER MILLE SIGN
                    key("%", moreKey("\u2030")),
                    "&",
                    // U+2013: "–" EN DASH
                    // U+2014: "—" EM DASH
                    // U+00B7: "·" MIDDLE DOT
                    key("-", joinMoreKeys("_", "\u2013", "\u2014", "\u00B7")),
                    // U+00B1: "±" PLUS-MINUS SIGN
                    key("+", moreKey("\u00B1")),
                    key("(", joinMoreKeys("<", "{", "[")),
                    key(")", joinMoreKeys(">", "}", "]")))
            .setKeysOfRow(3,
                    // U+2020: "†" DAGGER
                    // U+2021: "‡" DOUBLE DAGGER
                    // U+2605: "★" BLACK STAR
                    key("*", joinMoreKeys("\u2020", "\u2021", "\u2605")),
                    key(DOUBLE_QUOTE), key(SINGLE_QUOTE), key(":"), key(";"),
                    // U+00A1: "¡" INVERTED EXCLAMATION MARK
                    key("!", moreKey("\u00A1")),
                    // U+00BF: "¿" INVERTED QUESTION MARK
                    key("?", moreKey("\u00BF")))
            .setKeysOfRow(4,
                    key(","), key("_"), SPACE_KEY, key("/"),
                    // U+2026: "…" HORIZONTAL ELLIPSIS
                    key(".", moreKey("\u2026")))
            .build();

    public static class RtlSymbols extends Symbols {
        public RtlSymbols(final LayoutCustomizer customizer) {
            super(customizer);
        }

        // U+00AB: "«" LEFT-POINTING DOUBLE ANGLE QUOTATION MARK
        // U+00BB: "»" RIGHT-POINTING DOUBLE ANGLE QUOTATION MARK
        private static final ExpectedKey DAQUOTE_LEFT_RTL = key("\u00AB", "\u00BB");
        private static final ExpectedKey DAQUOTE_RIGHT_RTL = key("\u00BB", "\u00AB");
        public static ExpectedKey[] DOUBLE_ANGLE_QUOTES_LR_RTL = {
                DAQUOTE_LEFT_RTL, DAQUOTE_RIGHT_RTL
        };
        // U+2039: "‹" SINGLE LEFT-POINTING ANGLE QUOTATION MARK
        // U+203A: "›" SINGLE RIGHT-POINTING ANGLE QUOTATION MARK
        private static final ExpectedKey SAQUOTE_LEFT_RTL = key("\u2039", "\u203A");
        private static final ExpectedKey SAQUOTE_RIGHT_RTL = key("\u203A", "\u2039");
        public static ExpectedKey[] SINGLE_ANGLE_QUOTES_LR_RTL = {
                SAQUOTE_LEFT_RTL, SAQUOTE_RIGHT_RTL
        };

        @Override
        public ExpectedKey[][] getLayout(final boolean isPhone) {
            return new ExpectedKeyboardBuilder(super.getLayout(isPhone))
                    .replaceKeyOfLabel("(", key("(", ")",
                            moreKey("<", ">"), moreKey("{", "}"), moreKey("[", "]")))
                    .replaceKeyOfLabel(")", key(")", "(",
                            moreKey(">", "<"), moreKey("}", "{"), moreKey("]", "[")))
                    .build();
        }
    }
}
