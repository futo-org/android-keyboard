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

package com.android.inputmethod.latin;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.InflateException;

import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

public class KeyboardSwitcher implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "KeyboardSwitcher";
    private static final boolean DEBUG = false;

    public static final int MODE_TEXT = 0;
    public static final int MODE_URL = 1;
    public static final int MODE_EMAIL = 2;
    public static final int MODE_IM = 3;
    public static final int MODE_WEB = 4;
    public static final int MODE_PHONE = 5;

    public static final String DEFAULT_LAYOUT_ID = "4";
    public static final String PREF_KEYBOARD_LAYOUT = "pref_keyboard_layout_20100902";
    private static final int[] THEMES = new int [] {
        R.layout.input_basic, R.layout.input_basic_highcontrast, R.layout.input_stone_normal,
        R.layout.input_stone_bold, R.layout.input_gingerbread};

    // Ids for each characters' color in the keyboard
    private static final int CHAR_THEME_COLOR_WHITE = 0;
    private static final int CHAR_THEME_COLOR_BLACK = 1;

    // Tables which contains resource ids for each character theme color
    private static final int[] KBD_PHONE = new int[] {
            R.xml.kbd_phone, R.xml.kbd_phone_black
    };
    private static final int[] KBD_PHONE_SYMBOLS = new int[] {
            R.xml.kbd_phone_symbols, R.xml.kbd_phone_symbols_black
    };
    private static final int[] KBD_SYMBOLS = new int[] {
            R.xml.kbd_symbols, R.xml.kbd_symbols_black
    };
    private static final int[] KBD_SYMBOLS_SHIFT = new int[] {
            R.xml.kbd_symbols_shift, R.xml.kbd_symbols_shift_black
    };
    private static final int[] KBD_QWERTY = new int[] {
            R.xml.kbd_qwerty, R.xml.kbd_qwerty_black
    };

    private static final int SYMBOLS_MODE_STATE_NONE = 0;
    private static final int SYMBOLS_MODE_STATE_BEGIN = 1;
    private static final int SYMBOLS_MODE_STATE_SYMBOL = 2;

    private SubtypeSwitcher mSubtypeSwitcher;

    private LatinKeyboardView mInputView;
    private LatinIME mInputMethodService;

    private ShiftKeyState mShiftState = new ShiftKeyState();
    private ModifierKeyState mSymbolKeyState = new ModifierKeyState();

    private KeyboardId mSymbolsId;
    private KeyboardId mSymbolsShiftedId;

    private KeyboardId mCurrentId;
    private final HashMap<KeyboardId, SoftReference<LatinKeyboard>> mKeyboardCache =
            new HashMap<KeyboardId, SoftReference<LatinKeyboard>>();

    private int mMode = MODE_TEXT; /* default value */
    private int mImeOptions;
    private boolean mIsSymbols;
    /** mIsAutoCompletionActive indicates that auto completed word will be input instead of
     * what user actually typed. */
    private boolean mIsAutoCompletionActive;
    private boolean mVoiceButtonEnabled;
    private boolean mVoiceButtonOnPrimary;
    private int mSymbolsModeState = SYMBOLS_MODE_STATE_NONE;

    // Indicates whether or not we have the settings key
    private boolean mHasSettingsKey;
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
    }

    public static void init(LatinIME ims) {
        sInstance.mInputMethodService = ims;
        sInstance.mSubtypeSwitcher = SubtypeSwitcher.getInstance();

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ims);
        sInstance.mLayoutId = Integer.valueOf(
                prefs.getString(PREF_KEYBOARD_LAYOUT, DEFAULT_LAYOUT_ID));
        prefs.registerOnSharedPreferenceChangeListener(sInstance);
    }

    private void makeSymbolsKeyboardIds() {
        final Locale locale = mSubtypeSwitcher.getInputLocale();
        final int orientation = mInputMethodService.getResources().getConfiguration().orientation;
        final int mode = mMode;
        final int colorScheme = getCharColorId();
        final boolean hasSettingsKey = mHasSettingsKey;
        final boolean hasVoiceKey = mVoiceButtonEnabled && !mVoiceButtonOnPrimary;
        final int imeOptions = mImeOptions;
        mSymbolsId = new KeyboardId(locale, orientation, mode,
                KBD_SYMBOLS, colorScheme, hasSettingsKey, hasVoiceKey, imeOptions, true);
        mSymbolsShiftedId = new KeyboardId(locale, orientation, mode,
                KBD_SYMBOLS_SHIFT, colorScheme, hasSettingsKey, hasVoiceKey, imeOptions, true);
    }

    /**
     * Represents the parameters necessary to construct a new LatinKeyboard,
     * which also serve as a unique identifier for each keyboard type.
     */
    public static class KeyboardId {
        public final Locale mLocale;
        public final int mOrientation;
        public final int mMode;
        public final int[] mXmlArray;
        public final int mColorScheme;
        public final boolean mHasSettingsKey;
        public final boolean mHasVoiceKey;
        public final int mImeOptions;
        public final boolean mEnableShiftLock;

        private final int mHashCode;

        public KeyboardId(Locale locale, int orientation, int mode,
                int[] xmlArray, int colorScheme, boolean hasSettingsKey, boolean hasVoiceKey,
                int imeOptions, boolean enableShiftLock) {
            this.mLocale = locale;
            this.mOrientation = orientation;
            this.mMode = mode;
            this.mXmlArray = xmlArray;
            this.mColorScheme = colorScheme;
            this.mHasSettingsKey = hasSettingsKey;
            this.mHasVoiceKey = hasVoiceKey;
            this.mImeOptions = imeOptions;
            this.mEnableShiftLock = enableShiftLock;

            this.mHashCode = Arrays.hashCode(new Object[] {
                    locale,
                    orientation,
                    mode,
                    xmlArray,
                    colorScheme,
                    hasSettingsKey,
                    hasVoiceKey,
                    imeOptions,
                    enableShiftLock,
            });
        }

        public int getXmlId() {
            return mXmlArray[mColorScheme];
        }

        public boolean isAlphabetMode() {
            return mXmlArray == KBD_QWERTY;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof KeyboardId && equals((KeyboardId) other);
        }

        private boolean equals(KeyboardId other) {
            return other.mLocale.equals(this.mLocale)
                && other.mOrientation == this.mOrientation
                && other.mMode == this.mMode
                && other.mXmlArray == this.mXmlArray
                && other.mColorScheme == this.mColorScheme
                && other.mHasSettingsKey == this.mHasSettingsKey
                && other.mHasVoiceKey == this.mHasVoiceKey
                && other.mImeOptions == this.mImeOptions
                && other.mEnableShiftLock == this.mEnableShiftLock;
        }

        @Override
        public int hashCode() {
            return mHashCode;
        }

        @Override
        public String toString() {
            return String.format("[%s %s %5s imeOptions=0x%08x xml=0x%08x %s%s%s%s]",
                    mLocale,
                    (mOrientation == 1 ? "port" : "land"),
                    modeName(mMode),
                    mImeOptions,
                    mXmlArray[0],
                    (mColorScheme == CHAR_THEME_COLOR_WHITE ? "white" : "black"),
                    (mHasSettingsKey ? " hasSettingsKey" : ""),
                    (mHasVoiceKey ? " hasVoiceKey" : ""),
                    (mEnableShiftLock ? " enableShiftLock" : ""));
        }

        private static String modeName(int mode) {
            switch (mode) {
            case MODE_TEXT: return "text";
            case MODE_URL: return "url";
            case MODE_EMAIL: return "email";
            case MODE_IM: return "im";
            case MODE_WEB: return "web";
            case MODE_PHONE: return "phone";
            }
            return null;
        }
    }

    private boolean hasVoiceKey(boolean isSymbols) {
        return mVoiceButtonEnabled && (isSymbols != mVoiceButtonOnPrimary);
    }

    public void loadKeyboard(int mode, int imeOptions, boolean voiceButtonEnabled,
            boolean voiceButtonOnPrimary) {
        mSymbolsModeState = SYMBOLS_MODE_STATE_NONE;
        try {
            loadKeyboardInternal(mode, imeOptions, voiceButtonEnabled, voiceButtonOnPrimary,
                    false);
        } catch (RuntimeException e) {
            Log.w(TAG, e);
            LatinImeLogger.logOnException(mode + "," + imeOptions, e);
        }
    }

    private void loadKeyboardInternal(int mode, int imeOptions, boolean voiceButtonEnabled,
            boolean voiceButtonOnPrimary, boolean isSymbols) {
        if (mInputView == null) return;
        mInputView.setPreviewEnabled(mInputMethodService.getPopupOn());

        mMode = mode;
        mImeOptions = imeOptions;
        mVoiceButtonEnabled = voiceButtonEnabled;
        mVoiceButtonOnPrimary = voiceButtonOnPrimary;
        mIsSymbols = isSymbols;
        // Update the settings key state because number of enabled IMEs could have been changed
        mHasSettingsKey = getSettingsKeyMode(
                PreferenceManager.getDefaultSharedPreferences(mInputMethodService),
                mInputMethodService);
        makeSymbolsKeyboardIds();

        KeyboardId id = getKeyboardId(mode, imeOptions, isSymbols);
        LatinKeyboard keyboard = getKeyboard(id);

        if (mode == MODE_PHONE) {
            mInputView.setPhoneKeyboard(keyboard);
        }

        mCurrentId = id;
        mInputView.setKeyboard(keyboard);
    }

    private LatinKeyboard getKeyboard(KeyboardId id) {
        final SoftReference<LatinKeyboard> ref = mKeyboardCache.get(id);
        LatinKeyboard keyboard = (ref == null) ? null : ref.get();
        if (keyboard == null) {
            final Resources res = mInputMethodService.getResources();
            final Locale savedLocale =  mSubtypeSwitcher.changeSystemLocale(
                    mSubtypeSwitcher.getInputLocale());

            final int xml = id.getXmlId();
            keyboard = new LatinKeyboard(mInputMethodService, id);
            keyboard.setVoiceMode(
                    hasVoiceKey(xml == R.xml.kbd_symbols || xml == R.xml.kbd_symbols_black),
                    mVoiceButtonEnabled);
            keyboard.setLanguageSwitcher(mSubtypeSwitcher.getLanguageSwitcher());
            keyboard.setImeOptions(res, id.mMode, id.mImeOptions);
            keyboard.setColorOfSymbolIcons(isBlackSym(id.mColorScheme));

            if (id.mEnableShiftLock) {
                keyboard.enableShiftLock();
            }

            mKeyboardCache.put(id, new SoftReference<LatinKeyboard>(keyboard));
            if (DEBUG)
                Log.d(TAG, "keyboard cache size=" + mKeyboardCache.size() + ": "
                        + ((ref == null) ? "LOAD" : "GCed") + " id=" + id);

            mSubtypeSwitcher.changeSystemLocale(savedLocale);
        } else if (DEBUG) {
            Log.d(TAG, "keyboard cache size=" + mKeyboardCache.size() + ": HIT  id=" + id);
        }

        keyboard.onAutoCompletionStateChanged(mIsAutoCompletionActive);
        keyboard.setShifted(false);
        return keyboard;
    }

    private KeyboardId getKeyboardId(int mode, int imeOptions, boolean isSymbols) {
        final boolean hasVoiceKey = hasVoiceKey(isSymbols);
        final int charColorId = getCharColorId();
        final int[] xmlArray;
        final boolean enableShiftLock;

        if (isSymbols) {
            xmlArray = mode == MODE_PHONE ? KBD_PHONE_SYMBOLS : KBD_SYMBOLS;
            enableShiftLock = false;
        } else {  // QWERTY
            xmlArray = mode == MODE_PHONE ? KBD_PHONE : KBD_QWERTY;
            enableShiftLock = mode == MODE_PHONE ? false : true;
        }
        final int orientation = mInputMethodService.getResources().getConfiguration().orientation;
        final Locale locale = mSubtypeSwitcher.getInputLocale();
        return new KeyboardId(locale, orientation, mode, xmlArray,
                charColorId, mHasSettingsKey, hasVoiceKey, imeOptions, enableShiftLock);
    }

    public int getKeyboardMode() {
        return mMode;
    }

    public boolean isAlphabetMode() {
        return mCurrentId != null && mCurrentId.isAlphabetMode();
    }

    public boolean isInputViewShown() {
        return mInputView != null && mInputView.isShown();
    }

    public boolean isKeyboardAvailable() {
        if (mInputView != null)
            return mInputView.getLatinKeyboard() != null;
        return false;
    }

    private LatinKeyboard getLatinKeyboard() {
        if (mInputView != null)
            return mInputView.getLatinKeyboard();
        return null;
    }

    public void setPreferredLetters(int[] frequencies) {
        LatinKeyboard latinKeyboard = getLatinKeyboard();
        if (latinKeyboard != null)
            latinKeyboard.setPreferredLetters(frequencies);
    }

    public void keyReleased() {
        LatinKeyboard latinKeyboard = getLatinKeyboard();
        if (latinKeyboard != null)
            latinKeyboard.keyReleased();
    }

    public boolean isShifted() {
        LatinKeyboard latinKeyboard = getLatinKeyboard();
        if (latinKeyboard != null)
            return latinKeyboard.isShifted();
        return false;
    }

    public boolean isShiftLocked() {
        LatinKeyboard latinKeyboard = getLatinKeyboard();
        if (latinKeyboard != null)
            return latinKeyboard.isShiftLocked();
        return false;
    }

    private void setShifted(boolean shifted) {
        LatinKeyboard latinKeyboard = getLatinKeyboard();
        if (latinKeyboard != null && latinKeyboard.setShifted(shifted)) {
            mInputView.invalidateAllKeys();
        }
    }

    private void setShiftLocked(boolean shiftLocked) {
        LatinKeyboard latinKeyboard = getLatinKeyboard();
        if (latinKeyboard != null && latinKeyboard.setShiftLocked(shiftLocked)) {
            mInputView.invalidateAllKeys();
        }
    }

    public void toggleShift() {
        handleShiftInternal(false);
    }

    private void resetShift() {
        handleShiftInternal(true);
    }

    private void handleShiftInternal(boolean forceNormal) {
        mInputMethodService.mHandler.cancelUpdateShiftState();
        if (isAlphabetMode()) {
            if (forceNormal) {
                setShifted(false);
            } else {
                setShifted(!isShifted());
            }
        } else {
            toggleShiftInSymbol();
        }
    }

    public void toggleCapsLock() {
        mInputMethodService.mHandler.cancelUpdateShiftState();
        if (isAlphabetMode()) {
            if (isShiftLocked()) {
                // setShifted(false) also disable shift locked state.
                // Note: Caps lock LED is off when Key.on is false.
                setShifted(false);
            } else {
                // setShiftLocked(true) enable shift state too.
                // Note: Caps lock LED is on when Key.on is true.
                setShiftLocked(true);
            }
        }
    }

    public void updateShiftState() {
        if (isAlphabetMode() && !mShiftState.isIgnoring()) {
            final boolean autoCapsMode = mInputMethodService.getCurrentAutoCapsState();
            setShifted(mShiftState.isMomentary() || isShiftLocked() || autoCapsMode);
        }
    }

    public void changeKeyboardMode() {
        toggleKeyboardMode();
        if (isShiftLocked() && isAlphabetMode())
            setShiftLocked(true);
        updateShiftState();
    }

    public void onPressShift() {
        if (!isKeyboardAvailable())
            return;
        if (isAlphabetMode() && isShifted()) {
            // In alphabet mode, we don't call toggleShift() when we are already in the shifted
            // state.
            mShiftState.onPressOnShifted();
        } else {
            // In alphabet mode, we call toggleShift() to go into the shifted mode only when we are
            // not in the shifted state.
            // This else clause also handles shift key pressing in symbol mode.
            mShiftState.onPress();
            toggleShift();
        }
    }

    public void onReleaseShift() {
        if (!isKeyboardAvailable())
            return;
        if (isAlphabetMode()) {
            if (mShiftState.isMomentary()) {
                resetShift();
            } else if (isShifted() && mShiftState.isPressingOnShifted()) {
                // In alphabet mode, we call toggleShift() to go into the non shifted state only
                // when we are in the shifted state -- temporary shifted mode or caps lock mode.
                toggleShift();
            }
        }
        mShiftState.onRelease();
    }

    public void onPressSymbol() {
        changeKeyboardMode();
        mSymbolKeyState.onPress();
    }

    public void onReleaseSymbol() {
        if (mSymbolKeyState.isMomentary())
            changeKeyboardMode();
        mSymbolKeyState.onRelease();
    }

    public void onOtherKeyPressed() {
        mShiftState.onOtherKeyPressed();
        mSymbolKeyState.onOtherKeyPressed();
    }

    private void toggleShiftInSymbol() {
        if (isAlphabetMode())
            return;
        final LatinKeyboard keyboard;
        if (mCurrentId.equals(mSymbolsId) || !mCurrentId.equals(mSymbolsShiftedId)) {
            mCurrentId = mSymbolsShiftedId;
            keyboard = getKeyboard(mCurrentId);
            // Symbol shifted keyboard has an ALT key that has a caps lock style indicator. To
            // enable the indicator, we need to call enableShiftLock() and setShiftLocked(true).
            // Thus we can keep the ALT key's Key.on value true while LatinKey.onRelease() is
            // called.
            keyboard.setShiftLocked(true);
        } else {
            mCurrentId = mSymbolsId;
            keyboard = getKeyboard(mCurrentId);
            // Symbol keyboard has an ALT key that has a caps lock style indicator. To disable the
            // indicator, we need to call enableShiftLock() and setShiftLocked(false).
            keyboard.setShifted(false);
        }
        mInputView.setKeyboard(keyboard);
    }

    public void toggleKeyboardMode() {
        loadKeyboardInternal(mMode, mImeOptions, mVoiceButtonEnabled, mVoiceButtonOnPrimary,
                !mIsSymbols);
        if (mIsSymbols) {
            mSymbolsModeState = SYMBOLS_MODE_STATE_BEGIN;
        } else {
            mSymbolsModeState = SYMBOLS_MODE_STATE_NONE;
        }
    }

    public boolean hasDistinctMultitouch() {
        return mInputView != null && mInputView.hasDistinctMultitouch();
    }

    /**
     * Updates state machine to figure out when to automatically switch back to alpha mode.
     */
    public void onKey(int key) {
        // Switch back to alpha mode if user types one or more non-space/enter
        // characters followed by a space/enter
        switch (mSymbolsModeState) {
        case SYMBOLS_MODE_STATE_BEGIN:
            if (key != LatinIME.KEYCODE_SPACE && key != LatinIME.KEYCODE_ENTER && key > 0) {
                mSymbolsModeState = SYMBOLS_MODE_STATE_SYMBOL;
            }
            break;
        case SYMBOLS_MODE_STATE_SYMBOL:
            if (key == LatinIME.KEYCODE_ENTER || key == LatinIME.KEYCODE_SPACE) {
                changeKeyboardMode();
            }
            break;
        }
    }

    public LatinKeyboardView getInputView() {
        return mInputView;
    }

    public void loadKeyboardView() {
        loadKeyboardViewInternal(mLayoutId, true);
    }

    private void loadKeyboardViewInternal(int newLayout, boolean forceReset) {
        if (mLayoutId != newLayout || mInputView == null || forceReset) {
            if (mInputView != null) {
                mInputView.closing();
            }
            if (THEMES.length <= newLayout) {
                newLayout = Integer.valueOf(DEFAULT_LAYOUT_ID);
            }

            LatinIMEUtil.GCUtils.getInstance().reset();
            boolean tryGC = true;
            for (int i = 0; i < LatinIMEUtil.GCUtils.GC_TRY_LOOP_MAX && tryGC; ++i) {
                try {
                    mInputView = (LatinKeyboardView) mInputMethodService.getLayoutInflater(
                            ).inflate(THEMES[newLayout], null);
                    tryGC = false;
                } catch (OutOfMemoryError e) {
                    tryGC = LatinIMEUtil.GCUtils.getInstance().tryGCOrWait(
                            mLayoutId + "," + newLayout, e);
                } catch (InflateException e) {
                    tryGC = LatinIMEUtil.GCUtils.getInstance().tryGCOrWait(
                            mLayoutId + "," + newLayout, e);
                }
            }
            mInputView.setOnKeyboardActionListener(mInputMethodService);
            mLayoutId = newLayout;
        }
        mInputMethodService.mHandler.post(new Runnable() {
            public void run() {
                if (mInputView != null) {
                    mInputMethodService.setInputView(mInputView);
                }
                mInputMethodService.updateInputViewShown();
            }});
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (PREF_KEYBOARD_LAYOUT.equals(key)) {
            final int layoutId = Integer.valueOf(
                    sharedPreferences.getString(key, DEFAULT_LAYOUT_ID));
            loadKeyboardViewInternal(layoutId, false);
        } else if (LatinIMESettings.PREF_SETTINGS_KEY.equals(key)) {
            mHasSettingsKey = getSettingsKeyMode(sharedPreferences, mInputMethodService);
            loadKeyboardViewInternal(mLayoutId, true);
        }
    }

    public boolean isBlackSym() {
        if (mInputView != null && mInputView.getSymbolColorScheme() == 1) {
            return true;
        }
        return false;
    }

    private boolean isBlackSym(int colorScheme) {
        return colorScheme == CHAR_THEME_COLOR_BLACK;
    }

    private int getCharColorId() {
        if (isBlackSym()) {
            return CHAR_THEME_COLOR_BLACK;
        } else {
            return CHAR_THEME_COLOR_WHITE;
        }
    }

    public void onAutoCompletionStateChanged(boolean isAutoCompletion) {
        if (isAutoCompletion != mIsAutoCompletionActive) {
            LatinKeyboardView keyboardView = getInputView();
            mIsAutoCompletionActive = isAutoCompletion;
            keyboardView.invalidateKey(((LatinKeyboard) keyboardView.getKeyboard())
                    .onAutoCompletionStateChanged(isAutoCompletion));
        }
    }

    private static boolean getSettingsKeyMode(SharedPreferences prefs, Context context) {
        Resources resources = context.getResources();
        final boolean showSettingsKeyOption = resources.getBoolean(
                R.bool.config_enable_show_settings_key_option);
        if (showSettingsKeyOption) {
            final String settingsKeyMode = prefs.getString(LatinIMESettings.PREF_SETTINGS_KEY,
                    resources.getString(DEFAULT_SETTINGS_KEY_MODE));
            // We show the settings key when 1) SETTINGS_KEY_MODE_ALWAYS_SHOW or
            // 2) SETTINGS_KEY_MODE_AUTO and there are two or more enabled IMEs on the system
            if (settingsKeyMode.equals(resources.getString(SETTINGS_KEY_MODE_ALWAYS_SHOW))
                    || (settingsKeyMode.equals(resources.getString(SETTINGS_KEY_MODE_AUTO))
                            && LatinIMEUtil.hasMultipleEnabledIMEs(context))) {
                return true;
            }
        }
        return false;
    }
}
