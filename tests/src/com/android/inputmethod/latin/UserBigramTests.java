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
import com.android.inputmethod.latin.tests.R;
import java.util.Locale;

public class UserBigramTests extends AndroidTestCase {
    private static final String TAG = "UserBigramTests";

    private static final int SUGGESTION_STARTS = 6;
    private static final int MAX_DATA = 20;
    private static final int DELETE_DATA = 10;

    private SuggestHelper sh;

    @Override
    protected void setUp() {
        int[] resId = new int[] { R.raw.test };
        sh = new SuggestHelper(TAG, getTestContext(), resId, MAX_DATA, DELETE_DATA);
    }

    /************************** Tests ************************/

    /**
     * Test suggestion started at right time
     */
    public void testUserBigram() {
        for (int i = 0; i < SUGGESTION_STARTS; i++) sh.addToUserBigram(pair1);
        for (int i = 0; i < (SUGGESTION_STARTS - 1); i++) sh.addToUserBigram(pair2);

        assertTrue(sh.isUserBigramSuggestion("user", 'b', "bigram"));
        assertFalse(sh.isUserBigramSuggestion("android", 'p', "platform"));
    }

    /**
     * Test loading correct (locale) bigrams
     */
    public void testOpenAndClose() {
        for (int i = 0; i < SUGGESTION_STARTS; i++) sh.addToUserBigram(pair1);
        assertTrue(sh.isUserBigramSuggestion("user", 'b', "bigram"));

        // change to fr_FR
        sh.changeUserBigramLocale(getTestContext(), Locale.FRANCE);
        for (int i = 0; i < SUGGESTION_STARTS; i++) sh.addToUserBigram(pair3);
        assertTrue(sh.isUserBigramSuggestion("locale", 'f', "france"));
        assertFalse(sh.isUserBigramSuggestion("user", 'b', "bigram"));

        // change back to en_US
        sh.changeUserBigramLocale(getTestContext(), Locale.US);
        assertFalse(sh.isUserBigramSuggestion("locale", 'f', "france"));
        assertTrue(sh.isUserBigramSuggestion("user", 'b', "bigram"));
    }

    /**
     * Test data gets pruned when it is over maximum
     */
    public void testPruningData() {
        for (int i = 0; i < SUGGESTION_STARTS; i++) sh.addToUserBigram(sentence0);
        sh.flushUserBigrams();
        assertTrue(sh.isUserBigramSuggestion("Hello", 'w', "world"));

        sh.addToUserBigram(sentence1);
        sh.addToUserBigram(sentence2);
        assertTrue(sh.isUserBigramSuggestion("Hello", 'w', "world"));

        // pruning should happen
        sh.addToUserBigram(sentence3);
        sh.addToUserBigram(sentence4);

        // trying to reopen database to check pruning happened in database
        sh.changeUserBigramLocale(getTestContext(), Locale.US);
        assertFalse(sh.isUserBigramSuggestion("Hello", 'w', "world"));
    }

    final String[] pair1 = new String[] {"user", "bigram"};
    final String[] pair2 = new String[] {"android","platform"};
    final String[] pair3 = new String[] {"locale", "france"};
    final String sentence0 = "Hello world";
    final String sentence1 = "This is a test for user input based bigram";
    final String sentence2 = "It learns phrases that contain both dictionary and nondictionary "
            + "words";
    final String sentence3 = "This should give better suggestions than the previous version";
    final String sentence4 = "Android stock keyboard is improving";
}
