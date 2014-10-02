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
 * The Dvorak alphabet keyboard.
 */
public class Dvorak extends LayoutBase {
    private static final String LAYOUT_NAME = "dvorak";

    public Dvorak(final LayoutCustomizer customizer) {
        super(customizer, Symbols.class, SymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    @Override
    public ExpectedKey[][] getCommonAlphabetLayout(final boolean isPhone) {
        return ALPHABET_COMMON;
    }

    /**
     * Get the left most key of the first row.
     * @param isPhone true if requesting phone's keys.
     * @param elementId the element id of the requesting shifted mode.
     * @return the left most key of the first row.
     */
    protected ExpectedKey getRow1_1Key(final boolean isPhone, final int elementId) {
        if (elementId == KeyboardId.ELEMENT_ALPHABET
                || elementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED) {
            return key("'", joinMoreKeys(additionalMoreKey("1"), "!", "\""));
        }
        return key("\"", additionalMoreKey("1"));
    }

    /**
     * Get the 2nd left key of the first row.
     * @param isPhone true if requesting phone's keys.
     * @param elementId the element id of the requesting shifted mode.
     * @return the 2nd left key of the first row.
     */
    protected ExpectedKey getRow1_2Key(final boolean isPhone, final int elementId) {
        if (elementId == KeyboardId.ELEMENT_ALPHABET
                || elementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED) {
            return key(",", joinMoreKeys(additionalMoreKey("2"), "?", "<"));
        }
        return key("<", additionalMoreKey("2"));
    }

    /**
     * Get the 3rd left key of the first row.
     * @param isPhone true if requesting phone's keys.
     * @param elementId the element id of the requesting shifted mode.
     * @return the 3rd left key of the first row.
     */
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
