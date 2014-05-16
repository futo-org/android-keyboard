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
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.util.Log;

import com.android.inputmethod.latin.R;

import java.util.Arrays;
import java.util.Comparator;

public final class KeyboardTheme {
    private static final String TAG = KeyboardTheme.class.getSimpleName();

    static final String KLP_KEYBOARD_THEME_KEY = "pref_keyboard_layout_20110916";
    static final String LXX_KEYBOARD_THEME_KEY = "pref_keyboard_theme_20140509";

    static final int THEME_ID_ICS = 0;
    static final int THEME_ID_KLP = 2;
    static final int THEME_ID_LXX = 3;
    static final int DEFAULT_THEME_ID = THEME_ID_KLP;

    private static final KeyboardTheme[] KEYBOARD_THEMES = {
        new KeyboardTheme(THEME_ID_ICS, R.style.KeyboardTheme_ICS,
                VERSION_CODES.BASE),
        new KeyboardTheme(THEME_ID_KLP, R.style.KeyboardTheme_KLP,
                VERSION_CODES.ICE_CREAM_SANDWICH),
        new KeyboardTheme(THEME_ID_LXX, R.style.KeyboardTheme_LXX,
                // TODO: Update this constant once the *next* version becomes available.
                VERSION_CODES.CUR_DEVELOPMENT),
    };
    static {
        // Sort {@link #KEYBOARD_THEME} by descending order of {@link #mMinApiVersion}.
        Arrays.sort(KEYBOARD_THEMES, new Comparator<KeyboardTheme>() {
            @Override
            public int compare(final KeyboardTheme lhs, final KeyboardTheme rhs) {
                if (lhs.mMinApiVersion > rhs.mMinApiVersion) return -1;
                if (lhs.mMinApiVersion < rhs.mMinApiVersion) return 1;
                return 0;
            }
        });
    }

    public final int mThemeId;
    public final int mStyleId;
    final int mMinApiVersion;

    // Note: The themeId should be aligned with "themeId" attribute of Keyboard style
    // in values/themes-<style>.xml.
    private KeyboardTheme(final int themeId, final int styleId, final int minApiVersion) {
        mThemeId = themeId;
        mStyleId = styleId;
        mMinApiVersion = minApiVersion;
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

    // TODO: This method should be removed when {@link LatinImeLogger} is removed.
    public int getCompatibleThemeIdForLogging() {
        switch (mThemeId) {
        case THEME_ID_ICS:
            return 5;
        case THEME_ID_KLP:
            return 9;
        case THEME_ID_LXX:
            return 10;
        default: // Invalid theme
            return -1;
        }
    }

    private static KeyboardTheme searchKeyboardThemeById(final int themeId) {
        // TODO: This search algorithm isn't optimal if there are many themes.
        for (final KeyboardTheme theme : KEYBOARD_THEMES) {
            if (theme.mThemeId == themeId) {
                return theme;
            }
        }
        return null;
    }

    private static int getSdkVersion() {
        final int sdkVersion = Build.VERSION.SDK_INT;
        // TODO: Consider to remove this check once the *next* version becomes available.
        if (sdkVersion == VERSION_CODES.KITKAT && Build.VERSION.CODENAME.startsWith("L")) {
            return VERSION_CODES.CUR_DEVELOPMENT;
        }
        return sdkVersion;
    }

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

    public static void saveKeyboardThemeId(final String themeIdString,
            final SharedPreferences prefs) {
        saveKeyboardThemeId(themeIdString, prefs, getSdkVersion());
    }

    static String getPreferenceKey(final int sdkVersion) {
        if (sdkVersion <= VERSION_CODES.KITKAT) {
            return KLP_KEYBOARD_THEME_KEY;
        }
        return LXX_KEYBOARD_THEME_KEY;
    }

    static void saveKeyboardThemeId(final String themeIdString, final SharedPreferences prefs,
            final int sdkVersion) {
        final String prefKey = getPreferenceKey(sdkVersion);
        prefs.edit().putString(prefKey, themeIdString).apply();
    }

    public static KeyboardTheme getKeyboardTheme(final SharedPreferences prefs) {
        return getKeyboardTheme(prefs, getSdkVersion());
    }

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
