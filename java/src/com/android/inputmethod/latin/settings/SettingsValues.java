/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.keyboard.internal.KeySpecParser;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.InputAttributes;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.RichInputMethodManager;
import com.android.inputmethod.latin.SubtypeSwitcher;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.InputTypeUtils;
import com.android.inputmethod.latin.utils.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

/**
 * When you call the constructor of this class, you may want to change the current system locale by
 * using {@link com.android.inputmethod.latin.utils.RunInLocale}.
 */
public final class SettingsValues {
    private static final String TAG = SettingsValues.class.getSimpleName();
    // "floatMaxValue" and "floatNegativeInfinity" are special marker strings for
    // Float.NEGATIVE_INFINITE and Float.MAX_VALUE. Currently used for auto-correction settings.
    private static final String FLOAT_MAX_VALUE_MARKER_STRING = "floatMaxValue";
    private static final String FLOAT_NEGATIVE_INFINITY_MARKER_STRING = "floatNegativeInfinity";

    // From resources:
    public final int mDelayUpdateOldSuggestions;
    public final int[] mSymbolsPrecededBySpace;
    public final int[] mSymbolsFollowedBySpace;
    public final int[] mWordConnectors;
    public final SuggestedWords mSuggestPuncList;
    public final String mWordSeparators;
    public final int mSentenceSeparator;
    public final CharSequence mHintToSaveText;
    public final boolean mCurrentLanguageHasSpaces;

    // From preferences, in the same order as xml/prefs.xml:
    public final boolean mAutoCap;
    public final boolean mVibrateOn;
    public final boolean mSoundOn;
    public final boolean mKeyPreviewPopupOn;
    private final boolean mShowsVoiceInputKey;
    public final boolean mIncludesOtherImesInLanguageSwitchList;
    public final boolean mShowsLanguageSwitchKey;
    public final boolean mUseContactsDict;
    public final boolean mUseDoubleSpacePeriod;
    public final boolean mBlockPotentiallyOffensive;
    // Use bigrams to predict the next word when there is no input for it yet
    public final boolean mBigramPredictionEnabled;
    public final boolean mGestureInputEnabled;
    public final boolean mGestureTrailEnabled;
    public final boolean mGestureFloatingPreviewTextEnabled;
    public final boolean mSlidingKeyInputPreviewEnabled;
    public final boolean mPhraseGestureEnabled;
    public final int mKeyLongpressTimeout;
    public final Locale mLocale;

    // From the input box
    public final InputAttributes mInputAttributes;

    // Deduced settings
    public final int mKeypressVibrationDuration;
    public final float mKeypressSoundVolume;
    public final int mKeyPreviewPopupDismissDelay;
    private final boolean mAutoCorrectEnabled;
    public final float mAutoCorrectionThreshold;
    public final boolean mCorrectionEnabled;
    public final int mSuggestionVisibility;
    public final boolean mBoostPersonalizationDictionaryForDebug;
    public final boolean mUseOnlyPersonalizationDictionaryForDebug;

    // Setting values for additional features
    public final int[] mAdditionalFeaturesSettingValues =
            new int[AdditionalFeaturesSettingUtils.ADDITIONAL_FEATURES_SETTINGS_SIZE];

    // Debug settings
    public final boolean mIsInternal;

    public SettingsValues(final SharedPreferences prefs, final Locale locale, final Resources res,
            final InputAttributes inputAttributes) {
        mLocale = locale;
        // Get the resources
        mDelayUpdateOldSuggestions = res.getInteger(R.integer.config_delay_update_old_suggestions);
        mSymbolsPrecededBySpace =
                StringUtils.toCodePointArray(res.getString(R.string.symbols_preceded_by_space));
        Arrays.sort(mSymbolsPrecededBySpace);
        mSymbolsFollowedBySpace =
                StringUtils.toCodePointArray(res.getString(R.string.symbols_followed_by_space));
        Arrays.sort(mSymbolsFollowedBySpace);
        mWordConnectors =
                StringUtils.toCodePointArray(res.getString(R.string.symbols_word_connectors));
        Arrays.sort(mWordConnectors);
        final String[] suggestPuncsSpec = KeySpecParser.splitKeySpecs(res.getString(
                R.string.suggested_punctuations));
        mSuggestPuncList = createSuggestPuncList(suggestPuncsSpec);
        mWordSeparators = res.getString(R.string.symbols_word_separators);
        mSentenceSeparator = res.getInteger(R.integer.sentence_separator);
        mHintToSaveText = res.getText(R.string.hint_add_to_dictionary);
        mCurrentLanguageHasSpaces = res.getBoolean(R.bool.current_language_has_spaces);

        // Store the input attributes
        if (null == inputAttributes) {
            mInputAttributes = new InputAttributes(null, false /* isFullscreenMode */);
        } else {
            mInputAttributes = inputAttributes;
        }

        // Get the settings preferences
        mAutoCap = prefs.getBoolean(Settings.PREF_AUTO_CAP, true);
        mVibrateOn = Settings.readVibrationEnabled(prefs, res);
        mSoundOn = Settings.readKeypressSoundEnabled(prefs, res);
        mKeyPreviewPopupOn = Settings.readKeyPreviewPopupEnabled(prefs, res);
        mSlidingKeyInputPreviewEnabled = prefs.getBoolean(
                Settings.PREF_SLIDING_KEY_INPUT_PREVIEW, true);
        mShowsVoiceInputKey = needsToShowVoiceInputKey(prefs, res);
        final String autoCorrectionThresholdRawValue = prefs.getString(
                Settings.PREF_AUTO_CORRECTION_THRESHOLD,
                res.getString(R.string.auto_correction_threshold_mode_index_modest));
        mIncludesOtherImesInLanguageSwitchList = prefs.getBoolean(
                Settings.PREF_INCLUDE_OTHER_IMES_IN_LANGUAGE_SWITCH_LIST, false);
        mShowsLanguageSwitchKey = Settings.readShowsLanguageSwitchKey(prefs);
        mUseContactsDict = prefs.getBoolean(Settings.PREF_KEY_USE_CONTACTS_DICT, true);
        mUseDoubleSpacePeriod = prefs.getBoolean(Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, true);
        mBlockPotentiallyOffensive = Settings.readBlockPotentiallyOffensive(prefs, res);
        mAutoCorrectEnabled = Settings.readAutoCorrectEnabled(autoCorrectionThresholdRawValue, res);
        mBigramPredictionEnabled = readBigramPredictionEnabled(prefs, res);

        // Compute other readable settings
        mKeyLongpressTimeout = Settings.readKeyLongpressTimeout(prefs, res);
        mKeypressVibrationDuration = Settings.readKeypressVibrationDuration(prefs, res);
        mKeypressSoundVolume = Settings.readKeypressSoundVolume(prefs, res);
        mKeyPreviewPopupDismissDelay = Settings.readKeyPreviewPopupDismissDelay(prefs, res);
        mAutoCorrectionThreshold = readAutoCorrectionThreshold(res,
                autoCorrectionThresholdRawValue);
        mGestureInputEnabled = Settings.readGestureInputEnabled(prefs, res);
        mGestureTrailEnabled = prefs.getBoolean(Settings.PREF_GESTURE_PREVIEW_TRAIL, true);
        mGestureFloatingPreviewTextEnabled = prefs.getBoolean(
                Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT, true);
        mPhraseGestureEnabled = Settings.readPhraseGestureEnabled(prefs, res);
        mCorrectionEnabled = mAutoCorrectEnabled && !mInputAttributes.mInputTypeNoAutoCorrect;
        final String showSuggestionsSetting = prefs.getString(
                Settings.PREF_SHOW_SUGGESTIONS_SETTING,
                res.getString(R.string.prefs_suggestion_visibility_default_value));
        mSuggestionVisibility = createSuggestionVisibility(res, showSuggestionsSetting);
        AdditionalFeaturesSettingUtils.readAdditionalFeaturesPreferencesIntoArray(
                prefs, mAdditionalFeaturesSettingValues);
        mIsInternal = Settings.isInternal(prefs);
        mBoostPersonalizationDictionaryForDebug =
                Settings.readBoostPersonalizationDictionaryForDebug(prefs);
        mUseOnlyPersonalizationDictionaryForDebug =
                Settings.readUseOnlyPersonalizationDictionaryForDebug(prefs);
    }

    // Only for tests
    private SettingsValues(final Locale locale) {
        // TODO: locale is saved, but not used yet. May have to change this if tests require.
        mLocale = locale;
        mDelayUpdateOldSuggestions = 0;
        mSymbolsPrecededBySpace = new int[] { '(', '[', '{', '&' };
        Arrays.sort(mSymbolsPrecededBySpace);
        mSymbolsFollowedBySpace = new int[] { '.', ',', ';', ':', '!', '?', ')', ']', '}', '&' };
        Arrays.sort(mSymbolsFollowedBySpace);
        mWordConnectors = new int[] { '\'', '-' };
        Arrays.sort(mWordConnectors);
        mSentenceSeparator = Constants.CODE_PERIOD;
        final String[] suggestPuncsSpec = new String[] { "!", "?", ",", ":", ";" };
        mSuggestPuncList = createSuggestPuncList(suggestPuncsSpec);
        mWordSeparators = "&\t \n()[]{}*&<>+=|.,;:!?/_\"";
        mHintToSaveText = "Touch again to save";
        mCurrentLanguageHasSpaces = true;
        mInputAttributes = new InputAttributes(null, false /* isFullscreenMode */);
        mAutoCap = true;
        mVibrateOn = true;
        mSoundOn = true;
        mKeyPreviewPopupOn = true;
        mSlidingKeyInputPreviewEnabled = true;
        mShowsVoiceInputKey = true;
        mIncludesOtherImesInLanguageSwitchList = false;
        mShowsLanguageSwitchKey = true;
        mUseContactsDict = true;
        mUseDoubleSpacePeriod = true;
        mBlockPotentiallyOffensive = true;
        mAutoCorrectEnabled = true;
        mBigramPredictionEnabled = true;
        mKeyLongpressTimeout = 300;
        mKeypressVibrationDuration = 5;
        mKeypressSoundVolume = 1;
        mKeyPreviewPopupDismissDelay = 70;
        mAutoCorrectionThreshold = 1;
        mGestureInputEnabled = true;
        mGestureTrailEnabled = true;
        mGestureFloatingPreviewTextEnabled = true;
        mPhraseGestureEnabled = true;
        mCorrectionEnabled = mAutoCorrectEnabled && !mInputAttributes.mInputTypeNoAutoCorrect;
        mSuggestionVisibility = 0;
        mIsInternal = false;
        mBoostPersonalizationDictionaryForDebug = false;
        mUseOnlyPersonalizationDictionaryForDebug = false;
    }

    @UsedForTesting
    public static SettingsValues makeDummySettingsValuesForTest(final Locale locale) {
        return new SettingsValues(locale);
    }

    public boolean isApplicationSpecifiedCompletionsOn() {
        return mInputAttributes.mApplicationSpecifiedCompletionOn;
    }

    public boolean isSuggestionsRequested(final int displayOrientation) {
        return mInputAttributes.mIsSettingsSuggestionStripOn
                && (mCorrectionEnabled
                        || isSuggestionStripVisibleInOrientation(displayOrientation));
    }

    public boolean isSuggestionStripVisibleInOrientation(final int orientation) {
        return (mSuggestionVisibility == SUGGESTION_VISIBILITY_SHOW_VALUE)
                || (mSuggestionVisibility == SUGGESTION_VISIBILITY_SHOW_ONLY_PORTRAIT_VALUE
                        && orientation == Configuration.ORIENTATION_PORTRAIT);
    }

    public boolean isWordSeparator(final int code) {
        return mWordSeparators.contains(String.valueOf((char)code));
    }

    public boolean isWordConnector(final int code) {
        return Arrays.binarySearch(mWordConnectors, code) >= 0;
    }

    public boolean isWordCodePoint(final int code) {
        return Character.isLetter(code) || isWordConnector(code);
    }

    public boolean isUsuallyPrecededBySpace(final int code) {
        return Arrays.binarySearch(mSymbolsPrecededBySpace, code) >= 0;
    }

    public boolean isUsuallyFollowedBySpace(final int code) {
        return Arrays.binarySearch(mSymbolsFollowedBySpace, code) >= 0;
    }

    public boolean shouldInsertSpacesAutomatically() {
        return mInputAttributes.mShouldInsertSpacesAutomatically;
    }

    public boolean isVoiceKeyEnabled(final EditorInfo editorInfo) {
        final boolean shortcutImeEnabled = SubtypeSwitcher.getInstance().isShortcutImeEnabled();
        final int inputType = (editorInfo != null) ? editorInfo.inputType : 0;
        return shortcutImeEnabled && mShowsVoiceInputKey
                && !InputTypeUtils.isPasswordInputType(inputType);
    }

    public boolean isLanguageSwitchKeyEnabled() {
        if (!mShowsLanguageSwitchKey) {
            return false;
        }
        final RichInputMethodManager imm = RichInputMethodManager.getInstance();
        if (mIncludesOtherImesInLanguageSwitchList) {
            return imm.hasMultipleEnabledIMEsOrSubtypes(false /* include aux subtypes */);
        } else {
            return imm.hasMultipleEnabledSubtypesInThisIme(false /* include aux subtypes */);
        }
    }

    public boolean isSameInputType(final EditorInfo editorInfo) {
        return mInputAttributes.isSameInputType(editorInfo);
    }

    // Helper functions to create member values.
    private static SuggestedWords createSuggestPuncList(final String[] puncs) {
        final ArrayList<SuggestedWordInfo> puncList = CollectionUtils.newArrayList();
        if (puncs != null) {
            for (final String puncSpec : puncs) {
                // TODO: Stop using KeySpceParser.getLabel().
                puncList.add(new SuggestedWordInfo(KeySpecParser.getLabel(puncSpec),
                        SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_HARDCODED,
                        Dictionary.DICTIONARY_HARDCODED,
                        SuggestedWordInfo.NOT_AN_INDEX /* indexOfTouchPointOfSecondWord */,
                        SuggestedWordInfo.NOT_A_CONFIDENCE /* autoCommitFirstWordConfidence */));
            }
        }
        return new SuggestedWords(puncList,
                false /* typedWordValid */,
                false /* hasAutoCorrectionCandidate */,
                true /* isPunctuationSuggestions */,
                false /* isObsoleteSuggestions */,
                false /* isPrediction */);
    }

    private static final int SUGGESTION_VISIBILITY_SHOW_VALUE =
            R.string.prefs_suggestion_visibility_show_value;
    private static final int SUGGESTION_VISIBILITY_SHOW_ONLY_PORTRAIT_VALUE =
            R.string.prefs_suggestion_visibility_show_only_portrait_value;
    private static final int SUGGESTION_VISIBILITY_HIDE_VALUE =
            R.string.prefs_suggestion_visibility_hide_value;
    private static final int[] SUGGESTION_VISIBILITY_VALUE_ARRAY = new int[] {
        SUGGESTION_VISIBILITY_SHOW_VALUE,
        SUGGESTION_VISIBILITY_SHOW_ONLY_PORTRAIT_VALUE,
        SUGGESTION_VISIBILITY_HIDE_VALUE
    };

    private static int createSuggestionVisibility(final Resources res,
            final String suggestionVisiblityStr) {
        for (int visibility : SUGGESTION_VISIBILITY_VALUE_ARRAY) {
            if (suggestionVisiblityStr.equals(res.getString(visibility))) {
                return visibility;
            }
        }
        throw new RuntimeException("Bug: visibility string is not configured correctly");
    }

    private static boolean readBigramPredictionEnabled(final SharedPreferences prefs,
            final Resources res) {
        return prefs.getBoolean(Settings.PREF_BIGRAM_PREDICTIONS, res.getBoolean(
                R.bool.config_default_next_word_prediction));
    }

    private static float readAutoCorrectionThreshold(final Resources res,
            final String currentAutoCorrectionSetting) {
        final String[] autoCorrectionThresholdValues = res.getStringArray(
                R.array.auto_correction_threshold_values);
        // When autoCorrectionThreshold is greater than 1.0, it's like auto correction is off.
        final float autoCorrectionThreshold;
        try {
            final int arrayIndex = Integer.valueOf(currentAutoCorrectionSetting);
            if (arrayIndex >= 0 && arrayIndex < autoCorrectionThresholdValues.length) {
                final String val = autoCorrectionThresholdValues[arrayIndex];
                if (FLOAT_MAX_VALUE_MARKER_STRING.equals(val)) {
                    autoCorrectionThreshold = Float.MAX_VALUE;
                } else if (FLOAT_NEGATIVE_INFINITY_MARKER_STRING.equals(val)) {
                    autoCorrectionThreshold = Float.NEGATIVE_INFINITY;
                } else {
                    autoCorrectionThreshold = Float.parseFloat(val);
                }
            } else {
                autoCorrectionThreshold = Float.MAX_VALUE;
            }
        } catch (final NumberFormatException e) {
            // Whenever the threshold settings are correct, never come here.
            Log.w(TAG, "Cannot load auto correction threshold setting."
                    + " currentAutoCorrectionSetting: " + currentAutoCorrectionSetting
                    + ", autoCorrectionThresholdValues: "
                    + Arrays.toString(autoCorrectionThresholdValues), e);
            return Float.MAX_VALUE;
        }
        return autoCorrectionThreshold;
    }

    private static boolean needsToShowVoiceInputKey(SharedPreferences prefs, Resources res) {
        final String voiceModeMain = res.getString(R.string.voice_mode_main);
        final String voiceMode = prefs.getString(Settings.PREF_VOICE_MODE_OBSOLETE, voiceModeMain);
        final boolean showsVoiceInputKey = voiceMode == null || voiceMode.equals(voiceModeMain);
        if (!showsVoiceInputKey) {
            // Migrate settings from PREF_VOICE_MODE_OBSOLETE to PREF_VOICE_INPUT_KEY
            // Set voiceModeMain as a value of obsolete voice mode settings.
            prefs.edit().putString(Settings.PREF_VOICE_MODE_OBSOLETE, voiceModeMain).apply();
            // Disable voice input key.
            prefs.edit().putBoolean(Settings.PREF_VOICE_INPUT_KEY, false).apply();
        }
        return prefs.getBoolean(Settings.PREF_VOICE_INPUT_KEY, true);
    }
}
