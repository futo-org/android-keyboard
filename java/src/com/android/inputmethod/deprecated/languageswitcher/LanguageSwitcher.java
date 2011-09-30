/*
 * Copyright (C) 2010 The Android Open Source Project
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
import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.LocaleUtils;
import com.android.inputmethod.latin.Settings;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Keeps track of list of selected input languages and the current
 * input language that the user has selected.
 */
public class LanguageSwitcher {
    private static final String TAG = LanguageSwitcher.class.getSimpleName();

    @SuppressWarnings("unused")
    private static final String KEYBOARD_MODE = "keyboard";
    private static final String[] EMPTY_STIRNG_ARRAY = new String[0];

    private final ArrayList<Locale> mLocales = new ArrayList<Locale>();
    private final LatinIME mIme;
    private String[] mSelectedLanguageArray = EMPTY_STIRNG_ARRAY;
    private String   mSelectedLanguages;
    private int      mCurrentIndex = 0;
    private String   mDefaultInputLanguage;
    private Locale   mDefaultInputLocale;
    private Locale   mSystemLocale;

    public LanguageSwitcher(LatinIME ime) {
        mIme = ime;
    }

    public int getLocaleCount() {
        return mLocales.size();
    }

    public void onConfigurationChanged(Configuration conf, SharedPreferences prefs) {
        final Locale newLocale = conf.locale;
        if (!getSystemLocale().toString().equals(newLocale.toString())) {
            loadLocales(prefs, newLocale);
        }
    }

    /**
     * Loads the currently selected input languages from shared preferences.
     * @param sp shared preference for getting the current input language and enabled languages
     * @param systemLocale the current system locale, stored for changing the current input language
     * based on the system current system locale.
     * @return whether there was any change
     */
    public boolean loadLocales(SharedPreferences sp, Locale systemLocale) {
        if (LatinImeLogger.sDBG) {
            Log.d(TAG, "load locales");
        }
        if (systemLocale != null) {
            setSystemLocale(systemLocale);
        }
        String selectedLanguages = sp.getString(Settings.PREF_SELECTED_LANGUAGES, null);
        String currentLanguage   = sp.getString(Settings.PREF_INPUT_LANGUAGE, null);
        if (TextUtils.isEmpty(selectedLanguages)) {
            mSelectedLanguageArray = EMPTY_STIRNG_ARRAY;
            mSelectedLanguages = null;
            loadDefaults();
            if (mLocales.size() == 0) {
                return false;
            }
            mLocales.clear();
            return true;
        }
        if (selectedLanguages.equals(mSelectedLanguages)) {
            return false;
        }
        mSelectedLanguageArray = selectedLanguages.split(",");
        mSelectedLanguages = selectedLanguages; // Cache it for comparison later
        constructLocales();
        mCurrentIndex = 0;
        if (currentLanguage != null) {
            // Find the index
            mCurrentIndex = 0;
            for (int i = 0; i < mLocales.size(); i++) {
                if (mSelectedLanguageArray[i].equals(currentLanguage)) {
                    mCurrentIndex = i;
                    break;
                }
            }
            // If we didn't find the index, use the first one
        }
        return true;
    }

    private void loadDefaults() {
        if (LatinImeLogger.sDBG) {
            Log.d(TAG, "load default locales:");
        }
        mDefaultInputLocale = mIme.getResources().getConfiguration().locale;
        String country = mDefaultInputLocale.getCountry();
        mDefaultInputLanguage = mDefaultInputLocale.getLanguage() +
                (TextUtils.isEmpty(country) ? "" : "_" + country);
    }

    private void constructLocales() {
        mLocales.clear();
        for (final String lang : mSelectedLanguageArray) {
            final Locale locale = LocaleUtils.constructLocaleFromString(lang);
            mLocales.add(locale);
        }
    }

    /**
     * Returns the currently selected input language code, or the display language code if
     * no specific locale was selected for input.
     */
    public String getInputLanguage() {
        if (getLocaleCount() == 0) return mDefaultInputLanguage;

        return mSelectedLanguageArray[mCurrentIndex];
    }
    
    /**
     * Returns the list of enabled language codes.
     */
    public String[] getEnabledLanguages(boolean allowImplicitlySelectedLanguages) {
        if (mSelectedLanguageArray.length == 0 && allowImplicitlySelectedLanguages) {
            return new String[] { mDefaultInputLanguage };
        }
        return mSelectedLanguageArray;
    }

    /**
     * Returns the currently selected input locale, or the display locale if no specific
     * locale was selected for input.
     */
    public Locale getInputLocale() {
        if (getLocaleCount() == 0) return mDefaultInputLocale;

        return mLocales.get(mCurrentIndex);
    }

    private int nextLocaleIndex() {
        final int size = mLocales.size();
        return (mCurrentIndex + 1) % size;
    }

    private int prevLocaleIndex() {
        final int size = mLocales.size();
        return (mCurrentIndex - 1 + size) % size;
    }

    /**
     * Returns the next input locale in the list. Wraps around to the beginning of the
     * list if we're at the end of the list.
     */
    public Locale getNextInputLocale() {
        if (getLocaleCount() == 0) return mDefaultInputLocale;
        return mLocales.get(nextLocaleIndex());
    }

    /**
     * Sets the system locale (display UI) used for comparing with the input language.
     * @param locale the locale of the system
     */
    private void setSystemLocale(Locale locale) {
        mSystemLocale = locale;
    }

    /**
     * Returns the system locale.
     * @return the system locale
     */
    private Locale getSystemLocale() {
        return mSystemLocale;
    }

    /**
     * Returns the previous input locale in the list. Wraps around to the end of the
     * list if we're at the beginning of the list.
     */
    public Locale getPrevInputLocale() {
        if (getLocaleCount() == 0) return mDefaultInputLocale;
        return mLocales.get(prevLocaleIndex());
    }

    public void reset() {
        mCurrentIndex = 0;
    }

    public void next() {
        mCurrentIndex = nextLocaleIndex();
    }

    public void prev() {
        mCurrentIndex = prevLocaleIndex();
    }

    public void setLocale(String localeStr) {
        final int N = mLocales.size();
        for (int i = 0; i < N; ++i) {
            if (mLocales.get(i).toString().equals(localeStr)) {
                mCurrentIndex = i;
            }
        }
    }

    public void persist(SharedPreferences prefs) {
        Editor editor = prefs.edit();
        editor.putString(Settings.PREF_INPUT_LANGUAGE, getInputLanguage());
        SharedPreferencesCompat.apply(editor);
    }
}
