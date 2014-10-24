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
import com.android.inputmethod.latin.common.Constants;

import java.util.Locale;

/**
 * The Armenian Phonetic alphabet keyboard.
 */
public final class ArmenianPhonetic extends LayoutBase {
    private static final String LAYOUT_NAME = "armenian_phonetic";

    public ArmenianPhonetic(final Locale locale) {
        super(new ArmenianPhoneticCustomizer(locale), ArmenianSymbols.class,
                ArmenianSymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    private static class ArmenianPhoneticCustomizer extends LayoutCustomizer {
        ArmenianPhoneticCustomizer(final Locale locale) { super(locale); }

        @Override
        public int getNumberOfRows() { return 5; }

        @Override
        public ExpectedKey getAlphabetKey() { return ARMENIAN_ALPHABET_KEY; }

        @Override
        public ExpectedKey[] getRightShiftKeys(final boolean isPhone) {
            if (isPhone) {
                return EMPTY_KEYS;
            }
            // U+055C: "՜" ARMENIAN EXCLAMATION MARK
            // U+00A1: "¡" INVERTED EXCLAMATION MARK
            // U+055E: "՞" ARMENIAN QUESTION MARK
            // U+00BF: "¿" INVERTED QUESTION MARK
            return joinKeys(key("!", joinMoreKeys("\u055C", "\u00A1")),
                    key("?", joinMoreKeys("\u055E", "\u00BF")),
                    SHIFT_KEY);
        }

        @Override
        public ExpectedKey[] getKeysLeftToSpacebar(final boolean isPhone) {
            // U+055D: "՝" ARMENIAN COMMA
            return isPhone ? joinKeys(key("\u055D", SETTINGS_KEY))
                    : joinKeys(key("\u055D", SETTINGS_KEY));
        }

        @Override
        public ExpectedKey[] getKeysRightToSpacebar(final boolean isPhone) {
            // U+0589: "։" ARMENIAN FULL STOP
            final ExpectedKey fullStopKey = key("\u0589", getPunctuationMoreKeys(isPhone));
            return joinKeys(fullStopKey);
        }

        @Override
        public ExpectedKey[] getPunctuationMoreKeys(final boolean isPhone) {
            return ARMENIAN_PUNCTUATION_MORE_KEYS;
        }

        // U+0531: "Ա" ARMENIAN CAPITAL LETTER AYB
        // U+0532: "Բ" ARMENIAN CAPITAL LETTER BEN
        // U+0533: "Գ" ARMENIAN CAPITAL LETTER GIM
        private static final ExpectedKey ARMENIAN_ALPHABET_KEY = key(
                "\u0531\u0532\u0533", Constants.CODE_SWITCH_ALPHA_SYMBOL);

        // U+055E: "՞" ARMENIAN QUESTION MARK
        // U+055C: "՜" ARMENIAN EXCLAMATION MARK
        // U+055A: "՚" ARMENIAN APOSTROPHE
        // U+0559: "ՙ" ARMENIAN MODIFIER LETTER LEFT HALF RING
        // U+055D: "՝" ARMENIAN COMMA
        // U+055B: "՛" ARMENIAN EMPHASIS MARK
        // U+058A: "֊" ARMENIAN HYPHEN
        // U+00BB: "»" RIGHT-POINTING DOUBLE ANGLE QUOTATION MARK
        // U+00AB: "«" LEFT-POINTING DOUBLE ANGLE QUOTATION MARK
        // U+055F: "՟" ARMENIAN ABBREVIATION MARK
        private static final ExpectedKey[] ARMENIAN_PUNCTUATION_MORE_KEYS = joinMoreKeys(
                ",", "\u055E", "\u055C", ".", "\u055A", "\u0559", "?", "!",
                "\u055D", "\u055B", "\u058A", "\u00BB", "\u00AB", "\u055F", ";", ":");
    }

    @Override
    ExpectedKey[][] getCommonAlphabetLayout(final boolean isPhone) {
        final ExpectedKeyboardBuilder builder = new ExpectedKeyboardBuilder(ALPHABET_COMMON);
        if (isPhone) {
            // U+056D: "խ" ARMENIAN SMALL LETTER XEH
            // U+0577: "շ" ARMENIAN SMALL LETTER SHA
            builder.addKeysOnTheRightOfRow(3, "\u056D")
                    .addKeysOnTheRightOfRow(4, "\u0577");
        } else {
            // U+056D: "խ" ARMENIAN SMALL LETTER XEH
            // U+0577: "շ" ARMENIAN SMALL LETTER SHA
            builder.addKeysOnTheRightOfRow(2, "\u056D")
                    .addKeysOnTheRightOfRow(3, "\u0577");
        }
        return builder.build();
    }

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+0567: "է" ARMENIAN SMALL LETTER EH
                    key("\u0567", moreKey("1")),
                    // U+0569: "թ" ARMENIAN SMALL LETTER TO
                    key("\u0569", moreKey("2")),
                    // U+0583: "փ" ARMENIAN SMALL LETTER PIWR
                    key("\u0583", moreKey("3")),
                    // U+0571: "ձ" ARMENIAN SMALL LETTER JA
                    key("\u0571", moreKey("4")),
                    // U+057B: "ջ" ARMENIAN SMALL LETTER JHEH
                    key("\u057B", moreKey("5")),
                    // U+0580: "ր" ARMENIAN SMALL LETTER REH
                    key("\u0580", moreKey("6")),
                    // U+0579: "չ" ARMENIAN SMALL LETTER CHA
                    key("\u0579", moreKey("7")),
                    // U+0573: "ճ" ARMENIAN SMALL LETTER CHEH
                    key("\u0573", moreKey("8")),
                    // U+056A: "ժ" ARMENIAN SMALL LETTER ZHE
                    key("\u056A", moreKey("9")),
                    // U+056E: "ծ" ARMENIAN SMALL LETTER CA
                    key("\u056E", moreKey("0")))
            .setKeysOfRow(2,
                    // U+0584: "ք" ARMENIAN SMALL LETTER KEH
                    // U+0578: "ո" ARMENIAN SMALL LETTER VO
                    "\u0584", "\u0578",
                    // U+0565: "ե" ARMENIAN SMALL LETTER ECH
                    // U+0587: "և" ARMENIAN SMALL LIGATURE ECH YIWN
                    key("\u0565", moreKey("\u0587")),
                    // U+057C: "ռ" ARMENIAN SMALL LETTER RA
                    // U+057F: "տ" ARMENIAN SMALL LETTER TIWN
                    // U+0568: "ը" ARMENIAN SMALL LETTER ET
                    // U+0582: "ւ" ARMENIAN SMALL LETTER YIWN
                    // U+056B: "ի" ARMENIAN SMALL LETTER INI
                    // U+0585: "օ" ARMENIAN SMALL LETTER OH
                    // U+057A: "պ" ARMENIAN SMALL LETTER PEH
                    "\u057C", "\u057F", "\u0568", "\u0582", "\u056B", "\u0585", "\u057A")
            .setKeysOfRow(3,
                    // U+0561: "ա" ARMENIAN SMALL LETTER AYB
                    // U+057D: "ս" ARMENIAN SMALL LETTER SEH
                    // U+0564: "դ" ARMENIAN SMALL LETTER DA
                    // U+0586: "ֆ" ARMENIAN SMALL LETTER FEH
                    // U+0563: "գ" ARMENIAN SMALL LETTER GIM
                    // U+0570: "հ" ARMENIAN SMALL LETTER HO
                    // U+0575: "յ" ARMENIAN SMALL LETTER YI
                    // U+056F: "կ" ARMENIAN SMALL LETTER KEN
                    // U+056C: "լ" ARMENIAN SMALL LETTER LIWN
                    "\u0561", "\u057D", "\u0564", "\u0586", "\u0563", "\u0570", "\u0575", "\u056F",
                    "\u056C")
            .setKeysOfRow(4,
                    // U+0566: "զ" ARMENIAN SMALL LETTER ZA
                    // U+0572: "ղ" ARMENIAN SMALL LETTER GHAD
                    // U+0581: "ց" ARMENIAN SMALL LETTER CO
                    // U+057E: "վ" ARMENIAN SMALL LETTER VEW
                    // U+0562: "բ" ARMENIAN SMALL LETTER BEN
                    // U+0576: "ն" ARMENIAN SMALL LETTER NOW
                    // U+0574: "մ" ARMENIAN SMALL LETTER MEN
                    "\u0566", "\u0572", "\u0581", "\u057E", "\u0562", "\u0576", "\u0574")
            .build();

    private static final class ArmenianSymbols extends Symbols {
        public ArmenianSymbols(final LayoutCustomizer customizer) { super(customizer); }

        @Override
        public ExpectedKey[][] getLayout(final boolean isPhone) {
            final ExpectedKeyboardBuilder builder = new ExpectedKeyboardBuilder(
                    super.getLayout(isPhone));
            // U+055D: "՝" ARMENIAN COMMA
            builder.replaceKeyOfLabel(",", "\u055D");
            // U+055C: "՜" ARMENIAN EXCLAMATION MARK
            // U+00A1: "¡" INVERTED EXCLAMATION MARK
            // U+055E: "՞" ARMENIAN QUESTION MARK
            // U+00BF: "¿" INVERTED QUESTION MARK
            builder.setMoreKeysOf("!", "\u055C", "\u00A1")
                    .setMoreKeysOf("?", "\u055E", "\u00BF");
            return builder.build();
        }
    }

    private static final class ArmenianSymbolsShifted extends SymbolsShifted {
        public ArmenianSymbolsShifted(final LayoutCustomizer customizer) { super(customizer); }

        @Override
        public ExpectedKey[][] getLayout(final boolean isPhone) {
            final ExpectedKeyboardBuilder builder = new ExpectedKeyboardBuilder(
                    super.getLayout(isPhone));
            // U+055D: "՝" ARMENIAN COMMA
            builder.replaceKeyOfLabel(",", "\u055D");
            return builder.build();
        }
    }
}
