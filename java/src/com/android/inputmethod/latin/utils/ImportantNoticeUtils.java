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
import android.text.TextUtils;
import android.util.Log;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.R;

import java.util.concurrent.TimeUnit;

public final class ImportantNoticeUtils {
    private static final String TAG = ImportantNoticeUtils.class.getSimpleName();

    // {@link SharedPreferences} name to save the last important notice version that has been
    // displayed to users.
    private static final String PREFERENCE_NAME = "important_notice_pref";
    @UsedForTesting
    static final String KEY_IMPORTANT_NOTICE_VERSION = "important_notice_version";
    @UsedForTesting
    static final String KEY_TIMESTAMP_OF_FIRST_IMPORTANT_NOTICE =
            "timestamp_of_first_important_notice";
    @UsedForTesting
    static final long TIMEOUT_OF_IMPORTANT_NOTICE = TimeUnit.HOURS.toMillis(23);
    public static final int VERSION_TO_ENABLE_PERSONALIZED_SUGGESTIONS = 1;

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

    @UsedForTesting
    static SharedPreferences getImportantNoticePreferences(final Context context) {
        return context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
    }

    @UsedForTesting
    static int getCurrentImportantNoticeVersion(final Context context) {
        return context.getResources().getInteger(R.integer.config_important_notice_version);
    }

    @UsedForTesting
    static int getLastImportantNoticeVersion(final Context context) {
        return getImportantNoticePreferences(context).getInt(KEY_IMPORTANT_NOTICE_VERSION, 0);
    }

    public static int getNextImportantNoticeVersion(final Context context) {
        return getLastImportantNoticeVersion(context) + 1;
    }

    private static boolean hasNewImportantNotice(final Context context) {
        final int lastVersion = getLastImportantNoticeVersion(context);
        return getCurrentImportantNoticeVersion(context) > lastVersion;
    }

    @UsedForTesting
    static boolean hasTimeoutPassed(final Context context, final long currentTimeInMillis) {
        final SharedPreferences prefs = getImportantNoticePreferences(context);
        if (!prefs.contains(KEY_TIMESTAMP_OF_FIRST_IMPORTANT_NOTICE)) {
            prefs.edit()
                    .putLong(KEY_TIMESTAMP_OF_FIRST_IMPORTANT_NOTICE, currentTimeInMillis)
                    .apply();
        }
        final long firstDisplayTimeInMillis = prefs.getLong(
                KEY_TIMESTAMP_OF_FIRST_IMPORTANT_NOTICE, currentTimeInMillis);
        final long elapsedTime = currentTimeInMillis - firstDisplayTimeInMillis;
        return elapsedTime >= TIMEOUT_OF_IMPORTANT_NOTICE;
    }

    public static boolean shouldShowImportantNotice(final Context context) {
        if (!hasNewImportantNotice(context)) {
            return false;
        }
        final String importantNoticeTitle = getNextImportantNoticeTitle(context);
        if (TextUtils.isEmpty(importantNoticeTitle)) {
            return false;
        }
        if (isInSystemSetupWizard(context)) {
            return false;
        }
        if (hasTimeoutPassed(context, System.currentTimeMillis())) {
            updateLastImportantNoticeVersion(context);
            return false;
        }
        return true;
    }

    public static void updateLastImportantNoticeVersion(final Context context) {
        getImportantNoticePreferences(context)
                .edit()
                .putInt(KEY_IMPORTANT_NOTICE_VERSION, getNextImportantNoticeVersion(context))
                .remove(KEY_TIMESTAMP_OF_FIRST_IMPORTANT_NOTICE)
                .apply();
    }

    public static String getNextImportantNoticeTitle(final Context context) {
        final int nextVersion = getNextImportantNoticeVersion(context);
        final String[] importantNoticeTitleArray = context.getResources().getStringArray(
                R.array.important_notice_title_array);
        if (nextVersion > 0 && nextVersion < importantNoticeTitleArray.length) {
            return importantNoticeTitleArray[nextVersion];
        }
        return null;
    }

    public static String getNextImportantNoticeContents(final Context context) {
        final int nextVersion = getNextImportantNoticeVersion(context);
        final String[] importantNoticeContentsArray = context.getResources().getStringArray(
                R.array.important_notice_contents_array);
        if (nextVersion > 0 && nextVersion < importantNoticeContentsArray.length) {
            return importantNoticeContentsArray[nextVersion];
        }
        return null;
    }
}
