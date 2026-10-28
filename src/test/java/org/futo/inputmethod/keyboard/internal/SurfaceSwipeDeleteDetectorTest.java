/*
 * Copyright (C) 2024 The Android Open Source Project
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

package org.futo.inputmethod.keyboard.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class SurfaceSwipeDeleteDetectorTest {
    private static final int STEP = 100;
    private static final int START_X = 1000;
    private static final long HOLD_TIMEOUT = 500;

    private SurfaceSwipeDeleteDetector mDetector;

    @Before
    public void setUp() {
        mDetector = new SurfaceSwipeDeleteDetector();
        mDetector.start(START_X, STEP, HOLD_TIMEOUT);
    }

    @Test
    public void rightwardSwipeNeverInitiates() {
        assertEquals(0, mDetector.onMove(START_X + 350, false, 0));
        assertFalse(mDetector.isActive());
        assertEquals(0, mDetector.onMove(START_X + 50, false, 0));
        assertFalse(mDetector.isActive());
    }

    @Test
    public void movementBelowStepDoesNothing() {
        assertEquals(0, mDetector.onMove(START_X - (STEP - 1), false, 0));
        assertFalse(mDetector.isActive());
    }

    @Test
    public void singleLeftStepDeletesOneWord() {
        assertEquals(-1, mDetector.onMove(START_X - STEP, false, 0));
        assertTrue(mDetector.isActive());
    }

    @Test
    public void longLeftSwipeStillSelectsExactlyOneWord() {
        assertEquals(-1, mDetector.onMove(START_X - 3 * STEP, false, 0));
        assertTrue(mDetector.isActive());
        assertEquals(1, mDetector.getSelectedWords());
    }

    @Test
    public void immediateReleaseAfterLongSwipeKeepsOneWord() {
        assertEquals(-1, mDetector.onMove(START_X - 5 * STEP, false, 0));
        assertEquals(0, mDetector.onMove(START_X - 8 * STEP, false, HOLD_TIMEOUT - 1));
        assertEquals(1, mDetector.getSelectedWords());
    }

    @Test
    public void noAdjustmentBeforeHold() {
        assertEquals(-1, mDetector.onMove(START_X - STEP, false, 0));
        assertEquals(0, mDetector.onMove(START_X - 4 * STEP, false, HOLD_TIMEOUT - 1));
        assertEquals(0, mDetector.onMove(START_X + 2 * STEP, false, HOLD_TIMEOUT - 1));
        assertEquals(1, mDetector.getSelectedWords());
    }

    @Test
    public void adjustmentOnlyAfterHoldWithRebaseline() {
        assertEquals(-1, mDetector.onMove(START_X - 3 * STEP, false, 0));
        final int driftX = START_X - 6 * STEP;
        assertEquals(0, mDetector.onMove(driftX, false, HOLD_TIMEOUT - 1));
        assertEquals(1, mDetector.getSelectedWords());
        assertEquals(0, mDetector.onMove(driftX, false, HOLD_TIMEOUT));
        assertEquals(1, mDetector.getSelectedWords());
        assertEquals(-1, mDetector.onMove(driftX - STEP, false, HOLD_TIMEOUT));
        assertEquals(2, mDetector.getSelectedWords());
        assertEquals(-1, mDetector.onMove(driftX - 2 * STEP, false, HOLD_TIMEOUT));
        assertEquals(3, mDetector.getSelectedWords());
        assertEquals(1, mDetector.onMove(driftX - STEP, false, HOLD_TIMEOUT));
        assertEquals(2, mDetector.getSelectedWords());
    }

    @Test
    public void adjustmentClampsAtZeroWords() {
        assertEquals(-1, mDetector.onMove(START_X - STEP, false, 0));
        final int origin = START_X - STEP;
        assertEquals(0, mDetector.onMove(origin, false, HOLD_TIMEOUT));
        assertEquals(-1, mDetector.onMove(origin - STEP, false, HOLD_TIMEOUT));
        assertEquals(2, mDetector.getSelectedWords());
        assertEquals(2, mDetector.onMove(origin + 5 * STEP, false, HOLD_TIMEOUT));
        assertEquals(0, mDetector.getSelectedWords());
        assertEquals(0, mDetector.onMove(origin + 8 * STEP, false, HOLD_TIMEOUT));
        assertTrue(mDetector.isActive());
    }

    @Test
    public void rtlAdjustsInMirrorDirection() {
        assertEquals(1, mDetector.onMove(START_X - STEP, true, 0));
        assertEquals(1, mDetector.getSelectedWords());
        final int origin = START_X - STEP;
        assertEquals(0, mDetector.onMove(origin, true, HOLD_TIMEOUT));
        assertEquals(1, mDetector.onMove(origin - STEP, true, HOLD_TIMEOUT));
        assertEquals(2, mDetector.getSelectedWords());
        assertEquals(-2, mDetector.onMove(origin + 5 * STEP, true, HOLD_TIMEOUT));
        assertEquals(0, mDetector.getSelectedWords());
    }

    @Test
    public void stepsAreIncremental() {
        assertEquals(-1, mDetector.onMove(START_X - STEP, false, 0));
        assertEquals(0, mDetector.onMove(START_X - STEP, false, HOLD_TIMEOUT));
        assertEquals(-1, mDetector.onMove(START_X - 2 * STEP, false, HOLD_TIMEOUT));
        assertEquals(0, mDetector.onMove(START_X - 2 * STEP - (STEP - 1), false, HOLD_TIMEOUT));
        assertEquals(-1, mDetector.onMove(START_X - 3 * STEP, false, HOLD_TIMEOUT));
    }

    @Test
    public void rtlFlipsDirection() {
        assertEquals(1, mDetector.onMove(START_X - STEP, true, 0));
        assertTrue(mDetector.isActive());
    }

    @Test
    public void movingBackRightReleasesSelection() {
        assertEquals(-1, mDetector.onMove(START_X - 2 * STEP, false, 0));
        assertEquals(0, mDetector.onMove(START_X - 2 * STEP, false, HOLD_TIMEOUT));
        assertEquals(1, mDetector.onMove(START_X - STEP, false, HOLD_TIMEOUT));
        assertTrue(mDetector.isActive());
        assertEquals(0, mDetector.getSelectedWords());
    }

    @Test
    public void cancelStopsGesture() {
        mDetector.onMove(START_X - STEP, false, 0);
        assertTrue(mDetector.isActive());
        mDetector.cancel();
        assertFalse(mDetector.isActive());
        assertEquals(0, mDetector.onMove(START_X + STEP, false, 0));
    }

    @Test
    public void startResetsState() {
        mDetector.onMove(START_X - STEP, false, 0);
        assertTrue(mDetector.isActive());
        mDetector.start(500, STEP, HOLD_TIMEOUT);
        assertFalse(mDetector.isActive());
        assertEquals(-1, mDetector.onMove(500 - STEP, false, 0));
    }

    @Test
    public void nonPositiveStepIsGuarded() {
        mDetector.start(START_X, 0, HOLD_TIMEOUT);
        assertEquals(-1, mDetector.onMove(START_X - 1, false, 0));
        assertTrue(mDetector.isActive());
    }

    @Test
    public void keyPressJitterDoesNotActivate() {
        for (int dx = -5; dx <= 5; dx++) {
            assertEquals(0, mDetector.onMove(START_X + dx, false, 0));
        }
        assertFalse(mDetector.isActive());
    }

    @Test
    public void rightwardDriftFromKeyDoesNotActivate() {
        assertEquals(0, mDetector.onMove(START_X + 200, false, 0));
        assertEquals(0, mDetector.onMove(START_X + 100, false, 0));
        assertFalse(mDetector.isActive());
    }

    @Test
    public void leftSwipeFromKeyActivatesAfterFullStep() {
        assertEquals(0, mDetector.onMove(START_X - (STEP - 1), false, 0));
        assertFalse(mDetector.isActive());
        assertEquals(-1, mDetector.onMove(START_X - STEP, false, 0));
        assertTrue(mDetector.isActive());
    }

    @Test
    public void restartFromNewKeyResetsOrigin() {
        assertEquals(-1, mDetector.onMove(START_X - STEP, false, 0));
        assertTrue(mDetector.isActive());
        mDetector.start(500, STEP, HOLD_TIMEOUT);
        assertFalse(mDetector.isActive());
        assertEquals(0, mDetector.onMove(500 - (STEP - 1), false, 0));
        assertEquals(-1, mDetector.onMove(500 - STEP, false, 0));
        assertTrue(mDetector.isActive());
    }
}
