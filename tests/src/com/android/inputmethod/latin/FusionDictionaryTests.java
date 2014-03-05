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

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.inputmethod.latin.makedict.FormatSpec.DictionaryOptions;
import com.android.inputmethod.latin.makedict.FusionDictionary;
import com.android.inputmethod.latin.makedict.ProbabilityInfo;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNodeArray;

import java.util.HashMap;

/**
 * Unit test for FusionDictionary
 */
@SmallTest
public class FusionDictionaryTests extends AndroidTestCase {
    public void testFindWordInTree() {
        FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                new DictionaryOptions(new HashMap<String,String>()));

        dict.add("abc", new ProbabilityInfo(10), null, false /* isNotAWord */);
        assertNull(FusionDictionary.findWordInTree(dict.mRootNodeArray, "aaa"));
        assertNotNull(FusionDictionary.findWordInTree(dict.mRootNodeArray, "abc"));

        dict.add("aa", new ProbabilityInfo(10), null, false /* isNotAWord */);
        assertNull(FusionDictionary.findWordInTree(dict.mRootNodeArray, "aaa"));
        assertNotNull(FusionDictionary.findWordInTree(dict.mRootNodeArray, "aa"));

        dict.add("babcd", new ProbabilityInfo(10), null, false /* isNotAWord */);
        dict.add("bacde", new ProbabilityInfo(10), null, false /* isNotAWord */);
        assertNull(FusionDictionary.findWordInTree(dict.mRootNodeArray, "ba"));
        assertNotNull(FusionDictionary.findWordInTree(dict.mRootNodeArray, "babcd"));
        assertNotNull(FusionDictionary.findWordInTree(dict.mRootNodeArray, "bacde"));
    }
}
