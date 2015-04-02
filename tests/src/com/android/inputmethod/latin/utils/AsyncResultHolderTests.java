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

package com.android.inputmethod.latin.utils;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;

@MediumTest
public class AsyncResultHolderTests extends AndroidTestCase {
    static final String TAG = AsyncResultHolderTests.class.getSimpleName();

    private static final int TIMEOUT_IN_MILLISECONDS = 500;
    private static final int MARGIN_IN_MILLISECONDS = 250;
    private static final int DEFAULT_VALUE = 2;
    private static final int SET_VALUE = 1;

    private static <T> void setAfterGivenTime(final AsyncResultHolder<T> holder, final T value,
            final long time) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(time);
                } catch (InterruptedException e) {
                    Log.d(TAG, "Exception while sleeping", e);
                }
                holder.set(value);
            }
        }).start();
    }

    public void testGetWithoutSet() {
        final AsyncResultHolder<Integer> holder = new AsyncResultHolder<>("Test");
        final int resultValue = holder.get(DEFAULT_VALUE, TIMEOUT_IN_MILLISECONDS);
        assertEquals(DEFAULT_VALUE, resultValue);
    }

    public void testGetBeforeSet() {
        final AsyncResultHolder<Integer> holder = new AsyncResultHolder<>("Test");
        setAfterGivenTime(holder, SET_VALUE, TIMEOUT_IN_MILLISECONDS + MARGIN_IN_MILLISECONDS);
        final int resultValue = holder.get(DEFAULT_VALUE, TIMEOUT_IN_MILLISECONDS);
        assertEquals(DEFAULT_VALUE, resultValue);
    }

    public void testGetAfterSet() {
        final AsyncResultHolder<Integer> holder = new AsyncResultHolder<>("Test");
        holder.set(SET_VALUE);
        final int resultValue = holder.get(DEFAULT_VALUE, TIMEOUT_IN_MILLISECONDS);
        assertEquals(SET_VALUE, resultValue);
    }

    public void testGetBeforeTimeout() {
        final AsyncResultHolder<Integer> holder = new AsyncResultHolder<>("Test");
        setAfterGivenTime(holder, SET_VALUE, TIMEOUT_IN_MILLISECONDS - MARGIN_IN_MILLISECONDS);
        final int resultValue = holder.get(DEFAULT_VALUE, TIMEOUT_IN_MILLISECONDS);
        assertEquals(SET_VALUE, resultValue);
    }
}
