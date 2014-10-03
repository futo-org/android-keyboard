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
 * The Colemak alphabet keyboard.
 */
public final class Colemak extends LayoutBase {
    private static final String LAYOUT_NAME = "colemak";

    public Colemak(final LayoutCustomizer customizer) {
        super(customizer, Symbols.class, SymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    @Override
    ExpectedKey[][] getCommonAlphabetLayout(final boolean isPhone) {
        final ExpectedKeyboardBuilder builder = new ExpectedKeyboardBuilder(ALPHABET_COMMON);
        getCustomizer().setAccentedLetters(builder);
        builder.replaceKeyOfLabel(ROW1_10, key(";", additionalMoreKey("0"), moreKey(":")));
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
            builder.replaceKeyOfLabel(ROW1_10, key(":", additionalMoreKey("0")));
        }
        builder.toUpperCase(getLocale());
        return builder.build();
    }

    private static final String ROW1_10 = "ROW1_10";

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    key("q", additionalMoreKey("1")),
                    key("w", additionalMoreKey("2")),
                    key("f", additionalMoreKey("3")),
                    key("p", additionalMoreKey("4")),
                    key("g", additionalMoreKey("5")),
                    key("j", additionalMoreKey("6")),
                    key("l", additionalMoreKey("7")),
                    key("u", additionalMoreKey("8")),
                    key("y", additionalMoreKey("9")),
                    ROW1_10)
            .setKeysOfRow(2, "a", "r", "s", "t", "d", "h", "n", "e", "i", "o")
            .setKeysOfRow(3, "z", "x", "c", "v", "b", "k", "m")
            .build();
}
