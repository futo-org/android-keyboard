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

package com.android.inputmethod.keyboard.layout.customizer;

import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;

import java.util.Locale;

public class PcQwertyCustomizer extends LayoutCustomizer {
    public PcQwertyCustomizer(final Locale locale) { super(locale); }

    @Override
    public int getNumberOfRows() { return 5; }

    @Override
    public ExpectedKey[] getLeftShiftKeys(final boolean isPhone) {
        return joinKeys(SHIFT_KEY);
    }

    @Override
    public ExpectedKey[] getRightShiftKeys(final boolean isPhone) {
        return joinKeys(SHIFT_KEY);
    }

    @Override
    public ExpectedKey[] getKeysLeftToSpacebar(final boolean isPhone) {
        return joinKeys(SETTINGS_KEY);
    }

    @Override
    public ExpectedKey[] getKeysRightToSpacebar(final boolean isPhone) {
        return isPhone
                ? joinKeys(key(ENTER_KEY, EMOJI_ACTION_KEY))
                : joinKeys(EMOJI_NORMAL_KEY);
    }
}
