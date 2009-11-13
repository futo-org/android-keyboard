/*
 * Copyright (C) 2008 Google Inc.
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
import java.util.Map;

public class KeyboardSwitcher {

    public static final int MODE_TEXT = 1;
    public static final int MODE_SYMBOLS = 2;
    public static final int MODE_PHONE = 3;
    public static final int MODE_URL = 4;
    public static final int MODE_EMAIL = 5;
    public static final int MODE_IM = 6;
    
    public static final int MODE_TEXT_QWERTY = 0;
    public static final int MODE_TEXT_ALPHA = 1;
    public static final int MODE_TEXT_COUNT = 2;
    
    public static final int KEYBOARDMODE_NORMAL = R.id.mode_normal;
    public static final int KEYBOARDMODE_URL = R.id.mode_url;
    public static final int KEYBOARDMODE_EMAIL = R.id.mode_email;
    public static final int KEYBOARDMODE_IM = R.id.mode_im;

    private static final int SYMBOLS_MODE_STATE_NONE = 0;
    private static final int SYMBOLS_MODE_STATE_BEGIN = 1;
    private static final int SYMBOLS_MODE_STATE_SYMBOL = 2;

    LatinKeyboardView mInputView;
    LatinIME mContext;
    
    private KeyboardId mSymbolsId;
    private KeyboardId mSymbolsShiftedId;

    private KeyboardId mCurrentId;
    private Map<KeyboardId, LatinKeyboard> mKeyboards;
    
    private int mMode;
    private int mImeOptions;
    private int mTextMode = MODE_TEXT_QWERTY;
    private boolean mIsSymbols;
    private boolean mPreferSymbols;
    private int mSymbolsModeState = SYMBOLS_MODE_STATE_NONE;

    private int mLastDisplayWidth;

    KeyboardSwitcher(LatinIME context) {
        mContext = context;
        mKeyboards = new HashMap<KeyboardId, LatinKeyboard>();
        mSymbolsId = new KeyboardId(R.xml.kbd_symbols);
        mSymbolsShiftedId = new KeyboardId(R.xml.kbd_symbols_shift);
    }

    void setInputView(LatinKeyboardView inputView) {
        mInputView = inputView;
    }
    
    void makeKeyboards(boolean forceCreate) {
        if (forceCreate) mKeyboards.clear();
        // Configuration change is coming after the keyboard gets recreated. So don't rely on that.
        // If keyboards have already been made, check if we have a screen width change and 
        // create the keyboard layouts again at the correct orientation
        int displayWidth = mContext.getMaxWidth();
        if (displayWidth == mLastDisplayWidth) return;
        mLastDisplayWidth = displayWidth;
        if (!forceCreate) mKeyboards.clear();
        mSymbolsId = new KeyboardId(R.xml.kbd_symbols);
        mSymbolsShiftedId = new KeyboardId(R.xml.kbd_symbols_shift);
    }

    /**
     * Represents the parameters necessary to construct a new LatinKeyboard,
     * which also serve as a unique identifier for each keyboard type.
     */
    private static class KeyboardId {
        public int mXml;
        public int mMode;
        public boolean mEnableShiftLock;

        public KeyboardId(int xml, int mode, boolean enableShiftLock) {
            this.mXml = xml;
            this.mMode = mode;
            this.mEnableShiftLock = enableShiftLock;
        }

        public KeyboardId(int xml) {
            this(xml, 0, false);
        }

        public boolean equals(Object other) {
            return other instanceof KeyboardId && equals((KeyboardId) other);
        }

        public boolean equals(KeyboardId other) {
            return other.mXml == this.mXml && other.mMode == this.mMode;
        }

        public int hashCode() {
            return (mXml + 1) * (mMode + 1) * (mEnableShiftLock ? 2 : 1);
        }
    }

    void setKeyboardMode(int mode, int imeOptions) {
        mSymbolsModeState = SYMBOLS_MODE_STATE_NONE;
        mPreferSymbols = mode == MODE_SYMBOLS;
        setKeyboardMode(mode == MODE_SYMBOLS ? MODE_TEXT : mode, imeOptions,
                mPreferSymbols);
    }

    void setKeyboardMode(int mode, int imeOptions, boolean isSymbols) {
        mMode = mode;
        mImeOptions = imeOptions;
        mIsSymbols = isSymbols;
        mInputView.setPreviewEnabled(true);
        KeyboardId id = getKeyboardId(mode, imeOptions, isSymbols);
        LatinKeyboard keyboard = getKeyboard(id);

        if (mode == MODE_PHONE) {
            mInputView.setPhoneKeyboard(keyboard);
            mInputView.setPreviewEnabled(false);
        }

        mCurrentId = id;
        mInputView.setKeyboard(keyboard);
        keyboard.setShifted(false);
        keyboard.setShiftLocked(keyboard.isShiftLocked());
        keyboard.setImeOptions(mContext.getResources(), mMode, imeOptions);

    }

    private LatinKeyboard getKeyboard(KeyboardId id) {
        if (!mKeyboards.containsKey(id)) {
            LatinKeyboard keyboard = new LatinKeyboard(
                mContext, id.mXml, id.mMode);
            if (id.mEnableShiftLock) {
                keyboard.enableShiftLock();
            }
            mKeyboards.put(id, keyboard);
        }
        return mKeyboards.get(id);
    }

    private KeyboardId getKeyboardId(int mode, int imeOptions, boolean isSymbols) {
        if (isSymbols) {
            return (mode == MODE_PHONE)
                ? new KeyboardId(R.xml.kbd_phone_symbols) : new KeyboardId(R.xml.kbd_symbols);
        }

        switch (mode) {
            case MODE_TEXT:
                if (mTextMode == MODE_TEXT_QWERTY) {
                    return new KeyboardId(R.xml.kbd_qwerty, KEYBOARDMODE_NORMAL, true);
                } else if (mTextMode == MODE_TEXT_ALPHA) {
                    return new KeyboardId(R.xml.kbd_alpha, KEYBOARDMODE_NORMAL, true);
                }
                break;
            case MODE_SYMBOLS:
                return new KeyboardId(R.xml.kbd_symbols);
            case MODE_PHONE:
                return new KeyboardId(R.xml.kbd_phone);
            case MODE_URL:
                return new KeyboardId(R.xml.kbd_qwerty, KEYBOARDMODE_URL, true);
            case MODE_EMAIL:
                return new KeyboardId(R.xml.kbd_qwerty, KEYBOARDMODE_EMAIL, true);
            case MODE_IM:
                return new KeyboardId(R.xml.kbd_qwerty, KEYBOARDMODE_IM, true);
        }
        return null;
    }

    int getKeyboardMode() {
        return mMode;
    }
    
    boolean isTextMode() {
        return mMode == MODE_TEXT;
    }
    
    int getTextMode() {
        return mTextMode;
    }
    
    void setTextMode(int position) {
        if (position < MODE_TEXT_COUNT && position >= 0) {
            mTextMode = position;
        }
        if (isTextMode()) {
            setKeyboardMode(MODE_TEXT, mImeOptions);
        }
    }

    int getTextModeCount() {
        return MODE_TEXT_COUNT;
    }

    boolean isAlphabetMode() {
        KeyboardId current = mCurrentId;
        return current.mMode == KEYBOARDMODE_NORMAL
            || current.mMode == KEYBOARDMODE_URL
            || current.mMode == KEYBOARDMODE_EMAIL
            || current.mMode == KEYBOARDMODE_IM;
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
            mInputView.setKeyboard(getKeyboard(mSymbolsId));
            symbolsKeyboard.setShifted(false);
            symbolsKeyboard.setImeOptions(mContext.getResources(), mMode, mImeOptions);
        }
    }

    void toggleSymbols() {
        setKeyboardMode(mMode, mImeOptions, !mIsSymbols);
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
}
