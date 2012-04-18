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
import android.os.IBinder;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.latin.ImfUtils;

import java.lang.reflect.Method;

// TODO: Override this class with the concrete implementation if we need to take care of the
// performance.
public class InputMethodManagerCompatWrapper {
    private static final String TAG = InputMethodManagerCompatWrapper.class.getSimpleName();
    private static final Method METHOD_switchToNextInputMethod = CompatUtils.getMethod(
            InputMethodManager.class, "switchToNextInputMethod", IBinder.class, Boolean.TYPE);

    private static final InputMethodManagerCompatWrapper sInstance =
            new InputMethodManagerCompatWrapper();

    private InputMethodManager mImm;

    private InputMethodManagerCompatWrapper() {
        // This wrapper class is not publicly instantiable.
    }

    public static InputMethodManagerCompatWrapper getInstance() {
        if (sInstance.mImm == null) {
            throw new RuntimeException(TAG + ".getInstance() is called before initialization");
        }
        return sInstance;
    }

    public static void init(Context context) {
        sInstance.mImm = ImfUtils.getInputMethodManager(context);
    }

    public InputMethodSubtype getLastInputMethodSubtype() {
        return mImm.getLastInputMethodSubtype();
    }

    public boolean switchToLastInputMethod(IBinder token) {
        return mImm.switchToLastInputMethod(token);
    }

    public boolean switchToNextInputMethod(IBinder token, boolean onlyCurrentIme) {
        return (Boolean)CompatUtils.invoke(mImm, false, METHOD_switchToNextInputMethod, token,
                onlyCurrentIme);
    }

    public void showInputMethodPicker() {
        mImm.showInputMethodPicker();
    }
}
