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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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

import java.util.Locale;

public class Settings extends PreferenceActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener,
        DialogInterface.OnDismissListener, OnPreferenceClickListener {
    private static final String TAG = "Settings";

    public static final String PREF_GENERAL_SETTINGS_KEY = "general_settings";
    public static final String PREF_VIBRATE_ON = "vibrate_on";
    public static final String PREF_SOUND_ON = "sound_on";
    public static final String PREF_POPUP_ON = "popup_on";
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

    public static final String PREF_USABILITY_STUDY_MODE = "usability_study_mode";

    // Dialog ids
    private static final int VOICE_INPUT_CONFIRM_DIALOG = 0;

    private PreferenceScreen mInputLanguageSelection;
    private CheckBoxPreference mQuickFixes;
    private ListPreference mVoicePreference;
    private ListPreference mSettingsKeyPreference;
    private ListPreference mShowCorrectionSuggestionsPreference;
    private ListPreference mAutoCorrectionThreshold;
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
        final PreferenceGroup bigramGroup =
                (PreferenceGroup) findPreference(PREF_NGRAM_SETTINGS_KEY);

        final boolean showSettingsKeyOption = getResources().getBoolean(
                R.bool.config_enable_show_settings_key_option);
        if (!showSettingsKeyOption) {
            generalSettings.removePreference(mSettingsKeyPreference);
        }

        final boolean showVoiceKeyOption = getResources().getBoolean(
                R.bool.config_enable_show_voice_key_option);
        if (!showVoiceKeyOption) {
            generalSettings.removePreference(mVoicePreference);
        }

        if (!VibratorCompatWrapper.getInstance(this).hasVibrator()) {
            generalSettings.removePreference(findPreference(PREF_VIBRATE_ON));
        }

        final boolean showSubtypeSettings = getResources().getBoolean(
                R.bool.config_enable_show_subtype_settings);
        if (InputMethodServiceCompatWrapper.CAN_HANDLE_ON_CURRENT_INPUT_METHOD_SUBTYPE_CHANGED
                && !showSubtypeSettings) {
            generalSettings.removePreference(findPreference(PREF_SUBTYPES));
        }

        final boolean showPopupOption = getResources().getBoolean(
                R.bool.config_enable_show_popup_on_keypress_option);
        if (!showPopupOption) {
            generalSettings.removePreference(findPreference(PREF_POPUP_ON));
        }

        final boolean showRecorrectionOption = getResources().getBoolean(
                R.bool.config_enable_show_recorrection_option);
        if (!showRecorrectionOption) {
            generalSettings.removePreference(findPreference(PREF_RECORRECTION_ENABLED));
        }

        final boolean showQuickFixesOption = getResources().getBoolean(
                R.bool.config_enable_quick_fixes_option);
        if (!showQuickFixesOption) {
            textCorrectionGroup.removePreference(findPreference(PREF_QUICK_FIXES));
        }

        final boolean showBigramSuggestionsOption = getResources().getBoolean(
                R.bool.config_enable_bigram_suggestions_option);
        if (!showBigramSuggestionsOption) {
            textCorrectionGroup.removePreference(findPreference(PREF_BIGRAM_SUGGESTIONS));
            textCorrectionGroup.removePreference(findPreference(PREF_BIGRAM_PREDICTIONS));
        }

        final boolean showUsabilityModeStudyOption = getResources().getBoolean(
                R.bool.config_enable_usability_study_mode_option);
        if (!showUsabilityModeStudyOption) {
            getPreferenceScreen().removePreference(findPreference(PREF_USABILITY_STUDY_MODE));
        }
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
        }
        ensureConsistencyOfAutoCorrectionSettings();
        mVoiceOn = !(prefs.getString(PREF_VOICE_SETTINGS_KEY, mVoiceModeOff)
                .equals(mVoiceModeOff));
        updateVoiceModeSummary();
        updateSettingsKeySummary();
        updateShowCorrectionSuggestionsSummary();
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
