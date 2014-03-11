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

/**
 * The QWERTY alphabet keyboard.
 */
public final class Qwerty extends LayoutBase {
    public static final String LAYOUT_NAME = "qwerty";

    public static ExpectedKey[][] getLayout(final boolean isPhone) {
        return getDefaultAlphabetLayout(ALPHABET_COMMON, isPhone);
    }

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder(10, 9, 7, 3)
            .setLabelsOfRow(1, "q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
            .setMoreKeysOf("q", "1")
            .setMoreKeysOf("w", "2")
            .setMoreKeysOf("e", "3")
            .setMoreKeysOf("r", "4")
            .setMoreKeysOf("t", "5")
            .setMoreKeysOf("y", "6")
            .setMoreKeysOf("u", "7")
            .setMoreKeysOf("i", "8")
            .setMoreKeysOf("o", "9")
            .setMoreKeysOf("p", "0")
            .setLabelsOfRow(2, "a", "s", "d", "f", "g", "h", "j", "k", "l")
            .setLabelsOfRow(3, "z", "x", "c", "v", "b", "n", "m")
            .build();
}
