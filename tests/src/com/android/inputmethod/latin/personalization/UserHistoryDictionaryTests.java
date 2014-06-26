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

package com.android.inputmethod.latin.personalization;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;

import com.android.inputmethod.latin.ExpandableBinaryDictionary;
import com.android.inputmethod.latin.PrevWordsInfo;
import com.android.inputmethod.latin.PrevWordsInfo.WordInfo;
import com.android.inputmethod.latin.utils.BinaryDictionaryUtils;
import com.android.inputmethod.latin.utils.DistracterFilter;
import com.android.inputmethod.latin.utils.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for UserHistoryDictionary
 */
@LargeTest
public class UserHistoryDictionaryTests extends AndroidTestCase {
    private static final String TAG = UserHistoryDictionaryTests.class.getSimpleName();

    private static final String[] CHARACTERS = {
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
        "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"
    };

    private int mCurrentTime = 0;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        resetCurrentTimeForTestMode();
    }

    @Override
    protected void tearDown() throws Exception {
        stopTestModeInNativeCode();
        super.tearDown();
    }

    private void resetCurrentTimeForTestMode() {
        mCurrentTime = 0;
        setCurrentTimeForTestMode(mCurrentTime);
    }

    private void forcePassingShortTime() {
        // 3 days.
        final int timeToElapse = (int)TimeUnit.DAYS.toSeconds(3);
        mCurrentTime += timeToElapse;
        setCurrentTimeForTestMode(mCurrentTime);
    }

    private void forcePassingLongTime() {
        // 365 days.
        final int timeToElapse = (int)TimeUnit.DAYS.toSeconds(365);
        mCurrentTime += timeToElapse;
        setCurrentTimeForTestMode(mCurrentTime);
    }

    private static int setCurrentTimeForTestMode(final int currentTime) {
        return BinaryDictionaryUtils.setCurrentTimeForTest(currentTime);
    }

    private static int stopTestModeInNativeCode() {
        return BinaryDictionaryUtils.setCurrentTimeForTest(-1);
    }

    /**
     * Generates a random word.
     */
    private static String generateWord(final int value) {
        final int lengthOfChars = CHARACTERS.length;
        StringBuilder builder = new StringBuilder();
        long lvalue = Math.abs((long)value);
        while (lvalue > 0) {
            builder.append(CHARACTERS[(int)(lvalue % lengthOfChars)]);
            lvalue /= lengthOfChars;
        }
        return builder.toString();
    }

    private static List<String> generateWords(final int number, final Random random) {
        final HashSet<String> wordSet = new HashSet<>();
        while (wordSet.size() < number) {
            wordSet.add(generateWord(random.nextInt()));
        }
        return new ArrayList<>(wordSet);
    }

    private static void addToDict(final UserHistoryDictionary dict, final List<String> words) {
        PrevWordsInfo prevWordsInfo = PrevWordsInfo.EMPTY_PREV_WORDS_INFO;
        for (String word : words) {
            UserHistoryDictionary.addToDictionary(dict, prevWordsInfo, word, true,
                    (int)TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()),
                    DistracterFilter.EMPTY_DISTRACTER_FILTER);
            prevWordsInfo = prevWordsInfo.getNextPrevWordsInfo(new WordInfo(word));
        }
    }

    /**
     * @param checkContents if true, checks whether written words are actually in the dictionary
     * or not.
     */
    private void addAndWriteRandomWords(final Locale locale, final int numberOfWords,
            final Random random, final boolean checkContents) {
        final List<String> words = generateWords(numberOfWords, random);
        final UserHistoryDictionary dict = PersonalizationHelper.getUserHistoryDictionary(
                mContext, locale);
        // Add random words to the user history dictionary.
        addToDict(dict, words);
        if (checkContents) {
            dict.waitAllTasksForTests();
            for (int i = 0; i < numberOfWords; ++i) {
                final String word = words.get(i);
                assertTrue(dict.isInDictionary(word));
            }
        }
        // write to file.
        dict.close();
    }

    /**
     * Clear all entries in the user history dictionary.
     * @param locale dummy locale for testing.
     */
    private void clearHistory(final Locale locale) {
        final UserHistoryDictionary dict = PersonalizationHelper.getUserHistoryDictionary(
                mContext, locale);
        dict.waitAllTasksForTests();
        dict.clear();
        dict.close();
        dict.waitAllTasksForTests();
    }

    /**
     * Shut down executer and wait until all operations of user history are done.
     * @param locale dummy locale for testing.
     */
    private void waitForWriting(final Locale locale) {
        final UserHistoryDictionary dict = PersonalizationHelper.getUserHistoryDictionary(
                mContext, locale);
        dict.waitAllTasksForTests();
    }

    public void testRandomWords() {
        Log.d(TAG, "This test can be used for profiling.");
        Log.d(TAG, "Usage: please set UserHistoryDictionary.PROFILE_SAVE_RESTORE to true.");
        final Locale dummyLocale = new Locale("test_random_words" + System.currentTimeMillis());
        final String dictName = ExpandableBinaryDictionary.getDictName(
                UserHistoryDictionary.NAME, dummyLocale, null /* dictFile */);
        final File dictFile = ExpandableBinaryDictionary.getDictFile(
                mContext, dictName, null /* dictFile */);

        final int numberOfWords = 1000;
        final Random random = new Random(123456);

        try {
            clearHistory(dummyLocale);
            addAndWriteRandomWords(dummyLocale, numberOfWords, random,
                    true /* checksContents */);
        } finally {
            Log.d(TAG, "waiting for writing ...");
            waitForWriting(dummyLocale);
            assertTrue("check exisiting of " + dictFile, dictFile.exists());
            FileUtils.deleteRecursively(dictFile);
        }
    }

    public void testStressTestForSwitchingLanguagesAndAddingWords() {
        final int numberOfLanguages = 2;
        final int numberOfLanguageSwitching = 80;
        final int numberOfWordsInsertedForEachLanguageSwitch = 100;

        final File dictFiles[] = new File[numberOfLanguages];
        final Locale dummyLocales[] = new Locale[numberOfLanguages];
        try {
            final Random random = new Random(123456);

            // Create filename suffixes for this test.
            for (int i = 0; i < numberOfLanguages; i++) {
                dummyLocales[i] = new Locale("test_switching_languages" + i);
                final String dictName = ExpandableBinaryDictionary.getDictName(
                        UserHistoryDictionary.NAME, dummyLocales[i], null /* dictFile */);
                dictFiles[i] = ExpandableBinaryDictionary.getDictFile(
                        mContext, dictName, null /* dictFile */);
                clearHistory(dummyLocales[i]);
            }

            final long start = System.currentTimeMillis();

            for (int i = 0; i < numberOfLanguageSwitching; i++) {
                final int index = i % numberOfLanguages;
                // Switch languages to testFilenameSuffixes[index].
                addAndWriteRandomWords(dummyLocales[index],
                        numberOfWordsInsertedForEachLanguageSwitch, random,
                        false /* checksContents */);
            }

            final long end = System.currentTimeMillis();
            Log.d(TAG, "testStressTestForSwitchingLanguageAndAddingWords took "
                    + (end - start) + " ms");
        } finally {
            Log.d(TAG, "waiting for writing ...");
            for (int i = 0; i < numberOfLanguages; i++) {
                waitForWriting(dummyLocales[i]);
            }
            for (final File dictFile : dictFiles) {
                assertTrue("check exisiting of " + dictFile, dictFile.exists());
                FileUtils.deleteRecursively(dictFile);
            }
        }
    }

    public void testAddManyWords() {
        final Locale dummyLocale = new Locale("test_random_words" + System.currentTimeMillis());
        final String dictName = ExpandableBinaryDictionary.getDictName(
                UserHistoryDictionary.NAME, dummyLocale, null /* dictFile */);
        final File dictFile = ExpandableBinaryDictionary.getDictFile(
                mContext, dictName, null /* dictFile */);
        final int numberOfWords = 10000;
        final Random random = new Random(123456);
        clearHistory(dummyLocale);
        try {
            addAndWriteRandomWords(dummyLocale, numberOfWords, random, true /* checksContents */);
        } finally {
            Log.d(TAG, "waiting for writing ...");
            waitForWriting(dummyLocale);
            assertTrue("check exisiting of " + dictFile, dictFile.exists());
            FileUtils.deleteRecursively(dictFile);
        }
    }

    public void testDecaying() {
        final Locale dummyLocale = new Locale("test_decaying" + System.currentTimeMillis());
        final int numberOfWords = 5000;
        final Random random = new Random(123456);
        resetCurrentTimeForTestMode();
        clearHistory(dummyLocale);
        final List<String> words = generateWords(numberOfWords, random);
        final UserHistoryDictionary dict =
                PersonalizationHelper.getUserHistoryDictionary(getContext(), dummyLocale);
        dict.waitAllTasksForTests();
        PrevWordsInfo prevWordsInfo = PrevWordsInfo.EMPTY_PREV_WORDS_INFO;
        for (final String word : words) {
            UserHistoryDictionary.addToDictionary(dict, prevWordsInfo, word, true, mCurrentTime,
                    DistracterFilter.EMPTY_DISTRACTER_FILTER);
            prevWordsInfo = prevWordsInfo.getNextPrevWordsInfo(new WordInfo(word));
            dict.waitAllTasksForTests();
            assertTrue(dict.isInDictionary(word));
        }
        forcePassingShortTime();
        dict.runGCIfRequired();
        dict.waitAllTasksForTests();
        for (final String word : words) {
            assertTrue(dict.isInDictionary(word));
        }
        forcePassingLongTime();
        dict.runGCIfRequired();
        dict.waitAllTasksForTests();
        for (final String word : words) {
            assertFalse(dict.isInDictionary(word));
        }
    }
}
