/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.Context;
import android.os.Vibrator;

public class VibratorUtils {
    private static final VibratorUtils sInstance = new VibratorUtils();
    private Vibrator mVibrator;

    private VibratorUtils() {
        // This utility class is not publicly instantiable.
    }

    public static VibratorUtils getInstance(Context context) {
        if (sInstance.mVibrator == null) {
            sInstance.mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
        return sInstance;
    }

    public boolean hasVibrator() {
        if (mVibrator == null) {
            return false;
        }
        return mVibrator.hasVibrator();
    }

    public void vibrate(long milliseconds) {
        if (mVibrator == null) {
            return;
        }
        mVibrator.vibrate(milliseconds);
    }
}
