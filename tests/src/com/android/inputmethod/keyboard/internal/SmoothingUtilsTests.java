/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.inputmethod.keyboard.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.inputmethod.keyboard.internal.MatrixUtils.MatrixOperationFailedException;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SmoothingUtilsTests {
    // "run tests" -c com.android.inputmethod.keyboard.internal.SmoothingUtilsTests
    private static final boolean DEBUG = false;

    @Test
    public void testGet3DParamaters() {
        final float[] xs = new float[] {0, 1, 2, 3, 4};
        final float[] ys = new float[] {1, 4, 15, 40, 85}; // y = x^3 + x^2 + x + 1
        final float[][] retval = new float[4][1];
        try {
            SmoothingUtils.get3DParameters(xs, ys, retval);
            if (DEBUG) {
                MatrixUtils.dump("3d", retval);
            }
            for (int i = 0; i < 4; ++i) {
                assertEquals(retval[i][0], 1.0f, 0.001f);
            }
        } catch (MatrixOperationFailedException e) {
            assertTrue(false);
        }
    }
}
