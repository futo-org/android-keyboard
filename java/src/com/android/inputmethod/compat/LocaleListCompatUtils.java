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
    private static final Class CLASS_LocaleList = CompatUtils.getClass("android.util.LocaleList");
    private static final Method METHOD_getPrimary =
            CompatUtils.getMethod(CLASS_LocaleList, "getPrimary");

    private LocaleListCompatUtils() {
        // This utility class is not publicly instantiable.
    }

    public static Locale getPrimary(final Object localeList) {
        return (Locale) CompatUtils.invoke(localeList, null, METHOD_getPrimary);
    }
}
