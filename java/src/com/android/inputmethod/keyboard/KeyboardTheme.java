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
import android.util.Log;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.settings.Settings;

public final class KeyboardTheme {
    private static final String TAG = KeyboardTheme.class.getSimpleName();

    public static final int THEME_ID_ICS = 0;
    public static final int THEME_ID_GB = 1;
    public static final int THEME_ID_KLP = 2;
    private static final int DEFAULT_THEME_ID = THEME_ID_KLP;

    private static final KeyboardTheme[] KEYBOARD_THEMES = {
        new KeyboardTheme(THEME_ID_ICS, R.style.KeyboardTheme_ICS),
        new KeyboardTheme(THEME_ID_GB, R.style.KeyboardTheme_GB),
        new KeyboardTheme(THEME_ID_KLP, R.style.KeyboardTheme_KLP),
    };

    public final int mThemeId;
    public final int mStyleId;

    // Note: The themeId should be aligned with "themeId" attribute of Keyboard style
    // in values/style.xml.
    public KeyboardTheme(final int themeId, final int styleId) {
        mThemeId = themeId;
        mStyleId = styleId;
    }

    private static KeyboardTheme searchKeyboardTheme(final int themeId) {
        // TODO: This search algorithm isn't optimal if there are many themes.
        for (final KeyboardTheme theme : KEYBOARD_THEMES) {
            if (theme.mThemeId == themeId) {
                return theme;
            }
        }
        return null;
    }

    public static KeyboardTheme getDefaultKeyboardTheme() {
        return searchKeyboardTheme(DEFAULT_THEME_ID);
    }

    public static KeyboardTheme getKeyboardTheme(final SharedPreferences prefs) {
        final String themeIdString = prefs.getString(Settings.PREF_KEYBOARD_LAYOUT, null);
        if (themeIdString == null) {
            return getDefaultKeyboardTheme();
        }
        try {
            final int themeId = Integer.parseInt(themeIdString);
            final KeyboardTheme theme = searchKeyboardTheme(themeId);
            if (theme != null) {
                return theme;
            }
            Log.w(TAG, "Unknown keyboard theme in preference: " + themeIdString);
        } catch (final NumberFormatException e) {
            Log.w(TAG, "Illegal keyboard theme in preference: " + themeIdString);
        }
        // Reset preference to default value.
        final String defaultThemeIdString = Integer.toString(DEFAULT_THEME_ID);
        prefs.edit().putString(Settings.PREF_KEYBOARD_LAYOUT, defaultThemeIdString).apply();
        return getDefaultKeyboardTheme();
    }
}
