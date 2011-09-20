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

package com.android.inputmethod.compat;

import android.content.Context;
import android.os.Vibrator;

import java.lang.reflect.Method;

public class VibratorCompatWrapper {
    private static final Method METHOD_hasVibrator = CompatUtils.getMethod(Vibrator.class,
            "hasVibrator");

    private static final VibratorCompatWrapper sInstance = new VibratorCompatWrapper();
    private Vibrator mVibrator;

    private VibratorCompatWrapper() {
    }

    public static VibratorCompatWrapper getInstance(Context context) {
        if (sInstance.mVibrator == null) {
            sInstance.mVibrator =
                    (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
        return sInstance;
    }

    public boolean hasVibrator() {
        if (mVibrator == null)
            return false;
        return (Boolean) CompatUtils.invoke(mVibrator, true, METHOD_hasVibrator);
    }

    public void vibrate(long milliseconds) {
        mVibrator.vibrate(milliseconds);
    }
}
