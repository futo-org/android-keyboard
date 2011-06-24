/*
 * Copyright (C) 2008 The Android Open Source Project
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

import com.android.inputmethod.compat.CompatUtils;
import com.android.inputmethod.compat.InputMethodManagerCompatWrapper;
import com.android.inputmethod.compat.InputMethodServiceCompatWrapper;
import com.android.inputmethod.deprecated.VoiceProxy;
import com.android.inputmethod.compat.VibratorCompatWrapper;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.backup.BackupManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.speech.SpeechRecognizer;
import android.text.AutoText;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.widget.TextView;

import java.util.Arrays;
import java.util.Locale;

public class Settings extends PreferenceActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener,
        DialogInterface.OnDismissListener, OnPreferenceClickListener {
    private static final String TAG = "Settings";

    public static final String PREF_GENERAL_SETTINGS_KEY = "general_settings";
    public static final String PREF_VIBRATE_ON = "vibrate_on";
    public static final String PREF_SOUND_ON = "sound_on";
    public static final String PREF_KEY_PREVIEW_POPUP_ON = "popup_on";
    public static final String PREF_RECORRECTION_ENABLED = "recorrection_enabled";
    public static final String PREF_AUTO_CAP = "auto_cap";
    public static final String PREF_SETTINGS_KEY = "settings_key";
    public static final String PREF_VOICE_SETTINGS_KEY = "voice_mode";
    public static final String PREF_INPUT_LANGUAGE = "input_language";
    public static final String PREF_SELECTED_LANGUAGES = "selected_languages";
    public static final String PREF_SUBTYPES = "subtype_settings";

    public static final String PREF_CORRECTION_SETTINGS_KEY = "correction_settings";
    public static final String PREF_QUICK_FIXES = "quick_fixes";
    public static final String PREF_SHOW_SUGGESTIONS_SETTING = "show_suggestions_setting";
    public static final String PREF_AUTO_CORRECTION_THRESHOLD = "auto_correction_threshold";
    public static final String PREF_DEBUG_SETTINGS = "debug_settings";

    public static final String PREF_NGRAM_SETTINGS_KEY = "ngram_settings";
    public static final String PREF_BIGRAM_SUGGESTIONS = "bigram_suggestion";
    public static final String PREF_BIGRAM_PREDICTIONS = "bigram_prediction";

    public static final String PREF_MISC_SETTINGS_KEY = "misc_settings";

    public static final String PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY =
            "pref_key_preview_popup_dismiss_delay";
    public static final String PREF_KEY_USE_CONTACTS_DICT =
            "pref_key_use_contacts_dict";

    public static final String PREF_USABILITY_STUDY_MODE = "usability_study_mode";

    // Dialog ids
    private static final int VOICE_INPUT_CONFIRM_DIALOG = 0;

    public static class Values {
        // From resources:
        public final boolean mSwipeDownDismissKeyboardEnabled;
        public final int mDelayBeforeFadeoutLanguageOnSpacebar;
        public final int mDelayUpdateSuggestions;
        public final int mDelayUpdateOldSuggestions;
        public final int mDelayUpdateShiftState;
        public final int mDurationOfFadeoutLanguageOnSpacebar;
        public final float mFinalFadeoutFactorOfLanguageOnSpacebar;
        public final long mDoubleSpacesTurnIntoPeriodTimeout;
        public final String mWordSeparators;
        public final String mMagicSpaceStrippers;
        public final String mMagicSpaceSwappers;
        public final String mSuggestPuncs;
        public final SuggestedWords mSuggestPuncList;

        // From preferences:
        public final boolean mSoundOn; // Sound setting private to Latin IME (see mSilentModeOn)
        public final boolean mVibrateOn;
        public final boolean mKeyPreviewPopupOn;
        public final int mKeyPreviewPopupDismissDelay;
        public final boolean mAutoCap;
        public final boolean mQuickFixes;
        public final boolean mAutoCorrectEnabled;
        public final double mAutoCorrectionThreshold;
        // Suggestion: use bigrams to adjust scores of suggestions obtained from unigram dictionary
        public final boolean mBigramSuggestionEnabled;
        // Prediction: use bigrams to predict the next word when there is no input for it yet
        public final boolean mBigramPredictionEnabled;
        public final boolean mUseContactsDict;

        public Values(final SharedPreferences prefs, final Context context,
                final String localeStr) {
            final Resources res = context.getResources();
            final Locale savedLocale;
            if (null != localeStr) {
                final Locale keyboardLocale = Utils.constructLocaleFromString(localeStr);
                savedLocale = Utils.setSystemLocale(res, keyboardLocale);
            } else {
                savedLocale = null;
            }

            // Get the resources
            mSwipeDownDismissKeyboardEnabled = res.getBoolean(
                    R.bool.config_swipe_down_dismiss_keyboard_enabled);
            mDelayBeforeFadeoutLanguageOnSpacebar = res.getInteger(
                    R.integer.config_delay_before_fadeout_language_on_spacebar);
            mDelayUpdateSuggestions =
                    res.getInteger(R.integer.config_delay_update_suggestions);
            mDelayUpdateOldSuggestions = res.getInteger(
                    R.integer.config_delay_update_old_suggestions);
            mDelayUpdateShiftState =
                    res.getInteger(R.integer.config_delay_update_shift_state);
            mDurationOfFadeoutLanguageOnSpacebar = res.getInteger(
                    R.integer.config_duration_of_fadeout_language_on_spacebar);
            mFinalFadeoutFactorOfLanguageOnSpacebar = res.getInteger(
                    R.integer.config_final_fadeout_percentage_of_language_on_spacebar) / 100.0f;
            mDoubleSpacesTurnIntoPeriodTimeout = res.getInteger(
                    R.integer.config_double_spaces_turn_into_period_timeout);
            mMagicSpaceStrippers = res.getString(R.string.magic_space_stripping_symbols);
            mMagicSpaceSwappers = res.getString(R.string.magic_space_swapping_symbols);
            String wordSeparators = mMagicSpaceStrippers + mMagicSpaceSwappers
                    + res.getString(R.string.magic_space_promoting_symbols);
            final String notWordSeparators = res.getString(R.string.non_word_separator_symbols);
            for (int i = notWordSeparators.length() - 1; i >= 0; --i) {
                wordSeparators = wordSeparators.replace(notWordSeparators.substring(i, i + 1), "");
            }
            mWordSeparators = wordSeparators;
            mSuggestPuncs = res.getString(R.string.suggested_punctuations);
            // TODO: it would be nice not to recreate this each time we change the configuration
            mSuggestPuncList = createSuggestPuncList(mSuggestPuncs);

            // Get the settings preferences
            final boolean hasVibrator = VibratorCompatWrapper.getInstance(context).hasVibrator();
            mVibrateOn = hasVibrator && prefs.getBoolean(Settings.PREF_VIBRATE_ON, false);
            mSoundOn = prefs.getBoolean(Settings.PREF_SOUND_ON,
                    res.getBoolean(R.bool.config_default_sound_enabled));

            mKeyPreviewPopupOn = isKeyPreviewPopupEnabled(prefs, res);
            mKeyPreviewPopupDismissDelay = getKeyPreviewPopupDismissDelay(prefs, res);
            mAutoCap = prefs.getBoolean(Settings.PREF_AUTO_CAP, true);
            mQuickFixes = isQuickFixesEnabled(prefs, res);

            mAutoCorrectEnabled = isAutoCorrectEnabled(prefs, res);
            mBigramSuggestionEnabled = mAutoCorrectEnabled
                    && isBigramSuggestionEnabled(prefs, res, mAutoCorrectEnabled);
            mBigramPredictionEnabled = mBigramSuggestionEnabled
                    && isBigramPredictionEnabled(prefs, res);

            mAutoCorrectionThreshold = getAutoCorrectionThreshold(prefs, res);

            mUseContactsDict = prefs.getBoolean(Settings.PREF_KEY_USE_CONTACTS_DICT, true);

            Utils.setSystemLocale(res, savedLocale);
        }

        public boolean isSuggestedPunctuation(int code) {
            return mSuggestPuncs.contains(String.valueOf((char)code));
        }

        public boolean isWordSeparator(int code) {
            return mWordSeparators.contains(String.valueOf((char)code));
        }

        public boolean isMagicSpaceStripper(int code) {
            return mMagicSpaceStrippers.contains(String.valueOf((char)code));
        }

        public boolean isMagicSpaceSwapper(int code) {
            return mMagicSpaceSwappers.contains(String.valueOf((char)code));
        }

        // Helper methods
        private static boolean isQuickFixesEnabled(SharedPreferences sp, Resources resources) {
            final boolean showQuickFixesOption = resources.getBoolean(
                    R.bool.config_enable_quick_fixes_option);
            if (!showQuickFixesOption) {
                return isAutoCorrectEnabled(sp, resources);
            }
            return sp.getBoolean(Settings.PREF_QUICK_FIXES, resources.getBoolean(
                    R.bool.config_default_quick_fixes));
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
            return sp.getBoolean(Settings.PREF_KEY_PREVIEW_POPUP_ON,
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
            return sp.getBoolean(Settings.PREF_BIGRAM_SUGGESTIONS, resources.getBoolean(
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

        private static SuggestedWords createSuggestPuncList(final String puncs) {
            SuggestedWords.Builder builder = new SuggestedWords.Builder();
            if (puncs != null) {
                for (int i = 0; i < puncs.length(); i++) {
                    builder.addWord(puncs.subSequence(i, i + 1));
                }
            }
            return builder.build();
        }
    }

    private PreferenceScreen mInputLanguageSelection;
    private CheckBoxPreference mQuickFixes;
    private ListPreference mVoicePreference;
    private ListPreference mSettingsKeyPreference;
    private ListPreference mShowCorrectionSuggestionsPreference;
    private ListPreference mAutoCorrectionThreshold;
    private ListPreference mKeyPreviewPopupDismissDelay;
    // Suggestion: use bigrams to adjust scores of suggestions obtained from unigram dictionary
    private CheckBoxPreference mBigramSuggestion;
    // Prediction: use bigrams to predict the next word when there is no input for it yet
    private CheckBoxPreference mBigramPrediction;
    private Preference mDebugSettingsPreference;
    private boolean mVoiceOn;

    private AlertDialog mDialog;

    private VoiceProxy.VoiceLoggerWrapper mVoiceLogger;

    private boolean mOkClicked = false;
    private String mVoiceModeOff;

    private void ensureConsistencyOfAutoCorrectionSettings() {
        final String autoCorrectionOff = getResources().getString(
                R.string.auto_correction_threshold_mode_index_off);
        final String currentSetting = mAutoCorrectionThreshold.getValue();
        mBigramSuggestion.setEnabled(!currentSetting.equals(autoCorrectionOff));
        mBigramPrediction.setEnabled(!currentSetting.equals(autoCorrectionOff));
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        final Resources res = getResources();

        addPreferencesFromResource(R.xml.prefs);
        mInputLanguageSelection = (PreferenceScreen) findPreference(PREF_SUBTYPES);
        mInputLanguageSelection.setOnPreferenceClickListener(this);
        mQuickFixes = (CheckBoxPreference) findPreference(PREF_QUICK_FIXES);
        mVoicePreference = (ListPreference) findPreference(PREF_VOICE_SETTINGS_KEY);
        mSettingsKeyPreference = (ListPreference) findPreference(PREF_SETTINGS_KEY);
        mShowCorrectionSuggestionsPreference =
                (ListPreference) findPreference(PREF_SHOW_SUGGESTIONS_SETTING);
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        prefs.registerOnSharedPreferenceChangeListener(this);

        mVoiceModeOff = getString(R.string.voice_mode_off);
        mVoiceOn = !(prefs.getString(PREF_VOICE_SETTINGS_KEY, mVoiceModeOff)
                .equals(mVoiceModeOff));
        mVoiceLogger = VoiceProxy.VoiceLoggerWrapper.getInstance(this);

        mAutoCorrectionThreshold = (ListPreference) findPreference(PREF_AUTO_CORRECTION_THRESHOLD);
        mBigramSuggestion = (CheckBoxPreference) findPreference(PREF_BIGRAM_SUGGESTIONS);
        mBigramPrediction = (CheckBoxPreference) findPreference(PREF_BIGRAM_PREDICTIONS);
        mDebugSettingsPreference = findPreference(PREF_DEBUG_SETTINGS);
        if (mDebugSettingsPreference != null) {
            final Intent debugSettingsIntent = new Intent(Intent.ACTION_MAIN);
            debugSettingsIntent.setClassName(getPackageName(), DebugSettings.class.getName());
            mDebugSettingsPreference.setIntent(debugSettingsIntent);
        }

        ensureConsistencyOfAutoCorrectionSettings();

        final PreferenceGroup generalSettings =
                (PreferenceGroup) findPreference(PREF_GENERAL_SETTINGS_KEY);
        final PreferenceGroup textCorrectionGroup =
                (PreferenceGroup) findPreference(PREF_CORRECTION_SETTINGS_KEY);

        final boolean showSettingsKeyOption = res.getBoolean(
                R.bool.config_enable_show_settings_key_option);
        if (!showSettingsKeyOption) {
            generalSettings.removePreference(mSettingsKeyPreference);
        }

        final boolean showVoiceKeyOption = res.getBoolean(
                R.bool.config_enable_show_voice_key_option);
        if (!showVoiceKeyOption) {
            generalSettings.removePreference(mVoicePreference);
        }

        if (!VibratorCompatWrapper.getInstance(this).hasVibrator()) {
            generalSettings.removePreference(findPreference(PREF_VIBRATE_ON));
        }

        if (InputMethodServiceCompatWrapper.CAN_HANDLE_ON_CURRENT_INPUT_METHOD_SUBTYPE_CHANGED) {
            generalSettings.removePreference(findPreference(PREF_SUBTYPES));
        }

        final boolean showPopupOption = res.getBoolean(
                R.bool.config_enable_show_popup_on_keypress_option);
        if (!showPopupOption) {
            generalSettings.removePreference(findPreference(PREF_KEY_PREVIEW_POPUP_ON));
        }

        final boolean showRecorrectionOption = res.getBoolean(
                R.bool.config_enable_show_recorrection_option);
        if (!showRecorrectionOption) {
            generalSettings.removePreference(findPreference(PREF_RECORRECTION_ENABLED));
        }

        final boolean showQuickFixesOption = res.getBoolean(
                R.bool.config_enable_quick_fixes_option);
        if (!showQuickFixesOption) {
            textCorrectionGroup.removePreference(findPreference(PREF_QUICK_FIXES));
        }

        final boolean showBigramSuggestionsOption = res.getBoolean(
                R.bool.config_enable_bigram_suggestions_option);
        if (!showBigramSuggestionsOption) {
            textCorrectionGroup.removePreference(findPreference(PREF_BIGRAM_SUGGESTIONS));
            textCorrectionGroup.removePreference(findPreference(PREF_BIGRAM_PREDICTIONS));
        }

        final boolean showUsabilityModeStudyOption = res.getBoolean(
                R.bool.config_enable_usability_study_mode_option);
        if (!showUsabilityModeStudyOption) {
            getPreferenceScreen().removePreference(findPreference(PREF_USABILITY_STUDY_MODE));
        }

        mKeyPreviewPopupDismissDelay =
                (ListPreference)findPreference(PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY);
        final String[] entries = new String[] {
                res.getString(R.string.key_preview_popup_dismiss_no_delay),
                res.getString(R.string.key_preview_popup_dismiss_default_delay),
        };
        final String popupDismissDelayDefaultValue = Integer.toString(res.getInteger(
                R.integer.config_delay_after_preview));
        mKeyPreviewPopupDismissDelay.setEntries(entries);
        mKeyPreviewPopupDismissDelay.setEntryValues(
                new String[] { "0", popupDismissDelayDefaultValue });
        if (null == mKeyPreviewPopupDismissDelay.getValue()) {
            mKeyPreviewPopupDismissDelay.setValue(popupDismissDelayDefaultValue);
        }
        mKeyPreviewPopupDismissDelay.setEnabled(
                Settings.Values.isKeyPreviewPopupEnabled(prefs, res));
    }

    @Override
    protected void onResume() {
        super.onResume();
        int autoTextSize = AutoText.getSize(getListView());
        if (autoTextSize < 1) {
            ((PreferenceGroup) findPreference(PREF_CORRECTION_SETTINGS_KEY))
                    .removePreference(mQuickFixes);
        }
        if (!VoiceProxy.VOICE_INSTALLED
                || !SpeechRecognizer.isRecognitionAvailable(this)) {
            getPreferenceScreen().removePreference(mVoicePreference);
        } else {
            updateVoiceModeSummary();
        }
        updateSettingsKeySummary();
        updateShowCorrectionSuggestionsSummary();
        updateKeyPreviewPopupDelaySummary();
    }

    @Override
    protected void onDestroy() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(
                this);
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        (new BackupManager(this)).dataChanged();
        // If turning on voice input, show dialog
        if (key.equals(PREF_VOICE_SETTINGS_KEY) && !mVoiceOn) {
            if (!prefs.getString(PREF_VOICE_SETTINGS_KEY, mVoiceModeOff)
                    .equals(mVoiceModeOff)) {
                showVoiceConfirmation();
            }
        } else if (key.equals(PREF_KEY_PREVIEW_POPUP_ON)) {
            final ListPreference popupDismissDelay =
                (ListPreference)findPreference(PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY);
            if (null != popupDismissDelay) {
                popupDismissDelay.setEnabled(prefs.getBoolean(PREF_KEY_PREVIEW_POPUP_ON, true));
            }
        }
        ensureConsistencyOfAutoCorrectionSettings();
        mVoiceOn = !(prefs.getString(PREF_VOICE_SETTINGS_KEY, mVoiceModeOff)
                .equals(mVoiceModeOff));
        updateVoiceModeSummary();
        updateSettingsKeySummary();
        updateShowCorrectionSuggestionsSummary();
        updateKeyPreviewPopupDelaySummary();
    }

    @Override
    public boolean onPreferenceClick(Preference pref) {
        if (pref == mInputLanguageSelection) {
            startActivity(CompatUtils.getInputLanguageSelectionIntent(
                    Utils.getInputMethodId(InputMethodManagerCompatWrapper.getInstance(this),
                            getApplicationInfo().packageName), 0));
            return true;
        }
        return false;
    }

    private void updateShowCorrectionSuggestionsSummary() {
        mShowCorrectionSuggestionsPreference.setSummary(
                getResources().getStringArray(R.array.prefs_suggestion_visibilities)
                [mShowCorrectionSuggestionsPreference.findIndexOfValue(
                        mShowCorrectionSuggestionsPreference.getValue())]);
    }

    private void updateSettingsKeySummary() {
        mSettingsKeyPreference.setSummary(
                getResources().getStringArray(R.array.settings_key_modes)
                [mSettingsKeyPreference.findIndexOfValue(mSettingsKeyPreference.getValue())]);
    }

    private void updateKeyPreviewPopupDelaySummary() {
        final ListPreference lp = mKeyPreviewPopupDismissDelay;
        lp.setSummary(lp.getEntries()[lp.findIndexOfValue(lp.getValue())]);
    }

    private void showVoiceConfirmation() {
        mOkClicked = false;
        showDialog(VOICE_INPUT_CONFIRM_DIALOG);
        // Make URL in the dialog message clickable
        if (mDialog != null) {
            TextView textView = (TextView) mDialog.findViewById(android.R.id.message);
            if (textView != null) {
                textView.setMovementMethod(LinkMovementMethod.getInstance());
            }
        }
    }

    private void updateVoiceModeSummary() {
        mVoicePreference.setSummary(
                getResources().getStringArray(R.array.voice_input_modes_summary)
                [mVoicePreference.findIndexOfValue(mVoicePreference.getValue())]);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case VOICE_INPUT_CONFIRM_DIALOG:
                DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        if (whichButton == DialogInterface.BUTTON_NEGATIVE) {
                            mVoicePreference.setValue(mVoiceModeOff);
                            mVoiceLogger.settingsWarningDialogCancel();
                        } else if (whichButton == DialogInterface.BUTTON_POSITIVE) {
                            mOkClicked = true;
                            mVoiceLogger.settingsWarningDialogOk();
                        }
                        updateVoicePreference();
                    }
                };
                AlertDialog.Builder builder = new AlertDialog.Builder(this)
                        .setTitle(R.string.voice_warning_title)
                        .setPositiveButton(android.R.string.ok, listener)
                        .setNegativeButton(android.R.string.cancel, listener);

                // Get the current list of supported locales and check the current locale against
                // that list, to decide whether to put a warning that voice input will not work in
                // the current language as part of the pop-up confirmation dialog.
                boolean localeSupported = SubtypeSwitcher.getInstance().isVoiceSupported(
                        Locale.getDefault().toString());

                final CharSequence message;
                if (localeSupported) {
                    message = TextUtils.concat(
                            getText(R.string.voice_warning_may_not_understand), "\n\n",
                                    getText(R.string.voice_hint_dialog_message));
                } else {
                    message = TextUtils.concat(
                            getText(R.string.voice_warning_locale_not_supported), "\n\n",
                                    getText(R.string.voice_warning_may_not_understand), "\n\n",
                                            getText(R.string.voice_hint_dialog_message));
                }
                builder.setMessage(message);
                AlertDialog dialog = builder.create();
                mDialog = dialog;
                dialog.setOnDismissListener(this);
                mVoiceLogger.settingsWarningDialogShown();
                return dialog;
            default:
                Log.e(TAG, "unknown dialog " + id);
                return null;
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        mVoiceLogger.settingsWarningDialogDismissed();
        if (!mOkClicked) {
            // This assumes that onPreferenceClick gets called first, and this if the user
            // agreed after the warning, we set the mOkClicked value to true.
            mVoicePreference.setValue(mVoiceModeOff);
        }
    }

    private void updateVoicePreference() {
        boolean isChecked = !mVoicePreference.getValue().equals(mVoiceModeOff);
        mVoiceLogger.voiceInputSettingEnabled(isChecked);
    }
}
