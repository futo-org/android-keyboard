/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.inputmethod.latin;

import android.test.AndroidTestCase;
import android.util.Log;
import com.android.inputmethod.latin.tests.R;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.BufferedReader;
import java.util.StringTokenizer;

public class SuggestPerformanceTests extends AndroidTestCase {
    private static final String TAG = "SuggestPerformanceTests";

    private String mTestText;
    private SuggestHelper sh;

    @Override
    protected void setUp() {
        // TODO Figure out a way to directly using the dictionary rather than copying it over

        // For testing with real dictionary, TEMPORARILY COPY main dictionary into test directory.
        // DO NOT SUBMIT real dictionary under test directory.
        //int[] resId = new int[] { R.raw.main0, R.raw.main1, R.raw.main2 };

        int[] resId = new int[] { R.raw.test };

        sh = new SuggestHelper(TAG, getTestContext(), resId);
        loadString();
    }

    private void loadString() {
        try {
            InputStream is = getTestContext().getResources().openRawResource(R.raw.testtext);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line = reader.readLine();
            while (line != null) {
                sb.append(line + " ");
                line = reader.readLine();
            }
            mTestText = sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /************************** Helper functions ************************/
    private int lookForSuggestion(String prevWord, String currentWord) {
        for (int i = 1; i < currentWord.length(); i++) {
            if (i == 1) {
                if (sh.isDefaultNextSuggestion(prevWord, currentWord.substring(0, i),
                        currentWord)) {
                    return i;
                }
            } else {
                if (sh.isDefaultNextCorrection(prevWord, currentWord.substring(0, i),
                        currentWord)) {
                    return i;
                }
            }
        }
        return currentWord.length();
    }

    private double runText(boolean withBigrams) {
        StringTokenizer st = new StringTokenizer(mTestText);
        String prevWord = null;
        int typeCount = 0;
        int characterCount = 0; // without space
        int wordCount = 0;
        while (st.hasMoreTokens()) {
            String currentWord = st.nextToken();
            boolean endCheck = false;
            if (currentWord.matches("[\\w]*[\\.|?|!|*|@|&|/|:|;]")) {
                currentWord = currentWord.substring(0, currentWord.length() - 1);
                endCheck = true;
            }
            if (withBigrams && prevWord != null) {
                typeCount += lookForSuggestion(prevWord, currentWord);
            } else {
                typeCount += lookForSuggestion(null, currentWord);
            }
            characterCount += currentWord.length();
            if (!endCheck) prevWord = currentWord;
            wordCount++;
        }

        double result = (double) (characterCount - typeCount) / characterCount * 100;
        if (withBigrams) {
            Log.i(TAG, "with bigrams -> "  + result + " % saved!");
        } else {
            Log.i(TAG, "without bigrams  -> "  + result + " % saved!");
        }
        Log.i(TAG, "\ttotal number of words: " + wordCount);
        Log.i(TAG, "\ttotal number of characters: " + mTestText.length());
        Log.i(TAG, "\ttotal number of characters without space: " + characterCount);
        Log.i(TAG, "\ttotal number of characters typed: " + typeCount);
        return result;
    }


    /************************** Performance Tests ************************/
    /**
     * Compare the Suggest with and without bigram
     * Check the log for detail
     */
    public void testSuggestPerformance() {
        assertTrue(runText(false) <= runText(true));
    }
}
