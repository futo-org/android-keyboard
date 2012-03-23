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
    /* public for test */
    final public static int NUL = KeyDetector.NOT_A_CODE;

    // This must be the same as MAX_PROXIMITY_CHARS_SIZE else it will not work inside
    // native code - this value is passed at creation of the binary object and reused
    // as the size of the passed array afterwards so they can't be different.
    final public static int ROW_SIZE = ProximityInfo.MAX_PROXIMITY_CHARS_SIZE;

    // The number of keys in a row of the grid used by the spell checker.
    final public static int PROXIMITY_GRID_WIDTH = 11;
    // The number of rows in the grid used by the spell checker.
    final public static int PROXIMITY_GRID_HEIGHT = 3;

    final private static int NOT_AN_INDEX = -1;
    final public static int NOT_A_COORDINATE_PAIR = -1;

    // Helper methods
    final protected static void buildProximityIndices(final int[] proximity,
            final TreeMap<Integer, Integer> indices) {
        for (int i = 0; i < proximity.length; i += ROW_SIZE) {
            if (NUL != proximity[i]) indices.put(proximity[i], i / ROW_SIZE);
        }
    }
    final protected static int computeIndex(final int characterCode,
            final TreeMap<Integer, Integer> indices) {
        final Integer result = indices.get(characterCode);
        if (null == result) return NOT_AN_INDEX;
        return result;
    }

    private static class Latin {
        // This is a map from the code point to the index in the PROXIMITY array.
        // At the time the native code to read the binary dictionary needs the proximity info be
        // passed as a flat array spaced by MAX_PROXIMITY_CHARS_SIZE columns, one for each input
        // character.
        // Since we need to build such an array, we want to be able to search in our big proximity
        // data quickly by character, and a map is probably the best way to do this.
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
        final static int[] PROXIMITY = {
            // Proximity for row 1. This must have exactly ROW_SIZE entries for each letter,
            // and exactly PROXIMITY_GRID_WIDTH letters for a row. Pad with NUL's.
            // The number of rows must be exactly PROXIMITY_GRID_HEIGHT.
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
            NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,

            // Proximity for row 2. See comment above about size.
            'a', 'z', 'x', 's', 'w', 'q', 'e', 'i', 'o', 'u', NUL, NUL, NUL, NUL, NUL, NUL,
            's', 'q', 'a', 'z', 'x', 'c', 'd', 'e', 'w', NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'd', 'w', 's', 'x', 'c', 'v', 'f', 'r', 'e', NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'f', 'e', 'd', 'c', 'v', 'b', 'g', 't', 'r', NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'g', 'r', 'f', 'v', 'b', 'n', 'h', 'y', 't', NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'h', 't', 'g', 'b', 'n', 'm', 'j', 'u', 'y', NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'j', 'y', 'h', 'n', 'm', 'k', 'i', 'u', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'k', 'u', 'j', 'm', 'l', 'o', 'i', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'l', 'i', 'k', 'p', 'o', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,

            // Proximity for row 3. See comment above about size.
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
            buildProximityIndices(PROXIMITY, INDICES);
        }
        static int getIndexOf(int characterCode) {
            return computeIndex(characterCode, INDICES);
        }
    }

    private static class Cyrillic {
        final private static TreeMap<Integer, Integer> INDICES = new TreeMap<Integer, Integer>();
        // TODO: The following table is solely based on the keyboard layout. Consult with Russian
        // speakers on commonly misspelled words/letters.
        final static int[] PROXIMITY = {
            // Proximity for row 1. This must have exactly ROW_SIZE entries for each letter,
            // and exactly PROXIMITY_GRID_WIDTH letters for a row. Pad with NUL's.
            // The number of rows must be exactly PROXIMITY_GRID_HEIGHT.
            'й', 'ц', 'ф', 'ы', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'ц', 'й', 'ф', 'ы', 'в', 'у', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'у', 'ц', 'ы', 'в', 'а', 'к', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'к', 'у', 'в', 'а', 'п', 'е', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'е', 'к', 'а', 'п', 'р', 'н', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'н', 'е', 'п', 'р', 'о', 'г', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'г', 'н', 'р', 'о', 'л', 'ш', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'ш', 'г', 'о', 'л', 'д', 'щ', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'щ', 'ш', 'л', 'д', 'ж', 'з', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'з', 'щ', 'д', 'ж', 'э', 'х', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'х', 'з', 'ж', 'э', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,

            // Proximity for row 2. See comment above about size.
            'ф', 'й', 'ц', 'ы', 'я', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'ы', 'й', 'ц', 'у', 'ф', 'в', 'я', 'ч', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'в', 'ц', 'у', 'к', 'ы', 'а', 'я', 'ч', 'с', NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'а', 'у', 'к', 'е', 'в', 'п', 'ч', 'с', 'м', NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'п', 'к', 'е', 'н', 'а', 'р', 'с', 'м', 'и', NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'р', 'е', 'н', 'г', 'п', 'о', 'м', 'и', 'т', NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'о', 'н', 'г', 'ш', 'р', 'л', 'и', 'т', 'ь', NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'л', 'г', 'ш', 'щ', 'о', 'д', 'т', 'ь', 'б', NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'д', 'ш', 'щ', 'з', 'л', 'ж', 'ь', 'б', 'ю', NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'ж', 'щ', 'з', 'х', 'д', 'э', 'б', 'ю', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'э', 'з', 'х', 'ю', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,

            // Proximity for row 3. See comment above about size.
            'я', 'ф', 'ы', 'в', 'ч', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'ч', 'ы', 'в', 'а', 'я', 'с', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'с', 'в', 'а', 'п', 'ч', 'м', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'м', 'а', 'п', 'р', 'с', 'и', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'и', 'п', 'р', 'о', 'м', 'т', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'т', 'р', 'о', 'л', 'и', 'ь', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'ь', 'о', 'л', 'д', 'т', 'б', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'б', 'л', 'д', 'ж', 'ь', 'ю', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'ю', 'д', 'ж', 'э', 'б', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
        };
        static {
            buildProximityIndices(PROXIMITY, INDICES);
        }
        static int getIndexOf(int characterCode) {
            return computeIndex(characterCode, INDICES);
        }
    }

    public static int[] getProximityForScript(final int script) {
        switch (script) {
            case AndroidSpellCheckerService.SCRIPT_LATIN:
                return Latin.PROXIMITY;
            case AndroidSpellCheckerService.SCRIPT_CYRILLIC:
                return Cyrillic.PROXIMITY;
            default:
                throw new RuntimeException("Wrong script supplied: " + script);
        }
    }

    private static int getIndexOfCodeForScript(final int codePoint, final int script) {
        switch (script) {
            case AndroidSpellCheckerService.SCRIPT_LATIN:
                return Latin.getIndexOf(codePoint);
            case AndroidSpellCheckerService.SCRIPT_CYRILLIC:
                return Cyrillic.getIndexOf(codePoint);
            default:
                throw new RuntimeException("Wrong script supplied: " + script);
        }
    }

    // Returns (Y << 16) + X to avoid creating a temporary object. This is okay because
    // X and Y are limited to PROXIMITY_GRID_WIDTH resp. PROXIMITY_GRID_HEIGHT which is very
    // inferior to 1 << 16
    // As an exception, this returns NOT_A_COORDINATE_PAIR if the key is not on the grid
    public static int getXYForCodePointAndScript(final int codePoint, final int script) {
        final int index = getIndexOfCodeForScript(codePoint, script);
        if (NOT_AN_INDEX == index) return NOT_A_COORDINATE_PAIR;
        final int y = index / PROXIMITY_GRID_WIDTH;
        final int x = index % PROXIMITY_GRID_WIDTH;
        if (y > PROXIMITY_GRID_HEIGHT) {
            // Safety check, should be entirely useless
            throw new RuntimeException("Wrong y coordinate in spell checker proximity");
        }
        return (y << 16) + x;
    }
}
