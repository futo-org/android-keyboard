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

import com.android.inputmethod.latin.LatinImeLogger;

import android.util.Log;

import java.lang.reflect.Method;

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

    public InputMethodSubtypeCompatWrapper(Object subtype) {
        super((CLASS_InputMethodSubtype != null && CLASS_InputMethodSubtype.isInstance(subtype))
                ? subtype : null);
        if (DBG) {
            Log.d(TAG, "CreateInputMethodSubtypeCompatWrapper");
        }
    }

    public int getNameResId() {
        return (Integer)CompatUtils.invoke(mObj, 0, METHOD_getNameResId);
    }

    public int getIconResId() {
        return (Integer)CompatUtils.invoke(mObj, 0, METHOD_getIconResId);
    }

    public String getLocale() {
        final String s = (String)CompatUtils.invoke(mObj, null, METHOD_getLocale);
        if (s == null) return DEFAULT_LOCALE;
        return s;
    }

    public String getMode() {
        String s = (String)CompatUtils.invoke(mObj, null, METHOD_getMode);
        if (s == null) return DEFAULT_MODE;
        return s;
    }

    public String getExtraValue() {
        return (String)CompatUtils.invoke(mObj, null, METHOD_getExtraValue);
    }

    public boolean containsExtraValueKey(String key) {
        return (Boolean)CompatUtils.invoke(mObj, false, METHOD_containsExtraValueKey, key);
    }

    public String getExtraValueOf(String key) {
        return (String)CompatUtils.invoke(mObj, null, METHOD_getExtraValueOf, key);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof InputMethodSubtypeCompatWrapper) {
            InputMethodSubtypeCompatWrapper subtype = (InputMethodSubtypeCompatWrapper)o;
            return mObj.equals(subtype.getOriginalObject());
        } else {
            return mObj.equals(o);
        }
    }

}
