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

package com.android.inputmethod.latin.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.android.inputmethod.latin.settings.Settings;

public final class StatsUtils {
    private static final String TAG = StatsUtils.class.getSimpleName();
    private static final StatsUtils sInstance = new StatsUtils();

    public static void onCreateCompleted(final Context context) {
        sInstance.onCreateCompletedInternal(context);
    }

    private void onCreateCompletedInternal(final Context context) {
        mContext = context;
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        final Boolean usePersonalizedDict =
                prefs.getBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true);
        Log.d(TAG, "onCreateCompleted. context: " + context.toString() + "usePersonalizedDict: "
                + usePersonalizedDict);
    }

    public static void onDestroy() {
        sInstance.onDestroyInternal();
    }

    private void onDestroyInternal() {
        Log.d(TAG, "onDestroy. context: " + mContext.toString());
        mContext = null;
    }

    private Context mContext;
}
