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
import com.android.inputmethod.latin.define.ProductionFlag;

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

    public static void setCursorAnchorMonitorMode(final InputMethodService ims, final int mode) {
        if (ProductionFlag.USES_CURSOR_ANCHOR_MONITOR) {
            ExperimentalAPIUtils.setCursorAnchorMonitorMode(ims, mode);
        }
    }

    /*
     * For unreleased APIs. ProGuard will strip this class entirely, unless used explicitly.
     */
    private static final class ExperimentalAPIUtils {
        // Note that {@link InputMethodManager#setCursorAnchorMonitorMode} is not yet available as
        // an official API as of API level 19 (Build.VERSION_CODES.KITKAT).
        private static final Method METHOD_setCursorAnchorMonitorMode = CompatUtils.getMethod(
                InputMethodService.class, "setCursorAnchorMonitorMode", int.class);

        private ExperimentalAPIUtils() {
            // This utility class is not publicly instantiable.
        }

        public static void setCursorAnchorMonitorMode(final InputMethodService ims,
                final int mode) {
            CompatUtils.invoke(ims, null /* defaultValue */,
                    METHOD_setCursorAnchorMonitorMode, mode);
        }
    }
}
