/*
 * Copyright (C) 2008-2009 Google Inc.
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

import android.app.AlertDialog;
import android.app.Dialog;
import android.backup.BackupManager;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.Preference.OnPreferenceClickListener;
import android.speech.RecognitionManager;
import android.text.AutoText;
import android.util.Log;

import com.google.android.collect.Lists;

import com.android.inputmethod.voice.SettingsUtil;
import com.android.inputmethod.voice.VoiceInputLogger;

import java.util.ArrayList;
import java.util.Locale;

public class LatinIMESettings extends PreferenceActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener,
        OnPreferenceClickListener,
        DialogInterface.OnDismissListener {

    private static final String QUICK_FIXES_KEY = "quick_fixes";
    private static final String SHOW_SUGGESTIONS_KEY = "show_suggestions";
    private static final String PREDICTION_SETTINGS_KEY = "prediction_settings";
    private static final String VOICE_SETTINGS_KEY = "enable_voice_input";
    private static final String VOICE_ON_PRIMARY_KEY = "voice_on_main";
    private static final String VOICE_SERVER_KEY = "voice_server_url";

    private static final String TAG = "LatinIMESettings";

    // Dialog ids
    private static final int VOICE_INPUT_CONFIRM_DIALOG = 0;

    private CheckBoxPreference mQuickFixes;
    private CheckBoxPreference mShowSuggestions;
    private CheckBoxPreference mVoicePreference;
    private CheckBoxPreference mVoiceOnPrimary;

    private VoiceInputLogger mLogger;

    private boolean mOkClicked = false;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs);
        mQuickFixes = (CheckBoxPreference) findPreference(QUICK_FIXES_KEY);
        mShowSuggestions = (CheckBoxPreference) findPreference(SHOW_SUGGESTIONS_KEY);
        mVoicePreference = (CheckBoxPreference) findPreference(VOICE_SETTINGS_KEY);
        mVoiceOnPrimary = (CheckBoxPreference) findPreference(VOICE_ON_PRIMARY_KEY);
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        prefs.registerOnSharedPreferenceChangeListener(this);

        mVoicePreference.setOnPreferenceClickListener(this);
        mVoicePreference.setChecked(prefs.getBoolean(
                VOICE_SETTINGS_KEY, getResources().getBoolean(R.bool.voice_input_default)));

        mLogger = VoiceInputLogger.getLogger(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        int autoTextSize = AutoText.getSize(getListView());
        if (autoTextSize < 1) {
            ((PreferenceGroup) findPreference(PREDICTION_SETTINGS_KEY))
                    .removePreference(mQuickFixes);
        } else {
            mShowSuggestions.setDependency(QUICK_FIXES_KEY);
        }
        if (!LatinIME.VOICE_INSTALLED
                || !RecognitionManager.isRecognitionAvailable(this)) {
            getPreferenceScreen().removePreference(mVoiceOnPrimary);
            getPreferenceScreen().removePreference(mVoicePreference);
        }

        mVoicePreference.setChecked(
                getPreferenceManager().getSharedPreferences().getBoolean(VOICE_SETTINGS_KEY, true));
    }

    @Override
    protected void onDestroy() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(
                this);
        super.onDestroy();
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        (new BackupManager(this)).dataChanged();
    }

    public boolean onPreferenceClick(Preference preference) {
        if (preference == mVoicePreference) {
            if (mVoicePreference.isChecked()) {
                mOkClicked = false;
                showDialog(VOICE_INPUT_CONFIRM_DIALOG);
            } else {
                updateVoicePreference();
            }
        }
        return false;
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case VOICE_INPUT_CONFIRM_DIALOG:
                DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        if (whichButton == DialogInterface.BUTTON_NEGATIVE) {
                            mVoicePreference.setChecked(false);
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
                String supportedLocalesString = SettingsUtil.getSettingsString(
                        getContentResolver(),
                        SettingsUtil.LATIN_IME_VOICE_INPUT_SUPPORTED_LOCALES,
                        LatinIME.DEFAULT_VOICE_INPUT_SUPPORTED_LOCALES);
                ArrayList<String> voiceInputSupportedLocales =
                        Lists.newArrayList(supportedLocalesString.split("\\s+"));
                boolean localeSupported = voiceInputSupportedLocales.contains(
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
            mVoicePreference.setChecked(false);
        }
    }

    private void updateVoicePreference() {
        SharedPreferences.Editor editor = getPreferenceManager().getSharedPreferences().edit();
        boolean isChecked = mVoicePreference.isChecked();
        if (isChecked) {
            mLogger.voiceInputSettingEnabled();
        } else {
            mLogger.voiceInputSettingDisabled();
        }
        editor.putBoolean(VOICE_SETTINGS_KEY, isChecked);
        editor.commit();
    }
}
