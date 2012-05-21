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

import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.StaticInnerHandlerWrapper;
import com.android.inputmethod.latin.StringUtils;

import java.util.HashMap;
import java.util.HashSet;

/**
 * A view that renders a virtual {@link Keyboard}.
 *
 * @attr ref R.styleable#KeyboardView_backgroundDimAlpha
 * @attr ref R.styleable#KeyboardView_keyBackground
 * @attr ref R.styleable#KeyboardView_keyLetterRatio
 * @attr ref R.styleable#KeyboardView_keyLargeLetterRatio
 * @attr ref R.styleable#KeyboardView_keyLabelRatio
 * @attr ref R.styleable#KeyboardView_keyHintLetterRatio
 * @attr ref R.styleable#KeyboardView_keyShiftedLetterHintRatio
 * @attr ref R.styleable#KeyboardView_keyHintLabelRatio
 * @attr ref R.styleable#KeyboardView_keyLabelHorizontalPadding
 * @attr ref R.styleable#KeyboardView_keyHintLetterPadding
 * @attr ref R.styleable#KeyboardView_keyPopupHintLetterPadding
 * @attr ref R.styleable#KeyboardView_keyShiftedLetterHintPadding
 * @attr ref R.styleable#KeyboardView_keyTextStyle
 * @attr ref R.styleable#KeyboardView_keyPreviewLayout
 * @attr ref R.styleable#KeyboardView_keyPreviewTextRatio
 * @attr ref R.styleable#KeyboardView_keyPreviewOffset
 * @attr ref R.styleable#KeyboardView_keyPreviewHeight
 * @attr ref R.styleable#KeyboardView_keyTextColor
 * @attr ref R.styleable#KeyboardView_keyTextColorDisabled
 * @attr ref R.styleable#KeyboardView_keyHintLetterColor
 * @attr ref R.styleable#KeyboardView_keyHintLabelColor
 * @attr ref R.styleable#KeyboardView_keyShiftedLetterHintInactivatedColor
 * @attr ref R.styleable#KeyboardView_keyShiftedLetterHintActivatedColor
 * @attr ref R.styleable#KeyboardView_shadowColor
 * @attr ref R.styleable#KeyboardView_shadowRadius
 */
public class KeyboardView extends View implements PointerTracker.DrawingProxy {
    // Miscellaneous constants
    private static final int[] LONG_PRESSABLE_STATE_SET = { android.R.attr.state_long_pressable };

    // XML attributes
    protected final float mVerticalCorrection;
    protected final int mMoreKeysLayout;
    private final int mBackgroundDimAlpha;

    // HORIZONTAL ELLIPSIS "...", character for popup hint.
    private static final String POPUP_HINT_CHAR = "\u2026";

    // Margin between the label and the icon on a key that has both of them.
    // Specified by the fraction of the key width.
    // TODO: Use resource parameter for this value.
    private static final float LABEL_ICON_MARGIN = 0.05f;

    // The maximum key label width in the proportion to the key width.
    private static final float MAX_LABEL_RATIO = 0.90f;

    private final static int ALPHA_OPAQUE = 255;

    // Main keyboard
    private Keyboard mKeyboard;
    protected final KeyDrawParams mKeyDrawParams;

    // Key preview
    private final int mKeyPreviewLayoutId;
    protected final KeyPreviewDrawParams mKeyPreviewDrawParams;
    private boolean mShowKeyPreviewPopup = true;
    private int mDelayAfterPreview;
    private ViewGroup mPreviewPlacer;

    // Drawing
    /** True if the entire keyboard needs to be dimmed. */
    private boolean mNeedsToDimEntireKeyboard;
    /** Whether the keyboard bitmap buffer needs to be redrawn before it's blitted. **/
    private boolean mBufferNeedsUpdate;
    /** True if all keys should be drawn */
    private boolean mInvalidateAllKeys;
    /** The keys that should be drawn */
    private final HashSet<Key> mInvalidatedKeys = new HashSet<Key>();
    /** The region of invalidated keys */
    private final Rect mInvalidatedKeysRect = new Rect();
    /** The keyboard bitmap buffer for faster updates */
    private Bitmap mBuffer;
    /** The canvas for the above mutable keyboard bitmap */
    private Canvas mCanvas;
    private final Paint mPaint = new Paint();
    private final Paint.FontMetrics mFontMetrics = new Paint.FontMetrics();
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
        private static final int MSG_DISMISS_KEY_PREVIEW = 1;

        public DrawingHandler(KeyboardView outerInstance) {
            super(outerInstance);
        }

        @Override
        public void handleMessage(Message msg) {
            final KeyboardView keyboardView = getOuterInstance();
            if (keyboardView == null) return;
            final PointerTracker tracker = (PointerTracker) msg.obj;
            switch (msg.what) {
            case MSG_DISMISS_KEY_PREVIEW:
                tracker.getKeyPreviewText().setVisibility(View.INVISIBLE);
                break;
            }
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
            cancelAllDismissKeyPreviews();
        }
    }

    protected static class KeyDrawParams {
        // XML attributes
        public final int mKeyTextColor;
        public final int mKeyTextInactivatedColor;
        public final Typeface mKeyTextStyle;
        public final float mKeyLabelHorizontalPadding;
        public final float mKeyHintLetterPadding;
        public final float mKeyPopupHintLetterPadding;
        public final float mKeyShiftedLetterHintPadding;
        public final int mShadowColor;
        public final float mShadowRadius;
        public final Drawable mKeyBackground;
        public final int mKeyHintLetterColor;
        public final int mKeyHintLabelColor;
        public final int mKeyShiftedLetterHintInactivatedColor;
        public final int mKeyShiftedLetterHintActivatedColor;

        /* package */ final float mKeyLetterRatio;
        private final float mKeyLargeLetterRatio;
        private final float mKeyLabelRatio;
        private final float mKeyLargeLabelRatio;
        private final float mKeyHintLetterRatio;
        private final float mKeyShiftedLetterHintRatio;
        private final float mKeyHintLabelRatio;
        private static final float UNDEFINED_RATIO = -1.0f;

        public final Rect mPadding = new Rect();
        public int mKeyLetterSize;
        public int mKeyLargeLetterSize;
        public int mKeyLabelSize;
        public int mKeyLargeLabelSize;
        public int mKeyHintLetterSize;
        public int mKeyShiftedLetterHintSize;
        public int mKeyHintLabelSize;
        public int mAnimAlpha;

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
            mKeyLargeLabelRatio = getRatio(a, R.styleable.KeyboardView_keyLargeLabelRatio);
            mKeyLargeLetterRatio = getRatio(a, R.styleable.KeyboardView_keyLargeLetterRatio);
            mKeyHintLetterRatio = getRatio(a, R.styleable.KeyboardView_keyHintLetterRatio);
            mKeyShiftedLetterHintRatio = getRatio(a,
                    R.styleable.KeyboardView_keyShiftedLetterHintRatio);
            mKeyHintLabelRatio = getRatio(a, R.styleable.KeyboardView_keyHintLabelRatio);
            mKeyLabelHorizontalPadding = a.getDimension(
                    R.styleable.KeyboardView_keyLabelHorizontalPadding, 0);
            mKeyHintLetterPadding = a.getDimension(
                    R.styleable.KeyboardView_keyHintLetterPadding, 0);
            mKeyPopupHintLetterPadding = a.getDimension(
                    R.styleable.KeyboardView_keyPopupHintLetterPadding, 0);
            mKeyShiftedLetterHintPadding = a.getDimension(
                    R.styleable.KeyboardView_keyShiftedLetterHintPadding, 0);
            mKeyTextColor = a.getColor(R.styleable.KeyboardView_keyTextColor, 0xFF000000);
            mKeyTextInactivatedColor = a.getColor(
                    R.styleable.KeyboardView_keyTextInactivatedColor, 0xFF000000);
            mKeyHintLetterColor = a.getColor(R.styleable.KeyboardView_keyHintLetterColor, 0);
            mKeyHintLabelColor = a.getColor(R.styleable.KeyboardView_keyHintLabelColor, 0);
            mKeyShiftedLetterHintInactivatedColor = a.getColor(
                    R.styleable.KeyboardView_keyShiftedLetterHintInactivatedColor, 0);
            mKeyShiftedLetterHintActivatedColor = a.getColor(
                    R.styleable.KeyboardView_keyShiftedLetterHintActivatedColor, 0);
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
            mKeyLargeLabelSize = (int)(keyHeight * mKeyLargeLabelRatio);
            mKeyLargeLetterSize = (int)(keyHeight * mKeyLargeLetterRatio);
            mKeyHintLetterSize = (int)(keyHeight * mKeyHintLetterRatio);
            mKeyShiftedLetterHintSize = (int)(keyHeight * mKeyShiftedLetterHintRatio);
            mKeyHintLabelSize = (int)(keyHeight * mKeyHintLabelRatio);
        }

        public void blendAlpha(Paint paint) {
            final int color = paint.getColor();
            paint.setARGB((paint.getAlpha() * mAnimAlpha) / ALPHA_OPAQUE,
                    Color.red(color), Color.green(color), Color.blue(color));
        }
    }

    /* package */ static class KeyPreviewDrawParams {
        // XML attributes.
        public final Drawable mPreviewBackground;
        public final Drawable mPreviewLeftBackground;
        public final Drawable mPreviewRightBackground;
        public final int mPreviewTextColor;
        public final int mPreviewOffset;
        public final int mPreviewHeight;
        public final Typeface mKeyTextStyle;
        public final int mLingerTimeout;

        private final float mPreviewTextRatio;
        private final float mKeyLetterRatio;

        // The graphical geometry of the key preview.
        // <-width->
        // +-------+   ^
        // |       |   |
        // |preview| height (visible)
        // |       |   |
        // +       + ^ v
        //  \     /  |offset
        // +-\   /-+ v
        // |  +-+  |
        // |parent |
        // |    key|
        // +-------+
        // The background of a {@link TextView} being used for a key preview may have invisible
        // paddings. To align the more keys keyboard panel's visible part with the visible part of
        // the background, we need to record the width and height of key preview that don't include
        // invisible paddings.
        public int mPreviewVisibleWidth;
        public int mPreviewVisibleHeight;
        // The key preview may have an arbitrary offset and its background that may have a bottom
        // padding. To align the more keys keyboard and the key preview we also need to record the
        // offset between the top edge of parent key and the bottom of the visible part of key
        // preview background.
        public int mPreviewVisibleOffset;

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
            mPreviewOffset = a.getDimensionPixelOffset(
                    R.styleable.KeyboardView_keyPreviewOffset, 0);
            mPreviewHeight = a.getDimensionPixelSize(
                    R.styleable.KeyboardView_keyPreviewHeight, 80);
            mPreviewTextRatio = getRatio(a, R.styleable.KeyboardView_keyPreviewTextRatio);
            mPreviewTextColor = a.getColor(R.styleable.KeyboardView_keyPreviewTextColor, 0);
            mLingerTimeout = a.getInt(R.styleable.KeyboardView_keyPreviewLingerTimeout, 0);

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
        mBackgroundDimAlpha = a.getInt(R.styleable.KeyboardView_backgroundDimAlpha, 0);
        a.recycle();

        mDelayAfterPreview = mKeyPreviewDrawParams.mLingerTimeout;

        mPaint.setAntiAlias(true);
    }

    // Read fraction value in TypedArray as float.
    /* package */ static float getRatio(TypedArray a, int index) {
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
        mKeyboard = keyboard;
        LatinImeLogger.onSetKeyboard(keyboard);
        requestLayout();
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
            mInvalidateAllKeys = true;
            if (mCanvas != null) {
                mCanvas.setBitmap(mBuffer);
            } else {
                mCanvas = new Canvas(mBuffer);
            }
        }

        if (mKeyboard == null) return;

        final Canvas canvas = mCanvas;
        final Paint paint = mPaint;
        final KeyDrawParams params = mKeyDrawParams;

        if (mInvalidateAllKeys || mInvalidatedKeys.isEmpty()) {
            mInvalidatedKeysRect.set(0, 0, width, height);
            canvas.clipRect(mInvalidatedKeysRect, Op.REPLACE);
            canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR);
            // Draw all keys.
            for (final Key key : mKeyboard.mKeys) {
                onDrawKey(key, canvas, paint, params);
            }
            if (mNeedsToDimEntireKeyboard) {
                drawDimRectangle(canvas, mInvalidatedKeysRect, mBackgroundDimAlpha, paint);
            }
        } else {
            // Draw invalidated keys.
            for (final Key key : mInvalidatedKeys) {
                if (!mKeyboard.hasKey(key)) {
                    continue;
                }
                final int x = key.mX + getPaddingLeft();
                final int y = key.mY + getPaddingTop();
                mInvalidatedKeysRect.set(x, y, x + key.mWidth, y + key.mHeight);
                canvas.clipRect(mInvalidatedKeysRect, Op.REPLACE);
                canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR);
                onDrawKey(key, canvas, paint, params);
                if (mNeedsToDimEntireKeyboard) {
                    drawDimRectangle(canvas, mInvalidatedKeysRect, mBackgroundDimAlpha, paint);
                }
            }
        }

        mInvalidatedKeys.clear();
        mInvalidatedKeysRect.setEmpty();
        mInvalidateAllKeys = false;
    }

    public void dimEntireKeyboard(boolean dimmed) {
        final boolean needsRedrawing = mNeedsToDimEntireKeyboard != dimmed;
        mNeedsToDimEntireKeyboard = dimmed;
        if (needsRedrawing) {
            invalidateAllKeys();
        }
    }

    private void onDrawKey(Key key, Canvas canvas, Paint paint, KeyDrawParams params) {
        final int keyDrawX = key.mX + key.mVisualInsetsLeft + getPaddingLeft();
        final int keyDrawY = key.mY + getPaddingTop();
        canvas.translate(keyDrawX, keyDrawY);

        params.mAnimAlpha = ALPHA_OPAQUE;
        if (!key.isSpacer()) {
            onDrawKeyBackground(key, canvas, params);
        }
        onDrawKeyTopVisuals(key, canvas, paint, params);

        canvas.translate(-keyDrawX, -keyDrawY);
    }

    // Draw key background.
    protected void onDrawKeyBackground(Key key, Canvas canvas, KeyDrawParams params) {
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
        if (LatinImeLogger.sVISUALDEBUG) {
            drawRectangle(canvas, 0, 0, bgWidth, bgHeight, 0x80c00000, new Paint());
        }
        canvas.translate(-bgX, -bgY);
    }

    // Draw key top visuals.
    protected void onDrawKeyTopVisuals(Key key, Canvas canvas, Paint paint, KeyDrawParams params) {
        final int keyWidth = key.mWidth - key.mVisualInsetsLeft - key.mVisualInsetsRight;
        final int keyHeight = key.mHeight;
        final float centerX = keyWidth * 0.5f;
        final float centerY = keyHeight * 0.5f;

        if (LatinImeLogger.sVISUALDEBUG) {
            drawRectangle(canvas, 0, 0, keyWidth, keyHeight, 0x800000c0, new Paint());
        }

        // Draw key label.
        final Drawable icon = key.getIcon(mKeyboard.mIconsSet, params.mAnimAlpha);
        float positionX = centerX;
        if (key.mLabel != null) {
            final String label = key.mLabel;
            // For characters, use large font. For labels like "Done", use smaller font.
            paint.setTypeface(key.selectTypeface(params.mKeyTextStyle));
            final int labelSize = key.selectTextSize(params.mKeyLetterSize,
                    params.mKeyLargeLetterSize, params.mKeyLabelSize, params.mKeyLargeLabelSize,
                    params.mKeyHintLabelSize);
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

            paint.setColor(key.isShiftedLetterActivated()
                    ? params.mKeyTextInactivatedColor : params.mKeyTextColor);
            if (key.isEnabled()) {
                // Set a drop shadow for the text
                paint.setShadowLayer(params.mShadowRadius, 0, 0, params.mShadowColor);
            } else {
                // Make label invisible
                paint.setColor(Color.TRANSPARENT);
            }
            params.blendAlpha(paint);
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

            if (LatinImeLogger.sVISUALDEBUG) {
                final Paint line = new Paint();
                drawHorizontalLine(canvas, baseline, keyWidth, 0xc0008000, line);
                drawVerticalLine(canvas, positionX, keyHeight, 0xc0800080, line);
            }
        }

        // Draw hint label.
        if (key.mHintLabel != null) {
            final String hint = key.mHintLabel;
            final int hintColor;
            final int hintSize;
            if (key.hasHintLabel()) {
                hintColor = params.mKeyHintLabelColor;
                hintSize = params.mKeyHintLabelSize;
                paint.setTypeface(Typeface.DEFAULT);
            } else if (key.hasShiftedLetterHint()) {
                hintColor = key.isShiftedLetterActivated()
                        ? params.mKeyShiftedLetterHintActivatedColor
                        : params.mKeyShiftedLetterHintInactivatedColor;
                hintSize = params.mKeyShiftedLetterHintSize;
            } else { // key.hasHintLetter()
                hintColor = params.mKeyHintLetterColor;
                hintSize = params.mKeyHintLetterSize;
            }
            paint.setColor(hintColor);
            params.blendAlpha(paint);
            paint.setTextSize(hintSize);
            final float hintX, hintY;
            if (key.hasHintLabel()) {
                // The hint label is placed just right of the key label. Used mainly on
                // "phone number" layout.
                // TODO: Generalize the following calculations.
                hintX = positionX + getCharWidth(KEY_LABEL_REFERENCE_CHAR, paint) * 2;
                hintY = centerY + getCharHeight(KEY_LABEL_REFERENCE_CHAR, paint) / 2;
                paint.setTextAlign(Align.LEFT);
            } else if (key.hasShiftedLetterHint()) {
                // The hint label is placed at top-right corner of the key. Used mainly on tablet.
                hintX = keyWidth - params.mKeyShiftedLetterHintPadding
                        - getCharWidth(KEY_LABEL_REFERENCE_CHAR, paint) / 2;
                paint.getFontMetrics(mFontMetrics);
                hintY = -mFontMetrics.top;
                paint.setTextAlign(Align.CENTER);
            } else { // key.hasHintLetter()
                // The hint letter is placed at top-right corner of the key. Used mainly on phone.
                hintX = keyWidth - params.mKeyHintLetterPadding
                        - getCharWidth(KEY_NUMERIC_HINT_LABEL_REFERENCE_CHAR, paint) / 2;
                hintY = -paint.ascent();
                paint.setTextAlign(Align.CENTER);
            }
            canvas.drawText(hint, 0, hint.length(), hintX, hintY, paint);

            if (LatinImeLogger.sVISUALDEBUG) {
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

            if (LatinImeLogger.sVISUALDEBUG) {
                final Paint line = new Paint();
                drawVerticalLine(canvas, alignX, keyHeight, 0xc0800080, line);
                drawRectangle(canvas, iconX, iconY, iconWidth, iconHeight, 0x80c00000, line);
            }
        }

        if (key.hasPopupHint() && key.mMoreKeys != null && key.mMoreKeys.length > 0) {
            drawKeyPopupHint(key, canvas, paint, params);
        }
    }

    // Draw popup hint "..." at the bottom right corner of the key.
    protected void drawKeyPopupHint(Key key, Canvas canvas, Paint paint, KeyDrawParams params) {
        final int keyWidth = key.mWidth - key.mVisualInsetsLeft - key.mVisualInsetsRight;
        final int keyHeight = key.mHeight;

        paint.setTypeface(params.mKeyTextStyle);
        paint.setTextSize(params.mKeyHintLetterSize);
        paint.setColor(params.mKeyHintLabelColor);
        paint.setTextAlign(Align.CENTER);
        final float hintX = keyWidth - params.mKeyHintLetterPadding
                - getCharWidth(KEY_LABEL_REFERENCE_CHAR, paint) / 2;
        final float hintY = keyHeight - params.mKeyPopupHintLetterPadding;
        canvas.drawText(POPUP_HINT_CHAR, hintX, hintY, paint);

        if (LatinImeLogger.sVISUALDEBUG) {
            final Paint line = new Paint();
            drawHorizontalLine(canvas, (int)hintY, keyWidth, 0xc0808000, line);
            drawVerticalLine(canvas, (int)hintX, keyHeight, 0xc0808000, line);
        }
    }

    private static int getCharGeometryCacheKey(char referenceChar, Paint paint) {
        final int labelSize = (int)paint.getTextSize();
        final Typeface face = paint.getTypeface();
        final int codePointOffset = referenceChar << 15;
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

    // Working variable for the following methods.
    private final Rect mTextBounds = new Rect();

    private float getCharHeight(char[] referenceChar, Paint paint) {
        final Integer key = getCharGeometryCacheKey(referenceChar[0], paint);
        final Float cachedValue = sTextHeightCache.get(key);
        if (cachedValue != null)
            return cachedValue;

        paint.getTextBounds(referenceChar, 0, 1, mTextBounds);
        final float height = mTextBounds.height();
        sTextHeightCache.put(key, height);
        return height;
    }

    private float getCharWidth(char[] referenceChar, Paint paint) {
        final Integer key = getCharGeometryCacheKey(referenceChar[0], paint);
        final Float cachedValue = sTextWidthCache.get(key);
        if (cachedValue != null)
            return cachedValue;

        paint.getTextBounds(referenceChar, 0, 1, mTextBounds);
        final float width = mTextBounds.width();
        sTextWidthCache.put(key, width);
        return width;
    }

    public float getLabelWidth(String label, Paint paint) {
        paint.getTextBounds(label.toString(), 0, label.length(), mTextBounds);
        return mTextBounds.width();
    }

    protected static void drawIcon(Canvas canvas, Drawable icon, int x, int y, int width,
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

    // Overlay a dark rectangle to dim.
    private static void drawDimRectangle(Canvas canvas, Rect rect, int alpha, Paint paint) {
        paint.setColor(Color.BLACK);
        paint.setAlpha(alpha);
        canvas.drawRect(rect, paint);
    }

    public Paint newDefaultLabelPaint() {
        final Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTypeface(mKeyDrawParams.mKeyTextStyle);
        paint.setTextSize(mKeyDrawParams.mKeyLabelSize);
        return paint;
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
    public void dismissKeyPreview(PointerTracker tracker) {
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
                keyPreview, ViewLayoutUtils.newLayoutParam(mPreviewPlacer, 0, 0));
    }

    @Override
    public void showKeyPreview(PointerTracker tracker) {
        if (!mShowKeyPreviewPopup) return;

        final TextView previewText = tracker.getKeyPreviewText();
        // If the key preview has no parent view yet, add it to the ViewGroup which can place
        // key preview absolutely in SoftInputWindow.
        if (previewText.getParent() == null) {
            addKeyPreview(previewText);
        }

        mDrawingHandler.cancelDismissKeyPreview(tracker);
        final Key key = tracker.getKey();
        // If key is invalid or IME is already closed, we must not show key preview.
        // Trying to show key preview while root window is closed causes
        // WindowManager.BadTokenException.
        if (key == null)
            return;

        final KeyPreviewDrawParams params = mKeyPreviewDrawParams;
        final String label = key.isShiftedLetterActivated() ? key.mHintLabel : key.mLabel;
        // What we show as preview should match what we show on a key top in onBufferDraw().
        if (label != null) {
            // TODO Should take care of temporaryShiftLabel here.
            previewText.setCompoundDrawables(null, null, null, null);
            if (StringUtils.codePointCount(label) > 1) {
                previewText.setTextSize(TypedValue.COMPLEX_UNIT_PX, params.mKeyLetterSize);
                previewText.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                previewText.setTextSize(TypedValue.COMPLEX_UNIT_PX, params.mPreviewTextSize);
                previewText.setTypeface(params.mKeyTextStyle);
            }
            previewText.setText(label);
        } else {
            previewText.setCompoundDrawables(null, null, null,
                    key.getPreviewIcon(mKeyboard.mIconsSet));
            previewText.setText(null);
        }
        previewText.setBackgroundDrawable(params.mPreviewBackground);

        previewText.measure(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        final int keyDrawWidth = key.mWidth - key.mVisualInsetsLeft - key.mVisualInsetsRight;
        final int previewWidth = previewText.getMeasuredWidth();
        final int previewHeight = params.mPreviewHeight;
        // The width and height of visible part of the key preview background. The content marker
        // of the background 9-patch have to cover the visible part of the background.
        params.mPreviewVisibleWidth = previewWidth - previewText.getPaddingLeft()
                - previewText.getPaddingRight();
        params.mPreviewVisibleHeight = previewHeight - previewText.getPaddingTop()
                - previewText.getPaddingBottom();
        // The distance between the top edge of the parent key and the bottom of the visible part
        // of the key preview background.
        params.mPreviewVisibleOffset = params.mPreviewOffset - previewText.getPaddingBottom();
        getLocationInWindow(params.mCoordinates);
        // The key preview is horizontally aligned with the center of the visible part of the
        // parent key. If it doesn't fit in this {@link KeyboardView}, it is moved inward to fit and
        // the left/right background is used if such background is specified.
        int previewX = key.mX + key.mVisualInsetsLeft - (previewWidth - keyDrawWidth) / 2
                + params.mCoordinates[0];
        if (previewX < 0) {
            previewX = 0;
            if (params.mPreviewLeftBackground != null) {
                previewText.setBackgroundDrawable(params.mPreviewLeftBackground);
            }
        } else if (previewX > getWidth() - previewWidth) {
            previewX = getWidth() - previewWidth;
            if (params.mPreviewRightBackground != null) {
                previewText.setBackgroundDrawable(params.mPreviewRightBackground);
            }
        }
        // The key preview is placed vertically above the top edge of the parent key with an
        // arbitrary offset.
        final int previewY = key.mY - previewHeight + params.mPreviewOffset
                + params.mCoordinates[1];

        // Set the preview background state
        previewText.getBackground().setState(
                key.mMoreKeys != null ? LONG_PRESSABLE_STATE_SET : EMPTY_STATE_SET);
        previewText.setTextColor(params.mPreviewTextColor);
        ViewLayoutUtils.placeViewAt(
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
        mInvalidatedKeys.clear();
        mInvalidateAllKeys = true;
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
        if (mInvalidateAllKeys) return;
        if (key == null) return;
        mInvalidatedKeys.add(key);
        final int x = key.mX + getPaddingLeft();
        final int y = key.mY + getPaddingTop();
        mInvalidatedKeysRect.union(x, y, x + key.mWidth, y + key.mHeight);
        mBufferNeedsUpdate = true;
        invalidate(mInvalidatedKeysRect);
    }

    public void closing() {
        PointerTracker.dismissAllKeyPreviews();
        cancelAllMessages();

        mInvalidateAllKeys = true;
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
