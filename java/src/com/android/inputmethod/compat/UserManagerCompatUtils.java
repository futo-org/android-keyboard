/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.inputmethod.compat;

import android.content.Context;
import android.os.Build;
import android.os.UserManager;
import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.reflect.Method;

import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * A temporary solution until {@code UserManagerCompat.isUserUnlocked()} in the support-v4 library
 * becomes publicly available.
 */
public final class UserManagerCompatUtils {
    private static final Method METHOD_isUserUnlocked;

    static {
        // We do not try to search the method in Android M and prior.
        if (BuildCompatUtils.EFFECTIVE_SDK_INT <= Build.VERSION_CODES.M) {
            METHOD_isUserUnlocked = null;
        } else {
            METHOD_isUserUnlocked = CompatUtils.getMethod(UserManager.class, "isUserUnlocked");
        }
    }

    private UserManagerCompatUtils() {
        // This utility class is not publicly instantiable.
    }

    public static final int LOCK_STATE_UNKNOWN = 0;
    public static final int LOCK_STATE_UNLOCKED = 1;
    public static final int LOCK_STATE_LOCKED = 2;

    @Retention(SOURCE)
    @IntDef({LOCK_STATE_UNKNOWN, LOCK_STATE_UNLOCKED, LOCK_STATE_LOCKED})
    public @interface LockState {}

    /**
     * Check if the calling user is running in an "unlocked" state. A user is unlocked only after
     * they've entered their credentials (such as a lock pattern or PIN), and credential-encrypted
     * private app data storage is available.
     * @param context context from which {@link UserManager} should be obtained.
     * @return One of {@link LockState}.
     */
    @LockState
    public static int getUserLockState(final Context context) {
        if (METHOD_isUserUnlocked == null) {
            return LOCK_STATE_UNKNOWN;
        }
        final UserManager userManager = context.getSystemService(UserManager.class);
        if (userManager == null) {
            return LOCK_STATE_UNKNOWN;
        }
        final Boolean result =
                (Boolean) CompatUtils.invoke(userManager, null, METHOD_isUserUnlocked);
        if (result == null) {
            return LOCK_STATE_UNKNOWN;
        }
        return result ? LOCK_STATE_UNLOCKED : LOCK_STATE_LOCKED;
    }
}
