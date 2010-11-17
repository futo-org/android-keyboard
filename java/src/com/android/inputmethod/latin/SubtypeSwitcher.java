/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.inputmethod.latin;

import com.android.inputmethod.voice.SettingsUtil;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.inputmethodservice.InputMethodService;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodSubtype;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class SubtypeSwitcher {
    private static final boolean USE_LEGACY_LANGUAGE_SWITCHER = true;
    private static final String TAG = "SubtypeSwitcher";
    private static final SubtypeSwitcher sInstance = new SubtypeSwitcher();
    private InputMethodService mService;
    private Resources mResources;
    private Locale mSystemLocale;

    public static SubtypeSwitcher getInstance() {
        return sInstance;
    }

    public static void init(LatinIME service) {
        sInstance.mService = service;
        sInstance.mResources = service.getResources();
        sInstance.mSystemLocale = sInstance.mResources.getConfiguration().locale;
        if (USE_LEGACY_LANGUAGE_SWITCHER) {
            sInstance.initLanguageSwitcher(service);
        }
    }

    private SubtypeSwitcher() {
    }

    public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype subtype) {
    }

    //////////////////////////////////
    // Language Switching functions //
    //////////////////////////////////

    private int getEnabledKeyboardLocaleCount() {
        if (USE_LEGACY_LANGUAGE_SWITCHER) {
            return mLanguageSwitcher.getLocaleCount();
        }
        // TODO: Implement for no legacy mode
        return 0;
    }

    public boolean isLanguageSwitchEnabled() {
     // TODO: Takes care of two-char locale such as "en" in addition to "en_US"
        return !(getEnabledKeyboardLocaleCount() == 1 && getSystemLocale().getLanguage(
                ).equalsIgnoreCase(getInputLocale().getLanguage()));
    }

    public Locale getInputLocale() {
        if (USE_LEGACY_LANGUAGE_SWITCHER) {
            return mLanguageSwitcher.getInputLocale();
        }
        // TODO: Implement for no legacy mode
        return null;
    }

    public String getInputLanguage() {
        String inputLanguage = null;
        if (USE_LEGACY_LANGUAGE_SWITCHER) {
            inputLanguage = mLanguageSwitcher.getInputLanguage();
        }
        // Should return system locale if there is no Language available.
        if (inputLanguage == null) {
            inputLanguage = getSystemLocale().getLanguage();
        }
        return inputLanguage;
    }

    public String[] getEnabledLanguages() {
        if (USE_LEGACY_LANGUAGE_SWITCHER) {
            return mLanguageSwitcher.getEnabledLanguages();
        }
        // TODO: Implement for no legacy mode
        return null;
    }

    public Locale getSystemLocale() {
        return mSystemLocale;
    }

    public boolean isSystemLocaleSameAsInputLocale() {
        // TODO: Takes care of two-char locale such as "en" in addition to "en_US"
        return getSystemLocale().getLanguage().equalsIgnoreCase(
                getInputLanguage().substring(0, 2));
    }

    public void onConfigurationChanged(Configuration conf) {
        if (USE_LEGACY_LANGUAGE_SWITCHER) {
            // If the system locale changes and is different from the saved
            // locale (mSystemLocale), then reload the input locale list from the
            // latin ime settings (shared prefs) and reset the input locale
            // to the first one.
            final Locale systemLocale = conf.locale;
            if (!TextUtils.equals(systemLocale.toString(), mSystemLocale.toString())) {
                mSystemLocale = systemLocale;
                mLanguageSwitcher.loadLocales(
                        PreferenceManager.getDefaultSharedPreferences(mService));
                mLanguageSwitcher.setSystemLocale(systemLocale);
                toggleLanguage(true, true);
            }
            return;
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (USE_LEGACY_LANGUAGE_SWITCHER) {
            mLanguageSwitcher.loadLocales(sharedPreferences);
            return;
        }
    }

    public Locale changeSystemLocale(Locale newLocale) {
        Configuration conf = mResources.getConfiguration();
        Locale oldLocale = conf.locale;
        conf.locale = newLocale;
        mResources.updateConfiguration(conf, mResources.getDisplayMetrics());
        return oldLocale;
    }

    ////////////////////////////////////////////
    // Legacy Language Switch support //
    ////////////////////////////////////////////
    private LanguageSwitcher mLanguageSwitcher;


    // TODO: This can be an array of String
    // A list of locales which are supported by default for voice input, unless we get a
    // different list from Gservices.
    private static final String DEFAULT_VOICE_INPUT_SUPPORTED_LOCALES =
            "en " +
            "en_US " +
            "en_GB " +
            "en_AU " +
            "en_CA " +
            "en_IE " +
            "en_IN " +
            "en_NZ " +
            "en_SG " +
            "en_ZA ";

    public boolean isVoiceSupported(String locale) {
        // Get the current list of supported locales and check the current locale against that
        // list. We cache this value so as not to check it every time the user starts a voice
        // input. Because this method is called by onStartInputView, this should mean that as
        // long as the locale doesn't change while the user is keeping the IME open, the
        // value should never be stale.
        String supportedLocalesString = SettingsUtil.getSettingsString(
                mService.getContentResolver(),
                SettingsUtil.LATIN_IME_VOICE_INPUT_SUPPORTED_LOCALES,
                DEFAULT_VOICE_INPUT_SUPPORTED_LOCALES);
        List<String> voiceInputSupportedLocales = Arrays.asList(
                supportedLocalesString.split("\\s+"));
        return voiceInputSupportedLocales.contains(locale);
    }

    public void loadSettings(SharedPreferences prefs) {
        if (USE_LEGACY_LANGUAGE_SWITCHER) {
            mLanguageSwitcher.loadLocales(prefs);
        }
    }

    public void toggleLanguage(boolean reset, boolean next) {
        if (reset) {
            mLanguageSwitcher.reset();
        } else {
            if (next) {
                mLanguageSwitcher.next();
            } else {
                mLanguageSwitcher.prev();
            }
        }
        mLanguageSwitcher.persist();
    }

    private void initLanguageSwitcher(LatinIME service) {
        final Configuration conf = service.getResources().getConfiguration();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(service);
        mLanguageSwitcher = new LanguageSwitcher(service);
        mLanguageSwitcher.loadLocales(prefs);
        mLanguageSwitcher.setSystemLocale(conf.locale);
    }

    // TODO: remove this function when the refactor for LanguageSwitcher will be finished
    public LanguageSwitcher getLanguageSwitcher() {
        return mLanguageSwitcher;
    }
}