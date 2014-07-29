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

import com.android.inputmethod.latin.utils.FragmentUtils;

import android.app.ActionBar;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.MenuItem;

public final class SettingsActivity extends PreferenceActivity {
    public static final String EXTRA_SHOW_HOME_AS_UP = "show_home_as_up";
    private static final String DEFAULT_FRAGMENT = SettingsFragment.class.getName();
    private boolean mShowHomeAsUp;

    @Override
    protected void onCreate(final Bundle savedState) {
        super.onCreate(savedState);
        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            mShowHomeAsUp = getIntent().getBooleanExtra(EXTRA_SHOW_HOME_AS_UP, true);
            actionBar.setDisplayHomeAsUpEnabled(mShowHomeAsUp);
            actionBar.setHomeButtonEnabled(mShowHomeAsUp);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (mShowHomeAsUp && item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public Intent getIntent() {
        final Intent intent = super.getIntent();
        final String fragment = intent.getStringExtra(EXTRA_SHOW_FRAGMENT);
        if (fragment == null) {
            intent.putExtra(EXTRA_SHOW_FRAGMENT, DEFAULT_FRAGMENT);
        }
        intent.putExtra(EXTRA_NO_HEADERS, true);
        return intent;
    }

    @Override
    public boolean isValidFragment(final String fragmentName) {
        return FragmentUtils.isValidFragment(fragmentName);
    }
}
