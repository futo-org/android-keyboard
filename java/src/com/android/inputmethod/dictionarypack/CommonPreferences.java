/**
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.android.inputmethod.dictionarypack;

import android.content.Context;
import android.content.SharedPreferences;

public final class CommonPreferences {
    private static final String COMMON_PREFERENCES_NAME = "LatinImeDictPrefs";

    public static SharedPreferences getCommonPreferences(final Context context) {
        return context.getSharedPreferences(COMMON_PREFERENCES_NAME, 0);
    }

    public static void enable(final SharedPreferences pref, final String id) {
        final SharedPreferences.Editor editor = pref.edit();
        editor.putBoolean(id, true);
        editor.apply();
    }

    public static void disable(final SharedPreferences pref, final String id) {
        final SharedPreferences.Editor editor = pref.edit();
        editor.putBoolean(id, false);
        editor.apply();
    }
}
