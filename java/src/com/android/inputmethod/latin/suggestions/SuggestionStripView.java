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

package com.android.inputmethod.latin.suggestions;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.keyboard.MainKeyboardView;
import com.android.inputmethod.keyboard.MoreKeysPanel;
import com.android.inputmethod.latin.AudioAndHapticFeedbackManager;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.define.ProductionFlag;
import com.android.inputmethod.latin.suggestions.MoreSuggestions.MoreSuggestionsListener;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.research.ResearchLogger;

import java.util.ArrayList;

public final class SuggestionStripView extends RelativeLayout implements OnClickListener,
        OnLongClickListener {
    public interface Listener {
        public void addWordToUserDictionary(String word);
        public void pickSuggestionManually(int index, SuggestedWordInfo word);
    }

    // The maximum number of suggestions available. See {@link Suggest#mPrefMaxSuggestions}.
    public static final int MAX_SUGGESTIONS = 18;

    static final boolean DBG = LatinImeLogger.sDBG;

    private final ViewGroup mSuggestionsStrip;
    MainKeyboardView mMainKeyboardView;

    private final View mMoreSuggestionsContainer;
    private final MoreSuggestionsView mMoreSuggestionsView;
    private final MoreSuggestions.Builder mMoreSuggestionsBuilder;

    private final ArrayList<TextView> mWordViews = CollectionUtils.newArrayList();
    private final ArrayList<TextView> mDebugInfoViews = CollectionUtils.newArrayList();
    private final ArrayList<View> mDividerViews = CollectionUtils.newArrayList();

    Listener mListener;
    private SuggestedWords mSuggestedWords = SuggestedWords.EMPTY;

    private final SuggestionStripLayoutHelper mLayoutHelper;

    /**
     * Construct a {@link SuggestionStripView} for showing suggestions to be picked by the user.
     * @param context
     * @param attrs
     */
    public SuggestionStripView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.suggestionStripViewStyle);
    }

    public SuggestionStripView(final Context context, final AttributeSet attrs,
            final int defStyle) {
        super(context, attrs, defStyle);

        final LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.suggestions_strip, this);

        mSuggestionsStrip = (ViewGroup)findViewById(R.id.suggestions_strip);
        for (int pos = 0; pos < MAX_SUGGESTIONS; pos++) {
            final TextView word = (TextView)inflater.inflate(R.layout.suggestion_word, null);
            word.setOnClickListener(this);
            word.setOnLongClickListener(this);
            mWordViews.add(word);
            final View divider = inflater.inflate(R.layout.suggestion_divider, null);
            divider.setOnClickListener(this);
            mDividerViews.add(divider);
            mDebugInfoViews.add((TextView)inflater.inflate(R.layout.suggestion_info, null));
        }

        mLayoutHelper = new SuggestionStripLayoutHelper(
                context, attrs, defStyle, mWordViews, mDividerViews, mDebugInfoViews);

        mMoreSuggestionsContainer = inflater.inflate(R.layout.more_suggestions, null);
        mMoreSuggestionsView = (MoreSuggestionsView)mMoreSuggestionsContainer
                .findViewById(R.id.more_suggestions_view);
        mMoreSuggestionsBuilder = new MoreSuggestions.Builder(context, mMoreSuggestionsView);

        final Resources res = context.getResources();
        mMoreSuggestionsModalTolerance = res.getDimensionPixelOffset(
                R.dimen.more_suggestions_modal_tolerance);
        mMoreSuggestionsSlidingDetector = new GestureDetector(
                context, mMoreSuggestionsSlidingListener);
    }

    /**
     * A connection back to the input method.
     * @param listener
     */
    public void setListener(final Listener listener, final View inputView) {
        mListener = listener;
        mMainKeyboardView = (MainKeyboardView)inputView.findViewById(R.id.keyboard_view);
    }

    public void setSuggestions(final SuggestedWords suggestedWords) {
        clear();
        mSuggestedWords = suggestedWords;
        mLayoutHelper.layout(mSuggestedWords, mSuggestionsStrip, this);
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.suggestionStripView_setSuggestions(mSuggestedWords);
        }
    }

    public int setMoreSuggestionsHeight(final int remainingHeight) {
        return mLayoutHelper.setMoreSuggestionsHeight(remainingHeight);
    }

    public boolean isShowingAddToDictionaryHint() {
        return mSuggestionsStrip.getChildCount() > 0
                && mLayoutHelper.isAddToDictionaryShowing(mSuggestionsStrip.getChildAt(0));
    }

    public void showAddToDictionaryHint(final String word, final CharSequence hintText) {
        clear();
        mLayoutHelper.layoutAddToDictionaryHint(
                word, mSuggestionsStrip, getWidth(), hintText, this);
    }

    public boolean dismissAddToDictionaryHint() {
        if (isShowingAddToDictionaryHint()) {
            clear();
            return true;
        }
        return false;
    }

    public void clear() {
        mSuggestionsStrip.removeAllViews();
        removeAllViews();
        addView(mSuggestionsStrip);
        mMoreSuggestionsView.dismissMoreKeysPanel();
    }

    private final MoreSuggestionsListener mMoreSuggestionsListener = new MoreSuggestionsListener() {
        @Override
        public void onSuggestionSelected(final int index, final SuggestedWordInfo wordInfo) {
            mListener.pickSuggestionManually(index, wordInfo);
            mMoreSuggestionsView.dismissMoreKeysPanel();
        }

        @Override
        public void onCancelInput() {
            mMoreSuggestionsView.dismissMoreKeysPanel();
        }
    };

    private final MoreKeysPanel.Controller mMoreSuggestionsController =
            new MoreKeysPanel.Controller() {
        @Override
        public void onDismissMoreKeysPanel(final MoreKeysPanel panel) {
            mMainKeyboardView.onDismissMoreKeysPanel(panel);
        }

        @Override
        public void onShowMoreKeysPanel(final MoreKeysPanel panel) {
            mMainKeyboardView.onShowMoreKeysPanel(panel);
        }

        @Override
        public void onCancelMoreKeysPanel(final MoreKeysPanel panel) {
            mMoreSuggestionsView.dismissMoreKeysPanel();
        }
    };

    @Override
    public boolean onLongClick(final View view) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(
                Constants.NOT_A_CODE, this);
        return showMoreSuggestions();
    }

    boolean showMoreSuggestions() {
        final Keyboard parentKeyboard = KeyboardSwitcher.getInstance().getKeyboard();
        if (parentKeyboard == null) {
            return false;
        }
        final SuggestionStripLayoutHelper layoutHelper = mLayoutHelper;
        if (!layoutHelper.mMoreSuggestionsAvailable) {
            return false;
        }
        final int stripWidth = getWidth();
        final View container = mMoreSuggestionsContainer;
        final int maxWidth = stripWidth - container.getPaddingLeft() - container.getPaddingRight();
        final MoreSuggestions.Builder builder = mMoreSuggestionsBuilder;
        builder.layout(mSuggestedWords, layoutHelper.mSuggestionsCountInStrip, maxWidth,
                (int)(maxWidth * layoutHelper.mMinMoreSuggestionsWidth),
                layoutHelper.getMaxMoreSuggestionsRow(), parentKeyboard);
        mMoreSuggestionsView.setKeyboard(builder.build());
        container.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        final MoreKeysPanel moreKeysPanel = mMoreSuggestionsView;
        final int pointX = stripWidth / 2;
        final int pointY = -layoutHelper.mMoreSuggestionsBottomGap;
        moreKeysPanel.showMoreKeysPanel(this, mMoreSuggestionsController, pointX, pointY,
                mMoreSuggestionsListener);
        mMoreSuggestionsMode = MORE_SUGGESTIONS_CHECKING_MODAL_OR_SLIDING;
        mOriginX = mLastX;
        mOriginY = mLastY;
        for (int i = 0; i < layoutHelper.mSuggestionsCountInStrip; i++) {
            mWordViews.get(i).setPressed(false);
        }
        return true;
    }

    // Working variables for onLongClick and dispatchTouchEvent.
    private int mMoreSuggestionsMode = MORE_SUGGESTIONS_IN_MODAL_MODE;
    private static final int MORE_SUGGESTIONS_IN_MODAL_MODE = 0;
    private static final int MORE_SUGGESTIONS_CHECKING_MODAL_OR_SLIDING = 1;
    private static final int MORE_SUGGESTIONS_IN_SLIDING_MODE = 2;
    private int mLastX;
    private int mLastY;
    private int mOriginX;
    private int mOriginY;
    private final int mMoreSuggestionsModalTolerance;
    private final GestureDetector mMoreSuggestionsSlidingDetector;
    private final GestureDetector.OnGestureListener mMoreSuggestionsSlidingListener =
            new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onScroll(MotionEvent down, MotionEvent me, float deltaX, float deltaY) {
            final float dy = me.getY() - down.getY();
            if (deltaY > 0 && dy < 0) {
                return showMoreSuggestions();
            }
            return false;
        }
    };

    @Override
    public boolean dispatchTouchEvent(final MotionEvent me) {
        if (!mMoreSuggestionsView.isShowingInParent()) {
            mLastX = (int)me.getX();
            mLastY = (int)me.getY();
            if (mMoreSuggestionsSlidingDetector.onTouchEvent(me)) {
                return true;
            }
            return super.dispatchTouchEvent(me);
        }

        final int action = me.getAction();
        final int index = me.getActionIndex();
        final int x = (int)me.getX(index);
        final int y = (int)me.getY(index);

        if (mMoreSuggestionsMode == MORE_SUGGESTIONS_CHECKING_MODAL_OR_SLIDING) {
            if (Math.abs(x - mOriginX) >= mMoreSuggestionsModalTolerance
                    || mOriginY - y >= mMoreSuggestionsModalTolerance) {
                // Decided to be in the sliding input mode only when the touch point has been moved
                // upward.
                mMoreSuggestionsMode = MORE_SUGGESTIONS_IN_SLIDING_MODE;
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                // Decided to be in the modal input mode
                mMoreSuggestionsMode = MORE_SUGGESTIONS_IN_MODAL_MODE;
                mMoreSuggestionsView.adjustVerticalCorrectionForModalMode();
            }
            return true;
        }

        // MORE_SUGGESTIONS_IN_SLIDING_MODE
        me.setLocation(mMoreSuggestionsView.translateX(x), mMoreSuggestionsView.translateY(y));
        mMoreSuggestionsView.onTouchEvent(me);
        return true;
    }

    @Override
    public void onClick(final View view) {
        if (mLayoutHelper.isAddToDictionaryShowing(view)) {
            mListener.addWordToUserDictionary(mLayoutHelper.getAddToDictionaryWord());
            clear();
            return;
        }

        final Object tag = view.getTag();
        // Integer tag is set at
        // {@link SuggestionStripLayoutHelper#setupWordViewsTextAndColor(SuggestedWords,int)} and
        // {@link SuggestionStripLayoutHelper#layoutPunctuationSuggestions(SuggestedWords,ViewGroup}
        if (!(tag instanceof Integer)) {
            return;
        }
        final int index = (Integer) tag;
        if (index >= mSuggestedWords.size()) {
            return;
        }

        final SuggestedWordInfo wordInfo = mSuggestedWords.getInfo(index);
        mListener.pickSuggestionManually(index, wordInfo);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mMoreSuggestionsView.dismissMoreKeysPanel();
    }
}
