/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.inputmethod.latin.utils;

import android.content.Intent;
import android.text.TextUtils;

public final class IntentUtils {
    private static final String EXTRA_INPUT_METHOD_ID = "input_method_id";
    // TODO: Can these be constants instead of literal String constants?
    private static final String INPUT_METHOD_SUBTYPE_SETTINGS =
            "android.settings.INPUT_METHOD_SUBTYPE_SETTINGS";

    private IntentUtils() {
        // This utility class is not publicly instantiable.
    }

    public static Intent getInputLanguageSelectionIntent(final String inputMethodId,
            final int flagsForSubtypeSettings) {
        // Refer to android.provider.Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS
        final String action = INPUT_METHOD_SUBTYPE_SETTINGS;
        final Intent intent = new Intent(action);
        if (!TextUtils.isEmpty(inputMethodId)) {
            intent.putExtra(EXTRA_INPUT_METHOD_ID, inputMethodId);
        }
        if (flagsForSubtypeSettings > 0) {
            intent.setFlags(flagsForSubtypeSettings);
        }
        return intent;
    }
}
