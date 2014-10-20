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

import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class CompatUtils {
    private static final String TAG = CompatUtils.class.getSimpleName();

    private CompatUtils() {
        // This utility class is not publicly instantiable.
    }

    public static Class<?> getClass(final String className) {
        try {
            return Class.forName(className);
        } catch (final ClassNotFoundException e) {
            return null;
        }
    }

    public static Method getMethod(final Class<?> targetClass, final String name,
            final Class<?>... parameterTypes) {
        if (targetClass == null || TextUtils.isEmpty(name)) {
            return null;
        }
        try {
            return targetClass.getMethod(name, parameterTypes);
        } catch (final SecurityException | NoSuchMethodException e) {
            // ignore
        }
        return null;
    }

    public static Field getField(final Class<?> targetClass, final String name) {
        if (targetClass == null || TextUtils.isEmpty(name)) {
            return null;
        }
        try {
            return targetClass.getField(name);
        } catch (final SecurityException | NoSuchFieldException e) {
            // ignore
        }
        return null;
    }

    public static Constructor<?> getConstructor(final Class<?> targetClass,
            final Class<?> ... types) {
        if (targetClass == null || types == null) {
            return null;
        }
        try {
            return targetClass.getConstructor(types);
        } catch (final SecurityException | NoSuchMethodException e) {
            // ignore
        }
        return null;
    }

    public static Object newInstance(final Constructor<?> constructor, final Object ... args) {
        if (constructor == null) {
            return null;
        }
        try {
            return constructor.newInstance(args);
        } catch (final InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            Log.e(TAG, "Exception in newInstance", e);
        }
        return null;
    }

    public static Object invoke(final Object receiver, final Object defaultValue,
            final Method method, final Object... args) {
        if (method == null) {
            return defaultValue;
        }
        try {
            return method.invoke(receiver, args);
        } catch (final IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            Log.e(TAG, "Exception in invoke", e);
        }
        return defaultValue;
    }

    public static Object getFieldValue(final Object receiver, final Object defaultValue,
            final Field field) {
        if (field == null) {
            return defaultValue;
        }
        try {
            return field.get(receiver);
        } catch (final IllegalAccessException | IllegalArgumentException e) {
            Log.e(TAG, "Exception in getFieldValue", e);
        }
        return defaultValue;
    }

    public static void setFieldValue(final Object receiver, final Field field, final Object value) {
        if (field == null) {
            return;
        }
        try {
            field.set(receiver, value);
        } catch (final IllegalAccessException | IllegalArgumentException e) {
            Log.e(TAG, "Exception in setFieldValue", e);
        }
    }

    public static ClassWrapper getClassWrapper(final String className) {
        return new ClassWrapper(getClass(className));
    }

    public static final class ClassWrapper {
        private final Class<?> mClass;
        public ClassWrapper(final Class<?> targetClass) {
            mClass = targetClass;
        }

        public boolean exists() {
            return mClass != null;
        }

        public <T> ToObjectMethodWrapper<T> getMethod(final String name,
                final T defaultValue, final Class<?>... parameterTypes) {
            return new ToObjectMethodWrapper<>(CompatUtils.getMethod(mClass, name, parameterTypes),
                    defaultValue);
        }

        public ToIntMethodWrapper getPrimitiveMethod(final String name, final int defaultValue,
                final Class<?>... parameterTypes) {
            return new ToIntMethodWrapper(CompatUtils.getMethod(mClass, name, parameterTypes),
                    defaultValue);
        }

        public ToFloatMethodWrapper getPrimitiveMethod(final String name, final float defaultValue,
                final Class<?>... parameterTypes) {
            return new ToFloatMethodWrapper(CompatUtils.getMethod(mClass, name, parameterTypes),
                    defaultValue);
        }

        public ToBooleanMethodWrapper getPrimitiveMethod(final String name,
                final boolean defaultValue, final Class<?>... parameterTypes) {
            return new ToBooleanMethodWrapper(CompatUtils.getMethod(mClass, name, parameterTypes),
                    defaultValue);
        }
    }

    public static final class ToObjectMethodWrapper<T> {
        private final Method mMethod;
        private final T mDefaultValue;
        public ToObjectMethodWrapper(final Method method, final T defaultValue) {
            mMethod = method;
            mDefaultValue = defaultValue;
        }
        @SuppressWarnings("unchecked")
        public T invoke(final Object receiver, final Object... args) {
            return (T) CompatUtils.invoke(receiver, mDefaultValue, mMethod, args);
        }
    }

    public static final class ToIntMethodWrapper {
        private final Method mMethod;
        private final int mDefaultValue;
        public ToIntMethodWrapper(final Method method, final int defaultValue) {
            mMethod = method;
            mDefaultValue = defaultValue;
        }
        public int invoke(final Object receiver, final Object... args) {
            return (int) CompatUtils.invoke(receiver, mDefaultValue, mMethod, args);
        }
    }

    public static final class ToFloatMethodWrapper {
        private final Method mMethod;
        private final float mDefaultValue;
        public ToFloatMethodWrapper(final Method method, final float defaultValue) {
            mMethod = method;
            mDefaultValue = defaultValue;
        }
        public float invoke(final Object receiver, final Object... args) {
            return (float) CompatUtils.invoke(receiver, mDefaultValue, mMethod, args);
        }
    }

    public static final class ToBooleanMethodWrapper {
        private final Method mMethod;
        private final boolean mDefaultValue;
        public ToBooleanMethodWrapper(final Method method, final boolean defaultValue) {
            mMethod = method;
            mDefaultValue = defaultValue;
        }
        public boolean invoke(final Object receiver, final Object... args) {
            return (boolean) CompatUtils.invoke(receiver, mDefaultValue, mMethod, args);
        }
    }
}
