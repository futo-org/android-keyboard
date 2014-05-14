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
import android.preference.PreferenceManager;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

@SmallTest
public class KeyboardThemeTests extends AndroidTestCase {
    private SharedPreferences mPrefs;

    private static final int THEME_ID_NULL = -1;
    private static final int THEME_ID_ICS = KeyboardTheme.THEME_ID_ICS;
    private static final int THEME_ID_KLP = KeyboardTheme.THEME_ID_KLP;
    private static final int THEME_ID_LXX = KeyboardTheme.THEME_ID_LXX;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
    }

    private void assertDefaultKeyboardTheme(final int sdkVersion, final int oldThemeId,
            final int expectedThemeId) {
        if (oldThemeId == THEME_ID_NULL) {
            mPrefs.edit().remove(KeyboardTheme.KITKAT_KEYBOARD_THEME_KEY).apply();
        } else {
            final String themeIdString = Integer.toString(oldThemeId);
            mPrefs.edit().putString(KeyboardTheme.KITKAT_KEYBOARD_THEME_KEY, themeIdString).apply();
        }
        final KeyboardTheme defaultTheme =
                KeyboardTheme.getDefaultKeyboardTheme(mPrefs, sdkVersion);
        assertNotNull(defaultTheme);
        assertEquals(expectedThemeId, defaultTheme.mThemeId);
        assertFalse(mPrefs.contains(KeyboardTheme.KITKAT_KEYBOARD_THEME_KEY));
    }

    private void assertDefaultKeyboardThemeICS(final int sdkVersion) {
        assertDefaultKeyboardTheme(sdkVersion, THEME_ID_NULL, THEME_ID_ICS);
        assertDefaultKeyboardTheme(sdkVersion, THEME_ID_ICS, THEME_ID_ICS);
        assertDefaultKeyboardTheme(sdkVersion, THEME_ID_KLP, THEME_ID_KLP);
    }

    private void assertDefaultKeyboardThemeKLP(final int sdkVersion) {
        assertDefaultKeyboardTheme(sdkVersion, THEME_ID_NULL, THEME_ID_KLP);
        assertDefaultKeyboardTheme(sdkVersion, THEME_ID_ICS, THEME_ID_ICS);
        assertDefaultKeyboardTheme(sdkVersion, THEME_ID_KLP, THEME_ID_KLP);
    }

    private void assertDefaultKeyboardThemeLXX(final int sdkVersion) {
        // Forced to switch to LXX theme.
        assertDefaultKeyboardTheme(sdkVersion, THEME_ID_NULL, THEME_ID_LXX);
        assertDefaultKeyboardTheme(sdkVersion, THEME_ID_ICS, THEME_ID_LXX);
        assertDefaultKeyboardTheme(sdkVersion, THEME_ID_KLP, THEME_ID_LXX);
    }

    public void testDefaultKeyboardThemeICS() {
        assertDefaultKeyboardThemeICS(VERSION_CODES.ICE_CREAM_SANDWICH);
        assertDefaultKeyboardThemeICS(VERSION_CODES.ICE_CREAM_SANDWICH_MR1);
    }

    public void testDefaultKeyboardThemeJB() {
        assertDefaultKeyboardThemeICS(VERSION_CODES.JELLY_BEAN);
        assertDefaultKeyboardThemeICS(VERSION_CODES.JELLY_BEAN_MR1);
        assertDefaultKeyboardThemeICS(VERSION_CODES.JELLY_BEAN_MR2);
    }

    public void testDefaultKeyboardThemeKLP() {
        assertDefaultKeyboardThemeKLP(VERSION_CODES.KITKAT);
    }

    public void testDefaultKeyboardThemeLXX() {
        // TODO: Update this constant once the *next* version becomes available.
        assertDefaultKeyboardThemeLXX(VERSION_CODES.CUR_DEVELOPMENT);
    }
}
