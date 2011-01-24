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

import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.voice.SettingsUtil;
import com.android.inputmethod.voice.VoiceIMEConnector;
import com.android.inputmethod.voice.VoiceInput;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SubtypeSwitcher {
    private static boolean DBG = LatinImeLogger.sDBG;
    private static final String TAG = "SubtypeSwitcher";

    private static final char LOCALE_SEPARATER = '_';
    private static final String KEYBOARD_MODE = "keyboard";
    private static final String VOICE_MODE = "voice";
    private final TextUtils.SimpleStringSplitter mLocaleSplitter =
            new TextUtils.SimpleStringSplitter(LOCALE_SEPARATER);

    private static final SubtypeSwitcher sInstance = new SubtypeSwitcher();
    private /* final */ LatinIME mService;
    private /* final */ SharedPreferences mPrefs;
    private /* final */ InputMethodManager mImm;
    private /* final */ Resources mResources;
    private final ArrayList<InputMethodSubtype> mEnabledKeyboardSubtypesOfCurrentInputMethod =
            new ArrayList<InputMethodSubtype>();
    private final ArrayList<String> mEnabledLanguagesOfCurrentInputMethod = new ArrayList<String>();

    private boolean mConfigUseSpacebarLanguageSwitcher;

    /*-----------------------------------------------------------*/
    // Variants which should be changed only by reload functions.
    private boolean mNeedsToDisplayLanguage;
    private boolean mIsSystemLanguageSameAsInputLanguage;
    private InputMethodInfo mShortcutInfo;
    private InputMethodSubtype mShortcutSubtype;
    private List<InputMethodSubtype> mAllEnabledSubtypesOfCurrentInputMethod;
    private Locale mSystemLocale;
    private Locale mInputLocale;
    private String mInputLocaleStr;
    private String mMode;
    private VoiceInput mVoiceInput;
    /*-----------------------------------------------------------*/

    public static SubtypeSwitcher getInstance() {
        return sInstance;
    }

    public static void init(LatinIME service, SharedPreferences prefs) {
        sInstance.mPrefs = prefs;
        sInstance.resetParams(service);
        sInstance.updateAllParameters();

        SubtypeLocale.init(service);
    }

    private SubtypeSwitcher() {
        // Intentional empty constructor for singleton.
    }

    private void resetParams(LatinIME service) {
        mService = service;
        mResources = service.getResources();
        mImm = (InputMethodManager) service.getSystemService(Context.INPUT_METHOD_SERVICE);
        mEnabledKeyboardSubtypesOfCurrentInputMethod.clear();
        mEnabledLanguagesOfCurrentInputMethod.clear();
        mSystemLocale = null;
        mInputLocale = null;
        mInputLocaleStr = null;
        // Mode is initialized to KEYBOARD_MODE, in case that LatinIME can't obtain currentSubtype
        mMode = KEYBOARD_MODE;
        mAllEnabledSubtypesOfCurrentInputMethod = null;
        // TODO: Voice input should be created here
        mVoiceInput = null;
        mConfigUseSpacebarLanguageSwitcher = mResources.getBoolean(
                R.bool.config_use_spacebar_language_switcher);
        if (mConfigUseSpacebarLanguageSwitcher)
            initLanguageSwitcher(service);
    }

    // Update all parameters stored in SubtypeSwitcher.
    // Only configuration changed event is allowed to call this because this is heavy.
    private void updateAllParameters() {
        mSystemLocale = mResources.getConfiguration().locale;
        updateSubtype(mImm.getCurrentInputMethodSubtype());
        updateParametersOnStartInputView();
    }

    // Update parameters which are changed outside LatinIME. This parameters affect UI so they
    // should be updated every time onStartInputview.
    public void updateParametersOnStartInputView() {
        if (mConfigUseSpacebarLanguageSwitcher) {
            updateForSpacebarLanguageSwitch();
        } else {
            updateEnabledSubtypes();
        }
        updateShortcutIME();
    }

    // Reload enabledSubtypes from the framework.
    private void updateEnabledSubtypes() {
        boolean foundCurrentSubtypeBecameDisabled = true;
        mAllEnabledSubtypesOfCurrentInputMethod = mImm.getEnabledInputMethodSubtypeList(
                null, true);
        mEnabledLanguagesOfCurrentInputMethod.clear();
        mEnabledKeyboardSubtypesOfCurrentInputMethod.clear();
        for (InputMethodSubtype ims: mAllEnabledSubtypesOfCurrentInputMethod) {
            final String locale = ims.getLocale();
            final String mode = ims.getMode();
            mLocaleSplitter.setString(locale);
            if (mLocaleSplitter.hasNext()) {
                mEnabledLanguagesOfCurrentInputMethod.add(mLocaleSplitter.next());
            }
            if (locale.equals(mInputLocaleStr) && mode.equals(mMode)) {
                foundCurrentSubtypeBecameDisabled = false;
            }
            if (KEYBOARD_MODE.equals(ims.getMode())) {
                mEnabledKeyboardSubtypesOfCurrentInputMethod.add(ims);
            }
        }
        mNeedsToDisplayLanguage = !(getEnabledKeyboardLocaleCount() <= 1
                && mIsSystemLanguageSameAsInputLanguage);
        if (foundCurrentSubtypeBecameDisabled) {
            if (DBG) {
                Log.w(TAG, "Current subtype: " + mInputLocaleStr + ", " + mMode);
                Log.w(TAG, "Last subtype was disabled. Update to the current one.");
            }
            updateSubtype(mImm.getCurrentInputMethodSubtype());
        }
    }

    private void updateShortcutIME() {
        if (DBG) {
            Log.d(TAG, "Update shortcut IME from : "
                    + (mShortcutInfo == null ? "<null>" : mShortcutInfo.getId()) + ", "
                    + (mShortcutSubtype == null ? "<null>" : (mShortcutSubtype.getLocale()
                            + ", " + mShortcutSubtype.getMode())));
        }
        // TODO: Update an icon for shortcut IME
        Map<InputMethodInfo, List<InputMethodSubtype>> shortcuts =
                mImm.getShortcutInputMethodsAndSubtypes();
        for (InputMethodInfo imi: shortcuts.keySet()) {
            List<InputMethodSubtype> subtypes = shortcuts.get(imi);
            // TODO: Returns the first found IMI for now. Should handle all shortcuts as
            // appropriate.
            mShortcutInfo = imi;
            // TODO: Pick up the first found subtype for now. Should handle all subtypes
            // as appropriate.
            mShortcutSubtype = subtypes.size() > 0 ? subtypes.get(0) : null;
            break;
        }
        if (DBG) {
            Log.d(TAG, "Update shortcut IME to : "
                    + (mShortcutInfo == null ? "<null>" : mShortcutInfo.getId()) + ", "
                    + (mShortcutSubtype == null ? "<null>" : (mShortcutSubtype.getLocale()
                            + ", " + mShortcutSubtype.getMode())));
        }
    }

    // Update the current subtype. LatinIME.onCurrentInputMethodSubtypeChanged calls this function.
    public void updateSubtype(InputMethodSubtype newSubtype) {
        final String newLocale;
        final String newMode;
        if (newSubtype == null) {
            // Normally, newSubtype shouldn't be null. But just in case newSubtype was null,
            // fallback to the default locale and mode.
            Log.w(TAG, "Couldn't get the current subtype.");
            newLocale = "en_US";
            newMode = KEYBOARD_MODE;
        } else {
            newLocale = newSubtype.getLocale();
            newMode = newSubtype.getMode();
        }
        if (DBG) {
            Log.w(TAG, "Update subtype to:" + newLocale + "," + newMode
                    + ", from: " + mInputLocaleStr + ", " + mMode);
        }
        boolean languageChanged = false;
        if (!newLocale.equals(mInputLocaleStr)) {
            if (mInputLocaleStr != null) {
                languageChanged = true;
            }
            updateInputLocale(newLocale);
        }
        boolean modeChanged = false;
        String oldMode = mMode;
        if (!newMode.equals(mMode)) {
            if (mMode != null) {
                modeChanged = true;
            }
            mMode = newMode;
        }
        if (isKeyboardMode()) {
            if (modeChanged) {
                if (VOICE_MODE.equals(oldMode) && mVoiceInput != null) {
                    mVoiceInput.cancel();
                }
            }
            if (modeChanged || languageChanged) {
                mService.onRefreshKeyboard();
            }
        } else if (isVoiceMode()) {
            // If needsToShowWarningDialog is true, voice input need to show warning before
            // show recognition view.
            if (languageChanged || modeChanged
                    || VoiceIMEConnector.getInstance().needsToShowWarningDialog()) {
                if (mVoiceInput != null) {
                    triggerVoiceIME();
                }
            }
        } else {
            Log.w(TAG, "Unknown subtype mode: " + mMode);
        }
    }

    // Update the current input locale from Locale string.
    private void updateInputLocale(String inputLocaleStr) {
        // example: inputLocaleStr = "en_US" "en" ""
        // "en_US" --> language: en  & country: US
        // "en" --> language: en
        // "" --> the system locale
        mLocaleSplitter.setString(inputLocaleStr);
        if (mLocaleSplitter.hasNext()) {
            String language = mLocaleSplitter.next();
            if (mLocaleSplitter.hasNext()) {
                mInputLocale = new Locale(language, mLocaleSplitter.next());
            } else {
                mInputLocale = new Locale(language);
            }
            mInputLocaleStr = inputLocaleStr;
        } else {
            mInputLocale = mSystemLocale;
            String country = mSystemLocale.getCountry();
            mInputLocaleStr = mSystemLocale.getLanguage()
                    + (TextUtils.isEmpty(country) ? "" : "_" + mSystemLocale.getLanguage());
        }
        mIsSystemLanguageSameAsInputLanguage = getSystemLocale().getLanguage().equalsIgnoreCase(
                getInputLocale().getLanguage());
        mNeedsToDisplayLanguage = !(getEnabledKeyboardLocaleCount() <= 1
                && mIsSystemLanguageSameAsInputLanguage);
    }

    ////////////////////////////
    // Shortcut IME functions //
    ////////////////////////////

    public void switchToShortcutIME() {
        IBinder token = mService.getWindow().getWindow().getAttributes().token;
        if (token == null || mShortcutInfo == null) {
            return;
        }
        mImm.setInputMethodAndSubtype(token, mShortcutInfo.getId(), mShortcutSubtype);
    }

    public Drawable getShortcutIcon() {
        return getSubtypeIcon(mShortcutInfo, mShortcutSubtype);
    }

    private Drawable getSubtypeIcon(InputMethodInfo imi, InputMethodSubtype subtype) {
        final PackageManager pm = mService.getPackageManager();
        if (imi != null) {
            final String imiPackageName = imi.getPackageName();
            if (DBG) {
                Log.d(TAG, "Update icons of IME: " + imiPackageName + ","
                        + subtype.getLocale() + "," + subtype.getMode());
            }
            if (subtype != null) {
                return pm.getDrawable(imiPackageName, subtype.getIconResId(),
                        imi.getServiceInfo().applicationInfo);
            } else if (imi.getSubtypeCount() > 0 && imi.getSubtypeAt(0) != null) {
                return pm.getDrawable(imiPackageName,
                        imi.getSubtypeAt(0).getIconResId(),
                        imi.getServiceInfo().applicationInfo);
            } else {
                try {
                    return pm.getApplicationInfo(imiPackageName, 0).loadIcon(pm);
                } catch (PackageManager.NameNotFoundException e) {
                    Log.w(TAG, "IME can't be found: " + imiPackageName);
                }
            }
        }
        return null;
    }

    //////////////////////////////////
    // Language Switching functions //
    //////////////////////////////////

    public int getEnabledKeyboardLocaleCount() {
        if (mConfigUseSpacebarLanguageSwitcher) {
            return mLanguageSwitcher.getLocaleCount();
        } else {
            return mEnabledKeyboardSubtypesOfCurrentInputMethod.size();
        }
    }

    public boolean useSpacebarLanguageSwitcher() {
        return mConfigUseSpacebarLanguageSwitcher;
    }

    public boolean needsToDisplayLanguage() {
        return mNeedsToDisplayLanguage;
    }

    public Locale getInputLocale() {
        if (mConfigUseSpacebarLanguageSwitcher) {
            return mLanguageSwitcher.getInputLocale();
        } else {
            return mInputLocale;
        }
    }

    public String getInputLocaleStr() {
        if (mConfigUseSpacebarLanguageSwitcher) {
            String inputLanguage = null;
            inputLanguage = mLanguageSwitcher.getInputLanguage();
            // Should return system locale if there is no Language available.
            if (inputLanguage == null) {
                inputLanguage = getSystemLocale().getLanguage();
            }
            return inputLanguage;
        } else {
            return mInputLocaleStr;
        }
    }

    public String[] getEnabledLanguages() {
        if (mConfigUseSpacebarLanguageSwitcher) {
            return mLanguageSwitcher.getEnabledLanguages();
        } else {
            return mEnabledLanguagesOfCurrentInputMethod.toArray(
                    new String[mEnabledLanguagesOfCurrentInputMethod.size()]);
        }
    }

    public Locale getSystemLocale() {
        if (mConfigUseSpacebarLanguageSwitcher) {
            return mLanguageSwitcher.getSystemLocale();
        } else {
            return mSystemLocale;
        }
    }

    public boolean isSystemLanguageSameAsInputLanguage() {
        if (mConfigUseSpacebarLanguageSwitcher) {
            return getSystemLocale().getLanguage().equalsIgnoreCase(
                    getInputLocaleStr().substring(0, 2));
        } else {
            return mIsSystemLanguageSameAsInputLanguage;
        }
    }

    public void onConfigurationChanged(Configuration conf) {
        final Locale systemLocale = conf.locale;
        // If system configuration was changed, update all parameters.
        if (!TextUtils.equals(systemLocale.toString(), mSystemLocale.toString())) {
            if (mConfigUseSpacebarLanguageSwitcher) {
                // If the system locale changes and is different from the saved
                // locale (mSystemLocale), then reload the input locale list from the
                // latin ime settings (shared prefs) and reset the input locale
                // to the first one.
                mLanguageSwitcher.loadLocales(mPrefs);
                mLanguageSwitcher.setSystemLocale(systemLocale);
            } else {
                updateAllParameters();
            }
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (mConfigUseSpacebarLanguageSwitcher) {
            if (Settings.PREF_SELECTED_LANGUAGES.equals(key)) {
                mLanguageSwitcher.loadLocales(sharedPreferences);
            }
        }
    }

    /**
     * Change system locale for this application
     * @param newLocale
     * @return oldLocale
     */
    public Locale changeSystemLocale(Locale newLocale) {
        Configuration conf = mResources.getConfiguration();
        Locale oldLocale = conf.locale;
        conf.locale = newLocale;
        mResources.updateConfiguration(conf, mResources.getDisplayMetrics());
        return oldLocale;
    }

    public boolean isKeyboardMode() {
        return KEYBOARD_MODE.equals(mMode);
    }


    ///////////////////////////
    // Voice Input functions //
    ///////////////////////////

    public boolean setVoiceInput(VoiceInput vi) {
        if (mVoiceInput == null && vi != null) {
            mVoiceInput = vi;
            if (isVoiceMode()) {
                if (DBG) {
                    Log.d(TAG, "Set and call voice input.: " + getInputLocaleStr());
                }
                triggerVoiceIME();
                return true;
            }
        }
        return false;
    }

    public boolean isVoiceMode() {
        return VOICE_MODE.equals(mMode);
    }

    private void triggerVoiceIME() {
        if (!mService.isInputViewShown()) return;
        VoiceIMEConnector.getInstance().startListening(false,
                KeyboardSwitcher.getInstance().getInputView().getWindowToken());
    }

    //////////////////////////////////////
    // Spacebar Language Switch support //
    //////////////////////////////////////

    private LanguageSwitcher mLanguageSwitcher;

    public static String getFullDisplayName(Locale locale, boolean returnsNameInThisLocale) {
        if (returnsNameInThisLocale) {
            return toTitleCase(SubtypeLocale.getFullDisplayName(locale));
        } else {
            return toTitleCase(locale.getDisplayName());
        }
    }

    public static String getDisplayLanguage(Locale locale) {
        return toTitleCase(locale.getDisplayLanguage(locale));
    }

    public static String getShortDisplayLanguage(Locale locale) {
        return toTitleCase(locale.getLanguage());
    }

    private static String toTitleCase(String s) {
        if (s.length() == 0) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void updateForSpacebarLanguageSwitch() {
        // We need to update mNeedsToDisplayLanguage in onStartInputView because
        // getEnabledKeyboardLocaleCount could have been changed.
        mNeedsToDisplayLanguage = !(getEnabledKeyboardLocaleCount() <= 1
                && getSystemLocale().getLanguage().equalsIgnoreCase(
                        getInputLocale().getLanguage()));
    }

    public String getInputLanguageName() {
        return getDisplayLanguage(getInputLocale());
    }

    public String getNextInputLanguageName() {
        if (mConfigUseSpacebarLanguageSwitcher) {
            return getDisplayLanguage(mLanguageSwitcher.getNextInputLocale());
        } else {
            return "";
        }
    }

    public String getPreviousInputLanguageName() {
        if (mConfigUseSpacebarLanguageSwitcher) {
            return getDisplayLanguage(mLanguageSwitcher.getPrevInputLocale());
        } else {
            return "";
        }
    }

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

    public void loadSettings() {
        if (mConfigUseSpacebarLanguageSwitcher) {
            mLanguageSwitcher.loadLocales(mPrefs);
        }
    }

    public void toggleLanguage(boolean reset, boolean next) {
        if (mConfigUseSpacebarLanguageSwitcher) {
            if (reset) {
                mLanguageSwitcher.reset();
            } else {
                if (next) {
                    mLanguageSwitcher.next();
                } else {
                    mLanguageSwitcher.prev();
                }
            }
            mLanguageSwitcher.persist(mPrefs);
        }
    }

    private void initLanguageSwitcher(LatinIME service) {
        final Configuration conf = service.getResources().getConfiguration();
        mLanguageSwitcher = new LanguageSwitcher(service);
        mLanguageSwitcher.loadLocales(mPrefs);
        mLanguageSwitcher.setSystemLocale(conf.locale);
    }
}
