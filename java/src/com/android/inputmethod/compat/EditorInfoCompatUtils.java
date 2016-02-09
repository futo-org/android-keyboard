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

package com.android.inputmethod.compat;

import android.view.inputmethod.EditorInfo;

import java.lang.reflect.Field;
import java.util.Locale;

public final class EditorInfoCompatUtils {
    // Note that EditorInfo.IME_FLAG_FORCE_ASCII has been introduced
    // in API level 16 (Build.VERSION_CODES.JELLY_BEAN).
    private static final Field FIELD_IME_FLAG_FORCE_ASCII = CompatUtils.getField(
            EditorInfo.class, "IME_FLAG_FORCE_ASCII");
    private static final Integer OBJ_IME_FLAG_FORCE_ASCII = (Integer) CompatUtils.getFieldValue(
            null /* receiver */, null /* defaultValue */, FIELD_IME_FLAG_FORCE_ASCII);
    private static final Field FIELD_HINT_LOCALES = CompatUtils.getField(
            EditorInfo.class, "hintLocales");

    private EditorInfoCompatUtils() {
        // This utility class is not publicly instantiable.
    }

    public static boolean hasFlagForceAscii(final int imeOptions) {
        if (OBJ_IME_FLAG_FORCE_ASCII == null) return false;
        return (imeOptions & OBJ_IME_FLAG_FORCE_ASCII) != 0;
    }

    public static String imeActionName(final int imeOptions) {
        final int actionId = imeOptions & EditorInfo.IME_MASK_ACTION;
        switch (actionId) {
        case EditorInfo.IME_ACTION_UNSPECIFIED:
            return "actionUnspecified";
        case EditorInfo.IME_ACTION_NONE:
            return "actionNone";
        case EditorInfo.IME_ACTION_GO:
            return "actionGo";
        case EditorInfo.IME_ACTION_SEARCH:
            return "actionSearch";
        case EditorInfo.IME_ACTION_SEND:
            return "actionSend";
        case EditorInfo.IME_ACTION_NEXT:
            return "actionNext";
        case EditorInfo.IME_ACTION_DONE:
            return "actionDone";
        case EditorInfo.IME_ACTION_PREVIOUS:
            return "actionPrevious";
        default:
            return "actionUnknown(" + actionId + ")";
        }
    }

    public static String imeOptionsName(final int imeOptions) {
        final String action = imeActionName(imeOptions);
        final StringBuilder flags = new StringBuilder();
        if ((imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
            flags.append("flagNoEnterAction|");
        }
        if ((imeOptions & EditorInfo.IME_FLAG_NAVIGATE_NEXT) != 0) {
            flags.append("flagNavigateNext|");
        }
        if ((imeOptions & EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS) != 0) {
            flags.append("flagNavigatePrevious|");
        }
        if (hasFlagForceAscii(imeOptions)) {
            flags.append("flagForceAscii|");
        }
        return (action != null) ? flags + action : flags.toString();
    }

    public static Locale getPrimaryHintLocale(final EditorInfo editorInfo) {
        if (editorInfo == null) {
            return null;
        }
        final Object localeList = CompatUtils.getFieldValue(editorInfo, null, FIELD_HINT_LOCALES);
        if (localeList == null) {
            return null;
        }
        if (LocaleListCompatUtils.isEmpty(localeList)) {
            return null;
        }
        return LocaleListCompatUtils.get(localeList, 0);
    }
}
