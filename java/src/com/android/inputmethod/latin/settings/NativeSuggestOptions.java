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

package com.android.inputmethod.latin.settings;

public class NativeSuggestOptions {
    // Need to update suggest_options.h when you add, remove or reorder options.
    private static final int IS_GESTURE = 0;
    private static final int USE_FULL_EDIT_DISTANCE = 1;
    private static final int OPTIONS_SIZE = 2;

    private final int[] mOptions = new int[OPTIONS_SIZE
            + AdditionalFeaturesSettingUtils.ADDITIONAL_FEATURES_SETTINGS_SIZE];

    public void setIsGesture(final boolean value) {
        setBooleanOption(IS_GESTURE, value);
    }

    public void setUseFullEditDistance(final boolean value) {
        setBooleanOption(USE_FULL_EDIT_DISTANCE, value);
    }

    public void setAdditionalFeaturesOptions(final int[] additionalOptions) {
        if (additionalOptions == null) {
            return;
        }
        for (int i = 0; i < additionalOptions.length; i++) {
            setIntegerOption(OPTIONS_SIZE + i, additionalOptions[i]);
        }
    }

    public int[] getOptions() {
        return mOptions;
    }

    private void setBooleanOption(final int key, final boolean value) {
        mOptions[key] = value ? 1 : 0;
    }

    private void setIntegerOption(final int key, final int value) {
        mOptions[key] = value;
    }
}
