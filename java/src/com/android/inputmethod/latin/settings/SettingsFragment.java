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
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.dictionarypack.DictionarySettingsActivity;
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
import com.android.inputmethod.research.ResearchLogger;
import com.android.inputmethodcommon.InputMethodSettingsFragment;

import java.util.TreeSet;

public final class SettingsFragment extends InputMethodSettingsFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = SettingsFragment.class.getSimpleName();
    private static final boolean DBG_USE_INTERNAL_PERSONAL_DICTIONARY_SETTINGS = false;
    private static final boolean USE_INTERNAL_PERSONAL_DICTIONARY_SETTIGS =
            DBG_USE_INTERNAL_PERSONAL_DICTIONARY_SETTINGS
                    || Build.VERSION.SDK_INT <= 18 /* Build.VERSION.JELLY_BEAN_MR2 */;

    private CheckBoxPreference mVoiceInputKeyPreference;
    private ListPreference mShowCorrectionSuggestionsPreference;
    private ListPreference mAutoCorrectionThresholdPreference;
    private ListPreference mKeyPreviewPopupDismissDelay;
    // Use bigrams to predict the next word when there is no input for it yet
    private CheckBoxPreference mBigramPrediction;

    private void setPreferenceEnabled(final String preferenceKey, final boolean enabled) {
        final Preference preference = findPreference(preferenceKey);
        if (preference != null) {
            preference.setEnabled(enabled);
        }
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
        setInputMethodSettingsCategoryTitle(R.string.language_selection_title);
        setSubtypeEnablerTitle(R.string.select_language);
        addPreferencesFromResource(R.xml.prefs);
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        if (preferenceScreen != null) {
            preferenceScreen.setTitle(
                    ApplicationUtils.getAcitivityTitleResId(getActivity(), SettingsActivity.class));
        }

        final Resources res = getResources();
        final Context context = getActivity();

        // When we are called from the Settings application but we are not already running, some
        // singleton and utility classes may not have been initialized.  We have to call
        // initialization method of these classes here. See {@link LatinIME#onCreate()}.
        SubtypeSwitcher.init(context);
        SubtypeLocaleUtils.init(context);
        AudioAndHapticFeedbackManager.init(context);

        mVoiceInputKeyPreference =
                (CheckBoxPreference) findPreference(Settings.PREF_VOICE_INPUT_KEY);
        mShowCorrectionSuggestionsPreference =
                (ListPreference) findPreference(Settings.PREF_SHOW_SUGGESTIONS_SETTING);
        final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        prefs.registerOnSharedPreferenceChangeListener(this);

        mAutoCorrectionThresholdPreference =
                (ListPreference) findPreference(Settings.PREF_AUTO_CORRECTION_THRESHOLD);
        mBigramPrediction = (CheckBoxPreference) findPreference(Settings.PREF_BIGRAM_PREDICTIONS);
        ensureConsistencyOfAutoCorrectionSettings();

        final PreferenceGroup generalSettings =
                (PreferenceGroup) findPreference(Settings.PREF_GENERAL_SETTINGS);
        final PreferenceGroup miscSettings =
                (PreferenceGroup) findPreference(Settings.PREF_MISC_SETTINGS);

        final Preference debugSettings = findPreference(Settings.PREF_DEBUG_SETTINGS);
        if (debugSettings != null) {
            if (Settings.isInternal(prefs)) {
                final Intent debugSettingsIntent = new Intent(Intent.ACTION_MAIN);
                debugSettingsIntent.setClassName(
                        context.getPackageName(), DebugSettingsActivity.class.getName());
                debugSettings.setIntent(debugSettingsIntent);
            } else {
                miscSettings.removePreference(debugSettings);
            }
        }

        final Preference feedbackSettings = findPreference(Settings.PREF_SEND_FEEDBACK);
        final Preference aboutSettings = findPreference(Settings.PREF_ABOUT_KEYBOARD);
        if (feedbackSettings != null) {
            if (FeedbackUtils.isFeedbackFormSupported()) {
                feedbackSettings.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(final Preference pref) {
                        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                            // Use development-only feedback mechanism
                            ResearchLogger.getInstance().presentFeedbackDialogFromSettings();
                        } else {
                            FeedbackUtils.showFeedbackForm(getActivity());
                        }
                        return true;
                    }
                });
                aboutSettings.setTitle(FeedbackUtils.getAboutKeyboardTitleResId());
                aboutSettings.setIntent(FeedbackUtils.getAboutKeyboardIntent(getActivity()));
            } else {
                miscSettings.removePreference(feedbackSettings);
                miscSettings.removePreference(aboutSettings);
            }
        }
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            // The about screen contains items that may be confusing in development-only versions.
            miscSettings.removePreference(aboutSettings);
        }

        final boolean showVoiceKeyOption = res.getBoolean(
                R.bool.config_enable_show_voice_key_option);
        if (!showVoiceKeyOption) {
            generalSettings.removePreference(mVoiceInputKeyPreference);
        }

        final PreferenceGroup advancedSettings =
                (PreferenceGroup) findPreference(Settings.PREF_ADVANCED_SETTINGS);
        if (!AudioAndHapticFeedbackManager.getInstance().hasVibrator()) {
            removePreference(Settings.PREF_VIBRATE_ON, generalSettings);
            removePreference(Settings.PREF_VIBRATION_DURATION_SETTINGS, advancedSettings);
        }

        mKeyPreviewPopupDismissDelay =
                (ListPreference) findPreference(Settings.PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY);
        if (!Settings.readFromBuildConfigIfToShowKeyPreviewPopupSettingsOption(res)) {
            removePreference(Settings.PREF_POPUP_ON, generalSettings);
            removePreference(Settings.PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY, advancedSettings);
        } else {
            final String popupDismissDelayDefaultValue = Integer.toString(res.getInteger(
                    R.integer.config_key_preview_linger_timeout));
            mKeyPreviewPopupDismissDelay.setEntries(new String[] {
                    res.getString(R.string.key_preview_popup_dismiss_no_delay),
                    res.getString(R.string.key_preview_popup_dismiss_default_delay),
            });
            mKeyPreviewPopupDismissDelay.setEntryValues(new String[] {
                    "0",
                    popupDismissDelayDefaultValue
            });
            if (null == mKeyPreviewPopupDismissDelay.getValue()) {
                mKeyPreviewPopupDismissDelay.setValue(popupDismissDelayDefaultValue);
            }
            mKeyPreviewPopupDismissDelay.setEnabled(
                    Settings.readKeyPreviewPopupEnabled(prefs, res));
        }

        if (!res.getBoolean(R.bool.config_setup_wizard_available)) {
            removePreference(Settings.PREF_SHOW_SETUP_WIZARD_ICON, advancedSettings);
        }

        setPreferenceEnabled(Settings.PREF_INCLUDE_OTHER_IMES_IN_LANGUAGE_SWITCH_LIST,
                Settings.readShowsLanguageSwitchKey(prefs));

        final PreferenceGroup textCorrectionGroup =
                (PreferenceGroup) findPreference(Settings.PREF_CORRECTION_SETTINGS);
        final PreferenceScreen dictionaryLink =
                (PreferenceScreen) findPreference(Settings.PREF_CONFIGURE_DICTIONARIES_KEY);
        final Intent intent = dictionaryLink.getIntent();
        intent.setClassName(context.getPackageName(), DictionarySettingsActivity.class.getName());
        final int number = context.getPackageManager().queryIntentActivities(intent, 0).size();
        if (0 >= number) {
            textCorrectionGroup.removePreference(dictionaryLink);
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
            removePreference(Settings.PREF_GESTURE_SETTINGS, getPreferenceScreen());
        }

        AdditionalFeaturesSettingUtils.addAdditionalFeaturesPreferences(context, this);

        setupKeyLongpressTimeoutSettings(prefs, res);
        setupKeypressVibrationDurationSettings(prefs, res);
        setupKeypressSoundVolumeSettings(prefs, res);
        refreshEnablingsOfKeypressSoundAndVibrationSettings(prefs, res);
    }

    @Override
    public void onResume() {
        super.onResume();
        final boolean isShortcutImeEnabled = SubtypeSwitcher.getInstance().isShortcutImeEnabled();
        if (!isShortcutImeEnabled) {
            getPreferenceScreen().removePreference(mVoiceInputKeyPreference);
        }
        final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        final CheckBoxPreference showSetupWizardIcon =
                (CheckBoxPreference)findPreference(Settings.PREF_SHOW_SETUP_WIZARD_ICON);
        if (showSetupWizardIcon != null) {
            showSetupWizardIcon.setChecked(Settings.readShowSetupWizardIcon(prefs, getActivity()));
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
        } else if (key.equals(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY)) {
            setPreferenceEnabled(Settings.PREF_INCLUDE_OTHER_IMES_IN_LANGUAGE_SWITCH_LIST,
                    Settings.readShowsLanguageSwitchKey(prefs));
        } else if (key.equals(Settings.PREF_SHOW_SETUP_WIZARD_ICON)) {
            LauncherIconVisibilityManager.updateSetupWizardIconVisibility(getActivity());
        }
        ensureConsistencyOfAutoCorrectionSettings();
        updateShowCorrectionSuggestionsSummary();
        updateKeyPreviewPopupDelaySummary();
        refreshEnablingsOfKeypressSoundAndVibrationSettings(prefs, getResources());
    }

    private void ensureConsistencyOfAutoCorrectionSettings() {
        final String autoCorrectionOff = getResources().getString(
                R.string.auto_correction_threshold_mode_index_off);
        final String currentSetting = mAutoCorrectionThresholdPreference.getValue();
        mBigramPrediction.setEnabled(!currentSetting.equals(autoCorrectionOff));
    }

    private void updateShowCorrectionSuggestionsSummary() {
        mShowCorrectionSuggestionsPreference.setSummary(
                getResources().getStringArray(R.array.prefs_suggestion_visibilities)
                [mShowCorrectionSuggestionsPreference.findIndexOfValue(
                        mShowCorrectionSuggestionsPreference.getValue())]);
    }

    private void updateCustomInputStylesSummary() {
        final PreferenceScreen customInputStyles =
                (PreferenceScreen)findPreference(Settings.PREF_CUSTOM_INPUT_STYLES);
        final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        final Resources res = getResources();
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

    private void updateKeyPreviewPopupDelaySummary() {
        final ListPreference lp = mKeyPreviewPopupDismissDelay;
        final CharSequence[] entries = lp.getEntries();
        if (entries == null || entries.length <= 0) return;
        lp.setSummary(entries[lp.findIndexOfValue(lp.getValue())]);
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

    private void setupKeyLongpressTimeoutSettings(final SharedPreferences sp,
            final Resources res) {
        final SeekBarDialogPreference pref = (SeekBarDialogPreference)findPreference(
                Settings.PREF_KEY_LONGPRESS_TIMEOUT);
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
                return Settings.readKeyLongpressTimeout(sp, res);
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
}
