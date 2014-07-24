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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.utils.AdditionalSubtypeUtils;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.ArrayList;

/**
 * "Multi lingual options" settings sub screen.
 *
 * This settings sub screen handles the following input preferences.
 * - Language switch key
 * - Switch to other input methods
 * - Custom input styles
 */
public final class MultiLingualSettingsFragment extends SubScreenFragment {
    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_multi_lingual);

        final Context context = getActivity();

        // When we are called from the Settings application but we are not already running, some
        // singleton and utility classes may not have been initialized.  We have to call
        // initialization method of these classes here. See {@link LatinIME#onCreate()}.
        SubtypeLocaleUtils.init(context);

        if (!Settings.ENABLE_SHOW_LANGUAGE_SWITCH_KEY_SETTINGS) {
            removePreference(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY);
            removePreference(Settings.PREF_INCLUDE_OTHER_IMES_IN_LANGUAGE_SWITCH_LIST);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateCustomInputStylesSummary();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        // Nothing to do here.
    }

    private void updateCustomInputStylesSummary() {
        final SharedPreferences prefs = getSharedPreferences();
        final Resources res = getResources();
        final PreferenceScreen customInputStyles =
                (PreferenceScreen)findPreference(Settings.PREF_CUSTOM_INPUT_STYLES);
        final String prefSubtype = Settings.readPrefAdditionalSubtypes(prefs, res);
        final InputMethodSubtype[] subtypes =
                AdditionalSubtypeUtils.createAdditionalSubtypesArray(prefSubtype);
        final ArrayList<String> subtypeNames = new ArrayList<>();
        for (final InputMethodSubtype subtype : subtypes) {
            subtypeNames.add(SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(subtype));
        }
        // TODO: A delimiter of custom input styles should be localized.
        customInputStyles.setSummary(TextUtils.join(", ", subtypeNames));
    }
}
