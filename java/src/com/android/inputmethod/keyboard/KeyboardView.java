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
import android.os.Message;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.inputmethod.keyboard.internal.KeyDrawParams;
import com.android.inputmethod.keyboard.internal.KeyPreviewDrawParams;
import com.android.inputmethod.keyboard.internal.KeyVisualAttributes;
import com.android.inputmethod.keyboard.internal.PreviewPlacerView;
import com.android.inputmethod.latin.CollectionUtils;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.StaticInnerHandlerWrapper;
import com.android.inputmethod.latin.StringUtils;
import com.android.inputmethod.latin.define.ProductionFlag;
import com.android.inputmethod.research.ResearchLogger;

import java.util.HashSet;

/**
 * A view that renders a virtual {@link Keyboard}.
 *
 * @attr ref R.styleable#KeyboardView_keyBackground
 * @attr ref R.styleable#KeyboardView_moreKeysLayout
 * @attr ref R.styleable#KeyboardView_keyPreviewLayout
 * @attr ref R.styleable#KeyboardView_keyPreviewOffset
 * @attr ref R.styleable#KeyboardView_keyPreviewHeight
 * @attr ref R.styleable#KeyboardView_keyPreviewLingerTimeout
 * @attr ref R.styleable#KeyboardView_keyLabelHorizontalPadding
 * @attr ref R.styleable#KeyboardView_keyHintLetterPadding
 * @attr ref R.styleable#KeyboardView_keyPopupHintLetterPadding
 * @attr ref R.styleable#KeyboardView_keyShiftedLetterHintPadding
 * @attr ref R.styleable#KeyboardView_keyTextShadowRadius
 * @attr ref R.styleable#KeyboardView_backgroundDimAlpha
 * @attr ref R.styleable#KeyboardView_gestureFloatingPreviewTextSize
 * @attr ref R.styleable#KeyboardView_gestureFloatingPreviewTextColor
 * @attr ref R.styleable#KeyboardView_gestureFloatingPreviewTextOffset
 * @attr ref R.styleable#KeyboardView_gestureFloatingPreviewColor
 * @attr ref R.styleable#KeyboardView_gestureFloatingPreviewHorizontalPadding
 * @attr ref R.styleable#KeyboardView_gestureFloatingPreviewVerticalPadding
 * @attr ref R.styleable#KeyboardView_gestureFloatingPreviewRoundRadius
 * @attr ref R.styleable#KeyboardView_gestureFloatingPreviewTextLingerTimeout
 * @attr ref R.styleable#KeyboardView_gesturePreviewTrailFadeoutStartDelay
 * @attr ref R.styleable#KeyboardView_gesturePreviewTrailFadeoutDuration
 * @attr ref R.styleable#KeyboardView_gesturePreviewTrailUpdateInterval
 * @attr ref R.styleable#KeyboardView_gesturePreviewTrailColor
 * @attr ref R.styleable#KeyboardView_gesturePreviewTrailWidth
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
public class KeyboardView extends View implements PointerTracker.DrawingProxy {
    private static final String TAG = KeyboardView.class.getSimpleName();

    // XML attributes
    protected final KeyVisualAttributes mKeyVisualAttributes;
    private final int mKeyLabelHorizontalPadding;
    private final float mKeyHintLetterPadding;
    private final float mKeyPopupHintLetterPadding;
    private final float mKeyShiftedLetterHintPadding;
    private final float mKeyTextShadowRadius;
    protected final float mVerticalCorrection;
    protected final int mMoreKeysLayout;
    protected final Drawable mKeyBackground;
    protected final Rect mKeyBackgroundPadding = new Rect();
    private final int mBackgroundDimAlpha;

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

    // Preview placer view
    private final PreviewPlacerView mPreviewPlacerView;
    private final int[] mCoordinates = new int[2];

    // Key preview
    private static final int PREVIEW_ALPHA = 240;
    private final int mKeyPreviewLayoutId;
    private final int mPreviewOffset;
    private final int mPreviewHeight;
    private final int mPreviewLingerTimeout;
    private final SparseArray<TextView> mKeyPreviewTexts = CollectionUtils.newSparseArray();
    protected final KeyPreviewDrawParams mKeyPreviewDrawParams = new KeyPreviewDrawParams();
    private boolean mShowKeyPreviewPopup = true;
    private int mDelayAfterPreview;
    // Background state set
    private static final int[][][] KEY_PREVIEW_BACKGROUND_STATE_TABLE = {
        { // STATE_MIDDLE
            EMPTY_STATE_SET,
            { R.attr.state_has_morekeys }
        },
        { // STATE_LEFT
            { R.attr.state_left_edge },
            { R.attr.state_left_edge, R.attr.state_has_morekeys }
        },
        { // STATE_RIGHT
            { R.attr.state_right_edge },
            { R.attr.state_right_edge, R.attr.state_has_morekeys }
        }
    };
    private static final int STATE_MIDDLE = 0;
    private static final int STATE_LEFT = 1;
    private static final int STATE_RIGHT = 2;
    private static final int STATE_NORMAL = 0;
    private static final int STATE_HAS_MOREKEYS = 1;
    private static final int[] KEY_PREVIEW_BACKGROUND_DEFAULT_STATE =
            KEY_PREVIEW_BACKGROUND_STATE_TABLE[STATE_MIDDLE][STATE_NORMAL];

    // Drawing
    /** True if the entire keyboard needs to be dimmed. */
    private boolean mNeedsToDimEntireKeyboard;
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
    // This sparse array caches key label text height in pixel indexed by key label text size.
    private static final SparseArray<Float> sTextHeightCache = CollectionUtils.newSparseArray();
    // This sparse array caches key label text width in pixel indexed by key label text size.
    private static final SparseArray<Float> sTextWidthCache = CollectionUtils.newSparseArray();
    private static final char[] KEY_LABEL_REFERENCE_CHAR = { 'M' };
    private static final char[] KEY_NUMERIC_HINT_LABEL_REFERENCE_CHAR = { '8' };

    private final DrawingHandler mDrawingHandler = new DrawingHandler(this);

    public static class DrawingHandler extends StaticInnerHandlerWrapper<KeyboardView> {
        private static final int MSG_DISMISS_KEY_PREVIEW = 0;

        public DrawingHandler(final KeyboardView outerInstance) {
            super(outerInstance);
        }

        @Override
        public void handleMessage(final Message msg) {
            final KeyboardView keyboardView = getOuterInstance();
            if (keyboardView == null) return;
            final PointerTracker tracker = (PointerTracker) msg.obj;
            switch (msg.what) {
            case MSG_DISMISS_KEY_PREVIEW:
                final TextView previewText = keyboardView.mKeyPreviewTexts.get(tracker.mPointerId);
                if (previewText != null) {
                    previewText.setVisibility(INVISIBLE);
                }
                break;
            }
        }

        public void dismissKeyPreview(final long delay, final PointerTracker tracker) {
            sendMessageDelayed(obtainMessage(MSG_DISMISS_KEY_PREVIEW, tracker), delay);
        }

        public void cancelDismissKeyPreview(final PointerTracker tracker) {
            removeMessages(MSG_DISMISS_KEY_PREVIEW, tracker);
        }

        private void cancelAllDismissKeyPreviews() {
            removeMessages(MSG_DISMISS_KEY_PREVIEW);
        }

        public void cancelAllMessages() {
            cancelAllDismissKeyPreviews();
        }
    }

    public KeyboardView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.keyboardViewStyle);
    }

    public KeyboardView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        final TypedArray keyboardViewAttr = context.obtainStyledAttributes(attrs,
                R.styleable.KeyboardView, defStyle, R.style.KeyboardView);
        mKeyBackground = keyboardViewAttr.getDrawable(R.styleable.KeyboardView_keyBackground);
        mKeyBackground.getPadding(mKeyBackgroundPadding);
        mPreviewOffset = keyboardViewAttr.getDimensionPixelOffset(
                R.styleable.KeyboardView_keyPreviewOffset, 0);
        mPreviewHeight = keyboardViewAttr.getDimensionPixelSize(
                R.styleable.KeyboardView_keyPreviewHeight, 80);
        mPreviewLingerTimeout = keyboardViewAttr.getInt(
                R.styleable.KeyboardView_keyPreviewLingerTimeout, 0);
        mDelayAfterPreview = mPreviewLingerTimeout;
        mKeyLabelHorizontalPadding = keyboardViewAttr.getDimensionPixelOffset(
                R.styleable.KeyboardView_keyLabelHorizontalPadding, 0);
        mKeyHintLetterPadding = keyboardViewAttr.getDimension(
                R.styleable.KeyboardView_keyHintLetterPadding, 0);
        mKeyPopupHintLetterPadding = keyboardViewAttr.getDimension(
                R.styleable.KeyboardView_keyPopupHintLetterPadding, 0);
        mKeyShiftedLetterHintPadding = keyboardViewAttr.getDimension(
                R.styleable.KeyboardView_keyShiftedLetterHintPadding, 0);
        mKeyTextShadowRadius = keyboardViewAttr.getFloat(
                R.styleable.KeyboardView_keyTextShadowRadius, 0.0f);
        mKeyPreviewLayoutId = keyboardViewAttr.getResourceId(
                R.styleable.KeyboardView_keyPreviewLayout, 0);
        if (mKeyPreviewLayoutId == 0) {
            mShowKeyPreviewPopup = false;
        }
        mVerticalCorrection = keyboardViewAttr.getDimension(
                R.styleable.KeyboardView_verticalCorrection, 0);
        mMoreKeysLayout = keyboardViewAttr.getResourceId(
                R.styleable.KeyboardView_moreKeysLayout, 0);
        mBackgroundDimAlpha = keyboardViewAttr.getInt(
                R.styleable.KeyboardView_backgroundDimAlpha, 0);
        keyboardViewAttr.recycle();

        final TypedArray keyAttr = context.obtainStyledAttributes(attrs,
                R.styleable.Keyboard_Key, defStyle, R.style.KeyboardView);
        mKeyVisualAttributes = KeyVisualAttributes.newInstance(keyAttr);
        keyAttr.recycle();

        mPreviewPlacerView = new PreviewPlacerView(context, attrs);
        mPaint.setAntiAlias(true);
    }

    private static void blendAlpha(final Paint paint, final int alpha) {
        final int color = paint.getColor();
        paint.setARGB((paint.getAlpha() * alpha) / Constants.Color.ALPHA_OPAQUE,
                Color.red(color), Color.green(color), Color.blue(color));
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
        requestLayout();
        invalidateAllKeys();
        final int keyHeight = keyboard.mMostCommonKeyHeight - keyboard.mVerticalGap;
        mKeyDrawParams.updateParams(keyHeight, mKeyVisualAttributes);
        mKeyDrawParams.updateParams(keyHeight, keyboard.mKeyVisualAttributes);
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
    public void setKeyPreviewPopupEnabled(final boolean previewEnabled, final int delay) {
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

    public void setGesturePreviewMode(final boolean drawsGesturePreviewTrail,
            final boolean drawsGestureFloatingPreviewText) {
        mPreviewPlacerView.setGesturePreviewMode(
                drawsGesturePreviewTrail, drawsGestureFloatingPreviewText);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        if (mKeyboard != null) {
            // The main keyboard expands to the display width.
            final int height = mKeyboard.mOccupiedHeight + getPaddingTop() + getPaddingBottom();
            setMeasuredDimension(widthMeasureSpec, height);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    public void onDraw(final Canvas canvas) {
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
        canvas.drawBitmap(mOffscreenBuffer, 0, 0, null);
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
                    final int x = key.mX + getPaddingLeft();
                    final int y = key.mY + getPaddingTop();
                    mWorkingRect.set(x, y, x + key.mWidth, y + key.mHeight);
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
            for (final Key key : mKeyboard.mKeys) {
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

        // Overlay a dark rectangle to dim.
        if (mNeedsToDimEntireKeyboard) {
            paint.setColor(Color.BLACK);
            paint.setAlpha(mBackgroundDimAlpha);
            // Note: clipRegion() above is in effect if it was called.
            canvas.drawRect(0, 0, width, height, paint);
        }

        // ResearchLogging indicator.
        // TODO: Reimplement using a keyboard background image specific to the ResearchLogger,
        // and remove this call.
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.getInstance().paintIndicator(this, paint, canvas, width, height);
        }

        mInvalidatedKeys.clear();
        mInvalidateAllKeys = false;
    }

    public void dimEntireKeyboard(final boolean dimmed) {
        final boolean needsRedrawing = mNeedsToDimEntireKeyboard != dimmed;
        mNeedsToDimEntireKeyboard = dimmed;
        if (needsRedrawing) {
            invalidateAllKeys();
        }
    }

    private void onDrawKey(final Key key, final Canvas canvas, final Paint paint) {
        final int keyDrawX = key.getDrawX() + getPaddingLeft();
        final int keyDrawY = key.mY + getPaddingTop();
        canvas.translate(keyDrawX, keyDrawY);

        final int keyHeight = mKeyboard.mMostCommonKeyHeight - mKeyboard.mVerticalGap;
        final KeyVisualAttributes attr = key.mKeyVisualAttributes;
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
        final int bgHeight = key.mHeight + padding.top + padding.bottom;
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
            drawRectangle(canvas, 0, 0, bgWidth, bgHeight, 0x80c00000, new Paint());
        }
        canvas.translate(-bgX, -bgY);
    }

    // Draw key top visuals.
    protected void onDrawKeyTopVisuals(final Key key, final Canvas canvas, final Paint paint,
            final KeyDrawParams params) {
        final int keyWidth = key.getDrawWidth();
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
            paint.setTypeface(key.selectTypeface(params));
            paint.setTextSize(key.selectTextSize(params));
            final float labelCharHeight = getCharHeight(KEY_LABEL_REFERENCE_CHAR, paint);
            final float labelCharWidth = getCharWidth(KEY_LABEL_REFERENCE_CHAR, paint);

            // Vertical label text alignment.
            final float baseline = centerY + labelCharHeight / 2;

            // Horizontal label text alignment
            float labelWidth = 0;
            if (key.isAlignLeft()) {
                positionX = mKeyLabelHorizontalPadding;
                paint.setTextAlign(Align.LEFT);
            } else if (key.isAlignRight()) {
                positionX = keyWidth - mKeyLabelHorizontalPadding;
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

            paint.setColor(key.selectTextColor(params));
            if (key.isEnabled()) {
                // Set a drop shadow for the text
                paint.setShadowLayer(mKeyTextShadowRadius, 0, 0, params.mTextShadowColor);
            } else {
                // Make label invisible
                paint.setColor(Color.TRANSPARENT);
            }
            blendAlpha(paint, params.mAnimAlpha);
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
            final String hintLabel = key.mHintLabel;
            paint.setTextSize(key.selectHintTextSize(params));
            paint.setColor(key.selectHintTextColor(params));
            blendAlpha(paint, params.mAnimAlpha);
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
                hintX = keyWidth - mKeyShiftedLetterHintPadding
                        - getCharWidth(KEY_LABEL_REFERENCE_CHAR, paint) / 2;
                paint.getFontMetrics(mFontMetrics);
                hintY = -mFontMetrics.top;
                paint.setTextAlign(Align.CENTER);
            } else { // key.hasHintLetter()
                // The hint letter is placed at top-right corner of the key. Used mainly on phone.
                hintX = keyWidth - mKeyHintLetterPadding
                        - getCharWidth(KEY_NUMERIC_HINT_LABEL_REFERENCE_CHAR, paint) / 2;
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
        if (key.mLabel == null && icon != null) {
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

        if (key.hasPopupHint() && key.mMoreKeys != null && key.mMoreKeys.length > 0) {
            drawKeyPopupHint(key, canvas, paint, params);
        }
    }

    // Draw popup hint "..." at the bottom right corner of the key.
    protected void drawKeyPopupHint(final Key key, final Canvas canvas, final Paint paint,
            final KeyDrawParams params) {
        final int keyWidth = key.getDrawWidth();
        final int keyHeight = key.mHeight;

        paint.setTypeface(params.mTypeface);
        paint.setTextSize(params.mHintLetterSize);
        paint.setColor(params.mHintLabelColor);
        paint.setTextAlign(Align.CENTER);
        final float hintX = keyWidth - mKeyHintLetterPadding
                - getCharWidth(KEY_LABEL_REFERENCE_CHAR, paint) / 2;
        final float hintY = keyHeight - mKeyPopupHintLetterPadding;
        canvas.drawText(POPUP_HINT_CHAR, hintX, hintY, paint);

        if (LatinImeLogger.sVISUALDEBUG) {
            final Paint line = new Paint();
            drawHorizontalLine(canvas, (int)hintY, keyWidth, 0xc0808000, line);
            drawVerticalLine(canvas, (int)hintX, keyHeight, 0xc0808000, line);
        }
    }

    private static int getCharGeometryCacheKey(final char referenceChar, final Paint paint) {
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

    private float getCharHeight(final char[] referenceChar, final Paint paint) {
        final int key = getCharGeometryCacheKey(referenceChar[0], paint);
        final Float cachedValue = sTextHeightCache.get(key);
        if (cachedValue != null)
            return cachedValue;

        paint.getTextBounds(referenceChar, 0, 1, mTextBounds);
        final float height = mTextBounds.height();
        sTextHeightCache.put(key, height);
        return height;
    }

    private float getCharWidth(final char[] referenceChar, final Paint paint) {
        final int key = getCharGeometryCacheKey(referenceChar[0], paint);
        final Float cachedValue = sTextWidthCache.get(key);
        if (cachedValue != null)
            return cachedValue;

        paint.getTextBounds(referenceChar, 0, 1, mTextBounds);
        final float width = mTextBounds.width();
        sTextWidthCache.put(key, width);
        return width;
    }

    // TODO: Remove this method.
    public float getLabelWidth(final String label, final Paint paint) {
        paint.getTextBounds(label, 0, label.length(), mTextBounds);
        return mTextBounds.width();
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
        canvas.drawLine(0, y, w, y, paint);
    }

    private static void drawVerticalLine(final Canvas canvas, final float x, final float h,
            final int color, final Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.0f);
        paint.setColor(color);
        canvas.drawLine(x, 0, x, h, paint);
    }

    private static void drawRectangle(final Canvas canvas, final float x, final float y,
            final float w, final float h, final int color, final Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.0f);
        paint.setColor(color);
        canvas.translate(x, y);
        canvas.drawRect(0, 0, w, h, paint);
        canvas.translate(-x, -y);
    }

    public Paint newDefaultLabelPaint() {
        final Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTypeface(mKeyDrawParams.mTypeface);
        paint.setTextSize(mKeyDrawParams.mLabelSize);
        return paint;
    }

    public void cancelAllMessages() {
        mDrawingHandler.cancelAllMessages();
    }

    private TextView getKeyPreviewText(final int pointerId) {
        TextView previewText = mKeyPreviewTexts.get(pointerId);
        if (previewText != null) {
            return previewText;
        }
        final Context context = getContext();
        if (mKeyPreviewLayoutId != 0) {
            previewText = (TextView)LayoutInflater.from(context).inflate(mKeyPreviewLayoutId, null);
        } else {
            previewText = new TextView(context);
        }
        mKeyPreviewTexts.put(pointerId, previewText);
        return previewText;
    }

    private void dismissAllKeyPreviews() {
        final int pointerCount = mKeyPreviewTexts.size();
        for (int id = 0; id < pointerCount; id++) {
            final TextView previewText = mKeyPreviewTexts.get(id);
            if (previewText != null) {
                previewText.setVisibility(INVISIBLE);
            }
        }
        PointerTracker.setReleasedKeyGraphicsToAllKeys();
    }

    @Override
    public void dismissKeyPreview(final PointerTracker tracker) {
        mDrawingHandler.dismissKeyPreview(mDelayAfterPreview, tracker);
    }

    private void addKeyPreview(final TextView keyPreview) {
        locatePreviewPlacerView();
        mPreviewPlacerView.addView(
                keyPreview, ViewLayoutUtils.newLayoutParam(mPreviewPlacerView, 0, 0));
    }

    private void locatePreviewPlacerView() {
        if (mPreviewPlacerView.getParent() != null) {
            return;
        }
        final int width = getWidth();
        final int height = getHeight();
        if (width == 0 || height == 0) {
            // In transient state.
            return;
        }
        final int[] viewOrigin = new int[2];
        getLocationInWindow(viewOrigin);
        final DisplayMetrics dm = getResources().getDisplayMetrics();
        if (viewOrigin[1] < dm.heightPixels / 4) {
            // In transient state.
            return;
        }
        final View rootView = getRootView();
        if (rootView == null) {
            Log.w(TAG, "Cannot find root view");
            return;
        }
        final ViewGroup windowContentView = (ViewGroup)rootView.findViewById(android.R.id.content);
        // Note: It'd be very weird if we get null by android.R.id.content.
        if (windowContentView == null) {
            Log.w(TAG, "Cannot find android.R.id.content view to add PreviewPlacerView");
        } else {
            windowContentView.addView(mPreviewPlacerView);
            mPreviewPlacerView.setKeyboardViewGeometry(viewOrigin[0], viewOrigin[1], width, height);
        }
    }

    public void showGestureFloatingPreviewText(final String gestureFloatingPreviewText) {
        locatePreviewPlacerView();
        mPreviewPlacerView.setGestureFloatingPreviewText(gestureFloatingPreviewText);
    }

    public void dismissGestureFloatingPreviewText() {
        locatePreviewPlacerView();
        mPreviewPlacerView.dismissGestureFloatingPreviewText();
    }

    @Override
    public void showGesturePreviewTrail(final PointerTracker tracker,
            final boolean isOldestTracker) {
        locatePreviewPlacerView();
        mPreviewPlacerView.invalidatePointer(tracker, isOldestTracker);
    }

    @Override
    public void showKeyPreview(final PointerTracker tracker) {
        final KeyPreviewDrawParams previewParams = mKeyPreviewDrawParams;
        if (!mShowKeyPreviewPopup) {
            previewParams.mPreviewVisibleOffset = -mKeyboard.mVerticalGap;
            return;
        }

        final TextView previewText = getKeyPreviewText(tracker.mPointerId);
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
        if (key == null) {
            return;
        }

        final KeyDrawParams drawParams = mKeyDrawParams;
        previewText.setTextColor(drawParams.mPreviewTextColor);
        final Drawable background = previewText.getBackground();
        if (background != null) {
            background.setState(KEY_PREVIEW_BACKGROUND_DEFAULT_STATE);
            background.setAlpha(PREVIEW_ALPHA);
        }
        final String label = key.isShiftedLetterActivated() ? key.mHintLabel : key.mLabel;
        // What we show as preview should match what we show on a key top in onDraw().
        if (label != null) {
            // TODO Should take care of temporaryShiftLabel here.
            previewText.setCompoundDrawables(null, null, null, null);
            if (StringUtils.codePointCount(label) > 1) {
                previewText.setTextSize(TypedValue.COMPLEX_UNIT_PX, drawParams.mLetterSize);
                previewText.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                previewText.setTextSize(TypedValue.COMPLEX_UNIT_PX, drawParams.mPreviewTextSize);
                previewText.setTypeface(key.selectTypeface(drawParams));
            }
            previewText.setText(label);
        } else {
            previewText.setCompoundDrawables(null, null, null,
                    key.getPreviewIcon(mKeyboard.mIconsSet));
            previewText.setText(null);
        }

        previewText.measure(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        final int keyDrawWidth = key.getDrawWidth();
        final int previewWidth = previewText.getMeasuredWidth();
        final int previewHeight = mPreviewHeight;
        // The width and height of visible part of the key preview background. The content marker
        // of the background 9-patch have to cover the visible part of the background.
        previewParams.mPreviewVisibleWidth = previewWidth - previewText.getPaddingLeft()
                - previewText.getPaddingRight();
        previewParams.mPreviewVisibleHeight = previewHeight - previewText.getPaddingTop()
                - previewText.getPaddingBottom();
        // The distance between the top edge of the parent key and the bottom of the visible part
        // of the key preview background.
        previewParams.mPreviewVisibleOffset = mPreviewOffset - previewText.getPaddingBottom();
        getLocationInWindow(mCoordinates);
        // The key preview is horizontally aligned with the center of the visible part of the
        // parent key. If it doesn't fit in this {@link KeyboardView}, it is moved inward to fit and
        // the left/right background is used if such background is specified.
        final int statePosition;
        int previewX = key.getDrawX() - (previewWidth - keyDrawWidth) / 2 + mCoordinates[0];
        if (previewX < 0) {
            previewX = 0;
            statePosition = STATE_LEFT;
        } else if (previewX > getWidth() - previewWidth) {
            previewX = getWidth() - previewWidth;
            statePosition = STATE_RIGHT;
        } else {
            statePosition = STATE_MIDDLE;
        }
        // The key preview is placed vertically above the top edge of the parent key with an
        // arbitrary offset.
        final int previewY = key.mY - previewHeight + mPreviewOffset + mCoordinates[1];

        if (background != null) {
            final int hasMoreKeys = (key.mMoreKeys != null) ? STATE_HAS_MOREKEYS : STATE_NORMAL;
            background.setState(KEY_PREVIEW_BACKGROUND_STATE_TABLE[statePosition][hasMoreKeys]);
        }
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
    public void invalidateKey(final Key key) {
        if (mInvalidateAllKeys) return;
        if (key == null) return;
        mInvalidatedKeys.add(key);
        final int x = key.mX + getPaddingLeft();
        final int y = key.mY + getPaddingTop();
        invalidate(x, y, x + key.mWidth, y + key.mHeight);
    }

    public void closing() {
        dismissAllKeyPreviews();
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
        mPreviewPlacerView.removeAllViews();
        freeOffscreenBuffer();
    }
}
