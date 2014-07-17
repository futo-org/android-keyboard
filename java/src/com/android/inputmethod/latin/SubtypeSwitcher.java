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
import android.content.res.Resources;
import android.inputmethodservice.InputMethodService;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.compat.InputMethodSubtypeCompatUtils;
import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.keyboard.internal.LanguageOnSpacebarHelper;
import com.android.inputmethod.latin.define.DebugFlags;
import com.android.inputmethod.latin.utils.LocaleUtils;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SubtypeSwitcher {
    private static boolean DBG = DebugFlags.DEBUG_ENABLED;
    private static final String TAG = SubtypeSwitcher.class.getSimpleName();

    private static final SubtypeSwitcher sInstance = new SubtypeSwitcher();

    private /* final */ RichInputMethodManager mRichImm;
    private /* final */ Resources mResources;

    private final LanguageOnSpacebarHelper mLanguageOnSpacebarHelper =
            new LanguageOnSpacebarHelper();
    private InputMethodInfo mShortcutInputMethodInfo;
    private InputMethodSubtype mShortcutSubtype;
    private InputMethodSubtype mNoLanguageSubtype;
    private InputMethodSubtype mEmojiSubtype;
    private boolean mIsNetworkConnected;

    private static final String KEYBOARD_MODE = "keyboard";
    // Dummy no language QWERTY subtype. See {@link R.xml.method}.
    private static final int SUBTYPE_ID_OF_DUMMY_NO_LANGUAGE_SUBTYPE = 0xdde0bfd3;
    private static final String EXTRA_VALUE_OF_DUMMY_NO_LANGUAGE_SUBTYPE =
            "KeyboardLayoutSet=" + SubtypeLocaleUtils.QWERTY
            + "," + Constants.Subtype.ExtraValue.ASCII_CAPABLE
            + "," + Constants.Subtype.ExtraValue.ENABLED_WHEN_DEFAULT_IS_NOT_ASCII_CAPABLE
            + "," + Constants.Subtype.ExtraValue.EMOJI_CAPABLE;
    private static final InputMethodSubtype DUMMY_NO_LANGUAGE_SUBTYPE =
            InputMethodSubtypeCompatUtils.newInputMethodSubtype(
                    R.string.subtype_no_language_qwerty, R.drawable.ic_ime_switcher_dark,
                    SubtypeLocaleUtils.NO_LANGUAGE, KEYBOARD_MODE,
                    EXTRA_VALUE_OF_DUMMY_NO_LANGUAGE_SUBTYPE,
                    false /* isAuxiliary */, false /* overridesImplicitlyEnabledSubtype */,
                    SUBTYPE_ID_OF_DUMMY_NO_LANGUAGE_SUBTYPE);
    // Caveat: We probably should remove this when we add an Emoji subtype in {@link R.xml.method}.
    // Dummy Emoji subtype. See {@link R.xml.method}.
    private static final int SUBTYPE_ID_OF_DUMMY_EMOJI_SUBTYPE = 0xd78b2ed0;
    private static final String EXTRA_VALUE_OF_DUMMY_EMOJI_SUBTYPE =
            "KeyboardLayoutSet=" + SubtypeLocaleUtils.EMOJI
            + "," + Constants.Subtype.ExtraValue.EMOJI_CAPABLE;
    private static final InputMethodSubtype DUMMY_EMOJI_SUBTYPE =
            InputMethodSubtypeCompatUtils.newInputMethodSubtype(
                    R.string.subtype_emoji, R.drawable.ic_ime_switcher_dark,
                    SubtypeLocaleUtils.NO_LANGUAGE, KEYBOARD_MODE,
                    EXTRA_VALUE_OF_DUMMY_EMOJI_SUBTYPE,
                    false /* isAuxiliary */, false /* overridesImplicitlyEnabledSubtype */,
                    SUBTYPE_ID_OF_DUMMY_EMOJI_SUBTYPE);

    public static SubtypeSwitcher getInstance() {
        return sInstance;
    }

    public static void init(final Context context) {
        SubtypeLocaleUtils.init(context);
        RichInputMethodManager.init(context);
        sInstance.initialize(context);
    }

    private SubtypeSwitcher() {
        // Intentional empty constructor for singleton.
    }

    private void initialize(final Context context) {
        if (mResources != null) {
            return;
        }
        mResources = context.getResources();
        mRichImm = RichInputMethodManager.getInstance();
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);

        final NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        mIsNetworkConnected = (info != null && info.isConnected());

        onSubtypeChanged(getCurrentSubtype());
        updateParametersOnStartInputView();
    }

    /**
     * Update parameters which are changed outside LatinIME. This parameters affect UI so that they
     * should be updated every time onStartInputView is called.
     */
    public void updateParametersOnStartInputView() {
        final List<InputMethodSubtype> enabledSubtypesOfThisIme =
                mRichImm.getMyEnabledInputMethodSubtypeList(true);
        mLanguageOnSpacebarHelper.updateEnabledSubtypes(enabledSubtypesOfThisIme);
        updateShortcutIME();
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
                mRichImm.getInputMethodManager().getShortcutInputMethodsAndSubtypes();
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
        if (DBG) {
            Log.d(TAG, "Update shortcut IME to : "
                    + (mShortcutInputMethodInfo == null
                            ? "<null>" : mShortcutInputMethodInfo.getId()) + ", "
                    + (mShortcutSubtype == null ? "<null>" : (
                            mShortcutSubtype.getLocale() + ", " + mShortcutSubtype.getMode())));
        }
    }

    // Update the current subtype. LatinIME.onCurrentInputMethodSubtypeChanged calls this function.
    public void onSubtypeChanged(final InputMethodSubtype newSubtype) {
        if (DBG) {
            Log.w(TAG, "onSubtypeChanged: "
                    + SubtypeLocaleUtils.getSubtypeNameForLogging(newSubtype));
        }

        final Locale newLocale = SubtypeLocaleUtils.getSubtypeLocale(newSubtype);
        final Locale systemLocale = mResources.getConfiguration().locale;
        final boolean sameLocale = systemLocale.equals(newLocale);
        final boolean sameLanguage = systemLocale.getLanguage().equals(newLocale.getLanguage());
        final boolean implicitlyEnabled =
                mRichImm.checkIfSubtypeBelongsToThisImeAndImplicitlyEnabled(newSubtype);
        mLanguageOnSpacebarHelper.updateIsSystemLanguageSameAsInputLanguage(
                sameLocale || (sameLanguage && implicitlyEnabled));

        updateShortcutIME();
    }

    ////////////////////////////
    // Shortcut IME functions //
    ////////////////////////////

    public void switchToShortcutIME(final InputMethodService context) {
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
        final InputMethodManager imm = mRichImm.getInputMethodManager();
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                imm.setInputMethodAndSubtype(token, imiId, subtype);
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public boolean isShortcutImeEnabled() {
        updateShortcutIME();
        if (mShortcutInputMethodInfo == null) {
            return false;
        }
        if (mShortcutSubtype == null) {
            return true;
        }
        return mRichImm.checkIfSubtypeBelongsToImeAndEnabled(
                mShortcutInputMethodInfo, mShortcutSubtype);
    }

    public boolean isShortcutImeReady() {
        updateShortcutIME();
        if (mShortcutInputMethodInfo == null) {
            return false;
        }
        if (mShortcutSubtype == null) {
            return true;
        }
        if (mShortcutSubtype.containsExtraValueKey(REQ_NETWORK_CONNECTIVITY)) {
            return mIsNetworkConnected;
        }
        return true;
    }

    public void onNetworkStateChanged(final Intent intent) {
        final boolean noConnection = intent.getBooleanExtra(
                ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
        mIsNetworkConnected = !noConnection;

        KeyboardSwitcher.getInstance().onNetworkStateChanged();
    }

    //////////////////////////////////
    // Subtype Switching functions //
    //////////////////////////////////

    public int getLanguageOnSpacebarFormatType(final InputMethodSubtype subtype) {
        return mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(subtype);
    }

    public boolean isSystemLocaleSameAsLocaleOfAllEnabledSubtypesOfEnabledImes() {
        final Locale systemLocale = mResources.getConfiguration().locale;
        final Set<InputMethodSubtype> enabledSubtypesOfEnabledImes = new HashSet<>();
        final InputMethodManager inputMethodManager = mRichImm.getInputMethodManager();
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

    private static InputMethodSubtype sForcedSubtypeForTesting = null;
    @UsedForTesting
    void forceSubtype(final InputMethodSubtype subtype) {
        sForcedSubtypeForTesting = subtype;
    }

    public Locale getCurrentSubtypeLocale() {
        if (null != sForcedSubtypeForTesting) {
            return LocaleUtils.constructLocaleFromString(sForcedSubtypeForTesting.getLocale());
        }
        return SubtypeLocaleUtils.getSubtypeLocale(getCurrentSubtype());
    }

    public InputMethodSubtype getCurrentSubtype() {
        if (null != sForcedSubtypeForTesting) {
            return sForcedSubtypeForTesting;
        }
        return mRichImm.getCurrentInputMethodSubtype(getNoLanguageSubtype());
    }

    public InputMethodSubtype getNoLanguageSubtype() {
        if (mNoLanguageSubtype == null) {
            mNoLanguageSubtype = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                    SubtypeLocaleUtils.NO_LANGUAGE, SubtypeLocaleUtils.QWERTY);
        }
        if (mNoLanguageSubtype != null) {
            return mNoLanguageSubtype;
        }
        Log.w(TAG, "Can't find any language with QWERTY subtype");
        Log.w(TAG, "No input method subtype found; returning dummy subtype: "
                + DUMMY_NO_LANGUAGE_SUBTYPE);
        return DUMMY_NO_LANGUAGE_SUBTYPE;
    }

    public InputMethodSubtype getEmojiSubtype() {
        if (mEmojiSubtype == null) {
            mEmojiSubtype = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                    SubtypeLocaleUtils.NO_LANGUAGE, SubtypeLocaleUtils.EMOJI);
        }
        if (mEmojiSubtype != null) {
            return mEmojiSubtype;
        }
        Log.w(TAG, "Can't find emoji subtype");
        Log.w(TAG, "No input method subtype found; returning dummy subtype: "
                + DUMMY_EMOJI_SUBTYPE);
        return DUMMY_EMOJI_SUBTYPE;
    }

    public String getCombiningRulesExtraValueOfCurrentSubtype() {
        return SubtypeLocaleUtils.getCombiningRulesExtraValue(getCurrentSubtype());
    }
}
