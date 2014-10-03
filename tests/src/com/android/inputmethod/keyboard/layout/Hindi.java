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
import com.android.inputmethod.keyboard.layout.customizer.LayoutCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

/**
 * The Hindi keyboard.
 */
public final class Hindi extends LayoutBase {
    private static final String LAYOUT_NAME = "hindi";

    public Hindi(final LayoutCustomizer customizer) {
        super(customizer, HindiSymbols.class, SymbolsShifted.class);
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
                    // U+094C: "ौ" DEVANAGARI VOWEL SIGN AU
                    // U+094C/U+0902: "ौं" DEVANAGARI VOWEL SIGN AU/DEVANAGARI SIGN ANUSVARA
                    // U+0967: "१" DEVANAGARI DIGIT ONE
                    key(VOWEL_SIGN_AU, "\u094C", joinMoreKeys(
                            moreKey(VOWEL_SIGN_AU + "\u0902", "\u094C\u0902"),
                            "\u0967", "1")),
                    // U+0948: "ै" DEVANAGARI VOWEL SIGN AI
                    // U+0948/U+0902: "ैं" DEVANAGARI VOWEL SIGN AI/DEVANAGARI SIGN ANUSVARA
                    // U+0968: "२" DEVANAGARI DIGIT TWO
                    key(VOWEL_SIGN_AI, "\u0948", joinMoreKeys(
                            moreKey(VOWEL_SIGN_AI + "\u0902", "\u0948\u0902"),
                            "\u0968", "2")),
                    // U+093E: "ा" DEVANAGARI VOWEL SIGN AA
                    // U+093E/U+0902: "ां" DEVANAGARI VOWEL SIGN AA/DEVANAGARI SIGN ANUSVARA
                    // U+093E/U+0901: "ाँ" DEVANAGARI VOWEL SIGN AA/DEVANAGARI SIGN CANDRABINDU
                    // U+0969: "३" DEVANAGARI DIGIT THREE
                    key(VOWEL_SIGN_AA, "\u093E", joinMoreKeys(
                            moreKey(VOWEL_SIGN_AA + "\u0902", "\u093E\u0902"),
                            moreKey(VOWEL_SIGN_AA + "\u0901", "\u093E\u0901"),
                            "\u0969", "3")),
                    // U+0940: "ी" DEVANAGARI VOWEL SIGN II
                    // U+0940/U+0902: "ीं" DEVANAGARI VOWEL SIGN II/DEVANAGARI SIGN ANUSVARA
                    // U+096A: "४" DEVANAGARI DIGIT FOUR
                    key(VOWEL_SIGN_II, "\u0940", joinMoreKeys(
                            moreKey(VOWEL_SIGN_II + "\u0902", "\u0940\u0902"),
                            "\u096A", "4")),
                    // U+0942: "ू" DEVANAGARI VOWEL SIGN UU
                    // U+0942/U+0902: "ूं" DEVANAGARI VOWEL SIGN UU/DEVANAGARI SIGN ANUSVARA
                    // U+0942/U+0901: "ूँ" DEVANAGARI VOWEL SIGN UU/DEVANAGARI SIGN CANDRABINDU
                    // U+096B: "५" DEVANAGARI DIGIT FIVE
                    key(VOWEL_SIGN_UU, "\u0942", joinMoreKeys(
                            moreKey(VOWEL_SIGN_UU + "\u0902", "\u0942\u0902"),
                            moreKey(VOWEL_SIGN_UU + "\u0901", "\u0942\u0901"),
                            "\u096B", "5")),
                    // U+092C: "ब" DEVANAGARI LETTER BA
                    // U+092C/U+0952: "ब॒" DEVANAGARI LETTER BA/DEVANAGARI STRESS SIGN ANUDATTA
                    // U+096C: "६" DEVANAGARI DIGIT SIX
                    key("\u092C", joinMoreKeys("\u092C\u0952", "\u096C", "6")),
                    // U+0939: "ह" DEVANAGARI LETTER HA
                    // U+096D: "७" DEVANAGARI DIGIT SEVEN
                    key("\u0939", joinMoreKeys("\u096D", "7")),
                    // U+0917: "ग" DEVANAGARI LETTER GA
                    // U+091C/U+094D/U+091E:
                    //     "ज्ञ" DEVANAGARI LETTER JA/DEVANAGARI SIGN VIRAMA/DEVANAGARI LETTER NYA
                    // U+0917/U+093C: "ग़" DEVANAGARI LETTER GA/DEVANAGARI SIGN NUKTA
                    // U+0917/U+0952: "ग॒" DEVANAGARI LETTER GA/DEVANAGARI STRESS SIGN ANUDATTA
                    // U+096E: "८" DEVANAGARI DIGIT EIGHT
                    key("\u0917", joinMoreKeys("\u091C\u094D\u091E", "\u0917\u093C", "\u0917\u0952",
                            "\u096E", "8")),
                    // U+0926: "द" DEVANAGARI LETTER DA
                    // U+096F: "९" DEVANAGARI DIGIT NINE
                    key("\u0926", joinMoreKeys("\u096F", "9")),
                    // U+091C: "ज" DEVANAGARI LETTER JA
                    // U+091C/U+0952: "ज॒" DEVANAGARI LETTER JA/DEVANAGARI STRESS SIGN ANUDATTA
                    // U+091C/U+094D/U+091E:
                    //     "ज्ञ" DEVANAGARI LETTER JA/DEVANAGARI SIGN VIRAMA/DEVANAGARI LETTER NYA
                    // U+091C/U+093C: "ज़" DEVANAGARI LETTER JA/DEVANAGARI SIGN NUKTA
                    // U+0966: "०" DEVANAGARI DIGIT ZERO
                    key("\u091C", joinMoreKeys("\u091C\u0952", "\u091C\u094D\u091E", "\u091C\u093C",
                            "\u0966", "0")),
                    // U+0921: "ड" DEVANAGARI LETTER DDA
                    // U+0921/U+0952: "ड॒" DEVANAGARI LETTER DDA/DEVANAGARI STRESS SIGN ANUDATTA
                    // U+0921/U+093C: "ड़" DEVANAGARI LETTER DDA/DEVANAGARI SIGN NUKTA
                    key("\u0921", joinMoreKeys("\u0921\u0952", "\u0921\u093C")))
            .setKeysOfRow(2,
                    // U+094B: "ो" DEVANAGARI VOWEL SIGN O
                    // U+094B/U+0902: "қं" DEVANAGARI VOWEL SIGN O/DEVANAGARI SIGN ANUSVARA
                    // U+0949: "ॉ" DEVANAGARI VOWEL SIGN CANDRA O
                    // U+094A: "ॊ" DEVANAGARI VOWEL SIGN SHORT O
                    key(VOWEL_SIGN_O, "\u094B", joinMoreKeys(
                            moreKey(VOWEL_SIGN_O + "\u0902", "\u094B\u0902"),
                            moreKey(VOWEL_SIGN_CANDRA_O, "\u0949"),
                            moreKey(VOWEL_SIGN_SHORT_O, "\u094A"))),
                    // U+0947: "े" DEVANAGARI VOWEL SIGN E
                    // U+0947/U+0902: "ें" DEVANAGARI VOWEL SIGN E/DEVANAGARI SIGN ANUSVARA
                    key(VOWEL_SIGN_E, "\u0947",
                            moreKey(VOWEL_SIGN_E + "\u0902", "\u0947\u0902")),
                    // U+094D: "्" DEVANAGARI SIGN VIRAMA
                    key(SIGN_VIRAMA, "\u094D"),
                    // U+093F: "ि" DEVANAGARI VOWEL SIGN I
                    // U+093F/U+0902: "िं" DEVANAGARI VOWEL SIGN I/DEVANAGARI SIGN ANUSVARA
                    key(VOWEL_SIGN_I, "\u093F",
                            moreKey("\u093F" + SIGN_ANUSVARA, "\u093F\u0902")),
                    // U+0941: "ु" DEVANAGARI VOWEL SIGN U
                    // U+0941/U+0902: "ुं" DEVANAGARI VOWEL SIGN U/DEVANAGARI SIGN ANUSVARA
                    // U+0941/U+0901: "ुँ" DEVANAGARI VOWEL SIGN U/DEVANAGARI SIGN CANDRABINDU
                    key(VOWEL_SIGN_U, "\u0941", joinMoreKeys(
                            moreKey(VOWEL_SIGN_U + "\u0902", "\u0941\u0902"),
                            moreKey(VOWEL_SIGN_U + "\u0901", "\u0941\u0901"))),
                    // U+092A: "प" DEVANAGARI LETTER PA
                    "\u092A",
                    // U+0930: "र" DEVANAGARI LETTER RA
                    // U+090B: "ऋ" DEVANAGARI LETTER VOCALIC R
                    // U+0930/U+093C: "ऱ" DEVANAGARI LETTER RA/DEVANAGARI SIGN NUKTA
                    // U+0960: "ॠ" DEVANAGARI LETTER VOCALIC RR
                    key("\u0930", joinMoreKeys("\u090B", "\u0930\u093C", "\u0960")),
                    // U+0915: "क" DEVANAGARI LETTER KA
                    // U+0915/U+093C: "क़" DEVANAGARI LETTER KA/DEVANAGARI SIGN NUKTA
                    key("\u0915", moreKey("\u0915\u093C")),
                    // U+0924: "त" DEVANAGARI LETTER TA
                    // U+0924/U+094D/U+0930:
                    //     "त्र" DEVANAGARI LETTER TA/DEVANAGARI SIGN VIRAMA/DEVANAGARI LETTER RA
                    key("\u0924", moreKey("\u0924\u094D\u0930")),
                    // U+091A: "च" DEVANAGARI LETTER CA
                    // U+091F: "ट" DEVANAGARI LETTER TTA
                    "\u091A","\u091F")
            .setKeysOfRow(3,
                    // U+0949: "ॉ" DEVANAGARI VOWEL SIGN CANDRA O
                    key(VOWEL_SIGN_CANDRA_O, "\u0949"),
                    // U+0902: "ं" DEVANAGARI SIGN ANUSVARA
                    key(SIGN_ANUSVARA, "\u0902"),
                    // U+092E: "म" DEVANAGARI LETTER MA
                    // U+0950: "ॐ" DEVANAGARI OM
                    key("\u092E", moreKey("\u0950")),
                    // U+0928: "न" DEVANAGARI LETTER NA
                    // U+091E: "ञ" DEVANAGARI LETTER NYA
                    // U+0919: "ङ" DEVANAGARI LETTER NGA
                    // U+0928/U+093C: "ऩ" DEVANAGARI LETTER NA/DEVANAGARI SIGN NUKTA
                    key("\u0928", joinMoreKeys("\u091E", "\u0919", "\u0928\u093C")),
                    // U+0935: "व" DEVANAGARI LETTER VA
                    "\u0935",
                    // U+0932: "ल" DEVANAGARI LETTER LA
                    // U+090C: "ऌ" DEVANAGARI LETTER VOCALIC L
                    // U+0961: "ॡ" DEVANAGARI LETTER VOCALIC LL
                    key("\u0932", joinMoreKeys("\u090C", "\u0961")),
                    // U+0938: "स" DEVANAGARI LETTER SA
                    "\u0938",
                    // U+092F: "य" DEVANAGARI LETTER YA
                    // U+095F: "य़" DEVANAGARI LETTER YYA
                    key("\u092F", moreKey("\u095F")),
                    // U+093C: "़" DEVANAGARI SIGN NUKTA
                    // U+097D: "ॽ" DEVANAGARI LETTER GLOTTAL STOP
                    // U+0970: "॰" DEVANAGARI ABBREVIATION SIGN
                    // U+093D: "ऽ" DEVANAGARI SIGN AVAGRAHA
                    key(SIGN_NUKTA, "\u093C", joinMoreKeys(
                            moreKey(LETTER_GLOTTAL_STOP, "\u097D"),
                            moreKey(ABBREVIATION_SIGN, "\u0970"),
                            moreKey(SIGN_AVAGRAHA, "\u093D"))))
            .build();

    private static final ExpectedKey[][] ALPHABET_SHIFTED_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+0914: "औ" DEVANAGARI LETTER AU
                    // U+0912/U+0902: "ऒं" DEVANAGARI LETTER SHORT O//DEVANAGARI SIGN ANUSVARA
                    key("\u0914", moreKey("\u0912\u0902")),
                    // U+0910: "ऐ" DEVANAGARI LETTER AI
                    // U+0910/U+0902: "ऐं" DEVANAGARI LETTER AI/DEVANAGARI SIGN ANUSVARA
                    key("\u0910", moreKey("\u0910\u0902")),
                    // U+0906: "आ" DEVANAGARI LETTER AA
                    // U+0906/U+0902: "आं" DEVANAGARI LETTER AA/DEVANAGARI SIGN ANUSVARA
                    // U+0906/U+0901: "आँ" DEVANAGARI LETTER AA/DEVANAGARI SIGN CANDRABINDU
                    key("\u0906", joinMoreKeys("\u0906\u0902", "\u0906\u0901")),
                    // U+0908: "ई" DEVANAGARI LETTER II
                    // U+0908/U+0902: "ईं" DEVANAGARI LETTER II/DEVANAGARI SIGN ANUSVARA
                    key("\u0908", moreKey("\u0908\u0902")),
                    // U+090A: "ऊ" DEVANAGARI LETTER UU
                    // U+090A/U+0902: "ऊं" DEVANAGARI LETTER UU/DEVANAGARI SIGN ANUSVARA
                    // U+090A/U+0901: "ऊँ" DEVANAGARI LETTER UU/DEVANAGARI SIGN CANDRABINDU
                    key("\u090A", joinMoreKeys("\u090A\u0902", "\u090A\u0901")),
                    // U+092D: "भ" DEVANAGARI LETTER BHA
                    // U+0903: "ः" DEVANAGARI SIGN VISARGA
                    // U+0918: "घ" DEVANAGARI LETTER GHA
                    "\u092D", key(SIGN_VISARGA, "\u0903"), "\u0918",
                    // U+0927: "ध" DEVANAGARI LETTER DHA
                    // U+0915/U+094D/U+0937:
                    //     "क्ष" DEVANAGARI LETTER KA/DEVANAGARI SIGN VIRAMA/DEVANAGARI LETTER SSA
                    // U+0936/U+094D/U+0930:
                    //     "श्र" DEVANAGARI LETTER SHA/DEVANAGARI SIGN VIRAMA/DEVANAGARI LETTER RA
                    key("\u0927", joinMoreKeys("\u0915\u094D\u0937", "\u0936\u094D\u0930")),
                    // U+091D: "झ" DEVANAGARI LETTER JHA
                    // U+0922: "ढ" DEVANAGARI LETTER DDHA
                    "\u091D", "\u0922")
            .setKeysOfRow(2,
                    // U+0913: "ओ" DEVANAGARI LETTER O
                    // U+0913/U+0902: "ओं" DEVANAGARI LETTER O/DEVANAGARI SIGN ANUSVARA
                    // U+0911: "ऑ" DEVANAGARI LETTER CANDRA O
                    // U+0912: "ऒ" DEVANAGARI LETTER SHORT O
                    key("\u0913", joinMoreKeys("\u0913\u0902", "\u0911", "\u0912")),
                    // U+090F: "ए" DEVANAGARI LETTER E
                    // U+090F/U+0902: "एं" DEVANAGARI LETTER E/DEVANAGARI SIGN ANUSVARA
                    // U+090F/U+0901: "एँ" DEVANAGARI LETTER E/DEVANAGARI SIGN CANDRABINDU
                    // U+090D: "ऍ" DEVANAGARI LETTER CANDRA E
                    // U+090E: "ऎ" DEVANAGARI LETTER SHORT E
                    key("\u090F", joinMoreKeys("\u090F\u0902", "\u090F\u0901", "\u090D", "\u090E")),
                    // U+0905: "अ" DEVANAGARI LETTER A
                    // U+0905/U+0902: "अं" DEVANAGARI LETTER A/DEVANAGARI SIGN ANUSVARA
                    // U+0905/U+0901: "अँ" DEVANAGARI LETTER A/DEVANAGARI SIGN CANDRABINDU
                    key("\u0905", joinMoreKeys("\u0905\u0902", "\u0905\u0901")),
                    // U+0907: "इ" DEVANAGARI LETTER I
                    // U+0907/U+0902: "इं" DEVANAGARI LETTER I/DEVANAGARI SIGN ANUSVARA
                    // U+0907/U+0901: "इं" DEVANAGARI LETTER I/DEVANAGARI SIGN CANDRABINDU
                    key("\u0907", joinMoreKeys("\u0907\u0902", "\u0907\u0901")),
                    // U+0909: "उ" DEVANAGARI LETTER U
                    // U+0909/U+0902: "उं" DEVANAGARI LETTER U/DEVANAGARI SIGN ANUSVARA
                    // U+0909/U+0901: "उँ" DEVANAGARI LETTER U/DEVANAGARI SIGN CANDRABINDU
                    key("\u0909", joinMoreKeys("\u0909\u0902", "\u0909\u0901")),
                    // U+092B: "फ" DEVANAGARI LETTER PHA
                    // U+092B/U+093C: "फ़" DEVANAGARI LETTER PHA/DEVANAGARI SIGN NUKTA
                    key("\u092B", moreKey("\u092B\u093C")),
                    // U+0931: "ऱ" DEVANAGARI LETTER RRA
                    // U+094D/U+0930: "्र" DEVANAGARI SIGN VIRAMA/DEVANAGARI LETTER RA
                    // U+0930/U+094D: "र्" DEVANAGARI LETTER RA/DEVANAGARI SIGN VIRAMA
                    key("\u0931", joinMoreKeys("\u094D\u0930", "\u0930\u094D")),
                    // U+0916: "ख" DEVANAGARI LETTER KHA
                    // U+0916/U+093C: "ख़" DEVANAGARI LETTER KHA/DEVANAGARI SIGN NUKTA
                    key("\u0916", moreKey("\u0916\u093C")),
                    // U+0925: "थ" DEVANAGARI LETTER THA
                    // U+091B: "छ" DEVANAGARI LETTER CHA
                    // U+0920: "ठ" DEVANAGARI LETTER TTHA
                    "\u0925", "\u091B", "\u0920")
            .setKeysOfRow(3,
                    // U+0911: "ऑ" DEVANAGARI LETTER CANDRA O
                    "\u0911",
                    // U+0901: "ँ" DEVANAGARI SIGN CANDRABINDU
                    // U+0945: "ॅ" DEVANAGARI VOWEL SIGN CANDRA E
                    key(SIGN_CANDRABINDU, "\u0901", moreKey(VOWEL_SIGN_CANDRA_E, "\u0945")),
                    // U+0923: "ण" DEVANAGARI LETTER NNA
                    // U+0929: "ऩ" DEVANAGARI LETTER NNNA
                    "\u0923", "\u0929",
                    // U+0933: "ळ" DEVANAGARI LETTER LLA
                    // U+0934: "ऴ" DEVANAGARI LETTER LLLA
                    key("\u0933", moreKey("\u0934")),
                    // U+0936: "श" DEVANAGARI LETTER SHA
                    // U+0937: "ष" DEVANAGARI LETTER SSA
                    "\u0936", "\u0937",
                    // U+0943: "ृ" DEVANAGARI VOWEL SIGN VOCALIC R
                    // U+0944: "ॄ" DEVANAGARI VOWEL SIGN VOCALIC RR
                    key(VOWEL_SIGN_VOCALIC_R, "\u0943", moreKey(VOWEL_SIGN_VOCALIC_RR, "\u0944")),
                    // U+091E: "ञ" DEVANAGARI LETTER NYA
                    "\u091E")
            .build();

    static class HindiSymbols extends Symbols {
        public HindiSymbols(final LayoutCustomizer customizer) {
            super(customizer);
        }

        @Override
        public ExpectedKey[][] getLayout(final boolean isPhone) {
            return new ExpectedKeyboardBuilder(super.getLayout(isPhone))
                    // U+0967: "१" DEVANAGARI DIGIT ONE
                    // U+00B9: "¹" SUPERSCRIPT ONE
                    // U+00BD: "½" VULGAR FRACTION ONE HALF
                    // U+2153: "⅓" VULGAR FRACTION ONE THIRD
                    // U+00BC: "¼" VULGAR FRACTION ONE QUARTER
                    // U+215B: "⅛" VULGAR FRACTION ONE EIGHTH
                    .replaceKeyOfLabel("1", key("\u0967",
                            joinMoreKeys("1", "\u00B9", "\u00BD", "\u2153", "\u00BC", "\u215B")))
                    // U+0968: "२" DEVANAGARI DIGIT TWO
                    // U+00B2: "²" SUPERSCRIPT TWO
                    // U+2154: "⅔" VULGAR FRACTION TWO THIRDS
                    .replaceKeyOfLabel("2", key("\u0968", joinMoreKeys("2", "\u00B2", "\u2154")))
                    // U+0969: "३" DEVANAGARI DIGIT THREE
                    // U+00B3: "³" SUPERSCRIPT THREE
                    // U+00BE: "¾" VULGAR FRACTION THREE QUARTERS
                    // U+215C: "⅜" VULGAR FRACTION THREE EIGHTHS
                    .replaceKeyOfLabel("3", key("\u0969",
                            joinMoreKeys("3", "\u00B3", "\u00BE","\u215C")))
                    // U+096A: "४" DEVANAGARI DIGIT FOUR
                    // U+2074: "⁴" SUPERSCRIPT FOUR
                    .replaceKeyOfLabel("4", key("\u096A", joinMoreKeys("4", "\u2074")))
                    // U+096B: "५" DEVANAGARI DIGIT FIVE
                    // U+215D: "⅝" VULGAR FRACTION FIVE EIGHTHS
                    .replaceKeyOfLabel("5", key("\u096B", joinMoreKeys("5", "\u215D")))
                    // U+096C: "६" DEVANAGARI DIGIT SIX
                    .replaceKeyOfLabel("6", key("\u096C", moreKey("6")))
                    // U+096D: "७" DEVANAGARI DIGIT SEVEN
                    // U+215E: "⅞" VULGAR FRACTION SEVEN EIGHTHS
                    .replaceKeyOfLabel("7", key("\u096D", joinMoreKeys("7", "\u215E")))
                    // U+096E: "८" DEVANAGARI DIGIT EIGHT
                    .replaceKeyOfLabel("8", key("\u096E", moreKey("8")))
                    // U+096F: "९" DEVANAGARI DIGIT NINE
                    .replaceKeyOfLabel("9", key("\u096F", moreKey("9")))
                    // U+0966: "०" DEVANAGARI DIGIT ZERO
                    // U+207F: "ⁿ" SUPERSCRIPT LATIN SMALL LETTER N
                    // U+2205: "∅" EMPTY SET
                    .replaceKeyOfLabel("0", key("\u0966", joinMoreKeys("0", "\u207F", "\u2205")))
                    .build();
        }
    }
}
