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

import com.android.inputmethod.keyboard.layout.EastSlavic;
import com.android.inputmethod.keyboard.layout.LayoutBase;
import com.android.inputmethod.keyboard.layout.customizer.EastSlavicCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

/**
 * kk: Kazakh/east_slavic
 */
@SmallTest
public final class TestsKazakh extends LayoutTestsBase {
    private static final Locale LOCALE = new Locale("kk");
    private static final LayoutBase LAYOUT = new EastSlavic(new KazakhCustomizer(LOCALE));

    @Override
    LayoutBase getLayout() { return LAYOUT; }

    private static class KazakhCustomizer extends EastSlavicCustomizer {
        KazakhCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKeyboardBuilder setAccentedLetters(final ExpectedKeyboardBuilder builder) {
            return builder
                    // U+0443: "у" CYRILLIC SMALL LETTER U
                    // U+04AF: "ү" CYRILLIC SMALL LETTER STRAIGHT U
                    // U+04B1: "ұ" CYRILLIC SMALL LETTER STRAIGHT U WITH STROKE
                    .setMoreKeysOf("\u0443", "\u04AF", "\u04B1")
                    // U+043A: "к" CYRILLIC SMALL LETTER KA
                    // U+049B: "қ" CYRILLIC SMALL LETTER KA WITH DESCENDER
                    .setMoreKeysOf("\u043A", "\u049B")
                    // U+0435: "е" CYRILLIC SMALL LETTER IE
                    // U+0451: "ё" CYRILLIC SMALL LETTER IO
                    .setMoreKeysOf("\u0435", "\u0451")
                    // U+043D: "н" CYRILLIC SMALL LETTER EN
                    // U+04A3: "ң" CYRILLIC SMALL LETTER EN WITH DESCENDER
                    .setMoreKeysOf("\u043D", "\u04A3")
                    // U+0433: "г" CYRILLIC SMALL LETTER GHE
                    // U+0493: "ғ" CYRILLIC SMALL LETTER GHE WITH STROKE
                    .setMoreKeysOf("\u0433", "\u0493")
                    // U+0449: "щ" CYRILLIC SMALL LETTER SHCHA
                    .replaceKeyOfLabel(EastSlavic.ROW1_9, key("\u0449", additionalMoreKey("9")))
                    // U+044B: "ы" CYRILLIC SMALL LETTER YERU
                    // U+0456: "і" CYRILLIC SMALL LETTER BYELORUSSIAN-UKRAINIAN I
                    .replaceKeyOfLabel(EastSlavic.ROW2_2, key("\u044B", moreKey("\u0456")))
                    // U+0430: "а" CYRILLIC SMALL LETTER A
                    // U+04D9: "ә" CYRILLIC SMALL LETTER SCHWA
                    .setMoreKeysOf("\u0430", "\u04D9")
                    // U+043E: "о" CYRILLIC SMALL LETTER O
                    // U+04E9: "ө" CYRILLIC SMALL LETTER BARRED O
                    .setMoreKeysOf("\u043E", "\u04E9")
                    // U+044D: "э" CYRILLIC SMALL LETTER E
                    // U+04BB: "һ" CYRILLIC SMALL LETTER SHHA
                    .replaceKeyOfLabel(EastSlavic.ROW2_11, key("\u044D", moreKey("\u04BB")))
                    // U+0438: "и" CYRILLIC SMALL LETTER I
                    .replaceKeyOfLabel(EastSlavic.ROW3_5, "\u0438")
                    // U+044C: "ь" CYRILLIC SMALL LETTER SOFT SIGN
                    // U+044A: "ъ" CYRILLIC SMALL LETTER HARD SIGN
                    .setMoreKeysOf("\u044C", "\u044A");
        }
    }
}
