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
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.ScrollView;
import android.widget.Scroller;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.KeyDetector;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardView;
import com.android.inputmethod.latin.R;

/**
 * This is an extended {@link KeyboardView} class that hosts a vertical scroll keyboard.
 * Multi-touch unsupported. No {@link PointerTracker}s. No gesture support.
 * TODO: Vertical scroll capability should be removed from this class because it's no longer used.
 */
// TODO: Implement key popup preview.
public final class ScrollKeyboardView extends KeyboardView implements
        ScrollViewWithNotifier.ScrollListener, GestureDetector.OnGestureListener {
    private static final boolean PAGINATION = false;

    public interface OnKeyClickListener {
        public void onKeyClick(Key key);
    }

    private static final OnKeyClickListener EMPTY_LISTENER = new OnKeyClickListener() {
        @Override
        public void onKeyClick(final Key key) {}
    };

    private OnKeyClickListener mListener = EMPTY_LISTENER;
    private final KeyDetector mKeyDetector = new KeyDetector(0.0f /*keyHysteresisDistance */);
    private final GestureDetector mGestureDetector;

    private final Scroller mScroller;
    private ScrollViewWithNotifier mScrollView;

    public ScrollKeyboardView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.keyboardViewStyle);
    }

    public ScrollKeyboardView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        mGestureDetector = new GestureDetector(context, this);
        mGestureDetector.setIsLongpressEnabled(false /* isLongpressEnabled */);
        mScroller = new Scroller(context);
    }

    public void setScrollView(final ScrollViewWithNotifier scrollView) {
        mScrollView = scrollView;
        scrollView.setScrollListener(this);
    }

    private final Runnable mScrollTask = new Runnable() {
        @Override
        public void run() {
            final Scroller scroller = mScroller;
            final ScrollView scrollView = mScrollView;
            scroller.computeScrollOffset();
            scrollView.scrollTo(0, scroller.getCurrY());
            if (!scroller.isFinished()) {
                scrollView.post(this);
            }
        }
    };

    // {@link ScrollViewWithNotified#ScrollListener} methods.
    @Override
    public void notifyScrollChanged(final int scrollX, final int scrollY, final int oldX,
            final int oldY) {
        if (PAGINATION) {
            mScroller.forceFinished(true /* finished */);
            mScrollView.removeCallbacks(mScrollTask);
            final int currentTop = mScrollView.getScrollY();
            final int pageHeight = getKeyboard().mBaseHeight;
            final int lastPageNo = currentTop / pageHeight;
            final int lastPageTop = lastPageNo * pageHeight;
            final int nextPageNo = lastPageNo + 1;
            final int nextPageTop = Math.min(nextPageNo * pageHeight, getHeight() - pageHeight);
            final int scrollTo = (currentTop - lastPageTop) < (nextPageTop - currentTop)
                    ? lastPageTop : nextPageTop;
            final int deltaY = scrollTo - currentTop;
            mScroller.startScroll(0, currentTop, 0, deltaY, 300);
            mScrollView.post(mScrollTask);
        }
    }

    @Override
    public void notifyOverScrolled(final int scrollX, final int scrollY, final boolean clampedX,
            final boolean clampedY) {
        releaseCurrentKey();
    }

    public void setOnKeyClickListener(final OnKeyClickListener listener) {
        mListener = listener;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKeyboard(final Keyboard keyboard) {
        super.setKeyboard(keyboard);
        mKeyDetector.setKeyboard(keyboard, 0 /* correctionX */, 0 /* correctionY */);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onTouchEvent(final MotionEvent e) {
        if (mGestureDetector.onTouchEvent(e)) {
            return true;
        }
        final Key key = getKey(e);
        if (key != null && key != mCurrentKey) {
            releaseCurrentKey();
        }
        return true;
    }

    // {@link GestureDetector#OnGestureListener} methods.
    private Key mCurrentKey;

    private Key getKey(final MotionEvent e) {
        final int index = e.getActionIndex();
        final int x = (int)e.getX(index);
        final int y = (int)e.getY(index);
        return mKeyDetector.detectHitKey(x, y);
    }

    public void releaseCurrentKey() {
        final Key currentKey = mCurrentKey;
        if (currentKey == null) {
            return;
        }
        currentKey.onReleased();
        invalidateKey(currentKey);
        mCurrentKey = null;
    }

    @Override
    public boolean onDown(final MotionEvent e) {
        final Key key = getKey(e);
        releaseCurrentKey();
        mCurrentKey = key;
        if (key == null) {
            return false;
        }
        // TODO: May call {@link KeyboardActionListener#onPressKey(int,int,boolean)}.
        key.onPressed();
        invalidateKey(key);
        return false;
    }

    @Override
    public void onShowPress(final MotionEvent e) {
        // User feedback is done at {@link #onDown(MotionEvent)}.
    }

    @Override
    public boolean onSingleTapUp(final MotionEvent e) {
        final Key key = getKey(e);
        releaseCurrentKey();
        if (key == null) {
            return false;
        }
        // TODO: May call {@link KeyboardActionListener#onReleaseKey(int,boolean)}.
        key.onReleased();
        invalidateKey(key);
        mListener.onKeyClick(key);
        return true;
    }

    @Override
    public boolean onScroll(final MotionEvent e1, final MotionEvent e2, final float distanceX,
           final float distanceY) {
        releaseCurrentKey();
        return false;
    }

    @Override
    public boolean onFling(final MotionEvent e1, final MotionEvent e2, final float velocityX,
            final float velocityY) {
        releaseCurrentKey();
        return false;
    }

    @Override
    public void onLongPress(final MotionEvent e) {
        // Long press detection of {@link #mGestureDetector} is disabled and not used.
    }
}
