/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.inputmethod.latin;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Message;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.android.inputmethod.compat.FrameLayoutCompatUtils;
import com.android.inputmethod.compat.LinearLayoutCompatUtils;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;

import java.util.ArrayList;
import java.util.List;

public class CandidateView extends LinearLayout implements OnClickListener, OnLongClickListener {
    public interface Listener {
        public boolean addWordToDictionary(String word);
        public void pickSuggestionManually(int index, CharSequence word);
    }

    // The maximum number of suggestions available. See {@link Suggest#mPrefMaxSuggestions}.
    private static final int MAX_SUGGESTIONS = 18;
    private static final int WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT;

    private static final boolean DBG = LatinImeLogger.sDBG;

    private final ViewGroup mCandidatesStrip;
    private ViewGroup mCandidatesPane;
    private ViewGroup mCandidatesPaneContainer;
    private View mKeyboardView;

    private final ArrayList<TextView> mWords = new ArrayList<TextView>();
    private final ArrayList<TextView> mInfos = new ArrayList<TextView>();
    private final ArrayList<View> mDividers = new ArrayList<View>();

    private final PopupWindow mPreviewPopup;
    private final TextView mPreviewText;

    private Listener mListener;
    private SuggestedWords mSuggestions = SuggestedWords.EMPTY;
    private boolean mShowingAutoCorrectionInverted;

    private final SuggestionsStripParams mStripParams;
    private final SuggestionsPaneParams mPaneParams;
    private static final float MIN_TEXT_XSCALE = 0.70f;

    private final UiHandler mHandler = new UiHandler(this);

    private static class UiHandler extends StaticInnerHandlerWrapper<CandidateView> {
        private static final int MSG_HIDE_PREVIEW = 0;
        private static final int MSG_UPDATE_SUGGESTION = 1;

        private static final long DELAY_HIDE_PREVIEW = 1300;
        private static final long DELAY_UPDATE_SUGGESTION = 300;

        public UiHandler(CandidateView outerInstance) {
            super(outerInstance);
        }

        @Override
        public void dispatchMessage(Message msg) {
            final CandidateView candidateView = getOuterInstance();
            switch (msg.what) {
            case MSG_HIDE_PREVIEW:
                candidateView.hidePreview();
                break;
            case MSG_UPDATE_SUGGESTION:
                candidateView.updateSuggestions();
                break;
            }
        }

        public void postHidePreview() {
            cancelHidePreview();
            sendMessageDelayed(obtainMessage(MSG_HIDE_PREVIEW), DELAY_HIDE_PREVIEW);
        }

        public void cancelHidePreview() {
            removeMessages(MSG_HIDE_PREVIEW);
        }

        public void postUpdateSuggestions() {
            cancelUpdateSuggestions();
            sendMessageDelayed(obtainMessage(MSG_UPDATE_SUGGESTION),
                    DELAY_UPDATE_SUGGESTION);
        }

        public void cancelUpdateSuggestions() {
            removeMessages(MSG_UPDATE_SUGGESTION);
        }

        public void cancelAllMessages() {
            cancelHidePreview();
            cancelUpdateSuggestions();
        }
    }

    private static class CandidateViewParams {
        public final int mPadding;
        public final int mDividerWidth;
        public final int mDividerHeight;
        public final int mCandidateStripHeight;

        protected final List<TextView> mWords;
        protected final List<View> mDividers;
        protected final List<TextView> mInfos;

        protected CandidateViewParams(List<TextView> words, List<View> dividers,
                List<TextView> infos) {
            mWords = words;
            mDividers = dividers;
            mInfos = infos;

            final TextView word = words.get(0);
            final View divider = dividers.get(0);
            mPadding = word.getCompoundPaddingLeft() + word.getCompoundPaddingRight();
            divider.measure(WRAP_CONTENT, MATCH_PARENT);
            mDividerWidth = divider.getMeasuredWidth();
            mDividerHeight = divider.getMeasuredHeight();

            final Resources res = word.getResources();
            mCandidateStripHeight = res.getDimensionPixelOffset(R.dimen.candidate_strip_height);
        }
    }

    private static class SuggestionsPaneParams extends CandidateViewParams {
        public SuggestionsPaneParams(List<TextView> words, List<View> dividers,
                List<TextView> infos) {
            super(words, dividers, infos);
        }

        public int layout(SuggestedWords suggestions, ViewGroup paneView, int from, int textColor,
                int paneWidth) {
            final int count = Math.min(mWords.size(), suggestions.size());
            View centeringFrom = null, lastView = null;
            int x = 0, y = 0;
            for (int index = from; index < count; index++) {
                final int pos = index;
                final TextView word = mWords.get(pos);
                final View divider = mDividers.get(pos);
                final TextPaint paint = word.getPaint();
                word.setTextColor(textColor);
                final CharSequence styled = suggestions.getWord(pos);

                final TextView info;
                if (DBG) {
                    final CharSequence debugInfo = getDebugInfo(suggestions, index);
                    if (debugInfo != null) {
                        info = mInfos.get(index);
                        info.setText(debugInfo);
                    } else {
                        info = null;
                    }
                } else {
                    info = null;
                }

                final CharSequence text;
                final float scaleX;
                paint.setTextScaleX(1.0f);
                final int textWidth = getTextWidth(styled, paint);
                int available = paneWidth - x - mPadding;
                if (textWidth >= available) {
                    // Needs new row, centering previous row.
                    centeringCandidates(paneView, centeringFrom, lastView, x, paneWidth);
                    x = 0;
                    y += mCandidateStripHeight;
                }
                if (x != 0) {
                    // Add divider if this isn't the left most suggestion in current row.
                    paneView.addView(divider);
                    FrameLayoutCompatUtils.placeViewAt(divider, x, y
                            + (mCandidateStripHeight - mDividerHeight) / 2, mDividerWidth,
                            mDividerHeight);
                    x += mDividerWidth;
                }
                available = paneWidth - x - mPadding;
                text = getEllipsizedText(styled, available, paint);
                scaleX = paint.getTextScaleX();
                word.setText(text);
                word.setTextScaleX(scaleX);
                paneView.addView(word);
                lastView = word;
                if (x == 0)
                    centeringFrom = word;
                word.measure(WRAP_CONTENT,
                        MeasureSpec.makeMeasureSpec(mCandidateStripHeight, MeasureSpec.EXACTLY));
                final int width = word.getMeasuredWidth();
                final int height = word.getMeasuredHeight();
                FrameLayoutCompatUtils.placeViewAt(word, x, y + (mCandidateStripHeight - height)
                        / 2, width, height);
                x += width;
                if (info != null) {
                    paneView.addView(info);
                    lastView = info;
                    info.measure(WRAP_CONTENT, WRAP_CONTENT);
                    final int infoWidth = info.getMeasuredWidth();
                    FrameLayoutCompatUtils.placeViewAt(info, x - infoWidth, y, infoWidth,
                            info.getMeasuredHeight());
                }
            }
            if (x != 0) {
                // Centering last candidates row.
                centeringCandidates(paneView, centeringFrom, lastView, x, paneWidth);
            }

            return count - from;
        }
    }

    private static class SuggestionsStripParams extends CandidateViewParams {
        private static final int DEFAULT_CANDIDATE_COUNT_IN_STRIP = 3;
        private static final int DEFAULT_CENTER_CANDIDATE_PERCENTILE = 40;
        private static final int PUNCTUATIONS_IN_STRIP = 6;

        private final int mColorTypedWord;
        private final int mColorAutoCorrect;
        private final int mColorSuggestedCandidate;
        private final int mCandidateCountInStrip;
        private final float mCenterCandidateWeight;
        private final int mCenterCandidateIndex;
        private final Drawable mMoreCandidateHint;

        private static final CharacterStyle BOLD_SPAN = new StyleSpan(Typeface.BOLD);
        private static final CharacterStyle UNDERLINE_SPAN = new UnderlineSpan();
        private final CharacterStyle mInvertedForegroundColorSpan;
        private final CharacterStyle mInvertedBackgroundColorSpan;
        private static final int AUTO_CORRECT_BOLD = 0x01;
        private static final int AUTO_CORRECT_UNDERLINE = 0x02;
        private static final int AUTO_CORRECT_INVERT = 0x04;
        private static final int VALID_TYPED_WORD_BOLD = 0x08;

        private final int mSuggestionStripOption;

        private final ArrayList<CharSequence> mTexts = new ArrayList<CharSequence>();

        public boolean mMoreSuggestionsAvailable;

        public final TextView mWordToSaveView;
        private final TextView mHintToSaveView;
        private final CharSequence mHintToSaveText;

        public SuggestionsStripParams(Context context, AttributeSet attrs, int defStyle,
                List<TextView> words, List<View> dividers, List<TextView> infos) {
            super(words, dividers, infos);
            final TypedArray a = context.obtainStyledAttributes(
                    attrs, R.styleable.CandidateView, defStyle, R.style.CandidateViewStyle);
            mSuggestionStripOption = a.getInt(R.styleable.CandidateView_suggestionStripOption, 0);
            mColorTypedWord = a.getColor(R.styleable.CandidateView_colorTypedWord, 0);
            mColorAutoCorrect = a.getColor(R.styleable.CandidateView_colorAutoCorrect, 0);
            mColorSuggestedCandidate = a.getColor(R.styleable.CandidateView_colorSuggested, 0);
            mCandidateCountInStrip = a.getInt(
                    R.styleable.CandidateView_candidateCountInStrip,
                    DEFAULT_CANDIDATE_COUNT_IN_STRIP);
            mCenterCandidateWeight = a.getInt(
                    R.styleable.CandidateView_centerCandidatePercentile,
                    DEFAULT_CENTER_CANDIDATE_PERCENTILE) / 100.0f;
            a.recycle();

            mCenterCandidateIndex = mCandidateCountInStrip / 2;
            final Resources res = context.getResources();
            mMoreCandidateHint = res.getDrawable(R.drawable.more_suggestions_hint);

            mInvertedForegroundColorSpan = new ForegroundColorSpan(mColorTypedWord ^ 0x00ffffff);
            mInvertedBackgroundColorSpan = new BackgroundColorSpan(mColorTypedWord);

            final LayoutInflater inflater = LayoutInflater.from(context);
            mWordToSaveView = (TextView)inflater.inflate(R.layout.candidate_word, null);
            mHintToSaveView = (TextView)inflater.inflate(R.layout.candidate_word, null);
            mHintToSaveText = context.getText(R.string.hint_add_to_dictionary);
        }

        public int getTextColor() {
            return mColorTypedWord;
        }

        private CharSequence getStyledCandidateWord(SuggestedWords suggestions, int pos) {
            final CharSequence word = suggestions.getWord(pos);
            final boolean isAutoCorrect = pos == 1 && willAutoCorrect(suggestions);
            final boolean isTypedWordValid = pos == 0 && suggestions.mTypedWordValid;
            if (!isAutoCorrect && !isTypedWordValid)
                return word;

            final int len = word.length();
            final Spannable spannedWord = new SpannableString(word);
            final int option = mSuggestionStripOption;
            if ((isAutoCorrect && (option & AUTO_CORRECT_BOLD) != 0)
                    || (isTypedWordValid && (option & VALID_TYPED_WORD_BOLD) != 0)) {
                spannedWord.setSpan(BOLD_SPAN, 0, len, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
            }
            if (isAutoCorrect && (option & AUTO_CORRECT_UNDERLINE) != 0) {
                spannedWord.setSpan(UNDERLINE_SPAN, 0, len, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
            }
            return spannedWord;
        }

        private static boolean willAutoCorrect(SuggestedWords suggestions) {
            return !suggestions.mTypedWordValid && suggestions.mHasMinimalSuggestion;
        }

        private int getWordPosition(int index, SuggestedWords suggestions) {
            // TODO: This works for 3 suggestions. Revisit this algorithm when there are 5 or more
            // suggestions.
            final int centerPos = willAutoCorrect(suggestions) ? 1 : 0;
            if (index == mCenterCandidateIndex) {
                return centerPos;
            } else if (index == centerPos) {
                return mCenterCandidateIndex;
            } else {
                return index;
            }
        }

        private int getCandidateTextColor(int index, SuggestedWords suggestions, int pos) {
            // TODO: Need to revisit this logic with bigram suggestions
            final boolean isSuggestedCandidate = (pos != 0);

            final int color;
            if (index == mCenterCandidateIndex && willAutoCorrect(suggestions)) {
                color = mColorAutoCorrect;
            } else if (isSuggestedCandidate) {
                color = mColorSuggestedCandidate;
            } else {
                color = mColorTypedWord;
            }

            final SuggestedWordInfo info = (pos < suggestions.size())
                    ? suggestions.getInfo(pos) : null;
            if (info != null && info.isPreviousSuggestedWord()) {
                return applyAlpha(color, 0.5f);
            } else {
                return color;
            }
        }

        private static int applyAlpha(final int color, final float alpha) {
            final int newAlpha = (int)(Color.alpha(color) * alpha);
            return Color.argb(newAlpha, Color.red(color), Color.green(color), Color.blue(color));
        }

        public CharSequence getInvertedText(CharSequence text) {
            if ((mSuggestionStripOption & AUTO_CORRECT_INVERT) == 0)
                return null;
            final int len = text.length();
            final Spannable word = new SpannableString(text);
            word.setSpan(mInvertedBackgroundColorSpan, 0, len, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
            word.setSpan(mInvertedForegroundColorSpan, 0, len, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
            return word;
        }

        public int layout(SuggestedWords suggestions, ViewGroup stripView, ViewGroup paneView,
                int stripWidth) {
            if (suggestions.isPunctuationSuggestions()) {
                return layoutPunctuationSuggestions(suggestions, stripView);
            }

            final int countInStrip = mCandidateCountInStrip;
            setupTexts(suggestions, countInStrip);
            mMoreSuggestionsAvailable = (suggestions.size() > countInStrip);
            int x = 0;
            for (int index = 0; index < countInStrip; index++) {
                final int pos = getWordPosition(index, suggestions);

                if (index != 0) {
                    final View divider = mDividers.get(pos);
                    // Add divider if this isn't the left most suggestion in candidate strip.
                    stripView.addView(divider);
                }

                final CharSequence styled = mTexts.get(pos);
                final TextView word = mWords.get(pos);
                if (index == mCenterCandidateIndex && mMoreSuggestionsAvailable) {
                    // TODO: This "more suggestions hint" should have nicely designed icon.
                    word.setCompoundDrawablesWithIntrinsicBounds(
                            null, null, null, mMoreCandidateHint);
                    // HACK: To align with other TextView that has no compound drawables.
                    word.setCompoundDrawablePadding(-mMoreCandidateHint.getIntrinsicHeight());
                } else {
                    word.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
                }

                // Disable this candidate if the suggestion is null or empty.
                word.setEnabled(!TextUtils.isEmpty(styled));
                word.setTextColor(getCandidateTextColor(index, suggestions, pos));
                final int width = getCandidateWidth(index, stripWidth);
                final CharSequence text = getEllipsizedText(styled, width, word.getPaint());
                final float scaleX = word.getTextScaleX();
                word.setText(text); // TextView.setText() resets text scale x to 1.0.
                word.setTextScaleX(scaleX);
                stripView.addView(word);
                setLayoutWeight(word, getCandidateWeight(index), MATCH_PARENT);

                if (DBG) {
                    final CharSequence debugInfo = getDebugInfo(suggestions, pos);
                    if (debugInfo != null) {
                        final TextView info = mInfos.get(pos);
                        info.setText(debugInfo);
                        paneView.addView(info);
                        info.measure(WRAP_CONTENT, WRAP_CONTENT);
                        final int infoWidth = info.getMeasuredWidth();
                        final int y = info.getMeasuredHeight();
                        FrameLayoutCompatUtils.placeViewAt(info, x, 0, infoWidth, y);
                        x += infoWidth * 2;
                    }
                }
            }

            return countInStrip;
        }

        private int getCandidateWidth(int index, int maxWidth) {
            final int paddings = mPadding * mCandidateCountInStrip;
            final int dividers = mDividerWidth * (mCandidateCountInStrip - 1);
            final int availableWidth = maxWidth - paddings - dividers;
            return (int)(availableWidth * getCandidateWeight(index));
        }

        private float getCandidateWeight(int index) {
            if (index == mCenterCandidateIndex) {
                return mCenterCandidateWeight;
            } else {
                // TODO: Revisit this for cases of 5 or more suggestions
                return (1.0f - mCenterCandidateWeight) / (mCandidateCountInStrip - 1);
            }
        }

        private void setupTexts(SuggestedWords suggestions, int countInStrip) {
            mTexts.clear();
            final int count = Math.min(suggestions.size(), countInStrip);
            for (int pos = 0; pos < count; pos++) {
                final CharSequence styled = getStyledCandidateWord(suggestions, pos);
                mTexts.add(styled);
            }
            for (int pos = count; pos < countInStrip; pos++) {
                // Make this inactive for touches in layout().
                mTexts.add(null);
            }
        }

        private int layoutPunctuationSuggestions(SuggestedWords suggestions, ViewGroup stripView) {
            final int countInStrip = Math.min(suggestions.size(), PUNCTUATIONS_IN_STRIP);
            for (int index = 0; index < countInStrip; index++) {
                if (index != 0) {
                    // Add divider if this isn't the left most suggestion in candidate strip.
                    stripView.addView(mDividers.get(index));
                }

                final TextView word = mWords.get(index);
                word.setEnabled(true);
                word.setTextColor(mColorTypedWord);
                final CharSequence text = suggestions.getWord(index);
                word.setText(text);
                word.setTextScaleX(1.0f);
                word.setCompoundDrawables(null, null, null, null);
                stripView.addView(word);
                setLayoutWeight(word, 1.0f, mCandidateStripHeight);
            }
            mMoreSuggestionsAvailable = false;
            return countInStrip;
        }

        public void layoutAddToDictionaryHint(CharSequence word, ViewGroup stripView,
                int stripWidth) {
            final int width = stripWidth - mDividerWidth - mPadding * 2;

            final TextView wordView = mWordToSaveView;
            wordView.setTextColor(mColorTypedWord);
            final int wordWidth = (int)(width * mCenterCandidateWeight);
            final CharSequence text = getEllipsizedText(word, wordWidth, wordView.getPaint());
            final float wordScaleX = wordView.getTextScaleX();
            wordView.setTag(word);
            wordView.setText(text);
            wordView.setTextScaleX(wordScaleX);
            stripView.addView(wordView);
            setLayoutWeight(wordView, mCenterCandidateWeight, MATCH_PARENT);

            stripView.addView(mDividers.get(0));

            final TextView hintView = mHintToSaveView;
            hintView.setTextColor(mColorAutoCorrect);
            final int hintWidth = width - wordWidth;
            final float hintScaleX = getTextScaleX(mHintToSaveText, hintWidth, hintView.getPaint());
            hintView.setText(mHintToSaveText);
            hintView.setTextScaleX(hintScaleX);
            stripView.addView(hintView);
            setLayoutWeight(hintView, 1.0f - mCenterCandidateWeight, MATCH_PARENT);
        }
    }

    /**
     * Construct a CandidateView for showing suggested words for completion.
     * @param context
     * @param attrs
     */
    public CandidateView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.candidateViewStyle);
    }

    public CandidateView(Context context, AttributeSet attrs, int defStyle) {
        // Note: Up to version 10 (Gingerbread) of the API, LinearLayout doesn't have 3-argument
        // constructor.
        // TODO: Call 3-argument constructor, super(context, attrs, defStyle), when we abandon
        // backward compatibility with the version 10 or earlier of the API.
        super(context, attrs);
        if (defStyle != R.attr.candidateViewStyle) {
            throw new IllegalArgumentException(
                    "can't accept defStyle other than R.attr.candidateViewStyle: defStyle="
                    + defStyle);
        }
        setBackgroundDrawable(LinearLayoutCompatUtils.getBackgroundDrawable(
                context, attrs, defStyle, R.style.CandidateViewStyle));

        final LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.candidates_strip, this);

        mPreviewPopup = new PopupWindow(context);
        mPreviewText = (TextView) inflater.inflate(R.layout.candidate_preview, null);
        mPreviewPopup.setWindowLayoutMode(WRAP_CONTENT, WRAP_CONTENT);
        mPreviewPopup.setContentView(mPreviewText);
        mPreviewPopup.setBackgroundDrawable(null);

        mCandidatesStrip = (ViewGroup)findViewById(R.id.candidates_strip);
        for (int pos = 0; pos < MAX_SUGGESTIONS; pos++) {
            final TextView word = (TextView)inflater.inflate(R.layout.candidate_word, null);
            word.setTag(pos);
            word.setOnClickListener(this);
            word.setOnLongClickListener(this);
            mWords.add(word);
            final View divider = inflater.inflate(R.layout.candidate_divider, null);
            divider.setTag(pos);
            divider.setOnClickListener(this);
            mDividers.add(divider);
            mInfos.add((TextView)inflater.inflate(R.layout.candidate_info, null));
        }

        mStripParams = new SuggestionsStripParams(context, attrs, defStyle, mWords, mDividers,
                mInfos);
        mPaneParams = new SuggestionsPaneParams(mWords, mDividers, mInfos);
        mStripParams.mWordToSaveView.setOnClickListener(this);
    }

    /**
     * A connection back to the input method.
     * @param listener
     */
    public void setListener(Listener listener, View inputView) {
        mListener = listener;
        mKeyboardView = inputView.findViewById(R.id.keyboard_view);
        mCandidatesPane = FrameLayoutCompatUtils.getPlacer(
                (ViewGroup)inputView.findViewById(R.id.candidates_pane));
        mCandidatesPane.setOnClickListener(this);
        mCandidatesPaneContainer = (ViewGroup)inputView.findViewById(
                R.id.candidates_pane_container);
    }

    public void setSuggestions(SuggestedWords suggestions) {
        if (suggestions == null)
            return;
        mSuggestions = suggestions;
        if (mShowingAutoCorrectionInverted) {
            mHandler.postUpdateSuggestions();
        } else {
            updateSuggestions();
        }
    }

    private void updateSuggestions() {
        clear();
        closeCandidatesPane();
        if (mSuggestions.size() == 0)
            return;

        final int width = getWidth();
        final int countInStrip = mStripParams.layout(
                mSuggestions, mCandidatesStrip, mCandidatesPane, width);
        mPaneParams.layout(
                mSuggestions, mCandidatesPane, countInStrip, mStripParams.getTextColor(), width);
    }

    private static CharSequence getDebugInfo(SuggestedWords suggestions, int pos) {
        if (DBG && pos < suggestions.size()) {
            final SuggestedWordInfo wordInfo = suggestions.getInfo(pos);
            if (wordInfo != null) {
                final CharSequence debugInfo = wordInfo.getDebugString();
                if (!TextUtils.isEmpty(debugInfo)) {
                    return debugInfo;
                }
            }
        }
        return null;
    }

    private static void setLayoutWeight(View v, float weight, int height) {
        final ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof LinearLayout.LayoutParams) {
            final LinearLayout.LayoutParams llp = (LinearLayout.LayoutParams)lp;
            llp.weight = weight;
            llp.width = 0;
            llp.height = height;
        }
    }

    private static void centeringCandidates(ViewGroup parent, View from, View to, int width,
            int parentWidth) {
        final int fromIndex = parent.indexOfChild(from);
        final int toIndex = parent.indexOfChild(to);
        final int offset = (parentWidth - width) / 2;
        for (int index = fromIndex; index <= toIndex; index++) {
            offsetMargin(parent.getChildAt(index), offset, 0);
        }
    }

    private static void offsetMargin(View v, int dx, int dy) {
        if (v == null)
            return;
        final ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            final ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams)lp;
            mlp.setMargins(mlp.leftMargin + dx, mlp.topMargin + dy, 0, 0);
        }
    }

    private static float getTextScaleX(CharSequence text, int maxWidth, TextPaint paint) {
        paint.setTextScaleX(1.0f);
        final int width = getTextWidth(text, paint);
        if (width <= maxWidth) {
            return 1.0f;
        }
        return maxWidth / (float)width;
    }

    private static CharSequence getEllipsizedText(CharSequence text, int maxWidth,
            TextPaint paint) {
        if (text == null) return null;
        paint.setTextScaleX(1.0f);
        final int width = getTextWidth(text, paint);
        if (width <= maxWidth) {
            return text;
        }
        final float scaleX = maxWidth / (float)width;
        if (scaleX >= MIN_TEXT_XSCALE) {
            paint.setTextScaleX(scaleX);
            return text;
        }

        // Note that TextUtils.ellipsize() use text-x-scale as 1.0 if ellipsize is needed. To get
        // squeezed and ellipsized text, passes enlarged width (maxWidth / MIN_TEXT_XSCALE).
        final CharSequence ellipsized = TextUtils.ellipsize(
                text, paint, maxWidth / MIN_TEXT_XSCALE, TextUtils.TruncateAt.MIDDLE);
        paint.setTextScaleX(MIN_TEXT_XSCALE);
        return ellipsized;
    }

    private static int getTextWidth(CharSequence text, TextPaint paint) {
        if (TextUtils.isEmpty(text)) return 0;
        final Typeface savedTypeface = paint.getTypeface();
        paint.setTypeface(getTextTypeface(text));
        final int len = text.length();
        final float[] widths = new float[len];
        final int count = paint.getTextWidths(text, 0, len, widths);
        int width = 0;
        for (int i = 0; i < count; i++) {
            width += Math.round(widths[i] + 0.5f);
        }
        paint.setTypeface(savedTypeface);
        return width;
    }

    private static Typeface getTextTypeface(CharSequence text) {
        if (!(text instanceof SpannableString))
            return Typeface.DEFAULT;

        final SpannableString ss = (SpannableString)text;
        final StyleSpan[] styles = ss.getSpans(0, text.length(), StyleSpan.class);
        if (styles.length == 0)
            return Typeface.DEFAULT;

        switch (styles[0].getStyle()) {
        case Typeface.BOLD: return Typeface.DEFAULT_BOLD;
        // TODO: BOLD_ITALIC, ITALIC case?
        default: return Typeface.DEFAULT;
        }
    }

    private void expandCandidatesPane() {
        mCandidatesPaneContainer.setMinimumHeight(mKeyboardView.getMeasuredHeight());
        mCandidatesPaneContainer.setVisibility(VISIBLE);
        mKeyboardView.setVisibility(GONE);
    }

    private void closeCandidatesPane() {
        mCandidatesPaneContainer.setVisibility(GONE);
        mKeyboardView.setVisibility(VISIBLE);
    }

    private void toggleCandidatesPane() {
        if (mCandidatesPaneContainer.getVisibility() == VISIBLE) {
            closeCandidatesPane();
        } else {
            expandCandidatesPane();
        }
    }

    public void onAutoCorrectionInverted(CharSequence autoCorrectedWord) {
        final CharSequence inverted = mStripParams.getInvertedText(autoCorrectedWord);
        if (inverted == null)
            return;
        final TextView tv = mWords.get(1);
        tv.setText(inverted);
        mShowingAutoCorrectionInverted = true;
    }

    public boolean isShowingAddToDictionaryHint() {
        return mCandidatesStrip.getChildCount() > 0
                && mCandidatesStrip.getChildAt(0) == mStripParams.mWordToSaveView;
    }

    public void showAddToDictionaryHint(CharSequence word) {
        clear();
        mStripParams.layoutAddToDictionaryHint(word, mCandidatesStrip, getWidth());
    }

    public boolean dismissAddToDictionaryHint() {
        if (isShowingAddToDictionaryHint()) {
            clear();
            return true;
        }
        return false;
    }

    public SuggestedWords getSuggestions() {
        return mSuggestions;
    }

    public void clear() {
        mShowingAutoCorrectionInverted = false;
        mCandidatesStrip.removeAllViews();
        mCandidatesPane.removeAllViews();
        closeCandidatesPane();
    }

    private void hidePreview() {
        mPreviewPopup.dismiss();
    }

    private void showPreview(View view, CharSequence word) {
        if (TextUtils.isEmpty(word))
            return;

        final TextView previewText = mPreviewText;
        previewText.setTextColor(mStripParams.mColorTypedWord);
        previewText.setText(word);
        previewText.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        final int[] offsetInWindow = new int[2];
        view.getLocationInWindow(offsetInWindow);
        final int posX = offsetInWindow[0];
        final int posY = offsetInWindow[1] - previewText.getMeasuredHeight();
        final PopupWindow previewPopup = mPreviewPopup;
        if (previewPopup.isShowing()) {
            previewPopup.update(posX, posY, previewPopup.getWidth(), previewPopup.getHeight());
        } else {
            previewPopup.showAtLocation(this, Gravity.NO_GRAVITY, posX, posY);
        }
        previewText.setVisibility(VISIBLE);
        mHandler.postHidePreview();
    }

    private void addToDictionary(CharSequence word) {
        if (mListener.addWordToDictionary(word.toString())) {
            final CharSequence message = getContext().getString(R.string.added_word, word);
            showPreview(mStripParams.mWordToSaveView, message);
        }
    }

    @Override
    public boolean onLongClick(View view) {
        if (mStripParams.mMoreSuggestionsAvailable) {
            toggleCandidatesPane();
            return true;
        }
        return false;
    }

    @Override
    public void onClick(View view) {
        if (view == mStripParams.mWordToSaveView) {
            addToDictionary((CharSequence)view.getTag());
            clear();
            return;
        }

        if (view == mCandidatesPane) {
            closeCandidatesPane();
            return;
        }

        final Object tag = view.getTag();
        if (!(tag instanceof Integer))
            return;
        final int index = (Integer) tag;
        if (index >= mSuggestions.size())
            return;

        final CharSequence word = mSuggestions.getWord(index);
        mListener.pickSuggestionManually(index, word);
        // Because some punctuation letters are not treated as word separator depending on locale,
        // {@link #setSuggestions} might not be called and candidates pane left opened.
        closeCandidatesPane();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mHandler.cancelAllMessages();
        hidePreview();
    }
}
