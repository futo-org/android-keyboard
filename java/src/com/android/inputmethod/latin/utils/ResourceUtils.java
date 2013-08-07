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

package com.android.inputmethod.latin.utils;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.PatternSyntaxException;

public final class ResourceUtils {
    private static final String TAG = ResourceUtils.class.getSimpleName();

    public static final float UNDEFINED_RATIO = -1.0f;
    public static final int UNDEFINED_DIMENSION = -1;

    private ResourceUtils() {
        // This utility class is not publicly instantiable.
    }

    private static final HashMap<String, String> sDeviceOverrideValueMap =
            CollectionUtils.newHashMap();

    private static final String[] BUILD_KEYS_AND_VALUES = {
        "HARDWARE", Build.HARDWARE,
        "MODEL", Build.MODEL,
        "BRAND", Build.BRAND,
        "MANUFACTURER", Build.MANUFACTURER
    };
    private static final HashMap<String, String> sBuildKeyValues;
    private static final String sBuildKeyValuesDebugString;

    static {
        sBuildKeyValues = CollectionUtils.newHashMap();
        final ArrayList<String> keyValuePairs = CollectionUtils.newArrayList();
        final int keyCount = BUILD_KEYS_AND_VALUES.length / 2;
        for (int i = 0; i < keyCount; i++) {
            final int index = i * 2;
            final String key = BUILD_KEYS_AND_VALUES[index];
            final String value = BUILD_KEYS_AND_VALUES[index + 1];
            sBuildKeyValues.put(key, value);
            keyValuePairs.add(key + '=' + value);
        }
        sBuildKeyValuesDebugString = "[" + TextUtils.join(" ", keyValuePairs) + "]";
    }

    public static String getDeviceOverrideValue(final Resources res, final int overrideResId) {
        final int orientation = res.getConfiguration().orientation;
        final String key = overrideResId + "-" + orientation;
        if (sDeviceOverrideValueMap.containsKey(key)) {
            return sDeviceOverrideValueMap.get(key);
        }

        final String[] overrideArray = res.getStringArray(overrideResId);
        final String overrideValue = findConstantForKeyValuePairs(sBuildKeyValues, overrideArray);
        // The overrideValue might be an empty string.
        if (overrideValue != null) {
            Log.i(TAG, "Find override value:"
                    + " resource="+ res.getResourceEntryName(overrideResId)
                    + " build=" + sBuildKeyValuesDebugString
                    + " override=" + overrideValue);
            sDeviceOverrideValueMap.put(key, overrideValue);
            return overrideValue;
        }

        String defaultValue = null;
        try {
            defaultValue = findDefaultConstant(overrideArray);
            // The defaultValue might be an empty string.
            if (defaultValue == null) {
                Log.w(TAG, "Couldn't find override value nor default value:"
                        + " resource="+ res.getResourceEntryName(overrideResId)
                        + " build=" + sBuildKeyValuesDebugString);
            } else {
                Log.i(TAG, "Found default value:"
                        + " resource="+ res.getResourceEntryName(overrideResId)
                        + " build=" + sBuildKeyValuesDebugString
                        + " default=" + defaultValue);
            }
        } catch (final DeviceOverridePatternSyntaxError e) {
            Log.w(TAG, "Syntax error, ignored", e);
        }
        sDeviceOverrideValueMap.put(key, defaultValue);
        return defaultValue;
    }

    @SuppressWarnings("serial")
    static class DeviceOverridePatternSyntaxError extends Exception {
        public DeviceOverridePatternSyntaxError(final String message, final String expression) {
            this(message, expression, null);
        }

        public DeviceOverridePatternSyntaxError(final String message, final String expression,
                final Throwable throwable) {
            super(message + ": " + expression, throwable);
        }
    }

    /**
     * Find the condition that fulfills specified key value pairs from an array of
     * "condition,constant", and return the corresponding string constant. A condition is
     * "pattern1[:pattern2...] (or an empty string for the default). A pattern is
     * "key=regexp_value" string. The condition matches only if all patterns of the condition
     * are true for the specified key value pairs.
     *
     * For example, "condition,constant" has the following format.
     * (See {@link ResourceUtilsTests#testFindConstantForKeyValuePairsRegexp()})
     *  - HARDWARE=mako,constantForNexus4
     *  - MODEL=Nexus 4:MANUFACTURER=LGE,constantForNexus4
     *  - ,defaultConstant
     *
     * @param keyValuePairs attributes to be used to look for a matched condition.
     * @param conditionConstantArray an array of "condition,constant" elements to be searched.
     * @return the constant part of the matched "condition,constant" element. Returns null if no
     * condition matches.
     */
    @UsedForTesting
    static String findConstantForKeyValuePairs(final HashMap<String, String> keyValuePairs,
            final String[] conditionConstantArray) {
        if (conditionConstantArray == null || keyValuePairs == null) {
            return null;
        }
        String foundValue = null;
        for (final String conditionConstant : conditionConstantArray) {
            final int posComma = conditionConstant.indexOf(',');
            if (posComma < 0) {
                Log.w(TAG, "Array element has no comma: " + conditionConstant);
                continue;
            }
            final String condition = conditionConstant.substring(0, posComma);
            if (condition.isEmpty()) {
                // Default condition. The default condition should be searched by
                // {@link #findConstantForDefault(String[])}.
                continue;
            }
            try {
                if (fulfillsCondition(keyValuePairs, condition)) {
                    // Take first match
                    if (foundValue == null) {
                        foundValue = conditionConstant.substring(posComma + 1);
                    }
                    // And continue walking through all conditions.
                }
            } catch (final DeviceOverridePatternSyntaxError e) {
                Log.w(TAG, "Syntax error, ignored", e);
            }
        }
        return foundValue;
    }

    private static boolean fulfillsCondition(final HashMap<String,String> keyValuePairs,
            final String condition) throws DeviceOverridePatternSyntaxError {
        final String[] patterns = condition.split(":");
        // Check all patterns in a condition are true
        boolean matchedAll = true;
        for (final String pattern : patterns) {
            final int posEqual = pattern.indexOf('=');
            if (posEqual < 0) {
                throw new DeviceOverridePatternSyntaxError("Pattern has no '='", condition);
            }
            final String key = pattern.substring(0, posEqual);
            final String value = keyValuePairs.get(key);
            if (value == null) {
                throw new DeviceOverridePatternSyntaxError("Unknown key", condition);
            }
            final String patternRegexpValue = pattern.substring(posEqual + 1);
            try {
                if (!value.matches(patternRegexpValue)) {
                    matchedAll = false;
                    // And continue walking through all patterns.
                }
            } catch (final PatternSyntaxException e) {
                throw new DeviceOverridePatternSyntaxError("Syntax error", condition, e);
            }
        }
        return matchedAll;
    }

    @UsedForTesting
    static String findDefaultConstant(final String[] conditionConstantArray)
            throws DeviceOverridePatternSyntaxError {
        if (conditionConstantArray == null) {
            return null;
        }
        for (final String condition : conditionConstantArray) {
            final int posComma = condition.indexOf(',');
            if (posComma < 0) {
                throw new DeviceOverridePatternSyntaxError("Array element has no comma", condition);
            }
            if (posComma == 0) { // condition is empty.
                return condition.substring(posComma + 1);
            }
        }
        return null;
    }

    public static int getDefaultKeyboardWidth(final Resources res) {
        final DisplayMetrics dm = res.getDisplayMetrics();
        return dm.widthPixels;
    }

    public static int getDefaultKeyboardHeight(final Resources res) {
        final DisplayMetrics dm = res.getDisplayMetrics();
        final String keyboardHeightString = getDeviceOverrideValue(res, R.array.keyboard_heights);
        final float keyboardHeight;
        if (TextUtils.isEmpty(keyboardHeightString)) {
            keyboardHeight = res.getDimension(R.dimen.keyboardHeight);
        } else {
            keyboardHeight = Float.parseFloat(keyboardHeightString) * dm.density;
        }
        final float maxKeyboardHeight = res.getFraction(
                R.fraction.maxKeyboardHeight, dm.heightPixels, dm.heightPixels);
        float minKeyboardHeight = res.getFraction(
                R.fraction.minKeyboardHeight, dm.heightPixels, dm.heightPixels);
        if (minKeyboardHeight < 0.0f) {
            // Specified fraction was negative, so it should be calculated against display
            // width.
            minKeyboardHeight = -res.getFraction(
                    R.fraction.minKeyboardHeight, dm.widthPixels, dm.widthPixels);
        }
        // Keyboard height will not exceed maxKeyboardHeight and will not be less than
        // minKeyboardHeight.
        return (int)Math.max(Math.min(keyboardHeight, maxKeyboardHeight), minKeyboardHeight);
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
