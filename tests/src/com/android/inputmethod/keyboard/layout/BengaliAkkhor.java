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

/**
 * The Bengali Akkhor keyboard.
 */
public final class BengaliAkkhor extends LayoutBase {
    private static final String LAYOUT_NAME = "bengali_akkhor";

    public BengaliAkkhor(final LayoutCustomizer customizer) {
        super(customizer, Symbols.class, SymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    @Override
    ExpectedKey[][] getCommonAlphabetLayout(boolean isPhone) { return ALPHABET_COMMON; }

    @Override
    ExpectedKey[][] getCommonAlphabetShiftLayout(boolean isPhone, final int elementId) {
        if (elementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED) {
            return getCommonAlphabetLayout(isPhone);
        }
        return ALPHABET_SHIFTED_COMMON;
    }

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+09A7: "ধ" BENGALI LETTER DHA
                    // U+09E7: "১" BENGALI DIGIT ONE
                    // U+09A7/U+09CD/U+09AC:
                    //     "ধ্ব্র" BENGALI LETTER DHA/BENGALI SIGN VIRAMA/BENGALI LETTER BA
                    // U+09A7/U+09CD/U+09AF:
                    //     "ধ্য্র" BENGALI LETTER DHA/BENGALI SIGN VIRAMA/BENGALI LETTER YA
                    // U+09A7/U+09CD/U+09B0:
                    //     "ধ্র" BENGALI LETTER DHA/BENGALI SIGN VIRAMA/BENGALI LETTER RA
                    key("\u09A7", joinMoreKeys("\u09E7", "\u09A7\u09CD\u09AC", "\u09A7\u09CD\u09AF",
                            "\u09A7\u09CD\u09B0")),
                    // U+09A5: "থ" BENGALI LETTER THA
                    // U+09E8: "২" BENGALI DIGIT TWO
                    // U+09A5/U+09CD/U+09AF:
                    //     "থ্য" BENGALI LETTER THA/BENGALI SIGN VIRAMA/BENGALI LETTER YA
                    // U+09A5/U+09CD/U+09B0:
                    //     "থ্র" BENGALI LETTER THA/BENGALI SIGN VIRAMA/BENGALI LETTER RA
                    key("\u09A5", joinMoreKeys("\u09E8", "\u09A5\u09CD\u09AF",
                            "\u09A5\u09CD\u09B0")),
                    // U+09C7: "ে" BENGALI VOWEL SIGN E
                    // U+09E9: "৩" BENGALI DIGIT THREE
                    // U+098F: "এ" BENGALI LETTER E
                    key("\u09C7", joinMoreKeys("\u09E9", "\u098F")),
                    // U+09B0: "র" BENGALI LETTER RA
                    // U+09EA: "৪" BENGALI DIGIT FOUR
                    key("\u09B0", joinMoreKeys("\u09EA")),
                    // U+09A4: "ত" BENGALI LETTER TA
                    // U+09EB: "৫" BENGALI DIGIT FIVE
                    // U+09CE: "ৎ" BENGALI LETTER KHANDA TA
                    // U+09A4/U+09CD/U+09A4:
                    //     "ত্ত" BENGALI LETTER TA/BENGALI SIGN VIRAMA/BENGALI LETTER TA
                    // U+09A4/U+09CD/U+09A8:
                    //     "ত্ন" BENGALI LETTER TA/BENGALI SIGN VIRAMA/BENGALI LETTER NA
                    // U+09A4/U+09CD/U+09AC:
                    //     "ত্ব" BENGALI LETTER TA/BENGALI SIGN VIRAMA/BENGALI LETTER BA
                    // U+09A4/U+09CD/U+09AE:
                    //     "ত্ম" BENGALI LETTER TA/BENGALI SIGN VIRAMA/BENGALI LETTER MA
                    key("\u09A4", joinMoreKeys("\u09EB", "\u09CE", "\u09A4\u09CD\u09A4",
                            "\u09A4\u09CD\u09A8", "\u09A4\u09CD\u09AC", "\u09A4\u09CD\u09AE")),
                    // U+09DF: "য়" BENGALI LETTER YYA
                    // U+09EC: "৬" BENGALI DIGIT SIX
                    key("\u09DF", joinMoreKeys("\u09EC")),
                    // U+09C1: "ু" BENGALI VOWEL SIGN U
                    // U+09ED: "৭" BENGALI DIGIT SEVEN
                    // U+0989: "উ" BENGALI LETTER U
                    key("\u09C1", joinMoreKeys("\u09ED", "\u0989")),
                    // U+09BF: "ি" BENGALI VOWEL SIGN I
                    // U+09EE: "৮" BENGALI DIGIT EIGHT
                    // U+0987: "ই BENGALI LETTER I
                    key("\u09Bf", joinMoreKeys("\u09EE", "\u0987")),
                    // U+09CB: "ো" BENGALI VOWEL SIGN O
                    // U+09EF: "৯" BENGALI DIGIT NINE
                    // U+0993: "ও" BENGALI LETTER O
                    key("\u09CB", joinMoreKeys("\u09EF", "\u0993")),
                    // U+09AA: "প" BENGALI LETTER PA
                    // U+09E6: "০" BENGALI DIGIT ZERO
                    // U+09AA/U+09CD/U+09A4:
                    //     "প্ত" BENGALI LETTER PA/BENGALI SIGN VIRAMA/BENGALI LETTER TA
                    // U+09AA/U+09CD/U+09A8:
                    //     "প্ন" BENGALI LETTER PA/BENGALI SIGN VIRAMA/BENGALI LETTER NA
                    // U+09AA/U+09CD/U+09AA:
                    //     "প্প" BENGALI LETTER PA/BENGALI SIGN VIRAMA/BENGALI LETTER PA
                    // U+09AA/U+09CD/U+09AF:
                    //     "প্য" BENGALI LETTER PA/BENGALI SIGN VIRAMA/BENGALI LETTER YA
                    // U+09AA/U+09CD/U+09B0:
                    //     "প্র" BENGALI LETTER PA/BENGALI SIGN VIRAMA/BENGALI LETTER RA
                    // U+09AA/U+09CD/U+09B2:
                    //     "প্ল" BENGALI LETTER PA/BENGALI SIGN VIRAMA/BENGALI LETTER LA
                    // U+09AA/U+09CD/U+09B8:
                    //     "প্স" BENGALI LETTER PA/BENGALI SIGN VIRAMA/BENGALI LETTER SA
                    key("\u09AA", joinMoreKeys("\u09E6", "\u09AA\u09CD\u09A4", "\u09AA\u09CD\u09A8",
                            "\u09AA\u09CD\u09AA", "\u09AA\u09CD\u09AF", "\u09AA\u09CD\u09B0",
                            "\u09AA\u09CD\u09B2", "\u09AA\u09CD\u09B8")),
                    // U+0986: "আ" BENGALI LETTER AA
                    key("\u0986"))
            .setKeysOfRow(2,
                    // U+09BE: "া BENGALI VOWEL SIGN AA
                    // U+0986: "আ" BENGALI LETTER AA
                    key("\u09BE", moreKey("\u0986")),
                    // U+09B8: "স" BENGALI LETTER SA
                    // U+09B8/U+09CD/U+09AC:
                    //     "স্ব" BENGALI LETTER SA/BENGALI SIGN VIRAMA/BENGALI LETTER BA
                    // U+09B8/U+09CD/U+09A4:
                    //     "স্ত" BENGALI LETTER SA/BENGALI SIGN VIRAMA/BENGALI LETTER TA
                    // U+09B8/U+09CD/U+099F:
                    //     "স্ট" BENGALI LETTER SA/BENGALI SIGN VIRAMA/BENGALI LETTER TTA
                    // U+09B8/U+09CD/U+0995:
                    //     "স্ক" BENGALI LETTER SA/BENGALI SIGN VIRAMA/BENGALI LETTER KA
                    // U+09B8/U+09CD/U+09AA:
                    //     "স্প" BENGALI LETTER SA/BENGALI SIGN VIRAMA/BENGALI LETTER PA
                    key("\u09B8", joinMoreKeys("\u09B8\u09CD\u09AC", "\u09B8\u09CD\u09A4",
                            "\u09B8\u09CD\u099F", "\u09B8\u09CD\u0995", "\u09B8\u09CD\u09AA")),
                    // U+09A6: "দ" BENGALI LETTER DA
                    // U+09A6/U+09CD/U+09A6:
                    //     "দ্দ" BENGALI LETTER DA/BENGALI SIGN VIRAMA/BENGALI LETTER DA
                    // U+09A6/U+09CD/U+09A7:
                    //     "দ্ধ" BENGALI LETTER DA/BENGALI SIGN VIRAMA/BENGALI LETTER DHA
                    // U+09A6/U+09CD/U+09AC:
                    //     "দ্ব" BENGALI LETTER DA/BENGALI SIGN VIRAMA/BENGALI LETTER BA
                    // U+09A6/U+09CD/U+09AD:
                    //     "দ্ভ" BENGALI LETTER DA/BENGALI SIGN VIRAMA/BENGALI LETTER BHA
                    // U+09A6/U+09CD/U+09AE:
                    //     "দ্ম" BENGALI LETTER DA/BENGALI SIGN VIRAMA/BENGALI LETTER MA
                    // U+09A6/U+09CD/U+09AF:
                    //     "দ্য" BENGALI LETTER DA/BENGALI SIGN VIRAMA/BENGALI LETTER YA
                    // U+09A6/U+09CD/U+09B0:
                    //     "দ্র" BENGALI LETTER DA/BENGALI SIGN VIRAMA/BENGALI LETTER RA
                    key("\u09A6", joinMoreKeys("\u09A6\u09CD\u09A6", "\u09A6\u09CD\u09A7",
                            "\u09A6\u09CD\u09AC", "\u09A6\u09CD\u09AD", "\u09A6\u09CD\u09AE",
                            "\u09A6\u09CD\u09AF", "\u09A6\u09CD\u09B0")),
                    // U+09C3: "ৃ" BENGALI VOWEL SIGN VOCALIC R
                    // U+098B: "ঋ" BENGALI LETTER VOCALIC R
                    key("\u09C3", moreKey("\u098B")),
                    // U+0997: "গ" BENGALI LETTER GA
                    // U+0997/U+09CD/U+09A7:
                    //     "গ্ধ" BENGALI LETTER GA/BENGALI SIGN VIRAMA/BENGALI LETTER DH A
                    // U+0997/U+09CD/U+09B0:
                    //     "গ্র" BENGALI LETTER GA/BENGALI SIGN VIRAMA/BENGALI LETTER RA
                    // U+0997/U+09CD/U+09B2:
                    //     "গ্ল" BENGALI LETTER GA/BENGALI SIGN VIRAMA/BENGALI LETTER LA
                    // U+0997/U+09CD/U+09A8:
                    //     "গ্ন" BENGALI LETTER GA/BENGALI SIGN VIRAMA/BENGALI LETTER NA
                    key("\u0997", joinMoreKeys("\u0997\u09CD\u09A7", "\u0997\u09CD\u09B0",
                            "\u0997\u09CD\u09B2", "\u0997\u09CD\u09A8")),
                    // U+09CD: "্" BENGALI SIGN VIRAMA
                    key("\u09CD"),
                    // U+099C: "জ" BENGALI LETTER JA
                    // U+099C/U+09CD/U+099E:
                    //     "জ্ঞ" BENGALI LETTER JA/BENGALI SIGN VIRAMA/BENGALI LETTER NYA
                    // U+099C/U+09CD/U+099C:
                    //     "জ্জ" BENGALI LETTER JA/BENGALI SIGN VIRAMA/BENGALI LETTER JA
                    // U+099C/U+09CD/U+09AF:
                    //     "জ্ব" BENGALI LETTER JA/BENGALI SIGN VIRAMA/BENGALI LETTER YA
                    // U+099C/U+09CD/U+09AC:
                    //     "জ্য" BENGALI LETTER JA/BENGALI SIGN VIRAMA/BENGALI LETTER BA
                    // U+099C/U+09CD/U+09B0:
                    //     "জ্র" BENGALI LETTER JA/BENGALI SIGN VIRAMA/BENGALI LETTER RA
                    key("\u099C", joinMoreKeys("\u099C\u09CD\u099E", "\u099C\u09CD\u099C",
                            "\u099C\u09CD\u09AF", "\u099C\u09CD\u09AC", "\u099C\u09CD\u09B0")),
                    // U+0995: "ক" BENGALI LETTER KA
                    // U+0995/U+09CD/U+09B7:
                    //     "ক্ষ" BENGALI LETTER KA/BENGALI SIGN VIRAMA/BENGALI LETTER SSA
                    // U+0995/U+09CD/U+0995:
                    //     "ক্ক" BENGALI LETTER KA/BENGALI SIGN VIRAMA/BENGALI LETTER KA
                    // U+0995/U+09CD/U+099F:
                    //     "ক্ট" BENGALI LETTER KA/BENGALI SIGN VIRAMA/BENGALI LETTER TTA
                    // U+0995/U+09CD/U+09A4:
                    //     "ক্ত" BENGALI LETTER KA/BENGALI SIGN VIRAMA/BENGALI LETTER TA
                    // U+0995/U+09CD/U+09B0:
                    //     "ক্র" BENGALI LETTER KA/BENGALI SIGN VIRAMA/BENGALI LETTER RA
                    // U+0995/U+09CD/U+09B8:
                    //     "ক্স" BENGALI LETTER KA/BENGALI SIGN VIRAMA/BENGALI LETTER SA
                    // U+0995/U+09CD/U+09B2:
                    //     "ক্ল" BENGALI LETTER KA/BENGALI SIGN VIRAMA/BENGALI LETTER LA
                    key("\u0995", joinMoreKeys("\u0995\u09CD\u09B7", "\u0995\u09CD\u0995",
                            "\u0995\u09CD\u099F", "\u0995\u09CD\u09A4", "\u0995\u09CD\u09B0",
                            "\u0995\u09CD\u09B8", "\u0995\u09CD\u09B2")),
                    // U+09B2: "ল" BENGALI LETTER LA
                    // U+09B2/U+09CD/U+0995:
                    //     "ল্ক" BENGALI LETTER LA/BENGALI SIGN VIRAMA/BENGALI LETTER KA
                    // U+09B2/U+09CD/U+0997:
                    //     "ল্গ" BENGALI LETTER LA/BENGALI SIGN VIRAMA/BENGALI LETTER GA
                    // U+09B2/U+09CD/U+099F:
                    //     "ল্ট" BENGALI LETTER LA/BENGALI SIGN VIRAMA/BENGALI LETTER TTA
                    // U+09B2/U+09CD/U+09A1:
                    //     "ল্ড" BENGALI LETTER LA/BENGALI SIGN VIRAMA/BENGALI LETTER DDA
                    // U+09B2/U+09CD/U+09A4:
                    //     "ল্ত" BENGALI LETTER LA/BENGALI SIGN VIRAMA/BENGALI LETTER TA
                    // U+09B2/U+09CD/U+09A6:
                    //     "ল্দ" BENGALI LETTER LA/BENGALI SIGN VIRAMA/BENGALI LETTER DA
                    // U+09B2/U+09CD/U+09A7:
                    //     "ল্ধ" BENGALI LETTER LA/BENGALI SIGN VIRAMA/BENGALI LETTER DHA
                    // U+09B2/U+09CD/U+09AA:
                    //     "ল্প" BENGALI LETTER LA/BENGALI SIGN VIRAMA/BENGALI LETTER PA
                    // U+09B2/U+09CD/U+09AB:
                    //     "ল্ফ" BENGALI LETTER LA/BENGALI SIGN VIRAMA/BENGALI LETTER PHA
                    // U+09B2/U+09CD/U+09AC:
                    //     "ল্ব" BENGALI LETTER LA/BENGALI SIGN VIRAMA/BENGALI LETTER BA
                    // U+09B2/U+09CD/U+09AE:
                    //     "ল্ম" BENGALI LETTER LA/BENGALI SIGN VIRAMA/BENGALI LETTER MA
                    // U+09B2/U+09CD/U+09B2:
                    //     "ল্ল" BENGALI LETTER LA/BENGALI SIGN VIRAMA/BENGALI LETTER LA
                    key("\u09B2", joinMoreKeys("\u09B2\u09CD\u0995", "\u09B2\u09CD\u0997",
                            "\u09B2\u09CD\u099F", "\u09B2\u09CD\u09A1", "\u09B2\u09CD\u09A4",
                            "\u09B2\u09CD\u09A6", "\u09B2\u09CD\u09A7", "\u09B2\u09CD\u09AA",
                            "\u09B2\u09CD\u09AB", "\u09B2\u09CD\u09AC", "\u09B2\u09CD\u09AE",
                            "\u09B2\u09CD\u09B2")),
                    // U+0987: "ই" BENGALI LETTER I
                    key("\u0987"),
                    // U+0989: "উ" BENGALI LETTER U
                    key("\u0989"))
            .setKeysOfRow(3,
                    // U+09AF: "য" BENGALI LETTER YA
                    // U+09CD/U+09AF: "্য" BENGALI SIGN VIRAMA/BENGALI LETTER YA
                    key("\u09AF", moreKey("\u09CD\u09AF")),
                    // U+09B7: "ষ" BENGALI LETTER SSA
                    // U+09B7/U+09CD/U+0995:
                    //     "ষ্ক" BENGALI LETTER SSA/BENGALI SIGN VIRAMA/BENGALI LETTER KA
                    // U+09B7/U+09CD/U+099F:
                    //     "ষ্ট" BENGALI LETTER SSA/BENGALI SIGN VIRAMA/BENGALI LETTER TTA
                    // U+09B7/U+09CD/U+09A0:
                    //     "ষ্ঠ" BENGALI LETTER SSA/BENGALI SIGN VIRAMA/BENGALI LETTER TTHA
                    // U+09B7/U+09CD/U+09A3:
                    //     "ষ্ণ" BENGALI LETTER SSA/BENGALI SIGN VIRAMA/BENGALI LETTER NNA
                    // U+09B7/U+09CD/U+09AA:
                    //     "ষ্প" BENGALI LETTER SSA/BENGALI SIGN VIRAMA/BENGALI LETTER PA
                    // U+09B7/U+09CD/U+09AB:
                    //     "ষ্ফ" BENGALI LETTER SSA/BENGALI SIGN VIRAMA/BENGALI LETTER PHA
                    // U+09B7/U+09CD/U+09AE:
                    //     "ষ্ম" BENGALI LETTER SSA/BENGALI SIGN VIRAMA/BENGALI LETTER MA
                    key("\u09B7", joinMoreKeys("\u09B7\u09CD\u0995", "\u09B7\u09CD\u099F",
                            "\u09B7\u09CD\u09A0", "\u09B7\u09CD\u09A3", "\u09B7\u09CD\u09AA",
                            "\u09B7\u09CD\u09AB", "\u09B7\u09CD\u09AE")),
                    // U+099A: "চ" BENGALI LETTER CA
                    // U+099A/U+09CD/U+099A:
                    //     "চ্চ" BENGALI LETTER CA/BENGALI SIGN VIRAMA/BENGALI LETTER CA
                    // U+099A/U+09CD/U+099B:
                    //     "চ্ছ" BENGALI LETTER CA/BENGALI SIGN VIRAMA/BENGALI LETTER CHA
                    key("\u099A", joinMoreKeys("\u099A\u09CD\u099A", "\u099A\u09CD\u099B")),
                    // U+09AD: "ভ" BENGALI LETTER BHA
                    // U+09AD/U+09CD/U+09AF:
                    //     "ভ্" BENGALI LETTER BHA/BENGALI SIGN VIRAMA/BENGALI LETTER YA
                    // U+09AD/U+09CD/U+09B0:
                    //     "ভ্র" BENGALI LETTER BHA/BENGALI SIGN VIRAMA/BENGALI LETTER RA
                    // U+09AD/U+09CD/U+09B2:
                    //     "ভ্ল" BENGALI LETTER BHA/BENGALI SIGN VIRAMA/BENGALI LETTER LA
                    key("\u09AD", joinMoreKeys("\u09AD\u09CD\u09AF", "\u09AD\u09CD\u09B0",
                            "\u09AD\u09CD\u09B2")),
                    // U+09AC: "ব" BENGALI LETTER BA
                    // U+09CD/U+09AC: "্ব" BENGALI SIGN VIRAMA/BENGALI LETTER BA
                    // U+09AC/U+09CD/U+09B0:
                    //     "ব্র" BENGALI LETTER BA/BENGALI SIGN VIRAMA/BENGALI LETTER RA
                    // U+09AC/U+09CD/U+099C:
                    //     "ব্জ" BENGALI LETTER BA/BENGALI SIGN VIRAMA/BENGALI LETTER JA
                    // U+09AC/U+09CD/U+09A6:
                    //     "ব্দ" BENGALI LETTER BA/BENGALI SIGN VIRAMA/BENGALI LETTER DA
                    // U+09AC/U+09CD/U+09A7:
                    //     "ব্ধ" BENGALI LETTER BA/BENGALI SIGN VIRAMA/BENGALI LETTER DHA
                    // U+09AC/U+09CD/U+09AC:
                    //     "ব্ব" BENGALI LETTER BA/BENGALI SIGN VIRAMA/BENGALI LETTER BA
                    // U+09AC/U+09CD/U+09B2:
                    //     "ব্ল" BENGALI LETTER BA/BENGALI SIGN VIRAMA/BENGALI LETTER LA
                    // U+09F1: "ৱ" BENGALI LETTER RA WITH MIDDLE DIAGONAL
                    // U+09F0: "ৰ" BENGALI LETTER RA WITH LOWER DIAGONAL
                    key("\u09AC", joinMoreKeys("\u09CD\u09AC", "\u09AC\u09CD\u09B0",
                            "\u09AC\u09CD\u099C", "\u09AC\u09CD\u09A6", "\u09AC\u09CD\u09A7",
                            "\u09AC\u09CD\u09AC", "\u09AC\u09CD\u09B2", "\u09F1", "\u09F0")),
                    // U+09A8: "ন" BENGALI LETTER NA
                    // U+09A8/U+09CD/U+09A4:
                    //     "ন্ত" BENGALI LETTER NA/BENGALI SIGN VIRAMA/BENGALI LETTER TA
                    // U+09A8/U+09CD/U+09A5:
                    //     "ন্থ" BENGALI LETTER NA/BENGALI SIGN VIRAMA/BENGALI LETTER THA
                    // U+09A8/U+09CD/U+099F:
                    //     "ন্ট" BENGALI LETTER NA/BENGALI SIGN VIRAMA/BENGALI LETTER TTA
                    // U+09A8/U+09CD/U+09A6:
                    //     "ন্দ" BENGALI LETTER NA/BENGALI SIGN VIRAMA/BENGALI LETTER DA
                    // U+09A8/U+09CD/U+09A7:
                    //     "ন্ধ" BENGALI LETTER NA/BENGALI SIGN VIRAMA/BENGALI LETTER DHA
                    // U+09A8/U+09CD/U+09A1:
                    //     "ন্ড" BENGALI LETTER NA/BENGALI SIGN VIRAMA/BENGALI LETTER DDA
                    // U+09A8/U+09CD/U+09A8:
                    //     "ন্ন" BENGALI LETTER NA/BENGALI SIGN VIRAMA/BENGALI LETTER NA
                    // U+09A8/U+09CD/U+09AC:
                    //     "ন্ব" BENGALI LETTER NA/BENGALI SIGN VIRAMA/BENGALI LETTER BA
                    // U+09A8/U+09CD/U+09AE:
                    //     "ন্ম" BENGALI LETTER NA/BENGALI SIGN VIRAMA/BENGALI LETTER MA
                    // U+09A8/U+09CD/U+09B8:
                    //     "ন্স" BENGALI LETTER NA/BENGALI SIGN VIRAMA/BENGALI LETTER SA
                    key("\u09A8", joinMoreKeys("\u09A8\u09CD\u09A4", "\u09A8\u09CD\u09A5",
                            "\u09A8\u09CD\u099F", "\u09A8\u09CD\u09A6", "\u09A8\u09CD\u09A7",
                            "\u09A8\u09CD\u09A1", "\u09A8\u09CD\u09A8", "\u09A8\u09CD\u09AC",
                            "\u09A8\u09CD\u09AE", "\u09A8\u09CD\u09B8")),
                    // U+09AE: "ম" BENGALI LETTER MA
                    // U+09AE/U+09CD/U+09A8:
                    //     "ম্ন" BENGALI LETTER MA/BENGALI SIGN VIRAMA/BENGALI LETTER NA
                    // U+09AE/U+09CD/U+09AA:
                    //     "ম্প" BENGALI LETTER MA/BENGALI SIGN VIRAMA/BENGALI LETTER PA
                    // U+09AE/U+09CD/U+09AC:
                    //     "ম্ব" BENGALI LETTER MA/BENGALI SIGN VIRAMA/BENGALI LETTER BA
                    // U+09AE/U+09CD/U+09AD:
                    //     "ম্ভ" BENGALI LETTER MA/BENGALI SIGN VIRAMA/BENGALI LETTER BHA
                    // U+09AE/U+09CD/U+09AE:
                    //     "ম্ম" BENGALI LETTER MA/BENGALI SIGN VIRAMA/BENGALI LETTER MA
                    // U+09AE/U+09CD/U+09B0:
                    //     "ম্র" BENGALI LETTER MA/BENGALI SIGN VIRAMA/BENGALI LETTER RA
                    // U+09AE/U+09CD/U+09B2:
                    //     "ম্ল" BENGALI LETTER MA/BENGALI SIGN VIRAMA/BENGALI LETTER LA
                    key("\u09AE", joinMoreKeys("\u09AE\u09CD\u09A8", "\u09AE\u09CD\u09AA",
                            "\u09AE\u09CD\u09AC", "\u09AE\u09CD\u09AD", "\u09AE\u09CD\u09AE",
                            "\u09AE\u09CD\u09B0", "\u09AE\u09CD\u09B2")),
                    // U+098F: "এ" BENGALI LETTER E
                    key("\u098F"),
                    // U+0993: "ও" BENGALI LETTER O
                    key("\u0993"))
            .build();

    private static final ExpectedKey[][] ALPHABET_SHIFTED_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+09A2: "ঢ" BENGALI LETTER DDHA
                    key("\u09A2"),
                    // U+09A0: "ঠ" BENGALI LETTER TTHA
                    key("\u09A0"),
                    // U+09C8: "ৈ" BENGALI VOWEL SIGN AI
                    // U+0990: "ঐ" BENGALI LETTER AI
                    key("\u09C8", moreKey("\u0990")),
                    // U+09DC: "ড়" BENGALI LETTER RRA
                    // U+09BC: "়" BENGALI SIGN NUKTA
                    key("\u09DC", moreKey("\u09BC")),
                    // U+099F: "ট" BENGALI LETTER TTA
                    // U+09F3: "৳" BENGALI RUPEE SIGN
                    // U+099F/U+09CD/U+099F:
                    //     "ট্ট" BENGALI LETTER TTA/BENGALI SIGN VIRAMA/BENGALI LETTER TTA
                    // U+099F/U+09CD/U+09AC:
                    //     "ট্ব" BENGALI LETTER TTA/BENGALI SIGN VIRAMA/BENGALI LETTER BA
                    // U+099F/U+09CD/U+09AE:
                    //     "ট্ম" BENGALI LETTER TTA/BENGALI SIGN VIRAMA/BENGALI LETTER MA
                    key("\u099F", joinMoreKeys("\u09F3", "\u099F\u09CD\u099F", "\u099F\u09CD\u09AC",
                            "\u099F\u09CD\u09AE")),
                    // U+099E: "ঞ" BENGALI LETTER NYA
                    // U+099E/U+09CD/U+099A:
                    //     "ঞ্চ" BENGALI LETTER NYA/BENGALI SIGN VIRAMA/BENGALI LETTER CA
                    // U+099E/U+09CD/U+099B:
                    //     "ঞ্ছ" BENGALI LETTER NYA/BENGALI SIGN VIRAMA/BENGALI LETTER CHA
                    // U+099E/U+09CD/U+099C:
                    //     "ঞ্জ" BENGALI LETTER NYA/BENGALI SIGN VIRAMA/BENGALI LETTER JA
                    key("\u099E", joinMoreKeys("\u099E\u09CD\u099A", "\u099E\u09CD\u099B",
                            "\u099E\u09CD\u099C")),
                    // U+09C2: "ূ" BENGALI VOWEL SIGN UU
                    // U+098A: "ঊ" BENGALI LETTER UU
                    key("\u09C2", moreKey("\u098A")),
                    // U+09C0: "ী" BENGALI VOWEL SIGN II
                    // U+0988: "ঈ" BENGALI LETTER II
                    key("\u09C0", moreKey("\u0988")),
                    // U+09CC: "ৌ" BENGALI VOWEL SIGN AU
                    // U+099A: "ঔ" BENGALI LETTER CA
                    // U+09D7: "ৗ" BENGALI AU LENGTH MARK
                    key("\u09CC", joinMoreKeys("\u099A", "\u09D7")),
                    // U+09AB: "ফ" BENGALI LETTER PHA
                    // U+09AB/U+09CD/U+099F:
                    //     "ফ্ট" BENGALI LETTER PHA/BENGALI SIGN VIRAMA/BENGALI LETTER TTA
                    // U+09AB/U+09CD/U+09AF:
                    //     "ফ্য" BENGALI LETTER PHA/BENGALI SIGN VIRAMA/BENGALI LETTER YA
                    // U+09AB/U+09CD/U+09B0:
                    //     "ফ্র" BENGALI LETTER PHA/BENGALI SIGN VIRAMA/BENGALI LETTER RA
                    // U+09AB/U+09CD/U+09B2:
                    //     "ফ্ল" BENGALI LETTER PHA/BENGALI SIGN VIRAMA/BENGALI LETTER LA
                    key("\u09AB", joinMoreKeys("\u09AB\u09CD\u099F", "\u09AB\u09CD\u09AF",
                            "\u09AB\u09CD\u09B0", "\u09AB\u09CD\u09B2")),
                    // U+098B: "ঋ" BENGALI LETTER VOCALIC R
                    // U+098C: "ঌ" BENGALI LETTER VOCALIC L
                    // U+09E1: "ৡ" BENGALI LETTER VOCALIC LL
                    // U+09F4: "৴" BENGALI CURRENCY NUMERATOR ONE
                    // U+09F5: "৵" BENGALI CURRENCY NUMERATOR TWO
                    // U+09F6: "৶" BENGALI CURRENCY NUMERATOR THREE
                    // U+09E2: " ৢ" BENGALI VOWEL SIGN VOCALIC L
                    // U+09E3: " ৣ" BENGALI VOWEL SIGN VOCALIC LL
                    key("\u098B", joinMoreKeys("\u098C", "\u09E1", "\u09F4", "\u09F5", "\u09F6",
                            "\u09E2", "\u09E3")))
            .setKeysOfRow(2,
                    // U+0985: "অ" BENGALI LETTER A
                    key("\u0985"),
                    // U+09B6: "শ" BENGALI LETTER SHA
                    // U+09B6/U+09CD/U+099A:
                    //     "শ্চ" BENGALI LETTER SHA/BENGALI SIGN VIRAMA/BENGALI LETTER CA
                    // U+09B6/U+09CD/U+099B:
                    //     "শ্ছ" BENGALI LETTER SHA/BENGALI SIGN VIRAMA/BENGALI LETTER CHA
                    // U+09B6/U+09CD/U+09A4:
                    //     "শ্ত" BENGALI LETTER SHA/BENGALI SIGN VIRAMA/BENGALI LETTER TA
                    // U+09B6/U+09CD/U+09A8:
                    //     "শ্ন" BENGALI LETTER SHA/BENGALI SIGN VIRAMA/BENGALI LETTER NA
                    // U+09B6/U+09CD/U+09AC:
                    //     "শ্ব" BENGALI LETTER SHA/BENGALI SIGN VIRAMA/BENGALI LETTER BA
                    // U+09B6/U+09CD/U+09AE:
                    //     "শ্ম" BENGALI LETTER SHA/BENGALI SIGN VIRAMA/BENGALI LETTER MA
                    // U+09B6/U+09CD/U+09B0:
                    //     "শ্র" BENGALI LETTER SHA/BENGALI SIGN VIRAMA/BENGALI LETTER RA
                    // U+09B6/U+09CD/U+09B2:
                    //     "শ্ল" BENGALI LETTER SHA/BENGALI SIGN VIRAMA/BENGALI LETTER LA
                    key("\u09B6", joinMoreKeys("\u09B6\u09CD\u099A", "\u09B6\u09CD\u099B",
                            "\u09B6\u09CD\u09A4", "\u09B6\u09CD\u09A8", "\u09B6\u09CD\u09AC",
                            "\u09B6\u09CD\u09AE", "\u09B6\u09CD\u09B0", "\u09B6\u09CD\u09B2")),
                    // U+09A1: "ড" BENGALI LETTER DDA
                    // U+09A1/U+09CD/U+09A1:
                    //     "ড্ড" BENGALI LETTER DDA/BENGALI SIGN VIRAMA/BENGALI LETTER DDA
                    key("\u09A1", moreKey("\u09A1\u09CD\u09A1")),
                    // U+09DD: "ঢ়" BENGALI LETTER RHA
                    key("\u09DD"),
                    // U+0998: "ঘ" BENGALI LETTER GHA
                    key("\u0998"),
                    // U+09B9: "হ" BENGALI LETTER HA
                    // U+09BD: "ঽ" BENGALI SIGN AVAGRAHA
                    // U+09B9/U+09CD/U+09A3:
                    //     "হ্ণ" BENGALI LETTER HA/BENGALI SIGN VIRAMA/BENGALI LETTER NNA
                    // U+09B9/U+09CD/U+09A8:
                    //     "হ্ন" BENGALI LETTER HA/BENGALI SIGN VIRAMA/BENGALI LETTER NA
                    // U+09B9/U+09CD/U+09AC:
                    //     "হ্ব" BENGALI LETTER HA/BENGALI SIGN VIRAMA/BENGALI LETTER BA
                    // U+09B9/U+09CD/U+09AE:
                    //     "হ্ম" BENGALI LETTER HA/BENGALI SIGN VIRAMA/BENGALI LETTER MA
                    // U+09B9/U+09CD/U+09B0:
                    //     "হ্র" BENGALI LETTER HA/BENGALI SIGN VIRAMA/BENGALI LETTER RA
                    // U+09B9/U+09CD/U+09B2:
                    //     "হ্ল" BENGALI LETTER HA/BENGALI SIGN VIRAMA/BENGALI LETTER LA
                    key("\u09B9", joinMoreKeys("\u09BD", "\u09B9\u09CD\u09A3", "\u09B9\u09CD\u09A8",
                            "\u09B9\u09CD\u09AC", "\u09B9\u09CD\u09AE", "\u09B9\u09CD\u09B0",
                            "\u09B9\u09CD\u09B2")),
                    // U+099D: "ঝ" BENGALI LETTER JHA
                    key("\u099D"),
                    // U+0996: "খ" BENGALI LETTER KHA
                    key("\u0996"),
                    // U+09CE: "ৎ" BENGALI LETTER KHANDA TA
                    key("\u09CE"),
                    // U+0988: "ঈ" BENGALI LETTER II
                    key("\u0988"),
                    // U+098A: "ঊ" BENGALI LETTER UU
                    key("\u098A"))
            .setKeysOfRow(3,
                    // U+0964: "।" DEVANAGARI DANDA
                    // U+0965: "॥" DEVANAGARI DOUBLE DANDA
                    key("\u0964", moreKey("\u0965")),
                    // U+0999: "ঙ BENGALI LETTER NGA
                    // U+0999/U+09CD/U+0995: "ঙ্ক"
                    // U+0999/U+09CD/U+0996: "ঙ্খ"
                    // U+0999/U+09CD/U+0997: "ঙ্গ"
                    key("\u0999", joinMoreKeys("\u0999\u09CD\u0995", "\u0999\u09CD\u0996",
                            "\u0999\u09CD\u0997")),
                    // U+099B: "ছ" BENGALI LETTER CHA
                    key("\u099B"),
                    // U+0983: "ঃ" BENGALI SIGN VISARGA
                    key("\u0983"),
                    // U+0981: "ঁ" BENGALI SIGN CANDRABINDU
                    key("\u0981"),
                    // U+09A3: "ণ" BENGALI LETTER NNA
                    // U+09A3/U+09CD/U+099F:
                    //     "ণ্ট" BENGALI LETTER NNA/BENGALI SIGN VIRAMA/BENGALI LETTER TT/A
                    // U+09A3/U+09CD/U+09A1:
                    //     "ণ্ড" BENGALI LETTER NNA/BENGALI SIGN VIRAMA/BENGALI LETTER DDA
                    // U+09A3/U+09CD/U+09A3:
                    //     "ণ্ণ" BENGALI LETTER NNA/BENGALI SIGN VIRAMA/BENGALI LETTER NN
                    key("\u09A3", joinMoreKeys("\u09A3\u09CD\u099F", "\u09A3\u09CD\u09A1",
                            "\u09A3\u09CD\u09A3")),
                    // U+0982: "ং" BENGALI SIGN ANUSVARA
                    key("\u0982"),
                    // U+0990: "ঐ" BENGALI LETTER AI
                    key("\u0990"),
                    // U+0994: "ঔ" BENGALI LETTER AU
                    key("\u0994"))
            .build();
}
