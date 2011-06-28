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

package com.android.inputmethod.latin;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.latin.Dictionary.DataType;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

public class LatinImeLogger implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static boolean sDBG = false;
    public static boolean sVISUALDEBUG = false;

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    }

    public static void init(Context context, SharedPreferences prefs) {
    }

    public static void commit() {
    }

    public static void onDestroy() {
    }

    public static void logOnManualSuggestion(
            String before, String after, int position, List<CharSequence> suggestions) {
   }

    public static void logOnAutoCorrection(String before, String after, int separatorCode) {
    }

    public static void logOnAutoCorrectionCancelled() {
    }

    public static void logOnDelete() {
    }

    public static void logOnInputChar() {
    }

    public static void logOnInputSeparator() {
    }

    public static void logOnException(String metaData, Throwable e) {
    }

    public static void logOnWarning(String warning) {
    }

    public static void onStartSuggestion(CharSequence previousWords) {
    }

    public static void onAddSuggestedWord(String word, int typeId, DataType dataType) {
    }

    public static void onSetKeyboard(Keyboard kb) {
    }

    public static void onPrintAllUsabilityStudyLogs() {
    }
}
