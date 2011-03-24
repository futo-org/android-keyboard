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

import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.lang.reflect.Field;

public class EditorInfoCompatUtils {
    private static final Field FIELD_IME_FLAG_NAVIGATE_NEXT = CompatUtils.getField(
            EditorInfo.class, "IME_FLAG_NAVIGATE_NEXT");
    private static final Field FIELD_IME_FLAG_NAVIGATE_PREVIOUS = CompatUtils.getField(
            EditorInfo.class, "IME_FLAG_NAVIGATE_PREVIOUS");
    private static final Field FIELD_IME_ACTION_PREVIOUS = CompatUtils.getField(
            EditorInfo.class, "IME_FLAG_ACTION_PREVIOUS");
    private static final Integer OBJ_IME_FLAG_NAVIGATE_NEXT = (Integer) CompatUtils
            .getFieldValue(null, null, FIELD_IME_FLAG_NAVIGATE_NEXT);
    private static final Integer OBJ_IME_FLAG_NAVIGATE_PREVIOUS = (Integer) CompatUtils
            .getFieldValue(null, null, FIELD_IME_FLAG_NAVIGATE_PREVIOUS);
    private static final Integer OBJ_IME_ACTION_PREVIOUS = (Integer) CompatUtils
            .getFieldValue(null, null, FIELD_IME_ACTION_PREVIOUS);

    public static boolean hasFlagNavigateNext(int imeOptions) {
        if (OBJ_IME_FLAG_NAVIGATE_NEXT == null)
            return false;
        return (imeOptions & OBJ_IME_FLAG_NAVIGATE_NEXT) != 0;
    }

    public static boolean hasFlagNavigatePrevious(int imeOptions) {
        if (OBJ_IME_FLAG_NAVIGATE_PREVIOUS == null)
            return false;
        return (imeOptions & OBJ_IME_FLAG_NAVIGATE_PREVIOUS) != 0;
    }

    public static void performEditorActionNext(InputConnection ic) {
        ic.performEditorAction(EditorInfo.IME_ACTION_NEXT);
    }

    public static void performEditorActionPrevious(InputConnection ic) {
        if (OBJ_IME_ACTION_PREVIOUS == null)
            return;
        ic.performEditorAction(OBJ_IME_ACTION_PREVIOUS);
    }
}
