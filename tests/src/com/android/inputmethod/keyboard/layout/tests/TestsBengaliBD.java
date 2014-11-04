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

import com.android.inputmethod.keyboard.layout.BengaliAkkhor;
import com.android.inputmethod.keyboard.layout.LayoutBase;
import com.android.inputmethod.keyboard.layout.Symbols;
import com.android.inputmethod.keyboard.layout.customizer.BengaliCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;

import java.util.Locale;

/**
 * bn_BD: Bengali (Bangladesh)/bengali_akkhor
 */
@SmallTest
public final class TestsBengaliBD extends LayoutTestsBase {
    private static final Locale LOCALE = new Locale("bn", "BD");
    private static final LayoutBase LAYOUT = new BengaliAkkhor(new BengaliBDCustomzier(LOCALE));

    @Override
    LayoutBase getLayout() { return LAYOUT; }

    private static class BengaliBDCustomzier extends BengaliCustomizer {
        BengaliBDCustomzier(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey[] getRightShiftKeys(final boolean isPhone) {
            return isPhone ? EMPTY_KEYS : EXCLAMATION_AND_QUESTION_MARKS;
        }

        @Override
        public ExpectedKey[] getSpaceKeys(final boolean isPhone) {
            return joinKeys(LANGUAGE_SWITCH_KEY, SPACE_KEY, key(ZWNJ_KEY, ZWJ_KEY));
        }

        @Override
        public ExpectedKey getCurrencyKey() { return CURRENCY_RUPEE; }

        // U+09F3: "৳" BENGALI RUPEE SIGN
        private static final ExpectedKey CURRENCY_RUPEE = key("\u09F3",
                Symbols.CURRENCY_GENERIC_MORE_KEYS);
    }
}
