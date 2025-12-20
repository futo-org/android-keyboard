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

package org.futo.inputmethod.latin.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import org.futo.inputmethod.compat.AppWorkaroundsUtils;
import org.futo.inputmethod.latin.InputAttributes;
import org.futo.inputmethod.latin.R;
import org.futo.inputmethod.latin.uix.actions.ActionRegistry;
import org.futo.inputmethod.latin.uix.actions.RegistryKt;
import org.futo.inputmethod.latin.utils.AsyncResultHolder;
import org.futo.inputmethod.latin.utils.ResourceUtils;
import org.futo.inputmethod.latin.utils.TargetPackageInfoGetterTask;

import java.util.Arrays;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * When you call the constructor of this class, you may want to change the current system locale by
 * using {@link org.futo.inputmethod.latin.utils.RunInLocale}.
 */
// Non-final for testing via mock library.
public class SettingsValues {
    private static final String TAG = SettingsValues.class.getSimpleName();
    // "floatMaxValue" and "floatNegativeInfinity" are special marker strings for
    // Float.NEGATIVE_INFINITE and Float.MAX_VALUE. Currently used for auto-correction settings.
    private static final String FLOAT_MAX_VALUE_MARKER_STRING = "floatMaxValue";
    private static final String FLOAT_NEGATIVE_INFINITY_MARKER_STRING = "floatNegativeInfinity";
    private static final int TIMEOUT_TO_GET_TARGET_PACKAGE = 5; // seconds
    public static final float DEFAULT_SIZE_SCALE = 1.0f; // 100%

    // From resources:
    public final SpacingAndPunctuations mSpacingAndPunctuations;
    public final long mDoubleSpacePeriodTimeout;
    // From configuration:
    public final Locale mLocale;
    public final boolean mHasHardwareKeyboard;
    public final int mDisplayOrientation;
    public final boolean mIsRTL;
    // From preferences, in the same order as xml/prefs.xml:
    public final boolean mAutoCap;
    public final boolean mVibrateOn;
    public final boolean mSoundOn;
    public final boolean mKeyPreviewPopupOn;
    public final boolean mShowsVoiceInputKey;
    public final boolean mIncludesOtherImesInLanguageSwitchList;
    public final boolean mShowsActionKey;
    public final int mActionKeyId;

    public final boolean mUseContactsDict;
    public final boolean mUsePersonalizedDicts;
    public final boolean mUseDoubleSpacePeriod;
    public final boolean mBlockPotentiallyOffensive;
    // Use bigrams to predict the next word when there is no input for it yet
    public final boolean mBigramPredictionEnabled;
    public final boolean mTransformerPredictionEnabled;
    public final boolean mGestureInputEnabled;
    public final boolean mGestureTrailEnabled;
    public final boolean mGestureFloatingPreviewTextEnabled;
    public final boolean mSlidingKeyInputPreviewEnabled;
    public final int mKeyLongpressTimeout;
    public final boolean mEnableEmojiAltPhysicalKey;
    public final boolean mShowAppIcon;
    public final boolean mIsShowAppIconSettingInPreferences;
    public final boolean mCloudSyncEnabled;
    public final boolean mEnableMetricsLogging;
    public final boolean mShouldShowLxxSuggestionUi;
    public final boolean mIsNumberRowEnabled;
    public final boolean mIsNumberRowEnabledByUser;
    public final boolean mUseLocalNumbers;
    public final boolean mIsArrowRowEnabled;
    public final boolean mIsUsingAlternativePeriodKey;
    public final boolean mUseDictionaryKeyBoosting;
    public final int mScreenMetrics;

    public final boolean mBackspaceDeletesInsertedText;
    public final boolean mBackspaceUndoesAutocorrect;
    public final int mSpacebarMode;
    public final int mBackspaceMode;
    public final int mNumberRowMode;
    public final int mAltSpacesMode;

    // From the input box
    @Nonnull
    public final InputAttributes mInputAttributes;

    // Deduced settings
    public final int mKeypressVibrationDuration;
    public final float mKeypressSoundVolume;
    public final int mKeyPreviewPopupDismissDelay;
    private final boolean mAutoCorrectEnabled;
    public final float mAutoCorrectionThreshold;
    public final float mPlausibilityThreshold;
    public final boolean mAutoCorrectionEnabledPerUserSettings;
    public final boolean mAutoCorrectionEnabledPerTextFieldSettings;
    private final boolean mSuggestionsEnabledPerUserSettings;
    private final AsyncResultHolder<AppWorkaroundsUtils> mAppWorkarounds;

    // Debug settings
    public final boolean mIsInternal;
    public final boolean mHasCustomKeyPreviewAnimationParams;
    public final boolean mHasKeyboardResize;
    public final float mKeyboardHeightScale;
    public final int mKeyPreviewShowUpDuration;
    public final int mKeyPreviewDismissDuration;
    public final float mKeyPreviewShowUpStartXScale;
    public final float mKeyPreviewShowUpStartYScale;
    public final float mKeyPreviewDismissEndXScale;
    public final float mKeyPreviewDismissEndYScale;

    @Nullable public final String mAccount;

    public SettingsValues(final Context context, final SharedPreferences prefs, final Resources res,
            @Nonnull final InputAttributes inputAttributes) {
        if(inputAttributes.mLocaleOverride != null) {
            mLocale = inputAttributes.mLocaleOverride;
        } else {
            mLocale = res.getConfiguration().locale;
        }
        mIsRTL = TextUtils.getLayoutDirectionFromLocale(mLocale) == View.LAYOUT_DIRECTION_RTL;
        // Get the resources
        mSpacingAndPunctuations = new SpacingAndPunctuations(res);

        // Store the input attributes
        mInputAttributes = inputAttributes;

        // Get the settings preferences
        mAutoCap = prefs.getBoolean(Settings.PREF_AUTO_CAP, true);
        mVibrateOn = Settings.readVibrationEnabled(prefs, res);
        mSoundOn = Settings.readKeypressSoundEnabled(prefs, res);
        mKeyPreviewPopupOn = Settings.readKeyPreviewPopupEnabled(prefs, res);
        mSlidingKeyInputPreviewEnabled = prefs.getBoolean(
                DebugSettings.PREF_SLIDING_KEY_INPUT_PREVIEW, true);
        mShowsVoiceInputKey = true;
        mIncludesOtherImesInLanguageSwitchList = Settings.ENABLE_SHOW_LANGUAGE_SWITCH_KEY_SETTINGS
                ? prefs.getBoolean(Settings.PREF_INCLUDE_OTHER_IMES_IN_LANGUAGE_SWITCH_LIST, false)
                : true /* forcibly */;
        mActionKeyId = ActionRegistry.INSTANCE.actionStringIdToIdx(
                prefs.getString(
                        Settings.PREF_ACTION_KEY_ID,
                        RegistryKt.getDefaultActionKey()
                ));
        mShowsActionKey = mActionKeyId != -1;
        mIsNumberRowEnabledByUser = prefs.getBoolean(Settings.PREF_ENABLE_NUMBER_ROW, false);
        mIsNumberRowEnabled = mIsNumberRowEnabledByUser
                || (inputAttributes.mIsPasswordField && !inputAttributes.mIsNumericalPasswordField)
                || inputAttributes.mIsEmailField;
        mUseLocalNumbers = !prefs.getBoolean(Settings.PREF_USE_WESTERN_NUMERALS, false);
        mIsArrowRowEnabled = prefs.getBoolean(Settings.PREF_ENABLE_ARROW_ROW, false);
        mIsUsingAlternativePeriodKey = prefs.getBoolean(Settings.PREF_ENABLE_ALT_PERIOD_KEY, false) && SettingsValues.altPeriodKeyAllowedForLocale(mLocale);
        mUseDictionaryKeyBoosting = prefs.getBoolean(Settings.PREF_USE_DICT_KEY_BOOSTING, true);
        mUseContactsDict = prefs.getBoolean(Settings.PREF_KEY_USE_CONTACTS_DICT, true);
        mUsePersonalizedDicts = prefs.getBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true);
        mUseDoubleSpacePeriod = prefs.getBoolean(Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, true)
                && inputAttributes.mIsGeneralTextInput;
        mBlockPotentiallyOffensive = Settings.readBlockPotentiallyOffensive(prefs, res);
        mAutoCorrectEnabled = Settings.readAutoCorrectEnabled(prefs, res);
        final String autoCorrectionThresholdRawValue = mAutoCorrectEnabled
                ? res.getString(R.string.auto_correction_threshold_mode_index_modest)
                : res.getString(R.string.auto_correction_threshold_mode_index_off);
        mTransformerPredictionEnabled = readTransformerPredictionEnabled(prefs, res);
        mBigramPredictionEnabled = readBigramPredictionEnabled(prefs, res) || mTransformerPredictionEnabled;
        mDoubleSpacePeriodTimeout = res.getInteger(R.integer.config_double_space_period_timeout);
        mHasHardwareKeyboard = Settings.readHasHardwareKeyboard(res.getConfiguration());
        mEnableMetricsLogging = prefs.getBoolean(Settings.PREF_ENABLE_METRICS_LOGGING, true);
        mScreenMetrics = Settings.readScreenMetrics(res);

        mBackspaceDeletesInsertedText = prefs.getBoolean(Settings.PREF_BACKSPACE_DELETE_INSERTED_TEXT, true);
        mBackspaceUndoesAutocorrect = prefs.getBoolean(Settings.PREF_BACKSPACE_UNDO_AUTOCORRECT, true);

        mSpacebarMode = prefs.getInt(Settings.PREF_SPACEBAR_MODE, Settings.SPACEBAR_MODE_SWIPE_CURSOR);
        mBackspaceMode = prefs.getInt(Settings.PREF_BACKSPACE_MODE, Settings.BACKSPACE_MODE_CHARACTERS);
        mNumberRowMode = mIsNumberRowEnabledByUser ?
                prefs.getInt(Settings.PREF_NUMBER_ROW_MODE, Settings.NUMBER_ROW_MODE_DEFAULT)
                : Settings.NUMBER_ROW_MODE_DEFAULT;
        mAltSpacesMode = prefs.getInt(Settings.PREF_ALT_SPACES_MODE, Settings.DEFAULT_ALT_SPACES_MODE);

        mShouldShowLxxSuggestionUi = Settings.SHOULD_SHOW_LXX_SUGGESTION_UI
                && prefs.getBoolean(DebugSettings.PREF_SHOULD_SHOW_LXX_SUGGESTION_UI, true);
        // Compute other readable settings
        mKeyLongpressTimeout = Settings.readKeyLongpressTimeout(prefs, res);
        mKeypressVibrationDuration = Settings.readKeypressVibrationDuration(prefs, res);
        mKeypressSoundVolume = Settings.readKeypressSoundVolume(prefs, res);
        mKeyPreviewPopupDismissDelay = Settings.readKeyPreviewPopupDismissDelay(prefs, res);
        mEnableEmojiAltPhysicalKey = prefs.getBoolean(
                Settings.PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY, true);
        mShowAppIcon = Settings.readShowSetupWizardIcon(prefs, context);
        mIsShowAppIconSettingInPreferences = prefs.contains(Settings.PREF_SHOW_SETUP_WIZARD_ICON);
        mAutoCorrectionThreshold = readAutoCorrectionThreshold(res,
                autoCorrectionThresholdRawValue);
        mPlausibilityThreshold = Settings.readPlausibilityThreshold(res);
        mGestureInputEnabled = Settings.readGestureInputEnabled(prefs, res);
        mGestureTrailEnabled = prefs.getBoolean(Settings.PREF_GESTURE_PREVIEW_TRAIL, true);
        mCloudSyncEnabled = prefs.getBoolean(LocalSettingsConstants.PREF_ENABLE_CLOUD_SYNC, false);
        mAccount = prefs.getString(LocalSettingsConstants.PREF_ACCOUNT_NAME,
                null /* default */);
        mGestureFloatingPreviewTextEnabled = false && !mInputAttributes.mDisableGestureFloatingPreviewText
                && prefs.getBoolean(Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT, true);
        mAutoCorrectionEnabledPerUserSettings = mAutoCorrectEnabled
                && !mInputAttributes.mInputTypeNoAutoCorrect;
        mAutoCorrectionEnabledPerTextFieldSettings = !mInputAttributes.mInputTypeNoAutoCorrect;
        mSuggestionsEnabledPerUserSettings = readSuggestionsEnabled(prefs);
        mIsInternal = Settings.isInternal(prefs);
        mHasCustomKeyPreviewAnimationParams = prefs.getBoolean(
                DebugSettings.PREF_HAS_CUSTOM_KEY_PREVIEW_ANIMATION_PARAMS, false);
        mHasKeyboardResize = prefs.getBoolean(DebugSettings.PREF_RESIZE_KEYBOARD, false);
        mKeyboardHeightScale = Settings.readKeyboardHeight(prefs, DEFAULT_SIZE_SCALE);
        mKeyPreviewShowUpDuration = Settings.readKeyPreviewAnimationDuration(
                prefs, DebugSettings.PREF_KEY_PREVIEW_SHOW_UP_DURATION,
                res.getInteger(R.integer.config_key_preview_show_up_duration));
        mKeyPreviewDismissDuration = Settings.readKeyPreviewAnimationDuration(
                prefs, DebugSettings.PREF_KEY_PREVIEW_DISMISS_DURATION,
                res.getInteger(R.integer.config_key_preview_dismiss_duration));
        final float defaultKeyPreviewShowUpStartScale = ResourceUtils.getFloatFromFraction(
                res, R.fraction.config_key_preview_show_up_start_scale);
        final float defaultKeyPreviewDismissEndScale = ResourceUtils.getFloatFromFraction(
                res, R.fraction.config_key_preview_dismiss_end_scale);
        mKeyPreviewShowUpStartXScale = Settings.readKeyPreviewAnimationScale(
                prefs, DebugSettings.PREF_KEY_PREVIEW_SHOW_UP_START_X_SCALE,
                defaultKeyPreviewShowUpStartScale);
        mKeyPreviewShowUpStartYScale = Settings.readKeyPreviewAnimationScale(
                prefs, DebugSettings.PREF_KEY_PREVIEW_SHOW_UP_START_Y_SCALE,
                defaultKeyPreviewShowUpStartScale);
        mKeyPreviewDismissEndXScale = Settings.readKeyPreviewAnimationScale(
                prefs, DebugSettings.PREF_KEY_PREVIEW_DISMISS_END_X_SCALE,
                defaultKeyPreviewDismissEndScale);
        mKeyPreviewDismissEndYScale = Settings.readKeyPreviewAnimationScale(
                prefs, DebugSettings.PREF_KEY_PREVIEW_DISMISS_END_Y_SCALE,
                defaultKeyPreviewDismissEndScale);
        mDisplayOrientation = res.getConfiguration().orientation;
        mAppWorkarounds = new AsyncResultHolder<>("AppWorkarounds");
        final PackageInfo packageInfo = TargetPackageInfoGetterTask.getCachedPackageInfo(
                mInputAttributes.mTargetApplicationPackageName);
        if (null != packageInfo) {
            mAppWorkarounds.set(new AppWorkaroundsUtils(packageInfo));
        } else {
            new TargetPackageInfoGetterTask(context, mAppWorkarounds)
                    .execute(mInputAttributes.mTargetApplicationPackageName);
        }
    }

    public boolean isMetricsLoggingEnabled() {
        return mEnableMetricsLogging;
    }

    public boolean isApplicationSpecifiedCompletionsOn() {
        return mInputAttributes.mApplicationSpecifiedCompletionOn;
    }

    public boolean needsToLookupSuggestions() {
        return mInputAttributes.mShouldShowSuggestions
                && (mAutoCorrectionEnabledPerUserSettings || isSuggestionsEnabledPerUserSettings());
    }

    public boolean isSuggestionsEnabledPerUserSettings() {
        return mSuggestionsEnabledPerUserSettings;
    }

    public boolean isPersonalizationEnabled() {
        return mUsePersonalizedDicts;
    }

    public boolean isWordSeparator(final int code) {
        return mSpacingAndPunctuations.isWordSeparator(code);
    }

    public boolean isWordConnector(final int code) {
        return mSpacingAndPunctuations.isWordConnector(code);
    }

    public boolean isWordCodePoint(final int code) {
        int type = Character.getType(code);
        return Character.isLetter(code) || isWordConnector(code)
                || Character.NON_SPACING_MARK == type
                || Character.ENCLOSING_MARK == type
                || Character.COMBINING_SPACING_MARK == type
                // A digit can be a word codepoint because the user may have mistapped a number
                // instead of a letter, in which case the digit should be considered part of a word.
                || (Character.isDigit(code) && mIsNumberRowEnabled);
    }

    public boolean isUsuallyPrecededBySpace(final int code) {
        return mSpacingAndPunctuations.isUsuallyPrecededBySpace(code);
    }

    public boolean isOptionallyPrecededBySpace(final int code) {
        return mSpacingAndPunctuations.isOptionallyPrecededBySpace(code);
    }

    public boolean isUsuallyFollowedBySpace(final int code) {
        return mSpacingAndPunctuations.isUsuallyFollowedBySpace(code);
    }

    public boolean isUsuallyFollowedBySpaceIffPrecededBySpace(final int code) {
        return mSpacingAndPunctuations.isUsuallyFollowedBySpaceIffPrecededBySpace(code);
    }

    public boolean shouldInsertSpacesAutomatically() {
        return mInputAttributes.mShouldInsertSpacesAutomatically;
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

    private static final String SUGGESTIONS_VISIBILITY_HIDE_VALUE_OBSOLETE = "2";

    private static boolean readSuggestionsEnabled(final SharedPreferences prefs) {
        if (prefs.contains(Settings.PREF_SHOW_SUGGESTIONS_SETTING_OBSOLETE)) {
            final boolean alwaysHide = SUGGESTIONS_VISIBILITY_HIDE_VALUE_OBSOLETE.equals(
                    prefs.getString(Settings.PREF_SHOW_SUGGESTIONS_SETTING_OBSOLETE, null));
            prefs.edit()
                    .remove(Settings.PREF_SHOW_SUGGESTIONS_SETTING_OBSOLETE)
                    .putBoolean(Settings.PREF_SHOW_SUGGESTIONS, !alwaysHide)
                    .apply();
        }
        return prefs.getBoolean(Settings.PREF_SHOW_SUGGESTIONS, true);
    }

    private static boolean readBigramPredictionEnabled(final SharedPreferences prefs,
            final Resources res) {
        return prefs.getBoolean(Settings.PREF_BIGRAM_PREDICTIONS, res.getBoolean(
                R.bool.config_default_next_word_prediction));
    }

    private static boolean readTransformerPredictionEnabled(final SharedPreferences prefs,
            final Resources res) {
        return prefs.getBoolean(Settings.PREF_KEY_USE_TRANSFORMER_LM, true);
    }

    private static float readAutoCorrectionThreshold(final Resources res,
            final String currentAutoCorrectionSetting) {
        final String[] autoCorrectionThresholdValues = res.getStringArray(
                R.array.auto_correction_threshold_values);
        // When autoCorrectionThreshold is greater than 1.0, it's like auto correction is off.
        final float autoCorrectionThreshold;
        try {
            final int arrayIndex = Integer.parseInt(currentAutoCorrectionSetting);
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
        // Migrate preference from {@link Settings#PREF_VOICE_MODE_OBSOLETE} to
        // {@link Settings#PREF_VOICE_INPUT_KEY}.
        if (prefs.contains(Settings.PREF_VOICE_MODE_OBSOLETE)) {
            final String voiceModeMain = res.getString(R.string.voice_mode_main);
            final String voiceMode = prefs.getString(
                    Settings.PREF_VOICE_MODE_OBSOLETE, voiceModeMain);
            final boolean shouldShowVoiceInputKey = voiceModeMain.equals(voiceMode);
            prefs.edit()
                    .putBoolean(Settings.PREF_VOICE_INPUT_KEY, shouldShowVoiceInputKey)
                    // Remove the obsolete preference if exists.
                    .remove(Settings.PREF_VOICE_MODE_OBSOLETE)
                    .apply();
        }
        return prefs.getBoolean(Settings.PREF_VOICE_INPUT_KEY, true);
    }

    private static boolean altPeriodKeyAllowedForLocale(Locale locale) {
        String lang = locale.getLanguage();
        if(lang.equals("ar") || lang.equals("hi") || lang.equals("ckb") || lang.equals("fa") || lang.equals("new") || lang.equals("hy") || lang.equals("my"))
            return false;
        else
            return true;
    }

    public String dump() {
        final StringBuilder sb = new StringBuilder("Current settings :");
        sb.append("\n   mSpacingAndPunctuations = ");
        sb.append("" + mSpacingAndPunctuations.dump());
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
        sb.append("\n   mShowsActionKey = ");
        sb.append("" + mShowsActionKey);
        sb.append("\n   mActionKeyId = ");
        sb.append("" + mActionKeyId);
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
        sb.append("\n   mTransformerPredictionEnabled = ");
        sb.append("" + mTransformerPredictionEnabled);
        sb.append("\n   mGestureInputEnabled = ");
        sb.append("" + mGestureInputEnabled);
        sb.append("\n   mGestureTrailEnabled = ");
        sb.append("" + mGestureTrailEnabled);
        sb.append("\n   mGestureFloatingPreviewTextEnabled = ");
        sb.append("" + mGestureFloatingPreviewTextEnabled);
        sb.append("\n   mSlidingKeyInputPreviewEnabled = ");
        sb.append("" + mSlidingKeyInputPreviewEnabled);
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
        sb.append("\n   mAutoCorrectionEnabledPerUserSettings = ");
        sb.append("" + mAutoCorrectionEnabledPerUserSettings);
        sb.append("\n   mSuggestionsEnabledPerUserSettings = ");
        sb.append("" + mSuggestionsEnabledPerUserSettings);
        sb.append("\n   mDisplayOrientation = ");
        sb.append("" + mDisplayOrientation);
        sb.append("\n   mAppWorkarounds = ");
        final AppWorkaroundsUtils awu = mAppWorkarounds.get(null, 0);
        sb.append("" + (null == awu ? "null" : awu.toString()));
        sb.append("\n   mIsInternal = ");
        sb.append("" + mIsInternal);
        sb.append("\n   mKeyPreviewShowUpDuration = ");
        sb.append("" + mKeyPreviewShowUpDuration);
        sb.append("\n   mKeyPreviewDismissDuration = ");
        sb.append("" + mKeyPreviewDismissDuration);
        sb.append("\n   mKeyPreviewShowUpStartScaleX = ");
        sb.append("" + mKeyPreviewShowUpStartXScale);
        sb.append("\n   mKeyPreviewShowUpStartScaleY = ");
        sb.append("" + mKeyPreviewShowUpStartYScale);
        sb.append("\n   mKeyPreviewDismissEndScaleX = ");
        sb.append("" + mKeyPreviewDismissEndXScale);
        sb.append("\n   mKeyPreviewDismissEndScaleY = ");
        sb.append("" + mKeyPreviewDismissEndYScale);

        sb.append("\n   mDoubleSpacePeriodTimeout = ");
        sb.append("" + mDoubleSpacePeriodTimeout);
        sb.append("\n   mHasHardwareKeyboard = ");
        sb.append("" + mHasHardwareKeyboard);
        sb.append("\n   mIsRTL = ");
        sb.append("" + mIsRTL);
        sb.append("\n   mEnableEmojiAltPhysicalKey = ");
        sb.append("" + mEnableEmojiAltPhysicalKey);
        sb.append("\n   mShowAppIcon = ");
        sb.append("" + mShowAppIcon);
        sb.append("\n   mIsShowAppIconSettingInPreferences = ");
        sb.append("" + mIsShowAppIconSettingInPreferences);
        sb.append("\n   mCloudSyncEnabled = ");
        sb.append("" + mCloudSyncEnabled);
        sb.append("\n   mEnableMetricsLogging = ");
        sb.append("" + mEnableMetricsLogging);
        sb.append("\n   mShouldShowLxxSuggestionUi = ");
        sb.append("" + mShouldShowLxxSuggestionUi);
        sb.append("\n   mIsNumberRowEnabled = ");
        sb.append("" + mIsNumberRowEnabled);
        sb.append("\n   mIsNumberRowEnabledByUser = ");
        sb.append("" + mIsNumberRowEnabledByUser);
        sb.append("\n   mUseLocalNumbers = ");
        sb.append("" + mUseLocalNumbers);
        sb.append("\n   mIsArrowRowEnabled = ");
        sb.append("" + mIsArrowRowEnabled);
        sb.append("\n   mIsUsingAlternativePeriodKey = ");
        sb.append("" + mIsUsingAlternativePeriodKey);
        sb.append("\n   mUseDictionaryKeyBoosting = ");
        sb.append("" + mUseDictionaryKeyBoosting);
        sb.append("\n   mScreenMetrics = ");
        sb.append("" + mScreenMetrics);
        sb.append("\n   mBackspaceDeletesInsertedText = ");
        sb.append("" + mBackspaceDeletesInsertedText);
        sb.append("\n   mBackspaceUndoesAutocorrect = ");
        sb.append("" + mBackspaceUndoesAutocorrect);
        sb.append("\n   mSpacebarMode = ");
        sb.append("" + mSpacebarMode);
        sb.append("\n   mBackspaceMode = ");
        sb.append("" + mBackspaceMode);
        sb.append("\n   mNumberRowMode = ");
        sb.append("" + mNumberRowMode);
        sb.append("\n   mAltSpacesMode = ");
        sb.append("" + mAltSpacesMode);
        sb.append("\n   mPlausibilityThreshold = ");
        sb.append("" + mPlausibilityThreshold);
        sb.append("\n   mAutoCorrectionEnabledPerTextFieldSettings = ");
        sb.append("" + mAutoCorrectionEnabledPerTextFieldSettings);
        sb.append("\n   mHasCustomKeyPreviewAnimationParams = ");
        sb.append("" + mHasCustomKeyPreviewAnimationParams);
        sb.append("\n   mHasKeyboardResize = ");
        sb.append("" + mHasKeyboardResize);
        sb.append("\n   mKeyboardHeightScale = ");
        sb.append("" + mKeyboardHeightScale);
        return sb.toString();
    }
}
