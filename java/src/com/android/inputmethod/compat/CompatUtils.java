/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class CompatUtils {
    private static final String TAG = CompatUtils.class.getSimpleName();
    private static final String EXTRA_INPUT_METHOD_ID = "input_method_id";
    // TODO: Can these be constants instead of literal String constants?
    private static final String INPUT_METHOD_SUBTYPE_SETTINGS =
            "android.settings.INPUT_METHOD_SUBTYPE_SETTINGS";
    private static final String INPUT_LANGUAGE_SELECTION =
            "com.android.inputmethod.latin.INPUT_LANGUAGE_SELECTION";

    public static Intent getInputLanguageSelectionIntent(String inputMethodId,
            int flagsForSubtypeSettings) {
        final String action;
        Intent intent;
        if (android.os.Build.VERSION.SDK_INT
                >= /* android.os.Build.VERSION_CODES.HONEYCOMB */ 11) {
            // Refer to android.provider.Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS
            action = INPUT_METHOD_SUBTYPE_SETTINGS;
            intent = new Intent(action);
            if (!TextUtils.isEmpty(inputMethodId)) {
                intent.putExtra(EXTRA_INPUT_METHOD_ID, inputMethodId);
            }
            if (flagsForSubtypeSettings > 0) {
                intent.setFlags(flagsForSubtypeSettings);
            }
        } else {
            action = INPUT_LANGUAGE_SELECTION;
            intent = new Intent(action);
        }
        return intent;
    }

    public static Class<?> getClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static Method getMethod(Class<?> targetClass, String name,
            Class<?>... parameterTypes) {
        try {
            return targetClass.getMethod(name, parameterTypes);
        } catch (SecurityException e) {
            // ignore
            return null;
        } catch (NoSuchMethodException e) {
            // ignore
            return null;
        }
    }

    public static Object invoke(
            Object receiver, Object defaultValue, Method method, Object... args) {
        if (receiver == null || method == null) return defaultValue;
        try {
            return method.invoke(receiver, args);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Exception in invoke: IllegalArgmentException");
            return defaultValue;
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Exception in invoke: IllegalAccessException");
            return defaultValue;
        } catch (InvocationTargetException e) {
            Log.e(TAG, "Exception in invoke: IllegalTargetException");
            return defaultValue;
        }
    }

    public static List<InputMethodSubtypeCompatWrapper> copyInputMethodSubtypeListToWrappler(
            Object listObject) {
        if (!(listObject instanceof List<?>)) return null;
        final List<InputMethodSubtypeCompatWrapper> subtypes =
                new ArrayList<InputMethodSubtypeCompatWrapper>();
        for (Object o: (List<?>)listObject) {
            subtypes.add(new InputMethodSubtypeCompatWrapper(o));
        }
        return subtypes;
    }
}
