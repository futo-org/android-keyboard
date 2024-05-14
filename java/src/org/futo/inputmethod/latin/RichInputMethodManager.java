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

package org.futo.inputmethod.latin;

import static org.futo.inputmethod.latin.common.Constants.Subtype.KEYBOARD_MODE;

import android.content.Context;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import org.futo.inputmethod.annotations.UsedForTesting;
import org.futo.inputmethod.compat.InputMethodManagerCompatWrapper;
import org.futo.inputmethod.compat.InputMethodSubtypeCompatUtils;
import org.futo.inputmethod.latin.settings.Settings;
import org.futo.inputmethod.latin.utils.AdditionalSubtypeUtils;
import org.futo.inputmethod.latin.utils.LanguageOnSpacebarUtils;
import org.futo.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Enrichment class for InputMethodManager to simplify interaction and add functionality.
 */
// non final for easy mocking.
public class RichInputMethodManager {
    private static final String TAG = RichInputMethodManager.class.getSimpleName();
    private static final boolean DEBUG = false;

    private RichInputMethodManager() {
        // This utility class is not publicly instantiable.
    }

    private static final RichInputMethodManager sInstance = new RichInputMethodManager();

    private Context mContext;
    private InputMethodManagerCompatWrapper mImmWrapper;
    private InputMethodInfoCache mInputMethodInfoCache;
    private RichInputMethodSubtype mCurrentRichInputMethodSubtype;
    private InputMethodInfo mShortcutInputMethodInfo;
    private InputMethodSubtype mShortcutSubtype;

    private static final int INDEX_NOT_FOUND = -1;

    public static RichInputMethodManager getInstance() {
        sInstance.checkInitialized();
        return sInstance;
    }

    public static void init(final Context context) {
        sInstance.initInternal(context);
    }

    private boolean isInitialized() {
        return mImmWrapper != null;
    }

    private void checkInitialized() {
        if (!isInitialized()) {
            throw new RuntimeException(TAG + " is used before initialization");
        }
    }

    private void initInternal(final Context context) {
        if (isInitialized()) {
            return;
        }
        mImmWrapper = new InputMethodManagerCompatWrapper(context);
        mContext = context;
        mInputMethodInfoCache = new InputMethodInfoCache(
                mImmWrapper.mImm, context.getPackageName());

        // Initialize additional subtypes.
        SubtypeLocaleUtils.init(context);

        // Initialize the current input method subtype and the shortcut IME.
        refreshSubtypeCaches();
    }

    public InputMethodSubtype[] getAdditionalSubtypes() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        final String prefAdditionalSubtypes = Settings.readPrefAdditionalSubtypes(
                prefs, mContext.getResources());
        return AdditionalSubtypeUtils.createAdditionalSubtypesArray(prefAdditionalSubtypes);
    }

    public InputMethodManager getInputMethodManager() {
        checkInitialized();
        return mImmWrapper.mImm;
    }

    private static class InputMethodInfoCache {
        private final InputMethodManager mImm;
        private final String mImePackageName;

        private InputMethodInfo mCachedThisImeInfo;
        private final HashMap<InputMethodInfo, List<InputMethodSubtype>>
                mCachedSubtypeListWithImplicitlySelected;
        private final HashMap<InputMethodInfo, List<InputMethodSubtype>>
                mCachedSubtypeListOnlyExplicitlySelected;

        public InputMethodInfoCache(final InputMethodManager imm, final String imePackageName) {
            mImm = imm;
            mImePackageName = imePackageName;
            mCachedSubtypeListWithImplicitlySelected = new HashMap<>();
            mCachedSubtypeListOnlyExplicitlySelected = new HashMap<>();
        }

        public synchronized InputMethodInfo getInputMethodOfThisIme() {
            if (mCachedThisImeInfo != null) {
                return mCachedThisImeInfo;
            }
            for (final InputMethodInfo imi : mImm.getInputMethodList()) {
                if (imi.getPackageName().equals(mImePackageName)) {
                    mCachedThisImeInfo = imi;
                    return imi;
                }
            }
            throw new RuntimeException("Input method id for " + mImePackageName + " not found.");
        }

        public synchronized List<InputMethodSubtype> getEnabledInputMethodSubtypeList(
                final InputMethodInfo imi, final boolean allowsImplicitlySelectedSubtypes) {
            final HashMap<InputMethodInfo, List<InputMethodSubtype>> cache =
                    allowsImplicitlySelectedSubtypes
                    ? mCachedSubtypeListWithImplicitlySelected
                    : mCachedSubtypeListOnlyExplicitlySelected;
            final List<InputMethodSubtype> cachedList = cache.get(imi);
            if (cachedList != null) {
                return cachedList;
            }
            final List<InputMethodSubtype> result = mImm.getEnabledInputMethodSubtypeList(
                    imi, allowsImplicitlySelectedSubtypes);
            cache.put(imi, result);
            return result;
        }

        public synchronized void clear() {
            mCachedThisImeInfo = null;
            mCachedSubtypeListWithImplicitlySelected.clear();
            mCachedSubtypeListOnlyExplicitlySelected.clear();
        }
    }

    public InputMethodInfo getInputMethodInfoOfThisIme() {
        return mInputMethodInfoCache.getInputMethodOfThisIme();
    }

    public String getInputMethodIdOfThisIme() {
        return getInputMethodInfoOfThisIme().getId();
    }

    public void onSubtypeChanged(@Nonnull final InputMethodSubtype newSubtype) {
        updateCurrentSubtype(newSubtype);
        updateShortcutIme();
        if (DEBUG) {
            Log.w(TAG, "onSubtypeChanged: " + mCurrentRichInputMethodSubtype.getNameForLogging());
        }
    }

    private static RichInputMethodSubtype sForcedSubtypeForTesting = null;

    @UsedForTesting
    static void forceSubtype(@Nonnull final InputMethodSubtype subtype) {
        sForcedSubtypeForTesting = RichInputMethodSubtype.getRichInputMethodSubtype(subtype);
    }

    @Nonnull
    public Locale getCurrentSubtypeLocale() {
        if (null != sForcedSubtypeForTesting) {
            return sForcedSubtypeForTesting.getLocale();
        }
        return getCurrentSubtype().getLocale();
    }

    @Nonnull
    public RichInputMethodSubtype getCurrentSubtype() {
        if (null != sForcedSubtypeForTesting) {
            return sForcedSubtypeForTesting;
        }
        return mCurrentRichInputMethodSubtype;
    }


    public String getCombiningRulesExtraValueOfCurrentSubtype() {
        return SubtypeLocaleUtils.getCombiningRulesExtraValue(getCurrentSubtype().getRawSubtype());
    }

    public void refreshSubtypeCaches() {
        mInputMethodInfoCache.clear();
        updateCurrentSubtype(Subtypes.INSTANCE.getActiveSubtype(mContext));
        updateShortcutIme();
    }

    public boolean shouldOfferSwitchingToNextInputMethod(final IBinder binder,
            boolean defaultValue) {

        if(true) return defaultValue;
        // Use the default value instead on Jelly Bean MR2 and previous where
        // {@link InputMethodManager#shouldOfferSwitchingToNextInputMethod} isn't yet available
        // and on KitKat where the API is still just a stub to return true always.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            return defaultValue;
        }
        return mImmWrapper.shouldOfferSwitchingToNextInputMethod(binder);
    }

    public boolean isSystemLocaleSameAsLocaleOfAllEnabledSubtypesOfEnabledImes() {
        final Locale systemLocale = mContext.getResources().getConfiguration().locale;
        final Set<InputMethodSubtype> enabledSubtypesOfEnabledImes = new HashSet<>();
        final InputMethodManager inputMethodManager = getInputMethodManager();
        final List<InputMethodInfo> enabledInputMethodInfoList =
                inputMethodManager.getEnabledInputMethodList();
        for (final InputMethodInfo info : enabledInputMethodInfoList) {
            final List<InputMethodSubtype> enabledSubtypes =
                    inputMethodManager.getEnabledInputMethodSubtypeList(
                            info, true /* allowsImplicitlySelectedSubtypes */);
            if (enabledSubtypes.isEmpty()) {
                // An IME with no subtypes is found.
                return false;
            }
            enabledSubtypesOfEnabledImes.addAll(enabledSubtypes);
        }
        for (final InputMethodSubtype subtype : enabledSubtypesOfEnabledImes) {
            if (!subtype.isAuxiliary() && !subtype.getLocale().isEmpty()
                    && !systemLocale.equals(SubtypeLocaleUtils.getSubtypeLocale(subtype))) {
                return false;
            }
        }
        return true;
    }

    private void updateCurrentSubtype(@Nullable final InputMethodSubtype subtype) {
        mCurrentRichInputMethodSubtype = RichInputMethodSubtype.getRichInputMethodSubtype(subtype);
    }

    private void updateShortcutIme() {
        if (DEBUG) {
            Log.d(TAG, "Update shortcut IME from : "
                    + (mShortcutInputMethodInfo == null
                            ? "<null>" : mShortcutInputMethodInfo.getId()) + ", "
                    + (mShortcutSubtype == null ? "<null>" : (
                            mShortcutSubtype.getLocale() + ", " + mShortcutSubtype.getMode())));
        }
        final RichInputMethodSubtype richSubtype = mCurrentRichInputMethodSubtype;
        final Locale systemLocale = mContext.getResources().getConfiguration().locale;

        // TODO: Update an icon for shortcut IME
        final Map<InputMethodInfo, List<InputMethodSubtype>> shortcuts =
                getInputMethodManager().getShortcutInputMethodsAndSubtypes();
        mShortcutInputMethodInfo = null;
        mShortcutSubtype = null;
        for (final InputMethodInfo imi : shortcuts.keySet()) {
            final List<InputMethodSubtype> subtypes = shortcuts.get(imi);
            // TODO: Returns the first found IMI for now. Should handle all shortcuts as
            // appropriate.
            mShortcutInputMethodInfo = imi;
            // TODO: Pick up the first found subtype for now. Should handle all subtypes
            // as appropriate.
            mShortcutSubtype = subtypes.size() > 0 ? subtypes.get(0) : null;
            break;
        }
        if (DEBUG) {
            Log.d(TAG, "Update shortcut IME to : "
                    + (mShortcutInputMethodInfo == null
                            ? "<null>" : mShortcutInputMethodInfo.getId()) + ", "
                    + (mShortcutSubtype == null ? "<null>" : (
                            mShortcutSubtype.getLocale() + ", " + mShortcutSubtype.getMode())));
        }
    }

    public void switchToShortcutIme(final InputMethodService context) {
        if (mShortcutInputMethodInfo == null) {
            return;
        }

        final String imiId = mShortcutInputMethodInfo.getId();
        switchToTargetIME(imiId, mShortcutSubtype, context);
    }

    private void switchToTargetIME(final String imiId, final InputMethodSubtype subtype,
            final InputMethodService context) {
        final IBinder token = context.getWindow().getWindow().getAttributes().token;
        if (token == null) {
            return;
        }
        final InputMethodManager imm = getInputMethodManager();
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                imm.setInputMethodAndSubtype(token, imiId, subtype);
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public boolean isShortcutImeReady() {
        if (mShortcutInputMethodInfo == null) {
            return false;
        }
        if (mShortcutSubtype == null) {
            return true;
        }
        return true;
    }
}
