/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.latin;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Process;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceFragment;
import android.util.Log;

import com.android.inputmethod.keyboard.KeyboardSwitcher;

public class DebugSettings extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = DebugSettings.class.getSimpleName();
    private static final String DEBUG_MODE_KEY = "debug_mode";
    public static final String FORCE_NON_DISTINCT_MULTITOUCH_KEY = "force_non_distinct_multitouch";

    private boolean mServiceNeedsRestart = false;
    private CheckBoxPreference mDebugMode;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_for_debug);
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        prefs.registerOnSharedPreferenceChangeListener(this);

        mServiceNeedsRestart = false;
        mDebugMode = (CheckBoxPreference) findPreference(DEBUG_MODE_KEY);
        updateDebugMode();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mServiceNeedsRestart) Process.killProcess(Process.myPid());
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.equals(DEBUG_MODE_KEY)) {
            if (mDebugMode != null) {
                mDebugMode.setChecked(prefs.getBoolean(DEBUG_MODE_KEY, false));
                updateDebugMode();
                mServiceNeedsRestart = true;
            }
        } else if (key.equals(FORCE_NON_DISTINCT_MULTITOUCH_KEY)
                || key.equals(KeyboardSwitcher.PREF_KEYBOARD_LAYOUT)) {
            mServiceNeedsRestart = true;
        }
    }

    private void updateDebugMode() {
        if (mDebugMode == null) {
            return;
        }
        boolean isDebugMode = mDebugMode.isChecked();
        String version = "";
        try {
            final Context context = getActivity();
            final String packageName = context.getPackageName();
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            version = "Version " + info.versionName;
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Could not find version info.");
        }
        if (!isDebugMode) {
            mDebugMode.setTitle(version);
            mDebugMode.setSummary("");
        } else {
            mDebugMode.setTitle(getResources().getString(R.string.prefs_debug_mode));
            mDebugMode.setSummary(version);
        }
    }
}
