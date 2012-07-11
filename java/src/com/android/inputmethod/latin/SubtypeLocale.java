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
import static com.android.inputmethod.latin.Constants.Subtype.ExtraValue.UNTRANSLATABLE_STRING_IN_SUBTYPE_NAME;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.latin.LocaleUtils.RunInLocale;

import java.util.HashMap;
import java.util.Locale;

public class SubtypeLocale {
    static final String TAG = SubtypeLocale.class.getSimpleName();
    // This class must be located in the same package as LatinIME.java.
    private static final String RESOURCE_PACKAGE_NAME =
            DictionaryFactory.class.getPackage().getName();

    // Special language code to represent "no language".
    public static final String NO_LANGUAGE = "zz";
    public static final String QWERTY = "qwerty";
    public static final int UNKNOWN_KEYBOARD_LAYOUT = R.string.subtype_generic;

    private static boolean sInitialized = false;
    private static String[] sPredefinedKeyboardLayoutSet;
    // Keyboard layout to its display name map.
    private static final HashMap<String, String> sKeyboardLayoutToDisplayNameMap =
            new HashMap<String, String>();
    // Keyboard layout to subtype name resource id map.
    private static final HashMap<String, Integer> sKeyboardLayoutToNameIdsMap =
            new HashMap<String, Integer>();
    // Exceptional locale to subtype name resource id map.
    private static final HashMap<String, Integer> sExceptionalLocaleToWithLayoutNameIdsMap =
            new HashMap<String, Integer>();
    private static final String SUBTYPE_NAME_RESOURCE_GENERIC_PREFIX =
            "string/subtype_generic_";
    private static final String SUBTYPE_NAME_RESOURCE_WITH_LAYOUT_PREFIX =
            "string/subtype_with_layout_";
    private static final String SUBTYPE_NAME_RESOURCE_NO_LANGUAGE_PREFIX =
            "string/subtype_no_language_";
    // Exceptional locales to display name map.
    private static final HashMap<String, String> sExceptionalDisplayNamesMap =
            new HashMap<String, String>();
    // Keyboard layout set name for the subtypes that don't have a keyboardLayoutSet extra value.
    // This is for compatibility to keep the same subtype ids as pre-JellyBean.
    private static final HashMap<String,String> sLocaleAndExtraValueToKeyboardLayoutSetMap =
            new HashMap<String,String>();

    private SubtypeLocale() {
        // Intentional empty constructor for utility class.
    }

    // Note that this initialization method can be called multiple times.
    public static synchronized void init(Context context) {
        if (sInitialized) return;

        final Resources res = context.getResources();

        final String[] predefinedLayoutSet = res.getStringArray(R.array.predefined_layouts);
        sPredefinedKeyboardLayoutSet = predefinedLayoutSet;
        final String[] layoutDisplayNames = res.getStringArray(
                R.array.predefined_layout_display_names);
        for (int i = 0; i < predefinedLayoutSet.length; i++) {
            final String layoutName = predefinedLayoutSet[i];
            sKeyboardLayoutToDisplayNameMap.put(layoutName, layoutDisplayNames[i]);
            final String resourceName = SUBTYPE_NAME_RESOURCE_GENERIC_PREFIX + layoutName;
            final int resId = res.getIdentifier(resourceName, null, RESOURCE_PACKAGE_NAME);
            sKeyboardLayoutToNameIdsMap.put(layoutName, resId);
            // Register subtype name resource id of "No language" with key "zz_<layout>"
            final String noLanguageResName = SUBTYPE_NAME_RESOURCE_NO_LANGUAGE_PREFIX + layoutName;
            final int noLanguageResId = res.getIdentifier(
                    noLanguageResName, null, RESOURCE_PACKAGE_NAME);
            final String key = getNoLanguageLayoutKey(layoutName);
            sKeyboardLayoutToNameIdsMap.put(key, noLanguageResId);
        }

        final String[] exceptionalLocales = res.getStringArray(
                R.array.subtype_locale_exception_keys);
        final String[] exceptionalDisplayNames = res.getStringArray(
                R.array.subtype_locale_exception_values);
        for (int i = 0; i < exceptionalLocales.length; i++) {
            final String localeString = exceptionalLocales[i];
            sExceptionalDisplayNamesMap.put(localeString, exceptionalDisplayNames[i]);
            final String resourceName = SUBTYPE_NAME_RESOURCE_WITH_LAYOUT_PREFIX + localeString;
            final int resId = res.getIdentifier(resourceName, null, RESOURCE_PACKAGE_NAME);
            sExceptionalLocaleToWithLayoutNameIdsMap.put(localeString, resId);
        }

        final String[] keyboardLayoutSetMap = res.getStringArray(
                R.array.locale_and_extra_value_to_keyboard_layout_set_map);
        for (int i = 0; i < keyboardLayoutSetMap.length; i += 2) {
            final String key = keyboardLayoutSetMap[i];
            final String keyboardLayoutSet = keyboardLayoutSetMap[i + 1];
            sLocaleAndExtraValueToKeyboardLayoutSetMap.put(key, keyboardLayoutSet);
        }

        sInitialized = true;
    }

    public static String[] getPredefinedKeyboardLayoutSet() {
        return sPredefinedKeyboardLayoutSet;
    }

    public static boolean isExceptionalLocale(String localeString) {
        return sExceptionalLocaleToWithLayoutNameIdsMap.containsKey(localeString);
    }

    private static final String getNoLanguageLayoutKey(String keyboardLayoutName) {
        return NO_LANGUAGE + "_" + keyboardLayoutName;
    }

    public static int getSubtypeNameId(String localeString, String keyboardLayoutName) {
        if (Build.VERSION.SDK_INT >= /* JELLY_BEAN */ 15 && isExceptionalLocale(localeString)) {
            return sExceptionalLocaleToWithLayoutNameIdsMap.get(localeString);
        }
        final String key = localeString.equals(NO_LANGUAGE)
                ? getNoLanguageLayoutKey(keyboardLayoutName)
                : keyboardLayoutName;
        final Integer nameId = sKeyboardLayoutToNameIdsMap.get(key);
        return nameId == null ? UNKNOWN_KEYBOARD_LAYOUT : nameId;
    }

    public static String getSubtypeLocaleDisplayName(String localeString) {
        final String exceptionalValue = sExceptionalDisplayNamesMap.get(localeString);
        if (exceptionalValue != null) {
            return exceptionalValue;
        }
        final Locale locale = LocaleUtils.constructLocaleFromString(localeString);
        return StringUtils.toTitleCase(locale.getDisplayName(locale), locale);
    }

    // InputMethodSubtype's display name in its locale.
    //        isAdditionalSubtype (T=true, F=false)
    // locale layout |  display name
    // ------ ------ - ----------------------
    //  en_US qwerty F  English (US)            exception
    //  en_GB qwerty F  English (UK)            exception
    //  fr    azerty F  Français
    //  fr_CA qwerty F  Français (Canada)
    //  de    qwertz F  Deutsch
    //  zz    qwerty F  No language (QWERTY)    in system locale
    //  fr    qwertz T  Français (QWERTZ)
    //  de    qwerty T  Deutsch (QWERTY)
    //  en_US azerty T  English (US) (AZERTY)
    //  zz    azerty T  No language (AZERTY)    in system locale

    public static String getSubtypeDisplayName(final InputMethodSubtype subtype, Resources res) {
        final String replacementString = (Build.VERSION.SDK_INT >= /* JELLY_BEAN */ 15
                && subtype.containsExtraValueKey(UNTRANSLATABLE_STRING_IN_SUBTYPE_NAME))
                ? subtype.getExtraValueOf(UNTRANSLATABLE_STRING_IN_SUBTYPE_NAME)
                : getSubtypeLocaleDisplayName(subtype.getLocale());
        final int nameResId = subtype.getNameResId();
        final RunInLocale<String> getSubtypeName = new RunInLocale<String>() {
            @Override
            protected String job(Resources res) {
                try {
                    return res.getString(nameResId, replacementString);
                } catch (Resources.NotFoundException e) {
                    // TODO: Remove this catch when InputMethodManager.getCurrentInputMethodSubtype
                    // is fixed.
                    Log.w(TAG, "Unknown subtype: mode=" + subtype.getMode()
                            + " locale=" + subtype.getLocale()
                            + " extra=" + subtype.getExtraValue()
                            + "\n" + Utils.getStackTrace());
                    return "";
                }
            }
        };
        final Locale locale = isNoLanguage(subtype)
                ? res.getConfiguration().locale : getSubtypeLocale(subtype);
        return getSubtypeName.runInLocale(res, locale);
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
        return getKeyboardLayoutSetDisplayName(layoutName);
    }

    public static String getKeyboardLayoutSetDisplayName(String layoutName) {
        return sKeyboardLayoutToDisplayNameMap.get(layoutName);
    }

    public static String getKeyboardLayoutSetName(InputMethodSubtype subtype) {
        String keyboardLayoutSet = subtype.getExtraValueOf(KEYBOARD_LAYOUT_SET);
        if (keyboardLayoutSet == null) {
            // This subtype doesn't have a keyboardLayoutSet extra value, so lookup its keyboard
            // layout set in sLocaleAndExtraValueToKeyboardLayoutSetMap to keep it compatible with
            // pre-JellyBean.
            final String key = subtype.getLocale() + ":" + subtype.getExtraValue();
            keyboardLayoutSet = sLocaleAndExtraValueToKeyboardLayoutSetMap.get(key);
        }
        // TODO: Remove this null check when InputMethodManager.getCurrentInputMethodSubtype is
        // fixed.
        if (keyboardLayoutSet == null) {
            android.util.Log.w(TAG, "KeyboardLayoutSet not found, use QWERTY: " +
                    "locale=" + subtype.getLocale() + " extraValue=" + subtype.getExtraValue());
            return QWERTY;
        }
        return keyboardLayoutSet;
    }
}
