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

import java.util.LinkedList;

/**
 * A buffer that holds a fixed number of LogUnits.
 *
 * LogUnits are added in and shifted out in temporal order.  Only a subset of the LogUnits are
 * actual words; the other LogUnits do not count toward the word limit.  Once the buffer reaches
 * capacity, adding another LogUnit that is a word evicts the oldest LogUnits out one at a time to
 * stay under the capacity limit.
 *
 * This variant of a LogBuffer has a limited memory footprint because of its limited size.  This
 * makes it useful, for example, for recording a window of the user's most recent actions in case
 * they want to report an observed error that they do not know how to reproduce.
 */
public class FixedLogBuffer extends LogBuffer {
    /* package for test */ int mWordCapacity;
    // The number of members of mLogUnits that are actual words.
    private int mNumActualWords;

    /**
     * Create a new LogBuffer that can hold a fixed number of LogUnits that are words (and
     * unlimited number of non-word LogUnits), and that outputs its result to a researchLog.
     *
     * @param wordCapacity maximum number of words
     */
    public FixedLogBuffer(final int wordCapacity) {
        super();
        if (wordCapacity <= 0) {
            throw new IllegalArgumentException("wordCapacity must be 1 or greater.");
        }
        mWordCapacity = wordCapacity;
        mNumActualWords = 0;
    }

    protected int getNumActualWords() {
        return mNumActualWords;
    }

    /**
     * Adds a new LogUnit to the front of the LIFO queue, evicting existing LogUnit's
     * (oldest first) if word capacity is reached.
     */
    @Override
    public void shiftIn(final LogUnit newLogUnit) {
        if (newLogUnit.getWord() == null) {
            // This LogUnit isn't a word, so it doesn't count toward the word-limit.
            super.shiftIn(newLogUnit);
            return;
        }
        if (mNumActualWords == mWordCapacity) {
            shiftOutThroughFirstWord();
        }
        super.shiftIn(newLogUnit);
        mNumActualWords++; // Must be a word, or we wouldn't be here.
    }

    @Override
    public LogUnit unshiftIn() {
        final LogUnit logUnit = super.unshiftIn();
        if (logUnit != null && logUnit.hasWord()) {
            mNumActualWords--;
        }
        return logUnit;
    }

    public void shiftOutThroughFirstWord() {
        final LinkedList<LogUnit> logUnits = getLogUnits();
        while (!logUnits.isEmpty()) {
            final LogUnit logUnit = logUnits.removeFirst();
            onShiftOut(logUnit);
            if (logUnit.hasWord()) {
                // Successfully shifted out a word-containing LogUnit and made space for the new
                // LogUnit.
                mNumActualWords--;
                break;
            }
        }
    }

    /**
     * Removes all LogUnits from the buffer without calling onShiftOut().
     */
    @Override
    public void clear() {
        super.clear();
        mNumActualWords = 0;
    }

    /**
     * Called when a LogUnit is removed from the LogBuffer as a result of a shiftIn.  LogUnits are
     * removed in the order entered.  This method is not called when shiftOut is called directly.
     *
     * Base class does nothing; subclasses may override if they want to record non-privacy sensitive
     * events that fall off the end.
     */
    protected void onShiftOut(final LogUnit logUnit) {
    }

    /**
     * Called to deliberately remove the oldest LogUnit.  Usually called when draining the
     * LogBuffer.
     */
    @Override
    public LogUnit shiftOut() {
        if (isEmpty()) {
            return null;
        }
        final LogUnit logUnit = super.shiftOut();
        if (logUnit.hasWord()) {
            mNumActualWords--;
        }
        return logUnit;
    }
}
