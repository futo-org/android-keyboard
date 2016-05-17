/*
 * Copyright (C) 2016 The Android Open Source Project
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

import java.lang.reflect.Method;
import java.util.Locale;

public final class LocaleListCompatUtils {
    private static final Class CLASS_LocaleList = CompatUtils.getClass("android.os.LocaleList");
    private static final Method METHOD_get =
            CompatUtils.getMethod(CLASS_LocaleList, "get", int.class);
    private static final Method METHOD_isEmpty =
            CompatUtils.getMethod(CLASS_LocaleList, "isEmpty");

    private LocaleListCompatUtils() {
        // This utility class is not publicly instantiable.
    }

    public static boolean isEmpty(final Object localeList) {
        return (Boolean) CompatUtils.invoke(localeList, Boolean.FALSE, METHOD_isEmpty);
    }

    public static Locale get(final Object localeList, final int index) {
        return (Locale) CompatUtils.invoke(localeList, null, METHOD_get, index);
    }
}
