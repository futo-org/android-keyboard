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

import com.android.inputmethod.keyboard.layout.SerbianQwertz;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

public class SerbianLatinCustomizer extends LayoutCustomizer {
    public SerbianLatinCustomizer(final Locale locale) { super(locale); }

    @Override
    public ExpectedKey[] getRightShiftKeys(final boolean isPhone) {
        return isPhone ? EMPTY_KEYS : EXCLAMATION_AND_QUESTION_MARKS;
    }

    protected void setSerbianKeys(final ExpectedKeyboardBuilder builder) {
        builder
                // U+0161: "š" LATIN SMALL LETTER S WITH CARON
                .replaceKeyOfLabel(SerbianQwertz.ROW1_11, "\u0161")
                // U+010D: "č" LATIN SMALL LETTER C WITH CARON
                .replaceKeyOfLabel(SerbianQwertz.ROW2_10, "\u010D")
                // U+0107: "ć" LATIN SMALL LETTER C WITH ACUTE
                .replaceKeyOfLabel(SerbianQwertz.ROW2_11, "\u0107")
                // U+0111: "đ" LATIN SMALL LETTER D WITH STROKE
                .replaceKeyOfLabel(SerbianQwertz.ROW3_8, "\u0111")
                // U+017E: "ž" LATIN SMALL LETTER Z WITH CARON
                .replaceKeyOfLabel(SerbianQwertz.ROW3_9, "\u017E");
    }

    @SuppressWarnings("unused")
    protected void setMoreKeysOfS(final ExpectedKeyboardBuilder builder) {
        // Serbian QWERTZ has a dedicated "š" key.
    }

    @SuppressWarnings("unused")
    protected void setMoreKeysOfC(final ExpectedKeyboardBuilder builder) {
        // Serbian QWERTZ has a dedicated "č" and "ć" keys.
    }

    @SuppressWarnings("unused")
    protected void setMoreKeysOfD(final ExpectedKeyboardBuilder builder) {
        // Serbian QWERTZ has a dedicated "đ" key.
    }

    @SuppressWarnings("unused")
    protected void setMoreKeysOfZ(final ExpectedKeyboardBuilder builder) {
        // Serbian QWERTZ has a dedicated "ž" key.
    }

    @Override
    public ExpectedKeyboardBuilder setAccentedLetters(final ExpectedKeyboardBuilder builder) {
        setSerbianKeys(builder);
        setMoreKeysOfS(builder);
        setMoreKeysOfC(builder);
        setMoreKeysOfD(builder);
        setMoreKeysOfZ(builder);
        return builder
                // U+00E8: "è" LATIN SMALL LETTER E WITH GRAVE
                .setMoreKeysOf("e", "\u00E8")
                // U+00EC: "ì" LATIN SMALL LETTER I WITH GRAVE
                .setMoreKeysOf("i", "\u00EC");
    }
}
