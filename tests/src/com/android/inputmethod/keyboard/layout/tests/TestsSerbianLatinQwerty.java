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
import com.android.inputmethod.keyboard.layout.Qwerty;
import com.android.inputmethod.keyboard.layout.customizer.SerbianLatinCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

/**
 * sr_ZZ: Serbian (Latin)/qwerty
 */
@SmallTest
public final class TestsSerbianLatinQwerty extends LayoutTestsBase {
    private static final Locale LOCALE = new Locale("sr", "ZZ");
    private static final LayoutBase LAYOUT = new Qwerty(new SerbianLatinQwertyCustomizer(LOCALE));

    @Override
    LayoutBase getLayout() { return LAYOUT; }

    private static class SerbianLatinQwertyCustomizer extends SerbianLatinCustomizer {
        SerbianLatinQwertyCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey[] getRightShiftKeys(final boolean isPhone) {
            return isPhone ? EMPTY_KEYS
                    : joinKeys(EXCLAMATION_AND_QUESTION_MARKS, SHIFT_KEY);
        }

        @Override
        protected void setSerbianKeys(final ExpectedKeyboardBuilder builder) {
            // QWERTY layout doesn't have Serbian Latin Keys.
        }

        @Override
        protected void setMoreKeysOfS(final ExpectedKeyboardBuilder builder) {
            builder
                    // U+0161: "š" LATIN SMALL LETTER S WITH CARON
                    .setMoreKeysOf("s", "\u0161")
                    .setAdditionalMoreKeysPositionOf("s", 2);
        }

        @Override
        protected void setMoreKeysOfC(final ExpectedKeyboardBuilder builder) {
            builder
                    // U+010D: "č" LATIN SMALL LETTER C WITH CARON
                    // U+0107: "ć" LATIN SMALL LETTER C WITH ACUTE
                    .setMoreKeysOf("c", "\u010D", "\u0107")
                    .setAdditionalMoreKeysPositionOf("c", 3);
        }

        @Override
        protected void setMoreKeysOfD(final ExpectedKeyboardBuilder builder) {
            builder
                    // U+0111: "đ" LATIN SMALL LETTER D WITH STROKE
                    .setMoreKeysOf("d", "\u0111")
                    .setAdditionalMoreKeysPositionOf("d", 2);
        }

        @Override
        protected void setMoreKeysOfZ(final ExpectedKeyboardBuilder builder) {
            builder
                    // U+017E: "ž" LATIN SMALL LETTER Z WITH CARON
                    .setMoreKeysOf("z", "\u017E")
                    .setAdditionalMoreKeysPositionOf("z", 2);
        }
    }
}
