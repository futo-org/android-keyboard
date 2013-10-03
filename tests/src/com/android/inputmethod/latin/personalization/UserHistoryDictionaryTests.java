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

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;

import com.android.inputmethod.latin.ExpandableBinaryDictionary;
import com.android.inputmethod.latin.utils.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for UserHistoryDictionary
 */
@LargeTest
public class UserHistoryDictionaryTests extends AndroidTestCase {
    private static final String TAG = UserHistoryDictionaryTests.class.getSimpleName();
    private SharedPreferences mPrefs;

    private static final String[] CHARACTERS = {
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
        "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"
    };

    private static final int MIN_USER_HISTORY_DICTIONARY_FILE_SIZE = 1000;
    private static final int WAIT_TERMINATING_IN_MILLISECONDS = 100;

    @Override
    public void setUp() {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
    }

    /**
     * Generates a random word.
     */
    private String generateWord(final int value) {
        final int lengthOfChars = CHARACTERS.length;
        StringBuilder builder = new StringBuilder();
        long lvalue = Math.abs((long)value);
        while (lvalue > 0) {
            builder.append(CHARACTERS[(int)(lvalue % lengthOfChars)]);
            lvalue /= lengthOfChars;
        }
        return builder.toString();
    }

    private List<String> generateWords(final int number, final Random random) {
        final Set<String> wordSet = CollectionUtils.newHashSet();
        while (wordSet.size() < number) {
            wordSet.add(generateWord(random.nextInt()));
        }
        return new ArrayList<String>(wordSet);
    }

    private void addToDict(final UserHistoryDictionary dict, final List<String> words) {
        String prevWord = null;
        for (String word : words) {
            dict.addToDictionary(prevWord, word, true);
            prevWord = word;
        }
    }

    /**
     * @param checkContents if true, checks whether written words are actually in the dictionary
     * or not.
     */
    private void addAndWriteRandomWords(final String testFilenameSuffix, final int numberOfWords,
            final Random random, final boolean checkContents) {
        final List<String> words = generateWords(numberOfWords, random);
        final UserHistoryDictionary dict =
                PersonalizationHelper.getUserHistoryDictionary(getContext(),
                        testFilenameSuffix /* locale */, mPrefs);
        // Add random words to the user history dictionary.
        addToDict(dict, words);
        if (checkContents) {
            try {
                Thread.sleep(TimeUnit.MILLISECONDS.convert(5L, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
            }
            for (int i = 0; i < numberOfWords; ++i) {
                final String word = words.get(i);
                assertTrue(dict.isInDictionaryForTests(word));
            }
        }
        // write to file.
        dict.close();
    }

    /**
     * Clear all entries in the user history dictionary.
     * @param testFilenameSuffix file name suffix used for testing.
     */
    private void clearHistory(final String testFilenameSuffix) {
        final UserHistoryDictionary dict =
                PersonalizationHelper.getUserHistoryDictionary(getContext(),
                        testFilenameSuffix /* locale */, mPrefs);
        dict.clearAndFlushDictionary();
        dict.close();
    }

    /**
     * Shut down executer and wait until all operations of user history are done.
     * @param testFilenameSuffix file name suffix used for testing.
     */
    private void waitForWriting(final String testFilenameSuffix) {
        try {
            final UserHistoryDictionary dict =
                    PersonalizationHelper.getUserHistoryDictionary(getContext(),
                            testFilenameSuffix, mPrefs);
            dict.shutdownExecutorForTests();
            while (!dict.isTerminatedForTests()) {
                Thread.sleep(WAIT_TERMINATING_IN_MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Log.d(TAG, "InterruptedException: ", e);
        }
    }

    public void testRandomWords() {
        Log.d(TAG, "This test can be used for profiling.");
        Log.d(TAG, "Usage: please set UserHistoryDictionary.PROFILE_SAVE_RESTORE to true.");
        final String testFilenameSuffix = "testRandomWords" + System.currentTimeMillis();
        final String fileName = UserHistoryDictionary.NAME + "." + testFilenameSuffix
                + ExpandableBinaryDictionary.DICT_FILE_EXTENSION;

        final int numberOfWords = 1000;
        final Random random = new Random(123456);

        try {
            clearHistory(testFilenameSuffix);
            addAndWriteRandomWords(testFilenameSuffix, numberOfWords, random,
                    true /* checksContents */);
        } finally {
            Log.d(TAG, "waiting for writing ...");
            waitForWriting(testFilenameSuffix);
            final File dictFile = new File(getContext().getFilesDir(), fileName);
            if (dictFile != null) {
                assertTrue(dictFile.exists());
                assertTrue(dictFile.length() >= MIN_USER_HISTORY_DICTIONARY_FILE_SIZE);
                dictFile.delete();
            }
        }
    }

    public void testStressTestForSwitchingLanguagesAndAddingWords() {
        final int numberOfLanguages = 2;
        final int numberOfLanguageSwitching = 80;
        final int numberOfWordsInsertedForEachLanguageSwitch = 100;

        final File dictFiles[] = new File[numberOfLanguages];
        final String testFilenameSuffixes[] = new String[numberOfLanguages];
        try {
            final Random random = new Random(123456);

            // Create filename suffixes for this test.
            for (int i = 0; i < numberOfLanguages; i++) {
                testFilenameSuffixes[i] = "testSwitchingLanguages" + i;
                final String fileName = UserHistoryDictionary.NAME + "." +
                        testFilenameSuffixes[i] + ExpandableBinaryDictionary.DICT_FILE_EXTENSION;
                dictFiles[i] = new File(getContext().getFilesDir(), fileName);
                clearHistory(testFilenameSuffixes[i]);
            }

            final long start = System.currentTimeMillis();

            for (int i = 0; i < numberOfLanguageSwitching; i++) {
                final int index = i % numberOfLanguages;
                // Switch languages to testFilenameSuffixes[index].
                addAndWriteRandomWords(testFilenameSuffixes[index],
                        numberOfWordsInsertedForEachLanguageSwitch, random,
                        false /* checksContents */);
            }

            final long end = System.currentTimeMillis();
            Log.d(TAG, "testStressTestForSwitchingLanguageAndAddingWords took "
                    + (end - start) + " ms");
        } finally {
            Log.d(TAG, "waiting for writing ...");
            for (int i = 0; i < numberOfLanguages; i++) {
                waitForWriting(testFilenameSuffixes[i]);
            }
            for (final File file : dictFiles) {
                if (file != null) {
                    assertTrue(file.exists());
                    assertTrue(file.length() >= MIN_USER_HISTORY_DICTIONARY_FILE_SIZE);
                    file.delete();
                }
            }
        }
    }

    public void testAddManyWords() {
        final String testFilenameSuffix = "testRandomWords" + System.currentTimeMillis();
        final int numberOfWords =
                ExpandableBinaryDictionary.ENABLE_BINARY_DICTIONARY_DYNAMIC_UPDATE ?
                        10000 : 1000;
        final Random random = new Random(123456);
        clearHistory(testFilenameSuffix);
        try {
            addAndWriteRandomWords(testFilenameSuffix, numberOfWords, random,
                    true /* checksContents */);
        } finally {
            Log.d(TAG, "waiting for writing ...");
            waitForWriting(testFilenameSuffix);
            final String fileName = UserHistoryDictionary.NAME + "." + testFilenameSuffix
                    + ExpandableBinaryDictionary.DICT_FILE_EXTENSION;
            final File dictFile = new File(getContext().getFilesDir(), fileName);
            if (dictFile != null) {
                assertTrue(dictFile.exists());
                assertTrue(dictFile.length() >= MIN_USER_HISTORY_DICTIONARY_FILE_SIZE);
                dictFile.delete();
            }
        }
    }

}
