/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.latin.spellcheck;

import com.android.inputmethod.keyboard.KeyDetector;
import com.android.inputmethod.keyboard.ProximityInfo;

import java.util.TreeMap;

public class SpellCheckerProximityInfo {
    final private static int NUL = KeyDetector.NOT_A_CODE;

    // This must be the same as MAX_PROXIMITY_CHARS_SIZE else it will not work inside
    // native code - this value is passed at creation of the binary object and reused
    // as the size of the passed array afterwards so they can't be different.
    final public static int ROW_SIZE = ProximityInfo.MAX_PROXIMITY_CHARS_SIZE;

    // This is a map from the code point to the index in the PROXIMITY array.
    // At the time the native code to read the binary dictionary needs the proximity info be passed
    // as a flat array spaced by MAX_PROXIMITY_CHARS_SIZE columns, one for each input character.
    // Since we need to build such an array, we want to be able to search in our big proximity data
    // quickly by character, and a map is probably the best way to do this.
    final private static TreeMap<Integer, Integer> INDICES = new TreeMap<Integer, Integer>();

    // The proximity here is the union of
    // - the proximity for a QWERTY keyboard.
    // - the proximity for an AZERTY keyboard.
    // - the proximity for a QWERTZ keyboard.
    // ...plus, add all characters in the ('a', 'e', 'i', 'o', 'u') set to each other.
    //
    // The reasoning behind this construction is, almost any alphabetic text we may want
    // to spell check has been entered with one of the keyboards above. Also, specifically
    // to English, many spelling errors consist of the last vowel of the word being wrong
    // because in English vowels tend to merge with each other in pronunciation.
    final public static int[] PROXIMITY = {
        'q', 'w', 's', 'a', 'z', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
        'w', 'q', 'a', 's', 'd', 'e', 'x', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
        'e', 'w', 's', 'd', 'f', 'r', 'a', 'i', 'o', 'u', NUL, NUL, NUL, NUL, NUL, NUL,
        'r', 'e', 'd', 'f', 'g', 't', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
        't', 'r', 'f', 'g', 'h', 'y', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
        'y', 't', 'g', 'h', 'j', 'u', 'a', 's', 'd', 'x', NUL, NUL, NUL, NUL, NUL, NUL,
        'u', 'y', 'h', 'j', 'k', 'i', 'a', 'e', 'o', NUL, NUL, NUL, NUL, NUL, NUL, NUL,
        'i', 'u', 'j', 'k', 'l', 'o', 'a', 'e', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
        'o', 'i', 'k', 'l', 'p', 'a', 'e', 'u', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
        'p', 'o', 'l', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,

        'a', 'z', 'x', 's', 'w', 'q', 'e', 'i', 'o', 'u', NUL, NUL, NUL, NUL, NUL, NUL,
        's', 'q', 'a', 'z', 'x', 'c', 'd', 'e', 'w', NUL, NUL, NUL, NUL, NUL, NUL, NUL,
        'd', 'w', 's', 'x', 'c', 'v', 'f', 'r', 'e', 'w', NUL, NUL, NUL, NUL, NUL, NUL,
        'f', 'e', 'd', 'c', 'v', 'b', 'g', 't', 'r', NUL, NUL, NUL, NUL, NUL, NUL, NUL,
        'g', 'r', 'f', 'v', 'b', 'n', 'h', 'y', 't', NUL, NUL, NUL, NUL, NUL, NUL, NUL,
        'h', 't', 'g', 'b', 'n', 'm', 'j', 'u', 'y', NUL, NUL, NUL, NUL, NUL, NUL, NUL,
        'j', 'y', 'h', 'n', 'm', 'k', 'i', 'u', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
        'k', 'u', 'j', 'm', 'l', 'o', 'i', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
        'l', 'i', 'k', 'p', 'o', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
        NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,

        'z', 'a', 's', 'd', 'x', 't', 'g', 'h', 'j', 'u', 'q', 'e', NUL, NUL, NUL, NUL,
        'x', 'z', 'a', 's', 'd', 'c', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
        'c', 'x', 's', 'd', 'f', 'v', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
        'v', 'c', 'd', 'f', 'g', 'b', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
        'b', 'v', 'f', 'g', 'h', 'n', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
        'n', 'b', 'g', 'h', 'j', 'm', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
        'm', 'n', 'h', 'j', 'k', 'l', 'o', 'p', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
        NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
        NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
        NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
    };
    static {
        for (int i = 0; i < PROXIMITY.length; i += ROW_SIZE) {
            if (NUL != PROXIMITY[i]) INDICES.put(PROXIMITY[i], i);
        }
    }
    public static int getIndexOf(int characterCode) {
        final Integer result = INDICES.get(characterCode);
        if (null == result) return -1;
        return result;
    }
}
