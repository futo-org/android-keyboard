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

package com.android.inputmethod.research;

import android.view.MotionEvent;

/* package */ class LoggingUtils {
    private LoggingUtils() {
        // This utility class is not publicly instantiable.
    }

    /* package */ static String getMotionEventActionTypeString(final int actionType) {
        switch (actionType) {
        case MotionEvent.ACTION_CANCEL: return "CANCEL";
        case MotionEvent.ACTION_UP: return "UP";
        case MotionEvent.ACTION_DOWN: return "DOWN";
        case MotionEvent.ACTION_POINTER_UP: return "POINTER_UP";
        case MotionEvent.ACTION_POINTER_DOWN: return "POINTER_DOWN";
        case MotionEvent.ACTION_MOVE: return "MOVE";
        case MotionEvent.ACTION_OUTSIDE: return "OUTSIDE";
        default: return "ACTION_" + actionType;
        }
    }
}
