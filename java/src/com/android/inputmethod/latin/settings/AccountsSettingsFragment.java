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
import android.preference.Preference;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SubtypeSwitcher;
import com.android.inputmethod.latin.define.ProductionFlags;

/**
 * "Accounts & Privacy" settings sub screen.
 *
 * This settings sub screen handles the following preferences:
 * - TODO: Account selection/management for IME
 * - TODO: Sync preferences
 * - TODO: Privacy preferences
 */
public final class AccountsSettingsFragment extends SubScreenFragment {
    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_accounts);

        final Resources res = getResources();
        final Context context = getActivity();

        // When we are called from the Settings application but we are not already running, some
        // singleton and utility classes may not have been initialized.  We have to call
        // initialization method of these classes here. See {@link LatinIME#onCreate()}.
        SubtypeSwitcher.init(context);

        if (ProductionFlags.IS_METRICS_LOGGING_SUPPORTED) {
            final Preference enableMetricsLogging =
                    findPreference(Settings.PREF_ENABLE_METRICS_LOGGING);
            if (enableMetricsLogging != null) {
                final String enableMetricsLoggingTitle = res.getString(
                        R.string.enable_metrics_logging, getApplicationName());
                enableMetricsLogging.setTitle(enableMetricsLoggingTitle);
            }
        } else {
            removePreference(Settings.PREF_ENABLE_METRICS_LOGGING);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAccountSelection();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        // TODO: Look at the preference that changed before refreshing the view.
        refreshAccountSelection();
    }

    private void refreshAccountSelection() {
        // TODO: Fetch the currently selected account.
        // Set the summary for the account preference.
        // Depending on the account selection, enable/disable preferences that
        // depend on an account.
    }
}
