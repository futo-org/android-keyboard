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

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * An object that executes submitted tasks using a thread.
 */
public class PrioritizedSerialExecutor {
    public static final String TAG = PrioritizedSerialExecutor.class.getSimpleName();

    private final Object mLock = new Object();

    // The default value of capacities of task queues.
    private static final int TASK_QUEUE_CAPACITY = 1000;
    private final Queue<Runnable> mTasks;
    private final Queue<Runnable> mPrioritizedTasks;
    private boolean mIsShutdown;

    // The task which is running now.
    private Runnable mActive;

    public PrioritizedSerialExecutor() {
        mTasks = new ArrayDeque<Runnable>(TASK_QUEUE_CAPACITY);
        mPrioritizedTasks = new ArrayDeque<Runnable>(TASK_QUEUE_CAPACITY);
        mIsShutdown = false;
    }

    /**
     * Clears all queued tasks.
     */
    public void clearAllTasks() {
        synchronized(mLock) {
            mTasks.clear();
            mPrioritizedTasks.clear();
        }
    }

    /**
     * Enqueues the given task into the task queue.
     * @param r the enqueued task
     */
    public void execute(final Runnable r) {
        synchronized(mLock) {
            if (!mIsShutdown) {
                mTasks.offer(r);
                if (mActive == null) {
                    scheduleNext();
                }
            }
        }
    }

    /**
     * Enqueues the given task into the prioritized task queue.
     * @param r the enqueued task
     */
    public void executePrioritized(final Runnable r) {
        synchronized(mLock) {
            if (!mIsShutdown) {
                mPrioritizedTasks.offer(r);
                if (mActive ==  null) {
                    scheduleNext();
                }
            }
        }
    }

    private boolean fetchNextTasks() {
        synchronized(mLock) {
            mActive = mPrioritizedTasks.poll();
            if (mActive == null) {
                mActive = mTasks.poll();
            }
            return mActive != null;
        }
    }

    private void scheduleNext() {
        synchronized(mLock) {
            if (!fetchNextTasks()) {
                return;
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        do {
                            synchronized(mLock) {
                                if (mActive != null) {
                                    mActive.run();
                                }
                            }
                        } while (fetchNextTasks());
                    } finally {
                        scheduleNext();
                    }
                }
            }).start();
        }
    }

    public void remove(final Runnable r) {
        synchronized(mLock) {
            mTasks.remove(r);
            mPrioritizedTasks.remove(r);
        }
    }

    public void replaceAndExecute(final Runnable oldTask, final Runnable newTask) {
        synchronized(mLock) {
            if (oldTask != null) remove(oldTask);
            execute(newTask);
        }
    }

    public void shutdown() {
        synchronized(mLock) {
            mIsShutdown = true;
        }
    }

    public boolean isTerminated() {
        synchronized(mLock) {
            if (!mIsShutdown) {
                return false;
            }
            return mPrioritizedTasks.isEmpty() && mTasks.isEmpty() && mActive == null;
        }
    }
}
