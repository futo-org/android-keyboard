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
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.common.Constants;
import com.android.inputmethod.latin.common.StringUtils;

import java.util.Locale;

final class KeyCodeDescriptionMapper {
    private static final String TAG = KeyCodeDescriptionMapper.class.getSimpleName();
    private static final String SPOKEN_LETTER_RESOURCE_NAME_FORMAT = "spoken_accented_letter_%04X";
    private static final String SPOKEN_SYMBOL_RESOURCE_NAME_FORMAT = "spoken_symbol_%04X";
    private static final String SPOKEN_EMOJI_RESOURCE_NAME_FORMAT = "spoken_emoji_%04X";
    private static final String SPOKEN_EMOTICON_RESOURCE_NAME_PREFIX = "spoken_emoticon";
    private static final String SPOKEN_EMOTICON_CODE_POINT_FORMAT = "_%02X";

    // The resource ID of the string spoken for obscured keys
    private static final int OBSCURED_KEY_RES_ID = R.string.spoken_description_dot;

    private static final KeyCodeDescriptionMapper sInstance = new KeyCodeDescriptionMapper();

    public static KeyCodeDescriptionMapper getInstance() {
        return sInstance;
    }

    // Sparse array of spoken description resource IDs indexed by key codes
    private final SparseIntArray mKeyCodeMap = new SparseIntArray();

    private KeyCodeDescriptionMapper() {
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
        mKeyCodeMap.put(Constants.CODE_EMOJI, R.string.spoken_description_emoji);
        // Because the upper-case and lower-case mappings of the following letters is depending on
        // the locale, the upper case descriptions should be defined here. The lower case
        // descriptions are handled in {@link #getSpokenLetterDescriptionId(Context,int)}.
        // U+0049: "I" LATIN CAPITAL LETTER I
        // U+0069: "i" LATIN SMALL LETTER I
        // U+0130: "İ" LATIN CAPITAL LETTER I WITH DOT ABOVE
        // U+0131: "ı" LATIN SMALL LETTER DOTLESS I
        mKeyCodeMap.put(0x0049, R.string.spoken_letter_0049);
        mKeyCodeMap.put(0x0130, R.string.spoken_letter_0130);
    }

    /**
     * Returns the localized description of the action performed by a specified
     * key based on the current keyboard state.
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

        if (code == Constants.CODE_OUTPUT_TEXT) {
            final String outputText = key.getOutputText();
            final String description = getSpokenEmoticonDescription(context, outputText);
            return TextUtils.isEmpty(description) ? outputText : description;
        }

        // Just attempt to speak the description.
        if (code != Constants.CODE_UNSPECIFIED) {
            // If the key description should be obscured, now is the time to do it.
            final boolean isDefinedNonCtrl = Character.isDefined(code)
                    && !Character.isISOControl(code);
            if (shouldObscure && isDefinedNonCtrl) {
                return context.getString(OBSCURED_KEY_RES_ID);
            }
            final String description = getDescriptionForCodePoint(context, code);
            if (description != null) {
                return description;
            }
            if (!TextUtils.isEmpty(key.getLabel())) {
                return key.getLabel();
            }
            return context.getString(R.string.spoken_description_unknown);
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
    private static String getDescriptionForSwitchAlphaSymbol(final Context context,
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
    private static String getDescriptionForShiftKey(final Context context,
            final Keyboard keyboard) {
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
            resId = R.string.spoken_description_shift_shifted;
            break;
        case KeyboardId.ELEMENT_SYMBOLS:
            resId = R.string.spoken_description_symbols_shift;
            break;
        case KeyboardId.ELEMENT_SYMBOLS_SHIFTED:
            resId = R.string.spoken_description_symbols_shift_shifted;
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
    private static String getDescriptionForActionKey(final Context context, final Keyboard keyboard,
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
     * the specified key is pressed based on its key code point.
     *
     * @param context The package's context.
     * @param codePoint The code point from which to obtain a description.
     * @return a character sequence describing the code point.
     */
    public String getDescriptionForCodePoint(final Context context, final int codePoint) {
        // If the key description should be obscured, now is the time to do it.
        final int index = mKeyCodeMap.indexOfKey(codePoint);
        if (index >= 0) {
            return context.getString(mKeyCodeMap.valueAt(index));
        }
        final String accentedLetter = getSpokenAccentedLetterDescription(context, codePoint);
        if (accentedLetter != null) {
            return accentedLetter;
        }
        // Here, <code>code</code> may be a base (non-accented) letter.
        final String unsupportedSymbol = getSpokenSymbolDescription(context, codePoint);
        if (unsupportedSymbol != null) {
            return unsupportedSymbol;
        }
        final String emojiDescription = getSpokenEmojiDescription(context, codePoint);
        if (emojiDescription != null) {
            return emojiDescription;
        }
        if (Character.isDefined(codePoint) && !Character.isISOControl(codePoint)) {
            return StringUtils.newSingleCodePointString(codePoint);
        }
        return null;
    }

    // TODO: Remove this method once TTS supports those accented letters' verbalization.
    private String getSpokenAccentedLetterDescription(final Context context, final int code) {
        final boolean isUpperCase = Character.isUpperCase(code);
        final int baseCode = isUpperCase ? Character.toLowerCase(code) : code;
        final int baseIndex = mKeyCodeMap.indexOfKey(baseCode);
        final int resId = (baseIndex >= 0) ? mKeyCodeMap.valueAt(baseIndex)
                : getSpokenDescriptionId(context, baseCode, SPOKEN_LETTER_RESOURCE_NAME_FORMAT);
        if (resId == 0) {
            return null;
        }
        final String spokenText = context.getString(resId);
        return isUpperCase ? context.getString(R.string.spoken_description_upper_case, spokenText)
                : spokenText;
    }

    // TODO: Remove this method once TTS supports those symbols' verbalization.
    private String getSpokenSymbolDescription(final Context context, final int code) {
        final int resId = getSpokenDescriptionId(context, code, SPOKEN_SYMBOL_RESOURCE_NAME_FORMAT);
        if (resId == 0) {
            return null;
        }
        final String spokenText = context.getString(resId);
        if (!TextUtils.isEmpty(spokenText)) {
            return spokenText;
        }
        // If a translated description is empty, fall back to unknown symbol description.
        return context.getString(R.string.spoken_symbol_unknown);
    }

    // TODO: Remove this method once TTS supports emoji verbalization.
    private String getSpokenEmojiDescription(final Context context, final int code) {
        final int resId = getSpokenDescriptionId(context, code, SPOKEN_EMOJI_RESOURCE_NAME_FORMAT);
        if (resId == 0) {
            return null;
        }
        final String spokenText = context.getString(resId);
        if (!TextUtils.isEmpty(spokenText)) {
            return spokenText;
        }
        // If a translated description is empty, fall back to unknown emoji description.
        return context.getString(R.string.spoken_emoji_unknown);
    }

    private int getSpokenDescriptionId(final Context context, final int code,
            final String resourceNameFormat) {
        final String resourceName = String.format(Locale.ROOT, resourceNameFormat, code);
        final Resources resources = context.getResources();
        // Note that the resource package name may differ from the context package name.
        final String resourcePackageName = resources.getResourcePackageName(
                R.string.spoken_description_unknown);
        final int resId = resources.getIdentifier(resourceName, "string", resourcePackageName);
        if (resId != 0) {
            mKeyCodeMap.append(code, resId);
        }
        return resId;
    }

    // TODO: Remove this method once TTS supports emoticon verbalization.
    private static String getSpokenEmoticonDescription(final Context context,
            final String outputText) {
        final StringBuilder sb = new StringBuilder(SPOKEN_EMOTICON_RESOURCE_NAME_PREFIX);
        final int textLength = outputText.length();
        for (int index = 0; index < textLength; index = outputText.offsetByCodePoints(index, 1)) {
            final int codePoint = outputText.codePointAt(index);
            sb.append(String.format(Locale.ROOT, SPOKEN_EMOTICON_CODE_POINT_FORMAT, codePoint));
        }
        final String resourceName = sb.toString();
        final Resources resources = context.getResources();
        // Note that the resource package name may differ from the context package name.
        final String resourcePackageName = resources.getResourcePackageName(
                R.string.spoken_description_unknown);
        final int resId = resources.getIdentifier(resourceName, "string", resourcePackageName);
        return (resId == 0) ? null : resources.getString(resId);
    }
}
