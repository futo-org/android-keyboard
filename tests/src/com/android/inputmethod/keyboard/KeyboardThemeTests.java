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

import static com.android.inputmethod.keyboard.KeyboardTheme.THEME_ID_ICS;
import static com.android.inputmethod.keyboard.KeyboardTheme.THEME_ID_KLP;
import static com.android.inputmethod.keyboard.KeyboardTheme.THEME_ID_LXX_DARK;
import static com.android.inputmethod.keyboard.KeyboardTheme.THEME_ID_LXX_LIGHT;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.preference.PreferenceManager;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.Arrays;

@SmallTest
public class KeyboardThemeTests extends AndroidTestCase {
    private SharedPreferences mPrefs;

    private static final int THEME_ID_NULL = -1;
    private static final int THEME_ID_UNKNOWN = -2;
    private static final int THEME_ID_ILLEGAL = -3;
    private static final String ILLEGAL_THEME_ID_STRING = "ThisCausesNumberFormatExecption";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
    }

    /*
     * Helper functions.
     */

    private static boolean isValidKeyboardThemeId(final int themeId) {
        switch (themeId) {
        case THEME_ID_ICS:
        case THEME_ID_KLP:
        case THEME_ID_LXX_LIGHT:
        case THEME_ID_LXX_DARK:
            return true;
        default:
            return false;
        }
    }

    private void setKeyboardThemePreference(final String prefKey, final int themeId) {
        final String themeIdString = Integer.toString(themeId);
        if (isValidKeyboardThemeId(themeId) || themeId == THEME_ID_UNKNOWN) {
            // Set valid theme id to preference.
            mPrefs.edit().putString(prefKey, themeIdString).apply();
            return;
        }
        if (themeId == THEME_ID_NULL) {
            // Simulate undefined preference.
            mPrefs.edit().remove(prefKey).apply();
            return;
        }
        // themeId == THEME_ID_ILLEGAL
        // Simulate illegal format theme id in preference.
        mPrefs.edit().putString(prefKey, ILLEGAL_THEME_ID_STRING).apply();
    }

    private void assertKeyboardTheme(final int sdkVersion, final int expectedThemeId) {
        final KeyboardTheme actualTheme = KeyboardTheme.getKeyboardTheme(
                mPrefs, sdkVersion, KeyboardTheme.KEYBOARD_THEMES);
        assertEquals(expectedThemeId, actualTheme.mThemeId);
    }

    /*
     * Test keyboard theme preference on the same platform version and the same keyboard version.
     */

    private void assertKeyboardThemePreference(final int sdkVersion, final int previousThemeId,
            final int expectedThemeId) {
        // Clear preferences before testing.
        setKeyboardThemePreference(KeyboardTheme.KLP_KEYBOARD_THEME_KEY, THEME_ID_NULL);
        setKeyboardThemePreference(KeyboardTheme.LXX_KEYBOARD_THEME_KEY, THEME_ID_NULL);
        // Set the preference of the sdkVersion to previousThemeId.
        final String prefKey = KeyboardTheme.getPreferenceKey(sdkVersion);
        setKeyboardThemePreference(prefKey, previousThemeId);
        assertKeyboardTheme(sdkVersion, expectedThemeId);
    }

    private void assertKeyboardThemePreferenceOnKlp(final int sdkVersion) {
        final int defaultThemeId = THEME_ID_KLP;
        assertKeyboardThemePreference(sdkVersion, THEME_ID_NULL, defaultThemeId);
        assertKeyboardThemePreference(sdkVersion, THEME_ID_ICS, THEME_ID_ICS);
        assertKeyboardThemePreference(sdkVersion, THEME_ID_KLP, THEME_ID_KLP);
        assertKeyboardThemePreference(sdkVersion, THEME_ID_LXX_LIGHT, THEME_ID_LXX_LIGHT);
        assertKeyboardThemePreference(sdkVersion, THEME_ID_LXX_DARK, THEME_ID_LXX_DARK);
        assertKeyboardThemePreference(sdkVersion, THEME_ID_UNKNOWN, defaultThemeId);
        assertKeyboardThemePreference(sdkVersion, THEME_ID_ILLEGAL, defaultThemeId);
    }

    public void testKeyboardThemePreferenceOnKlp() {
        assertKeyboardThemePreferenceOnKlp(VERSION_CODES.ICE_CREAM_SANDWICH);
        assertKeyboardThemePreferenceOnKlp(VERSION_CODES.ICE_CREAM_SANDWICH_MR1);
        assertKeyboardThemePreferenceOnKlp(VERSION_CODES.JELLY_BEAN);
        assertKeyboardThemePreferenceOnKlp(VERSION_CODES.JELLY_BEAN_MR1);
        assertKeyboardThemePreferenceOnKlp(VERSION_CODES.JELLY_BEAN_MR2);
        assertKeyboardThemePreferenceOnKlp(VERSION_CODES.KITKAT);
    }

    private void assertKeyboardThemePreferenceOnLxx(final int sdkVersion) {
        final int defaultThemeId = THEME_ID_LXX_LIGHT;
        assertKeyboardThemePreference(sdkVersion, THEME_ID_NULL, defaultThemeId);
        assertKeyboardThemePreference(sdkVersion, THEME_ID_ICS, THEME_ID_ICS);
        assertKeyboardThemePreference(sdkVersion, THEME_ID_KLP, THEME_ID_KLP);
        assertKeyboardThemePreference(sdkVersion, THEME_ID_LXX_LIGHT, THEME_ID_LXX_LIGHT);
        assertKeyboardThemePreference(sdkVersion, THEME_ID_LXX_DARK, THEME_ID_LXX_DARK);
        assertKeyboardThemePreference(sdkVersion, THEME_ID_UNKNOWN, defaultThemeId);
        assertKeyboardThemePreference(sdkVersion, THEME_ID_ILLEGAL, defaultThemeId);
    }

    public void testKeyboardThemePreferenceOnLxx() {
        assertKeyboardThemePreferenceOnLxx(Build.VERSION_CODES.LOLLIPOP);
    }

    /*
     * Test default keyboard theme based on the platform version.
     */

    private void assertDefaultKeyboardTheme(final int sdkVersion, final int previousThemeId,
            final int expectedThemeId) {
        final String oldPrefKey = KeyboardTheme.KLP_KEYBOARD_THEME_KEY;
        setKeyboardThemePreference(oldPrefKey, previousThemeId);

        final KeyboardTheme defaultTheme = KeyboardTheme.getDefaultKeyboardTheme(
                mPrefs, sdkVersion, KeyboardTheme.KEYBOARD_THEMES);

        assertNotNull(defaultTheme);
        assertEquals(expectedThemeId, defaultTheme.mThemeId);
        if (sdkVersion <= VERSION_CODES.KITKAT) {
            // Old preference must be retained if it is valid. Otherwise it must be pruned.
            assertEquals(isValidKeyboardThemeId(previousThemeId), mPrefs.contains(oldPrefKey));
            return;
        }
        // Old preference must be removed.
        assertFalse(mPrefs.contains(oldPrefKey));
    }

    private void assertDefaultKeyboardThemeOnKlp(final int sdkVersion) {
        assertDefaultKeyboardTheme(sdkVersion, THEME_ID_NULL, THEME_ID_KLP);
        assertDefaultKeyboardTheme(sdkVersion, THEME_ID_ICS, THEME_ID_ICS);
        assertDefaultKeyboardTheme(sdkVersion, THEME_ID_KLP, THEME_ID_KLP);
        assertDefaultKeyboardTheme(sdkVersion, THEME_ID_UNKNOWN, THEME_ID_KLP);
        assertDefaultKeyboardTheme(sdkVersion, THEME_ID_ILLEGAL, THEME_ID_KLP);
    }

    public void testDefaultKeyboardThemeOnKlp() {
        assertDefaultKeyboardThemeOnKlp(VERSION_CODES.ICE_CREAM_SANDWICH);
        assertDefaultKeyboardThemeOnKlp(VERSION_CODES.ICE_CREAM_SANDWICH_MR1);
        assertDefaultKeyboardThemeOnKlp(VERSION_CODES.JELLY_BEAN);
        assertDefaultKeyboardThemeOnKlp(VERSION_CODES.JELLY_BEAN_MR1);
        assertDefaultKeyboardThemeOnKlp(VERSION_CODES.JELLY_BEAN_MR2);
        assertDefaultKeyboardThemeOnKlp(VERSION_CODES.KITKAT);
    }

    private void assertDefaultKeyboardThemeOnLxx(final int sdkVersion) {
        // Forced to switch to LXX theme.
        assertDefaultKeyboardTheme(sdkVersion, THEME_ID_NULL, THEME_ID_LXX_LIGHT);
        assertDefaultKeyboardTheme(sdkVersion, THEME_ID_ICS, THEME_ID_LXX_LIGHT);
        assertDefaultKeyboardTheme(sdkVersion, THEME_ID_KLP, THEME_ID_LXX_LIGHT);
        assertDefaultKeyboardTheme(sdkVersion, THEME_ID_UNKNOWN, THEME_ID_LXX_LIGHT);
        assertDefaultKeyboardTheme(sdkVersion, THEME_ID_ILLEGAL, THEME_ID_LXX_LIGHT);
    }

    public void testDefaultKeyboardThemeOnLxx() {
        assertDefaultKeyboardThemeOnLxx(Build.VERSION_CODES.LOLLIPOP);
    }

    /*
     * Test keyboard theme preference while upgrading the keyboard that doesn't support LXX theme
     * to the keyboard that supports LXX theme.
     */

    private void assertUpgradeKeyboardToLxxOn(final int sdkVersion, final int previousThemeId,
            final int expectedThemeId) {
        setKeyboardThemePreference(KeyboardTheme.KLP_KEYBOARD_THEME_KEY, previousThemeId);
        // Clean up new keyboard theme preference to simulate "upgrade to LXX keyboard".
        setKeyboardThemePreference(KeyboardTheme.LXX_KEYBOARD_THEME_KEY, THEME_ID_NULL);

        final KeyboardTheme theme = KeyboardTheme.getKeyboardTheme(
                mPrefs, sdkVersion, KeyboardTheme.KEYBOARD_THEMES);

        assertNotNull(theme);
        assertEquals(expectedThemeId, theme.mThemeId);
        if (sdkVersion <= VERSION_CODES.KITKAT) {
            // New preference must not exist.
            assertFalse(mPrefs.contains(KeyboardTheme.LXX_KEYBOARD_THEME_KEY));
            // Old preference must be retained if it is valid. Otherwise it must be pruned.
            assertEquals(isValidKeyboardThemeId(previousThemeId),
                    mPrefs.contains(KeyboardTheme.KLP_KEYBOARD_THEME_KEY));
            if (isValidKeyboardThemeId(previousThemeId)) {
                // Old preference must have an expected value.
                assertEquals(mPrefs.getString(KeyboardTheme.KLP_KEYBOARD_THEME_KEY, null),
                        Integer.toString(expectedThemeId));
            }
            return;
        }
        // Old preference must be removed.
        assertFalse(mPrefs.contains(KeyboardTheme.KLP_KEYBOARD_THEME_KEY));
        // New preference must not exist.
        assertFalse(mPrefs.contains(KeyboardTheme.LXX_KEYBOARD_THEME_KEY));
    }

    private void assertUpgradeKeyboardToLxxOnKlp(final int sdkVersion) {
        assertUpgradeKeyboardToLxxOn(sdkVersion, THEME_ID_NULL, THEME_ID_KLP);
        assertUpgradeKeyboardToLxxOn(sdkVersion, THEME_ID_ICS, THEME_ID_ICS);
        assertUpgradeKeyboardToLxxOn(sdkVersion, THEME_ID_KLP, THEME_ID_KLP);
        assertUpgradeKeyboardToLxxOn(sdkVersion, THEME_ID_UNKNOWN, THEME_ID_KLP);
        assertUpgradeKeyboardToLxxOn(sdkVersion, THEME_ID_ILLEGAL, THEME_ID_KLP);
    }

    // Upgrading keyboard on I,J and K.
    public void testUpgradeKeyboardToLxxOnKlp() {
        assertUpgradeKeyboardToLxxOnKlp(VERSION_CODES.ICE_CREAM_SANDWICH);
        assertUpgradeKeyboardToLxxOnKlp(VERSION_CODES.ICE_CREAM_SANDWICH_MR1);
        assertUpgradeKeyboardToLxxOnKlp(VERSION_CODES.JELLY_BEAN);
        assertUpgradeKeyboardToLxxOnKlp(VERSION_CODES.JELLY_BEAN_MR1);
        assertUpgradeKeyboardToLxxOnKlp(VERSION_CODES.JELLY_BEAN_MR2);
        assertUpgradeKeyboardToLxxOnKlp(VERSION_CODES.KITKAT);
    }

    private void assertUpgradeKeyboardToLxxOnLxx(final int sdkVersion) {
        // Forced to switch to LXX theme.
        assertUpgradeKeyboardToLxxOn(sdkVersion, THEME_ID_NULL, THEME_ID_LXX_LIGHT);
        assertUpgradeKeyboardToLxxOn(sdkVersion, THEME_ID_ICS, THEME_ID_LXX_LIGHT);
        assertUpgradeKeyboardToLxxOn(sdkVersion, THEME_ID_KLP, THEME_ID_LXX_LIGHT);
        assertUpgradeKeyboardToLxxOn(sdkVersion, THEME_ID_UNKNOWN, THEME_ID_LXX_LIGHT);
        assertUpgradeKeyboardToLxxOn(sdkVersion, THEME_ID_ILLEGAL, THEME_ID_LXX_LIGHT);
    }

    // Upgrading keyboard on L.
    public void testUpgradeKeyboardToLxxOnLxx() {
        assertUpgradeKeyboardToLxxOnLxx(Build.VERSION_CODES.LOLLIPOP);
    }

    /*
     * Test keyboard theme preference while upgrading platform version.
     */

    private void assertUpgradePlatformFromTo(final int oldSdkVersion, final int newSdkVersion,
            final int previousThemeId, final int expectedThemeId) {
        if (newSdkVersion < oldSdkVersion) {
            // No need to test.
            return;
        }
        // Clean up preferences.
        setKeyboardThemePreference(KeyboardTheme.KLP_KEYBOARD_THEME_KEY, THEME_ID_NULL);
        setKeyboardThemePreference(KeyboardTheme.LXX_KEYBOARD_THEME_KEY, THEME_ID_NULL);

        final String oldPrefKey = KeyboardTheme.getPreferenceKey(oldSdkVersion);
        setKeyboardThemePreference(oldPrefKey, previousThemeId);

        assertKeyboardTheme(newSdkVersion, expectedThemeId);
    }

    private void assertUpgradePlatformFromKlpToKlp(final int oldSdkVersion,
            final int newSdkVersion) {
        assertUpgradePlatformFromTo(oldSdkVersion, newSdkVersion, THEME_ID_NULL, THEME_ID_KLP);
        assertUpgradePlatformFromTo(oldSdkVersion, newSdkVersion, THEME_ID_ICS, THEME_ID_ICS);
        assertUpgradePlatformFromTo(oldSdkVersion, newSdkVersion, THEME_ID_KLP, THEME_ID_KLP);
        assertUpgradePlatformFromTo(oldSdkVersion, newSdkVersion, THEME_ID_UNKNOWN, THEME_ID_KLP);
        assertUpgradePlatformFromTo(oldSdkVersion, newSdkVersion, THEME_ID_ILLEGAL, THEME_ID_KLP);
    }

    private void assertUpgradePlatformToKlpFrom(final int oldSdkVersion) {
        assertUpgradePlatformFromKlpToKlp(oldSdkVersion, VERSION_CODES.ICE_CREAM_SANDWICH);
        assertUpgradePlatformFromKlpToKlp(oldSdkVersion, VERSION_CODES.ICE_CREAM_SANDWICH_MR1);
        assertUpgradePlatformFromKlpToKlp(oldSdkVersion, VERSION_CODES.JELLY_BEAN);
        assertUpgradePlatformFromKlpToKlp(oldSdkVersion, VERSION_CODES.JELLY_BEAN_MR1);
        assertUpgradePlatformFromKlpToKlp(oldSdkVersion, VERSION_CODES.JELLY_BEAN_MR2);
        assertUpgradePlatformFromKlpToKlp(oldSdkVersion, VERSION_CODES.KITKAT);
    }

    // Update platform from I,J, and K to I,J, and K
    public void testUpgradePlatformToKlpFromKlp() {
        assertUpgradePlatformToKlpFrom(VERSION_CODES.ICE_CREAM_SANDWICH);
        assertUpgradePlatformToKlpFrom(VERSION_CODES.ICE_CREAM_SANDWICH_MR1);
        assertUpgradePlatformToKlpFrom(VERSION_CODES.JELLY_BEAN);
        assertUpgradePlatformToKlpFrom(VERSION_CODES.JELLY_BEAN_MR1);
        assertUpgradePlatformToKlpFrom(VERSION_CODES.JELLY_BEAN_MR2);
        assertUpgradePlatformToKlpFrom(VERSION_CODES.KITKAT);
    }

    private void assertUpgradePlatformToLxxFrom(final int oldSdkVersion) {
        // Forced to switch to LXX theme.
        final int newSdkVersion = Build.VERSION_CODES.LOLLIPOP;
        assertUpgradePlatformFromTo(
                oldSdkVersion, newSdkVersion, THEME_ID_NULL, THEME_ID_LXX_LIGHT);
        assertUpgradePlatformFromTo(
                oldSdkVersion, newSdkVersion, THEME_ID_ICS, THEME_ID_LXX_LIGHT);
        assertUpgradePlatformFromTo(
                oldSdkVersion, newSdkVersion, THEME_ID_KLP, THEME_ID_LXX_LIGHT);
        assertUpgradePlatformFromTo(
                oldSdkVersion, newSdkVersion, THEME_ID_UNKNOWN, THEME_ID_LXX_LIGHT);
        assertUpgradePlatformFromTo(
                oldSdkVersion, newSdkVersion, THEME_ID_ILLEGAL, THEME_ID_LXX_LIGHT);
    }

    // Update platform from I,J, and K to L
    public void testUpgradePlatformToLxx() {
        assertUpgradePlatformToLxxFrom(VERSION_CODES.ICE_CREAM_SANDWICH);
        assertUpgradePlatformToLxxFrom(VERSION_CODES.ICE_CREAM_SANDWICH_MR1);
        assertUpgradePlatformToLxxFrom(VERSION_CODES.JELLY_BEAN);
        assertUpgradePlatformToLxxFrom(VERSION_CODES.JELLY_BEAN_MR1);
        assertUpgradePlatformToLxxFrom(VERSION_CODES.JELLY_BEAN_MR2);
        assertUpgradePlatformToLxxFrom(VERSION_CODES.KITKAT);
    }

    // Update platform from L to L.
    public void testUpgradePlatformToLxxFromLxx() {
        final int oldSdkVersion = Build.VERSION_CODES.LOLLIPOP;
        final int newSdkVersion = Build.VERSION_CODES.LOLLIPOP;
        assertUpgradePlatformFromTo(
                oldSdkVersion, newSdkVersion, THEME_ID_NULL, THEME_ID_LXX_LIGHT);
        assertUpgradePlatformFromTo(
                oldSdkVersion, newSdkVersion, THEME_ID_ICS, THEME_ID_ICS);
        assertUpgradePlatformFromTo(
                oldSdkVersion, newSdkVersion, THEME_ID_KLP, THEME_ID_KLP);
        assertUpgradePlatformFromTo(
                oldSdkVersion, newSdkVersion, THEME_ID_LXX_LIGHT, THEME_ID_LXX_LIGHT);
        assertUpgradePlatformFromTo(
                oldSdkVersion, newSdkVersion, THEME_ID_LXX_DARK, THEME_ID_LXX_DARK);
        assertUpgradePlatformFromTo(
                oldSdkVersion, newSdkVersion, THEME_ID_UNKNOWN, THEME_ID_LXX_LIGHT);
        assertUpgradePlatformFromTo(
                oldSdkVersion, newSdkVersion, THEME_ID_ILLEGAL, THEME_ID_LXX_LIGHT);
    }

    /*
     * Test that KeyboardTheme array should be sorted by descending order of
     * {@link KeyboardTheme#mMinApiVersion}.
     */
    private static void assertSortedKeyboardThemeArray(final KeyboardTheme[] array) {
        assertNotNull(array);
        final int length = array.length;
        assertTrue("array length=" + length, length > 0);
        for (int index = 0; index < length - 1; index++) {
            final KeyboardTheme theme = array[index];
            final KeyboardTheme nextTheme = array[index + 1];
            assertTrue("sorted MinApiVersion: "
                    + theme.mThemeName + ": minApiVersion=" + theme.mMinApiVersion,
                    theme.mMinApiVersion >= nextTheme.mMinApiVersion);
        }
    }

    public void testSortedKeyboardTheme() {
        assertSortedKeyboardThemeArray(KeyboardTheme.KEYBOARD_THEMES);
    }

    public void testSortedAvailableKeyboardTheme() {
        assertSortedKeyboardThemeArray(KeyboardTheme.getAvailableThemeArray(getContext()));
    }

    /*
     * Test for missing selected theme.
     */
    private static KeyboardTheme[] LIMITED_THEMES = {
        KeyboardTheme.searchKeyboardThemeById(THEME_ID_ICS, KeyboardTheme.KEYBOARD_THEMES),
        KeyboardTheme.searchKeyboardThemeById(THEME_ID_KLP, KeyboardTheme.KEYBOARD_THEMES)
    };
    static {
        Arrays.sort(LIMITED_THEMES);
        assertSortedKeyboardThemeArray(LIMITED_THEMES);
    }

    public void testMissingSelectedThemeIcs() {
        // Clean up preferences.
        setKeyboardThemePreference(KeyboardTheme.KLP_KEYBOARD_THEME_KEY, THEME_ID_NULL);
        setKeyboardThemePreference(KeyboardTheme.LXX_KEYBOARD_THEME_KEY, THEME_ID_NULL);

        final int sdkVersion = VERSION_CODES.ICE_CREAM_SANDWICH;
        final String oldPrefKey = KeyboardTheme.getPreferenceKey(sdkVersion);
        setKeyboardThemePreference(oldPrefKey, THEME_ID_LXX_LIGHT);

        final KeyboardTheme actualTheme = KeyboardTheme.getKeyboardTheme(
                mPrefs, sdkVersion, LIMITED_THEMES);
        // LXX_LIGHT is missing, fall-back to KLP.
        assertEquals(THEME_ID_KLP, actualTheme.mThemeId);
    }

    public void testMissingSelectedThemeKlp() {
        // Clean up preferences.
        setKeyboardThemePreference(KeyboardTheme.KLP_KEYBOARD_THEME_KEY, THEME_ID_NULL);
        setKeyboardThemePreference(KeyboardTheme.LXX_KEYBOARD_THEME_KEY, THEME_ID_NULL);

        final int sdkVersion = VERSION_CODES.KITKAT;
        final String oldPrefKey = KeyboardTheme.getPreferenceKey(sdkVersion);
        setKeyboardThemePreference(oldPrefKey, THEME_ID_LXX_LIGHT);

        final KeyboardTheme actualTheme = KeyboardTheme.getKeyboardTheme(
                mPrefs, sdkVersion, LIMITED_THEMES);
        // LXX_LIGHT is missing, fall-back to KLP.
        assertEquals(THEME_ID_KLP, actualTheme.mThemeId);
    }

    public void testMissingSelectedThemeLxx() {
        // Clean up preferences.
        setKeyboardThemePreference(KeyboardTheme.KLP_KEYBOARD_THEME_KEY, THEME_ID_NULL);
        setKeyboardThemePreference(KeyboardTheme.LXX_KEYBOARD_THEME_KEY, THEME_ID_NULL);

        final int sdkVersion = Build.VERSION_CODES.LOLLIPOP;
        final String oldPrefKey = KeyboardTheme.getPreferenceKey(sdkVersion);
        setKeyboardThemePreference(oldPrefKey, THEME_ID_LXX_DARK);

        final KeyboardTheme actualTheme = KeyboardTheme.getKeyboardTheme(
                mPrefs, sdkVersion, LIMITED_THEMES);
        // LXX_DARK is missing, fall-back to KLP.
        assertEquals(THEME_ID_KLP, actualTheme.mThemeId);
    }
}
