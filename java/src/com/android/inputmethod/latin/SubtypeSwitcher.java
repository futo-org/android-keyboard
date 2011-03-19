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

import com.android.inputmethod.compat.InputMethodManagerCompatWrapper;
import com.android.inputmethod.compat.InputMethodSubtypeCompatWrapper;
import com.android.inputmethod.deprecated.VoiceConnector;
import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.keyboard.LatinKeyboard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SubtypeSwitcher {
    private static boolean DBG = LatinImeLogger.sDBG;
    private static final String TAG = SubtypeSwitcher.class.getSimpleName();

    private static final char LOCALE_SEPARATER = '_';
    private static final String KEYBOARD_MODE = "keyboard";
    private static final String VOICE_MODE = "voice";
    private static final String SUBTYPE_EXTRAVALUE_REQUIRE_NETWORK_CONNECTIVITY =
            "requireNetworkConnectivity";
    private final TextUtils.SimpleStringSplitter mLocaleSplitter =
            new TextUtils.SimpleStringSplitter(LOCALE_SEPARATER);

    private static final SubtypeSwitcher sInstance = new SubtypeSwitcher();
    private /* final */ LatinIME mService;
    private /* final */ SharedPreferences mPrefs;
    private /* final */ InputMethodManagerCompatWrapper mImm;
    private /* final */ Resources mResources;
    private /* final */ ConnectivityManager mConnectivityManager;
    private /* final */ boolean mConfigUseSpacebarLanguageSwitcher;
    private final ArrayList<InputMethodSubtypeCompatWrapper>
            mEnabledKeyboardSubtypesOfCurrentInputMethod =
                    new ArrayList<InputMethodSubtypeCompatWrapper>();
    private final ArrayList<String> mEnabledLanguagesOfCurrentInputMethod = new ArrayList<String>();

    /*-----------------------------------------------------------*/
    // Variants which should be changed only by reload functions.
    private boolean mNeedsToDisplayLanguage;
    private boolean mIsSystemLanguageSameAsInputLanguage;
    private InputMethodInfo mShortcutInputMethodInfo;
    private InputMethodSubtypeCompatWrapper mShortcutSubtype;
    private List<InputMethodSubtypeCompatWrapper> mAllEnabledSubtypesOfCurrentInputMethod;
    private InputMethodSubtypeCompatWrapper mCurrentSubtype;
    private Locale mSystemLocale;
    private Locale mInputLocale;
    private String mInputLocaleStr;
    private VoiceConnector.VoiceInputConnector mVoiceInputConnector;
    /*-----------------------------------------------------------*/

    private boolean mIsNetworkConnected;

    public static SubtypeSwitcher getInstance() {
        return sInstance;
    }

    public static void init(LatinIME service, SharedPreferences prefs) {
        sInstance.initialize(service, prefs);
        sInstance.updateAllParameters();

        SubtypeLocale.init(service);
    }

    private SubtypeSwitcher() {
        // Intentional empty constructor for singleton.
    }

    private void initialize(LatinIME service, SharedPreferences prefs) {
        mService = service;
        mPrefs = prefs;
        mResources = service.getResources();
        mImm = InputMethodManagerCompatWrapper.getInstance(service);
        mConnectivityManager = (ConnectivityManager) service.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        mEnabledKeyboardSubtypesOfCurrentInputMethod.clear();
        mEnabledLanguagesOfCurrentInputMethod.clear();
        mSystemLocale = null;
        mInputLocale = null;
        mInputLocaleStr = null;
        mCurrentSubtype = null;
        mAllEnabledSubtypesOfCurrentInputMethod = null;
        // TODO: Voice input should be created here
        mVoiceInputConnector = null;
        mConfigUseSpacebarLanguageSwitcher = mResources.getBoolean(
                R.bool.config_use_spacebar_language_switcher);
        if (mConfigUseSpacebarLanguageSwitcher)
            initLanguageSwitcher(service);

        final NetworkInfo info = mConnectivityManager.getActiveNetworkInfo();
        mIsNetworkConnected = (info != null && info.isConnected());
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
        final String currentMode = getCurrentSubtypeMode();
        boolean foundCurrentSubtypeBecameDisabled = true;
        mAllEnabledSubtypesOfCurrentInputMethod = mImm.getEnabledInputMethodSubtypeList(
                null, true);
        mEnabledLanguagesOfCurrentInputMethod.clear();
        mEnabledKeyboardSubtypesOfCurrentInputMethod.clear();
        for (InputMethodSubtypeCompatWrapper ims: mAllEnabledSubtypesOfCurrentInputMethod) {
            final String locale = ims.getLocale();
            final String mode = ims.getMode();
            mLocaleSplitter.setString(locale);
            if (mLocaleSplitter.hasNext()) {
                mEnabledLanguagesOfCurrentInputMethod.add(mLocaleSplitter.next());
            }
            if (locale.equals(mInputLocaleStr) && mode.equals(currentMode)) {
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
                Log.w(TAG, "Current subtype: " + mInputLocaleStr + ", " + currentMode);
                Log.w(TAG, "Last subtype was disabled. Update to the current one.");
            }
            updateSubtype(mImm.getCurrentInputMethodSubtype());
        }
    }

    private void updateShortcutIME() {
        if (DBG) {
            Log.d(TAG, "Update shortcut IME from : "
                    + (mShortcutInputMethodInfo == null
                            ? "<null>" : mShortcutInputMethodInfo.getId()) + ", "
                    + (mShortcutSubtype == null ? "<null>" : (mShortcutSubtype.getLocale()
                            + ", " + mShortcutSubtype.getMode())));
        }
        // TODO: Update an icon for shortcut IME
        Map<InputMethodInfo, List<InputMethodSubtypeCompatWrapper>> shortcuts =
                mImm.getShortcutInputMethodsAndSubtypes();
        for (InputMethodInfo imi: shortcuts.keySet()) {
            List<InputMethodSubtypeCompatWrapper> subtypes = shortcuts.get(imi);
            // TODO: Returns the first found IMI for now. Should handle all shortcuts as
            // appropriate.
            mShortcutInputMethodInfo = imi;
            // TODO: Pick up the first found subtype for now. Should handle all subtypes
            // as appropriate.
            mShortcutSubtype = subtypes.size() > 0 ? subtypes.get(0) : null;
            break;
        }
        if (DBG) {
            Log.d(TAG, "Update shortcut IME to : "
                    + (mShortcutInputMethodInfo == null
                            ? "<null>" : mShortcutInputMethodInfo.getId()) + ", "
                    + (mShortcutSubtype == null ? "<null>" : (mShortcutSubtype.getLocale()
                            + ", " + mShortcutSubtype.getMode())));
        }
    }

    // Update the current subtype. LatinIME.onCurrentInputMethodSubtypeChanged calls this function.
    public void updateSubtype(InputMethodSubtypeCompatWrapper newSubtype) {
        final String newLocale;
        final String newMode;
        final String oldMode = getCurrentSubtypeMode();
        if (newSubtype == null) {
            // Normally, newSubtype shouldn't be null. But just in case newSubtype was null,
            // fallback to the default locale.
            Log.w(TAG, "Couldn't get the current subtype.");
            newLocale = "en_US";
            newMode = KEYBOARD_MODE;
        } else {
            newLocale = newSubtype.getLocale();
            newMode = newSubtype.getMode();
        }
        if (DBG) {
            Log.w(TAG, "Update subtype to:" + newLocale + "," + newMode
                    + ", from: " + mInputLocaleStr + ", " + oldMode);
        }
        boolean languageChanged = false;
        if (!newLocale.equals(mInputLocaleStr)) {
            if (mInputLocaleStr != null) {
                languageChanged = true;
            }
            updateInputLocale(newLocale);
        }
        boolean modeChanged = false;
        if (!newMode.equals(oldMode)) {
            if (oldMode != null) {
                modeChanged = true;
            }
        }
        mCurrentSubtype = newSubtype;

        // If the old mode is voice input, we need to reset or cancel its status.
        // We cancel its status when we change mode, while we reset otherwise.
        if (isKeyboardMode()) {
            if (modeChanged) {
                if (VOICE_MODE.equals(oldMode) && mVoiceInputConnector != null) {
                    mVoiceInputConnector.cancel();
                }
            }
            if (modeChanged || languageChanged) {
                updateShortcutIME();
                mService.onRefreshKeyboard();
            }
        } else if (isVoiceMode() && mVoiceInputConnector != null) {
            if (VOICE_MODE.equals(oldMode)) {
                mVoiceInputConnector.reset();
            }
            // If needsToShowWarningDialog is true, voice input need to show warning before
            // show recognition view.
            if (languageChanged || modeChanged
                    || VoiceConnector.getInstance().needsToShowWarningDialog()) {
                triggerVoiceIME();
            }
        } else {
            Log.w(TAG, "Unknown subtype mode: " + newMode);
            if (VOICE_MODE.equals(oldMode) && mVoiceInputConnector != null) {
                // We need to reset the voice input to release the resources and to reset its status
                // as it is not the current input mode.
                mVoiceInputConnector.reset();
            }
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
        final IBinder token = mService.getWindow().getWindow().getAttributes().token;
        if (token == null || mShortcutInputMethodInfo == null) {
            return;
        }
        final String imiId = mShortcutInputMethodInfo.getId();
        final InputMethodSubtypeCompatWrapper subtype = mShortcutSubtype;
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                mImm.setInputMethodAndSubtype(token, imiId, subtype);
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                // Calls in this method need to be done in the same thread as the thread which
                // called switchToShortcutIME().

                // Notify an event that the current subtype was changed. This event will be
                // handled if "onCurrentInputMethodSubtypeChanged" can't be implemented
                // when the API level is 10 or previous.
                mService.notifyOnCurrentInputMethodSubtypeChanged(subtype);
            }
        }.execute();
    }

    public Drawable getShortcutIcon() {
        return getSubtypeIcon(mShortcutInputMethodInfo, mShortcutSubtype);
    }

    private Drawable getSubtypeIcon(InputMethodInfo imi, InputMethodSubtypeCompatWrapper subtype) {
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

    private static boolean contains(String[] hay, String needle) {
        for (String element : hay) {
            if (element.equals(needle))
                return true;
        }
        return false;
    }

    public boolean isShortcutImeEnabled() {
        if (mShortcutInputMethodInfo == null)
            return false;
        if (mShortcutSubtype == null)
            return true;
        final boolean allowsImplicitlySelectedSubtypes = true;
        for (final InputMethodSubtypeCompatWrapper enabledSubtype :
                mImm.getEnabledInputMethodSubtypeList(
                        mShortcutInputMethodInfo, allowsImplicitlySelectedSubtypes)) {
            if (enabledSubtype.equals(mShortcutSubtype))
                return true;
        }
        return false;
    }

    public boolean isShortcutImeReady() {
        if (mShortcutInputMethodInfo == null)
            return false;
        if (mShortcutSubtype == null)
            return true;
        if (contains(mShortcutSubtype.getExtraValue().split(","),
                SUBTYPE_EXTRAVALUE_REQUIRE_NETWORK_CONNECTIVITY)) {
            return mIsNetworkConnected;
        }
        return true;
    }

    public void onNetworkStateChanged(Intent intent) {
        final boolean noConnection = intent.getBooleanExtra(
                ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
        mIsNetworkConnected = !noConnection;

        final KeyboardSwitcher switcher = KeyboardSwitcher.getInstance();
        final LatinKeyboard keyboard = switcher.getLatinKeyboard();
        if (keyboard != null) {
            keyboard.updateShortcutKey(isShortcutImeReady(), switcher.getInputView());
        }
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
            int enabledLanguageCount = mEnabledLanguagesOfCurrentInputMethod.size();
            // Workaround for explicitly specifying the voice language
            if (enabledLanguageCount == 1) {
                mEnabledLanguagesOfCurrentInputMethod.add(
                        mEnabledLanguagesOfCurrentInputMethod.get(0));
                ++enabledLanguageCount;
            }
            return mEnabledLanguagesOfCurrentInputMethod.toArray(
                    new String[enabledLanguageCount]);
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
        return KEYBOARD_MODE.equals(getCurrentSubtypeMode());
    }


    ///////////////////////////
    // Voice Input functions //
    ///////////////////////////

    public boolean setVoiceInputConnector(VoiceConnector.VoiceInputConnector vi) {
        if (mVoiceInputConnector == null && vi != null) {
            mVoiceInputConnector = vi;
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
        return null == mCurrentSubtype ? false : VOICE_MODE.equals(getCurrentSubtypeMode());
    }

    private void triggerVoiceIME() {
        if (!mService.isInputViewShown()) return;
        VoiceConnector.getInstance().startListening(false,
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

    /////////////////////////////
    // Other utility functions //
    /////////////////////////////

    public String getCurrentSubtypeExtraValue() {
        // If null, return what an empty ExtraValue would return : the empty string.
        return null != mCurrentSubtype ? mCurrentSubtype.getExtraValue() : "";
    }

    public boolean currentSubtypeContainsExtraValueKey(String key) {
        // If null, return what an empty ExtraValue would return : false.
        return null != mCurrentSubtype ? mCurrentSubtype.containsExtraValueKey(key) : false;
    }

    public String getCurrentSubtypeExtraValueOf(String key) {
        // If null, return what an empty ExtraValue would return : null.
        return null != mCurrentSubtype ? mCurrentSubtype.getExtraValueOf(key) : null;
    }

    public String getCurrentSubtypeMode() {
        return null != mCurrentSubtype ? mCurrentSubtype.getMode() : KEYBOARD_MODE;
    }


    public boolean isVoiceSupported(String locale) {
        // Get the current list of supported locales and check the current locale against that
        // list. We cache this value so as not to check it every time the user starts a voice
        // input. Because this method is called by onStartInputView, this should mean that as
        // long as the locale doesn't change while the user is keeping the IME open, the
        // value should never be stale.
        String supportedLocalesString = VoiceConnector.getSupportedLocalesString(
                mService.getContentResolver());
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
