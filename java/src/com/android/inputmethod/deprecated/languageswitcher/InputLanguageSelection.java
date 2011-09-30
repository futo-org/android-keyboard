/*
 * Copyright (C) 2008-2009 The Android Open Source Project
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

package com.android.inputmethod.deprecated.languageswitcher;

import com.android.inputmethod.compat.SharedPreferencesCompat;
import com.android.inputmethod.keyboard.internal.KeyboardBuilder;
import com.android.inputmethod.latin.DictionaryFactory;
import com.android.inputmethod.latin.LocaleUtils;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.Settings;
import com.android.inputmethod.latin.Utils;

import org.xmlpull.v1.XmlPullParserException;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Pair;

import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.TreeMap;

public class InputLanguageSelection extends PreferenceActivity {

    private SharedPreferences mPrefs;
    private String mSelectedLanguages;
    private HashMap<CheckBoxPreference, Locale> mLocaleMap =
            new HashMap<CheckBoxPreference, Locale>();

    private static class LocaleEntry implements Comparable<Object> {
        private static Collator sCollator = Collator.getInstance();

        private String mLabel;
        public final Locale mLocale;

        public LocaleEntry(String label, Locale locale) {
            this.mLabel = label;
            this.mLocale = locale;
        }

        @Override
        public String toString() {
            return this.mLabel;
        }

        @Override
        public int compareTo(Object o) {
            return sCollator.compare(this.mLabel, ((LocaleEntry) o).mLabel);
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.language_prefs);
        // Get the settings preferences
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mSelectedLanguages = mPrefs.getString(Settings.PREF_SELECTED_LANGUAGES, "");
        String[] languageList = mSelectedLanguages.split(",");
        ArrayList<LocaleEntry> availableLanguages = getUniqueLocales();
        PreferenceGroup parent = getPreferenceScreen();
        final HashMap<Long, LocaleEntry> dictionaryIdLocaleMap = new HashMap<Long, LocaleEntry>();
        final TreeMap<LocaleEntry, Boolean> localeHasDictionaryMap =
                new TreeMap<LocaleEntry, Boolean>();
        for (int i = 0; i < availableLanguages.size(); i++) {
            LocaleEntry loc = availableLanguages.get(i);
            Locale locale = loc.mLocale;
            final Pair<Long, Boolean> hasDictionaryOrLayout = hasDictionaryOrLayout(locale);
            final Long dictionaryId = hasDictionaryOrLayout.first;
            final boolean hasLayout = hasDictionaryOrLayout.second;
            final boolean hasDictionary = dictionaryId != null;
            // Add this locale to the supported list if:
            // 1) this locale has a layout/ 2) this locale has a dictionary
            // If some locales have no layout but have a same dictionary, the shortest locale
            // will be added to the supported list.
            if (!hasLayout && !hasDictionary) {
                continue;
            }
            if (hasLayout) {
                localeHasDictionaryMap.put(loc, hasDictionary);
            }
            if (!hasDictionary) {
                continue;
            }
            if (dictionaryIdLocaleMap.containsKey(dictionaryId)) {
                final String newLocale = locale.toString();
                final String oldLocale =
                        dictionaryIdLocaleMap.get(dictionaryId).mLocale.toString();
                // Check if this locale is more appropriate to be the candidate of the input locale.
                if (oldLocale.length() <= newLocale.length() && !hasLayout) {
                    // Don't add this new locale to the map<dictionary id, locale> if:
                    // 1) the new locale's name is longer than the existing one, and
                    // 2) the new locale doesn't have its layout
                    continue;
                }
            }
            dictionaryIdLocaleMap.put(dictionaryId, loc);
        }

        for (LocaleEntry localeEntry : dictionaryIdLocaleMap.values()) {
            if (!localeHasDictionaryMap.containsKey(localeEntry)) {
                localeHasDictionaryMap.put(localeEntry, true);
            }
        }

        for (Entry<LocaleEntry, Boolean> entry : localeHasDictionaryMap.entrySet()) {
            final LocaleEntry localeEntry = entry.getKey();
            final Locale locale = localeEntry.mLocale;
            final Boolean hasDictionary = entry.getValue();
            CheckBoxPreference pref = new CheckBoxPreference(this);
            pref.setTitle(localeEntry.mLabel);
            boolean checked = isLocaleIn(locale, languageList);
            pref.setChecked(checked);
            if (hasDictionary) {
                pref.setSummary(R.string.has_dictionary);
            }
            mLocaleMap.put(pref, locale);
            parent.addPreference(pref);
        }
    }

    private boolean isLocaleIn(Locale locale, String[] list) {
        String lang = get5Code(locale);
        for (int i = 0; i < list.length; i++) {
            if (lang.equalsIgnoreCase(list[i])) return true;
        }
        return false;
    }

    private Pair<Long, Boolean> hasDictionaryOrLayout(Locale locale) {
        if (locale == null) return new Pair<Long, Boolean>(null, false);
        final Resources res = getResources();
        final Locale saveLocale = LocaleUtils.setSystemLocale(res, locale);
        final Long dictionaryId = DictionaryFactory.getDictionaryId(this, locale);
        boolean hasLayout = false;

        try {
            final String localeStr = locale.toString();
            final String[] layoutCountryCodes = KeyboardBuilder.parseKeyboardLocale(
                    this, R.xml.kbd_qwerty).split(",", -1);
            if (!TextUtils.isEmpty(localeStr) && layoutCountryCodes.length > 0) {
                for (String s : layoutCountryCodes) {
                    if (s.equals(localeStr)) {
                        hasLayout = true;
                        break;
                    }
                }
            }
        } catch (XmlPullParserException e) {
        } catch (IOException e) {
        }
        LocaleUtils.setSystemLocale(res, saveLocale);
        return new Pair<Long, Boolean>(dictionaryId, hasLayout);
    }

    private String get5Code(Locale locale) {
        String country = locale.getCountry();
        return locale.getLanguage()
                + (TextUtils.isEmpty(country) ? "" : "_" + country);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save the selected languages
        String checkedLanguages = "";
        PreferenceGroup parent = getPreferenceScreen();
        int count = parent.getPreferenceCount();
        for (int i = 0; i < count; i++) {
            CheckBoxPreference pref = (CheckBoxPreference) parent.getPreference(i);
            if (pref.isChecked()) {
                checkedLanguages += get5Code(mLocaleMap.get(pref)) + ",";
            }
        }
        if (checkedLanguages.length() < 1) checkedLanguages = null; // Save null
        Editor editor = mPrefs.edit();
        editor.putString(Settings.PREF_SELECTED_LANGUAGES, checkedLanguages);
        SharedPreferencesCompat.apply(editor);
    }

    public ArrayList<LocaleEntry> getUniqueLocales() {
        String[] locales = getAssets().getLocales();
        Arrays.sort(locales);
        ArrayList<LocaleEntry> uniqueLocales = new ArrayList<LocaleEntry>();

        final int origSize = locales.length;
        LocaleEntry[] preprocess = new LocaleEntry[origSize];
        int finalSize = 0;
        for (int i = 0 ; i < origSize; i++ ) {
            String s = locales[i];
            int len = s.length();
            String language = "";
            String country = "";
            if (len == 5) {
                language = s.substring(0, 2);
                country = s.substring(3, 5);
            } else if (len < 5) {
                language = s;
            }
            Locale l = new Locale(language, country);

            // Exclude languages that are not relevant to LatinIME
            if (TextUtils.isEmpty(language)) {
                continue;
            }

            if (finalSize == 0) {
                preprocess[finalSize++] =
                        new LocaleEntry(Utils.getFullDisplayName(l, false), l);
            } else {
                if (s.equals("zz_ZZ")) {
                    // ignore this locale
                } else {
                    final String displayName = Utils.getFullDisplayName(l, false);
                    preprocess[finalSize++] = new LocaleEntry(displayName, l);
                }
            }
        }
        for (int i = 0; i < finalSize ; i++) {
            uniqueLocales.add(preprocess[i]);
        }
        return uniqueLocales;
    }
}
