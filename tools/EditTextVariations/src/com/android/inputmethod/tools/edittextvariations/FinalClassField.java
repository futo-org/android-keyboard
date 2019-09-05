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

package com.android.inputmethod.tools.edittextvariations;

import java.lang.reflect.Field;

public final class FinalClassField<T> {
    public final boolean defined;
    public final String name;
    public final String className;
    public final T value;

    @SuppressWarnings("unchecked")
    private FinalClassField(final Field field, final String className, final String fieldName,
            final T compatValue) {
        this.defined = field != null;
        this.name = fieldName;
        this.className = className;
        T v = null;
        try {
            final Object obj = field.get(null);
            v = (T) obj;
        } catch (final Exception e) {
            v = compatValue;
        }
        this.value = v;
    }

    public static <T> FinalClassField<T> newInstance(final Class<?> definedClass, final String name,
            final T compatValue) {
        if (definedClass == null)
            throw new NullPointerException("defined class");
        String className = definedClass.getCanonicalName();
        try {
            return new FinalClassField<>(
                    definedClass.getField(name), className, name, compatValue);
        } catch (Exception e) {
            return new FinalClassField<>(null, className, name, compatValue);
        }
    }

    public static <T> FinalClassField<T> newInstance(final String className, final String fieldName,
            final T compatValue) {
        try {
            return newInstance(Class.forName(className), fieldName, compatValue);
        } catch (ClassNotFoundException e) {
            return new FinalClassField<>(null, className, fieldName, compatValue);
        }
    }
}
