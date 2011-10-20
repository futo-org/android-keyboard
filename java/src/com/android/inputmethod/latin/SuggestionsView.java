/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Message;
import android.os.SystemClock;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.inputmethod.compat.FrameLayoutCompatUtils;
import com.android.inputmethod.keyboard.KeyboardActionListener;
import com.android.inputmethod.keyboard.KeyboardView;
import com.android.inputmethod.keyboard.MoreKeysPanel;
import com.android.inputmethod.keyboard.PointerTracker;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;

import java.util.ArrayList;
import java.util.List;

public class SuggestionsView extends RelativeLayout implements OnClickListener,
        OnLongClickListener {
    public interface Listener {
        public boolean addWordToDictionary(String word);
        public void pickSuggestionManually(int index, CharSequence word);
    }

    // The maximum number of suggestions available. See {@link Suggest#mPrefMaxSuggestions}.
    public static final int MAX_SUGGESTIONS = 18;

    private static final boolean DBG = LatinImeLogger.sDBG;

    private final ViewGroup mSuggestionsStrip;
    private KeyboardView mKeyboardView;

    private final View mMoreSuggestionsContainer;
    private final MoreSuggestionsView mMoreSuggestionsView;
    private final MoreSuggestions.Builder mMoreSuggestionsBuilder;
    private final PopupWindow mMoreSuggestionsWindow;

    private final ArrayList<TextView> mWords = new ArrayList<TextView>();
    private final ArrayList<TextView> mInfos = new ArrayList<TextView>();
    private final ArrayList<View> mDividers = new ArrayList<View>();

    private final PopupWindow mPreviewPopup;
    private final TextView mPreviewText;

    private Listener mListener;
    private SuggestedWords mSuggestions = SuggestedWords.EMPTY;

    private final SuggestionsViewParams mParams;
    private static final float MIN_TEXT_XSCALE = 0.70f;

    private final UiHandler mHandler = new UiHandler(this);

    private static class UiHandler extends StaticInnerHandlerWrapper<SuggestionsView> {
        private static final int MSG_HIDE_PREVIEW = 0;

        private static final long DELAY_HIDE_PREVIEW = 1300;

        public UiHandler(SuggestionsView outerInstance) {
            super(outerInstance);
        }

        @Override
        public void dispatchMessage(Message msg) {
            final SuggestionsView suggestionsView = getOuterInstance();
            switch (msg.what) {
            case MSG_HIDE_PREVIEW:
                suggestionsView.hidePreview();
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

        public void cancelAllMessages() {
            cancelHidePreview();
        }
    }

    private static class SuggestionsViewParams {
        private static final int DEFAULT_SUGGESTIONS_COUNT_IN_STRIP = 3;
        private static final int DEFAULT_CENTER_SUGGESTION_PERCENTILE = 40;
        private static final int DEFAULT_MAX_MORE_SUGGESTIONS_ROW = 2;
        private static final int PUNCTUATIONS_IN_STRIP = 5;

        public final int mPadding;
        public final int mDividerWidth;
        public final int mSuggestionsStripHeight;
        public final int mSuggestionsCountInStrip;
        public final int mMaxMoreSuggestionsRow;
        public final float mMinMoreSuggestionsWidth;
        public final int mMoreSuggestionsBottomGap;

        private final List<TextView> mWords;
        private final List<View> mDividers;
        private final List<TextView> mInfos;

        private final int mColorTypedWord;
        private final int mColorAutoCorrect;
        private final int mColorSuggested;
        private final float mAlphaObsoleted;
        private final float mCenterSuggestionWeight;
        private final int mCenterSuggestionIndex;
        private final Drawable mMoreSuggestionsHint;
        private static final String MORE_SUGGESTIONS_HINT = "\u2026";

        private static final CharacterStyle BOLD_SPAN = new StyleSpan(Typeface.BOLD);
        private static final CharacterStyle UNDERLINE_SPAN = new UnderlineSpan();
        private static final int AUTO_CORRECT_BOLD = 0x01;
        private static final int AUTO_CORRECT_UNDERLINE = 0x02;
        private static final int VALID_TYPED_WORD_BOLD = 0x04;

        private final int mSuggestionStripOption;

        private final ArrayList<CharSequence> mTexts = new ArrayList<CharSequence>();

        public boolean mMoreSuggestionsAvailable;

        public final TextView mWordToSaveView;
        private final TextView mHintToSaveView;
        private final CharSequence mHintToSaveText;

        public SuggestionsViewParams(Context context, AttributeSet attrs, int defStyle,
                List<TextView> words, List<View> dividers, List<TextView> infos) {
            mWords = words;
            mDividers = dividers;
            mInfos = infos;

            final TextView word = words.get(0);
            final View divider = dividers.get(0);
            mPadding = word.getCompoundPaddingLeft() + word.getCompoundPaddingRight();
            divider.measure(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            mDividerWidth = divider.getMeasuredWidth();

            final Resources res = word.getResources();
            mSuggestionsStripHeight = res.getDimensionPixelSize(R.dimen.suggestions_strip_height);

            final TypedArray a = context.obtainStyledAttributes(
                    attrs, R.styleable.SuggestionsView, defStyle, R.style.SuggestionsViewStyle);
            mSuggestionStripOption = a.getInt(R.styleable.SuggestionsView_suggestionStripOption, 0);
            final float alphaTypedWord = getPercent(a,
                    R.styleable.SuggestionsView_alphaTypedWord, 100);
            final float alphaAutoCorrect = getPercent(a,
                    R.styleable.SuggestionsView_alphaAutoCorrect, 100);
            final float alphaSuggested = getPercent(a,
                    R.styleable.SuggestionsView_alphaSuggested, 100);
            mAlphaObsoleted = getPercent(a, R.styleable.SuggestionsView_alphaSuggested, 100);
            mColorTypedWord = applyAlpha(
                    a.getColor(R.styleable.SuggestionsView_colorTypedWord, 0), alphaTypedWord);
            mColorAutoCorrect = applyAlpha(
                    a.getColor(R.styleable.SuggestionsView_colorAutoCorrect, 0), alphaAutoCorrect);
            mColorSuggested = applyAlpha(
                    a.getColor(R.styleable.SuggestionsView_colorSuggested, 0), alphaSuggested);
            mSuggestionsCountInStrip = a.getInt(
                    R.styleable.SuggestionsView_suggestionsCountInStrip,
                    DEFAULT_SUGGESTIONS_COUNT_IN_STRIP);
            mCenterSuggestionWeight = getPercent(a,
                    R.styleable.SuggestionsView_centerSuggestionPercentile,
                    DEFAULT_CENTER_SUGGESTION_PERCENTILE);
            mMaxMoreSuggestionsRow = a.getInt(
                    R.styleable.SuggestionsView_maxMoreSuggestionsRow,
                    DEFAULT_MAX_MORE_SUGGESTIONS_ROW);
            mMinMoreSuggestionsWidth = getRatio(a,
                    R.styleable.SuggestionsView_minMoreSuggestionsWidth);
            a.recycle();

            mMoreSuggestionsHint = getMoreSuggestionsHint(res,
                    res.getDimension(R.dimen.more_suggestions_hint_text_size), mColorAutoCorrect);
            mCenterSuggestionIndex = mSuggestionsCountInStrip / 2;
            mMoreSuggestionsBottomGap = res.getDimensionPixelOffset(
                    R.dimen.more_suggestions_bottom_gap);

            final LayoutInflater inflater = LayoutInflater.from(context);
            mWordToSaveView = (TextView)inflater.inflate(R.layout.suggestion_word, null);
            mHintToSaveView = (TextView)inflater.inflate(R.layout.suggestion_word, null);
            mHintToSaveText = context.getText(R.string.hint_add_to_dictionary);
        }

        private static Drawable getMoreSuggestionsHint(Resources res, float textSize, int color) {
            final Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setTextAlign(Align.CENTER);
            paint.setTextSize(textSize);
            paint.setColor(color);
            final Rect bounds = new Rect();
            paint.getTextBounds(MORE_SUGGESTIONS_HINT, 0, MORE_SUGGESTIONS_HINT.length(), bounds);
            final int width = Math.round(bounds.width() + 0.5f);
            final int height = Math.round(bounds.height() + 0.5f);
            final Bitmap buffer = Bitmap.createBitmap(
                    width, (height * 3 / 2), Bitmap.Config.ARGB_8888);
            final Canvas canvas = new Canvas(buffer);
            canvas.drawText(MORE_SUGGESTIONS_HINT, width / 2, height, paint);
            return new BitmapDrawable(res, buffer);
        }

        // Read integer value in TypedArray as percent.
        private static float getPercent(TypedArray a, int index, int defValue) {
            return a.getInt(index, defValue) / 100.0f;
        }

        // Read fraction value in TypedArray as float.
        private static float getRatio(TypedArray a, int index) {
            return a.getFraction(index, 1000, 1000, 1) / 1000.0f;
        }

        private CharSequence getStyledSuggestionWord(SuggestedWords suggestions, int pos) {
            final CharSequence word = suggestions.getWord(pos);
            final boolean isAutoCorrect = pos == 1 && Utils.willAutoCorrect(suggestions);
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

        private int getWordPosition(int index, SuggestedWords suggestions) {
            // TODO: This works for 3 suggestions. Revisit this algorithm when there are 5 or more
            // suggestions.
            final int centerPos = Utils.willAutoCorrect(suggestions) ? 1 : 0;
            if (index == mCenterSuggestionIndex) {
                return centerPos;
            } else if (index == centerPos) {
                return mCenterSuggestionIndex;
            } else {
                return index;
            }
        }

        private int getSuggestionTextColor(int index, SuggestedWords suggestions, int pos) {
            // TODO: Need to revisit this logic with bigram suggestions
            final boolean isSuggested = (pos != 0);

            final int color;
            if (index == mCenterSuggestionIndex && Utils.willAutoCorrect(suggestions)) {
                color = mColorAutoCorrect;
            } else if (isSuggested) {
                color = mColorSuggested;
            } else {
                color = mColorTypedWord;
            }
            if (LatinImeLogger.sDBG) {
                if (index == mCenterSuggestionIndex && suggestions.mHasAutoCorrectionCandidate
                        && suggestions.shouldBlockAutoCorrection()) {
                    return 0xFFFF0000;
                }
            }

            final SuggestedWordInfo info = (pos < suggestions.size())
                    ? suggestions.getInfo(pos) : null;
            if (info != null && info.isObsoleteSuggestedWord()) {
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

        public void layout(SuggestedWords suggestions, ViewGroup stripView, ViewGroup placer,
                int stripWidth) {
            if (suggestions.isPunctuationSuggestions()) {
                layoutPunctuationSuggestions(suggestions, stripView);
                return;
            }

            final int countInStrip = mSuggestionsCountInStrip;
            setupTexts(suggestions, countInStrip);
            mMoreSuggestionsAvailable = (suggestions.size() > countInStrip);
            int x = 0;
            for (int index = 0; index < countInStrip; index++) {
                final int pos = getWordPosition(index, suggestions);

                if (index != 0) {
                    final View divider = mDividers.get(pos);
                    // Add divider if this isn't the left most suggestion in suggestions strip.
                    addDivider(stripView, divider);
                    x += divider.getMeasuredWidth();
                }

                final CharSequence styled = mTexts.get(pos);
                final TextView word = mWords.get(pos);
                if (index == mCenterSuggestionIndex && mMoreSuggestionsAvailable) {
                    // TODO: This "more suggestions hint" should have nicely designed icon.
                    word.setCompoundDrawablesWithIntrinsicBounds(
                            null, null, null, mMoreSuggestionsHint);
                    // HACK: To align with other TextView that has no compound drawables.
                    word.setCompoundDrawablePadding(-mMoreSuggestionsHint.getIntrinsicHeight());
                } else {
                    word.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
                }

                // Disable this suggestion if the suggestion is null or empty.
                word.setEnabled(!TextUtils.isEmpty(styled));
                word.setTextColor(getSuggestionTextColor(index, suggestions, pos));
                final int width = getSuggestionWidth(index, stripWidth);
                final CharSequence text = getEllipsizedText(styled, width, word.getPaint());
                final float scaleX = word.getTextScaleX();
                word.setText(text); // TextView.setText() resets text scale x to 1.0.
                word.setTextScaleX(scaleX);
                stripView.addView(word);
                setLayoutWeight(
                        word, getSuggestionWeight(index), ViewGroup.LayoutParams.MATCH_PARENT);
                x += word.getMeasuredWidth();

                if (DBG) {
                    final CharSequence debugInfo = getDebugInfo(suggestions, pos);
                    if (debugInfo != null) {
                        final TextView info = mInfos.get(pos);
                        info.setText(debugInfo);
                        placer.addView(info);
                        info.measure(ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                        final int infoWidth = info.getMeasuredWidth();
                        final int y = info.getMeasuredHeight();
                        FrameLayoutCompatUtils.placeViewAt(
                                info, x - infoWidth, y, infoWidth, info.getMeasuredHeight());
                    }
                }
            }
        }

        private int getSuggestionWidth(int index, int maxWidth) {
            final int paddings = mPadding * mSuggestionsCountInStrip;
            final int dividers = mDividerWidth * (mSuggestionsCountInStrip - 1);
            final int availableWidth = maxWidth - paddings - dividers;
            return (int)(availableWidth * getSuggestionWeight(index));
        }

        private float getSuggestionWeight(int index) {
            if (index == mCenterSuggestionIndex) {
                return mCenterSuggestionWeight;
            } else {
                // TODO: Revisit this for cases of 5 or more suggestions
                return (1.0f - mCenterSuggestionWeight) / (mSuggestionsCountInStrip - 1);
            }
        }

        private void setupTexts(SuggestedWords suggestions, int countInStrip) {
            mTexts.clear();
            final int count = Math.min(suggestions.size(), countInStrip);
            for (int pos = 0; pos < count; pos++) {
                final CharSequence styled = getStyledSuggestionWord(suggestions, pos);
                mTexts.add(styled);
            }
            for (int pos = count; pos < countInStrip; pos++) {
                // Make this inactive for touches in layout().
                mTexts.add(null);
            }
        }

        private void layoutPunctuationSuggestions(SuggestedWords suggestions, ViewGroup stripView) {
            final int countInStrip = Math.min(suggestions.size(), PUNCTUATIONS_IN_STRIP);
            for (int index = 0; index < countInStrip; index++) {
                if (index != 0) {
                    // Add divider if this isn't the left most suggestion in suggestions strip.
                    addDivider(stripView, mDividers.get(index));
                }

                final TextView word = mWords.get(index);
                word.setEnabled(true);
                word.setTextColor(mColorTypedWord);
                final CharSequence text = suggestions.getWord(index);
                word.setText(text);
                word.setTextScaleX(1.0f);
                word.setCompoundDrawables(null, null, null, null);
                stripView.addView(word);
                setLayoutWeight(word, 1.0f, mSuggestionsStripHeight);
            }
            mMoreSuggestionsAvailable = false;
        }

        public void layoutAddToDictionaryHint(CharSequence word, ViewGroup stripView,
                int stripWidth) {
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

            final TextView hintView = mHintToSaveView;
            hintView.setTextColor(mColorAutoCorrect);
            final int hintWidth = width - wordWidth;
            final float hintScaleX = getTextScaleX(mHintToSaveText, hintWidth, hintView.getPaint());
            hintView.setText(mHintToSaveText);
            hintView.setTextScaleX(hintScaleX);
            stripView.addView(hintView);
            setLayoutWeight(
                    hintView, 1.0f - mCenterSuggestionWeight, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    /**
     * Construct a {@link SuggestionsView} for showing suggested words for completion.
     * @param context
     * @param attrs
     */
    public SuggestionsView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.suggestionsViewStyle);
    }

    public SuggestionsView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.suggestions_strip, this);

        mPreviewPopup = new PopupWindow(context);
        mPreviewText = (TextView) inflater.inflate(R.layout.suggestion_preview, null);
        mPreviewPopup.setWindowLayoutMode(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mPreviewPopup.setContentView(mPreviewText);
        mPreviewPopup.setBackgroundDrawable(null);

        mSuggestionsStrip = (ViewGroup)findViewById(R.id.suggestions_strip);
        for (int pos = 0; pos < MAX_SUGGESTIONS; pos++) {
            final TextView word = (TextView)inflater.inflate(R.layout.suggestion_word, null);
            word.setTag(pos);
            word.setOnClickListener(this);
            word.setOnLongClickListener(this);
            mWords.add(word);
            final View divider = inflater.inflate(R.layout.suggestion_divider, null);
            divider.setTag(pos);
            divider.setOnClickListener(this);
            mDividers.add(divider);
            mInfos.add((TextView)inflater.inflate(R.layout.suggestion_info, null));
        }

        mParams = new SuggestionsViewParams(context, attrs, defStyle, mWords, mDividers, mInfos);
        mParams.mWordToSaveView.setOnClickListener(this);

        mMoreSuggestionsContainer = inflater.inflate(R.layout.more_suggestions, null);
        mMoreSuggestionsView = (MoreSuggestionsView)mMoreSuggestionsContainer
                .findViewById(R.id.more_suggestions_view);
        mMoreSuggestionsBuilder = new MoreSuggestions.Builder(mMoreSuggestionsView);

        final PopupWindow moreWindow = new PopupWindow(context);
        moreWindow.setWindowLayoutMode(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        moreWindow.setBackgroundDrawable(new ColorDrawable(android.R.color.transparent));
        moreWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        moreWindow.setFocusable(true);
        moreWindow.setOutsideTouchable(true);
        moreWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                mKeyboardView.dimEntireKeyboard(false);
            }
        });
        mMoreSuggestionsWindow = moreWindow;

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
    public void setListener(Listener listener, View inputView) {
        mListener = listener;
        mKeyboardView = (KeyboardView)inputView.findViewById(R.id.keyboard_view);
    }

    public void setSuggestions(SuggestedWords suggestions) {
        if (suggestions == null || suggestions.size() == 0)
            return;

        clear();
        mSuggestions = suggestions;
        mParams.layout(mSuggestions, mSuggestionsStrip, this, getWidth());
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

    public boolean isShowingAddToDictionaryHint() {
        return mSuggestionsStrip.getChildCount() > 0
                && mSuggestionsStrip.getChildAt(0) == mParams.mWordToSaveView;
    }

    public void showAddToDictionaryHint(CharSequence word) {
        clear();
        mParams.layoutAddToDictionaryHint(word, mSuggestionsStrip, getWidth());
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
        mSuggestionsStrip.removeAllViews();
        removeAllViews();
        addView(mSuggestionsStrip);
        dismissMoreSuggestions();
    }

    private void hidePreview() {
        mPreviewPopup.dismiss();
    }

    private void showPreview(View view, CharSequence word) {
        if (TextUtils.isEmpty(word))
            return;

        final TextView previewText = mPreviewText;
        previewText.setTextColor(mParams.mColorTypedWord);
        previewText.setText(word);
        previewText.measure(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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
            showPreview(mParams.mWordToSaveView, message);
        }
    }

    private final KeyboardActionListener mMoreSuggestionsListener =
            new KeyboardActionListener.Adapter() {
        @Override
        public boolean onCustomRequest(int requestCode) {
            final int index = requestCode;
            final CharSequence word = mSuggestions.getWord(index);
            mListener.pickSuggestionManually(index, word);
            dismissMoreSuggestions();
            return true;
        }

        @Override
        public void onCancelInput() {
            dismissMoreSuggestions();
        }
    };

    private final MoreKeysPanel.Controller mMoreSuggestionsController =
            new MoreKeysPanel.Controller() {
        @Override
        public boolean dismissMoreKeysPanel() {
            return dismissMoreSuggestions();
        }
    };

    private boolean dismissMoreSuggestions() {
        if (mMoreSuggestionsWindow.isShowing()) {
            mMoreSuggestionsWindow.dismiss();
            return true;
        }
        return false;
    }

    public boolean handleBack() {
        return dismissMoreSuggestions();
    }

    @Override
    public boolean onLongClick(View view) {
        return showMoreSuggestions();
    }

    private boolean showMoreSuggestions() {
        final SuggestionsViewParams params = mParams;
        if (params.mMoreSuggestionsAvailable) {
            final int stripWidth = getWidth();
            final View container = mMoreSuggestionsContainer;
            final int maxWidth = stripWidth - container.getPaddingLeft()
                    - container.getPaddingRight();
            final MoreSuggestions.Builder builder = mMoreSuggestionsBuilder;
            builder.layout(mSuggestions, params.mSuggestionsCountInStrip, maxWidth,
                    (int)(maxWidth * params.mMinMoreSuggestionsWidth),
                    params.mMaxMoreSuggestionsRow);
            mMoreSuggestionsView.setKeyboard(builder.build());
            container.measure(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

            final MoreKeysPanel moreKeysPanel = mMoreSuggestionsView;
            final int pointX = stripWidth / 2;
            final int pointY = -params.mMoreSuggestionsBottomGap;
            moreKeysPanel.showMoreKeysPanel(
                    this, mMoreSuggestionsController, pointX, pointY,
                    mMoreSuggestionsWindow, mMoreSuggestionsListener);
            mMoreSuggestionsMode = MORE_SUGGESTIONS_CHECKING_MODAL_OR_SLIDING;
            mOriginX = mLastX;
            mOriginY = mLastY;
            mKeyboardView.dimEntireKeyboard(true);
            for (int i = 0; i < params.mSuggestionsCountInStrip; i++) {
                mWords.get(i).setPressed(false);
            }
            return true;
        }
        return false;
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
    public boolean dispatchTouchEvent(MotionEvent me) {
        if (!mMoreSuggestionsWindow.isShowing()
                || mMoreSuggestionsMode == MORE_SUGGESTIONS_IN_MODAL_MODE) {
            mLastX = (int)me.getX();
            mLastY = (int)me.getY();
            if (mMoreSuggestionsSlidingDetector.onTouchEvent(me)) {
                return true;
            }
            return super.dispatchTouchEvent(me);
        }

        final MoreKeysPanel moreKeysPanel = mMoreSuggestionsView;
        final int action = me.getAction();
        final long eventTime = me.getEventTime();
        final int index = me.getActionIndex();
        final int id = me.getPointerId(index);
        final PointerTracker tracker = PointerTracker.getPointerTracker(id, moreKeysPanel);
        final int x = (int)me.getX(index);
        final int y = (int)me.getY(index);
        final int translatedX = moreKeysPanel.translateX(x);
        final int translatedY = moreKeysPanel.translateY(y);

        if (mMoreSuggestionsMode == MORE_SUGGESTIONS_CHECKING_MODAL_OR_SLIDING) {
            if (Math.abs(x - mOriginX) >= mMoreSuggestionsModalTolerance
                    || mOriginY - y >= mMoreSuggestionsModalTolerance) {
                // Decided to be in the sliding input mode only when the touch point has been moved
                // upward.
                mMoreSuggestionsMode = MORE_SUGGESTIONS_IN_SLIDING_MODE;
                tracker.onShowMoreKeysPanel(
                        translatedX, translatedY, SystemClock.uptimeMillis(), moreKeysPanel);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                // Decided to be in the modal input mode
                mMoreSuggestionsMode = MORE_SUGGESTIONS_IN_MODAL_MODE;
            }
            return true;
        }

        // MORE_SUGGESTIONS_IN_SLIDING_MODE
        tracker.processMotionEvent(action, translatedX, translatedY, eventTime, moreKeysPanel);
        return true;
    }

    @Override
    public void onClick(View view) {
        if (view == mParams.mWordToSaveView) {
            addToDictionary((CharSequence)view.getTag());
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
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mHandler.cancelAllMessages();
        hidePreview();
    }
}
