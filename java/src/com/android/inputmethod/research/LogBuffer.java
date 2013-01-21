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
 * Maintain a FIFO queue of LogUnits.
 *
 * This class provides an unbounded queue.  This is useful when the user is aware that their actions
 * are being recorded, such as when they are trying to reproduce a bug.  In this case, there should
 * not be artificial restrictions on how many events that can be saved.
 */
public class LogBuffer {
    // TODO: Gracefully handle situations in which this LogBuffer is consuming too much memory.
    // This may happen, for example, if the user has forgotten that data is being logged.
    private final LinkedList<LogUnit> mLogUnits;

    public LogBuffer() {
        mLogUnits = new LinkedList<LogUnit>();
    }

    protected LinkedList<LogUnit> getLogUnits() {
        return mLogUnits;
    }

    public void clear() {
        mLogUnits.clear();
    }

    public void shiftIn(final LogUnit logUnit) {
        mLogUnits.add(logUnit);
    }

    public LogUnit unshiftIn() {
        if (mLogUnits.isEmpty()) {
            return null;
        }
        return mLogUnits.removeLast();
    }

    public LogUnit peekLastLogUnit() {
        if (mLogUnits.isEmpty()) {
            return null;
        }
        return mLogUnits.peekLast();
    }

    public boolean isEmpty() {
        return mLogUnits.isEmpty();
    }

    public LogUnit shiftOut() {
        if (isEmpty()) {
            return null;
        }
        return mLogUnits.removeFirst();
    }
}
