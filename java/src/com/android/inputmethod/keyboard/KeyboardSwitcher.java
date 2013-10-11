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
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.accessibility.AccessibleKeyboardViewProxy;
import com.android.inputmethod.keyboard.KeyboardLayoutSet.KeyboardLayoutSetException;
import com.android.inputmethod.keyboard.internal.KeyboardState;
import com.android.inputmethod.latin.InputView;
import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.RichInputMethodManager;
import com.android.inputmethod.latin.SubtypeSwitcher;
import com.android.inputmethod.latin.WordComposer;
import com.android.inputmethod.latin.settings.Settings;
import com.android.inputmethod.latin.settings.SettingsValues;
import com.android.inputmethod.latin.utils.ResourceUtils;

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
        new KeyboardTheme(0, R.style.KeyboardTheme_ICS),
        new KeyboardTheme(1, R.style.KeyboardTheme_GB),
    };

    private SubtypeSwitcher mSubtypeSwitcher;
    private SharedPreferences mPrefs;

    private InputView mCurrentInputView;
    private View mMainKeyboardFrame;
    private MainKeyboardView mKeyboardView;
    private EmojiPalettesView mEmojiPalettesView;
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
        Log.w(TAG, "Illegal keyboard theme in preference: " + themeIndex + ", default to "
                + defaultIndex);
        return KEYBOARD_THEMES[Integer.valueOf(defaultIndex)];
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
        final int keyboardWidth = ResourceUtils.getDefaultKeyboardWidth(res);
        final int keyboardHeight = ResourceUtils.getDefaultKeyboardHeight(res);
        builder.setKeyboardGeometry(keyboardWidth, keyboardHeight);
        builder.setSubtype(mSubtypeSwitcher.getCurrentSubtype());
        builder.setOptions(
                settingsValues.isVoiceKeyEnabled(editorInfo),
                true /* always show a voice key on the main keyboard */,
                settingsValues.isLanguageSwitchKeyEnabled());
        mKeyboardLayoutSet = builder.build();
        try {
            mState.onLoadKeyboard();
        } catch (KeyboardLayoutSetException e) {
            Log.w(TAG, "loading keyboard failed: " + e.mKeyboardId, e.getCause());
            LatinImeLogger.logOnException(e.mKeyboardId.toString(), e.getCause());
            return;
        }
    }

    public void saveKeyboardState() {
        if (getKeyboard() != null || isShowingEmojiPalettes()) {
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
        // Make {@link MainKeyboardView} visible and hide {@link EmojiPalettesView}.
        setMainKeyboardFrame();
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

    private void setMainKeyboardFrame() {
        mMainKeyboardFrame.setVisibility(View.VISIBLE);
        mEmojiPalettesView.setVisibility(View.GONE);
        mEmojiPalettesView.stopEmojiPalettes();
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setEmojiKeyboard() {
        mMainKeyboardFrame.setVisibility(View.GONE);
        mEmojiPalettesView.startEmojiPalettes();
        mEmojiPalettesView.setVisibility(View.VISIBLE);
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
    public void startDoubleTapShiftKeyTimer() {
        final MainKeyboardView keyboardView = getMainKeyboardView();
        if (keyboardView != null) {
            keyboardView.startDoubleTapShiftKeyTimer();
        }
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void cancelDoubleTapShiftKeyTimer() {
        final MainKeyboardView keyboardView = getMainKeyboardView();
        if (keyboardView != null) {
            keyboardView.cancelDoubleTapShiftKeyTimer();
        }
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public boolean isInDoubleTapShiftKeyTimeout() {
        final MainKeyboardView keyboardView = getMainKeyboardView();
        return keyboardView != null && keyboardView.isInDoubleTapShiftKeyTimeout();
    }

    /**
     * Updates state machine to figure out when to automatically switch back to the previous mode.
     */
    public void onCodeInput(final int code) {
        mState.onCodeInput(code, mLatinIME.getCurrentAutoCapsState());
    }

    private boolean isShowingMainKeyboard() {
        return null != mKeyboardView && mKeyboardView.isShown();
    }

    public boolean isShowingEmojiPalettes() {
        return mEmojiPalettesView != null && mEmojiPalettesView.isShown();
    }

    public boolean isShowingMoreKeysPanel() {
        if (isShowingEmojiPalettes()) {
            return false;
        }
        return mKeyboardView.isShowingMoreKeysPanel();
    }

    public View getVisibleKeyboardView() {
        if (isShowingEmojiPalettes()) {
            return mEmojiPalettesView;
        }
        return mKeyboardView;
    }

    public MainKeyboardView getMainKeyboardView() {
        return mKeyboardView;
    }

    public void deallocateMemory() {
        if (mKeyboardView != null) {
            mKeyboardView.cancelAllOngoingEvents();
            mKeyboardView.deallocateMemory();
        }
        if (mEmojiPalettesView != null) {
            mEmojiPalettesView.stopEmojiPalettes();
        }
    }

    public boolean isShowingMainKeyboardOrEmojiPalettes() {
        return isShowingMainKeyboard() || isShowingEmojiPalettes();
    }

    public View onCreateInputView(final boolean isHardwareAcceleratedDrawingEnabled) {
        if (mKeyboardView != null) {
            mKeyboardView.closing();
        }

        setContextThemeWrapper(mLatinIME, mKeyboardTheme);
        mCurrentInputView = (InputView)LayoutInflater.from(mThemeContext).inflate(
                R.layout.input_view, null);
        mMainKeyboardFrame = mCurrentInputView.findViewById(R.id.main_keyboard_frame);
        mEmojiPalettesView = (EmojiPalettesView)mCurrentInputView.findViewById(
                R.id.emoji_keyboard_view);

        mKeyboardView = (MainKeyboardView) mCurrentInputView.findViewById(R.id.keyboard_view);
        mKeyboardView.setHardwareAcceleratedDrawingEnabled(isHardwareAcceleratedDrawingEnabled);
        mKeyboardView.setKeyboardActionListener(mLatinIME);
        mEmojiPalettesView.setHardwareAcceleratedDrawingEnabled(
                isHardwareAcceleratedDrawingEnabled);
        mEmojiPalettesView.setKeyboardActionListener(mLatinIME);

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
