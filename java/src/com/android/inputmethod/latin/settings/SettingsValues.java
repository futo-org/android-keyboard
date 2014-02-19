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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.compat.AppWorkaroundsUtils;
import com.android.inputmethod.latin.InputAttributes;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.RichInputMethodManager;
import com.android.inputmethod.latin.utils.AsyncResultHolder;
import com.android.inputmethod.latin.utils.ResourceUtils;
import com.android.inputmethod.latin.utils.TargetPackageInfoGetterTask;

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
    private static final int TIMEOUT_TO_GET_TARGET_PACKAGE = 5; // seconds

    // From resources:
    public final SpacingAndPunctuations mSpacingAndPunctuations;
    public final int mDelayUpdateOldSuggestions;

    // From preferences, in the same order as xml/prefs.xml:
    public final boolean mAutoCap;
    public final boolean mVibrateOn;
    public final boolean mSoundOn;
    public final boolean mKeyPreviewPopupOn;
    public final boolean mShowsVoiceInputKey;
    public final boolean mIncludesOtherImesInLanguageSwitchList;
    public final boolean mShowsLanguageSwitchKey;
    public final boolean mUseContactsDict;
    public final boolean mUsePersonalizedDicts;
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
    public final boolean mUseOnlyPersonalizationDictionaryForDebug;
    public final int mDisplayOrientation;
    private final AsyncResultHolder<AppWorkaroundsUtils> mAppWorkarounds;

    // Setting values for additional features
    public final int[] mAdditionalFeaturesSettingValues =
            new int[AdditionalFeaturesSettingUtils.ADDITIONAL_FEATURES_SETTINGS_SIZE];

    // Debug settings
    public final boolean mIsInternal;
    public final int mKeyPreviewShowUpDuration;
    public final int mKeyPreviewDismissDuration;
    public final float mKeyPreviewShowUpStartScale;
    public final float mKeyPreviewDismissEndScale;

    public SettingsValues(final Context context, final SharedPreferences prefs, final Resources res,
            final InputAttributes inputAttributes) {
        mLocale = res.getConfiguration().locale;
        // Get the resources
        mDelayUpdateOldSuggestions = res.getInteger(R.integer.config_delay_update_old_suggestions);
        mSpacingAndPunctuations = new SpacingAndPunctuations(res);

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
        mUsePersonalizedDicts = prefs.getBoolean(Settings.PREF_USE_PERSONALIZED_DICTS, false);
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
        mKeyPreviewShowUpDuration = Settings.readKeyPreviewAnimationDuration(
                prefs, DebugSettings.PREF_KEY_PREVIEW_SHOW_UP_DURATION,
                res.getInteger(R.integer.config_key_preview_show_up_duration));
        mKeyPreviewDismissDuration = Settings.readKeyPreviewAnimationDuration(
                prefs, DebugSettings.PREF_KEY_PREVIEW_DISMISS_DURATION,
                res.getInteger(R.integer.config_key_preview_dismiss_duration));
        mKeyPreviewShowUpStartScale = Settings.readKeyPreviewAnimationScale(
                prefs, DebugSettings.PREF_KEY_PREVIEW_SHOW_UP_START_SCALE,
                ResourceUtils.getFloatFromFraction(
                        res, R.fraction.config_key_preview_show_up_start_scale));
        mKeyPreviewDismissEndScale = Settings.readKeyPreviewAnimationScale(
                prefs, DebugSettings.PREF_KEY_PREVIEW_DISMISS_END_SCALE,
                ResourceUtils.getFloatFromFraction(
                        res, R.fraction.config_key_preview_dismiss_end_scale));
        mUseOnlyPersonalizationDictionaryForDebug = prefs.getBoolean(
                DebugSettings.PREF_USE_ONLY_PERSONALIZATION_DICTIONARY_FOR_DEBUG, false);
        mDisplayOrientation = res.getConfiguration().orientation;
        mAppWorkarounds = new AsyncResultHolder<AppWorkaroundsUtils>();
        final PackageInfo packageInfo = TargetPackageInfoGetterTask.getCachedPackageInfo(
                mInputAttributes.mTargetApplicationPackageName);
        if (null != packageInfo) {
            mAppWorkarounds.set(new AppWorkaroundsUtils(packageInfo));
        } else {
            new TargetPackageInfoGetterTask(context, mAppWorkarounds)
                    .execute(mInputAttributes.mTargetApplicationPackageName);
        }
    }

    public boolean isApplicationSpecifiedCompletionsOn() {
        return mInputAttributes.mApplicationSpecifiedCompletionOn;
    }

    public boolean isSuggestionsRequested() {
        return mInputAttributes.mIsSettingsSuggestionStripOn
                && (mCorrectionEnabled || isSuggestionStripVisible());
    }

    public boolean isSuggestionStripVisible() {
        return (mSuggestionVisibility == SUGGESTION_VISIBILITY_SHOW_VALUE)
                || (mSuggestionVisibility == SUGGESTION_VISIBILITY_SHOW_ONLY_PORTRAIT_VALUE
                        && mDisplayOrientation == Configuration.ORIENTATION_PORTRAIT);
    }

    public boolean isWordSeparator(final int code) {
        return mSpacingAndPunctuations.isWordSeparator(code);
    }

    public boolean isWordConnector(final int code) {
        return mSpacingAndPunctuations.isWordConnector(code);
    }

    public boolean isWordCodePoint(final int code) {
        return Character.isLetter(code) || isWordConnector(code);
    }

    public boolean isUsuallyPrecededBySpace(final int code) {
        return mSpacingAndPunctuations.isUsuallyPrecededBySpace(code);
    }

    public boolean isUsuallyFollowedBySpace(final int code) {
        return mSpacingAndPunctuations.isUsuallyFollowedBySpace(code);
    }

    public boolean shouldInsertSpacesAutomatically() {
        return mInputAttributes.mShouldInsertSpacesAutomatically;
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

    public boolean hasSameOrientation(final Configuration configuration) {
        return mDisplayOrientation == configuration.orientation;
    }

    public boolean isBeforeJellyBean() {
        final AppWorkaroundsUtils appWorkaroundUtils
                = mAppWorkarounds.get(null, TIMEOUT_TO_GET_TARGET_PACKAGE);
        return null == appWorkaroundUtils ? false : appWorkaroundUtils.isBeforeJellyBean();
    }

    public boolean isBrokenByRecorrection() {
        final AppWorkaroundsUtils appWorkaroundUtils
                = mAppWorkarounds.get(null, TIMEOUT_TO_GET_TARGET_PACKAGE);
        return null == appWorkaroundUtils ? false : appWorkaroundUtils.isBrokenByRecorrection();
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

    private static boolean needsToShowVoiceInputKey(final SharedPreferences prefs,
            final Resources res) {
        if (!prefs.contains(Settings.PREF_VOICE_INPUT_KEY)) {
            // Migrate preference from {@link Settings#PREF_VOICE_MODE_OBSOLETE} to
            // {@link Settings#PREF_VOICE_INPUT_KEY}.
            final String voiceModeMain = res.getString(R.string.voice_mode_main);
            final String voiceMode = prefs.getString(
                    Settings.PREF_VOICE_MODE_OBSOLETE, voiceModeMain);
            final boolean shouldShowVoiceInputKey = voiceModeMain.equals(voiceMode);
            prefs.edit().putBoolean(Settings.PREF_VOICE_INPUT_KEY, shouldShowVoiceInputKey).apply();
        }
        // Remove the obsolete preference if exists.
        if (prefs.contains(Settings.PREF_VOICE_MODE_OBSOLETE)) {
            prefs.edit().remove(Settings.PREF_VOICE_MODE_OBSOLETE).apply();
        }
        return prefs.getBoolean(Settings.PREF_VOICE_INPUT_KEY, true);
    }

    public String dump() {
        final StringBuilder sb = new StringBuilder("Current settings :");
        sb.append("\n   mSpacingAndPunctuations = ");
        sb.append("" + mSpacingAndPunctuations.dump());
        sb.append("\n   mDelayUpdateOldSuggestions = ");
        sb.append("" + mDelayUpdateOldSuggestions);
        sb.append("\n   mAutoCap = ");
        sb.append("" + mAutoCap);
        sb.append("\n   mVibrateOn = ");
        sb.append("" + mVibrateOn);
        sb.append("\n   mSoundOn = ");
        sb.append("" + mSoundOn);
        sb.append("\n   mKeyPreviewPopupOn = ");
        sb.append("" + mKeyPreviewPopupOn);
        sb.append("\n   mShowsVoiceInputKey = ");
        sb.append("" + mShowsVoiceInputKey);
        sb.append("\n   mIncludesOtherImesInLanguageSwitchList = ");
        sb.append("" + mIncludesOtherImesInLanguageSwitchList);
        sb.append("\n   mShowsLanguageSwitchKey = ");
        sb.append("" + mShowsLanguageSwitchKey);
        sb.append("\n   mUseContactsDict = ");
        sb.append("" + mUseContactsDict);
        sb.append("\n   mUsePersonalizedDicts = ");
        sb.append("" + mUsePersonalizedDicts);
        sb.append("\n   mUseDoubleSpacePeriod = ");
        sb.append("" + mUseDoubleSpacePeriod);
        sb.append("\n   mBlockPotentiallyOffensive = ");
        sb.append("" + mBlockPotentiallyOffensive);
        sb.append("\n   mBigramPredictionEnabled = ");
        sb.append("" + mBigramPredictionEnabled);
        sb.append("\n   mGestureInputEnabled = ");
        sb.append("" + mGestureInputEnabled);
        sb.append("\n   mGestureTrailEnabled = ");
        sb.append("" + mGestureTrailEnabled);
        sb.append("\n   mGestureFloatingPreviewTextEnabled = ");
        sb.append("" + mGestureFloatingPreviewTextEnabled);
        sb.append("\n   mSlidingKeyInputPreviewEnabled = ");
        sb.append("" + mSlidingKeyInputPreviewEnabled);
        sb.append("\n   mPhraseGestureEnabled = ");
        sb.append("" + mPhraseGestureEnabled);
        sb.append("\n   mKeyLongpressTimeout = ");
        sb.append("" + mKeyLongpressTimeout);
        sb.append("\n   mLocale = ");
        sb.append("" + mLocale);
        sb.append("\n   mInputAttributes = ");
        sb.append("" + mInputAttributes);
        sb.append("\n   mKeypressVibrationDuration = ");
        sb.append("" + mKeypressVibrationDuration);
        sb.append("\n   mKeypressSoundVolume = ");
        sb.append("" + mKeypressSoundVolume);
        sb.append("\n   mKeyPreviewPopupDismissDelay = ");
        sb.append("" + mKeyPreviewPopupDismissDelay);
        sb.append("\n   mAutoCorrectEnabled = ");
        sb.append("" + mAutoCorrectEnabled);
        sb.append("\n   mAutoCorrectionThreshold = ");
        sb.append("" + mAutoCorrectionThreshold);
        sb.append("\n   mCorrectionEnabled = ");
        sb.append("" + mCorrectionEnabled);
        sb.append("\n   mSuggestionVisibility = ");
        sb.append("" + mSuggestionVisibility);
        sb.append("\n   mUseOnlyPersonalizationDictionaryForDebug = ");
        sb.append("" + mUseOnlyPersonalizationDictionaryForDebug);
        sb.append("\n   mDisplayOrientation = ");
        sb.append("" + mDisplayOrientation);
        sb.append("\n   mAppWorkarounds = ");
        final AppWorkaroundsUtils awu = mAppWorkarounds.get(null, 0);
        sb.append("" + (null == awu ? "null" : awu.toString()));
        sb.append("\n   mAdditionalFeaturesSettingValues = ");
        sb.append("" + Arrays.toString(mAdditionalFeaturesSettingValues));
        sb.append("\n   mIsInternal = ");
        sb.append("" + mIsInternal);
        sb.append("\n   mKeyPreviewShowUpDuration = ");
        sb.append("" + mKeyPreviewShowUpDuration);
        sb.append("\n   mKeyPreviewDismissDuration = ");
        sb.append("" + mKeyPreviewDismissDuration);
        sb.append("\n   mKeyPreviewShowUpStartScale = ");
        sb.append("" + mKeyPreviewShowUpStartScale);
        sb.append("\n   mKeyPreviewDismissEndScale = ");
        sb.append("" + mKeyPreviewDismissEndScale);
        return sb.toString();
    }
}
