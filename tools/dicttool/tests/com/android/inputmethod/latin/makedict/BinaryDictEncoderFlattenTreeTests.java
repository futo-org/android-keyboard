/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.inputmethod.latin.makedict;

import com.android.inputmethod.latin.makedict.FormatSpec.DictionaryOptions;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNodeArray;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Unit tests for BinaryDictEncoderUtils.flattenTree().
 */
public class BinaryDictEncoderFlattenTreeTests extends TestCase {
    // Test the flattened array contains the expected number of nodes, and
    // that it does not contain any duplicates.
    public void testFlattenNodes() {
        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                new DictionaryOptions(new HashMap<String, String>()));
        dict.add("foo", new ProbabilityInfo(1), null, false /* isNotAWord */,
                false /* isPossiblyOffensive */);
        dict.add("fta", new ProbabilityInfo(1), null, false /* isNotAWord */,
                false /* isPossiblyOffensive */);
        dict.add("ftb", new ProbabilityInfo(1), null, false /* isNotAWord */,
                false /* isPossiblyOffensive */);
        dict.add("bar", new ProbabilityInfo(1), null, false /* isNotAWord */,
                false /* isPossiblyOffensive */);
        dict.add("fool", new ProbabilityInfo(1), null, false /* isNotAWord */,
                false /* isPossiblyOffensive */);
        final ArrayList<PtNodeArray> result =
                BinaryDictEncoderUtils.flattenTree(dict.mRootNodeArray);
        assertEquals(4, result.size());
        while (!result.isEmpty()) {
            final PtNodeArray n = result.remove(0);
            assertFalse("Flattened array contained the same node twice", result.contains(n));
        }
    }
}
