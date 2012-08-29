/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.latin;

import android.content.res.Resources;
import android.os.Build;

import java.util.HashMap;

public class ResourceUtils {
    private ResourceUtils() {
        // This utility class is not publicly instantiable.
    }

    private static final String HARDWARE_PREFIX = Build.HARDWARE + ",";
    private static final HashMap<String, String> sDeviceOverrideValueMap =
            CollectionUtils.newHashMap();

    public static String getDeviceOverrideValue(Resources res, int overrideResId, String defValue) {
        final int orientation = res.getConfiguration().orientation;
        final String key = overrideResId + "-" + orientation;
        if (!sDeviceOverrideValueMap.containsKey(key)) {
            String overrideValue = defValue;
            for (final String element : res.getStringArray(overrideResId)) {
                if (element.startsWith(HARDWARE_PREFIX)) {
                    overrideValue = element.substring(HARDWARE_PREFIX.length());
                    break;
                }
            }
            sDeviceOverrideValueMap.put(key, overrideValue);
        }
        return sDeviceOverrideValueMap.get(key);
    }
}
