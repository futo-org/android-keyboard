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

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.text.TextUtils;
import android.util.Log;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.permissions.PermissionsUtil;
import com.android.inputmethod.latin.settings.SettingsValues;

import java.util.concurrent.TimeUnit;

public final class ImportantNoticeUtils {
    private static final String TAG = ImportantNoticeUtils.class.getSimpleName();

    // {@link SharedPreferences} name to save the last important notice version that has been
    // displayed to users.
    private static final String PREFERENCE_NAME = "important_notice_pref";

    private static final String KEY_SUGGEST_CONTACTS_NOTICE = "important_notice_suggest_contacts";

    @UsedForTesting
    static final String KEY_TIMESTAMP_OF_CONTACTS_NOTICE = "timestamp_of_suggest_contacts_notice";

    @UsedForTesting
    static final long TIMEOUT_OF_IMPORTANT_NOTICE = TimeUnit.HOURS.toMillis(23);

    // Copy of the hidden {@link Settings.Secure#USER_SETUP_COMPLETE} settings key.
    // The value is zero until each multiuser completes system setup wizard.
    // Caveat: This is a hidden API.
    private static final String Settings_Secure_USER_SETUP_COMPLETE = "user_setup_complete";
    private static final int USER_SETUP_IS_NOT_COMPLETE = 0;

    private ImportantNoticeUtils() {
        // This utility class is not publicly instantiable.
    }

    @UsedForTesting
    static boolean isInSystemSetupWizard(final Context context) {
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

    @UsedForTesting
    static SharedPreferences getImportantNoticePreferences(final Context context) {
        return context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
    }

    @UsedForTesting
    static boolean hasContactsNoticeShown(final Context context) {
        return getImportantNoticePreferences(context).getBoolean(
                KEY_SUGGEST_CONTACTS_NOTICE, false);
    }

    public static boolean shouldShowImportantNotice(final Context context,
            final SettingsValues settingsValues) {
        // Check to see whether "Use Contacts" is enabled by the user.
        if (!settingsValues.mUseContactsDict) {
            return false;
        }

        if (hasContactsNoticeShown(context)) {
            return false;
        }

        // Don't show the dialog if we have all the permissions.
        if (PermissionsUtil.checkAllPermissionsGranted(
                context, Manifest.permission.READ_CONTACTS)) {
            return false;
        }

        final String importantNoticeTitle = getSuggestContactsNoticeTitle(context);
        if (TextUtils.isEmpty(importantNoticeTitle)) {
            return false;
        }
        if (isInSystemSetupWizard(context)) {
            return false;
        }
        if (hasContactsNoticeTimeoutPassed(context, System.currentTimeMillis())) {
            updateContactsNoticeShown(context);
            return false;
        }
        return true;
    }

    public static String getSuggestContactsNoticeTitle(final Context context) {
        return context.getResources().getString(R.string.important_notice_suggest_contact_names);
    }

    @UsedForTesting
    static boolean hasContactsNoticeTimeoutPassed(
            final Context context, final long currentTimeInMillis) {
        final SharedPreferences prefs = getImportantNoticePreferences(context);
        if (!prefs.contains(KEY_TIMESTAMP_OF_CONTACTS_NOTICE)) {
            prefs.edit()
                    .putLong(KEY_TIMESTAMP_OF_CONTACTS_NOTICE, currentTimeInMillis)
                    .apply();
        }
        final long firstDisplayTimeInMillis = prefs.getLong(
                KEY_TIMESTAMP_OF_CONTACTS_NOTICE, currentTimeInMillis);
        final long elapsedTime = currentTimeInMillis - firstDisplayTimeInMillis;
        return elapsedTime >= TIMEOUT_OF_IMPORTANT_NOTICE;
    }

    public static void updateContactsNoticeShown(final Context context) {
        getImportantNoticePreferences(context)
                .edit()
                .putBoolean(KEY_SUGGEST_CONTACTS_NOTICE, true)
                .remove(KEY_TIMESTAMP_OF_CONTACTS_NOTICE)
                .apply();
    }
}
