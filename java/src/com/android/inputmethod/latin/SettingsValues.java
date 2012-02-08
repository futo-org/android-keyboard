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
import com.android.inputmethod.keyboard.internal.KeySpecParser;

import java.util.Arrays;
import java.util.Locale;

public class SettingsValues {
    private static final String TAG = SettingsValues.class.getSimpleName();

    // From resources:
    public final int mDelayUpdateOldSuggestions;
    public final String mMagicSpaceStrippers;
    public final String mMagicSpaceSwappers;
    private final String mSuggestPuncs;
    public final SuggestedWords mSuggestPuncList;
    public final SuggestedWords mSuggestPuncOutputTextList;
    private final String mSymbolsExcludedFromWordSeparators;
    public final String mWordSeparators;
    public final CharSequence mHintToSaveText;

    // From preferences, in the same order as xml/prefs.xml:
    public final boolean mAutoCap;
    public final boolean mVibrateOn;
    public final boolean mSoundOn;
    public final boolean mKeyPreviewPopupOn;
    private final boolean mShowSettingsKey;
    private final String mVoiceMode;
    private final String mAutoCorrectionThresholdRawValue;
    public final String mShowSuggestionsSetting;
    @SuppressWarnings("unused") // TODO: Use this
    private final boolean mUsabilityStudyMode;
    @SuppressWarnings("unused") // TODO: Use this
    private final String mKeyPreviewPopupDismissDelayRawValue;
    public final boolean mUseContactsDict;
    // Suggestion: use bigrams to adjust scores of suggestions obtained from unigram dictionary
    public final boolean mBigramSuggestionEnabled;
    // Prediction: use bigrams to predict the next word when there is no input for it yet
    public final boolean mBigramPredictionEnabled;
    public final boolean mEnableSuggestionSpanInsertion;
    @SuppressWarnings("unused") // TODO: Use this
    private final int mVibrationDurationSettingsRawValue;
    @SuppressWarnings("unused") // TODO: Use this
    private final float mKeypressSoundVolumeRawValue;

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
        if (LatinImeLogger.sDBG) {
            final int length = mMagicSpaceStrippers.length();
            for (int i = 0; i < length; i = mMagicSpaceStrippers.offsetByCodePoints(i, 1)) {
                if (isMagicSpaceSwapper(mMagicSpaceStrippers.codePointAt(i))) {
                    throw new RuntimeException("Char code " + mMagicSpaceStrippers.codePointAt(i)
                            + " is both a magic space swapper and stripper.");
                }
            }
        }
        final String[] suggestPuncsSpec = KeySpecParser.parseCsvString(
                res.getString(R.string.suggested_punctuations), res, R.string.english_ime_name);
        mSuggestPuncs = createSuggestPuncs(suggestPuncsSpec);
        mSuggestPuncList = createSuggestPuncList(suggestPuncsSpec);
        mSuggestPuncOutputTextList = createSuggestPuncOutputTextList(suggestPuncsSpec);
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
        final String voiceModeMain = res.getString(R.string.voice_mode_main);
        final String voiceModeOff = res.getString(R.string.voice_mode_off);
        mVoiceMode = prefs.getString(Settings.PREF_VOICE_MODE, voiceModeMain);
        mAutoCorrectionThresholdRawValue = prefs.getString(Settings.PREF_AUTO_CORRECTION_THRESHOLD,
                res.getString(R.string.auto_correction_threshold_mode_index_modest));
        mShowSuggestionsSetting = prefs.getString(Settings.PREF_SHOW_SUGGESTIONS_SETTING,
                res.getString(R.string.prefs_suggestion_visibility_default_value));
        mUsabilityStudyMode = getUsabilityStudyMode(prefs);
        mKeyPreviewPopupDismissDelayRawValue = prefs.getString(
                Settings.PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY,
                Integer.toString(res.getInteger(R.integer.config_key_preview_linger_timeout)));
        mUseContactsDict = prefs.getBoolean(Settings.PREF_KEY_USE_CONTACTS_DICT, true);
        mAutoCorrectEnabled = isAutoCorrectEnabled(res, mAutoCorrectionThresholdRawValue);
        mBigramSuggestionEnabled = mAutoCorrectEnabled
                && isBigramSuggestionEnabled(prefs, res, mAutoCorrectEnabled);
        mBigramPredictionEnabled = mBigramSuggestionEnabled
                && isBigramPredictionEnabled(prefs, res);
        mEnableSuggestionSpanInsertion =
                prefs.getBoolean(Settings.PREF_KEY_ENABLE_SPAN_INSERT, true);
        mVibrationDurationSettingsRawValue =
                prefs.getInt(Settings.PREF_VIBRATION_DURATION_SETTINGS, -1);
        mKeypressSoundVolumeRawValue = prefs.getFloat(Settings.PREF_KEYPRESS_SOUND_VOLUME, -1.0f);

        // Compute other readable settings
        mKeypressVibrationDuration = getCurrentVibrationDuration(prefs, res);
        mFxVolume = getCurrentKeypressSoundVolume(prefs, res);
        mKeyPreviewPopupDismissDelay = getKeyPreviewPopupDismissDelay(prefs, res);
        mAutoCorrectionThreshold = getAutoCorrectionThreshold(res,
                mAutoCorrectionThresholdRawValue);
        mVoiceKeyEnabled = mVoiceMode != null && !mVoiceMode.equals(voiceModeOff);
        mVoiceKeyOnMain = mVoiceMode != null && mVoiceMode.equals(voiceModeMain);

        LocaleUtils.setSystemLocale(res, savedLocale);
    }

    // Helper functions to create member values.
    private static String createSuggestPuncs(final String[] puncs) {
        final StringBuilder sb = new StringBuilder();
        if (puncs != null) {
            for (final String puncSpec : puncs) {
                sb.append(KeySpecParser.getLabel(puncSpec));
            }
        }
        return sb.toString();
    }

    private static SuggestedWords createSuggestPuncList(final String[] puncs) {
        final SuggestedWords.Builder builder = new SuggestedWords.Builder();
        if (puncs != null) {
            for (final String puncSpec : puncs) {
                builder.addWord(KeySpecParser.getLabel(puncSpec));
            }
        }
        return builder.setIsPunctuationSuggestions().build();
    }

    private static SuggestedWords createSuggestPuncOutputTextList(final String[] puncs) {
        final SuggestedWords.Builder builder = new SuggestedWords.Builder();
        if (puncs != null) {
            for (final String puncSpec : puncs) {
                final String outputText = KeySpecParser.getOutputText(puncSpec);
                if (outputText != null) {
                    builder.addWord(outputText);
                } else {
                    builder.addWord(KeySpecParser.getLabel(puncSpec));
                }
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

    public boolean isWordSeparator(int code) {
        return mWordSeparators.contains(String.valueOf((char)code));
    }

    public boolean isSymbolExcludedFromWordSeparators(int code) {
        return mSymbolsExcludedFromWordSeparators.contains(String.valueOf((char)code));
    }

    public boolean isMagicSpaceStripper(int code) {
        // TODO: this does not work if the code does not fit in a char
        return mMagicSpaceStrippers.contains(String.valueOf((char)code));
    }

    public boolean isMagicSpaceSwapper(int code) {
        // TODO: this does not work if the code does not fit in a char
        return mMagicSpaceSwappers.contains(String.valueOf((char)code));
    }

    private static boolean isAutoCorrectEnabled(final Resources resources,
            final String currentAutoCorrectionSetting) {
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
        // TODO: use mKeyPreviewPopupDismissDelayRawValue instead of reading it again here.
        return Integer.parseInt(sp.getString(Settings.PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY,
                Integer.toString(resources.getInteger(
                        R.integer.config_key_preview_linger_timeout))));
    }

    private static boolean isBigramSuggestionEnabled(final SharedPreferences sp,
            final Resources resources, final boolean autoCorrectEnabled) {
        final boolean showBigramSuggestionsOption = resources.getBoolean(
                R.bool.config_enable_bigram_suggestions_option);
        if (!showBigramSuggestionsOption) {
            return autoCorrectEnabled;
        }
        return sp.getBoolean(Settings.PREF_BIGRAM_SUGGESTION, resources.getBoolean(
                R.bool.config_default_bigram_suggestions));
    }

    private static boolean isBigramPredictionEnabled(final SharedPreferences sp,
            final Resources resources) {
        return sp.getBoolean(Settings.PREF_BIGRAM_PREDICTIONS, resources.getBoolean(
                R.bool.config_default_bigram_prediction));
    }

    private static double getAutoCorrectionThreshold(final Resources resources,
            final String currentAutoCorrectionSetting) {
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

    public boolean isFullscreenModeAllowed(Resources res) {
        return res.getBoolean(R.bool.config_use_fullscreen_mode);
    }

    // Accessed from the settings interface, hence public
    public static float getCurrentKeypressSoundVolume(final SharedPreferences sp,
                final Resources res) {
        // TODO: use mVibrationDurationSettingsRawValue instead of reading it again here
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
        // TODO: use mKeypressVibrationDuration instead of reading it again here
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

    // Likewise
    public static boolean getUsabilityStudyMode(final SharedPreferences prefs) {
        // TODO: use mUsabilityStudyMode instead of reading it again here
        return prefs.getBoolean(Settings.PREF_USABILITY_STUDY_MODE, true);
    }
}
