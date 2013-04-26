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

package com.android.inputmethod.latin.userdictionary;

import com.android.inputmethod.latin.LocaleUtils;
import com.android.inputmethod.latin.R;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.provider.UserDictionary;
import android.text.TextUtils;

import java.util.Locale;
import java.util.TreeSet;

// Caveat: This class is basically taken from
// packages/apps/Settings/src/com/android/settings/inputmethod/UserDictionaryList.java
// in order to deal with some devices that have issues with the user dictionary handling

public class UserDictionaryList extends PreferenceFragment {

    public static final String USER_DICTIONARY_SETTINGS_INTENT_ACTION =
            "android.settings.USER_DICTIONARY_SETTINGS";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(getActivity()));
    }

    public static TreeSet<String> getUserDictionaryLocalesSet(Activity activity) {
        @SuppressWarnings("deprecation")
        final Cursor cursor = activity.managedQuery(UserDictionary.Words.CONTENT_URI,
                new String[] { UserDictionary.Words.LOCALE },
                null, null, null);
        final TreeSet<String> localeList = new TreeSet<String>();
        boolean addedAllLocale = false;
        if (null == cursor) {
            // The user dictionary service is not present or disabled. Return null.
            return null;
        } else if (cursor.moveToFirst()) {
            final int columnIndex = cursor.getColumnIndex(UserDictionary.Words.LOCALE);
            do {
                final String locale = cursor.getString(columnIndex);
                final boolean allLocale = TextUtils.isEmpty(locale);
                localeList.add(allLocale ? "" : locale);
                if (allLocale) {
                    addedAllLocale = true;
                }
            } while (cursor.moveToNext());
        }
        if (!UserDictionarySettings.IS_SHORTCUT_API_SUPPORTED && !addedAllLocale) {
            // For ICS, we need to show "For all languages" in case that the keyboard locale
            // is different from the system locale
            localeList.add("");
        }
        localeList.add(Locale.getDefault().toString());
        return localeList;
    }

    /**
     * Creates the entries that allow the user to go into the user dictionary for each locale.
     * @param userDictGroup The group to put the settings in.
     */
    protected void createUserDictSettings(PreferenceGroup userDictGroup) {
        final Activity activity = getActivity();
        userDictGroup.removeAll();
        final TreeSet<String> localeList =
                UserDictionaryList.getUserDictionaryLocalesSet(activity);

        if (localeList.isEmpty()) {
            userDictGroup.addPreference(createUserDictionaryPreference(null, activity));
        } else {
            for (String locale : localeList) {
                userDictGroup.addPreference(createUserDictionaryPreference(locale, activity));
            }
        }
    }

    /**
     * Create a single User Dictionary Preference object, with its parameters set.
     * @param locale The locale for which this user dictionary is for.
     * @return The corresponding preference.
     */
    protected Preference createUserDictionaryPreference(String locale, Activity activity) {
        final Preference newPref = new Preference(getActivity());
        final Intent intent = new Intent(USER_DICTIONARY_SETTINGS_INTENT_ACTION);
        if (null == locale) {
            newPref.setTitle(Locale.getDefault().getDisplayName());
        } else {
            if ("".equals(locale))
                newPref.setTitle(getString(R.string.user_dict_settings_all_languages));
            else
                newPref.setTitle(LocaleUtils.constructLocaleFromString(locale).getDisplayName());
            intent.putExtra("locale", locale);
            newPref.getExtras().putString("locale", locale);
        }
        newPref.setIntent(intent);
        newPref.setFragment(UserDictionarySettings.class.getName());
        return newPref;
    }

    @Override
    public void onResume() {
        super.onResume();
        createUserDictSettings(getPreferenceScreen());
    }
}
