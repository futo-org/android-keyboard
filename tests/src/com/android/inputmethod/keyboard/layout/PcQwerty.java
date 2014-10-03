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

/**
 * The PC QWERTY alphabet keyboard.
 */
public final class PcQwerty extends LayoutBase {
    private static final String LAYOUT_NAME = "pcqwerty";

    public PcQwerty(final LayoutCustomizer customizer) {
        super(customizer, Symbols.class, SymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    @Override
    ExpectedKey[][] getCommonAlphabetLayout(final boolean isPhone) {
        final LayoutCustomizer customizer = getCustomizer();
        final ExpectedKeyboardBuilder builder = new ExpectedKeyboardBuilder(ALPHABET_COMMON);
        customizer.setAccentedLetters(builder);
        builder.replaceKeyOfLabel(ROW1_1, key("`", moreKey("~")))
                .replaceKeyOfLabel(ROW2_11, key("[", moreKey("{")))
                .replaceKeyOfLabel(ROW2_12, key("]", moreKey("}")))
                .replaceKeyOfLabel(ROW2_13, key("\\", moreKey("|")))
                .replaceKeyOfLabel(ROW3_10, key(";", moreKey(":")))
                .replaceKeyOfLabel(ROW3_11, key("'", joinMoreKeys(additionalMoreKey("\""),
                        customizer.getDoubleQuoteMoreKeys(),
                        customizer.getSingleQuoteMoreKeys())))
                .setAdditionalMoreKeysPositionOf("'", 4)
                .replaceKeyOfLabel(ROW4_8, key(",", moreKey("<")))
                .replaceKeyOfLabel(ROW4_9, key(".", moreKey(">")))
                // U+00BF: "¿" INVERTED QUESTION MARK
                .replaceKeyOfLabel(ROW4_10, key("/", joinMoreKeys("?", "\u00BF")));
        if (isPhone) {
            // U+221E: "∞" INFINITY
            // U+2260: "≠" NOT EQUAL TO
            // U+2248: "≈" ALMOST EQUAL TO
            builder.replaceKeyOfLabel(ROW1_13, key("=",
                    joinMoreKeys("\u221E", "\u2260", "\u2248", "+")));
        } else {
            // U+221E: "∞" INFINITY
            // U+2260: "≠" NOT EQUAL TO
            // U+2248: "≈" ALMOST EQUAL TO
            builder.replaceKeyOfLabel(ROW1_13, key("=",
                    joinMoreKeys("+", "\u221E", "\u2260", "\u2248")));
        }
        return builder.build();
    }

    @Override
    ExpectedKey[][] getCommonAlphabetShiftLayout(final boolean isPhone, final int elementId) {
        final ExpectedKeyboardBuilder builder;
        if (elementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED
                || elementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED) {
            builder = new ExpectedKeyboardBuilder(getCommonAlphabetLayout(isPhone));
        } else {
            builder = new ExpectedKeyboardBuilder(ALPHABET_COMMON);
            final LayoutCustomizer customizer = getCustomizer();
            customizer.setAccentedLetters(builder);
            builder.setKeysOfRow(1,
                    "~",
                    // U+00A1: "¡" INVERTED EXCLAMATION MARK
                    key("!", moreKey("\u00A1")),
                    "@", "#",
                    customizer.getCurrencyKey(),
                    // U+2030: "‰" PER MILLE SIGN
                    key("%", moreKey("\u2030")),
                    "^", "&",
                    // U+2020: "†" DAGGER
                    // U+2021: "‡" DOUBLE DAGGER
                    // U+2605: "★" BLACK STAR
                    key("*", joinMoreKeys("\u2020", "\u2021", "\u2605")),
                    "(", ")", "_",
                    // U+00B1: "±" PLUS-MINUS SIGN
                    // U+00D7: "×" MULTIPLICATION SIGN
                    // U+00F7: "÷" DIVISION SIGN
                    // U+221A: "√" SQUARE ROOT
                    key("+", joinMoreKeys("\u00B1", "\u00D7", "\u00F7", "\u221A")))
                    .replaceKeyOfLabel(ROW2_11, key("{"))
                    .replaceKeyOfLabel(ROW2_12, key("}"))
                    .replaceKeyOfLabel(ROW2_13, key("|"))
                    .replaceKeyOfLabel(ROW3_10, key(":"))
                    .replaceKeyOfLabel(ROW3_11, key("\"", joinMoreKeys(
                            customizer.getDoubleQuoteMoreKeys(),
                            customizer.getSingleQuoteMoreKeys())))
                    // U+2039: "‹" SINGLE LEFT-POINTING ANGLE QUOTATION MARK
                    // U+2264: "≤" LESS-THAN OR EQUAL TO
                    // U+00AB: "«" LEFT-POINTING DOUBLE ANGLE QUOTATION MARK
                    .replaceKeyOfLabel(ROW4_8, key("<", joinMoreKeys("\u2039", "\u2264", "\u00AB")))
                    // U+203A: "›" SINGLE RIGHT-POINTING ANGLE QUOTATION MARK
                    // U+2265: "≥" GREATER-THAN EQUAL TO
                    // U+00BB: "»" RIGHT-POINTING DOUBLE ANGLE QUOTATION MARK
                    .replaceKeyOfLabel(ROW4_9, key(">", joinMoreKeys("\u203A", "\u2265", "\u00BB")))
                    // U+00BF: "¿" INVERTED QUESTION MARK
                    .replaceKeyOfLabel(ROW4_10, key("?", moreKey("\u00BF")));
        }
        builder.toUpperCase(getLocale());
        return builder.build();
    }

    // Helper method to create alphabet layout by adding special function keys.
    @Override
    ExpectedKeyboardBuilder convertCommonLayoutToKeyboard(final ExpectedKeyboardBuilder builder,
            final boolean isPhone) {
        final LayoutCustomizer customizer = getCustomizer();
        builder.setKeysOfRow(5, (Object[])customizer.getSpaceKeys(isPhone));
        builder.addKeysOnTheLeftOfRow(5, (Object[])customizer.getKeysLeftToSpacebar(isPhone));
        builder.addKeysOnTheRightOfRow(5, (Object[])customizer.getKeysRightToSpacebar(isPhone));
        if (isPhone) {
            builder.addKeysOnTheRightOfRow(3, DELETE_KEY);
        } else {
            builder.addKeysOnTheRightOfRow(1, DELETE_KEY)
                    .addKeysOnTheLeftOfRow(2, TAB_KEY)
                    .addKeysOnTheRightOfRow(3, ENTER_KEY);
        }
        builder.addKeysOnTheLeftOfRow(4, (Object[])customizer.getLeftShiftKeys(isPhone))
                .addKeysOnTheRightOfRow(4, (Object[])customizer.getRightShiftKeys(isPhone));
        return builder;
    }

    @Override
    public ExpectedKey[][] getLayout(final boolean isPhone, final int elementId) {
        if (elementId == KeyboardId.ELEMENT_SYMBOLS
                || elementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED) {
            return null;
        }
        return super.getLayout(isPhone, elementId);
    }

    private static final String ROW1_1 = "ROW1_1";
    private static final String ROW1_13 = "ROW1_13";
    private static final String ROW2_11 = "ROW2_11";
    private static final String ROW2_12 = "ROW2_12";
    private static final String ROW2_13 = "ROW2_13";
    private static final String ROW3_10 = "ROW3_10";
    private static final String ROW3_11 = "ROW3_11";
    private static final String ROW4_8 = "ROW4_8";
    private static final String ROW4_9 = "ROW4_9";
    private static final String ROW4_10 = "ROW4_10";

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    ROW1_1,
                    // U+00A1: "¡" INVERTED EXCLAMATION MARK
                    // U+00B9: "¹" SUPERSCRIPT ONE
                    // U+00BD: "½" VULGAR FRACTION ONE HALF
                    // U+2153: "⅓" VULGAR FRACTION ONE THIRD
                    // U+00BC: "¼" VULGAR FRACTION ONE QUARTER
                    // U+215B: "⅛" VULGAR FRACTION ONE EIGHTH
                    key("1", joinMoreKeys(
                            "!", "\u00A1", "\u00B9", "\u00BD", "\u2153", "\u00BC", "\u215B")),
                    // U+00B2: "²" SUPERSCRIPT TWO
                    // U+2154: "⅔" VULGAR FRACTION TWO THIRDS
                    key("2", joinMoreKeys("@", "\u00B2", "\u2154")),
                    // U+00B3: "³" SUPERSCRIPT THREE
                    // U+00BE: "¾" VULGAR FRACTION THREE QUARTERS
                    // U+215C: "⅜" VULGAR FRACTION THREE EIGHTHS
                    key("3", joinMoreKeys("#", "\u00B3", "\u00BE", "\u215C")),
                    // U+2074: "⁴" SUPERSCRIPT FOUR
                    key("4", joinMoreKeys("$", "\u2074")),
                    // U+215D: "⅝" VULGAR FRACTION FIVE EIGHTHS
                    key("5", joinMoreKeys("%", "\u215D")),
                    key("6", moreKey("^")),
                    // U+215E: "⅞" VULGAR FRACTION SEVEN EIGHTHS
                    key("7", joinMoreKeys("&", "\u215E")),
                    key("8", moreKey("*")),
                    key("9", moreKey("(")),
                    // U+207F: "ⁿ" SUPERSCRIPT LATIN SMALL LETTER N
                    // U+2205: "∅" EMPTY SET
                    key("0", joinMoreKeys(")", "\u207F", "\u2205")),
                    // U+2013: "–" EN DASH
                    // U+2014: "—" EM DASH
                    // U+00B7: "·" MIDDLE DOT
                    key("-", joinMoreKeys("_", "\u2013", "\u2014", "\u00B7")),
                    ROW1_13)
            .setKeysOfRow(2, "q", "w", "e", "r", "t", "y", "u", "i", "o", "p",
                    ROW2_11, ROW2_12, ROW2_13)
            .setKeysOfRow(3, "a", "s", "d", "f", "g", "h", "j", "k", "l", ROW3_10, ROW3_11)
            .setKeysOfRow(4, "z", "x", "c", "v", "b", "n", "m", ROW4_8, ROW4_9, ROW4_10)
            .build();
}
