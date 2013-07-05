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

package com.android.inputmethod.research;

import android.util.Log;

import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.define.ProductionFlag;

import java.util.concurrent.TimeUnit;

public class Statistics {
    private static final String TAG = Statistics.class.getSimpleName();
    private static final boolean DEBUG = false
            && ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS_DEBUG;

    // TODO: Cleanup comments to only including those giving meaningful information.
    // Number of characters entered during a typing session
    int mCharCount;
    // Number of letter characters entered during a typing session
    int mLetterCount;
    // Number of number characters entered
    int mNumberCount;
    // Number of space characters entered
    int mSpaceCount;
    // Number of delete operations entered (taps on the backspace key)
    int mDeleteKeyCount;
    // Number of words entered during a session.
    int mWordCount;
    // Number of words found in the dictionary.
    int mDictionaryWordCount;
    // Number of words split and spaces automatically entered.
    int mSplitWordsCount;
    // Number of words entered during a session.
    int mCorrectedWordsCount;
    // Number of gestures that were input.
    int mGesturesInputCount;
    // Number of gestures that were deleted.
    int mGesturesDeletedCount;
    // Total number of characters in words entered by gesture.
    int mGesturesCharsCount;
    // Number of manual suggestions chosen.
    int mManualSuggestionsCount;
    // Number of times that autocorrection was invoked.
    int mAutoCorrectionsCount;
    // Number of times a commit was reverted in this session.
    int mRevertCommitsCount;
    // Whether the text field was empty upon editing
    boolean mIsEmptyUponStarting;
    boolean mIsEmptinessStateKnown;

    // Counts of how often an n-gram is collected or not, and the reasons for the decision.
    // Keep consistent with publishability result code list in MainLogBuffer
    int mPublishableCount;
    int mUnpublishableStoppingCount;
    int mUnpublishableIncorrectWordCount;
    int mUnpublishableSampledTooRecently;
    int mUnpublishableDictionaryUnavailable;
    int mUnpublishableMayContainDigit;
    int mUnpublishableNotInDictionary;

    // Timers to count average time to enter a key, first press a delete key,
    // between delete keys, and then to return typing after a delete key.
    final AverageTimeCounter mKeyCounter = new AverageTimeCounter();
    final AverageTimeCounter mBeforeDeleteKeyCounter = new AverageTimeCounter();
    final AverageTimeCounter mDuringRepeatedDeleteKeysCounter = new AverageTimeCounter();
    final AverageTimeCounter mAfterDeleteKeyCounter = new AverageTimeCounter();

    static class AverageTimeCounter {
        int mCount;
        int mTotalTime;

        public void reset() {
            mCount = 0;
            mTotalTime = 0;
        }

        public void add(long deltaTime) {
            mCount++;
            mTotalTime += deltaTime;
        }

        public int getAverageTime() {
            if (mCount == 0) {
                return 0;
            }
            return mTotalTime / mCount;
        }
    }

    // To account for the interruptions when the user's attention is directed elsewhere, times
    // longer than MIN_TYPING_INTERMISSION are not counted when estimating this statistic.
    public static final long MIN_TYPING_INTERMISSION = TimeUnit.SECONDS.toMillis(2);
    public static final long MIN_DELETION_INTERMISSION = TimeUnit.SECONDS.toMillis(10);

    // The last time that a tap was performed
    private long mLastTapTime;
    // The type of the last keypress (delete key or not)
    boolean mIsLastKeyDeleteKey;

    private static final Statistics sInstance = new Statistics();

    public static Statistics getInstance() {
        return sInstance;
    }

    private Statistics() {
        reset();
    }

    public void reset() {
        mCharCount = 0;
        mLetterCount = 0;
        mNumberCount = 0;
        mSpaceCount = 0;
        mDeleteKeyCount = 0;
        mWordCount = 0;
        mDictionaryWordCount = 0;
        mSplitWordsCount = 0;
        mCorrectedWordsCount = 0;
        mGesturesInputCount = 0;
        mGesturesDeletedCount = 0;
        mManualSuggestionsCount = 0;
        mRevertCommitsCount = 0;
        mAutoCorrectionsCount = 0;
        mIsEmptyUponStarting = true;
        mIsEmptinessStateKnown = false;
        mKeyCounter.reset();
        mBeforeDeleteKeyCounter.reset();
        mDuringRepeatedDeleteKeysCounter.reset();
        mAfterDeleteKeyCounter.reset();
        mGesturesCharsCount = 0;
        mGesturesDeletedCount = 0;
        mPublishableCount = 0;
        mUnpublishableStoppingCount = 0;
        mUnpublishableIncorrectWordCount = 0;
        mUnpublishableSampledTooRecently = 0;
        mUnpublishableDictionaryUnavailable = 0;
        mUnpublishableMayContainDigit = 0;
        mUnpublishableNotInDictionary = 0;

        mLastTapTime = 0;
        mIsLastKeyDeleteKey = false;
    }

    public void recordChar(int codePoint, long time) {
        if (DEBUG) {
            Log.d(TAG, "recordChar() called");
        }
        if (codePoint == Constants.CODE_DELETE) {
            mDeleteKeyCount++;
            recordUserAction(time, true /* isDeletion */);
        } else {
            mCharCount++;
            if (Character.isDigit(codePoint)) {
                mNumberCount++;
            }
            if (Character.isLetter(codePoint)) {
                mLetterCount++;
            }
            if (Character.isSpaceChar(codePoint)) {
                mSpaceCount++;
            }
            recordUserAction(time, false /* isDeletion */);
        }
    }

    public void recordWordEntered(final boolean isDictionaryWord,
            final boolean containsCorrection) {
        mWordCount++;
        if (isDictionaryWord) {
            mDictionaryWordCount++;
        }
        if (containsCorrection) {
            mCorrectedWordsCount++;
        }
    }

    public void recordSplitWords() {
        mSplitWordsCount++;
    }

    public void recordGestureInput(final int numCharsEntered, final long time) {
        mGesturesInputCount++;
        mGesturesCharsCount += numCharsEntered;
        recordUserAction(time, false /* isDeletion */);
    }

    public void setIsEmptyUponStarting(final boolean isEmpty) {
        mIsEmptyUponStarting = isEmpty;
        mIsEmptinessStateKnown = true;
    }

    public void recordGestureDelete(final int length, final long time) {
        mGesturesDeletedCount++;
        recordUserAction(time, true /* isDeletion */);
    }

    public void recordManualSuggestion(final long time) {
        mManualSuggestionsCount++;
        recordUserAction(time, false /* isDeletion */);
    }

    public void recordAutoCorrection(final long time) {
        mAutoCorrectionsCount++;
        recordUserAction(time, false /* isDeletion */);
    }

    public void recordRevertCommit(final long time) {
        mRevertCommitsCount++;
        recordUserAction(time, true /* isDeletion */);
    }

    private void recordUserAction(final long time, final boolean isDeletion) {
        final long delta = time - mLastTapTime;
        if (isDeletion) {
            if (delta < MIN_DELETION_INTERMISSION) {
                if (mIsLastKeyDeleteKey) {
                    mDuringRepeatedDeleteKeysCounter.add(delta);
                } else {
                    mBeforeDeleteKeyCounter.add(delta);
                }
            } else {
                ResearchLogger.onUserPause(delta);
            }
        } else {
            if (mIsLastKeyDeleteKey && delta < MIN_DELETION_INTERMISSION) {
                mAfterDeleteKeyCounter.add(delta);
            } else if (!mIsLastKeyDeleteKey && delta < MIN_TYPING_INTERMISSION) {
                mKeyCounter.add(delta);
            } else {
                ResearchLogger.onUserPause(delta);
            }
        }
        mIsLastKeyDeleteKey = isDeletion;
        mLastTapTime = time;
    }

    public void recordPublishabilityResultCode(final int publishabilityResultCode) {
        // Keep consistent with publishability result code list in MainLogBuffer
        switch (publishabilityResultCode) {
        case MainLogBuffer.PUBLISHABILITY_PUBLISHABLE:
            mPublishableCount++;
            break;
        case MainLogBuffer.PUBLISHABILITY_UNPUBLISHABLE_STOPPING:
            mUnpublishableStoppingCount++;
            break;
        case MainLogBuffer.PUBLISHABILITY_UNPUBLISHABLE_INCORRECT_WORD_COUNT:
            mUnpublishableIncorrectWordCount++;
            break;
        case MainLogBuffer.PUBLISHABILITY_UNPUBLISHABLE_SAMPLED_TOO_RECENTLY:
            mUnpublishableSampledTooRecently++;
            break;
        case MainLogBuffer.PUBLISHABILITY_UNPUBLISHABLE_DICTIONARY_UNAVAILABLE:
            mUnpublishableDictionaryUnavailable++;
            break;
        case MainLogBuffer.PUBLISHABILITY_UNPUBLISHABLE_MAY_CONTAIN_DIGIT:
            mUnpublishableMayContainDigit++;
            break;
        case MainLogBuffer.PUBLISHABILITY_UNPUBLISHABLE_NOT_IN_DICTIONARY:
            mUnpublishableNotInDictionary++;
            break;
        }
    }
}
