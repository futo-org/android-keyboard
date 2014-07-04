/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.compat.CompatUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;

@UsedForTesting
public final class LocaleSpanCompatUtils {
    // Note that LocaleSpan(Locale locale) has been introduced in API level 17
    // (Build.VERSION_CODE.JELLY_BEAN_MR1).
    private static Class<?> getLocalSpanClass() {
        try {
            return Class.forName("android.text.style.LocaleSpan");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
    private static final Constructor<?> LOCALE_SPAN_CONSTRUCTOR;
    private static final Method LOCALE_SPAN_GET_LOCALE;
    static {
        final Class<?> localeSpanClass = getLocalSpanClass();
        LOCALE_SPAN_CONSTRUCTOR = CompatUtils.getConstructor(localeSpanClass, Locale.class);
        LOCALE_SPAN_GET_LOCALE = CompatUtils.getMethod(localeSpanClass, "getLocale");
    }

    @UsedForTesting
    public static boolean isLocaleSpanAvailable() {
        return (LOCALE_SPAN_CONSTRUCTOR != null && LOCALE_SPAN_GET_LOCALE != null);
    }

    @UsedForTesting
    public static Object newLocaleSpan(final Locale locale) {
        return CompatUtils.newInstance(LOCALE_SPAN_CONSTRUCTOR, locale);
    }

    @UsedForTesting
    public static Locale getLocaleFromLocaleSpan(final Object localeSpan) {
        return (Locale) CompatUtils.invoke(localeSpan, null, LOCALE_SPAN_GET_LOCALE);
    }
}
