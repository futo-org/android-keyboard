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

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.view.InflateException;

import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

public class KeyboardSwitcher implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final int MODE_TEXT = 0;
    public static final int MODE_URL = 1;
    public static final int MODE_EMAIL = 2;
    public static final int MODE_IM = 3;
    public static final int MODE_WEB = 4;
    public static final int MODE_PHONE = 5;

    public static final int MODE_NONE = -1;

    // Main keyboard layouts without the settings key
    public static final int KEYBOARDMODE_NORMAL = R.id.mode_normal;
    public static final int KEYBOARDMODE_URL = R.id.mode_url;
    public static final int KEYBOARDMODE_EMAIL = R.id.mode_email;
    public static final int KEYBOARDMODE_IM = R.id.mode_im;
    public static final int KEYBOARDMODE_WEB = R.id.mode_webentry;
    public static final int[] QWERTY_MODES = {
            KEYBOARDMODE_NORMAL,
            KEYBOARDMODE_URL,
            KEYBOARDMODE_EMAIL,
            KEYBOARDMODE_IM,
            KEYBOARDMODE_WEB,
            0 /* for MODE_PHONE */ };
    // Main keyboard layouts with the settings key
    public static final int KEYBOARDMODE_NORMAL_WITH_SETTINGS_KEY =
            R.id.mode_normal_with_settings_key;
    public static final int KEYBOARDMODE_URL_WITH_SETTINGS_KEY =
            R.id.mode_url_with_settings_key;
    public static final int KEYBOARDMODE_EMAIL_WITH_SETTINGS_KEY =
            R.id.mode_email_with_settings_key;
    public static final int KEYBOARDMODE_IM_WITH_SETTINGS_KEY =
            R.id.mode_im_with_settings_key;
    public static final int KEYBOARDMODE_WEB_WITH_SETTINGS_KEY =
            R.id.mode_webentry_with_settings_key;
    public static final int[] QWERTY_WITH_SETTINGS_KEY_MODES = {
            KEYBOARDMODE_NORMAL_WITH_SETTINGS_KEY,
            KEYBOARDMODE_URL_WITH_SETTINGS_KEY,
            KEYBOARDMODE_EMAIL_WITH_SETTINGS_KEY,
            KEYBOARDMODE_IM_WITH_SETTINGS_KEY,
            KEYBOARDMODE_WEB_WITH_SETTINGS_KEY,
            0 /* for MODE_PHONE */ };

    // Symbols keyboard layouts without the settings key
    public static final int KEYBOARDMODE_SYMBOLS_NORMAL = R.id.mode_symbols_normal;
    public static final int KEYBOARDMODE_SYMBOLS_URL = R.id.mode_symbols_url;
    public static final int KEYBOARDMODE_SYMBOLS_EMAIL = R.id.mode_symbols_email;
    public static final int KEYBOARDMODE_SYMBOLS_IM = R.id.mode_symbols_im;
    public static final int KEYBOARDMODE_SYMBOLS_WEB = R.id.mode_symbols_webentry;
    public static final int[] SYMBOLS_MODES = {
            KEYBOARDMODE_SYMBOLS_NORMAL,
            KEYBOARDMODE_SYMBOLS_URL,
            KEYBOARDMODE_SYMBOLS_EMAIL,
            KEYBOARDMODE_SYMBOLS_IM,
            KEYBOARDMODE_SYMBOLS_WEB,
            0 /* for MODE_PHONE */ };
    // Symbols keyboard layouts with the settings key
    public static final int KEYBOARDMODE_SYMBOLS_NORMAL_WITH_SETTINGS_KEY =
            R.id.mode_symbols_normal_with_settings_key;
    public static final int KEYBOARDMODE_SYMBOLS_URL_WITH_SETTINGS_KEY =
            R.id.mode_symbols_url_with_settings_key;
    public static final int KEYBOARDMODE_SYMBOLS_EMAIL_WITH_SETTINGS_KEY =
            R.id.mode_symbols_email_with_settings_key;
    public static final int KEYBOARDMODE_SYMBOLS_IM_WITH_SETTINGS_KEY =
            R.id.mode_symbols_im_with_settings_key;
    public static final int KEYBOARDMODE_SYMBOLS_WEB_WITH_SETTINGS_KEY =
            R.id.mode_symbols_webentry_with_settings_key;
    public static final int[] SYMBOLS_WITH_SETTINGS_KEY_MODES = {
            KEYBOARDMODE_SYMBOLS_NORMAL_WITH_SETTINGS_KEY,
            KEYBOARDMODE_SYMBOLS_URL_WITH_SETTINGS_KEY,
            KEYBOARDMODE_SYMBOLS_EMAIL_WITH_SETTINGS_KEY,
            KEYBOARDMODE_SYMBOLS_IM_WITH_SETTINGS_KEY,
            KEYBOARDMODE_SYMBOLS_WEB_WITH_SETTINGS_KEY,
            0 /* for MODE_PHONE */ };

    public static final String DEFAULT_LAYOUT_ID = "4";
    public static final String PREF_KEYBOARD_LAYOUT = "pref_keyboard_layout_20100902";
    private static final int[] THEMES = new int [] {
        R.layout.input_basic, R.layout.input_basic_highcontrast, R.layout.input_stone_normal,
        R.layout.input_stone_bold, R.layout.input_gingerbread};

    // Ids for each characters' color in the keyboard
    private static final int CHAR_THEME_COLOR_WHITE = 0;
    private static final int CHAR_THEME_COLOR_BLACK = 1;

    // Tables which contains resource ids for each character theme color
    private static final int[] KBD_PHONE = new int[] {R.xml.kbd_phone, R.xml.kbd_phone_black};
    private static final int[] KBD_PHONE_SYMBOLS = new int[] {
        R.xml.kbd_phone_symbols, R.xml.kbd_phone_symbols_black};
    private static final int[] KBD_SYMBOLS = new int[] {
        R.xml.kbd_symbols, R.xml.kbd_symbols_black};
    private static final int[] KBD_SYMBOLS_SHIFT = new int[] {
        R.xml.kbd_symbols_shift, R.xml.kbd_symbols_shift_black};
    private static final int[] KBD_QWERTY = new int[] {R.xml.kbd_qwerty, R.xml.kbd_qwerty_black};

    private static final int SYMBOLS_MODE_STATE_NONE = 0;
    private static final int SYMBOLS_MODE_STATE_BEGIN = 1;
    private static final int SYMBOLS_MODE_STATE_SYMBOL = 2;

    private LatinKeyboardView mInputView;
    private static final int[] ALPHABET_MODES = {
        KEYBOARDMODE_NORMAL,
        KEYBOARDMODE_URL,
        KEYBOARDMODE_EMAIL,
        KEYBOARDMODE_IM,
        KEYBOARDMODE_WEB,
        KEYBOARDMODE_NORMAL_WITH_SETTINGS_KEY,
        KEYBOARDMODE_URL_WITH_SETTINGS_KEY,
        KEYBOARDMODE_EMAIL_WITH_SETTINGS_KEY,
        KEYBOARDMODE_IM_WITH_SETTINGS_KEY,
        KEYBOARDMODE_WEB_WITH_SETTINGS_KEY };

    private final LatinIME mInputMethodService;

    private KeyboardId mSymbolsId;
    private KeyboardId mSymbolsShiftedId;

    private KeyboardId mCurrentId;
    private final HashMap<KeyboardId, SoftReference<LatinKeyboard>> mKeyboards;

    private int mMode = MODE_NONE; /** One of the MODE_XXX values */
    private int mImeOptions;
    private boolean mIsSymbols;
    /** mIsAutoCompletionActive indicates that auto completed word will be input instead of
     * what user actually typed. */
    private boolean mIsAutoCompletionActive;
    private boolean mHasVoice;
    private boolean mVoiceOnPrimary;
    private int mSymbolsModeState = SYMBOLS_MODE_STATE_NONE;

    // Indicates whether or not we have the settings key
    private boolean mHasSettingsKey;
    private static final int SETTINGS_KEY_MODE_AUTO = R.string.settings_key_mode_auto;
    private static final int SETTINGS_KEY_MODE_ALWAYS_SHOW =
            R.string.settings_key_mode_always_show;
    private static final int SETTINGS_KEY_MODE_ALWAYS_HIDE =
            R.string.settings_key_mode_always_hide;

    private int mLastDisplayWidth;
    private LanguageSwitcher mLanguageSwitcher;
    private Locale mInputLocale;

    private int mLayoutId;

    public KeyboardSwitcher(LatinIME ims) {
        mInputMethodService = ims;

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ims);
        mLayoutId = Integer.valueOf(prefs.getString(PREF_KEYBOARD_LAYOUT, DEFAULT_LAYOUT_ID));
        updateSettingsKeyState(prefs);
        prefs.registerOnSharedPreferenceChangeListener(this);

        mKeyboards = new HashMap<KeyboardId, SoftReference<LatinKeyboard>>();
        mSymbolsId = makeSymbolsId(false);
        mSymbolsShiftedId = makeSymbolsShiftedId(false);
    }

    /**
     * Sets the input locale, when there are multiple locales for input.
     * If no locale switching is required, then the locale should be set to null.
     * @param locale the current input locale, or null for default locale with no locale 
     * button.
     */
    public void setLanguageSwitcher(LanguageSwitcher languageSwitcher) {
        mLanguageSwitcher = languageSwitcher;
        mInputLocale = mLanguageSwitcher.getInputLocale();
    }

    private KeyboardId makeSymbolsId(boolean hasVoice) {
        final int mode = mMode == MODE_NONE ? MODE_TEXT : mMode;
        return new KeyboardId(KBD_SYMBOLS[getCharColorId()], mHasSettingsKey ?
                SYMBOLS_WITH_SETTINGS_KEY_MODES[mode] : SYMBOLS_MODES[mode],
                false, hasVoice);
    }

    private KeyboardId makeSymbolsShiftedId(boolean hasVoice) {
        final int mode = mMode == MODE_NONE ? MODE_TEXT : mMode;
        return new KeyboardId(KBD_SYMBOLS_SHIFT[getCharColorId()], mHasSettingsKey ?
                SYMBOLS_WITH_SETTINGS_KEY_MODES[mode] : SYMBOLS_MODES[mode],
                false, hasVoice);
    }

    private void makeSymbolsKeyboardIds() {
        mSymbolsId = makeSymbolsId(mHasVoice && !mVoiceOnPrimary);
        mSymbolsShiftedId = makeSymbolsShiftedId(mHasVoice && !mVoiceOnPrimary);
    }

    public void makeKeyboards(boolean forceCreate) {
        makeSymbolsKeyboardIds();
        if (forceCreate) mKeyboards.clear();
        // Configuration change is coming after the keyboard gets recreated. So don't rely on that.
        // If keyboards have already been made, check if we have a screen width change and 
        // create the keyboard layouts again at the correct orientation
        int displayWidth = mInputMethodService.getMaxWidth();
        if (displayWidth == mLastDisplayWidth) return;
        mLastDisplayWidth = displayWidth;
        if (!forceCreate) mKeyboards.clear();
    }

    /**
     * Represents the parameters necessary to construct a new LatinKeyboard,
     * which also serve as a unique identifier for each keyboard type.
     */
    private static class KeyboardId {
        // TODO: should have locale and portrait/landscape orientation?
        public final int mXml;
        public final int mKeyboardMode; /** A KEYBOARDMODE_XXX value */
        public final boolean mEnableShiftLock;
        public final boolean mHasVoice;

        private final int mHashCode;

        public KeyboardId(int xml, int mode, boolean enableShiftLock, boolean hasVoice) {
            this.mXml = xml;
            this.mKeyboardMode = mode;
            this.mEnableShiftLock = enableShiftLock;
            this.mHasVoice = hasVoice;

            this.mHashCode = Arrays.hashCode(new Object[] {
               xml, mode, enableShiftLock, hasVoice
            });
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof KeyboardId && equals((KeyboardId) other);
        }

        private boolean equals(KeyboardId other) {
            return other.mXml == this.mXml
                && other.mKeyboardMode == this.mKeyboardMode
                && other.mEnableShiftLock == this.mEnableShiftLock
                && other.mHasVoice == this.mHasVoice;
        }

        @Override
        public int hashCode() {
            return mHashCode;
        }
    }

    public void setVoiceMode(boolean enableVoice, boolean voiceOnPrimary) {
        if (enableVoice != mHasVoice || voiceOnPrimary != mVoiceOnPrimary) {
            mKeyboards.clear();
        }
        mHasVoice = enableVoice;
        mVoiceOnPrimary = voiceOnPrimary;
        setKeyboardMode(mMode, mImeOptions, mHasVoice, mIsSymbols);
    }

    private boolean hasVoiceButton(boolean isSymbols) {
        return mHasVoice && (isSymbols != mVoiceOnPrimary);
    }

    public void setKeyboardMode(int mode, int imeOptions, boolean enableVoice) {
        mSymbolsModeState = SYMBOLS_MODE_STATE_NONE;
        try {
            setKeyboardMode(mode, imeOptions, enableVoice, false);
        } catch (RuntimeException e) {
            LatinImeLogger.logOnException(mode + "," + imeOptions, e);
        }
    }

    private void setKeyboardMode(int mode, int imeOptions, boolean enableVoice, boolean isSymbols) {
        if (mInputView == null) return;
        mMode = mode;
        mImeOptions = imeOptions;
        makeSymbolsKeyboardIds();
        if (enableVoice != mHasVoice) {
            // TODO clean up this unnecessary recursive call.
            setVoiceMode(enableVoice, mVoiceOnPrimary);
        }
        mIsSymbols = isSymbols;

        mInputView.setPreviewEnabled(mInputMethodService.getPopupOn());
        KeyboardId id = getKeyboardId(mode, imeOptions, isSymbols);
        LatinKeyboard keyboard = null;
        keyboard = getKeyboard(id);

        if (mode == MODE_PHONE) {
            mInputView.setPhoneKeyboard(keyboard);
        }

        mCurrentId = id;
        mInputView.setKeyboard(keyboard);
        keyboard.setShifted(false);
        keyboard.setShiftLocked(keyboard.isShiftLocked());
        keyboard.setImeOptions(mInputMethodService.getResources(), mode, imeOptions);
        keyboard.setColorOfSymbolIcons(mIsAutoCompletionActive, isBlackSym());
        // Update the settings key state because number of enabled IMEs could have been changed
        updateSettingsKeyState(PreferenceManager.getDefaultSharedPreferences(mInputMethodService));
    }

    private LatinKeyboard getKeyboard(KeyboardId id) {
        SoftReference<LatinKeyboard> ref = mKeyboards.get(id);
        LatinKeyboard keyboard = (ref == null) ? null : ref.get();
        if (keyboard == null) {
            Resources orig = mInputMethodService.getResources();
            Configuration conf = orig.getConfiguration();
            Locale saveLocale = conf.locale;
            conf.locale = mInputLocale;
            orig.updateConfiguration(conf, null);
            keyboard = new LatinKeyboard(mInputMethodService, id.mXml, id.mKeyboardMode);
            keyboard.setVoiceMode(hasVoiceButton(id.mXml == R.xml.kbd_symbols
                    || id.mXml == R.xml.kbd_symbols_black), mHasVoice);
            keyboard.setLanguageSwitcher(mLanguageSwitcher, mIsAutoCompletionActive, isBlackSym());

            if (id.mEnableShiftLock) {
                keyboard.enableShiftLock();
            }
            mKeyboards.put(id, new SoftReference<LatinKeyboard>(keyboard));

            conf.locale = saveLocale;
            orig.updateConfiguration(conf, null);
        }
        return keyboard;
    }

    private KeyboardId getKeyboardId(int mode, int imeOptions, boolean isSymbols) {
        final boolean hasVoice = hasVoiceButton(isSymbols);
        final int charColorId = getCharColorId();
        final int keyboardRowsResId;
        final boolean enableShiftLock;
        final int keyboardMode;

        if (mode == MODE_NONE) {
            LatinImeLogger.logOnWarning(
                    "getKeyboardId:" + mode + "," + imeOptions + "," + isSymbols);
            mode = MODE_TEXT;
        }
        if (isSymbols) {
            keyboardRowsResId = mode == MODE_PHONE
                    ? KBD_PHONE_SYMBOLS[charColorId] : KBD_SYMBOLS[charColorId];
            enableShiftLock = false;
            keyboardMode = mHasSettingsKey
                    ? SYMBOLS_WITH_SETTINGS_KEY_MODES[mode] : SYMBOLS_MODES[mode];
        } else {  // QWERTY
            keyboardRowsResId = mode == MODE_PHONE
                    ? KBD_PHONE[charColorId] : KBD_QWERTY[charColorId];
            enableShiftLock = mode == MODE_PHONE ? false : true;
            keyboardMode = mHasSettingsKey
                    ? QWERTY_WITH_SETTINGS_KEY_MODES[mode] : QWERTY_MODES[mode];
        }
        return new KeyboardId(keyboardRowsResId, keyboardMode, enableShiftLock, hasVoice);
    }

    public int getKeyboardMode() {
        return mMode;
    }
    
    public boolean isAlphabetMode() {
        if (mCurrentId == null) {
            return false;
        }
        int currentMode = mCurrentId.mKeyboardMode;
        for (Integer mode : ALPHABET_MODES) {
            if (currentMode == mode) {
                return true;
            }
        }
        return false;
    }

    public void setShifted(boolean shifted) {
        if (mInputView != null) {
            mInputView.setShifted(shifted);
        }
    }

    public void setShiftLocked(boolean shiftLocked) {
        if (mInputView != null) {
            mInputView.setShiftLocked(shiftLocked);
        }
    }

    public void toggleShift() {
        if (isAlphabetMode())
            return;
        if (mCurrentId.equals(mSymbolsId) || !mCurrentId.equals(mSymbolsShiftedId)) {
            LatinKeyboard symbolsShiftedKeyboard = getKeyboard(mSymbolsShiftedId);
            mCurrentId = mSymbolsShiftedId;
            mInputView.setKeyboard(symbolsShiftedKeyboard);
            // Symbol shifted keyboard has an ALT key that has a caps lock style indicator. To
            // enable the indicator, we need to call enableShiftLock() and setShiftLocked(true).
            // Thus we can keep the ALT key's Key.on value true while LatinKey.onRelease() is
            // called.
            symbolsShiftedKeyboard.enableShiftLock();
            symbolsShiftedKeyboard.setShiftLocked(true);
            symbolsShiftedKeyboard.setImeOptions(mInputMethodService.getResources(),
                    mMode, mImeOptions);
        } else {
            LatinKeyboard symbolsKeyboard = getKeyboard(mSymbolsId);
            mCurrentId = mSymbolsId;
            mInputView.setKeyboard(symbolsKeyboard);
            // Symbol keyboard has an ALT key that has a caps lock style indicator. To disable the
            // indicator, we need to call enableShiftLock() and setShiftLocked(false).
            symbolsKeyboard.enableShiftLock();
            symbolsKeyboard.setShifted(false);
            symbolsKeyboard.setImeOptions(mInputMethodService.getResources(), mMode, mImeOptions);
        }
    }

    public void toggleSymbols() {
        setKeyboardMode(mMode, mImeOptions, mHasVoice, !mIsSymbols);
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
     * Returns true if the keyboard needs to switch back 
     */
    public boolean onKey(int key) {
        // Switch back to alpha mode if user types one or more non-space/enter characters
        // followed by a space/enter
        switch (mSymbolsModeState) {
            case SYMBOLS_MODE_STATE_BEGIN:
                if (key != LatinIME.KEYCODE_SPACE && key != LatinIME.KEYCODE_ENTER && key > 0) {
                    mSymbolsModeState = SYMBOLS_MODE_STATE_SYMBOL;
                }
                break;
            case SYMBOLS_MODE_STATE_SYMBOL:
                if (key == LatinIME.KEYCODE_ENTER || key == LatinIME.KEYCODE_SPACE) return true;
                break;
        }
        return false;
    }

    public LatinKeyboardView getInputView() {
        return mInputView;
    }

    public void recreateInputView() {
        changeLatinKeyboardView(mLayoutId, true);
    }

    private void changeLatinKeyboardView(int newLayout, boolean forceReset) {
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
            changeLatinKeyboardView(
                    Integer.valueOf(sharedPreferences.getString(key, DEFAULT_LAYOUT_ID)), false);
        } else if (LatinIMESettings.PREF_SETTINGS_KEY.equals(key)) {
            updateSettingsKeyState(sharedPreferences);
            recreateInputView();
        }
    }

    public boolean isBlackSym () {
        if (mInputView != null && mInputView.getSymbolColorScheme() == 1) {
            return true;
        }
        return false;
    }

    private int getCharColorId () {
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

    private void updateSettingsKeyState(SharedPreferences prefs) {
        Resources resources = mInputMethodService.getResources();
        final boolean showSettingsKeyOption = resources.getBoolean(
                R.bool.config_enable_show_settings_key_option);
        final int defaultSettingsKeyMode = showSettingsKeyOption
                ? SETTINGS_KEY_MODE_AUTO : SETTINGS_KEY_MODE_ALWAYS_HIDE;
        final String settingsKeyMode = prefs.getString(LatinIMESettings.PREF_SETTINGS_KEY,
                resources.getString(defaultSettingsKeyMode));
        // We show the settings key when 1) SETTINGS_KEY_MODE_ALWAYS_SHOW or
        // 2) SETTINGS_KEY_MODE_AUTO and there are two or more enabled IMEs on the system
        if (settingsKeyMode.equals(resources.getString(SETTINGS_KEY_MODE_ALWAYS_SHOW))
                || (settingsKeyMode.equals(resources.getString(SETTINGS_KEY_MODE_AUTO))
                        && LatinIMEUtil.hasMultipleEnabledIMEs(mInputMethodService))) {
            mHasSettingsKey = true;
        } else {
            mHasSettingsKey = false;
        }
    }
}
