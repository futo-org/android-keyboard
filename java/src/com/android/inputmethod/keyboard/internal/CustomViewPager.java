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

package com.android.inputmethod.keyboard.internal;

import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

/**
 * Custom view pager to prevent {@link ViewPager} from crashing while handling multi-touch
 * event.
 */
public class CustomViewPager extends ViewPager {
    private static final String TAG = CustomViewPager.class.getSimpleName();

    public CustomViewPager(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent event) {
        // This only happens when you multi-touch, take the first finger off and move.
        // Unfortunately this causes {@link ViewPager} to crash, so we will ignore such events.
        if (event.getAction() == MotionEvent.ACTION_MOVE && event.getPointerId(0) != 0) {
            Log.w(TAG, "Ignored multi-touch move event to prevent ViewPager from crashing");
            return false;
        }

        return super.onInterceptTouchEvent(event);
    }
}
