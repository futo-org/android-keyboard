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
import android.content.pm.PackageManager;
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
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.accessibility.AccessibilityEvent;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.android.inputmethod.accessibility.AccessibilityUtils;
import com.android.inputmethod.accessibility.AccessibleKeyboardViewProxy;
import com.android.inputmethod.compat.FrameLayoutCompatUtils;
import com.android.inputmethod.keyboard.internal.Key;
import com.android.inputmethod.keyboard.internal.MiniKeyboardBuilder;
import com.android.inputmethod.keyboard.internal.PointerTrackerQueue;
import com.android.inputmethod.keyboard.internal.SwipeTracker;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.WeakHashMap;

/**
 * A view that renders a virtual {@link Keyboard}. It handles rendering of keys and detecting key
 * presses and touch movements.
 *
 * @attr ref R.styleable#KeyboardView_backgroundDimAmount
 * @attr ref R.styleable#KeyboardView_keyBackground
 * @attr ref R.styleable#KeyboardView_keyHysteresisDistance
 * @attr ref R.styleable#KeyboardView_keyLetterRatio
 * @attr ref R.styleable#KeyboardView_keyLabelRatio
 * @attr ref R.styleable#KeyboardView_keyHintLetterRatio
 * @attr ref R.styleable#KeyboardView_keyUppercaseLetterRatio
 * @attr ref R.styleable#KeyboardView_keyTextStyle
 * @attr ref R.styleable#KeyboardView_keyPreviewLayout
 * @attr ref R.styleable#KeyboardView_keyPreviewOffset
 * @attr ref R.styleable#KeyboardView_keyPreviewHeight
 * @attr ref R.styleable#KeyboardView_keyTextColor
 * @attr ref R.styleable#KeyboardView_keyTextColorDisabled
 * @attr ref R.styleable#KeyboardView_keyHintLetterColor
 * @attr ref R.styleable#KeyboardView_keyUppercaseLetterInactivatedColor
 * @attr ref R.styleable#KeyboardView_keyUppercaseLetterActivatedColor
 * @attr ref R.styleable#KeyboardView_verticalCorrection
 * @attr ref R.styleable#KeyboardView_popupLayout
 * @attr ref R.styleable#KeyboardView_shadowColor
 * @attr ref R.styleable#KeyboardView_shadowRadius
 */
public class KeyboardView extends View implements PointerTracker.UIProxy {
    private static final String TAG = KeyboardView.class.getSimpleName();
    private static final boolean DEBUG_SHOW_ALIGN = false;
    private static final boolean DEBUG_KEYBOARD_GRID = false;

    private static final boolean ENABLE_CAPSLOCK_BY_LONGPRESS = true;
    private static final boolean ENABLE_CAPSLOCK_BY_DOUBLETAP = true;

    // Timing constants
    private final int mKeyRepeatInterval;

    // Miscellaneous constants
    private static final int[] LONG_PRESSABLE_STATE_SET = { android.R.attr.state_long_pressable };
    private static final int HINT_ICON_VERTICAL_ADJUSTMENT_PIXEL = -1;

    // XML attribute
    private final float mKeyLetterRatio;
    private final int mKeyTextColor;
    private final int mKeyTextInactivatedColor;
    private final Typeface mKeyTextStyle;
    private final float mKeyLabelRatio;
    private final float mKeyHintLetterRatio;
    private final float mKeyUppercaseLetterRatio;
    private final int mShadowColor;
    private final float mShadowRadius;
    private final Drawable mKeyBackground;
    private final float mBackgroundDimAmount;
    private final float mKeyHysteresisDistance;
    private final float mVerticalCorrection;
    private final int mPreviewOffset;
    private final int mPreviewHeight;
    private final int mPopupLayout;
    private final Drawable mKeyPopupHintIcon;
    private final int mKeyHintLetterColor;
    private final int mKeyUppercaseLetterInactivatedColor;
    private final int mKeyUppercaseLetterActivatedColor;

    // Main keyboard
    private Keyboard mKeyboard;
    private int mKeyLetterSize;
    private int mKeyLabelSize;
    private int mKeyHintLetterSize;
    private int mKeyUppercaseLetterSize;

    // Key preview
    private boolean mInForeground;
    private TextView mPreviewText;
    private float mPreviewTextRatio;
    private int mPreviewTextSize;
    private boolean mShowKeyPreviewPopup = true;
    private int mKeyPreviewPopupDisplayedY = -1;
    private final int mDelayBeforePreview;
    private int mDelayAfterPreview;
    private ViewGroup mPreviewPlacer;
    private final int[] mCoordinates = new int[2];

    // Mini keyboard
    private PopupWindow mPopupWindow;
    private PopupPanel mPopupMiniKeyboardPanel;
    private final WeakHashMap<Key, PopupPanel> mPopupPanelCache =
            new WeakHashMap<Key, PopupPanel>();

    /** Listener for {@link KeyboardActionListener}. */
    private KeyboardActionListener mKeyboardActionListener;

    private final ArrayList<PointerTracker> mPointerTrackers = new ArrayList<PointerTracker>();

    // TODO: Let the PointerTracker class manage this pointer queue
    private final PointerTrackerQueue mPointerQueue = new PointerTrackerQueue();

    private final boolean mHasDistinctMultitouch;
    private int mOldPointerCount = 1;
    private int mOldKeyIndex;

    protected KeyDetector mKeyDetector = new KeyDetector();

    // Swipe gesture detector
    protected GestureDetector mGestureDetector;
    private final SwipeTracker mSwipeTracker = new SwipeTracker();
    private final int mSwipeThreshold;
    private final boolean mDisambiguateSwipe;

    // Drawing
    /** Whether the keyboard bitmap needs to be redrawn before it's blitted. **/
    private boolean mDrawPending;
    /** Notes if the keyboard just changed, so that we could possibly reallocate the mBuffer. */
    private boolean mKeyboardChanged;
    /** The dirty region in the keyboard bitmap */
    private final Rect mDirtyRect = new Rect();
    /** The key to invalidate. */
    private Key mInvalidatedKey;
    /** The dirty region for single key drawing */
    private final Rect mInvalidatedKeyRect = new Rect();
    /** The keyboard bitmap for faster updates */
    private Bitmap mBuffer;
    /** The canvas for the above mutable keyboard bitmap */
    private Canvas mCanvas;
    private final Paint mPaint = new Paint();
    private final Rect mPadding = new Rect();
    private final Rect mTextBounds = new Rect();
    // This map caches key label text height in pixel as value and key label text size as map key.
    private final HashMap<Integer, Integer> mTextHeightCache = new HashMap<Integer, Integer>();
    // This map caches key label text width in pixel as value and key label text size as map key.
    private final HashMap<Integer, Integer> mTextWidthCache = new HashMap<Integer, Integer>();
    // Distance from horizontal center of the key, proportional to key label text height and width.
    private static final float KEY_LABEL_VERTICAL_ADJUSTMENT_FACTOR_CENTER = 0.45f;
    private static final float KEY_LABEL_VERTICAL_PADDING_FACTOR = 1.60f;
    private static final String KEY_LABEL_REFERENCE_CHAR = "M";
    private final int mKeyLabelHorizontalPadding;

    private final UIHandler mHandler = new UIHandler();

    class UIHandler extends Handler {
        private static final int MSG_SHOW_KEY_PREVIEW = 1;
        private static final int MSG_DISMISS_KEY_PREVIEW = 2;
        private static final int MSG_REPEAT_KEY = 3;
        private static final int MSG_LONGPRESS_KEY = 4;
        private static final int MSG_LONGPRESS_SHIFT_KEY = 5;
        private static final int MSG_IGNORE_DOUBLE_TAP = 6;

        private boolean mInKeyRepeat;

        @Override
        public void handleMessage(Message msg) {
            final PointerTracker tracker = (PointerTracker) msg.obj;
            switch (msg.what) {
            case MSG_SHOW_KEY_PREVIEW:
                showKey(msg.arg1, tracker);
                break;
            case MSG_DISMISS_KEY_PREVIEW:
                mPreviewText.setVisibility(View.INVISIBLE);
                break;
            case MSG_REPEAT_KEY:
                tracker.onRepeatKey(msg.arg1);
                startKeyRepeatTimer(mKeyRepeatInterval, msg.arg1, tracker);
                break;
            case MSG_LONGPRESS_KEY:
                openMiniKeyboardIfRequired(msg.arg1, tracker);
                break;
            case MSG_LONGPRESS_SHIFT_KEY:
                onLongPressShiftKey(tracker);
                break;
            }
        }

        public void showKeyPreview(long delay, int keyIndex, PointerTracker tracker) {
            removeMessages(MSG_SHOW_KEY_PREVIEW);
            if (mPreviewText.getVisibility() == VISIBLE || delay == 0) {
                // Show right away, if it's already visible and finger is moving around
                showKey(keyIndex, tracker);
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

        public void startKeyRepeatTimer(long delay, int keyIndex, PointerTracker tracker) {
            mInKeyRepeat = true;
            sendMessageDelayed(obtainMessage(MSG_REPEAT_KEY, keyIndex, 0, tracker), delay);
        }

        public void cancelKeyRepeatTimer() {
            mInKeyRepeat = false;
            removeMessages(MSG_REPEAT_KEY);
        }

        public boolean isInKeyRepeat() {
            return mInKeyRepeat;
        }

        public void startLongPressTimer(long delay, int keyIndex, PointerTracker tracker) {
            cancelLongPressTimers();
            sendMessageDelayed(obtainMessage(MSG_LONGPRESS_KEY, keyIndex, 0, tracker), delay);
        }

        public void startLongPressShiftTimer(long delay, int keyIndex, PointerTracker tracker) {
            cancelLongPressTimers();
            if (ENABLE_CAPSLOCK_BY_LONGPRESS) {
                sendMessageDelayed(
                        obtainMessage(MSG_LONGPRESS_SHIFT_KEY, keyIndex, 0, tracker), delay);
            }
        }

        public void cancelLongPressTimers() {
            removeMessages(MSG_LONGPRESS_KEY);
            removeMessages(MSG_LONGPRESS_SHIFT_KEY);
        }

        public void cancelKeyTimers() {
            cancelKeyRepeatTimer();
            cancelLongPressTimers();
            removeMessages(MSG_IGNORE_DOUBLE_TAP);
        }

        public void startIgnoringDoubleTap() {
            sendMessageDelayed(obtainMessage(MSG_IGNORE_DOUBLE_TAP),
                    ViewConfiguration.getDoubleTapTimeout());
        }

        public boolean isIgnoringDoubleTap() {
            return hasMessages(MSG_IGNORE_DOUBLE_TAP);
        }

        public void cancelAllMessages() {
            cancelKeyTimers();
            cancelAllShowKeyPreviews();
            cancelAllDismissKeyPreviews();
        }
    }

    public KeyboardView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.keyboardViewStyle);
    }

    public KeyboardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.KeyboardView, defStyle, R.style.KeyboardView);

        mKeyBackground = a.getDrawable(R.styleable.KeyboardView_keyBackground);
        mKeyHysteresisDistance = a.getDimensionPixelOffset(
                R.styleable.KeyboardView_keyHysteresisDistance, 0);
        mVerticalCorrection = a.getDimensionPixelOffset(
                R.styleable.KeyboardView_verticalCorrection, 0);
        final int previewLayout = a.getResourceId(R.styleable.KeyboardView_keyPreviewLayout, 0);
        mPreviewOffset = a.getDimensionPixelOffset(R.styleable.KeyboardView_keyPreviewOffset, 0);
        mPreviewHeight = a.getDimensionPixelSize(R.styleable.KeyboardView_keyPreviewHeight, 80);
        mKeyLetterRatio = getRatio(a, R.styleable.KeyboardView_keyLetterRatio);
        mKeyLabelRatio = getRatio(a, R.styleable.KeyboardView_keyLabelRatio);
        mKeyHintLetterRatio = getRatio(a, R.styleable.KeyboardView_keyHintLetterRatio);
        mKeyUppercaseLetterRatio = getRatio(a,
                R.styleable.KeyboardView_keyUppercaseLetterRatio);
        mKeyTextColor = a.getColor(R.styleable.KeyboardView_keyTextColor, 0xFF000000);
        mKeyTextInactivatedColor = a.getColor(
                R.styleable.KeyboardView_keyTextInactivatedColor, 0xFF000000);
        mKeyPopupHintIcon = a.getDrawable(R.styleable.KeyboardView_keyPopupHintIcon);
        mKeyHintLetterColor = a.getColor(R.styleable.KeyboardView_keyHintLetterColor, 0);
        mKeyUppercaseLetterInactivatedColor = a.getColor(
                R.styleable.KeyboardView_keyUppercaseLetterInactivatedColor, 0);
        mKeyUppercaseLetterActivatedColor = a.getColor(
                R.styleable.KeyboardView_keyUppercaseLetterActivatedColor, 0);
        mKeyTextStyle = Typeface.defaultFromStyle(
                a.getInt(R.styleable.KeyboardView_keyTextStyle, Typeface.NORMAL));
        mPopupLayout = a.getResourceId(R.styleable.KeyboardView_popupLayout, 0);
        mShadowColor = a.getColor(R.styleable.KeyboardView_shadowColor, 0);
        mShadowRadius = a.getFloat(R.styleable.KeyboardView_shadowRadius, 0f);
        // TODO: Use Theme (android.R.styleable.Theme_backgroundDimAmount)
        mBackgroundDimAmount = a.getFloat(R.styleable.KeyboardView_backgroundDimAmount, 0.5f);
        a.recycle();

        final Resources res = getResources();

        if (previewLayout != 0) {
            mPreviewText = (TextView) LayoutInflater.from(context).inflate(previewLayout, null);
            mPreviewTextRatio = getRatio(res, R.fraction.key_preview_text_ratio);
        } else {
            mShowKeyPreviewPopup = false;
        }
        mDelayBeforePreview = res.getInteger(R.integer.config_delay_before_preview);
        mDelayAfterPreview = res.getInteger(R.integer.config_delay_after_preview);
        mKeyLabelHorizontalPadding = (int)res.getDimension(
                R.dimen.key_label_horizontal_alignment_padding);

        mPaint.setAntiAlias(true);
        mPaint.setTextAlign(Align.CENTER);
        mPaint.setAlpha(255);

        mKeyBackground.getPadding(mPadding);

        mSwipeThreshold = (int) (500 * res.getDisplayMetrics().density);
        // TODO: Refer to frameworks/base/core/res/res/values/config.xml
        mDisambiguateSwipe = res.getBoolean(R.bool.config_swipeDisambiguation);

        GestureDetector.SimpleOnGestureListener listener =
                new GestureDetector.SimpleOnGestureListener() {
            private boolean mProcessingShiftDoubleTapEvent = false;

            @Override
            public boolean onFling(MotionEvent me1, MotionEvent me2, float velocityX,
                    float velocityY) {
                final float absX = Math.abs(velocityX);
                final float absY = Math.abs(velocityY);
                float deltaY = me2.getY() - me1.getY();
                int travelY = getHeight() / 2; // Half the keyboard height
                mSwipeTracker.computeCurrentVelocity(1000);
                final float endingVelocityY = mSwipeTracker.getYVelocity();
                if (velocityY > mSwipeThreshold && absX < absY / 2 && deltaY > travelY) {
                    if (mDisambiguateSwipe && endingVelocityY >= velocityY / 4) {
                        onSwipeDown();
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean onDoubleTap(MotionEvent firstDown) {
                if (ENABLE_CAPSLOCK_BY_DOUBLETAP && mKeyboard instanceof LatinKeyboard
                        && ((LatinKeyboard) mKeyboard).isAlphaKeyboard()) {
                    final int pointerIndex = firstDown.getActionIndex();
                    final int id = firstDown.getPointerId(pointerIndex);
                    final PointerTracker tracker = getPointerTracker(id);
                    // If the first down event is on shift key.
                    if (tracker.isOnShiftKey((int)firstDown.getX(), (int)firstDown.getY())) {
                        mProcessingShiftDoubleTapEvent = true;
                        return true;
                    }
                }
                mProcessingShiftDoubleTapEvent = false;
                return false;
            }

            @Override
            public boolean onDoubleTapEvent(MotionEvent secondTap) {
                if (mProcessingShiftDoubleTapEvent
                        && secondTap.getAction() == MotionEvent.ACTION_DOWN) {
                    final MotionEvent secondDown = secondTap;
                    final int pointerIndex = secondDown.getActionIndex();
                    final int id = secondDown.getPointerId(pointerIndex);
                    final PointerTracker tracker = getPointerTracker(id);
                    // If the second down event is also on shift key.
                    if (tracker.isOnShiftKey((int)secondDown.getX(), (int)secondDown.getY())) {
                        // Detected a double tap on shift key. If we are in the ignoring double tap
                        // mode, it means we have already turned off caps lock in
                        // {@link KeyboardSwitcher#onReleaseShift} .
                        final boolean ignoringDoubleTap = mHandler.isIgnoringDoubleTap();
                        if (!ignoringDoubleTap)
                            onDoubleTapShiftKey(tracker);
                        return true;
                    }
                    // Otherwise these events should not be handled as double tap.
                    mProcessingShiftDoubleTapEvent = false;
                }
                return mProcessingShiftDoubleTapEvent;
            }
        };

        final boolean ignoreMultitouch = true;
        mGestureDetector = new GestureDetector(getContext(), listener, null, ignoreMultitouch);
        mGestureDetector.setIsLongpressEnabled(false);

        mHasDistinctMultitouch = context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT);
        mKeyRepeatInterval = res.getInteger(R.integer.config_key_repeat_interval);
    }

    // Read fraction value in TypedArray as float.
    private static float getRatio(TypedArray a, int index) {
        return a.getFraction(index, 1000, 1000, 1) / 1000.0f;
    }

    // Read fraction value in resource as float.
    private static float getRatio(Resources res, int id) {
        return res.getFraction(id, 1000, 1000) / 1000.0f;
    }

    public void startIgnoringDoubleTap() {
        if (ENABLE_CAPSLOCK_BY_DOUBLETAP)
            mHandler.startIgnoringDoubleTap();
    }

    public void setOnKeyboardActionListener(KeyboardActionListener listener) {
        mKeyboardActionListener = listener;
        for (PointerTracker tracker : mPointerTrackers) {
            tracker.setOnKeyboardActionListener(listener);
        }
    }

    /**
     * Returns the {@link KeyboardActionListener} object.
     * @return the listener attached to this keyboard
     */
    protected KeyboardActionListener getOnKeyboardActionListener() {
        return mKeyboardActionListener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        // TODO: Should notify InputMethodService instead?
        KeyboardSwitcher.getInstance().onSizeChanged();
    }

    /**
     * Attaches a keyboard to this view. The keyboard can be switched at any time and the
     * view will re-layout itself to accommodate the keyboard.
     * @see Keyboard
     * @see #getKeyboard()
     * @param keyboard the keyboard to display in this view
     */
    public void setKeyboard(Keyboard keyboard) {
        if (mKeyboard != null) {
            dismissAllKeyPreviews();
        }
        // Remove any pending messages, except dismissing preview
        mHandler.cancelKeyTimers();
        mHandler.cancelAllShowKeyPreviews();
        mKeyboard = keyboard;
        LatinImeLogger.onSetKeyboard(keyboard);
        mKeyDetector.setKeyboard(keyboard, -getPaddingLeft(),
                -getPaddingTop() + mVerticalCorrection);
        for (PointerTracker tracker : mPointerTrackers) {
            tracker.setKeyboard(keyboard, mKeyHysteresisDistance);
        }
        requestLayout();
        mKeyboardChanged = true;
        invalidateAllKeys();
        mKeyDetector.setProximityThreshold(keyboard.getMostCommonKeyWidth());
        mPopupPanelCache.clear();
        final int keyHeight = keyboard.getRowHeight() - keyboard.getVerticalGap();
        mKeyLetterSize = (int)(keyHeight * mKeyLetterRatio);
        mKeyLabelSize = (int)(keyHeight * mKeyLabelRatio);
        mKeyHintLetterSize = (int)(keyHeight * mKeyHintLetterRatio);
        mKeyUppercaseLetterSize = (int)(
                keyHeight * mKeyUppercaseLetterRatio);
        mPreviewTextSize = (int)(keyHeight * mPreviewTextRatio);
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
     * Returns whether the device has distinct multi-touch panel.
     * @return true if the device has distinct multi-touch panel.
     */
    @Override
    public boolean hasDistinctMultitouch() {
        return mHasDistinctMultitouch;
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

    /**
     * When enabled, calls to {@link KeyboardActionListener#onCodeInput} will include key
     * codes for adjacent keys.  When disabled, only the primary key code will be
     * reported.
     * @param enabled whether or not the proximity correction is enabled
     */
    public void setProximityCorrectionEnabled(boolean enabled) {
        mKeyDetector.setProximityCorrectionEnabled(enabled);
    }

    /**
     * Returns true if proximity correction is enabled.
     */
    public boolean isProximityCorrectionEnabled() {
        return mKeyDetector.isProximityCorrectionEnabled();
    }

    protected CharSequence adjustCase(CharSequence label) {
        if (mKeyboard.isShiftedOrShiftLocked() && label != null && label.length() < 3
                && Character.isLowerCase(label.charAt(0))) {
            return label.toString().toUpperCase();
        }
        return label;
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Round up a little
        if (mKeyboard == null) {
            setMeasuredDimension(
                    getPaddingLeft() + getPaddingRight(), getPaddingTop() + getPaddingBottom());
        } else {
            int width = mKeyboard.getMinWidth() + getPaddingLeft() + getPaddingRight();
            if (MeasureSpec.getSize(widthMeasureSpec) < width + 10) {
                width = MeasureSpec.getSize(widthMeasureSpec);
            }
            setMeasuredDimension(
                    width, mKeyboard.getHeight() + getPaddingTop() + getPaddingBottom());
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mDrawPending || mBuffer == null || mKeyboardChanged) {
            onBufferDraw();
        }
        canvas.drawBitmap(mBuffer, 0, 0, null);
    }

    private void onBufferDraw() {
        final int width = getWidth();
        final int height = getHeight();
        if (width == 0 || height == 0)
            return;
        if (mBuffer == null || mKeyboardChanged) {
            mKeyboardChanged = false;
            mDirtyRect.union(0, 0, width, height);
        }
        if (mBuffer == null || mBuffer.getWidth() != width || mBuffer.getHeight() != height) {
            if (mBuffer != null)
                mBuffer.recycle();
            mBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
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

        if (mInvalidatedKey != null && mInvalidatedKeyRect.contains(mDirtyRect)) {
            // Draw a single key.
            onBufferDrawKey(canvas, mInvalidatedKey);
        } else {
            // Draw all keys.
            for (final Key key : mKeyboard.getKeys()) {
                onBufferDrawKey(canvas, key);
            }
        }

        // TODO: Move this function to ProximityInfo for getting rid of
        // public declarations for
        // GRID_WIDTH and GRID_HEIGHT
        if (DEBUG_KEYBOARD_GRID) {
            Paint p = new Paint();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.0f);
            p.setColor(0x800000c0);
            int cw = (mKeyboard.getMinWidth() + mKeyboard.GRID_WIDTH - 1)
                    / mKeyboard.GRID_WIDTH;
            int ch = (mKeyboard.getHeight() + mKeyboard.GRID_HEIGHT - 1)
                    / mKeyboard.GRID_HEIGHT;
            for (int i = 0; i <= mKeyboard.GRID_WIDTH; i++)
                canvas.drawLine(i * cw, 0, i * cw, ch * mKeyboard.GRID_HEIGHT, p);
            for (int i = 0; i <= mKeyboard.GRID_HEIGHT; i++)
                canvas.drawLine(0, i * ch, cw * mKeyboard.GRID_WIDTH, i * ch, p);
        }

        // Overlay a dark rectangle to dim the keyboard
        if (mPopupMiniKeyboardPanel != null) {
            mPaint.setColor((int) (mBackgroundDimAmount * 0xFF) << 24);
            canvas.drawRect(0, 0, width, height, mPaint);
        }

        mInvalidatedKey = null;
        mDrawPending = false;
        mDirtyRect.setEmpty();
    }

    private void onBufferDrawKey(final Canvas canvas, final Key key) {
        final Paint paint = mPaint;
        final Drawable keyBackground = mKeyBackground;
        final Rect padding = mPadding;
        final int kbdPaddingLeft = getPaddingLeft();
        final int kbdPaddingTop = getPaddingTop();
        final int keyDrawX = key.mX + key.mVisualInsetsLeft;
        final int keyDrawWidth = key.mWidth - key.mVisualInsetsLeft - key.mVisualInsetsRight;
        final int rowHeight = padding.top + key.mHeight;
        final boolean isManualTemporaryUpperCase = mKeyboard.isManualTemporaryUpperCase();

        canvas.translate(keyDrawX + kbdPaddingLeft, key.mY + kbdPaddingTop);

        // Draw key background.
        final int[] drawableState = key.getCurrentDrawableState();
        keyBackground.setState(drawableState);
        final Rect bounds = keyBackground.getBounds();
        if (keyDrawWidth != bounds.right || key.mHeight != bounds.bottom) {
            keyBackground.setBounds(0, 0, keyDrawWidth, key.mHeight);
        }
        keyBackground.draw(canvas);

        // Draw key label.
        if (key.mLabel != null) {
            // Switch the character to uppercase if shift is pressed
            final String label = key.mLabel == null ? null : adjustCase(key.mLabel).toString();
            // For characters, use large font. For labels like "Done", use small font.
            final int labelSize = getLabelSizeAndSetPaint(label, key.mLabelOption, paint);
            final int labelCharHeight = getLabelCharHeight(labelSize, paint);

            // Vertical label text alignment.
            final float baseline;
            if ((key.mLabelOption & Key.LABEL_OPTION_ALIGN_BOTTOM) != 0) {
                baseline = key.mHeight - labelCharHeight * KEY_LABEL_VERTICAL_PADDING_FACTOR;
                if (DEBUG_SHOW_ALIGN)
                    drawHorizontalLine(canvas, (int)baseline, keyDrawWidth, 0xc0008000,
                            new Paint());
            } else { // Align center
                final float centerY = (key.mHeight + padding.top - padding.bottom) / 2;
                baseline = centerY + labelCharHeight * KEY_LABEL_VERTICAL_ADJUSTMENT_FACTOR_CENTER;
                if (DEBUG_SHOW_ALIGN)
                    drawHorizontalLine(canvas, (int)baseline, keyDrawWidth, 0xc0008000,
                            new Paint());
            }
            // Horizontal label text alignment
            final int positionX;
            if ((key.mLabelOption & Key.LABEL_OPTION_ALIGN_LEFT) != 0) {
                positionX = mKeyLabelHorizontalPadding + padding.left;
                paint.setTextAlign(Align.LEFT);
                if (DEBUG_SHOW_ALIGN)
                    drawVerticalLine(canvas, positionX, rowHeight, 0xc0800080, new Paint());
            } else if ((key.mLabelOption & Key.LABEL_OPTION_ALIGN_RIGHT) != 0) {
                positionX = keyDrawWidth - mKeyLabelHorizontalPadding - padding.right;
                paint.setTextAlign(Align.RIGHT);
                if (DEBUG_SHOW_ALIGN)
                    drawVerticalLine(canvas, positionX, rowHeight, 0xc0808000, new Paint());
            } else {
                positionX = (keyDrawWidth + padding.left - padding.right) / 2;
                paint.setTextAlign(Align.CENTER);
                if (DEBUG_SHOW_ALIGN) {
                    if (label.length() > 1)
                        drawVerticalLine(canvas, positionX, rowHeight, 0xc0008080, new Paint());
                }
            }
            if (key.hasUppercaseLetter() && isManualTemporaryUpperCase) {
                paint.setColor(mKeyTextInactivatedColor);
            } else {
                paint.setColor(mKeyTextColor);
            }
            if (key.mEnabled) {
                // Set a drop shadow for the text
                paint.setShadowLayer(mShadowRadius, 0, 0, mShadowColor);
            } else {
                // Make label invisible
                paint.setColor(Color.TRANSPARENT);
            }
            canvas.drawText(label, positionX, baseline, paint);
            // Turn off drop shadow
            paint.setShadowLayer(0, 0, 0, 0);
        }

        // Draw hint letter.
        if (key.mHintLetter != null) {
            final String label = key.mHintLetter.toString();
            final int textColor;
            final int textSize;
            if (key.hasUppercaseLetter()) {
                textColor = isManualTemporaryUpperCase ? mKeyUppercaseLetterActivatedColor
                        : mKeyUppercaseLetterInactivatedColor;
                textSize = mKeyUppercaseLetterSize;
            } else {
                textColor = mKeyHintLetterColor;
                textSize = mKeyHintLetterSize;
            }
            paint.setColor(textColor);
            paint.setTextSize(textSize);
            // Note: padding.right for drawX?
            final float drawX = keyDrawWidth - getLabelCharWidth(textSize, paint);
            final float drawY = -paint.ascent() + padding.top;
            canvas.drawText(label, drawX, drawY, paint);
        }

        // Draw key icon.
        final Drawable icon = key.getIcon();
        if (key.mLabel == null && icon != null) {
            final int drawableWidth = icon.getIntrinsicWidth();
            final int drawableHeight = icon.getIntrinsicHeight();
            final int drawableX;
            final int drawableY = (key.mHeight + padding.top - padding.bottom - drawableHeight) / 2;
            if ((key.mLabelOption & Key.LABEL_OPTION_ALIGN_LEFT) != 0) {
                drawableX = padding.left + mKeyLabelHorizontalPadding;
                if (DEBUG_SHOW_ALIGN)
                    drawVerticalLine(canvas, drawableX, rowHeight, 0xc0800080, new Paint());
            } else if ((key.mLabelOption & Key.LABEL_OPTION_ALIGN_RIGHT) != 0) {
                drawableX = keyDrawWidth - padding.right - mKeyLabelHorizontalPadding
                        - drawableWidth;
                if (DEBUG_SHOW_ALIGN)
                    drawVerticalLine(canvas, drawableX + drawableWidth, rowHeight,
                            0xc0808000, new Paint());
            } else { // Align center
                drawableX = (keyDrawWidth + padding.left - padding.right - drawableWidth) / 2;
                if (DEBUG_SHOW_ALIGN)
                    drawVerticalLine(canvas, drawableX + drawableWidth / 2, rowHeight,
                            0xc0008080, new Paint());
            }
            drawIcon(canvas, icon, drawableX, drawableY, drawableWidth, drawableHeight);
            if (DEBUG_SHOW_ALIGN)
                drawRectangle(canvas, drawableX, drawableY, drawableWidth, drawableHeight,
                        0x80c00000, new Paint());
        }

        // Draw popup hint icon "...".
        // TODO: Draw "..." by text.
        if (key.hasPopupHint()) {
            final int drawableWidth = keyDrawWidth;
            final int drawableHeight = key.mHeight;
            final int drawableX = 0;
            final int drawableY = HINT_ICON_VERTICAL_ADJUSTMENT_PIXEL;
            final Drawable hintIcon = mKeyPopupHintIcon;
            drawIcon(canvas, hintIcon, drawableX, drawableY, drawableWidth, drawableHeight);
            if (DEBUG_SHOW_ALIGN)
                drawRectangle(canvas, drawableX, drawableY, drawableWidth, drawableHeight,
                        0x80c0c000, new Paint());
        }

        canvas.translate(-keyDrawX - kbdPaddingLeft, -key.mY - kbdPaddingTop);
    }

    public int getLabelSizeAndSetPaint(CharSequence label, int keyLabelOption, Paint paint) {
        // For characters, use large font. For labels like "Done", use small font.
        final int labelSize;
        final Typeface labelStyle;
        if (label.length() > 1) {
            labelSize = mKeyLabelSize;
            if ((keyLabelOption & Key.LABEL_OPTION_FONT_NORMAL) != 0) {
                labelStyle = Typeface.DEFAULT;
            } else {
                labelStyle = Typeface.DEFAULT_BOLD;
            }
        } else {
            labelSize = mKeyLetterSize;
            labelStyle = mKeyTextStyle;
        }
        paint.setTextSize(labelSize);
        paint.setTypeface(labelStyle);
        return labelSize;
    }

    private int getLabelCharHeight(int labelSize, Paint paint) {
        Integer labelHeightValue = mTextHeightCache.get(labelSize);
        final int labelCharHeight;
        if (labelHeightValue != null) {
            labelCharHeight = labelHeightValue;
        } else {
            paint.getTextBounds(KEY_LABEL_REFERENCE_CHAR, 0, 1, mTextBounds);
            labelCharHeight = mTextBounds.height();
            mTextHeightCache.put(labelSize, labelCharHeight);
        }
        return labelCharHeight;
    }

    private int getLabelCharWidth(int labelSize, Paint paint) {
        Integer labelWidthValue = mTextWidthCache.get(labelSize);
        final int labelCharWidth;
        if (labelWidthValue != null) {
            labelCharWidth = labelWidthValue;
        } else {
            paint.getTextBounds(KEY_LABEL_REFERENCE_CHAR, 0, 1, mTextBounds);
            labelCharWidth = mTextBounds.width();
            mTextWidthCache.put(labelSize, labelCharWidth);
        }
        return labelCharWidth;
    }

    private static void drawIcon(Canvas canvas, Drawable icon, int x, int y, int width,
            int height) {
        canvas.translate(x, y);
        icon.setBounds(0, 0, width, height);
        icon.draw(canvas);
        canvas.translate(-x, -y);
    }

    private static void drawHorizontalLine(Canvas canvas, int y, int w, int color, Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.0f);
        paint.setColor(color);
        canvas.drawLine(0, y, w, y, paint);
    }

    private static void drawVerticalLine(Canvas canvas, int x, int h, int color, Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.0f);
        paint.setColor(color);
        canvas.drawLine(x, 0, x, h, paint);
    }

    private static void drawRectangle(Canvas canvas, int x, int y, int w, int h, int color,
            Paint paint) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.0f);
        paint.setColor(color);
        canvas.translate(x, y);
        canvas.drawRect(0, 0, w, h, paint);
        canvas.translate(-x, -y);
    }

    public void setForeground(boolean foreground) {
        mInForeground = foreground;
    }

    // TODO: clean up this method.
    private void dismissAllKeyPreviews() {
        for (PointerTracker tracker : mPointerTrackers) {
            tracker.setReleasedKeyGraphics();
            dismissKeyPreview(tracker);
        }
    }

    @Override
    public void showKeyPreview(int keyIndex, PointerTracker tracker) {
        if (mShowKeyPreviewPopup) {
            mHandler.showKeyPreview(mDelayBeforePreview, keyIndex, tracker);
        } else if (mKeyboard.needSpacebarPreview(keyIndex)) {
            // Show key preview (in this case, slide language switcher) without any delay.
            showKey(keyIndex, tracker);
        }
    }

    @Override
    public void dismissKeyPreview(PointerTracker tracker) {
        if (mShowKeyPreviewPopup) {
            mHandler.cancelShowKeyPreview(tracker);
            mHandler.dismissKeyPreview(mDelayAfterPreview, tracker);
        } else if (mKeyboard.needSpacebarPreview(KeyDetector.NOT_A_KEY)) {
            // Dismiss key preview (in this case, slide language switcher) without any delay.
            mPreviewText.setVisibility(View.INVISIBLE);
        }
        // Clear key preview display position.
        mKeyPreviewPopupDisplayedY = -1;
    }

    private void addKeyPreview(TextView keyPreview) {
        if (mPreviewPlacer == null) {
            mPreviewPlacer = FrameLayoutCompatUtils.getPlacer(
                    (ViewGroup)getRootView().findViewById(android.R.id.content));
        }
        final ViewGroup placer = mPreviewPlacer;
        placer.addView(keyPreview, FrameLayoutCompatUtils.newLayoutParam(placer, 0, 0));
    }

    // TODO: Introduce minimum duration for displaying key previews
    // TODO: Display up to two key previews when the user presses two keys at the same time
    private void showKey(final int keyIndex, PointerTracker tracker) {
        final TextView previewText = mPreviewText;
        // If the key preview has no parent view yet, add it to the ViewGroup which can place
        // key preview absolutely in SoftInputWindow.
        if (previewText.getParent() == null) {
            addKeyPreview(previewText);
        }

        final Key key = tracker.getKey(keyIndex);
        // If keyIndex is invalid or IME is already closed, we must not show key preview.
        // Trying to show key preview while root window is closed causes
        // WindowManager.BadTokenException.
        if (key == null || !mInForeground)
            return;

        mHandler.cancelAllDismissKeyPreviews();

        final int keyDrawX = key.mX + key.mVisualInsetsLeft;
        final int keyDrawWidth = key.mWidth - key.mVisualInsetsLeft - key.mVisualInsetsRight;
        // What we show as preview should match what we show on key top in onBufferDraw(). 
        if (key.mLabel != null) {
            // TODO Should take care of temporaryShiftLabel here.
            previewText.setCompoundDrawables(null, null, null, null);
            previewText.setText(adjustCase(tracker.getPreviewText(key)));
            if (key.mLabel.length() > 1) {
                previewText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mKeyLetterSize);
                previewText.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                previewText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mPreviewTextSize);
                previewText.setTypeface(mKeyTextStyle);
            }
        } else {
            final Drawable previewIcon = key.getPreviewIcon();
            previewText.setCompoundDrawables(null, null, null,
                   previewIcon != null ? previewIcon : key.getIcon());
            previewText.setText(null);
        }
        // Set the preview background state
        previewText.getBackground().setState(
                key.mPopupCharacters != null ? LONG_PRESSABLE_STATE_SET : EMPTY_STATE_SET);

        previewText.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        final int previewWidth = Math.max(previewText.getMeasuredWidth(), keyDrawWidth
                + previewText.getPaddingLeft() + previewText.getPaddingRight());
        final int previewHeight = mPreviewHeight;
        getLocationInWindow(mCoordinates);
        final int previewX = keyDrawX - (previewWidth - keyDrawWidth) / 2 + mCoordinates[0];
        final int previewY = key.mY - previewHeight + mCoordinates[1] + mPreviewOffset;
        // Record key preview position to display mini-keyboard later at the same position
        mKeyPreviewPopupDisplayedY = previewY;

        // Place the key preview.
        // TODO: Adjust position of key previews which touch screen edges
        final MarginLayoutParams lp = (MarginLayoutParams)previewText.getLayoutParams();
        lp.width = previewWidth;
        lp.height = previewHeight;
        lp.setMargins(previewX, previewY, 0, 0);
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
        mDrawPending = true;
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
        onBufferDraw();
        invalidate(mInvalidatedKeyRect);
    }

    private boolean openMiniKeyboardIfRequired(int keyIndex, PointerTracker tracker) {
        // Check if we have a popup layout specified first.
        if (mPopupLayout == 0) {
            return false;
        }

        final Key parentKey = tracker.getKey(keyIndex);
        if (parentKey == null)
            return false;
        boolean result = onLongPress(parentKey, tracker);
        if (result) {
            dismissAllKeyPreviews();
            tracker.onLongPressed(mPointerQueue);
        }
        return result;
    }

    private void onLongPressShiftKey(PointerTracker tracker) {
        tracker.onLongPressed(mPointerQueue);
        mKeyboardActionListener.onCodeInput(Keyboard.CODE_CAPSLOCK, null, 0, 0);
    }

    private void onDoubleTapShiftKey(@SuppressWarnings("unused") PointerTracker tracker) {
        // When shift key is double tapped, the first tap is correctly processed as usual tap. And
        // the second tap is treated as this double tap event, so that we need not mark tracker
        // calling setAlreadyProcessed() nor remove the tracker from mPointerQueueueue.
        mKeyboardActionListener.onCodeInput(Keyboard.CODE_CAPSLOCK, null, 0, 0);
    }

    // This default implementation returns a popup mini keyboard panel.
    // A derived class may return a language switcher popup panel, for instance.
    protected PopupPanel onCreatePopupPanel(Key parentKey) {
        if (parentKey.mPopupCharacters == null)
            return null;

        final View container = LayoutInflater.from(getContext()).inflate(mPopupLayout, null);
        if (container == null)
            throw new NullPointerException();

        final PopupMiniKeyboardView miniKeyboardView =
                (PopupMiniKeyboardView)container.findViewById(R.id.mini_keyboard_view);
        miniKeyboardView.setOnKeyboardActionListener(new KeyboardActionListener() {
            @Override
            public void onCodeInput(int primaryCode, int[] keyCodes, int x, int y) {
                mKeyboardActionListener.onCodeInput(primaryCode, keyCodes, x, y);
                dismissMiniKeyboard();
            }

            @Override
            public void onTextInput(CharSequence text) {
                mKeyboardActionListener.onTextInput(text);
                dismissMiniKeyboard();
            }

            @Override
            public void onCancelInput() {
                mKeyboardActionListener.onCancelInput();
                dismissMiniKeyboard();
            }

            @Override
            public void onSwipeDown() {
                // Nothing to do.
            }
            @Override
            public void onPress(int primaryCode, boolean withSliding) {
                mKeyboardActionListener.onPress(primaryCode, withSliding);
            }
            @Override
            public void onRelease(int primaryCode, boolean withSliding) {
                mKeyboardActionListener.onRelease(primaryCode, withSliding);
            }
        });

        final Keyboard keyboard = new MiniKeyboardBuilder(this, mKeyboard.getPopupKeyboardResId(),
                parentKey, mKeyboard).build();
        miniKeyboardView.setKeyboard(keyboard);

        container.measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.AT_MOST));

        return miniKeyboardView;
    }

    /**
     * Called when a key is long pressed. By default this will open mini keyboard associated
     * with this key.
     * @param parentKey the key that was long pressed
     * @param tracker the pointer tracker which pressed the parent key
     * @return true if the long press is handled, false otherwise. Subclasses should call the
     * method on the base class if the subclass doesn't wish to handle the call.
     */
    protected boolean onLongPress(Key parentKey, PointerTracker tracker) {
        PopupPanel popupPanel = mPopupPanelCache.get(parentKey);
        if (popupPanel == null) {
            popupPanel = onCreatePopupPanel(parentKey);
            if (popupPanel == null)
                return false;
            mPopupPanelCache.put(parentKey, popupPanel);
        }
        if (mPopupWindow == null) {
            mPopupWindow = new PopupWindow(getContext());
            mPopupWindow.setBackgroundDrawable(null);
            mPopupWindow.setAnimationStyle(R.style.PopupMiniKeyboardAnimation);
            // Allow popup window to be drawn off the screen.
            mPopupWindow.setClippingEnabled(false);
        }
        mPopupMiniKeyboardPanel = popupPanel;
        popupPanel.showPanel(this, parentKey, tracker, mKeyPreviewPopupDisplayedY, mPopupWindow);

        invalidateAllKeys();
        return true;
    }

    private PointerTracker getPointerTracker(final int id) {
        final ArrayList<PointerTracker> pointers = mPointerTrackers;
        final KeyboardActionListener listener = mKeyboardActionListener;

        // Create pointer trackers until we can get 'id+1'-th tracker, if needed.
        for (int i = pointers.size(); i <= id; i++) {
            final PointerTracker tracker =
                new PointerTracker(i, this, mHandler, mKeyDetector, this);
            if (mKeyboard != null)
                tracker.setKeyboard(mKeyboard, mKeyHysteresisDistance);
            if (listener != null)
                tracker.setOnKeyboardActionListener(listener);
            pointers.add(tracker);
        }

        return pointers.get(id);
    }

    public boolean isInSlidingKeyInput() {
        if (mPopupMiniKeyboardPanel != null) {
            return mPopupMiniKeyboardPanel.isInSlidingKeyInput();
        } else {
            return mPointerQueue.isInSlidingKeyInput();
        }
    }

    public int getPointerCount() {
        return mOldPointerCount;
    }

    @Override
    public boolean onTouchEvent(MotionEvent me) {
        final int action = me.getActionMasked();
        final int pointerCount = me.getPointerCount();
        final int oldPointerCount = mOldPointerCount;
        mOldPointerCount = pointerCount;

        // TODO: cleanup this code into a multi-touch to single-touch event converter class?
        // If the device does not have distinct multi-touch support panel, ignore all multi-touch
        // events except a transition from/to single-touch.
        if (!mHasDistinctMultitouch && pointerCount > 1 && oldPointerCount > 1) {
            return true;
        }

        // Track the last few movements to look for spurious swipes.
        mSwipeTracker.addMovement(me);

        // Gesture detector must be enabled only when mini-keyboard is not on the screen.
        if (mPopupMiniKeyboardPanel == null && mGestureDetector != null
                && mGestureDetector.onTouchEvent(me)) {
            dismissAllKeyPreviews();
            mHandler.cancelKeyTimers();
            return true;
        }

        final long eventTime = me.getEventTime();
        final int index = me.getActionIndex();
        final int id = me.getPointerId(index);
        final int x = (int)me.getX(index);
        final int y = (int)me.getY(index);

        // Needs to be called after the gesture detector gets a turn, as it may have displayed the
        // mini keyboard
        if (mPopupMiniKeyboardPanel != null) {
            return mPopupMiniKeyboardPanel.onTouchEvent(me);
        }

        if (mHandler.isInKeyRepeat()) {
            final PointerTracker tracker = getPointerTracker(id);
            // Key repeating timer will be canceled if 2 or more keys are in action, and current
            // event (UP or DOWN) is non-modifier key.
            if (pointerCount > 1 && !tracker.isModifier()) {
                mHandler.cancelKeyRepeatTimer();
            }
            // Up event will pass through.
        }

        // TODO: cleanup this code into a multi-touch to single-touch event converter class?
        // Translate mutli-touch event to single-touch events on the device that has no distinct
        // multi-touch panel.
        if (!mHasDistinctMultitouch) {
            // Use only main (id=0) pointer tracker.
            PointerTracker tracker = getPointerTracker(0);
            if (pointerCount == 1 && oldPointerCount == 2) {
                // Multi-touch to single touch transition.
                // Send a down event for the latest pointer if the key is different from the
                // previous key.
                final int newKeyIndex = tracker.getKeyIndexOn(x, y);
                if (mOldKeyIndex != newKeyIndex) {
                    tracker.onDownEvent(x, y, eventTime, null);
                    if (action == MotionEvent.ACTION_UP)
                        tracker.onUpEvent(x, y, eventTime, null);
                }
            } else if (pointerCount == 2 && oldPointerCount == 1) {
                // Single-touch to multi-touch transition.
                // Send an up event for the last pointer.
                final int lastX = tracker.getLastX();
                final int lastY = tracker.getLastY();
                mOldKeyIndex = tracker.getKeyIndexOn(lastX, lastY);
                tracker.onUpEvent(lastX, lastY, eventTime, null);
            } else if (pointerCount == 1 && oldPointerCount == 1) {
                tracker.onTouchEvent(action, x, y, eventTime, null);
            } else {
                Log.w(TAG, "Unknown touch panel behavior: pointer count is " + pointerCount
                        + " (old " + oldPointerCount + ")");
            }
            return true;
        }

        final PointerTrackerQueue queue = mPointerQueue;
        if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < pointerCount; i++) {
                final PointerTracker tracker = getPointerTracker(me.getPointerId(i));
                tracker.onMoveEvent((int)me.getX(i), (int)me.getY(i), eventTime, queue);
            }
        } else {
            final PointerTracker tracker = getPointerTracker(id);
            switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                tracker.onDownEvent(x, y, eventTime, queue);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                tracker.onUpEvent(x, y, eventTime, queue);
                break;
            case MotionEvent.ACTION_CANCEL:
                tracker.onCancelEvent(x, y, eventTime, queue);
                break;
            }
        }

        return true;
    }

    protected void onSwipeDown() {
        mKeyboardActionListener.onSwipeDown();
    }

    public void closing() {
        mPreviewText.setVisibility(View.GONE);
        mHandler.cancelAllMessages();

        dismissMiniKeyboard();
        mDirtyRect.union(0, 0, getWidth(), getHeight());
        mPopupPanelCache.clear();
        requestLayout();
    }

    public void purgeKeyboardAndClosing() {
        mKeyboard = null;
        closing();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        closing();
    }

    private boolean dismissMiniKeyboard() {
        if (mPopupWindow != null && mPopupWindow.isShowing()) {
            mPopupWindow.dismiss();
            mPopupMiniKeyboardPanel = null;
            invalidateAllKeys();
            return true;
        }
        return false;
    }

    public boolean handleBack() {
        return dismissMiniKeyboard();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (AccessibilityUtils.getInstance().isTouchExplorationEnabled()) {
            return AccessibleKeyboardViewProxy.getInstance().dispatchTouchEvent(event)
                    || super.dispatchTouchEvent(event);
        }

        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (AccessibilityUtils.getInstance().isTouchExplorationEnabled()) {
            final PointerTracker tracker = getPointerTracker(0);
            return AccessibleKeyboardViewProxy.getInstance().dispatchPopulateAccessibilityEvent(
                    event, tracker) || super.dispatchPopulateAccessibilityEvent(event);
        }

        return super.dispatchPopulateAccessibilityEvent(event);
    }

    public boolean onHoverEvent(MotionEvent event) {
        // Since reflection doesn't support calling superclass methods, this
        // method checks for the existence of onHoverEvent() in the View class
        // before returning a value.
        if (AccessibilityUtils.getInstance().isTouchExplorationEnabled()) {
            final PointerTracker tracker = getPointerTracker(0);
            return AccessibleKeyboardViewProxy.getInstance().onHoverEvent(event, tracker);
        }

        return false;
    }
}
