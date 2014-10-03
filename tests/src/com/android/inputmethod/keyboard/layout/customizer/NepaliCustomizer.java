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

import com.android.inputmethod.keyboard.layout.Symbols;
import com.android.inputmethod.keyboard.layout.SymbolsShifted;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;

import java.util.Locale;

public class NepaliCustomizer extends DevanagariCustomizer {
    public NepaliCustomizer(final Locale locale) { super(locale); }

    @Override
    public ExpectedKey getCurrencyKey() { return CURRENCY_NEPALI; }

    @Override
    public ExpectedKey[] getOtherCurrencyKeys() {
        return SymbolsShifted.CURRENCIES_OTHER_GENERIC;
    }

    @Override
    public ExpectedKey[] getSpaceKeys(final boolean isPhone) {
        return joinKeys(LANGUAGE_SWITCH_KEY, SPACE_KEY, key(ZWNJ_KEY, ZWJ_KEY));
    }

    @Override
    public ExpectedKey[] getKeysRightToSpacebar(final boolean isPhone) {
        // U+0964: "।" DEVANAGARI DANDA
        final ExpectedKey periodKey = key("\u0964", getPunctuationMoreKeys(isPhone));
        return joinKeys(periodKey);
    }

    @Override
    public ExpectedKey[] getPunctuationMoreKeys(final boolean isPhone) {
        return isPhone ? NEPALI_PHONE_PUNCTUATION_MORE_KEYS
                : NEPALI_TABLET_PUNCTUATION_MORE_KEYS;
    }

    // U+0930/U+0941/U+002E "रु." NEPALESE RUPEE SIGN
    private static final ExpectedKey CURRENCY_NEPALI = key("\u0930\u0941\u002E",
            Symbols.DOLLAR_SIGN, Symbols.CENT_SIGN, Symbols.EURO_SIGN, Symbols.POUND_SIGN,
            Symbols.YEN_SIGN, Symbols.PESO_SIGN);

    // Punctuation more keys for phone form factor.
    private static final ExpectedKey[] NEPALI_PHONE_PUNCTUATION_MORE_KEYS = joinKeys(
            ".", ",", "?", "!", "#", ")", "(", "/", ";",
            "'", "@", ":", "-", "\"", "+", "%", "&");
    // Punctuation more keys for tablet form factor.
    private static final ExpectedKey[] NEPALI_TABLET_PUNCTUATION_MORE_KEYS = joinKeys(
            ".", ",", "'", "#", ")", "(", "/", ";",
            "@", ":", "-", "\"", "+", "%", "&");
}
