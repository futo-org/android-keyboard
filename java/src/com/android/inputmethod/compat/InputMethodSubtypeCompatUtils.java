/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.inputmethod.compat;

import android.os.Build;
import android.view.inputmethod.InputMethodSubtype;

import java.lang.reflect.Constructor;

public final class InputMethodSubtypeCompatUtils {
    private static final String TAG = InputMethodSubtypeCompatUtils.class.getSimpleName();
    // Note that InputMethodSubtype(int nameId, int iconId, String locale, String mode,
    // String extraValue, boolean isAuxiliary, boolean overridesImplicitlyEnabledSubtype, int id)
    // has been introduced in API level 17 (Build.VERSION_CODE.JELLY_BEAN_MR1).
    private static final Constructor<?> CONSTRUCTOR_INPUT_METHOD_SUBTYPE =
            CompatUtils.getConstructor(InputMethodSubtype.class,
                    Integer.TYPE, Integer.TYPE, String.class, String.class, String.class,
                    Boolean.TYPE, Boolean.TYPE, Integer.TYPE);
    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            if (CONSTRUCTOR_INPUT_METHOD_SUBTYPE == null) {
                android.util.Log.w(TAG, "Warning!!! Constructor is not defined.");
            }
        }
    }
    private InputMethodSubtypeCompatUtils() {
        // This utility class is not publicly instantiable.
    }

    public static InputMethodSubtype newInputMethodSubtype(int nameId, int iconId, String locale,
            String mode, String extraValue, boolean isAuxiliary,
            boolean overridesImplicitlyEnabledSubtype, int id) {
        if (CONSTRUCTOR_INPUT_METHOD_SUBTYPE == null
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return new InputMethodSubtype(nameId, iconId, locale, mode, extraValue, isAuxiliary,
                    overridesImplicitlyEnabledSubtype);
        }
        return (InputMethodSubtype) CompatUtils.newInstance(CONSTRUCTOR_INPUT_METHOD_SUBTYPE,
                nameId, iconId, locale, mode, extraValue, isAuxiliary,
                overridesImplicitlyEnabledSubtype, id);
    }
}
