/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.inputmethod.deprecated.voice;

import android.os.Bundle;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

/**
 * Represents information about a given text field, which can be passed
 * to the speech recognizer as context information.
 */
public class FieldContext {
    private static final boolean DBG = false;
    
    static final String LABEL = "label";
    static final String HINT = "hint";
    static final String PACKAGE_NAME = "packageName";
    static final String FIELD_ID = "fieldId";
    static final String FIELD_NAME = "fieldName";
    static final String SINGLE_LINE = "singleLine";
    static final String INPUT_TYPE = "inputType";
    static final String IME_OPTIONS = "imeOptions";
    static final String SELECTED_LANGUAGE = "selectedLanguage";
    static final String ENABLED_LANGUAGES = "enabledLanguages";

    Bundle mFieldInfo;

    public FieldContext(InputConnection conn, EditorInfo info,
            String selectedLanguage, String[] enabledLanguages) {
        mFieldInfo = new Bundle();
        addEditorInfoToBundle(info, mFieldInfo);
        addInputConnectionToBundle(conn, mFieldInfo);
        addLanguageInfoToBundle(selectedLanguage, enabledLanguages, mFieldInfo);
        if (DBG) Log.i("FieldContext", "Bundle = " + mFieldInfo.toString());
    }

    private static String safeToString(Object o) {
        if (o == null) {
            return "";
        }
        return o.toString();
    }

    private static void addEditorInfoToBundle(EditorInfo info, Bundle bundle) {
        if (info == null) {
            return;
        }

        bundle.putString(LABEL, safeToString(info.label));
        bundle.putString(HINT, safeToString(info.hintText));
        bundle.putString(PACKAGE_NAME, safeToString(info.packageName));
        bundle.putInt(FIELD_ID, info.fieldId);
        bundle.putString(FIELD_NAME, safeToString(info.fieldName));
        bundle.putInt(INPUT_TYPE, info.inputType);
        bundle.putInt(IME_OPTIONS, info.imeOptions);
    }

    @SuppressWarnings("static-access")
    private static void addInputConnectionToBundle(
        InputConnection conn, Bundle bundle) {
        if (conn == null) {
            return;
        }

        ExtractedText et = conn.getExtractedText(new ExtractedTextRequest(), 0);
        if (et == null) {
            return;
        }
        bundle.putBoolean(SINGLE_LINE, (et.flags & et.FLAG_SINGLE_LINE) > 0);
    }
    
    private static void addLanguageInfoToBundle(
            String selectedLanguage, String[] enabledLanguages, Bundle bundle) {
        bundle.putString(SELECTED_LANGUAGE, selectedLanguage);
        bundle.putStringArray(ENABLED_LANGUAGES, enabledLanguages);
    }

    public Bundle getBundle() {
        return mFieldInfo;
    }

    @Override
    public String toString() {
        return mFieldInfo.toString();
    }
}
