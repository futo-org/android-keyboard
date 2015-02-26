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

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import java.util.List;

/**
 * Utility for determining if the device has managed profiles.
 */
public class ManagedProfileUtils {
    private static final boolean DEBUG = false;
    private static final String TAG = ManagedProfileUtils.class.getSimpleName();

    private ManagedProfileUtils() {
        // This utility class is not publicly instantiable.
    }

    /**
     * Note that {@link UserManager#getUserProfiles} has been introduced
     * in API level 21 (Build.VERSION_CODES.LOLLIPOP).
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static boolean hasManagedWorkProfile(final Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }

        final UserManager userManagerService =
                (UserManager) context.getSystemService(Context.USER_SERVICE);
        if (userManagerService != null) {
            if (DEBUG) {
                Log.d(TAG, "Detecting managed profile...");
            }
            final List<UserHandle> userProfiles = userManagerService.getUserProfiles();
            if (userProfiles.size() > 1) {
                if (DEBUG) {
                    Log.d(TAG, "More than one user profile => Managed profile exists.");
                }
                return true;
            }
        }
        if (DEBUG) {
            Log.d(TAG, "Managed profile not detected.");
        }
        return false;
    }
}
