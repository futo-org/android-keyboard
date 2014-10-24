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

public class DevanagariCustomizer extends LayoutCustomizer {
    public DevanagariCustomizer(final Locale locale) { super(locale); }

    @Override
    public ExpectedKey getAlphabetKey() { return HINDI_ALPHABET_KEY; }

    @Override
    public ExpectedKey getSymbolsKey() { return HINDI_SYMBOLS_KEY; }

    @Override
    public ExpectedKey getBackToSymbolsKey() { return HINDI_BACK_TO_SYMBOLS_KEY; }

    // U+0915: "क" DEVANAGARI LETTER KA
    // U+0916: "ख" DEVANAGARI LETTER KHA
    // U+0917: "ग" DEVANAGARI LETTER GA
    private static final ExpectedKey HINDI_ALPHABET_KEY = key(
            "\u0915\u0916\u0917", Constants.CODE_SWITCH_ALPHA_SYMBOL);
    // U+0967: "१" DEVANAGARI DIGIT ONE
    // U+0968: "२" DEVANAGARI DIGIT TWO
    // U+0969: "३" DEVANAGARI DIGIT THREE
    private static final String HINDI_SYMBOLS_LABEL = "?\u0967\u0968\u0969";
    private static final ExpectedKey HINDI_SYMBOLS_KEY = key(HINDI_SYMBOLS_LABEL,
            Constants.CODE_SWITCH_ALPHA_SYMBOL);
    private static final ExpectedKey HINDI_BACK_TO_SYMBOLS_KEY = key(HINDI_SYMBOLS_LABEL,
            Constants.CODE_SHIFT);
}
