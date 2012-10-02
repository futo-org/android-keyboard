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

import android.app.AlertDialog;
import android.app.backup.BackupManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.android.inputmethod.latin.define.ProductionFlag;
import com.android.inputmethodcommon.InputMethodSettingsFragment;

public final class Settings extends InputMethodSettingsFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    // In the same order as xml/prefs.xml
    public static final String PREF_GENERAL_SETTINGS = "general_settings";
    public static final String PREF_AUTO_CAP = "auto_cap";
    public static final String PREF_VIBRATE_ON = "vibrate_on";
    public static final String PREF_SOUND_ON = "sound_on";
    public static final String PREF_POPUP_ON = "popup_on";
    public static final String PREF_VOICE_MODE = "voice_mode";
    public static final String PREF_CORRECTION_SETTINGS = "correction_settings";
    public static final String PREF_CONFIGURE_DICTIONARIES_KEY = "configure_dictionaries_key";
    public static final String PREF_AUTO_CORRECTION_THRESHOLD = "auto_correction_threshold";
    public static final String PREF_SHOW_SUGGESTIONS_SETTING = "show_suggestions_setting";
    public static final String PREF_MISC_SETTINGS = "misc_settings";
    public static final String PREF_LAST_USER_DICTIONARY_WRITE_TIME =
            "last_user_dictionary_write_time";
    public static final String PREF_ADVANCED_SETTINGS = "pref_advanced_settings";
    public static final String PREF_KEY_USE_CONTACTS_DICT = "pref_key_use_contacts_dict";
    public static final String PREF_SHOW_LANGUAGE_SWITCH_KEY =
            "pref_show_language_switch_key";
    public static final String PREF_INCLUDE_OTHER_IMES_IN_LANGUAGE_SWITCH_LIST =
            "pref_include_other_imes_in_language_switch_list";
    public static final String PREF_CUSTOM_INPUT_STYLES = "custom_input_styles";
    public static final String PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY =
            "pref_key_preview_popup_dismiss_delay";
    public static final String PREF_BIGRAM_PREDICTIONS = "next_word_prediction";
    public static final String PREF_GESTURE_INPUT = "gesture_input";
    public static final String PREF_VIBRATION_DURATION_SETTINGS =
            "pref_vibration_duration_settings";
    public static final String PREF_KEYPRESS_SOUND_VOLUME =
            "pref_keypress_sound_volume";
    public static final String PREF_GESTURE_PREVIEW_TRAIL = "pref_gesture_preview_trail";
    public static final String PREF_GESTURE_FLOATING_PREVIEW_TEXT =
            "pref_gesture_floating_preview_text";

    public static final String PREF_INPUT_LANGUAGE = "input_language";
    public static final String PREF_SELECTED_LANGUAGES = "selected_languages";
    public static final String PREF_DEBUG_SETTINGS = "debug_settings";

    private PreferenceScreen mKeypressVibrationDurationSettingsPref;
    private PreferenceScreen mKeypressSoundVolumeSettingsPref;
    private ListPreference mVoicePreference;
    private ListPreference mShowCorrectionSuggestionsPreference;
    private ListPreference mAutoCorrectionThresholdPreference;
    private ListPreference mKeyPreviewPopupDismissDelay;
    // Use bigrams to predict the next word when there is no input for it yet
    private CheckBoxPreference mBigramPrediction;
    private Preference mDebugSettingsPreference;

    private TextView mKeypressVibrationDurationSettingsTextView;
    private TextView mKeypressSoundVolumeSettingsTextView;

    private static void setPreferenceEnabled(final Preference preference, final boolean enabled) {
        if (preference != null) {
            preference.setEnabled(enabled);
        }
    }

    private void ensureConsistencyOfAutoCorrectionSettings() {
        final String autoCorrectionOff = getResources().getString(
                R.string.auto_correction_threshold_mode_index_off);
        final String currentSetting = mAutoCorrectionThresholdPreference.getValue();
        setPreferenceEnabled(mBigramPrediction, !currentSetting.equals(autoCorrectionOff));
    }

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        setInputMethodSettingsCategoryTitle(R.string.language_selection_title);
        setSubtypeEnablerTitle(R.string.select_language);
        addPreferencesFromResource(R.xml.prefs);

        final Resources res = getResources();
        final Context context = getActivity();

        // When we are called from the Settings application but we are not already running, the
        // {@link SubtypeLocale} class may not have been initialized. It is safe to call
        // {@link SubtypeLocale#init(Context)} multiple times.
        SubtypeLocale.init(context);
        mVoicePreference = (ListPreference) findPreference(PREF_VOICE_MODE);
        mShowCorrectionSuggestionsPreference =
                (ListPreference) findPreference(PREF_SHOW_SUGGESTIONS_SETTING);
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        prefs.registerOnSharedPreferenceChangeListener(this);

        mAutoCorrectionThresholdPreference =
                (ListPreference) findPreference(PREF_AUTO_CORRECTION_THRESHOLD);
        mBigramPrediction = (CheckBoxPreference) findPreference(PREF_BIGRAM_PREDICTIONS);
        ensureConsistencyOfAutoCorrectionSettings();

        final PreferenceGroup generalSettings =
                (PreferenceGroup) findPreference(PREF_GENERAL_SETTINGS);
        final PreferenceGroup textCorrectionGroup =
                (PreferenceGroup) findPreference(PREF_CORRECTION_SETTINGS);
        final PreferenceGroup miscSettings =
                (PreferenceGroup) findPreference(PREF_MISC_SETTINGS);

        mDebugSettingsPreference = findPreference(PREF_DEBUG_SETTINGS);
        if (mDebugSettingsPreference != null) {
            if (ProductionFlag.IS_INTERNAL) {
                final Intent debugSettingsIntent = new Intent(Intent.ACTION_MAIN);
                debugSettingsIntent.setClassName(
                        context.getPackageName(), DebugSettingsActivity.class.getName());
                mDebugSettingsPreference.setIntent(debugSettingsIntent);
            } else {
                miscSettings.removePreference(mDebugSettingsPreference);
            }
        }

        final boolean showVoiceKeyOption = res.getBoolean(
                R.bool.config_enable_show_voice_key_option);
        if (!showVoiceKeyOption) {
            generalSettings.removePreference(mVoicePreference);
        }

        final PreferenceGroup advancedSettings =
                (PreferenceGroup) findPreference(PREF_ADVANCED_SETTINGS);
        if (!VibratorUtils.getInstance(context).hasVibrator()) {
            generalSettings.removePreference(findPreference(PREF_VIBRATE_ON));
            if (null != advancedSettings) { // Theoretically advancedSettings cannot be null
                advancedSettings.removePreference(findPreference(PREF_VIBRATION_DURATION_SETTINGS));
            }
        }

        final boolean showKeyPreviewPopupOption = res.getBoolean(
                R.bool.config_enable_show_popup_on_keypress_option);
        mKeyPreviewPopupDismissDelay =
                (ListPreference) findPreference(PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY);
        if (!showKeyPreviewPopupOption) {
            generalSettings.removePreference(findPreference(PREF_POPUP_ON));
            if (null != advancedSettings) { // Theoretically advancedSettings cannot be null
                advancedSettings.removePreference(mKeyPreviewPopupDismissDelay);
            }
        } else {
            final String[] entries = new String[] {
                    res.getString(R.string.key_preview_popup_dismiss_no_delay),
                    res.getString(R.string.key_preview_popup_dismiss_default_delay),
            };
            final String popupDismissDelayDefaultValue = Integer.toString(res.getInteger(
                    R.integer.config_key_preview_linger_timeout));
            mKeyPreviewPopupDismissDelay.setEntries(entries);
            mKeyPreviewPopupDismissDelay.setEntryValues(
                    new String[] { "0", popupDismissDelayDefaultValue });
            if (null == mKeyPreviewPopupDismissDelay.getValue()) {
                mKeyPreviewPopupDismissDelay.setValue(popupDismissDelayDefaultValue);
            }
            setPreferenceEnabled(mKeyPreviewPopupDismissDelay,
                    SettingsValues.isKeyPreviewPopupEnabled(prefs, res));
        }

        setPreferenceEnabled(findPreference(PREF_INCLUDE_OTHER_IMES_IN_LANGUAGE_SWITCH_LIST),
                SettingsValues.showsLanguageSwitchKey(prefs));

        final PreferenceScreen dictionaryLink =
                (PreferenceScreen) findPreference(PREF_CONFIGURE_DICTIONARIES_KEY);
        final Intent intent = dictionaryLink.getIntent();

        final int number = context.getPackageManager().queryIntentActivities(intent, 0).size();
        if (0 >= number) {
            textCorrectionGroup.removePreference(dictionaryLink);
        }

        final boolean gestureInputEnabledByBuildConfig = res.getBoolean(
                R.bool.config_gesture_input_enabled_by_build_config);
        final Preference gesturePreviewTrail = findPreference(PREF_GESTURE_PREVIEW_TRAIL);
        final Preference gestureFloatingPreviewText = findPreference(
                PREF_GESTURE_FLOATING_PREVIEW_TEXT);
        if (!gestureInputEnabledByBuildConfig) {
            miscSettings.removePreference(findPreference(PREF_GESTURE_INPUT));
            miscSettings.removePreference(gesturePreviewTrail);
            miscSettings.removePreference(gestureFloatingPreviewText);
        } else {
            final boolean gestureInputEnabledByUser = prefs.getBoolean(PREF_GESTURE_INPUT, true);
            setPreferenceEnabled(gesturePreviewTrail, gestureInputEnabledByUser);
            setPreferenceEnabled(gestureFloatingPreviewText, gestureInputEnabledByUser);
        }

        mKeypressVibrationDurationSettingsPref =
                (PreferenceScreen) findPreference(PREF_VIBRATION_DURATION_SETTINGS);
        if (mKeypressVibrationDurationSettingsPref != null) {
            mKeypressVibrationDurationSettingsPref.setOnPreferenceClickListener(
                    new OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(Preference arg0) {
                            showKeypressVibrationDurationSettingsDialog();
                            return true;
                        }
                    });
            updateKeypressVibrationDurationSettingsSummary(prefs, res);
        }

        mKeypressSoundVolumeSettingsPref =
                (PreferenceScreen) findPreference(PREF_KEYPRESS_SOUND_VOLUME);
        if (mKeypressSoundVolumeSettingsPref != null) {
            mKeypressSoundVolumeSettingsPref.setOnPreferenceClickListener(
                    new OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(Preference arg0) {
                            showKeypressSoundVolumeSettingDialog();
                            return true;
                        }
                    });
            updateKeypressSoundVolumeSummary(prefs, res);
        }
        refreshEnablingsOfKeypressSoundAndVibrationSettings(prefs, res);
    }

    @Override
    public void onResume() {
        super.onResume();
        final boolean isShortcutImeEnabled = SubtypeSwitcher.getInstance().isShortcutImeEnabled();
        if (isShortcutImeEnabled) {
            updateVoiceModeSummary();
        } else {
            getPreferenceScreen().removePreference(mVoicePreference);
        }
        updateShowCorrectionSuggestionsSummary();
        updateKeyPreviewPopupDelaySummary();
        updateCustomInputStylesSummary();
    }

    @Override
    public void onDestroy() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(
                this);
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        (new BackupManager(getActivity())).dataChanged();
        if (key.equals(PREF_POPUP_ON)) {
            setPreferenceEnabled(findPreference(PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY),
                    prefs.getBoolean(PREF_POPUP_ON, true));
        } else if (key.equals(PREF_SHOW_LANGUAGE_SWITCH_KEY)) {
            setPreferenceEnabled(findPreference(PREF_INCLUDE_OTHER_IMES_IN_LANGUAGE_SWITCH_LIST),
                    SettingsValues.showsLanguageSwitchKey(prefs));
        } else if (key.equals(PREF_GESTURE_INPUT)) {
            final boolean gestureInputEnabledByConfig = getResources().getBoolean(
                    R.bool.config_gesture_input_enabled_by_build_config);
            if (gestureInputEnabledByConfig) {
                final boolean gestureInputEnabledByUser = prefs.getBoolean(
                        PREF_GESTURE_INPUT, true);
                setPreferenceEnabled(findPreference(PREF_GESTURE_PREVIEW_TRAIL),
                        gestureInputEnabledByUser);
                setPreferenceEnabled(findPreference(PREF_GESTURE_FLOATING_PREVIEW_TEXT),
                        gestureInputEnabledByUser);
            }
        }
        ensureConsistencyOfAutoCorrectionSettings();
        updateVoiceModeSummary();
        updateShowCorrectionSuggestionsSummary();
        updateKeyPreviewPopupDelaySummary();
        refreshEnablingsOfKeypressSoundAndVibrationSettings(prefs, getResources());
    }

    private void updateShowCorrectionSuggestionsSummary() {
        mShowCorrectionSuggestionsPreference.setSummary(
                getResources().getStringArray(R.array.prefs_suggestion_visibilities)
                [mShowCorrectionSuggestionsPreference.findIndexOfValue(
                        mShowCorrectionSuggestionsPreference.getValue())]);
    }

    private void updateCustomInputStylesSummary() {
        final PreferenceScreen customInputStyles =
                (PreferenceScreen)findPreference(PREF_CUSTOM_INPUT_STYLES);
        final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        final Resources res = getResources();
        final String prefSubtype = SettingsValues.getPrefAdditionalSubtypes(prefs, res);
        final InputMethodSubtype[] subtypes =
                AdditionalSubtype.createAdditionalSubtypesArray(prefSubtype);
        final StringBuilder styles = new StringBuilder();
        for (final InputMethodSubtype subtype : subtypes) {
            if (styles.length() > 0) styles.append(", ");
            styles.append(SubtypeLocale.getSubtypeDisplayName(subtype, res));
        }
        customInputStyles.setSummary(styles);
    }

    private void updateKeyPreviewPopupDelaySummary() {
        final ListPreference lp = mKeyPreviewPopupDismissDelay;
        final CharSequence[] entries = lp.getEntries();
        if (entries == null || entries.length <= 0) return;
        lp.setSummary(entries[lp.findIndexOfValue(lp.getValue())]);
    }

    private void updateVoiceModeSummary() {
        mVoicePreference.setSummary(
                getResources().getStringArray(R.array.voice_input_modes_summary)
                        [mVoicePreference.findIndexOfValue(mVoicePreference.getValue())]);
    }

    private void refreshEnablingsOfKeypressSoundAndVibrationSettings(
            final SharedPreferences sp, final Resources res) {
        if (mKeypressVibrationDurationSettingsPref != null) {
            final boolean hasVibratorHardware = VibratorUtils.getInstance(getActivity())
                    .hasVibrator();
            final boolean vibrateOnByUser = sp.getBoolean(Settings.PREF_VIBRATE_ON,
                    res.getBoolean(R.bool.config_default_vibration_enabled));
            setPreferenceEnabled(mKeypressVibrationDurationSettingsPref,
                    hasVibratorHardware && vibrateOnByUser);
        }

        if (mKeypressSoundVolumeSettingsPref != null) {
            final boolean soundOn = sp.getBoolean(Settings.PREF_SOUND_ON,
                    res.getBoolean(R.bool.config_default_sound_enabled));
            setPreferenceEnabled(mKeypressSoundVolumeSettingsPref, soundOn);
        }
    }

    private void updateKeypressVibrationDurationSettingsSummary(
            final SharedPreferences sp, final Resources res) {
        if (mKeypressVibrationDurationSettingsPref != null) {
            mKeypressVibrationDurationSettingsPref.setSummary(
                    SettingsValues.getCurrentVibrationDuration(sp, res)
                            + res.getString(R.string.settings_ms));
        }
    }

    private void showKeypressVibrationDurationSettingsDialog() {
        final SharedPreferences sp = getPreferenceManager().getSharedPreferences();
        final Context context = getActivity();
        final Resources res = context.getResources();
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.prefs_keypress_vibration_duration_settings);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                final int ms = Integer.valueOf(
                        mKeypressVibrationDurationSettingsTextView.getText().toString());
                sp.edit().putInt(Settings.PREF_VIBRATION_DURATION_SETTINGS, ms).apply();
                updateKeypressVibrationDurationSettingsSummary(sp, res);
            }
        });
        builder.setNegativeButton(android.R.string.cancel,  new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();
            }
        });
        final View v = LayoutInflater.from(context).inflate(
                R.layout.vibration_settings_dialog, null);
        final int currentMs = SettingsValues.getCurrentVibrationDuration(
                getPreferenceManager().getSharedPreferences(), getResources());
        mKeypressVibrationDurationSettingsTextView = (TextView)v.findViewById(R.id.vibration_value);
        final SeekBar sb = (SeekBar)v.findViewById(R.id.vibration_settings);
        sb.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {
                final int tempMs = arg1;
                mKeypressVibrationDurationSettingsTextView.setText(String.valueOf(tempMs));
            }

            @Override
            public void onStartTrackingTouch(SeekBar arg0) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar arg0) {
                final int tempMs = arg0.getProgress();
                VibratorUtils.getInstance(context).vibrate(tempMs);
            }
        });
        sb.setProgress(currentMs);
        mKeypressVibrationDurationSettingsTextView.setText(String.valueOf(currentMs));
        builder.setView(v);
        builder.create().show();
    }

    private void updateKeypressSoundVolumeSummary(final SharedPreferences sp, final Resources res) {
        if (mKeypressSoundVolumeSettingsPref != null) {
            mKeypressSoundVolumeSettingsPref.setSummary(String.valueOf(
                    (int)(SettingsValues.getCurrentKeypressSoundVolume(sp, res) * 100)));
        }
    }

    private void showKeypressSoundVolumeSettingDialog() {
        final Context context = getActivity();
        final AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        final SharedPreferences sp = getPreferenceManager().getSharedPreferences();
        final Resources res = context.getResources();
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.prefs_keypress_sound_volume_settings);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                final float volume =
                        ((float)Integer.valueOf(
                                mKeypressSoundVolumeSettingsTextView.getText().toString())) / 100;
                sp.edit().putFloat(Settings.PREF_KEYPRESS_SOUND_VOLUME, volume).apply();
                updateKeypressSoundVolumeSummary(sp, res);
            }
        });
        builder.setNegativeButton(android.R.string.cancel,  new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();
            }
        });
        final View v = LayoutInflater.from(context).inflate(
                R.layout.sound_effect_volume_dialog, null);
        final int currentVolumeInt =
                (int)(SettingsValues.getCurrentKeypressSoundVolume(sp, res) * 100);
        mKeypressSoundVolumeSettingsTextView =
                (TextView)v.findViewById(R.id.sound_effect_volume_value);
        final SeekBar sb = (SeekBar)v.findViewById(R.id.sound_effect_volume_bar);
        sb.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {
                final int tempVolume = arg1;
                mKeypressSoundVolumeSettingsTextView.setText(String.valueOf(tempVolume));
            }

            @Override
            public void onStartTrackingTouch(SeekBar arg0) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar arg0) {
                final float tempVolume = ((float)arg0.getProgress()) / 100;
                am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, tempVolume);
            }
        });
        sb.setProgress(currentVolumeInt);
        mKeypressSoundVolumeSettingsTextView.setText(String.valueOf(currentVolumeInt));
        builder.setView(v);
        builder.create().show();
    }
}
