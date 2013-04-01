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

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

@SmallTest
public class HermiteInterpolatorTests extends AndroidTestCase {
    private final HermiteInterpolator mInterpolator = new HermiteInterpolator();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    private static final float EPSLION = 0.0000005f;

    private static void assertFloatEquals(final String message, float expected, float actual) {
        if (Math.abs(expected - actual) >= EPSLION) {
            fail(String.format("%s expected:<%s> but was:<%s>", message, expected, actual));
        }
    }

    // t=0 p0=(0,1)
    // t=1 p1=(1,0)
    // t=2 p2=(3,2)
    // t=3 p3=(2,3)
    //   y
    //   |
    // 3 +       o p3
    //   |
    // 2 +           o p2
    //   |
    // 1 o p0
    //   |    p1
    // 0 +---o---+---+-- x
    //   0   1   2   3
    private final int[] mXCoords = { 0, 1, 3, 2 };
    private final int[] mYCoords = { 1, 0, 2, 3 };
    private static final int p0 = 0;
    private static final int p1 = 1;
    private static final int p2 = 2;
    private static final int p3 = 3;

    public void testP0P1() {
        // [(p0 p1) p2 p3]
        mInterpolator.reset(mXCoords, mYCoords, p0, p3 + 1);
        mInterpolator.setInterval(p0 - 1, p0, p1, p1 + 1);
        assertEquals("p0x", mXCoords[p0], mInterpolator.mP1X);
        assertEquals("p0y", mYCoords[p0], mInterpolator.mP1Y);
        assertEquals("p1x", mXCoords[p1], mInterpolator.mP2X);
        assertEquals("p1y", mYCoords[p1], mInterpolator.mP2Y);
        // XY-slope at p0=3.0 (-0.75/-0.25)
        assertFloatEquals("slope x p0", -0.25f, mInterpolator.mSlope1X);
        assertFloatEquals("slope y p0", -0.75f, mInterpolator.mSlope1Y);
        // XY-slope at p1=1/3.0 (0.50/1.50)
        assertFloatEquals("slope x p1",  1.50f, mInterpolator.mSlope2X);
        assertFloatEquals("slope y p1",  0.50f, mInterpolator.mSlope2Y);
        // t=0.0 (p0)
        mInterpolator.interpolate(0.0f);
        assertFloatEquals("t=0.0 x", 0.0f, mInterpolator.mInterpolatedX);
        assertFloatEquals("t=0.0 y", 1.0f, mInterpolator.mInterpolatedY);
        // t=0.2
        mInterpolator.interpolate(0.2f);
        assertFloatEquals("t=0.2 x", 0.02400f, mInterpolator.mInterpolatedX);
        assertFloatEquals("t=0.2 y", 0.78400f, mInterpolator.mInterpolatedY);
        // t=0.5
        mInterpolator.interpolate(0.5f);
        assertFloatEquals("t=0.5 x", 0.28125f, mInterpolator.mInterpolatedX);
        assertFloatEquals("t=0.5 y", 0.34375f, mInterpolator.mInterpolatedY);
        // t=0.8
        mInterpolator.interpolate(0.8f);
        assertFloatEquals("t=0.8 x", 0.69600f, mInterpolator.mInterpolatedX);
        assertFloatEquals("t=0.8 y", 0.01600f, mInterpolator.mInterpolatedY);
        // t=1.0 (p1)
        mInterpolator.interpolate(1.0f);
        assertFloatEquals("t=1.0 x", 1.0f, mInterpolator.mInterpolatedX);
        assertFloatEquals("t=1.0 y", 0.0f, mInterpolator.mInterpolatedY);
    }

    public void testP1P2() {
        // [p0 (p1 p2) p3]
        mInterpolator.reset(mXCoords, mYCoords, p0, p3 + 1);
        mInterpolator.setInterval(p1 - 1, p1, p2, p2 + 1);
        assertEquals("p1x", mXCoords[p1], mInterpolator.mP1X);
        assertEquals("p1y", mYCoords[p1], mInterpolator.mP1Y);
        assertEquals("p2x", mXCoords[p2], mInterpolator.mP2X);
        assertEquals("p2y", mYCoords[p2], mInterpolator.mP2Y);
        // XY-slope at p1=1/3.0 (0.50/1.50)
        assertFloatEquals("slope x p1",  1.50f, mInterpolator.mSlope1X);
        assertFloatEquals("slope y p1",  0.50f, mInterpolator.mSlope1Y);
        // XY-slope at p2=3.0 (1.50/0.50)
        assertFloatEquals("slope x p2",  0.50f, mInterpolator.mSlope2X);
        assertFloatEquals("slope y p2",  1.50f, mInterpolator.mSlope2Y);
        // t=0.0 (p1)
        mInterpolator.interpolate(0.0f);
        assertFloatEquals("t=0.0 x", 1.0f, mInterpolator.mInterpolatedX);
        assertFloatEquals("t=0.0 y", 0.0f, mInterpolator.mInterpolatedY);
        // t=0.2
        mInterpolator.interpolate(0.2f);
        assertFloatEquals("t=0.2 x", 1.384f, mInterpolator.mInterpolatedX);
        assertFloatEquals("t=0.2 y", 0.224f, mInterpolator.mInterpolatedY);
        // t=0.5
        mInterpolator.interpolate(0.5f);
        assertFloatEquals("t=0.5 x", 2.125f, mInterpolator.mInterpolatedX);
        assertFloatEquals("t=0.5 y", 0.875f, mInterpolator.mInterpolatedY);
        // t=0.8
        mInterpolator.interpolate(0.8f);
        assertFloatEquals("t=0.8 x", 2.776f, mInterpolator.mInterpolatedX);
        assertFloatEquals("t=0.8 y", 1.616f, mInterpolator.mInterpolatedY);
        // t=1.0 (p2)
        mInterpolator.interpolate(1.0f);
        assertFloatEquals("t=1.0 x", 3.0f, mInterpolator.mInterpolatedX);
        assertFloatEquals("t=1.0 y", 2.0f, mInterpolator.mInterpolatedY);
    }

    public void testP2P3() {
        // [p0 p1 (p2 p3)]
        mInterpolator.reset(mXCoords, mYCoords, p0, p3 + 1);
        mInterpolator.setInterval(p2 - 1, p2, p3, p3 + 1);
        assertEquals("p2x", mXCoords[p2], mInterpolator.mP1X);
        assertEquals("p2y", mYCoords[p2], mInterpolator.mP1Y);
        assertEquals("p3x", mXCoords[p3], mInterpolator.mP2X);
        assertEquals("p3y", mYCoords[p3], mInterpolator.mP2Y);
        // XY-slope at p2=3.0 (1.50/0.50)
        assertFloatEquals("slope x p2",  0.50f, mInterpolator.mSlope1X);
        assertFloatEquals("slope y p2",  1.50f, mInterpolator.mSlope1Y);
        // XY-slope at p3=1/3.0 (-0.25/-0.75)
        assertFloatEquals("slope x p3", -0.75f, mInterpolator.mSlope2X);
        assertFloatEquals("slope y p3", -0.25f, mInterpolator.mSlope2Y);
        // t=0.0 (p2)
        mInterpolator.interpolate(0.0f);
        assertFloatEquals("t=0.0 x", 3.0f, mInterpolator.mInterpolatedX);
        assertFloatEquals("t=0.0 y", 2.0f, mInterpolator.mInterpolatedY);
        // t=0.2
        mInterpolator.interpolate(0.2f);
        assertFloatEquals("t=0.2 x", 2.98400f, mInterpolator.mInterpolatedX);
        assertFloatEquals("t=0.2 y", 2.30400f, mInterpolator.mInterpolatedY);
        // t=0.5
        mInterpolator.interpolate(0.5f);
        assertFloatEquals("t=0.5 x", 2.65625f, mInterpolator.mInterpolatedX);
        assertFloatEquals("t=0.5 y", 2.71875f, mInterpolator.mInterpolatedY);
        // t=0.8
        mInterpolator.interpolate(0.8f);
        assertFloatEquals("t=0.8 x", 2.21600f, mInterpolator.mInterpolatedX);
        assertFloatEquals("t=0.8 y", 2.97600f, mInterpolator.mInterpolatedY);
        // t=1.0 (p3)
        mInterpolator.interpolate(1.0f);
        assertFloatEquals("t=1.0 x", 2.0f, mInterpolator.mInterpolatedX);
        assertFloatEquals("t=1.0 y", 3.0f, mInterpolator.mInterpolatedY);
    }

    public void testJustP1P2() {
        // [(p1 p2)]
        mInterpolator.reset(mXCoords, mYCoords, p1, p2 + 1);
        mInterpolator.setInterval(p1 - 1, p1, p2, p2 + 1);
        assertEquals("p1x", mXCoords[p1], mInterpolator.mP1X);
        assertEquals("p1y", mYCoords[p1], mInterpolator.mP1Y);
        assertEquals("p2x", mXCoords[p2], mInterpolator.mP2X);
        assertEquals("p2y", mYCoords[p2], mInterpolator.mP2Y);
        // XY-slope at p1=1.0 (2.0/2.0)
        assertFloatEquals("slope x p1", 2.00f, mInterpolator.mSlope1X);
        assertFloatEquals("slope y p1", 2.00f, mInterpolator.mSlope1Y);
        // XY-slope at p2=1.0 (2.0/2.0)
        assertFloatEquals("slope x p2", 2.00f, mInterpolator.mSlope2X);
        assertFloatEquals("slope y p2", 2.00f, mInterpolator.mSlope2Y);
        // t=0.0 (p1)
        mInterpolator.interpolate(0.0f);
        assertFloatEquals("t=0.0 x", 1.0f, mInterpolator.mInterpolatedX);
        assertFloatEquals("t=0.0 y", 0.0f, mInterpolator.mInterpolatedY);
        // t=0.2
        mInterpolator.interpolate(0.2f);
        assertFloatEquals("t=0.2 x", 1.4f, mInterpolator.mInterpolatedX);
        assertFloatEquals("t=0.2 y", 0.4f, mInterpolator.mInterpolatedY);
        // t=0.5
        mInterpolator.interpolate(0.5f);
        assertFloatEquals("t=0.5 x", 2.0f, mInterpolator.mInterpolatedX);
        assertFloatEquals("t=0.5 y", 1.0f, mInterpolator.mInterpolatedY);
        // t=0.8
        mInterpolator.interpolate(0.8f);
        assertFloatEquals("t=0.8 x", 2.6f, mInterpolator.mInterpolatedX);
        assertFloatEquals("t=0.8 y", 1.6f, mInterpolator.mInterpolatedY);
        // t=1.0 (p2)
        mInterpolator.interpolate(1.0f);
        assertFloatEquals("t=1.0 x", 3.0f, mInterpolator.mInterpolatedX);
        assertFloatEquals("t=1.0 y", 2.0f, mInterpolator.mInterpolatedY);
    }
}
