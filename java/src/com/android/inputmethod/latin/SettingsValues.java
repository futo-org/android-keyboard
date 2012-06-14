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
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.keyboard.internal.KeySpecParser;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * When you call the constructor of this class, you may want to change the current system locale by
 * using {@link LocaleUtils.RunInLocale}.
 */
public class SettingsValues {
    private static final String TAG = SettingsValues.class.getSimpleName();

    // From resources:
    public final int mDelayUpdateOldSuggestions;
    public final String mWeakSpaceStrippers;
    public final String mWeakSpaceSwappers;
    private final String mPhantomSpacePromotingSymbols;
    public final SuggestedWords mSuggestPuncList;
    private final String mSymbolsExcludedFromWordSeparators;
    public final String mWordSeparators;
    public final CharSequence mHintToSaveText;

    // From preferences, in the same order as xml/prefs.xml:
    public final boolean mAutoCap;
    public final boolean mVibrateOn;
    public final boolean mSoundOn;
    public final boolean mKeyPreviewPopupOn;
    private final String mVoiceMode;
    private final String mAutoCorrectionThresholdRawValue;
    public final String mShowSuggestionsSetting;
    @SuppressWarnings("unused") // TODO: Use this
    private final boolean mUsabilityStudyMode;
    public final boolean mIncludesOtherImesInLanguageSwitchList;
    public final boolean mIsLanguageSwitchKeySuppressed;
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
    private final InputMethodSubtype[] mAdditionalSubtypes;

    // Deduced settings
    public final int mKeypressVibrationDuration;
    public final float mFxVolume;
    public final int mKeyPreviewPopupDismissDelay;
    public final boolean mAutoCorrectEnabled;
    public final float mAutoCorrectionThreshold;
    private final boolean mVoiceKeyEnabled;
    private final boolean mVoiceKeyOnMain;

    public SettingsValues(final SharedPreferences prefs, final Context context) {
        final Resources res = context.getResources();

        // Get the resources
        mDelayUpdateOldSuggestions = res.getInteger(R.integer.config_delay_update_old_suggestions);
        mWeakSpaceStrippers = res.getString(R.string.weak_space_stripping_symbols);
        mWeakSpaceSwappers = res.getString(R.string.weak_space_swapping_symbols);
        mPhantomSpacePromotingSymbols = res.getString(R.string.phantom_space_promoting_symbols);
        if (LatinImeLogger.sDBG) {
            final int length = mWeakSpaceStrippers.length();
            for (int i = 0; i < length; i = mWeakSpaceStrippers.offsetByCodePoints(i, 1)) {
                if (isWeakSpaceSwapper(mWeakSpaceStrippers.codePointAt(i))) {
                    throw new RuntimeException("Char code " + mWeakSpaceStrippers.codePointAt(i)
                            + " is both a weak space swapper and stripper.");
                }
            }
        }
        final String[] suggestPuncsSpec = KeySpecParser.parseCsvString(
                res.getString(R.string.suggested_punctuations), null);
        mSuggestPuncList = createSuggestPuncList(suggestPuncsSpec);
        mSymbolsExcludedFromWordSeparators =
                res.getString(R.string.symbols_excluded_from_word_separators);
        mWordSeparators = createWordSeparators(mWeakSpaceStrippers, mWeakSpaceSwappers,
                mSymbolsExcludedFromWordSeparators, res);
        mHintToSaveText = context.getText(R.string.hint_add_to_dictionary);

        // Get the settings preferences
        mAutoCap = prefs.getBoolean(Settings.PREF_AUTO_CAP, true);
        mVibrateOn = isVibrateOn(context, prefs, res);
        mSoundOn = prefs.getBoolean(Settings.PREF_SOUND_ON,
                res.getBoolean(R.bool.config_default_sound_enabled));
        mKeyPreviewPopupOn = isKeyPreviewPopupEnabled(prefs, res);
        final String voiceModeMain = res.getString(R.string.voice_mode_main);
        final String voiceModeOff = res.getString(R.string.voice_mode_off);
        mVoiceMode = prefs.getString(Settings.PREF_VOICE_MODE, voiceModeMain);
        mAutoCorrectionThresholdRawValue = prefs.getString(Settings.PREF_AUTO_CORRECTION_THRESHOLD,
                res.getString(R.string.auto_correction_threshold_mode_index_modest));
        mShowSuggestionsSetting = prefs.getString(Settings.PREF_SHOW_SUGGESTIONS_SETTING,
                res.getString(R.string.prefs_suggestion_visibility_default_value));
        mUsabilityStudyMode = getUsabilityStudyMode(prefs);
        mIncludesOtherImesInLanguageSwitchList = prefs.getBoolean(
                Settings.PREF_INCLUDE_OTHER_IMES_IN_LANGUAGE_SWITCH_LIST, false);
        mIsLanguageSwitchKeySuppressed = isLanguageSwitchKeySupressed(prefs);
        mKeyPreviewPopupDismissDelayRawValue = prefs.getString(
                Settings.PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY,
                Integer.toString(res.getInteger(R.integer.config_key_preview_linger_timeout)));
        mUseContactsDict = prefs.getBoolean(Settings.PREF_KEY_USE_CONTACTS_DICT, true);
        mAutoCorrectEnabled = isAutoCorrectEnabled(res, mAutoCorrectionThresholdRawValue);
        mBigramSuggestionEnabled = mAutoCorrectEnabled
                && isBigramSuggestionEnabled(prefs, res, mAutoCorrectEnabled);
        mBigramPredictionEnabled = mBigramSuggestionEnabled
                && isBigramPredictionEnabled(prefs, res);
        // TODO: remove mEnableSuggestionSpanInsertion. It's always true.
        mEnableSuggestionSpanInsertion = true;
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
        mAdditionalSubtypes = AdditionalSubtype.createAdditionalSubtypesArray(
                getPrefAdditionalSubtypes(prefs, res));
    }

    // Helper functions to create member values.
    private static SuggestedWords createSuggestPuncList(final String[] puncs) {
        final ArrayList<SuggestedWords.SuggestedWordInfo> puncList =
                new ArrayList<SuggestedWords.SuggestedWordInfo>();
        if (puncs != null) {
            for (final String puncSpec : puncs) {
                puncList.add(new SuggestedWords.SuggestedWordInfo(
                        KeySpecParser.getLabel(puncSpec), SuggestedWordInfo.MAX_SCORE));
            }
        }
        return new SuggestedWords(puncList,
                false /* typedWordValid */,
                false /* hasAutoCorrectionCandidate */,
                false /* allowsToBeAutoCorrected */,
                true /* isPunctuationSuggestions */,
                false /* isObsoleteSuggestions */,
                false /* isPrediction */);
    }

    private static String createWordSeparators(final String weakSpaceStrippers,
            final String weakSpaceSwappers, final String symbolsExcludedFromWordSeparators,
            final Resources res) {
        String wordSeparators = weakSpaceStrippers + weakSpaceSwappers
                + res.getString(R.string.phantom_space_promoting_symbols);
        for (int i = symbolsExcludedFromWordSeparators.length() - 1; i >= 0; --i) {
            wordSeparators = wordSeparators.replace(
                    symbolsExcludedFromWordSeparators.substring(i, i + 1), "");
        }
        return wordSeparators;
    }

    private static boolean isVibrateOn(final Context context, final SharedPreferences prefs,
            final Resources res) {
        final boolean hasVibrator = VibratorUtils.getInstance(context).hasVibrator();
        return hasVibrator && prefs.getBoolean(Settings.PREF_VIBRATE_ON,
                res.getBoolean(R.bool.config_default_vibration_enabled));
    }

    public boolean isWordSeparator(int code) {
        return mWordSeparators.contains(String.valueOf((char)code));
    }

    public boolean isSymbolExcludedFromWordSeparators(int code) {
        return mSymbolsExcludedFromWordSeparators.contains(String.valueOf((char)code));
    }

    public boolean isWeakSpaceStripper(int code) {
        // TODO: this does not work if the code does not fit in a char
        return mWeakSpaceStrippers.contains(String.valueOf((char)code));
    }

    public boolean isWeakSpaceSwapper(int code) {
        // TODO: this does not work if the code does not fit in a char
        return mWeakSpaceSwappers.contains(String.valueOf((char)code));
    }

    public boolean isPhantomSpacePromotingSymbol(int code) {
        // TODO: this does not work if the code does not fit in a char
        return mPhantomSpacePromotingSymbols.contains(String.valueOf((char)code));
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
        // TODO: remove this method. Bigram suggestion is always true.
        return true;
    }

    private static boolean isBigramPredictionEnabled(final SharedPreferences sp,
            final Resources resources) {
        return sp.getBoolean(Settings.PREF_BIGRAM_PREDICTIONS, resources.getBoolean(
                R.bool.config_default_next_word_prediction));
    }

    private static float getAutoCorrectionThreshold(final Resources resources,
            final String currentAutoCorrectionSetting) {
        final String[] autoCorrectionThresholdValues = resources.getStringArray(
                R.array.auto_correction_threshold_values);
        // When autoCorrectionThreshold is greater than 1.0, it's like auto correction is off.
        float autoCorrectionThreshold = Float.MAX_VALUE;
        try {
            final int arrayIndex = Integer.valueOf(currentAutoCorrectionSetting);
            if (arrayIndex >= 0 && arrayIndex < autoCorrectionThresholdValues.length) {
                autoCorrectionThreshold = Float.parseFloat(
                        autoCorrectionThresholdValues[arrayIndex]);
            }
        } catch (NumberFormatException e) {
            // Whenever the threshold settings are correct, never come here.
            autoCorrectionThreshold = Float.MAX_VALUE;
            Log.w(TAG, "Cannot load auto correction threshold setting."
                    + " currentAutoCorrectionSetting: " + currentAutoCorrectionSetting
                    + ", autoCorrectionThresholdValues: "
                    + Arrays.toString(autoCorrectionThresholdValues));
        }
        return autoCorrectionThreshold;
    }

    public boolean isVoiceKeyEnabled(final EditorInfo editorInfo) {
        final boolean shortcutImeEnabled = SubtypeSwitcher.getInstance().isShortcutImeEnabled();
        final int inputType = (editorInfo != null) ? editorInfo.inputType : 0;
        return shortcutImeEnabled && mVoiceKeyEnabled
                && !InputTypeUtils.isPasswordInputType(inputType);
    }

    public boolean isVoiceKeyOnMain() {
        return mVoiceKeyOnMain;
    }

    public static boolean isLanguageSwitchKeySupressed(SharedPreferences sp) {
        return sp.getBoolean(Settings.PREF_SUPPRESS_LANGUAGE_SWITCH_KEY, false);
    }

    public boolean isLanguageSwitchKeyEnabled(Context context) {
        if (mIsLanguageSwitchKeySuppressed) {
            return false;
        }
        if (mIncludesOtherImesInLanguageSwitchList) {
            return ImfUtils.hasMultipleEnabledIMEsOrSubtypes(
                    context, /* include aux subtypes */false);
        } else {
            return ImfUtils.hasMultipleEnabledSubtypesInThisIme(
                    context, /* include aux subtypes */false);
        }
    }

    public boolean isFullscreenModeAllowed(Resources res) {
        return res.getBoolean(R.bool.config_use_fullscreen_mode);
    }

    public InputMethodSubtype[] getAdditionalSubtypes() {
        return mAdditionalSubtypes;
    }

    public static String getPrefAdditionalSubtypes(final SharedPreferences prefs,
            final Resources res) {
        final String prefSubtypes = res.getString(R.string.predefined_subtypes, "");
        return prefs.getString(Settings.PREF_CUSTOM_INPUT_STYLES, prefSubtypes);
    }

    // Accessed from the settings interface, hence public
    public static float getCurrentKeypressSoundVolume(final SharedPreferences sp,
                final Resources res) {
        // TODO: use mVibrationDurationSettingsRawValue instead of reading it again here
        final float volume = sp.getFloat(Settings.PREF_KEYPRESS_SOUND_VOLUME, -1.0f);
        if (volume >= 0) {
            return volume;
        }

        return Float.parseFloat(
                Utils.getDeviceOverrideValue(res, R.array.keypress_volumes, "-1.0f"));
    }

    // Likewise
    public static int getCurrentVibrationDuration(final SharedPreferences sp,
                final Resources res) {
        // TODO: use mKeypressVibrationDuration instead of reading it again here
        final int ms = sp.getInt(Settings.PREF_VIBRATION_DURATION_SETTINGS, -1);
        if (ms >= 0) {
            return ms;
        }

        return Integer.parseInt(
                Utils.getDeviceOverrideValue(res, R.array.keypress_vibration_durations, "-1"));
    }

    // Likewise
    public static boolean getUsabilityStudyMode(final SharedPreferences prefs) {
        // TODO: use mUsabilityStudyMode instead of reading it again here
        return prefs.getBoolean(Settings.PREF_USABILITY_STUDY_MODE, true);
    }

    public static long getLastUserHistoryWriteTime(
            final SharedPreferences prefs, final String locale) {
        final String str = prefs.getString(Settings.PREF_LAST_USER_DICTIONARY_WRITE_TIME, "");
        final HashMap<String, Long> map = Utils.localeAndTimeStrToHashMap(str);
        if (map.containsKey(locale)) {
            return map.get(locale);
        }
        return 0;
    }

    public static void setLastUserHistoryWriteTime(
            final SharedPreferences prefs, final String locale) {
        final String oldStr = prefs.getString(Settings.PREF_LAST_USER_DICTIONARY_WRITE_TIME, "");
        final HashMap<String, Long> map = Utils.localeAndTimeStrToHashMap(oldStr);
        map.put(locale, System.currentTimeMillis());
        final String newStr = Utils.localeAndTimeHashMapToStr(map);
        prefs.edit().putString(Settings.PREF_LAST_USER_DICTIONARY_WRITE_TIME, newStr).apply();
    }
}
