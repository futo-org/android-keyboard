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

import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;
import com.android.inputmethod.keyboard.layout.expected.LayoutBase;
import com.android.inputmethod.latin.Constants;

/**
 * The symbols keyboard layout.
 */
public final class Symbols extends LayoutBase {
    public static ExpectedKey[][] getLayout(final boolean isPhone) {
        return isPhone ? toPhoneSymbol(SYMBOLS_COMMON) : toTabletSymbols(SYMBOLS_COMMON);
    }

    public static ExpectedKey[][] getDefaultLayout(final boolean isPhone) {
        final ExpectedKeyboardBuilder builder = new ExpectedKeyboardBuilder(SYMBOLS_COMMON);
        builder.replaceKeyOfLabel(CURRENCY, Symbols.CURRENCY_DOLLAR);
        builder.replaceKeyOfLabel(DOUBLE_QUOTE,
                key("\"", join(Symbols.DOUBLE_QUOTES_9LR, Symbols.DOUBLE_ANGLE_QUOTES_LR)));
        builder.replaceKeyOfLabel(SINGLE_QUOTE,
                key("'", join(Symbols.SINGLE_QUOTES_9LR, Symbols.SINGLE_ANGLE_QUOTES_LR)));
        final ExpectedKey[][] symbolsCommon = builder.build();
        return isPhone ? toPhoneSymbol(symbolsCommon) : toTabletSymbols(symbolsCommon);
    }

    // Functional keys.
    public static final ExpectedKey ALPHABET_KEY = key("ABC", Constants.CODE_SWITCH_ALPHA_SYMBOL);
    public static final ExpectedKey SYMBOLS_SHIFT_KEY = key("= \\ <", Constants.CODE_SHIFT);
    public static final ExpectedKey TABLET_SYMBOLS_SHIFT_KEY = key("~ [ <", Constants.CODE_SHIFT);

    // Variations of the "currency" key on the 2nd row.
    public static final String CURRENCY = "currency";
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

    // Variations of the "double quote" key's "more keys" on the 3rd row.
    public static final String DOUBLE_QUOTE = "double_quote";
    // U+201C: "“" LEFT DOUBLE QUOTATION MARK
    // U+201D: "”" RIGHT DOUBLE QUOTATION MARK
    // U+201E: "„" DOUBLE LOW-9 QUOTATION MARK
    static final ExpectedKey DQUOTE_LEFT = key("\u201C");
    static final ExpectedKey DQUOTE_RIGHT = key("\u201D");
    static final ExpectedKey DQUOTE_LOW9 = key("\u201E");
    public static ExpectedKey[] DOUBLE_QUOTES_9LR = { DQUOTE_LOW9, DQUOTE_LEFT, DQUOTE_RIGHT };
    public static ExpectedKey[] DOUBLE_QUOTES_R9L = { DQUOTE_RIGHT, DQUOTE_LOW9, DQUOTE_LEFT };
    public static ExpectedKey[] DOUBLE_QUOTES_L9R = { DQUOTE_LEFT, DQUOTE_LOW9, DQUOTE_RIGHT };
    public static ExpectedKey[] DOUBLE_QUOTES_LR9 = { DQUOTE_LEFT, DQUOTE_RIGHT, DQUOTE_LOW9 };
    // U+00AB: "«" LEFT-POINTING DOUBLE ANGLE QUOTATION MARK
    // U+00BB: "»" RIGHT-POINTING DOUBLE ANGLE QUOTATION MARK
    private static final ExpectedKey DAQUOTE_LEFT = key("\u00AB");
    private static final ExpectedKey DAQUOTE_RIGHT = key("\u00BB");
    private static final ExpectedKey DAQUOTE_LEFT_RTL = key("\u00AB", "\u00BB");
    private static final ExpectedKey DAQUOTE_RIGHT_RTL = key("\u00BB", "\u00AB");
    public static ExpectedKey[] DOUBLE_ANGLE_QUOTES_LR = { DAQUOTE_LEFT, DAQUOTE_RIGHT };
    public static ExpectedKey[] DOUBLE_ANGLE_QUOTES_RL = { DAQUOTE_RIGHT, DAQUOTE_LEFT };
    public static ExpectedKey[] DOUBLE_ANGLE_QUOTES_RTL = { DAQUOTE_LEFT_RTL, DAQUOTE_RIGHT_RTL };

    // Variations of the "single quote" key's "more keys" on the 3rd row.
    public static final String SINGLE_QUOTE = "single_quote";
    // U+2018: "‘" LEFT SINGLE QUOTATION MARK
    // U+2019: "’" RIGHT SINGLE QUOTATION MARK
    // U+201A: "‚" SINGLE LOW-9 QUOTATION MARK
    static final ExpectedKey SQUOTE_LEFT = key("\u2018");
    static final ExpectedKey SQUOTE_RIGHT = key("\u2019");
    static final ExpectedKey SQUOTE_LOW9 = key("\u201A");
    public static ExpectedKey[] SINGLE_QUOTES_9LR = { SQUOTE_LOW9, SQUOTE_LEFT, SQUOTE_RIGHT };
    public static ExpectedKey[] SINGLE_QUOTES_R9L = { SQUOTE_RIGHT, SQUOTE_LOW9, SQUOTE_LEFT };
    public static ExpectedKey[] SINGLE_QUOTES_L9R = { SQUOTE_LEFT, SQUOTE_LOW9, SQUOTE_RIGHT };
    public static ExpectedKey[] SINGLE_QUOTES_LR9 = { SQUOTE_LEFT, SQUOTE_RIGHT, SQUOTE_LOW9 };
    // U+2039: "‹" SINGLE LEFT-POINTING ANGLE QUOTATION MARK
    // U+203A: "›" SINGLE RIGHT-POINTING ANGLE QUOTATION MARK
    private static final ExpectedKey SAQUOTE_LEFT = key("\u2039");
    private static final ExpectedKey SAQUOTE_RIGHT = key("\u203A");
    private static final ExpectedKey SAQUOTE_LEFT_RTL = key("\u2039", "\u203A");
    private static final ExpectedKey SAQUOTE_RIGHT_RTL = key("\u203A", "\u2039");
    public static ExpectedKey[] SINGLE_ANGLE_QUOTES_LR = { SAQUOTE_LEFT, SAQUOTE_RIGHT };
    public static ExpectedKey[] SINGLE_ANGLE_QUOTES_RL = { SAQUOTE_RIGHT, SAQUOTE_LEFT };
    public static ExpectedKey[] SINGLE_ANGLE_QUOTES_RTL = { SAQUOTE_LEFT_RTL, SAQUOTE_RIGHT_RTL };

    // Common symbols keyboard layout.
    public static final ExpectedKey[][] SYMBOLS_COMMON = new ExpectedKeyboardBuilder(10, 9, 7, 5)
            .setLabelsOfRow(1, "1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
            // U+00B9: "¹" SUPERSCRIPT ONE
            // U+00BD: "½" VULGAR FRACTION ONE HALF
            // U+2153: "⅓" VULGAR FRACTION ONE THIRD
            // U+00BC: "¼" VULGAR FRACTION ONE QUARTER
            // U+215B: "⅛" VULGAR FRACTION ONE EIGHTH
            .setMoreKeysOf("1", "\u00B9", "\u00BD", "\u2153", "\u00BC", "\u215B")
            // U+00B2: "²" SUPERSCRIPT TWO
            // U+2154: "⅔" VULGAR FRACTION TWO THIRDS
            .setMoreKeysOf("2", "\u00B2", "\u2154")
            // U+00B3: "³" SUPERSCRIPT THREE
            // U+00BE: "¾" VULGAR FRACTION THREE QUARTERS
            // U+215C: "⅜" VULGAR FRACTION THREE EIGHTHS
            .setMoreKeysOf("3", "\u00B3", "\u00BE", "\u215C")
            // U+2074: "⁴" SUPERSCRIPT FOUR
            .setMoreKeysOf("4", "\u2074")
            // U+215D: "⅝" VULGAR FRACTION FIVE EIGHTHS
            .setMoreKeysOf("5", "\u215D")
            // U+215E: "⅞" VULGAR FRACTION SEVEN EIGHTHS
            .setMoreKeysOf("7", "\u215E")
            // U+207F: "ⁿ" SUPERSCRIPT LATIN SMALL LETTER N
            // U+2205: "∅" EMPTY SET
            .setMoreKeysOf("0", "\u207F", "\u2205")
            .setLabelsOfRow(2, "@", "#", CURRENCY, "%", "&", "-", "+", "(", ")")
            // U+2030: "‰" PER MILLE SIGN
            .setMoreKeysOf("%", "\u2030")
            // U+2013: "–" EN DASH
            // U+2014: "—" EM DASH
            // U+00B7: "·" MIDDLE DOT
            .setMoreKeysOf("-", "_", "\u2013", "\u2014", "\u00B7")
            // U+00B1: "±" PLUS-MINUS SIGN
            .setMoreKeysOf("+", "\u00B1")
            .setMoreKeysOf("(", "<", "{", "[")
            .setMoreKeysOf(")", ">", "}", "]")
            .setLabelsOfRow(3, "*", DOUBLE_QUOTE, SINGLE_QUOTE, ":", ";", "!", "?")
            // U+2020: "†" DAGGER
            // U+2021: "‡" DOUBLE DAGGER
            // U+2605: "★" BLACK STAR
            .setMoreKeysOf("*", "\u2020", "\u2021", "\u2605")
            // U+00A1: "¡" INVERTED EXCLAMATION MARK
            .setMoreKeysOf("!", "\u00A1")
            // U+00BF: "¿" INVERTED QUESTION MARK
            .setMoreKeysOf("?", "\u00BF")
            .setLabelsOfRow(4, "_", "/", " ", ",", ".")
            // U+2026: "…" HORIZONTAL ELLIPSIS
            .setMoreKeysOf(".", "\u2026")
            .build();

    private static ExpectedKey[][] toPhoneSymbol(final ExpectedKey[][] common) {
        return new ExpectedKeyboardBuilder(common)
                .addKeysOnTheLeftOfRow(3, Symbols.SYMBOLS_SHIFT_KEY)
                .addKeysOnTheRightOfRow(3, DELETE_KEY)
                .addKeysOnTheLeftOfRow(4, Symbols.ALPHABET_KEY)
                .addKeysOnTheRightOfRow(4, key(ENTER_KEY, EMOJI_KEY))
                .build();
    }

    private static ExpectedKey[][] toTabletSymbols(final ExpectedKey[][] common) {
        return new ExpectedKeyboardBuilder(common)
                .addKeysOnTheLeftOfRow(3,
                        key("\\"), key("="))
                .addKeysOnTheRightOfRow(1, DELETE_KEY)
                .addKeysOnTheRightOfRow(2, ENTER_KEY)
                .addKeysOnTheLeftOfRow(3, Symbols.TABLET_SYMBOLS_SHIFT_KEY)
                .addKeysOnTheRightOfRow(3, Symbols.TABLET_SYMBOLS_SHIFT_KEY)
                .addKeysOnTheLeftOfRow(4, Symbols.ALPHABET_KEY)
                .addKeysOnTheRightOfRow(4, EMOJI_KEY)
                .build();
    }
}
