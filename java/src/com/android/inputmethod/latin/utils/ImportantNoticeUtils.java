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

package com.android.inputmethod.latin.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;

import com.android.inputmethod.latin.InputAttributes;
import com.android.inputmethod.latin.R;

public final class ImportantNoticeUtils {
    private static final String TAG = ImportantNoticeUtils.class.getSimpleName();

    // {@link SharedPreferences} name to save the last important notice version that has been
    // displayed to users.
    private static final String PREFERENCE_NAME = "important_notice";
    private static final String KEY_IMPORTANT_NOTICE_VERSION = "important_notice_version";

    // Copy of the hidden {@link Settings.Secure#USER_SETUP_COMPLETE} settings key.
    // The value is zero until each multiuser completes system setup wizard.
    // Caveat: This is a hidden API.
    private static final String Settings_Secure_USER_SETUP_COMPLETE = "user_setup_complete";
    private static final int USER_SETUP_IS_NOT_COMPLETE = 0;

    private ImportantNoticeUtils() {
        // This utility class is not publicly instantiable.
    }

    private static boolean isInSystemSetupWizard(final Context context) {
        try {
            final int userSetupComplete = Settings.Secure.getInt(
                    context.getContentResolver(), Settings_Secure_USER_SETUP_COMPLETE);
            return userSetupComplete == USER_SETUP_IS_NOT_COMPLETE;
        } catch (final SettingNotFoundException e) {
            Log.w(TAG, "Can't find settings in Settings.Secure: key="
                    + Settings_Secure_USER_SETUP_COMPLETE);
            return false;
        }
    }

    private static SharedPreferences getImportantNoticePreferences(final Context context) {
        return context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
    }

    private static int getCurrentImportantNoticeVersion(final Context context) {
        return context.getResources().getInteger(R.integer.config_important_notice_version);
    }

    private static boolean hasNewImportantNotice(final Context context) {
        final SharedPreferences prefs = getImportantNoticePreferences(context);
        final int lastVersion = prefs.getInt(KEY_IMPORTANT_NOTICE_VERSION, 0);
        return getCurrentImportantNoticeVersion(context) > lastVersion;
    }

    public static boolean shouldShowImportantNotice(final Context context,
            final InputAttributes inputAttributes) {
        if (inputAttributes == null || inputAttributes.mIsPasswordField) {
            return false;
        }
        return hasNewImportantNotice(context) && !isInSystemSetupWizard(context);
    }

    public static void updateLastImportantNoticeVersion(final Context context) {
        final SharedPreferences prefs = getImportantNoticePreferences(context);
        prefs.edit()
                .putInt(KEY_IMPORTANT_NOTICE_VERSION, getCurrentImportantNoticeVersion(context))
                .apply();
    }
}
