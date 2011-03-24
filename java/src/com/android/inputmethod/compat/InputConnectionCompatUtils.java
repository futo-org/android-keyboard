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

import android.util.Log;
import android.view.inputmethod.InputConnection;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class InputConnectionCompatUtils {
    private static final String TAG = InputConnectionCompatUtils.class.getSimpleName();
    private static final Class<?> CLASS_CorrectionInfo = CompatUtils
            .getClass("android.view.inputmethod.CorrectionInfo");
    private static final Class<?>[] INPUT_TYPE_CorrectionInfo = new Class<?>[] { int.class,
            CharSequence.class, CharSequence.class };
    private static final Constructor<?> CONSTRUCTOR_CorrectionInfo = CompatUtils
            .getConstructor(CLASS_CorrectionInfo, INPUT_TYPE_CorrectionInfo);
    private static final Method METHOD_InputConnection_commitCorrection = CompatUtils
            .getMethod(InputConnection.class, "commitCorrection", CLASS_CorrectionInfo);

    public static void commitCorrection(InputConnection ic, int offset, CharSequence oldText,
            CharSequence newText) {
        if (ic == null || CONSTRUCTOR_CorrectionInfo == null
                || METHOD_InputConnection_commitCorrection == null) {
            return;
        }
        Object[] args = { offset, oldText, newText };
        try {
            Object correctionInfo = CONSTRUCTOR_CorrectionInfo.newInstance(args);
            CompatUtils.invoke(ic, null, METHOD_InputConnection_commitCorrection,
                    correctionInfo);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Error in commitCorrection: IllegalArgumentException");
        } catch (InstantiationException e) {
            Log.e(TAG, "Error in commitCorrection: InstantiationException");
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Error in commitCorrection: IllegalAccessException");
        } catch (InvocationTargetException e) {
            Log.e(TAG, "Error in commitCorrection: InvocationTargetException");
        }
    }
}
