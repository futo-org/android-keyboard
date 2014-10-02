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
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey.ExpectedAdditionalMoreKey;

import java.util.Locale;

public class DvorakCustomizer extends LayoutCustomizer {
    public DvorakCustomizer(final Locale locale) { super(locale); }

    @Override
    public ExpectedKey[] getLeftShiftKeys(final boolean isPhone) {
        return isPhone ? joinKeys(SHIFT_KEY): joinKeys(SHIFT_KEY, key("q"));
    }

    @Override
    public ExpectedKey[] getRightShiftKeys(final boolean isPhone) {
        return isPhone ? EMPTY_KEYS : joinKeys(key("z"), SHIFT_KEY);
    }

    @Override
    public ExpectedKey[] getKeysLeftToSpacebar(final boolean isPhone) {
        // U+00A1: "¡" INVERTED EXCLAMATION MARK
        return isPhone ? joinKeys(key("q", SETTINGS_KEY))
                : joinKeys(key("!", joinMoreKeys("\u00A1", SETTINGS_KEY)));
    }

    @Override
    public ExpectedKey[] getKeysRightToSpacebar(final boolean isPhone) {
        final ExpectedAdditionalMoreKey[] punctuationMoreKeys =
                convertToAdditionalMoreKeys(getPunctuationMoreKeys(isPhone));
        // U+00BF: "¿" INVERTED QUESTION MARK
        return isPhone
                ? joinKeys(key("z", punctuationMoreKeys))
                : joinKeys(key("?", joinMoreKeys(punctuationMoreKeys, "\u00BF")));
    }

    private static ExpectedAdditionalMoreKey[] convertToAdditionalMoreKeys(
            final ExpectedKey ... moreKeys) {
        final ExpectedAdditionalMoreKey[] additionalMoreKeys =
                new ExpectedAdditionalMoreKey[moreKeys.length];
        for (int index = 0; index < moreKeys.length; index++) {
            additionalMoreKeys[index] = ExpectedAdditionalMoreKey.newInstance(moreKeys[index]);
        }
        return additionalMoreKeys;
    }

    public static class EnglishDvorakCustomizer extends DvorakCustomizer {
        private final EnglishCustomizer mEnglishCustomizer;

        public EnglishDvorakCustomizer(final Locale locale) {
            super(locale);
            mEnglishCustomizer = new EnglishCustomizer(locale);
        }

        @Override
        public ExpectedKeyboardBuilder setAccentedLetters(final ExpectedKeyboardBuilder builder) {
            return mEnglishCustomizer.setAccentedLetters(builder);
        }
    }
}
