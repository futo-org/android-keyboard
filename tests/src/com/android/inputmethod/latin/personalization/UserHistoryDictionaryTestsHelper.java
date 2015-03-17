/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.content.Context;

import com.android.inputmethod.latin.BinaryDictionary;
import com.android.inputmethod.latin.NgramContext;
import com.android.inputmethod.latin.NgramContext.WordInfo;
import com.android.inputmethod.latin.common.FileUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Utility class for helping while running tests involving {@link UserHistoryDictionary}.
 */
public class UserHistoryDictionaryTestsHelper {

    /**
     * Locale prefix for generating dummy locales for tests.
     */
    public static final String TEST_LOCALE_PREFIX = "test-";

    /**
     * Characters for generating random words.
     */
    private static final String[] CHARACTERS = {
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
        "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"
    };

    /**
     * Remove all the test dictionary files created for the given locale.
     */
    public static void removeAllTestDictFiles(final String filter, final Context context) {
        final FilenameFilter filenameFilter = new FilenameFilter() {
            @Override
            public boolean accept(final File dir, final String filename) {
                return filename.startsWith(UserHistoryDictionary.NAME + "." + filter);
            }
        };
        FileUtils.deleteFilteredFiles(context.getFilesDir(), filenameFilter);
    }

    /**
     * Generates and writes random words to dictionary. Caller can be assured
     * that the write tasks would be finished; and its success would be reflected
     * in the returned boolean.
     *
     * @param dict {@link UserHistoryDictionary} to which words should be added.
     * @param numberOfWords number of words to be added.
     * @param random helps generate random words.
     * @param checkContents if true, checks whether written words are actually in the dictionary.
     * @param currentTime timestamp that would be used for adding the words.
     * @returns true if all words have been written to dictionary successfully.
     */
    public static boolean addAndWriteRandomWords(final UserHistoryDictionary dict,
            final int numberOfWords, final Random random, final boolean checkContents,
            final int currentTime) {
        final List<String> words = generateWords(numberOfWords, random);
        // Add random words to the user history dictionary.
        addWordsToDictionary(dict, words, currentTime);
        boolean success = true;
        if (checkContents) {
            dict.waitAllTasksForTests();
            for (int i = 0; i < numberOfWords; ++i) {
                final String word = words.get(i);
                if (!dict.isInDictionary(word)) {
                    success = false;
                    break;
                }
            }
        }
        // write to file.
        dict.close();
        dict.waitAllTasksForTests();
        return success;
    }

    private static void addWordsToDictionary(final UserHistoryDictionary dict,
            final List<String> words, final int timestamp) {
        NgramContext ngramContext = NgramContext.getEmptyPrevWordsContext(
                BinaryDictionary.MAX_PREV_WORD_COUNT_FOR_N_GRAM);
        for (final String word : words) {
            UserHistoryDictionary.addToDictionary(dict, ngramContext, word, true, timestamp);
            ngramContext = ngramContext.getNextNgramContext(new WordInfo(word));
        }
    }

    /**
     * Creates unique test locale for using within tests.
     */
    public static Locale getDummyLocale(final String name) {
        return new Locale(TEST_LOCALE_PREFIX + name + System.currentTimeMillis());
    }

    /**
     * Generates random words.
     *
     * @param numberOfWords number of words to generate.
     * @param random salt used for generating random words.
     */
    public static List<String> generateWords(final int numberOfWords, final Random random) {
        final HashSet<String> wordSet = new HashSet<>();
        while (wordSet.size() < numberOfWords) {
            wordSet.add(generateWord(random.nextInt()));
        }
        return new ArrayList<>(wordSet);
    }

    /**
     * Generates a random word.
     */
    private static String generateWord(final int value) {
        final int lengthOfChars = CHARACTERS.length;
        final StringBuilder builder = new StringBuilder();
        long lvalue = Math.abs((long)value);
        while (lvalue > 0) {
            builder.append(CHARACTERS[(int)(lvalue % lengthOfChars)]);
            lvalue /= lengthOfChars;
        }
        return builder.toString();
    }
}
