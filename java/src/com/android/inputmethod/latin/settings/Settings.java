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

package com.android.inputmethod.latin.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.util.Log;

import com.android.inputmethod.latin.AudioAndHapticFeedbackManager;
import com.android.inputmethod.latin.InputAttributes;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.utils.AdditionalSubtypeUtils;
import com.android.inputmethod.latin.utils.LocaleUtils;
import com.android.inputmethod.latin.utils.ResourceUtils;
import com.android.inputmethod.latin.utils.RunInLocale;
import com.android.inputmethod.latin.utils.StringUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

public final class Settings implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = Settings.class.getSimpleName();
    // In the same order as xml/prefs.xml
    public static final String PREF_GENERAL_SETTINGS = "general_settings";
    public static final String PREF_AUTO_CAP = "auto_cap";
    public static final String PREF_VIBRATE_ON = "vibrate_on";
    public static final String PREF_SOUND_ON = "sound_on";
    public static final String PREF_POPUP_ON = "popup_on";
    // PREF_VOICE_MODE_OBSOLETE is obsolete. Use PREF_VOICE_INPUT_KEY instead.
    public static final String PREF_VOICE_MODE_OBSOLETE = "voice_mode";
    public static final String PREF_VOICE_INPUT_KEY = "pref_voice_input_key";
    public static final String PREF_CORRECTION_SETTINGS = "correction_settings";
    public static final String PREF_EDIT_PERSONAL_DICTIONARY = "edit_personal_dictionary";
    public static final String PREF_CONFIGURE_DICTIONARIES_KEY = "configure_dictionaries_key";
    public static final String PREF_AUTO_CORRECTION_THRESHOLD = "auto_correction_threshold";
    public static final String PREF_SHOW_SUGGESTIONS_SETTING = "show_suggestions_setting";
    public static final String PREF_MISC_SETTINGS = "misc_settings";
    public static final String PREF_LAST_USER_DICTIONARY_WRITE_TIME =
            "last_user_dictionary_write_time";
    public static final String PREF_ADVANCED_SETTINGS = "pref_advanced_settings";
    public static final String PREF_KEY_USE_CONTACTS_DICT = "pref_key_use_contacts_dict";
    public static final String PREF_KEY_USE_DOUBLE_SPACE_PERIOD =
            "pref_key_use_double_space_period";
    public static final String PREF_BLOCK_POTENTIALLY_OFFENSIVE =
            "pref_key_block_potentially_offensive";
    public static final String PREF_SHOW_LANGUAGE_SWITCH_KEY =
            "pref_show_language_switch_key";
    public static final String PREF_INCLUDE_OTHER_IMES_IN_LANGUAGE_SWITCH_LIST =
            "pref_include_other_imes_in_language_switch_list";
    public static final String PREF_CUSTOM_INPUT_STYLES = "custom_input_styles";
    public static final String PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY =
            "pref_key_preview_popup_dismiss_delay";
    public static final String PREF_BIGRAM_PREDICTIONS = "next_word_prediction";
    public static final String PREF_GESTURE_SETTINGS = "gesture_typing_settings";
    public static final String PREF_GESTURE_INPUT = "gesture_input";
    public static final String PREF_SLIDING_KEY_INPUT_PREVIEW = "pref_sliding_key_input_preview";
    public static final String PREF_KEY_LONGPRESS_TIMEOUT = "pref_key_longpress_timeout";
    public static final String PREF_VIBRATION_DURATION_SETTINGS =
            "pref_vibration_duration_settings";
    public static final String PREF_KEYPRESS_SOUND_VOLUME =
            "pref_keypress_sound_volume";
    public static final String PREF_GESTURE_PREVIEW_TRAIL = "pref_gesture_preview_trail";
    public static final String PREF_GESTURE_FLOATING_PREVIEW_TEXT =
            "pref_gesture_floating_preview_text";
    public static final String PREF_SHOW_SETUP_WIZARD_ICON = "pref_show_setup_wizard_icon";
    public static final String PREF_PHRASE_GESTURE_ENABLED = "pref_gesture_space_aware";

    public static final String PREF_INPUT_LANGUAGE = "input_language";
    public static final String PREF_SELECTED_LANGUAGES = "selected_languages";
    public static final String PREF_DEBUG_SETTINGS = "debug_settings";
    public static final String PREF_KEY_IS_INTERNAL = "pref_key_is_internal";

    // This preference key is deprecated. Use {@link #PREF_SHOW_LANGUAGE_SWITCH_KEY} instead.
    // This is being used only for the backward compatibility.
    private static final String PREF_SUPPRESS_LANGUAGE_SWITCH_KEY =
            "pref_suppress_language_switch_key";

    private static final String PREF_LAST_USED_PERSONALIZATION_TOKEN =
            "pref_last_used_personalization_token";
    public static final String PREF_SEND_FEEDBACK = "send_feedback";
    public static final String PREF_ABOUT_KEYBOARD = "about_keyboard";

    // Emoji
    public static final String PREF_EMOJI_RECENT_KEYS = "emoji_recent_keys";
    public static final String PREF_EMOJI_CATEGORY_LAST_TYPED_ID = "emoji_category_last_typed_id";
    public static final String PREF_LAST_SHOWN_EMOJI_CATEGORY_ID = "last_shown_emoji_category_id";

    private Resources mRes;
    private SharedPreferences mPrefs;
    private SettingsValues mSettingsValues;
    private final ReentrantLock mSettingsValuesLock = new ReentrantLock();

    private static final Settings sInstance = new Settings();

    public static Settings getInstance() {
        return sInstance;
    }

    public static void init(final Context context) {
        sInstance.onCreate(context);
    }

    private Settings() {
        // Intentional empty constructor for singleton.
    }

    private void onCreate(final Context context) {
        mRes = context.getResources();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mPrefs.registerOnSharedPreferenceChangeListener(this);
    }

    public void onDestroy() {
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        mSettingsValuesLock.lock();
        try {
            if (mSettingsValues == null) {
                // TODO: Introduce a static function to register this class and ensure that
                // loadSettings must be called before "onSharedPreferenceChanged" is called.
                Log.w(TAG, "onSharedPreferenceChanged called before loadSettings.");
                return;
            }
            loadSettings(mSettingsValues.mLocale, mSettingsValues.mInputAttributes);
        } finally {
            mSettingsValuesLock.unlock();
        }
    }

    public void loadSettings(final Locale locale, final InputAttributes inputAttributes) {
        mSettingsValuesLock.lock();
        try {
            final SharedPreferences prefs = mPrefs;
            final RunInLocale<SettingsValues> job = new RunInLocale<SettingsValues>() {
                @Override
                protected SettingsValues job(final Resources res) {
                    return new SettingsValues(prefs, locale, res, inputAttributes);
                }
            };
            mSettingsValues = job.runInLocale(mRes, locale);
        } finally {
            mSettingsValuesLock.unlock();
        }
    }

    // TODO: Remove this method and add proxy method to SettingsValues.
    public SettingsValues getCurrent() {
        return mSettingsValues;
    }

    public boolean isInternal() {
        return mSettingsValues.mIsInternal;
    }

    public String getWordSeparators() {
        return mSettingsValues.mWordSeparators;
    }

    public boolean isWordSeparator(final int code) {
        return mSettingsValues.isWordSeparator(code);
    }

    public boolean getBlockPotentiallyOffensive() {
        return mSettingsValues.mBlockPotentiallyOffensive;
    }

    // Accessed from the settings interface, hence public
    public static boolean readKeypressSoundEnabled(final SharedPreferences prefs,
            final Resources res) {
        return prefs.getBoolean(Settings.PREF_SOUND_ON,
                res.getBoolean(R.bool.config_default_sound_enabled));
    }

    public static boolean readVibrationEnabled(final SharedPreferences prefs,
            final Resources res) {
        final boolean hasVibrator = AudioAndHapticFeedbackManager.getInstance().hasVibrator();
        return hasVibrator && prefs.getBoolean(PREF_VIBRATE_ON,
                res.getBoolean(R.bool.config_default_vibration_enabled));
    }

    public static boolean readAutoCorrectEnabled(final String currentAutoCorrectionSetting,
            final Resources res) {
        final String autoCorrectionOff = res.getString(
                R.string.auto_correction_threshold_mode_index_off);
        return !currentAutoCorrectionSetting.equals(autoCorrectionOff);
    }

    public static boolean readBlockPotentiallyOffensive(final SharedPreferences prefs,
            final Resources res) {
        return prefs.getBoolean(Settings.PREF_BLOCK_POTENTIALLY_OFFENSIVE,
                res.getBoolean(R.bool.config_block_potentially_offensive));
    }

    public static boolean readFromBuildConfigIfGestureInputEnabled(final Resources res) {
        return res.getBoolean(R.bool.config_gesture_input_enabled_by_build_config);
    }

    public static boolean readGestureInputEnabled(final SharedPreferences prefs,
            final Resources res) {
        return readFromBuildConfigIfGestureInputEnabled(res)
                && prefs.getBoolean(Settings.PREF_GESTURE_INPUT, true);
    }

    public static boolean readPhraseGestureEnabled(final SharedPreferences prefs,
            final Resources res) {
        return prefs.getBoolean(Settings.PREF_PHRASE_GESTURE_ENABLED,
                res.getBoolean(R.bool.config_default_phrase_gesture_enabled));
    }

    public static boolean readFromBuildConfigIfToShowKeyPreviewPopupSettingsOption(
            final Resources res) {
        return res.getBoolean(R.bool.config_enable_show_option_of_key_preview_popup);
    }

    public static boolean readKeyPreviewPopupEnabled(final SharedPreferences prefs,
            final Resources res) {
        final boolean defaultKeyPreviewPopup = res.getBoolean(
                R.bool.config_default_key_preview_popup);
        if (!readFromBuildConfigIfToShowKeyPreviewPopupSettingsOption(res)) {
            return defaultKeyPreviewPopup;
        }
        return prefs.getBoolean(PREF_POPUP_ON, defaultKeyPreviewPopup);
    }

    public static int readKeyPreviewPopupDismissDelay(final SharedPreferences prefs,
            final Resources res) {
        return Integer.parseInt(prefs.getString(PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY,
                Integer.toString(res.getInteger(
                        R.integer.config_key_preview_linger_timeout))));
    }

    public static boolean readShowsLanguageSwitchKey(final SharedPreferences prefs) {
        if (prefs.contains(PREF_SUPPRESS_LANGUAGE_SWITCH_KEY)) {
            final boolean suppressLanguageSwitchKey = prefs.getBoolean(
                    PREF_SUPPRESS_LANGUAGE_SWITCH_KEY, false);
            final SharedPreferences.Editor editor = prefs.edit();
            editor.remove(PREF_SUPPRESS_LANGUAGE_SWITCH_KEY);
            editor.putBoolean(PREF_SHOW_LANGUAGE_SWITCH_KEY, !suppressLanguageSwitchKey);
            editor.apply();
        }
        return prefs.getBoolean(PREF_SHOW_LANGUAGE_SWITCH_KEY, true);
    }

    public static String readPrefAdditionalSubtypes(final SharedPreferences prefs,
            final Resources res) {
        final String predefinedPrefSubtypes = AdditionalSubtypeUtils.createPrefSubtypes(
                res.getStringArray(R.array.predefined_subtypes));
        return prefs.getString(PREF_CUSTOM_INPUT_STYLES, predefinedPrefSubtypes);
    }

    public static void writePrefAdditionalSubtypes(final SharedPreferences prefs,
            final String prefSubtypes) {
        prefs.edit().putString(Settings.PREF_CUSTOM_INPUT_STYLES, prefSubtypes).apply();
    }

    public static float readKeypressSoundVolume(final SharedPreferences prefs,
            final Resources res) {
        final float volume = prefs.getFloat(PREF_KEYPRESS_SOUND_VOLUME, -1.0f);
        return (volume >= 0) ? volume : readDefaultKeypressSoundVolume(res);
    }

    public static float readDefaultKeypressSoundVolume(final Resources res) {
        return Float.parseFloat(
                ResourceUtils.getDeviceOverrideValue(res, R.array.keypress_volumes));
    }

    public static int readKeyLongpressTimeout(final SharedPreferences prefs,
            final Resources res) {
        final int ms = prefs.getInt(PREF_KEY_LONGPRESS_TIMEOUT, -1);
        return (ms >= 0) ? ms : readDefaultKeyLongpressTimeout(res);
    }

    public static int readDefaultKeyLongpressTimeout(final Resources res) {
        return res.getInteger(R.integer.config_default_longpress_key_timeout);
    }

    public static int readKeypressVibrationDuration(final SharedPreferences prefs,
            final Resources res) {
        final int ms = prefs.getInt(PREF_VIBRATION_DURATION_SETTINGS, -1);
        return (ms >= 0) ? ms : readDefaultKeypressVibrationDuration(res);
    }

    public static int readDefaultKeypressVibrationDuration(final Resources res) {
        return Integer.parseInt(
                ResourceUtils.getDeviceOverrideValue(res, R.array.keypress_vibration_durations));
    }

    public static boolean readUsabilityStudyMode(final SharedPreferences prefs) {
        return prefs.getBoolean(DebugSettings.PREF_USABILITY_STUDY_MODE, true);
    }

    public static long readLastUserHistoryWriteTime(final SharedPreferences prefs,
            final String locale) {
        final String str = prefs.getString(PREF_LAST_USER_DICTIONARY_WRITE_TIME, "");
        final HashMap<String, Long> map = LocaleUtils.localeAndTimeStrToHashMap(str);
        if (map.containsKey(locale)) {
            return map.get(locale);
        }
        return 0;
    }

    public static void writeLastUserHistoryWriteTime(final SharedPreferences prefs,
            final String locale) {
        final String oldStr = prefs.getString(PREF_LAST_USER_DICTIONARY_WRITE_TIME, "");
        final HashMap<String, Long> map = LocaleUtils.localeAndTimeStrToHashMap(oldStr);
        map.put(locale, System.currentTimeMillis());
        final String newStr = LocaleUtils.localeAndTimeHashMapToStr(map);
        prefs.edit().putString(PREF_LAST_USER_DICTIONARY_WRITE_TIME, newStr).apply();
    }

    public static boolean readUseFullscreenMode(final Resources res) {
        return res.getBoolean(R.bool.config_use_fullscreen_mode);
    }

    public static boolean readShowSetupWizardIcon(final SharedPreferences prefs,
            final Context context) {
        final boolean enableSetupWizardByConfig = context.getResources().getBoolean(
                R.bool.config_setup_wizard_available);
        if (!enableSetupWizardByConfig) {
            return false;
        }
        if (!prefs.contains(Settings.PREF_SHOW_SETUP_WIZARD_ICON)) {
            final ApplicationInfo appInfo = context.getApplicationInfo();
            final boolean isApplicationInSystemImage =
                    (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            // Default value
            return !isApplicationInSystemImage;
        }
        return prefs.getBoolean(Settings.PREF_SHOW_SETUP_WIZARD_ICON, false);
    }

    public static boolean isInternal(final SharedPreferences prefs) {
        return prefs.getBoolean(Settings.PREF_KEY_IS_INTERNAL, false);
    }

    public static boolean readUseOnlyPersonalizationDictionaryForDebug(
            final SharedPreferences prefs) {
        return prefs.getBoolean(
                DebugSettings.PREF_USE_ONLY_PERSONALIZATION_DICTIONARY_FOR_DEBUG, false);
    }

    public static boolean readBoostPersonalizationDictionaryForDebug(
            final SharedPreferences prefs) {
        return prefs.getBoolean(
                DebugSettings.PREF_BOOST_PERSONALIZATION_DICTIONARY_FOR_DEBUG, false);
    }

    public void writeLastUsedPersonalizationToken(byte[] token) {
        final String tokenStr = StringUtils.byteArrayToHexString(token);
        mPrefs.edit().putString(PREF_LAST_USED_PERSONALIZATION_TOKEN, tokenStr).apply();
    }

    public byte[] readLastUsedPersonalizationToken() {
        final String tokenStr = mPrefs.getString(PREF_LAST_USED_PERSONALIZATION_TOKEN, null);
        return StringUtils.hexStringToByteArray(tokenStr);
    }

    public static void writeEmojiRecentKeys(final SharedPreferences prefs, String str) {
        prefs.edit().putString(PREF_EMOJI_RECENT_KEYS, str).apply();
    }

    public static String readEmojiRecentKeys(final SharedPreferences prefs) {
        return prefs.getString(PREF_EMOJI_RECENT_KEYS, "");
    }

    public static void writeLastTypedEmojiCategoryPageId(
            final SharedPreferences prefs, final int categoryId, final int categoryPageId) {
        final String key = PREF_EMOJI_CATEGORY_LAST_TYPED_ID + categoryId;
        prefs.edit().putInt(key, categoryPageId).apply();
    }

    public static int readLastTypedEmojiCategoryPageId(
            final SharedPreferences prefs, final int categoryId) {
        final String key = PREF_EMOJI_CATEGORY_LAST_TYPED_ID + categoryId;
        return prefs.getInt(key, 0);
    }

    public static void writeLastShownEmojiCategoryId(
            final SharedPreferences prefs, final int categoryId) {
        prefs.edit().putInt(PREF_LAST_SHOWN_EMOJI_CATEGORY_ID, categoryId).apply();
    }

    public static int readLastShownEmojiCategoryId(
            final SharedPreferences prefs, final int defValue) {
        return prefs.getInt(PREF_LAST_SHOWN_EMOJI_CATEGORY_ID, defValue);
    }
}
