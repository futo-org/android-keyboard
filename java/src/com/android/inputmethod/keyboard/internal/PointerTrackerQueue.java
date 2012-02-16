/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.keyboard.internal;

import android.util.Log;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.PointerTracker;

import java.util.Iterator;
import java.util.LinkedList;

public class PointerTrackerQueue {
    private static final String TAG = PointerTrackerQueue.class.getSimpleName();
    private static final boolean DEBUG = false;

    private final LinkedList<PointerTracker> mQueue = new LinkedList<PointerTracker>();

    public synchronized void add(PointerTracker tracker) {
        mQueue.add(tracker);
    }

    public synchronized void remove(PointerTracker tracker) {
        mQueue.remove(tracker);
    }

    public synchronized void releaseAllPointersOlderThan(PointerTracker tracker,
            long eventTime) {
        if (DEBUG) {
            Log.d(TAG, "releaseAllPoniterOlderThan: [" + tracker.mPointerId + "] " + this);
        }
        if (!mQueue.contains(tracker)) {
            return;
        }
        final Iterator<PointerTracker> it = mQueue.iterator();
        while (it.hasNext()) {
            final PointerTracker t = it.next();
            if (t == tracker) {
                break;
            }
            if (!t.isModifier()) {
                t.onPhantomUpEvent(t.getLastX(), t.getLastY(), eventTime);
                it.remove();
            }
        }
    }

    public void releaseAllPointers(long eventTime) {
        releaseAllPointersExcept(null, eventTime);
    }

    public synchronized void releaseAllPointersExcept(PointerTracker tracker, long eventTime) {
        if (DEBUG) {
            if (tracker == null) {
                Log.d(TAG, "releaseAllPoniters: " + this);
            } else {
                Log.d(TAG, "releaseAllPoniterExcept: [" + tracker.mPointerId + "] " + this);
            }
        }
        final Iterator<PointerTracker> it = mQueue.iterator();
        while (it.hasNext()) {
            final PointerTracker t = it.next();
            if (t != tracker) {
                t.onPhantomUpEvent(t.getLastX(), t.getLastY(), eventTime);
                it.remove();
            }
        }
    }

    public synchronized boolean isAnyInSlidingKeyInput() {
        for (final PointerTracker tracker : mQueue) {
            if (tracker.isInSlidingKeyInput()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (final PointerTracker tracker : mQueue) {
            if (sb.length() > 0)
                sb.append(" ");
            sb.append("[" + tracker.mPointerId + " "
                + Keyboard.printableCode(tracker.getKey().mCode) + "]");
        }
        return sb.toString();
    }
}
