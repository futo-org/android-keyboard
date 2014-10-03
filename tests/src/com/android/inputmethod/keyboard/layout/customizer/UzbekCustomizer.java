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

import com.android.inputmethod.keyboard.layout.Nordic;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

public class UzbekCustomizer extends TurkicCustomizer {
    public UzbekCustomizer(final Locale locale) { super(locale); }

    protected void setUzbekKeys(final ExpectedKeyboardBuilder builder) {
        builder
                // U+006F/U+02BB: "oʻ" LATIN SMALL LETTER O/MODIFIER LETTER TURNED COMMA
                .replaceKeyOfLabel(Nordic.ROW1_11, "o\u02BB")
                // U+0067/U+02BB: "gʻ" LATIN SMALL LETTER G/MODIFIER LETTER TURNED COMMA
                .replaceKeyOfLabel(Nordic.ROW2_10, "g\u02BB")
                // U+02BC: "ʼ" MODIFIER LETTER APOSTROPHE
                .replaceKeyOfLabel(Nordic.ROW2_11, "\u02BC");
    }

    @Override
    public ExpectedKeyboardBuilder setAccentedLetters(final ExpectedKeyboardBuilder builder) {
        setUzbekKeys(builder);
        return super.setAccentedLetters(builder);
    }
}
