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
import com.android.inputmethod.latin.SettingsValues;
import com.android.inputmethod.latin.SubtypeSwitcher;
import com.android.inputmethod.latin.Utils;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Locale;

public class KeyboardSwitcher implements KeyboardState.SwitchActions,
        SharedPreferences.OnSharedPreferenceChangeListener {
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

    private KeyboardId mMainKeyboardId;
    private KeyboardId mSymbolsKeyboardId;
    private KeyboardId mSymbolsShiftedKeyboardId;

    private KeyboardId mCurrentId;
    private final HashMap<KeyboardId, SoftReference<LatinKeyboard>> mKeyboardCache =
            new HashMap<KeyboardId, SoftReference<LatinKeyboard>>();

    /** mIsAutoCorrectionActive indicates that auto corrected word will be input instead of
     * what user actually typed. */
    private boolean mIsAutoCorrectionActive;

    private int mThemeIndex = -1;
    private Context mThemeContext;

    private static final KeyboardSwitcher sInstance = new KeyboardSwitcher();

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
        mState = new KeyboardState(this);
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

    public void loadKeyboard(EditorInfo editorInfo, SettingsValues settingsValues) {
        try {
            mMainKeyboardId = getKeyboardId(editorInfo, false, false, settingsValues);
            mSymbolsKeyboardId = getKeyboardId(editorInfo, true, false, settingsValues);
            mSymbolsShiftedKeyboardId = getKeyboardId(editorInfo, true, true, settingsValues);
            mState.onLoadKeyboard(mResources.getString(R.string.layout_switch_back_symbols),
                    hasDistinctMultitouch());
        } catch (RuntimeException e) {
            Log.w(TAG, "loading keyboard failed: " + mMainKeyboardId, e);
            LatinImeLogger.logOnException(mMainKeyboardId.toString(), e);
        }
    }

    public void saveKeyboardState() {
        if (mCurrentId != null) {
            mState.onSaveKeyboardState(isAlphabetMode(), isSymbolShifted());
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
        updateShiftLockState(keyboard);
        mKeyboardView.setKeyPreviewPopupEnabled(
                SettingsValues.isKeyPreviewPopupEnabled(mPrefs, mResources),
                SettingsValues.getKeyPreviewPopupDismissDelay(mPrefs, mResources));
        final boolean localeChanged = (oldKeyboard == null)
                || !keyboard.mId.mLocale.equals(oldKeyboard.mId.mLocale);
        mInputMethodService.mHandler.startDisplayLanguageOnSpacebar(localeChanged);
        updateShiftState();
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
            final boolean isShift, SettingsValues settingsValues) {
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

    // TODO: Delegate to KeyboardState
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

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setShifted(int shiftMode) {
        mInputMethodService.mHandler.cancelUpdateShiftState();
        LatinKeyboard latinKeyboard = getLatinKeyboard();
        if (latinKeyboard == null)
            return;
        if (shiftMode == AUTOMATIC_SHIFT) {
            latinKeyboard.setAutomaticTemporaryUpperCase();
        } else {
            final boolean shifted = (shiftMode == MANUAL_SHIFT);
            // On non-distinct multi touch panel device, we should also turn off the shift locked
            // state when shift key is pressed to go to normal mode.
            // On the other hand, on distinct multi touch panel device, turning off the shift
            // locked state with shift key pressing is handled by onReleaseShift().
            if (!hasDistinctMultitouch() && !shifted && mState.isShiftLocked()) {
                latinKeyboard.setShiftLocked(false);
            }
            latinKeyboard.setShifted(shifted);
        }
        mKeyboardView.invalidateAllKeys();
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setShiftLocked(boolean shiftLocked) {
        mInputMethodService.mHandler.cancelUpdateShiftState();
        LatinKeyboard latinKeyboard = getLatinKeyboard();
        if (latinKeyboard == null)
            return;
        latinKeyboard.setShiftLocked(shiftLocked);
        mKeyboardView.invalidateAllKeys();
        if (!shiftLocked) {
            // To be able to turn off caps lock by "double tap" on shift key, we should ignore
            // the second tap of the "double tap" from now for a while because we just have
            // already turned off caps lock above.
            mKeyboardView.startIgnoringDoubleTap();
        }
    }

    /**
     * Toggle keyboard shift state triggered by user touch event.
     */
    public void toggleShift() {
        if (DEBUG_STATE) {
            Log.d(TAG, "toggleShift: " + mState);
        }
        mState.onToggleShift(isAlphabetMode(), isSymbolShifted());
    }

    /**
     * Toggle caps lock state triggered by user touch event.
     */
    public void toggleCapsLock() {
        if (DEBUG_STATE) {
            Log.d(TAG, "toggleCapsLock: " + mState);
        }
        mState.onToggleCapsLock(isAlphabetMode());
    }

    /**
     * Toggle between alphabet and symbols modes triggered by user touch event.
     */
    public void toggleAlphabetAndSymbols() {
        if (DEBUG_STATE) {
            Log.d(TAG, "toggleAlphabetAndSymbols: " + mState);
        }
        mState.onToggleAlphabetAndSymbols(isAlphabetMode());
    }

    /**
     * Update keyboard shift state triggered by connected EditText status change.
     */
    public void updateShiftState() {
        if (DEBUG_STATE) {
            Log.d(TAG, "updateShiftState: " + mState
                    + " autoCaps=" + mInputMethodService.getCurrentAutoCapsState());
        }
        mState.onUpdateShiftState(isAlphabetMode(), mInputMethodService.getCurrentAutoCapsState());
    }

    public void onPressShift(boolean withSliding) {
        if (!isKeyboardAvailable())
            return;
        if (DEBUG_STATE) {
            Log.d(TAG, "onPressShift: " + mState + " sliding=" + withSliding);
        }
        mState.onPressShift(isAlphabetMode(), isSymbolShifted());
    }

    public void onReleaseShift(boolean withSliding) {
        if (!isKeyboardAvailable())
            return;
        if (DEBUG_STATE) {
            Log.d(TAG, "onReleaseShift: " + mState + " sliding=" + withSliding);
        }
        mState.onReleaseShift(isAlphabetMode(), isSymbolShifted(), withSliding);
    }

    public void onPressSymbol() {
        if (DEBUG_STATE) {
            Log.d(TAG, "onPressSymbol: " + mState);
        }
        mState.onPressSymbol(isAlphabetMode());
    }

    public void onReleaseSymbol() {
        if (DEBUG_STATE) {
            Log.d(TAG, "onReleaseSymbol: " + mState);
        }
        mState.onReleaseSymbol(isAlphabetMode());
    }

    public void onOtherKeyPressed() {
        if (DEBUG_STATE) {
            Log.d(TAG, "onOtherKeyPressed: " + mState);
        }
        mState.onOtherKeyPressed();
    }

    public void onCancelInput() {
        mState.onCancelInput(isAlphabetMode(), isSymbolShifted(), isSinglePointer());
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setSymbolsKeyboard() {
        setKeyboard(getKeyboard(mSymbolsKeyboardId));
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setAlphabetKeyboard() {
        setKeyboard(getKeyboard(mMainKeyboardId));
    }

    // TODO: Remove this method
    private boolean isSymbolShifted() {
        return mCurrentId != null && mCurrentId.equals(mSymbolsShiftedKeyboardId);
    }

    // Implements {@link KeyboardState.SwitchActions}.
    @Override
    public void setSymbolsShiftedKeyboard() {
        setKeyboard(getKeyboard(mSymbolsShiftedKeyboardId));
    }

    public boolean isInMomentarySwitchState() {
        return mState.isInMomentarySwitchState();
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

    /**
     * Updates state machine to figure out when to automatically snap back to the previous mode.
     */
    public void onCodeInput(int code) {
        if (DEBUG_STATE) {
            Log.d(TAG, "onCodeInput: code=" + code + " isSinglePointer=" + isSinglePointer()
                    + " " + mState);
        }
        mState.onCodeInput(isAlphabetMode(), isSymbolShifted(), code, isSinglePointer());
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
