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

package com.android.inputmethod.compat;

import android.inputmethodservice.InputMethodService;

import java.lang.reflect.Method;

public final class InputMethodServiceCompatUtils {
    // Note that {@link InputMethodService#enableHardwareAcceleration} has been introduced
    // in API level 17 (Build.VERSION_CODES.JELLY_BEAN_MR1).
    private static final Method METHOD_enableHardwareAcceleration =
            CompatUtils.getMethod(InputMethodService.class, "enableHardwareAcceleration");

    private InputMethodServiceCompatUtils() {
        // This utility class is not publicly instantiable.
    }

    public static boolean enableHardwareAcceleration(final InputMethodService ims) {
        return (Boolean)CompatUtils.invoke(ims, false /* defaultValue */,
                METHOD_enableHardwareAcceleration);
    }
}
