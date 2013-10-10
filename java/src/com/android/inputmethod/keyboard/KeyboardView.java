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
import android.graphics.Region;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import com.android.inputmethod.keyboard.internal.KeyDrawParams;
import com.android.inputmethod.keyboard.internal.KeyVisualAttributes;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.define.ProductionFlag;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.TypefaceUtils;
import com.android.inputmethod.research.ResearchLogger;

import java.util.HashSet;

/**
 * A view that renders a virtual {@link Keyboard}.
 *
 * @attr ref R.styleable#KeyboardView_keyBackground
 * @attr ref R.styleable#KeyboardView_keyLabelHorizontalPadding
 * @attr ref R.styleable#KeyboardView_keyHintLetterPadding
 * @attr ref R.styleable#KeyboardView_keyPopupHintLetterPadding
 * @attr ref R.styleable#KeyboardView_keyShiftedLetterHintPadding
 * @attr ref R.styleable#KeyboardView_keyTextShadowRadius
 * @attr ref R.styleable#KeyboardView_verticalCorrection
 * @attr ref R.styleable#Keyboard_Key_keyTypeface
 * @attr ref R.styleable#Keyboard_Key_keyLetterSize
 * @attr ref R.styleable#Keyboard_Key_keyLabelSize
 * @attr ref R.styleable#Keyboard_Key_keyLargeLetterRatio
 * @attr ref R.styleable#Keyboard_Key_keyLargeLabelRatio
 * @attr ref R.styleable#Keyboard_Key_keyHintLetterRatio
 * @attr ref R.styleable#Keyboard_Key_keyShiftedLetterHintRatio
 * @attr ref R.styleable#Keyboard_Key_keyHintLabelRatio
 * @attr ref R.styleable#Keyboard_Key_keyPreviewTextRatio
 * @attr ref R.styleable#Keyboard_Key_keyTextColor
 * @attr ref R.styleable#Keyboard_Key_keyTextColorDisabled
 * @attr ref R.styleable#Keyboard_Key_keyTextShadowColor
 * @attr ref R.styleable#Keyboard_Key_keyHintLetterColor
 * @attr ref R.styleable#Keyboard_Key_keyHintLabelColor
 * @attr ref R.styleable#Keyboard_Key_keyShiftedLetterHintInactivatedColor
 * @attr ref R.styleable#Keyboard_Key_keyShiftedLetterHintActivatedColor
 * @attr ref R.styleable#Keyboard_Key_keyPreviewTextColor
 */
public class KeyboardView extends View {
    // XML attributes
    private final KeyVisualAttributes mKeyVisualAttributes;
    private final int mKeyLabelHorizontalPadding;
    private final float mKeyHintLetterPadding;
    private final float mKeyPopupHintLetterPadding;
    private final float mKeyShiftedLetterHintPadding;
    private final float mKeyTextShadowRadius;
    private final float mVerticalCorrection;
    private final Drawable mKeyBackground;
    private final Rect mKeyBackgroundPadding = new Rect();

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
    protected final KeyDrawParams mKeyDrawParams = new KeyDrawParams();

    // Drawing
    /** True if all keys should be drawn */
    private boolean mInvalidateAllKeys;
    /** The keys that should be drawn */
    private final HashSet<Key> mInvalidatedKeys = CollectionUtils.newHashSet();
    /** The working rectangle variable */
    private final Rect mWorkingRect = new Rect();
    /** The keyboard bitmap buffer for faster updates */
    /** The clip region to draw keys */
    private final Region mClipRegion = new Region();
    private Bitmap mOffscreenBuffer;
    /** The canvas for the above mutable keyboard bitmap */
    private final Canvas mOffscreenCanvas = new Canvas();
    private final Paint mPaint = new Paint();
    private final Paint.FontMetrics mFontMetrics = new Paint.FontMetrics();
    private static final char[] KEY_LABEL_REFERENCE_CHAR = { 'M' };
    private static final char[] KEY_NUMERIC_HINT_LABEL_REFERENCE_CHAR = { '8' };

    public KeyboardView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.keyboardViewStyle);
    }

    public KeyboardView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        final TypedArray keyboardViewAttr = context.obtainStyledAttributes(attrs,
                R.styleable.KeyboardView, defStyle, R.style.KeyboardView);
        mKeyBackground = keyboardViewAttr.getDrawable(R.styleable.KeyboardView_keyBackground);
        mKeyBackground.getPadding(mKeyBackgroundPadding);
        mKeyLabelHorizontalPadding = keyboardViewAttr.getDimensionPixelOffset(
                R.styleable.KeyboardView_keyLabelHorizontalPadding, 0);
        mKeyHintLetterPadding = keyboardViewAttr.getDimension(
                R.styleable.KeyboardView_keyHintLetterPadding, 0.0f);
        mKeyPopupHintLetterPadding = keyboardViewAttr.getDimension(
                R.styleable.KeyboardView_keyPopupHintLetterPadding, 0.0f);
        mKeyShiftedLetterHintPadding = keyboardViewAttr.getDimension(
                R.styleable.KeyboardView_keyShiftedLetterHintPadding, 0.0f);
        mKeyTextShadowRadius = keyboardViewAttr.getFloat(
                R.styleable.KeyboardView_keyTextShadowRadius, 0.0f);
        mVerticalCorrection = keyboardViewAttr.getDimension(
                R.styleable.KeyboardView_verticalCorrection, 0.0f);
        keyboardViewAttr.recycle();

        final TypedArray keyAttr = context.obtainStyledAttributes(attrs,
                R.styleable.Keyboard_Key, defStyle, R.style.KeyboardView);
        mKeyVisualAttributes = KeyVisualAttributes.newInstance(keyAttr);
        keyAttr.recycle();

        mPaint.setAntiAlias(true);
    }

    private static void blendAlpha(final Paint paint, final int alpha) {
        final int color = paint.getColor();
        paint.setARGB((paint.getAlpha() * alpha) / Constants.Color.ALPHA_OPAQUE,
                Color.red(color), Color.green(color), Color.blue(color));
    }

    public void setHardwareAcceleratedDrawingEnabled(final boolean enabled) {
        if (!enabled) return;
        // TODO: Should use LAYER_TYPE_SOFTWARE when hardware acceleration is off?
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    /**
     * Attaches a keyboard to this view. The keyboard can be switched at any time and the
     * view will re-layout itself to accommodate the keyboard.
     * @see Keyboard
     * @see #getKeyboard()
     * @param keyboard the keyboard to display in this view
     */
    public void setKeyboard(final Keyboard keyboard) {
        mKeyboard = keyboard;
        LatinImeLogger.onSetKeyboard(keyboard);
        final int keyHeight = keyboard.mMostCommonKeyHeight - keyboard.mVerticalGap;
        mKeyDrawParams.updateParams(keyHeight, mKeyVisualAttributes);
        mKeyDrawParams.updateParams(keyHeight, keyboard.mKeyVisualAttributes);
        invalidateAllKeys();
        requestLayout();
    }

    /**
     * Returns the current keyboard being displayed by this view.
     * @return the currently attached keyboard
     * @see #setKeyboard(Keyboard)
     */
    public Keyboard getKeyboard() {
        return mKeyboard;
    }

    protected float getVerticalCorrection() {
        return mVerticalCorrection;
    }

    protected void updateKeyDrawParams(final int keyHeight) {
        mKeyDrawParams.updateParams(keyHeight, mKeyVisualAttributes);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        if (mKeyboard == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        // The main keyboard expands to the entire this {@link KeyboardView}.
        final int width = mKeyboard.mOccupiedWidth + getPaddingLeft() + getPaddingRight();
        final int height = mKeyboard.mOccupiedHeight + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);
        if (canvas.isHardwareAccelerated()) {
            onDrawKeyboard(canvas);
            return;
        }

        final boolean bufferNeedsUpdates = mInvalidateAllKeys || !mInvalidatedKeys.isEmpty();
        if (bufferNeedsUpdates || mOffscreenBuffer == null) {
            if (maybeAllocateOffscreenBuffer()) {
                mInvalidateAllKeys = true;
                // TODO: Stop using the offscreen canvas even when in software rendering
                mOffscreenCanvas.setBitmap(mOffscreenBuffer);
            }
            onDrawKeyboard(mOffscreenCanvas);
        }
        canvas.drawBitmap(mOffscreenBuffer, 0.0f, 0.0f, null);
    }

    private boolean maybeAllocateOffscreenBuffer() {
        final int width = getWidth();
        final int height = getHeight();
        if (width == 0 || height == 0) {
            return false;
        }
        if (mOffscreenBuffer != null && mOffscreenBuffer.getWidth() == width
                && mOffscreenBuffer.getHeight() == height) {
            return false;
        }
        freeOffscreenBuffer();
        mOffscreenBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        return true;
    }

    private void freeOffscreenBuffer() {
        mOffscreenCanvas.setBitmap(null);
        mOffscreenCanvas.setMatrix(null);
        if (mOffscreenBuffer != null) {
            mOffscreenBuffer.recycle();
            mOffscreenBuffer = null;
        }
    }

    private void onDrawKeyboard(final Canvas canvas) {
        if (mKeyboard == null) return;

        final int width = getWidth();
        final int height = getHeight();
        final Paint paint = mPaint;

        // Calculate clip region and set.
        final boolean drawAllKeys = mInvalidateAllKeys || mInvalidatedKeys.isEmpty();
        final boolean isHardwareAccelerated = canvas.isHardwareAccelerated();
        // TODO: Confirm if it's really required to draw all keys when hardware acceleration is on.
        if (drawAllKeys || isHardwareAccelerated) {
            mClipRegion.set(0, 0, width, height);
        } else {
            mClipRegion.setEmpty();
            for (final Key key : mInvalidatedKeys) {
                if (mKeyboard.hasKey(key)) {
                    final int x = key.getX() + getPaddingLeft();
                    final int y = key.getY() + getPaddingTop();
                    mWorkingRect.set(x, y, x + key.getWidth(), y + key.getHeight());
                    mClipRegion.union(mWorkingRect);
                }
            }
        }
        if (!isHardwareAccelerated) {
            canvas.clipRegion(mClipRegion, Region.Op.REPLACE);
            // Draw keyboard background.
            canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR);
            final Drawable background = getBackground();
            if (background != null) {
                background.draw(canvas);
            }
        }

        // TODO: Confirm if it's really required to draw all keys when hardware acceleration is on.
        if (drawAllKeys || isHardwareAccelerated) {
            // Draw all keys.
            for (final Key key : mKeyboard.getKeys()) {
                onDrawKey(key, canvas, paint);
            }
        } else {
            // Draw invalidated keys.
            for (final Key key : mInvalidatedKeys) {
                if (mKeyboard.hasKey(key)) {
                    onDrawKey(key, canvas, paint);
                }
            }
        }

        // Research Logging (Development Only Diagnostics) indicator.
        // TODO: Reimplement using a keyboard background image specific to the ResearchLogger,
        // and remove this call.
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.getInstance().paintIndicator(this, paint, canvas, width, height);
        }

        mInvalidatedKeys.clear();
        mInvalidateAllKeys = false;
    }

    private void onDrawKey(final Key key, final Canvas canvas, final Paint paint) {
        final int keyDrawX = key.getDrawX() + getPaddingLeft();
        final int keyDrawY = key.getY() + getPaddingTop();
        canvas.translate(keyDrawX, keyDrawY);

        final int keyHeight = mKeyboard.mMostCommonKeyHeight - mKeyboard.mVerticalGap;
        final KeyVisualAttributes attr = key.getVisualAttributes();
        final KeyDrawParams params = mKeyDrawParams.mayCloneAndUpdateParams(keyHeight, attr);
        params.mAnimAlpha = Constants.Color.ALPHA_OPAQUE;

        if (!key.isSpacer()) {
            onDrawKeyBackground(key, canvas);
        }
        onDrawKeyTopVisuals(key, canvas, paint, params);

        canvas.translate(-keyDrawX, -keyDrawY);
    }

    // Draw key background.
    protected void onDrawKeyBackground(final Key key, final Canvas canvas) {
        final Rect padding = mKeyBackgroundPadding;
        final int bgWidth = key.getDrawWidth() + padding.left + padding.right;
        final int bgHeight = key.getHeight() + padding.top + padding.bottom;
        final int bgX = -padding.left;
        final int bgY = -padding.top;
        final int[] drawableState = key.getCurrentDrawableState();
        final Drawable background = mKeyBackground;
        background.setState(drawableState);
        final Rect bounds = background.getBounds();
        if (bgWidth != bounds.right || bgHeight != bounds.bottom) {
            background.setBounds(0, 0, bgWidth, bgHeight);
        }
        canvas.translate(bgX, bgY);
        background.draw(canvas);
        if (LatinImeLogger.sVISUALDEBUG) {
            drawRectangle(canvas, 0.0f, 0.0f, bgWidth, bgHeight, 0x80c00000, new Paint());
        }
        canvas.translate(-bgX, -bgY);
    }

    // Draw key top visuals.
    protected void onDrawKeyTopVisuals(final Key key, final Canvas canvas, final Paint paint,
            final KeyDrawParams params) {
        final int keyWidth = key.getDrawWidth();
        final int keyHeight = key.getHeight();
        final float centerX = keyWidth * 0.5f;
        final float centerY = keyHeight * 0.5f;

        if (LatinImeLogger.sVISUALDEBUG) {
            drawRectangle(canvas, 0.0f, 0.0f, keyWidth, keyHeight, 0x800000c0, new Paint());
        }

        // Draw key label.
        final Drawable icon = key.getIcon(mKeyboard.mIconsSet, params.mAnimAlpha);
        float positionX = centerX;
        final String label = key.getLabel();
        if (label != null) {
            paint.setTypeface(key.selectTypeface(params));
            paint.setTextSize(key.selectTextSize(params));
            final float labelCharHeight = TypefaceUtils.getCharHeight(
                    KEY_LABEL_REFERENCE_CHAR, paint);
            final float labelCharWidth = TypefaceUtils.getCharWidth(
                    KEY_LABEL_REFERENCE_CHAR, paint);

            // Vertical label text alignment.
            final float baseline = centerY + labelCharHeight / 2.0f;

            // Horizontal label text alignment
            float labelWidth = 0.0f;
            if (key.isAlignLeft()) {
                positionX = mKeyLabelHorizontalPadding;
                paint.setTextAlign(Align.LEFT);
            } else if (key.isAlignRight()) {
                positionX = keyWidth - mKeyLabelHorizontalPadding;
                paint.setTextAlign(Align.RIGHT);
            } else if (key.isAlignLeftOfCenter()) {
                // TODO: Parameterise this?
                positionX = centerX - labelCharWidth * 7.0f / 4.0f;
                paint.setTextAlign(Align.LEFT);
            } else if (key.hasLabelWithIconLeft() && icon != null) {
                labelWidth = TypefaceUtils.getLabelWidth(label, paint) + icon.getIntrinsicWidth()
                        + LABEL_ICON_MARGIN * keyWidth;
                positionX = centerX + labelWidth / 2.0f;
                paint.setTextAlign(Align.RIGHT);
            } else if (key.hasLabelWithIconRight() && icon != null) {
                labelWidth = TypefaceUtils.getLabelWidth(label, paint) + icon.getIntrinsicWidth()
                        + LABEL_ICON_MARGIN * keyWidth;
                positionX = centerX - labelWidth / 2.0f;
                paint.setTextAlign(Align.LEFT);
            } else {
                positionX = centerX;
                paint.setTextAlign(Align.CENTER);
            }
            if (key.needsXScale()) {
                paint.setTextScaleX(Math.min(1.0f,
                        (keyWidth * MAX_LABEL_RATIO) / TypefaceUtils.getLabelWidth(label, paint)));
            }

            paint.setColor(key.selectTextColor(params));
            if (key.isEnabled()) {
                // Set a drop shadow for the text
                paint.setShadowLayer(mKeyTextShadowRadius, 0.0f, 0.0f, params.mTextShadowColor);
            } else {
                // Make label invisible
                paint.setColor(Color.TRANSPARENT);
            }
            blendAlpha(paint, params.mAnimAlpha);
            canvas.drawText(label, 0, label.length(), positionX, baseline, paint);
            // Turn off drop shadow and reset x-scale.
            paint.setShadowLayer(0.0f, 0.0f, 0.0f, Color.TRANSPARENT);
            paint.setTextScaleX(1.0f);

            if (icon != null) {
                final int iconWidth = icon.getIntrinsicWidth();
                final int iconHeight = icon.getIntrinsicHeight();
                final int iconY = (keyHeight - iconHeight) / 2;
                if (key.hasLabelWithIconLeft()) {
                    final int iconX = (int)(centerX - labelWidth / 2.0f);
                    drawIcon(canvas, icon, iconX, iconY, iconWidth, iconHeight);
                } else if (key.hasLabelWithIconRight()) {
                    final int iconX = (int)(centerX + labelWidth / 2.0f - iconWidth);
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
        final String hintLabel = key.getHintLabel();
        if (hintLabel != null) {
            paint.setTextSize(key.selectHintTextSize(params));
            paint.setColor(key.selectHintTextColor(params));
            // TODO: Should add a way to specify type face for hint letters
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            blendAlpha(paint, params.mAnimAlpha);
            final float hintX, hintY;
            if (key.hasHintLabel()) {
                // The hint label is placed just right of the key label. Used mainly on
                // "phone number" layout.
                // TODO: Generalize the following calculations.
                hintX = positionX
                        + TypefaceUtils.getCharWidth(KEY_LABEL_REFERENCE_CHAR, paint) * 2.0f;
                hintY = centerY
                        + TypefaceUtils.getCharHeight(KEY_LABEL_REFERENCE_CHAR, paint) / 2.0f;
                paint.setTextAlign(Align.LEFT);
            } else if (key.hasShiftedLetterHint()) {
                // The hint label is placed at top-right corner of the key. Used mainly on tablet.
                hintX = keyWidth - mKeyShiftedLetterHintPadding
                        - TypefaceUtils.getCharWidth(KEY_LABEL_REFERENCE_CHAR, paint) / 2.0f;
                paint.getFontMetrics(mFontMetrics);
                hintY = -mFontMetrics.top;
                paint.setTextAlign(Align.CENTER);
            } else { // key.hasHintLetter()
                // The hint letter is placed at top-right corner of the key. Used mainly on phone.
                final float keyNumericHintLabelReferenceCharWidth =
                        TypefaceUtils.getCharWidth(KEY_NUMERIC_HINT_LABEL_REFERENCE_CHAR, paint);
                final float keyHintLabelStringWidth =
                        TypefaceUtils.getStringWidth(hintLabel, paint);
                hintX = keyWidth - mKeyHintLetterPadding
                        - Math.max(keyNumericHintLabelReferenceCharWidth, keyHintLabelStringWidth)
                                / 2.0f;
                hintY = -paint.ascent();
                paint.setTextAlign(Align.CENTER);
            }
            canvas.drawText(hintLabel, 0, hintLabel.length(), hintX, hintY, paint);

            if (LatinImeLogger.sVISUALDEBUG) {
                final Paint line = new Paint();
                drawHorizontalLine(canvas, (int)hintY, keyWidth, 0xc0808000, line);
                drawVerticalLine(canvas, (int)hintX, keyHeight, 0xc0808000, line);
            }
        }

        // Draw key icon.
        if (label == null && icon != null) {
            final int iconWidth = Math.min(icon.getIntrinsicWidth(), keyWidth);
            final int iconHeight = icon.getIntrinsicHeight();
            final int iconX, alignX;
            final int iconY = (keyHeight - iconHeight) / 2;
            if (key.isAlignLeft()) {
                iconX = mKeyLabelHorizontalPadding;
                alignX = iconX;
            } else if (key.isAlignRight()) {
                iconX = keyWidth - mKeyLabelHorizontalPadding - iconWidth;
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

        if (key.hasPopupHint() && key.getMoreKeys() != null) {
            drawKeyPopupHint(key, canvas, paint, params);
        }
    }

    // Draw popup hint "..." at the bottom right corner of the key.
    protected void drawKeyPopupHint(final Key key, final Canvas canvas, final Paint paint,
            final KeyDrawParams params) {
        final int keyWidth = key.getDrawWidth();
        final int keyHeight = key.getHeight();

        paint.setTypeface(params.mTypeface);
        paint.setTextSize(params.mHintLetterSize);
        paint.setColor(params.mHintLabelColor);
        paint.setTextAlign(Align.CENTER);
        final float hintX = keyWidth - mKeyHintLetterPadding
                - TypefaceUtils.getCharWidth(KEY_LABEL_REFERENCE_CHAR, paint) / 2.0f;
        final float hintY = keyHeight - mKeyPopupHintLetterPadding;
        canvas.drawText(POPUP_HINT_CHAR, hintX, hintY, paint);

        if (LatinImeLogger.sVISUALDEBUG) {
            final Paint line = new Paint();
            drawHorizontalLine(canvas, (int)hintY, keyWidth, 0xc0808000, line);
            drawVerticalLine(canvas, (int)hintX, keyHeight, 0xc0808000, line);
        }
    }

    protected static void drawIcon(final Canvas canvas, final Drawable icon, final int x,
            final int y, final int width, final int height) {
        canvas.translate(x, y);
        icon.setBounds(0, 0, width, height);
        icon.draw(canvas);
        canvas.translate(-x, -y);
    }

    private static void drawHorizontalLine(final Canvas canvas, final float y, final float w,
            final int color, final Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.0f);
        paint.setColor(color);
        canvas.drawLine(0.0f, y, w, y, paint);
    }

    private static void drawVerticalLine(final Canvas canvas, final float x, final float h,
            final int color, final Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.0f);
        paint.setColor(color);
        canvas.drawLine(x, 0.0f, x, h, paint);
    }

    private static void drawRectangle(final Canvas canvas, final float x, final float y,
            final float w, final float h, final int color, final Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.0f);
        paint.setColor(color);
        canvas.translate(x, y);
        canvas.drawRect(0.0f, 0.0f, w, h, paint);
        canvas.translate(-x, -y);
    }

    public Paint newLabelPaint(final Key key) {
        final Paint paint = new Paint();
        paint.setAntiAlias(true);
        if (key == null) {
            paint.setTypeface(mKeyDrawParams.mTypeface);
            paint.setTextSize(mKeyDrawParams.mLabelSize);
        } else {
            paint.setTypeface(key.selectTypeface(mKeyDrawParams));
            paint.setTextSize(key.selectTextSize(mKeyDrawParams));
        }
        return paint;
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
        invalidate();
    }

    /**
     * Invalidates a key so that it will be redrawn on the next repaint. Use this method if only
     * one key is changing it's content. Any changes that affect the position or size of the key
     * may not be honored.
     * @param key key in the attached {@link Keyboard}.
     * @see #invalidateAllKeys
     */
    public void invalidateKey(final Key key) {
        if (mInvalidateAllKeys) return;
        if (key == null) return;
        mInvalidatedKeys.add(key);
        final int x = key.getX() + getPaddingLeft();
        final int y = key.getY() + getPaddingTop();
        invalidate(x, y, x + key.getWidth(), y + key.getHeight());
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        freeOffscreenBuffer();
    }

    public void deallocateMemory() {
        freeOffscreenBuffer();
    }
}
