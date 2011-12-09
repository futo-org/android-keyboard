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

package com.android.inputmethod.latin;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.compat.InputTypeCompatUtils;
import com.android.inputmethod.compat.VibratorCompatWrapper;

import java.util.Arrays;
import java.util.Locale;

public class SettingsValues {
    private static final String TAG = SettingsValues.class.getSimpleName();

    // From resources:
    public final int mDelayUpdateOldSuggestions;
    public final String mMagicSpaceStrippers;
    public final String mMagicSpaceSwappers;
    public final String mSuggestPuncs;
    public final SuggestedWords mSuggestPuncList;
    private final String mSymbolsExcludedFromWordSeparators;
    public final String mWordSeparators;
    public final CharSequence mHintToSaveText;

    // From preferences, in the same order as xml/prefs.xml:
    public final boolean mAutoCap;
    public final boolean mVibrateOn;
    public final boolean mSoundOn;
    public final boolean mKeyPreviewPopupOn;
    private final boolean mShowSettingsKey;
    // TODO: add a member for the raw "voice_mode" setting
    // TODO: add a member for the raw "auto_correction_threshold" setting
    // TODO: add a member for the raw "show_suggestions_setting" setting
    // TODO: add a member for the raw "usability_study_mode" setting
    // TODO: add a member for the raw "pref_key_preview_popup_dismiss_delay" setting
    public final boolean mUseContactsDict;
    // Suggestion: use bigrams to adjust scores of suggestions obtained from unigram dictionary
    public final boolean mBigramSuggestionEnabled;
    // Prediction: use bigrams to predict the next word when there is no input for it yet
    public final boolean mBigramPredictionEnabled;
    public final boolean mEnableSuggestionSpanInsertion;
    // TODO: add a member for the raw "pref_vibration_duration_settings" setting
    // TODO: add a member for the raw "pref_keypress_sound_volume" setting

    // Deduced settings
    public final int mKeypressVibrationDuration;
    public final float mFxVolume;
    public final int mKeyPreviewPopupDismissDelay;
    public final boolean mAutoCorrectEnabled;
    public final double mAutoCorrectionThreshold;
    private final boolean mVoiceKeyEnabled;
    private final boolean mVoiceKeyOnMain;

    public SettingsValues(final SharedPreferences prefs, final Context context,
            final String localeStr) {
        final Resources res = context.getResources();
        final Locale savedLocale;
        if (null != localeStr) {
            final Locale keyboardLocale = LocaleUtils.constructLocaleFromString(localeStr);
            savedLocale = LocaleUtils.setSystemLocale(res, keyboardLocale);
        } else {
            savedLocale = null;
        }

        // Get the resources
        mDelayUpdateOldSuggestions = res.getInteger(R.integer.config_delay_update_old_suggestions);
        mMagicSpaceStrippers = res.getString(R.string.magic_space_stripping_symbols);
        mMagicSpaceSwappers = res.getString(R.string.magic_space_swapping_symbols);
        mSuggestPuncs = res.getString(R.string.suggested_punctuations);
        // TODO: it would be nice not to recreate this each time we change the configuration
        mSuggestPuncList = createSuggestPuncList(mSuggestPuncs);
        mSymbolsExcludedFromWordSeparators =
                res.getString(R.string.symbols_excluded_from_word_separators);
        mWordSeparators = createWordSeparators(mMagicSpaceStrippers, mMagicSpaceSwappers,
                mSymbolsExcludedFromWordSeparators, res);
        mHintToSaveText = context.getText(R.string.hint_add_to_dictionary);

        // Get the settings preferences
        mAutoCap = prefs.getBoolean(Settings.PREF_AUTO_CAP, true);
        mVibrateOn = isVibrateOn(context, prefs, res);
        mSoundOn = prefs.getBoolean(Settings.PREF_SOUND_ON,
                res.getBoolean(R.bool.config_default_sound_enabled));
        mKeyPreviewPopupOn = isKeyPreviewPopupEnabled(prefs, res);
        mShowSettingsKey = isSettingsKeyShown(prefs, res);
        mUseContactsDict = prefs.getBoolean(Settings.PREF_KEY_USE_CONTACTS_DICT, true);
        mAutoCorrectEnabled = isAutoCorrectEnabled(prefs, res);
        mBigramSuggestionEnabled = mAutoCorrectEnabled
                && isBigramSuggestionEnabled(prefs, res, mAutoCorrectEnabled);
        mBigramPredictionEnabled = mBigramSuggestionEnabled
                && isBigramPredictionEnabled(prefs, res);
        mEnableSuggestionSpanInsertion =
            prefs.getBoolean(Settings.PREF_KEY_ENABLE_SPAN_INSERT, true);

        // Compute other readable settings
        mKeyPreviewPopupDismissDelay = getKeyPreviewPopupDismissDelay(prefs, res);
        mAutoCorrectionThreshold = getAutoCorrectionThreshold(prefs, res);
        final String voiceModeMain = res.getString(R.string.voice_mode_main);
        final String voiceModeOff = res.getString(R.string.voice_mode_off);
        final String voiceMode = prefs.getString(Settings.PREF_VOICE_MODE, voiceModeMain);
        mVoiceKeyEnabled = voiceMode != null && !voiceMode.equals(voiceModeOff);
        mVoiceKeyOnMain = voiceMode != null && voiceMode.equals(voiceModeMain);

        mFxVolume = getCurrentKeypressSoundVolume(prefs, res);
        mKeypressVibrationDuration = getCurrentVibrationDuration(prefs, res);

        LocaleUtils.setSystemLocale(res, savedLocale);
    }

    // Helper functions to create member values.
    private static SuggestedWords createSuggestPuncList(final String puncs) {
        SuggestedWords.Builder builder = new SuggestedWords.Builder();
        if (puncs != null) {
            for (int i = 0; i < puncs.length(); i++) {
                builder.addWord(puncs.subSequence(i, i + 1));
            }
        }
        return builder.setIsPunctuationSuggestions().build();
    }

    private static String createWordSeparators(final String magicSpaceStrippers,
            final String magicSpaceSwappers, final String symbolsExcludedFromWordSeparators,
            final Resources res) {
        String wordSeparators = magicSpaceStrippers + magicSpaceSwappers
                + res.getString(R.string.magic_space_promoting_symbols);
        for (int i = symbolsExcludedFromWordSeparators.length() - 1; i >= 0; --i) {
            wordSeparators = wordSeparators.replace(
                    symbolsExcludedFromWordSeparators.substring(i, i + 1), "");
        }
        return wordSeparators;
    }

    private static boolean isSettingsKeyShown(final SharedPreferences prefs, final Resources res) {
        final boolean defaultShowSettingsKey = res.getBoolean(
                R.bool.config_default_show_settings_key);
        return isShowSettingsKeyOptionEnabled(res)
                ? prefs.getBoolean(Settings.PREF_SHOW_SETTINGS_KEY, defaultShowSettingsKey)
                : defaultShowSettingsKey;
    }

    public static boolean isShowSettingsKeyOptionEnabled(final Resources resources) {
        // TODO: Read this once and for all into a public final member
        return resources.getBoolean(R.bool.config_enable_show_settings_key_option);
    }

    private static boolean isVibrateOn(final Context context, final SharedPreferences prefs,
            final Resources res) {
        final boolean hasVibrator = VibratorCompatWrapper.getInstance(context).hasVibrator();
        return hasVibrator && prefs.getBoolean(Settings.PREF_VIBRATE_ON,
                res.getBoolean(R.bool.config_default_vibration_enabled));
    }

    public boolean isSuggestedPunctuation(int code) {
        return mSuggestPuncs.contains(String.valueOf((char)code));
    }

    public boolean isWordSeparator(int code) {
        return mWordSeparators.contains(String.valueOf((char)code));
    }

    public boolean isSymbolExcludedFromWordSeparators(int code) {
        return mSymbolsExcludedFromWordSeparators.contains(String.valueOf((char)code));
    }

    public boolean isMagicSpaceStripper(int code) {
        return mMagicSpaceStrippers.contains(String.valueOf((char)code));
    }

    public boolean isMagicSpaceSwapper(int code) {
        return mMagicSpaceSwappers.contains(String.valueOf((char)code));
    }

    private static boolean isAutoCorrectEnabled(SharedPreferences sp, Resources resources) {
        final String currentAutoCorrectionSetting = sp.getString(
                Settings.PREF_AUTO_CORRECTION_THRESHOLD,
                resources.getString(R.string.auto_correction_threshold_mode_index_modest));
        final String autoCorrectionOff = resources.getString(
                R.string.auto_correction_threshold_mode_index_off);
        return !currentAutoCorrectionSetting.equals(autoCorrectionOff);
    }

    // Public to access from KeyboardSwitcher. Should it have access to some
    // process-global instance instead?
    public static boolean isKeyPreviewPopupEnabled(SharedPreferences sp, Resources resources) {
        final boolean showPopupOption = resources.getBoolean(
                R.bool.config_enable_show_popup_on_keypress_option);
        if (!showPopupOption) return resources.getBoolean(R.bool.config_default_popup_preview);
        return sp.getBoolean(Settings.PREF_POPUP_ON,
                resources.getBoolean(R.bool.config_default_popup_preview));
    }

    // Likewise
    public static int getKeyPreviewPopupDismissDelay(SharedPreferences sp,
            Resources resources) {
        return Integer.parseInt(sp.getString(Settings.PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY,
                Integer.toString(resources.getInteger(R.integer.config_delay_after_preview))));
    }

    private static boolean isBigramSuggestionEnabled(SharedPreferences sp, Resources resources,
            boolean autoCorrectEnabled) {
        final boolean showBigramSuggestionsOption = resources.getBoolean(
                R.bool.config_enable_bigram_suggestions_option);
        if (!showBigramSuggestionsOption) {
            return autoCorrectEnabled;
        }
        return sp.getBoolean(Settings.PREF_BIGRAM_SUGGESTION, resources.getBoolean(
                R.bool.config_default_bigram_suggestions));
    }

    private static boolean isBigramPredictionEnabled(SharedPreferences sp,
            Resources resources) {
        return sp.getBoolean(Settings.PREF_BIGRAM_PREDICTIONS, resources.getBoolean(
                R.bool.config_default_bigram_prediction));
    }

    private static double getAutoCorrectionThreshold(SharedPreferences sp,
            Resources resources) {
        final String currentAutoCorrectionSetting = sp.getString(
                Settings.PREF_AUTO_CORRECTION_THRESHOLD,
                resources.getString(R.string.auto_correction_threshold_mode_index_modest));
        final String[] autoCorrectionThresholdValues = resources.getStringArray(
                R.array.auto_correction_threshold_values);
        // When autoCorrectionThreshold is greater than 1.0, it's like auto correction is off.
        double autoCorrectionThreshold = Double.MAX_VALUE;
        try {
            final int arrayIndex = Integer.valueOf(currentAutoCorrectionSetting);
            if (arrayIndex >= 0 && arrayIndex < autoCorrectionThresholdValues.length) {
                autoCorrectionThreshold = Double.parseDouble(
                        autoCorrectionThresholdValues[arrayIndex]);
            }
        } catch (NumberFormatException e) {
            // Whenever the threshold settings are correct, never come here.
            autoCorrectionThreshold = Double.MAX_VALUE;
            Log.w(TAG, "Cannot load auto correction threshold setting."
                    + " currentAutoCorrectionSetting: " + currentAutoCorrectionSetting
                    + ", autoCorrectionThresholdValues: "
                    + Arrays.toString(autoCorrectionThresholdValues));
        }
        return autoCorrectionThreshold;
    }

    public boolean isSettingsKeyEnabled() {
        return mShowSettingsKey;
    }

    public boolean isVoiceKeyEnabled(final EditorInfo editorInfo) {
        final boolean shortcutImeEnabled = SubtypeSwitcher.getInstance().isShortcutImeEnabled();
        final int inputType = (editorInfo != null) ? editorInfo.inputType : 0;
        return shortcutImeEnabled && mVoiceKeyEnabled
                && !InputTypeCompatUtils.isPasswordInputType(inputType);
    }

    public boolean isVoiceKeyOnMain() {
        return mVoiceKeyOnMain;
    }

    // Accessed from the settings interface, hence public
    public static float getCurrentKeypressSoundVolume(final SharedPreferences sp,
                final Resources res) {
        final float volume = sp.getFloat(Settings.PREF_KEYPRESS_SOUND_VOLUME, -1.0f);
        if (volume >= 0) {
            return volume;
        }

        final String[] volumePerHardwareList = res.getStringArray(R.array.keypress_volumes);
        final String hardwarePrefix = Build.HARDWARE + ",";
        for (final String element : volumePerHardwareList) {
            if (element.startsWith(hardwarePrefix)) {
                return Float.parseFloat(element.substring(element.lastIndexOf(',') + 1));
            }
        }
        return -1.0f;
    }

    // Likewise
    public static int getCurrentVibrationDuration(final SharedPreferences sp,
                final Resources res) {
        final int ms = sp.getInt(Settings.PREF_VIBRATION_DURATION_SETTINGS, -1);
        if (ms >= 0) {
            return ms;
        }
        final String[] durationPerHardwareList = res.getStringArray(
                R.array.keypress_vibration_durations);
        final String hardwarePrefix = Build.HARDWARE + ",";
        for (final String element : durationPerHardwareList) {
            if (element.startsWith(hardwarePrefix)) {
                return (int)Long.parseLong(element.substring(element.lastIndexOf(',') + 1));
            }
        }
        return -1;
    }
}
