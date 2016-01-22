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
import android.text.TextUtils;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.RichInputMethodSubtype;
import com.android.inputmethod.latin.common.Constants;
import com.android.inputmethod.latin.common.LocaleUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;

import javax.annotation.Nonnull;

public final class InputMethodSubtypeCompatUtils {
    private static final String TAG = InputMethodSubtypeCompatUtils.class.getSimpleName();
    // Note that InputMethodSubtype(int nameId, int iconId, String locale, String mode,
    // String extraValue, boolean isAuxiliary, boolean overridesImplicitlyEnabledSubtype, int id)
    // has been introduced in API level 17 (Build.VERSION_CODE.JELLY_BEAN_MR1).
    private static final Constructor<?> CONSTRUCTOR_INPUT_METHOD_SUBTYPE =
            CompatUtils.getConstructor(InputMethodSubtype.class,
                    int.class, int.class, String.class, String.class, String.class, boolean.class,
                    boolean.class, int.class);
    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            if (CONSTRUCTOR_INPUT_METHOD_SUBTYPE == null) {
                android.util.Log.w(TAG, "Warning!!! Constructor is not defined.");
            }
        }
    }

    // Note that {@link InputMethodSubtype#isAsciiCapable()} has been introduced in API level 19
    // (Build.VERSION_CODE.KITKAT).
    private static final Method METHOD_isAsciiCapable = CompatUtils.getMethod(
            InputMethodSubtype.class, "isAsciiCapable");

    private InputMethodSubtypeCompatUtils() {
        // This utility class is not publicly instantiable.
    }

    @SuppressWarnings("deprecation")
    @Nonnull
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

    public static boolean isAsciiCapable(final RichInputMethodSubtype subtype) {
        return isAsciiCapable(subtype.getRawSubtype());
    }

    public static boolean isAsciiCapable(final InputMethodSubtype subtype) {
        return isAsciiCapableWithAPI(subtype)
                || subtype.containsExtraValueKey(Constants.Subtype.ExtraValue.ASCII_CAPABLE);
    }

    // Note that InputMethodSubtype.getLanguageTag() is expected to be available in Android N+.
    private static final Method GET_LANGUAGE_TAG =
            CompatUtils.getMethod(InputMethodSubtype.class, "getLanguageTag");

    public static Locale getLocaleObject(final InputMethodSubtype subtype) {
        // Locale.forLanguageTag() is available only in Android L and later.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final String languageTag = (String) CompatUtils.invoke(subtype, null, GET_LANGUAGE_TAG);
            if (!TextUtils.isEmpty(languageTag)) {
                return Locale.forLanguageTag(languageTag);
            }
        }
        return LocaleUtils.constructLocaleFromString(subtype.getLocale());
    }

    @UsedForTesting
    public static boolean isAsciiCapableWithAPI(final InputMethodSubtype subtype) {
        return (Boolean)CompatUtils.invoke(subtype, false, METHOD_isAsciiCapable);
    }
}
