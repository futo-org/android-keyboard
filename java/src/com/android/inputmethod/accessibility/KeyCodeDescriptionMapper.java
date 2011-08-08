/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.inputmethod.accessibility;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.latin.R;

import java.util.HashMap;

public class KeyCodeDescriptionMapper {
    // The resource ID of the string spoken for obscured keys
    private static final int OBSCURED_KEY_RES_ID = R.string.spoken_description_dot;

    private static KeyCodeDescriptionMapper sInstance = new KeyCodeDescriptionMapper();

    // Map of key labels to spoken description resource IDs
    private final HashMap<CharSequence, Integer> mKeyLabelMap;

    // Map of key codes to spoken description resource IDs
    private final HashMap<Integer, Integer> mKeyCodeMap;

    // Map of shifted key codes to spoken description resource IDs
    private final HashMap<Integer, Integer> mShiftedKeyCodeMap;

    // Map of shift-locked key codes to spoken description resource IDs
    private final HashMap<Integer, Integer> mShiftLockedKeyCodeMap;

    public static void init(Context context, SharedPreferences prefs) {
        sInstance.initInternal(context, prefs);
    }

    public static KeyCodeDescriptionMapper getInstance() {
        return sInstance;
    }

    private KeyCodeDescriptionMapper() {
        mKeyLabelMap = new HashMap<CharSequence, Integer>();
        mKeyCodeMap = new HashMap<Integer, Integer>();
        mShiftedKeyCodeMap = new HashMap<Integer, Integer>();
        mShiftLockedKeyCodeMap = new HashMap<Integer, Integer>();
    }

    private void initInternal(Context context, SharedPreferences prefs) {
        // Manual label substitutions for key labels with no string resource
        mKeyLabelMap.put(":-)", R.string.spoken_description_smiley);

        // Symbols that most TTS engines can't speak
        mKeyCodeMap.put((int) '.', R.string.spoken_description_period);
        mKeyCodeMap.put((int) ',', R.string.spoken_description_comma);
        mKeyCodeMap.put((int) '(', R.string.spoken_description_left_parenthesis);
        mKeyCodeMap.put((int) ')', R.string.spoken_description_right_parenthesis);
        mKeyCodeMap.put((int) ':', R.string.spoken_description_colon);
        mKeyCodeMap.put((int) ';', R.string.spoken_description_semicolon);
        mKeyCodeMap.put((int) '!', R.string.spoken_description_exclamation_mark);
        mKeyCodeMap.put((int) '?', R.string.spoken_description_question_mark);
        mKeyCodeMap.put((int) '\"', R.string.spoken_description_double_quote);
        mKeyCodeMap.put((int) '\'', R.string.spoken_description_single_quote);
        mKeyCodeMap.put((int) '*', R.string.spoken_description_star);
        mKeyCodeMap.put((int) '#', R.string.spoken_description_pound);
        mKeyCodeMap.put((int) ' ', R.string.spoken_description_space);

        // Non-ASCII symbols (must use escape codes!)
        mKeyCodeMap.put((int) '\u2022', R.string.spoken_description_dot);
        mKeyCodeMap.put((int) '\u221A', R.string.spoken_description_square_root);
        mKeyCodeMap.put((int) '\u03C0', R.string.spoken_description_pi);
        mKeyCodeMap.put((int) '\u0394', R.string.spoken_description_delta);
        mKeyCodeMap.put((int) '\u2122', R.string.spoken_description_trademark);
        mKeyCodeMap.put((int) '\u2105', R.string.spoken_description_care_of);
        mKeyCodeMap.put((int) '\u2026', R.string.spoken_description_ellipsis);
        mKeyCodeMap.put((int) '\u201E', R.string.spoken_description_low_double_quote);
        mKeyCodeMap.put((int) '\uFF0A', R.string.spoken_description_star);

        // Special non-character codes defined in Keyboard
        mKeyCodeMap.put(Keyboard.CODE_DELETE, R.string.spoken_description_delete);
        mKeyCodeMap.put(Keyboard.CODE_ENTER, R.string.spoken_description_return);
        mKeyCodeMap.put(Keyboard.CODE_SETTINGS, R.string.spoken_description_settings);
        mKeyCodeMap.put(Keyboard.CODE_SHIFT, R.string.spoken_description_shift);
        mKeyCodeMap.put(Keyboard.CODE_SHORTCUT, R.string.spoken_description_mic);
        mKeyCodeMap.put(Keyboard.CODE_SWITCH_ALPHA_SYMBOL, R.string.spoken_description_to_symbol);
        mKeyCodeMap.put(Keyboard.CODE_TAB, R.string.spoken_description_tab);

        // Shifted versions of non-character codes defined in Keyboard
        mShiftedKeyCodeMap.put(Keyboard.CODE_SHIFT, R.string.spoken_description_shift_shifted);

        // Shift-locked versions of non-character codes defined in Keyboard
        mShiftLockedKeyCodeMap.put(Keyboard.CODE_SHIFT, R.string.spoken_description_caps_lock);
    }

    /**
     * Returns the localized description of the action performed by a specified
     * key based on the current keyboard state.
     * <p>
     * The order of precedence for key descriptions is:
     * <ol>
     * <li>Manually-defined based on the key label</li>
     * <li>Automatic or manually-defined based on the key code</li>
     * <li>Automatically based on the key label</li>
     * <li>{code null} for keys with no label or key code defined</li>
     * </p>
     *
     * @param context The package's context.
     * @param keyboard The keyboard on which the key resides.
     * @param key The key from which to obtain a description.
     * @param shouldObscure {@true} if text (e.g. non-control) characters should be obscured.
     * @return a character sequence describing the action performed by pressing
     *         the key
     */
    public CharSequence getDescriptionForKey(Context context, Keyboard keyboard, Key key,
            boolean shouldObscure) {
        if (key.mCode == Keyboard.CODE_SWITCH_ALPHA_SYMBOL) {
            final CharSequence description = getDescriptionForSwitchAlphaSymbol(context, keyboard);
            if (description != null)
                return description;
        }

        if (!TextUtils.isEmpty(key.mLabel)) {
            final String label = key.mLabel.toString().trim();

            if (mKeyLabelMap.containsKey(label)) {
                return context.getString(mKeyLabelMap.get(label));
            } else if (label.length() == 1
                    || (keyboard.isManualTemporaryUpperCase() && !TextUtils
                            .isEmpty(key.mHintLabel))) {
                return getDescriptionForKeyCode(context, keyboard, key, shouldObscure);
            } else {
                return label;
            }
        } else if (key.mCode != Keyboard.CODE_DUMMY) {
            return getDescriptionForKeyCode(context, keyboard, key, shouldObscure);
        }

        return null;
    }

    /**
     * Returns a context-specific description for the CODE_SWITCH_ALPHA_SYMBOL
     * key or {@code null} if there is not a description provided for the
     * current keyboard context.
     *
     * @param context The package's context.
     * @param keyboard The keyboard on which the key resides.
     * @return a character sequence describing the action performed by pressing
     *         the key
     */
    private CharSequence getDescriptionForSwitchAlphaSymbol(Context context, Keyboard keyboard) {
        final KeyboardId id = keyboard.mId;

        if (id.isAlphabetKeyboard()) {
            return context.getString(R.string.spoken_description_to_symbol);
        } else if (id.isSymbolsKeyboard()) {
            return context.getString(R.string.spoken_description_to_alpha);
        } else if (id.isPhoneShiftKeyboard()) {
            return context.getString(R.string.spoken_description_to_numeric);
        } else if (id.isPhoneKeyboard()) {
            return context.getString(R.string.spoken_description_to_symbol);
        } else {
            return null;
        }
    }

    /**
     * Returns the keycode for the specified key given the current keyboard
     * state.
     *
     * @param keyboard The keyboard on which the key resides.
     * @param key The key from which to obtain a key code.
     * @return the key code for the specified key
     */
    private int getCorrectKeyCode(Keyboard keyboard, Key key) {
        if (keyboard.isManualTemporaryUpperCase() && !TextUtils.isEmpty(key.mHintLabel)) {
            return key.mHintLabel.charAt(0);
        } else {
            return key.mCode;
        }
    }

    /**
     * Returns a localized character sequence describing what will happen when
     * the specified key is pressed based on its key code.
     * <p>
     * The order of precedence for key code descriptions is:
     * <ol>
     * <li>Manually-defined shift-locked description</li>
     * <li>Manually-defined shifted description</li>
     * <li>Manually-defined normal description</li>
     * <li>Automatic based on the character represented by the key code</li>
     * <li>Fall-back for undefined or control characters</li>
     * </ol>
     * </p>
     *
     * @param context The package's context.
     * @param keyboard The keyboard on which the key resides.
     * @param key The key from which to obtain a description.
     * @param shouldObscure {@true} if text (e.g. non-control) characters should be obscured.
     * @return a character sequence describing the action performed by pressing
     *         the key
     */
    private CharSequence getDescriptionForKeyCode(Context context, Keyboard keyboard, Key key,
            boolean shouldObscure) {
        final int code = getCorrectKeyCode(keyboard, key);

        if (keyboard.isShiftLocked() && mShiftLockedKeyCodeMap.containsKey(code)) {
            return context.getString(mShiftLockedKeyCodeMap.get(code));
        } else if (keyboard.isShiftedOrShiftLocked() && mShiftedKeyCodeMap.containsKey(code)) {
            return context.getString(mShiftedKeyCodeMap.get(code));
        }

        // If the key description should be obscured, now is the time to do it.
        final boolean isDefinedNonCtrl = Character.isDefined(code) && !Character.isISOControl(code);
        if (shouldObscure && isDefinedNonCtrl) {
            return context.getString(OBSCURED_KEY_RES_ID);
        }

        if (mKeyCodeMap.containsKey(code)) {
            return context.getString(mKeyCodeMap.get(code));
        } else if (isDefinedNonCtrl) {
            return Character.toString((char) code);
        } else {
            return context.getString(R.string.spoken_description_unknown, code);
        }
    }
}
