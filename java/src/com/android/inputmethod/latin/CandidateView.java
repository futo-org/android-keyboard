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

    private static final CharacterStyle BOLD_SPAN = new StyleSpan(Typeface.BOLD);
    private static final CharacterStyle UNDERLINE_SPAN = new UnderlineSpan();
    // The maximum number of suggestions available. See {@link Suggest#mPrefMaxSuggestions}.
    private static final int MAX_SUGGESTIONS = 18;
    private static final int WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT;

    private static final boolean DBG = LatinImeLogger.sDBG;

    private final ViewGroup mCandidatesStrip;
    private final int mCandidateCountInStrip;
    private static final int DEFAULT_CANDIDATE_COUNT_IN_STRIP = 3;
    private final ViewGroup mCandidatesPaneControl;
    private final TextView mExpandCandidatesPane;
    private final TextView mCloseCandidatesPane;
    private ViewGroup mCandidatesPane;
    private ViewGroup mCandidatesPaneContainer;
    private View mKeyboardView;

    private final ArrayList<TextView> mWords = new ArrayList<TextView>();
    private final ArrayList<TextView> mInfos = new ArrayList<TextView>();
    private final ArrayList<View> mDividers = new ArrayList<View>();

    private final int mCandidateStripHeight;
    private final CharacterStyle mInvertedForegroundColorSpan;
    private final CharacterStyle mInvertedBackgroundColorSpan;
    private final int mAutoCorrectHighlight;
    private static final int AUTO_CORRECT_BOLD = 0x01;
    private static final int AUTO_CORRECT_UNDERLINE = 0x02;
    private static final int AUTO_CORRECT_INVERT = 0x04;
    private final int mColorTypedWord;
    private final int mColorAutoCorrect;
    private final int mColorSuggestedCandidate;

    private final PopupWindow mPreviewPopup;
    private final TextView mPreviewText;

    private final View mTouchToSave;
    private final TextView mWordToSave;

    private Listener mListener;
    private SuggestedWords mSuggestions = SuggestedWords.EMPTY;
    private boolean mShowingAutoCorrectionInverted;
    private boolean mShowingAddToDictionary;

    private final CandidateViewLayoutParams mParams;
    private static final int PUNCTUATIONS_IN_STRIP = 6;
    private static final float MIN_TEXT_XSCALE = 0.8f;

    private final UiHandler mHandler = new UiHandler(this);

    private static class UiHandler extends StaticInnerHandlerWrapper<CandidateView> {
        private static final int MSG_HIDE_PREVIEW = 0;
        private static final int MSG_UPDATE_SUGGESTION = 1;

        private static final long DELAY_HIDE_PREVIEW = 1000;
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

    private static class CandidateViewLayoutParams {
        public final TextPaint mPaint;
        public final int mPadding;
        public final int mDividerWidth;
        public final int mDividerHeight;
        public final int mControlWidth;
        private final int mAutoCorrectHighlight;

        public final ArrayList<CharSequence> mTexts = new ArrayList<CharSequence>();

        public int mCountInStrip;
        // True if the mCountInStrip suggestions can fit in suggestion strip in equally divided
        // width without squeezing the text.
        public boolean mCanUseFixedWidthColumns;
        public int mMaxWidth;
        public int mAvailableWidthForWords;
        public int mConstantWidthForPaddings;
        public int mVariableWidthForWords;
        public float mScaleX;

        public CandidateViewLayoutParams(Resources res, View divider, View control,
                int autoCorrectHighlight) {
            mPaint = new TextPaint();
            final float textSize = res.getDimension(R.dimen.candidate_text_size);
            mPaint.setTextSize(textSize);
            mPadding = res.getDimensionPixelSize(R.dimen.candidate_padding);
            divider.measure(WRAP_CONTENT, MATCH_PARENT);
            mDividerWidth = divider.getMeasuredWidth();
            mDividerHeight = divider.getMeasuredHeight();
            mControlWidth = control.getMeasuredWidth();
            mAutoCorrectHighlight = autoCorrectHighlight;
        }

        public void layoutStrip(SuggestedWords suggestions, int maxWidth, int maxCount) {
            final int size = suggestions.size();
            setupTexts(suggestions, size, mAutoCorrectHighlight);
            mCountInStrip = Math.min(maxCount, size);
            mScaleX = 1.0f;

            do {
                mMaxWidth = maxWidth;
                if (size > mCountInStrip) {
                    mMaxWidth -= mControlWidth;
                }

                tryLayout();

                if (mCanUseFixedWidthColumns) {
                    return;
                }
                if (mVariableWidthForWords <= mAvailableWidthForWords) {
                    return;
                }

                final float scaleX = mAvailableWidthForWords / (float)mVariableWidthForWords;
                if (scaleX >= MIN_TEXT_XSCALE) {
                    mScaleX = scaleX;
                    return;
                }

                mCountInStrip--;
            } while (mCountInStrip > 1);
        }

        public void tryLayout() {
            final int maxCount = mCountInStrip;
            final int dividers = mDividerWidth * (maxCount - 1);
            mConstantWidthForPaddings = dividers + mPadding * maxCount * 2;
            mAvailableWidthForWords = mMaxWidth - mConstantWidthForPaddings;

            mPaint.setTextScaleX(mScaleX);
            final int maxFixedWidthForWord = (mMaxWidth - dividers) / maxCount - mPadding * 2;
            mCanUseFixedWidthColumns = true;
            mVariableWidthForWords = 0;
            for (int i = 0; i < maxCount; i++) {
                final int width = getTextWidth(mTexts.get(i), mPaint);
                if (width > maxFixedWidthForWord)
                    mCanUseFixedWidthColumns = false;
                mVariableWidthForWords += width;
            }
        }

        private void setupTexts(SuggestedWords suggestions, int count, int autoCorrectHighlight) {
            mTexts.clear();
            for (int i = 0; i < count; i++) {
                final CharSequence suggestion = suggestions.getWord(i);
                if (suggestion == null) continue;

                final boolean isAutoCorrect = suggestions.mHasMinimalSuggestion
                        && ((i == 1 && !suggestions.mTypedWordValid)
                                || (i == 0 && suggestions.mTypedWordValid));
                // HACK: even if i == 0, we use mColorOther when this suggestion's length is 1
                // and there are multiple suggestions, such as the default punctuation list.
                // TODO: Need to revisit this logic with bigram suggestions
                final CharSequence styled = getStyledCandidateWord(suggestion, isAutoCorrect,
                        autoCorrectHighlight);
                mTexts.add(styled);
            }
        }

        @Override
        public String toString() {
            return String.format(
                    "count=%d width=%d avail=%d fixcol=%s scaleX=%4.2f const=%d var=%d",
                    mCountInStrip, mMaxWidth, mAvailableWidthForWords, mCanUseFixedWidthColumns,
                    mScaleX, mConstantWidthForPaddings, mVariableWidthForWords);
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
                    "can't accept defStyle other than R.attr.candidayeViewStyle: defStyle="
                    + defStyle);
        }
        setBackgroundDrawable(LinearLayoutCompatUtils.getBackgroundDrawable(
                context, attrs, defStyle, R.style.CandidateViewStyle));

        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.CandidateView, defStyle, R.style.CandidateViewStyle);
        mAutoCorrectHighlight = a.getInt(R.styleable.CandidateView_autoCorrectHighlight, 0);
        mColorTypedWord = a.getColor(R.styleable.CandidateView_colorTypedWord, 0);
        mColorAutoCorrect = a.getColor(R.styleable.CandidateView_colorAutoCorrect, 0);
        mColorSuggestedCandidate = a.getColor(R.styleable.CandidateView_colorSuggested, 0);
        mCandidateCountInStrip = a.getInt(
                R.styleable.CandidateView_candidateCountInStrip, DEFAULT_CANDIDATE_COUNT_IN_STRIP);
        a.recycle();

        Resources res = context.getResources();
        LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.candidates_strip, this);

        mPreviewPopup = new PopupWindow(context);
        mPreviewText = (TextView) inflater.inflate(R.layout.candidate_preview, null);
        mPreviewPopup.setWindowLayoutMode(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        mPreviewPopup.setContentView(mPreviewText);
        mPreviewPopup.setBackgroundDrawable(null);

        mCandidatesStrip = (ViewGroup)findViewById(R.id.candidates_strip);
        mCandidateStripHeight = res.getDimensionPixelOffset(R.dimen.candidate_strip_height);
        for (int i = 0; i < MAX_SUGGESTIONS; i++) {
            final TextView word = (TextView)inflater.inflate(R.layout.candidate_word, null);
            word.setTag(i);
            word.setOnClickListener(this);
            if (i == 0)
                word.setOnLongClickListener(this);
            mWords.add(word);
            mInfos.add((TextView)inflater.inflate(R.layout.candidate_info, null));
            mDividers.add(inflater.inflate(R.layout.candidate_divider, null));
        }

        mTouchToSave = findViewById(R.id.touch_to_save);
        mWordToSave = (TextView)findViewById(R.id.word_to_save);
        mWordToSave.setOnClickListener(this);

        mInvertedForegroundColorSpan = new ForegroundColorSpan(mColorTypedWord ^ 0x00ffffff);
        mInvertedBackgroundColorSpan = new BackgroundColorSpan(mColorTypedWord);

        mCandidatesPaneControl = (ViewGroup)findViewById(R.id.candidates_pane_control);
        mExpandCandidatesPane = (TextView)findViewById(R.id.expand_candidates_pane);
        mExpandCandidatesPane.getBackground().setAlpha(180);
        mExpandCandidatesPane.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                expandCandidatesPane();
            }
        });
        mCloseCandidatesPane = (TextView)findViewById(R.id.close_candidates_pane);
        mCloseCandidatesPane.getBackground().setAlpha(180);
        mCloseCandidatesPane.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                closeCandidatesPane();
            }
        });
        mCandidatesPaneControl.measure(WRAP_CONTENT, WRAP_CONTENT);

        mParams = new CandidateViewLayoutParams(
                res, mDividers.get(0), mCandidatesPaneControl, mAutoCorrectHighlight);
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

    private static CharSequence getStyledCandidateWord(CharSequence word, boolean isAutoCorrect,
            int autoCorrectHighlight) {
        if (!isAutoCorrect)
            return word;
        final Spannable spannedWord = new SpannableString(word);
        if ((autoCorrectHighlight & AUTO_CORRECT_BOLD) != 0)
            spannedWord.setSpan(BOLD_SPAN, 0, word.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        if ((autoCorrectHighlight & AUTO_CORRECT_UNDERLINE) != 0)
            spannedWord.setSpan(UNDERLINE_SPAN, 0, word.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        return spannedWord;
    }

    private int getCandidateTextColor(boolean isAutoCorrect, boolean isSuggestedCandidate,
            SuggestedWordInfo info) {
        final int color;
        if (isAutoCorrect) {
            color = mColorAutoCorrect;
        } else if (isSuggestedCandidate) {
            color = mColorSuggestedCandidate;
        } else {
            color = mColorTypedWord;
        }
        if (info != null && info.isPreviousSuggestedWord()) {
            final int newAlpha = (int)(Color.alpha(color) * 0.5f);
            return Color.argb(newAlpha, Color.red(color), Color.green(color), Color.blue(color));
        } else {
            return color;
        }
    }

    private void updateSuggestions() {
        final SuggestedWords suggestions = mSuggestions;
        final List<SuggestedWordInfo> suggestedWordInfoList = suggestions.mSuggestedWordInfoList;
        final int paneWidth = getWidth();
        final CandidateViewLayoutParams params = mParams;

        clear();
        closeCandidatesPane();
        if (suggestions.size() == 0)
            return;

        params.layoutStrip(suggestions, paneWidth, suggestions.isPunctuationSuggestions()
                ? PUNCTUATIONS_IN_STRIP : mCandidateCountInStrip);

        final int count = Math.min(mWords.size(), suggestions.size());
        if (count <= params.mCountInStrip && !DBG) {
            mCandidatesPaneControl.setVisibility(GONE);
        } else {
            mCandidatesPaneControl.setVisibility(VISIBLE);
            mExpandCandidatesPane.setVisibility(VISIBLE);
        }

        final int countInStrip = params.mCountInStrip;
        View centeringFrom = null, lastView = null;
        int x = 0, y = 0, infoX = 0;
        for (int i = 0; i < count; i++) {
            final int pos;
            if (i <= 1) {
                final boolean willAutoCorrect = !suggestions.mTypedWordValid
                        && suggestions.mHasMinimalSuggestion;
                pos = willAutoCorrect ? 1 - i : i;
            } else {
                pos = i;
            }
            final CharSequence suggestion = suggestions.getWord(pos);
            if (suggestion == null) continue;

            final SuggestedWordInfo suggestionInfo = (suggestedWordInfoList != null)
                    ? suggestedWordInfoList.get(pos) : null;
            final boolean isAutoCorrect = suggestions.mHasMinimalSuggestion
                    && ((pos == 1 && !suggestions.mTypedWordValid)
                            || (pos == 0 && suggestions.mTypedWordValid));
            // HACK: even if i == 0, we use mColorOther when this suggestion's length is 1
            // and there are multiple suggestions, such as the default punctuation list.
            // TODO: Need to revisit this logic with bigram suggestions
            final boolean isSuggestedCandidate = (pos != 0);
            final boolean isPunctuationSuggestions = (suggestion.length() == 1 && count > 1);

            final TextView word = mWords.get(pos);
            final TextPaint paint = word.getPaint();
            // TODO: Reorder candidates in strip as appropriate. The center candidate should hold
            // the word when space is typed (valid typed word or auto corrected word).
            word.setTextColor(getCandidateTextColor(isAutoCorrect,
                    isSuggestedCandidate || isPunctuationSuggestions, suggestionInfo));
            final CharSequence styled = params.mTexts.get(pos);

            final TextView info;
            if (DBG && suggestionInfo != null
                    && !TextUtils.isEmpty(suggestionInfo.getDebugString())) {
                info = mInfos.get(i);
                info.setText(suggestionInfo.getDebugString());
            } else {
                info = null;
            }

            final CharSequence text;
            final float scaleX;
            if (i < countInStrip) {
                if (i == 0 && params.mCountInStrip == 1) {
                    text = getEllipsizedText(styled, params.mMaxWidth, paint);
                    scaleX = paint.getTextScaleX();
                } else {
                    text = styled;
                    scaleX = params.mScaleX;
                }
                word.setText(text);
                word.setTextScaleX(scaleX);
                if (i != 0) {
                    // Add divider if this isn't the left most suggestion in candidate strip.
                    mCandidatesStrip.addView(mDividers.get(i));
                }
                mCandidatesStrip.addView(word);
                if (params.mCanUseFixedWidthColumns) {
                    setLayoutWeight(word, 1.0f, mCandidateStripHeight);
                } else {
                    final int width = getTextWidth(text, paint) + params.mPadding * 2;
                    setLayoutWeight(word, width, mCandidateStripHeight);
                }
                if (info != null) {
                    mCandidatesPane.addView(info);
                    info.measure(WRAP_CONTENT, WRAP_CONTENT);
                    final int width = info.getMeasuredWidth();
                    y = info.getMeasuredHeight();
                    FrameLayoutCompatUtils.placeViewAt(info, infoX, 0, width, y);
                    infoX += width * 2;
                }
            } else {
                paint.setTextScaleX(1.0f);
                final int textWidth = getTextWidth(styled, paint);
                int available = paneWidth - x - params.mPadding * 2;
                if (textWidth >= available) {
                    // Needs new row, centering previous row.
                    centeringCandidates(centeringFrom, lastView, x, paneWidth);
                    x = 0;
                    y += mCandidateStripHeight;
                }
                if (x != 0) {
                    // Add divider if this isn't the left most suggestion in current row.
                    final View divider = mDividers.get(i);
                    mCandidatesPane.addView(divider);
                    FrameLayoutCompatUtils.placeViewAt(
                            divider, x, y + (mCandidateStripHeight - params.mDividerHeight) / 2,
                            params.mDividerWidth, params.mDividerHeight);
                    x += params.mDividerWidth;
                }
                available = paneWidth - x - params.mPadding * 2;
                text = getEllipsizedText(styled, available, paint);
                scaleX = paint.getTextScaleX();
                word.setText(text);
                word.setTextScaleX(scaleX);
                mCandidatesPane.addView(word);
                lastView = word;
                if (x == 0) centeringFrom = word;
                word.measure(WRAP_CONTENT,
                        MeasureSpec.makeMeasureSpec(mCandidateStripHeight, MeasureSpec.EXACTLY));
                final int width = word.getMeasuredWidth();
                final int height = word.getMeasuredHeight();
                FrameLayoutCompatUtils.placeViewAt(
                        word, x, y + (mCandidateStripHeight - height) / 2, width, height);
                x += width;
                if (info != null) {
                    mCandidatesPane.addView(info);
                    lastView = info;
                    info.measure(WRAP_CONTENT, WRAP_CONTENT);
                    final int infoWidth = info.getMeasuredWidth();
                    FrameLayoutCompatUtils.placeViewAt(
                            info, x - infoWidth, y, infoWidth, info.getMeasuredHeight());
                }
            }
        }
        if (x != 0) {
            // Centering last candidates row.
            centeringCandidates(centeringFrom, lastView, x, paneWidth);
        }
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

    private void centeringCandidates(View from, View to, int width, int paneWidth) {
        final ViewGroup pane = mCandidatesPane;
        final int fromIndex = pane.indexOfChild(from);
        final int toIndex = pane.indexOfChild(to);
        final int offset = (paneWidth - width) / 2;
        for (int index = fromIndex; index <= toIndex; index++) {
            offsetMargin(pane.getChildAt(index), offset, 0);
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

    private static CharSequence getEllipsizedText(CharSequence text, int maxWidth,
            TextPaint paint) {
        paint.setTextScaleX(1.0f);
        final int width = getTextWidth(text, paint);
        final float scaleX = Math.min(maxWidth / (float)width, 1.0f);
        if (scaleX >= MIN_TEXT_XSCALE) {
            paint.setTextScaleX(scaleX);
            return text;
        }

        // Note that TextUtils.ellipsize() use text-x-scale as 1.0 if ellipsize is needed. To get
        // squeezed and ellipsezed text, passes enlarged width (maxWidth / MIN_TEXT_XSCALE).
        final CharSequence ellipsized = TextUtils.ellipsize(
                text, paint, maxWidth / MIN_TEXT_XSCALE, TextUtils.TruncateAt.MIDDLE);
        paint.setTextScaleX(MIN_TEXT_XSCALE);
        return ellipsized;
    }

    private static int getTextWidth(CharSequence text, TextPaint paint) {
        if (TextUtils.isEmpty(text)) return 0;
        paint.setTypeface(getTextTypeface(text));
        final int len = text.length();
        final float[] widths = new float[len];
        final int count = paint.getTextWidths(text, 0, len, widths);
        float width = 0;
        for (int i = 0; i < count; i++) {
            width += widths[i];
        }
        return (int)Math.round(width + 0.5);
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
        mExpandCandidatesPane.setVisibility(GONE);
        mCloseCandidatesPane.setVisibility(VISIBLE);
        mCandidatesPaneContainer.setMinimumHeight(mKeyboardView.getMeasuredHeight());
        mCandidatesPaneContainer.setVisibility(VISIBLE);
        mKeyboardView.setVisibility(GONE);
    }

    private void closeCandidatesPane() {
        mExpandCandidatesPane.setVisibility(VISIBLE);
        mCloseCandidatesPane.setVisibility(GONE);
        mCandidatesPaneContainer.setVisibility(GONE);
        mKeyboardView.setVisibility(VISIBLE);
    }

    public void onAutoCorrectionInverted(CharSequence autoCorrectedWord) {
        if ((mAutoCorrectHighlight & AUTO_CORRECT_INVERT) == 0)
            return;
        final TextView tv = mWords.get(1);
        final Spannable word = new SpannableString(autoCorrectedWord);
        final int wordLength = word.length();
        word.setSpan(mInvertedBackgroundColorSpan, 0, wordLength,
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        word.setSpan(mInvertedForegroundColorSpan, 0, wordLength,
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        tv.setText(word);
        mShowingAutoCorrectionInverted = true;
    }

    public boolean isShowingAddToDictionaryHint() {
        return mShowingAddToDictionary;
    }

    public void showAddToDictionaryHint(CharSequence word) {
        mWordToSave.setText(word);
        mShowingAddToDictionary = true;
        mCandidatesStrip.setVisibility(GONE);
        mTouchToSave.setVisibility(VISIBLE);
    }

    public boolean dismissAddToDictionaryHint() {
        if (!mShowingAddToDictionary) return false;
        clear();
        return true;
    }

    public SuggestedWords getSuggestions() {
        return mSuggestions;
    }

    public void clear() {
        mShowingAddToDictionary = false;
        mShowingAutoCorrectionInverted = false;
        mTouchToSave.setVisibility(GONE);
        mCandidatesStrip.setVisibility(VISIBLE);
        mCandidatesStrip.removeAllViews();
        mCandidatesPane.removeAllViews();
    }

    private void hidePreview() {
        mPreviewPopup.dismiss();
    }

    private void showPreview(int index, CharSequence word) {
        if (TextUtils.isEmpty(word))
            return;

        final TextView previewText = mPreviewText;
        previewText.setTextColor(mColorTypedWord);
        previewText.setText(word);
        previewText.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        View v = mWords.get(index);
        final int[] offsetInWindow = new int[2];
        v.getLocationInWindow(offsetInWindow);
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
            showPreview(0, getContext().getString(R.string.added_word, word));
        }
    }

    @Override
    public boolean onLongClick(View view) {
        final Object tag = view.getTag();
        if (!(tag instanceof Integer))
            return true;
        final int index = (Integer) tag;
        if (index >= mSuggestions.size())
            return true;

        final CharSequence word = mSuggestions.getWord(index);
        if (word.length() < 2)
            return false;
        addToDictionary(word);
        return true;
    }

    @Override
    public void onClick(View view) {
        if (view == mWordToSave) {
            addToDictionary(((TextView)view).getText());
            clear();
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
