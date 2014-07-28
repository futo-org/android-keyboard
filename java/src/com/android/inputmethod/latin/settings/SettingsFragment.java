/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.app.Activity;
import android.app.backup.BackupManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.preference.TwoStatePreference;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.android.inputmethod.keyboard.KeyboardTheme;
import com.android.inputmethod.latin.AudioAndHapticFeedbackManager;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.define.ProductionFlags;
import com.android.inputmethod.latin.setup.LauncherIconVisibilityManager;
import com.android.inputmethod.latin.utils.ApplicationUtils;
import com.android.inputmethod.latin.utils.FeedbackUtils;
import com.android.inputmethodcommon.InputMethodSettingsFragment;

public final class SettingsFragment extends InputMethodSettingsFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = SettingsFragment.class.getSimpleName();

    private static final int NO_MENU_GROUP = Menu.NONE; // We don't care about menu grouping.
    private static final int MENU_FEEDBACK = Menu.FIRST; // The first menu item id and order.
    private static final int MENU_ABOUT = Menu.FIRST + 1; // The second menu item id and order.

    private void setPreferenceEnabled(final String preferenceKey, final boolean enabled) {
        final Preference preference = findPreference(preferenceKey);
        if (preference != null) {
            preference.setEnabled(enabled);
        }
    }

    private void updateListPreferenceSummaryToCurrentValue(final String prefKey) {
        // Because the "%s" summary trick of {@link ListPreference} doesn't work properly before
        // KitKat, we need to update the summary programmatically.
        final ListPreference listPreference = (ListPreference)findPreference(prefKey);
        if (listPreference == null) {
            return;
        }
        final CharSequence entries[] = listPreference.getEntries();
        final int entryIndex = listPreference.findIndexOfValue(listPreference.getValue());
        listPreference.setSummary(entryIndex < 0 ? null : entries[entryIndex]);
    }

    private static void removePreference(final String preferenceKey, final PreferenceGroup parent) {
        if (parent == null) {
            return;
        }
        final Preference preference = parent.findPreference(preferenceKey);
        if (preference != null) {
            parent.removePreference(preference);
        }
    }

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        setHasOptionsMenu(true);
        setInputMethodSettingsCategoryTitle(R.string.language_selection_title);
        setSubtypeEnablerTitle(R.string.select_language);
        addPreferencesFromResource(R.xml.prefs);
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        TwoStatePreferenceHelper.replaceCheckBoxPreferencesBySwitchPreferences(preferenceScreen);
        preferenceScreen.setTitle(
                ApplicationUtils.getActivityTitleResId(getActivity(), SettingsActivity.class));

        final Resources res = getResources();
        final Context context = getActivity();

        // When we are called from the Settings application but we are not already running, some
        // singleton and utility classes may not have been initialized.  We have to call
        // initialization method of these classes here. See {@link LatinIME#onCreate()}.
        AudioAndHapticFeedbackManager.init(context);

        final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        prefs.registerOnSharedPreferenceChangeListener(this);

        final PreferenceScreen advancedScreen =
                (PreferenceScreen) findPreference(Settings.SCREEN_ADVANCED);
        final PreferenceScreen debugScreen =
                (PreferenceScreen) findPreference(Settings.SCREEN_DEBUG);

        if (!Settings.isInternal(prefs)) {
            advancedScreen.removePreference(debugScreen);
        }

        if (!AudioAndHapticFeedbackManager.getInstance().hasVibrator()) {
            removePreference(Settings.PREF_VIBRATION_DURATION_SETTINGS, advancedScreen);
        }

        // TODO: consolidate key preview dismiss delay with the key preview animation parameters.
        if (!Settings.readFromBuildConfigIfToShowKeyPreviewPopupOption(res)) {
            removePreference(Settings.PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY, advancedScreen);
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

        if (!res.getBoolean(R.bool.config_setup_wizard_available)) {
            removePreference(Settings.PREF_SHOW_SETUP_WIZARD_ICON, advancedScreen);
        }

        if (ProductionFlags.IS_METRICS_LOGGING_SUPPORTED) {
            final Preference enableMetricsLogging =
                    findPreference(Settings.PREF_ENABLE_METRICS_LOGGING);
            if (enableMetricsLogging != null) {
                final int applicationLabelRes = context.getApplicationInfo().labelRes;
                final String applicationName = res.getString(applicationLabelRes);
                final String enableMetricsLoggingTitle = res.getString(
                        R.string.enable_metrics_logging, applicationName);
                enableMetricsLogging.setTitle(enableMetricsLoggingTitle);
            }
        } else {
            removePreference(Settings.PREF_ENABLE_METRICS_LOGGING, advancedScreen);
        }

        if (!Settings.readFromBuildConfigIfGestureInputEnabled(res)) {
            getPreferenceScreen().removePreference(findPreference(Settings.SCREEN_GESTURE));
        }

        AdditionalFeaturesSettingUtils.addAdditionalFeaturesPreferences(context, this);

        setupKeypressVibrationDurationSettings(prefs, res);
        setupKeypressSoundVolumeSettings(prefs, res);
        refreshEnablingsOfKeypressSoundAndVibrationSettings(prefs, res);
    }

    @Override
    public void onResume() {
        super.onResume();
        final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        final TwoStatePreference showSetupWizardIcon =
                (TwoStatePreference)findPreference(Settings.PREF_SHOW_SETUP_WIZARD_ICON);
        if (showSetupWizardIcon != null) {
            showSetupWizardIcon.setChecked(Settings.readShowSetupWizardIcon(prefs, getActivity()));
        }
        updateListPreferenceSummaryToCurrentValue(Settings.PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY);
        final ListPreference keyboardThemePref = (ListPreference)findPreference(
                Settings.PREF_KEYBOARD_THEME);
        if (keyboardThemePref != null) {
            final KeyboardTheme keyboardTheme = KeyboardTheme.getKeyboardTheme(prefs);
            final String value = Integer.toString(keyboardTheme.mThemeId);
            final CharSequence entries[] = keyboardThemePref.getEntries();
            final int entryIndex = keyboardThemePref.findIndexOfValue(value);
            keyboardThemePref.setSummary(entryIndex < 0 ? null : entries[entryIndex]);
            keyboardThemePref.setValue(value);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        final ListPreference keyboardThemePref = (ListPreference)findPreference(
                Settings.PREF_KEYBOARD_THEME);
        if (keyboardThemePref != null) {
            KeyboardTheme.saveKeyboardThemeId(keyboardThemePref.getValue(), prefs);
        }
    }

    @Override
    public void onDestroy() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(
                this);
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        final Activity activity = getActivity();
        if (activity == null) {
            // TODO: Introduce a static function to register this class and ensure that
            // onCreate must be called before "onSharedPreferenceChanged" is called.
            Log.w(TAG, "onSharedPreferenceChanged called before activity starts.");
            return;
        }
        (new BackupManager(activity)).dataChanged();
        final Resources res = getResources();
        if (key.equals(Settings.PREF_POPUP_ON)) {
            setPreferenceEnabled(Settings.PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY,
                    Settings.readKeyPreviewPopupEnabled(prefs, res));
        } else if (key.equals(Settings.PREF_SHOW_SETUP_WIZARD_ICON)) {
            LauncherIconVisibilityManager.updateSetupWizardIconVisibility(getActivity());
        }
        updateListPreferenceSummaryToCurrentValue(Settings.PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY);
        updateListPreferenceSummaryToCurrentValue(Settings.PREF_KEYBOARD_THEME);
        refreshEnablingsOfKeypressSoundAndVibrationSettings(prefs, getResources());
    }

    private void refreshEnablingsOfKeypressSoundAndVibrationSettings(
            final SharedPreferences sp, final Resources res) {
        setPreferenceEnabled(Settings.PREF_VIBRATION_DURATION_SETTINGS,
                Settings.readVibrationEnabled(sp, res));
        setPreferenceEnabled(Settings.PREF_KEYPRESS_SOUND_VOLUME,
                Settings.readKeypressSoundEnabled(sp, res));
    }

    private void setupKeypressVibrationDurationSettings(final SharedPreferences sp,
            final Resources res) {
        final SeekBarDialogPreference pref = (SeekBarDialogPreference)findPreference(
                Settings.PREF_VIBRATION_DURATION_SETTINGS);
        if (pref == null) {
            return;
        }
        pref.setInterface(new SeekBarDialogPreference.ValueProxy() {
            @Override
            public void writeValue(final int value, final String key) {
                sp.edit().putInt(key, value).apply();
            }

            @Override
            public void writeDefaultValue(final String key) {
                sp.edit().remove(key).apply();
            }

            @Override
            public int readValue(final String key) {
                return Settings.readKeypressVibrationDuration(sp, res);
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

    private void setupKeypressSoundVolumeSettings(final SharedPreferences sp, final Resources res) {
        final SeekBarDialogPreference pref = (SeekBarDialogPreference)findPreference(
                Settings.PREF_KEYPRESS_SOUND_VOLUME);
        if (pref == null) {
            return;
        }
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
                sp.edit().putFloat(key, getValueFromPercentage(value)).apply();
            }

            @Override
            public void writeDefaultValue(final String key) {
                sp.edit().remove(key).apply();
            }

            @Override
            public int readValue(final String key) {
                return getPercentageFromValue(Settings.readKeypressSoundVolume(sp, res));
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

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        if (FeedbackUtils.isFeedbackFormSupported()) {
            menu.add(NO_MENU_GROUP, MENU_FEEDBACK /* itemId */, MENU_FEEDBACK /* order */,
                    R.string.send_feedback);
        }
        final int aboutResId = FeedbackUtils.getAboutKeyboardTitleResId();
        if (aboutResId != 0) {
            menu.add(NO_MENU_GROUP, MENU_ABOUT /* itemId */, MENU_ABOUT /* order */, aboutResId);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == MENU_FEEDBACK) {
            FeedbackUtils.showFeedbackForm(getActivity());
            return true;
        }
        if (itemId == MENU_ABOUT) {
            final Intent aboutIntent = FeedbackUtils.getAboutKeyboardIntent(getActivity());
            if (aboutIntent != null) {
                startActivity(aboutIntent);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }
}
