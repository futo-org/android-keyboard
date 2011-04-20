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

import android.test.AndroidTestCase;

import com.android.inputmethod.latin.tests.R;

public class UtilsTests extends AndroidTestCase {

    // The following is meant to be a reasonable default for
    // the "word_separators" resource.
    private static final String sSeparators = ".,:;!?-";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    /************************** Tests ************************/

    /**
     * Test for getting previous word (for bigram suggestions)
     */
    public void testGetPreviousWord() {
        // If one of the following cases breaks, the bigram suggestions won't work.
        assertEquals(EditingUtils.getPreviousWord("abc def", sSeparators), "abc");
        assertNull(EditingUtils.getPreviousWord("abc", sSeparators));
        assertNull(EditingUtils.getPreviousWord("abc. def", sSeparators));

        // The following tests reflect the current behavior of the function
        // EditingUtils#getPreviousWord.
        // TODO: However at this time, the code does never go
        // into such a path, so it should be safe to change the behavior of
        // this function if needed - especially since it does not seem very
        // logical. These tests are just there to catch any unintentional
        // changes in the behavior of the EditingUtils#getPreviousWord method.
        assertEquals(EditingUtils.getPreviousWord("abc def ", sSeparators), "abc");
        assertEquals(EditingUtils.getPreviousWord("abc def.", sSeparators), "abc");
        assertEquals(EditingUtils.getPreviousWord("abc def .", sSeparators), "def");
        assertNull(EditingUtils.getPreviousWord("abc ", sSeparators));
    }

    /**
     * Test for getting the word before the cursor (for bigram)
     */
    public void testGetThisWord() {
        assertEquals(EditingUtils.getThisWord("abc def", sSeparators), "def");
        assertEquals(EditingUtils.getThisWord("abc def ", sSeparators), "def");
        assertNull(EditingUtils.getThisWord("abc def.", sSeparators));
        assertNull(EditingUtils.getThisWord("abc def .", sSeparators));
    }
}
