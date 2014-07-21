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

import android.util.Log;
import android.view.inputmethod.InputConnection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class InputConnectionCompatUtils {
    private static final String TAG = InputConnectionCompatUtils.class.getSimpleName();

    // Note that CursorAnchorInfoRequest is supposed to be available in API level 21 and later.
    private static Class<?> getCursorAnchorInfoRequestClass() {
        try {
            return Class.forName("android.view.inputmethod.CursorAnchorInfoRequest");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static final Class<?> TYPE_CursorAnchorInfoRequest;
    private static final Constructor<?> CONSTRUCTOR_CursorAnchorInfoRequest;
    private static final Method METHOD_requestCursorAnchorInfo;
    static {
        TYPE_CursorAnchorInfoRequest = getCursorAnchorInfoRequestClass();
        CONSTRUCTOR_CursorAnchorInfoRequest = CompatUtils.getConstructor(
                TYPE_CursorAnchorInfoRequest, int.class, int.class);
        METHOD_requestCursorAnchorInfo = CompatUtils.getMethod(InputConnection.class,
                "requestCursorAnchorInfo", TYPE_CursorAnchorInfoRequest);
    }

    public static boolean isRequestCursorAnchorInfoAvailable() {
        return METHOD_requestCursorAnchorInfo != null &&
                CONSTRUCTOR_CursorAnchorInfoRequest != null;
    }

    /**
     * Local copies of some constants in CursorAnchorInfoRequest until the SDK becomes publicly
     * available.
     */
    private final static int RESULT_NOT_HANDLED = 0;
    private final static int RESULT_SCHEDULED = 1;
    private final static int TYPE_CURSOR_ANCHOR_INFO = 1;
    private final static int FLAG_CURSOR_ANCHOR_INFO_MONITOR = 1;
    private final static int FLAG_CURSOR_ANCHOR_INFO_IMMEDIATE = 2;
    private final static int TYPE_CURSOR_RECT = 2;
    private final static int FLAG_CURSOR_RECT_MONITOR = 1;
    private final static int FLAG_CURSOR_RECT_IN_SCREEN_COORDINATES = 2;
    private final static int FLAG_CURSOR_RECT_WITH_VIEW_MATRIX = 4;

    private static int requestCursorAnchorInfoImpl(final InputConnection inputConnection,
            final int type, final int flags) {
        if (!isRequestCursorAnchorInfoAvailable()) {
             return RESULT_NOT_HANDLED;
        }
        final Object requestObject = CompatUtils.newInstance(
                CONSTRUCTOR_CursorAnchorInfoRequest, type, flags);
        if (requestObject == null) {
            return RESULT_NOT_HANDLED;
        }
        return (Integer) CompatUtils.invoke(inputConnection,
                RESULT_NOT_HANDLED /* defaultValue */,
                METHOD_requestCursorAnchorInfo, requestObject);
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
    public static boolean requestCursorAnchorInfo(final InputConnection inputConnection,
            final boolean enableMonitor, final boolean requestImmediateCallback) {
        final int requestFlags = (enableMonitor ? FLAG_CURSOR_ANCHOR_INFO_MONITOR : 0)
                | (requestImmediateCallback ? FLAG_CURSOR_ANCHOR_INFO_IMMEDIATE : 0);
        final int requestResult = requestCursorAnchorInfoImpl(inputConnection,
                TYPE_CURSOR_ANCHOR_INFO, requestFlags);
        switch (requestResult) {
            case RESULT_NOT_HANDLED:
                return false;
            case RESULT_SCHEDULED:
                return true;
            default:
                Log.w(TAG, "requestCursorAnchorInfo returned unknown result=" + requestResult
                        + " for type=TYPE_CURSOR_ANCHOR_INFO flags=" + requestFlags);
                return true;
        }
    }

    /**
     * Requests the editor to call back {@link InputMethodManager#updateCursor}.
     * @param inputConnection the input connection to which the request is to be sent.
     * @param enableMonitor {@code true} to request the editor to call back the method whenever the
     * cursor position is changed.
     * @return {@code false} if the request is not handled. Otherwise returns {@code true}.
     */
    public static boolean requestCursorRect(final InputConnection inputConnection,
            final boolean enableMonitor) {
        final int requestFlags = enableMonitor ?
                FLAG_CURSOR_RECT_MONITOR | FLAG_CURSOR_RECT_IN_SCREEN_COORDINATES |
                FLAG_CURSOR_RECT_WITH_VIEW_MATRIX : 0;
        final int requestResult = requestCursorAnchorInfoImpl(inputConnection, TYPE_CURSOR_RECT,
                requestFlags);
        switch (requestResult) {
            case RESULT_NOT_HANDLED:
                return false;
            case RESULT_SCHEDULED:
                return true;
            default:
                Log.w(TAG, "requestCursorAnchorInfo returned unknown result=" + requestResult
                        + " for type=TYPE_CURSOR_RECT flags=" + requestFlags);
                return true;
        }
    }
}
