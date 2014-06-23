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
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Build;
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
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.dictionarypack.DictionarySettingsActivity;
import com.android.inputmethod.keyboard.KeyboardTheme;
import com.android.inputmethod.latin.AudioAndHapticFeedbackManager;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SubtypeSwitcher;
import com.android.inputmethod.latin.define.ProductionFlag;
import com.android.inputmethod.latin.setup.LauncherIconVisibilityManager;
import com.android.inputmethod.latin.userdictionary.UserDictionaryList;
import com.android.inputmethod.latin.userdictionary.UserDictionarySettings;
import com.android.inputmethod.latin.utils.AdditionalSubtypeUtils;
import com.android.inputmethod.latin.utils.ApplicationUtils;
import com.android.inputmethod.latin.utils.FeedbackUtils;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;
import com.android.inputmethodcommon.InputMethodSettingsFragment;

import java.util.TreeSet;

public final class SettingsFragment extends InputMethodSettingsFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = SettingsFragment.class.getSimpleName();
    private static final boolean DBG_USE_INTERNAL_PERSONAL_DICTIONARY_SETTINGS = false;
    private static final boolean USE_INTERNAL_PERSONAL_DICTIONARY_SETTIGS =
            DBG_USE_INTERNAL_PERSONAL_DICTIONARY_SETTINGS
            || Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR2;

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
        if (preferenceScreen != null) {
            preferenceScreen.setTitle(
                    ApplicationUtils.getActivityTitleResId(getActivity(), SettingsActivity.class));
        }

        final Resources res = getResources();
        final Context context = getActivity();

        // When we are called from the Settings application but we are not already running, some
        // singleton and utility classes may not have been initialized.  We have to call
        // initialization method of these classes here. See {@link LatinIME#onCreate()}.
        SubtypeSwitcher.init(context);
        SubtypeLocaleUtils.init(context);
        AudioAndHapticFeedbackManager.init(context);

        final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        prefs.registerOnSharedPreferenceChangeListener(this);

        ensureConsistencyOfAutoCorrectionSettings();

        final PreferenceScreen inputScreen =
                (PreferenceScreen) findPreference(Settings.SCREEN_INPUT);
        final PreferenceScreen advancedScreen =
                (PreferenceScreen) findPreference(Settings.SCREEN_ADVANCED);
        final Preference debugScreen = findPreference(Settings.SCREEN_DEBUG);
        if (Settings.isInternal(prefs)) {
            final Intent debugSettingsIntent = new Intent(Intent.ACTION_MAIN);
            debugSettingsIntent.setClassName(
                    context.getPackageName(), DebugSettingsActivity.class.getName());
            debugScreen.setIntent(debugSettingsIntent);
        } else {
            advancedScreen.removePreference(debugScreen);
        }

        final boolean showVoiceKeyOption = res.getBoolean(
                R.bool.config_enable_show_voice_key_option);
        if (!showVoiceKeyOption) {
            removePreference(Settings.PREF_VOICE_INPUT_KEY, inputScreen);
        }

        if (!AudioAndHapticFeedbackManager.getInstance().hasVibrator()) {
            removePreference(Settings.PREF_VIBRATE_ON, inputScreen);
            removePreference(Settings.PREF_VIBRATION_DURATION_SETTINGS, advancedScreen);
        }
        if (!Settings.ENABLE_SHOW_LANGUAGE_SWITCH_KEY_SETTINGS) {
            final PreferenceScreen multiLingualScreen =
                    (PreferenceScreen) findPreference(Settings.SCREEN_MULTI_LINGUAL);
            removePreference(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, multiLingualScreen);
            removePreference(
                    Settings.PREF_INCLUDE_OTHER_IMES_IN_LANGUAGE_SWITCH_LIST, multiLingualScreen);
        }

        // TODO: consolidate key preview dismiss delay with the key preview animation parameters.
        if (!Settings.readFromBuildConfigIfToShowKeyPreviewPopupOption(res)) {
            removePreference(Settings.PREF_POPUP_ON, inputScreen);
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

        final PreferenceScreen correctionScreen =
                (PreferenceScreen) findPreference(Settings.SCREEN_CORRECTION);
        final PreferenceScreen dictionaryLink =
                (PreferenceScreen) findPreference(Settings.PREF_CONFIGURE_DICTIONARIES_KEY);
        final Intent intent = dictionaryLink.getIntent();
        intent.setClassName(context.getPackageName(), DictionarySettingsActivity.class.getName());
        final int number = context.getPackageManager().queryIntentActivities(intent, 0).size();
        if (0 >= number) {
            correctionScreen.removePreference(dictionaryLink);
        }

        if (ProductionFlag.IS_METRICS_LOGGING_SUPPORTED) {
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

        final Preference editPersonalDictionary =
                findPreference(Settings.PREF_EDIT_PERSONAL_DICTIONARY);
        final Intent editPersonalDictionaryIntent = editPersonalDictionary.getIntent();
        final ResolveInfo ri = USE_INTERNAL_PERSONAL_DICTIONARY_SETTIGS ? null
                : context.getPackageManager().resolveActivity(
                        editPersonalDictionaryIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (ri == null) {
            overwriteUserDictionaryPreference(editPersonalDictionary);
        }

        if (!Settings.readFromBuildConfigIfGestureInputEnabled(res)) {
            removePreference(Settings.SCREEN_GESTURE, getPreferenceScreen());
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
        final Resources res = getResources();
        final Preference voiceInputKeyOption = findPreference(Settings.PREF_VOICE_INPUT_KEY);
        if (voiceInputKeyOption != null) {
            final boolean isShortcutImeEnabled = SubtypeSwitcher.getInstance()
                    .isShortcutImeEnabled();
            voiceInputKeyOption.setEnabled(isShortcutImeEnabled);
            voiceInputKeyOption.setSummary(isShortcutImeEnabled ? null
                    : res.getText(R.string.voice_input_disabled_summary));
        }
        final TwoStatePreference showSetupWizardIcon =
                (TwoStatePreference)findPreference(Settings.PREF_SHOW_SETUP_WIZARD_ICON);
        if (showSetupWizardIcon != null) {
            showSetupWizardIcon.setChecked(Settings.readShowSetupWizardIcon(prefs, getActivity()));
        }
        updateListPreferenceSummaryToCurrentValue(Settings.PREF_SHOW_SUGGESTIONS_SETTING);
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
        updateCustomInputStylesSummary(prefs, res);
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
        ensureConsistencyOfAutoCorrectionSettings();
        updateListPreferenceSummaryToCurrentValue(Settings.PREF_SHOW_SUGGESTIONS_SETTING);
        updateListPreferenceSummaryToCurrentValue(Settings.PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY);
        updateListPreferenceSummaryToCurrentValue(Settings.PREF_KEYBOARD_THEME);
        refreshEnablingsOfKeypressSoundAndVibrationSettings(prefs, getResources());
    }

    private void ensureConsistencyOfAutoCorrectionSettings() {
        final String autoCorrectionOff = getResources().getString(
                R.string.auto_correction_threshold_mode_index_off);
        final ListPreference autoCorrectionThresholdPref = (ListPreference)findPreference(
                Settings.PREF_AUTO_CORRECTION_THRESHOLD);
        final String currentSetting = autoCorrectionThresholdPref.getValue();
        setPreferenceEnabled(
                Settings.PREF_BIGRAM_PREDICTIONS, !currentSetting.equals(autoCorrectionOff));
    }

    private void updateCustomInputStylesSummary(final SharedPreferences prefs,
            final Resources res) {
        final PreferenceScreen customInputStyles =
                (PreferenceScreen)findPreference(Settings.PREF_CUSTOM_INPUT_STYLES);
        final String prefSubtype = Settings.readPrefAdditionalSubtypes(prefs, res);
        final InputMethodSubtype[] subtypes =
                AdditionalSubtypeUtils.createAdditionalSubtypesArray(prefSubtype);
        final StringBuilder styles = new StringBuilder();
        for (final InputMethodSubtype subtype : subtypes) {
            if (styles.length() > 0) styles.append(", ");
            styles.append(SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(subtype));
        }
        customInputStyles.setSummary(styles);
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

    private void overwriteUserDictionaryPreference(Preference userDictionaryPreference) {
        final Activity activity = getActivity();
        final TreeSet<String> localeList = UserDictionaryList.getUserDictionaryLocalesSet(activity);
        if (null == localeList) {
            // The locale list is null if and only if the user dictionary service is
            // not present or disabled. In this case we need to remove the preference.
            getPreferenceScreen().removePreference(userDictionaryPreference);
        } else if (localeList.size() <= 1) {
            userDictionaryPreference.setFragment(UserDictionarySettings.class.getName());
            // If the size of localeList is 0, we don't set the locale parameter in the
            // extras. This will be interpreted by the UserDictionarySettings class as
            // meaning "the current locale".
            // Note that with the current code for UserDictionaryList#getUserDictionaryLocalesSet()
            // the locale list always has at least one element, since it always includes the current
            // locale explicitly. @see UserDictionaryList.getUserDictionaryLocalesSet().
            if (localeList.size() == 1) {
                final String locale = (String)localeList.toArray()[0];
                userDictionaryPreference.getExtras().putString("locale", locale);
            }
        } else {
            userDictionaryPreference.setFragment(UserDictionaryList.class.getName());
        }
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        if (!FeedbackUtils.isFeedbackFormSupported()) {
            return;
        }
        menu.add(NO_MENU_GROUP, MENU_FEEDBACK /* itemId */, MENU_FEEDBACK /* order */,
                R.string.send_feedback);
        menu.add(NO_MENU_GROUP, MENU_ABOUT /* itemId */, MENU_ABOUT /* order */,
                FeedbackUtils.getAboutKeyboardTitleResId());
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == MENU_FEEDBACK) {
            FeedbackUtils.showFeedbackForm(getActivity());
            return true;
        }
        if (itemId == MENU_ABOUT) {
            startActivity(FeedbackUtils.getAboutKeyboardIntent(getActivity()));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
