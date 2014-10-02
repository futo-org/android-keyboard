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
 * The AZERTY alphabet keyboard.
 */
public final class Azerty extends LayoutBase {
    public Azerty(final LayoutCustomizer customizer) {
        super(customizer, Symbols.class, SymbolsShifted.class);
    }

    @Override
    public String getName() { return "azerty"; }

    @Override
    ExpectedKey[][] getCommonAlphabetLayout(final boolean isPhone) {
        final LayoutCustomizer customizer = getCustomizer();
        final ExpectedKeyboardBuilder builder = new ExpectedKeyboardBuilder(ALPHABET_COMMON);
        customizer.setAccentedLetters(builder);
        builder.replaceKeyOfLabel(ROW3_QUOTE, key("'", joinMoreKeys(
                customizer.getSingleQuoteMoreKeys(),
                customizer.getSingleAngleQuoteKeys())));
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
            getCustomizer().setAccentedLetters(builder);
            builder.replaceKeyOfLabel(ROW3_QUOTE, "?");
        }
        builder.toUpperCase(getLocale());
        return builder.build();
    }

    private static final String ROW3_QUOTE = "ROW3_QUOUTE";

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    key("a", additionalMoreKey("1")),
                    key("z", additionalMoreKey("2")),
                    key("e", additionalMoreKey("3")),
                    key("r", additionalMoreKey("4")),
                    key("t", additionalMoreKey("5")),
                    key("y", additionalMoreKey("6")),
                    key("u", additionalMoreKey("7")),
                    key("i", additionalMoreKey("8")),
                    key("o", additionalMoreKey("9")),
                    key("p", additionalMoreKey("0")))
            .setKeysOfRow(2, "q", "s", "d", "f", "g", "h", "j", "k", "l", "m")
            .setKeysOfRow(3, "w", "x", "c", "v", "b", "n", ROW3_QUOTE)
            .build();
}
