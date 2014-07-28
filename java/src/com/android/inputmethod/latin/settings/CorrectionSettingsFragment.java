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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;

import com.android.inputmethod.dictionarypack.DictionarySettingsActivity;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.userdictionary.UserDictionaryList;
import com.android.inputmethod.latin.userdictionary.UserDictionarySettings;

import java.util.TreeSet;

/**
 * "Text correction" settings sub screen.
 *
 * This settings sub screen handles the following text correction preferences.
 * - Personal dictionary
 * - Add-on dictionaries
 * - Block offensive words
 * - Auto-correction
 * - Show correction suggestions
 * - Personalized suggestions
 * - Suggest Contact names
 * - Next-word suggestions
 */
public final class CorrectionSettingsFragment extends SubScreenFragment {
    private static final boolean DBG_USE_INTERNAL_PERSONAL_DICTIONARY_SETTINGS = false;
    private static final boolean USE_INTERNAL_PERSONAL_DICTIONARY_SETTIGS =
            DBG_USE_INTERNAL_PERSONAL_DICTIONARY_SETTINGS
            || Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR2;

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_correction);

        final Context context = getActivity();
        final PackageManager pm = context.getPackageManager();

        ensureConsistencyOfAutoCorrectionSettings();

        final Preference dictionaryLink = findPreference(Settings.PREF_CONFIGURE_DICTIONARIES_KEY);
        final Intent intent = dictionaryLink.getIntent();
        intent.setClassName(context.getPackageName(), DictionarySettingsActivity.class.getName());
        final int number = pm.queryIntentActivities(intent, 0).size();
        if (0 >= number) {
            removePreference(Settings.PREF_CONFIGURE_DICTIONARIES_KEY);
        }

        final Preference editPersonalDictionary =
                findPreference(Settings.PREF_EDIT_PERSONAL_DICTIONARY);
        final Intent editPersonalDictionaryIntent = editPersonalDictionary.getIntent();
        final ResolveInfo ri = USE_INTERNAL_PERSONAL_DICTIONARY_SETTIGS ? null
                : pm.resolveActivity(
                        editPersonalDictionaryIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (ri == null) {
            overwriteUserDictionaryPreference(editPersonalDictionary);
        }
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        ensureConsistencyOfAutoCorrectionSettings();
    }

    private void ensureConsistencyOfAutoCorrectionSettings() {
        final String autoCorrectionOff = getString(
                R.string.auto_correction_threshold_mode_index_off);
        final ListPreference autoCorrectionThresholdPref = (ListPreference)findPreference(
                Settings.PREF_AUTO_CORRECTION_THRESHOLD);
        final String currentSetting = autoCorrectionThresholdPref.getValue();
        setPreferenceEnabled(
                Settings.PREF_BIGRAM_PREDICTIONS, !currentSetting.equals(autoCorrectionOff));
    }

    private void overwriteUserDictionaryPreference(final Preference userDictionaryPreference) {
        final Activity activity = getActivity();
        final TreeSet<String> localeList = UserDictionaryList.getUserDictionaryLocalesSet(activity);
        if (null == localeList) {
            // The locale list is null if and only if the user dictionary service is
            // not present or disabled. In this case we need to remove the preference.
            getPreferenceScreen().removePreference(userDictionaryPreference);
        } else if (localeList.size() <= 1) {
            userDictionaryPreference.setFragment(UserDictionarySettings.class.getName());
            // If the size of localeList is 0, we don't set the locale parameter in the
            // extras. This will be interpreted by the UserDictionarySettings class as
            // meaning "the current locale".
            // Note that with the current code for UserDictionaryList#getUserDictionaryLocalesSet()
            // the locale list always has at least one element, since it always includes the current
            // locale explicitly. @see UserDictionaryList.getUserDictionaryLocalesSet().
            if (localeList.size() == 1) {
                final String locale = (String)localeList.toArray()[0];
                userDictionaryPreference.getExtras().putString("locale", locale);
            }
        } else {
            userDictionaryPreference.setFragment(UserDictionaryList.class.getName());
        }
    }
}
