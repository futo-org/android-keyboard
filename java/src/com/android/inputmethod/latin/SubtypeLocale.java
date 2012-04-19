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

import static com.android.inputmethod.latin.Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET;

import android.content.Context;
import android.content.res.Resources;
import android.view.inputmethod.InputMethodSubtype;

import java.util.HashMap;
import java.util.Locale;

public class SubtypeLocale {
    private static final String TAG = SubtypeLocale.class.getSimpleName();

    // Special language code to represent "no language".
    public static final String NO_LANGUAGE = "zz";

    // Exceptional locales to display name map.
    private static final HashMap<String, String> sExceptionalDisplayNamesMap =
            new HashMap<String, String>();

    private SubtypeLocale() {
        // Intentional empty constructor for utility class.
    }

    public static void init(Context context) {
        final Resources res = context.getResources();
        final String[] locales = res.getStringArray(R.array.subtype_locale_exception_keys);
        final String[] displayNames = res.getStringArray(R.array.subtype_locale_exception_values);
        for (int i = 0; i < locales.length; i++) {
            sExceptionalDisplayNamesMap.put(locales[i], displayNames[i]);
        }
    }

    // Get InputMethodSubtype's display name in its locale.
    //        isAdditionalSubtype (T=true, F=false)
    // locale layout | Short  Middle      Full
    // ------ ------ - ---- --------- -----------------
    //  en_US qwerty F  En  English   English (US)      exception
    //  en_GB qwerty F  En  English   English (UK)      exception
    //  fr    azerty F  Fr  Français  Français
    //  fr_CA qwerty F  Fr  Français  Français (Canada)
    //  de    qwertz F  De  Deutsch   Deutsch
    //  zz    qwerty F      QWERTY    QWERTY
    //  fr    qwertz T  Fr  Français  Français (QWERTZ)
    //  de    qwerty T  De  Deutsch   Deutsch (QWERTY)
    //  en_US azerty T  En  English   English (US) (AZERTY)
    //  zz    azerty T      AZERTY    AZERTY

    // Get InputMethodSubtype's full display name in its locale.
    public static String getFullDisplayName(InputMethodSubtype subtype) {
        if (isNoLanguage(subtype)) {
            return getKeyboardLayoutSetDisplayName(subtype);
        }

        final String exceptionalValue = sExceptionalDisplayNamesMap.get(subtype.getLocale());

        final Locale locale = getSubtypeLocale(subtype);
        if (AdditionalSubtype.isAdditionalSubtype(subtype)) {
            final String language = (exceptionalValue != null) ? exceptionalValue
                    : StringUtils.toTitleCase(locale.getDisplayLanguage(locale), locale);
            final String layout = getKeyboardLayoutSetDisplayName(subtype);
            return String.format("%s (%s)", language, layout);
        }

        if (exceptionalValue != null) {
            return exceptionalValue;
        }

        return StringUtils.toTitleCase(locale.getDisplayName(locale), locale);
    }

    // Get InputMethodSubtype's middle display name in its locale.
    public static String getMiddleDisplayName(InputMethodSubtype subtype) {
        if (isNoLanguage(subtype)) {
            return getKeyboardLayoutSetDisplayName(subtype);
        }
        final Locale locale = getSubtypeLocale(subtype);
        return StringUtils.toTitleCase(locale.getDisplayLanguage(locale), locale);
    }

    // Get InputMethodSubtype's short display name in its locale.
    public static String getShortDisplayName(InputMethodSubtype subtype) {
        if (isNoLanguage(subtype)) {
            return "";
        }
        final Locale locale = getSubtypeLocale(subtype);
        return StringUtils.toTitleCase(locale.getLanguage(), locale);
    }

    public static boolean isNoLanguage(InputMethodSubtype subtype) {
        final String localeString = subtype.getLocale();
        return localeString.equals(NO_LANGUAGE);
    }

    public static Locale getSubtypeLocale(InputMethodSubtype subtype) {
        final String localeString = subtype.getLocale();
        return LocaleUtils.constructLocaleFromString(localeString);
    }

    public static String getKeyboardLayoutSetDisplayName(InputMethodSubtype subtype) {
        final String layoutName = getKeyboardLayoutSetName(subtype);
        // TODO: This hack should be removed.
        if (layoutName.equals(AdditionalSubtype.DVORAK)) {
            return StringUtils.toTitleCase(layoutName, Locale.US);
        }
        return layoutName.toUpperCase();
    }

    public static String getKeyboardLayoutSetName(InputMethodSubtype subtype) {
        final String keyboardLayoutSet = subtype.getExtraValueOf(KEYBOARD_LAYOUT_SET);
        // TODO: Remove this null check when InputMethodManager.getCurrentInputMethodSubtype is
        // fixed.
        if (keyboardLayoutSet == null) {
            android.util.Log.w(TAG, "KeyboardLayoutSet not found, use QWERTY: " +
                    "locale=" + subtype.getLocale() + " extraValue=" + subtype.getExtraValue());
            return AdditionalSubtype.QWERTY;
        }
        return keyboardLayoutSet;
    }
}
