/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.text.InputType;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.compat.InputTypeCompatUtils;

/**
 * Class to hold attributes of the input field.
 */
public class InputAttributes {
    private final String TAG = InputAttributes.class.getSimpleName();

    final public boolean mInsertSpaceOnPickSuggestionManually;
    final public boolean mInputTypeNoAutoCorrect;
    final public boolean mIsSettingsSuggestionStripOn;
    final public boolean mApplicationSpecifiedCompletionOn;

    public InputAttributes(final EditorInfo editorInfo, final boolean isFullscreenMode) {
        final boolean insertSpaceOnPickSuggestionManually;
        final boolean inputTypeNoAutoCorrect;
        final boolean isSettingsSuggestionStripOn;
        final boolean applicationSpecifiedCompletionOn;

        if (editorInfo == null || editorInfo.inputType == InputType.TYPE_CLASS_TEXT) {
            insertSpaceOnPickSuggestionManually = false;
            isSettingsSuggestionStripOn = false;
            inputTypeNoAutoCorrect = false;
            applicationSpecifiedCompletionOn = false;
        } else {
            final int inputType = editorInfo.inputType;
            if (inputType == InputType.TYPE_NULL) {
                // TODO: We should honor TYPE_NULL specification.
                Log.i(TAG, "InputType.TYPE_NULL is specified");
            }
            final int inputClass = inputType & InputType.TYPE_MASK_CLASS;
            final int variation = inputType & InputType.TYPE_MASK_VARIATION;
            if (inputClass == 0) {
                Log.w(TAG, String.format("Unexpected input class: inputType=0x%08x"
                        + " imeOptions=0x%08x",
                        inputType, editorInfo.imeOptions));
            }
            final boolean flagNoSuggestions =
                    0 != (inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            final boolean flagMultiLine =
                    0 != (inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            final boolean flagAutoCorrect =
                    0 != (inputType & InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
            final boolean flagAutoComplete =
                    0 != (inputType & InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);

            // Make sure that passwords are not displayed in {@link SuggestionsView}.
            if (InputTypeCompatUtils.isPasswordInputType(inputType)
                    || InputTypeCompatUtils.isVisiblePasswordInputType(inputType)
                    || InputTypeCompatUtils.isEmailVariation(variation)
                    || InputType.TYPE_TEXT_VARIATION_URI == variation
                    || InputType.TYPE_TEXT_VARIATION_FILTER == variation
                    || flagNoSuggestions
                    || flagAutoComplete) {
                isSettingsSuggestionStripOn = false;
            } else {
                isSettingsSuggestionStripOn = true;
            }

            if (InputTypeCompatUtils.isEmailVariation(variation)
                    || variation == InputType.TYPE_TEXT_VARIATION_PERSON_NAME) {
                // The point in turning this off is that we don't want to insert a space after
                // a name when filling a form: we can't delete trailing spaces when changing fields
                insertSpaceOnPickSuggestionManually = false;
            } else {
                insertSpaceOnPickSuggestionManually = true;
            }

            // If it's a browser edit field and auto correct is not ON explicitly, then
            // disable auto correction, but keep suggestions on.
            // If NO_SUGGESTIONS is set, don't do prediction.
            // If it's not multiline and the autoCorrect flag is not set, then don't correct
            if ((variation == InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT
                    && !flagAutoCorrect)
                    || flagNoSuggestions
                    || (!flagAutoCorrect && !flagMultiLine)) {
                inputTypeNoAutoCorrect = true;
            } else {
                inputTypeNoAutoCorrect = false;
            }

            applicationSpecifiedCompletionOn = flagAutoComplete && isFullscreenMode;
        }

        mInsertSpaceOnPickSuggestionManually = insertSpaceOnPickSuggestionManually;
        mInputTypeNoAutoCorrect = inputTypeNoAutoCorrect;
        mIsSettingsSuggestionStripOn = isSettingsSuggestionStripOn;
        mApplicationSpecifiedCompletionOn = applicationSpecifiedCompletionOn;
    }
}
