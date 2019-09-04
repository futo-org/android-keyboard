/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.inputmethod.latin.permissions;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for permissions.
 */
public class PermissionsUtil {

    /**
     * Returns the list of permissions not granted from the given list of permissions.
     * @param context Context
     * @param permissions list of permissions to check.
     * @return the list of permissions that do not have permission to use.
     */
    public static List<String> getDeniedPermissions(Context context,
                                                          String... permissions) {
        final List<String> deniedPermissions = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                deniedPermissions.add(permission);
            }
        }
        return deniedPermissions;
    }

    /**
     * Uses the given activity and requests the user for permissions.
     * @param activity activity to use.
     * @param requestCode request code/id to use.
     * @param permissions String array of permissions that needs to be requested.
     */
    public static void requestPermissions(Activity activity, int requestCode,
                                          String[] permissions) {
        ActivityCompat.requestPermissions(activity, permissions, requestCode);
    }

    /**
     * Checks if all the permissions are granted.
     */
    public static boolean allGranted(@NonNull int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Queries if al the permissions are granted for the given permission strings.
     */
    public static boolean checkAllPermissionsGranted(Context context, String... permissions) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            // For all pre-M devices, we should have all the premissions granted on install.
            return true;
        }

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}
