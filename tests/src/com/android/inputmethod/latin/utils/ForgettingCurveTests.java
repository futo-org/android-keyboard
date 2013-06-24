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

package com.android.inputmethod.latin.utils;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

@SmallTest
public class ForgettingCurveTests extends AndroidTestCase {
    public void testFcToFreq() {
        for (int i = 0; i < Byte.MAX_VALUE; ++i) {
            final byte fc = (byte)i;
            final int e = UserHistoryForgettingCurveUtils.fcToElapsedTime(fc);
            final int c = UserHistoryForgettingCurveUtils.fcToCount(fc);
            final int l = UserHistoryForgettingCurveUtils.fcToLevel(fc);
            final byte fc2 = UserHistoryForgettingCurveUtils.calcFc(e, c, l);
            assertEquals(fc, fc2);
        }
        byte fc = 0;
        int l;
        for (int i = 0; i < 4; ++i) {
            for (int j = 0; j < (UserHistoryForgettingCurveUtils.COUNT_MAX + 1); ++j) {
                fc = UserHistoryForgettingCurveUtils.pushCount(fc, true);
            }
            l = UserHistoryForgettingCurveUtils.fcToLevel(fc);
            assertEquals(l, Math.max(1, Math.min(i + 1, 3)));
        }
        fc = 0;
        for (int i = 0; i < 4; ++i) {
            for (int j = 0; j < (UserHistoryForgettingCurveUtils.COUNT_MAX + 1); ++j) {
                fc = UserHistoryForgettingCurveUtils.pushCount(fc, false);
            }
            l = UserHistoryForgettingCurveUtils.fcToLevel(fc);
            assertEquals(l, Math.min(i + 1, 3));
        }
        for (int i = 0; i < 4; ++i) {
            for (int j = 0; j < (UserHistoryForgettingCurveUtils.ELAPSED_TIME_MAX + 1); ++j) {
                fc = UserHistoryForgettingCurveUtils.pushElapsedTime(fc);
            }
            l = UserHistoryForgettingCurveUtils.fcToLevel(fc);
            assertEquals(l, Math.max(0, 2 - i));
        }
    }
}
