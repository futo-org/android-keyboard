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

package com.android.inputmethod.latin.sync;

import android.content.Context;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;

public class BeanstalkManager {
    private static final Object sLock = new Object();

    @GuardedBy("sLock")
    private static BeanstalkManager sInstance;

    /**
     * @return the singleton instance of {@link BeanstalkManager}.
     */
    @Nonnull
    public static BeanstalkManager getInstance(Context context) {
        synchronized(sLock) {
            if (sInstance == null) {
                sInstance = new BeanstalkManager(context.getApplicationContext());
            }
        }
        return sInstance;
    }

    private BeanstalkManager(final Context context) {
        // Intentional private constructor for singleton.
    }

    public void onCreate() {
    }

    public void requestSync() {
    }

    public void onDestroy() {
    }
}