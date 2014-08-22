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

package com.android.inputmethod.compat;

import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

public final class InputConnectionCompatUtils {
    private static final CompatUtils.ClassWrapper sInputConnectionType;
    private static final CompatUtils.ToBooleanMethodWrapper sRequestUpdateCursorAnchorInfoMethod;
    static {
        sInputConnectionType = new CompatUtils.ClassWrapper(InputConnection.class);
        sRequestUpdateCursorAnchorInfoMethod = sInputConnectionType.getPrimitiveMethod(
                "requestUpdateCursorAnchorInfo", false, int.class);
    }

    public static boolean isRequestUpdateCursorAnchorInfoAvailable() {
        return sRequestUpdateCursorAnchorInfoMethod != null;
    }

    /**
     * Local copies of some constants in CursorAnchorInfoRequest until the SDK becomes publicly
     * available.
     */
    private static int REQUEST_UPDATE_CURSOR_UPDATE_IMMEDIATE = 1 << 0;
    private static int REQUEST_UPDATE_CURSOR_UPDATE_MONITOR = 1 << 1;

    private static boolean requestUpdateCursorAnchorInfoImpl(final InputConnection inputConnection,
            final int cursorUpdateMode) {
        if (!isRequestUpdateCursorAnchorInfoAvailable()) {
             return false;
        }
        return sRequestUpdateCursorAnchorInfoMethod.invoke(inputConnection, cursorUpdateMode);
    }

    /**
     * Requests the editor to call back {@link InputMethodManager#updateCursorAnchorInfo}.
     * @param inputConnection the input connection to which the request is to be sent.
     * @param enableMonitor {@code true} to request the editor to call back the method whenever the
     * cursor/anchor position is changed.
     * @param requestImmediateCallback {@code true} to request the editor to call back the method
     * as soon as possible to notify the current cursor/anchor position to the input method.
     * @return {@code false} if the request is not handled. Otherwise returns {@code true}.
     */
    public static boolean requestUpdateCursorAnchorInfo(final InputConnection inputConnection,
            final boolean enableMonitor, final boolean requestImmediateCallback) {
        final int cursorUpdateMode = (enableMonitor ? REQUEST_UPDATE_CURSOR_UPDATE_MONITOR : 0)
                | (requestImmediateCallback ? REQUEST_UPDATE_CURSOR_UPDATE_IMMEDIATE : 0);
        return requestUpdateCursorAnchorInfoImpl(inputConnection, cursorUpdateMode);
    }

}
