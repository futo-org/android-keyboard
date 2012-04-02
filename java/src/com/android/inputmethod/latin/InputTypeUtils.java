/*
 * Copyright (C) 2012 The Android Open Source Project
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

public class InputTypeUtils implements InputType {
    private static final int WEB_TEXT_PASSWORD_INPUT_TYPE =
            TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_WEB_PASSWORD;
    private static final int WEB_TEXT_EMAIL_ADDRESS_INPUT_TYPE =
            TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS;
    private static final int NUMBER_PASSWORD_INPUT_TYPE =
            TYPE_CLASS_NUMBER | TYPE_NUMBER_VARIATION_PASSWORD;
    private static final int TEXT_PASSWORD_INPUT_TYPE =
            TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_PASSWORD;
    private static final int TEXT_VISIBLE_PASSWORD_INPUT_TYPE =
            TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;

    private InputTypeUtils() {
        // This utility class is not publicly instantiable.
    }

    private static boolean isWebEditTextInputType(int inputType) {
        return inputType == (TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_WEB_EDIT_TEXT);
    }

    private static boolean isWebPasswordInputType(int inputType) {
        return WEB_TEXT_PASSWORD_INPUT_TYPE != 0
                && inputType == WEB_TEXT_PASSWORD_INPUT_TYPE;
    }

    private static boolean isWebEmailAddressInputType(int inputType) {
        return WEB_TEXT_EMAIL_ADDRESS_INPUT_TYPE != 0
                && inputType == WEB_TEXT_EMAIL_ADDRESS_INPUT_TYPE;
    }

    private static boolean isNumberPasswordInputType(int inputType) {
        return NUMBER_PASSWORD_INPUT_TYPE != 0
                && inputType == NUMBER_PASSWORD_INPUT_TYPE;
    }

    private static boolean isTextPasswordInputType(int inputType) {
        return inputType == TEXT_PASSWORD_INPUT_TYPE;
    }

    private static boolean isWebEmailAddressVariation(int variation) {
        return variation == TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS;
    }

    public static boolean isEmailVariation(int variation) {
        return variation == TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || isWebEmailAddressVariation(variation);
    }

    public static boolean isWebInputType(int inputType) {
        final int maskedInputType =
                inputType & (TYPE_MASK_CLASS | TYPE_MASK_VARIATION);
        return isWebEditTextInputType(maskedInputType) || isWebPasswordInputType(maskedInputType)
                || isWebEmailAddressInputType(maskedInputType);
    }

    // Please refer to TextView.isPasswordInputType
    public static boolean isPasswordInputType(int inputType) {
        final int maskedInputType =
                inputType & (TYPE_MASK_CLASS | TYPE_MASK_VARIATION);
        return isTextPasswordInputType(maskedInputType) || isWebPasswordInputType(maskedInputType)
                || isNumberPasswordInputType(maskedInputType);
    }

    // Please refer to TextView.isVisiblePasswordInputType
    public static boolean isVisiblePasswordInputType(int inputType) {
        final int maskedInputType =
                inputType & (TYPE_MASK_CLASS | TYPE_MASK_VARIATION);
        return maskedInputType == TEXT_VISIBLE_PASSWORD_INPUT_TYPE;
    }
}
