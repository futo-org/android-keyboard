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

package com.android.inputmethod.latin.settings;

import android.app.backup.BackupManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.util.Log;

/**
 * A base abstract class for a {@link PreferenceFragment} that implements a nested
 * {@link PreferenceScreen} of the main preference screen.
 */
public abstract class SubScreenFragment extends PreferenceFragment
        implements OnSharedPreferenceChangeListener {
    private OnSharedPreferenceChangeListener mSharedPreferenceChangeListener;

    static void setPreferenceEnabled(final String prefKey, final boolean enabled,
            final PreferenceScreen screen) {
        final Preference preference = screen.findPreference(prefKey);
        if (preference != null) {
            preference.setEnabled(enabled);
        }
    }

    static void removePreference(final String prefKey, final PreferenceScreen screen) {
        final Preference preference = screen.findPreference(prefKey);
        if (preference != null) {
            screen.removePreference(preference);
        }
    }

    static void updateListPreferenceSummaryToCurrentValue(final String prefKey,
            final PreferenceScreen screen) {
        // Because the "%s" summary trick of {@link ListPreference} doesn't work properly before
        // KitKat, we need to update the summary programmatically.
        final ListPreference listPreference = (ListPreference)screen.findPreference(prefKey);
        if (listPreference == null) {
            return;
        }
        final CharSequence entries[] = listPreference.getEntries();
        final int entryIndex = listPreference.findIndexOfValue(listPreference.getValue());
        listPreference.setSummary(entryIndex < 0 ? null : entries[entryIndex]);
    }

    final void setPreferenceEnabled(final String prefKey, final boolean enabled) {
        setPreferenceEnabled(prefKey, enabled, getPreferenceScreen());
    }

    final void removePreference(final String prefKey) {
        removePreference(prefKey, getPreferenceScreen());
    }

    final void updateListPreferenceSummaryToCurrentValue(final String prefKey) {
        updateListPreferenceSummaryToCurrentValue(prefKey, getPreferenceScreen());
    }

    final SharedPreferences getSharedPreferences() {
        return getPreferenceManager().getSharedPreferences();
    }

    /**
     * Gets the application name to display on the UI.
     */
    final String getApplicationName() {
        final Context context = getActivity();
        final Resources res = getResources();
        final int applicationLabelRes = context.getApplicationInfo().labelRes;
        return res.getString(applicationLabelRes);
    }

    @Override
    public void addPreferencesFromResource(final int preferencesResId) {
        super.addPreferencesFromResource(preferencesResId);
        TwoStatePreferenceHelper.replaceCheckBoxPreferencesBySwitchPreferences(
                getPreferenceScreen());
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
                final SubScreenFragment fragment = SubScreenFragment.this;
                final Context context = fragment.getActivity();
                if (context == null || fragment.getPreferenceScreen() == null) {
                    final String tag = fragment.getClass().getSimpleName();
                    // TODO: Introduce a static function to register this class and ensure that
                    // onCreate must be called before "onSharedPreferenceChanged" is called.
                    Log.w(tag, "onSharedPreferenceChanged called before activity starts.");
                    return;
                }
                new BackupManager(context).dataChanged();
                fragment.onSharedPreferenceChanged(prefs, key);
            }
        };
        getSharedPreferences().registerOnSharedPreferenceChangeListener(
                mSharedPreferenceChangeListener);
    }

    @Override
    public void onDestroy() {
        getSharedPreferences().unregisterOnSharedPreferenceChangeListener(
                mSharedPreferenceChangeListener);
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        // This method may be overridden by an extended class.
    }
}
