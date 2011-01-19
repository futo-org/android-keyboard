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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;

public class CandidateView extends LinearLayout implements OnClickListener, OnLongClickListener {

    private static final CharacterStyle BOLD_SPAN = new StyleSpan(Typeface.BOLD);
    private static final CharacterStyle UNDERLINE_SPAN = new UnderlineSpan();
    private static final int MAX_SUGGESTIONS = 16;

    private final ArrayList<View> mWords = new ArrayList<View>();
    private final boolean mConfigCandidateHighlightFontColorEnabled;
    private final CharacterStyle mInvertedForegroundColorSpan;
    private final CharacterStyle mInvertedBackgroundColorSpan;
    private final int mColorNormal;
    private final int mColorRecommended;
    private final int mColorOther;
    private final PopupWindow mPreviewPopup;
    private final TextView mPreviewText;

    private LatinIME mService;
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
        mPreviewPopup.setAnimationStyle(R.style.KeyPreviewAnimation);
        mConfigCandidateHighlightFontColorEnabled =
                res.getBoolean(R.bool.config_candidate_highlight_font_color_enabled);
        mColorNormal = res.getColor(R.color.candidate_normal);
        mColorRecommended = res.getColor(R.color.candidate_recommended);
        mColorOther = res.getColor(R.color.candidate_other);
        mInvertedForegroundColorSpan = new ForegroundColorSpan(mColorNormal ^ 0x00ffffff);
        mInvertedBackgroundColorSpan = new BackgroundColorSpan(mColorNormal);

        for (int i = 0; i < MAX_SUGGESTIONS; i++) {
            View v = inflater.inflate(R.layout.candidate, null);
            TextView tv = (TextView)v.findViewById(R.id.candidate_word);
            tv.setTag(i);
            tv.setOnClickListener(this);
            if (i == 0)
                tv.setOnLongClickListener(this);
            ImageView divider = (ImageView)v.findViewById(R.id.candidate_divider);
            // Do not display divider of first candidate.
            divider.setVisibility(i == 0 ? GONE : VISIBLE);
            mWords.add(v);
        }

        scrollTo(0, getScrollY());
    }

    /**
     * A connection back to the service to communicate with the text field
     * @param listener
     */
    public void setService(LatinIME listener) {
        mService = listener;
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
        final SuggestedWords suggestions = mSuggestions;
        clear();
        final int count = suggestions.size();
        final Object[] debugInfo = suggestions.mDebugInfo;
        for (int i = 0; i < count; i++) {
            CharSequence word = suggestions.getWord(i);
            if (word == null) continue;
            final int wordLength = word.length();

            final View v = mWords.get(i);
            final TextView tv = (TextView)v.findViewById(R.id.candidate_word);
            final TextView dv = (TextView)v.findViewById(R.id.candidate_debug_info);
            tv.setTextColor(mColorNormal);
            // TODO: Needs safety net?
            if (suggestions.mHasMinimalSuggestion
                    && ((i == 1 && !suggestions.mTypedWordValid)
                            || (i == 0 && suggestions.mTypedWordValid))) {
                final CharacterStyle style;
                if (mConfigCandidateHighlightFontColorEnabled) {
                    style = BOLD_SPAN;
                    tv.setTextColor(mColorRecommended);
                } else {
                    style = UNDERLINE_SPAN;
                }
                final Spannable spannedWord = new SpannableString(word);
                spannedWord.setSpan(style, 0, wordLength, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
                word = spannedWord;
            } else if (i != 0 || (wordLength == 1 && count > 1)) {
                // HACK: even if i == 0, we use mColorOther when this
                // suggestion's length is 1
                // and there are multiple suggestions, such as the default
                // punctuation list.
                if (mConfigCandidateHighlightFontColorEnabled)
                    tv.setTextColor(mColorOther);
            }
            tv.setText(word);
            tv.setClickable(true);
            if (debugInfo != null && i < debugInfo.length && debugInfo[i] != null
                    && !TextUtils.isEmpty(debugInfo[i].toString())) {
                dv.setText(debugInfo[i].toString());
                dv.setVisibility(VISIBLE);
            } else {
                dv.setVisibility(GONE);
            }
            addView(v);
        }

        scrollTo(0, getScrollY());
        requestLayout();
    }

    public void onAutoCorrectionInverted(CharSequence autoCorrectedWord) {
        // Displaying auto corrected word as inverted is enabled only when highlighting candidate
        // with color is disabled.
        if (mConfigCandidateHighlightFontColorEnabled)
            return;
        final TextView tv = (TextView)mWords.get(1).findViewById(R.id.candidate_word);
        final Spannable word = new SpannableString(autoCorrectedWord);
        final int wordLength = word.length();
        word.setSpan(mInvertedBackgroundColorSpan, 0, wordLength, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        word.setSpan(mInvertedForegroundColorSpan, 0, wordLength, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
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
        TextView tv = (TextView)getChildAt(1).findViewById(R.id.candidate_word);
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
        previewText.setTextColor(mColorNormal);
        previewText.setText(word);
        previewText.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        View v = getChildAt(index);
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
        if (mService.addWordToDictionary(word.toString())) {
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
            if (!mSuggestions.mIsApplicationSpecifiedCompletions) {
                TextEntryState.acceptedSuggestion(mSuggestions.getWord(0), word);
            }
            mService.pickSuggestionManually(index, word);
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mHandler.cancelAllMessages();
        hidePreview();
    }
}
