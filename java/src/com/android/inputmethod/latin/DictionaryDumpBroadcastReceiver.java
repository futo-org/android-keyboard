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

package com.android.inputmethod.latin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class DictionaryDumpBroadcastReceiver extends BroadcastReceiver {
  private static final String TAG = DictionaryDumpBroadcastReceiver.class.getSimpleName();

    private static final String DOMAIN = "com.android.inputmethod.latin";
    public static final String DICTIONARY_DUMP_INTENT_ACTION = DOMAIN + ".DICT_DUMP";
    public static final String DICTIONARY_NAME_KEY = "dictName";

    final LatinIME mLatinIme;

    public DictionaryDumpBroadcastReceiver(final LatinIME latinIme) {
        mLatinIme = latinIme;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.equals(DICTIONARY_DUMP_INTENT_ACTION)) {
            final String dictName = intent.getStringExtra(DICTIONARY_NAME_KEY);
            if (dictName == null) {
                Log.e(TAG, "Received dictionary dump intent action " +
                      "but the dictionary name is not set.");
                return;
            }
            mLatinIme.dumpDictionaryForDebug(dictName);
        }
    }
}
