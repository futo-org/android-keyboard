/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.inputmethod.latin.makedict;

import android.util.Log;

/**
 * Wrapper to redirect log events to the right output medium.
 */
public final class MakedictLog {
    public static final boolean DBG = false;
    private static final String TAG = MakedictLog.class.getSimpleName();

    public static void d(String message) {
        if (DBG) {
            Log.d(TAG, message);
        }
    }

    public static void i(String message) {
        if (DBG) {
            Log.i(TAG, message);
        }
    }

    public static void w(String message) {
        Log.w(TAG, message);
    }

    public static void e(String message) {
        Log.e(TAG, message);
    }
}
