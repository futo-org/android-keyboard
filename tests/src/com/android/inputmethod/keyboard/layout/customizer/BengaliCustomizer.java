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

import com.android.inputmethod.keyboard.layout.SymbolsShifted;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.latin.common.Constants;

import java.util.Locale;

public class BengaliCustomizer extends LayoutCustomizer {
    public BengaliCustomizer(final Locale locale) { super(locale); }

    @Override
    public ExpectedKey getAlphabetKey() { return BENGALI_ALPHABET_KEY; }

    @Override
    public ExpectedKey[] getOtherCurrencyKeys() {
        return SymbolsShifted.CURRENCIES_OTHER_GENERIC;
    }

    @Override
    public ExpectedKey[] getRightShiftKeys(final boolean isPhone) {
        return isPhone ? EMPTY_KEYS : EXCLAMATION_AND_QUESTION_MARKS;
    }

    // U+0995: "क" BENGALI LETTER KA
    // U+0996: "ख" BENGALI LETTER KHA
    // U+0997: "ग" BENGALI LETTER GA
    private static final ExpectedKey BENGALI_ALPHABET_KEY = key(
            "\u0995\u0996\u0997", Constants.CODE_SWITCH_ALPHA_SYMBOL);
}
