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

import android.util.Log;

import java.util.concurrent.TimeUnit;

public final class UserHistoryForgettingCurveUtils {
    private static final String TAG = UserHistoryForgettingCurveUtils.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static final int DEFAULT_FC_FREQ = 127;
    private static final int BOOSTED_FC_FREQ = 200;
    private static int FC_FREQ_MAX = DEFAULT_FC_FREQ;
    /* package */ static final int COUNT_MAX = 3;
    private static final int FC_LEVEL_MAX = 3;
    /* package */ static final int ELAPSED_TIME_MAX = 15;
    private static final int ELAPSED_TIME_INTERVAL_HOURS = 6;
    private static final long ELAPSED_TIME_INTERVAL_MILLIS =
            TimeUnit.HOURS.toMillis(ELAPSED_TIME_INTERVAL_HOURS);
    private static final int HALF_LIFE_HOURS = 48;
    private static final int MAX_PUSH_ELAPSED = (FC_LEVEL_MAX + 1) * (ELAPSED_TIME_MAX + 1);

    public static void boostMaxFreqForDebug() {
        FC_FREQ_MAX = BOOSTED_FC_FREQ;
    }

    public static void resetMaxFreqForDebug() {
        FC_FREQ_MAX = DEFAULT_FC_FREQ;
    }

    private UserHistoryForgettingCurveUtils() {
        // This utility class is not publicly instantiable.
    }

    public static final class ForgettingCurveParams {
        private byte mFc;
        long mLastTouchedTime = 0;
        private final boolean mIsValid;

        private void updateLastTouchedTime() {
            mLastTouchedTime = System.currentTimeMillis();
        }

        public ForgettingCurveParams(boolean isValid) {
            this(System.currentTimeMillis(), isValid);
        }

        private ForgettingCurveParams(long now, boolean isValid) {
            this(pushCount((byte)0, isValid), now, now, isValid);
        }

        /** This constructor is called when the user history bigram dictionary is being restored. */
        public ForgettingCurveParams(int fc, long now, long last) {
            // All words with level >= 1 had been saved.
            // Invalid words with level == 0 had been saved.
            // Valid words words with level == 0 had *not* been saved.
            this(fc, now, last, fcToLevel((byte)fc) > 0);
        }

        private ForgettingCurveParams(int fc, long now, long last, boolean isValid) {
            mIsValid = isValid;
            mFc = (byte)fc;
            mLastTouchedTime = last;
            updateElapsedTime(now);
        }

        public boolean isValid() {
            return mIsValid;
        }

        public byte getFc() {
            updateElapsedTime(System.currentTimeMillis());
            return mFc;
        }

        public int getFrequency() {
            updateElapsedTime(System.currentTimeMillis());
            return UserHistoryForgettingCurveUtils.fcToFreq(mFc);
        }

        public int notifyTypedAgainAndGetFrequency() {
            updateLastTouchedTime();
            // TODO: Check whether this word is valid or not
            mFc = pushCount(mFc, false);
            return UserHistoryForgettingCurveUtils.fcToFreq(mFc);
        }

        private void updateElapsedTime(long now) {
            final int elapsedTimeCount =
                    (int)((now - mLastTouchedTime) / ELAPSED_TIME_INTERVAL_MILLIS);
            if (elapsedTimeCount <= 0) {
                return;
            }
            if (elapsedTimeCount >= MAX_PUSH_ELAPSED) {
                mLastTouchedTime = now;
                mFc = 0;
                return;
            }
            for (int i = 0; i < elapsedTimeCount; ++i) {
                mLastTouchedTime += ELAPSED_TIME_INTERVAL_MILLIS;
                mFc = pushElapsedTime(mFc);
            }
        }
    }

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
            // Reserved words, just return -1
            return -1;
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
            if (DEBUG) {
                Log.d(TAG, "Upgrade level.");
            }
        } else {
            ++count;
        }
        return calcFc(0, count, level);
    }

    // TODO: isValid should be false for a word whose frequency is 0,
    // or that is not in the dictionary.
    /**
     * Check wheather we should save the bigram to the SQL DB or not
     */
    public static boolean needsToSave(byte fc, boolean isValid, boolean addLevel0Bigram) {
        int level = fcToLevel(fc);
        if (level == 0) {
            if (isValid || !addLevel0Bigram) {
                return false;
            }
        }
        final int elapsedTime = fcToElapsedTime(fc);
        return (elapsedTime < ELAPSED_TIME_MAX - 1 || level > 0);
    }

    private static final class MathUtils {
        public static final int[][] SCORE_TABLE = new int[FC_LEVEL_MAX][ELAPSED_TIME_MAX + 1];
        static {
            for (int i = 0; i < FC_LEVEL_MAX; ++i) {
                final float initialFreq;
                if (i >= 2) {
                    initialFreq = FC_FREQ_MAX;
                } else if (i == 1) {
                    initialFreq = FC_FREQ_MAX / 2;
                } else if (i == 0) {
                    initialFreq = FC_FREQ_MAX / 4;
                } else {
                    continue;
                }
                for (int j = 0; j < ELAPSED_TIME_MAX; ++j) {
                    final float elapsedHours = j * ELAPSED_TIME_INTERVAL_HOURS;
                    final float freq = initialFreq
                            * (float)Math.pow(initialFreq, elapsedHours / HALF_LIFE_HOURS);
                    final int intFreq = Math.min(FC_FREQ_MAX, Math.max(0, (int)freq));
                    SCORE_TABLE[i][j] = intFreq;
                }
            }
        }
    }
}
