/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.inputmethod.latin.setup;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.util.Log;

import com.android.inputmethod.latin.settings.Settings;

/**
 * This class handles the {@link Intent#ACTION_MY_PACKAGE_REPLACED} broadcast intent when this IME
 * package has been replaced by a newer version of the same package. This class also handles
 * {@link Intent#ACTION_BOOT_COMPLETED} and {@link Intent#ACTION_USER_INITIALIZE} broadcast intent.
 *
 * If this IME has already been installed in the system image and a new version of this IME has
 * been installed, {@link Intent#ACTION_MY_PACKAGE_REPLACED} is received to this class to hide the
 * setup wizard's icon.
 *
 * If this IME has already been installed in the data partition and a new version of this IME has
 * been installed, {@link Intent#ACTION_MY_PACKAGE_REPLACED} is forwarded to this class but it
 * will not hide the setup wizard's icon, and the icon will appear on the launcher.
 *
 * If this IME hasn't been installed yet and has been newly installed, no
 * {@link Intent#ACTION_MY_PACKAGE_REPLACED} will be sent and the setup wizard's icon will appear
 * on the launcher.
 *
 * When the device has been booted, {@link Intent#ACTION_BOOT_COMPLETED} is forwarded to this class
 * to check whether the setup wizard's icon should be appeared or not on the launcher
 * depending on which partition this IME is installed.
 *
 * When a multiuser account has been created, {@link Intent#ACTION_USER_INITIALIZE} is forwarded to
 * this class to check whether the setup wizard's icon should be appeared or not on the launcher
 * depending on which partition this IME is installed.
 */
public final class LauncherIconVisibilityManager {
    private static final String TAG = LauncherIconVisibilityManager.class.getSimpleName();

    public static void updateSetupWizardIconVisibility(final Context context) {
        final ComponentName setupWizardActivity = new ComponentName(context, SetupActivity.class);
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean stateHasSet;
        if (Settings.readShowSetupWizardIcon(prefs, context)) {
            stateHasSet = setActivityState(context, setupWizardActivity,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
            Log.i(TAG, (stateHasSet ? "Enable activity: " : "Activity has already been enabled: ")
                    + setupWizardActivity);
        } else {
            stateHasSet = setActivityState(context, setupWizardActivity,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
            Log.i(TAG, (stateHasSet ? "Disable activity: " : "Activity has already been disabled: ")
                    + setupWizardActivity);
        }
    }

    private static boolean setActivityState(final Context context,
            final ComponentName activityComponent, final int activityState) {
        final PackageManager pm = context.getPackageManager();
        final int activityComponentState = pm.getComponentEnabledSetting(activityComponent);
        if (activityComponentState == activityState) {
            return false;
        }
        pm.setComponentEnabledSetting(
                activityComponent, activityState, PackageManager.DONT_KILL_APP);
        return true;
    }
}
