/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.latin;

import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.Suppress;
import android.util.Pair;

/*
 * Relevant characters for this test :
 * Spurs the need to reorder :
 * U+1031 MYANMAR VOWEL SIGN E : ေ
 * U+1004 U+103A U+1039 Kinzi. It's a compound character.
 *
 * List of consonants :
 * U+1000 MYANMAR LETTER KA က
 * U+1001 MYANMAR LETTER KHA ခ
 * U+1002 MYANMAR LETTER GA ဂ
 * U+1003 MYANMAR LETTER GHA ဃ
 * U+1004 MYANMAR LETTER NGA င
 * U+1005 MYANMAR LETTER CA စ
 * U+1006 MYANMAR LETTER CHA ဆ
 * U+1007 MYANMAR LETTER JA ဇ
 * U+1008 MYANMAR LETTER JHA ဈ
 * U+1009 MYANMAR LETTER NYA ဉ
 * U+100A MYANMAR LETTER NNYA ည
 * U+100B MYANMAR LETTER TTA ဋ
 * U+100C MYANMAR LETTER TTHA ဌ
 * U+100D MYANMAR LETTER DDA ဍ
 * U+100E MYANMAR LETTER DDHA ဎ
 * U+100F MYANMAR LETTER NNA ဏ
 * U+1010 MYANMAR LETTER TA တ
 * U+1011 MYANMAR LETTER THA ထ
 * U+1012 MYANMAR LETTER DA ဒ
 * U+1013 MYANMAR LETTER DHA ဓ
 * U+1014 MYANMAR LETTER NA န
 * U+1015 MYANMAR LETTER PA ပ
 * U+1016 MYANMAR LETTER PHA ဖ
 * U+1017 MYANMAR LETTER BA ဗ
 * U+1018 MYANMAR LETTER BHA ဘ
 * U+1019 MYANMAR LETTER MA မ
 * U+101A MYANMAR LETTER YA ယ
 * U+101B MYANMAR LETTER RA ရ
 * U+101C MYANMAR LETTER LA လ
 * U+101D MYANMAR LETTER WA ဝ
 * U+101E MYANMAR LETTER SA သ
 * U+101F MYANMAR LETTER HA ဟ
 * U+1020 MYANMAR LETTER LLA ဠ
 * U+103F MYANMAR LETTER GREAT SA ဿ
 *
 * List of medials :
 * U+103B MYANMAR CONSONANT SIGN MEDIAL YA ျ
 * U+103C MYANMAR CONSONANT SIGN MEDIAL RA ြ
 * U+103D MYANMAR CONSONANT SIGN MEDIAL WA ွ
 * U+103E MYANMAR CONSONANT SIGN MEDIAL HA ှ
 * U+105E MYANMAR CONSONANT SIGN MON MEDIAL NA ၞ
 * U+105F MYANMAR CONSONANT SIGN MON MEDIAL MA ၟ
 * U+1060 MYANMAR CONSONANT SIGN MON MEDIAL LA ၠ
 * U+1082 MYANMAR CONSONANT SIGN SHAN MEDIAL WA ႂ
 *
 * Other relevant characters :
 * U+200C ZERO WIDTH NON-JOINER
 * U+200B ZERO WIDTH SPACE
 */

@LargeTest
// These tests are inactive until the combining code for Myanmar Reordering is sorted out.
@Suppress
@SuppressWarnings("rawtypes")
public class InputLogicTestsReorderingMyanmar extends InputTestsBase {
    // The tests are formatted as follows.
    // Each test is an entry in the array of Pair arrays.

    // One test is an array of pairs. Each pair contains, in the `first' member,
    // the code points that the next key press should contain. In the `second'
    // member is stored the string that should be in the text view after this
    // key press.

    private static final Pair[][] TESTS = {

        // Tests for U+1031 MYANMAR VOWEL SIGN E : ေ
        new Pair[] { // Type : U+1031 U+1000 U+101F ေ က ဟ
            Pair.create(new int[] { 0x1031 }, "\u1031"), // ေ
            Pair.create(new int[] { 0x1000 }, "\u1000\u1031"), // ကေ
            Pair.create(new int[] { 0x101F }, "\u1000\u1031\u101F") // ကေဟ
        },

        new Pair[] { // Type : U+1000 U+1031 U+101F က ေ ဟ
            Pair.create(new int[] { 0x1000 }, "\u1000"), // က
            Pair.create(new int[] { 0x1031 }, "\u1000\u200B\u1031"), // က‌ေ
            Pair.create(new int[] { 0x101F }, "\u1000\u101F\u1031") // ကဟေ
        },

        new Pair[] { // Type : U+1031 U+101D U+103E U+1018 ေ ဝ ှ ဘ
            Pair.create(new int[] { 0x1031 }, "\u1031"), // ေ
            Pair.create(new int[] { 0x101D }, "\u101D\u1031"), // ဝေ
            Pair.create(new int[] { 0x103E }, "\u101D\u103E\u1031"), // ဝှေ
            Pair.create(new int[] { 0x1018 }, "\u101D\u103E\u1031\u1018") // ဝှေဘ
        },

        new Pair[] { // Type : U+1031 U+1014 U+1031 U+1000 U+102C U+1004 U+103A U+1038 U+101C
            // U+102C U+1038 U+104B ေ န ေ က ာ င ် း လ ာ း ။
            Pair.create(new int[] { 0x1031 }, "\u1031"), // ေ
            Pair.create(new int[] { 0x1014 }, "\u1014\u1031"), // နေ
            Pair.create(new int[] { 0x1031 }, "\u1014\u1031\u1031"), // နေ‌ေ
            Pair.create(new int[] { 0x1000 }, "\u1014\u1031\u1000\u1031"), // နေကေ
            Pair.create(new int[] { 0x102C }, "\u1014\u1031\u1000\u1031\u102C"), // နေကော
            Pair.create(new int[] { 0x1004 }, "\u1014\u1031\u1000\u1031\u102C\u1004"), // နေကောင
            Pair.create(new int[] { 0x103A }, // နေကောင်
                    "\u1014\u1031\u1000\u1031\u102C\u1004\u103A"),
            Pair.create(new int[] { 0x1038 }, // နေကောင်း
                    "\u1014\u1031\u1000\u1031\u102C\u1004\u103A\u1038"),
            Pair.create(new int[] { 0x101C }, // နေကောင်းလ
                    "\u1014\u1031\u1000\u1031\u102C\u1004\u103A\u1038\u101C"),
            Pair.create(new int[] { 0x102C }, // နေကောင်းလာ
                    "\u1014\u1031\u1000\u1031\u102C\u1004\u103A\u1038\u101C\u102C"),
            Pair.create(new int[] { 0x1038 }, // နေကောင်းလား
                    "\u1014\u1031\u1000\u1031\u102C\u1004\u103A\u1038\u101C\u102C\u1038"),
            Pair.create(new int[] { 0x104B }, // နေကောင်းလား။
                    "\u1014\u1031\u1000\u1031\u102C\u1004\u103A\u1038\u101C\u102C\u1038\u104B")
        },

        new Pair[] { // Type : U+1031 U+1031 U+1031 U+1000 ေ ေ ေ က
            Pair.create(new int[] { 0x1031 }, "\u1031"), // ေ
            Pair.create(new int[] { 0x1031 }, "\u1031\u1031"), // ေေ
            Pair.create(new int[] { 0x1031 }, "\u1031\u1031\u1031"), // U+1031ေေေ
            Pair.create(new int[] { 0x1000 }, "\u1031\u1031\u1000\u1031") // ေေကေ
        },

        new Pair[] { // Type : U+1031 U+1001 U+103B U+103D U+1038 ေ ခ ျ ွ း
            Pair.create(new int[] { 0x1031 }, "\u1031"), // ေ
            Pair.create(new int[] { 0x1001 }, "\u1001\u1031"), // ခေ
            Pair.create(new int[] { 0x103B }, "\u1001\u103B\u1031"), // ချေ
            Pair.create(new int[] { 0x103D }, "\u1001\u103B\u103D\u1031"), // ချွေ
            Pair.create(new int[] { 0x1038 }, "\u1001\u103B\u103D\u1031\u1038") // ချွေး
        },

        // Tests for Kinzi U+1004 U+103A U+1039 :

        /* Kinzi reordering is not implemented yet. Uncomment these tests when it is.

        new Pair[] { // Type : U+1021 U+1002 (U+1004 U+103A U+1039)
            // U+101C U+1014 U+103A အ ဂ (င ် ္) လ န ်
            Pair.create(new int[] { 0x1021 }, "\u1021"), // အ
            Pair.create(new int[] { 0x1002 }, "\u1021\u1002"), // အဂ
            Pair.create(new int[] { 0x1004, 0x103A, 0x1039 }, // အင်္ဂ
                    "\u1021\u1004\u103A\u1039\u1002"),
            Pair.create(new int[] { 0x101C }, // အင်္ဂလ
                    "\u1021\u1004\u103A\u1039\u1002\u101C"),
            Pair.create(new int[] { 0x1014 }, // အင်္ဂလန
                    "\u1021\u1004\u103A\u1039\u1002\u101C\u1014"),
            Pair.create(new int[] { 0x103A }, // အင်္ဂလန်
                    "\u1021\u1004\u103A\u1039\u1002\u101C\u1014\u103A")
        },

        new Pair[] { //Type : kinzi after a whole syllable U+101E U+1001 U+103B U+102D U+102F
            // (U+1004 U+103A U+1039) U+1004 U+103A U+1038 သ ခ ျ ိ ု င ် ္ င ် း
            Pair.create(new int[] { 0x101E }, "\u101E"), // သခ
            Pair.create(new int[] { 0x1001 }, "\u101E\u1001"), // သခ
            Pair.create(new int[] { 0x103B }, "\u101E\u1001\u103B"), // သချ
            Pair.create(new int[] { 0x102D }, "\u101E\u1001\u103B\u102D"), // သချိ
            Pair.create(new int[] { 0x102F }, "\u101E\u1001\u103B\u102D\u102F"), // သချို
            Pair.create(new int[] { 0x1004, 0x103A, 0x1039}, // သင်္ချို
                    "\u101E\u1004\u103A\u1039\u1001\u103B\u102D\u102F"),
            Pair.create(new int[] { 0x1004 }, // သင်္ချိုင
                    "\u101E\u1004\u103A\u1039\u1001\u103B\u102D\u102F\u1004"),
            Pair.create(new int[] { 0x103A }, // သင်္ချိုင်
                    "\u101E\u1004\u103A\u1039\u1001\u103B\u102D\u102F\u1004\u103A"),
            Pair.create(new int[] { 0x1038 }, // သင်္ချိုင်း
                    "\u101E\u1004\u103A\u1039\u1001\u103B\u102D\u102F\u1004\u103A\u1038")
        },

        new Pair[] { // Type : kinzi after the consonant U+101E U+1001 (U+1004 U+103A U+1039)
            // U+103B U+102D U+102F U+1004 U+103A U+1038 သ ခ င ် ္ ျ ိ ု င ် း
            Pair.create(new int[] { 0x101E }, "\u101E"), // သခ
            Pair.create(new int[] { 0x1001 }, "\u101E\u1001"), // သခ
            Pair.create(new int[] { 0x1004, 0x103A, 0x1039 }, // သင်္ခ
                    "\u101E\u1004\u103A\u1039\u1001"),
            Pair.create(new int[] { 0x103B }, // သင်္ချ
                    "\u101E\u1004\u103A\u1039\u1001\u103B"),
            Pair.create(new int[] { 0x102D }, // သင်္ချိ
                    "\u101E\u1004\u103A\u1039\u1001\u103B\u102D"),
            Pair.create(new int[] { 0x102F }, // သင်္ချို
                    "\u101E\u1004\u103A\u1039\u1001\u103B\u102D\u102F"),
            Pair.create(new int[] { 0x1004 }, // သင်္ချိုင
                    "\u101E\u1004\u103A\u1039\u1001\u103B\u102D\u102F\u1004"),
            Pair.create(new int[] { 0x103A }, // သင်္ချိုင်
                    "\u101E\u1004\u103A\u1039\u1001\u103B\u102D\u102F\u1004\u103A"),
            Pair.create(new int[] { 0x1038 }, // သင်္ချိုင်း
                    "\u101E\u1004\u103A\u1039\u1001\u103B\u102D\u102F\u1004\u103A\u1038")
        },
        */
    };

    @SuppressWarnings("unchecked")
    private void doMyanmarTest(final int testNumber, final Pair[] test) {
        int stepNumber = 0;
        for (final Pair<int[], String> step : test) {
            ++stepNumber;
            final int[] input = step.first;
            final String expectedResult = step.second;
            if (input.length > 1) {
                mLatinIME.onTextInput(new String(input, 0, input.length));
            } else {
                type(input[0]);
            }
            assertEquals("Myanmar reordering test " + testNumber + ", step " + stepNumber,
                    expectedResult, mEditText.getText().toString());
        }
    }

    public void testMyanmarReordering() {
        int testNumber = 0;
        changeLanguage("my_MM", "CombiningRules=MyanmarReordering");
        for (final Pair[] test : TESTS) {
            // Small trick to reset LatinIME : setText("") and send updateSelection with values
            // LatinIME has never seen, and cursor pos 0,0.
            mEditText.setText("");
            mLatinIME.onUpdateSelection(1, 1, 0, 0, -1, -1);
            doMyanmarTest(++testNumber, test);
        }
    }
}
