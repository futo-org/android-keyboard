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

package com.android.inputmethod.latin;

import android.text.InputType;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.latin.utils.InputTypeUtils;
import com.android.inputmethod.latin.utils.StringUtils;

/**
 * Class to hold attributes of the input field.
 */
public final class InputAttributes {
    private final String TAG = InputAttributes.class.getSimpleName();

    final public boolean mInputTypeNoAutoCorrect;
    final public boolean mIsSettingsSuggestionStripOn;
    final public boolean mApplicationSpecifiedCompletionOn;
    final public boolean mShouldInsertSpacesAutomatically;
    final private int mInputType;

    public InputAttributes(final EditorInfo editorInfo, final boolean isFullscreenMode) {
        final int inputType = null != editorInfo ? editorInfo.inputType : 0;
        final int inputClass = inputType & InputType.TYPE_MASK_CLASS;
        mInputType = inputType;
        if (inputClass != InputType.TYPE_CLASS_TEXT) {
            // If we are not looking at a TYPE_CLASS_TEXT field, the following strange
            // cases may arise, so we do a couple sanity checks for them. If it's a
            // TYPE_CLASS_TEXT field, these special cases cannot happen, by construction
            // of the flags.
            if (null == editorInfo) {
                Log.w(TAG, "No editor info for this field. Bug?");
            } else if (InputType.TYPE_NULL == inputType) {
                // TODO: We should honor TYPE_NULL specification.
                Log.i(TAG, "InputType.TYPE_NULL is specified");
            } else if (inputClass == 0) {
                // TODO: is this check still necessary?
                Log.w(TAG, String.format("Unexpected input class: inputType=0x%08x"
                        + " imeOptions=0x%08x",
                        inputType, editorInfo.imeOptions));
            }
            mIsSettingsSuggestionStripOn = false;
            mInputTypeNoAutoCorrect = false;
            mApplicationSpecifiedCompletionOn = false;
            mShouldInsertSpacesAutomatically = false;
        } else {
            final int variation = inputType & InputType.TYPE_MASK_VARIATION;
            final boolean flagNoSuggestions =
                    0 != (inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            final boolean flagMultiLine =
                    0 != (inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            final boolean flagAutoCorrect =
                    0 != (inputType & InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
            final boolean flagAutoComplete =
                    0 != (inputType & InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);

            // TODO: Have a helper method in InputTypeUtils
            // Make sure that passwords are not displayed in {@link SuggestionStripView}.
            if (InputTypeUtils.isPasswordInputType(inputType)
                    || InputTypeUtils.isVisiblePasswordInputType(inputType)
                    || InputTypeUtils.isEmailVariation(variation)
                    || InputType.TYPE_TEXT_VARIATION_URI == variation
                    || InputType.TYPE_TEXT_VARIATION_FILTER == variation
                    || flagNoSuggestions
                    || flagAutoComplete) {
                mIsSettingsSuggestionStripOn = false;
            } else {
                mIsSettingsSuggestionStripOn = true;
            }

            mShouldInsertSpacesAutomatically = InputTypeUtils.isAutoSpaceFriendlyType(inputType);

            // If it's a browser edit field and auto correct is not ON explicitly, then
            // disable auto correction, but keep suggestions on.
            // If NO_SUGGESTIONS is set, don't do prediction.
            // If it's not multiline and the autoCorrect flag is not set, then don't correct
            if ((variation == InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT
                    && !flagAutoCorrect)
                    || flagNoSuggestions
                    || (!flagAutoCorrect && !flagMultiLine)) {
                mInputTypeNoAutoCorrect = true;
            } else {
                mInputTypeNoAutoCorrect = false;
            }

            mApplicationSpecifiedCompletionOn = flagAutoComplete && isFullscreenMode;
        }
    }

    public boolean isTypeNull() {
        return InputType.TYPE_NULL == mInputType;
    }

    public boolean isSameInputType(final EditorInfo editorInfo) {
        return editorInfo.inputType == mInputType;
    }

    @SuppressWarnings("unused")
    private void dumpFlags(final int inputType) {
        Log.i(TAG, "Input class:");
        final int inputClass = inputType & InputType.TYPE_MASK_CLASS;
        if (inputClass == InputType.TYPE_CLASS_TEXT)
            Log.i(TAG, "  TYPE_CLASS_TEXT");
        if (inputClass == InputType.TYPE_CLASS_PHONE)
            Log.i(TAG, "  TYPE_CLASS_PHONE");
        if (inputClass == InputType.TYPE_CLASS_NUMBER)
            Log.i(TAG, "  TYPE_CLASS_NUMBER");
        if (inputClass == InputType.TYPE_CLASS_DATETIME)
            Log.i(TAG, "  TYPE_CLASS_DATETIME");
        Log.i(TAG, "Variation:");
        switch (InputType.TYPE_MASK_VARIATION & inputType) {
            case InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS:
                Log.i(TAG, "  TYPE_TEXT_VARIATION_EMAIL_ADDRESS");
                break;
            case InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT:
                Log.i(TAG, "  TYPE_TEXT_VARIATION_EMAIL_SUBJECT");
                break;
            case InputType.TYPE_TEXT_VARIATION_FILTER:
                Log.i(TAG, "  TYPE_TEXT_VARIATION_FILTER");
                break;
            case InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE:
                Log.i(TAG, "  TYPE_TEXT_VARIATION_LONG_MESSAGE");
                break;
            case InputType.TYPE_TEXT_VARIATION_NORMAL:
                Log.i(TAG, "  TYPE_TEXT_VARIATION_NORMAL");
                break;
            case InputType.TYPE_TEXT_VARIATION_PASSWORD:
                Log.i(TAG, "  TYPE_TEXT_VARIATION_PASSWORD");
                break;
            case InputType.TYPE_TEXT_VARIATION_PERSON_NAME:
                Log.i(TAG, "  TYPE_TEXT_VARIATION_PERSON_NAME");
                break;
            case InputType.TYPE_TEXT_VARIATION_PHONETIC:
                Log.i(TAG, "  TYPE_TEXT_VARIATION_PHONETIC");
                break;
            case InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS:
                Log.i(TAG, "  TYPE_TEXT_VARIATION_POSTAL_ADDRESS");
                break;
            case InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE:
                Log.i(TAG, "  TYPE_TEXT_VARIATION_SHORT_MESSAGE");
                break;
            case InputType.TYPE_TEXT_VARIATION_URI:
                Log.i(TAG, "  TYPE_TEXT_VARIATION_URI");
                break;
            case InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:
                Log.i(TAG, "  TYPE_TEXT_VARIATION_VISIBLE_PASSWORD");
                break;
            case InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT:
                Log.i(TAG, "  TYPE_TEXT_VARIATION_WEB_EDIT_TEXT");
                break;
            case InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS:
                Log.i(TAG, "  TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS");
                break;
            case InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD:
                Log.i(TAG, "  TYPE_TEXT_VARIATION_WEB_PASSWORD");
                break;
            default:
                Log.i(TAG, "  Unknown variation");
                break;
        }
        Log.i(TAG, "Flags:");
        if (0 != (inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS))
            Log.i(TAG, "  TYPE_TEXT_FLAG_NO_SUGGESTIONS");
        if (0 != (inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE))
            Log.i(TAG, "  TYPE_TEXT_FLAG_MULTI_LINE");
        if (0 != (inputType & InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE))
            Log.i(TAG, "  TYPE_TEXT_FLAG_IME_MULTI_LINE");
        if (0 != (inputType & InputType.TYPE_TEXT_FLAG_CAP_WORDS))
            Log.i(TAG, "  TYPE_TEXT_FLAG_CAP_WORDS");
        if (0 != (inputType & InputType.TYPE_TEXT_FLAG_CAP_SENTENCES))
            Log.i(TAG, "  TYPE_TEXT_FLAG_CAP_SENTENCES");
        if (0 != (inputType & InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS))
            Log.i(TAG, "  TYPE_TEXT_FLAG_CAP_CHARACTERS");
        if (0 != (inputType & InputType.TYPE_TEXT_FLAG_AUTO_CORRECT))
            Log.i(TAG, "  TYPE_TEXT_FLAG_AUTO_CORRECT");
        if (0 != (inputType & InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE))
            Log.i(TAG, "  TYPE_TEXT_FLAG_AUTO_COMPLETE");
    }

    // Pretty print
    @Override
    public String toString() {
        return "\n mInputTypeNoAutoCorrect = " + mInputTypeNoAutoCorrect
                + "\n mIsSettingsSuggestionStripOn = " + mIsSettingsSuggestionStripOn
                + "\n mApplicationSpecifiedCompletionOn = " + mApplicationSpecifiedCompletionOn;
    }

    public static boolean inPrivateImeOptions(String packageName, String key,
            EditorInfo editorInfo) {
        if (editorInfo == null) return false;
        final String findingKey = (packageName != null) ? packageName + "." + key
                : key;
        return StringUtils.containsInCommaSplittableText(findingKey, editorInfo.privateImeOptions);
    }
}
