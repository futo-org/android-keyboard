/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.inputmethod.latin.common;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class UnicodeSurrogateTests {

    @Test
    public void testIsLowSurrogate() {
        assertFalse(UnicodeSurrogate.isLowSurrogate('\uD7FF'));
        assertTrue(UnicodeSurrogate.isLowSurrogate('\uD83D'));
        assertFalse(UnicodeSurrogate.isLowSurrogate('\uDC00'));
    }

    @Test
    public void testIsHighSurrogate() {
        assertFalse(UnicodeSurrogate.isHighSurrogate('\uDBFF'));
        assertTrue(UnicodeSurrogate.isHighSurrogate('\uDE25'));
        assertFalse(UnicodeSurrogate.isHighSurrogate('\uE000'));
    }
}
