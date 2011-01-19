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

package com.android.inputmethod.latin;

import android.content.Context;
import android.content.res.Resources;

import java.util.Locale;

public class SubtypeLocale {
    private static String[] sExceptionKeys;
    private static String[] sExceptionValues;

    private SubtypeLocale() {
        // Intentional empty constructor for utility class.
    }

    public static void init(Context context) {
        final Resources res = context.getResources();
        sExceptionKeys = res.getStringArray(R.array.subtype_locale_exception_keys);
        sExceptionValues = res.getStringArray(R.array.subtype_locale_exception_values);
    }

    public static String getFullDisplayName(Locale locale) {
        String localeCode = locale.toString();
        for (int index = 0; index < sExceptionKeys.length; index++) {
            if (sExceptionKeys[index].equals(localeCode))
                return sExceptionValues[index];
        }
        return locale.getDisplayName(locale);
    }
}
