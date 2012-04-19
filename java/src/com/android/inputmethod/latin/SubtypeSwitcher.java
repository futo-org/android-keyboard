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

import static com.android.inputmethod.latin.Constants.Subtype.KEYBOARD_MODE;
import static com.android.inputmethod.latin.Constants.Subtype.ExtraValue.REQ_NETWORK_CONNECTIVITY;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.keyboard.KeyboardSwitcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SubtypeSwitcher {
    private static boolean DBG = LatinImeLogger.sDBG;
    private static final String TAG = SubtypeSwitcher.class.getSimpleName();

    private static final char LOCALE_SEPARATOR = '_';
    private final TextUtils.SimpleStringSplitter mLocaleSplitter =
            new TextUtils.SimpleStringSplitter(LOCALE_SEPARATOR);

    private static final SubtypeSwitcher sInstance = new SubtypeSwitcher();
    private /* final */ LatinIME mService;
    private /* final */ InputMethodManager mImm;
    private /* final */ Resources mResources;
    private /* final */ ConnectivityManager mConnectivityManager;
    private final ArrayList<InputMethodSubtype> mEnabledKeyboardSubtypesOfCurrentInputMethod =
            new ArrayList<InputMethodSubtype>();
    private final ArrayList<String> mEnabledLanguagesOfCurrentInputMethod = new ArrayList<String>();

    /*-----------------------------------------------------------*/
    // Variants which should be changed only by reload functions.
    private boolean mNeedsToDisplayLanguage;
    private boolean mIsDictionaryAvailable;
    private boolean mIsSystemLanguageSameAsInputLanguage;
    private InputMethodInfo mShortcutInputMethodInfo;
    private InputMethodSubtype mShortcutSubtype;
    private List<InputMethodSubtype> mAllEnabledSubtypesOfCurrentInputMethod;
    private InputMethodSubtype mNoLanguageSubtype;
    // Note: This variable is always non-null after {@link #initialize(LatinIME)}.
    private InputMethodSubtype mCurrentSubtype;
    private Locale mSystemLocale;
    private Locale mInputLocale;
    private String mInputLocaleStr;
    /*-----------------------------------------------------------*/

    private boolean mIsNetworkConnected;

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
        mEnabledKeyboardSubtypesOfCurrentInputMethod.clear();
        mEnabledLanguagesOfCurrentInputMethod.clear();
        mSystemLocale = null;
        mInputLocale = null;
        mInputLocaleStr = null;
        mCurrentSubtype = mImm.getCurrentInputMethodSubtype();
        mAllEnabledSubtypesOfCurrentInputMethod = null;
        mNoLanguageSubtype = ImfUtils.findSubtypeByLocaleAndKeyboardLayoutSet(
                service, SubtypeLocale.NO_LANGUAGE, AdditionalSubtype.QWERTY);

        final NetworkInfo info = mConnectivityManager.getActiveNetworkInfo();
        mIsNetworkConnected = (info != null && info.isConnected());
    }

    // Update all parameters stored in SubtypeSwitcher.
    // Only configuration changed event is allowed to call this because this is heavy.
    private void updateAllParameters() {
        mSystemLocale = mResources.getConfiguration().locale;
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
        final String currentMode = mCurrentSubtype.getMode();
        boolean foundCurrentSubtypeBecameDisabled = true;
        mAllEnabledSubtypesOfCurrentInputMethod = mImm.getEnabledInputMethodSubtypeList(
                null, true);
        mEnabledLanguagesOfCurrentInputMethod.clear();
        mEnabledKeyboardSubtypesOfCurrentInputMethod.clear();
        for (InputMethodSubtype ims : mAllEnabledSubtypesOfCurrentInputMethod) {
            final String locale = ims.getLocale();
            final String mode = ims.getMode();
            mLocaleSplitter.setString(locale);
            if (mLocaleSplitter.hasNext()) {
                mEnabledLanguagesOfCurrentInputMethod.add(mLocaleSplitter.next());
            }
            if (locale.equals(mInputLocaleStr) && mode.equals(currentMode)) {
                foundCurrentSubtypeBecameDisabled = false;
            }
            if (KEYBOARD_MODE.equals(ims.getMode())) {
                mEnabledKeyboardSubtypesOfCurrentInputMethod.add(ims);
            }
        }
        mNeedsToDisplayLanguage = !(getEnabledKeyboardLocaleCount() <= 1
                && mIsSystemLanguageSameAsInputLanguage);
        if (foundCurrentSubtypeBecameDisabled) {
            if (DBG) {
                Log.w(TAG, "Current subtype: " + mInputLocaleStr + ", " + currentMode);
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
        final String newLocale = newSubtype.getLocale();
        final String newMode = newSubtype.getMode();
        final String oldMode = mCurrentSubtype.getMode();
        if (DBG) {
            Log.w(TAG, "Update subtype to:" + newLocale + "," + newMode
                    + ", from: " + mInputLocaleStr + ", " + oldMode);
        }
        boolean languageChanged = false;
        if (!newLocale.equals(mInputLocaleStr)) {
            if (mInputLocaleStr != null) {
                languageChanged = true;
            }
            updateInputLocale(newLocale);
        }
        boolean modeChanged = false;
        if (!newMode.equals(oldMode)) {
            if (oldMode != null) {
                modeChanged = true;
            }
        }
        mCurrentSubtype = newSubtype;

        if (KEYBOARD_MODE.equals(mCurrentSubtype.getMode())) {
            if (modeChanged || languageChanged) {
                updateShortcutIME();
                mService.onRefreshKeyboard();
            }
        } else {
            final String packageName = mService.getPackageName();
            int version = -1;
            try {
                version = mService.getPackageManager().getPackageInfo(
                        packageName, 0).versionCode;
            } catch (NameNotFoundException e) {
            }
            Log.w(TAG, "Unknown subtype mode: " + newMode + "," + version + ", " + packageName
                    + ". IME is already changed to other IME.");
            Log.w(TAG, "Subtype mode:" + newSubtype.getMode());
            Log.w(TAG, "Subtype locale:" + newSubtype.getLocale());
            Log.w(TAG, "Subtype extra value:" + newSubtype.getExtraValue());
            Log.w(TAG, "Subtype is auxiliary:" + newSubtype.isAuxiliary());
        }
    }

    // Update the current input locale from Locale string.
    private void updateInputLocale(String inputLocaleStr) {
        // example: inputLocaleStr = "en_US" "en" ""
        // "en_US" --> language: en  & country: US
        // "en" --> language: en
        // "" --> the system locale
        if (!TextUtils.isEmpty(inputLocaleStr)) {
            mInputLocale = LocaleUtils.constructLocaleFromString(inputLocaleStr);
            mInputLocaleStr = inputLocaleStr;
        } else {
            mInputLocale = mSystemLocale;
            String country = mSystemLocale.getCountry();
            mInputLocaleStr = mSystemLocale.getLanguage()
                    + (TextUtils.isEmpty(country) ? "" : "_" + mSystemLocale.getLanguage());
        }
        mIsSystemLanguageSameAsInputLanguage = getSystemLocale().getLanguage().equalsIgnoreCase(
                getInputLocale().getLanguage());
        mNeedsToDisplayLanguage = !(getEnabledKeyboardLocaleCount() <= 1
                && mIsSystemLanguageSameAsInputLanguage);
        mIsDictionaryAvailable = DictionaryFactory.isDictionaryAvailable(mService, mInputLocale);
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
    // Language Switching functions //
    //////////////////////////////////

    public int getEnabledKeyboardLocaleCount() {
        return mEnabledKeyboardSubtypesOfCurrentInputMethod.size();
    }

    public boolean needsToDisplayLanguage(Locale keyboardLocale) {
        if (keyboardLocale.toString().equals(SubtypeLocale.NO_LANGUAGE)) {
            return true;
        }
        if (!keyboardLocale.equals(mInputLocale)) {
            return false;
        }
        return mNeedsToDisplayLanguage;
    }

    public Locale getInputLocale() {
        return mInputLocale;
    }

    public String getInputLocaleStr() {
        return mInputLocaleStr;
    }

    public String[] getEnabledLanguages() {
        int enabledLanguageCount = mEnabledLanguagesOfCurrentInputMethod.size();
        // Workaround for explicitly specifying the voice language
        if (enabledLanguageCount == 1) {
            mEnabledLanguagesOfCurrentInputMethod.add(mEnabledLanguagesOfCurrentInputMethod
                    .get(0));
            ++enabledLanguageCount;
        }
        return mEnabledLanguagesOfCurrentInputMethod.toArray(new String[enabledLanguageCount]);
    }

    public Locale getSystemLocale() {
        return mSystemLocale;
    }

    public boolean isSystemLanguageSameAsInputLanguage() {
        return mIsSystemLanguageSameAsInputLanguage;
    }

    public void onConfigurationChanged(Configuration conf) {
        final Locale systemLocale = conf.locale;
        // If system configuration was changed, update all parameters.
        if (!TextUtils.equals(systemLocale.toString(), mSystemLocale.toString())) {
            updateAllParameters();
        }
    }

    public boolean isDictionaryAvailable() {
        return mIsDictionaryAvailable;
    }

    public InputMethodSubtype getCurrentSubtype() {
        return mCurrentSubtype;
    }

    public InputMethodSubtype getNoLanguageSubtype() {
        return mNoLanguageSubtype;
    }
}
