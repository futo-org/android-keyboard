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

import static com.android.inputmethod.keyboard.layout.DevanagariLetterConstants.*;

import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.layout.Hindi.HindiSymbols;
import com.android.inputmethod.keyboard.layout.customizer.NepaliCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

/**
 * The nepali_romanized layout
 */
public final class NepaliRomanized extends LayoutBase {
    private static final String LAYOUT_NAME = "nepali_romanized";

    public NepaliRomanized(final Locale locale) {
        super(new NepaliCustomizer(locale), HindiSymbols.class, SymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    @Override
    ExpectedKey[][] getCommonAlphabetLayout(final boolean isPhone) { return ALPHABET_COMMON; }

    @Override
    ExpectedKey[][] getCommonAlphabetShiftLayout(final boolean isPhone, final int elementId) {
        if (elementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED) {
            return getCommonAlphabetLayout(isPhone);
        }
        return ALPHABET_SHIFTED_COMMON;
    }

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+091F: "ट" DEVANAGARI LETTER TTA
                    // U+0967: "१" DEVANAGARI DIGIT ONE
                    // U+093C: "़" DEVANAGARI SIGN NUKTA
                    key("\u091F", joinMoreKeys("\u0967", "1", key(SIGN_NUKTA, "\u093C"))),
                    // U+094C: "ौ" DEVANAGARI VOWEL SIGN AU
                    // U+0968: "२" DEVANAGARI DIGIT TWO
                    key(VOWEL_SIGN_AU, "\u094C", joinMoreKeys("\u0968", "2")),
                    // U+0947: "े" DEVANAGARI VOWEL SIGN E
                    // U+0969: "३" DEVANAGARI DIGIT THREE
                    key(VOWEL_SIGN_E, "\u0947", joinMoreKeys("\u0969", "3")),
                    // U+0930: "र" DEVANAGARI LETTER RA
                    // U+096A: "४" DEVANAGARI DIGIT FOUR
                    key("\u0930", joinMoreKeys("\u096A", "4")),
                    // U+0924: "त" DEVANAGARI LETTER TA
                    // U+096B: "५" DEVANAGARI DIGIT FIVE
                    key("\u0924", joinMoreKeys("\u096B", "5")),
                    // U+092F: "य" DEVANAGARI LETTER YA
                    // U+096C: "६" DEVANAGARI DIGIT SIX
                    key("\u092F", joinMoreKeys("\u096C", "6")),
                    // U+0941: "ु" DEVANAGARI VOWEL SIGN U
                    // U+096D: "७" DEVANAGARI DIGIT SEVEN
                    key(VOWEL_SIGN_U, "\u0941", joinMoreKeys("\u096D", "7")),
                    // U+093F: "ि" DEVANAGARI VOWEL SIGN I
                    // U+096E: "८" DEVANAGARI DIGIT EIGHT
                    key(VOWEL_SIGN_I, "\u093F", joinMoreKeys("\u096E", "8")),
                    // U+094B: "ो" DEVANAGARI VOWEL SIGN O
                    // U+096F: "९" DEVANAGARI DIGIT NINE
                    key(VOWEL_SIGN_O, "\u094B", joinMoreKeys("\u096F", "9")),
                    // U+092A: "प" DEVANAGARI LETTER PA
                    // U+0966: "०" DEVANAGARI DIGIT ZERO
                    key("\u092A", joinMoreKeys("\u0966", "0")),
                    // U+0907: "इ" DEVANAGARI LETTER I
                    "\u0907")
            .setKeysOfRow(2,
                    // U+093E: "ा" DEVANAGARI VOWEL SIGN AA
                    key(VOWEL_SIGN_AA, "\u093E"),
                    // U+0938: "स" DEVANAGARI LETTER SA
                    // U+0926: "द" DEVANAGARI LETTER DA
                    // U+0909: "उ" DEVANAGARI LETTER U
                    // U+0917: "ग" DEVANAGARI LETTER GA
                    // U+0939: "ह" DEVANAGARI LETTER HA
                    // U+091C: "ज" DEVANAGARI LETTER JA
                    // U+0915: "क" DEVANAGARI LETTER KA
                    // U+0932: "ल" DEVANAGARI LETTER LA
                    // U+090F: "ए" DEVANAGARI LETTER E
                    // U+0950: "ॐ" DEVANAGARI OM
                    "\u0938", "\u0926", "\u0909", "\u0917", "\u0939", "\u091C", "\u0915", "\u0932",
                    "\u090F", "\u0950")
            .setKeysOfRow(3,
                    // U+0937: "ष" DEVANAGARI LETTER SSA
                    // U+0921: "ड" DEVANAGARI LETTER DDA
                    // U+091A: "च" DEVANAGARI LETTER CA
                    // U+0935: "व" DEVANAGARI LETTER VA
                    // U+092C: "ब" DEVANAGARI LETTER BHA
                    // U+0928: "न" DEVANAGARI LETTER NA
                    // U+092E: "म" DEVANAGARI LETTER MA
                    "\u0937", "\u0921", "\u091A", "\u0935", "\u092C", "\u0928", "\u092E",
                    // U+094D: "्" DEVANAGARI SIGN VIRAMA
                    // U+093D: "ऽ" DEVANAGARI SIGN AVAGRAHA
                    key(SIGN_VIRAMA, "\u094D", moreKey("\u093D")))
            .build();

    private static final ExpectedKey[][] ALPHABET_SHIFTED_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+0920: "ठ" DEVANAGARI LETTER TTHA
                    // U+0914: "औ" DEVANAGARI LETTER AU
                    "\u0920", "\u0914",
                    // U+0948: "ै" DEVANAGARI VOWEL SIGN AI
                    key(VOWEL_SIGN_AI, "\u0948"),
                    // U+0943: "ृ" DEVANAGARI VOWEL SIGN VOCALIC R
                    key(VOWEL_SIGN_VOCALIC_R, "\u0943"),
                    // U+0925: "थ" DEVANAGARI LETTER THA
                    // U+091E: "ञ" DEVANAGARI LETTER NYA
                    "\u0925", "\u091E",
                    // U+0942: "ू" DEVANAGARI VOWEL SIGN UU
                    key(VOWEL_SIGN_UU, "\u0942"),
                    // U+0940: "ी" DEVANAGARI VOWEL SIGN II
                    key(VOWEL_SIGN_II, "\u0940"),
                    // U+0913: "ओ" DEVANAGARI LETTER O
                    // U+092B: "फ" DEVANAGARI LETTER PHA
                    // U+0908: "ई" DEVANAGARI LETTER II
                    "\u0913", "\u092B", "\u0908")
            .setKeysOfRow(2,
                    // U+0906: "आ" DEVANAGARI LETTER AA
                    // U+0936: "श" DEVANAGARI LETTER SHA
                    // U+0927: "ध" DEVANAGARI LETTER DHA
                    // U+090A: "ऊ" DEVANAGARI LETTER UU
                    // U+0918: "घ" DEVANAGARI LETTER GHA
                    // U+0905: "अ" DEVANAGARI LETTER A
                    // U+091D: "झ" DEVANAGARI LETTER JHA
                    // U+0916: "ख" DEVANAGARI LETTER KHA
                    // U+0965: "॥" DEVANAGARI DOUBLE DANDA
                    // U+0910: "ऐ" DEVANAGARI LETTER AI
                    // U+0903: "ः" DEVANAGARI SIGN VISARGA
                    "\u0906", "\u0936", "\u0927", "\u090A", "\u0918", "\u0905", "\u091D", "\u0916",
                    "\u0965", "\u0910", key(SIGN_VISARGA, "\u0903"))
            .setKeysOfRow(3,
                    // U+090B: "ऋ" DEVANAGARI LETTER VOCALIC R
                    // U+0922: "ढ" DEVANAGARI LETTER DDHA
                    // U+091B: "छ" DEVANAGARI LETTER CHA
                    "\u090B", "\u0922", "\u091B",
                    // U+0901: "ँ" DEVANAGARI SIGN CANDRABINDU
                    key(SIGN_CANDRABINDU, "\u0901"),
                    // U+092D: "भ" DEVANAGARI LETTER BHA
                    // U+0923: "ण" DEVANAGARI LETTER NNA
                    "\u092D", "\u0923",
                    // U+0902: "ं" DEVANAGARI SIGN ANUSVARA
                    key(SIGN_ANUSVARA, "\u0902"),
                    // U+0919: "ङ" DEVANAGARI LETTER NGA
                    "\u0919")
            .build();
}
