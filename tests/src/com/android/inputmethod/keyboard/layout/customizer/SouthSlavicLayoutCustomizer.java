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
import com.android.inputmethod.latin.common.Constants;

import java.util.Locale;

public class SouthSlavicLayoutCustomizer extends LayoutCustomizer {
    public SouthSlavicLayoutCustomizer(final Locale locale) {
        super(locale);
    }

    @Override
    public final ExpectedKey getAlphabetKey() { return SOUTH_SLAVIC_ALPHABET_KEY; }

    @Override
    public ExpectedKey[] getRightShiftKeys(final boolean isPhone) {
        return isPhone ? EMPTY_KEYS : EXCLAMATION_AND_QUESTION_MARKS;
    }

    // U+0410: "А" CYRILLIC CAPITAL LETTER A
    // U+0411: "Б" CYRILLIC CAPITAL LETTER BE
    // U+0412: "В" CYRILLIC CAPITAL LETTER VE
    private static final ExpectedKey SOUTH_SLAVIC_ALPHABET_KEY = key(
            "\u0410\u0411\u0412", Constants.CODE_SWITCH_ALPHA_SYMBOL);
}
