/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.inputmethod.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.accessibility.AccessibleKeyboardViewProxy;
import com.android.inputmethod.keyboard.KeyboardLayoutSet.KeyboardLayoutSetException;
import com.android.inputmethod.keyboard.PointerTracker.TimerProxy;
import com.android.inputmethod.keyboard.internal.KeyboardState;
import com.android.inputmethod.latin.AudioAndHapticFeedbackManager;
import com.android.inputmethod.latin.InputView;
import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.RichInputMethodManager;
import com.android.inputmethod.latin.Settings;
import com.android.inputmethod.latin.SettingsValues;
import com.android.inputmethod.latin.SubtypeSwitcher;
import com.android.inputmethod.latin.WordComposer;

public final class KeyboardSwitcher implements KeyboardState.SwitchActions {
    private static final String TAG = KeyboardSwitcher.class.getSimpleName();

    public static final String PREF_KEYBOARD_LAYOUT = "pref_keyboard_layout_20110916";

    static final class KeyboardTheme {
        public final int mThemeId;
        public final int mStyleId;

        // Note: The themeId should be aligned with "themeId" attribute of Keyboard style
        // in values/style.xml.
        public KeyboardTheme(final int themeId, final int styleId) {
            mThemeId = themeId;
            mStyleId = styleId;
        }
    }

    private static final KeyboardTheme[] KEYBOARD_THEMES = {
        new KeyboardTheme(0, R.style.KeyboardTheme),
        new KeyboardTheme(1, R.style.KeyboardTheme_HighContrast),
        new KeyboardTheme(6, R.style.KeyboardTheme_Stone),
        new KeyboardTheme(7, R.style.KeyboardTheme_Stone_Bold),
        new KeyboardTheme(8, R.style.KeyboardTheme_Gingerbread),
        new KeyboardTheme(5, R.style.KeyboardTheme_IceCreamSandwich),
    };

    private final AudioAndHapticFeedbackManager mFeedbackManager =
            AudioAndHapticFeedbackManager.getInstance();
    private SubtypeSwitcher mSubtypeSwitcher;
    private SharedPreferences mPrefs;

    private InputView mCurrentInputView;
    private MainKeyboardView mKeyboardView;
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

    public static void init(final LatinIME latinIme) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(latinIme);
        sInstance.initInternal(latinIme, prefs);
    }

    private void initInternal(final LatinIME latinIme, final SharedPreferences prefs) {
        mLatinIME = latinIme;
        mResources = latinIme.getResources();
        mPrefs = prefs;
        mSubtypeSwitcher = SubtypeSwitcher.getInstance();
        mState = new KeyboardState(this);
        setContextThemeWrapper(latinIme, getKeyboardTheme(latinIme, prefs));
    }

    private static KeyboardTheme getKeyboardTheme(final Context context,
            final SharedPreferences prefs) {
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

    private void setContextThemeWrapper(final Context context, final KeyboardTheme keyboardTheme) {
        if (mThemeContext == null || mKeyboardTheme.mThemeId != keyboardTheme.mThemeId) {
            mKeyboardTheme = keyboardTheme;
            mThemeContext = new ContextThemeWrapper(context, keyboardTheme.mStyleId);
            KeyboardLayoutSet.clearKeyboardCache();
        }
    }

    public void loadKeyboard(final EditorInfo editorInfo, final SettingsValues settingsValues) {
        final KeyboardLayoutSet.Builder builder = new KeyboardLayoutSet.Builder(
                mThemeContext, editorInfo);
        final Resources res = mThemeContext.getResources();
        final DisplayMetrics dm = res.getDisplayMetrics();
        builder.setScreenGeometry(dm.widthPixels, dm.heightPixels);
        builder.setSubtype(mSubtypeSwitcher.getCurrentSubtype());
        builder.setOptions(
                settingsValues.isVoiceKeyEnabled(editorInfo),
                settingsValues.isVoiceKeyOnMain(),
                settingsValues.isLanguageSwitchKeyEnabled());
        mKeyboardLayoutSet = builder.build();
        try {
            mState.onLoadKeyboard();
            mFeedbackManager.onSettingsChanged(settingsValues);
        } catch (KeyboardLayoutSetException e) {
            Log.w(TAG, "loading keyboard failed: " + e.mKeyboardId, e.getCause());
            LatinImeLogger.logOnException(e.mKeyboardId.toString(), e.getCause());
            return;
        }
    }

    public void onRingerModeChanged() {
        mFeedbackManager.onRingerModeChanged();
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
        final MainKeyboardView keyboardView = mKeyboardView;
        final Keyboard oldKeyboard = keyboardView.getKeyboard();
        keyboardView.setKeyboard(keyboard);
        mCurrentInputView.setKeyboardGeometry(keyboard.mTopPadding);
        keyboardView.setKeyPreviewPopupEnabled(
                Settings.readKeyPreviewPopupEnabled(mPrefs, mResources),
                Settings.readKeyPreviewPopupDismissDelay(mPrefs, mResources));
        keyboardView.updateAutoCorrectionState(mIsAutoCorrectionActive);
        keyboardView.updateShortcutKey(mSubtypeSwitcher.isShortcutImeReady());
        final boolean subtypeChanged = (oldKeyboard == null)
                || !keyboard.mId.mLocale.equals(oldKeyboard.mId.mLocale);
        final boolean needsToDisplayLanguage = mSubtypeSwitcher.needsToDisplayLanguage(
                keyboard.mId.mLocale);
        keyboardView.startDisplayLanguageOnSpacebar(subtypeChanged, needsToDisplayLanguage,
                RichInputMethodManager.getInstance().hasMultipleEnabledIMEsOrSubtypes(true));
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
        mState.onUpdateShiftState(mLatinIME.getCurrentAutoCapsState(),
                mLatinIME.getCurrentRecapitalizeState());
    }

    // TODO: Remove this method. Come up with a more comprehensive way to reset the keyboard layout
    // when a keyboard layout set doesn't get reloaded in LatinIME.onStartInputViewInternal().
    public void resetKeyboardStateToAlphabet() {
        mState.onResetKeyboardStateToAlphabet();
    }

    public void onPressKey(final int code, final boolean isSinglePointer) {
        if (isVibrateAndSoundFeedbackRequired()) {
            mFeedbackManager.hapticAndAudioFeedback(code, mKeyboardView);
        }
        mState.onPressKey(code, isSinglePointer, mLatinIME.getCurrentAutoCapsState());
    }

    public void onReleaseKey(final int code, final boolean withSliding) {
        mState.onReleaseKey(code, withSliding);
    }

    public void onFinishSlidingInput() {
        mState.onFinishSlidingInput();
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
        mState.onUpdateShiftState(mLatinIME.getCurrentAutoCapsState(),
                mLatinIME.getCurrentRecapitalizeState());
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void startDoubleTapTimer() {
        final MainKeyboardView keyboardView = getMainKeyboardView();
        if (keyboardView != null) {
            final TimerProxy timer = keyboardView.getTimerProxy();
            timer.startDoubleTapTimer();
        }
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void cancelDoubleTapTimer() {
        final MainKeyboardView keyboardView = getMainKeyboardView();
        if (keyboardView != null) {
            final TimerProxy timer = keyboardView.getTimerProxy();
            timer.cancelDoubleTapTimer();
        }
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public boolean isInDoubleTapTimeout() {
        final MainKeyboardView keyboardView = getMainKeyboardView();
        return (keyboardView != null)
                ? keyboardView.getTimerProxy().isInDoubleTapTimeout() : false;
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void startLongPressTimer(final int code) {
        final MainKeyboardView keyboardView = getMainKeyboardView();
        if (keyboardView != null) {
            final TimerProxy timer = keyboardView.getTimerProxy();
            timer.startLongPressTimer(code);
        }
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void cancelLongPressTimer() {
        final MainKeyboardView keyboardView = getMainKeyboardView();
        if (keyboardView != null) {
            final TimerProxy timer = keyboardView.getTimerProxy();
            timer.cancelLongPressTimer();
        }
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void hapticAndAudioFeedback(final int code) {
        mFeedbackManager.hapticAndAudioFeedback(code, mKeyboardView);
    }

    public void onLongPressTimeout(final int code) {
        mState.onLongPressTimeout(code);
    }

    public boolean isInMomentarySwitchState() {
        return mState.isInMomentarySwitchState();
    }

    private boolean isVibrateAndSoundFeedbackRequired() {
        return mKeyboardView != null && !mKeyboardView.isInSlidingKeyInput();
    }

    /**
     * Updates state machine to figure out when to automatically switch back to the previous mode.
     */
    public void onCodeInput(final int code) {
        mState.onCodeInput(code, mLatinIME.getCurrentAutoCapsState());
    }

    public MainKeyboardView getMainKeyboardView() {
        return mKeyboardView;
    }

    public View onCreateInputView(final boolean isHardwareAcceleratedDrawingEnabled) {
        if (mKeyboardView != null) {
            mKeyboardView.closing();
        }

        setContextThemeWrapper(mLatinIME, mKeyboardTheme);
        mCurrentInputView = (InputView)LayoutInflater.from(mThemeContext).inflate(
                R.layout.input_view, null);

        mKeyboardView = (MainKeyboardView) mCurrentInputView.findViewById(R.id.keyboard_view);
        if (isHardwareAcceleratedDrawingEnabled) {
            mKeyboardView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            // TODO: Should use LAYER_TYPE_SOFTWARE when hardware acceleration is off?
        }
        mKeyboardView.setKeyboardActionListener(mLatinIME);

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

    public void onAutoCorrectionStateChanged(final boolean isAutoCorrection) {
        if (mIsAutoCorrectionActive != isAutoCorrection) {
            mIsAutoCorrectionActive = isAutoCorrection;
            if (mKeyboardView != null) {
                mKeyboardView.updateAutoCorrectionState(isAutoCorrection);
            }
        }
    }

    public int getKeyboardShiftMode() {
        final Keyboard keyboard = getKeyboard();
        if (keyboard == null) {
            return WordComposer.CAPS_MODE_OFF;
        }
        switch (keyboard.mId.mElementId) {
        case KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED:
        case KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED:
            return WordComposer.CAPS_MODE_MANUAL_SHIFT_LOCKED;
        case KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED:
            return WordComposer.CAPS_MODE_MANUAL_SHIFTED;
        case KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED:
            return WordComposer.CAPS_MODE_AUTO_SHIFTED;
        default:
            return WordComposer.CAPS_MODE_OFF;
        }
    }
}
