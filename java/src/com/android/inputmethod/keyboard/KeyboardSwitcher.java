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

import com.android.inputmethod.compat.InputMethodManagerCompatWrapper;
import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.Settings;
import com.android.inputmethod.latin.SubtypeSwitcher;
import com.android.inputmethod.latin.Utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.util.Log;
import android.view.InflateException;
import android.view.inputmethod.EditorInfo;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Locale;

public class KeyboardSwitcher implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "KeyboardSwitcher";
    private static final boolean DEBUG = false;
    public static final boolean DEBUG_STATE = false;

    private static String sConfigDefaultKeyboardThemeId;
    public static final String PREF_KEYBOARD_LAYOUT = "pref_keyboard_layout_20100902";
    private static final int[] KEYBOARD_THEMES = {
        R.layout.input_basic,
        R.layout.input_basic_highcontrast,
        R.layout.input_stone_normal,
        R.layout.input_stone_bold,
        R.layout.input_gingerbread,
        R.layout.input_honeycomb,
    };

    private SubtypeSwitcher mSubtypeSwitcher;
    private SharedPreferences mPrefs;

    private LatinKeyboardView mInputView;
    private LatinIME mInputMethodService;

    // TODO: Combine these key state objects with auto mode switch state.
    private ShiftKeyState mShiftKeyState = new ShiftKeyState("Shift");
    private ModifierKeyState mSymbolKeyState = new ModifierKeyState("Symbol");

    private KeyboardId mSymbolsId;
    private KeyboardId mSymbolsShiftedId;

    private KeyboardId mCurrentId;
    private final HashMap<KeyboardId, SoftReference<LatinKeyboard>> mKeyboardCache =
            new HashMap<KeyboardId, SoftReference<LatinKeyboard>>();

    private EditorInfo mAttribute;
    private boolean mIsSymbols;
    /** mIsAutoCorrectionActive indicates that auto corrected word will be input instead of
     * what user actually typed. */
    private boolean mIsAutoCorrectionActive;
    private boolean mVoiceKeyEnabled;
    private boolean mVoiceButtonOnPrimary;

    // TODO: Encapsulate these state handling to separate class and combine with ShiftKeyState
    // and ModifierKeyState.
    private static final int SWITCH_STATE_ALPHA = 0;
    private static final int SWITCH_STATE_SYMBOL_BEGIN = 1;
    private static final int SWITCH_STATE_SYMBOL = 2;
    // The following states are used only on the distinct multi-touch panel devices.
    private static final int SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL = 3;
    private static final int SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE = 4;
    private static final int SWITCH_STATE_CHORDING_ALPHA = 5;
    private static final int SWITCH_STATE_CHORDING_SYMBOL = 6;
    private int mSwitchState = SWITCH_STATE_ALPHA;

    // Indicates whether or not we have the settings key in option of settings
    private boolean mSettingsKeyEnabledInSettings;
    private static final int SETTINGS_KEY_MODE_AUTO = R.string.settings_key_mode_auto;
    private static final int SETTINGS_KEY_MODE_ALWAYS_SHOW =
            R.string.settings_key_mode_always_show;
    // NOTE: No need to have SETTINGS_KEY_MODE_ALWAYS_HIDE here because it's not being referred to
    // in the source code now.
    // Default is SETTINGS_KEY_MODE_AUTO.
    private static final int DEFAULT_SETTINGS_KEY_MODE = SETTINGS_KEY_MODE_AUTO;

    private int mLayoutId;

    private static final KeyboardSwitcher sInstance = new KeyboardSwitcher();

    public static KeyboardSwitcher getInstance() {
        return sInstance;
    }

    private KeyboardSwitcher() {
        // Intentional empty constructor for singleton.
    }

    public static void init(LatinIME ims, SharedPreferences prefs) {
        sInstance.mInputMethodService = ims;
        sInstance.mPrefs = prefs;
        sInstance.mSubtypeSwitcher = SubtypeSwitcher.getInstance();

        try {
            sConfigDefaultKeyboardThemeId = ims.getString(
                    R.string.config_default_keyboard_theme_id);
            sInstance.mLayoutId = Integer.valueOf(
                    prefs.getString(PREF_KEYBOARD_LAYOUT, sConfigDefaultKeyboardThemeId));
        } catch (NumberFormatException e) {
            sConfigDefaultKeyboardThemeId = "0";
            sInstance.mLayoutId = 0;
        }
        prefs.registerOnSharedPreferenceChangeListener(sInstance);
    }

    public void loadKeyboard(EditorInfo attribute, boolean voiceKeyEnabled,
            boolean voiceButtonOnPrimary) {
        mSwitchState = SWITCH_STATE_ALPHA;
        try {
            loadKeyboardInternal(attribute, voiceKeyEnabled, voiceButtonOnPrimary, false);
        } catch (RuntimeException e) {
            // Get KeyboardId to record which keyboard has been failed to load.
            final KeyboardId id = getKeyboardId(attribute, false);
            Log.w(TAG, "loading keyboard failed: " + id, e);
            LatinImeLogger.logOnException(id.toString(), e);
        }
    }

    private void loadKeyboardInternal(EditorInfo attribute, boolean voiceButtonEnabled,
            boolean voiceButtonOnPrimary, boolean isSymbols) {
        if (mInputView == null) return;

        mAttribute = attribute;
        mVoiceKeyEnabled = voiceButtonEnabled;
        mVoiceButtonOnPrimary = voiceButtonOnPrimary;
        mIsSymbols = isSymbols;
        // Update the settings key state because number of enabled IMEs could have been changed
        mSettingsKeyEnabledInSettings = getSettingsKeyMode(mPrefs, mInputMethodService);
        final KeyboardId id = getKeyboardId(attribute, isSymbols);
        makeSymbolsKeyboardIds(id.mMode, attribute);
        mCurrentId = id;
        mInputView.setKeyPreviewEnabled(mInputMethodService.getPopupOn());
        setKeyboard(getKeyboard(id));
    }

    private void setKeyboard(final Keyboard newKeyboard) {
        final Keyboard oldKeyboard = mInputView.getKeyboard();
        mInputView.setKeyboard(newKeyboard);
        final boolean localeChanged = (oldKeyboard == null)
                || !newKeyboard.mId.mLocale.equals(oldKeyboard.mId.mLocale);
        mInputMethodService.mHandler.startDisplayLanguageOnSpacebar(localeChanged);
    }

    private LatinKeyboard getKeyboard(KeyboardId id) {
        final SoftReference<LatinKeyboard> ref = mKeyboardCache.get(id);
        LatinKeyboard keyboard = (ref == null) ? null : ref.get();
        if (keyboard == null) {
            final Resources res = mInputMethodService.getResources();
            final Locale savedLocale = Utils.setSystemLocale(res,
                    mSubtypeSwitcher.getInputLocale());

            keyboard = new LatinKeyboard(mInputMethodService, id);

            if (id.mEnableShiftLock) {
                keyboard.enableShiftLock();
            }

            mKeyboardCache.put(id, new SoftReference<LatinKeyboard>(keyboard));
            if (DEBUG)
                Log.d(TAG, "keyboard cache size=" + mKeyboardCache.size() + ": "
                        + ((ref == null) ? "LOAD" : "GCed") + " id=" + id);

            Utils.setSystemLocale(res, savedLocale);
        } else if (DEBUG) {
            Log.d(TAG, "keyboard cache size=" + mKeyboardCache.size() + ": HIT  id=" + id);
        }

        keyboard.onAutoCorrectionStateChanged(mIsAutoCorrectionActive);
        keyboard.setShifted(false);
        // If the cached keyboard had been switched to another keyboard while the language was
        // displayed on its spacebar, it might have had arbitrary text fade factor. In such case,
        // we should reset the text fade factor. It is also applicable to shortcut key.
        keyboard.setSpacebarTextFadeFactor(0.0f, null);
        keyboard.updateShortcutKey(mSubtypeSwitcher.isShortcutImeReady(), null);
        keyboard.setSpacebarSlidingLanguageSwitchDiff(0);
        return keyboard;
    }

    private boolean hasVoiceKey(boolean isSymbols) {
        return mVoiceKeyEnabled && (isSymbols != mVoiceButtonOnPrimary);
    }

    private boolean hasSettingsKey(EditorInfo attribute) {
        return mSettingsKeyEnabledInSettings
            && !Utils.inPrivateImeOptions(mInputMethodService.getPackageName(),
                    LatinIME.IME_OPTION_NO_SETTINGS_KEY, attribute);
    }

    private KeyboardId getKeyboardId(EditorInfo attribute, boolean isSymbols) {
        final int mode = Utils.getKeyboardMode(attribute);
        final boolean hasVoiceKey = hasVoiceKey(isSymbols);
        final int charColorId = getColorScheme();
        final int xmlId;
        final boolean enableShiftLock;

        if (isSymbols) {
            if (mode == KeyboardId.MODE_PHONE) {
                xmlId = R.xml.kbd_phone_symbols;
            } else if (mode == KeyboardId.MODE_NUMBER) {
                // Note: MODE_NUMBER keyboard layout has no "switch alpha symbol" key.
                xmlId = R.xml.kbd_number;
            } else {
                xmlId = R.xml.kbd_symbols;
            }
            enableShiftLock = false;
        } else {
            if (mode == KeyboardId.MODE_PHONE) {
                xmlId = R.xml.kbd_phone;
                enableShiftLock = false;
            } else if (mode == KeyboardId.MODE_NUMBER) {
                xmlId = R.xml.kbd_number;
                enableShiftLock = false;
            } else {
                xmlId = R.xml.kbd_qwerty;
                enableShiftLock = true;
            }
        }
        final boolean hasSettingsKey = hasSettingsKey(attribute);
        final Resources res = mInputMethodService.getResources();
        final int orientation = res.getConfiguration().orientation;
        final Locale locale = mSubtypeSwitcher.getInputLocale();
        return new KeyboardId(
                res.getResourceEntryName(xmlId), xmlId, charColorId, locale, orientation, mode,
                attribute, hasSettingsKey, mVoiceKeyEnabled, hasVoiceKey, enableShiftLock);
    }

    private void makeSymbolsKeyboardIds(final int mode, EditorInfo attribute) {
        final Locale locale = mSubtypeSwitcher.getInputLocale();
        final Resources res = mInputMethodService.getResources();
        final int orientation = res.getConfiguration().orientation;
        final int colorScheme = getColorScheme();
        final boolean hasVoiceKey = mVoiceKeyEnabled && !mVoiceButtonOnPrimary;
        final boolean hasSettingsKey = hasSettingsKey(attribute);
        // Note: This comment is only applied for phone number keyboard layout.
        // On non-xlarge device, "@integer/key_switch_alpha_symbol" key code is used to switch
        // between "phone keyboard" and "phone symbols keyboard".  But on xlarge device,
        // "@integer/key_shift" key code is used for that purpose in order to properly display
        // "more" and "locked more" key labels.  To achieve these behavior, we should initialize
        // mSymbolsId and mSymbolsShiftedId to "phone keyboard" and "phone symbols keyboard"
        // respectively here for xlarge device's layout switching.
        int xmlId = mode == KeyboardId.MODE_PHONE ? R.xml.kbd_phone : R.xml.kbd_symbols;
        final String xmlName = res.getResourceEntryName(xmlId);
        mSymbolsId = new KeyboardId(xmlName, xmlId, colorScheme, locale, orientation, mode,
                attribute, hasSettingsKey, mVoiceKeyEnabled, hasVoiceKey, false);
        xmlId = mode == KeyboardId.MODE_PHONE ? R.xml.kbd_phone_symbols : R.xml.kbd_symbols_shift;
        mSymbolsShiftedId = new KeyboardId(xmlName, xmlId, colorScheme, locale, orientation, mode,
                attribute, hasSettingsKey, mVoiceKeyEnabled, hasVoiceKey, false);
    }

    public int getKeyboardMode() {
        return mCurrentId != null ? mCurrentId.mMode : KeyboardId.MODE_TEXT;
    }

    public boolean isAlphabetMode() {
        return mCurrentId != null && mCurrentId.isAlphabetKeyboard();
    }

    public boolean isInputViewShown() {
        return mInputView != null && mInputView.isShown();
    }

    public boolean isKeyboardAvailable() {
        if (mInputView != null)
            return mInputView.getKeyboard() != null;
        return false;
    }

    public LatinKeyboard getLatinKeyboard() {
        if (mInputView != null) {
            final Keyboard keyboard = mInputView.getKeyboard();
            if (keyboard instanceof LatinKeyboard)
                return (LatinKeyboard)keyboard;
        }
        return null;
    }

    public boolean isShiftedOrShiftLocked() {
        LatinKeyboard latinKeyboard = getLatinKeyboard();
        if (latinKeyboard != null)
            return latinKeyboard.isShiftedOrShiftLocked();
        return false;
    }

    public boolean isShiftLocked() {
        LatinKeyboard latinKeyboard = getLatinKeyboard();
        if (latinKeyboard != null)
            return latinKeyboard.isShiftLocked();
        return false;
    }

    public boolean isAutomaticTemporaryUpperCase() {
        LatinKeyboard latinKeyboard = getLatinKeyboard();
        if (latinKeyboard != null)
            return latinKeyboard.isAutomaticTemporaryUpperCase();
        return false;
    }

    public boolean isManualTemporaryUpperCase() {
        LatinKeyboard latinKeyboard = getLatinKeyboard();
        if (latinKeyboard != null)
            return latinKeyboard.isManualTemporaryUpperCase();
        return false;
    }

    private boolean isManualTemporaryUpperCaseFromAuto() {
        LatinKeyboard latinKeyboard = getLatinKeyboard();
        if (latinKeyboard != null)
            return latinKeyboard.isManualTemporaryUpperCaseFromAuto();
        return false;
    }

    private void setManualTemporaryUpperCase(boolean shifted) {
        LatinKeyboard latinKeyboard = getLatinKeyboard();
        if (latinKeyboard != null) {
            // On non-distinct multi touch panel device, we should also turn off the shift locked
            // state when shift key is pressed to go to normal mode.
            // On the other hand, on distinct multi touch panel device, turning off the shift locked
            // state with shift key pressing is handled by onReleaseShift().
            if ((!hasDistinctMultitouch() || isAccessibilityEnabled())
                    && !shifted && latinKeyboard.isShiftLocked()) {
                latinKeyboard.setShiftLocked(false);
            }
            if (latinKeyboard.setShifted(shifted)) {
                mInputView.invalidateAllKeys();
            }
        }
    }

    private void setShiftLocked(boolean shiftLocked) {
        LatinKeyboard latinKeyboard = getLatinKeyboard();
        if (latinKeyboard != null && latinKeyboard.setShiftLocked(shiftLocked)) {
            mInputView.invalidateAllKeys();
        }
    }

    /**
     * Toggle keyboard shift state triggered by user touch event.
     */
    public void toggleShift() {
        mInputMethodService.mHandler.cancelUpdateShiftState();
        if (DEBUG_STATE)
            Log.d(TAG, "toggleShift:"
                    + " keyboard=" + getLatinKeyboard().getKeyboardShiftState()
                    + " shiftKeyState=" + mShiftKeyState);
        if (isAlphabetMode()) {
            setManualTemporaryUpperCase(!isShiftedOrShiftLocked());
        } else {
            toggleShiftInSymbol();
        }
    }

    public void toggleCapsLock() {
        mInputMethodService.mHandler.cancelUpdateShiftState();
        if (DEBUG_STATE)
            Log.d(TAG, "toggleCapsLock:"
                    + " keyboard=" + getLatinKeyboard().getKeyboardShiftState()
                    + " shiftKeyState=" + mShiftKeyState);
        if (isAlphabetMode()) {
            if (isShiftLocked()) {
                // Shift key is long pressed while caps lock state, we will toggle back to normal
                // state. And mark as if shift key is released.
                setShiftLocked(false);
                mShiftKeyState.onRelease();
            } else {
                setShiftLocked(true);
            }
        }
    }

    private void setAutomaticTemporaryUpperCase() {
        LatinKeyboard latinKeyboard = getLatinKeyboard();
        if (latinKeyboard != null) {
            latinKeyboard.setAutomaticTemporaryUpperCase();
            mInputView.invalidateAllKeys();
        }
    }

    /**
     * Update keyboard shift state triggered by connected EditText status change.
     */
    public void updateShiftState() {
        final ShiftKeyState shiftKeyState = mShiftKeyState;
        if (DEBUG_STATE)
            Log.d(TAG, "updateShiftState:"
                    + " autoCaps=" + mInputMethodService.getCurrentAutoCapsState()
                    + " keyboard=" + getLatinKeyboard().getKeyboardShiftState()
                    + " shiftKeyState=" + shiftKeyState);
        if (isAlphabetMode()) {
            if (!isShiftLocked() && !shiftKeyState.isIgnoring()) {
                if (shiftKeyState.isReleasing() && mInputMethodService.getCurrentAutoCapsState()) {
                    // Only when shift key is releasing, automatic temporary upper case will be set.
                    setAutomaticTemporaryUpperCase();
                } else {
                    setManualTemporaryUpperCase(shiftKeyState.isMomentary());
                }
            }
        } else {
            // In symbol keyboard mode, we should clear shift key state because only alphabet
            // keyboard has shift key.
            shiftKeyState.onRelease();
        }
    }

    public void changeKeyboardMode() {
        if (DEBUG_STATE)
            Log.d(TAG, "changeKeyboardMode:"
                    + " keyboard=" + getLatinKeyboard().getKeyboardShiftState()
                    + " shiftKeyState=" + mShiftKeyState);
        toggleKeyboardMode();
        if (isShiftLocked() && isAlphabetMode())
            setShiftLocked(true);
        updateShiftState();
    }

    public void onPressShift(boolean withSliding) {
        if (!isKeyboardAvailable())
            return;
        // If accessibility is enabled, disable momentary shift lock.
        if (isAccessibilityEnabled())
            return;
        ShiftKeyState shiftKeyState = mShiftKeyState;
        if (DEBUG_STATE)
            Log.d(TAG, "onPressShift:"
                    + " keyboard=" + getLatinKeyboard().getKeyboardShiftState()
                    + " shiftKeyState=" + shiftKeyState + " sliding=" + withSliding);
        if (isAlphabetMode()) {
            if (isShiftLocked()) {
                // Shift key is pressed while caps lock state, we will treat this state as shifted
                // caps lock state and mark as if shift key pressed while normal state.
                shiftKeyState.onPress();
                setManualTemporaryUpperCase(true);
            } else if (isAutomaticTemporaryUpperCase()) {
                // Shift key is pressed while automatic temporary upper case, we have to move to
                // manual temporary upper case.
                shiftKeyState.onPress();
                setManualTemporaryUpperCase(true);
            } else if (isShiftedOrShiftLocked()) {
                // In manual upper case state, we just record shift key has been pressing while
                // shifted state.
                shiftKeyState.onPressOnShifted();
            } else {
                // In base layout, chording or manual temporary upper case mode is started.
                shiftKeyState.onPress();
                toggleShift();
            }
        } else {
            // In symbol mode, just toggle symbol and symbol more keyboard.
            shiftKeyState.onPress();
            toggleShift();
            mSwitchState = SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE;
        }
    }

    public void onReleaseShift(boolean withSliding) {
        if (!isKeyboardAvailable())
            return;
        // If accessibility is enabled, disable momentary shift lock.
        if (isAccessibilityEnabled())
            return;
        ShiftKeyState shiftKeyState = mShiftKeyState;
        if (DEBUG_STATE)
            Log.d(TAG, "onReleaseShift:"
                    + " keyboard=" + getLatinKeyboard().getKeyboardShiftState()
                    + " shiftKeyState=" + shiftKeyState + " sliding=" + withSliding);
        if (isAlphabetMode()) {
            if (shiftKeyState.isMomentary()) {
                // After chording input while normal state.
                toggleShift();
            } else if (isShiftLocked() && !shiftKeyState.isIgnoring() && !withSliding) {
                // Shift has been pressed without chording while caps lock state.
                toggleCapsLock();
                // To be able to turn off caps lock by "double tap" on shift key, we should ignore
                // the second tap of the "double tap" from now for a while because we just have
                // already turned off caps lock above.
                mInputView.startIgnoringDoubleTap();
            } else if (isShiftedOrShiftLocked() && shiftKeyState.isPressingOnShifted()
                    && !withSliding) {
                // Shift has been pressed without chording while shifted state.
                toggleShift();
            } else if (isManualTemporaryUpperCaseFromAuto() && shiftKeyState.isPressing()
                    && !withSliding) {
                // Shift has been pressed without chording while manual temporary upper case
                // transited from automatic temporary upper case.
                toggleShift();
            }
        } else {
            // In symbol mode, snap back to the previous keyboard mode if the user chords the shift
            // key and another key, then releases the shift key.
            if (mSwitchState == SWITCH_STATE_CHORDING_SYMBOL) {
                toggleShift();
            }
        }
        shiftKeyState.onRelease();
    }

    public void onPressSymbol() {
        // If accessibility is enabled, disable momentary symbol lock.
        if (isAccessibilityEnabled())
            return;
        if (DEBUG_STATE)
            Log.d(TAG, "onPressSymbol:"
                    + " keyboard=" + getLatinKeyboard().getKeyboardShiftState()
                    + " symbolKeyState=" + mSymbolKeyState);
        changeKeyboardMode();
        mSymbolKeyState.onPress();
        mSwitchState = SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL;
    }

    public void onReleaseSymbol() {
        // If accessibility is enabled, disable momentary symbol lock.
        if (isAccessibilityEnabled())
            return;
        if (DEBUG_STATE)
            Log.d(TAG, "onReleaseSymbol:"
                    + " keyboard=" + getLatinKeyboard().getKeyboardShiftState()
                    + " symbolKeyState=" + mSymbolKeyState);
        // Snap back to the previous keyboard mode if the user chords the mode change key and
        // another key, then releases the mode change key.
        if (mSwitchState == SWITCH_STATE_CHORDING_ALPHA) {
            changeKeyboardMode();
        }
        mSymbolKeyState.onRelease();
    }

    public void onOtherKeyPressed() {
        // If accessibility is enabled, disable momentary mode locking.
        if (isAccessibilityEnabled())
            return;
        if (DEBUG_STATE)
            Log.d(TAG, "onOtherKeyPressed:"
                    + " keyboard=" + getLatinKeyboard().getKeyboardShiftState()
                    + " shiftKeyState=" + mShiftKeyState
                    + " symbolKeyState=" + mSymbolKeyState);
        mShiftKeyState.onOtherKeyPressed();
        mSymbolKeyState.onOtherKeyPressed();
    }

    public void onCancelInput() {
        // Snap back to the previous keyboard mode if the user cancels sliding input.
        if (getPointerCount() == 1) {
            if (mSwitchState == SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL) {
                changeKeyboardMode();
            } else if (mSwitchState == SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE) {
                toggleShift();
            }
        }
    }

    private void toggleShiftInSymbol() {
        if (isAlphabetMode())
            return;
        final LatinKeyboard keyboard;
        if (mCurrentId.equals(mSymbolsId) || !mCurrentId.equals(mSymbolsShiftedId)) {
            mCurrentId = mSymbolsShiftedId;
            keyboard = getKeyboard(mCurrentId);
            // Symbol shifted keyboard has an ALT key that has a caps lock style indicator. To
            // enable the indicator, we need to call setShiftLocked(true).
            keyboard.setShiftLocked(true);
        } else {
            mCurrentId = mSymbolsId;
            keyboard = getKeyboard(mCurrentId);
            // Symbol keyboard has an ALT key that has a caps lock style indicator. To disable the
            // indicator, we need to call setShiftLocked(false).
            keyboard.setShiftLocked(false);
        }
        setKeyboard(keyboard);
    }

    public boolean isInMomentarySwitchState() {
        return mSwitchState == SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL
                || mSwitchState == SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE;
    }

    public boolean isVibrateAndSoundFeedbackRequired() {
        return mInputView == null || !mInputView.isInSlidingKeyInput();
    }

    private int getPointerCount() {
        return mInputView == null ? 0 : mInputView.getPointerCount();
    }

    private void toggleKeyboardMode() {
        loadKeyboardInternal(mAttribute, mVoiceKeyEnabled, mVoiceButtonOnPrimary, !mIsSymbols);
        if (mIsSymbols) {
            mSwitchState = SWITCH_STATE_SYMBOL_BEGIN;
        } else {
            mSwitchState = SWITCH_STATE_ALPHA;
        }
    }

    public boolean isAccessibilityEnabled() {
        return mInputView != null && mInputView.isAccessibilityEnabled();
    }

    public boolean hasDistinctMultitouch() {
        return mInputView != null && mInputView.hasDistinctMultitouch();
    }

    private static boolean isSpaceCharacter(int c) {
        return c == Keyboard.CODE_SPACE || c == Keyboard.CODE_ENTER;
    }

    private static boolean isQuoteCharacter(int c) {
        // Apostrophe, quotation mark.
        if (c == '\'' || c == '"')
            return true;
        // \u2018: Left single quotation mark
        // \u2019: Right single quotation mark
        // \u201a: Single low-9 quotation mark
        // \u201b: Single high-reversed-9 quotation mark
        // \u201c: Left double quotation mark
        // \u201d: Right double quotation mark
        // \u201e: Double low-9 quotation mark
        // \u201f: Double high-reversed-9 quotation mark
        if (c >= '\u2018' && c <= '\u201f')
            return true;
        // \u00ab: Left-pointing double angle quotation mark
        // \u00bb: Right-pointing double angle quotation mark
        if (c == '\u00ab' || c == '\u00bb')
            return true;
        return false;
    }

    /**
     * Updates state machine to figure out when to automatically snap back to the previous mode.
     */
    public void onKey(int code) {
        if (DEBUG_STATE)
            Log.d(TAG, "onKey: code=" + code + " switchState=" + mSwitchState
                    + " pointers=" + getPointerCount());
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
                if (mIsSymbols) {
                    mSwitchState = SWITCH_STATE_SYMBOL_BEGIN;
                } else {
                    mSwitchState = SWITCH_STATE_ALPHA;
                }
            } else if (getPointerCount() == 1) {
                // Snap back to the previous keyboard mode if the user pressed the mode change key
                // and slid to other key, then released the finger.
                // If the user cancels the sliding input, snapping back to the previous keyboard
                // mode is handled by {@link #onCancelInput}.
                changeKeyboardMode();
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
            } else if (getPointerCount() == 1) {
                // Snap back to the previous keyboard mode if the user pressed the shift key on
                // symbol mode and slid to other key, then released the finger.
                toggleShift();
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
            break;
        case SWITCH_STATE_SYMBOL:
        case SWITCH_STATE_CHORDING_SYMBOL:
            // Snap back to alpha keyboard mode if user types one or more non-space/enter
            // characters followed by a space/enter or quotation mark.
            if (isSpaceCharacter(code) || isQuoteCharacter(code)) {
                changeKeyboardMode();
            }
            break;
        }
    }

    public LatinKeyboardView getInputView() {
        return mInputView;
    }

    public LatinKeyboardView onCreateInputView() {
        createInputViewInternal(mLayoutId, true);
        return mInputView;
    }

    private void createInputViewInternal(int newLayout, boolean forceReset) {
        int layoutId = newLayout;
        if (mLayoutId != layoutId || mInputView == null || forceReset) {
            if (mInputView != null) {
                mInputView.closing();
            }
            if (KEYBOARD_THEMES.length <= layoutId) {
                layoutId = Integer.valueOf(sConfigDefaultKeyboardThemeId);
            }

            Utils.GCUtils.getInstance().reset();
            boolean tryGC = true;
            for (int i = 0; i < Utils.GCUtils.GC_TRY_LOOP_MAX && tryGC; ++i) {
                try {
                    mInputView = (LatinKeyboardView) mInputMethodService.getLayoutInflater(
                            ).inflate(KEYBOARD_THEMES[layoutId], null);
                    tryGC = false;
                } catch (OutOfMemoryError e) {
                    Log.w(TAG, "load keyboard failed: " + e);
                    tryGC = Utils.GCUtils.getInstance().tryGCOrWait(
                            mLayoutId + "," + layoutId, e);
                } catch (InflateException e) {
                    Log.w(TAG, "load keyboard failed: " + e);
                    tryGC = Utils.GCUtils.getInstance().tryGCOrWait(
                            mLayoutId + "," + layoutId, e);
                }
            }
            mInputView.setOnKeyboardActionListener(mInputMethodService);
            mLayoutId = layoutId;
        }
    }

    private void postSetInputView() {
        mInputMethodService.mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mInputView != null) {
                    mInputMethodService.setInputView(mInputView);
                }
                mInputMethodService.updateInputViewShown();
            }
        });
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (PREF_KEYBOARD_LAYOUT.equals(key)) {
            final int layoutId = Integer.valueOf(
                    sharedPreferences.getString(key, sConfigDefaultKeyboardThemeId));
            createInputViewInternal(layoutId, false);
            postSetInputView();
        } else if (Settings.PREF_SETTINGS_KEY.equals(key)) {
            mSettingsKeyEnabledInSettings = getSettingsKeyMode(sharedPreferences,
                    mInputMethodService);
            createInputViewInternal(mLayoutId, true);
            postSetInputView();
        }
    }

    private int getColorScheme() {
        return (mInputView != null)
                ? mInputView.getColorScheme() : KeyboardView.COLOR_SCHEME_WHITE;
    }

    public void onAutoCorrectionStateChanged(boolean isAutoCorrection) {
        if (isAutoCorrection != mIsAutoCorrectionActive) {
            LatinKeyboardView keyboardView = getInputView();
            mIsAutoCorrectionActive = isAutoCorrection;
            keyboardView.invalidateKey(((LatinKeyboard) keyboardView.getKeyboard())
                    .onAutoCorrectionStateChanged(isAutoCorrection));
        }
    }

    private static boolean getSettingsKeyMode(SharedPreferences prefs, Context context) {
        Resources resources = context.getResources();
        final boolean showSettingsKeyOption = resources.getBoolean(
                R.bool.config_enable_show_settings_key_option);
        if (showSettingsKeyOption) {
            final String settingsKeyMode = prefs.getString(Settings.PREF_SETTINGS_KEY,
                    resources.getString(DEFAULT_SETTINGS_KEY_MODE));
            // We show the settings key when 1) SETTINGS_KEY_MODE_ALWAYS_SHOW or
            // 2) SETTINGS_KEY_MODE_AUTO and there are two or more enabled IMEs on the system
            if (settingsKeyMode.equals(resources.getString(SETTINGS_KEY_MODE_ALWAYS_SHOW))
                    || (settingsKeyMode.equals(resources.getString(SETTINGS_KEY_MODE_AUTO))
                            && Utils.hasMultipleEnabledIMEsOrSubtypes(
                                    (InputMethodManagerCompatWrapper.getInstance(context))))) {
                return true;
            }
            return false;
        }
        // If the show settings key option is disabled, we always try showing the settings key.
        return true;
    }
}
