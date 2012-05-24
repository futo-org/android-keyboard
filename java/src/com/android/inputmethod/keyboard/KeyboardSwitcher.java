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
import com.android.inputmethod.keyboard.KeyboardLayoutSet.KeyboardLayoutSetException;
import com.android.inputmethod.keyboard.PointerTracker.TimerProxy;
import com.android.inputmethod.keyboard.internal.KeyboardState;
import com.android.inputmethod.latin.DebugSettings;
import com.android.inputmethod.latin.ImfUtils;
import com.android.inputmethod.latin.InputView;
import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SettingsValues;
import com.android.inputmethod.latin.SubtypeSwitcher;
import com.android.inputmethod.latin.Utils;

public class KeyboardSwitcher implements KeyboardState.SwitchActions {
    private static final String TAG = KeyboardSwitcher.class.getSimpleName();

    public static final String PREF_KEYBOARD_LAYOUT = "pref_keyboard_layout_20110916";

    static class KeyboardTheme {
        public final String mName;
        public final int mThemeId;
        public final int mStyleId;

        public KeyboardTheme(String name, int themeId, int styleId) {
            mName = name;
            mThemeId = themeId;
            mStyleId = styleId;
        }
    }

    private static final KeyboardTheme[] KEYBOARD_THEMES = {
        new KeyboardTheme("Basic",            0, R.style.KeyboardTheme),
        new KeyboardTheme("HighContrast",     1, R.style.KeyboardTheme_HighContrast),
        new KeyboardTheme("Stone",            6, R.style.KeyboardTheme_Stone),
        new KeyboardTheme("Stne.Bold",        7, R.style.KeyboardTheme_Stone_Bold),
        new KeyboardTheme("GingerBread",      8, R.style.KeyboardTheme_Gingerbread),
        new KeyboardTheme("IceCreamSandwich", 5, R.style.KeyboardTheme_IceCreamSandwich),
    };

    private SubtypeSwitcher mSubtypeSwitcher;
    private SharedPreferences mPrefs;
    private boolean mForceNonDistinctMultitouch;

    private InputView mCurrentInputView;
    private LatinKeyboardView mKeyboardView;
    private LatinIME mLatinIME;
    private Resources mResources;

    private KeyboardState mState;

    private KeyboardLayoutSet mKeyboardLayoutSet;

    /** mIsAutoCorrectionActive indicates that auto corrected word will be input instead of
     * what user actually typed. */
    private boolean mIsAutoCorrectionActive;

    private KeyboardTheme mKeyboardTheme = KEYBOARD_THEMES[0];
    private Context mThemeContext;

    private static final KeyboardSwitcher sInstance = new KeyboardSwitcher();

    public static KeyboardSwitcher getInstance() {
        return sInstance;
    }

    private KeyboardSwitcher() {
        // Intentional empty constructor for singleton.
    }

    public static void init(LatinIME latinIme, SharedPreferences prefs) {
        sInstance.initInternal(latinIme, prefs);
    }

    private void initInternal(LatinIME latinIme, SharedPreferences prefs) {
        mLatinIME = latinIme;
        mResources = latinIme.getResources();
        mPrefs = prefs;
        mSubtypeSwitcher = SubtypeSwitcher.getInstance();
        mState = new KeyboardState(this);
        setContextThemeWrapper(latinIme, getKeyboardTheme(latinIme, prefs));
        mForceNonDistinctMultitouch = prefs.getBoolean(
                DebugSettings.FORCE_NON_DISTINCT_MULTITOUCH_KEY, false);
    }

    private static KeyboardTheme getKeyboardTheme(Context context, SharedPreferences prefs) {
        final String defaultIndex = context.getString(R.string.config_default_keyboard_theme_index);
        final String themeIndex = prefs.getString(PREF_KEYBOARD_LAYOUT, defaultIndex);
        try {
            final int index = Integer.valueOf(themeIndex);
            if (index >= 0 && index < KEYBOARD_THEMES.length) {
                return KEYBOARD_THEMES[index];
            }
        } catch (NumberFormatException e) {
            // Format error, keyboard theme is default to 0.
        }
        Log.w(TAG, "Illegal keyboard theme in preference: " + themeIndex + ", default to 0");
        return KEYBOARD_THEMES[0];
    }

    private void setContextThemeWrapper(Context context, KeyboardTheme keyboardTheme) {
        if (mKeyboardTheme.mThemeId != keyboardTheme.mThemeId) {
            mKeyboardTheme = keyboardTheme;
            mThemeContext = new ContextThemeWrapper(context, keyboardTheme.mStyleId);
            KeyboardLayoutSet.clearKeyboardCache();
        }
    }

    public void loadKeyboard(EditorInfo editorInfo, SettingsValues settingsValues) {
        final KeyboardLayoutSet.Builder builder = new KeyboardLayoutSet.Builder(
                mThemeContext, editorInfo);
        builder.setScreenGeometry(mThemeContext.getResources().getConfiguration().orientation,
                mThemeContext.getResources().getDisplayMetrics().widthPixels);
        builder.setSubtype(mSubtypeSwitcher.getCurrentSubtype());
        builder.setOptions(
                settingsValues.isVoiceKeyEnabled(editorInfo),
                settingsValues.isVoiceKeyOnMain(),
                settingsValues.isLanguageSwitchKeyEnabled(mThemeContext));
        mKeyboardLayoutSet = builder.build();
        try {
            mState.onLoadKeyboard(mResources.getString(R.string.layout_switch_back_symbols));
        } catch (KeyboardLayoutSetException e) {
            Log.w(TAG, "loading keyboard failed: " + e.mKeyboardId, e.getCause());
            LatinImeLogger.logOnException(e.mKeyboardId.toString(), e.getCause());
            return;
        }
    }

    public void saveKeyboardState() {
        if (getKeyboard() != null) {
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
        mKeyboardView.updateShortcutKey(mSubtypeSwitcher.isShortcutImeReady());
        final boolean subtypeChanged = (oldKeyboard == null)
                || !keyboard.mId.mLocale.equals(oldKeyboard.mId.mLocale);
        final boolean needsToDisplayLanguage = mSubtypeSwitcher.needsToDisplayLanguage(
                keyboard.mId.mLocale);
        mKeyboardView.startDisplayLanguageOnSpacebar(subtypeChanged, needsToDisplayLanguage,
                ImfUtils.hasMultipleEnabledIMEsOrSubtypes(mLatinIME, true));
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
        mState.onUpdateShiftState(mLatinIME.getCurrentAutoCapsState());
    }

    public void onPressKey(int code) {
        if (isVibrateAndSoundFeedbackRequired()) {
            mLatinIME.hapticAndAudioFeedback(code);
        }
        mState.onPressKey(code, isSinglePointer(), mLatinIME.getCurrentAutoCapsState());
    }

    public void onReleaseKey(int code, boolean withSliding) {
        mState.onReleaseKey(code, withSliding);
    }

    public void onCancelInput() {
        mState.onCancelInput(isSinglePointer());
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setAlphabetKeyboard() {
        setKeyboard(mKeyboardLayoutSet.getKeyboard(KeyboardId.ELEMENT_ALPHABET));
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setAlphabetManualShiftedKeyboard() {
        setKeyboard(mKeyboardLayoutSet.getKeyboard(KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED));
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setAlphabetAutomaticShiftedKeyboard() {
        setKeyboard(mKeyboardLayoutSet.getKeyboard(KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED));
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setAlphabetShiftLockedKeyboard() {
        setKeyboard(mKeyboardLayoutSet.getKeyboard(KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED));
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setAlphabetShiftLockShiftedKeyboard() {
        setKeyboard(mKeyboardLayoutSet.getKeyboard(KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED));
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setSymbolsKeyboard() {
        setKeyboard(mKeyboardLayoutSet.getKeyboard(KeyboardId.ELEMENT_SYMBOLS));
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setSymbolsShiftedKeyboard() {
        setKeyboard(mKeyboardLayoutSet.getKeyboard(KeyboardId.ELEMENT_SYMBOLS_SHIFTED));
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void requestUpdatingShiftState() {
        mState.onUpdateShiftState(mLatinIME.getCurrentAutoCapsState());
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void startDoubleTapTimer() {
        final LatinKeyboardView keyboardView = getKeyboardView();
        if (keyboardView != null) {
            final TimerProxy timer = keyboardView.getTimerProxy();
            timer.startDoubleTapTimer();
        }
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void cancelDoubleTapTimer() {
        final LatinKeyboardView keyboardView = getKeyboardView();
        if (keyboardView != null) {
            final TimerProxy timer = keyboardView.getTimerProxy();
            timer.cancelDoubleTapTimer();
        }
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public boolean isInDoubleTapTimeout() {
        final LatinKeyboardView keyboardView = getKeyboardView();
        return (keyboardView != null)
                ? keyboardView.getTimerProxy().isInDoubleTapTimeout() : false;
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void startLongPressTimer(int code) {
        final LatinKeyboardView keyboardView = getKeyboardView();
        if (keyboardView != null) {
            final TimerProxy timer = keyboardView.getTimerProxy();
            timer.startLongPressTimer(code);
        }
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void cancelLongPressTimer() {
        final LatinKeyboardView keyboardView = getKeyboardView();
        if (keyboardView != null) {
            final TimerProxy timer = keyboardView.getTimerProxy();
            timer.cancelLongPressTimer();
        }
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void hapticAndAudioFeedback(int code) {
        mLatinIME.hapticAndAudioFeedback(code);
    }

    public void onLongPressTimeout(int code) {
        mState.onLongPressTimeout(code);
    }

    public boolean isInMomentarySwitchState() {
        return mState.isInMomentarySwitchState();
    }

    private boolean isVibrateAndSoundFeedbackRequired() {
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
        mState.onCodeInput(code, isSinglePointer(), mLatinIME.getCurrentAutoCapsState());
    }

    public LatinKeyboardView getKeyboardView() {
        return mKeyboardView;
    }

    public View onCreateInputView() {
        if (mKeyboardView != null) {
            mKeyboardView.closing();
        }

        Utils.GCUtils.getInstance().reset();
        boolean tryGC = true;
        for (int i = 0; i < Utils.GCUtils.GC_TRY_LOOP_MAX && tryGC; ++i) {
            try {
                setContextThemeWrapper(mLatinIME, mKeyboardTheme);
                mCurrentInputView = (InputView)LayoutInflater.from(mThemeContext).inflate(
                        R.layout.input_view, null);
                tryGC = false;
            } catch (OutOfMemoryError e) {
                Log.w(TAG, "load keyboard failed: " + e);
                tryGC = Utils.GCUtils.getInstance().tryGCOrWait(mKeyboardTheme.mName, e);
            } catch (InflateException e) {
                Log.w(TAG, "load keyboard failed: " + e);
                tryGC = Utils.GCUtils.getInstance().tryGCOrWait(mKeyboardTheme.mName, e);
            }
        }

        mKeyboardView = (LatinKeyboardView) mCurrentInputView.findViewById(R.id.keyboard_view);
        mKeyboardView.setKeyboardActionListener(mLatinIME);
        if (mForceNonDistinctMultitouch) {
            mKeyboardView.setDistinctMultitouch(false);
        }

        // This always needs to be set since the accessibility state can
        // potentially change without the input view being re-created.
        AccessibleKeyboardViewProxy.getInstance().setView(mKeyboardView);

        return mCurrentInputView;
    }

    public void onNetworkStateChanged() {
        if (mKeyboardView != null) {
            mKeyboardView.updateShortcutKey(mSubtypeSwitcher.isShortcutImeReady());
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
