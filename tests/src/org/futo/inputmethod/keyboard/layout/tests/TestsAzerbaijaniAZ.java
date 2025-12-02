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

package org.futo.inputmethod.keyboard.layout.tests;

import android.test.suitebuilder.annotation.SmallTest;

import org.futo.inputmethod.keyboard.layout.LayoutBase;
import org.futo.inputmethod.keyboard.layout.Qwerty;
import org.futo.inputmethod.keyboard.layout.Symbols;
import org.futo.inputmethod.keyboard.layout.SymbolsShifted;
import org.futo.inputmethod.keyboard.layout.customizer.TurkicCustomizer;
import org.futo.inputmethod.keyboard.layout.expected.ExpectedKey;

import java.util.Locale;

/**
 * az_AZ: Azerbaijani (Azerbaijan)/qwerty
 */
@SmallTest
public final class TestsAzerbaijaniAZ extends LayoutTestsBase {
    private static final Locale LOCALE = new Locale("az", "AZ");
    private static final LayoutBase LAYOUT = new Qwerty(new AzerbaijaniCustomizer(LOCALE));

    @Override
    LayoutBase getLayout() { return LAYOUT; }

    private static class AzerbaijaniCustomizer extends TurkicCustomizer {
        public AzerbaijaniCustomizer(Locale locale) { super(locale); }

        private static final ExpectedKey CURRENCY_MANAT = key("₼", Symbols.CURRENCY_GENERIC_MORE_KEYS);

        @Override
        public ExpectedKey getCurrencyKey() {
            return CURRENCY_MANAT;
        }

        @Override
        public ExpectedKey[] getOtherCurrencyKeys() {
            return SymbolsShifted.CURRENCIES_OTHER_GENERIC;
        }
    }
}
