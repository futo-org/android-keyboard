/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.inputmethod.deprecated;

import com.android.inputmethod.compat.InputMethodManagerCompatWrapper;
import com.android.inputmethod.deprecated.languageswitcher.LanguageSwitcher;
import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.Settings;

import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

// This class is used only when the IME doesn't use method.xml for language switching.
public class LanguageSwitcherProxy implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final LanguageSwitcherProxy sInstance = new LanguageSwitcherProxy();
    private LatinIME mService;
    private LanguageSwitcher mLanguageSwitcher;
    private SharedPreferences mPrefs;

    public static LanguageSwitcherProxy getInstance() {
        if (InputMethodManagerCompatWrapper.SUBTYPE_SUPPORTED) return null;
        return sInstance;
    }

    public static void init(LatinIME service, SharedPreferences prefs) {
        if (InputMethodManagerCompatWrapper.SUBTYPE_SUPPORTED) return;
        final Configuration conf = service.getResources().getConfiguration();
        sInstance.mLanguageSwitcher = new LanguageSwitcher(service);
        sInstance.mLanguageSwitcher.loadLocales(prefs, conf.locale);
        sInstance.mPrefs = prefs;
        sInstance.mService = service;
        prefs.registerOnSharedPreferenceChangeListener(sInstance);
    }

    public static void onConfigurationChanged(Configuration conf) {
        if (InputMethodManagerCompatWrapper.SUBTYPE_SUPPORTED) return;
        sInstance.mLanguageSwitcher.onConfigurationChanged(conf, sInstance.mPrefs);
    }

    public static void loadSettings() {
        if (InputMethodManagerCompatWrapper.SUBTYPE_SUPPORTED) return;
        sInstance.mLanguageSwitcher.loadLocales(sInstance.mPrefs, null);
    }

    public int getLocaleCount() {
        return mLanguageSwitcher.getLocaleCount();
    }

    public String[] getEnabledLanguages(boolean allowImplicitlySelectedLanguages) {
        return mLanguageSwitcher.getEnabledLanguages(allowImplicitlySelectedLanguages);
    }

    public Locale getInputLocale() {
        return mLanguageSwitcher.getInputLocale();
    }

    public void setLocale(String localeStr) {
        mLanguageSwitcher.setLocale(localeStr);
        mLanguageSwitcher.persist(mPrefs);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        // PREF_SELECTED_LANGUAGES: enabled input subtypes
        // PREF_INPUT_LANGUAGE: current input subtype
        if (key.equals(Settings.PREF_SELECTED_LANGUAGES)
                || key.equals(Settings.PREF_INPUT_LANGUAGE)) {
            mLanguageSwitcher.loadLocales(prefs, null);
            if (mService != null) {
                mService.onRefreshKeyboard();
            }
        }
    }
}
