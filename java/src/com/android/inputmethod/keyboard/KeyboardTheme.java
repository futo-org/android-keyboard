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

import android.content.SharedPreferences;
import android.os.Build.VERSION_CODES;
import android.util.Log;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.compat.BuildCompatUtils;
import com.android.inputmethod.latin.R;

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

    private static final KeyboardTheme[] KEYBOARD_THEMES = {
        new KeyboardTheme(THEME_ID_ICS, "ICS", R.style.KeyboardTheme_ICS,
                // This has never been selected because we support ICS or later.
                VERSION_CODES.BASE),
        new KeyboardTheme(THEME_ID_KLP, "KLP", R.style.KeyboardTheme_KLP,
                // Default theme for ICS, JB, and KLP.
                VERSION_CODES.ICE_CREAM_SANDWICH),
        new KeyboardTheme(THEME_ID_LXX_LIGHT, "LXXLight", R.style.KeyboardTheme_LXX_Light,
                // Default theme for LXX.
                BuildCompatUtils.VERSION_CODES_LXX),
        new KeyboardTheme(THEME_ID_LXX_DARK, "LXXDark", R.style.KeyboardTheme_LXX_Dark,
                VERSION_CODES.BASE),
    };

    static {
        // Sort {@link #KEYBOARD_THEME} by descending order of {@link #mMinApiVersion}.
        Arrays.sort(KEYBOARD_THEMES);
    }

    public final int mThemeId;
    public final int mStyleId;
    public final String mThemeName;
    private final int mMinApiVersion;

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

    @UsedForTesting
    static KeyboardTheme searchKeyboardThemeById(final int themeId) {
        // TODO: This search algorithm isn't optimal if there are many themes.
        for (final KeyboardTheme theme : KEYBOARD_THEMES) {
            if (theme.mThemeId == themeId) {
                return theme;
            }
        }
        return null;
    }

    @UsedForTesting
    static KeyboardTheme getDefaultKeyboardTheme(final SharedPreferences prefs,
            final int sdkVersion) {
        final String klpThemeIdString = prefs.getString(KLP_KEYBOARD_THEME_KEY, null);
        if (klpThemeIdString != null) {
            if (sdkVersion <= VERSION_CODES.KITKAT) {
                try {
                    final int themeId = Integer.parseInt(klpThemeIdString);
                    final KeyboardTheme theme = searchKeyboardThemeById(themeId);
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
        for (final KeyboardTheme theme : KEYBOARD_THEMES) {
            if (sdkVersion >= theme.mMinApiVersion) {
                return theme;
            }
        }
        return searchKeyboardThemeById(DEFAULT_THEME_ID);
    }

    public static String getKeyboardThemeName(final int themeId) {
        final KeyboardTheme theme = searchKeyboardThemeById(themeId);
        return theme.mThemeName;
    }

    public static void saveKeyboardThemeId(final String themeIdString,
            final SharedPreferences prefs) {
        saveKeyboardThemeId(themeIdString, prefs, BuildCompatUtils.EFFECTIVE_SDK_INT);
    }

    @UsedForTesting
    static String getPreferenceKey(final int sdkVersion) {
        if (sdkVersion <= VERSION_CODES.KITKAT) {
            return KLP_KEYBOARD_THEME_KEY;
        }
        return LXX_KEYBOARD_THEME_KEY;
    }

    @UsedForTesting
    static void saveKeyboardThemeId(final String themeIdString,
            final SharedPreferences prefs, final int sdkVersion) {
        final String prefKey = getPreferenceKey(sdkVersion);
        prefs.edit().putString(prefKey, themeIdString).apply();
    }

    public static KeyboardTheme getKeyboardTheme(final SharedPreferences prefs) {
        return getKeyboardTheme(prefs, BuildCompatUtils.EFFECTIVE_SDK_INT);
    }

    @UsedForTesting
    static KeyboardTheme getKeyboardTheme(final SharedPreferences prefs, final int sdkVersion) {
        final String lxxThemeIdString = prefs.getString(LXX_KEYBOARD_THEME_KEY, null);
        if (lxxThemeIdString == null) {
            return getDefaultKeyboardTheme(prefs, sdkVersion);
        }
        try {
            final int themeId = Integer.parseInt(lxxThemeIdString);
            final KeyboardTheme theme = searchKeyboardThemeById(themeId);
            if (theme != null) {
                return theme;
            }
            Log.w(TAG, "Unknown keyboard theme in LXX preference: " + lxxThemeIdString);
        } catch (final NumberFormatException e) {
            Log.w(TAG, "Illegal keyboard theme in LXX preference: " + lxxThemeIdString, e);
        }
        // Remove preference that contains unknown or illegal theme id.
        prefs.edit().remove(LXX_KEYBOARD_THEME_KEY).apply();
        return getDefaultKeyboardTheme(prefs, sdkVersion);
    }
}
