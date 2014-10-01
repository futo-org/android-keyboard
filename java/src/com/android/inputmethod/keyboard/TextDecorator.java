/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.inputmethod.keyboard;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.inputmethodservice.InputMethodService;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.CursorAnchorInfo;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.compat.CursorAnchorInfoCompatWrapper;
import com.android.inputmethod.latin.utils.LeakGuardHandlerWrapper;

import javax.annotation.Nonnull;

/**
 * A controller class of the add-to-dictionary indicator (a.k.a. TextDecorator). This class
 * is designed to be independent of UI subsystems such as {@link View}. All the UI related
 * operations are delegated to {@link TextDecoratorUi} via {@link TextDecoratorUiOperator}.
 */
public class TextDecorator {
    private static final String TAG = TextDecorator.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final int INVALID_CURSOR_INDEX = -1;

    private static final int MODE_MONITOR = 0;
    private static final int MODE_WAITING_CURSOR_INDEX = 1;
    private static final int MODE_SHOWING_INDICATOR = 2;

    private int mMode = MODE_MONITOR;

    private String mLastComposingText = null;
    private boolean mHasRtlCharsInLastComposingText = false;
    private RectF mComposingTextBoundsForLastComposingText = new RectF();

    private boolean mIsFullScreenMode = false;
    private String mWaitingWord = null;
    private int mWaitingCursorStart = INVALID_CURSOR_INDEX;
    private int mWaitingCursorEnd = INVALID_CURSOR_INDEX;
    private CursorAnchorInfoCompatWrapper mCursorAnchorInfoWrapper = null;

    @Nonnull
    private final Listener mListener;

    @Nonnull
    private TextDecoratorUiOperator mUiOperator = EMPTY_UI_OPERATOR;

    public interface Listener {
        /**
         * Called when the user clicks the indicator to add the word into the dictionary.
         * @param word the word which the user clicked on.
         */
        void onClickComposingTextToAddToDictionary(final String word);
    }

    public TextDecorator(final Listener listener) {
        mListener = (listener != null) ? listener : EMPTY_LISTENER;
    }

    /**
     * Sets the UI operator for {@link TextDecorator}. Any user visible operations will be
     * delegated to the associated UI operator.
     * @param uiOperator the UI operator to be associated.
     */
    public void setUiOperator(final TextDecoratorUiOperator uiOperator) {
        mUiOperator.disposeUi();
        mUiOperator = uiOperator;
        mUiOperator.setOnClickListener(getOnClickHandler());
    }

    private final Runnable mDefaultOnClickHandler = new Runnable() {
        @Override
        public void run() {
            onClickIndicator();
        }
    };

    @UsedForTesting
    final Runnable getOnClickHandler() {
        return mDefaultOnClickHandler;
    }

    /**
     * Shows the "Add to dictionary" indicator and associates it with associating the given word.
     *
     * @param word the word which should be associated with the indicator. This object will be
     * passed back in {@link Listener#onClickComposingTextToAddToDictionary(String)}.
     * @param selectionStart the cursor index (inclusive) when the indicator should be displayed.
     * @param selectionEnd the cursor index (exclusive) when the indicator should be displayed.
     */
    public void showAddToDictionaryIndicator(final String word, final int selectionStart,
            final int selectionEnd) {
        mWaitingWord = word;
        mWaitingCursorStart = selectionStart;
        mWaitingCursorEnd = selectionEnd;
        mMode = MODE_WAITING_CURSOR_INDEX;
        layoutLater();
        return;
    }

    /**
     * Must be called when the input method is about changing to for from the full screen mode.
     * @param fullScreenMode {@code true} if the input method is entering the full screen mode.
     * {@code false} is the input method is finishing the full screen mode.
     */
    public void notifyFullScreenMode(final boolean fullScreenMode) {
        final boolean fullScreenModeChanged = (mIsFullScreenMode != fullScreenMode);
        mIsFullScreenMode = fullScreenMode;
        if (fullScreenModeChanged) {
            layoutLater();
        }
    }

    /**
     * Resets previous requests and makes indicator invisible.
     */
    public void reset() {
        mWaitingWord = null;
        mMode = MODE_MONITOR;
        mWaitingCursorStart = INVALID_CURSOR_INDEX;
        mWaitingCursorEnd = INVALID_CURSOR_INDEX;
        cancelLayoutInternalExpectedly("Resetting internal state.");
    }

    /**
     * Must be called when the {@link InputMethodService#onUpdateCursorAnchorInfo(CursorAnchorInfo)}
     * is called.
     *
     * <p>CAVEAT: Currently the input method author is responsible for ignoring
     * {@link InputMethodService#onUpdateCursorAnchorInfo(CursorAnchorInfo)} called in full screen
     * mode.</p>
     * @param info the compatibility wrapper object for the received {@link CursorAnchorInfo}.
     */
    public void onUpdateCursorAnchorInfo(final CursorAnchorInfoCompatWrapper info) {
        mCursorAnchorInfoWrapper = info;
        // Do not use layoutLater() to minimize the latency.
        layoutImmediately();
    }

    private void cancelLayoutInternalUnexpectedly(final String message) {
        mUiOperator.hideUi();
        Log.d(TAG, message);
    }

    private void cancelLayoutInternalExpectedly(final String message) {
        mUiOperator.hideUi();
        if (DEBUG) {
            Log.d(TAG, message);
        }
    }

    private void layoutLater() {
        mLayoutInvalidator.invalidateLayout();
    }


    private void layoutImmediately() {
        // Clear pending layout requests.
        mLayoutInvalidator.cancelInvalidateLayout();
        layoutMain();
    }

    private void layoutMain() {
        final CursorAnchorInfoCompatWrapper info = mCursorAnchorInfoWrapper;

        if (info == null || !info.isAvailable()) {
            cancelLayoutInternalExpectedly("CursorAnchorInfo isn't available.");
            return;
        }

        final Matrix matrix = info.getMatrix();
        if (matrix == null) {
            cancelLayoutInternalUnexpectedly("Matrix is null");
        }

        final CharSequence composingText = info.getComposingText();
        if (!TextUtils.isEmpty(composingText)) {
            final int composingTextStart = info.getComposingTextStart();
            final int lastCharRectIndex = composingTextStart + composingText.length() - 1;
            final RectF lastCharRect = info.getCharacterBounds(lastCharRectIndex);
            final int lastCharRectFlags = info.getCharacterBoundsFlags(lastCharRectIndex);
            final boolean hasInvisibleRegionInLastCharRect =
                    (lastCharRectFlags & CursorAnchorInfoCompatWrapper.FLAG_HAS_INVISIBLE_REGION)
                            != 0;
            if (lastCharRect == null || matrix == null || hasInvisibleRegionInLastCharRect) {
                mUiOperator.hideUi();
                return;
            }

            // Note that the following layout information is fragile, and must be invalidated
            // even when surrounding text next to the composing text is changed because it can
            // affect how the composing text is rendered.
            // TODO: Investigate if we can change the input logic to make the target text
            // composing state so that we can retrieve the character bounds reliably.
            final String composingTextString = composingText.toString();
            final float top = lastCharRect.top;
            final float bottom = lastCharRect.bottom;
            float left = lastCharRect.left;
            float right = lastCharRect.right;
            boolean useRtlLayout = false;
            for (int i = composingText.length() - 1; i >= 0; --i) {
                final int characterIndex = composingTextStart + i;
                final RectF characterBounds = info.getCharacterBounds(characterIndex);
                final int characterBoundsFlags = info.getCharacterBoundsFlags(characterIndex);
                if (characterBounds == null) {
                    break;
                }
                if (characterBounds.top != top) {
                    break;
                }
                if (characterBounds.bottom != bottom) {
                    break;
                }
                if ((characterBoundsFlags & CursorAnchorInfoCompatWrapper.FLAG_IS_RTL) != 0) {
                    // This is for both RTL text and bi-directional text. RTL languages usually mix
                    // RTL characters with LTR characters and in this case we should display the
                    // indicator on the left, while in LTR languages that normally never happens.
                    // TODO: Try to come up with a better algorithm.
                    useRtlLayout = true;
                }
                left = Math.min(characterBounds.left, left);
                right = Math.max(characterBounds.right, right);
            }
            mLastComposingText = composingTextString;
            mHasRtlCharsInLastComposingText = useRtlLayout;
            mComposingTextBoundsForLastComposingText.set(left, top, right, bottom);
        }

        final int selectionStart = info.getSelectionStart();
        final int selectionEnd = info.getSelectionEnd();
        switch (mMode) {
            case MODE_MONITOR:
                mUiOperator.hideUi();
                return;
            case MODE_WAITING_CURSOR_INDEX:
                if (selectionStart != mWaitingCursorStart || selectionEnd != mWaitingCursorEnd) {
                    mUiOperator.hideUi();
                    return;
                }
                mMode = MODE_SHOWING_INDICATOR;
                break;
            case MODE_SHOWING_INDICATOR:
                if (selectionStart != mWaitingCursorStart || selectionEnd != mWaitingCursorEnd) {
                    mUiOperator.hideUi();
                    mMode = MODE_MONITOR;
                    mWaitingCursorStart = INVALID_CURSOR_INDEX;
                    mWaitingCursorEnd = INVALID_CURSOR_INDEX;
                    return;
                }
                break;
            default:
                cancelLayoutInternalUnexpectedly("Unexpected internal mode=" + mMode);
                return;
        }

        if (!TextUtils.equals(mLastComposingText, mWaitingWord)) {
            cancelLayoutInternalUnexpectedly("mLastComposingText doesn't match mWaitingWord");
            return;
        }

        if ((info.getInsertionMarkerFlags() &
                CursorAnchorInfoCompatWrapper.FLAG_HAS_INVISIBLE_REGION) != 0) {
            mUiOperator.hideUi();
            return;
        }

        mUiOperator.layoutUi(matrix, mComposingTextBoundsForLastComposingText,
                mHasRtlCharsInLastComposingText);
    }

    private void onClickIndicator() {
        if (mMode != MODE_SHOWING_INDICATOR) {
            return;
        }
        mListener.onClickComposingTextToAddToDictionary(mWaitingWord);
    }

    private final LayoutInvalidator mLayoutInvalidator = new LayoutInvalidator(this);

    /**
     * Used for managing pending layout tasks for {@link TextDecorator#layoutLater()}.
     */
    private static final class LayoutInvalidator {
        private final HandlerImpl mHandler;
        public LayoutInvalidator(final TextDecorator ownerInstance) {
            mHandler = new HandlerImpl(ownerInstance);
        }

        private static final int MSG_LAYOUT = 0;

        private static final class HandlerImpl
                extends LeakGuardHandlerWrapper<TextDecorator> {
            public HandlerImpl(final TextDecorator ownerInstance) {
                super(ownerInstance);
            }

            @Override
            public void handleMessage(final Message msg) {
                final TextDecorator owner = getOwnerInstance();
                if (owner == null) {
                    return;
                }
                switch (msg.what) {
                    case MSG_LAYOUT:
                        owner.layoutMain();
                        break;
                }
            }
        }

        /**
         * Puts a layout task into the scheduler. Does nothing if one or more layout tasks are
         * already scheduled.
         */
        public void invalidateLayout() {
            if (!mHandler.hasMessages(MSG_LAYOUT)) {
                mHandler.obtainMessage(MSG_LAYOUT).sendToTarget();
            }
        }

        /**
         * Clears the pending layout tasks.
         */
        public void cancelInvalidateLayout() {
            mHandler.removeMessages(MSG_LAYOUT);
        }
    }

    private final static Listener EMPTY_LISTENER = new Listener() {
        @Override
        public void onClickComposingTextToAddToDictionary(final String word) {
        }
    };

    private final static TextDecoratorUiOperator EMPTY_UI_OPERATOR = new TextDecoratorUiOperator() {
        @Override
        public void disposeUi() {
        }
        @Override
        public void hideUi() {
        }
        @Override
        public void setOnClickListener(Runnable listener) {
        }
        @Override
        public void layoutUi(Matrix matrix, RectF composingTextBounds, boolean useRtlLayout) {
        }
    };
}
