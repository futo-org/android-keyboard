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
import android.content.res.TypedArray;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;

import java.util.HashMap;

public final class ResourceUtils {
    private static final String TAG = ResourceUtils.class.getSimpleName();
    private static final boolean DEBUG = false;

    public static final float UNDEFINED_RATIO = -1.0f;
    public static final int UNDEFINED_DIMENSION = -1;

    private ResourceUtils() {
        // This utility class is not publicly instantiable.
    }

    private static final String DEFAULT_KEY = "DEFAULT";
    private static final HashMap<String, String> sDeviceOverrideValueMap =
            CollectionUtils.newHashMap();

    public static String getDeviceOverrideValue(final Resources res, final int overrideResId) {
        final int orientation = res.getConfiguration().orientation;
        final String key = overrideResId + "-" + orientation;
        if (sDeviceOverrideValueMap.containsKey(key)) {
            return sDeviceOverrideValueMap.get(key);
        }

        final String[] overrideArray = res.getStringArray(overrideResId);
        final String hardwareKey = "HARDWARE=" + Build.HARDWARE;
        final String overrideValue = StringUtils.findValueOfKey(hardwareKey, overrideArray);
        // The overrideValue might be an empty string.
        if (overrideValue != null) {
            if (DEBUG) {
                Log.d(TAG, "Find override value:"
                        + " resource="+ res.getResourceEntryName(overrideResId)
                        + " " + hardwareKey + " override=" + overrideValue);
            }
            sDeviceOverrideValueMap.put(key, overrideValue);
            return overrideValue;
        }

        final String defaultValue = StringUtils.findValueOfKey(DEFAULT_KEY, overrideArray);
        // The defaultValue might be an empty string.
        if (defaultValue == null) {
            Log.w(TAG, "Couldn't find override value nor default value:"
                    + " resource="+ res.getResourceEntryName(overrideResId)
                    + " " + hardwareKey);
        } else if (DEBUG) {
            Log.d(TAG, "Found default value:"
                + " resource="+ res.getResourceEntryName(overrideResId)
                + " " + hardwareKey + " " + DEFAULT_KEY + "=" + defaultValue);
        }
        sDeviceOverrideValueMap.put(key, defaultValue);
        return defaultValue;
    }

    public static boolean isValidFraction(final float fraction) {
        return fraction >= 0.0f;
    }

    // {@link Resources#getDimensionPixelSize(int)} returns at least one pixel size.
    public static boolean isValidDimensionPixelSize(final int dimension) {
        return dimension > 0;
    }

    // {@link Resources#getDimensionPixelOffset(int)} may return zero pixel offset.
    public static boolean isValidDimensionPixelOffset(final int dimension) {
        return dimension >= 0;
    }

    public static float getFraction(final TypedArray a, final int index, final float defValue) {
        final TypedValue value = a.peekValue(index);
        if (value == null || !isFractionValue(value)) {
            return defValue;
        }
        return a.getFraction(index, 1, 1, defValue);
    }

    public static float getFraction(final TypedArray a, final int index) {
        return getFraction(a, index, UNDEFINED_RATIO);
    }

    public static int getDimensionPixelSize(final TypedArray a, final int index) {
        final TypedValue value = a.peekValue(index);
        if (value == null || !isDimensionValue(value)) {
            return ResourceUtils.UNDEFINED_DIMENSION;
        }
        return a.getDimensionPixelSize(index, ResourceUtils.UNDEFINED_DIMENSION);
    }

    public static float getDimensionOrFraction(final TypedArray a, final int index, final int base,
            final float defValue) {
        final TypedValue value = a.peekValue(index);
        if (value == null) {
            return defValue;
        }
        if (isFractionValue(value)) {
            return a.getFraction(index, base, base, defValue);
        } else if (isDimensionValue(value)) {
            return a.getDimension(index, defValue);
        }
        return defValue;
    }

    public static int getEnumValue(final TypedArray a, final int index, final int defValue) {
        final TypedValue value = a.peekValue(index);
        if (value == null) {
            return defValue;
        }
        if (isIntegerValue(value)) {
            return a.getInt(index, defValue);
        }
        return defValue;
    }

    public static boolean isFractionValue(final TypedValue v) {
        return v.type == TypedValue.TYPE_FRACTION;
    }

    public static boolean isDimensionValue(final TypedValue v) {
        return v.type == TypedValue.TYPE_DIMENSION;
    }

    public static boolean isIntegerValue(final TypedValue v) {
        return v.type >= TypedValue.TYPE_FIRST_INT && v.type <= TypedValue.TYPE_LAST_INT;
    }

    public static boolean isStringValue(final TypedValue v) {
        return v.type == TypedValue.TYPE_STRING;
    }
}
