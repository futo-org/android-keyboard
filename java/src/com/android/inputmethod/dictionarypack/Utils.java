/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.inputmethod.dictionarypack;

import android.util.Log;

/**
 * A class for various utility methods, especially debugging.
 */
public final class Utils {
    private final static String TAG = Utils.class.getSimpleName() + ":DEBUG --";
    private final static boolean DEBUG = DictionaryProvider.DEBUG;

    /**
     * Calls .toString() on its non-null argument or returns "null"
     * @param o the object to convert to a string
     * @return the result of .toString() or null
     */
    public static String s(final Object o) {
        return null == o ? "null" : o.toString();
    }

    /**
     * Get the string representation of the current stack trace, for debugging purposes.
     * @return a readable, carriage-return-separated string for the current stack trace.
     */
    public static String getStackTrace() {
        final StringBuilder sb = new StringBuilder();
        try {
            throw new RuntimeException();
        } catch (RuntimeException e) {
            StackTraceElement[] frames = e.getStackTrace();
            // Start at 1 because the first frame is here and we don't care about it
            for (int j = 1; j < frames.length; ++j) {
                sb.append(frames[j].toString() + "\n");
            }
        }
        return sb.toString();
    }

    /**
     * Get the stack trace contained in an exception as a human-readable string.
     * @param e the exception
     * @return the human-readable stack trace
     */
    public static String getStackTrace(final Exception e) {
        final StringBuilder sb = new StringBuilder();
        final StackTraceElement[] frames = e.getStackTrace();
        for (int j = 0; j < frames.length; ++j) {
            sb.append(frames[j].toString() + "\n");
        }
        return sb.toString();
    }

    /**
     * Helper log method to ease null-checks and adding spaces.
     *
     * This sends all arguments to the log, separated by spaces. Any null argument is converted
     * to the "null" string. It uses a very visible tag and log level for debugging purposes.
     *
     * @param args the stuff to send to the log
     */
    public static void l(final Object... args) {
        if (!DEBUG) return;
        final StringBuilder sb = new StringBuilder();
        for (final Object o : args) {
            sb.append(s(o).toString());
            sb.append(" ");
        }
        Log.e(TAG, sb.toString());
    }

    /**
     * Helper log method to put stuff in red.
     *
     * This does the same as #l but prints in red
     *
     * @param args the stuff to send to the log
     */
    public static void r(final Object... args) {
        if (!DEBUG) return;
        final StringBuilder sb = new StringBuilder("\u001B[31m");
        for (final Object o : args) {
            sb.append(s(o).toString());
            sb.append(" ");
        }
        sb.append("\u001B[0m");
        Log.e(TAG, sb.toString());
    }
}
