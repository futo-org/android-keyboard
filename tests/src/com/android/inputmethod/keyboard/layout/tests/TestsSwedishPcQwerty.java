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
import com.android.inputmethod.keyboard.layout.PcQwerty;
import com.android.inputmethod.keyboard.layout.customizer.LayoutCustomizer;
import com.android.inputmethod.keyboard.layout.customizer.PcQwertyCustomizer;
import com.android.inputmethod.keyboard.layout.customizer.SwedishCustomizer;
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

        SwedishPcQwertyCustomizer(final Locale locale) {
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

        @Override
        protected void setMoreKeysOfA(final ExpectedKeyboardBuilder builder) {
            builder
                    // U+00E4: "ä" LATIN SMALL LETTER A WITH DIAERESIS
                    // U+00E5: "å" LATIN SMALL LETTER A WITH RING ABOVE
                    // U+00E6: "æ" LATIN SMALL LETTER AE
                    // U+00E1: "á" LATIN SMALL LETTER A WITH ACUTE
                    // U+00E0: "à" LATIN SMALL LETTER A WITH GRAVE
                    // U+00E2: "â" LATIN SMALL LETTER A WITH CIRCUMFLEX
                    // U+0105: "ą" LATIN SMALL LETTER A WITH OGONEK
                    // U+00E3: "ã" LATIN SMALL LETTER A WITH TILDE
                    .setMoreKeysOf("a", "\u00E4", "\u00E5", "\u00E6", "\u00E1", "\u00E0", "\u00E2",
                            "\u0105", "\u00E3");
        }

        @Override
        protected void setMoreKeysOfO(final ExpectedKeyboardBuilder builder) {
            builder
                    // U+00F6: "ö" LATIN SMALL LETTER O WITH DIAERESIS
                    // U+00F8: "ø" LATIN SMALL LETTER O WITH STROKE
                    // U+0153: "œ" LATIN SMALL LIGATURE OE
                    // U+00F3: "ó" LATIN SMALL LETTER O WITH ACUTE
                    // U+00F2: "ò" LATIN SMALL LETTER O WITH GRAVE
                    // U+00F4: "ô" LATIN SMALL LETTER O WITH CIRCUMFLEX
                    // U+00F5: "õ" LATIN SMALL LETTER O WITH TILDE
                    // U+014D: "ō" LATIN SMALL LETTER O WITH MACRON
                    .setMoreKeysOf("o", "\u00F6", "\u00F8", "\u0153", "\u00F3", "\u00F2", "\u00F4",
                            "\u00F5", "\u014D");
        }

    }
}
