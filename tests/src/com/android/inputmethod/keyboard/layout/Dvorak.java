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
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey.ExpectedAdditionalMoreKey;
import com.android.inputmethod.latin.settings.CustomInputStyleSettingsFragment;

import java.util.Locale;

/**
 * The Dvorak alphabet keyboard.
 */
public class Dvorak extends LayoutBase {
    private static final String LAYOUT_NAME = "dvorak";

    public Dvorak(final LayoutCustomizer customizer) {
        super(customizer, Symbols.class, SymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    public static class DvorakCustomizer extends LayoutCustomizer {
        public DvorakCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey[] getLeftShiftKeys(final boolean isPhone) {
            return isPhone ? joinKeys(SHIFT_KEY): joinKeys(SHIFT_KEY, key("q"));
        }

        @Override
        public ExpectedKey[] getRightShiftKeys(final boolean isPhone) {
            return isPhone ? EMPTY_KEYS : joinKeys(key("z"), SHIFT_KEY);
        }

        @Override
        public ExpectedKey[] getKeysLeftToSpacebar(final boolean isPhone) {
            // U+00A1: "¡" INVERTED EXCLAMATION MARK
            return isPhone ? joinKeys(key("q", SETTINGS_KEY))
                    : joinKeys(key("!", joinMoreKeys("\u00A1", SETTINGS_KEY)));
        }

        @Override
        public ExpectedKey[] getKeysRightToSpacebar(final boolean isPhone) {
            final ExpectedAdditionalMoreKey[] punctuationMoreKeys =
                    convertToAdditionalMoreKeys(getPunctuationMoreKeys(isPhone));
            // U+00BF: "¿" INVERTED QUESTION MARK
            return isPhone
                    ? joinKeys(key("z", punctuationMoreKeys))
                    : joinKeys(key("?", joinMoreKeys(punctuationMoreKeys, "\u00BF")));
        }

        private static ExpectedAdditionalMoreKey[] convertToAdditionalMoreKeys(
                final ExpectedKey ... moreKeys) {
            final ExpectedAdditionalMoreKey[] additionalMoreKeys =
                    new ExpectedAdditionalMoreKey[moreKeys.length];
            for (int index = 0; index < moreKeys.length; index++) {
                additionalMoreKeys[index] = ExpectedAdditionalMoreKey.newInstance(moreKeys[index]);
            }
            return additionalMoreKeys;
        }
    }

    @Override
    public ExpectedKey[][] getCommonAlphabetLayout(final boolean isPhone) {
        return ALPHABET_COMMON;
    }

    protected ExpectedKey getRow1_1Key(final boolean isPhone, final int elementId) {
        if (elementId == KeyboardId.ELEMENT_ALPHABET
                || elementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED) {
            return key("'", joinMoreKeys(additionalMoreKey("1"), "!", "\""));
        }
        return key("\"", additionalMoreKey("1"));
    }

    protected ExpectedKey getRow1_2Key(final boolean isPhone, final int elementId) {
        if (elementId == KeyboardId.ELEMENT_ALPHABET
                || elementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED) {
            return key(",", joinMoreKeys(additionalMoreKey("2"), "?", "<"));
        }
        return key("<", additionalMoreKey("2"));
    }

    protected ExpectedKey getRow1_3Key(final boolean isPhone, final int elementId) {
        if (elementId == KeyboardId.ELEMENT_ALPHABET
                || elementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED) {
            return key(".", joinMoreKeys(additionalMoreKey("3"), ">"));
        }
        return key(">", additionalMoreKey("3"));
    }

    @Override
    public ExpectedKey[][] getLayout(final boolean isPhone, final int elementId) {
        if (elementId == KeyboardId.ELEMENT_SYMBOLS
                || elementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED) {
            return super.getLayout(isPhone, elementId);
        }
        final ExpectedKeyboardBuilder builder = new ExpectedKeyboardBuilder(
                getCommonAlphabetLayout(isPhone));
        builder.replaceKeyOfLabel(ROW1_1, getRow1_1Key(isPhone, elementId))
                .replaceKeyOfLabel(ROW1_2, getRow1_2Key(isPhone, elementId))
                .replaceKeyOfLabel(ROW1_3, getRow1_3Key(isPhone, elementId));
        convertCommonLayoutToKeyboard(builder, isPhone);
        getCustomizer().setAccentedLetters(builder);
        if (elementId != KeyboardId.ELEMENT_ALPHABET) {
            builder.toUpperCase(getLocale());
            builder.replaceKeysOfAll(SHIFT_KEY, SHIFTED_SHIFT_KEY);
        }
        return builder.build();
    }

    public static final String ROW1_1 = "ROW1_1";
    public static final String ROW1_2 = "ROW1_2";
    public static final String ROW1_3 = "ROW1_3";

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    ROW1_1, ROW1_2, ROW1_3,
                    key("p", additionalMoreKey("4")),
                    key("y", additionalMoreKey("5")),
                    key("f", additionalMoreKey("6")),
                    key("g", additionalMoreKey("7")),
                    key("c", additionalMoreKey("8")),
                    key("r", additionalMoreKey("9")),
                    key("l", additionalMoreKey("0")))
            .setKeysOfRow(2, "a", "o", "e", "u", "i", "d", "h", "t", "n", "s")
            .setKeysOfRow(3, "j", "k", "x", "b", "m", "w", "v")
            .build();
}
