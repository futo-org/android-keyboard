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
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;

import com.android.inputmethod.latin.tests.R;

import java.util.Locale;

public class UserBigramSuggestTests extends SuggestTestsBase {
    private static final int SUGGESTION_STARTS = 6;
    private static final int MAX_DATA = 20;
    private static final int DELETE_DATA = 10;

    private UserBigramSuggestHelper mHelper;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final AssetFileDescriptor dict = openTestRawResourceFd(R.raw.test);
        final Locale locale = Locale.US;
        mHelper = new UserBigramSuggestHelper(
                getContext(), mTestPackageFile, dict.getStartOffset(), dict.getLength(),
                MAX_DATA, DELETE_DATA,
                createKeyboardId(locale, Configuration.ORIENTATION_PORTRAIT), locale);
    }

    /************************** Tests ************************/

    /**
     * Test suggestion started at right time
     */
    public void testUserBigram() {
        for (int i = 0; i < SUGGESTION_STARTS; i++) mHelper.addToUserBigram(pair1);
        for (int i = 0; i < (SUGGESTION_STARTS - 1); i++) mHelper.addToUserBigram(pair2);

        isInSuggestions("bigram", mHelper.searchUserBigramSuggestion("user", 'b', "bigram"));
        isNotInSuggestions("platform",
                mHelper.searchUserBigramSuggestion("android", 'p', "platform"));
    }

    /**
     * Test loading correct (locale) bigrams
     */
    public void testOpenAndClose() {
        for (int i = 0; i < SUGGESTION_STARTS; i++) mHelper.addToUserBigram(pair1);
        isInSuggestions("bigram in default locale",
                mHelper.searchUserBigramSuggestion("user", 'b', "bigram"));

        // change to fr_FR
        mHelper.changeUserBigramLocale(Locale.FRANCE);
        for (int i = 0; i < SUGGESTION_STARTS; i++) mHelper.addToUserBigram(pair3);
        isInSuggestions("france in fr_FR",
                mHelper.searchUserBigramSuggestion("locale", 'f', "france"));
        isNotInSuggestions("bigram in fr_FR",
                mHelper.searchUserBigramSuggestion("user", 'b', "bigram"));

        // change back to en_US
        mHelper.changeUserBigramLocale(Locale.US);
        isNotInSuggestions("france in en_US",
                mHelper.searchUserBigramSuggestion("locale", 'f', "france"));
        isInSuggestions("bigram in en_US",
                mHelper.searchUserBigramSuggestion("user", 'b', "bigram"));
    }

    /**
     * Test data gets pruned when it is over maximum
     */
    public void testPruningData() {
        for (int i = 0; i < SUGGESTION_STARTS; i++) mHelper.addToUserBigram(sentence0);
        mHelper.flushUserBigrams();
        isInSuggestions("world after several sentence 0",
                mHelper.searchUserBigramSuggestion("Hello", 'w', "world"));

        mHelper.addToUserBigram(sentence1);
        mHelper.addToUserBigram(sentence2);
        isInSuggestions("world after sentence 1 and 2",
                mHelper.searchUserBigramSuggestion("Hello", 'w', "world"));

        // pruning should happen
        mHelper.addToUserBigram(sentence3);
        mHelper.addToUserBigram(sentence4);

        // trying to reopen database to check pruning happened in database
        mHelper.changeUserBigramLocale(Locale.US);
        isNotInSuggestions("world after sentence 3 and 4",
                mHelper.searchUserBigramSuggestion("Hello", 'w', "world"));
    }

    private static final String[] pair1 = {"user", "bigram"};
    private static final String[] pair2 = {"android","platform"};
    private static final String[] pair3 = {"locale", "france"};
    private static final String sentence0 = "Hello world";
    private static final String sentence1 = "This is a test for user input based bigram";
    private static final String sentence2 = "It learns phrases that contain both dictionary and "
        + "nondictionary words";
    private static final String sentence3 = "This should give better suggestions than the previous "
        + "version";
    private static final String sentence4 = "Android stock keyboard is improving";
}
