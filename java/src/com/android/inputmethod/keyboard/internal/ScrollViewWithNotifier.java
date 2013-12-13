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
import android.util.AttributeSet;
import android.widget.ScrollView;

/**
 * This is an extended {@link ScrollView} that can notify
 * {@link ScrollView#onScrollChanged(int,int,int,int} and
 * {@link ScrollView#onOverScrolled(int,int,int,int)} to a content view.
 */
public class ScrollViewWithNotifier extends ScrollView {
    private ScrollListener mScrollListener = EMPTY_LISTER;

    public interface ScrollListener {
        public void notifyScrollChanged(int scrollX, int scrollY, int oldX, int oldY);
        public void notifyOverScrolled(int scrollX, int scrollY, boolean clampedX,
                boolean clampedY);
    }

    private static final ScrollListener EMPTY_LISTER = new ScrollListener() {
        @Override
        public void notifyScrollChanged(int scrollX, int scrollY, int oldX, int oldY) {}
        @Override
        public void notifyOverScrolled(int scrollX, int scrollY, boolean clampedX,
                boolean clampedY) {}
    };

    public ScrollViewWithNotifier(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onScrollChanged(final int scrollX, final int scrollY, final int oldX,
            final int oldY) {
        super.onScrollChanged(scrollX, scrollY, oldX, oldY);
        mScrollListener.notifyScrollChanged(scrollX, scrollY, oldX, oldY);
    }

    @Override
    protected void onOverScrolled(final int scrollX, final int scrollY, final boolean clampedX,
            final boolean clampedY) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY);
        mScrollListener.notifyOverScrolled(scrollX, scrollY, clampedX, clampedY);
    }

    public void setScrollListener(final ScrollListener listener) {
        mScrollListener = listener;
    }
}
