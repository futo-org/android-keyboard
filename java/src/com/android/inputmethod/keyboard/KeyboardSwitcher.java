/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.accessibility.AccessibleKeyboardViewProxy;
import com.android.inputmethod.keyboard.internal.KeyboardState;
import com.android.inputmethod.latin.DebugSettings;
import com.android.inputmethod.latin.InputView;
import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.Settings;
import com.android.inputmethod.latin.SettingsValues;
import com.android.inputmethod.latin.SubtypeSwitcher;
import com.android.inputmethod.latin.Utils;

public class KeyboardSwitcher implements KeyboardState.SwitchActions,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = KeyboardSwitcher.class.getSimpleName();

    public static final String PREF_KEYBOARD_LAYOUT = "pref_keyboard_layout_20110916";
    private static final int[] KEYBOARD_THEMES = {
        R.style.KeyboardTheme,
        R.style.KeyboardTheme_HighContrast,
        R.style.KeyboardTheme_Stone,
        R.style.KeyboardTheme_Stone_Bold,
        R.style.KeyboardTheme_Gingerbread,
        R.style.KeyboardTheme_IceCreamSandwich,
    };

    private SubtypeSwitcher mSubtypeSwitcher;
    private SharedPreferences mPrefs;
    private boolean mForceNonDistinctMultitouch;

    private InputView mCurrentInputView;
    private LatinKeyboardView mKeyboardView;
    private LatinIME mInputMethodService;
    private Resources mResources;

    private KeyboardState mState;

    private KeyboardSet mKeyboardSet;

    /** mIsAutoCorrectionActive indicates that auto corrected word will be input instead of
     * what user actually typed. */
    private boolean mIsAutoCorrectionActive;

    private int mThemeIndex = -1;
    private Context mThemeContext;

    private static final KeyboardSwitcher sInstance = new KeyboardSwitcher();

    public static KeyboardSwitcher getInstance() {
        return sInstance;
    }

    private KeyboardSwitcher() {
        // Intentional empty constructor for singleton.
    }

    public static void init(LatinIME ims, SharedPreferences prefs) {
        sInstance.initInternal(ims, prefs);
    }

    private void initInternal(LatinIME ims, SharedPreferences prefs) {
        mInputMethodService = ims;
        mResources = ims.getResources();
        mPrefs = prefs;
        mSubtypeSwitcher = SubtypeSwitcher.getInstance();
        mState = new KeyboardState(this);
        setContextThemeWrapper(ims, getKeyboardThemeIndex(ims, prefs));
        prefs.registerOnSharedPreferenceChangeListener(this);
        mForceNonDistinctMultitouch = prefs.getBoolean(
                DebugSettings.FORCE_NON_DISTINCT_MULTITOUCH_KEY, false);
    }

    private static int getKeyboardThemeIndex(Context context, SharedPreferences prefs) {
        final String defaultThemeId = context.getString(R.string.config_default_keyboard_theme_id);
        final String themeId = prefs.getString(PREF_KEYBOARD_LAYOUT, defaultThemeId);
        try {
            final int themeIndex = Integer.valueOf(themeId);
            if (themeIndex >= 0 && themeIndex < KEYBOARD_THEMES.length)
                return themeIndex;
        } catch (NumberFormatException e) {
            // Format error, keyboard theme is default to 0.
        }
        Log.w(TAG, "Illegal keyboard theme in preference: " + themeId + ", default to 0");
        return 0;
    }

    private void setContextThemeWrapper(Context context, int themeIndex) {
        if (mThemeIndex != themeIndex) {
            mThemeIndex = themeIndex;
            mThemeContext = new ContextThemeWrapper(context, KEYBOARD_THEMES[themeIndex]);
            KeyboardSet.clearKeyboardCache();
        }
    }

    public void loadKeyboard(EditorInfo editorInfo, SettingsValues settingsValues) {
        final KeyboardSet.Builder builder = new KeyboardSet.Builder(mThemeContext, editorInfo);
        builder.setScreenGeometry(mThemeContext.getResources().getConfiguration().orientation,
                mThemeContext.getResources().getDisplayMetrics().widthPixels);
        builder.setSubtype(
                mSubtypeSwitcher.getInputLocale(),
                mSubtypeSwitcher.currentSubtypeContainsExtraValueKey(
                        LatinIME.SUBTYPE_EXTRA_VALUE_ASCII_CAPABLE),
                mSubtypeSwitcher.currentSubtypeContainsExtraValueKey(
                        LatinIME.SUBTYPE_EXTRA_VALUE_SUPPORT_TOUCH_POSITION_CORRECTION));
        builder.setOptions(
                settingsValues.isSettingsKeyEnabled(),
                settingsValues.isVoiceKeyEnabled(editorInfo),
                settingsValues.isVoiceKeyOnMain());
        mKeyboardSet = builder.build();
        final KeyboardId mainKeyboardId = mKeyboardSet.getMainKeyboardId();
        try {
            mState.onLoadKeyboard(mResources.getString(R.string.layout_switch_back_symbols));
        } catch (RuntimeException e) {
            Log.w(TAG, "loading keyboard failed: " + mainKeyboardId, e);
            LatinImeLogger.logOnException(mainKeyboardId.toString(), e);
            return;
        }
        // TODO: Should get rid of this special case handling for Phone Number layouts once we
        // have separate layouts with unique KeyboardIds for alphabet and alphabet-shifted
        // respectively.
        if (mainKeyboardId.isPhoneKeyboard()) {
            mState.setSymbolsKeyboard();
        }
    }

    public void saveKeyboardState() {
        if (isKeyboardAvailable()) {
            mState.onSaveKeyboardState();
        }
    }

    public void onFinishInputView() {
        mIsAutoCorrectionActive = false;
    }

    public void onHideWindow() {
        mIsAutoCorrectionActive = false;
    }

    private void setKeyboard(final Keyboard keyboard) {
        final Keyboard oldKeyboard = mKeyboardView.getKeyboard();
        mKeyboardView.setKeyboard(keyboard);
        mCurrentInputView.setKeyboardGeometry(keyboard.mTopPadding);
        mKeyboardView.setKeyPreviewPopupEnabled(
                SettingsValues.isKeyPreviewPopupEnabled(mPrefs, mResources),
                SettingsValues.getKeyPreviewPopupDismissDelay(mPrefs, mResources));
        mKeyboardView.updateAutoCorrectionState(mIsAutoCorrectionActive);
        // If the cached keyboard had been switched to another keyboard while the language was
        // displayed on its spacebar, it might have had arbitrary text fade factor. In such
        // case, we should reset the text fade factor. It is also applicable to shortcut key.
        mKeyboardView.updateSpacebar(0.0f,
                mSubtypeSwitcher.needsToDisplayLanguage(keyboard.mId.mLocale));
        mKeyboardView.updateShortcutKey(mSubtypeSwitcher.isShortcutImeReady());
        final boolean localeChanged = (oldKeyboard == null)
                || !keyboard.mId.mLocale.equals(oldKeyboard.mId.mLocale);
        mInputMethodService.mHandler.startDisplayLanguageOnSpacebar(localeChanged);
    }

    public boolean isAlphabetMode() {
        final Keyboard keyboard = getKeyboard();
        return keyboard != null && keyboard.mId.isAlphabetKeyboard();
    }

    public boolean isInputViewShown() {
        return mCurrentInputView != null && mCurrentInputView.isShown();
    }

    public boolean isShiftedOrShiftLocked() {
        final Keyboard keyboard = getKeyboard();
        return keyboard != null && keyboard.isShiftedOrShiftLocked();
    }

    public boolean isManualTemporaryUpperCase() {
        final Keyboard keyboard = getKeyboard();
        return keyboard != null && keyboard.isManualTemporaryUpperCase();
    }

    public boolean isKeyboardAvailable() {
        if (mKeyboardView != null)
            return mKeyboardView.getKeyboard() != null;
        return false;
    }

    public Keyboard getKeyboard() {
        if (mKeyboardView != null) {
            return mKeyboardView.getKeyboard();
        }
        return null;
    }

    /**
     * Update keyboard shift state triggered by connected EditText status change.
     */
    public void updateShiftState() {
        mState.onUpdateShiftState(mInputMethodService.getCurrentAutoCapsState());
    }

    public void onPressKey(int code) {
        mState.onPressKey(code);
    }

    public void onReleaseKey(int code, boolean withSliding) {
        mState.onReleaseKey(code, withSliding);
    }

    public void onCancelInput() {
        mState.onCancelInput(isSinglePointer());
    }

    // TODO: Remove these constants.
    private static final int ALPHABET_UNSHIFTED = 0;
    private static final int ALPHABET_MANUAL_SHIFTED = 1;
    private static final int ALPHABET_AUTOMATIC_SHIFTED = 2;
    private static final int ALPHABET_SHIFT_LOCKED = 3;
    private static final int ALPHABET_SHIFT_LOCK_SHIFTED = 4;

    // TODO: Remove this method.
    private void updateAlphabetKeyboardShiftState(int shiftMode) {
        mInputMethodService.mHandler.cancelUpdateShiftState();
        Keyboard keyboard = getKeyboard();
        if (keyboard == null)
            return;
        switch (shiftMode) {
        case ALPHABET_UNSHIFTED:
            keyboard.setShifted(false);
            break;
        case ALPHABET_MANUAL_SHIFTED:
            keyboard.setShifted(true);
            break;
        case ALPHABET_AUTOMATIC_SHIFTED:
            keyboard.setAutomaticTemporaryUpperCase();
            break;
        case ALPHABET_SHIFT_LOCKED:
            keyboard.setShiftLocked(true);
            break;
        case ALPHABET_SHIFT_LOCK_SHIFTED:
            keyboard.setShiftLocked(true);
            keyboard.setShifted(true);
            break;
        }
        mKeyboardView.invalidateAllKeys();
        if (shiftMode != ALPHABET_SHIFT_LOCKED) {
            // To be able to turn off caps lock by "double tap" on shift key, we should ignore
            // the second tap of the "double tap" from now for a while because we just have
            // already turned off caps lock above.
            mKeyboardView.startIgnoringDoubleTap();
        }
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setAlphabetKeyboard() {
        setKeyboard(mKeyboardSet.getMainKeyboard());
        updateAlphabetKeyboardShiftState(ALPHABET_UNSHIFTED);
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setAlphabetManualShiftedKeyboard() {
        setKeyboard(mKeyboardSet.getMainKeyboard());
        updateAlphabetKeyboardShiftState(ALPHABET_MANUAL_SHIFTED);
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setAlphabetAutomaticShiftedKeyboard() {
        setKeyboard(mKeyboardSet.getMainKeyboard());
        updateAlphabetKeyboardShiftState(ALPHABET_AUTOMATIC_SHIFTED);
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setAlphabetShiftLockedKeyboard() {
        setKeyboard(mKeyboardSet.getMainKeyboard());
        updateAlphabetKeyboardShiftState(ALPHABET_SHIFT_LOCKED);
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setAlphabetShiftLockShiftedKeyboard() {
        setKeyboard(mKeyboardSet.getMainKeyboard());
        updateAlphabetKeyboardShiftState(ALPHABET_SHIFT_LOCK_SHIFTED);
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setSymbolsKeyboard() {
        setKeyboard(mKeyboardSet.getSymbolsKeyboard());
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setSymbolsShiftedKeyboard() {
        setKeyboard(mKeyboardSet.getSymbolsShiftedKeyboard());
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void requestUpdatingShiftState() {
        mState.onUpdateShiftState(mInputMethodService.getCurrentAutoCapsState());
    }

    public boolean isInMomentarySwitchState() {
        return mState.isInMomentarySwitchState();
    }

    public boolean isVibrateAndSoundFeedbackRequired() {
        return mKeyboardView != null && !mKeyboardView.isInSlidingKeyInput();
    }

    private boolean isSinglePointer() {
        return mKeyboardView != null && mKeyboardView.getPointerCount() == 1;
    }

    public boolean hasDistinctMultitouch() {
        return mKeyboardView != null && mKeyboardView.hasDistinctMultitouch();
    }

    /**
     * Updates state machine to figure out when to automatically switch back to the previous mode.
     */
    public void onCodeInput(int code) {
        mState.onCodeInput(code, isSinglePointer(), mInputMethodService.getCurrentAutoCapsState());
    }

    public LatinKeyboardView getKeyboardView() {
        return mKeyboardView;
    }

    public View onCreateInputView() {
        return createInputView(mThemeIndex, true);
    }

    private View createInputView(final int newThemeIndex, final boolean forceRecreate) {
        if (mCurrentInputView != null && mThemeIndex == newThemeIndex && !forceRecreate)
            return mCurrentInputView;

        if (mKeyboardView != null) {
            mKeyboardView.closing();
        }

        final int oldThemeIndex = mThemeIndex;
        Utils.GCUtils.getInstance().reset();
        boolean tryGC = true;
        for (int i = 0; i < Utils.GCUtils.GC_TRY_LOOP_MAX && tryGC; ++i) {
            try {
                setContextThemeWrapper(mInputMethodService, newThemeIndex);
                mCurrentInputView = (InputView)LayoutInflater.from(mThemeContext).inflate(
                        R.layout.input_view, null);
                tryGC = false;
            } catch (OutOfMemoryError e) {
                Log.w(TAG, "load keyboard failed: " + e);
                tryGC = Utils.GCUtils.getInstance().tryGCOrWait(
                        oldThemeIndex + "," + newThemeIndex, e);
            } catch (InflateException e) {
                Log.w(TAG, "load keyboard failed: " + e);
                tryGC = Utils.GCUtils.getInstance().tryGCOrWait(
                        oldThemeIndex + "," + newThemeIndex, e);
            }
        }

        mKeyboardView = (LatinKeyboardView) mCurrentInputView.findViewById(R.id.keyboard_view);
        mKeyboardView.setKeyboardActionListener(mInputMethodService);
        if (mForceNonDistinctMultitouch) {
            mKeyboardView.setDistinctMultitouch(false);
        }

        // This always needs to be set since the accessibility state can
        // potentially change without the input view being re-created.
        AccessibleKeyboardViewProxy.setView(mKeyboardView);

        return mCurrentInputView;
    }

    private void postSetInputView(final View newInputView) {
        final LatinIME latinIme = mInputMethodService;
        latinIme.mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (newInputView != null) {
                    latinIme.setInputView(newInputView);
                }
                latinIme.updateInputViewShown();
            }
        });
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (PREF_KEYBOARD_LAYOUT.equals(key)) {
            final int themeIndex = getKeyboardThemeIndex(mInputMethodService, sharedPreferences);
            postSetInputView(createInputView(themeIndex, false));
        } else if (Settings.PREF_SHOW_SETTINGS_KEY.equals(key)) {
            postSetInputView(createInputView(mThemeIndex, true));
        }
    }

    public void onNetworkStateChanged() {
        if (mKeyboardView != null) {
            mKeyboardView.updateShortcutKey(SubtypeSwitcher.getInstance().isShortcutImeReady());
        }
    }

    public void onAutoCorrectionStateChanged(boolean isAutoCorrection) {
        if (mIsAutoCorrectionActive != isAutoCorrection) {
            mIsAutoCorrectionActive = isAutoCorrection;
            if (mKeyboardView != null) {
                mKeyboardView.updateAutoCorrectionState(isAutoCorrection);
            }
        }
    }
}
