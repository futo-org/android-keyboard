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

package com.android.inputmethod.keyboard.layout.tests;

import com.android.inputmethod.keyboard.layout.LayoutBase;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;

import java.util.Locale;

public class HindiCustomizer extends DevanagariCustomizer {
    public HindiCustomizer(final Locale locale) { super(locale); }

    @Override
    public ExpectedKey[] getKeysRightToSpacebar(final boolean isPhone) {
        // U+0964: "।" DEVANAGARI DANDA
        final ExpectedKey periodKey = LayoutBase.key("\u0964", getPunctuationMoreKeys(isPhone));
        return LayoutBase.joinKeys(periodKey);
    }

    @Override
    public ExpectedKey[] getPunctuationMoreKeys(final boolean isPhone) {
        return isPhone ? HINDI_PHONE_PUNCTUATION_MORE_KEYS : HINDI_TABLET_PUNCTUATION_MORE_KEYS;
    }

    // Punctuation more keys for phone form factor.
    private static final ExpectedKey[] HINDI_PHONE_PUNCTUATION_MORE_KEYS = LayoutBase.joinKeys(
            ",", ".", "?", "!", "#", ")", "(", "/", ";",
            "'", "@", ":", "-", "\"", "+", "%", "&");
    // Punctuation more keys for tablet form factor.
    private static final ExpectedKey[] HINDI_TABLET_PUNCTUATION_MORE_KEYS = LayoutBase.joinKeys(
            ",", ".", "'", "#", ")", "(", "/", ";",
            "@", ":", "-", "\"", "+", "%", "&");
}
