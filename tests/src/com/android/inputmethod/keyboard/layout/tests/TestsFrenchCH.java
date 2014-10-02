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
import com.android.inputmethod.keyboard.layout.Swiss;
import com.android.inputmethod.keyboard.layout.customizer.FrenchCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

/**
 * fr_CH: French (Switzerland)/swiss
 */
@SmallTest
public final class TestsFrenchCH extends LayoutTestsBase {
    private static final Locale LOCALE = new Locale("fr", "CH");
    private static final LayoutBase LAYOUT = new Swiss(new FrenchCHCustomizer(LOCALE));

    @Override
    LayoutBase getLayout() { return LAYOUT; }

    private static class FrenchCHCustomizer extends FrenchCustomizer {
        FrenchCHCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKeyboardBuilder setAccentedLetters(final ExpectedKeyboardBuilder builder) {
            super.setAccentedLetters(builder);
            return builder
                    // U+00E8: "è" LATIN SMALL LETTER E WITH GRAVE
                    // U+00FC: "ü" LATIN SMALL LETTER U WITH DIAERESIS
                    .replaceKeyOfLabel(Swiss.ROW1_11, key("\u00E8", moreKey("\u00FC")))
                    // U+00E9: "é" LATIN SMALL LETTER E WITH ACUTE
                    // U+00F6: "ö" LATIN SMALL LETTER O WITH DIAERESIS
                    .replaceKeyOfLabel(Swiss.ROW2_10, key("\u00E9", moreKey("\u00F6")))
                    // U+00E0: "à" LATIN SMALL LETTER A WITH GRAVE
                    // U+00E4: "ä" LATIN SMALL LETTER A WITH DIAERESIS
                    .replaceKeyOfLabel(Swiss.ROW2_11, key("\u00E0", moreKey("\u00E4")));
        }
    }
}
