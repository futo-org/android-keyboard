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

package com.android.inputmethod.compat;

import android.text.InputType;

import java.lang.reflect.Field;

public class InputTypeCompatUtils {
    private static final Field FIELD_InputType_TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS =
            CompatUtils.getField(InputType.class, "TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS");
    private static final Field FIELD_InputType_TYPE_TEXT_VARIATION_WEB_PASSWORD = CompatUtils
            .getField(InputType.class, "TYPE_TEXT_VARIATION_WEB_PASSWORD");
    private static final Field FIELD_InputType_TYPE_NUMBER_VARIATION_PASSWORD = CompatUtils
            .getField(InputType.class, "TYPE_NUMBER_VARIATION_PASSWORD");
    private static final Integer OBJ_InputType_TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS =
            (Integer) CompatUtils.getFieldValue(null, null,
                    FIELD_InputType_TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS);
    private static final Integer OBJ_InputType_TYPE_TEXT_VARIATION_WEB_PASSWORD =
            (Integer) CompatUtils.getFieldValue(null, null,
                    FIELD_InputType_TYPE_TEXT_VARIATION_WEB_PASSWORD);
    private static final Integer OBJ_InputType_TYPE_NUMBER_VARIATION_PASSWORD =
            (Integer) CompatUtils.getFieldValue(null, null,
                    FIELD_InputType_TYPE_NUMBER_VARIATION_PASSWORD);
    private static final int WEB_TEXT_PASSWORD_INPUT_TYPE;
    private static final int WEB_TEXT_EMAIL_ADDRESS_INPUT_TYPE;
    private static final int NUMBER_PASSWORD_INPUT_TYPE;
    private static final int TEXT_PASSWORD_INPUT_TYPE =
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
    private static final int TEXT_VISIBLE_PASSWORD_INPUT_TYPE =
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;

    static {
        WEB_TEXT_PASSWORD_INPUT_TYPE =
            OBJ_InputType_TYPE_TEXT_VARIATION_WEB_PASSWORD != null
                    ? InputType.TYPE_CLASS_TEXT | OBJ_InputType_TYPE_TEXT_VARIATION_WEB_PASSWORD
                    : 0;
        WEB_TEXT_EMAIL_ADDRESS_INPUT_TYPE =
            OBJ_InputType_TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS != null
                    ? InputType.TYPE_CLASS_TEXT
                            | OBJ_InputType_TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                    : 0;
        NUMBER_PASSWORD_INPUT_TYPE =
                OBJ_InputType_TYPE_NUMBER_VARIATION_PASSWORD != null
                        ? InputType.TYPE_CLASS_NUMBER | OBJ_InputType_TYPE_NUMBER_VARIATION_PASSWORD
                        : 0;
    }

    private static boolean isWebEditTextInputType(int inputType) {
        return inputType == (InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT);
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
        return OBJ_InputType_TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS != null
                && variation == OBJ_InputType_TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS;
    }

    public static boolean isEmailVariation(int variation) {
        return variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || isWebEmailAddressVariation(variation);
    }

    public static boolean isWebInputType(int inputType) {
        final int maskedInputType =
                inputType & (InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION);
        return isWebEditTextInputType(maskedInputType) || isWebPasswordInputType(maskedInputType)
                || isWebEmailAddressInputType(maskedInputType);
    }

    // Please refer to TextView.isPasswordInputType
    public static boolean isPasswordInputType(int inputType) {
        final int maskedInputType =
                inputType & (InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION);
        return isTextPasswordInputType(maskedInputType) || isWebPasswordInputType(maskedInputType)
                || isNumberPasswordInputType(maskedInputType);
    }

    // Please refer to TextView.isVisiblePasswordInputType
    public static boolean isVisiblePasswordInputType(int inputType) {
        final int maskedInputType =
                inputType & (InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION);
        return maskedInputType == TEXT_VISIBLE_PASSWORD_INPUT_TYPE;
    }
}
