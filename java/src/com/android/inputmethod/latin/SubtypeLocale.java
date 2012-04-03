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
    // Special language code to represent "no language".
    /* package for test */ static final String NO_LANGUAGE = "zz";
    // Special country code to represent "QWERTY".
    /* package for test */ static final String QWERTY = "QY";

    public static final Locale LOCALE_NO_LANGUAGE_QWERTY = new Locale(NO_LANGUAGE, QWERTY);

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

    private static String lookupExceptionalLocale(String key) {
        for (int index = 0; index < sExceptionKeys.length; index++) {
            if (sExceptionKeys[index].equals(key)) {
                return sExceptionValues[index];
            }
        }
        return null;
    }

    // Get Locale's full display name in its locale.
    // For example:
    // "fr_CH" is converted to "Français (Suisse)".
    // "de_QY" is converted to "Deutsche (QWERTY)". (Any locale that has country code "QY")
    // "zz_QY" is converted to "QWERTY". (The language code "zz" means "No language", thus just
    // ends up with the keyboard layout name.)
    public static String getFullDisplayName(Locale locale) {
        final String key;
        if (locale.getLanguage().equals(NO_LANGUAGE)) {
            key = locale.getCountry();
        } else if (locale.getCountry().equals(QWERTY)) {
            key = "*_" + QWERTY;
        } else {
            key = locale.toString();
        }
        final String value = lookupExceptionalLocale(key);
        if (value == null) {
            return toTitleCase(locale.getDisplayName(locale), locale);
        }
        if (value.indexOf("%s") >= 0) {
            final String languageName = toTitleCase(locale.getDisplayLanguage(locale), locale);
            return String.format(value, languageName);
        }
        return value;
    }

    // Get Locale's middle display name in its locale.
    // For example:
    // "fr_CH" is converted to "Français".
    // "de_QY" is converted to "Deutsche". (Any locale that has country code "QY")
    // "zz_QY" is converted to "QWERTY". (The language code "zz" means "No language", thus just
    // ends up with the keyboard layout name.)
    public static String getMiddleDisplayName(Locale locale) {
        if (NO_LANGUAGE.equals(locale.getLanguage())) {
            return lookupExceptionalLocale(locale.getCountry());
        } else {
            return toTitleCase(locale.getDisplayLanguage(locale), locale);
        }
    }

    // Get Locale's short display name in its locale.
    // For example:
    // "fr_CH" is converted to "Fr".
    // "de_QY" is converted to "De". (Any locale that has country code "QY")
    // "zz_QY" is converter to "QY". (The language code "zz" means "No language", thus just ends
    // up with the keyboard layout name.)
    public static String getShortDisplayName(Locale locale) {
        if (NO_LANGUAGE.equals(locale.getLanguage())) {
            return locale.getCountry();
        } else {
            return toTitleCase(locale.getLanguage(), locale);
        }
    }

    public static String toTitleCase(String s, Locale locale) {
        if (s.length() <= 1) {
            // TODO: is this really correct? Shouldn't this be s.toUpperCase()?
            return s;
        }
        // TODO: fix the bugs below
        // - This does not work for Greek, because it returns upper case instead of title case.
        // - It does not work for Serbian, because it fails to account for the "lj" character,
        // which should be "Lj" in title case and "LJ" in upper case.
        // - It does not work for Dutch, because it fails to account for the "ij" digraph, which
        // are two different characters but both should be capitalized as "IJ" as if they were
        // a single letter.
        // - It also does not work with unicode surrogate code points.
        return s.toUpperCase(locale).charAt(0) + s.substring(1);
    }
}
