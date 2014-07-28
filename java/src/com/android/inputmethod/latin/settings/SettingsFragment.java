/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.inputmethod.latin.settings;

import android.app.Activity;
import android.app.backup.BackupManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.android.inputmethod.keyboard.KeyboardTheme;
import com.android.inputmethod.latin.AudioAndHapticFeedbackManager;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.utils.ApplicationUtils;
import com.android.inputmethod.latin.utils.FeedbackUtils;
import com.android.inputmethodcommon.InputMethodSettingsFragment;

public final class SettingsFragment extends InputMethodSettingsFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = SettingsFragment.class.getSimpleName();

    private static final int NO_MENU_GROUP = Menu.NONE; // We don't care about menu grouping.
    private static final int MENU_FEEDBACK = Menu.FIRST; // The first menu item id and order.
    private static final int MENU_ABOUT = Menu.FIRST + 1; // The second menu item id and order.

    private void updateListPreferenceSummaryToCurrentValue(final String prefKey) {
        // Because the "%s" summary trick of {@link ListPreference} doesn't work properly before
        // KitKat, we need to update the summary programmatically.
        final ListPreference listPreference = (ListPreference)findPreference(prefKey);
        if (listPreference == null) {
            return;
        }
        final CharSequence entries[] = listPreference.getEntries();
        final int entryIndex = listPreference.findIndexOfValue(listPreference.getValue());
        listPreference.setSummary(entryIndex < 0 ? null : entries[entryIndex]);
    }

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        setHasOptionsMenu(true);
        setInputMethodSettingsCategoryTitle(R.string.language_selection_title);
        setSubtypeEnablerTitle(R.string.select_language);
        addPreferencesFromResource(R.xml.prefs);
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        TwoStatePreferenceHelper.replaceCheckBoxPreferencesBySwitchPreferences(preferenceScreen);
        preferenceScreen.setTitle(
                ApplicationUtils.getActivityTitleResId(getActivity(), SettingsActivity.class));

        final Resources res = getResources();
        final Context context = getActivity();

        // When we are called from the Settings application but we are not already running, some
        // singleton and utility classes may not have been initialized.  We have to call
        // initialization method of these classes here. See {@link LatinIME#onCreate()}.
        AudioAndHapticFeedbackManager.init(context);

        final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        prefs.registerOnSharedPreferenceChangeListener(this);

        if (!Settings.readFromBuildConfigIfGestureInputEnabled(res)) {
            getPreferenceScreen().removePreference(findPreference(Settings.SCREEN_GESTURE));
        }

        AdditionalFeaturesSettingUtils.addAdditionalFeaturesPreferences(context, this);
    }

    @Override
    public void onResume() {
        super.onResume();
        final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        final ListPreference keyboardThemePref = (ListPreference)findPreference(
                Settings.PREF_KEYBOARD_THEME);
        if (keyboardThemePref != null) {
            final KeyboardTheme keyboardTheme = KeyboardTheme.getKeyboardTheme(prefs);
            final String value = Integer.toString(keyboardTheme.mThemeId);
            final CharSequence entries[] = keyboardThemePref.getEntries();
            final int entryIndex = keyboardThemePref.findIndexOfValue(value);
            keyboardThemePref.setSummary(entryIndex < 0 ? null : entries[entryIndex]);
            keyboardThemePref.setValue(value);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        final ListPreference keyboardThemePref = (ListPreference)findPreference(
                Settings.PREF_KEYBOARD_THEME);
        if (keyboardThemePref != null) {
            KeyboardTheme.saveKeyboardThemeId(keyboardThemePref.getValue(), prefs);
        }
    }

    @Override
    public void onDestroy() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(
                this);
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        final Activity activity = getActivity();
        if (activity == null) {
            // TODO: Introduce a static function to register this class and ensure that
            // onCreate must be called before "onSharedPreferenceChanged" is called.
            Log.w(TAG, "onSharedPreferenceChanged called before activity starts.");
            return;
        }
        (new BackupManager(activity)).dataChanged();
        updateListPreferenceSummaryToCurrentValue(Settings.PREF_KEYBOARD_THEME);
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        if (FeedbackUtils.isFeedbackFormSupported()) {
            menu.add(NO_MENU_GROUP, MENU_FEEDBACK /* itemId */, MENU_FEEDBACK /* order */,
                    R.string.send_feedback);
        }
        final int aboutResId = FeedbackUtils.getAboutKeyboardTitleResId();
        if (aboutResId != 0) {
            menu.add(NO_MENU_GROUP, MENU_ABOUT /* itemId */, MENU_ABOUT /* order */, aboutResId);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == MENU_FEEDBACK) {
            FeedbackUtils.showFeedbackForm(getActivity());
            return true;
        }
        if (itemId == MENU_ABOUT) {
            final Intent aboutIntent = FeedbackUtils.getAboutKeyboardIntent(getActivity());
            if (aboutIntent != null) {
                startActivity(aboutIntent);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }
}
