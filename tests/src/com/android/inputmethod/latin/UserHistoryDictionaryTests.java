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

package com.android.inputmethod.latin;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

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
            dict.forceAddWordForTest(prevWord, word, true);
            prevWord = word;
        }
    }

    public void testRandomWords() {
        File dictFile = null;
        try {
            Log.d(TAG, "This test can be used for profiling.");
            Log.d(TAG, "Usage: please set UserHisotoryDictionary.PROFILE_SAVE_RESTORE to true.");
            final int numberOfWords = 1000;
            final Random random = new Random(123456);
            List<String> words = generateWords(numberOfWords, random);

            final String locale = "testRandomWords";
            final String fileName = "UserHistoryDictionary." + locale + ".dict";
            dictFile = new File(getContext().getFilesDir(), fileName);
            final UserHistoryDictionary dict = UserHistoryDictionary.getInstance(getContext(),
                    locale, mPrefs);
            dict.isTest = true;

            addToDict(dict, words);

            try {
                Log.d(TAG, "waiting for adding the word ...");
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Log.d(TAG, "InterruptedException: " + e);
            }

            // write to file
            dict.close();

            try {
                Log.d(TAG, "waiting for writing ...");
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Log.d(TAG, "InterruptedException: " + e);
            }
        } finally {
            if (dictFile != null) {
                dictFile.delete();
            }
        }
    }
}
