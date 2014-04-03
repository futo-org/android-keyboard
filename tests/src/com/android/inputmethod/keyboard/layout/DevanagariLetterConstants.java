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

import android.os.Build;

/**
 * This class offers label strings of Devanagari letters that need the dotted circle to draw
 * its glyph.
 */
class DevanagariLetterConstants {
    private static final boolean NEEDS_DOTTED_CIRCLE =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN;
    // U+25CC: "◌" DOTTED CIRCLE
    private static final String DOTTED_CIRCLE = NEEDS_DOTTED_CIRCLE ? "\u25CC" : "";

    // U+0901: "ँ" DEVANAGARI SIGN CANDRABINDU
    static final String SIGN_CANDRABINDU = DOTTED_CIRCLE + "\u0901";
    // U+0902: "ं" DEVANAGARI SIGN ANUSVARA
    static final String SIGN_ANUSVARA = DOTTED_CIRCLE + "\u0902";
    // U+0903: "ः" DEVANAGARI SIGN VISARGA
    static final String SIGN_VISARGA = DOTTED_CIRCLE + "\u0903";
    // U+093C: "़" DEVANAGARI SIGN NUKTA
    static final String SIGN_NUKTA = DOTTED_CIRCLE + "\u093C";
    // U+093D: "ऽ" DEVANAGARI SIGN AVAGRAHA
    static final String SIGN_AVAGRAHA = DOTTED_CIRCLE + "\u093D";
    // U+093E: "ा" DEVANAGARI VOWEL SIGN AA
    static final String VOWEL_SIGN_AA = DOTTED_CIRCLE + "\u093E";
    // U+093F: "ि" DEVANAGARI VOWEL SIGN I
    static final String VOWEL_SIGN_I = DOTTED_CIRCLE + "\u093F";
    // U+0940: "ी" DEVANAGARI VOWEL SIGN II
    static final String VOWEL_SIGN_II = DOTTED_CIRCLE + "\u0940";
    // U+0941: "ु" DEVANAGARI VOWEL SIGN U
    static final String VOWEL_SIGN_U = DOTTED_CIRCLE + "\u0941";
    // U+0942: "ू" DEVANAGARI VOWEL SIGN UU
    static final String VOWEL_SIGN_UU = DOTTED_CIRCLE + "\u0942";
    // U+0943: "ृ" DEVANAGARI VOWEL SIGN VOCALIC R
    static final String VOWEL_SIGN_VOCALIC_R = DOTTED_CIRCLE + "\u0943";
    // U+0944: "ॄ" DEVANAGARI VOWEL SIGN VOCALIC RR
    static final String VOWEL_SIGN_VOCALIC_RR = DOTTED_CIRCLE + "\u0944";
    // U+0945: "ॅ" DEVANAGARI VOWEL SIGN CANDRA E
    static final String VOWEL_SIGN_CANDRA_E = DOTTED_CIRCLE + "\u0945";
    // U+0947: "े" DEVANAGARI VOWEL SIGN E
    static final String VOWEL_SIGN_E = DOTTED_CIRCLE + "\u0947";
    // U+0948: "ै" DEVANAGARI VOWEL SIGN AI
    static final String VOWEL_SIGN_AI = DOTTED_CIRCLE + "\u0948";
    // U+0949: "ॉ" DEVANAGARI VOWEL SIGN CANDRA O
    static final String VOWEL_SIGN_CANDRA_O = DOTTED_CIRCLE + "\u0949";
    // U+094A: "ॊ" DEVANAGARI VOWEL SIGN SHORT O
    static final String VOWEL_SIGN_SHORT_O = DOTTED_CIRCLE + "\u094A";
    // U+094B: "ो" DEVANAGARI VOWEL SIGN O
    static final String VOWEL_SIGN_O = DOTTED_CIRCLE + "\u094B";
    // U+094C: "ौ" DEVANAGARI VOWEL SIGN AU
    static final String VOWEL_SIGN_AU = DOTTED_CIRCLE + "\u094C";
    // U+094D: "्" DEVANAGARI SIGN VIRAMA
    static final String SIGN_VIRAMA = DOTTED_CIRCLE + "\u094D";
    // U+0970: "॰" DEVANAGARI ABBREVIATION SIGN
    static final String ABBREVIATION_SIGN = DOTTED_CIRCLE + "\u0970";
    // U+097D: "ॽ" DEVANAGARI LETTER GLOTTAL STOP
    static final String LETTER_GLOTTAL_STOP = DOTTED_CIRCLE + "\u097D";
}
