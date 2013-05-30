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

    private final ArrayList<TextView> mWordViews;
    private final ArrayList<View> mDividerViews;
    private final ArrayList<TextView> mDebugInfoViews;

    private final int mColorValidTypedWord;
    private final int mColorTypedWord;
    private final int mColorAutoCorrect;
    private final int mColorSuggested;
    private final float mAlphaObsoleted;
    private final float mCenterSuggestionWeight;
    private final int mCenterPositionInStrip;
    private final Drawable mMoreSuggestionsHint;
    private static final String MORE_SUGGESTIONS_HINT = "\u2026";
    private static final String LEFTWARDS_ARROW = "\u2190";

    private static final CharacterStyle BOLD_SPAN = new StyleSpan(Typeface.BOLD);
    private static final CharacterStyle UNDERLINE_SPAN = new UnderlineSpan();
    private static final int AUTO_CORRECT_BOLD = 0x01;
    private static final int AUTO_CORRECT_UNDERLINE = 0x02;
    private static final int VALID_TYPED_WORD_BOLD = 0x04;

    private final int mSuggestionStripOption;

    private final ArrayList<CharSequence> mWords = CollectionUtils.newArrayList();

    public boolean mMoreSuggestionsAvailable;

    private final TextView mWordToSaveView;
    private final TextView mLeftwardsArrowView;
    private final TextView mHintToSaveView;

    public SuggestionStripLayoutHelper(final Context context, final AttributeSet attrs,
            final int defStyle, final ArrayList<TextView> wordViews,
            final ArrayList<View> dividerViews, final ArrayList<TextView> debugInfoViews) {
        mWordViews = wordViews;
        mDividerViews = dividerViews;
        mDebugInfoViews = debugInfoViews;

        final TextView wordView = wordViews.get(0);
        final View dividerView = dividerViews.get(0);
        mPadding = wordView.getCompoundPaddingLeft() + wordView.getCompoundPaddingRight();
        dividerView.measure(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mDividerWidth = dividerView.getMeasuredWidth();

        final Resources res = wordView.getResources();
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
        mCenterPositionInStrip = mSuggestionsCountInStrip / 2;
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
        if (!isAutoCorrect && !isTypedWordValid) {
            return word;
        }

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

    private int getIndexInSuggestedWords(final int positionInStrip,
            final SuggestedWords suggestedWords) {
        // TODO: This works for 3 suggestions. Revisit this algorithm when there are 5 or more
        // suggestions.
        final int mostImportantIndexInSuggestedWords = suggestedWords.willAutoCorrect() ? 1 : 0;
        if (positionInStrip == mCenterPositionInStrip) {
            return mostImportantIndexInSuggestedWords;
        }
        if (positionInStrip == mostImportantIndexInSuggestedWords) {
            return mCenterPositionInStrip;
        }
        return positionInStrip;
    }

    private int getSuggestionTextColor(final int positionInStrip,
            final SuggestedWords suggestedWords) {
        final int indexInSuggestedWords = getIndexInSuggestedWords(
                positionInStrip, suggestedWords);
        // TODO: Need to revisit this logic with bigram suggestions
        final boolean isSuggested = (indexInSuggestedWords != 0);

        final int color;
        if (positionInStrip == mCenterPositionInStrip && suggestedWords.willAutoCorrect()) {
            color = mColorAutoCorrect;
        } else if (positionInStrip == mCenterPositionInStrip && suggestedWords.mTypedWordValid) {
            color = mColorValidTypedWord;
        } else if (isSuggested) {
            color = mColorSuggested;
        } else {
            color = mColorTypedWord;
        }
        if (LatinImeLogger.sDBG && suggestedWords.size() > 1) {
            // If we auto-correct, then the autocorrection is in slot 0 and the typed word
            // is in slot 1.
            if (positionInStrip == mCenterPositionInStrip
                    && AutoCorrection.shouldBlockAutoCorrectionBySafetyNet(
                            suggestedWords.getWord(1), suggestedWords.getWord(0))) {
                return 0xFFFF0000;
            }
        }

        if (suggestedWords.mIsObsoleteSuggestions && isSuggested) {
            return applyAlpha(color, mAlphaObsoleted);
        }
        return color;
    }

    private static int applyAlpha(final int color, final float alpha) {
        final int newAlpha = (int)(Color.alpha(color) * alpha);
        return Color.argb(newAlpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static void addDivider(final ViewGroup stripView, final View dividerView) {
        stripView.addView(dividerView);
        final LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams)dividerView.getLayoutParams();
        params.gravity = Gravity.CENTER;
    }

    public void layout(final SuggestedWords suggestedWords, final ViewGroup stripView,
            final ViewGroup placerView) {
        if (suggestedWords.mIsPunctuationSuggestions) {
            layoutPunctuationSuggestions(suggestedWords, stripView);
            return;
        }

        final int countInStrip = mSuggestionsCountInStrip;
        setupWords(suggestedWords, countInStrip);
        mMoreSuggestionsAvailable = (suggestedWords.size() > countInStrip);
        int x = 0;
        for (int positionInStrip = 0; positionInStrip < countInStrip; positionInStrip++) {
            if (positionInStrip != 0) {
                final View divider = mDividerViews.get(positionInStrip);
                // Add divider if this isn't the left most suggestion in suggestions strip.
                addDivider(stripView, divider);
                x += divider.getMeasuredWidth();
            }

            final int width = getSuggestionWidth(positionInStrip, placerView.getWidth());
            final TextView wordView = layoutWord(suggestedWords, positionInStrip, width);
            stripView.addView(wordView);
            setLayoutWeight(wordView, getSuggestionWeight(positionInStrip),
                    ViewGroup.LayoutParams.MATCH_PARENT);
            x += wordView.getMeasuredWidth();

            if (SuggestionStripView.DBG) {
                layoutDebugInfo(suggestedWords, positionInStrip, placerView, x);
            }
        }
    }

    /**
     * Format appropriately the suggested word indirectly specified by
     * <code>positionInStrip</code> as text in a corresponding {@link TextView}. When the
     * suggested word doesn't exist, the corresponding {@link TextView} will be disabled
     * and never respond to user interaction. The suggested word may be shrunk or ellipsized to
     * fit in the specified width.
     *
     * The <code>positionInStrip</code> argument is the index in the suggestion strip. The indices
     * increase towards the right for LTR scripts and the left for RTL scripts, starting with 0.
     * The index of the most important suggestion is in {@link #mCenterPositionInStrip}. This
     * usually doesn't match the index in <code>suggedtedWords</code> -- see
     * {@link #getIndexInSuggestedWords(int,SuggestedWords)}.
     *
     * @param suggestedWords the list of suggestions.
     * @param positionInStrip the in the suggestion strip.
     * @param width the maximum width for layout in pixels.
     * @return the {@link TextView} containing the suggested word appropriately formatted.
     */
    private TextView layoutWord(final SuggestedWords suggestedWords, final int positionInStrip,
            final int width) {
        final int indexInSuggestedWords = getIndexInSuggestedWords(positionInStrip, suggestedWords);
        final CharSequence word = mWords.get(indexInSuggestedWords);
        final TextView wordView = mWordViews.get(indexInSuggestedWords);
        if (positionInStrip == mCenterPositionInStrip && mMoreSuggestionsAvailable) {
            // TODO: This "more suggestions hint" should have a nicely designed icon.
            wordView.setCompoundDrawablesWithIntrinsicBounds(
                    null, null, null, mMoreSuggestionsHint);
            // HACK: Align with other TextViews that have no compound drawables.
            wordView.setCompoundDrawablePadding(-mMoreSuggestionsHint.getIntrinsicHeight());
        } else {
            wordView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        }

        // Disable this suggestion if the suggestion is null or empty.
        wordView.setEnabled(!TextUtils.isEmpty(word));
        wordView.setTextColor(getSuggestionTextColor(positionInStrip, suggestedWords));
        final CharSequence text = getEllipsizedText(word, width, wordView.getPaint());
        final float scaleX = wordView.getTextScaleX();
        wordView.setText(text); // TextView.setText() resets text scale x to 1.0.
        wordView.setTextScaleX(scaleX);
        return wordView;
    }

    private void layoutDebugInfo(final SuggestedWords suggestedWords, final int positionInStrip,
            final ViewGroup placerView, final int x) {
        final int indexInSuggestedWords = getIndexInSuggestedWords(positionInStrip, suggestedWords);
        if (indexInSuggestedWords >= suggestedWords.size()) {
            return;
        }
        final String debugInfo = Utils.getDebugInfo(suggestedWords, indexInSuggestedWords);
        if (debugInfo == null) {
            return;
        }
        final TextView debugInfoView = mDebugInfoViews.get(indexInSuggestedWords);
        debugInfoView.setText(debugInfo);
        placerView.addView(debugInfoView);
        debugInfoView.measure(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        final int infoWidth = debugInfoView.getMeasuredWidth();
        final int y = debugInfoView.getMeasuredHeight();
        ViewLayoutUtils.placeViewAt(
                debugInfoView, x - infoWidth, y, infoWidth, debugInfoView.getMeasuredHeight());
    }

    private int getSuggestionWidth(final int positionInStrip, final int maxWidth) {
        final int paddings = mPadding * mSuggestionsCountInStrip;
        final int dividers = mDividerWidth * (mSuggestionsCountInStrip - 1);
        final int availableWidth = maxWidth - paddings - dividers;
        return (int)(availableWidth * getSuggestionWeight(positionInStrip));
    }

    private float getSuggestionWeight(final int positionInStrip) {
        if (positionInStrip == mCenterPositionInStrip) {
            return mCenterSuggestionWeight;
        }
        // TODO: Revisit this for cases of 5 or more suggestions
        return (1.0f - mCenterSuggestionWeight) / (mSuggestionsCountInStrip - 1);
    }

    private void setupWords(final SuggestedWords suggestedWords, final int countInStrip) {
        mWords.clear();
        final int count = Math.min(suggestedWords.size(), countInStrip);
        for (int pos = 0; pos < count; pos++) {
            final CharSequence styled = getStyledSuggestionWord(suggestedWords, pos);
            mWords.add(styled);
        }
        for (int pos = count; pos < countInStrip; pos++) {
            // Make this inactive for touches in layout().
            mWords.add(null);
        }
    }

    private void layoutPunctuationSuggestions(final SuggestedWords suggestedWords,
            final ViewGroup stripView) {
        final int countInStrip = Math.min(suggestedWords.size(), PUNCTUATIONS_IN_STRIP);
        for (int indexInStrip = 0; indexInStrip < countInStrip; indexInStrip++) {
            if (indexInStrip != 0) {
                // Add divider if this isn't the left most suggestion in suggestions strip.
                addDivider(stripView, mDividerViews.get(indexInStrip));
            }

            final TextView word = mWordViews.get(indexInStrip);
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

        stripView.addView(mDividerViews.get(0));

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
        if (text == null) {
            return null;
        }
        final float scaleX = getTextScaleX(text, maxWidth, paint);
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
        if (TextUtils.isEmpty(text)) {
            return 0;
        }
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
        if (!(text instanceof SpannableString)) {
            return Typeface.DEFAULT;
        }

        final SpannableString ss = (SpannableString)text;
        final StyleSpan[] styles = ss.getSpans(0, text.length(), StyleSpan.class);
        if (styles.length == 0) {
            return Typeface.DEFAULT;
        }

        if (styles[0].getStyle() == Typeface.BOLD) {
            return Typeface.DEFAULT_BOLD;
        }
        // TODO: BOLD_ITALIC, ITALIC case?
        return Typeface.DEFAULT;
    }
}
