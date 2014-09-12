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

import com.android.inputmethod.compat.InputMethodServiceCompatUtils;
import com.android.inputmethod.keyboard.KeyboardLayoutSet.KeyboardLayoutSetException;
import com.android.inputmethod.keyboard.emoji.EmojiPalettesView;
import com.android.inputmethod.keyboard.internal.KeyboardState;
import com.android.inputmethod.keyboard.internal.KeyboardTextsSet;
import com.android.inputmethod.latin.InputView;
import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.RichInputMethodManager;
import com.android.inputmethod.latin.SubtypeSwitcher;
import com.android.inputmethod.latin.WordComposer;
import com.android.inputmethod.latin.settings.Settings;
import com.android.inputmethod.latin.settings.SettingsValues;
import com.android.inputmethod.latin.utils.ResourceUtils;
import com.android.inputmethod.latin.utils.ScriptUtils;

public final class KeyboardSwitcher implements KeyboardState.SwitchActions {
    private static final String TAG = KeyboardSwitcher.class.getSimpleName();

    private SubtypeSwitcher mSubtypeSwitcher;
    private SharedPreferences mPrefs;

    private InputView mCurrentInputView;
    private View mMainKeyboardFrame;
    private MainKeyboardView mKeyboardView;
    private EmojiPalettesView mEmojiPalettesView;
    private LatinIME mLatinIME;
    private boolean mIsHardwareAcceleratedDrawingEnabled;

    private KeyboardState mState;

    private KeyboardLayoutSet mKeyboardLayoutSet;
    // TODO: The following {@link KeyboardTextsSet} should be in {@link KeyboardLayoutSet}.
    private final KeyboardTextsSet mKeyboardTextsSet = new KeyboardTextsSet();

    private KeyboardTheme mKeyboardTheme;
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
        mPrefs = prefs;
        mSubtypeSwitcher = SubtypeSwitcher.getInstance();
        mState = new KeyboardState(this);
        mIsHardwareAcceleratedDrawingEnabled =
                InputMethodServiceCompatUtils.enableHardwareAcceleration(mLatinIME);
    }

    public void updateKeyboardTheme() {
        final boolean themeUpdated = updateKeyboardThemeAndContextThemeWrapper(
                mLatinIME, KeyboardTheme.getKeyboardTheme(mPrefs));
        if (themeUpdated && mKeyboardView != null) {
            mLatinIME.setInputView(onCreateInputView(mIsHardwareAcceleratedDrawingEnabled));
        }
    }

    private boolean updateKeyboardThemeAndContextThemeWrapper(final Context context,
            final KeyboardTheme keyboardTheme) {
        if (mThemeContext == null || !keyboardTheme.equals(mKeyboardTheme)) {
            mKeyboardTheme = keyboardTheme;
            mThemeContext = new ContextThemeWrapper(context, keyboardTheme.mStyleId);
            KeyboardLayoutSet.onKeyboardThemeChanged();
            return true;
        }
        return false;
    }

    public void loadKeyboard(final EditorInfo editorInfo, final SettingsValues settingsValues,
            final int currentAutoCapsState, final int currentRecapitalizeState) {
        final KeyboardLayoutSet.Builder builder = new KeyboardLayoutSet.Builder(
                mThemeContext, editorInfo);
        final Resources res = mThemeContext.getResources();
        final int keyboardWidth = ResourceUtils.getDefaultKeyboardWidth(res);
        final int keyboardHeight = ResourceUtils.getDefaultKeyboardHeight(res);
        builder.setKeyboardGeometry(keyboardWidth, keyboardHeight);
        builder.setSubtype(mSubtypeSwitcher.getCurrentSubtype());
        builder.setVoiceInputKeyEnabled(settingsValues.mShowsVoiceInputKey);
        builder.setLanguageSwitchKeyEnabled(mLatinIME.shouldShowLanguageSwitchKey());
        mKeyboardLayoutSet = builder.build();
        try {
            mState.onLoadKeyboard(currentAutoCapsState, currentRecapitalizeState);
            mKeyboardTextsSet.setLocale(mSubtypeSwitcher.getCurrentSubtypeLocale(), mThemeContext);
        } catch (KeyboardLayoutSetException e) {
            Log.w(TAG, "loading keyboard failed: " + e.mKeyboardId, e.getCause());
            return;
        }
    }

    public void saveKeyboardState() {
        if (getKeyboard() != null || isShowingEmojiPalettes()) {
            mState.onSaveKeyboardState();
        }
    }

    public void onHideWindow() {
        if (mKeyboardView != null) {
            mKeyboardView.onHideWindow();
        }
    }

    private void setKeyboard(final Keyboard keyboard) {
        // Make {@link MainKeyboardView} visible and hide {@link EmojiPalettesView}.
        final SettingsValues currentSettingsValues = Settings.getInstance().getCurrent();
        setMainKeyboardFrame(currentSettingsValues);
        // TODO: pass this object to setKeyboard instead of getting the current values.
        final MainKeyboardView keyboardView = mKeyboardView;
        final Keyboard oldKeyboard = keyboardView.getKeyboard();
        keyboardView.setKeyboard(keyboard);
        mCurrentInputView.setKeyboardTopPadding(keyboard.mTopPadding);
        keyboardView.setKeyPreviewPopupEnabled(
                currentSettingsValues.mKeyPreviewPopupOn,
                currentSettingsValues.mKeyPreviewPopupDismissDelay);
        keyboardView.setKeyPreviewAnimationParams(
                currentSettingsValues.mHasCustomKeyPreviewAnimationParams,
                currentSettingsValues.mKeyPreviewShowUpStartXScale,
                currentSettingsValues.mKeyPreviewShowUpStartYScale,
                currentSettingsValues.mKeyPreviewShowUpDuration,
                currentSettingsValues.mKeyPreviewDismissEndXScale,
                currentSettingsValues.mKeyPreviewDismissEndYScale,
                currentSettingsValues.mKeyPreviewDismissDuration);
        keyboardView.updateShortcutKey(mSubtypeSwitcher.isShortcutImeReady());
        final boolean subtypeChanged = (oldKeyboard == null)
                || !keyboard.mId.mLocale.equals(oldKeyboard.mId.mLocale);
        final int languageOnSpacebarFormatType = mSubtypeSwitcher.getLanguageOnSpacebarFormatType(
                keyboard.mId.mSubtype);
        final boolean hasMultipleEnabledIMEsOrSubtypes = RichInputMethodManager.getInstance()
                .hasMultipleEnabledIMEsOrSubtypes(true /* shouldIncludeAuxiliarySubtypes */);
        keyboardView.startDisplayLanguageOnSpacebar(subtypeChanged, languageOnSpacebarFormatType,
                hasMultipleEnabledIMEsOrSubtypes);
    }

    public Keyboard getKeyboard() {
        if (mKeyboardView != null) {
            return mKeyboardView.getKeyboard();
        }
        return null;
    }

    // TODO: Remove this method. Come up with a more comprehensive way to reset the keyboard layout
    // when a keyboard layout set doesn't get reloaded in LatinIME.onStartInputViewInternal().
    public void resetKeyboardStateToAlphabet(final int currentAutoCapsState,
            final int currentRecapitalizeState) {
        mState.onResetKeyboardStateToAlphabet(currentAutoCapsState, currentRecapitalizeState);
    }

    public void onPressKey(final int code, final boolean isSinglePointer,
            final int currentAutoCapsState, final int currentRecapitalizeState) {
        mState.onPressKey(code, isSinglePointer, currentAutoCapsState, currentRecapitalizeState);
    }

    public void onReleaseKey(final int code, final boolean withSliding,
            final int currentAutoCapsState, final int currentRecapitalizeState) {
        mState.onReleaseKey(code, withSliding, currentAutoCapsState, currentRecapitalizeState);
    }

    public void onFinishSlidingInput(final int currentAutoCapsState,
            final int currentRecapitalizeState) {
        mState.onFinishSlidingInput(currentAutoCapsState, currentRecapitalizeState);
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

    private void setMainKeyboardFrame(final SettingsValues settingsValues) {
        mMainKeyboardFrame.setVisibility(
                settingsValues.mHasHardwareKeyboard ? View.GONE : View.VISIBLE);
        mEmojiPalettesView.setVisibility(View.GONE);
        mEmojiPalettesView.stopEmojiPalettes();
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setEmojiKeyboard() {
        final Keyboard keyboard = mKeyboardLayoutSet.getKeyboard(KeyboardId.ELEMENT_ALPHABET);
        mMainKeyboardFrame.setVisibility(View.GONE);
        mEmojiPalettesView.startEmojiPalettes(
                mKeyboardTextsSet.getText(KeyboardTextsSet.SWITCH_TO_ALPHA_KEY_LABEL),
                mKeyboardView.getKeyVisualAttribute(), keyboard.mIconsSet);
        mEmojiPalettesView.setVisibility(View.VISIBLE);
    }

    public void onToggleEmojiKeyboard() {
        if (mKeyboardLayoutSet == null || !isShowingEmojiPalettes()) {
            mLatinIME.startShowingInputView();
            setEmojiKeyboard();
        } else {
            mLatinIME.stopShowingInputView();
            setAlphabetKeyboard();
        }
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setSymbolsShiftedKeyboard() {
        setKeyboard(mKeyboardLayoutSet.getKeyboard(KeyboardId.ELEMENT_SYMBOLS_SHIFTED));
    }

    // Future method for requesting an updating to the shift state.
    public void requestUpdatingShiftState(final int currentAutoCapsState,
            final int currentRecapitalizeState) {
        mState.onUpdateShiftState(currentAutoCapsState, currentRecapitalizeState);
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
    public void onCodeInput(final int code, final int currentAutoCapsState,
            final int currentRecapitalizeState) {
        mState.onCodeInput(code, currentAutoCapsState, currentRecapitalizeState);
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

    public View onCreateInputView(final boolean isHardwareAcceleratedDrawingEnabled) {
        if (mKeyboardView != null) {
            mKeyboardView.closing();
        }

        updateKeyboardThemeAndContextThemeWrapper(
                mLatinIME, KeyboardTheme.getKeyboardTheme(mPrefs));
        mCurrentInputView = (InputView)LayoutInflater.from(mThemeContext).inflate(
                R.layout.input_view, null);
        mMainKeyboardFrame = mCurrentInputView.findViewById(R.id.main_keyboard_frame);
        mEmojiPalettesView = (EmojiPalettesView)mCurrentInputView.findViewById(
                R.id.emoji_palettes_view);

        mKeyboardView = (MainKeyboardView) mCurrentInputView.findViewById(R.id.keyboard_view);
        mKeyboardView.setHardwareAcceleratedDrawingEnabled(isHardwareAcceleratedDrawingEnabled);
        mKeyboardView.setKeyboardActionListener(mLatinIME);
        mEmojiPalettesView.setHardwareAcceleratedDrawingEnabled(
                isHardwareAcceleratedDrawingEnabled);
        mEmojiPalettesView.setKeyboardActionListener(mLatinIME);
        return mCurrentInputView;
    }

    public void onNetworkStateChanged() {
        if (mKeyboardView != null) {
            mKeyboardView.updateShortcutKey(mSubtypeSwitcher.isShortcutImeReady());
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

    public int getCurrentKeyboardScriptId() {
        if (null == mKeyboardLayoutSet) {
            return ScriptUtils.SCRIPT_UNKNOWN;
        }
        return mKeyboardLayoutSet.getScriptId();
    }
}
