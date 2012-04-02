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

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO: Override this class with the concrete implementation if we need to take care of the
// performance.
public class InputMethodManagerCompatWrapper {
    private static final String TAG = InputMethodManagerCompatWrapper.class.getSimpleName();
    private static final Method METHOD_getCurrentInputMethodSubtype =
            CompatUtils.getMethod(InputMethodManager.class, "getCurrentInputMethodSubtype");
    private static final Method METHOD_getLastInputMethodSubtype =
            CompatUtils.getMethod(InputMethodManager.class, "getLastInputMethodSubtype");
    private static final Method METHOD_getEnabledInputMethodSubtypeList =
            CompatUtils.getMethod(InputMethodManager.class, "getEnabledInputMethodSubtypeList",
                    InputMethodInfo.class, boolean.class);
    private static final Method METHOD_getShortcutInputMethodsAndSubtypes =
            CompatUtils.getMethod(InputMethodManager.class, "getShortcutInputMethodsAndSubtypes");
    private static final Method METHOD_setInputMethodAndSubtype =
            CompatUtils.getMethod(
                    InputMethodManager.class, "setInputMethodAndSubtype", IBinder.class,
                    String.class, InputMethodSubtypeCompatWrapper.CLASS_InputMethodSubtype);
    private static final Method METHOD_switchToLastInputMethod = CompatUtils.getMethod(
            InputMethodManager.class, "switchToLastInputMethod", IBinder.class);
    private static final Method METHOD_switchToNextInputMethod = CompatUtils.getMethod(
            InputMethodManager.class, "switchToNextInputMethod", IBinder.class, Boolean.TYPE);

    private static final InputMethodManagerCompatWrapper sInstance =
            new InputMethodManagerCompatWrapper();

    private InputMethodManager mImm;

    public static InputMethodManagerCompatWrapper getInstance() {
        if (sInstance.mImm == null)
            Log.w(TAG, "getInstance() is called before initialization");
        return sInstance;
    }

    public static void init(InputMethodServiceCompatWrapper service) {
        sInstance.mImm = (InputMethodManager) service.getSystemService(
                Context.INPUT_METHOD_SERVICE);
    }

    public InputMethodSubtypeCompatWrapper getCurrentInputMethodSubtype() {
        Object o = CompatUtils.invoke(mImm, null, METHOD_getCurrentInputMethodSubtype);
        return new InputMethodSubtypeCompatWrapper(o);
    }

    public InputMethodSubtypeCompatWrapper getLastInputMethodSubtype() {
        Object o = CompatUtils.invoke(mImm, null, METHOD_getLastInputMethodSubtype);
        return new InputMethodSubtypeCompatWrapper(o);
    }

    public List<InputMethodSubtypeCompatWrapper> getEnabledInputMethodSubtypeList(
            InputMethodInfo imi, boolean allowsImplicitlySelectedSubtypes) {
        Object retval = CompatUtils.invoke(mImm, null, METHOD_getEnabledInputMethodSubtypeList,
                imi, allowsImplicitlySelectedSubtypes);
        if (retval == null || !(retval instanceof List<?>) || ((List<?>)retval).isEmpty()) {
            // Returns an empty list
            return Collections.emptyList();
        }
        return CompatUtils.copyInputMethodSubtypeListToWrapper(retval);
    }

    public Map<InputMethodInfo, List<InputMethodSubtypeCompatWrapper>>
            getShortcutInputMethodsAndSubtypes() {
        Object retval = CompatUtils.invoke(mImm, null, METHOD_getShortcutInputMethodsAndSubtypes);
        if (retval == null || !(retval instanceof Map<?, ?>) || ((Map<?, ?>)retval).isEmpty()) {
            // Returns an empty map
            return Collections.emptyMap();
        }
        Map<InputMethodInfo, List<InputMethodSubtypeCompatWrapper>> shortcutMap =
                new HashMap<InputMethodInfo, List<InputMethodSubtypeCompatWrapper>>();
        final Map<?, ?> retvalMap = (Map<?, ?>)retval;
        for (Object key : retvalMap.keySet()) {
            if (!(key instanceof InputMethodInfo)) {
                Log.e(TAG, "Class type error.");
                return null;
            }
            shortcutMap.put((InputMethodInfo)key,
                    CompatUtils.copyInputMethodSubtypeListToWrapper(retvalMap.get(key)));
        }
        return shortcutMap;
    }

    // We don't call this method when we switch between subtypes within this IME.
    public void setInputMethodAndSubtype(
            IBinder token, String id, InputMethodSubtypeCompatWrapper subtype) {
        // TODO: Support subtype change on non-subtype-supported platform.
        if (subtype != null && subtype.hasOriginalObject()) {
            CompatUtils.invoke(mImm, null, METHOD_setInputMethodAndSubtype,
                    token, id, subtype.getOriginalObject());
        } else {
            mImm.setInputMethod(token, id);
        }
    }

    public boolean switchToLastInputMethod(IBinder token) {
        return (Boolean)CompatUtils.invoke(mImm, false, METHOD_switchToLastInputMethod, token);
    }

    public boolean switchToNextInputMethod(IBinder token, boolean onlyCurrentIme) {
        return (Boolean)CompatUtils.invoke(mImm, false, METHOD_switchToNextInputMethod, token,
                onlyCurrentIme);
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
