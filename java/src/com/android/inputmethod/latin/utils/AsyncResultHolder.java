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

import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * This class is a holder of the result of an asynchronous computation.
 *
 * @param <E> the type of the result.
 */
public class AsyncResultHolder<E> {

    private final Object mLock = new Object();

    private E mResult;
    private final String mTag;
    private final CountDownLatch mLatch;

    public AsyncResultHolder(final String tag) {
        mTag = tag;
        mLatch = new CountDownLatch(1);
    }

    /**
     * Sets the result value of this holder.
     *
     * @param result the value to set.
     */
    public void set(final E result) {
        synchronized(mLock) {
            if (mLatch.getCount() > 0) {
                mResult = result;
                mLatch.countDown();
            }
        }
    }

    /**
     * Gets the result value held in this holder.
     * Causes the current thread to wait unless the value is set or the specified time is elapsed.
     *
     * @param defaultValue the default value.
     * @param timeOut the maximum time to wait.
     * @return if the result is set before the time limit then the result, otherwise defaultValue.
     */
    public E get(final E defaultValue, final long timeOut) {
        try {
            return mLatch.await(timeOut, TimeUnit.MILLISECONDS) ? mResult : defaultValue;
        } catch (InterruptedException e) {
            Log.w(mTag, "get() : Interrupted after " + timeOut + " ms");
            return defaultValue;
        }
    }
}
