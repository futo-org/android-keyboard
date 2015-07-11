/*
 * Copyright (C) 2014 The Android Open Source Project
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
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;

import com.android.inputmethod.latin.AudioAndHapticFeedbackManager;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SystemBroadcastReceiver;
import com.android.inputmethod.latin.define.ProductionFlags;

/**
 * "Advanced" settings sub screen.
 *
 * This settings sub screen handles the following advanced preferences.
 * - Key popup dismiss delay
 * - Keypress vibration duration
 * - Keypress sound volume
 * - Show app icon
 * - Improve keyboard
 * - Debug settings
 */
public final class AdvancedSettingsFragment extends SubScreenFragment {
    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_advanced);

        final Resources res = getResources();
        final Context context = getActivity();

        // When we are called from the Settings application but we are not already running, some
        // singleton and utility classes may not have been initialized.  We have to call
        // initialization method of these classes here. See {@link LatinIME#onCreate()}.
        AudioAndHapticFeedbackManager.init(context);

        final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();

        if (!Settings.isInternal(prefs)) {
            removePreference(Settings.SCREEN_DEBUG);
        }

        if (!AudioAndHapticFeedbackManager.getInstance().hasVibrator()) {
            removePreference(Settings.PREF_VIBRATION_DURATION_SETTINGS);
        }

        // TODO: consolidate key preview dismiss delay with the key preview animation parameters.
        if (!Settings.readFromBuildConfigIfToShowKeyPreviewPopupOption(res)) {
            removePreference(Settings.PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY);
        } else {
            // TODO: Cleanup this setup.
            final ListPreference keyPreviewPopupDismissDelay =
                    (ListPreference) findPreference(Settings.PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY);
            final String popupDismissDelayDefaultValue = Integer.toString(res.getInteger(
                    R.integer.config_key_preview_linger_timeout));
            keyPreviewPopupDismissDelay.setEntries(new String[] {
                    res.getString(R.string.key_preview_popup_dismiss_no_delay),
                    res.getString(R.string.key_preview_popup_dismiss_default_delay),
            });
            keyPreviewPopupDismissDelay.setEntryValues(new String[] {
                    "0",
                    popupDismissDelayDefaultValue
            });
            if (null == keyPreviewPopupDismissDelay.getValue()) {
                keyPreviewPopupDismissDelay.setValue(popupDismissDelayDefaultValue);
            }
            keyPreviewPopupDismissDelay.setEnabled(
                    Settings.readKeyPreviewPopupEnabled(prefs, res));
        }

        setupKeypressVibrationDurationSettings();
        setupKeypressSoundVolumeSettings();
        setupKeyLongpressTimeoutSettings();
        refreshEnablingsOfKeypressSoundAndVibrationSettings();
    }

    @Override
    public void onResume() {
        super.onResume();
        final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        updateListPreferenceSummaryToCurrentValue(Settings.PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY);
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        final Resources res = getResources();
        if (key.equals(Settings.PREF_POPUP_ON)) {
            setPreferenceEnabled(Settings.PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY,
                    Settings.readKeyPreviewPopupEnabled(prefs, res));
        } else if (key.equals(Settings.PREF_SHOW_SETUP_WIZARD_ICON)) {
            SystemBroadcastReceiver.toggleAppIcon(getActivity());
        }
        updateListPreferenceSummaryToCurrentValue(Settings.PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY);
        refreshEnablingsOfKeypressSoundAndVibrationSettings();
    }

    private void refreshEnablingsOfKeypressSoundAndVibrationSettings() {
        final SharedPreferences prefs = getSharedPreferences();
        final Resources res = getResources();
        setPreferenceEnabled(Settings.PREF_VIBRATION_DURATION_SETTINGS,
                Settings.readVibrationEnabled(prefs, res));
        setPreferenceEnabled(Settings.PREF_KEYPRESS_SOUND_VOLUME,
                Settings.readKeypressSoundEnabled(prefs, res));
    }

    private void setupKeypressVibrationDurationSettings() {
        final SeekBarDialogPreference pref = (SeekBarDialogPreference)findPreference(
                Settings.PREF_VIBRATION_DURATION_SETTINGS);
        if (pref == null) {
            return;
        }
        final SharedPreferences prefs = getSharedPreferences();
        final Resources res = getResources();
        pref.setInterface(new SeekBarDialogPreference.ValueProxy() {
            @Override
            public void writeValue(final int value, final String key) {
                prefs.edit().putInt(key, value).apply();
            }

            @Override
            public void writeDefaultValue(final String key) {
                prefs.edit().remove(key).apply();
            }

            @Override
            public int readValue(final String key) {
                return Settings.readKeypressVibrationDuration(prefs, res);
            }

            @Override
            public int readDefaultValue(final String key) {
                return Settings.readDefaultKeypressVibrationDuration(res);
            }

            @Override
            public void feedbackValue(final int value) {
                AudioAndHapticFeedbackManager.getInstance().vibrate(value);
            }

            @Override
            public String getValueText(final int value) {
                if (value < 0) {
                    return res.getString(R.string.settings_system_default);
                }
                return res.getString(R.string.abbreviation_unit_milliseconds, value);
            }
        });
    }

    private void setupKeypressSoundVolumeSettings() {
        final SeekBarDialogPreference pref = (SeekBarDialogPreference)findPreference(
                Settings.PREF_KEYPRESS_SOUND_VOLUME);
        if (pref == null) {
            return;
        }
        final SharedPreferences prefs = getSharedPreferences();
        final Resources res = getResources();
        final AudioManager am = (AudioManager)getActivity().getSystemService(Context.AUDIO_SERVICE);
        pref.setInterface(new SeekBarDialogPreference.ValueProxy() {
            private static final float PERCENTAGE_FLOAT = 100.0f;

            private float getValueFromPercentage(final int percentage) {
                return percentage / PERCENTAGE_FLOAT;
            }

            private int getPercentageFromValue(final float floatValue) {
                return (int)(floatValue * PERCENTAGE_FLOAT);
            }

            @Override
            public void writeValue(final int value, final String key) {
                prefs.edit().putFloat(key, getValueFromPercentage(value)).apply();
            }

            @Override
            public void writeDefaultValue(final String key) {
                prefs.edit().remove(key).apply();
            }

            @Override
            public int readValue(final String key) {
                return getPercentageFromValue(Settings.readKeypressSoundVolume(prefs, res));
            }

            @Override
            public int readDefaultValue(final String key) {
                return getPercentageFromValue(Settings.readDefaultKeypressSoundVolume(res));
            }

            @Override
            public String getValueText(final int value) {
                if (value < 0) {
                    return res.getString(R.string.settings_system_default);
                }
                return Integer.toString(value);
            }

            @Override
            public void feedbackValue(final int value) {
                am.playSoundEffect(
                        AudioManager.FX_KEYPRESS_STANDARD, getValueFromPercentage(value));
            }
        });
    }

    private void setupKeyLongpressTimeoutSettings() {
        final SharedPreferences prefs = getSharedPreferences();
        final Resources res = getResources();
        final SeekBarDialogPreference pref = (SeekBarDialogPreference)findPreference(
                Settings.PREF_KEY_LONGPRESS_TIMEOUT);
        if (pref == null) {
            return;
        }
        pref.setInterface(new SeekBarDialogPreference.ValueProxy() {
            @Override
            public void writeValue(final int value, final String key) {
                prefs.edit().putInt(key, value).apply();
            }

            @Override
            public void writeDefaultValue(final String key) {
                prefs.edit().remove(key).apply();
            }

            @Override
            public int readValue(final String key) {
                return Settings.readKeyLongpressTimeout(prefs, res);
            }

            @Override
            public int readDefaultValue(final String key) {
                return Settings.readDefaultKeyLongpressTimeout(res);
            }

            @Override
            public String getValueText(final int value) {
                return res.getString(R.string.abbreviation_unit_milliseconds, value);
            }

            @Override
            public void feedbackValue(final int value) {}
        });
    }
}
