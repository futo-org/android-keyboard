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

package com.android.inputmethod.tools.edittextvariations;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class InstanceMethod {
    public final boolean defined;
    public final String name;
    public final String className;

    private final Class<?> clazz;
    private final Method method;

    private InstanceMethod(final Class<?> receiverClass, final Method instanceMethod,
            final String receiverName, final String methodName) {
        this.defined = instanceMethod != null;
        this.clazz = receiverClass;
        this.method = instanceMethod;
        this.name = methodName;
        this.className = receiverName;
    }

    public Object invoke(final Object receiverObject, final Object... args) {
        if (!defined)
            throw new RuntimeException("method " + name + " not defined");
        if (receiverObject == null)
            throw new NullPointerException("receiver object");
        if (clazz.isInstance(receiverObject)) {
            try {
                if (args.length == 0) {
                    return method.invoke(receiverObject);
                }
                return method.invoke(clazz, args);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("IllegalArgumentException");
            } catch (IllegalAccessException e) {
                throw new RuntimeException("IllegalAccessException");
            } catch (InvocationTargetException e) {
                throw new RuntimeException("InvocationTargetException");
            }
        }
        throw new RuntimeException("receiver type not matched: method=" + name
                + " actual receiver=" + receiverObject.getClass().getCanonicalName());
    }

    public static InstanceMethod newInstance(final Class<?> receiverClass, final String methodName,
            final Class<?>... parameterTypes) {
        if (receiverClass == null)
            throw new NullPointerException("receiver class");
        final String className = receiverClass.getCanonicalName();
        try {
            return new InstanceMethod(receiverClass,
                    receiverClass.getMethod(methodName, parameterTypes), className, methodName);
        } catch (Exception e) {
            return new InstanceMethod(receiverClass, null, className, methodName);
        }
    }

    public static InstanceMethod newInstance(final String className, final String methodName,
            final Class<?>... parameterTypes) {
        try {
            return newInstance(Class.forName(className), methodName, parameterTypes);
        } catch (ClassNotFoundException e) {
            return new InstanceMethod(null, null, className, methodName);
        }
    }
}
