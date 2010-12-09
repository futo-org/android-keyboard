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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class CandidateView extends LinearLayout implements OnClickListener, OnLongClickListener {
    private LatinIME mService;
    private final ArrayList<CharSequence> mSuggestions = new ArrayList<CharSequence>();
    private final ArrayList<View> mWords = new ArrayList<View>();

    private final TextView mPreviewText;
    private final PopupWindow mPreviewPopup;
    
    private static final int MAX_SUGGESTIONS = 16;

    private final boolean mConfigCandidateHighlightFontColorEnabled;
    private final int mColorNormal;
    private final int mColorRecommended;
    private final int mColorOther;
    private static final CharacterStyle BOLD_SPAN = new StyleSpan(Typeface.BOLD);
    private static final CharacterStyle UNDERLINE_SPAN = new UnderlineSpan();
    private final CharacterStyle mInvertedForegroundColorSpan;
    private final CharacterStyle mInvertedBackgroundColorSpan;

    private boolean mShowingCompletions;
    private boolean mShowingAutoCorrectionInverted;

    private boolean mShowingAddToDictionary;

    private final UiHandler mHandler = new UiHandler();

    private static class UpdateSuggestionsArgs {
        public final List<CharSequence> mSuggestions;
        public final boolean mCompletions;
        public final boolean mTypedWordValid;
        public final boolean mHaveMinimalSuggestion;
        public UpdateSuggestionsArgs(List<CharSequence> suggestions, boolean completions,
                boolean typedWordValid, boolean haveMinimalSuggestion) {
            mSuggestions = suggestions;
            mCompletions = completions;
            mTypedWordValid = typedWordValid;
            mHaveMinimalSuggestion = haveMinimalSuggestion;
        }
    }

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
                UpdateSuggestionsArgs args = (UpdateSuggestionsArgs)msg.obj;
                updateSuggestions(args.mSuggestions, args.mCompletions, args.mTypedWordValid,
                        args.mHaveMinimalSuggestion);
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

        public void postUpdateSuggestions(List<CharSequence> suggestions, boolean completions,
                boolean typedWordValid, boolean haveMinimalSuggestion) {
            cancelUpdateSuggestions();
            sendMessageDelayed(obtainMessage(MSG_UPDATE_SUGGESTION, new UpdateSuggestionsArgs(
                    suggestions, completions, typedWordValid, haveMinimalSuggestion)),
                    DELAY_UPDATE_SUGGESTION);
        }

        public void cancelUpdateSuggestions() {
            removeMessages(MSG_UPDATE_SUGGESTION);
        }

        public void cancelAllMessages() {
            cancelHidePreview();
            cancelUpdateSuggestions();
        }
    };

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
        mPreviewPopup.setWindowLayoutMode(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
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
            divider.setVisibility(i == 0 ? View.GONE : View.VISIBLE);
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

    public void setSuggestions(List<CharSequence> suggestions, boolean completions,
            boolean typedWordValid, boolean haveMinimalSuggestion) {
        if (mShowingAutoCorrectionInverted) {
            mHandler.postUpdateSuggestions(suggestions, completions, typedWordValid,
                    haveMinimalSuggestion);
        } else {
            updateSuggestions(suggestions, completions, typedWordValid, haveMinimalSuggestion);
        }
    }

    private void updateSuggestions(List<CharSequence> suggestions, boolean completions,
            boolean typedWordValid, boolean haveMinimalSuggestion) {
        clear();
        if (suggestions != null) {
            int insertCount = Math.min(suggestions.size(), MAX_SUGGESTIONS);
            for (CharSequence suggestion : suggestions) {
                mSuggestions.add(suggestion);
                if (--insertCount == 0)
                    break;
            }
        }

        final int count = mSuggestions.size();
        boolean existsAutoCompletion = false;

        for (int i = 0; i < count; i++) {
            CharSequence suggestion = mSuggestions.get(i);
            if (suggestion == null) continue;
            final int wordLength = suggestion.length();

            final View v = mWords.get(i);
            final TextView tv = (TextView)v.findViewById(R.id.candidate_word);
            tv.setTextColor(mColorNormal);
            if (haveMinimalSuggestion
                    && ((i == 1 && !typedWordValid) || (i == 0 && typedWordValid))) {
                final CharacterStyle style;
                if (mConfigCandidateHighlightFontColorEnabled) {
                    style = BOLD_SPAN;
                    tv.setTextColor(mColorRecommended);
                } else {
                    style = UNDERLINE_SPAN;
                }
                final Spannable word = new SpannableString(suggestion);
                word.setSpan(style, 0, wordLength, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                suggestion = word;
                existsAutoCompletion = true;
            } else if (i != 0 || (wordLength == 1 && count > 1)) {
                // HACK: even if i == 0, we use mColorOther when this
                // suggestion's length is 1
                // and there are multiple suggestions, such as the default
                // punctuation list.
                if (mConfigCandidateHighlightFontColorEnabled)
                    tv.setTextColor(mColorOther);
            }
            tv.setText(suggestion);
            tv.setClickable(true);
            addView(v);
        }

        mShowingCompletions = completions;
        // TODO: Move this call back to LatinIME
        if (mConfigCandidateHighlightFontColorEnabled)
            mService.onAutoCompletionStateChanged(existsAutoCompletion);

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
        word.setSpan(mInvertedBackgroundColorSpan, 0, wordLength,
                Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        word.setSpan(mInvertedForegroundColorSpan, 0, wordLength,
                Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        tv.setText(word);
        mShowingAutoCorrectionInverted = true;
    }

    public boolean isShowingAddToDictionaryHint() {
        return mShowingAddToDictionary;
    }

    public void showAddToDictionaryHint(CharSequence word) {
        ArrayList<CharSequence> suggestions = new ArrayList<CharSequence>();
        suggestions.add(word);
        suggestions.add(getContext().getText(R.string.hint_add_to_dictionary));
        setSuggestions(suggestions, false, false, false);
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

    /* package */ List<CharSequence> getSuggestions() {
        return mSuggestions;
    }

    public void clear() {
        // Don't call mSuggestions.clear() because it's being used for logging
        // in LatinIME.pickSuggestionManually().
        mSuggestions.clear();
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
        CharSequence word = mSuggestions.get(index);
        if (word.length() < 2)
            return false;
        addToDictionary(word);
        return true;
    }

    @Override
    public void onClick(View view) {
        int index = (Integer) view.getTag();
        CharSequence word = mSuggestions.get(index);
        if (mShowingAddToDictionary && index == 0) {
            addToDictionary(word);
        } else {
            if (!mShowingCompletions) {
                TextEntryState.acceptedSuggestion(mSuggestions.get(0), word);
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
