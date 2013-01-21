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

import static com.android.inputmethod.latin.Constants.Subtype.KEYBOARD_MODE;
import static com.android.inputmethod.latin.Constants.Subtype.ExtraValue.IS_ADDITIONAL_SUBTYPE;
import static com.android.inputmethod.latin.Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET;
import static com.android.inputmethod.latin.Constants.Subtype.ExtraValue.UNTRANSLATABLE_STRING_IN_SUBTYPE_NAME;

import android.os.Build;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodSubtype;

import java.util.ArrayList;

public final class AdditionalSubtype {
    private static final InputMethodSubtype[] EMPTY_SUBTYPE_ARRAY = new InputMethodSubtype[0];

    private AdditionalSubtype() {
        // This utility class is not publicly instantiable.
    }

    public static boolean isAdditionalSubtype(final InputMethodSubtype subtype) {
        return subtype.containsExtraValueKey(IS_ADDITIONAL_SUBTYPE);
    }

    private static final String LOCALE_AND_LAYOUT_SEPARATOR = ":";
    private static final String PREF_SUBTYPE_SEPARATOR = ";";

    public static InputMethodSubtype createAdditionalSubtype(final String localeString,
            final String keyboardLayoutSetName, final String extraValue) {
        final String layoutExtraValue = KEYBOARD_LAYOUT_SET + "=" + keyboardLayoutSetName;
        final String layoutDisplayNameExtraValue;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN
                && SubtypeLocale.isExceptionalLocale(localeString)) {
            final String layoutDisplayName = SubtypeLocale.getKeyboardLayoutSetDisplayName(
                    keyboardLayoutSetName);
            layoutDisplayNameExtraValue = StringUtils.appendToCsvIfNotExists(
                    UNTRANSLATABLE_STRING_IN_SUBTYPE_NAME + "=" + layoutDisplayName, extraValue);
        } else {
            layoutDisplayNameExtraValue = extraValue;
        }
        final String additionalSubtypeExtraValue = StringUtils.appendToCsvIfNotExists(
                IS_ADDITIONAL_SUBTYPE, layoutDisplayNameExtraValue);
        final int nameId = SubtypeLocale.getSubtypeNameId(localeString, keyboardLayoutSetName);
        return new InputMethodSubtype(nameId, R.drawable.ic_subtype_keyboard,
                localeString, KEYBOARD_MODE,
                layoutExtraValue + "," + additionalSubtypeExtraValue, false, false);
    }

    public static String getPrefSubtype(final InputMethodSubtype subtype) {
        final String localeString = subtype.getLocale();
        final String keyboardLayoutSetName = SubtypeLocale.getKeyboardLayoutSetName(subtype);
        final String layoutExtraValue = KEYBOARD_LAYOUT_SET + "=" + keyboardLayoutSetName;
        final String extraValue = StringUtils.removeFromCsvIfExists(layoutExtraValue,
                StringUtils.removeFromCsvIfExists(IS_ADDITIONAL_SUBTYPE, subtype.getExtraValue()));
        final String basePrefSubtype = localeString + LOCALE_AND_LAYOUT_SEPARATOR
                + keyboardLayoutSetName;
        return extraValue.isEmpty() ? basePrefSubtype
                : basePrefSubtype + LOCALE_AND_LAYOUT_SEPARATOR + extraValue;
    }

    public static InputMethodSubtype createAdditionalSubtype(final String prefSubtype) {
        final String elems[] = prefSubtype.split(LOCALE_AND_LAYOUT_SEPARATOR);
        if (elems.length < 2 || elems.length > 3) {
            throw new RuntimeException("Unknown additional subtype specified: " + prefSubtype);
        }
        final String localeString = elems[0];
        final String keyboardLayoutSetName = elems[1];
        final String extraValue = (elems.length == 3) ? elems[2] : null;
        return createAdditionalSubtype(localeString, keyboardLayoutSetName, extraValue);
    }

    public static InputMethodSubtype[] createAdditionalSubtypesArray(final String prefSubtypes) {
        if (TextUtils.isEmpty(prefSubtypes)) {
            return EMPTY_SUBTYPE_ARRAY;
        }
        final String[] prefSubtypeArray = prefSubtypes.split(PREF_SUBTYPE_SEPARATOR);
        final ArrayList<InputMethodSubtype> subtypesList =
                CollectionUtils.newArrayList(prefSubtypeArray.length);
        for (final String prefSubtype : prefSubtypeArray) {
            final InputMethodSubtype subtype = createAdditionalSubtype(prefSubtype);
            if (subtype.getNameResId() == SubtypeLocale.UNKNOWN_KEYBOARD_LAYOUT) {
                // Skip unknown keyboard layout subtype. This may happen when predefined keyboard
                // layout has been removed.
                continue;
            }
            subtypesList.add(subtype);
        }
        return subtypesList.toArray(new InputMethodSubtype[subtypesList.size()]);
    }

    public static String createPrefSubtypes(final InputMethodSubtype[] subtypes) {
        if (subtypes == null || subtypes.length == 0) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        for (final InputMethodSubtype subtype : subtypes) {
            if (sb.length() > 0) {
                sb.append(PREF_SUBTYPE_SEPARATOR);
            }
            sb.append(getPrefSubtype(subtype));
        }
        return sb.toString();
    }

    public static String createPrefSubtypes(final String[] prefSubtypes) {
        if (prefSubtypes == null || prefSubtypes.length == 0) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        for (final String prefSubtype : prefSubtypes) {
            if (sb.length() > 0) {
                sb.append(PREF_SUBTYPE_SEPARATOR);
            }
            sb.append(prefSubtype);
        }
        return sb.toString();
    }
}
