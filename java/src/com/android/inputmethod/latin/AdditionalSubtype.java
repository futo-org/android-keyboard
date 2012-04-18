/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.latin;

import android.view.inputmethod.InputMethodSubtype;

import java.util.HashMap;

public class AdditionalSubtype {
    public static final String QWERTY = "qwerty";
    public static final String QWERTZ = "qwertz";
    public static final String AZERTY = "azerty";

    private static final String SUBTYPE_MODE_KEYBOARD = "keyboard";
    private static final String SUBTYPE_EXTRA_VALUE_IS_ADDITIONAL_SUBTYPE = "isAdditionalSubtype";

    // Keyboard layout to subtype name resource id map.
    private static final HashMap<String, Integer> sKeyboardLayoutToNameIdsMap =
            new HashMap<String, Integer>();

    static {
        sKeyboardLayoutToNameIdsMap.put(QWERTY, R.string.subtype_generic_qwerty);
        sKeyboardLayoutToNameIdsMap.put(QWERTZ, R.string.subtype_generic_qwertz);
        sKeyboardLayoutToNameIdsMap.put(AZERTY, R.string.subtype_generic_azerty);
    }

    public static boolean isAdditionalSubtype(InputMethodSubtype subtype) {
        return subtype.containsExtraValueKey(SUBTYPE_EXTRA_VALUE_IS_ADDITIONAL_SUBTYPE);
    }

    public static InputMethodSubtype createAdditionalSubtype(
            String localeString, String keyboardLayoutSet) {
        final String extraValue = String.format(
                "%s=%s,%s", LatinIME.SUBTYPE_EXTRA_VALUE_KEYBOARD_LAYOUT_SET, keyboardLayoutSet,
                SUBTYPE_EXTRA_VALUE_IS_ADDITIONAL_SUBTYPE);
        Integer nameId = sKeyboardLayoutToNameIdsMap.get(keyboardLayoutSet);
        if (nameId == null) nameId = R.string.subtype_generic;
        return new InputMethodSubtype(nameId, R.drawable.ic_subtype_keyboard,
                localeString, SUBTYPE_MODE_KEYBOARD, extraValue, false, false);
    }

    private static final String LOCALE_AND_LAYOUT_SEPARATOR = ":";
    private static final String SUBTYPE_SEPARATOR = ",";

    public static InputMethodSubtype[] createAdditionalSubtypesArray(String csvSubtypes) {
        final String[] subtypeSpecs = csvSubtypes.split(SUBTYPE_SEPARATOR);
        final InputMethodSubtype[] subtypesArray = new InputMethodSubtype[subtypeSpecs.length];
        for (int i = 0; i < subtypeSpecs.length; i++) {
            final String elems[] = subtypeSpecs[i].split(LOCALE_AND_LAYOUT_SEPARATOR);
            final String localeString = elems[0];
            final String keyboardLayoutSetName = elems[1];
            subtypesArray[i] = AdditionalSubtype.createAdditionalSubtype(
                    localeString, keyboardLayoutSetName);
        }
        return subtypesArray;
    }
}
