/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.inputmethod.latin.spellcheck;

import com.android.inputmethod.keyboard.ProximityInfo;
import com.android.inputmethod.latin.CollectionUtils;
import com.android.inputmethod.latin.Constants;

import java.util.TreeMap;

public final class SpellCheckerProximityInfo extends ProximityInfo {
    public SpellCheckerProximityInfo(final int script) {
        super(getProximityForScript(script), PROXIMITY_GRID_WIDTH, PROXIMITY_GRID_HEIGHT);
    }

    private static final int NUL = Constants.NOT_A_CODE;

    // This must be the same as MAX_PROXIMITY_CHARS_SIZE else it will not work inside
    // native code - this value is passed at creation of the binary object and reused
    // as the size of the passed array afterwards so they can't be different.
    private static final int ROW_SIZE = ProximityInfo.MAX_PROXIMITY_CHARS_SIZE;

    // The number of keys in a row of the grid used by the spell checker.
    private static final int PROXIMITY_GRID_WIDTH = 11;
    // The number of rows in the grid used by the spell checker.
    private static final int PROXIMITY_GRID_HEIGHT = 3;

    private static final int NOT_AN_INDEX = -1;
    public static final int NOT_A_COORDINATE_PAIR = -1;

    // Helper methods
    static void buildProximityIndices(final int[] proximity,
            final TreeMap<Integer, Integer> indices) {
        for (int i = 0; i < proximity.length; i += ROW_SIZE) {
            if (NUL != proximity[i]) indices.put(proximity[i], i / ROW_SIZE);
        }
    }

    static int computeIndex(final int characterCode,
            final TreeMap<Integer, Integer> indices) {
        final Integer result = indices.get(characterCode);
        if (null == result) return NOT_AN_INDEX;
        return result;
    }

    private static final class Latin {
        // This is a map from the code point to the index in the PROXIMITY array.
        // At the time the native code to read the binary dictionary needs the proximity info be
        // passed as a flat array spaced by MAX_PROXIMITY_CHARS_SIZE columns, one for each input
        // character.
        // Since we need to build such an array, we want to be able to search in our big proximity
        // data quickly by character, and a map is probably the best way to do this.
        private static final TreeMap<Integer, Integer> INDICES = CollectionUtils.newTreeMap();

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
        /*
        The Qwerty layout this represents looks like the following:
            q w e r t y u i o p
             a s d f g h j k l
               z x c v b n m
        */
        static final int[] PROXIMITY = {
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
            NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
        };

        static {
            buildProximityIndices(PROXIMITY, INDICES);
        }

        static int getIndexOf(int characterCode) {
            return computeIndex(characterCode, INDICES);
        }
    }

    private static final class Cyrillic {
        private static final TreeMap<Integer, Integer> INDICES = CollectionUtils.newTreeMap();
        // TODO: The following table is solely based on the keyboard layout. Consult with Russian
        // speakers on commonly misspelled words/letters.
        /*
        The Russian layout this represents looks like the following:
            й ц у к е н г ш щ з х
            ф ы в а п р о л д ж э
              я ч с м и т ь б ю

        This gives us the following table:
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

            'я', 'ф', 'ы', 'в', 'ч', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'ч', 'ы', 'в', 'а', 'я', 'с', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'с', 'в', 'а', 'п', 'ч', 'м', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'м', 'а', 'п', 'р', 'с', 'и', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'и', 'п', 'р', 'о', 'м', 'т', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'т', 'р', 'о', 'л', 'и', 'ь', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'ь', 'о', 'л', 'д', 'т', 'б', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'б', 'л', 'д', 'ж', 'ь', 'ю', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'ю', 'д', 'ж', 'э', 'б', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,

        Using the following characters:
        */
        private static final int CY_SHORT_I = '\u0439'; // й
        private static final int CY_TSE = '\u0446'; // ц
        private static final int CY_U = '\u0443'; // у
        private static final int CY_KA = '\u043A'; // к
        private static final int CY_IE = '\u0435'; // е
        private static final int CY_EN = '\u043D'; // н
        private static final int CY_GHE = '\u0433'; // г
        private static final int CY_SHA = '\u0448'; // ш
        private static final int CY_SHCHA = '\u0449'; // щ
        private static final int CY_ZE = '\u0437'; // з
        private static final int CY_HA = '\u0445'; // х
        private static final int CY_EF = '\u0444'; // ф
        private static final int CY_YERU = '\u044B'; // ы
        private static final int CY_VE = '\u0432'; // в
        private static final int CY_A = '\u0430'; // а
        private static final int CY_PE = '\u043F'; // п
        private static final int CY_ER = '\u0440'; // р
        private static final int CY_O = '\u043E'; // о
        private static final int CY_EL = '\u043B'; // л
        private static final int CY_DE = '\u0434'; // д
        private static final int CY_ZHE = '\u0436'; // ж
        private static final int CY_E = '\u044D'; // э
        private static final int CY_YA = '\u044F'; // я
        private static final int CY_CHE = '\u0447'; // ч
        private static final int CY_ES = '\u0441'; // с
        private static final int CY_EM = '\u043C'; // м
        private static final int CY_I = '\u0438'; // и
        private static final int CY_TE = '\u0442'; // т
        private static final int CY_SOFT_SIGN = '\u044C'; // ь
        private static final int CY_BE = '\u0431'; // б
        private static final int CY_YU = '\u044E'; // ю
        static final int[] PROXIMITY = {
            // Proximity for row 1. This must have exactly ROW_SIZE entries for each letter,
            // and exactly PROXIMITY_GRID_WIDTH letters for a row. Pad with NUL's.
            // The number of rows must be exactly PROXIMITY_GRID_HEIGHT.
            CY_SHORT_I, CY_TSE, CY_EF, CY_YERU, NUL, NUL, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_TSE, CY_SHORT_I, CY_EF, CY_YERU, CY_VE, CY_U, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_U, CY_TSE, CY_YERU, CY_VE, CY_A, CY_KA, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_KA, CY_U, CY_VE, CY_A, CY_PE, CY_IE, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_IE, CY_KA, CY_A, CY_PE, CY_ER, CY_EN, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_EN, CY_IE, CY_PE, CY_ER, CY_O, CY_GHE, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_GHE, CY_EN, CY_ER, CY_O, CY_EL, CY_SHA, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_SHA, CY_GHE, CY_O, CY_EL, CY_DE, CY_SHCHA, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_SHCHA, CY_SHA, CY_EL, CY_DE, CY_ZHE, CY_ZE, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_ZE, CY_SHCHA, CY_DE, CY_ZHE, CY_E, CY_HA, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_HA, CY_ZE, CY_ZHE, CY_E, NUL, NUL, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,

            // Proximity for row 2. See comment above about size.
            CY_EF, CY_SHORT_I, CY_TSE, CY_YERU, CY_YA, NUL, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_YERU, CY_SHORT_I, CY_TSE, CY_U, CY_EF, CY_VE, CY_YA, CY_CHE,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_VE, CY_TSE, CY_U, CY_KA, CY_YERU, CY_A, CY_YA, CY_CHE,
                    CY_ES, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_A, CY_U, CY_KA, CY_IE, CY_VE, CY_PE, CY_CHE, CY_ES,
                    CY_EM, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_PE, CY_KA, CY_IE, CY_EN, CY_A, CY_ER, CY_ES, CY_EM,
                    CY_I, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_ER, CY_IE, CY_EN, CY_GHE, CY_PE, CY_O, CY_EM, CY_I,
                    CY_TE, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_O, CY_EN, CY_GHE, CY_SHA, CY_ER, CY_EL, CY_I, CY_TE,
                    CY_SOFT_SIGN, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_EL, CY_GHE, CY_SHA, CY_SHCHA, CY_O, CY_DE, CY_TE, CY_SOFT_SIGN,
                    CY_BE, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_DE, CY_SHA, CY_SHCHA, CY_ZE, CY_EL, CY_ZHE, CY_SOFT_SIGN, CY_BE,
                    CY_YU, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_ZHE, CY_SHCHA, CY_ZE, CY_HA, CY_DE, CY_E, CY_BE, CY_YU,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_E, CY_ZE, CY_HA, CY_YU, NUL, NUL, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,

            // Proximity for row 3. See comment above about size.
            CY_YA, CY_EF, CY_YERU, CY_VE, CY_CHE, NUL, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_CHE, CY_YERU, CY_VE, CY_A, CY_YA, CY_ES, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_ES, CY_VE, CY_A, CY_PE, CY_CHE, CY_EM, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_EM, CY_A, CY_PE, CY_ER, CY_ES, CY_I, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_I, CY_PE, CY_ER, CY_O, CY_EM, CY_TE, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_TE, CY_ER, CY_O, CY_EL, CY_I, CY_SOFT_SIGN, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_SOFT_SIGN, CY_O, CY_EL, CY_DE, CY_TE, CY_BE, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_BE, CY_EL, CY_DE, CY_ZHE, CY_SOFT_SIGN, CY_YU, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            CY_YU, CY_DE, CY_ZHE, CY_E, CY_BE, NUL, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
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

    private static final class Greek {
        private static final TreeMap<Integer, Integer> INDICES = CollectionUtils.newTreeMap();
        // TODO: The following table is solely based on the keyboard layout. Consult with Greek
        // speakers on commonly misspelled words/letters.
        /*
        The Greek layout this represents looks like the following:
            ; ς ε ρ τ υ θ ι ο π
             α σ δ φ γ η ξ κ λ
               ζ χ ψ ω β ν μ

        This gives us the following table:
            'ς', 'ε', 'α', 'σ', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'ε', 'ς', 'ρ', 'σ', 'δ', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'ρ', 'ε', 'τ', 'δ', 'φ', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'τ', 'ρ', 'υ', 'φ', 'γ', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'υ', 'τ', 'θ', 'γ', 'η', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'θ', 'υ', 'ι', 'η', 'ξ', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'ι', 'θ', 'ο', 'ξ', 'κ', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'ο', 'ι', 'π', 'κ', 'λ', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'π', 'ο', 'λ', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,

            'α', 'ς', 'σ', 'ζ', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'σ', 'ς', 'ε', 'α', 'δ', 'ζ', 'χ', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'δ', 'ε', 'ρ', 'σ', 'φ', 'ζ', 'χ', 'ψ', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'φ', 'ρ', 'τ', 'δ', 'γ', 'χ', 'ψ', 'ω', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'γ', 'τ', 'υ', 'φ', 'η', 'ψ', 'ω', 'β', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'η', 'υ', 'θ', 'γ', 'ξ', 'ω', 'β', 'ν', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'ξ', 'θ', 'ι', 'η', 'κ', 'β', 'ν', 'μ', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'κ', 'ι', 'ο', 'ξ', 'λ', 'ν', 'μ', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'λ', 'ο', 'π', 'κ', 'μ', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,

            'ζ', 'α', 'σ', 'δ', 'χ', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'χ', 'σ', 'δ', 'φ', 'ζ', 'ψ', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'ψ', 'δ', 'φ', 'γ', 'χ', 'ω', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'ω', 'φ', 'γ', 'η', 'ψ', 'β', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'β', 'γ', 'η', 'ξ', 'ω', 'ν', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'ν', 'η', 'ξ', 'κ', 'β', 'μ', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            'μ', 'ξ', 'κ', 'λ', 'ν', NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,

        Using the following characters:
        */
        private static final int GR_FINAL_SIGMA = '\u03C2'; // ς
        private static final int GR_EPSILON = '\u03B5'; // ε
        private static final int GR_RHO = '\u03C1'; // ρ
        private static final int GR_TAU = '\u03C4'; // τ
        private static final int GR_UPSILON = '\u03C5'; // υ
        private static final int GR_THETA = '\u03B8'; // θ
        private static final int GR_IOTA = '\u03B9'; // ι
        private static final int GR_OMICRON = '\u03BF'; // ο
        private static final int GR_PI = '\u03C0'; // π
        private static final int GR_ALPHA = '\u03B1'; // α
        private static final int GR_SIGMA = '\u03C3'; // σ
        private static final int GR_DELTA = '\u03B4'; // δ
        private static final int GR_PHI = '\u03C6'; // φ
        private static final int GR_GAMMA = '\u03B3'; // γ
        private static final int GR_ETA = '\u03B7'; // η
        private static final int GR_XI = '\u03BE'; // ξ
        private static final int GR_KAPPA = '\u03BA'; // κ
        private static final int GR_LAMDA = '\u03BB'; // λ
        private static final int GR_ZETA = '\u03B6'; // ζ
        private static final int GR_CHI = '\u03C7'; // χ
        private static final int GR_PSI = '\u03C8'; // ψ
        private static final int GR_OMEGA = '\u03C9'; // ω
        private static final int GR_BETA = '\u03B2'; // β
        private static final int GR_NU = '\u03BD'; // ν
        private static final int GR_MU = '\u03BC'; // μ
        static final int[] PROXIMITY = {
            // Proximity for row 1. This must have exactly ROW_SIZE entries for each letter,
            // and exactly PROXIMITY_GRID_WIDTH letters for a row. Pad with NUL's.
            // The number of rows must be exactly PROXIMITY_GRID_HEIGHT.
            GR_FINAL_SIGMA, GR_EPSILON, GR_ALPHA, GR_SIGMA, NUL, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            GR_EPSILON, GR_FINAL_SIGMA, GR_RHO, GR_SIGMA, GR_DELTA, NUL, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            GR_RHO, GR_EPSILON, GR_TAU, GR_DELTA, GR_PHI, NUL, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            GR_TAU, GR_RHO, GR_UPSILON, GR_PHI, GR_GAMMA, NUL, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            GR_UPSILON, GR_TAU, GR_THETA, GR_GAMMA, GR_ETA, NUL, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            GR_THETA, GR_UPSILON, GR_IOTA, GR_ETA, GR_XI, NUL, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            GR_IOTA, GR_THETA, GR_OMICRON, GR_XI, GR_KAPPA, NUL, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            GR_OMICRON, GR_IOTA, GR_PI, GR_KAPPA, GR_LAMDA, NUL, NUL, NUL, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            GR_PI, GR_OMICRON, GR_LAMDA, NUL, NUL, NUL, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,

            GR_ALPHA, GR_FINAL_SIGMA, GR_SIGMA, GR_ZETA, NUL, NUL, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            GR_SIGMA, GR_FINAL_SIGMA, GR_EPSILON, GR_ALPHA, GR_DELTA, GR_ZETA, GR_CHI, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            GR_DELTA, GR_EPSILON, GR_RHO, GR_SIGMA, GR_PHI, GR_ZETA, GR_CHI, GR_PSI,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            GR_PHI, GR_RHO, GR_TAU, GR_DELTA, GR_GAMMA, GR_CHI, GR_PSI, GR_OMEGA,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            GR_GAMMA, GR_TAU, GR_UPSILON, GR_PHI, GR_ETA, GR_PSI, GR_OMEGA, GR_BETA,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            GR_ETA, GR_UPSILON, GR_THETA, GR_GAMMA, GR_XI, GR_OMEGA, GR_BETA, GR_NU,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            GR_XI, GR_THETA, GR_IOTA, GR_ETA, GR_KAPPA, GR_BETA, GR_NU, GR_MU,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            GR_KAPPA, GR_IOTA, GR_OMICRON, GR_XI, GR_LAMDA, GR_NU, GR_MU, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            GR_LAMDA, GR_OMICRON, GR_PI, GR_KAPPA, GR_MU, NUL, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,

            GR_ZETA, GR_ALPHA, GR_SIGMA, GR_DELTA, GR_CHI, NUL, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            GR_CHI, GR_SIGMA, GR_DELTA, GR_PHI, GR_ZETA, GR_PSI, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            GR_PSI, GR_DELTA, GR_PHI, GR_GAMMA, GR_CHI, GR_OMEGA, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            GR_OMEGA, GR_PHI, GR_GAMMA, GR_ETA, GR_PSI, GR_BETA, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            GR_BETA, GR_GAMMA, GR_ETA, GR_XI, GR_OMEGA, GR_NU, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            GR_NU, GR_ETA, GR_XI, GR_KAPPA, GR_BETA, GR_MU, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            GR_MU, GR_XI, GR_KAPPA, GR_LAMDA, GR_NU, NUL, NUL, NUL,
                    NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
            NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL, NUL,
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

    private static int[] getProximityForScript(final int script) {
        switch (script) {
        case AndroidSpellCheckerService.SCRIPT_LATIN:
            return Latin.PROXIMITY;
        case AndroidSpellCheckerService.SCRIPT_CYRILLIC:
            return Cyrillic.PROXIMITY;
        case AndroidSpellCheckerService.SCRIPT_GREEK:
            return Greek.PROXIMITY;
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
        case AndroidSpellCheckerService.SCRIPT_GREEK:
            return Greek.getIndexOf(codePoint);
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
