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

import com.android.inputmethod.latin.EditingUtils.SelectedWord;

import android.view.inputmethod.InputConnection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class InputConnectionCompatUtils {
    private static final Class<?> CLASS_CorrectionInfo = CompatUtils
            .getClass("android.view.inputmethod.CorrectionInfo");
    private static final Class<?>[] INPUT_TYPE_CorrectionInfo = new Class<?>[] { int.class,
            CharSequence.class, CharSequence.class };
    private static final Constructor<?> CONSTRUCTOR_CorrectionInfo = CompatUtils
            .getConstructor(CLASS_CorrectionInfo, INPUT_TYPE_CorrectionInfo);
    private static final Method METHOD_InputConnection_commitCorrection = CompatUtils
            .getMethod(InputConnection.class, "commitCorrection", CLASS_CorrectionInfo);
    private static final Method METHOD_getSelectedText = CompatUtils
            .getMethod(InputConnection.class, "getSelectedText", int.class);
    private static final Method METHOD_setComposingRegion = CompatUtils
            .getMethod(InputConnection.class, "setComposingRegion", int.class, int.class);
    public static final boolean RECORRECTION_SUPPORTED;

    static {
        RECORRECTION_SUPPORTED = METHOD_getSelectedText != null
                && METHOD_setComposingRegion != null;
    }

    public static void commitCorrection(InputConnection ic, int offset, CharSequence oldText,
            CharSequence newText) {
        if (ic == null || CONSTRUCTOR_CorrectionInfo == null
                || METHOD_InputConnection_commitCorrection == null) {
            return;
        }
        Object[] args = { offset, oldText, newText };
        Object correctionInfo = CompatUtils.newInstance(CONSTRUCTOR_CorrectionInfo, args);
        if (correctionInfo != null) {
            CompatUtils.invoke(ic, null, METHOD_InputConnection_commitCorrection,
                    correctionInfo);
        }
    }


    /**
     * Returns the selected text between the selStart and selEnd positions.
     */
    public static CharSequence getSelectedText(InputConnection ic, int selStart, int selEnd) {
        // Use reflection, for backward compatibility
        return (CharSequence) CompatUtils.invoke(
                ic, null, METHOD_getSelectedText, 0);
    }

    /**
     * Tries to set the text into composition mode if there is support for it in the framework.
     */
    public static void underlineWord(InputConnection ic, SelectedWord word) {
        // Use reflection, for backward compatibility
        // If method not found, there's nothing we can do. It still works but just wont underline
        // the word.
        CompatUtils.invoke(
                ic, null, METHOD_setComposingRegion, word.mStart, word.mEnd);
    }
}
