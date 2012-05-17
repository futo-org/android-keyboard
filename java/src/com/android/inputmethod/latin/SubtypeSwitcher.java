/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.inputmethod.latin;

import static com.android.inputmethod.latin.Constants.Subtype.ExtraValue.REQ_NETWORK_CONNECTIVITY;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.keyboard.KeyboardSwitcher;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SubtypeSwitcher {
    private static boolean DBG = LatinImeLogger.sDBG;
    private static final String TAG = SubtypeSwitcher.class.getSimpleName();

    private static final SubtypeSwitcher sInstance = new SubtypeSwitcher();
    private /* final */ LatinIME mService;
    private /* final */ InputMethodManager mImm;
    private /* final */ Resources mResources;
    private /* final */ ConnectivityManager mConnectivityManager;

    /*-----------------------------------------------------------*/
    // Variants which should be changed only by reload functions.
    private NeedsToDisplayLanguage mNeedsToDisplayLanguage = new NeedsToDisplayLanguage();
    private InputMethodInfo mShortcutInputMethodInfo;
    private InputMethodSubtype mShortcutSubtype;
    private InputMethodSubtype mNoLanguageSubtype;
    // Note: This variable is always non-null after {@link #initialize(LatinIME)}.
    private InputMethodSubtype mCurrentSubtype;
    private Locale mCurrentSystemLocale;
    /*-----------------------------------------------------------*/

    private boolean mIsNetworkConnected;

    static class NeedsToDisplayLanguage {
        private int mEnabledSubtypeCount;
        private boolean mIsSystemLanguageSameAsInputLanguage;

        public boolean getValue() {
            return mEnabledSubtypeCount >= 2 || !mIsSystemLanguageSameAsInputLanguage;
        }

        public void updateEnabledSubtypeCount(int count) {
            mEnabledSubtypeCount = count;
        }

        public void updateIsSystemLanguageSameAsInputLanguage(boolean isSame) {
            mIsSystemLanguageSameAsInputLanguage = isSame;
        }
    }

    public static SubtypeSwitcher getInstance() {
        return sInstance;
    }

    public static void init(LatinIME service) {
        SubtypeLocale.init(service);
        sInstance.initialize(service);
        sInstance.updateAllParameters();
    }

    private SubtypeSwitcher() {
        // Intentional empty constructor for singleton.
    }

    private void initialize(LatinIME service) {
        mService = service;
        mResources = service.getResources();
        mImm = ImfUtils.getInputMethodManager(service);
        mConnectivityManager = (ConnectivityManager) service.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        mCurrentSystemLocale = mResources.getConfiguration().locale;
        mCurrentSubtype = mImm.getCurrentInputMethodSubtype();
        mNoLanguageSubtype = ImfUtils.findSubtypeByLocaleAndKeyboardLayoutSet(
                service, SubtypeLocale.NO_LANGUAGE, SubtypeLocale.QWERTY);
        if (mNoLanguageSubtype == null) {
            throw new RuntimeException("Can't find no lanugage with QWERTY subtype");
        }

        final NetworkInfo info = mConnectivityManager.getActiveNetworkInfo();
        mIsNetworkConnected = (info != null && info.isConnected());
    }

    // Update all parameters stored in SubtypeSwitcher.
    // Only configuration changed event is allowed to call this because this is heavy.
    private void updateAllParameters() {
        mCurrentSystemLocale = mResources.getConfiguration().locale;
        updateSubtype(mImm.getCurrentInputMethodSubtype());
        updateParametersOnStartInputView();
    }

    // Update parameters which are changed outside LatinIME. This parameters affect UI so they
    // should be updated every time onStartInputview.
    public void updateParametersOnStartInputView() {
        updateEnabledSubtypes();
        updateShortcutIME();
    }

    // Reload enabledSubtypes from the framework.
    private void updateEnabledSubtypes() {
        final InputMethodSubtype currentSubtype = mCurrentSubtype;
        boolean foundCurrentSubtypeBecameDisabled = true;
        final List<InputMethodSubtype> enabledSubtypesOfThisIme =
                mImm.getEnabledInputMethodSubtypeList(null, true);
        for (InputMethodSubtype ims : enabledSubtypesOfThisIme) {
            if (ims.equals(currentSubtype)) {
                foundCurrentSubtypeBecameDisabled = false;
            }
        }
        mNeedsToDisplayLanguage.updateEnabledSubtypeCount(enabledSubtypesOfThisIme.size());
        if (foundCurrentSubtypeBecameDisabled) {
            if (DBG) {
                Log.w(TAG, "Last subtype: "
                        + currentSubtype.getLocale() + "/" + currentSubtype.getExtraValue());
                Log.w(TAG, "Last subtype was disabled. Update to the current one.");
            }
            updateSubtype(mImm.getCurrentInputMethodSubtype());
        }
    }

    private void updateShortcutIME() {
        if (DBG) {
            Log.d(TAG, "Update shortcut IME from : "
                    + (mShortcutInputMethodInfo == null
                            ? "<null>" : mShortcutInputMethodInfo.getId()) + ", "
                    + (mShortcutSubtype == null ? "<null>" : (
                            mShortcutSubtype.getLocale() + ", " + mShortcutSubtype.getMode())));
        }
        // TODO: Update an icon for shortcut IME
        final Map<InputMethodInfo, List<InputMethodSubtype>> shortcuts =
                mImm.getShortcutInputMethodsAndSubtypes();
        mShortcutInputMethodInfo = null;
        mShortcutSubtype = null;
        for (InputMethodInfo imi : shortcuts.keySet()) {
            List<InputMethodSubtype> subtypes = shortcuts.get(imi);
            // TODO: Returns the first found IMI for now. Should handle all shortcuts as
            // appropriate.
            mShortcutInputMethodInfo = imi;
            // TODO: Pick up the first found subtype for now. Should handle all subtypes
            // as appropriate.
            mShortcutSubtype = subtypes.size() > 0 ? subtypes.get(0) : null;
            break;
        }
        if (DBG) {
            Log.d(TAG, "Update shortcut IME to : "
                    + (mShortcutInputMethodInfo == null
                            ? "<null>" : mShortcutInputMethodInfo.getId()) + ", "
                    + (mShortcutSubtype == null ? "<null>" : (
                            mShortcutSubtype.getLocale() + ", " + mShortcutSubtype.getMode())));
        }
    }

    // Update the current subtype. LatinIME.onCurrentInputMethodSubtypeChanged calls this function.
    public void updateSubtype(InputMethodSubtype newSubtype) {
        if (DBG) {
            Log.w(TAG, "onCurrentInputMethodSubtypeChanged: to: "
                    + newSubtype.getLocale() + "/" + newSubtype.getExtraValue() + ", from: "
                    + mCurrentSubtype.getLocale() + "/" + mCurrentSubtype.getExtraValue());
        }

        final Locale newLocale = SubtypeLocale.getSubtypeLocale(newSubtype);
        mNeedsToDisplayLanguage.updateIsSystemLanguageSameAsInputLanguage(
                mCurrentSystemLocale.equals(newLocale));

        if (newSubtype.equals(mCurrentSubtype)) return;

        mCurrentSubtype = newSubtype;
        updateShortcutIME();
        mService.onRefreshKeyboard();
    }

    ////////////////////////////
    // Shortcut IME functions //
    ////////////////////////////

    public void switchToShortcutIME() {
        if (mShortcutInputMethodInfo == null) {
            return;
        }

        final String imiId = mShortcutInputMethodInfo.getId();
        switchToTargetIME(imiId, mShortcutSubtype);
    }

    private void switchToTargetIME(final String imiId, final InputMethodSubtype subtype) {
        final IBinder token = mService.getWindow().getWindow().getAttributes().token;
        if (token == null) {
            return;
        }
        final InputMethodManager imm = mImm;
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                imm.setInputMethodAndSubtype(token, imiId, subtype);
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public boolean isShortcutImeEnabled() {
        if (mShortcutInputMethodInfo == null) {
            return false;
        }
        if (mShortcutSubtype == null) {
            return true;
        }
        final boolean allowsImplicitlySelectedSubtypes = true;
        for (final InputMethodSubtype enabledSubtype : mImm.getEnabledInputMethodSubtypeList(
                mShortcutInputMethodInfo, allowsImplicitlySelectedSubtypes)) {
            if (enabledSubtype.equals(mShortcutSubtype)) {
                return true;
            }
        }
        return false;
    }

    public boolean isShortcutImeReady() {
        if (mShortcutInputMethodInfo == null)
            return false;
        if (mShortcutSubtype == null)
            return true;
        if (mShortcutSubtype.containsExtraValueKey(REQ_NETWORK_CONNECTIVITY)) {
            return mIsNetworkConnected;
        }
        return true;
    }

    public void onNetworkStateChanged(Intent intent) {
        final boolean noConnection = intent.getBooleanExtra(
                ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
        mIsNetworkConnected = !noConnection;

        KeyboardSwitcher.getInstance().onNetworkStateChanged();
    }

    //////////////////////////////////
    // Subtype Switching functions //
    //////////////////////////////////

    public boolean needsToDisplayLanguage(Locale keyboardLocale) {
        if (keyboardLocale.toString().equals(SubtypeLocale.NO_LANGUAGE)) {
            return true;
        }
        if (!keyboardLocale.equals(getCurrentSubtypeLocale())) {
            return false;
        }
        return mNeedsToDisplayLanguage.getValue();
    }

    public Locale getCurrentSubtypeLocale() {
        return SubtypeLocale.getSubtypeLocale(mCurrentSubtype);
    }

    public void onConfigurationChanged(Configuration conf) {
        final Locale systemLocale = conf.locale;
        // If system configuration was changed, update all parameters.
        if (!systemLocale.equals(mCurrentSystemLocale)) {
            updateAllParameters();
        }
    }

    public InputMethodSubtype getCurrentSubtype() {
        return mCurrentSubtype;
    }

    public InputMethodSubtype getNoLanguageSubtype() {
        return mNoLanguageSubtype;
    }
}
