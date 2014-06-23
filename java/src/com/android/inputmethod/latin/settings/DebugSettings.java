/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Process;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;

import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.DictionaryDumpBroadcastReceiver;
import com.android.inputmethod.latin.DictionaryFacilitator;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.debug.ExternalDictionaryGetterForDebug;
import com.android.inputmethod.latin.utils.ApplicationUtils;
import com.android.inputmethod.latin.utils.ResourceUtils;

public final class DebugSettings extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String PREF_DEBUG_MODE = "debug_mode";
    public static final String PREF_FORCE_NON_DISTINCT_MULTITOUCH = "force_non_distinct_multitouch";
    public static final String PREF_KEY_PREVIEW_SHOW_UP_START_SCALE =
            "pref_key_preview_show_up_start_scale";
    public static final String PREF_KEY_PREVIEW_DISMISS_END_SCALE =
            "pref_key_preview_dismiss_end_scale";
    public static final String PREF_KEY_PREVIEW_SHOW_UP_DURATION =
            "pref_key_preview_show_up_duration";
    public static final String PREF_KEY_PREVIEW_DISMISS_DURATION =
            "pref_key_preview_dismiss_duration";
    private static final String PREF_READ_EXTERNAL_DICTIONARY = "read_external_dictionary";
    private static final String PREF_KEY_DUMP_DICTS = "pref_key_dump_dictionaries";
    private static final String PREF_KEY_DUMP_DICT_PREFIX = "pref_key_dump_dictionaries";
    private static final String DICT_NAME_KEY_FOR_EXTRAS = "dict_name";
    public static final String PREF_SLIDING_KEY_INPUT_PREVIEW = "pref_sliding_key_input_preview";
    public static final String PREF_KEY_LONGPRESS_TIMEOUT = "pref_key_longpress_timeout";

    private boolean mServiceNeedsRestart = false;
    private CheckBoxPreference mDebugMode;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_for_debug);
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        prefs.registerOnSharedPreferenceChangeListener(this);

        final PreferenceScreen readExternalDictionary =
                (PreferenceScreen) findPreference(PREF_READ_EXTERNAL_DICTIONARY);
        if (null != readExternalDictionary) {
            readExternalDictionary.setOnPreferenceClickListener(
                    new Preference.OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(final Preference arg0) {
                            ExternalDictionaryGetterForDebug.chooseAndInstallDictionary(
                                    getActivity());
                            mServiceNeedsRestart = true;
                            return true;
                        }
                    });
        }

        final PreferenceGroup dictDumpPreferenceGroup =
                (PreferenceGroup)findPreference(PREF_KEY_DUMP_DICTS);
        final OnPreferenceClickListener dictDumpPrefClickListener =
                new DictDumpPrefClickListener(this);
        for (final String dictName : DictionaryFacilitator.DICT_TYPE_TO_CLASS.keySet()) {
            final Preference preference = new Preference(getActivity());
            preference.setKey(PREF_KEY_DUMP_DICT_PREFIX + dictName);
            preference.setTitle("Dump " + dictName + " dictionary");
            preference.setOnPreferenceClickListener(dictDumpPrefClickListener);
            preference.getExtras().putString(DICT_NAME_KEY_FOR_EXTRAS, dictName);
            dictDumpPreferenceGroup.addPreference(preference);
        }
        final Resources res = getResources();
        setupKeyLongpressTimeoutSettings(prefs, res);
        setupKeyPreviewAnimationDuration(prefs, res, PREF_KEY_PREVIEW_SHOW_UP_DURATION,
                res.getInteger(R.integer.config_key_preview_show_up_duration));
        setupKeyPreviewAnimationDuration(prefs, res, PREF_KEY_PREVIEW_DISMISS_DURATION,
                res.getInteger(R.integer.config_key_preview_dismiss_duration));
        setupKeyPreviewAnimationScale(prefs, res, PREF_KEY_PREVIEW_SHOW_UP_START_SCALE,
                ResourceUtils.getFloatFromFraction(
                        res, R.fraction.config_key_preview_show_up_start_scale));
        setupKeyPreviewAnimationScale(prefs, res, PREF_KEY_PREVIEW_DISMISS_END_SCALE,
                ResourceUtils.getFloatFromFraction(
                        res, R.fraction.config_key_preview_dismiss_end_scale));

        mServiceNeedsRestart = false;
        mDebugMode = (CheckBoxPreference) findPreference(PREF_DEBUG_MODE);
        updateDebugMode();
    }

    private static class DictDumpPrefClickListener implements OnPreferenceClickListener {
        final PreferenceFragment mPreferenceFragment;

        public DictDumpPrefClickListener(final PreferenceFragment preferenceFragment) {
            mPreferenceFragment = preferenceFragment;
        }

        @Override
        public boolean onPreferenceClick(final Preference arg0) {
            final String dictName = arg0.getExtras().getString(DICT_NAME_KEY_FOR_EXTRAS);
            if (dictName != null) {
                final Intent intent =
                        new Intent(DictionaryDumpBroadcastReceiver.DICTIONARY_DUMP_INTENT_ACTION);
                intent.putExtra(DictionaryDumpBroadcastReceiver.DICTIONARY_NAME_KEY, dictName);
                mPreferenceFragment.getActivity().sendBroadcast(intent);
            }
            return true;
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mServiceNeedsRestart) {
            Process.killProcess(Process.myPid());
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.equals(PREF_DEBUG_MODE) && mDebugMode != null) {
            mDebugMode.setChecked(prefs.getBoolean(PREF_DEBUG_MODE, false));
            updateDebugMode();
            mServiceNeedsRestart = true;
            return;
        }
        if (key.equals(PREF_FORCE_NON_DISTINCT_MULTITOUCH)) {
            mServiceNeedsRestart = true;
            return;
        }
    }

    private void updateDebugMode() {
        if (mDebugMode == null) {
            return;
        }
        boolean isDebugMode = mDebugMode.isChecked();
        final String version = getResources().getString(
                R.string.version_text, ApplicationUtils.getVersionName(getActivity()));
        if (!isDebugMode) {
            mDebugMode.setTitle(version);
            mDebugMode.setSummary("");
        } else {
            mDebugMode.setTitle(getResources().getString(R.string.prefs_debug_mode));
            mDebugMode.setSummary(version);
        }
    }

    private void setupKeyLongpressTimeoutSettings(final SharedPreferences sp,
            final Resources res) {
        final SeekBarDialogPreference pref = (SeekBarDialogPreference)findPreference(
                PREF_KEY_LONGPRESS_TIMEOUT);
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

    private void setupKeyPreviewAnimationScale(final SharedPreferences sp, final Resources res,
            final String prefKey, final float defaultValue) {
        final SeekBarDialogPreference pref = (SeekBarDialogPreference)findPreference(prefKey);
        if (pref == null) {
            return;
        }
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
                return getPercentageFromValue(
                        Settings.readKeyPreviewAnimationScale(sp, key, defaultValue));
            }

            @Override
            public int readDefaultValue(final String key) {
                return getPercentageFromValue(defaultValue);
            }

            @Override
            public String getValueText(final int value) {
                if (value < 0) {
                    return res.getString(R.string.settings_system_default);
                }
                return String.format("%d%%", value);
            }

            @Override
            public void feedbackValue(final int value) {}
        });
    }

    private void setupKeyPreviewAnimationDuration(final SharedPreferences sp, final Resources res,
            final String prefKey, final int defaultValue) {
        final SeekBarDialogPreference pref = (SeekBarDialogPreference)findPreference(prefKey);
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
                return Settings.readKeyPreviewAnimationDuration(sp, key, defaultValue);
            }

            @Override
            public int readDefaultValue(final String key) {
                return defaultValue;
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
