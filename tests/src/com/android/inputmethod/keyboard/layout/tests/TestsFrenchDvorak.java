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

import android.test.suitebuilder.annotation.SmallTest;

import com.android.inputmethod.keyboard.layout.Dvorak;
import com.android.inputmethod.keyboard.layout.LayoutBase;
import com.android.inputmethod.keyboard.layout.customizer.DvorakCustomizer;
import com.android.inputmethod.keyboard.layout.customizer.FrenchCustomizer.FrenchEuroCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

/**
 * fr: French/dvorak
 */
@SmallTest
public final class TestsFrenchDvorak extends LayoutTestsBase {
    private static final Locale LOCALE = new Locale("fr");
    private static final LayoutBase LAYOUT = new Dvorak(new FrenchDvorakCustomizer(LOCALE));

    @Override
    LayoutBase getLayout() { return LAYOUT; }

    private static class FrenchDvorakCustomizer extends DvorakCustomizer {
        private final FrenchEuroCustomizer mFrenchEuroCustomizer;

        FrenchDvorakCustomizer(final Locale locale) {
            super(locale);
            mFrenchEuroCustomizer = new FrenchEuroCustomizer(locale);
        }

        @Override
        public ExpectedKey getCurrencyKey() { return mFrenchEuroCustomizer.getCurrencyKey(); }

        @Override
        public ExpectedKey[] getOtherCurrencyKeys() {
            return mFrenchEuroCustomizer.getOtherCurrencyKeys();
        }

        @Override
        public ExpectedKeyboardBuilder setAccentedLetters(final ExpectedKeyboardBuilder builder) {
            return mFrenchEuroCustomizer.setAccentedLetters(builder);
        }
    }
}