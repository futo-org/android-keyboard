/**
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.latin.dicttool;

import com.android.inputmethod.latin.makedict.BinaryDictDecoderEncoderTests;
import com.android.inputmethod.latin.makedict.BinaryDictEncoderFlattenTreeTests;
import com.android.inputmethod.latin.makedict.BinaryDictIOUtilsTests;
import com.android.inputmethod.latin.makedict.FusionDictionaryTest;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Dicttool command implementing self-tests.
 */
public class Test extends Dicttool.Command {
    public static final String COMMAND = "test";
    private long mSeed = System.currentTimeMillis();
    private int mMaxUnigrams = BinaryDictIOUtilsTests.DEFAULT_MAX_UNIGRAMS;

    private static final Class<?>[] sClassesToTest = {
        BinaryDictOffdeviceUtilsTests.class,
        FusionDictionaryTest.class,
        BinaryDictDecoderEncoderTests.class,
        BinaryDictEncoderFlattenTreeTests.class,
        BinaryDictIOUtilsTests.class
    };
    private ArrayList<Method> mAllTestMethods = new ArrayList<Method>();
    private ArrayList<String> mUsedTestMethods = new ArrayList<String>();

    public Test() {
        for (final Class<?> c : sClassesToTest) {
            for (final Method m : c.getDeclaredMethods()) {
                if (m.getName().startsWith("test") && Void.TYPE == m.getReturnType()
                        && 0 == m.getParameterTypes().length) {
                    mAllTestMethods.add(m);
                }
            }
        }
    }

    @Override
    public String getHelp() {
        final StringBuilder s = new StringBuilder("test [-s seed] [-m maxUnigrams] [testName...]\n"
                + "If seed is not specified, the current time is used.\nTest list is:\n");
        for (final Method m : mAllTestMethods) {
            s.append("  ");
            s.append(m.getName());
            s.append("\n");
        }
        return s.toString();
    }

    @Override
    public void run() throws IllegalAccessException, InstantiationException,
            InvocationTargetException {
        int i = 0;
        while (i < mArgs.length) {
            final String arg = mArgs[i++];
            if ("-s".equals(arg)) {
                mSeed = Long.parseLong(mArgs[i++]);
            } else if ("-m".equals(arg)) {
                mMaxUnigrams = Integer.parseInt(mArgs[i++]);
            } else {
                mUsedTestMethods.add(arg);
            }
        }
        runChosenTests();
    }

    private void runChosenTests() throws IllegalAccessException, InstantiationException,
            InvocationTargetException {
        for (final Method m : mAllTestMethods) {
            final Class<?> declaringClass = m.getDeclaringClass();
            if (!mUsedTestMethods.isEmpty() && !mUsedTestMethods.contains(m.getName())) continue;
            // Some of the test classes expose a two-argument constructor, taking a long as a
            // seed for Random, and an int for a vocabulary size to test the dictionary with. They
            // correspond respectively to the -s and -m numerical arguments to this command, which
            // are stored in mSeed and mMaxUnigrams. If the two-arguments constructor is present,
            // then invoke it; otherwise, invoke the default constructor.
            Constructor<?> twoArgsConstructor = null;
            try {
                twoArgsConstructor = declaringClass.getDeclaredConstructor(Long.TYPE, Integer.TYPE);
            } catch (NoSuchMethodException e) {
                // No constructor with two args
            }
            final Object instance = null == twoArgsConstructor ? declaringClass.newInstance()
                    : twoArgsConstructor.newInstance(mSeed, mMaxUnigrams);
            m.invoke(instance);
        }
    }
}
