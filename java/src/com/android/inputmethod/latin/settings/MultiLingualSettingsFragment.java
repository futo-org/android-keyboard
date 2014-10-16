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

import android.os.Bundle;

import com.android.inputmethod.latin.R;

import java.util.ArrayList;

/**
 * "Multilingual options" settings sub screen.
 *
 * This settings sub screen handles the following input preferences.
 * - Language switch key
 * - Switch to other input methods
 */
public final class MultiLingualSettingsFragment extends SubScreenFragment {
    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_multilingual);
        if (!Settings.ENABLE_SHOW_LANGUAGE_SWITCH_KEY_SETTINGS) {
            removePreference(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY);
            removePreference(Settings.PREF_INCLUDE_OTHER_IMES_IN_LANGUAGE_SWITCH_LIST);
        }
        AdditionalFeaturesSettingUtils.addAdditionalFeaturesPreferences(getActivity(), this);
    }
}
