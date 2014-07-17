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

import android.os.Build;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.SwitchPreference;

import java.util.ArrayList;

public class TwoStatePreferenceHelper {
    private static final String EMPTY_TEXT = "";

    private TwoStatePreferenceHelper() {
        // This utility class is not publicly instantiable.
    }

    public static void replaceCheckBoxPreferencesBySwitchPreferences(final PreferenceGroup group) {
        // The keyboard settings keeps using a CheckBoxPreference on KitKat or previous.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            return;
        }
        // The keyboard settings starts using a SwitchPreference without switch on/off text on
        // API versions newer than KitKat.
        replaceAllCheckBoxPreferencesBySwitchPreferences(group);
    }

    private static void replaceAllCheckBoxPreferencesBySwitchPreferences(
            final PreferenceGroup group) {
        final ArrayList<Preference> preferences = new ArrayList<>();
        final int count = group.getPreferenceCount();
        for (int index = 0; index < count; index++) {
            preferences.add(group.getPreference(index));
        }
        group.removeAll();
        for (int index = 0; index < count; index++) {
            final Preference preference = preferences.get(index);
            if (preference instanceof CheckBoxPreference) {
                addSwitchPreferenceBasedOnCheckBoxPreference((CheckBoxPreference)preference, group);
            } else {
                group.addPreference(preference);
                if (preference instanceof PreferenceGroup) {
                    replaceAllCheckBoxPreferencesBySwitchPreferences((PreferenceGroup)preference);
                }
            }
        }
    }

    static void addSwitchPreferenceBasedOnCheckBoxPreference(final CheckBoxPreference checkBox,
            final PreferenceGroup group) {
        final SwitchPreference switchPref = new SwitchPreference(checkBox.getContext());
        switchPref.setTitle(checkBox.getTitle());
        switchPref.setKey(checkBox.getKey());
        switchPref.setOrder(checkBox.getOrder());
        switchPref.setPersistent(checkBox.isPersistent());
        switchPref.setEnabled(checkBox.isEnabled());
        switchPref.setChecked(checkBox.isChecked());
        switchPref.setSummary(checkBox.getSummary());
        switchPref.setSummaryOn(checkBox.getSummaryOn());
        switchPref.setSummaryOff(checkBox.getSummaryOff());
        switchPref.setSwitchTextOn(EMPTY_TEXT);
        switchPref.setSwitchTextOff(EMPTY_TEXT);
        group.addPreference(switchPref);
        switchPref.setDependency(checkBox.getDependency());
    }
}
