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

public class TamilCustomizer extends LayoutCustomizer {
    public TamilCustomizer(final Locale locale) { super(locale); }

    @Override
    public ExpectedKey getAlphabetKey() { return TAMIL_ALPHABET_KEY; }

    @Override
    public ExpectedKey[] getLeftShiftKeys(final boolean isPhone) {
        return EMPTY_KEYS;
    }

    @Override
    public ExpectedKey[] getRightShiftKeys(final boolean isPhone) {
        return isPhone ? EMPTY_KEYS : EXCLAMATION_AND_QUESTION_MARKS;
    }

    // U+0BA4: "த" TAMIL LETTER TA
    // U+0BAE/U+0BBF: "மி" TAMIL LETTER MA/TAMIL VOWEL SIGN I
    // U+0BB4/U+0BCD: "ழ்" TAMIL LETTER LLLA/TAMIL SIGN VIRAMA
    private static final ExpectedKey TAMIL_ALPHABET_KEY = key(
            "\u0BA4\u0BAE\u0BBF\u0BB4\u0BCD", Constants.CODE_SWITCH_ALPHA_SYMBOL);
}
