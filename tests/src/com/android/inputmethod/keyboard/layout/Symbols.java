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
    public static ExpectedKey[][] getSymbols(final boolean isPhone) {
        return isPhone ? toPhoneSymbol(SYMBOLS_COMMON) : toTabletSymbols(SYMBOLS_COMMON);
    }

    // Functional keys.
    public static final ExpectedKey ALPHABET_KEY = key("ABC", Constants.CODE_SWITCH_ALPHA_SYMBOL);
    public static final ExpectedKey SYMBOLS_SHIFT_KEY = key("= \\ <", Constants.CODE_SHIFT);
    public static final ExpectedKey TABLET_SYMBOLS_SHIFT_KEY = key("~ [ <", Constants.CODE_SHIFT);

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
            .setLabelsOfRow(2, "@", "#", "$", "%", "&", "-", "+", "(", ")")
            // U+00A2: "¢" CENT SIGN
            // U+00A3: "£" POUND SIGN
            // U+20AC: "€" EURO SIGN
            // U+00A5: "¥" YEN SIGN
            // U+20B1: "₱" PESO SIGN
            .setMoreKeysOf("$", "\u00A2", "\u00A3", "\u20AC", "\u00A5", "\u20B1")
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
            .setLabelsOfRow(3, "*", "\"", "'", ":", ";", "!", "?")
            // U+2020: "†" DAGGER
            // U+2021: "‡" DOUBLE DAGGER
            // U+2605: "★" BLACK STAR
            .setMoreKeysOf("*", "\u2020", "\u2021", "\u2605")
            // U+201E: "„" DOUBLE LOW-9 QUOTATION MARK
            // U+201C: "“" LEFT DOUBLE QUOTATION MARK
            // U+201D: "”" RIGHT DOUBLE QUOTATION MARK
            // U+00AB: "«" LEFT-POINTING DOUBLE ANGLE QUOTATION MARK
            // U+00BB: "»" RIGHT-POINTING DOUBLE ANGLE QUOTATION MARK
            .setMoreKeysOf("\"", "\u201E", "\u201C", "\u201D", "\u00AB", "\u00BB")
            // U+201A: "‚" SINGLE LOW-9 QUOTATION MARK
            // U+2018: "‘" LEFT SINGLE QUOTATION MARK
            // U+2019: "’" RIGHT SINGLE QUOTATION MARK
            // U+2039: "‹" SINGLE LEFT-POINTING ANGLE QUOTATION MARK
            // U+203A: "›" SINGLE RIGHT-POINTING ANGLE QUOTATION MARK
            .setMoreKeysOf("'", "\u201A", "\u2018", "\u2019", "\u2039", "\u203A")
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

    // Helper method to add currency symbols for Euro.
    public static ExpectedKeyboardBuilder euro(final ExpectedKeyboardBuilder builder) {
        return builder
                // U+20AC: "€" EURO SIGN
                // U+00A2: "¢" CENT SIGN
                // U+00A3: "£" POUND SIGN
                // U+00A5: "¥" YEN SIGN
                // U+20B1: "₱" PESO SIGN
                .replaceKeyOfLabel("$", key("\u20AC",
                        moreKey("\u00A2"), moreKey("\u00A3"), moreKey("$"),
                        moreKey("\u00A5"), moreKey("\u20B1")));
    }

    // Helper method to add single quotes "more keys".
    // "9LLR" means "9-low/Left quotation marks, Left/Right-pointing angle quotation marks".
    public static ExpectedKeyboardBuilder singleQuotes9LLR(final ExpectedKeyboardBuilder builder) {
        return builder
                // U+2019: "’" RIGHT SINGLE QUOTATION MARK
                // U+201A: "‚" SINGLE LOW-9 QUOTATION MARK
                // U+2018: "‘" LEFT SINGLE QUOTATION MARK
                // U+2039: "‹" SINGLE LEFT-POINTING ANGLE QUOTATION MARK
                // U+203A: "›" SINGLE RIGHT-POINTING ANGLE QUOTATION MARK
                .setMoreKeysOf("'", "\u2019", "\u201A", "\u2018", "\u2039", "\u203A");
    }

    // Helper method to add single quotes "more keys".
    // "9LLR" means "9-low/Left quotation marks, Right/Left-pointing angle quotation marks".
    public static ExpectedKeyboardBuilder singleQuotes9LRL(final ExpectedKeyboardBuilder builder) {
        return builder
                // U+2019: "’" RIGHT SINGLE QUOTATION MARK
                // U+201A: "‚" SINGLE LOW-9 QUOTATION MARK
                // U+2018: "‘" LEFT SINGLE QUOTATION MARK
                // U+203A: "›" SINGLE RIGHT-POINTING ANGLE QUOTATION MARK
                // U+2039: "‹" SINGLE LEFT-POINTING ANGLE QUOTATION MARK
                .setMoreKeysOf("'", "\u2019", "\u201A", "\u2018", "\u203A", "\u2039");
    }

    // Helper method to add double quotes "more keys".
    // "9LLR" means "9-low/Left quotation marks, Left/Right-pointing angle quotation marks".
    public static ExpectedKeyboardBuilder doubleQuotes9LLR(final ExpectedKeyboardBuilder builder) {
        return builder
                // U+201D: "”" RIGHT DOUBLE QUOTATION MARK
                // U+201E: "„" DOUBLE LOW-9 QUOTATION MARK
                // U+201C: "“" LEFT DOUBLE QUOTATION MARK
                // U+00AB: "«" LEFT-POINTING DOUBLE ANGLE QUOTATION MARK
                // U+00BB: "»" RIGHT-POINTING DOUBLE ANGLE QUOTATION MARK
                .setMoreKeysOf("\"", "\u201D", "\u201E", "\u201C", "\u00AB", "\u00BB");
    }

    // Helper method to add double quotes "more keys".
    // "9LLR" means "9-low/Left quotation marks, Right/Left-pointing angle quotation marks".
    public static ExpectedKeyboardBuilder doubleQuotes9LRL(final ExpectedKeyboardBuilder builder) {
        return builder
                // U+201D: "”" RIGHT DOUBLE QUOTATION MARK
                // U+201E: "„" DOUBLE LOW-9 QUOTATION MARK
                // U+201C: "“" LEFT DOUBLE QUOTATION MARK
                // U+00BB: "»" RIGHT-POINTING DOUBLE ANGLE QUOTATION MARK
                // U+00AB: "«" LEFT-POINTING DOUBLE ANGLE QUOTATION MARK
                .setMoreKeysOf("\"", "\u201D", "\u201E", "\u201C", "\u00BB", "\u00AB");
    }
}
