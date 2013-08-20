/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Process;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;

import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.debug.ExternalDictionaryGetterForDebug;
import com.android.inputmethod.latin.utils.ApplicationUtils;

public final class DebugSettings extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String PREF_DEBUG_MODE = "debug_mode";
    public static final String PREF_FORCE_NON_DISTINCT_MULTITOUCH = "force_non_distinct_multitouch";
    public static final String PREF_USABILITY_STUDY_MODE = "usability_study_mode";
    public static final String PREF_STATISTICS_LOGGING = "enable_logging";
    public static final String PREF_USE_ONLY_PERSONALIZATION_DICTIONARY_FOR_DEBUG =
            "use_only_personalization_dictionary_for_debug";
    public static final String PREF_BOOST_PERSONALIZATION_DICTIONARY_FOR_DEBUG =
            "boost_personalization_dictionary_for_debug";
    private static final String PREF_READ_EXTERNAL_DICTIONARY = "read_external_dictionary";
    private static final boolean SHOW_STATISTICS_LOGGING = false;

    private boolean mServiceNeedsRestart = false;
    private CheckBoxPreference mDebugMode;
    private CheckBoxPreference mStatisticsLoggingPref;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_for_debug);
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        prefs.registerOnSharedPreferenceChangeListener(this);

        final Preference usabilityStudyPref = findPreference(PREF_USABILITY_STUDY_MODE);
        if (usabilityStudyPref instanceof CheckBoxPreference) {
            final CheckBoxPreference checkbox = (CheckBoxPreference)usabilityStudyPref;
            checkbox.setChecked(prefs.getBoolean(PREF_USABILITY_STUDY_MODE,
                    LatinImeLogger.getUsabilityStudyMode(prefs)));
            checkbox.setSummary(R.string.settings_warning_researcher_mode);
        }
        final Preference statisticsLoggingPref = findPreference(PREF_STATISTICS_LOGGING);
        if (statisticsLoggingPref instanceof CheckBoxPreference) {
            mStatisticsLoggingPref = (CheckBoxPreference) statisticsLoggingPref;
            if (!SHOW_STATISTICS_LOGGING) {
                getPreferenceScreen().removePreference(statisticsLoggingPref);
            }
        }

        final PreferenceScreen readExternalDictionary =
                (PreferenceScreen) findPreference(PREF_READ_EXTERNAL_DICTIONARY);
        if (null != readExternalDictionary) {
            readExternalDictionary.setOnPreferenceClickListener(
                    new Preference.OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(final Preference arg0) {
                            ExternalDictionaryGetterForDebug.chooseAndInstallDictionary(
                                    getActivity());
                            mServiceNeedsRestart = true;
                            return true;
                        }
                    });
        }

        mServiceNeedsRestart = false;
        mDebugMode = (CheckBoxPreference) findPreference(PREF_DEBUG_MODE);
        updateDebugMode();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mServiceNeedsRestart) Process.killProcess(Process.myPid());
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.equals(PREF_DEBUG_MODE)) {
            if (mDebugMode != null) {
                mDebugMode.setChecked(prefs.getBoolean(PREF_DEBUG_MODE, false));
                final boolean checked = mDebugMode.isChecked();
                if (mStatisticsLoggingPref != null) {
                    if (checked) {
                        getPreferenceScreen().addPreference(mStatisticsLoggingPref);
                    } else {
                        getPreferenceScreen().removePreference(mStatisticsLoggingPref);
                    }
                }
                updateDebugMode();
                mServiceNeedsRestart = true;
            }
        } else if (key.equals(PREF_FORCE_NON_DISTINCT_MULTITOUCH)
                || key.equals(KeyboardSwitcher.PREF_KEYBOARD_LAYOUT)) {
            mServiceNeedsRestart = true;
        } else if (key.equals(PREF_USE_ONLY_PERSONALIZATION_DICTIONARY_FOR_DEBUG)) {
            mServiceNeedsRestart = true;
        }
    }

    private void updateDebugMode() {
        if (mDebugMode == null) {
            return;
        }
        boolean isDebugMode = mDebugMode.isChecked();
        final String version = getResources().getString(
                R.string.version_text, ApplicationUtils.getVersionName(getActivity()));
        if (!isDebugMode) {
            mDebugMode.setTitle(version);
            mDebugMode.setSummary("");
        } else {
            mDebugMode.setTitle(getResources().getString(R.string.prefs_debug_mode));
            mDebugMode.setSummary(version);
        }
    }
}
