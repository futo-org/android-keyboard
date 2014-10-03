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
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;
import com.android.inputmethod.keyboard.layout.expected.AbstractLayoutBase;

/**
 * The symbols shifted keyboard layout.
 */
public class SymbolsShifted extends AbstractLayoutBase {
    private final LayoutCustomizer mCustomizer;

    public SymbolsShifted(final LayoutCustomizer customizer) {
        mCustomizer = customizer;
    }

    public ExpectedKey[][] getLayout(final boolean isPhone) {
        final ExpectedKeyboardBuilder builder = new ExpectedKeyboardBuilder(SYMBOLS_SHIFTED_COMMON);
        final LayoutCustomizer customizer = mCustomizer;
        builder.replaceKeyOfLabel(OTHER_CURRENCIES, (Object[])customizer.getOtherCurrencyKeys());
        if (isPhone) {
            builder.addKeysOnTheLeftOfRow(3, customizer.getBackToSymbolsKey())
                    .addKeysOnTheRightOfRow(3, DELETE_KEY)
                    .addKeysOnTheLeftOfRow(4, customizer.getAlphabetKey())
                    .addKeysOnTheRightOfRow(4, customizer.getEnterKey(isPhone));
        } else {
            // Tablet symbols shifted keyboard has extra two keys at the right edge of the 3rd row.
            // U+00BF: "¿" INVERTED QUESTION MARK
            // U+00A1: "¡" INVERTED EXCLAMATION MARK
            builder.addKeysOnTheRightOfRow(3, (Object[])joinKeys("\u00A1", "\u00BF"));
            builder.addKeysOnTheRightOfRow(1, DELETE_KEY)
                    .addKeysOnTheRightOfRow(2, customizer.getEnterKey(isPhone))
                    .addKeysOnTheLeftOfRow(3, customizer.getBackToSymbolsKey())
                    .addKeysOnTheRightOfRow(3, customizer.getBackToSymbolsKey())
                    .addKeysOnTheLeftOfRow(4, customizer.getAlphabetKey())
                    .addKeysOnTheRightOfRow(4, customizer.getEmojiKey(isPhone));
        }
        return builder.build();
    }

    // Variations of the "other currencies" keys on the 2rd row.
    public static final String OTHER_CURRENCIES = "OTHER_CURRENCY";
    public static final ExpectedKey[] CURRENCIES_OTHER_THAN_DOLLAR = {
        Symbols.POUND_SIGN, Symbols.CENT_SIGN, Symbols.EURO_SIGN, Symbols.YEN_SIGN
    };
    public static final ExpectedKey[] CURRENCIES_OTHER_THAN_EURO = {
        Symbols.POUND_SIGN, Symbols.YEN_SIGN, key(Symbols.DOLLAR_SIGN, Symbols.CENT_SIGN),
        Symbols.CENT_SIGN
    };
    public static final ExpectedKey[] CURRENCIES_OTHER_GENERIC = {
        Symbols.POUND_SIGN, Symbols.EURO_SIGN, key(Symbols.DOLLAR_SIGN, Symbols.CENT_SIGN),
        Symbols.CENT_SIGN
    };

    // Common symbols shifted keyboard layout.
    private static final ExpectedKey[][] SYMBOLS_SHIFTED_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+0060: "`" GRAVE ACCENT
                    "~", "\u0060", "|",
                    // U+2022: "•" BULLET
                    // U+266A: "♪" EIGHTH NOTE
                    // U+2665: "♥" BLACK HEART SUIT
                    // U+2660: "♠" BLACK SPADE SUIT
                    // U+2666: "♦" BLACK DIAMOND SUIT
                    // U+2663: "♣" BLACK CLUB SUIT
                    key("\u2022", joinMoreKeys("\u266A", "\u2665", "\u2660", "\u2666", "\u2663")),
                    // U+221A: "√" SQUARE ROOT
                    "\u221A",
                    // U+03C0: "π" GREEK SMALL LETTER PI
                    // U+03A0: "Π" GREEK CAPITAL LETTER PI
                    key("\u03C0", moreKey("\u03A0")),
                    // U+00F7: "÷" DIVISION SIGN
                    // U+00D7: "×" MULTIPLICATION SIGN
                    "\u00F7", "\u00D7",
                    // U+00B6: "¶" PILCROW SIGN
                    // U+00A7: "§" SECTION SIGN
                    key("\u00B6", moreKey("\u00A7")),
                    // U+2206: "∆" INCREMENT
                    "\u2206")
            .setKeysOfRow(2,
                    OTHER_CURRENCIES,
                    // U+2191: "↑" UPWARDS ARROW
                    // U+2193: "↓" DOWNWARDS ARROW
                    // U+2190: "←" LEFTWARDS ARROW
                    // U+2192: "→" RIGHTWARDS ARROW
                    key("^", joinMoreKeys("\u2191", "\u2193", "\u2190", "\u2192")),
                    // U+00B0: "°" DEGREE SIGN
                    // U+2032: "′" PRIME
                    // U+2033: "″" DOUBLE PRIME
                    key("\u00B0", joinMoreKeys("\u2032", "\u2033")),
                    // U+2260: "≠" NOT EQUAL TO
                    // U+2248: "≈" ALMOST EQUAL TO
                    // U+221E: "∞" INFINITY
                    key("=", joinMoreKeys("\u2260", "\u2248", "\u221E")),
                    "{", "}")
            .setKeysOfRow(3,
                    // U+00A9: "©" COPYRIGHT SIGN
                    // U+00AE: "®" REGISTERED SIGN
                    // U+2122: "™" TRADE MARK SIGN
                    // U+2105: "℅" CARE OF
                    "\\", "\u00A9", "\u00AE", "\u2122", "\u2105", "[", "]")
            .setKeysOfRow(4,
                    ",",
                    // U+2039: "‹" SINGLE LEFT-POINTING ANGLE QUOTATION MARK
                    // U+2264: "≤" LESS-THAN OR EQUAL TO
                    // U+00AB: "«" LEFT-POINTING DOUBLE ANGLE QUOTATION MARK
                    key("<", joinMoreKeys("\u2039", "\u2264", "\u00AB")),
                    SPACE_KEY,
                    // U+203A: "›" SINGLE RIGHT-POINTING ANGLE QUOTATION MARK
                    // U+2265: "≥" GREATER-THAN EQUAL TO
                    // U+00BB: "»" RIGHT-POINTING DOUBLE ANGLE QUOTATION MARK
                    key(">", joinMoreKeys("\u203A", "\u2265", "\u00BB")),
                    // U+2026: "…" HORIZONTAL ELLIPSIS
                    key(".", moreKey("\u2026")))
            .build();

    public static class RtlSymbolsShifted extends SymbolsShifted {
        public RtlSymbolsShifted(final LayoutCustomizer customizer) {
            super(customizer);
        }

        @Override
        public ExpectedKey[][] getLayout(final boolean isPhone) {
            return new ExpectedKeyboardBuilder(super.getLayout(isPhone))
                .replaceKeyOfLabel("{", key("{", "}"))
                .replaceKeyOfLabel("}", key("}", "{"))
                .replaceKeyOfLabel("[", key("[", "]"))
                .replaceKeyOfLabel("]", key("]", "["))
                // U+2039: "‹" SINGLE LEFT-POINTING ANGLE QUOTATION MARK
                // U+2264: "≤" LESS-THAN OR EQUAL TO
                // U+00AB: "«" LEFT-POINTING DOUBLE ANGLE QUOTATION MARK
                .replaceKeyOfLabel("<", key("<", ">",
                        moreKey("\u2039", "\u203A"), moreKey("\u2264", "\u2265"),
                        moreKey("\u00AB", "\u00BB")))
                // U+203A: "›" SINGLE RIGHT-POINTING ANGLE QUOTATION MARK
                // U+2265: "≥" GREATER-THAN EQUAL TO
                // U+00BB: "»" RIGHT-POINTING DOUBLE ANGLE QUOTATION MARK
                .replaceKeyOfLabel(">", key(">", "<",
                        moreKey("\u203A", "\u2039"), moreKey("\u2265", "\u2264"),
                        moreKey("\u00BB", "\u00AB")))
                .build();
        }
    }
}
