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
import com.android.inputmethod.keyboard.layout.Symbols;
import com.android.inputmethod.keyboard.layout.customizer.EnglishCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;

import java.util.Locale;

/*
 * en_GB: English (Great Britain)/qwerty
 */
@SmallTest
public final class TestsEnglishUK extends TestsEnglishUS {
    private static final Locale LOCALE = new Locale("en", "GB");
    private static final LayoutBase LAYOUT = new Qwerty(new EnglishUKCustomizer(LOCALE));

    @Override
    LayoutBase getLayout() { return LAYOUT; }

    private static class EnglishUKCustomizer extends EnglishCustomizer {
        EnglishUKCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey getCurrencyKey() { return CURRENCY_POUND; }

        @Override
        public ExpectedKey[] getOtherCurrencyKeys() { return CURRENCIES_OTHER_THAN_POUND; }

        private static final ExpectedKey CURRENCY_POUND = key(Symbols.POUND_SIGN,
                Symbols.CENT_SIGN, Symbols.DOLLAR_SIGN, Symbols.EURO_SIGN, Symbols.YEN_SIGN,
                Symbols.PESO_SIGN);

        private static final ExpectedKey[] CURRENCIES_OTHER_THAN_POUND = {
            Symbols.EURO_SIGN, Symbols.YEN_SIGN, key(Symbols.DOLLAR_SIGN, Symbols.CENT_SIGN),
            Symbols.CENT_SIGN
        };
    }
}
