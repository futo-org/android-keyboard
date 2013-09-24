/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.app.ActivityManager;
import android.content.Context;

import java.lang.reflect.Method;

public class ActivityManagerCompatUtils {
    private static final Object LOCK = new Object();
    private static volatile Boolean sBoolean = null;
    private static final Method METHOD_isLowRamDevice = CompatUtils.getMethod(
            ActivityManager.class, "isLowRamDevice");

    private ActivityManagerCompatUtils() {
        // Do not instantiate this class.
    }

    public static boolean isLowRamDevice(Context context) {
        if (sBoolean == null) {
            synchronized(LOCK) {
                if (sBoolean == null) {
                    final ActivityManager am =
                            (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                    sBoolean = (Boolean)CompatUtils.invoke(am, false, METHOD_isLowRamDevice);
                }
            }
        }
        return sBoolean;
    }
}
