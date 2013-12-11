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

package com.android.inputmethod.keyboard.tools;

import java.util.HashMap;
import java.util.Locale;

/**
 * A class to help with handling Locales in string form.
 *
 * This is a subset of com/android/inputmethod/latin/utils/LocaleUtils.java in order to use
 * for the make-keyboard-text tool.
 */
public final class LocaleUtils {
    private LocaleUtils() {
        // Intentional empty constructor for utility class.
    }

    private static final HashMap<String, Locale> sLocaleCache = new HashMap<String, Locale>();

    /**
     * Creates a locale from a string specification.
     */
    public static Locale constructLocaleFromString(final String localeStr) {
        if (localeStr == null) {
            return null;
        }
        synchronized (sLocaleCache) {
            Locale retval = sLocaleCache.get(localeStr);
            if (retval != null) {
                return retval;
            }
            String[] localeParams = localeStr.split("_", 3);
            if (localeParams.length == 1) {
                retval = new Locale(localeParams[0]);
            } else if (localeParams.length == 2) {
                retval = new Locale(localeParams[0], localeParams[1]);
            } else if (localeParams.length == 3) {
                retval = new Locale(localeParams[0], localeParams[1], localeParams[2]);
            }
            if (retval != null) {
                sLocaleCache.put(localeStr, retval);
            }
            return retval;
        }
    }
}
