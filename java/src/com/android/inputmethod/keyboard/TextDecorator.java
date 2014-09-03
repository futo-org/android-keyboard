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
import android.graphics.PointF;
import android.graphics.RectF;
import android.inputmethodservice.InputMethodService;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.CursorAnchorInfo;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.compat.CursorAnchorInfoCompatWrapper;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.utils.LeakGuardHandlerWrapper;

import javax.annotation.Nonnull;

/**
 * A controller class of commit/add-to-dictionary indicator (a.k.a. TextDecorator). This class
 * is designed to be independent of UI subsystems such as {@link View}. All the UI related
 * operations are delegated to {@link TextDecoratorUi} via {@link TextDecoratorUiOperator}.
 */
public class TextDecorator {
    private static final String TAG = TextDecorator.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final int MODE_NONE = 0;
    private static final int MODE_COMMIT = 1;
    private static final int MODE_ADD_TO_DICTIONARY = 2;

    private int mMode = MODE_NONE;

    private final PointF mLocalOrigin = new PointF();
    private final RectF mRelativeIndicatorBounds = new RectF();
    private final RectF mRelativeComposingTextBounds = new RectF();

    private boolean mIsFullScreenMode = false;
    private SuggestedWordInfo mWaitingWord = null;
    private CursorAnchorInfoCompatWrapper mCursorAnchorInfoWrapper = null;

    @Nonnull
    private final Listener mListener;

    @Nonnull
    private TextDecoratorUiOperator mUiOperator = EMPTY_UI_OPERATOR;

    public interface Listener {
        /**
         * Called when the user clicks the composing text to commit.
         * @param wordInfo the suggested word which the user clicked on.
         */
        void onClickComposingTextToCommit(final SuggestedWordInfo wordInfo);

        /**
         * Called when the user clicks the composing text to add the word into the dictionary.
         * @param wordInfo the suggested word which the user clicked on.
         */
        void onClickComposingTextToAddToDictionary(final SuggestedWordInfo wordInfo);
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
     * Shows the "Commit" indicator and associates it with the given suggested word.
     *
     * <p>The effect of {@link #showCommitIndicator(SuggestedWordInfo)} and
     * {@link #showAddToDictionaryIndicator(SuggestedWordInfo)} are exclusive to each other. Call
     * {@link #reset()} to hide the indicator.</p>
     *
     * @param wordInfo the suggested word which should be associated with the indicator. This object
     * will be passed back in {@link Listener#onClickComposingTextToCommit(SuggestedWordInfo)}
     */
    public void showCommitIndicator(final SuggestedWordInfo wordInfo) {
        if (mMode == MODE_COMMIT && wordInfo != null &&
                TextUtils.equals(mWaitingWord.mWord, wordInfo.mWord)) {
            // Skip layout for better performance.
            return;
        }
        mWaitingWord = wordInfo;
        mMode = MODE_COMMIT;
        layoutLater();
    }

    /**
     * Shows the "Add to dictionary" indicator and associates it with associating the given
     * suggested word.
     *
     * <p>The effect of {@link #showCommitIndicator(SuggestedWordInfo)} and
     * {@link #showAddToDictionaryIndicator(SuggestedWordInfo)} are exclusive to each other. Call
     * {@link #reset()} to hide the indicator.</p>
     *
     * @param wordInfo the suggested word which should be associated with the indicator. This object
     * will be passed back in
     * {@link Listener#onClickComposingTextToAddToDictionary(SuggestedWordInfo)}.
     */
    public void showAddToDictionaryIndicator(final SuggestedWordInfo wordInfo) {
        if (mMode == MODE_ADD_TO_DICTIONARY && wordInfo != null &&
                TextUtils.equals(mWaitingWord.mWord, wordInfo.mWord)) {
            // Skip layout for better performance.
            return;
        }
        mWaitingWord = wordInfo;
        mMode = MODE_ADD_TO_DICTIONARY;
        layoutLater();
        return;
    }

    /**
     * Must be called when the input method is about changing to for from the full screen mode.
     * @param fullScreenMode {@code true} if the input method is entering the full screen mode.
     * {@code false} is the input method is finishing the full screen mode.
     */
    public void notifyFullScreenMode(final boolean fullScreenMode) {
        final boolean currentFullScreenMode = mIsFullScreenMode;
        if (!currentFullScreenMode && fullScreenMode) {
            // Currently full screen mode is not supported.
            // TODO: Support full screen mode.
            mUiOperator.hideUi();
        }
        mIsFullScreenMode = fullScreenMode;
    }

    /**
     * Resets previous requests and makes indicator invisible.
     */
    public void reset() {
        mWaitingWord = null;
        mMode = MODE_NONE;
        mLocalOrigin.set(0.0f, 0.0f);
        mRelativeIndicatorBounds.set(0.0f, 0.0f, 0.0f, 0.0f);
        mRelativeComposingTextBounds.set(0.0f, 0.0f, 0.0f, 0.0f);
        cancelLayoutInternalExpectedly("Resetting internal state.");
    }

    /**
     * Must be called when the {@link InputMethodService#onUpdateCursorAnchorInfo()} is called.
     *
     * <p>CAVEAT: Currently the input method author is responsible for ignoring
     * {@link InputMethodService#onUpdateCursorAnchorInfo()} called in full screen mode.</p>
     * @param info the compatibility wrapper object for the received {@link CursorAnchorInfo}.
     */
    public void onUpdateCursorAnchorInfo(final CursorAnchorInfoCompatWrapper info) {
        if (mIsFullScreenMode) {
            // TODO: Consider to call InputConnection#requestCursorAnchorInfo to disable the
            // event callback to suppress unnecessary event callbacks.
            return;
        }
        mCursorAnchorInfoWrapper = info;
        // Do not use layoutLater() to minimize the latency.
        layoutImmediately();
    }

    /**
     * Hides indicator if the new composing text doesn't match the expected one.
     *
     * <p>Calling this method is optional but recommended whenever the new composition is passed to
     * the application. The motivation of this method is to reduce the UI latency. With this method,
     * we can hide the indicator without waiting the arrival of the
     * {@link InputMethodService#onUpdateCursorAnchorInfo(CursorAnchorInfo)} callback, assuming that
     * the application accepts the new composing text without any modification. Even if this
     * assumption is false, the indicator will be shown again when
     * {@link InputMethodService#onUpdateCursorAnchorInfo(CursorAnchorInfo)} is actually received.
     * </p>
     *
     * @param newComposingText the new composing text that is being passed to the application.
     */
    public void hideIndicatorIfNecessary(final CharSequence newComposingText) {
        if (mMode != MODE_COMMIT && mMode != MODE_ADD_TO_DICTIONARY) {
            return;
        }
        if (!TextUtils.equals(newComposingText, mWaitingWord.mWord)) {
            mUiOperator.hideUi();
        }
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
        if (mIsFullScreenMode) {
            cancelLayoutInternalUnexpectedly("Full screen mode isn't yet supported.");
            return;
        }

        if (mMode != MODE_COMMIT && mMode != MODE_ADD_TO_DICTIONARY) {
            if (mMode == MODE_NONE) {
                cancelLayoutInternalExpectedly("Not ready for layouting.");
            } else {
                cancelLayoutInternalUnexpectedly("Unknown mMode=" + mMode);
            }
            return;
        }

        final CursorAnchorInfoCompatWrapper info = mCursorAnchorInfoWrapper;

        if (info == null) {
            cancelLayoutInternalExpectedly("CursorAnchorInfo isn't available.");
            return;
        }

        final Matrix matrix = info.getMatrix();
        if (matrix == null) {
            cancelLayoutInternalUnexpectedly("Matrix is null");
        }

        final CharSequence composingText = info.getComposingText();
        if (mMode == MODE_COMMIT) {
            if (composingText == null) {
                cancelLayoutInternalExpectedly("composingText is null.");
                return;
            }
            final int composingTextStart = info.getComposingTextStart();
            final int lastCharRectIndex = composingTextStart + composingText.length() - 1;
            final RectF lastCharRect = info.getCharacterRect(lastCharRectIndex);
            final int lastCharRectFlag = info.getCharacterRectFlags(lastCharRectIndex);
            final boolean hasInvisibleRegionInLastCharRect =
                    (lastCharRectFlag & CursorAnchorInfoCompatWrapper.FLAG_HAS_INVISIBLE_REGION)
                            != 0;
            if (lastCharRect == null || matrix == null || hasInvisibleRegionInLastCharRect) {
                mUiOperator.hideUi();
                return;
            }
            final RectF segmentStartCharRect = new RectF(lastCharRect);
            for (int i = composingText.length() - 2; i >= 0; --i) {
                final RectF charRect = info.getCharacterRect(composingTextStart + i);
                if (charRect == null) {
                    break;
                }
                if (charRect.top != segmentStartCharRect.top) {
                    break;
                }
                if (charRect.bottom != segmentStartCharRect.bottom) {
                    break;
                }
                segmentStartCharRect.set(charRect);
            }

            mLocalOrigin.set(lastCharRect.right, lastCharRect.top);
            mRelativeIndicatorBounds.set(lastCharRect.right, lastCharRect.top,
                    lastCharRect.right + lastCharRect.height(), lastCharRect.bottom);
            mRelativeIndicatorBounds.offset(-mLocalOrigin.x, -mLocalOrigin.y);

            mRelativeIndicatorBounds.set(lastCharRect.right, lastCharRect.top,
                    lastCharRect.right + lastCharRect.height(), lastCharRect.bottom);
            mRelativeIndicatorBounds.offset(-mLocalOrigin.x, -mLocalOrigin.y);

            mRelativeComposingTextBounds.set(segmentStartCharRect.left, segmentStartCharRect.top,
                    segmentStartCharRect.right, segmentStartCharRect.bottom);
            mRelativeComposingTextBounds.offset(-mLocalOrigin.x, -mLocalOrigin.y);

            if (mWaitingWord == null) {
                cancelLayoutInternalExpectedly("mWaitingText is null.");
                return;
            }
            if (TextUtils.isEmpty(mWaitingWord.mWord)) {
                cancelLayoutInternalExpectedly("mWaitingText.mWord is empty.");
                return;
            }
            if (!TextUtils.equals(composingText, mWaitingWord.mWord)) {
                // This is indeed an expected situation because of the asynchronous nature of
                // input method framework in Android. Note that composingText is notified from the
                // application, while mWaitingWord.mWord is obtained directly from the InputLogic.
                cancelLayoutInternalExpectedly(
                        "Composing text doesn't match the one we are waiting for.");
                return;
            }
        } else {
            if (!TextUtils.isEmpty(composingText)) {
                // This is an unexpected case.
                // TODO: Document this.
                mUiOperator.hideUi();
                return;
            }
            // In MODE_ADD_TO_DICTIONARY, we cannot retrieve the character position at all because
            // of the lack of composing text. We will use the insertion marker position instead.
            if ((info.getInsertionMarkerFlags() &
                    CursorAnchorInfoCompatWrapper.FLAG_HAS_INVISIBLE_REGION) != 0) {
                mUiOperator.hideUi();
                return;
            }
            final float insertionMarkerHolizontal = info.getInsertionMarkerHorizontal();
            final float insertionMarkerTop = info.getInsertionMarkerTop();
            mLocalOrigin.set(insertionMarkerHolizontal, insertionMarkerTop);
        }

        final RectF indicatorBounds = new RectF(mRelativeIndicatorBounds);
        final RectF composingTextBounds = new RectF(mRelativeComposingTextBounds);
        indicatorBounds.offset(mLocalOrigin.x, mLocalOrigin.y);
        composingTextBounds.offset(mLocalOrigin.x, mLocalOrigin.y);
        mUiOperator.layoutUi(mMode == MODE_COMMIT, matrix, indicatorBounds, composingTextBounds);
    }

    private void onClickIndicator() {
        if (mWaitingWord == null || TextUtils.isEmpty(mWaitingWord.mWord)) {
            return;
        }
        switch (mMode) {
            case MODE_COMMIT:
                mListener.onClickComposingTextToCommit(mWaitingWord);
                break;
            case MODE_ADD_TO_DICTIONARY:
                mListener.onClickComposingTextToAddToDictionary(mWaitingWord);
                break;
        }
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
        public void onClickComposingTextToCommit(SuggestedWordInfo wordInfo) {
        }
        @Override
        public void onClickComposingTextToAddToDictionary(SuggestedWordInfo wordInfo) {
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
        public void layoutUi(boolean isCommitMode, Matrix matrix, RectF indicatorBounds,
                RectF composingTextBounds) {
        }
    };
}
