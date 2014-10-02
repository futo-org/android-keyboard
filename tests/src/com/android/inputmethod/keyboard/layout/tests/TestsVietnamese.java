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
import com.android.inputmethod.keyboard.layout.SymbolsShifted;
import com.android.inputmethod.keyboard.layout.customizer.LayoutCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

/**
 * vi: Vietnamese/qwerty
 */
@SmallTest
public final class TestsVietnamese extends LayoutTestsBase {
    private static final Locale LOCALE = new Locale("vi");
    private static final LayoutBase LAYOUT = new Qwerty(new VietnameseCustomizer(LOCALE));

    @Override
    LayoutBase getLayout() { return LAYOUT; }

    private static class VietnameseCustomizer extends LayoutCustomizer {
        VietnameseCustomizer(final Locale locale) { super(locale);  }

        @Override
        public ExpectedKey getCurrencyKey() { return CURRENCY_DONG; }

        @Override
        public ExpectedKey[] getOtherCurrencyKeys() {
            return SymbolsShifted.CURRENCIES_OTHER_GENERIC;
        }

        // U+20AB: "₫" DONG SIGN
        private static final ExpectedKey CURRENCY_DONG = key("\u20AB",
                Symbols.CURRENCY_GENERIC_MORE_KEYS);

        @Override
        public ExpectedKeyboardBuilder setAccentedLetters(final ExpectedKeyboardBuilder builder) {
            return builder
                    // U+00E8: "è" LATIN SMALL LETTER E WITH GRAVE
                    // U+00E9: "é" LATIN SMALL LETTER E WITH ACUTE
                    // U+1EBB: "ẻ" LATIN SMALL LETTER E WITH HOOK ABOVE
                    // U+1EBD: "ẽ" LATIN SMALL LETTER E WITH TILDE
                    // U+1EB9: "ẹ" LATIN SMALL LETTER E WITH DOT BELOW
                    // U+00EA: "ê" LATIN SMALL LETTER E WITH CIRCUMFLEX
                    // U+1EC1: "ề" LATIN SMALL LETTER E WITH CIRCUMFLEX AND GRAVE
                    // U+1EBF: "ế" LATIN SMALL LETTER E WITH CIRCUMFLEX AND ACUTE
                    // U+1EC3: "ể" LATIN SMALL LETTER E WITH CIRCUMFLEX AND HOOK ABOVE
                    // U+1EC5: "ễ" LATIN SMALL LETTER E WITH CIRCUMFLEX AND TILDE
                    // U+1EC7: "ệ" LATIN SMALL LETTER E WITH CIRCUMFLEX AND DOT BELOW
                    .setMoreKeysOf("e",
                            "\u00E8", "\u00E9", "\u1EBB", "\u1EBD", "\u1EB9", "\u00EA", "\u1EC1",
                            "\u1EBF", "\u1EC3", "\u1EC5", "\u1EC7")
                    // U+1EF3: "ỳ" LATIN SMALL LETTER Y WITH GRAVE
                    // U+00FD: "ý" LATIN SMALL LETTER Y WITH ACUTE
                    // U+1EF7: "ỷ" LATIN SMALL LETTER Y WITH HOOK ABOVE
                    // U+1EF9: "ỹ" LATIN SMALL LETTER Y WITH TILDE
                    // U+1EF5: "ỵ" LATIN SMALL LETTER Y WITH DOT BELOW
                    .setMoreKeysOf("y", "\u1EF3", "\u00FD", "\u1EF7", "\u1EF9", "\u1EF5")
                    // U+00F9: "ù" LATIN SMALL LETTER U WITH GRAVE
                    // U+00FA: "ú" LATIN SMALL LETTER U WITH ACUTE
                    // U+1EE7: "ủ" LATIN SMALL LETTER U WITH HOOK ABOVE
                    // U+0169: "ũ" LATIN SMALL LETTER U WITH TILDE
                    // U+1EE5: "ụ" LATIN SMALL LETTER U WITH DOT BELOW
                    // U+01B0: "ư" LATIN SMALL LETTER U WITH HORN
                    // U+1EEB: "ừ" LATIN SMALL LETTER U WITH HORN AND GRAVE
                    // U+1EE9: "ứ" LATIN SMALL LETTER U WITH HORN AND ACUTE
                    // U+1EED: "ử" LATIN SMALL LETTER U WITH HORN AND HOOK ABOVE
                    // U+1EEF: "ữ" LATIN SMALL LETTER U WITH HORN AND TILDE
                    // U+1EF1: "ự" LATIN SMALL LETTER U WITH HORN AND DOT BELOW
                    .setMoreKeysOf("u",
                            "\u00F9", "\u00FA", "\u1EE7", "\u0169", "\u1EE5", "\u01B0", "\u1EEB",
                            "\u1EE9", "\u1EED", "\u1EEF", "\u1EF1")
                    // U+00EC: "ì" LATIN SMALL LETTER I WITH GRAVE
                    // U+00ED: "í" LATIN SMALL LETTER I WITH ACUTE
                    // U+1EC9: "ỉ" LATIN SMALL LETTER I WITH HOOK ABOVE
                    // U+0129: "ĩ" LATIN SMALL LETTER I WITH TILDE
                    // U+1ECB: "ị" LATIN SMALL LETTER I WITH DOT BELOW
                    .setMoreKeysOf("i", "\u00EC", "\u00ED", "\u1EC9", "\u0129", "\u1ECB")
                    // U+00F2: "ò" LATIN SMALL LETTER O WITH GRAVE
                    // U+00F3: "ó" LATIN SMALL LETTER O WITH ACUTE
                    // U+1ECF: "ỏ" LATIN SMALL LETTER O WITH HOOK ABOVE
                    // U+00F5: "õ" LATIN SMALL LETTER O WITH TILDE
                    // U+1ECD: "ọ" LATIN SMALL LETTER O WITH DOT BELOW
                    // U+00F4: "ô" LATIN SMALL LETTER O WITH CIRCUMFLEX
                    // U+1ED3: "ồ" LATIN SMALL LETTER O WITH CIRCUMFLEX AND GRAVE
                    // U+1ED1: "ố" LATIN SMALL LETTER O WITH CIRCUMFLEX AND ACUTE
                    // U+1ED5: "ổ" LATIN SMALL LETTER O WITH CIRCUMFLEX AND HOOK ABOVE
                    // U+1ED7: "ỗ" LATIN SMALL LETTER O WITH CIRCUMFLEX AND TILDE
                    // U+1ED9: "ộ" LATIN SMALL LETTER O WITH CIRCUMFLEX AND DOT BELOW
                    // U+01A1: "ơ" LATIN SMALL LETTER O WITH HORN
                    // U+1EDD: "ờ" LATIN SMALL LETTER O WITH HORN AND GRAVE
                    // U+1EDB: "ớ" LATIN SMALL LETTER O WITH HORN AND ACUTE
                    // U+1EDF: "ở" LATIN SMALL LETTER O WITH HORN AND HOOK ABOVE
                    // U+1EE1: "ỡ" LATIN SMALL LETTER O WITH HORN AND TILDE
                    // U+1EE3: "ợ" LATIN SMALL LETTER O WITH HORN AND DOT BELOW
                    .setMoreKeysOf("o",
                            "\u00F2", "\u00F3", "\u1ECF", "\u00F5", "\u1ECD", "\u00F4", "\u1ED3",
                            "\u1ED1", "\u1ED5", "\u1ED7", "\u1ED9", "\u01A1", "\u1EDD", "\u1EDB",
                            "\u1EDF", "\u1EE1", "\u1EE3")
                    // U+00E0: "à" LATIN SMALL LETTER A WITH GRAVE
                    // U+00E1: "á" LATIN SMALL LETTER A WITH ACUTE
                    // U+1EA3: "ả" LATIN SMALL LETTER A WITH HOOK ABOVE
                    // U+00E3: "ã" LATIN SMALL LETTER A WITH TILDE
                    // U+1EA1: "ạ" LATIN SMALL LETTER A WITH DOT BELOW
                    // U+0103: "ă" LATIN SMALL LETTER A WITH BREVE
                    // U+1EB1: "ằ" LATIN SMALL LETTER A WITH BREVE AND GRAVE
                    // U+1EAF: "ắ" LATIN SMALL LETTER A WITH BREVE AND ACUTE
                    // U+1EB3: "ẳ" LATIN SMALL LETTER A WITH BREVE AND HOOK ABOVE
                    // U+1EB5: "ẵ" LATIN SMALL LETTER A WITH BREVE AND TILDE
                    // U+1EB7: "ặ" LATIN SMALL LETTER A WITH BREVE AND DOT BELOW
                    // U+00E2: "â" LATIN SMALL LETTER A WITH CIRCUMFLEX
                    // U+1EA7: "ầ" LATIN SMALL LETTER A WITH CIRCUMFLEX AND GRAVE
                    // U+1EA5: "ấ" LATIN SMALL LETTER A WITH CIRCUMFLEX AND ACUTE
                    // U+1EA9: "ẩ" LATIN SMALL LETTER A WITH CIRCUMFLEX AND HOOK ABOVE
                    // U+1EAB: "ẫ" LATIN SMALL LETTER A WITH CIRCUMFLEX AND TILDE
                    // U+1EAD: "ậ" LATIN SMALL LETTER A WITH CIRCUMFLEX AND DOT BELOW
                    .setMoreKeysOf("a",
                            "\u00E0", "\u00E1", "\u1EA3", "\u00E3", "\u1EA1", "\u0103", "\u1EB1",
                            "\u1EAF", "\u1EB3", "\u1EB5", "\u1EB7", "\u00E2", "\u1EA7", "\u1EA5",
                            "\u1EA9", "\u1EAB", "\u1EAD")
                    // U+0111: "đ" LATIN SMALL LETTER D WITH STROKE
                    .setMoreKeysOf("d", "\u0111");
        }
    }
}
