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

package com.android.inputmethod.latin.utils;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

public final class ApplicationUtils {
    private static final String TAG = ApplicationUtils.class.getSimpleName();

    private ApplicationUtils() {
        // This utility class is not publicly instantiable.
    }

    public static int getActivityTitleResId(final Context context,
            final Class<? extends Activity> cls) {
        final ComponentName cn = new ComponentName(context, cls);
        try {
            final ActivityInfo ai = context.getPackageManager().getActivityInfo(cn, 0);
            if (ai != null) {
                return ai.labelRes;
            }
        } catch (final NameNotFoundException e) {
            Log.e(TAG, "Failed to get settings activity title res id.", e);
        }
        return 0;
    }

    /**
     * A utility method to get the application's PackageInfo.versionName
     * @return the application's PackageInfo.versionName
     */
    public static String getVersionName(final Context context) {
        try {
            if (context == null) {
                return "";
            }
            final String packageName = context.getPackageName();
            final PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            return info.versionName;
        } catch (final NameNotFoundException e) {
            Log.e(TAG, "Could not find version info.", e);
        }
        return "";
    }

    /**
     * A utility method to get the application's PackageInfo.versionCode
     * @return the application's PackageInfo.versionCode
     */
    public static int getVersionCode(final Context context) {
        try {
            if (context == null) {
                return 0;
            }
            final String packageName = context.getPackageName();
            final PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            return info.versionCode;
        } catch (final NameNotFoundException e) {
            Log.e(TAG, "Could not find version info.", e);
        }
        return 0;
    }
}
