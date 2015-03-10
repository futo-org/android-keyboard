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

import com.android.inputmethod.latin.DictionaryFacilitator;
import com.android.inputmethod.latin.settings.SettingsValues;

@SuppressWarnings("unused")
public class StatsUtilsManager {

    private static final StatsUtilsManager sInstance = new StatsUtilsManager();
    private static StatsUtilsManager sTestInstance = null;

    /**
     * @return the singleton instance of {@link StatsUtilsManager}.
     */
    public static StatsUtilsManager getInstance() {
        return sTestInstance != null ? sTestInstance : sInstance;
    }

    public static void setTestInstance(final StatsUtilsManager testInstance) {
        sTestInstance = testInstance;
    }

    public void onCreate(final Context context, final DictionaryFacilitator dictionaryFacilitator) {
    }

    public void onLoadSettings(final Context context, final SettingsValues settingsValues) {
    }

    public void onStartInputView() {
    }

    public void onFinishInputView() {
    }

    public void onDestroy(final Context context) {
    }
}
