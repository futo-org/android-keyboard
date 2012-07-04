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

import android.content.Context;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import java.util.Collections;
import java.util.List;

/**
 * Utility class for Input Method Framework
 */
public class ImfUtils {
    private ImfUtils() {
        // This utility class is not publicly instantiable.
    }

    private static InputMethodManager sInputMethodManager;

    public static InputMethodManager getInputMethodManager(Context context) {
        if (sInputMethodManager == null) {
            sInputMethodManager = (InputMethodManager)context.getSystemService(
                    Context.INPUT_METHOD_SERVICE);
        }
        return sInputMethodManager;
    }

    private static InputMethodInfo sInputMethodInfoOfThisIme;

    public static InputMethodInfo getInputMethodInfoOfThisIme(Context context) {
        if (sInputMethodInfoOfThisIme == null) {
            final InputMethodManager imm = getInputMethodManager(context);
            final String packageName = context.getPackageName();
            for (final InputMethodInfo imi : imm.getInputMethodList()) {
                if (imi.getPackageName().equals(packageName))
                    return imi;
            }
            throw new RuntimeException("Can not find input method id for " + packageName);
        }
        return sInputMethodInfoOfThisIme;
    }

    public static String getInputMethodIdOfThisIme(Context context) {
        return getInputMethodInfoOfThisIme(context).getId();
    }

    public static boolean checkIfSubtypeBelongsToThisImeAndEnabled(Context context,
            InputMethodSubtype ims) {
        final InputMethodInfo myImi = getInputMethodInfoOfThisIme(context);
        final InputMethodManager imm = getInputMethodManager(context);
        // TODO: Cache all subtypes of this IME for optimization
        final List<InputMethodSubtype> subtypes = imm.getEnabledInputMethodSubtypeList(myImi, true);
        for (final InputMethodSubtype subtype : subtypes) {
            if (subtype.equals(ims)) {
                return true;
            }
        }
        return false;
    }

    public static boolean checkIfSubtypeBelongsToThisIme(Context context,
            InputMethodSubtype ims) {
        final InputMethodInfo myImi = getInputMethodInfoOfThisIme(context);
        final int count = myImi.getSubtypeCount();
        for (int i = 0; i < count; i++) {
            final InputMethodSubtype subtype = myImi.getSubtypeAt(i);
            if (subtype.equals(ims)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasMultipleEnabledIMEsOrSubtypes(Context context,
            final boolean shouldIncludeAuxiliarySubtypes) {
        final InputMethodManager imm = getInputMethodManager(context);
        final List<InputMethodInfo> enabledImis = imm.getEnabledInputMethodList();
        return hasMultipleEnabledSubtypes(context, shouldIncludeAuxiliarySubtypes, enabledImis);
    }

    public static boolean hasMultipleEnabledSubtypesInThisIme(Context context,
            final boolean shouldIncludeAuxiliarySubtypes) {
        final InputMethodInfo myImi = getInputMethodInfoOfThisIme(context);
        final List<InputMethodInfo> imiList = Collections.singletonList(myImi);
        return hasMultipleEnabledSubtypes(context, shouldIncludeAuxiliarySubtypes, imiList);
    }

    private static boolean hasMultipleEnabledSubtypes(Context context,
            final boolean shouldIncludeAuxiliarySubtypes, List<InputMethodInfo> imiList) {
        final InputMethodManager imm = getInputMethodManager(context);

        // Number of the filtered IMEs
        int filteredImisCount = 0;

        for (InputMethodInfo imi : imiList) {
            // We can return true immediately after we find two or more filtered IMEs.
            if (filteredImisCount > 1) return true;
            final List<InputMethodSubtype> subtypes =
                    imm.getEnabledInputMethodSubtypeList(imi, true);
            // IMEs that have no subtypes should be counted.
            if (subtypes.isEmpty()) {
                ++filteredImisCount;
                continue;
            }

            int auxCount = 0;
            for (InputMethodSubtype subtype : subtypes) {
                if (subtype.isAuxiliary()) {
                    ++auxCount;
                }
            }
            final int nonAuxCount = subtypes.size() - auxCount;

            // IMEs that have one or more non-auxiliary subtypes should be counted.
            // If shouldIncludeAuxiliarySubtypes is true, IMEs that have two or more auxiliary
            // subtypes should be counted as well.
            if (nonAuxCount > 0 || (shouldIncludeAuxiliarySubtypes && auxCount > 1)) {
                ++filteredImisCount;
                continue;
            }
        }

        if (filteredImisCount > 1) {
            return true;
        }
        final List<InputMethodSubtype> subtypes = imm.getEnabledInputMethodSubtypeList(null, true);
        int keyboardCount = 0;
        // imm.getEnabledInputMethodSubtypeList(null, true) will return the current IME's
        // both explicitly and implicitly enabled input method subtype.
        // (The current IME should be LatinIME.)
        for (InputMethodSubtype subtype : subtypes) {
            if (KEYBOARD_MODE.equals(subtype.getMode())) {
                ++keyboardCount;
            }
        }
        return keyboardCount > 1;
    }

    public static InputMethodSubtype findSubtypeByLocaleAndKeyboardLayoutSet(
            Context context, String localeString, String keyboardLayoutSetName) {
        final InputMethodInfo imi = getInputMethodInfoOfThisIme(context);
        final int count = imi.getSubtypeCount();
        for (int i = 0; i < count; i++) {
            final InputMethodSubtype subtype = imi.getSubtypeAt(i);
            final String layoutName = SubtypeLocale.getKeyboardLayoutSetName(subtype);
            if (localeString.equals(subtype.getLocale())
                    && keyboardLayoutSetName.equals(layoutName)) {
                return subtype;
            }
        }
        return null;
    }

    public static void setAdditionalInputMethodSubtypes(Context context,
            InputMethodSubtype[] subtypes) {
        final InputMethodManager imm = getInputMethodManager(context);
        final String imiId = getInputMethodIdOfThisIme(context);
        imm.setAdditionalInputMethodSubtypes(imiId, subtypes);
    }
}
