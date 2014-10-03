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

import com.android.inputmethod.keyboard.layout.LayoutBase;
import com.android.inputmethod.keyboard.layout.Spanish;
import com.android.inputmethod.keyboard.layout.customizer.EuroCustomizer;
import com.android.inputmethod.keyboard.layout.customizer.SpanishCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;

import java.util.Locale;

/**
 * es: Spanish/spanish
 */
@SmallTest
public class TestsSpanish extends LayoutTestsBase {
    private static final Locale LOCALE = new Locale("es");
    private static final LayoutBase LAYOUT = new Spanish(new SpanishESCustomizer(LOCALE));

    @Override
    LayoutBase getLayout() { return LAYOUT; }

    private static class SpanishESCustomizer extends SpanishCustomizer {
        private final EuroCustomizer mEuroCustomizer;

        SpanishESCustomizer(final Locale locale) {
            super(locale);
            mEuroCustomizer = new EuroCustomizer(locale);
        }

        @Override
        public ExpectedKey getCurrencyKey() { return mEuroCustomizer.getCurrencyKey(); }

        @Override
        public ExpectedKey[] getOtherCurrencyKeys() {
            return mEuroCustomizer.getOtherCurrencyKeys();
        }
    }
}
