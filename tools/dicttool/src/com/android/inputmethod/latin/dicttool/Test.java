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

import com.android.inputmethod.latin.common.FileUtils;
import com.android.inputmethod.latin.makedict.BinaryDictDecoderEncoderTests;
import com.android.inputmethod.latin.makedict.BinaryDictEncoderFlattenTreeTests;
import com.android.inputmethod.latin.makedict.FusionDictionaryTest;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;

/**
 * Dicttool command implementing self-tests.
 */
public class Test extends Dicttool.Command {
    private static final String getTmpDir() {
        try {
            return Files.createTempDirectory("dicttool").toString();
        } catch (IOException e) {
            throw new RuntimeException("Can't get temporary directory", e);
        }
    }
    private static final String TEST_TMP_DIR_BASE = getTmpDir();
    public static final File TEST_TMP_DIR = new File(TEST_TMP_DIR_BASE);
    public static final String COMMAND = "test";
    private static final int DEFAULT_MAX_UNIGRAMS = 1500;
    private long mSeed = System.currentTimeMillis();
    private int mMaxUnigrams = DEFAULT_MAX_UNIGRAMS;

    private static final Class<?>[] sClassesToTest = {
        BinaryDictOffdeviceUtilsTests.class,
        FusionDictionaryTest.class,
        BinaryDictDecoderEncoderTests.class,
        BinaryDictEncoderFlattenTreeTests.class,
    };
    private ArrayList<Method> mAllTestMethods = new ArrayList<>();
    private ArrayList<String> mUsedTestMethods = new ArrayList<>();

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
        final StringBuilder s = new StringBuilder(
                "test [-s seed] [-m maxUnigrams] [-n] [testName...]\n"
                + "If seed is not specified, the current time is used.\n"
                + "If -n option is provided, do not delete temporary files in "
                + TEST_TMP_DIR_BASE + "/*.\n"
                + "Test list is:\n");
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
        boolean deleteTmpDir = true;
        while (i < mArgs.length) {
            final String arg = mArgs[i++];
            if ("-s".equals(arg)) {
                mSeed = Long.parseLong(mArgs[i++]);
            } else if ("-m".equals(arg)) {
                mMaxUnigrams = Integer.parseInt(mArgs[i++]);
            } else if ("-n".equals(arg)) {
                deleteTmpDir = false;
            } else {
                mUsedTestMethods.add(arg);
            }
        }
        try {
            runChosenTests();
        } finally {
            if (deleteTmpDir) {
                FileUtils.deleteRecursively(TEST_TMP_DIR);
            }
        }
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
