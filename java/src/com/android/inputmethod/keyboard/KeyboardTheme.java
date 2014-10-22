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

package com.android.inputmethod.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.preference.PreferenceManager;
import android.util.Log;

import com.android.inputmethod.compat.BuildCompatUtils;
import com.android.inputmethod.latin.R;

import java.util.ArrayList;
import java.util.Arrays;

public final class KeyboardTheme implements Comparable<KeyboardTheme> {
    private static final String TAG = KeyboardTheme.class.getSimpleName();

    static final String KLP_KEYBOARD_THEME_KEY = "pref_keyboard_layout_20110916";
    static final String LXX_KEYBOARD_THEME_KEY = "pref_keyboard_theme_20140509";

    // These should be aligned with Keyboard.themeId and Keyboard.Case.keyboardTheme
    // attributes' values in attrs.xml.
    public static final int THEME_ID_ICS = 0;
    public static final int THEME_ID_KLP = 2;
    public static final int THEME_ID_LXX_LIGHT = 3;
    public static final int THEME_ID_LXX_DARK = 4;
    public static final int DEFAULT_THEME_ID = THEME_ID_KLP;

    private static KeyboardTheme[] AVAILABLE_KEYBOARD_THEMES;

    /* package private for testing */
    static final KeyboardTheme[] KEYBOARD_THEMES = {
        new KeyboardTheme(THEME_ID_ICS, "ICS", R.style.KeyboardTheme_ICS,
                // This has never been selected because we support ICS or later.
                VERSION_CODES.BASE),
        new KeyboardTheme(THEME_ID_KLP, "KLP", R.style.KeyboardTheme_KLP,
                // Default theme for ICS, JB, and KLP.
                VERSION_CODES.ICE_CREAM_SANDWICH),
        new KeyboardTheme(THEME_ID_LXX_LIGHT, "LXXLight", R.style.KeyboardTheme_LXX_Light,
                // Default theme for LXX.
                Build.VERSION_CODES.LOLLIPOP),
        new KeyboardTheme(THEME_ID_LXX_DARK, "LXXDark", R.style.KeyboardTheme_LXX_Dark,
                // This has never been selected as default theme.
                VERSION_CODES.BASE),
    };

    static {
        // Sort {@link #KEYBOARD_THEME} by descending order of {@link #mMinApiVersion}.
        Arrays.sort(KEYBOARD_THEMES);
    }

    public final int mThemeId;
    public final int mStyleId;
    public final String mThemeName;
    public final int mMinApiVersion;

    // Note: The themeId should be aligned with "themeId" attribute of Keyboard style
    // in values/themes-<style>.xml.
    private KeyboardTheme(final int themeId, final String themeName, final int styleId,
            final int minApiVersion) {
        mThemeId = themeId;
        mThemeName = themeName;
        mStyleId = styleId;
        mMinApiVersion = minApiVersion;
    }

    @Override
    public int compareTo(final KeyboardTheme rhs) {
        if (mMinApiVersion > rhs.mMinApiVersion) return -1;
        if (mMinApiVersion < rhs.mMinApiVersion) return 1;
        return 0;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) return true;
        return (o instanceof KeyboardTheme) && ((KeyboardTheme)o).mThemeId == mThemeId;
    }

    @Override
    public int hashCode() {
        return mThemeId;
    }

    /* package private for testing */
    static KeyboardTheme searchKeyboardThemeById(final int themeId,
            final KeyboardTheme[] availableThemeIds) {
        // TODO: This search algorithm isn't optimal if there are many themes.
        for (final KeyboardTheme theme : availableThemeIds) {
            if (theme.mThemeId == themeId) {
                return theme;
            }
        }
        return null;
    }

    /* package private for testing */
    static KeyboardTheme getDefaultKeyboardTheme(final SharedPreferences prefs,
            final int sdkVersion, final KeyboardTheme[] availableThemeArray) {
        final String klpThemeIdString = prefs.getString(KLP_KEYBOARD_THEME_KEY, null);
        if (klpThemeIdString != null) {
            if (sdkVersion <= VERSION_CODES.KITKAT) {
                try {
                    final int themeId = Integer.parseInt(klpThemeIdString);
                    final KeyboardTheme theme = searchKeyboardThemeById(themeId,
                            availableThemeArray);
                    if (theme != null) {
                        return theme;
                    }
                    Log.w(TAG, "Unknown keyboard theme in KLP preference: " + klpThemeIdString);
                } catch (final NumberFormatException e) {
                    Log.w(TAG, "Illegal keyboard theme in KLP preference: " + klpThemeIdString, e);
                }
            }
            // Remove old preference.
            Log.i(TAG, "Remove KLP keyboard theme preference: " + klpThemeIdString);
            prefs.edit().remove(KLP_KEYBOARD_THEME_KEY).apply();
        }
        // TODO: This search algorithm isn't optimal if there are many themes.
        for (final KeyboardTheme theme : availableThemeArray) {
            if (sdkVersion >= theme.mMinApiVersion) {
                return theme;
            }
        }
        return searchKeyboardThemeById(DEFAULT_THEME_ID, availableThemeArray);
    }

    public static String getKeyboardThemeName(final int themeId) {
        final KeyboardTheme theme = searchKeyboardThemeById(themeId, KEYBOARD_THEMES);
        return theme.mThemeName;
    }

    public static void saveKeyboardThemeId(final int themeId, final SharedPreferences prefs) {
        saveKeyboardThemeId(themeId, prefs, BuildCompatUtils.EFFECTIVE_SDK_INT);
    }

    /* package private for testing */
    static String getPreferenceKey(final int sdkVersion) {
        if (sdkVersion <= VERSION_CODES.KITKAT) {
            return KLP_KEYBOARD_THEME_KEY;
        }
        return LXX_KEYBOARD_THEME_KEY;
    }

    /* package private for testing */
    static void saveKeyboardThemeId(final int themeId, final SharedPreferences prefs,
            final int sdkVersion) {
        final String prefKey = getPreferenceKey(sdkVersion);
        prefs.edit().putString(prefKey, Integer.toString(themeId)).apply();
    }

    public static KeyboardTheme getKeyboardTheme(final Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final KeyboardTheme[] availableThemeArray = getAvailableThemeArray(context);
        return getKeyboardTheme(prefs, BuildCompatUtils.EFFECTIVE_SDK_INT, availableThemeArray);
    }

    /* package private for testing */
    static KeyboardTheme[] getAvailableThemeArray(final Context context) {
        if (AVAILABLE_KEYBOARD_THEMES == null) {
            final int[] availableThemeIdStringArray = context.getResources().getIntArray(
                    R.array.keyboard_theme_ids);
            final ArrayList<KeyboardTheme> availableThemeList = new ArrayList<>();
            for (final int id : availableThemeIdStringArray) {
                final KeyboardTheme theme = searchKeyboardThemeById(id, KEYBOARD_THEMES);
                if (theme != null) {
                    availableThemeList.add(theme);
                }
            }
            AVAILABLE_KEYBOARD_THEMES = availableThemeList.toArray(
                    new KeyboardTheme[availableThemeList.size()]);
            Arrays.sort(AVAILABLE_KEYBOARD_THEMES);
        }
        return AVAILABLE_KEYBOARD_THEMES;
    }

    /* package private for testing */
    static KeyboardTheme getKeyboardTheme(final SharedPreferences prefs, final int sdkVersion,
            final KeyboardTheme[] availableThemeArray) {
        final String lxxThemeIdString = prefs.getString(LXX_KEYBOARD_THEME_KEY, null);
        if (lxxThemeIdString == null) {
            return getDefaultKeyboardTheme(prefs, sdkVersion, availableThemeArray);
        }
        try {
            final int themeId = Integer.parseInt(lxxThemeIdString);
            final KeyboardTheme theme = searchKeyboardThemeById(themeId, availableThemeArray);
            if (theme != null) {
                return theme;
            }
            Log.w(TAG, "Unknown keyboard theme in LXX preference: " + lxxThemeIdString);
        } catch (final NumberFormatException e) {
            Log.w(TAG, "Illegal keyboard theme in LXX preference: " + lxxThemeIdString, e);
        }
        // Remove preference that contains unknown or illegal theme id.
        prefs.edit().remove(LXX_KEYBOARD_THEME_KEY).apply();
        return getDefaultKeyboardTheme(prefs, sdkVersion, availableThemeArray);
    }
}
