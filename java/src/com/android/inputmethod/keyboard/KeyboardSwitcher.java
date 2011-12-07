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
import android.content.res.Configuration;
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.accessibility.AccessibleKeyboardViewProxy;
import com.android.inputmethod.keyboard.internal.KeyboardState;
import com.android.inputmethod.latin.InputView;
import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.LocaleUtils;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.Settings;
import com.android.inputmethod.latin.SubtypeSwitcher;
import com.android.inputmethod.latin.Utils;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Locale;

public class KeyboardSwitcher implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = KeyboardSwitcher.class.getSimpleName();
    private static final boolean DEBUG_CACHE = LatinImeLogger.sDBG;
    public static final boolean DEBUG_STATE = false;

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

    private InputView mCurrentInputView;
    private LatinKeyboardView mKeyboardView;
    private LatinIME mInputMethodService;
    private String mPackageName;
    private Resources mResources;

    private KeyboardState mState;
    private static final int UNSHIFT = 0;
    private static final int MANUAL_SHIFT = 1;
    private static final int AUTOMATIC_SHIFT = 2;

    private KeyboardId mMainKeyboardId;
    private KeyboardId mSymbolsKeyboardId;
    private KeyboardId mSymbolsShiftedKeyboardId;

    private KeyboardId mCurrentId;
    private final HashMap<KeyboardId, SoftReference<LatinKeyboard>> mKeyboardCache =
            new HashMap<KeyboardId, SoftReference<LatinKeyboard>>();

    private KeyboardLayoutState mSavedKeyboardState = new KeyboardLayoutState();

    /** mIsAutoCorrectionActive indicates that auto corrected word will be input instead of
     * what user actually typed. */
    private boolean mIsAutoCorrectionActive;

    // TODO: Encapsulate these state handling to separate class and combine with ShiftKeyState
    // and ModifierKeyState into KeyboardState.
    private static final int SWITCH_STATE_ALPHA = 0;
    private static final int SWITCH_STATE_SYMBOL_BEGIN = 1;
    private static final int SWITCH_STATE_SYMBOL = 2;
    // The following states are used only on the distinct multi-touch panel devices.
    private static final int SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL = 3;
    private static final int SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE = 4;
    private static final int SWITCH_STATE_CHORDING_ALPHA = 5;
    private static final int SWITCH_STATE_CHORDING_SYMBOL = 6;
    private int mSwitchState = SWITCH_STATE_ALPHA;

    private String mLayoutSwitchBackSymbols;

    private int mThemeIndex = -1;
    private Context mThemeContext;

    private static final KeyboardSwitcher sInstance = new KeyboardSwitcher();

    // TODO: Move this to KeyboardState.
    private class KeyboardLayoutState {
        private boolean mIsValid;
        private boolean mIsAlphabetMode;
        private boolean mIsShiftLocked;
        private boolean mIsShifted;

        public void save() {
            mIsAlphabetMode = isAlphabetMode();
            if (mIsAlphabetMode) {
                mIsShiftLocked = mState.isShiftLocked();
                mIsShifted = !mIsShiftLocked && mState.isShiftedOrShiftLocked();
            } else {
                mIsShiftLocked = false;
                mIsShifted = isSymbolShifted();
            }
            mIsValid = true;
        }

        public void restore(boolean forceRestore) {
            if (!mIsValid) {
                if (forceRestore) {
                    setAlphabetKeyboard();
                }
                return;
            }
            mIsValid = false;

            if (mIsAlphabetMode) {
                setAlphabetKeyboard();
                if (mIsShiftLocked) {
                    setShiftLocked(true);
                }
                if (mIsShifted) {
                    setShifted(MANUAL_SHIFT);
                }
            } else {
                if (mIsShifted) {
                    setSymbolsShiftedKeyboard();
                } else {
                    setSymbolsKeyboard();
                }
            }
        }
    }

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
        mPackageName = ims.getPackageName();
        mResources = ims.getResources();
        mPrefs = prefs;
        mSubtypeSwitcher = SubtypeSwitcher.getInstance();
        mState = new KeyboardState();
        setContextThemeWrapper(ims, getKeyboardThemeIndex(ims, prefs));
        prefs.registerOnSharedPreferenceChangeListener(this);
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
            mKeyboardCache.clear();
        }
    }

    public void loadKeyboard(EditorInfo editorInfo, Settings.Values settingsValues) {
        try {
            mMainKeyboardId = getKeyboardId(editorInfo, false, false, settingsValues);
            mSymbolsKeyboardId = getKeyboardId(editorInfo, true, false, settingsValues);
            mSymbolsShiftedKeyboardId = getKeyboardId(editorInfo, true, true, settingsValues);
            mState.onLoadKeyboard();
            mLayoutSwitchBackSymbols = mResources.getString(R.string.layout_switch_back_symbols);
            mSavedKeyboardState.restore(mCurrentId == null);
        } catch (RuntimeException e) {
            Log.w(TAG, "loading keyboard failed: " + mMainKeyboardId, e);
            LatinImeLogger.logOnException(mMainKeyboardId.toString(), e);
        }
    }

    public void saveKeyboardState() {
        if (mCurrentId != null) {
            mSavedKeyboardState.save();
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
        mCurrentId = keyboard.mId;
        mSwitchState = getSwitchState();
        updateShiftLockState(keyboard);
        mKeyboardView.setKeyPreviewPopupEnabled(
                Settings.Values.isKeyPreviewPopupEnabled(mPrefs, mResources),
                Settings.Values.getKeyPreviewPopupDismissDelay(mPrefs, mResources));
        final boolean localeChanged = (oldKeyboard == null)
                || !keyboard.mId.mLocale.equals(oldKeyboard.mId.mLocale);
        mInputMethodService.mHandler.startDisplayLanguageOnSpacebar(localeChanged);
        updateShiftState();
    }

    private int getSwitchState() {
        return isAlphabetMode() ? SWITCH_STATE_ALPHA : SWITCH_STATE_SYMBOL_BEGIN;
    }

    private void updateShiftLockState(Keyboard keyboard) {
        if (mCurrentId.equals(mSymbolsShiftedKeyboardId)) {
            // Symbol keyboard may have an ALT key that has a caps lock style indicator (a.k.a.
            // sticky shift key). To show or dismiss the indicator, we need to call setShiftLocked()
            // that takes care of the current keyboard having such ALT key or not.
            keyboard.setShiftLocked(keyboard.hasShiftLockKey());
        } else if (mCurrentId.equals(mSymbolsKeyboardId)) {
            // Symbol keyboard has an ALT key that has a caps lock style indicator. To disable the
            // indicator, we need to call setShiftLocked(false).
            keyboard.setShiftLocked(false);
        }
    }

    private LatinKeyboard getKeyboard(KeyboardId id) {
        final SoftReference<LatinKeyboard> ref = mKeyboardCache.get(id);
        LatinKeyboard keyboard = (ref == null) ? null : ref.get();
        if (keyboard == null) {
            final Locale savedLocale = LocaleUtils.setSystemLocale(mResources, id.mLocale);
            try {
                final LatinKeyboard.Builder builder = new LatinKeyboard.Builder(mThemeContext);
                builder.load(id);
                builder.setTouchPositionCorrectionEnabled(
                        mSubtypeSwitcher.currentSubtypeContainsExtraValueKey(
                                LatinIME.SUBTYPE_EXTRA_VALUE_SUPPORT_TOUCH_POSITION_CORRECTION));
                keyboard = builder.build();
            } finally {
                LocaleUtils.setSystemLocale(mResources, savedLocale);
            }
            mKeyboardCache.put(id, new SoftReference<LatinKeyboard>(keyboard));

            if (DEBUG_CACHE) {
                Log.d(TAG, "keyboard cache size=" + mKeyboardCache.size() + ": "
                        + ((ref == null) ? "LOAD" : "GCed") + " id=" + id
                        + " theme=" + Keyboard.themeName(keyboard.mThemeId));
            }
        } else if (DEBUG_CACHE) {
            Log.d(TAG, "keyboard cache size=" + mKeyboardCache.size() + ": HIT  id=" + id
                    + " theme=" + Keyboard.themeName(keyboard.mThemeId));
        }

        keyboard.onAutoCorrectionStateChanged(mIsAutoCorrectionActive);
        keyboard.setShiftLocked(false);
        keyboard.setShifted(false);
        // If the cached keyboard had been switched to another keyboard while the language was
        // displayed on its spacebar, it might have had arbitrary text fade factor. In such case,
        // we should reset the text fade factor. It is also applicable to shortcut key.
        keyboard.setSpacebarTextFadeFactor(0.0f, null);
        keyboard.updateShortcutKey(mSubtypeSwitcher.isShortcutImeReady(), null);
        return keyboard;
    }

    private KeyboardId getKeyboardId(EditorInfo editorInfo, final boolean isSymbols,
            final boolean isShift, Settings.Values settingsValues) {
        final int mode = Utils.getKeyboardMode(editorInfo);
        final int xmlId;
        switch (mode) {
        case KeyboardId.MODE_PHONE:
            xmlId = (isSymbols && isShift) ? R.xml.kbd_phone_shift : R.xml.kbd_phone;
            break;
        case KeyboardId.MODE_NUMBER:
            xmlId = R.xml.kbd_number;
            break;
        default:
            if (isSymbols) {
                xmlId = isShift ? R.xml.kbd_symbols_shift : R.xml.kbd_symbols;
            } else {
                xmlId = R.xml.kbd_qwerty;
            }
            break;
        }

        final boolean settingsKeyEnabled = settingsValues.isSettingsKeyEnabled();
        @SuppressWarnings("deprecation")
        final boolean noMicrophone = Utils.inPrivateImeOptions(
                mPackageName, LatinIME.IME_OPTION_NO_MICROPHONE, editorInfo)
                || Utils.inPrivateImeOptions(
                        null, LatinIME.IME_OPTION_NO_MICROPHONE_COMPAT, editorInfo);
        final boolean voiceKeyEnabled = settingsValues.isVoiceKeyEnabled(editorInfo)
                && !noMicrophone;
        final boolean voiceKeyOnMain = settingsValues.isVoiceKeyOnMain();
        final boolean noSettingsKey = Utils.inPrivateImeOptions(
                mPackageName, LatinIME.IME_OPTION_NO_SETTINGS_KEY, editorInfo);
        final boolean hasSettingsKey = settingsKeyEnabled && !noSettingsKey;
        final int f2KeyMode = getF2KeyMode(settingsKeyEnabled, noSettingsKey);
        final boolean hasShortcutKey = voiceKeyEnabled && (isSymbols != voiceKeyOnMain);
        final boolean forceAscii = Utils.inPrivateImeOptions(
                mPackageName, LatinIME.IME_OPTION_FORCE_ASCII, editorInfo);
        final boolean asciiCapable = mSubtypeSwitcher.currentSubtypeContainsExtraValueKey(
                LatinIME.SUBTYPE_EXTRA_VALUE_ASCII_CAPABLE);
        final Locale locale = (forceAscii && !asciiCapable)
                ? Locale.US : mSubtypeSwitcher.getInputLocale();
        final Configuration conf = mResources.getConfiguration();
        final DisplayMetrics dm = mResources.getDisplayMetrics();

        return new KeyboardId(
                mResources.getResourceEntryName(xmlId), xmlId, locale, conf.orientation,
                dm.widthPixels, mode, editorInfo, hasSettingsKey, f2KeyMode, noSettingsKey,
                voiceKeyEnabled, hasShortcutKey);
    }

    public int getKeyboardMode() {
        return mCurrentId != null ? mCurrentId.mMode : KeyboardId.MODE_TEXT;
    }

    public boolean isAlphabetMode() {
        return mCurrentId != null && mCurrentId.isAlphabetKeyboard();
    }

    public boolean isInputViewShown() {
        return mCurrentInputView != null && mCurrentInputView.isShown();
    }

    public boolean isKeyboardAvailable() {
        if (mKeyboardView != null)
            return mKeyboardView.getKeyboard() != null;
        return false;
    }

    public LatinKeyboard getLatinKeyboard() {
        if (mKeyboardView != null) {
            final Keyboard keyboard = mKeyboardView.getKeyboard();
            if (keyboard instanceof LatinKeyboard)
                return (LatinKeyboard)keyboard;
        }
        return null;
    }

    public boolean isShiftedOrShiftLocked() {
        return mState.isShiftedOrShiftLocked();
    }

    public boolean isManualTemporaryUpperCase() {
        return mState.isManualTemporaryUpperCase();
    }

    private void setShifted(int shiftMode) {
        LatinKeyboard latinKeyboard = getLatinKeyboard();
        if (latinKeyboard == null)
            return;
        if (shiftMode == AUTOMATIC_SHIFT) {
            mState.setAutomaticTemporaryUpperCase();
            latinKeyboard.setAutomaticTemporaryUpperCase();
        } else {
            final boolean shifted = (shiftMode == MANUAL_SHIFT);
            // On non-distinct multi touch panel device, we should also turn off the shift locked
            // state when shift key is pressed to go to normal mode.
            // On the other hand, on distinct multi touch panel device, turning off the shift
            // locked state with shift key pressing is handled by onReleaseShift().
            if (!hasDistinctMultitouch() && !shifted && latinKeyboard.isShiftLocked()) {
                mState.setShiftLocked(false);
                latinKeyboard.setShiftLocked(false);
            }
            mState.setShifted(shifted);
            latinKeyboard.setShifted(shifted);
        }
        mKeyboardView.invalidateAllKeys();
    }

    private void setShiftLocked(boolean shiftLocked) {
        LatinKeyboard latinKeyboard = getLatinKeyboard();
        if (latinKeyboard == null)
            return;
        mState.setShiftLocked(shiftLocked);
        latinKeyboard.setShiftLocked(shiftLocked);
        mKeyboardView.invalidateAllKeys();
    }

    /**
     * Toggle keyboard shift state triggered by user touch event.
     */
    public void toggleShift() {
        mInputMethodService.mHandler.cancelUpdateShiftState();
        if (DEBUG_STATE) {
            Log.d(TAG, "toggleShift: " + mState);
        }
        if (isAlphabetMode()) {
            setShifted(mState.isShiftedOrShiftLocked() ? UNSHIFT : MANUAL_SHIFT);
        } else {
            toggleShiftInSymbols();
        }
    }

    public void toggleCapsLock() {
        mInputMethodService.mHandler.cancelUpdateShiftState();
        if (DEBUG_STATE) {
            Log.d(TAG, "toggleCapsLock: " + mState);
        }
        if (isAlphabetMode()) {
            if (mState.isShiftLocked()) {
                // Shift key is long pressed while caps lock state, we will toggle back to normal
                // state. And mark as if shift key is released.
                setShiftLocked(false);
                mState.onToggleCapsLock();
            } else {
                setShiftLocked(true);
            }
        }
    }

    public void toggleKeyboardMode() {
        if (DEBUG_STATE) {
            Log.d(TAG, "toggleKeyboard: " + mState);
        }
        toggleAlphabetAndSymbols();
    }

    private void startIgnoringDoubleTap() {
        if (mKeyboardView != null) {
            mKeyboardView.startIgnoringDoubleTap();
        }
    }

    /**
     * Update keyboard shift state triggered by connected EditText status change.
     */
    public void updateShiftState() {
        if (DEBUG_STATE) {
            Log.d(TAG, "updateShiftState: " + mState
                    + " autoCaps=" + mInputMethodService.getCurrentAutoCapsState());
        }
        final boolean isAlphabetMode = isAlphabetMode();
        final boolean isShiftLocked = mState.isShiftLocked();
        if (isAlphabetMode) {
            if (!isShiftLocked && !mState.isShiftKeyIgnoring()) {
                if (mState.isShiftKeyReleasing() && mInputMethodService.getCurrentAutoCapsState()) {
                    // Only when shift key is releasing, automatic temporary upper case will be set.
                    setShifted(AUTOMATIC_SHIFT);
                } else {
                    setShifted(mState.isShiftKeyMomentary() ? MANUAL_SHIFT : UNSHIFT);
                }
            }
        }
        mState.onUpdateShiftState(isAlphabetMode);
    }

    public void onPressShift(boolean withSliding) {
        if (!isKeyboardAvailable())
            return;
        if (DEBUG_STATE) {
            Log.d(TAG, "onPressShift: " + mState + " sliding=" + withSliding);
        }
        final boolean isAlphabetMode = isAlphabetMode();
        final boolean isShiftLocked = mState.isShiftLocked();
        final boolean isAutomaticTemporaryUpperCase = mState.isAutomaticTemporaryUpperCase();
        final boolean isShiftedOrShiftLocked = mState.isShiftedOrShiftLocked();
        if (isAlphabetMode) {
            if (isShiftLocked) {
                // Shift key is pressed while caps lock state, we will treat this state as shifted
                // caps lock state and mark as if shift key pressed while normal state.
                setShifted(MANUAL_SHIFT);
            } else if (isAutomaticTemporaryUpperCase) {
                // Shift key is pressed while automatic temporary upper case, we have to move to
                // manual temporary upper case.
                setShifted(MANUAL_SHIFT);
            } else if (isShiftedOrShiftLocked) {
                // In manual upper case state, we just record shift key has been pressing while
                // shifted state.
            } else {
                // In base layout, chording or manual temporary upper case mode is started.
                toggleShift();
            }
        } else {
            // In symbol mode, just toggle symbol and symbol more keyboard.
            toggleShiftInSymbols();
            mSwitchState = SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE;
        }
        mState.onPressShift(isAlphabetMode, isShiftLocked, isAutomaticTemporaryUpperCase,
                isShiftedOrShiftLocked);
    }

    public void onReleaseShift(boolean withSliding) {
        if (!isKeyboardAvailable())
            return;
        if (DEBUG_STATE) {
            Log.d(TAG, "onReleaseShift: " + mState + " sliding=" + withSliding);
        }
        final boolean isAlphabetMode = isAlphabetMode();
        final boolean isShiftLocked = mState.isShiftLocked();
        final boolean isShiftLockShifted = mState.isShiftLockShifted();
        final boolean isShiftedOrShiftLocked = mState.isShiftedOrShiftLocked();
        final boolean isManualTemporaryUpperCaseFromAuto =
                mState.isManualTemporaryUpperCaseFromAuto();
        if (isAlphabetMode) {
            if (mState.isShiftKeyMomentary()) {
                // After chording input while normal state.
                toggleShift();
            } else if (isShiftLocked && !isShiftLockShifted && (mState.isShiftKeyPressing()
                    || mState.isShiftKeyPressingOnShifted()) && !withSliding) {
                // Shift has been long pressed, ignore this release.
            } else if (isShiftLocked && !mState.isShiftKeyIgnoring() && !withSliding) {
                // Shift has been pressed without chording while caps lock state.
                toggleCapsLock();
                // To be able to turn off caps lock by "double tap" on shift key, we should ignore
                // the second tap of the "double tap" from now for a while because we just have
                // already turned off caps lock above.
                startIgnoringDoubleTap();
            } else if (isShiftedOrShiftLocked && mState.isShiftKeyPressingOnShifted()
                    && !withSliding) {
                // Shift has been pressed without chording while shifted state.
                toggleShift();
            } else if (isManualTemporaryUpperCaseFromAuto && mState.isShiftKeyPressing()
                    && !withSliding) {
                // Shift has been pressed without chording while manual temporary upper case
                // transited from automatic temporary upper case.
                toggleShift();
            }
        } else {
            // In symbol mode, snap back to the previous keyboard mode if the user chords the shift
            // key and another key, then releases the shift key.
            if (mSwitchState == SWITCH_STATE_CHORDING_SYMBOL) {
                toggleShiftInSymbols();
            }
        }
        mState.onReleaseShift();
    }

    public void onPressSymbol() {
        if (DEBUG_STATE) {
            Log.d(TAG, "onPressSymbol: " + mState);
        }
        toggleAlphabetAndSymbols();
        mState.onPressSymbol();
        mSwitchState = SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL;
    }

    public void onReleaseSymbol() {
        if (DEBUG_STATE) {
            Log.d(TAG, "onReleaseSymbol: " + mState);
            }
        // Snap back to the previous keyboard mode if the user chords the mode change key and
        // another key, then releases the mode change key.
        if (mSwitchState == SWITCH_STATE_CHORDING_ALPHA) {
            toggleAlphabetAndSymbols();
        }
        mState.onReleaseSymbol();
    }

    public void onOtherKeyPressed() {
        if (DEBUG_STATE) {
            Log.d(TAG, "onOtherKeyPressed: " + mState);
        }
        mState.onOtherKeyPressed();
    }

    public void onCancelInput() {
        // Snap back to the previous keyboard mode if the user cancels sliding input.
        if (isSinglePointer()) {
            if (mSwitchState == SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL) {
                toggleAlphabetAndSymbols();
            } else if (mSwitchState == SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE) {
                toggleShiftInSymbols();
            }
        }
    }

    private boolean mPrevMainKeyboardWasShiftLocked;

    private void setSymbolsKeyboard() {
        mPrevMainKeyboardWasShiftLocked = mState.isShiftLocked();
        setKeyboard(getKeyboard(mSymbolsKeyboardId));
    }

    private void setAlphabetKeyboard() {
        setKeyboard(getKeyboard(mMainKeyboardId));
        setShiftLocked(mPrevMainKeyboardWasShiftLocked);
        mPrevMainKeyboardWasShiftLocked = false;
    }

    private void toggleAlphabetAndSymbols() {
        if (isAlphabetMode()) {
            setSymbolsKeyboard();
        } else {
            setAlphabetKeyboard();
        }
    }

    private boolean isSymbolShifted() {
        return mCurrentId != null && mCurrentId.equals(mSymbolsShiftedKeyboardId);
    }

    private void setSymbolsShiftedKeyboard() {
        setKeyboard(getKeyboard(mSymbolsShiftedKeyboardId));
    }

    private void toggleShiftInSymbols() {
        if (isSymbolShifted()) {
            setSymbolsKeyboard();
        } else {
            setSymbolsShiftedKeyboard();
        }
    }

    public boolean isInMomentarySwitchState() {
        return mSwitchState == SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL
                || mSwitchState == SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE;
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

    private static boolean isSpaceCharacter(int c) {
        return c == Keyboard.CODE_SPACE || c == Keyboard.CODE_ENTER;
    }

    private boolean isLayoutSwitchBackCharacter(int c) {
        if (TextUtils.isEmpty(mLayoutSwitchBackSymbols)) return false;
        if (mLayoutSwitchBackSymbols.indexOf(c) >= 0) return true;
        return false;
    }

    /**
     * Updates state machine to figure out when to automatically snap back to the previous mode.
     */
    public void onKey(int code) {
        if (DEBUG_STATE) {
            Log.d(TAG, "onKey: code=" + code + " switchState=" + mSwitchState
                    + " isSinglePointer=" + isSinglePointer());
        }
        switch (mSwitchState) {
        case SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL:
            // Only distinct multi touch devices can be in this state.
            // On non-distinct multi touch devices, mode change key is handled by
            // {@link LatinIME#onCodeInput}, not by {@link LatinIME#onPress} and
            // {@link LatinIME#onRelease}. So, on such devices, {@link #mSwitchState} starts
            // from {@link #SWITCH_STATE_SYMBOL_BEGIN}, or {@link #SWITCH_STATE_ALPHA}, not from
            // {@link #SWITCH_STATE_MOMENTARY}.
            if (code == Keyboard.CODE_SWITCH_ALPHA_SYMBOL) {
                // Detected only the mode change key has been pressed, and then released.
                if (mCurrentId.equals(mMainKeyboardId)) {
                    mSwitchState = SWITCH_STATE_ALPHA;
                } else {
                    mSwitchState = SWITCH_STATE_SYMBOL_BEGIN;
                }
            } else if (isSinglePointer()) {
                // Snap back to the previous keyboard mode if the user pressed the mode change key
                // and slid to other key, then released the finger.
                // If the user cancels the sliding input, snapping back to the previous keyboard
                // mode is handled by {@link #onCancelInput}.
                toggleAlphabetAndSymbols();
            } else {
                // Chording input is being started. The keyboard mode will be snapped back to the
                // previous mode in {@link onReleaseSymbol} when the mode change key is released.
                mSwitchState = SWITCH_STATE_CHORDING_ALPHA;
            }
            break;
        case SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE:
            if (code == Keyboard.CODE_SHIFT) {
                // Detected only the shift key has been pressed on symbol layout, and then released.
                mSwitchState = SWITCH_STATE_SYMBOL_BEGIN;
            } else if (isSinglePointer()) {
                // Snap back to the previous keyboard mode if the user pressed the shift key on
                // symbol mode and slid to other key, then released the finger.
                toggleShiftInSymbols();
                mSwitchState = SWITCH_STATE_SYMBOL;
            } else {
                // Chording input is being started. The keyboard mode will be snapped back to the
                // previous mode in {@link onReleaseShift} when the shift key is released.
                mSwitchState = SWITCH_STATE_CHORDING_SYMBOL;
            }
            break;
        case SWITCH_STATE_SYMBOL_BEGIN:
            if (!isSpaceCharacter(code) && code >= 0) {
                mSwitchState = SWITCH_STATE_SYMBOL;
            }
            // Snap back to alpha keyboard mode immediately if user types a quote character.
            if (isLayoutSwitchBackCharacter(code)) {
                setAlphabetKeyboard();
            }
            break;
        case SWITCH_STATE_SYMBOL:
        case SWITCH_STATE_CHORDING_SYMBOL:
            // Snap back to alpha keyboard mode if user types one or more non-space/enter
            // characters followed by a space/enter or a quote character.
            if (isSpaceCharacter(code) || isLayoutSwitchBackCharacter(code)) {
                setAlphabetKeyboard();
            }
            break;
        }
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

        // This always needs to be set since the accessibility state can
        // potentially change without the input view being re-created.
        AccessibleKeyboardViewProxy.setView(mKeyboardView);

        return mCurrentInputView;
    }

    private void postSetInputView(final View newInputView) {
        mInputMethodService.mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (newInputView != null) {
                    mInputMethodService.setInputView(newInputView);
                }
                mInputMethodService.updateInputViewShown();
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

    public void onAutoCorrectionStateChanged(boolean isAutoCorrection) {
        if (mIsAutoCorrectionActive != isAutoCorrection) {
            mIsAutoCorrectionActive = isAutoCorrection;
            final LatinKeyboard keyboard = getLatinKeyboard();
            if (keyboard != null && keyboard.needsAutoCorrectionSpacebarLed()) {
                final Key invalidatedKey = keyboard.onAutoCorrectionStateChanged(isAutoCorrection);
                final LatinKeyboardView keyboardView = getKeyboardView();
                if (keyboardView != null)
                    keyboardView.invalidateKey(invalidatedKey);
            }
        }
    }

    private static int getF2KeyMode(boolean settingsKeyEnabled, boolean noSettingsKey) {
        if (noSettingsKey) {
            // Never shows the Settings key
            return KeyboardId.F2KEY_MODE_SHORTCUT_IME;
        }

        if (settingsKeyEnabled) {
            return KeyboardId.F2KEY_MODE_SETTINGS;
        } else {
            // It should be alright to fall back to the Settings key on 7-inch layouts
            // even when the Settings key is not explicitly enabled.
            return KeyboardId.F2KEY_MODE_SHORTCUT_IME_OR_SETTINGS;
        }
    }
}
