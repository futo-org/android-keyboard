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

import com.android.inputmethod.keyboard.internal.KeyboardIconsSet;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;
import com.android.inputmethod.keyboard.layout.expected.LayoutBase;
import com.android.inputmethod.latin.Constants;

import java.util.Locale;

/**
 * The generic upper case alphabet keyboard layout.
 */
public final class AlphabetShifted extends LayoutBase {
    public static ExpectedKey[][] getAlphabet(final ExpectedKey[][] lowerCaseKeyboard,
            final Locale locale) {
        final ExpectedKey[][] upperCaseKeyboard = ExpectedKeyboardBuilder.toUpperCase(
                lowerCaseKeyboard, locale);
        return new ExpectedKeyboardBuilder(upperCaseKeyboard)
                .replaceKeyOfAll(SHIFT_KEY, SHIFTED_SHIFT_KEY)
                .build();
    }

    // Icon id.
    private static final int ICON_SHIFTED_SHIFT = KeyboardIconsSet.getIconId("shift_key_shifted");

    // Functional key.
    private static final ExpectedKey SHIFTED_SHIFT_KEY = key(
            ICON_SHIFTED_SHIFT, Constants.CODE_SHIFT, CAPSLOCK_MORE_KEY);
}
