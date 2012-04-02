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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import com.android.inputmethod.latin.SubtypeSwitcher;
import com.android.inputmethod.latin.SubtypeUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

    // For the compatibility, IMM will create dummy subtypes if subtypes are not found.
    // This is required to be false if the current behavior is broken. For now, it's ok to be true.
    public static final boolean FORCE_ENABLE_VOICE_EVEN_WITH_NO_VOICE_SUBTYPES =
            !InputMethodServiceCompatWrapper.CAN_HANDLE_ON_CURRENT_INPUT_METHOD_SUBTYPE_CHANGED;
    private static final String VOICE_MODE = "voice";
    private static final String KEYBOARD_MODE = "keyboard";

    private InputMethodServiceCompatWrapper mService;
    private InputMethodManager mImm;
    private PackageManager mPackageManager;
    private ApplicationInfo mApplicationInfo;
    private String mLatinImePackageName;

    public static InputMethodManagerCompatWrapper getInstance() {
        if (sInstance.mImm == null)
            Log.w(TAG, "getInstance() is called before initialization");
        return sInstance;
    }

    public static void init(InputMethodServiceCompatWrapper service) {
        sInstance.mService = service;
        sInstance.mImm = (InputMethodManager) service.getSystemService(
                Context.INPUT_METHOD_SERVICE);
        sInstance.mLatinImePackageName = service.getPackageName();
        sInstance.mPackageManager = service.getPackageManager();
        sInstance.mApplicationInfo = service.getApplicationInfo();
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
            if (!FORCE_ENABLE_VOICE_EVEN_WITH_NO_VOICE_SUBTYPES) {
                // Returns an empty list
                return Collections.emptyList();
            }
            // Creates dummy subtypes
            @SuppressWarnings("unused")
            List<InputMethodSubtypeCompatWrapper> subtypeList =
                    new ArrayList<InputMethodSubtypeCompatWrapper>();
            InputMethodSubtypeCompatWrapper keyboardSubtype = getLastResortSubtype(KEYBOARD_MODE);
            InputMethodSubtypeCompatWrapper voiceSubtype = getLastResortSubtype(VOICE_MODE);
            if (keyboardSubtype != null) {
                subtypeList.add(keyboardSubtype);
            }
            if (voiceSubtype != null) {
                subtypeList.add(voiceSubtype);
            }
            return subtypeList;
        }
        return CompatUtils.copyInputMethodSubtypeListToWrapper(retval);
    }

    private InputMethodInfo getLatinImeInputMethodInfo() {
        if (TextUtils.isEmpty(mLatinImePackageName))
            return null;
        return SubtypeUtils.getInputMethodInfo(mLatinImePackageName);
    }

    private static InputMethodSubtypeCompatWrapper getLastResortSubtype(String mode) {
        if (VOICE_MODE.equals(mode) && !FORCE_ENABLE_VOICE_EVEN_WITH_NO_VOICE_SUBTYPES)
            return null;
        Locale inputLocale = SubtypeSwitcher.getInstance().getInputLocale();
        if (inputLocale == null)
            return null;
        return new InputMethodSubtypeCompatWrapper(0, 0, inputLocale.toString(), mode, "");
    }

    public Map<InputMethodInfo, List<InputMethodSubtypeCompatWrapper>>
            getShortcutInputMethodsAndSubtypes() {
        Object retval = CompatUtils.invoke(mImm, null, METHOD_getShortcutInputMethodsAndSubtypes);
        if (retval == null || !(retval instanceof Map<?, ?>) || ((Map<?, ?>)retval).isEmpty()) {
            if (!FORCE_ENABLE_VOICE_EVEN_WITH_NO_VOICE_SUBTYPES) {
                // Returns an empty map
                return Collections.emptyMap();
            }
            // Creates dummy subtypes
            @SuppressWarnings("unused")
            InputMethodInfo imi = getLatinImeInputMethodInfo();
            InputMethodSubtypeCompatWrapper voiceSubtype = getLastResortSubtype(VOICE_MODE);
            if (imi != null && voiceSubtype != null) {
                Map<InputMethodInfo, List<InputMethodSubtypeCompatWrapper>>
                        shortcutMap =
                                new HashMap<InputMethodInfo,
                                        List<InputMethodSubtypeCompatWrapper>>();
                List<InputMethodSubtypeCompatWrapper> subtypeList =
                        new ArrayList<InputMethodSubtypeCompatWrapper>();
                subtypeList.add(voiceSubtype);
                shortcutMap.put(imi, subtypeList);
                return shortcutMap;
            } else {
                return Collections.emptyMap();
            }
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
