/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.inputmethod.keyboard;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Region.Op;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Message;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.inputmethod.compat.FrameLayoutCompatUtils;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.StaticInnerHandlerWrapper;

import java.util.HashMap;

/**
 * A view that renders a virtual {@link Keyboard}.
 *
 * @attr ref R.styleable#KeyboardView_backgroundDimAmount
 * @attr ref R.styleable#KeyboardView_keyBackground
 * @attr ref R.styleable#KeyboardView_keyLetterRatio
 * @attr ref R.styleable#KeyboardView_keyLargeLetterRatio
 * @attr ref R.styleable#KeyboardView_keyLabelRatio
 * @attr ref R.styleable#KeyboardView_keyHintLetterRatio
 * @attr ref R.styleable#KeyboardView_keyUppercaseLetterRatio
 * @attr ref R.styleable#KeyboardView_keyHintLabelRatio
 * @attr ref R.styleable#KeyboardView_keyLabelHorizontalPadding
 * @attr ref R.styleable#KeyboardView_keyHintLetterPadding
 * @attr ref R.styleable#KeyboardView_keyPopupHintLetterPadding
 * @attr ref R.styleable#KeyboardView_keyUppercaseLetterPadding
 * @attr ref R.styleable#KeyboardView_keyTextStyle
 * @attr ref R.styleable#KeyboardView_keyPreviewLayout
 * @attr ref R.styleable#KeyboardView_keyPreviewTextRatio
 * @attr ref R.styleable#KeyboardView_keyPreviewOffset
 * @attr ref R.styleable#KeyboardView_keyPreviewHeight
 * @attr ref R.styleable#KeyboardView_keyTextColor
 * @attr ref R.styleable#KeyboardView_keyTextColorDisabled
 * @attr ref R.styleable#KeyboardView_keyHintLetterColor
 * @attr ref R.styleable#KeyboardView_keyHintLabelColor
 * @attr ref R.styleable#KeyboardView_keyUppercaseLetterInactivatedColor
 * @attr ref R.styleable#KeyboardView_keyUppercaseLetterActivatedColor
 * @attr ref R.styleable#KeyboardView_shadowColor
 * @attr ref R.styleable#KeyboardView_shadowRadius
 */
public class KeyboardView extends View implements PointerTracker.DrawingProxy {
    // Miscellaneous constants
    private static final int[] LONG_PRESSABLE_STATE_SET = { android.R.attr.state_long_pressable };

    // XML attributes
    protected final float mVerticalCorrection;
    protected final int mMoreKeysLayout;
    private final float mBackgroundDimAmount;

    // HORIZONTAL ELLIPSIS "...", character for popup hint.
    private static final String POPUP_HINT_CHAR = "\u2026";

    // Margin between the label and the icon on a key that has both of them.
    // Specified by the fraction of the key width.
    // TODO: Use resource parameter for this value.
    private static final float LABEL_ICON_MARGIN = 0.05f;

    // The maximum key label width in the proportion to the key width.
    private static final float MAX_LABEL_RATIO = 0.90f;

    // Main keyboard
    private Keyboard mKeyboard;
    private final KeyDrawParams mKeyDrawParams;

    // Key preview
    private final int mKeyPreviewLayoutId;
    protected final KeyPreviewDrawParams mKeyPreviewDrawParams;
    private boolean mShowKeyPreviewPopup = true;
    private final int mDelayBeforePreview;
    private int mDelayAfterPreview;
    private ViewGroup mPreviewPlacer;

    // Drawing
    /** True if the entire keyboard needs to be dimmed. */
    private boolean mNeedsToDimBackground;
    /** Whether the keyboard bitmap buffer needs to be redrawn before it's blitted. **/
    private boolean mBufferNeedsUpdate;
    /** The dirty region in the keyboard bitmap */
    private final Rect mDirtyRect = new Rect();
    /** The key to invalidate. */
    private Key mInvalidatedKey;
    /** The dirty region for single key drawing */
    private final Rect mInvalidatedKeyRect = new Rect();
    /** The keyboard bitmap buffer for faster updates */
    private Bitmap mBuffer;
    /** The canvas for the above mutable keyboard bitmap */
    private Canvas mCanvas;
    private final Paint mPaint = new Paint();
    // This map caches key label text height in pixel as value and key label text size as map key.
    private static final HashMap<Integer, Float> sTextHeightCache =
            new HashMap<Integer, Float>();
    // This map caches key label text width in pixel as value and key label text size as map key.
    private static final HashMap<Integer, Float> sTextWidthCache =
            new HashMap<Integer, Float>();
    private static final char[] KEY_LABEL_REFERENCE_CHAR = { 'M' };
    private static final char[] KEY_NUMERIC_HINT_LABEL_REFERENCE_CHAR = { '8' };

    private final DrawingHandler mDrawingHandler = new DrawingHandler(this);

    public static class DrawingHandler extends StaticInnerHandlerWrapper<KeyboardView> {
        private static final int MSG_SHOW_KEY_PREVIEW = 1;
        private static final int MSG_DISMISS_KEY_PREVIEW = 2;

        public DrawingHandler(KeyboardView outerInstance) {
            super(outerInstance);
        }

        @Override
        public void handleMessage(Message msg) {
            final KeyboardView keyboardView = getOuterInstance();
            if (keyboardView == null) return;
            final PointerTracker tracker = (PointerTracker) msg.obj;
            switch (msg.what) {
            case MSG_SHOW_KEY_PREVIEW:
                keyboardView.showKey(msg.arg1, tracker);
                break;
            case MSG_DISMISS_KEY_PREVIEW:
                tracker.getKeyPreviewText().setVisibility(View.INVISIBLE);
                break;
            }
        }

        public void showKeyPreview(long delay, int keyIndex, PointerTracker tracker) {
            removeMessages(MSG_SHOW_KEY_PREVIEW);
            final KeyboardView keyboardView = getOuterInstance();
            if (keyboardView == null) return;
            if (tracker.getKeyPreviewText().getVisibility() == VISIBLE || delay == 0) {
                // Show right away, if it's already visible and finger is moving around
                keyboardView.showKey(keyIndex, tracker);
            } else {
                sendMessageDelayed(
                        obtainMessage(MSG_SHOW_KEY_PREVIEW, keyIndex, 0, tracker), delay);
            }
        }

        public void cancelShowKeyPreview(PointerTracker tracker) {
            removeMessages(MSG_SHOW_KEY_PREVIEW, tracker);
        }

        public void cancelAllShowKeyPreviews() {
            removeMessages(MSG_SHOW_KEY_PREVIEW);
        }

        public void dismissKeyPreview(long delay, PointerTracker tracker) {
            sendMessageDelayed(obtainMessage(MSG_DISMISS_KEY_PREVIEW, tracker), delay);
        }

        public void cancelDismissKeyPreview(PointerTracker tracker) {
            removeMessages(MSG_DISMISS_KEY_PREVIEW, tracker);
        }

        public void cancelAllDismissKeyPreviews() {
            removeMessages(MSG_DISMISS_KEY_PREVIEW);
        }

        public void cancelAllMessages() {
            cancelAllShowKeyPreviews();
            cancelAllDismissKeyPreviews();
        }
    }

    private static class KeyDrawParams {
        // XML attributes
        public final int mKeyTextColor;
        public final int mKeyTextInactivatedColor;
        public final Typeface mKeyTextStyle;
        public final float mKeyLabelHorizontalPadding;
        public final float mKeyHintLetterPadding;
        public final float mKeyPopupHintLetterPadding;
        public final float mKeyUppercaseLetterPadding;
        public final int mShadowColor;
        public final float mShadowRadius;
        public final Drawable mKeyBackground;
        public final int mKeyHintLetterColor;
        public final int mKeyHintLabelColor;
        public final int mKeyUppercaseLetterInactivatedColor;
        public final int mKeyUppercaseLetterActivatedColor;

        private final float mKeyLetterRatio;
        private final float mKeyLargeLetterRatio;
        private final float mKeyLabelRatio;
        private final float mKeyHintLetterRatio;
        private final float mKeyUppercaseLetterRatio;
        private final float mKeyHintLabelRatio;
        private static final float UNDEFINED_RATIO = -1.0f;

        public final Rect mPadding = new Rect();
        public int mKeyLetterSize;
        public int mKeyLargeLetterSize;
        public int mKeyLabelSize;
        public int mKeyHintLetterSize;
        public int mKeyUppercaseLetterSize;
        public int mKeyHintLabelSize;

        public KeyDrawParams(TypedArray a) {
            mKeyBackground = a.getDrawable(R.styleable.KeyboardView_keyBackground);
            if (a.hasValue(R.styleable.KeyboardView_keyLetterSize)) {
                mKeyLetterRatio = UNDEFINED_RATIO;
                mKeyLetterSize = a.getDimensionPixelSize(R.styleable.KeyboardView_keyLetterSize, 0);
            } else {
                mKeyLetterRatio = getRatio(a, R.styleable.KeyboardView_keyLetterRatio);
            }
            if (a.hasValue(R.styleable.KeyboardView_keyLabelSize)) {
                mKeyLabelRatio = UNDEFINED_RATIO;
                mKeyLabelSize = a.getDimensionPixelSize(R.styleable.KeyboardView_keyLabelSize, 0);
            } else {
                mKeyLabelRatio = getRatio(a, R.styleable.KeyboardView_keyLabelRatio);
            }
            mKeyLargeLetterRatio = getRatio(a, R.styleable.KeyboardView_keyLargeLetterRatio);
            mKeyHintLetterRatio = getRatio(a, R.styleable.KeyboardView_keyHintLetterRatio);
            mKeyUppercaseLetterRatio = getRatio(a,
                    R.styleable.KeyboardView_keyUppercaseLetterRatio);
            mKeyHintLabelRatio = getRatio(a, R.styleable.KeyboardView_keyHintLabelRatio);
            mKeyLabelHorizontalPadding = a.getDimension(
                    R.styleable.KeyboardView_keyLabelHorizontalPadding, 0);
            mKeyHintLetterPadding = a.getDimension(
                    R.styleable.KeyboardView_keyHintLetterPadding, 0);
            mKeyPopupHintLetterPadding = a.getDimension(
                    R.styleable.KeyboardView_keyPopupHintLetterPadding, 0);
            mKeyUppercaseLetterPadding = a.getDimension(
                    R.styleable.KeyboardView_keyUppercaseLetterPadding, 0);
            mKeyTextColor = a.getColor(R.styleable.KeyboardView_keyTextColor, 0xFF000000);
            mKeyTextInactivatedColor = a.getColor(
                    R.styleable.KeyboardView_keyTextInactivatedColor, 0xFF000000);
            mKeyHintLetterColor = a.getColor(R.styleable.KeyboardView_keyHintLetterColor, 0);
            mKeyHintLabelColor = a.getColor(R.styleable.KeyboardView_keyHintLabelColor, 0);
            mKeyUppercaseLetterInactivatedColor = a.getColor(
                    R.styleable.KeyboardView_keyUppercaseLetterInactivatedColor, 0);
            mKeyUppercaseLetterActivatedColor = a.getColor(
                    R.styleable.KeyboardView_keyUppercaseLetterActivatedColor, 0);
            mKeyTextStyle = Typeface.defaultFromStyle(
                    a.getInt(R.styleable.KeyboardView_keyTextStyle, Typeface.NORMAL));
            mShadowColor = a.getColor(R.styleable.KeyboardView_shadowColor, 0);
            mShadowRadius = a.getFloat(R.styleable.KeyboardView_shadowRadius, 0f);

            mKeyBackground.getPadding(mPadding);
        }

        public void updateKeyHeight(int keyHeight) {
            if (mKeyLetterRatio >= 0.0f)
                mKeyLetterSize = (int)(keyHeight * mKeyLetterRatio);
            if (mKeyLabelRatio >= 0.0f)
                mKeyLabelSize = (int)(keyHeight * mKeyLabelRatio);
            mKeyLargeLetterSize = (int)(keyHeight * mKeyLargeLetterRatio);
            mKeyHintLetterSize = (int)(keyHeight * mKeyHintLetterRatio);
            mKeyUppercaseLetterSize = (int)(keyHeight * mKeyUppercaseLetterRatio);
            mKeyHintLabelSize = (int)(keyHeight * mKeyHintLabelRatio);
        }
    }

    protected static class KeyPreviewDrawParams {
        // XML attributes.
        public final Drawable mPreviewBackground;
        public final Drawable mPreviewLeftBackground;
        public final Drawable mPreviewRightBackground;
        public final int mPreviewBackgroundWidth;
        public final int mPreviewBackgroundHeight;
        public final int mPreviewTextColor;
        public final int mPreviewOffset;
        public final int mPreviewHeight;
        public final Typeface mKeyTextStyle;

        private final float mPreviewTextRatio;
        private final float mKeyLetterRatio;

        public int mPreviewTextSize;
        public int mKeyLetterSize;
        public final int[] mCoordinates = new int[2];

        private static final int PREVIEW_ALPHA = 240;

        public KeyPreviewDrawParams(TypedArray a, KeyDrawParams keyDrawParams) {
            mPreviewBackground = a.getDrawable(R.styleable.KeyboardView_keyPreviewBackground);
            mPreviewLeftBackground = a.getDrawable(
                    R.styleable.KeyboardView_keyPreviewLeftBackground);
            mPreviewRightBackground = a.getDrawable(
                    R.styleable.KeyboardView_keyPreviewRightBackground);
            setAlpha(mPreviewBackground, PREVIEW_ALPHA);
            setAlpha(mPreviewLeftBackground, PREVIEW_ALPHA);
            setAlpha(mPreviewRightBackground, PREVIEW_ALPHA);
            mPreviewBackgroundWidth = a.getDimensionPixelSize(
                    R.styleable.KeyboardView_keyPreviewBackgroundWidth, 0);
            mPreviewBackgroundHeight = a.getDimensionPixelSize(
                    R.styleable.KeyboardView_keyPreviewBackgroundHeight, 0);
            mPreviewOffset = a.getDimensionPixelOffset(
                    R.styleable.KeyboardView_keyPreviewOffset, 0);
            mPreviewHeight = a.getDimensionPixelSize(
                    R.styleable.KeyboardView_keyPreviewHeight, 80);
            mPreviewTextRatio = getRatio(a, R.styleable.KeyboardView_keyPreviewTextRatio);
            mPreviewTextColor = a.getColor(R.styleable.KeyboardView_keyPreviewTextColor, 0);

            mKeyLetterRatio = keyDrawParams.mKeyLetterRatio;
            mKeyTextStyle = keyDrawParams.mKeyTextStyle;
        }

        public void updateKeyHeight(int keyHeight) {
            mPreviewTextSize = (int)(keyHeight * mPreviewTextRatio);
            mKeyLetterSize = (int)(keyHeight * mKeyLetterRatio);
        }

        private static void setAlpha(Drawable drawable, int alpha) {
            if (drawable == null)
                return;
            drawable.setAlpha(alpha);
        }
    }

    public KeyboardView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.keyboardViewStyle);
    }

    public KeyboardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.KeyboardView, defStyle, R.style.KeyboardView);

        mKeyDrawParams = new KeyDrawParams(a);
        mKeyPreviewDrawParams = new KeyPreviewDrawParams(a, mKeyDrawParams);
        mKeyPreviewLayoutId = a.getResourceId(R.styleable.KeyboardView_keyPreviewLayout, 0);
        if (mKeyPreviewLayoutId == 0) {
            mShowKeyPreviewPopup = false;
        }
        mVerticalCorrection = a.getDimensionPixelOffset(
                R.styleable.KeyboardView_verticalCorrection, 0);
        mMoreKeysLayout = a.getResourceId(R.styleable.KeyboardView_moreKeysLayout, 0);
        mBackgroundDimAmount = a.getFloat(R.styleable.KeyboardView_backgroundDimAmount, 0.5f);
        a.recycle();

        final Resources res = getResources();

        mDelayBeforePreview = res.getInteger(R.integer.config_delay_before_preview);
        mDelayAfterPreview = res.getInteger(R.integer.config_delay_after_preview);

        mPaint.setAntiAlias(true);
        mPaint.setTextAlign(Align.CENTER);
        mPaint.setAlpha(255);
    }

    // Read fraction value in TypedArray as float.
    private static float getRatio(TypedArray a, int index) {
        return a.getFraction(index, 1000, 1000, 1) / 1000.0f;
    }

    /**
     * Attaches a keyboard to this view. The keyboard can be switched at any time and the
     * view will re-layout itself to accommodate the keyboard.
     * @see Keyboard
     * @see #getKeyboard()
     * @param keyboard the keyboard to display in this view
     */
    public void setKeyboard(Keyboard keyboard) {
        // Remove any pending dismissing preview
        mDrawingHandler.cancelAllShowKeyPreviews();
        if (mKeyboard != null) {
            PointerTracker.dismissAllKeyPreviews();
        }
        mKeyboard = keyboard;
        LatinImeLogger.onSetKeyboard(keyboard);
        requestLayout();
        mDirtyRect.set(0, 0, getWidth(), getHeight());
        mBufferNeedsUpdate = true;
        invalidateAllKeys();
        final int keyHeight = keyboard.mMostCommonKeyHeight - keyboard.mVerticalGap;
        mKeyDrawParams.updateKeyHeight(keyHeight);
        mKeyPreviewDrawParams.updateKeyHeight(keyHeight);
    }

    /**
     * Returns the current keyboard being displayed by this view.
     * @return the currently attached keyboard
     * @see #setKeyboard(Keyboard)
     */
    public Keyboard getKeyboard() {
        return mKeyboard;
    }

    /**
     * Enables or disables the key feedback popup. This is a popup that shows a magnified
     * version of the depressed key. By default the preview is enabled.
     * @param previewEnabled whether or not to enable the key feedback preview
     * @param delay the delay after which the preview is dismissed
     * @see #isKeyPreviewPopupEnabled()
     */
    public void setKeyPreviewPopupEnabled(boolean previewEnabled, int delay) {
        mShowKeyPreviewPopup = previewEnabled;
        mDelayAfterPreview = delay;
    }

    /**
     * Returns the enabled state of the key feedback preview
     * @return whether or not the key feedback preview is enabled
     * @see #setKeyPreviewPopupEnabled(boolean, int)
     */
    public boolean isKeyPreviewPopupEnabled() {
        return mShowKeyPreviewPopup;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mKeyboard != null) {
            // The main keyboard expands to the display width.
            final int height = mKeyboard.mOccupiedHeight + getPaddingTop() + getPaddingBottom();
            setMeasuredDimension(widthMeasureSpec, height);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mBufferNeedsUpdate || mBuffer == null) {
            mBufferNeedsUpdate = false;
            onBufferDraw();
        }
        canvas.drawBitmap(mBuffer, 0, 0, null);
    }

    private void onBufferDraw() {
        final int width = getWidth();
        final int height = getHeight();
        if (width == 0 || height == 0)
            return;
        if (mBuffer == null || mBuffer.getWidth() != width || mBuffer.getHeight() != height) {
            if (mBuffer != null)
                mBuffer.recycle();
            mBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            mDirtyRect.union(0, 0, width, height);
            if (mCanvas != null) {
                mCanvas.setBitmap(mBuffer);
            } else {
                mCanvas = new Canvas(mBuffer);
            }
        }
        final Canvas canvas = mCanvas;
        canvas.clipRect(mDirtyRect, Op.REPLACE);
        canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR);

        if (mKeyboard == null) return;

        final boolean isManualTemporaryUpperCase = mKeyboard.isManualTemporaryUpperCase();
        final KeyDrawParams params = mKeyDrawParams;
        if (mInvalidatedKey != null && mInvalidatedKeyRect.contains(mDirtyRect)) {
            // Draw a single key.
            final int keyDrawX = mInvalidatedKey.mX + mInvalidatedKey.mVisualInsetsLeft
                    + getPaddingLeft();
            final int keyDrawY = mInvalidatedKey.mY + getPaddingTop();
            canvas.translate(keyDrawX, keyDrawY);
            onBufferDrawKey(mInvalidatedKey, mKeyboard, canvas, mPaint, params,
                    isManualTemporaryUpperCase);
            canvas.translate(-keyDrawX, -keyDrawY);
        } else {
            // Draw all keys.
            for (final Key key : mKeyboard.mKeys) {
                final int keyDrawX = key.mX + key.mVisualInsetsLeft + getPaddingLeft();
                final int keyDrawY = key.mY + getPaddingTop();
                canvas.translate(keyDrawX, keyDrawY);
                onBufferDrawKey(key, mKeyboard, canvas, mPaint, params, isManualTemporaryUpperCase);
                canvas.translate(-keyDrawX, -keyDrawY);
            }
        }

        // Overlay a dark rectangle to dim the entire keyboard
        if (mNeedsToDimBackground) {
            mPaint.setColor((int) (mBackgroundDimAmount * 0xFF) << 24);
            canvas.drawRect(0, 0, width, height, mPaint);
        }

        mInvalidatedKey = null;
        mDirtyRect.setEmpty();
    }

    public void dimEntireKeyboard(boolean dimmed) {
        final boolean needsRedrawing = mNeedsToDimBackground != dimmed;
        mNeedsToDimBackground = dimmed;
        if (needsRedrawing) {
            invalidateAllKeys();
        }
    }

    private static void onBufferDrawKey(final Key key, final Keyboard keyboard, final Canvas canvas,
            Paint paint, KeyDrawParams params, boolean isManualTemporaryUpperCase) {
        final boolean debugShowAlign = LatinImeLogger.sVISUALDEBUG;
        // Draw key background.
        if (!key.isSpacer()) {
            final int bgWidth = key.mWidth - key.mVisualInsetsLeft - key.mVisualInsetsRight
                    + params.mPadding.left + params.mPadding.right;
            final int bgHeight = key.mHeight + params.mPadding.top + params.mPadding.bottom;
            final int bgX = -params.mPadding.left;
            final int bgY = -params.mPadding.top;
            final int[] drawableState = key.getCurrentDrawableState();
            final Drawable background = params.mKeyBackground;
            background.setState(drawableState);
            final Rect bounds = background.getBounds();
            if (bgWidth != bounds.right || bgHeight != bounds.bottom) {
                background.setBounds(0, 0, bgWidth, bgHeight);
            }
            canvas.translate(bgX, bgY);
            background.draw(canvas);
            if (debugShowAlign) {
                drawRectangle(canvas, 0, 0, bgWidth, bgHeight, 0x80c00000, new Paint());
            }
            canvas.translate(-bgX, -bgY);
        }

        // Draw key top visuals.
        final int keyWidth = key.mWidth - key.mVisualInsetsLeft - key.mVisualInsetsRight;
        final int keyHeight = key.mHeight;
        final float centerX = keyWidth * 0.5f;
        final float centerY = keyHeight * 0.5f;

        if (debugShowAlign) {
            drawRectangle(canvas, 0, 0, keyWidth, keyHeight, 0x800000c0, new Paint());
        }

        // Draw key label.
        final Drawable icon = key.getIcon();
        float positionX = centerX;
        if (key.mLabel != null) {
            // Switch the character to uppercase if shift is pressed
            final CharSequence label = keyboard.adjustLabelCase(key.mLabel);
            // For characters, use large font. For labels like "Done", use smaller font.
            paint.setTypeface(key.selectTypeface(params.mKeyTextStyle));
            final int labelSize = key.selectTextSize(params.mKeyLetterSize,
                    params.mKeyLargeLetterSize, params.mKeyLabelSize, params.mKeyHintLabelSize);
            paint.setTextSize(labelSize);
            final float labelCharHeight = getCharHeight(KEY_LABEL_REFERENCE_CHAR, paint);
            final float labelCharWidth = getCharWidth(KEY_LABEL_REFERENCE_CHAR, paint);

            // Vertical label text alignment.
            final float baseline = centerY + labelCharHeight / 2;

            // Horizontal label text alignment
            float labelWidth = 0;
            if (key.isAlignLeft()) {
                positionX = (int)params.mKeyLabelHorizontalPadding;
                paint.setTextAlign(Align.LEFT);
            } else if (key.isAlignRight()) {
                positionX = keyWidth - (int)params.mKeyLabelHorizontalPadding;
                paint.setTextAlign(Align.RIGHT);
            } else if (key.isAlignLeftOfCenter()) {
                // TODO: Parameterise this?
                positionX = centerX - labelCharWidth * 7 / 4;
                paint.setTextAlign(Align.LEFT);
            } else if (key.hasLabelWithIconLeft() && icon != null) {
                labelWidth = getLabelWidth(label, paint) + icon.getIntrinsicWidth()
                        + LABEL_ICON_MARGIN * keyWidth;
                positionX = centerX + labelWidth / 2;
                paint.setTextAlign(Align.RIGHT);
            } else if (key.hasLabelWithIconRight() && icon != null) {
                labelWidth = getLabelWidth(label, paint) + icon.getIntrinsicWidth()
                        + LABEL_ICON_MARGIN * keyWidth;
                positionX = centerX - labelWidth / 2;
                paint.setTextAlign(Align.LEFT);
            } else {
                positionX = centerX;
                paint.setTextAlign(Align.CENTER);
            }
            if (key.needsXScale()) {
                paint.setTextScaleX(
                        Math.min(1.0f, (keyWidth * MAX_LABEL_RATIO) / getLabelWidth(label, paint)));
            }

            if (key.hasUppercaseLetter() && isManualTemporaryUpperCase) {
                paint.setColor(params.mKeyTextInactivatedColor);
            } else {
                paint.setColor(params.mKeyTextColor);
            }
            if (key.isEnabled()) {
                // Set a drop shadow for the text
                paint.setShadowLayer(params.mShadowRadius, 0, 0, params.mShadowColor);
            } else {
                // Make label invisible
                paint.setColor(Color.TRANSPARENT);
            }
            canvas.drawText(label, 0, label.length(), positionX, baseline, paint);
            // Turn off drop shadow and reset x-scale.
            paint.setShadowLayer(0, 0, 0, 0);
            paint.setTextScaleX(1.0f);

            if (icon != null) {
                final int iconWidth = icon.getIntrinsicWidth();
                final int iconHeight = icon.getIntrinsicHeight();
                final int iconY = (keyHeight - iconHeight) / 2;
                if (key.hasLabelWithIconLeft()) {
                    final int iconX = (int)(centerX - labelWidth / 2);
                    drawIcon(canvas, icon, iconX, iconY, iconWidth, iconHeight);
                } else if (key.hasLabelWithIconRight()) {
                    final int iconX = (int)(centerX + labelWidth / 2 - iconWidth);
                    drawIcon(canvas, icon, iconX, iconY, iconWidth, iconHeight);
                }
            }

            if (debugShowAlign) {
                final Paint line = new Paint();
                drawHorizontalLine(canvas, baseline, keyWidth, 0xc0008000, line);
                drawVerticalLine(canvas, positionX, keyHeight, 0xc0800080, line);
            }
        }

        // Draw hint label.
        if (key.mHintLabel != null) {
            final CharSequence hint = key.mHintLabel;
            final int hintColor;
            final int hintSize;
            if (key.hasHintLabel()) {
                hintColor = params.mKeyHintLabelColor;
                hintSize = params.mKeyHintLabelSize;
                paint.setTypeface(Typeface.DEFAULT);
            } else if (key.hasUppercaseLetter()) {
                hintColor = isManualTemporaryUpperCase
                        ? params.mKeyUppercaseLetterActivatedColor
                        : params.mKeyUppercaseLetterInactivatedColor;
                hintSize = params.mKeyUppercaseLetterSize;
            } else { // key.hasHintLetter()
                hintColor = params.mKeyHintLetterColor;
                hintSize = params.mKeyHintLetterSize;
            }
            paint.setColor(hintColor);
            paint.setTextSize(hintSize);
            final float hintX, hintY;
            if (key.hasHintLabel()) {
                // The hint label is placed just right of the key label. Used mainly on
                // "phone number" layout.
                // TODO: Generalize the following calculations.
                hintX = positionX + getCharWidth(KEY_LABEL_REFERENCE_CHAR, paint) * 2;
                hintY = centerY + getCharHeight(KEY_LABEL_REFERENCE_CHAR, paint) / 2;
                paint.setTextAlign(Align.LEFT);
            } else if (key.hasUppercaseLetter()) {
                // The hint label is placed at top-right corner of the key. Used mainly on tablet.
                hintX = keyWidth - params.mKeyUppercaseLetterPadding
                        - getCharWidth(KEY_LABEL_REFERENCE_CHAR, paint) / 2;
                hintY = -paint.ascent();
                paint.setTextAlign(Align.CENTER);
            } else { // key.hasHintLetter()
                // The hint label is placed at top-right corner of the key. Used mainly on phone.
                hintX = keyWidth - params.mKeyHintLetterPadding
                        - getCharWidth(KEY_NUMERIC_HINT_LABEL_REFERENCE_CHAR, paint) / 2;
                hintY = -paint.ascent();
                paint.setTextAlign(Align.CENTER);
            }
            canvas.drawText(hint, 0, hint.length(), hintX, hintY, paint);

            if (debugShowAlign) {
                final Paint line = new Paint();
                drawHorizontalLine(canvas, (int)hintY, keyWidth, 0xc0808000, line);
                drawVerticalLine(canvas, (int)hintX, keyHeight, 0xc0808000, line);
            }
        }

        // Draw key icon.
        if (key.mLabel == null && icon != null) {
            final int iconWidth = icon.getIntrinsicWidth();
            final int iconHeight = icon.getIntrinsicHeight();
            final int iconX, alignX;
            final int iconY = (keyHeight - iconHeight) / 2;
            if (key.isAlignLeft()) {
                iconX = (int)params.mKeyLabelHorizontalPadding;
                alignX = iconX;
            } else if (key.isAlignRight()) {
                iconX = keyWidth - (int)params.mKeyLabelHorizontalPadding - iconWidth;
                alignX = iconX + iconWidth;
            } else { // Align center
                iconX = (keyWidth - iconWidth) / 2;
                alignX = iconX + iconWidth / 2;
            }
            drawIcon(canvas, icon, iconX, iconY, iconWidth, iconHeight);

            if (debugShowAlign) {
                final Paint line = new Paint();
                drawVerticalLine(canvas, alignX, keyHeight, 0xc0800080, line);
                drawRectangle(canvas, iconX, iconY, iconWidth, iconHeight, 0x80c00000, line);
            }
        }

        // Draw popup hint "..." at the bottom right corner of the key.
        if ((key.hasPopupHint() && key.mMoreKeys != null && key.mMoreKeys.length > 0)
                || key.needsSpecialPopupHint()) {
            paint.setTextSize(params.mKeyHintLetterSize);
            paint.setColor(params.mKeyHintLabelColor);
            paint.setTextAlign(Align.CENTER);
            final float hintX = keyWidth - params.mKeyHintLetterPadding
                    - getCharWidth(KEY_LABEL_REFERENCE_CHAR, paint) / 2;
            final float hintY = keyHeight - params.mKeyPopupHintLetterPadding;
            canvas.drawText(POPUP_HINT_CHAR, hintX, hintY, paint);

            if (debugShowAlign) {
                final Paint line = new Paint();
                drawHorizontalLine(canvas, (int)hintY, keyWidth, 0xc0808000, line);
                drawVerticalLine(canvas, (int)hintX, keyHeight, 0xc0808000, line);
            }
        }
    }

    private static final Rect sTextBounds = new Rect();

    private static int getCharGeometryCacheKey(char reference, Paint paint) {
        final int labelSize = (int)paint.getTextSize();
        final Typeface face = paint.getTypeface();
        final int codePointOffset = reference << 15;
        if (face == Typeface.DEFAULT) {
            return codePointOffset + labelSize;
        } else if (face == Typeface.DEFAULT_BOLD) {
            return codePointOffset + labelSize + 0x1000;
        } else if (face == Typeface.MONOSPACE) {
            return codePointOffset + labelSize + 0x2000;
        } else {
            return codePointOffset + labelSize;
        }
    }

    private static float getCharHeight(char[] character, Paint paint) {
        final Integer key = getCharGeometryCacheKey(character[0], paint);
        final Float cachedValue = sTextHeightCache.get(key);
        if (cachedValue != null)
            return cachedValue;

        paint.getTextBounds(character, 0, 1, sTextBounds);
        final float height = sTextBounds.height();
        sTextHeightCache.put(key, height);
        return height;
    }

    private static float getCharWidth(char[] character, Paint paint) {
        final Integer key = getCharGeometryCacheKey(character[0], paint);
        final Float cachedValue = sTextWidthCache.get(key);
        if (cachedValue != null)
            return cachedValue;

        paint.getTextBounds(character, 0, 1, sTextBounds);
        final float width = sTextBounds.width();
        sTextWidthCache.put(key, width);
        return width;
    }

    private static float getLabelWidth(CharSequence label, Paint paint) {
        paint.getTextBounds(label.toString(), 0, label.length(), sTextBounds);
        return sTextBounds.width();
    }

    public float getDefaultLabelWidth(CharSequence label, Paint paint) {
        paint.setTextSize(mKeyDrawParams.mKeyLabelSize);
        paint.setTypeface(mKeyDrawParams.mKeyTextStyle);
        return getLabelWidth(label, paint);
    }

    private static void drawIcon(Canvas canvas, Drawable icon, int x, int y, int width,
            int height) {
        canvas.translate(x, y);
        icon.setBounds(0, 0, width, height);
        icon.draw(canvas);
        canvas.translate(-x, -y);
    }

    private static void drawHorizontalLine(Canvas canvas, float y, float w, int color,
            Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.0f);
        paint.setColor(color);
        canvas.drawLine(0, y, w, y, paint);
    }

    private static void drawVerticalLine(Canvas canvas, float x, float h, int color, Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.0f);
        paint.setColor(color);
        canvas.drawLine(x, 0, x, h, paint);
    }

    private static void drawRectangle(Canvas canvas, float x, float y, float w, float h, int color,
            Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.0f);
        paint.setColor(color);
        canvas.translate(x, y);
        canvas.drawRect(0, 0, w, h, paint);
        canvas.translate(-x, -y);
    }

    public void cancelAllMessages() {
        mDrawingHandler.cancelAllMessages();
    }

    // Called by {@link PointerTracker} constructor to create a TextView.
    @Override
    public TextView inflateKeyPreviewText() {
        final Context context = getContext();
        if (mKeyPreviewLayoutId != 0) {
            return (TextView)LayoutInflater.from(context).inflate(mKeyPreviewLayoutId, null);
        } else {
            return new TextView(context);
        }
    }

    @Override
    public void showKeyPreview(int keyIndex, PointerTracker tracker) {
        if (mShowKeyPreviewPopup) {
            mDrawingHandler.showKeyPreview(mDelayBeforePreview, keyIndex, tracker);
        }
    }

    @Override
    public void cancelShowKeyPreview(PointerTracker tracker) {
        mDrawingHandler.cancelShowKeyPreview(tracker);
    }

    @Override
    public void dismissKeyPreview(PointerTracker tracker) {
        mDrawingHandler.cancelShowKeyPreview(tracker);
        mDrawingHandler.dismissKeyPreview(mDelayAfterPreview, tracker);
    }

    private void addKeyPreview(TextView keyPreview) {
        if (mPreviewPlacer == null) {
            mPreviewPlacer = new RelativeLayout(getContext());
            final ViewGroup windowContentView =
                    (ViewGroup)getRootView().findViewById(android.R.id.content);
            windowContentView.addView(mPreviewPlacer);
        }
        mPreviewPlacer.addView(
                keyPreview, FrameLayoutCompatUtils.newLayoutParam(mPreviewPlacer, 0, 0));
    }

    private void showKey(final int keyIndex, PointerTracker tracker) {
        final TextView previewText = tracker.getKeyPreviewText();
        // If the key preview has no parent view yet, add it to the ViewGroup which can place
        // key preview absolutely in SoftInputWindow.
        if (previewText.getParent() == null) {
            addKeyPreview(previewText);
        }

        mDrawingHandler.cancelDismissKeyPreview(tracker);
        final Key key = tracker.getKey(keyIndex);
        // If keyIndex is invalid or IME is already closed, we must not show key preview.
        // Trying to show key preview while root window is closed causes
        // WindowManager.BadTokenException.
        if (key == null)
            return;

        final KeyPreviewDrawParams params = mKeyPreviewDrawParams;
        final int keyDrawX = key.mX + key.mVisualInsetsLeft;
        final int keyDrawWidth = key.mWidth - key.mVisualInsetsLeft - key.mVisualInsetsRight;
        // What we show as preview should match what we show on key top in onBufferDraw(). 
        if (key.mLabel != null) {
            // TODO Should take care of temporaryShiftLabel here.
            previewText.setCompoundDrawables(null, null, null, null);
            if (key.mLabel.length() > 1) {
                previewText.setTextSize(TypedValue.COMPLEX_UNIT_PX, params.mKeyLetterSize);
                previewText.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                previewText.setTextSize(TypedValue.COMPLEX_UNIT_PX, params.mPreviewTextSize);
                previewText.setTypeface(params.mKeyTextStyle);
            }
            previewText.setText(mKeyboard.adjustLabelCase(key.mLabel));
        } else {
            final Drawable previewIcon = key.getPreviewIcon();
            previewText.setCompoundDrawables(null, null, null,
                   previewIcon != null ? previewIcon : key.getIcon());
            previewText.setText(null);
        }
        previewText.setBackgroundDrawable(params.mPreviewBackground);

        previewText.measure(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        final int previewWidth = Math.max(previewText.getMeasuredWidth(), keyDrawWidth
                + previewText.getPaddingLeft() + previewText.getPaddingRight());
        final int previewHeight = params.mPreviewHeight;
        getLocationInWindow(params.mCoordinates);
        int previewX = keyDrawX - (previewWidth - keyDrawWidth) / 2 + params.mCoordinates[0];
        final int previewY = key.mY - previewHeight
                + params.mCoordinates[1] + params.mPreviewOffset;
        if (previewX < 0 && params.mPreviewLeftBackground != null) {
            previewText.setBackgroundDrawable(params.mPreviewLeftBackground);
            previewX = 0;
        } else if (previewX + previewWidth > getWidth() && params.mPreviewRightBackground != null) {
            previewText.setBackgroundDrawable(params.mPreviewRightBackground);
            previewX = getWidth() - previewWidth;
        }

        // Set the preview background state
        previewText.getBackground().setState(
                key.mMoreKeys != null ? LONG_PRESSABLE_STATE_SET : EMPTY_STATE_SET);
        previewText.setTextColor(params.mPreviewTextColor);
        FrameLayoutCompatUtils.placeViewAt(
                previewText, previewX, previewY, previewWidth, previewHeight);
        previewText.setVisibility(VISIBLE);
    }

    /**
     * Requests a redraw of the entire keyboard. Calling {@link #invalidate} is not sufficient
     * because the keyboard renders the keys to an off-screen buffer and an invalidate() only
     * draws the cached buffer.
     * @see #invalidateKey(Key)
     */
    public void invalidateAllKeys() {
        mDirtyRect.union(0, 0, getWidth(), getHeight());
        mBufferNeedsUpdate = true;
        invalidate();
    }

    /**
     * Invalidates a key so that it will be redrawn on the next repaint. Use this method if only
     * one key is changing it's content. Any changes that affect the position or size of the key
     * may not be honored.
     * @param key key in the attached {@link Keyboard}.
     * @see #invalidateAllKeys
     */
    @Override
    public void invalidateKey(Key key) {
        if (key == null)
            return;
        mInvalidatedKey = key;
        final int x = key.mX + getPaddingLeft();
        final int y = key.mY + getPaddingTop();
        mInvalidatedKeyRect.set(x, y, x + key.mWidth, y + key.mHeight);
        mDirtyRect.union(mInvalidatedKeyRect);
        mBufferNeedsUpdate = true;
        invalidate(mInvalidatedKeyRect);
    }

    public void closing() {
        PointerTracker.dismissAllKeyPreviews();
        cancelAllMessages();

        mDirtyRect.union(0, 0, getWidth(), getHeight());
        requestLayout();
    }

    @Override
    public boolean dismissMoreKeysPanel() {
        return false;
    }

    public void purgeKeyboardAndClosing() {
        mKeyboard = null;
        closing();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        closing();
        if (mPreviewPlacer != null) {
            mPreviewPlacer.removeAllViews();
        }
        if (mBuffer != null) {
            mBuffer.recycle();
            mBuffer = null;
        }
    }
}
