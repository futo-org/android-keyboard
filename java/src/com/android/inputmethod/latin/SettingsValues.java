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

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.keyboard.internal.KeySpecParser;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * When you call the constructor of this class, you may want to change the current system locale by
 * using {@link LocaleUtils.RunInLocale}.
 */
public final class SettingsValues {
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
    public final boolean mShowsLanguageSwitchKey;
    @SuppressWarnings("unused") // TODO: Use this
    private final String mKeyPreviewPopupDismissDelayRawValue;
    public final boolean mUseContactsDict;
    public final boolean mUseDoubleSpacePeriod;
    // Use bigrams to predict the next word when there is no input for it yet
    public final boolean mBigramPredictionEnabled;
    public final boolean mGestureInputEnabled;
    public final boolean mGesturePreviewTrailEnabled;
    public final boolean mGestureFloatingPreviewTextEnabled;

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
    private final boolean mVoiceKeyEnabled;
    private final boolean mVoiceKeyOnMain;

    public SettingsValues(final SharedPreferences prefs, final Resources res,
            final InputAttributes inputAttributes) {
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
        mHintToSaveText = res.getText(R.string.hint_add_to_dictionary);

        // Store the input attributes
        if (null == inputAttributes) {
            mInputAttributes = new InputAttributes(null, false /* isFullscreenMode */);
        } else {
            mInputAttributes = inputAttributes;
        }

        // Get the settings preferences
        mAutoCap = prefs.getBoolean(Settings.PREF_AUTO_CAP, true);
        mVibrateOn = readVibrationEnabled(prefs, res);
        mSoundOn = prefs.getBoolean(Settings.PREF_SOUND_ON,
                res.getBoolean(R.bool.config_default_sound_enabled));
        mKeyPreviewPopupOn = Settings.readKeyPreviewPopupEnabled(prefs, res);
        final String voiceModeMain = res.getString(R.string.voice_mode_main);
        final String voiceModeOff = res.getString(R.string.voice_mode_off);
        mVoiceMode = prefs.getString(Settings.PREF_VOICE_MODE, voiceModeMain);
        mAutoCorrectionThresholdRawValue = prefs.getString(Settings.PREF_AUTO_CORRECTION_THRESHOLD,
                res.getString(R.string.auto_correction_threshold_mode_index_modest));
        mShowSuggestionsSetting = prefs.getString(Settings.PREF_SHOW_SUGGESTIONS_SETTING,
                res.getString(R.string.prefs_suggestion_visibility_default_value));
        mUsabilityStudyMode = Settings.readUsabilityStudyMode(prefs);
        mIncludesOtherImesInLanguageSwitchList = prefs.getBoolean(
                Settings.PREF_INCLUDE_OTHER_IMES_IN_LANGUAGE_SWITCH_LIST, false);
        mShowsLanguageSwitchKey = Settings.readShowsLanguageSwitchKey(prefs);
        mKeyPreviewPopupDismissDelayRawValue = prefs.getString(
                Settings.PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY,
                Integer.toString(res.getInteger(R.integer.config_key_preview_linger_timeout)));
        mUseContactsDict = prefs.getBoolean(Settings.PREF_KEY_USE_CONTACTS_DICT, true);
        mUseDoubleSpacePeriod = prefs.getBoolean(Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, true);
        mAutoCorrectEnabled = readAutoCorrectEnabled(res, mAutoCorrectionThresholdRawValue);
        mBigramPredictionEnabled = readBigramPredictionEnabled(prefs, res);

        // Compute other readable settings
        mKeypressVibrationDuration = Settings.readKeypressVibrationDuration(prefs, res);
        mKeypressSoundVolume = Settings.readKeypressSoundVolume(prefs, res);
        mKeyPreviewPopupDismissDelay = Settings.readKeyPreviewPopupDismissDelay(prefs, res);
        mAutoCorrectionThreshold = readAutoCorrectionThreshold(res,
                mAutoCorrectionThresholdRawValue);
        mVoiceKeyEnabled = mVoiceMode != null && !mVoiceMode.equals(voiceModeOff);
        mVoiceKeyOnMain = mVoiceMode != null && mVoiceMode.equals(voiceModeMain);
        final boolean gestureInputEnabledByBuildConfig = res.getBoolean(
                R.bool.config_gesture_input_enabled_by_build_config);
        mGestureInputEnabled = gestureInputEnabledByBuildConfig
                && prefs.getBoolean(Settings.PREF_GESTURE_INPUT, true);
        mGesturePreviewTrailEnabled = prefs.getBoolean(Settings.PREF_GESTURE_PREVIEW_TRAIL, true);
        mGestureFloatingPreviewTextEnabled = prefs.getBoolean(
                Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT, true);
        mCorrectionEnabled = mAutoCorrectEnabled && !mInputAttributes.mInputTypeNoAutoCorrect;
        mSuggestionVisibility = createSuggestionVisibility(res);
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

    public boolean isSymbolExcludedFromWordSeparators(final int code) {
        return mSymbolsExcludedFromWordSeparators.contains(String.valueOf((char)code));
    }

    // TODO: use "Phantom" instead of "Weak" in this method name
    public boolean isWeakSpaceStripper(final int code) {
        // TODO: this does not work if the code does not fit in a char
        return mWeakSpaceStrippers.contains(String.valueOf((char)code));
    }

    // TODO: use "Phantom" instead of "Weak" in this method name
    public boolean isWeakSpaceSwapper(final int code) {
        // TODO: this does not work if the code does not fit in a char
        return mWeakSpaceSwappers.contains(String.valueOf((char)code));
    }

    public boolean isPhantomSpacePromotingSymbol(final int code) {
        // TODO: this does not work if the code does not fit in a char
        return mPhantomSpacePromotingSymbols.contains(String.valueOf((char)code));
    }

    public boolean shouldInsertSpacesAutomatically() {
        return mInputAttributes.mShouldInsertSpacesAutomatically;
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
                puncList.add(new SuggestedWordInfo(KeySpecParser.getLabel(puncSpec),
                        SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_HARDCODED,
                        Dictionary.TYPE_HARDCODED));
            }
        }
        return new SuggestedWords(puncList,
                false /* typedWordValid */,
                false /* hasAutoCorrectionCandidate */,
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

    private int createSuggestionVisibility(final Resources res) {
        final String suggestionVisiblityStr = mShowSuggestionsSetting;
        for (int visibility : SUGGESTION_VISIBILITY_VALUE_ARRAY) {
            if (suggestionVisiblityStr.equals(res.getString(visibility))) {
                return visibility;
            }
        }
        throw new RuntimeException("Bug: visibility string is not configured correctly");
    }

    private static boolean readVibrationEnabled(final SharedPreferences prefs,
            final Resources res) {
        final boolean hasVibrator = AudioAndHapticFeedbackManager.getInstance().hasVibrator();
        return hasVibrator && prefs.getBoolean(Settings.PREF_VIBRATE_ON,
                res.getBoolean(R.bool.config_default_vibration_enabled));
    }

    private static boolean readAutoCorrectEnabled(final Resources res,
            final String currentAutoCorrectionSetting) {
        final String autoCorrectionOff = res.getString(
                R.string.auto_correction_threshold_mode_index_off);
        return !currentAutoCorrectionSetting.equals(autoCorrectionOff);
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
}
