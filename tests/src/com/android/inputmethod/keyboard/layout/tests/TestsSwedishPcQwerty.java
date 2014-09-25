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
import com.android.inputmethod.keyboard.layout.LayoutBase.LayoutCustomizer;
import com.android.inputmethod.keyboard.layout.PcQwerty;
import com.android.inputmethod.keyboard.layout.PcQwerty.PcQwertyCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

/**
 * sv: Swedish/pcqwerty
 */
@SmallTest
public final class TestsSwedishPcQwerty extends LayoutTestsBase {
    private static final Locale LOCALE = new Locale("sv");
    private static final LayoutBase LAYOUT = new PcQwerty(new SwedishPcQwertyCustomizer(LOCALE));

    @Override
    LayoutBase getLayout() { return LAYOUT; }

    private static class SwedishPcQwertyCustomizer extends SwedishCustomizer {
        private final LayoutCustomizer mPcQwertyCustomizer;

        public SwedishPcQwertyCustomizer(final Locale locale) {
            super(locale);
            mPcQwertyCustomizer = new PcQwertyCustomizer(locale);
        }

        @Override
        public ExpectedKey getCurrencyKey() {
            return mPcQwertyCustomizer.getCurrencyKey();
        }

        @Override
        public ExpectedKey[] getOtherCurrencyKeys() {
            return mPcQwertyCustomizer.getOtherCurrencyKeys();
        }

        @Override
        public int getNumberOfRows() {
            return mPcQwertyCustomizer.getNumberOfRows();
        }

        @Override
        public ExpectedKey[] getLeftShiftKeys(final boolean isPhone) {
            return mPcQwertyCustomizer.getLeftShiftKeys(isPhone);
        }

        @Override
        public ExpectedKey[] getRightShiftKeys(final boolean isPhone) {
            return mPcQwertyCustomizer.getRightShiftKeys(isPhone);
        }

        @Override
        public ExpectedKey[] getKeysLeftToSpacebar(final boolean isPhone) {
            return mPcQwertyCustomizer.getKeysLeftToSpacebar(isPhone);
        }

        @Override
        public ExpectedKey[] getKeysRightToSpacebar(final boolean isPhone) {
            return mPcQwertyCustomizer.getKeysRightToSpacebar(isPhone);
        }

        @Override
        protected void setNordicKeys(final ExpectedKeyboardBuilder builder) {
            // PC QWERTY layout doesn't have Nordic keys.
        }
    }
}
