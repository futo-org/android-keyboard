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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;

import com.android.inputmethod.dictionarypack.DictionarySettingsActivity;
import com.android.inputmethod.latin.R;

/**
 * "Text correction" settings sub screen.
 *
 * This settings sub screen handles the following text correction preferences.
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
    private static final boolean USE_INTERNAL_PERSONAL_DICTIONARY_SETTINGS =
            DBG_USE_INTERNAL_PERSONAL_DICTIONARY_SETTINGS
            || Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR2;

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_correction);

        final Context context = getActivity();
        final PackageManager pm = context.getPackageManager();

        final Preference dictionaryLink = findPreference(Settings.PREF_CONFIGURE_DICTIONARIES_KEY);
        final Intent intent = dictionaryLink.getIntent();
        intent.setClassName(context.getPackageName(), DictionarySettingsActivity.class.getName());
        final int number = pm.queryIntentActivities(intent, 0).size();
        if (0 >= number) {
            removePreference(Settings.PREF_CONFIGURE_DICTIONARIES_KEY);
        }
    }
}
