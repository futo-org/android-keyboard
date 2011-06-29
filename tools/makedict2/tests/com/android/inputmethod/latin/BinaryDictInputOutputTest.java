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

package com.android.inputmethod.latin;

import com.android.inputmethod.latin.FusionDictionary.Node;

import java.util.ArrayList;

import junit.framework.TestCase;

/**
 * Unit tests for BinaryDictInputOutput.
 */
public class BinaryDictInputOutputTest extends TestCase {

    public void setUp() throws Exception {
        super.setUp();
    }

    public void tearDown() throws Exception {
        super.tearDown();
    }

    // Test the flattened array contains the expected number of nodes, and
    // that it does not contain any duplicates.
    public void testFlattenNodes() {
        final FusionDictionary dict = new FusionDictionary();
        dict.add("foo", 1, null);
        dict.add("fta", 1, null);
        dict.add("ftb", 1, null);
        dict.add("bar", 1, null);
        dict.add("fool", 1, null);
        final ArrayList<Node> result = BinaryDictInputOutput.flattenTree(dict.mRoot);
        assertEquals(4, result.size());
        while (!result.isEmpty()) {
            final Node n = result.remove(0);
            assertFalse("Flattened array contained the same node twice", result.contains(n));
        }
    }

}
