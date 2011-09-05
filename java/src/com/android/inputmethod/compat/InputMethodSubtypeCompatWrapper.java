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

package com.android.inputmethod.compat;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.text.TextUtils;
import android.util.Log;

import com.android.inputmethod.latin.LatinImeLogger;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;

// TODO: Override this class with the concrete implementation if we need to take care of the
// performance.
public final class InputMethodSubtypeCompatWrapper extends AbstractCompatWrapper {
    private static final boolean DBG = LatinImeLogger.sDBG;
    private static final String TAG = InputMethodSubtypeCompatWrapper.class.getSimpleName();
    private static final String DEFAULT_LOCALE = "en_US";
    private static final String DEFAULT_MODE = "keyboard";

    public static final Class<?> CLASS_InputMethodSubtype =
            CompatUtils.getClass("android.view.inputmethod.InputMethodSubtype");
    private static final Method METHOD_getNameResId =
            CompatUtils.getMethod(CLASS_InputMethodSubtype, "getNameResId");
    private static final Method METHOD_getIconResId =
            CompatUtils.getMethod(CLASS_InputMethodSubtype, "getIconResId");
    private static final Method METHOD_getLocale =
            CompatUtils.getMethod(CLASS_InputMethodSubtype, "getLocale");
    private static final Method METHOD_getMode =
            CompatUtils.getMethod(CLASS_InputMethodSubtype, "getMode");
    private static final Method METHOD_getExtraValue =
            CompatUtils.getMethod(CLASS_InputMethodSubtype, "getExtraValue");
    private static final Method METHOD_containsExtraValueKey =
            CompatUtils.getMethod(CLASS_InputMethodSubtype, "containsExtraValueKey", String.class);
    private static final Method METHOD_getExtraValueOf =
            CompatUtils.getMethod(CLASS_InputMethodSubtype, "getExtraValueOf", String.class);
    private static final Method METHOD_isAuxiliary =
            CompatUtils.getMethod(CLASS_InputMethodSubtype, "isAuxiliary");
    private static final Method METHOD_getDisplayName =
            CompatUtils.getMethod(CLASS_InputMethodSubtype, "getDisplayName", Context.class,
                    String.class, ApplicationInfo.class);

    private final int mDummyNameResId;
    private final int mDummyIconResId;
    private final String mDummyLocale;
    private final String mDummyMode;
    private final String mDummyExtraValues;

    public InputMethodSubtypeCompatWrapper(Object subtype) {
        super((CLASS_InputMethodSubtype != null && CLASS_InputMethodSubtype.isInstance(subtype))
                ? subtype : null);
        mDummyNameResId = 0;
        mDummyIconResId = 0;
        mDummyLocale = DEFAULT_LOCALE;
        mDummyMode = DEFAULT_MODE;
        mDummyExtraValues = "";
    }

    // Constructor for creating a dummy subtype.
    public InputMethodSubtypeCompatWrapper(int nameResId, int iconResId, String locale,
            String mode, String extraValues) {
        super(null);
        if (DBG) {
            Log.d(TAG, "CreateInputMethodSubtypeCompatWrapper");
        }
        mDummyNameResId = nameResId;
        mDummyIconResId = iconResId;
        mDummyLocale = locale != null ? locale : "";
        mDummyMode = mode != null ? mode : "";
        mDummyExtraValues = extraValues != null ? extraValues : "";
    }

    public int getNameResId() {
        if (mObj == null) return mDummyNameResId;
        return (Integer)CompatUtils.invoke(mObj, 0, METHOD_getNameResId);
    }

    public int getIconResId() {
        if (mObj == null) return mDummyIconResId;
        return (Integer)CompatUtils.invoke(mObj, 0, METHOD_getIconResId);
    }

    public String getLocale() {
        if (mObj == null) return mDummyLocale;
        final String s = (String)CompatUtils.invoke(mObj, null, METHOD_getLocale);
        return s != null ? s : DEFAULT_LOCALE;
    }

    public String getMode() {
        if (mObj == null) return mDummyMode;
        String s = (String)CompatUtils.invoke(mObj, null, METHOD_getMode);
        if (TextUtils.isEmpty(s)) return DEFAULT_MODE;
        return s;
    }

    public String getExtraValue() {
        if (mObj == null) return mDummyExtraValues;
        return (String)CompatUtils.invoke(mObj, null, METHOD_getExtraValue);
    }

    public boolean containsExtraValueKey(String key) {
        return (Boolean)CompatUtils.invoke(mObj, false, METHOD_containsExtraValueKey, key);
    }

    public String getExtraValueOf(String key) {
        return (String)CompatUtils.invoke(mObj, null, METHOD_getExtraValueOf, key);
    }

    public boolean isAuxiliary() {
        return (Boolean)CompatUtils.invoke(mObj, false, METHOD_isAuxiliary);
    }

    public CharSequence getDisplayName(Context context, String packageName,
            ApplicationInfo appInfo) {
        if (mObj != null) {
            return (CharSequence)CompatUtils.invoke(
                    mObj, "", METHOD_getDisplayName, context, packageName, appInfo);
        }

        // The code below are based on {@link InputMethodSubtype#getDisplayName}.

        final Locale locale = new Locale(getLocale());
        final String localeStr = locale.getDisplayName();
        if (getNameResId() == 0) {
            return localeStr;
        }
        final CharSequence subtypeName = context.getText(getNameResId());
        if (!TextUtils.isEmpty(localeStr)) {
            return String.format(subtypeName.toString(), localeStr);
        } else {
            return localeStr;
        }
    }

    public boolean isDummy() {
        return !hasOriginalObject();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof InputMethodSubtypeCompatWrapper) {
            InputMethodSubtypeCompatWrapper subtype = (InputMethodSubtypeCompatWrapper)o;
            if (mObj == null) {
                // easy check of dummy subtypes
                return (mDummyNameResId == subtype.mDummyNameResId
                        && mDummyIconResId == subtype.mDummyIconResId
                        && mDummyLocale.equals(subtype.mDummyLocale)
                        && mDummyMode.equals(subtype.mDummyMode)
                        && mDummyExtraValues.equals(subtype.mDummyExtraValues));
            }
            return mObj.equals(subtype.getOriginalObject());
        } else {
            return mObj.equals(o);
        }
    }

    @Override
    public int hashCode() {
        if (mObj == null) {
            return hashCodeInternal(mDummyNameResId, mDummyIconResId, mDummyLocale,
                    mDummyMode, mDummyExtraValues);
        }
        return mObj.hashCode();
    }

    private static int hashCodeInternal(int nameResId, int iconResId, String locale,
            String mode, String extraValue) {
        return Arrays
                .hashCode(new Object[] { nameResId, iconResId, locale, mode, extraValue });
    }
}
