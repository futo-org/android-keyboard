/*
 * Copyright (C) 2008 The Android Open Source Project
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
import android.preference.PreferenceScreen;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.utils.ApplicationUtils;
import com.android.inputmethod.latin.utils.FeedbackUtils;
import com.android.inputmethodcommon.InputMethodSettingsFragment;

public final class SettingsFragment extends InputMethodSettingsFragment {
    private static final int NO_MENU_GROUP = Menu.NONE; // We don't care about menu grouping.
    private static final int MENU_FEEDBACK = Menu.FIRST; // The first menu item id and order.
    private static final int MENU_ABOUT = Menu.FIRST + 1; // The second menu item id and order.

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        setHasOptionsMenu(true);
        setInputMethodSettingsCategoryTitle(R.string.language_selection_title);
        setSubtypeEnablerTitle(R.string.select_language);
        addPreferencesFromResource(R.xml.prefs);
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        preferenceScreen.setTitle(
                ApplicationUtils.getActivityTitleResId(getActivity(), SettingsActivity.class));
    }

    @Override
    public void onResume() {
        super.onResume();
        ThemeSettingsFragment.updateKeyboardThemeSummary(findPreference(Settings.SCREEN_THEME));
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        if (FeedbackUtils.isFeedbackFormSupported()) {
            menu.add(NO_MENU_GROUP, MENU_FEEDBACK /* itemId */, MENU_FEEDBACK /* order */,
                    R.string.send_feedback);
        }
        final int aboutResId = FeedbackUtils.getAboutKeyboardTitleResId();
        if (aboutResId != 0) {
            menu.add(NO_MENU_GROUP, MENU_ABOUT /* itemId */, MENU_ABOUT /* order */, aboutResId);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == MENU_FEEDBACK) {
            FeedbackUtils.showFeedbackForm(getActivity());
            return true;
        }
        if (itemId == MENU_ABOUT) {
            final Intent aboutIntent = FeedbackUtils.getAboutKeyboardIntent(getActivity());
            if (aboutIntent != null) {
                startActivity(aboutIntent);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }
}
