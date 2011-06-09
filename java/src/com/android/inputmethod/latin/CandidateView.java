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
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
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
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

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
    private static final int MAX_SUGGESTIONS = 16;

    private static final boolean DBG = LatinImeLogger.sDBG;

    private final ArrayList<TextView> mWords = new ArrayList<TextView>();
    private final ArrayList<View> mDividers = new ArrayList<View>();
    private final int mCandidatePadding;
    private final boolean mConfigCandidateHighlightFontColorEnabled;
    private final CharacterStyle mInvertedForegroundColorSpan;
    private final CharacterStyle mInvertedBackgroundColorSpan;
    private final int mColorTypedWord;
    private final int mColorAutoCorrect;
    private final int mColorSuggestedCandidate;
    private final PopupWindow mPreviewPopup;
    private final TextView mPreviewText;

    private Listener mListener;
    private SuggestedWords mSuggestions = SuggestedWords.EMPTY;
    private boolean mShowingAutoCorrectionInverted;
    private boolean mShowingAddToDictionary;

    private final UiHandler mHandler = new UiHandler();

    private class UiHandler extends Handler {
        private static final int MSG_HIDE_PREVIEW = 0;
        private static final int MSG_UPDATE_SUGGESTION = 1;

        private static final long DELAY_HIDE_PREVIEW = 1000;
        private static final long DELAY_UPDATE_SUGGESTION = 300;

        @Override
        public void dispatchMessage(Message msg) {
            switch (msg.what) {
            case MSG_HIDE_PREVIEW:
                hidePreview();
                break;
            case MSG_UPDATE_SUGGESTION:
                updateSuggestions();
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
        super(context, attrs);

        Resources res = context.getResources();
        mPreviewPopup = new PopupWindow(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        mPreviewText = (TextView) inflater.inflate(R.layout.candidate_preview, null);
        mPreviewPopup.setWindowLayoutMode(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        mPreviewPopup.setContentView(mPreviewText);
        mPreviewPopup.setBackgroundDrawable(null);
        mConfigCandidateHighlightFontColorEnabled =
                res.getBoolean(R.bool.config_candidate_highlight_font_color_enabled);
        mColorTypedWord = res.getColor(R.color.candidate_typed_word);
        mColorAutoCorrect = res.getColor(R.color.candidate_auto_correct);
        mColorSuggestedCandidate = res.getColor(R.color.candidate_suggested);
        mInvertedForegroundColorSpan = new ForegroundColorSpan(mColorTypedWord ^ 0x00ffffff);
        mInvertedBackgroundColorSpan = new BackgroundColorSpan(mColorTypedWord);

        mCandidatePadding = res.getDimensionPixelOffset(R.dimen.candidate_padding);
        for (int i = 0; i < MAX_SUGGESTIONS; i++) {
            final TextView tv = (TextView)inflater.inflate(R.layout.candidate, null);
            tv.setTag(i);
            tv.setOnClickListener(this);
            if (i == 0)
                tv.setOnLongClickListener(this);
            mWords.add(tv);
            if (i > 0) {
                View divider = inflater.inflate(R.layout.candidate_divider, null);
                mDividers.add(divider);
            }
        }

        scrollTo(0, getScrollY());
    }

    /**
     * A connection back to the input method.
     * @param listener
     */
    public void setListener(Listener listener) {
        mListener = listener;
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
        final CharacterStyle style = mConfigCandidateHighlightFontColorEnabled ? BOLD_SPAN
                : UNDERLINE_SPAN;
        final Spannable spannedWord = new SpannableString(word);
        spannedWord.setSpan(style, 0, word.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        return spannedWord;
    }

    private int getCandidateTextColor(boolean isAutoCorrect, boolean isSuggestedCandidate,
            SuggestedWordInfo info) {
        final int color;
        if (isAutoCorrect && mConfigCandidateHighlightFontColorEnabled) {
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
        final int count = Math.min(mWords.size(), suggestions.size());
        for (int i = 0; i < count; i++) {
            final CharSequence word = suggestions.getWord(i);
            if (word == null) continue;

            final SuggestedWordInfo info = (suggestedWordInfoList != null)
                    ? suggestedWordInfoList.get(i) : null;
            final boolean isAutoCorrect = suggestions.mHasMinimalSuggestion
                    && ((i == 1 && !suggestions.mTypedWordValid)
                            || (i == 0 && suggestions.mTypedWordValid));
            // HACK: even if i == 0, we use mColorOther when this suggestion's length is 1
            // and there are multiple suggestions, such as the default punctuation list.
            // TODO: Need to revisit this logic with bigram suggestions
            final boolean isSuggestedCandidate = (i != 0);
            final boolean isPunctuationSuggestions = (word.length() == 1 && count > 1);

            final TextView tv = mWords.get(i);
            tv.setTextColor(getCandidateTextColor(isAutoCorrect,
                    isSuggestedCandidate || isPunctuationSuggestions, info));
            tv.setText(getStyledCandidateWord(word, isAutoCorrect));
            if (i == 0) {
                tv.setPadding(mCandidatePadding, 0, 0, 0);
            } else if (i == count - 1) {
                tv.setPadding(0, 0, mCandidatePadding, 0);
            } else {
                tv.setPadding(0, 0, 0, 0);
            }
            if (i > 0)
                addView(mDividers.get(i - 1));
            addView(tv);

            if (DBG && info != null) {
                final TextView dv = new TextView(getContext(), null);
                dv.setTextSize(10.0f);
                dv.setTextColor(0xff808080);
                dv.setText(info.getDebugString());
                addView(dv);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)dv.getLayoutParams();
                lp.gravity = Gravity.BOTTOM;
            }
        }

        scrollTo(0, getScrollY());
        requestLayout();
    }

    public void onAutoCorrectionInverted(CharSequence autoCorrectedWord) {
        // Displaying auto corrected word as inverted is enabled only when highlighting candidate
        // with color is disabled.
        if (mConfigCandidateHighlightFontColorEnabled)
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

    public boolean isConfigCandidateHighlightFontColorEnabled() {
        return mConfigCandidateHighlightFontColorEnabled;
    }

    public boolean isShowingAddToDictionaryHint() {
        return mShowingAddToDictionary;
    }

    public void showAddToDictionaryHint(CharSequence word) {
        SuggestedWords.Builder builder = new SuggestedWords.Builder()
                .addWord(word)
                .addWord(getContext().getText(R.string.hint_add_to_dictionary));
        setSuggestions(builder.build());
        mShowingAddToDictionary = true;
        // Disable R.string.hint_add_to_dictionary button
        TextView tv = mWords.get(1);
        tv.setClickable(false);
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
        removeAllViews();
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
        int index = (Integer) view.getTag();
        CharSequence word = mSuggestions.getWord(index);
        if (word.length() < 2)
            return false;
        addToDictionary(word);
        return true;
    }

    @Override
    public void onClick(View view) {
        int index = (Integer) view.getTag();
        CharSequence word = mSuggestions.getWord(index);
        if (mShowingAddToDictionary && index == 0) {
            addToDictionary(word);
        } else {
            mListener.pickSuggestionManually(index, word);
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mHandler.cancelAllMessages();
        hidePreview();
    }
}
