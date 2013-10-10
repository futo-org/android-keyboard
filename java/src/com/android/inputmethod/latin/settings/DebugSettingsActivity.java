/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceActivity;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.utils.FragmentUtils;

public final class DebugSettingsActivity extends PreferenceActivity {
    private static final String DEFAULT_FRAGMENT = DebugSettings.class.getName();

    @Override
    public Intent getIntent() {
        final Intent intent = super.getIntent();
        intent.putExtra(EXTRA_SHOW_FRAGMENT, DEFAULT_FRAGMENT);
        intent.putExtra(EXTRA_NO_HEADERS, true);
        return intent;
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.english_ime_debug_settings);
    }

    // TODO: Uncomment the override annotation once we start using SDK version 19.
    // @Override
    public boolean isValidFragment(String fragmentName) {
        return FragmentUtils.isValidFragment(fragmentName);
    }
}
