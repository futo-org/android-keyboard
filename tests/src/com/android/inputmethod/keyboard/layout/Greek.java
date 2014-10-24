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
import com.android.inputmethod.keyboard.layout.customizer.EuroCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;
import com.android.inputmethod.latin.common.Constants;

import java.util.Locale;

/**
 * The Greek alphabet keyboard.
 */
public final class Greek extends LayoutBase {
    private static final String LAYOUT_NAME = "greek";

    public Greek(final Locale locale) {
        super(new GreekCustomizer(locale), Symbols.class, SymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    private static class GreekCustomizer extends EuroCustomizer {
        GreekCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey getAlphabetKey() { return GREEK_ALPHABET_KEY; }

        // U+0391: "Α" GREEK CAPITAL LETTER ALPHA
        // U+0392: "Β" GREEK CAPITAL LETTER BETA
        // U+0393: "Γ" GREEK CAPITAL LETTER GAMMA
        private static final ExpectedKey GREEK_ALPHABET_KEY = key(
                "\u0391\u0392\u0393", Constants.CODE_SWITCH_ALPHA_SYMBOL);
    }

    @Override
    ExpectedKey[][] getCommonAlphabetLayout(final boolean isPhone) {
        final ExpectedKeyboardBuilder builder = new ExpectedKeyboardBuilder(ALPHABET_COMMON);
        builder.replaceKeyOfLabel(ROW1_1, ROW1_1_SEMICOLON);
        builder.replaceKeyOfLabel(ROW1_2, ROW1_2_FINAL_SIGMA);
        return builder.build();
    }

    @Override
    ExpectedKey[][] getCommonAlphabetShiftLayout(final boolean isPhone, final int elementId) {
        final ExpectedKeyboardBuilder builder = new ExpectedKeyboardBuilder(ALPHABET_COMMON);
        builder.toUpperCase(getLocale());
        if (elementId == KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED
                || elementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED) {
            builder.replaceKeyOfLabel(ROW1_1, ROW1_1_COLON);
        } else {
            builder.replaceKeyOfLabel(ROW1_1, ROW1_1_SEMICOLON);
        }
        builder.replaceKeyOfLabel(ROW1_2, ROW1_2_FINAL_SIGMA);
        return builder.build();
    }

    private static final String ROW1_1 = "ROW1_1";
    private static final ExpectedKey ROW1_1_SEMICOLON = key(";", joinMoreKeys("1", ":"));
    private static final ExpectedKey ROW1_1_COLON = key(":", joinMoreKeys("1", ";"));

    private static final String ROW1_2 = "ROW2_2";
    // U+03C2: "ς" GREEK SMALL LETTER FINAL SIGMA
    private static final ExpectedKey ROW1_2_FINAL_SIGMA = key("\u03C2", moreKey("2"));

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    key(ROW1_1, moreKey("1")),
                    key(ROW1_2, moreKey("2")),
                    // U+03B5: "ε" GREEK SMALL LETTER EPSILON
                    // U+03AD: "έ" GREEK SMALL LETTER EPSILON WITH TONOS
                    key("\u03B5", joinMoreKeys("\u03AD", "3")),
                    // U+03C1: "ρ" GREEK SMALL LETTER RHO
                    key("\u03C1", moreKey("4")),
                    // U+03C4: "τ" GREEK SMALL LETTER TAU
                    key("\u03C4", moreKey("5")),
                    // U+03C5: "υ" GREEK SMALL LETTER UPSILON
                    // U+03CD: "ύ" GREEK SMALL LETTER UPSILON WITH TONOS
                    // U+03CB: "ϋ" GREEK SMALL LETTER UPSILON WITH DIALYTIKA
                    // U+03B0: "ΰ" GREEK SMALL LETTER UPSILON WITH DIALYTIKA AND TONOS
                    key("\u03C5", joinMoreKeys("\u03CD", "6", "\u03CB", "\u03B0")),
                    // U+03B8: "θ" GREEK SMALL LETTER THETA
                    key("\u03B8", moreKey("7")),
                    // U+03B9: "ι" GREEK SMALL LETTER IOTA
                    // U+03AF: "ί" GREEK SMALL LETTER IOTA WITH TONOS
                    // U+03CA: "ϊ" GREEK SMALL LETTER IOTA WITH DIALYTIKA
                    // U+0390: "ΐ" GREEK SMALL LETTER IOTA WITH DIALYTIKA AND TONOS
                    key("\u03B9", joinMoreKeys("\u03AF", "8", "\u03CA", "\u0390")),
                    // U+03BF: "ο" GREEK SMALL LETTER OMICRON
                    // U+03CC: "ό" GREEK SMALL LETTER OMICRON WITH TONOS
                    key("\u03BF", joinMoreKeys("\u03CC", "9")),
                    // U+03C0: "π" GREEK SMALL LETTER PI
                    key("\u03C0", moreKey("0")))
            .setKeysOfRow(2,
                    // U+03B1: "α" GREEK SMALL LETTER ALPHA
                    // U+03AC: "ά" GREEK SMALL LETTER ALPHA WITH TONOS
                    key("\u03B1", moreKey("\u03AC")),
                    // U+03C3: "σ" GREEK SMALL LETTER SIGMA
                    // U+03B4: "δ" GREEK SMALL LETTER DELTA
                    // U+03C6: "φ" GREEK SMALL LETTER PHI
                    // U+03B3: "γ" GREEK SMALL LETTER GAMMA
                    "\u03C3", "\u03B4", "\u03C6", "\u03B3",
                    // U+03B7: "η" GREEK SMALL LETTER ETA
                    // U+03AE: "ή" GREEK SMALL LETTER ETA WITH TONOS
                    key("\u03B7", moreKey("\u03AE")),
                    // U+03BE: "ξ" GREEK SMALL LETTER XI
                    // U+03BA: "κ" GREEK SMALL LETTER KAPPA
                    // U+03BB: "λ" GREEK SMALL LETTER LAMDA
                    "\u03BE", "\u03BA", "\u03BB")
            .setKeysOfRow(3,
                    // U+03B6: "ζ" GREEK SMALL LETTER ZETA
                    // U+03C7: "χ" GREEK SMALL LETTER CHI
                    // U+03C8: "ψ" GREEK SMALL LETTER PSI
                    "\u03B6", "\u03C7", "\u03C8",
                    // U+03C9: "ω" GREEK SMALL LETTER OMEGA
                    // U+03CE: "ώ" GREEK SMALL LETTER OMEGA WITH TONOS
                    key("\u03C9", moreKey("\u03CE")),
                    // U+03B2: "β" GREEK SMALL LETTER BETA
                    // U+03BD: "ν" GREEK SMALL LETTER NU
                    // U+03BC: "μ" GREEK SMALL LETTER MU
                    "\u03B2", "\u03BD", "\u03BC")
            .build();
}
