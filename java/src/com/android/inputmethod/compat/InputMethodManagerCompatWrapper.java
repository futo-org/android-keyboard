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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import com.android.inputmethod.deprecated.LanguageSwitcherProxy;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SubtypeSwitcher;
import com.android.inputmethod.latin.Utils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

    private static final InputMethodManagerCompatWrapper sInstance =
            new InputMethodManagerCompatWrapper();

    public static final boolean SUBTYPE_SUPPORTED;

    static {
        // This static initializer guarantees that METHOD_getShortcutInputMethodsAndSubtypes is
        // already instantiated.
        SUBTYPE_SUPPORTED = METHOD_getShortcutInputMethodsAndSubtypes != null;
    }

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
    private LanguageSwitcherProxy mLanguageSwitcherProxy;
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
        sInstance.mLanguageSwitcherProxy = LanguageSwitcherProxy.getInstance();
    }

    public InputMethodSubtypeCompatWrapper getCurrentInputMethodSubtype() {
        if (!SUBTYPE_SUPPORTED) {
            return new InputMethodSubtypeCompatWrapper(
                    0, 0, mLanguageSwitcherProxy.getInputLocale().toString(), KEYBOARD_MODE, "");
        }
        Object o = CompatUtils.invoke(mImm, null, METHOD_getCurrentInputMethodSubtype);
        return new InputMethodSubtypeCompatWrapper(o);
    }

    public List<InputMethodSubtypeCompatWrapper> getEnabledInputMethodSubtypeList(
            InputMethodInfoCompatWrapper imi, boolean allowsImplicitlySelectedSubtypes) {
        if (!SUBTYPE_SUPPORTED) {
            String[] languages = mLanguageSwitcherProxy.getEnabledLanguages(
                    allowsImplicitlySelectedSubtypes);
            List<InputMethodSubtypeCompatWrapper> subtypeList =
                    new ArrayList<InputMethodSubtypeCompatWrapper>();
            for (String lang: languages) {
                subtypeList.add(new InputMethodSubtypeCompatWrapper(0, 0, lang, KEYBOARD_MODE, ""));
            }
            return subtypeList;
        }
        Object retval = CompatUtils.invoke(mImm, null, METHOD_getEnabledInputMethodSubtypeList,
                (imi != null ? imi.getInputMethodInfo() : null), allowsImplicitlySelectedSubtypes);
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

    private InputMethodInfoCompatWrapper getLatinImeInputMethodInfo() {
        if (TextUtils.isEmpty(mLatinImePackageName))
            return null;
        return Utils.getInputMethodInfo(this, mLatinImePackageName);
    }

    @SuppressWarnings("unused")
    private InputMethodSubtypeCompatWrapper getLastResortSubtype(String mode) {
        if (VOICE_MODE.equals(mode) && !FORCE_ENABLE_VOICE_EVEN_WITH_NO_VOICE_SUBTYPES)
            return null;
        Locale inputLocale = SubtypeSwitcher.getInstance().getInputLocale();
        if (inputLocale == null)
            return null;
        return new InputMethodSubtypeCompatWrapper(0, 0, inputLocale.toString(), mode, "");
    }

    public Map<InputMethodInfoCompatWrapper, List<InputMethodSubtypeCompatWrapper>>
            getShortcutInputMethodsAndSubtypes() {
        Object retval = CompatUtils.invoke(mImm, null, METHOD_getShortcutInputMethodsAndSubtypes);
        if (retval == null || !(retval instanceof Map<?, ?>) || ((Map<?, ?>)retval).isEmpty()) {
            if (!FORCE_ENABLE_VOICE_EVEN_WITH_NO_VOICE_SUBTYPES) {
                // Returns an empty map
                return Collections.emptyMap();
            }
            // Creates dummy subtypes
            @SuppressWarnings("unused")
            InputMethodInfoCompatWrapper imi = getLatinImeInputMethodInfo();
            InputMethodSubtypeCompatWrapper voiceSubtype = getLastResortSubtype(VOICE_MODE);
            if (imi != null && voiceSubtype != null) {
                Map<InputMethodInfoCompatWrapper, List<InputMethodSubtypeCompatWrapper>>
                        shortcutMap =
                                new HashMap<InputMethodInfoCompatWrapper,
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
        if (SubtypeSwitcher.getInstance().isDummyVoiceMode()) {
            return true;
        }
        return (Boolean)CompatUtils.invoke(mImm, false, METHOD_switchToLastInputMethod, token);
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
        if (SUBTYPE_SUPPORTED) {
            mImm.showInputMethodPicker();
            return;
        }

        // The code below are based on {@link InputMethodManager#showInputMethodMenuInternal}.

        final InputMethodInfoCompatWrapper myImi = Utils.getInputMethodInfo(
                this, mLatinImePackageName);
        final List<InputMethodSubtypeCompatWrapper> myImsList = getEnabledInputMethodSubtypeList(
                myImi, true);
        final InputMethodSubtypeCompatWrapper currentIms = getCurrentInputMethodSubtype();
        final List<InputMethodInfoCompatWrapper> imiList = getEnabledInputMethodList();
        imiList.remove(myImi);
        Collections.sort(imiList, new Comparator<InputMethodInfoCompatWrapper>() {
            @Override
            public int compare(InputMethodInfoCompatWrapper imi1,
                    InputMethodInfoCompatWrapper imi2) {
                final CharSequence imiId1 = imi1.loadLabel(mPackageManager) + "/" + imi1.getId();
                final CharSequence imiId2 = imi2.loadLabel(mPackageManager) + "/" + imi2.getId();
                return imiId1.toString().compareTo(imiId2.toString());
            }
        });

        final int myImsCount = myImsList.size();
        final int imiCount = imiList.size();
        final CharSequence[] items = new CharSequence[myImsCount + imiCount];

        int checkedItem = 0;
        int index = 0;
        final CharSequence myImiLabel = myImi.loadLabel(mPackageManager);
        for (int i = 0; i < myImsCount; i++) {
            InputMethodSubtypeCompatWrapper ims = myImsList.get(i);
            if (currentIms.equals(ims))
                checkedItem = index;
            final CharSequence title = TextUtils.concat(
                    ims.getDisplayName(mService, mLatinImePackageName, mApplicationInfo),
                    " (" + myImiLabel, ")");
            items[index] = title;
            index++;
        }

        for (int i = 0; i < imiCount; i++) {
            final InputMethodInfoCompatWrapper imi = imiList.get(i);
            final CharSequence title = imi.loadLabel(mPackageManager);
            items[index] = title;
            index++;
        }

        final OnClickListener buttonListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface di, int whichButton) {
                final Intent intent = new Intent("android.settings.INPUT_METHOD_SETTINGS");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                mService.startActivity(intent);
            }
        };
        final InputMethodServiceCompatWrapper service = mService;
        final IBinder token = service.getWindow().getWindow().getAttributes().token;
        final OnClickListener selectionListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface di, int which) {
                di.dismiss();
                if (which < myImsCount) {
                    final int imsIndex = which;
                    final InputMethodSubtypeCompatWrapper ims = myImsList.get(imsIndex);
                    service.notifyOnCurrentInputMethodSubtypeChanged(ims);
                } else {
                    final int imiIndex = which - myImsCount;
                    final InputMethodInfoCompatWrapper imi = imiList.get(imiIndex);
                    setInputMethodAndSubtype(token, imi.getId(), null);
                }
            }
        };

        final AlertDialog.Builder builder = new AlertDialog.Builder(mService)
                .setTitle(mService.getString(R.string.selectInputMethod))
                .setNeutralButton(R.string.configure_input_method, buttonListener)
                .setSingleChoiceItems(items, checkedItem, selectionListener);
        mService.showOptionDialogInternal(builder.create());
    }
}
