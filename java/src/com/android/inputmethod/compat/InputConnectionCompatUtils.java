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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class InputConnectionCompatUtils {
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
     * A local copy of CursorAnchorInfoRequest.RESULT_NOT_HANDLED until the SDK becomes publicly
     * available.
     */
    private final static int CURSOR_ANCHOR_INFO_REQUEST_RESULT_NOT_HANDLED = 0;

    public static int requestCursorAnchorInfo(final InputConnection inputConnection,
            final int type, final int flags) {
        if (!isRequestCursorAnchorInfoAvailable()) {
             return CURSOR_ANCHOR_INFO_REQUEST_RESULT_NOT_HANDLED;
        }
        final Object requestObject = CompatUtils.newInstance(
                CONSTRUCTOR_CursorAnchorInfoRequest, type, flags);
        if (requestObject == null) {
            return CURSOR_ANCHOR_INFO_REQUEST_RESULT_NOT_HANDLED;
        }
        return (Integer) CompatUtils.invoke(inputConnection,
                CURSOR_ANCHOR_INFO_REQUEST_RESULT_NOT_HANDLED /* defaultValue */,
                METHOD_requestCursorAnchorInfo, requestObject);
    }
}
