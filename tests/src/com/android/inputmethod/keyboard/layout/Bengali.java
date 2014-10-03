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

import com.android.inputmethod.keyboard.layout.customizer.LayoutCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

/**
 * The Bengali keyboard.
 */
public final class Bengali extends LayoutBase {
    private static final String LAYOUT_NAME = "bengali";

    public Bengali(final LayoutCustomizer customizer) {
        super(customizer, Symbols.class, SymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    @Override
    ExpectedKey[][] getCommonAlphabetLayout(boolean isPhone) { return ALPHABET_COMMON; }

    @Override
    ExpectedKey[][] getCommonAlphabetShiftLayout(boolean isPhone, final int elementId) {
        return null;
    }

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+0994: "ঔ" BENGALI LETTER AU
                    // U+09CC: "ৌ" BENGALI VOWEL SIGN AU
                    // U+09E7: "১" BENGALI DIGIT ONE
                    key("\u0994", joinMoreKeys("\u09CC", "\u09E7", "1")),
                    // U+0990: "ঐ" BENGALI LETTER AI
                    // U+09C8: "ৈ" BENGALI VOWEL SIGN AI
                    // U+09E8: "২" BENGALI DIGIT TWO
                    key("\u0990", joinMoreKeys("\u09C8", "\u09E8", "2")),
                    // U+0986: "আ" BENGALI LETTER AA
                    // U+09BE: "া" BENGALI VOWEL SIGN AA
                    // U+09E9: "৩" BENGALI DIGIT THREE
                    key("\u0986", joinMoreKeys("\u09BE", "\u09E9", "3")),
                    // U+0988: "ঈ" BENGALI LETTER II
                    // U+09C0: "ী" BENGALI VOWEL SIGN II
                    // U+09EA: "৪" BENGALI DIGIT FOUR
                    key("\u0988", joinMoreKeys("\u09C0", "\u09EA", "4")),
                    // U+098A: "ঊ" BENGALI LETTER UU
                    // U+09C2: "ূ" BENGALI VOWEL SIGN UU
                    // U+09EB: "৫" BENGALI DIGIT FIVE
                    key("\u098A", joinMoreKeys("\u09C2", "\u09EB", "5")),
                    // U+09AC: "ব" BENGALI LETTER BA
                    // U+09AD: "ভ" BENGALI LETTER BHA
                    // U+09EC: "৬" BENGALI DIGIT SIX
                    key("\u09AC", joinMoreKeys("\u09AD", "\u09EC", "6")),
                    // U+09B9: "হ" BENGALI LETTER HA
                    // U+09ED: "৭" BENGALI DIGIT SEVEN
                    key("\u09B9", joinMoreKeys("\u09ED", "7")),
                    // U+0997: "গ" BENGALI LETTER GA
                    // U+0998: "ঘ" BENGALI LETTER GHA
                    // U+09EE: "৮" BENGALI DIGIT EIGHT
                    key("\u0997", joinMoreKeys("\u0998", "\u09EE", "8")),
                    // U+09A6: "দ" BENGALI LETTER DA
                    // U+09A7: "ধ" BENGALI LETTER DHA
                    // U+09EF: "৯" BENGALI DIGIT NINE
                    key("\u09A6", joinMoreKeys("\u09A7", "\u09EF", "9")),
                    // U+099C: "জ" BENGALI LETTER JA
                    // U+099D: "ঝ" BENGALI LETTER JHA
                    // U+099C/U+09CD/U+099E:
                    //     "জ্ঞ" BENGALI LETTER JA/BENGALI SIGN VIRAMA/BENGALI LETTER NYA
                    // U+09E6: "০" BENGALI DIGIT ZERO
                    key("\u099C", joinMoreKeys("\u099D", "\u099C\u09CD\u099E", "\u09E6", "0")),
                    // U+09A1: "ড" BENGALI LETTER DDA
                    // U+09A1/U+09BC: "ড়" BENGALI LETTER DDA/BENGALI SIGN NUKTA
                    key("\u09A1", moreKey("\u09A1\u09BC")))
            .setKeysOfRow(2,
                    // U+0993: "ও" BENGALI LETTER O
                    // U+09CB: "ো" BENGALI VOWEL SIGN O
                    key("\u0993", moreKey("\u09CB")),
                    // U+098F: "এ" BENGALI LETTER E
                    // U+09C7: "ে" BENGALI VOWEL SIGN E
                    key("\u098F", moreKey("\u09C7")),
                    // U+0985: "অ" BENGALI LETTER A
                    // U+09CD: "্" BENGALI SIGN VIRAMA
                    key("\u0985", moreKey("\u09CD")),
                    // U+0987: "ই" BENGALI LETTER I
                    // U+09BF: "ি" BENGALI VOWEL SIGN I
                    key("\u0987", moreKey("\u09BF")),
                    // U+0989: "উ" BENGALI LETTER U
                    // U+09C1: "ু" BENGALI VOWEL SIGN U
                    key("\u0989", moreKey("\u09C1")),
                    // U+09AA: "প" BENGALI LETTER PA
                    // U+09AB: "ফ" BENGALI LETTER PHA
                    key("\u09AA", moreKey("\u09AB")),
                    // U+09B0: "র" BENGALI LETTER RA
                    // U+09C3: "ৃ" BENGALI VOWEL SIGN VOCALIC R
                    // U+098B: "ঋ" BENGALI LETTER VOCALIC R
                    // U+09A4/U+09CD/U+09B0:
                    //     "ত্র" BENGALI LETTER TA/BENGALI SIGN VIRAMA/BENGALI LETTER RA
                    key("\u09B0", joinMoreKeys("\u09C3", "\u098B", "\u09A4\u09CD\u09B0")),
                    // U+0995: "ক" BENGALI LETTER KA
                    // U+0996: "খ" BENGALI LETTER KHA
                    key("\u0995", moreKey("\u0996")),
                    // U+09A4: "ত" BENGALI LETTER TA
                    // U+09CE: "ৎ" BENGALI LETTER KHANDA TA
                    // U+09A5: "থ" BENGALI LETTER THA
                    // U+09A4/U+09CD/U+09A4:
                    //     "ত্ত" BENGALI LETTER TA/BENGALI SIGN VIRAMA/BENGALI LETTER TA
                    key("\u09A4", joinMoreKeys("\u09CE", "\u09A5", "\u09A4\u09CD\u09A4")),
                    // U+099A: "চ" BENGALI LETTER CA
                    // U+099B: "ছ" BENGALI LETTER CHA
                    key("\u099A", moreKey("\u099B")),
                    // U+099F: "ট" BENGALI LETTER TTA
                    // U+09A0: "ঠ" BENGALI LETTER TTHA
                    key("\u099F", moreKey("\u09A0")))
            .setKeysOfRow(3,
                    // U+0981: "ঁ" BENGALI SIGN CANDRABINDU
                    // U+0983: "ঃ" BENGALI SIGN VISARGA
                    // U+0982: "ং" BENGALI SIGN ANUSVARA
                    key("\u0981", joinMoreKeys("\u0983", "\u0982")),
                    // U+09A2: "ঢ" BENGALI LETTER DDHA
                    // U+09A2/U+09BC: "ঢ়" BENGALI LETTER DDHA/BENGALI SIGN NUKTA
                    key("\u09A2", moreKey("\u09A2\u09BC")),
                    // U+09AE: "ম" BENGALI LETTER MA
                    "\u09AE",
                    // U+09A8: "ন" BENGALI LETTER NA
                    // U+09A3: "ণ" BENGALI LETTER NNA
                    key("\u09A8", moreKey("\u09A3")),
                    // U+099E: "ঞ" BENGALI LETTER NYA
                    // U+0999: "ঙ" BENGALI LETTER NGA
                    // U+099E/U+09CD/U+099C:
                    //     "ঞ্জ" BENGALI LETTER NYA/BENGALI SIGN VIRAMA/BENGALI LETTER JA
                    key("\u099E", joinMoreKeys("\u0999", "\u099E\u09CD\u099C")),
                    // U+09B2: "ল" BENGALI LETTER LA
                    "\u09B2",
                    // U+09B7: "ষ" BENGALI LETTER SSA
                    // U+0995/U+09CD/U+09B7:
                    //     "ক্ষ" BENGALI LETTER KA/BENGALI SIGN VIRAMA/BENGALI LETTER SSA
                    key("\u09B7", moreKey("\u0995\u09CD\u09B7")),
                    // U+09B8: "স" BENGALI LETTER SA
                    // U+09B6: "শ" BENGALI LETTER SHA
                    key("\u09B8", moreKey("\u09B6")),
                    // U+09DF: "য়" BENGALI LETTER YYA
                    // U+09AF: "য" BENGALI LETTER YA
                    key("\u09DF", moreKey("\u09AF")),
                    // U+0964: "।" DEVANAGARI DANDA
                    // U+0965: "॥" DEVANAGARI DOUBLE DANDA
                    key("\u0964", moreKey("\u0965")))
            .build();
}
