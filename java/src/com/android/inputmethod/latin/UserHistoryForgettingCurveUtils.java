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

public class UserHistoryForgettingCurveUtils {
    private static final int FC_FREQ_MAX = 127;
    /* package */ static final int COUNT_MAX = 3;
    private static final int FC_LEVEL_MAX = 3;
    /* package */ static final int ELAPSED_TIME_MAX = 15;
    private static final int ELAPSED_TIME_INTERVAL_HOURS = 6;
    private static final int HALF_LIFE_HOURS = 48;

    /* package */ static  int fcToElapsedTime(byte fc) {
        return fc & 0x0F;
    }

    /* package */ static int fcToCount(byte fc) {
        return (fc >> 4) & 0x03;
    }

    /* package */ static int fcToLevel(byte fc) {
        return (fc >> 6) & 0x03;
    }

    private static int calcFreq(int elapsedTime, int count, int level) {
        if (level <= 0) {
            // Reserved words, just return 0
            return 0;
        }
        if (count == COUNT_MAX) {
            // Temporary promote because it's frequently typed recently
            ++level;
        }
        final int et = Math.min(FC_FREQ_MAX, Math.max(0, elapsedTime));
        final int l = Math.min(FC_LEVEL_MAX, Math.max(0, level));
        return MathUtils.SCORE_TABLE[l - 1][et];
    }

    /* pakcage */ static byte calcFc(int elapsedTime, int count, int level) {
        final int et = Math.min(FC_FREQ_MAX, Math.max(0, elapsedTime));
        final int c = Math.min(COUNT_MAX, Math.max(0, count));
        final int l = Math.min(FC_LEVEL_MAX, Math.max(0, level));
        return (byte)(et | (c << 4) | (l << 6));
    }

    public static int fcToFreq(byte fc) {
        final int elapsedTime = fcToElapsedTime(fc);
        final int count = fcToCount(fc);
        final int level = fcToLevel(fc);
        return calcFreq(elapsedTime, count, level);
    }

    public static byte pushElapsedTime(byte fc) {
        int elapsedTime = fcToElapsedTime(fc);
        int count = fcToCount(fc);
        int level = fcToLevel(fc);
        if (elapsedTime >= ELAPSED_TIME_MAX) {
            // Downgrade level
            elapsedTime = 0;
            count = COUNT_MAX;
            --level;
        } else {
            ++elapsedTime;
        }
        return calcFc(elapsedTime, count, level);
    }

    public static byte pushCount(byte fc, boolean isValid) {
        final int elapsedTime = fcToElapsedTime(fc);
        int count = fcToCount(fc);
        int level = fcToLevel(fc);
        if ((elapsedTime == 0 && count >= COUNT_MAX) || (isValid && level == 0)) {
            // Upgrade level
            ++level;
            count = 0;
        } else {
            ++count;
        }
        return calcFc(0, count, level);
    }

    private static class MathUtils {
        public static final int[][] SCORE_TABLE = new int[FC_LEVEL_MAX][ELAPSED_TIME_MAX + 1];
        static {
            for (int i = 0; i < FC_LEVEL_MAX; ++i) {
                final double initialFreq;
                if (i >= 2) {
                    initialFreq = (double)FC_FREQ_MAX;
                } else if (i == 1) {
                    initialFreq = (double)FC_FREQ_MAX / 2;
                } else if (i == 0) {
                    initialFreq = (double)FC_FREQ_MAX / 4;
                } else {
                    continue;
                }
                for (int j = 0; j < ELAPSED_TIME_MAX; ++j) {
                    final double elapsedHour = j * ELAPSED_TIME_INTERVAL_HOURS;
                    final double freq =
                            initialFreq * Math.pow(initialFreq, elapsedHour / HALF_LIFE_HOURS);
                    final int intFreq = Math.min(FC_FREQ_MAX, Math.max(0, (int)freq));
                    SCORE_TABLE[i][j] = intFreq;
                }
            }
        }
    }
}
