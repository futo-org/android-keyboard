/*
 * Copyright (C) 2010,2011 The Android Open Source Project
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
import com.android.inputmethod.latin.tests.R;

import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.text.TextUtils;
import android.util.Slog;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.StringTokenizer;

public class SuggestPerformanceTests extends SuggestTestsBase {
    private static final String TAG = SuggestPerformanceTests.class.getSimpleName();

    private String mTestText;
    private SuggestHelper mHelper;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final AssetFileDescriptor dict = openTestRawResourceFd(R.raw.test);
        mHelper = new SuggestHelper(
                getContext(), mTestPackageFile, dict.getStartOffset(), dict.getLength(),
                createKeyboardId(Locale.US, Configuration.ORIENTATION_PORTRAIT));
        loadString(R.raw.testtext);
    }

    private void loadString(int testFileId) {
        final String testFile = getTestContext().getResources().getResourceName(testFileId);
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(
                    new InputStreamReader(openTestRawResource(testFileId)));
            final StringBuilder sb = new StringBuilder();
            String line;
            Slog.i(TAG, "Reading test file " + testFile);
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                sb.append(" ");
            }
            mTestText = sb.toString();
        } catch (Exception e) {
            Slog.e(TAG, "Can not read " + testFile);
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    Slog.e(TAG, "Closing " + testFile + " failed");
                }
            }
        }
    }

    /************************** Helper functions ************************/
    private int lookForBigramSuggestion(String prevWord, String currentWord) {
        for (int i = 1; i < currentWord.length(); i++) {
            final CharSequence prefix = currentWord.substring(0, i);
            final CharSequence word = (i == 1)
                    ? mHelper.getBigramFirstSuggestion(prevWord, prefix)
                    : mHelper.getBigramAutoCorrection(prevWord, prefix);
            if (TextUtils.equals(word, currentWord))
                return i;
        }
        return currentWord.length();
    }

    private double runText(boolean withBigrams) {
        mHelper.setCorrectionMode(
                withBigrams ? Suggest.CORRECTION_FULL_BIGRAM : Suggest.CORRECTION_FULL);
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
                typeCount += lookForBigramSuggestion(prevWord, currentWord);
            } else {
                typeCount += lookForBigramSuggestion(null, currentWord);
            }
            characterCount += currentWord.length();
            if (!endCheck) prevWord = currentWord;
            wordCount++;
        }

        double result = (double) (characterCount - typeCount) / characterCount * 100;
        if (withBigrams) {
            Slog.i(TAG, "with bigrams -> "  + result + " % saved!");
        } else {
            Slog.i(TAG, "without bigrams  -> "  + result + " % saved!");
        }
        Slog.i(TAG, "\ttotal number of words: " + wordCount);
        Slog.i(TAG, "\ttotal number of characters: " + mTestText.length());
        Slog.i(TAG, "\ttotal number of characters without space: " + characterCount);
        Slog.i(TAG, "\ttotal number of characters typed: " + typeCount);
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
