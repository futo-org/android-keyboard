/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.compat.InputMethodManagerCompatWrapper;

import java.util.Collections;
import java.util.List;

/**
 * Enrichment class for InputMethodManager to simplify interaction and add functionality.
 */
public final class RichInputMethodManager {
    private static final String TAG = RichInputMethodManager.class.getSimpleName();

    private RichInputMethodManager() {
        // This utility class is not publicly instantiable.
    }

    private static final RichInputMethodManager sInstance = new RichInputMethodManager();

    private InputMethodManagerCompatWrapper mImmWrapper;
    private InputMethodInfo mInputMethodInfoOfThisIme;

    public static RichInputMethodManager getInstance() {
        sInstance.checkInitialized();
        return sInstance;
    }

    public static void init(final Context context, final SharedPreferences prefs) {
        sInstance.initInternal(context, prefs);
    }

    private boolean isInitialized() {
        return mImmWrapper != null;
    }

    private void checkInitialized() {
        if (!isInitialized()) {
            throw new RuntimeException(TAG + " is used before initialization");
        }
    }

    private void initInternal(final Context context, final SharedPreferences prefs) {
        if (isInitialized()) {
            return;
        }
        mImmWrapper = new InputMethodManagerCompatWrapper(context);
        mInputMethodInfoOfThisIme = getInputMethodInfoOfThisIme(context);

        // Initialize additional subtypes.
        SubtypeLocale.init(context);
        final String prefAdditionalSubtypes = SettingsValues.getPrefAdditionalSubtypes(
                prefs, context.getResources());
        final InputMethodSubtype[] additionalSubtypes =
                AdditionalSubtype.createAdditionalSubtypesArray(prefAdditionalSubtypes);
        setAdditionalInputMethodSubtypes(additionalSubtypes);
    }

    public InputMethodManager getInputMethodManager() {
        checkInitialized();
        return mImmWrapper.mImm;
    }

    private InputMethodInfo getInputMethodInfoOfThisIme(final Context context) {
        final String packageName = context.getPackageName();
        for (final InputMethodInfo imi : mImmWrapper.mImm.getInputMethodList()) {
            if (imi.getPackageName().equals(packageName)) {
                return imi;
            }
        }
        throw new RuntimeException("Input method id for " + packageName + " not found.");
    }

    public boolean switchToNextInputMethod(final IBinder token, final boolean onlyCurrentIme) {
        final boolean result = mImmWrapper.switchToNextInputMethod(token, onlyCurrentIme);
        if (!result) {
            mImmWrapper.mImm.switchToLastInputMethod(token);
            return false;
        }
        return true;
    }

    public InputMethodInfo getInputMethodInfoOfThisIme() {
        return mInputMethodInfoOfThisIme;
    }

    public String getInputMethodIdOfThisIme() {
        return mInputMethodInfoOfThisIme.getId();
    }

    public boolean checkIfSubtypeBelongsToThisImeAndEnabled(final InputMethodSubtype subtype) {
        return checkIfSubtypeBelongsToImeAndEnabled(mInputMethodInfoOfThisIme, subtype);
    }

    public boolean checkIfSubtypeBelongsToThisImeAndImplicitlyEnabled(
            final InputMethodSubtype subtype) {
        final boolean subtypeEnabled = checkIfSubtypeBelongsToThisImeAndEnabled(subtype);
        final boolean subtypeExplicitlyEnabled = checkIfSubtypeBelongsToList(
                subtype, mImmWrapper.mImm.getEnabledInputMethodSubtypeList(
                        mInputMethodInfoOfThisIme, false /* allowsImplicitlySelectedSubtypes */));
        return subtypeEnabled && !subtypeExplicitlyEnabled;
    }

    public boolean checkIfSubtypeBelongsToImeAndEnabled(final InputMethodInfo imi,
            final InputMethodSubtype subtype) {
        return checkIfSubtypeBelongsToList(
                subtype, mImmWrapper.mImm.getEnabledInputMethodSubtypeList(
                        imi, true /* allowsImplicitlySelectedSubtypes */));
    }

    private static boolean checkIfSubtypeBelongsToList(final InputMethodSubtype subtype,
            final List<InputMethodSubtype> subtypes) {
        for (final InputMethodSubtype ims : subtypes) {
            if (ims.equals(subtype)) {
                return true;
            }
        }
        return false;
    }

    public boolean checkIfSubtypeBelongsToThisIme(final InputMethodSubtype subtype) {
        final InputMethodInfo myImi = mInputMethodInfoOfThisIme;
        final int count = myImi.getSubtypeCount();
        for (int i = 0; i < count; i++) {
            final InputMethodSubtype ims = myImi.getSubtypeAt(i);
            if (ims.equals(subtype)) {
                return true;
            }
        }
        return false;
    }

    public InputMethodSubtype getCurrentInputMethodSubtype(
            final InputMethodSubtype defaultSubtype) {
        final InputMethodSubtype currentSubtype = mImmWrapper.mImm.getCurrentInputMethodSubtype();
        return (currentSubtype != null) ? currentSubtype : defaultSubtype;
    }

    public boolean hasMultipleEnabledIMEsOrSubtypes(final boolean shouldIncludeAuxiliarySubtypes) {
        final List<InputMethodInfo> enabledImis = mImmWrapper.mImm.getEnabledInputMethodList();
        return hasMultipleEnabledSubtypes(shouldIncludeAuxiliarySubtypes, enabledImis);
    }

    public boolean hasMultipleEnabledSubtypesInThisIme(
            final boolean shouldIncludeAuxiliarySubtypes) {
        final List<InputMethodInfo> imiList = Collections.singletonList(mInputMethodInfoOfThisIme);
        return hasMultipleEnabledSubtypes(shouldIncludeAuxiliarySubtypes, imiList);
    }

    private boolean hasMultipleEnabledSubtypes(final boolean shouldIncludeAuxiliarySubtypes,
            final List<InputMethodInfo> imiList) {
        // Number of the filtered IMEs
        int filteredImisCount = 0;

        for (InputMethodInfo imi : imiList) {
            // We can return true immediately after we find two or more filtered IMEs.
            if (filteredImisCount > 1) return true;
            final List<InputMethodSubtype> subtypes =
                    mImmWrapper.mImm.getEnabledInputMethodSubtypeList(imi, true);
            // IMEs that have no subtypes should be counted.
            if (subtypes.isEmpty()) {
                ++filteredImisCount;
                continue;
            }

            int auxCount = 0;
            for (InputMethodSubtype subtype : subtypes) {
                if (subtype.isAuxiliary()) {
                    ++auxCount;
                }
            }
            final int nonAuxCount = subtypes.size() - auxCount;

            // IMEs that have one or more non-auxiliary subtypes should be counted.
            // If shouldIncludeAuxiliarySubtypes is true, IMEs that have two or more auxiliary
            // subtypes should be counted as well.
            if (nonAuxCount > 0 || (shouldIncludeAuxiliarySubtypes && auxCount > 1)) {
                ++filteredImisCount;
                continue;
            }
        }

        if (filteredImisCount > 1) {
            return true;
        }
        final List<InputMethodSubtype> subtypes =
                mImmWrapper.mImm.getEnabledInputMethodSubtypeList(null, true);
        int keyboardCount = 0;
        // imm.getEnabledInputMethodSubtypeList(null, true) will return the current IME's
        // both explicitly and implicitly enabled input method subtype.
        // (The current IME should be LatinIME.)
        for (InputMethodSubtype subtype : subtypes) {
            if (KEYBOARD_MODE.equals(subtype.getMode())) {
                ++keyboardCount;
            }
        }
        return keyboardCount > 1;
    }

    public InputMethodSubtype findSubtypeByLocaleAndKeyboardLayoutSet(final String localeString,
            final String keyboardLayoutSetName) {
        final InputMethodInfo myImi = mInputMethodInfoOfThisIme;
        final int count = myImi.getSubtypeCount();
        for (int i = 0; i < count; i++) {
            final InputMethodSubtype subtype = myImi.getSubtypeAt(i);
            final String layoutName = SubtypeLocale.getKeyboardLayoutSetName(subtype);
            if (localeString.equals(subtype.getLocale())
                    && keyboardLayoutSetName.equals(layoutName)) {
                return subtype;
            }
        }
        return null;
    }

    public void setInputMethodAndSubtype(final IBinder token, final InputMethodSubtype subtype) {
        mImmWrapper.mImm.setInputMethodAndSubtype(
                token, mInputMethodInfoOfThisIme.getId(), subtype);
    }

    public void setAdditionalInputMethodSubtypes(final InputMethodSubtype[] subtypes) {
        mImmWrapper.mImm.setAdditionalInputMethodSubtypes(
                mInputMethodInfoOfThisIme.getId(), subtypes);
    }
}
