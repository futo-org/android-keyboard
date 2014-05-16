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

package com.android.inputmethod.latin.utils;

import android.content.Context;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.keyboard.MainKeyboardView;

public class DistracterFilterUtils {
    private DistracterFilterUtils() {
        // This utility class is not publicly instantiable.
    }

    public static final DistracterFilter createDistracterFilter(final Context context,
            final KeyboardSwitcher keyboardSwitcher) {
        final MainKeyboardView mainKeyboardView = keyboardSwitcher.getMainKeyboardView();
        // TODO: Create Keyboard when mainKeyboardView is null.
        // TODO: Figure out the most reasonable keyboard for the filter. Refer to the
        // spellchecker's logic.
        final Keyboard keyboard = (mainKeyboardView != null) ?
                mainKeyboardView.getKeyboard() : null;
        final DistracterFilter distracterFilter = new DistracterFilter(context, keyboard);
        return distracterFilter;
    }
}
