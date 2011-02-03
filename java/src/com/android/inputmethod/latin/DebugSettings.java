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

import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Process;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.util.Log;

public class DebugSettings extends PreferenceActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "DebugSettings";
    private static final String DEBUG_MODE_KEY = "debug_mode";

    private boolean mServiceNeedsRestart = false;
    private CheckBoxPreference mDebugMode;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_for_debug);
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        prefs.registerOnSharedPreferenceChangeListener(this);

        mServiceNeedsRestart = false;
        mDebugMode = (CheckBoxPreference) findPreference(DEBUG_MODE_KEY);
        updateDebugMode();
    }

    @Override
    protected void onStop() {
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
        }
    }

    private void updateDebugMode() {
        if (mDebugMode == null) {
            return;
        }
        boolean isDebugMode = mDebugMode.isChecked();
        String version = "";
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
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
