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
import android.widget.ImageView;
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
    private static final int UNSPECIFIED_MEASURESPEC = MeasureSpec.makeMeasureSpec(
            0, MeasureSpec.UNSPECIFIED);

    private static final boolean DBG = LatinImeLogger.sDBG;

    private final View mCandidatesStrip;
    private static final int NUM_CANDIDATES_IN_STRIP = 3;
    private final ImageView mExpandCandidatesPane;
    private final ImageView mCloseCandidatesPane;
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

        Resources res = context.getResources();
        LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.candidates_strip, this);

        mPreviewPopup = new PopupWindow(context);
        mPreviewText = (TextView) inflater.inflate(R.layout.candidate_preview, null);
        mPreviewPopup.setWindowLayoutMode(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        mPreviewPopup.setContentView(mPreviewText);
        mPreviewPopup.setBackgroundDrawable(null);

        mCandidatesStrip = findViewById(R.id.candidates_strip);
        mCandidateStripHeight = res.getDimensionPixelOffset(R.dimen.candidate_strip_height);
        for (int i = 0; i < MAX_SUGGESTIONS; i++) {
            final TextView word, info;
            switch (i) {
            case 0:
                word = (TextView)findViewById(R.id.word_left);
                word.setPadding(res.getDimensionPixelOffset(R.dimen.candidate_padding), 0, 0, 0);
                info = (TextView)findViewById(R.id.info_left);
                break;
            case 1:
                word = (TextView)findViewById(R.id.word_center);
                info = (TextView)findViewById(R.id.info_center);
                break;
            case 2:
                word = (TextView)findViewById(R.id.word_right);
                info = (TextView)findViewById(R.id.info_right);
                break;
            default:
                word = (TextView)inflater.inflate(R.layout.candidate_word, null);
                info = (TextView)inflater.inflate(R.layout.candidate_info, null);
                break;
            }
            word.setTag(i);
            word.setOnClickListener(this);
            if (i == 0)
                word.setOnLongClickListener(this);
            mWords.add(word);
            mInfos.add(info);
            if (i > 0) {
                final View divider = inflater.inflate(R.layout.candidate_divider, null);
                divider.measure(UNSPECIFIED_MEASURESPEC, UNSPECIFIED_MEASURESPEC);
                mDividers.add(divider);
            }
        }

        mTouchToSave = findViewById(R.id.touch_to_save);
        mWordToSave = (TextView)findViewById(R.id.word_to_save);
        mWordToSave.setOnClickListener(this);

        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.CandidateView, defStyle, R.style.CandidateViewStyle);
        mAutoCorrectHighlight = a.getInt(R.styleable.CandidateView_autoCorrectHighlight, 0);
        mColorTypedWord = a.getColor(R.styleable.CandidateView_colorTypedWord, 0);
        mColorAutoCorrect = a.getColor(R.styleable.CandidateView_colorAutoCorrect, 0);
        mColorSuggestedCandidate = a.getColor(R.styleable.CandidateView_colorSuggested, 0);
        mInvertedForegroundColorSpan = new ForegroundColorSpan(mColorTypedWord ^ 0x00ffffff);
        mInvertedBackgroundColorSpan = new BackgroundColorSpan(mColorTypedWord);

        mExpandCandidatesPane = (ImageView)findViewById(R.id.expand_candidates_pane);
        mExpandCandidatesPane.setImageDrawable(
                a.getDrawable(R.styleable.CandidateView_iconExpandPane));
        mExpandCandidatesPane.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                expandCandidatesPane();
            }
        });
        mCloseCandidatesPane = (ImageView)findViewById(R.id.close_candidates_pane);
        mCloseCandidatesPane.setImageDrawable(
                a.getDrawable(R.styleable.CandidateView_iconClosePane));
        mCloseCandidatesPane.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                closeCandidatesPane();
            }
        });

        a.recycle();
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

    private CharSequence getStyledCandidateWord(CharSequence word, boolean isAutoCorrect) {
        if (!isAutoCorrect)
            return word;
        final Spannable spannedWord = new SpannableString(word);
        if ((mAutoCorrectHighlight & AUTO_CORRECT_BOLD) != 0)
            spannedWord.setSpan(BOLD_SPAN, 0, word.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        if ((mAutoCorrectHighlight & AUTO_CORRECT_UNDERLINE) != 0)
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

        clear();
        final int paneWidth = getWidth();
        final int dividerWidth = mDividers.get(0).getMeasuredWidth();
        final int dividerHeight = mDividers.get(0).getMeasuredHeight();
        int x = 0;
        int y = 0;
        int fromIndex = NUM_CANDIDATES_IN_STRIP;
        final int count = Math.min(mWords.size(), suggestions.size());
        closeCandidatesPane();
        mExpandCandidatesPane.setEnabled(count >= NUM_CANDIDATES_IN_STRIP);
        for (int i = 0; i < count; i++) {
            final CharSequence suggestion = suggestions.getWord(i);
            if (suggestion == null) continue;

            final SuggestedWordInfo suggestionInfo = (suggestedWordInfoList != null)
                    ? suggestedWordInfoList.get(i) : null;
            final boolean isAutoCorrect = suggestions.mHasMinimalSuggestion
                    && ((i == 1 && !suggestions.mTypedWordValid)
                            || (i == 0 && suggestions.mTypedWordValid));
            // HACK: even if i == 0, we use mColorOther when this suggestion's length is 1
            // and there are multiple suggestions, such as the default punctuation list.
            // TODO: Need to revisit this logic with bigram suggestions
            final boolean isSuggestedCandidate = (i != 0);
            final boolean isPunctuationSuggestions = (suggestion.length() == 1 && count > 1);

            final TextView word = mWords.get(i);
            // TODO: Reorder candidates in strip as appropriate. The center candidate should hold
            // the word when space is typed (valid typed word or auto corrected word).
            word.setTextColor(getCandidateTextColor(isAutoCorrect,
                    isSuggestedCandidate || isPunctuationSuggestions, suggestionInfo));
            word.setText(getStyledCandidateWord(suggestion, isAutoCorrect));
            // TODO: call TextView.setTextScaleX() to fit the candidate in single line.
            word.measure(UNSPECIFIED_MEASURESPEC, UNSPECIFIED_MEASURESPEC);
            final int width = word.getMeasuredWidth();
            final int height = word.getMeasuredHeight();

            final TextView info;
            if (DBG && suggestionInfo != null
                    && !TextUtils.isEmpty(suggestionInfo.getDebugString())) {
                info = mInfos.get(i);
                info.setText(suggestionInfo.getDebugString());
                info.setVisibility(View.VISIBLE);
                info.measure(UNSPECIFIED_MEASURESPEC, UNSPECIFIED_MEASURESPEC);
            } else {
                info = null;
            }

            if (i < NUM_CANDIDATES_IN_STRIP) {
                if (info != null) {
                    final int infoWidth = info.getMeasuredWidth();
                    FrameLayoutCompatUtils.placeViewAt(
                            info, width - infoWidth, 0, infoWidth, info.getMeasuredHeight());
                }
            } else {
                // TODO: Handle overflow case.
                if (dividerWidth + x + width >= paneWidth) {
                    centeringCandidates(fromIndex, i - 1, x, paneWidth);
                    x = 0;
                    y += mCandidateStripHeight;
                    fromIndex = i;
                }
                if (x != 0) {
                    final View divider = mDividers.get(i - NUM_CANDIDATES_IN_STRIP);
                    mCandidatesPane.addView(divider);
                    FrameLayoutCompatUtils.placeViewAt(
                            divider, x, y + (mCandidateStripHeight - dividerHeight) / 2,
                            dividerWidth, dividerHeight);
                    x += dividerWidth;
                }
                mCandidatesPane.addView(word);
                FrameLayoutCompatUtils.placeViewAt(
                        word, x, y + (mCandidateStripHeight - height) / 2, width, height);
                if (info != null) {
                    mCandidatesPane.addView(info);
                    final int infoWidth = info.getMeasuredWidth();
                    FrameLayoutCompatUtils.placeViewAt(
                            info, x + width - infoWidth, y, infoWidth, info.getMeasuredHeight());
                }
                x += width;
            }
        }
        if (x != 0) {
            // Centering last candidates row.
            centeringCandidates(fromIndex, count - 1, x, paneWidth);
        }
    }

    private void centeringCandidates(int from, int to, int width, int paneWidth) {
        final ViewGroup pane = mCandidatesPane;
        final int fromIndex = pane.indexOfChild(mWords.get(from));
        final int toIndex;
        if (mInfos.get(to).getParent() != null) {
            toIndex = pane.indexOfChild(mInfos.get(to));
        } else {
            toIndex = pane.indexOfChild(mWords.get(to));
        }
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

    private void expandCandidatesPane() {
        mExpandCandidatesPane.setVisibility(View.GONE);
        mCloseCandidatesPane.setVisibility(View.VISIBLE);
        mCandidatesPaneContainer.setMinimumHeight(mKeyboardView.getMeasuredHeight());
        mCandidatesPaneContainer.setVisibility(View.VISIBLE);
        mKeyboardView.setVisibility(View.GONE);
    }

    private void closeCandidatesPane() {
        mExpandCandidatesPane.setVisibility(View.VISIBLE);
        mCloseCandidatesPane.setVisibility(View.GONE);
        mCandidatesPaneContainer.setVisibility(View.GONE);
        mKeyboardView.setVisibility(View.VISIBLE);
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
        mCandidatesStrip.setVisibility(View.GONE);
        mTouchToSave.setVisibility(View.VISIBLE);
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
        for (int i = 0; i < NUM_CANDIDATES_IN_STRIP; i++) {
            mWords.get(i).setText(null);
            mInfos.get(i).setVisibility(View.GONE);
        }
        mTouchToSave.setVisibility(View.GONE);
        mCandidatesStrip.setVisibility(View.VISIBLE);
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
