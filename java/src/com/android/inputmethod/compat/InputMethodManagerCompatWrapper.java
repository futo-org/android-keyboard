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
import java.util.ArrayList;
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
    private static final Method METHOD_getEnabledInputMethodSubtypeList =
            CompatUtils.getMethod(InputMethodManager.class, "getEnabledInputMethodSubtypeList",
                    InputMethodInfo.class, boolean.class);
    private static final Method METHOD_getShortcutInputMethodsAndSubtypes =
            CompatUtils.getMethod(InputMethodManager.class, "getShortcutInputMethodsAndSubtypes");
    private static final Method METHOD_setInputMethodAndSubtype =
            CompatUtils.getMethod(
                    InputMethodManager.class, "setInputMethodAndSubtype", IBinder.class,
                    String.class, InputMethodSubtypeCompatWrapper.CLASS_InputMethodSubtype);

    private static final InputMethodManagerCompatWrapper sInstance =
            new InputMethodManagerCompatWrapper();

    private InputMethodManager mImm;
    private InputMethodManagerCompatWrapper() {
    }

    public static InputMethodManagerCompatWrapper getInstance(Context context) {
        if (sInstance.mImm == null) {
            sInstance.init(context);
        }
        return sInstance;
    }

    private synchronized void init(Context context) {
        mImm = (InputMethodManager) context.getSystemService(
                Context.INPUT_METHOD_SERVICE);
    }

    public InputMethodSubtypeCompatWrapper getCurrentInputMethodSubtype() {
        return new InputMethodSubtypeCompatWrapper(
                CompatUtils.invoke(mImm, null, METHOD_getCurrentInputMethodSubtype));
    }

    public List<InputMethodSubtypeCompatWrapper> getEnabledInputMethodSubtypeList(
            InputMethodInfoCompatWrapper imi, boolean allowsImplicitlySelectedSubtypes) {
        Object retval = CompatUtils.invoke(mImm, null, METHOD_getEnabledInputMethodSubtypeList,
                (imi != null ? imi.getInputMethodInfo() : null), allowsImplicitlySelectedSubtypes);
        return CompatUtils.copyInputMethodSubtypeListToWrapper((List<?>)retval);
    }

    public Map<InputMethodInfoCompatWrapper, List<InputMethodSubtypeCompatWrapper>>
            getShortcutInputMethodsAndSubtypes() {
        Object retval = CompatUtils.invoke(mImm, null, METHOD_getShortcutInputMethodsAndSubtypes);
        // Returns an empty map
        if (!(retval instanceof Map)) return Collections.emptyMap();
        Map<InputMethodInfoCompatWrapper, List<InputMethodSubtypeCompatWrapper>> shortcutMap =
                new HashMap<InputMethodInfoCompatWrapper, List<InputMethodSubtypeCompatWrapper>>();
        final Map<?, ?> retvalMap = (Map<?, ?>)retval;
        for (Object key : retvalMap.keySet()) {
            if (!(key instanceof InputMethodInfo)) {
                Log.e(TAG, "Class type error.");
                return null;
            }
            shortcutMap.put(new InputMethodInfoCompatWrapper((InputMethodInfo)key),
                    CompatUtils.copyInputMethodSubtypeListToWrapper(retvalMap.get(key)));
        }
        return shortcutMap;
    }

    public void setInputMethodAndSubtype(
            IBinder token, String id, InputMethodSubtypeCompatWrapper subtype) {
        CompatUtils.invoke(mImm, null, METHOD_setInputMethodAndSubtype,
                token, id, subtype.getOriginalObject());
    }

    public boolean switchToLastInputMethod(IBinder token) {
        return false;
    }

    public List<InputMethodInfoCompatWrapper> getEnabledInputMethodList() {
        if (mImm == null) return null;
        List<InputMethodInfoCompatWrapper> imis = new ArrayList<InputMethodInfoCompatWrapper>();
        for (InputMethodInfo imi : mImm.getEnabledInputMethodList()) {
            imis.add(new InputMethodInfoCompatWrapper(imi));
        }
        return imis;
    }

    public void showInputMethodPicker() {
        if (mImm == null) return;
        mImm.showInputMethodPicker();
    }
}
