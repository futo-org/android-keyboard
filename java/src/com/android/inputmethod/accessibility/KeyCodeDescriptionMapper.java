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
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.utils.CollectionUtils;

import java.util.HashMap;

public final class KeyCodeDescriptionMapper {
    private static final String TAG = KeyCodeDescriptionMapper.class.getSimpleName();

    // The resource ID of the string spoken for obscured keys
    private static final int OBSCURED_KEY_RES_ID = R.string.spoken_description_dot;

    private static KeyCodeDescriptionMapper sInstance = new KeyCodeDescriptionMapper();

    // Map of key labels to spoken description resource IDs
    private final HashMap<CharSequence, Integer> mKeyLabelMap = CollectionUtils.newHashMap();

    // Sparse array of spoken description resource IDs indexed by key codes
    private final SparseIntArray mKeyCodeMap;

    public static void init() {
        sInstance.initInternal();
    }

    public static KeyCodeDescriptionMapper getInstance() {
        return sInstance;
    }

    private KeyCodeDescriptionMapper() {
        mKeyCodeMap = new SparseIntArray();
    }

    private void initInternal() {
        // Manual label substitutions for key labels with no string resource
        mKeyLabelMap.put(":-)", R.string.spoken_description_smiley);

        // Special non-character codes defined in Keyboard
        mKeyCodeMap.put(Constants.CODE_SPACE, R.string.spoken_description_space);
        mKeyCodeMap.put(Constants.CODE_DELETE, R.string.spoken_description_delete);
        mKeyCodeMap.put(Constants.CODE_ENTER, R.string.spoken_description_return);
        mKeyCodeMap.put(Constants.CODE_SETTINGS, R.string.spoken_description_settings);
        mKeyCodeMap.put(Constants.CODE_SHIFT, R.string.spoken_description_shift);
        mKeyCodeMap.put(Constants.CODE_SHORTCUT, R.string.spoken_description_mic);
        mKeyCodeMap.put(Constants.CODE_SWITCH_ALPHA_SYMBOL, R.string.spoken_description_to_symbol);
        mKeyCodeMap.put(Constants.CODE_TAB, R.string.spoken_description_tab);
        mKeyCodeMap.put(Constants.CODE_LANGUAGE_SWITCH,
                R.string.spoken_description_language_switch);
        mKeyCodeMap.put(Constants.CODE_ACTION_NEXT, R.string.spoken_description_action_next);
        mKeyCodeMap.put(Constants.CODE_ACTION_PREVIOUS,
                R.string.spoken_description_action_previous);
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
     * @return a character sequence describing the action performed by pressing the key
     */
    public String getDescriptionForKey(final Context context, final Keyboard keyboard,
            final Key key, final boolean shouldObscure) {
        final int code = key.getCode();

        if (code == Constants.CODE_SWITCH_ALPHA_SYMBOL) {
            final String description = getDescriptionForSwitchAlphaSymbol(context, keyboard);
            if (description != null) {
                return description;
            }
        }

        if (code == Constants.CODE_SHIFT) {
            return getDescriptionForShiftKey(context, keyboard);
        }

        if (code == Constants.CODE_ENTER) {
            // The following function returns the correct description in all action and
            // regular enter cases, taking care of all modes.
            return getDescriptionForActionKey(context, keyboard, key);
        }

        if (!TextUtils.isEmpty(key.getLabel())) {
            final String label = key.getLabel().trim();

            // First, attempt to map the label to a pre-defined description.
            if (mKeyLabelMap.containsKey(label)) {
                return context.getString(mKeyLabelMap.get(label));
            }
        }

        // Just attempt to speak the description.
        if (key.getCode() != Constants.CODE_UNSPECIFIED) {
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
     * @return a character sequence describing the action performed by pressing the key
     */
    private String getDescriptionForSwitchAlphaSymbol(final Context context,
            final Keyboard keyboard) {
        final KeyboardId keyboardId = keyboard.mId;
        final int elementId = keyboardId.mElementId;
        final int resId;

        switch (elementId) {
        case KeyboardId.ELEMENT_ALPHABET:
        case KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED:
        case KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED:
        case KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED:
        case KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED:
            resId = R.string.spoken_description_to_symbol;
            break;
        case KeyboardId.ELEMENT_SYMBOLS:
        case KeyboardId.ELEMENT_SYMBOLS_SHIFTED:
            resId = R.string.spoken_description_to_alpha;
            break;
        case KeyboardId.ELEMENT_PHONE:
            resId = R.string.spoken_description_to_symbol;
            break;
        case KeyboardId.ELEMENT_PHONE_SYMBOLS:
            resId = R.string.spoken_description_to_numeric;
            break;
        default:
            Log.e(TAG, "Missing description for keyboard element ID:" + elementId);
            return null;
        }
        return context.getString(resId);
    }

    /**
     * Returns a context-sensitive description of the "Shift" key.
     *
     * @param context The package's context.
     * @param keyboard The keyboard on which the key resides.
     * @return A context-sensitive description of the "Shift" key.
     */
    private String getDescriptionForShiftKey(final Context context, final Keyboard keyboard) {
        final KeyboardId keyboardId = keyboard.mId;
        final int elementId = keyboardId.mElementId;
        final int resId;

        switch (elementId) {
        case KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED:
        case KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED:
            resId = R.string.spoken_description_caps_lock;
            break;
        case KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED:
        case KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED:
        case KeyboardId.ELEMENT_SYMBOLS_SHIFTED:
            resId = R.string.spoken_description_shift_shifted;
            break;
        default:
            resId = R.string.spoken_description_shift;
        }
        return context.getString(resId);
    }

    /**
     * Returns a context-sensitive description of the "Enter" action key.
     *
     * @param context The package's context.
     * @param keyboard The keyboard on which the key resides.
     * @param key The key to describe.
     * @return Returns a context-sensitive description of the "Enter" action key.
     */
    private String getDescriptionForActionKey(final Context context, final Keyboard keyboard,
            final Key key) {
        final KeyboardId keyboardId = keyboard.mId;
        final int actionId = keyboardId.imeAction();
        final int resId;

        // Always use the label, if available.
        if (!TextUtils.isEmpty(key.getLabel())) {
            return key.getLabel().trim();
        }

        // Otherwise, use the action ID.
        switch (actionId) {
        case EditorInfo.IME_ACTION_SEARCH:
            resId = R.string.spoken_description_search;
            break;
        case EditorInfo.IME_ACTION_GO:
            resId = R.string.label_go_key;
            break;
        case EditorInfo.IME_ACTION_SEND:
            resId = R.string.label_send_key;
            break;
        case EditorInfo.IME_ACTION_NEXT:
            resId = R.string.label_next_key;
            break;
        case EditorInfo.IME_ACTION_DONE:
            resId = R.string.label_done_key;
            break;
        case EditorInfo.IME_ACTION_PREVIOUS:
            resId = R.string.label_previous_key;
            break;
        default:
            resId = R.string.spoken_description_return;
        }
        return context.getString(resId);
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
     * @return a character sequence describing the action performed by pressing the key
     */
    private String getDescriptionForKeyCode(final Context context, final Keyboard keyboard,
            final Key key, final boolean shouldObscure) {
        final int code = key.getCode();

        // If the key description should be obscured, now is the time to do it.
        final boolean isDefinedNonCtrl = Character.isDefined(code) && !Character.isISOControl(code);
        if (shouldObscure && isDefinedNonCtrl) {
            return context.getString(OBSCURED_KEY_RES_ID);
        }
        if (mKeyCodeMap.indexOfKey(code) >= 0) {
            return context.getString(mKeyCodeMap.get(code));
        }
        if (isDefinedNonCtrl) {
            return Character.toString((char) code);
        }
        if (!TextUtils.isEmpty(key.getLabel())) {
            return key.getLabel();
        }
        return context.getString(R.string.spoken_description_unknown, code);
    }
}
