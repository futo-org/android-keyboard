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

package com.android.inputmethod.keyboard.layout;

import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.layout.customizer.LayoutCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;
import com.android.inputmethod.latin.common.Constants;

import java.util.Locale;

/**
 * The Georgian alphabet keyboard.
 */
public final class Georgian extends LayoutBase {
    private static final String LAYOUT_NAME = "georgian";

    public Georgian(final Locale locale) {
        super(new GeorgianCustomizer(locale), Symbols.class, SymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    private static class GeorgianCustomizer extends LayoutCustomizer {
        GeorgianCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey getAlphabetKey() { return GEORGIAN_ALPHABET_KEY; }

        @Override
        public ExpectedKey[] getDoubleQuoteMoreKeys() { return Symbols.DOUBLE_QUOTES_R9L; }

        @Override
        public ExpectedKey[] getSingleQuoteMoreKeys() { return Symbols.SINGLE_QUOTES_R9L; }

        // U+10D0: "ა" GEORGIAN LETTER AN
        // U+10D1: "ბ" GEORGIAN LETTER BAN
        // U+10D2: "გ" GEORGIAN LETTER GAN
        private static final ExpectedKey GEORGIAN_ALPHABET_KEY = key(
                "\u10D0\u10D1\u10D2", Constants.CODE_SWITCH_ALPHA_SYMBOL);
    }

    @Override
    ExpectedKey[][] getCommonAlphabetLayout(final boolean isPhone) {
        return ALPHABET_COMMON;
    }

    @Override
    public ExpectedKey[][] getCommonAlphabetShiftLayout(final boolean isPhone,
            final int elementId) {
        if (elementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED) {
            return getCommonAlphabetLayout(isPhone);
        }
        return ALPHABET_SHIFTED_COMMON;
    }

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+10E5: "ქ" GEORGIAN LETTER GHAN
                    key("\u10E5", moreKey("1")),
                    // U+10EC: "წ" GEORGIAN LETTER CIL
                    key("\u10EC", moreKey("2")),
                    // U+10D4: "ე" GEORGIAN LETTER EN
                    // U+10F1: "ჱ" GEORGIAN LETTER HE
                    key("\u10D4", joinMoreKeys("3", "\u10F1")),
                    // U+10E0: "რ" GEORGIAN LETTER RAE
                    key("\u10E0", moreKey("4")),
                    // U+10E2: "ტ" GEORGIAN LETTER TAR
                    key("\u10E2", moreKey("5")),
                    // U+10E7: "ყ" GEORGIAN LETTER QAR
                    // U+10F8: "ჸ" GEORGIAN LETTER ELIFI
                    key("\u10E7", joinMoreKeys("6", "\u10F8")),
                    // U+10E3: "უ" GEORGIAN LETTER UN
                    key("\u10E3", moreKey("7")),
                    // U+10D8: "ი" GEORGIAN LETTER IN
                    // U+10F2: "ჲ" GEORGIAN LETTER HIE
                    key("\u10D8", joinMoreKeys("8", "\u10F2")),
                    // U+10DD: "ო" GEORGIAN LETTER ON
                    key("\u10DD", moreKey("9")),
                    // U+10DE: "პ" GEORGIAN LETTER PAR
                    key("\u10DE", moreKey("0")))
            .setKeysOfRow(2,
                    // U+10D0: "ა" GEORGIAN LETTER AN
                    // U+10FA: "ჺ" GEORGIAN LETTER AIN
                    key("\u10D0", moreKey("\u10FA")),
                    // U+10E1: "ს" GEORGIAN LETTER SAN
                    // U+10D3: "დ" GEORGIAN LETTER DON
                    "\u10E1", "\u10D3",
                    // U+10E4: "ფ" GEORGIAN LETTER PHAR
                    // U+10F6: "ჶ" GEORGIAN LETTER FI
                    key("\u10E4", moreKey("\u10F6")),
                    // U+10D2: "გ" GEORGIAN LETTER GAN
                    // U+10F9: "ჹ" GEORGIAN LETTER TURNED GAN
                    key("\u10D2", moreKey("\u10F9")),
                    // U+10F0: "ჰ" GEORGIAN LETTER HAE
                    // U+10F5: "ჵ" GEORGIAN LETTER HOE
                    key("\u10F0", moreKey("\u10F5")),
                    // U+10EF: "ჯ" GEORGIAN LETTER JHAN
                    // U+10F7: "ჷ" GEORGIAN LETTER YN
                    key("\u10EF", moreKey("\u10F7")),
                    // U+10D9: "კ" GEORGIAN LETTER KAN
                    // U+10DA: "ლ" GEORGIAN LETTER LAS
                    "\u10D9", "\u10DA")
            .setKeysOfRow(3,
                    // U+10D6: "ზ" GEORGIAN LETTER ZEN
                    "\u10D6",
                    // U+10EE: "ხ" GEORGIAN LETTER XAN
                    // U+10F4: "ჴ" GEORGIAN LETTER HAR
                    key("\u10EE", moreKey("\u10F4")),
                    // U+10EA: "ც" GEORGIAN LETTER CAN
                    "\u10EA",
                    // U+10D5: "ვ" GEORGIAN LETTER VIN
                    // U+10F3: "ჳ" GEORGIAN LETTER WE
                    key("\u10D5", moreKey("\u10F3")),
                    // U+10D1: "ბ" GEORGIAN LETTER BAN
                    "\u10D1",
                    // U+10DC: "ნ" GEORGIAN LETTER NAR
                    // U+10FC: "ჼ" MODIFIER LETTER GEORGIAN NAR
                    key("\u10DC", moreKey("\u10FC")),
                    // U+10DB: "მ" GEORGIAN LETTER MAN
                    "\u10DB")
            .build();

    private static final ExpectedKey[][] ALPHABET_SHIFTED_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    key("Q", moreKey("1")),
                    // U+10ED: "ჭ" GEORGIAN LETTER CHAR
                    key("\u10ED", moreKey("2")),
                    key("E", moreKey("3")),
                    // U+10E6: "ღ" GEORGIAN LETTER GHAN
                    key("\u10E6", moreKey("4")),
                    // U+10D7: "თ" GEORGIAN LETTER TAN
                    key("\u10D7", moreKey("5")),
                    key("Y", moreKey("6")),
                    key("U", moreKey("7")),
                    key("I", moreKey("8")),
                    key("O", moreKey("9")),
                    key("P", moreKey("0")))
            .setKeysOfRow(2,
                    // U+10E8: "შ" GEORGIAN LETTER SHIN
                    // U+10DF: "ჟ" GEORGIAN LETTER ZHAR
                    "A", "\u10E8", "D", "F", "G", "H", "\u10DF", "K", "L")
            .setKeysOfRow(3,
                    // U+10EB: "ძ" GEORGIAN LETTER JIL
                    // U+10E9: "ჩ" GEORGIAN LETTER CHIN
                    "\u10EB", "X", "\u10E9", "V", "B", "N", "M")
            .build();
}
