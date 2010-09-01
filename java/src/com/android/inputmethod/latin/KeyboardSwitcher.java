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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.view.InflateException;

public class KeyboardSwitcher implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final int MODE_NONE = 0;
    public static final int MODE_TEXT = 1;
    public static final int MODE_SYMBOLS = 2;
    public static final int MODE_PHONE = 3;
    public static final int MODE_URL = 4;
    public static final int MODE_EMAIL = 5;
    public static final int MODE_IM = 6;
    public static final int MODE_WEB = 7;

    public static final int KEYBOARDMODE_NORMAL = R.id.mode_normal;
    public static final int KEYBOARDMODE_URL = R.id.mode_url;
    public static final int KEYBOARDMODE_EMAIL = R.id.mode_email;
    public static final int KEYBOARDMODE_IM = R.id.mode_im;
    public static final int KEYBOARDMODE_WEB = R.id.mode_webentry;

    public static final String DEFAULT_LAYOUT_ID = "4";
    public static final String PREF_KEYBOARD_LAYOUT = "keyboard_layout";
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

    LatinKeyboardView mInputView;
    private static final int[] ALPHABET_MODES = {
        KEYBOARDMODE_NORMAL,
        KEYBOARDMODE_URL,
        KEYBOARDMODE_EMAIL,
        KEYBOARDMODE_IM,
        KEYBOARDMODE_WEB};

    Context mContext;
    LatinIME mInputMethodService;

    private KeyboardId mSymbolsId;
    private KeyboardId mSymbolsShiftedId;

    private KeyboardId mCurrentId;
    private Map<KeyboardId, LatinKeyboard> mKeyboards;

    private int mMode = MODE_NONE; /** One of the MODE_XXX values */
    private int mImeOptions;
    private boolean mIsSymbols;
    private boolean mHasVoice;
    private boolean mVoiceOnPrimary;
    private boolean mPreferSymbols;
    private int mSymbolsModeState = SYMBOLS_MODE_STATE_NONE;

    private int mLastDisplayWidth;
    private LanguageSwitcher mLanguageSwitcher;
    private Locale mInputLocale;

    private int mLayoutId;

    KeyboardSwitcher(Context context, LatinIME ims) {
        mContext = context;

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ims);
        mLayoutId = Integer.valueOf(prefs.getString(PREF_KEYBOARD_LAYOUT, DEFAULT_LAYOUT_ID));
        prefs.registerOnSharedPreferenceChangeListener(this);

        mKeyboards = new HashMap<KeyboardId, LatinKeyboard>();
        mSymbolsId = makeSymbolsId(false);
        mSymbolsShiftedId = makeSymbolsShiftedId(false);
        mInputMethodService = ims;
    }

    /**
     * Sets the input locale, when there are multiple locales for input.
     * If no locale switching is required, then the locale should be set to null.
     * @param locale the current input locale, or null for default locale with no locale 
     * button.
     */
    void setLanguageSwitcher(LanguageSwitcher languageSwitcher) {
        mLanguageSwitcher = languageSwitcher;
        mInputLocale = mLanguageSwitcher.getInputLocale();
    }

    void setInputView(LatinKeyboardView inputView) {
        mInputView = inputView;
    }

    private KeyboardId makeSymbolsId(boolean hasVoice) {
        return new KeyboardId(KBD_SYMBOLS[getCharColorId()], hasVoice);
    }

    private KeyboardId makeSymbolsShiftedId(boolean hasVoice) {
        return new KeyboardId(KBD_SYMBOLS_SHIFT[getCharColorId()], hasVoice);
    }

    void makeKeyboards(boolean forceCreate) {
        mSymbolsId = makeSymbolsId(mHasVoice && !mVoiceOnPrimary);
        mSymbolsShiftedId = makeSymbolsShiftedId(mHasVoice && !mVoiceOnPrimary);

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
        public int mXml;
        public int mKeyboardMode; /** A KEYBOARDMODE_XXX value */
        public boolean mEnableShiftLock;
        public boolean mHasVoice;

        public KeyboardId(int xml, int mode, boolean enableShiftLock, boolean hasVoice) {
            this.mXml = xml;
            this.mKeyboardMode = mode;
            this.mEnableShiftLock = enableShiftLock;
            this.mHasVoice = hasVoice;
        }

        public KeyboardId(int xml, boolean hasVoice) {
            this(xml, 0, false, hasVoice);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof KeyboardId && equals((KeyboardId) other);
        }

        public boolean equals(KeyboardId other) {
          return other.mXml == this.mXml
              && other.mKeyboardMode == this.mKeyboardMode
              && other.mEnableShiftLock == this.mEnableShiftLock;
        }

        @Override
        public int hashCode() {
            return (mXml + 1) * (mKeyboardMode + 1) * (mEnableShiftLock ? 2 : 1)
                    * (mHasVoice ? 4 : 8);
        }
    }

    void setVoiceMode(boolean enableVoice, boolean voiceOnPrimary) {
        if (enableVoice != mHasVoice || voiceOnPrimary != mVoiceOnPrimary) {
            mKeyboards.clear();
        }
        mHasVoice = enableVoice;
        mVoiceOnPrimary = voiceOnPrimary;
        setKeyboardMode(mMode, mImeOptions, mHasVoice,
                mIsSymbols);
    }

    boolean hasVoiceButton(boolean isSymbols) {
        return mHasVoice && (isSymbols != mVoiceOnPrimary);
    }

    void setKeyboardMode(int mode, int imeOptions, boolean enableVoice) {
        mSymbolsModeState = SYMBOLS_MODE_STATE_NONE;
        mPreferSymbols = mode == MODE_SYMBOLS;
        if (mode == MODE_SYMBOLS) {
            mode = MODE_TEXT;
        }
        try {
            setKeyboardMode(mode, imeOptions, enableVoice, mPreferSymbols);
        } catch (RuntimeException e) {
            LatinImeLogger.logOnException(mode + "," + imeOptions + "," + mPreferSymbols, e);
        }
    }

    void setKeyboardMode(int mode, int imeOptions, boolean enableVoice, boolean isSymbols) {
        if (mInputView == null) return;
        mMode = mode;
        mImeOptions = imeOptions;
        if (enableVoice != mHasVoice) {
            setVoiceMode(mHasVoice, mVoiceOnPrimary);
        }
        mIsSymbols = isSymbols;

        mInputView.setPreviewEnabled(mInputMethodService.getPopupOn());
        KeyboardId id = getKeyboardId(mode, imeOptions, isSymbols);
        LatinKeyboard keyboard = null;
        keyboard = getKeyboard(id);

        if (mode == MODE_PHONE) {
            mInputView.setPhoneKeyboard(keyboard);
            mInputView.setPreviewEnabled(false);
        }

        mCurrentId = id;
        mInputView.setKeyboard(keyboard);
        keyboard.setShifted(false);
        keyboard.setShiftLocked(keyboard.isShiftLocked());
        keyboard.setImeOptions(mContext.getResources(), mMode, imeOptions);
        keyboard.setBlackFlag(isBlackSym());
    }

    private LatinKeyboard getKeyboard(KeyboardId id) {
        if (!mKeyboards.containsKey(id)) {
            Resources orig = mContext.getResources();
            Configuration conf = orig.getConfiguration();
            Locale saveLocale = conf.locale;
            conf.locale = mInputLocale;
            orig.updateConfiguration(conf, null);
            LatinKeyboard keyboard = new LatinKeyboard(
                mContext, id.mXml, id.mKeyboardMode);
            keyboard.setVoiceMode(hasVoiceButton(id.mXml == R.xml.kbd_symbols
                    || id.mXml == R.xml.kbd_symbols_black), mHasVoice);
            keyboard.setLanguageSwitcher(mLanguageSwitcher);
            keyboard.setBlackFlag(isBlackSym());

            if (id.mEnableShiftLock) {
                keyboard.enableShiftLock();
            }
            mKeyboards.put(id, keyboard);

            conf.locale = saveLocale;
            orig.updateConfiguration(conf, null);
        }
        return mKeyboards.get(id);
    }

    private KeyboardId getKeyboardId(int mode, int imeOptions, boolean isSymbols) {
        boolean hasVoice = hasVoiceButton(isSymbols);
        int charColorId = getCharColorId();
        // TODO: generalize for any KeyboardId
        int keyboardRowsResId = KBD_QWERTY[charColorId];
        if (isSymbols) {
            if (mode == MODE_PHONE) {
                return new KeyboardId(KBD_PHONE_SYMBOLS[charColorId], hasVoice);
            } else {
                return new KeyboardId(KBD_SYMBOLS[charColorId], hasVoice);
            }
        }
        switch (mode) {
            case MODE_NONE:
                LatinImeLogger.logOnWarning(
                        "getKeyboardId:" + mode + "," + imeOptions + "," + isSymbols);
                /* fall through */
            case MODE_TEXT:
                return new KeyboardId(keyboardRowsResId, KEYBOARDMODE_NORMAL, true, hasVoice);
            case MODE_SYMBOLS:
                return new KeyboardId(KBD_SYMBOLS[charColorId], hasVoice);
            case MODE_PHONE:
                return new KeyboardId(KBD_PHONE[charColorId], hasVoice);
            case MODE_URL:
                return new KeyboardId(keyboardRowsResId, KEYBOARDMODE_URL, true, hasVoice);
            case MODE_EMAIL:
                return new KeyboardId(keyboardRowsResId, KEYBOARDMODE_EMAIL, true, hasVoice);
            case MODE_IM:
                return new KeyboardId(keyboardRowsResId, KEYBOARDMODE_IM, true, hasVoice);
            case MODE_WEB:
                return new KeyboardId(keyboardRowsResId, KEYBOARDMODE_WEB, true, hasVoice);
        }
        return null;
    }

    int getKeyboardMode() {
        return mMode;
    }
    
    boolean isTextMode() {
        return mMode == MODE_TEXT;
    }

    boolean isAlphabetMode() {
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

    void setShifted(boolean shifted) {
        if (mInputView != null) {
            mInputView.setShifted(shifted);
        }
    }

    void setShiftLocked(boolean shiftLocked) {
        if (mInputView != null) {
            mInputView.setShiftLocked(shiftLocked);
        }
    }

    void toggleShift() {
        if (mCurrentId.equals(mSymbolsId)) {
            LatinKeyboard symbolsKeyboard = getKeyboard(mSymbolsId);
            LatinKeyboard symbolsShiftedKeyboard = getKeyboard(mSymbolsShiftedId);
            symbolsKeyboard.setShifted(true);
            mCurrentId = mSymbolsShiftedId;
            mInputView.setKeyboard(symbolsShiftedKeyboard);
            symbolsShiftedKeyboard.setShifted(true);
            symbolsShiftedKeyboard.setImeOptions(mContext.getResources(), mMode, mImeOptions);
        } else if (mCurrentId.equals(mSymbolsShiftedId)) {
            LatinKeyboard symbolsKeyboard = getKeyboard(mSymbolsId);
            LatinKeyboard symbolsShiftedKeyboard = getKeyboard(mSymbolsShiftedId);
            symbolsShiftedKeyboard.setShifted(false);
            mCurrentId = mSymbolsId;
            mInputView.setKeyboard(symbolsKeyboard);
            symbolsKeyboard.setShifted(false);
            symbolsKeyboard.setImeOptions(mContext.getResources(), mMode, mImeOptions);
        }
    }

    void toggleSymbols() {
        setKeyboardMode(mMode, mImeOptions, mHasVoice, !mIsSymbols);
        if (mIsSymbols && !mPreferSymbols) {
            mSymbolsModeState = SYMBOLS_MODE_STATE_BEGIN;
        } else {
            mSymbolsModeState = SYMBOLS_MODE_STATE_NONE;
        }
    }

    /**
     * Updates state machine to figure out when to automatically switch back to alpha mode.
     * Returns true if the keyboard needs to switch back 
     */
    boolean onKey(int key) {
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
        }
    }

    public boolean isBlackSym () {
        if (mInputView != null && mInputView.getSymbolColorSheme() == 1) {
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

}
