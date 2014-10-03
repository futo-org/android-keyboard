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

public final class Qwertz extends LayoutBase {
    private static final String LAYOUT_NAME = "qwertz";

    public Qwertz(final LayoutCustomizer customizer) {
        super(customizer, Symbols.class, SymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    @Override
    ExpectedKey[][] getCommonAlphabetLayout(final boolean isPhone) { return ALPHABET_COMMON; }

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    key("q", additionalMoreKey("1")),
                    key("w", additionalMoreKey("2")),
                    key("e", additionalMoreKey("3")),
                    key("r", additionalMoreKey("4")),
                    key("t", additionalMoreKey("5")),
                    key("z", additionalMoreKey("6")),
                    key("u", additionalMoreKey("7")),
                    key("i", additionalMoreKey("8")),
                    key("o", additionalMoreKey("9")),
                    key("p", additionalMoreKey("0")))
            .setKeysOfRow(2, "a", "s", "d", "f", "g", "h", "j", "k", "l")
            .setKeysOfRow(3, "y", "x", "c", "v", "b", "n", "m")
            .build();
}
