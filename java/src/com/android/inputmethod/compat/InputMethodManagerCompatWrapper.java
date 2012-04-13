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
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

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
        if (sInstance.mImm == null)
            Log.w(TAG, "getInstance() is called before initialization");
        return sInstance;
    }

    public static void init(Context context) {
        sInstance.mImm = (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    public InputMethodSubtype getCurrentInputMethodSubtype() {
        return mImm.getCurrentInputMethodSubtype();
    }

    public InputMethodSubtype getLastInputMethodSubtype() {
        return mImm.getLastInputMethodSubtype();
    }

    public List<InputMethodSubtype> getEnabledInputMethodSubtypeList(
            InputMethodInfo imi, boolean allowsImplicitlySelectedSubtypes) {
        return mImm.getEnabledInputMethodSubtypeList(imi, allowsImplicitlySelectedSubtypes);
    }

    public Map<InputMethodInfo, List<InputMethodSubtype>> getShortcutInputMethodsAndSubtypes() {
        return mImm.getShortcutInputMethodsAndSubtypes();
    }

    // We don't call this method when we switch between subtypes within this IME.
    public void setInputMethodAndSubtype(IBinder token, String id, InputMethodSubtype subtype) {
        mImm.setInputMethodAndSubtype(token, id, subtype);
    }

    public boolean switchToLastInputMethod(IBinder token) {
        return mImm.switchToLastInputMethod(token);
    }

    public boolean switchToNextInputMethod(IBinder token, boolean onlyCurrentIme) {
        return (Boolean)CompatUtils.invoke(mImm, false, METHOD_switchToNextInputMethod, token,
                onlyCurrentIme);
    }

    public List<InputMethodInfo> getInputMethodList() {
        if (mImm == null) return null;
        return mImm.getInputMethodList();
    }

    public List<InputMethodInfo> getEnabledInputMethodList() {
        if (mImm == null) return null;
        return mImm.getEnabledInputMethodList();
    }

    public void showInputMethodPicker() {
        if (mImm == null) return;
        mImm.showInputMethodPicker();
    }
}
