/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.inputmethod.latin;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import com.android.inputmethod.keyboard.MainKeyboardView;
import com.android.inputmethod.latin.suggestions.MoreSuggestionsView;
import com.android.inputmethod.latin.suggestions.SuggestionStripView;

public final class InputView extends LinearLayout {
    private final Rect mInputViewRect = new Rect();
    private KeyboardTopPaddingForwarder mKeyboardTopPaddingForwarder;
    private MoreSuggestionsViewCanceler mMoreSuggestionsViewCanceler;

    public InputView(final Context context, final AttributeSet attrs) {
        super(context, attrs, 0);
    }

    @Override
    protected void onFinishInflate() {
        final SuggestionStripView suggestionStripView =
                (SuggestionStripView)findViewById(R.id.suggestion_strip_view);
        final MainKeyboardView mainKeyboardView =
                (MainKeyboardView)findViewById(R.id.keyboard_view);
        mKeyboardTopPaddingForwarder = new KeyboardTopPaddingForwarder(
                mainKeyboardView, suggestionStripView);
        mMoreSuggestionsViewCanceler = new MoreSuggestionsViewCanceler(
                mainKeyboardView, suggestionStripView);
    }

    public void setKeyboardTopPadding(final int keyboardTopPadding) {
        mKeyboardTopPaddingForwarder.setKeyboardTopPadding(keyboardTopPadding);
    }

    @Override
    public boolean dispatchTouchEvent(final MotionEvent me) {
        final Rect rect = mInputViewRect;
        getGlobalVisibleRect(rect);
        final int x = (int)me.getX() + rect.left;
        final int y = (int)me.getY() + rect.top;

        // The touch events that hit the top padding of keyboard should be
        // forwarded to {@link SuggestionStripView}.
        if (mKeyboardTopPaddingForwarder.dispatchTouchEvent(x, y, me)) {
            return true;
        }
        // To cancel {@link MoreSuggestionsView}, we should intercept a touch event to
        // {@link MainKeyboardView} and dismiss the {@link MoreSuggestionsView}.
        if (mMoreSuggestionsViewCanceler.dispatchTouchEvent(x, y, me)) {
            return true;
        }
        return super.dispatchTouchEvent(me);
    }

    /**
     * This class forwards series of {@link MotionEvent}s from <code>Forwarder</code> view to
     * <code>Receiver</code> view.
     *
     * @param <Sender> a {@link View} that may send a {@link MotionEvent} to <Receiver>.
     * @param <Receiver> a {@link View} that receives forwarded {@link MotionEvent} from
     *     <Forwarder>.
     */
    private static abstract class MotionEventForwarder<Sender extends View, Receiver extends View> {
        protected final Sender mSenderView;
        protected final Receiver mReceiverView;

        private boolean mIsForwardingEvent;
        protected final Rect mEventSendingRect = new Rect();
        protected final Rect mEventReceivingRect = new Rect();

        public MotionEventForwarder(final Sender senderView, final Receiver receiverView) {
            mSenderView = senderView;
            mReceiverView = receiverView;
        }

        // Return true if a touch event of global coordinate x, y needs to be forwarded.
        protected abstract boolean needsToForward(final int x, final int y);

        // Translate global x-coordinate to <code>Receiver</code> local coordinate.
        protected int translateX(final int x) {
            return x - mEventReceivingRect.left;
        }

        // Translate global y-coordinate to <code>Receiver</code> local coordinate.
        protected int translateY(final int y) {
            return y - mEventReceivingRect.top;
        }

        // Callback when a {@link MotionEvent} is forwarded.
        protected void onForwardingEvent(final MotionEvent me) {}

        // Dispatches a {@link MotioneEvent} to <code>Receiver</code> if needed and returns true.
        // Otherwise returns false.
        public boolean dispatchTouchEvent(final int x, final int y, final MotionEvent me) {
            // Forwards a {link MotionEvent} only if both <code>Sender</code> and
            // <code>Receiver</code> are visible.
            if (mSenderView.getVisibility() != View.VISIBLE ||
                    mReceiverView.getVisibility() != View.VISIBLE) {
                return false;
            }
            final Rect sendingRect = mEventSendingRect;
            mSenderView.getGlobalVisibleRect(sendingRect);
            if (!mIsForwardingEvent && !sendingRect.contains(x, y)) {
                return false;
            }

            boolean shouldForwardToReceiver = false;

            switch (me.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // If the down event happens in the forwarding area, successive {@link MotionEvent}s
                // should be forwarded.
                if (needsToForward(x, y)) {
                    mIsForwardingEvent = true;
                    shouldForwardToReceiver = true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                shouldForwardToReceiver = mIsForwardingEvent;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                shouldForwardToReceiver = mIsForwardingEvent;
                mIsForwardingEvent = false;
                break;
            }

            if (!shouldForwardToReceiver) {
                return false;
            }

            final Rect receivingRect = mEventReceivingRect;
            mReceiverView.getGlobalVisibleRect(receivingRect);
            // Translate global coordinates to <code>Receiver</code> local coordinates.
            me.setLocation(translateX(x), translateY(y));
            mReceiverView.dispatchTouchEvent(me);
            onForwardingEvent(me);
            return true;
        }
    }

    /**
     * This class forwards {@link MotionEvent}s happened in the top padding of
     * {@link MainKeyboardView} to {@link SuggestionStripView}.
     */
    private static class KeyboardTopPaddingForwarder
            extends MotionEventForwarder<MainKeyboardView, SuggestionStripView> {
        private int mKeyboardTopPadding;

        public KeyboardTopPaddingForwarder(final MainKeyboardView mainKeyboardView,
                final SuggestionStripView suggestionStripView) {
            super(mainKeyboardView, suggestionStripView);
        }

        public void setKeyboardTopPadding(final int keyboardTopPadding) {
            mKeyboardTopPadding = keyboardTopPadding;
        }

        private boolean isInKeyboardTopPadding(final int y) {
            return y < mEventSendingRect.top + mKeyboardTopPadding;
        }

        @Override
        protected boolean needsToForward(final int x, final int y) {
            return isInKeyboardTopPadding(y);
        }

        @Override
        protected int translateY(final int y) {
            final int translatedY = super.translateY(y);
            if (isInKeyboardTopPadding(y)) {
                // The forwarded event should have coordinates that are inside of
                // the target.
                return Math.min(translatedY, mEventReceivingRect.height() - 1);
            }
            return translatedY;
        }
    }

    /**
     * This class forwards {@link MotionEvent}s happened in the {@link MainKeyboardView} to
     * {@link SuggestionStripView} when the {@link MoreSuggestionsView} is showing.
     * {@link SuggestionStripView} dismisses {@link MoreSuggestionsView} when it receives those
     * events.
     */
    private static class MoreSuggestionsViewCanceler
            extends MotionEventForwarder<MainKeyboardView, SuggestionStripView> {
        public MoreSuggestionsViewCanceler(final MainKeyboardView mainKeyboardView,
                final SuggestionStripView suggestionStripView) {
            super(mainKeyboardView, suggestionStripView);
        }

        @Override
        protected boolean needsToForward(final int x, final int y) {
            return mReceiverView.isShowingMoreSuggestionPanel() && mEventSendingRect.contains(x, y);
        }

        @Override
        protected void onForwardingEvent(final MotionEvent me) {
            if (me.getActionMasked() == MotionEvent.ACTION_DOWN) {
                mReceiverView.dismissMoreSuggestionsPanel();
            }
        }
    }
}
