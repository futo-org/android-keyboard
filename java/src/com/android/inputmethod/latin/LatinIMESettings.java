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

import java.util.Locale;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.backup.BackupManager;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.speech.SpeechRecognizer;
import android.text.AutoText;
import android.util.Log;

import com.android.inputmethod.voice.VoiceIMEConnector;
import com.android.inputmethod.voice.VoiceInputLogger;

public class LatinIMESettings extends PreferenceActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener,
        DialogInterface.OnDismissListener {

    private static final String QUICK_FIXES_KEY = "quick_fixes";
    private static final String PREDICTION_SETTINGS_KEY = "prediction_settings";
    private static final String VOICE_SETTINGS_KEY = "voice_mode";
    private static final String PREF_AUTO_COMPLETION_THRESHOLD = "auto_completion_threshold";
    private static final String PREF_BIGRAM_SUGGESTIONS = "bigram_suggestion";
    /* package */ static final String PREF_SETTINGS_KEY = "settings_key";
    /* package */ static final String PREF_VIBRATE_ON = "vibrate_on";

    private static final String TAG = "LatinIMESettings";

    // Dialog ids
    private static final int VOICE_INPUT_CONFIRM_DIALOG = 0;

    private CheckBoxPreference mQuickFixes;
    private ListPreference mVoicePreference;
    private ListPreference mSettingsKeyPreference;
    private ListPreference mAutoCompletionThreshold;
    private CheckBoxPreference mBigramSuggestion;
    private boolean mVoiceOn;

    private VoiceInputLogger mLogger;

    private boolean mOkClicked = false;
    private String mVoiceModeOff;

    private void ensureConsistencyOfAutoCompletionSettings() {
        final String autoCompletionOff = getResources().getString(
                R.string.auto_completion_threshold_mode_value_off);
        final String currentSetting = mAutoCompletionThreshold.getValue();
        mBigramSuggestion.setEnabled(!currentSetting.equals(autoCompletionOff));
    }
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs);
        mQuickFixes = (CheckBoxPreference) findPreference(QUICK_FIXES_KEY);
        mVoicePreference = (ListPreference) findPreference(VOICE_SETTINGS_KEY);
        mSettingsKeyPreference = (ListPreference) findPreference(PREF_SETTINGS_KEY);
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        prefs.registerOnSharedPreferenceChangeListener(this);

        mVoiceModeOff = getString(R.string.voice_mode_off);
        mVoiceOn = !(prefs.getString(VOICE_SETTINGS_KEY, mVoiceModeOff).equals(mVoiceModeOff));
        mLogger = VoiceInputLogger.getLogger(this);

        mAutoCompletionThreshold = (ListPreference) findPreference(PREF_AUTO_COMPLETION_THRESHOLD);
        mBigramSuggestion = (CheckBoxPreference) findPreference(PREF_BIGRAM_SUGGESTIONS);
        ensureConsistencyOfAutoCompletionSettings();

        final boolean showSettingsKeyOption = getResources().getBoolean(
                R.bool.config_enable_show_settings_key_option);
        if (!showSettingsKeyOption)
            getPreferenceScreen().removePreference(mSettingsKeyPreference);

        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            getPreferenceScreen().removePreference(
                    getPreferenceScreen().findPreference(PREF_VIBRATE_ON));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        int autoTextSize = AutoText.getSize(getListView());
        if (autoTextSize < 1) {
            ((PreferenceGroup) findPreference(PREDICTION_SETTINGS_KEY))
                    .removePreference(mQuickFixes);
        }
        if (!VoiceIMEConnector.VOICE_INSTALLED
                || !SpeechRecognizer.isRecognitionAvailable(this)) {
            getPreferenceScreen().removePreference(mVoicePreference);
        } else {
            updateVoiceModeSummary();
        }
        updateSettingsKeySummary();
    }

    @Override
    protected void onDestroy() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(
                this);
        super.onDestroy();
    }

    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        (new BackupManager(this)).dataChanged();
        // If turning on voice input, show dialog
        if (key.equals(VOICE_SETTINGS_KEY) && !mVoiceOn) {
            if (!prefs.getString(VOICE_SETTINGS_KEY, mVoiceModeOff)
                    .equals(mVoiceModeOff)) {
                showVoiceConfirmation();
            }
        }
        ensureConsistencyOfAutoCompletionSettings();
        mVoiceOn = !(prefs.getString(VOICE_SETTINGS_KEY, mVoiceModeOff).equals(mVoiceModeOff));
        updateVoiceModeSummary();
        updateSettingsKeySummary();
    }

    private void updateSettingsKeySummary() {
        mSettingsKeyPreference.setSummary(
                getResources().getStringArray(R.array.settings_key_modes)
                [mSettingsKeyPreference.findIndexOfValue(mSettingsKeyPreference.getValue())]);
    }

    private void showVoiceConfirmation() {
        mOkClicked = false;
        showDialog(VOICE_INPUT_CONFIRM_DIALOG);
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
                    public void onClick(DialogInterface dialog, int whichButton) {
                        if (whichButton == DialogInterface.BUTTON_NEGATIVE) {
                            mVoicePreference.setValue(mVoiceModeOff);
                            mLogger.settingsWarningDialogCancel();
                        } else if (whichButton == DialogInterface.BUTTON_POSITIVE) {
                            mOkClicked = true;
                            mLogger.settingsWarningDialogOk();
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

                if (localeSupported) {
                    String message = getString(R.string.voice_warning_may_not_understand) + "\n\n" +
                            getString(R.string.voice_hint_dialog_message);
                    builder.setMessage(message);
                } else {
                    String message = getString(R.string.voice_warning_locale_not_supported) +
                            "\n\n" + getString(R.string.voice_warning_may_not_understand) + "\n\n" +
                            getString(R.string.voice_hint_dialog_message);
                    builder.setMessage(message);
                }

                AlertDialog dialog = builder.create();
                dialog.setOnDismissListener(this);
                mLogger.settingsWarningDialogShown();
                return dialog;
            default:
                Log.e(TAG, "unknown dialog " + id);
                return null;
        }
    }

    public void onDismiss(DialogInterface dialog) {
        mLogger.settingsWarningDialogDismissed();
        if (!mOkClicked) {
            // This assumes that onPreferenceClick gets called first, and this if the user
            // agreed after the warning, we set the mOkClicked value to true.
            mVoicePreference.setValue(mVoiceModeOff);
        }
    }

    private void updateVoicePreference() {
        boolean isChecked = !mVoicePreference.getValue().equals(mVoiceModeOff);
        if (isChecked) {
            mLogger.voiceInputSettingEnabled();
        } else {
            mLogger.voiceInputSettingDisabled();
        }
    }
}
