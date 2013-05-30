/*
 * Copyright (C) 2013 The Android Open Source Project
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
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.Paint.Align;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.inputmethod.keyboard.ViewLayoutUtils;
import com.android.inputmethod.latin.AutoCorrection;
import com.android.inputmethod.latin.CollectionUtils;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.ResourceUtils;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.Utils;

import java.util.ArrayList;

final class SuggestionStripLayoutHelper {
    private static final int DEFAULT_SUGGESTIONS_COUNT_IN_STRIP = 3;
    private static final float DEFAULT_CENTER_SUGGESTION_PERCENTILE = 0.40f;
    private static final int DEFAULT_MAX_MORE_SUGGESTIONS_ROW = 2;
    private static final int PUNCTUATIONS_IN_STRIP = 5;
    private static final float MIN_TEXT_XSCALE = 0.70f;

    public final int mPadding;
    public final int mDividerWidth;
    public final int mSuggestionsStripHeight;
    public final int mSuggestionsCountInStrip;
    public final int mMoreSuggestionsRowHeight;
    private int mMaxMoreSuggestionsRow;
    public final float mMinMoreSuggestionsWidth;
    public final int mMoreSuggestionsBottomGap;

    private final ArrayList<TextView> mWords;
    private final ArrayList<View> mDividers;
    private final ArrayList<TextView> mInfos;

    private final int mColorValidTypedWord;
    private final int mColorTypedWord;
    private final int mColorAutoCorrect;
    private final int mColorSuggested;
    private final float mAlphaObsoleted;
    private final float mCenterSuggestionWeight;
    private final int mCenterSuggestionIndex;
    private final Drawable mMoreSuggestionsHint;
    private static final String MORE_SUGGESTIONS_HINT = "\u2026";
    private static final String LEFTWARDS_ARROW = "\u2190";

    private static final CharacterStyle BOLD_SPAN = new StyleSpan(Typeface.BOLD);
    private static final CharacterStyle UNDERLINE_SPAN = new UnderlineSpan();
    private static final int AUTO_CORRECT_BOLD = 0x01;
    private static final int AUTO_CORRECT_UNDERLINE = 0x02;
    private static final int VALID_TYPED_WORD_BOLD = 0x04;

    private final int mSuggestionStripOption;

    private final ArrayList<CharSequence> mTexts = CollectionUtils.newArrayList();

    public boolean mMoreSuggestionsAvailable;

    private final TextView mWordToSaveView;
    private final TextView mLeftwardsArrowView;
    private final TextView mHintToSaveView;

    public SuggestionStripLayoutHelper(final Context context, final AttributeSet attrs,
            final int defStyle, final ArrayList<TextView> words, final ArrayList<View> dividers,
            final ArrayList<TextView> infos) {
        mWords = words;
        mDividers = dividers;
        mInfos = infos;

        final TextView word = words.get(0);
        final View divider = dividers.get(0);
        mPadding = word.getCompoundPaddingLeft() + word.getCompoundPaddingRight();
        divider.measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mDividerWidth = divider.getMeasuredWidth();

        final Resources res = word.getResources();
        mSuggestionsStripHeight = res.getDimensionPixelSize(R.dimen.suggestions_strip_height);

        final TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.SuggestionStripView, defStyle, R.style.SuggestionStripViewStyle);
        mSuggestionStripOption = a.getInt(
                R.styleable.SuggestionStripView_suggestionStripOption, 0);
        final float alphaValidTypedWord = ResourceUtils.getFraction(a,
                R.styleable.SuggestionStripView_alphaValidTypedWord, 1.0f);
        final float alphaTypedWord = ResourceUtils.getFraction(a,
                R.styleable.SuggestionStripView_alphaTypedWord, 1.0f);
        final float alphaAutoCorrect = ResourceUtils.getFraction(a,
                R.styleable.SuggestionStripView_alphaAutoCorrect, 1.0f);
        final float alphaSuggested = ResourceUtils.getFraction(a,
                R.styleable.SuggestionStripView_alphaSuggested, 1.0f);
        mAlphaObsoleted = ResourceUtils.getFraction(a,
                R.styleable.SuggestionStripView_alphaSuggested, 1.0f);
        mColorValidTypedWord = applyAlpha(a.getColor(
                R.styleable.SuggestionStripView_colorValidTypedWord, 0), alphaValidTypedWord);
        mColorTypedWord = applyAlpha(a.getColor(
                R.styleable.SuggestionStripView_colorTypedWord, 0), alphaTypedWord);
        mColorAutoCorrect = applyAlpha(a.getColor(
                R.styleable.SuggestionStripView_colorAutoCorrect, 0), alphaAutoCorrect);
        mColorSuggested = applyAlpha(a.getColor(
                R.styleable.SuggestionStripView_colorSuggested, 0), alphaSuggested);
        mSuggestionsCountInStrip = a.getInt(
                R.styleable.SuggestionStripView_suggestionsCountInStrip,
                DEFAULT_SUGGESTIONS_COUNT_IN_STRIP);
        mCenterSuggestionWeight = ResourceUtils.getFraction(a,
                R.styleable.SuggestionStripView_centerSuggestionPercentile,
                DEFAULT_CENTER_SUGGESTION_PERCENTILE);
        mMaxMoreSuggestionsRow = a.getInt(
                R.styleable.SuggestionStripView_maxMoreSuggestionsRow,
                DEFAULT_MAX_MORE_SUGGESTIONS_ROW);
        mMinMoreSuggestionsWidth = ResourceUtils.getFraction(a,
                R.styleable.SuggestionStripView_minMoreSuggestionsWidth, 1.0f);
        a.recycle();

        mMoreSuggestionsHint = getMoreSuggestionsHint(res,
                res.getDimension(R.dimen.more_suggestions_hint_text_size), mColorAutoCorrect);
        mCenterSuggestionIndex = mSuggestionsCountInStrip / 2;
        mMoreSuggestionsBottomGap = res.getDimensionPixelOffset(
                R.dimen.more_suggestions_bottom_gap);
        mMoreSuggestionsRowHeight = res.getDimensionPixelSize(R.dimen.more_suggestions_row_height);

        final LayoutInflater inflater = LayoutInflater.from(context);
        mWordToSaveView = (TextView)inflater.inflate(R.layout.suggestion_word, null);
        mLeftwardsArrowView = (TextView)inflater.inflate(R.layout.hint_add_to_dictionary, null);
        mHintToSaveView = (TextView)inflater.inflate(R.layout.hint_add_to_dictionary, null);
    }

    public int getMaxMoreSuggestionsRow() {
        return mMaxMoreSuggestionsRow;
    }

    private int getMoreSuggestionsHeight() {
        return mMaxMoreSuggestionsRow * mMoreSuggestionsRowHeight + mMoreSuggestionsBottomGap;
    }

    public int setMoreSuggestionsHeight(final int remainingHeight) {
        final int currentHeight = getMoreSuggestionsHeight();
        if (currentHeight <= remainingHeight) {
            return currentHeight;
        }

        mMaxMoreSuggestionsRow = (remainingHeight - mMoreSuggestionsBottomGap)
                / mMoreSuggestionsRowHeight;
        final int newHeight = getMoreSuggestionsHeight();
        return newHeight;
    }

    private static Drawable getMoreSuggestionsHint(final Resources res, final float textSize,
            final int color) {
        final Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextAlign(Align.CENTER);
        paint.setTextSize(textSize);
        paint.setColor(color);
        final Rect bounds = new Rect();
        paint.getTextBounds(MORE_SUGGESTIONS_HINT, 0, MORE_SUGGESTIONS_HINT.length(), bounds);
        final int width = Math.round(bounds.width() + 0.5f);
        final int height = Math.round(bounds.height() + 0.5f);
        final Bitmap buffer = Bitmap.createBitmap(width, (height * 3 / 2), Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(buffer);
        canvas.drawText(MORE_SUGGESTIONS_HINT, width / 2, height, paint);
        return new BitmapDrawable(res, buffer);
    }

    private CharSequence getStyledSuggestionWord(final SuggestedWords suggestedWords,
            final int indexInSuggestedWords) {
        final String word = suggestedWords.getWord(indexInSuggestedWords);
        final boolean isAutoCorrect = indexInSuggestedWords == 1
                && suggestedWords.willAutoCorrect();
        final boolean isTypedWordValid = indexInSuggestedWords == 0
                && suggestedWords.mTypedWordValid;
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

    private int getIndexInSuggestedWords(final int indexInStrip,
            final SuggestedWords suggestedWords) {
        // TODO: This works for 3 suggestions. Revisit this algorithm when there are 5 or more
        // suggestions.
        final int mostImportantIndexInSuggestedWords = suggestedWords.willAutoCorrect() ? 1 : 0;
        if (indexInStrip == mCenterSuggestionIndex) {
            return mostImportantIndexInSuggestedWords;
        } else if (indexInStrip == mostImportantIndexInSuggestedWords) {
            return mCenterSuggestionIndex;
        } else {
            return indexInStrip;
        }
    }

    private int getSuggestionTextColor(final int indexInStrip,
            final SuggestedWords suggestedWords) {
        final int indexInSuggestedWords = getIndexInSuggestedWords(
                indexInStrip, suggestedWords);
        // TODO: Need to revisit this logic with bigram suggestions
        final boolean isSuggested = (indexInSuggestedWords != 0);

        final int color;
        if (indexInStrip == mCenterSuggestionIndex && suggestedWords.willAutoCorrect()) {
            color = mColorAutoCorrect;
        } else if (indexInStrip == mCenterSuggestionIndex && suggestedWords.mTypedWordValid) {
            color = mColorValidTypedWord;
        } else if (isSuggested) {
            color = mColorSuggested;
        } else {
            color = mColorTypedWord;
        }
        if (LatinImeLogger.sDBG && suggestedWords.size() > 1) {
            // If we auto-correct, then the autocorrection is in slot 0 and the typed word
            // is in slot 1.
            if (indexInStrip == mCenterSuggestionIndex
                    && AutoCorrection.shouldBlockAutoCorrectionBySafetyNet(
                            suggestedWords.getWord(1), suggestedWords.getWord(0))) {
                return 0xFFFF0000;
            }
        }

        if (suggestedWords.mIsObsoleteSuggestions && isSuggested) {
            return applyAlpha(color, mAlphaObsoleted);
        } else {
            return color;
        }
    }

    private static int applyAlpha(final int color, final float alpha) {
        final int newAlpha = (int)(Color.alpha(color) * alpha);
        return Color.argb(newAlpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static void addDivider(final ViewGroup stripView, final View divider) {
        stripView.addView(divider);
        final LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams)divider.getLayoutParams();
        params.gravity = Gravity.CENTER;
    }

    public void layout(final SuggestedWords suggestedWords, final ViewGroup stripView,
            final ViewGroup placer, final int stripWidth) {
        if (suggestedWords.mIsPunctuationSuggestions) {
            layoutPunctuationSuggestions(suggestedWords, stripView);
            return;
        }

        final int countInStrip = mSuggestionsCountInStrip;
        setupTexts(suggestedWords, countInStrip);
        mMoreSuggestionsAvailable = (suggestedWords.size() > countInStrip);
        int x = 0;
        for (int indexInStrip = 0; indexInStrip < countInStrip; indexInStrip++) {
            if (indexInStrip != 0) {
                final View divider = mDividers.get(indexInStrip);
                // Add divider if this isn't the left most suggestion in suggestions strip.
                addDivider(stripView, divider);
                x += divider.getMeasuredWidth();
            }

            final int width = getSuggestionWidth(indexInStrip, stripWidth);
            final TextView word = layoutWord(suggestedWords, indexInStrip, width);
            stripView.addView(word);
            setLayoutWeight(word, getSuggestionWeight(indexInStrip),
                    ViewGroup.LayoutParams.MATCH_PARENT);
            x += word.getMeasuredWidth();

            if (SuggestionStripView.DBG) {
                layoutDebugInfo(suggestedWords, indexInStrip, placer, x);
            }
        }
    }

    /**
     * Format appropriately the suggested word indirectly specified by
     * <code>indexInStrip</code> as text in a corresponding {@link TextView}. When the
     * suggested word doesn't exist, the corresponding {@link TextView} will be disabled
     * and never respond to user interaction. The suggested word may be shrunk or ellipsized to
     * fit in the specified width.
     *
     * The <code>indexInStrip</code> argument is the index in the suggestion strip. The indices
     * increase towards the right for LTR scripts and the left for RTL scripts, starting with 0.
     * The index of the most important suggestion is in {@link #mCenterSuggestionIndex}. This
     * usually doesn't match the index in <code>suggedtedWords</code> -- see
     * {@link #getIndexInSuggestedWords(int,SuggestedWords)}.
     *
     * @param suggestedWords the list of suggestions.
     * @param indexInStrip the in the suggestion strip.
     * @param width the maximum width for layout in pixels.
     * @return the {@link TextView} containing the suggested word appropriately formatted.
     */
    private TextView layoutWord(final SuggestedWords suggestedWords, final int indexInStrip,
            final int width) {
        final int indexInSuggestedWords = getIndexInSuggestedWords(indexInStrip, suggestedWords);
        final CharSequence styled = mTexts.get(indexInSuggestedWords);
        final TextView word = mWords.get(indexInSuggestedWords);
        if (indexInStrip == mCenterSuggestionIndex && mMoreSuggestionsAvailable) {
            // TODO: This "more suggestions hint" should have a nicely designed icon.
            word.setCompoundDrawablesWithIntrinsicBounds(
                    null, null, null, mMoreSuggestionsHint);
            // HACK: Align with other TextViews that have no compound drawables.
            word.setCompoundDrawablePadding(-mMoreSuggestionsHint.getIntrinsicHeight());
        } else {
            word.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        }

        // Disable this suggestion if the suggestion is null or empty.
        word.setEnabled(!TextUtils.isEmpty(styled));
        word.setTextColor(getSuggestionTextColor(indexInStrip, suggestedWords));
        final CharSequence text = getEllipsizedText(styled, width, word.getPaint());
        final float scaleX = word.getTextScaleX();
        word.setText(text); // TextView.setText() resets text scale x to 1.0.
        word.setTextScaleX(scaleX);
        return word;
    }

    private void layoutDebugInfo(final SuggestedWords suggestedWords, final int indexInStrip,
            final ViewGroup placer, final int x) {
        final int indexInSuggestedWords = getIndexInSuggestedWords(indexInStrip, suggestedWords);
        if (indexInSuggestedWords >= suggestedWords.size()) {
            return;
        }
        final String debugInfo = Utils.getDebugInfo(suggestedWords, indexInSuggestedWords);
        if (debugInfo == null) {
            return;
        }
        final TextView info = mInfos.get(indexInSuggestedWords);
        info.setText(debugInfo);
        placer.addView(info);
        info.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        final int infoWidth = info.getMeasuredWidth();
        final int y = info.getMeasuredHeight();
        ViewLayoutUtils.placeViewAt(
                info, x - infoWidth, y, infoWidth, info.getMeasuredHeight());
    }

    private int getSuggestionWidth(final int indexInStrip, final int maxWidth) {
        final int paddings = mPadding * mSuggestionsCountInStrip;
        final int dividers = mDividerWidth * (mSuggestionsCountInStrip - 1);
        final int availableWidth = maxWidth - paddings - dividers;
        return (int)(availableWidth * getSuggestionWeight(indexInStrip));
    }

    private float getSuggestionWeight(final int indexInStrip) {
        if (indexInStrip == mCenterSuggestionIndex) {
            return mCenterSuggestionWeight;
        } else {
            // TODO: Revisit this for cases of 5 or more suggestions
            return (1.0f - mCenterSuggestionWeight) / (mSuggestionsCountInStrip - 1);
        }
    }

    private void setupTexts(final SuggestedWords suggestedWords, final int countInStrip) {
        mTexts.clear();
        final int count = Math.min(suggestedWords.size(), countInStrip);
        for (int pos = 0; pos < count; pos++) {
            final CharSequence styled = getStyledSuggestionWord(suggestedWords, pos);
            mTexts.add(styled);
        }
        for (int pos = count; pos < countInStrip; pos++) {
            // Make this inactive for touches in layout().
            mTexts.add(null);
        }
    }

    private void layoutPunctuationSuggestions(final SuggestedWords suggestedWords,
            final ViewGroup stripView) {
        final int countInStrip = Math.min(suggestedWords.size(), PUNCTUATIONS_IN_STRIP);
        for (int indexInStrip = 0; indexInStrip < countInStrip; indexInStrip++) {
            if (indexInStrip != 0) {
                // Add divider if this isn't the left most suggestion in suggestions strip.
                addDivider(stripView, mDividers.get(indexInStrip));
            }

            final TextView word = mWords.get(indexInStrip);
            word.setEnabled(true);
            word.setTextColor(mColorAutoCorrect);
            final String text = suggestedWords.getWord(indexInStrip);
            word.setText(text);
            word.setTextScaleX(1.0f);
            word.setCompoundDrawables(null, null, null, null);
            stripView.addView(word);
            setLayoutWeight(word, 1.0f, mSuggestionsStripHeight);
        }
        mMoreSuggestionsAvailable = false;
    }

    public void layoutAddToDictionaryHint(final String word, final ViewGroup stripView,
            final int stripWidth, final CharSequence hintText, final OnClickListener listener) {
        final int width = stripWidth - mDividerWidth - mPadding * 2;

        final TextView wordView = mWordToSaveView;
        wordView.setTextColor(mColorTypedWord);
        final int wordWidth = (int)(width * mCenterSuggestionWeight);
        final CharSequence text = getEllipsizedText(word, wordWidth, wordView.getPaint());
        final float wordScaleX = wordView.getTextScaleX();
        wordView.setTag(word);
        wordView.setText(text);
        wordView.setTextScaleX(wordScaleX);
        stripView.addView(wordView);
        setLayoutWeight(wordView, mCenterSuggestionWeight, ViewGroup.LayoutParams.MATCH_PARENT);

        stripView.addView(mDividers.get(0));

        final TextView leftArrowView = mLeftwardsArrowView;
        leftArrowView.setTextColor(mColorAutoCorrect);
        leftArrowView.setText(LEFTWARDS_ARROW);
        stripView.addView(leftArrowView);

        final TextView hintView = mHintToSaveView;
        hintView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        hintView.setTextColor(mColorAutoCorrect);
        final int hintWidth = width - wordWidth - leftArrowView.getWidth();
        final float hintScaleX = getTextScaleX(hintText, hintWidth, hintView.getPaint());
        hintView.setText(hintText);
        hintView.setTextScaleX(hintScaleX);
        stripView.addView(hintView);
        setLayoutWeight(
                hintView, 1.0f - mCenterSuggestionWeight, ViewGroup.LayoutParams.MATCH_PARENT);

        wordView.setOnClickListener(listener);
        leftArrowView.setOnClickListener(listener);
        hintView.setOnClickListener(listener);
    }

    public CharSequence getAddToDictionaryWord() {
        return (CharSequence)mWordToSaveView.getTag();
    }

    public boolean isAddToDictionaryShowing(final View v) {
        return v == mWordToSaveView || v == mHintToSaveView || v == mLeftwardsArrowView;
    }

    private static void setLayoutWeight(final View v, final float weight, final int height) {
        final ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof LinearLayout.LayoutParams) {
            final LinearLayout.LayoutParams llp = (LinearLayout.LayoutParams)lp;
            llp.weight = weight;
            llp.width = 0;
            llp.height = height;
        }
    }

    private static float getTextScaleX(final CharSequence text, final int maxWidth,
            final TextPaint paint) {
        paint.setTextScaleX(1.0f);
        final int width = getTextWidth(text, paint);
        if (width <= maxWidth) {
            return 1.0f;
        }
        return maxWidth / (float)width;
    }

    private static CharSequence getEllipsizedText(final CharSequence text, final int maxWidth,
            final TextPaint paint) {
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

        // Note that TextUtils.ellipsize() use text-x-scale as 1.0 if ellipsize is needed. To
        // get squeezed and ellipsized text, passes enlarged width (maxWidth / MIN_TEXT_XSCALE).
        final CharSequence ellipsized = TextUtils.ellipsize(
                text, paint, maxWidth / MIN_TEXT_XSCALE, TextUtils.TruncateAt.MIDDLE);
        paint.setTextScaleX(MIN_TEXT_XSCALE);
        return ellipsized;
    }

    private static int getTextWidth(final CharSequence text, final TextPaint paint) {
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

    private static Typeface getTextTypeface(final CharSequence text) {
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
}
