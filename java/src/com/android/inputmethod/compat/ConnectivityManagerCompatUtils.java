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

package com.android.inputmethod.compat;

import android.net.ConnectivityManager;

import java.lang.reflect.Method;

public final class ConnectivityManagerCompatUtils {
    // ConnectivityManager#isActiveNetworkMetered() has been introduced
    // in API level 16 (Build.VERSION_CODES.JELLY_BEAN).
    private static final Method METHOD_isActiveNetworkMetered = CompatUtils.getMethod(
            ConnectivityManager.class, "isActiveNetworkMetered");

    public static boolean isActiveNetworkMetered(final ConnectivityManager manager) {
        return (Boolean)CompatUtils.invoke(manager,
                // If the API telling whether the network is metered or not is not available,
                // then the closest thing is "if it's a mobile connection".
                manager.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_MOBILE,
                METHOD_isActiveNetworkMetered);
    }
}
