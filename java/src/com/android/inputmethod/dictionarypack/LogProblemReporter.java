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
 * A very simple problem reporter.
 */
final class LogProblemReporter implements ProblemReporter {
    private final String TAG;

    public LogProblemReporter(final String tag) {
        TAG = tag;
    }

    @Override
    public void report(final Exception e) {
        Log.e(TAG, "Reporting problem", e);
    }
}
