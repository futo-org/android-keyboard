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

import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;

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
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.WeakHashMap;

/**
 * A view that renders a virtual {@link Keyboard}. It handles rendering of keys and detecting key
 * presses and touch movements.
 *
 * @attr ref R.styleable#KeyboardView_keyBackground
 * @attr ref R.styleable#KeyboardView_keyPreviewLayout
 * @attr ref R.styleable#KeyboardView_keyPreviewOffset
 * @attr ref R.styleable#KeyboardView_labelTextSize
 * @attr ref R.styleable#KeyboardView_keyTextSize
 * @attr ref R.styleable#KeyboardView_keyTextColor
 * @attr ref R.styleable#KeyboardView_verticalCorrection
 * @attr ref R.styleable#KeyboardView_popupLayout
 */
public class KeyboardView extends View implements PointerTracker.UIProxy {
    private static final String TAG = KeyboardView.class.getSimpleName();
    private static final boolean DEBUG_SHOW_ALIGN = false;
    private static final boolean DEBUG_KEYBOARD_GRID = false;

    private static final boolean ENABLE_CAPSLOCK_BY_LONGPRESS = false;
    private static final boolean ENABLE_CAPSLOCK_BY_DOUBLETAP = true;

    public static final int COLOR_SCHEME_WHITE = 0;
    public static final int COLOR_SCHEME_BLACK = 1;

    // Timing constants
    private final int mKeyRepeatInterval;

    // Miscellaneous constants
    private static final int[] LONG_PRESSABLE_STATE_SET = { android.R.attr.state_long_pressable };
    private static final int HINT_ICON_VERTICAL_ADJUSTMENT_PIXEL = -1;

    // XML attribute
    private int mKeyLetterSize;
    private int mKeyTextColor;
    private int mKeyTextColorDisabled;
    private Typeface mKeyLetterStyle = Typeface.DEFAULT;
    private int mLabelTextSize;
    private int mColorScheme = COLOR_SCHEME_WHITE;
    private int mShadowColor;
    private float mShadowRadius;
    private Drawable mKeyBackground;
    private float mBackgroundDimAmount;
    private float mKeyHysteresisDistance;
    private float mVerticalCorrection;
    private int mPreviewOffset;
    private int mPreviewHeight;
    private int mPopupLayout;

    // Main keyboard
    private Keyboard mKeyboard;

    // Key preview
    private boolean mInForeground;
    private TextView mPreviewText;
    private int mPreviewTextSizeLarge;
    private final int[] mOffsetInWindow = new int[2];
    private boolean mShowKeyPreview = true;
    private int mKeyPreviewDisplayedY;
    private final int mDelayBeforePreview;
    private final int mDelayAfterPreview;
    private ViewGroup mPreviewPlacer;

    // Mini keyboard
    private PopupWindow mMiniKeyboardWindow;
    private KeyboardView mMiniKeyboardView;
    private final WeakHashMap<Key, View> mMiniKeyboardCache = new WeakHashMap<Key, View>();
    private int mMiniKeyboardOriginX;
    private int mMiniKeyboardOriginY;
    private long mMiniKeyboardDisplayedTime;
    private int[] mWindowOffset;
    private final float mMiniKeyboardSlideAllowance;
    private int mMiniKeyboardTrackerId;
    private final boolean mConfigShowMiniKeyboardAtTouchedPoint;

    /** Listener for {@link KeyboardActionListener}. */
    private KeyboardActionListener mKeyboardActionListener;

    private final ArrayList<PointerTracker> mPointerTrackers = new ArrayList<PointerTracker>();

    // TODO: Let the PointerTracker class manage this pointer queue
    private final PointerTrackerQueue mPointerQueue = new PointerTrackerQueue();

    private final boolean mHasDistinctMultitouch;
    private int mOldPointerCount = 1;

    // Accessibility
    private boolean mIsAccessibilityEnabled;

    protected KeyDetector mKeyDetector = new KeyDetector();

    // Swipe gesture detector
    private GestureDetector mGestureDetector;
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
    private final Paint mPaint;
    private final Rect mPadding;
    // This map caches key label text height in pixel as value and key label text size as map key.
    private final HashMap<Integer, Integer> mTextHeightCache = new HashMap<Integer, Integer>();
    // Distance from horizontal center of the key, proportional to key label text height and width.
    private final float KEY_LABEL_VERTICAL_ADJUSTMENT_FACTOR_CENTER = 0.45f;
    private final float KEY_LABEL_VERTICAL_PADDING_FACTOR = 1.60f;
    private final String KEY_LABEL_REFERENCE_CHAR = "H";
    private final int KEY_LABEL_OPTION_ALIGN_LEFT = 1;
    private final int KEY_LABEL_OPTION_ALIGN_RIGHT = 2;
    private final int KEY_LABEL_OPTION_ALIGN_BOTTOM = 8;
    private final int KEY_LABEL_OPTION_FONT_NORMAL = 16;
    private final int mKeyLabelHorizontalPadding;

    private final UIHandler mHandler = new UIHandler();

    class UIHandler extends Handler {
        private static final int MSG_SHOW_KEY_PREVIEW = 1;
        private static final int MSG_DISMISS_KEY_PREVIEW = 2;
        private static final int MSG_REPEAT_KEY = 3;
        private static final int MSG_LONGPRESS_KEY = 4;
        private static final int MSG_LONGPRESS_SHIFT_KEY = 5;

        private boolean mInKeyRepeat;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SHOW_KEY_PREVIEW:
                    showKey(msg.arg1, (PointerTracker)msg.obj);
                    break;
                case MSG_DISMISS_KEY_PREVIEW:
                    mPreviewText.setVisibility(View.INVISIBLE);
                    break;
                case MSG_REPEAT_KEY: {
                    final PointerTracker tracker = (PointerTracker)msg.obj;
                    tracker.repeatKey(msg.arg1);
                    startKeyRepeatTimer(mKeyRepeatInterval, msg.arg1, tracker);
                    break;
                }
                case MSG_LONGPRESS_KEY: {
                    final PointerTracker tracker = (PointerTracker)msg.obj;
                    openMiniKeyboardIfRequired(msg.arg1, tracker);
                    break;
                }
                case MSG_LONGPRESS_SHIFT_KEY: {
                    final PointerTracker tracker = (PointerTracker)msg.obj;
                    onLongPressShiftKey(tracker);
                    break;
                }
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
        int previewLayout = 0;
        int keyTextSize = 0;

        int n = a.getIndexCount();

        for (int i = 0; i < n; i++) {
            int attr = a.getIndex(i);

            switch (attr) {
            case R.styleable.KeyboardView_keyBackground:
                mKeyBackground = a.getDrawable(attr);
                break;
            case R.styleable.KeyboardView_keyHysteresisDistance:
                mKeyHysteresisDistance = a.getDimensionPixelOffset(attr, 0);
                break;
            case R.styleable.KeyboardView_verticalCorrection:
                mVerticalCorrection = a.getDimensionPixelOffset(attr, 0);
                break;
            case R.styleable.KeyboardView_keyPreviewLayout:
                previewLayout = a.getResourceId(attr, 0);
                break;
            case R.styleable.KeyboardView_keyPreviewOffset:
                mPreviewOffset = a.getDimensionPixelOffset(attr, 0);
                break;
            case R.styleable.KeyboardView_keyPreviewHeight:
                mPreviewHeight = a.getDimensionPixelSize(attr, 80);
                break;
            case R.styleable.KeyboardView_keyLetterSize:
                mKeyLetterSize = a.getDimensionPixelSize(attr, 18);
                break;
            case R.styleable.KeyboardView_keyTextColor:
                mKeyTextColor = a.getColor(attr, 0xFF000000);
                break;
            case R.styleable.KeyboardView_keyTextColorDisabled:
                mKeyTextColorDisabled = a.getColor(attr, 0xFF000000);
                break;
            case R.styleable.KeyboardView_labelTextSize:
                mLabelTextSize = a.getDimensionPixelSize(attr, 14);
                break;
            case R.styleable.KeyboardView_popupLayout:
                mPopupLayout = a.getResourceId(attr, 0);
                break;
            case R.styleable.KeyboardView_shadowColor:
                mShadowColor = a.getColor(attr, 0);
                break;
            case R.styleable.KeyboardView_shadowRadius:
                mShadowRadius = a.getFloat(attr, 0f);
                break;
            // TODO: Use Theme (android.R.styleable.Theme_backgroundDimAmount)
            case R.styleable.KeyboardView_backgroundDimAmount:
                mBackgroundDimAmount = a.getFloat(attr, 0.5f);
                break;
            case R.styleable.KeyboardView_keyLetterStyle:
                mKeyLetterStyle = Typeface.defaultFromStyle(a.getInt(attr, Typeface.NORMAL));
                break;
            case R.styleable.KeyboardView_colorScheme:
                mColorScheme = a.getInt(attr, COLOR_SCHEME_WHITE);
                break;
            }
        }

        final Resources res = getResources();

        if (previewLayout != 0) {
            mPreviewText = (TextView) LayoutInflater.from(context).inflate(previewLayout, null);
            mPreviewTextSizeLarge = (int) res.getDimension(R.dimen.key_preview_text_size_large);
        } else {
            mShowKeyPreview = false;
        }
        mDelayBeforePreview = res.getInteger(R.integer.config_delay_before_preview);
        mDelayAfterPreview = res.getInteger(R.integer.config_delay_after_preview);
        mKeyLabelHorizontalPadding = (int)res.getDimension(
                R.dimen.key_label_horizontal_alignment_padding);

        mMiniKeyboardWindow = new PopupWindow(context);
        mMiniKeyboardWindow.setBackgroundDrawable(null);
        mMiniKeyboardWindow.setAnimationStyle(R.style.MiniKeyboardAnimation);
        // Allow popup window to be drawn off the screen.
        mMiniKeyboardWindow.setClippingEnabled(false);

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setTextSize(keyTextSize);
        mPaint.setTextAlign(Align.CENTER);
        mPaint.setAlpha(255);

        mPadding = new Rect(0, 0, 0, 0);
        mKeyBackground.getPadding(mPadding);

        mSwipeThreshold = (int) (500 * res.getDisplayMetrics().density);
        // TODO: Refer frameworks/base/core/res/res/values/config.xml
        mDisambiguateSwipe = res.getBoolean(R.bool.config_swipeDisambiguation);
        mMiniKeyboardSlideAllowance = res.getDimension(R.dimen.mini_keyboard_slide_allowance);
        mConfigShowMiniKeyboardAtTouchedPoint = res.getBoolean(
                R.bool.config_show_mini_keyboard_at_touched_point);

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
        mMiniKeyboardCache.clear();
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
     * Enables or disables accessibility.
     * @param accessibilityEnabled whether or not to enable accessibility
     */
    public void setAccessibilityEnabled(boolean accessibilityEnabled) {
        mIsAccessibilityEnabled = accessibilityEnabled;

        // Propagate this change to all existing pointer trackers.
        for (PointerTracker tracker : mPointerTrackers) {
            tracker.setAccessibilityEnabled(accessibilityEnabled);
        }
    }

    /**
     * Returns whether the device has accessibility enabled.
     * @return true if the device has accessibility enabled.
     */
    @Override
    public boolean isAccessibilityEnabled() {
        return mIsAccessibilityEnabled;
    }

    /**
     * Enables or disables the key feedback preview. This is a preview that shows a magnified
     * version of the depressed key. By default the preview is enabled.
     * @param previewEnabled whether or not to enable the key feedback preview
     * @see #isKeyPreviewEnabled()
     */
    public void setKeyPreviewEnabled(boolean previewEnabled) {
        mShowKeyPreview = previewEnabled;
    }

    /**
     * Returns the enabled state of the key feedback preview
     * @return whether or not the key feedback preview is enabled
     * @see #setKeyPreviewEnabled(boolean)
     */
    public boolean isKeyPreviewEnabled() {
        return mShowKeyPreview;
    }

    public int getColorScheme() {
        return mColorScheme;
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
            mBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            mCanvas = new Canvas(mBuffer);
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
        if (mMiniKeyboardView != null) {
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
            if ((key.mLabelOption & KEY_LABEL_OPTION_ALIGN_BOTTOM) != 0) {
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
            if ((key.mLabelOption & KEY_LABEL_OPTION_ALIGN_LEFT) != 0) {
                positionX = mKeyLabelHorizontalPadding + padding.left;
                paint.setTextAlign(Align.LEFT);
                if (DEBUG_SHOW_ALIGN)
                    drawVerticalLine(canvas, positionX, rowHeight, 0xc0800080, new Paint());
            } else if ((key.mLabelOption & KEY_LABEL_OPTION_ALIGN_RIGHT) != 0) {
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
            if (key.mManualTemporaryUpperCaseHintIcon != null && isManualTemporaryUpperCase) {
                paint.setColor(mKeyTextColorDisabled);
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

        // Draw key icon.
        final Drawable icon = key.getIcon();
        if (key.mLabel == null && icon != null) {
            final int drawableWidth = icon.getIntrinsicWidth();
            final int drawableHeight = icon.getIntrinsicHeight();
            final int drawableX;
            final int drawableY = (key.mHeight + padding.top - padding.bottom - drawableHeight) / 2;
            if ((key.mLabelOption & KEY_LABEL_OPTION_ALIGN_LEFT) != 0) {
                drawableX = padding.left + mKeyLabelHorizontalPadding;
                if (DEBUG_SHOW_ALIGN)
                    drawVerticalLine(canvas, drawableX, rowHeight, 0xc0800080, new Paint());
            } else if ((key.mLabelOption & KEY_LABEL_OPTION_ALIGN_RIGHT) != 0) {
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

        // Draw hint icon.
        if (key.mHintIcon != null) {
            final int drawableWidth = keyDrawWidth;
            final int drawableHeight = key.mHeight;
            final int drawableX = 0;
            final int drawableY = HINT_ICON_VERTICAL_ADJUSTMENT_PIXEL;
            Drawable hintIcon = (isManualTemporaryUpperCase
                    && key.mManualTemporaryUpperCaseHintIcon != null)
                    ? key.mManualTemporaryUpperCaseHintIcon : key.mHintIcon;
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
            labelSize = mLabelTextSize;
            if ((keyLabelOption & KEY_LABEL_OPTION_FONT_NORMAL) != 0) {
                labelStyle = Typeface.DEFAULT;
            } else {
                labelStyle = Typeface.DEFAULT_BOLD;
            }
        } else {
            labelSize = mKeyLetterSize;
            labelStyle = mKeyLetterStyle;
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
            Rect textBounds = new Rect();
            paint.getTextBounds(KEY_LABEL_REFERENCE_CHAR, 0, 1, textBounds);
            labelCharHeight = textBounds.height();
            mTextHeightCache.put(labelSize, labelCharHeight);
        }
        return labelCharHeight;
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
        if (mShowKeyPreview) {
            mHandler.showKeyPreview(mDelayBeforePreview, keyIndex, tracker);
        } else if (mKeyboard.needSpacebarPreview(keyIndex)) {
            // Show key preview (in this case, slide language switcher) without any delay.
            showKey(keyIndex, tracker);
        }
    }

    @Override
    public void dismissKeyPreview(PointerTracker tracker) {
        if (mShowKeyPreview) {
            mHandler.cancelShowKeyPreview(tracker);
            mHandler.dismissKeyPreview(mDelayAfterPreview, tracker);
        } else if (mKeyboard.needSpacebarPreview(KeyDetector.NOT_A_KEY)) {
            // Dismiss key preview (in this case, slide language switcher) without any delay.
            mPreviewText.setVisibility(View.INVISIBLE);
        }
    }

    private void addKeyPreview(TextView keyPreview) {
        ViewGroup placer = mPreviewPlacer;
        if (placer == null) {
            final FrameLayout screenContent = (FrameLayout) getRootView().findViewById(
                    android.R.id.content);
            if (android.os.Build.VERSION.SDK_INT >= /* HONEYCOMB */11) {
                placer = screenContent;
            } else {
                // Insert LinearLayout to be able to setMargin because pre-Honeycomb FrameLayout
                // could not handle setMargin properly.
                placer = new LinearLayout(getContext());
                screenContent.addView(placer);
            }
            mPreviewPlacer = placer;
        }
        if (placer instanceof FrameLayout) {
            placer.addView(keyPreview, new FrameLayout.LayoutParams(0, 0));
        } else {
            placer.addView(keyPreview, new LinearLayout.LayoutParams(0, 0));
        }
    }

    // TODO: Introduce minimum duration for displaying key previews
    // TODO: Display up to two key previews when the user presses two keys at the same time
    private void showKey(final int keyIndex, PointerTracker tracker) {
        // If the key preview has no parent view yet, add it to the ViewGroup which can place
        // key preview absolutely in SoftInputWindow.
        if (mPreviewText.getParent() == null) {
            addKeyPreview(mPreviewText);
        }

        final Key key = tracker.getKey(keyIndex);
        // If keyIndex is invalid or IME is already closed, we must not show key preview.
        // Trying to show key preview while root window is closed causes
        // WindowManager.BadTokenException.
        if (key == null || !mInForeground)
            return;
        final int keyDrawX = key.mX + key.mVisualInsetsLeft;
        final int keyDrawWidth = key.mWidth - key.mVisualInsetsLeft - key.mVisualInsetsRight;
        // What we show as preview should match what we show on key top in onBufferDraw(). 
        if (key.mLabel != null) {
            // TODO Should take care of temporaryShiftLabel here.
            mPreviewText.setCompoundDrawables(null, null, null, null);
            mPreviewText.setText(adjustCase(tracker.getPreviewText(key)));
            if (key.mLabel.length() > 1) {
                mPreviewText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mKeyLetterSize);
                mPreviewText.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                mPreviewText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mPreviewTextSizeLarge);
                mPreviewText.setTypeface(mKeyLetterStyle);
            }
        } else {
            final Drawable previewIcon = key.getPreviewIcon();
            mPreviewText.setCompoundDrawables(null, null, null,
                   previewIcon != null ? previewIcon : key.getIcon());
            mPreviewText.setText(null);
        }
        // Set the preview background state
        mPreviewText.getBackground().setState(
                key.mPopupCharacters != null ? LONG_PRESSABLE_STATE_SET : EMPTY_STATE_SET);

        mPreviewText.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int previewWidth = Math.max(mPreviewText.getMeasuredWidth(), keyDrawWidth
                + mPreviewText.getPaddingLeft() + mPreviewText.getPaddingRight());
        final int previewHeight = mPreviewHeight;
        final ViewGroup.LayoutParams lp = mPreviewText.getLayoutParams();
        lp.width = previewWidth;
        lp.height = previewHeight;

        int previewX = keyDrawX - (previewWidth - keyDrawWidth) / 2;
        int previewY = key.mY - previewHeight + mPreviewOffset;

        mHandler.cancelAllDismissKeyPreviews();
        getLocationInWindow(mOffsetInWindow);
        previewX += mOffsetInWindow[0];
        previewY += mOffsetInWindow[1];

        // Place the key preview.
        // TODO: Adjust position of key previews which touch screen edges
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams)lp;
            mlp.setMargins(previewX, previewY, 0, 0);
        }
        // Record key preview position to display mini-keyboard later at the same position
        mKeyPreviewDisplayedY = previewY;
        mPreviewText.setVisibility(VISIBLE);
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
        mInvalidatedKeyRect.set(0, 0, key.mWidth, key.mHeight);
        mInvalidatedKeyRect.offset(key.mX + getPaddingLeft(), key.mY + getPaddingTop());
        mDirtyRect.union(mInvalidatedKeyRect);
        onBufferDraw();
        invalidate(mInvalidatedKeyRect);
    }

    private boolean openMiniKeyboardIfRequired(int keyIndex, PointerTracker tracker) {
        // Check if we have a popup layout specified first.
        if (mPopupLayout == 0) {
            return false;
        }

        Key parentKey = tracker.getKey(keyIndex);
        if (parentKey == null)
            return false;
        boolean result = onLongPress(parentKey, tracker);
        if (result) {
            dismissAllKeyPreviews();
            mMiniKeyboardTrackerId = tracker.mPointerId;
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

    private View inflateMiniKeyboardContainer(Key parentKey) {
        final View container = LayoutInflater.from(getContext()).inflate(mPopupLayout, null);
        if (container == null)
            throw new NullPointerException();

        final KeyboardView miniKeyboardView =
                (KeyboardView)container.findViewById(R.id.KeyboardView);
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
        // Override default ProximityKeyDetector.
        miniKeyboardView.mKeyDetector = new MiniKeyboardKeyDetector(mMiniKeyboardSlideAllowance);
        // Remove gesture detector on mini-keyboard
        miniKeyboardView.mGestureDetector = null;

        final Keyboard keyboard = new MiniKeyboardBuilder(this, mKeyboard.getPopupKeyboardResId(),
                parentKey).build();
        miniKeyboardView.setKeyboard(keyboard);

        container.measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.AT_MOST));

        return container;
    }

    private static boolean isOneRowKeys(List<Key> keys) {
        if (keys.size() == 0) return false;
        final int edgeFlags = keys.get(0).mEdgeFlags;
        // HACK: The first key of mini keyboard which was inflated from xml and has multiple rows,
        // does not have both top and bottom edge flags on at the same time.  On the other hand,
        // the first key of mini keyboard that was created with popupCharacters must have both top
        // and bottom edge flags on.
        // When you want to use one row mini-keyboard from xml file, make sure that the row has
        // both top and bottom edge flags set.
        return (edgeFlags & Keyboard.EDGE_TOP) != 0
                && (edgeFlags & Keyboard.EDGE_BOTTOM) != 0;
    }

    /**
     * Called when a key is long pressed. By default this will open any mini keyboard associated
     * with this key through the attributes popupLayout and popupCharacters.
     * @param parentKey the key that was long pressed
     * @return true if the long press is handled, false otherwise. Subclasses should call the
     * method on the base class if the subclass doesn't wish to handle the call.
     */
    protected boolean onLongPress(Key parentKey, PointerTracker tracker) {
        if (parentKey.mPopupCharacters == null)
            return false;

        View container = mMiniKeyboardCache.get(parentKey);
        if (container == null) {
            container = inflateMiniKeyboardContainer(parentKey);
            mMiniKeyboardCache.put(parentKey, container);
        }
        mMiniKeyboardView = (KeyboardView)container.findViewById(R.id.KeyboardView);
        final MiniKeyboard miniKeyboard = (MiniKeyboard)mMiniKeyboardView.getKeyboard();

        if (mWindowOffset == null) {
            mWindowOffset = new int[2];
            getLocationInWindow(mWindowOffset);
        }
        final int pointX = (mConfigShowMiniKeyboardAtTouchedPoint) ? tracker.getLastX()
                : parentKey.mX + parentKey.mWidth / 2;
        final int miniKeyboardX = pointX - miniKeyboard.getDefaultCoordX()
                - container.getPaddingLeft()
                + getPaddingLeft() + mWindowOffset[0];
        final int miniKeyboardY = parentKey.mY - mKeyboard.getVerticalGap()
                - (container.getMeasuredHeight() - container.getPaddingBottom())
                + getPaddingTop() + mWindowOffset[1];
        final int x = miniKeyboardX;
        final int y = mShowKeyPreview && isOneRowKeys(miniKeyboard.getKeys())
                ? mKeyPreviewDisplayedY : miniKeyboardY;

        mMiniKeyboardOriginX = x + container.getPaddingLeft() - mWindowOffset[0];
        mMiniKeyboardOriginY = y + container.getPaddingTop() - mWindowOffset[1];
        if (miniKeyboard.setShifted(
                mKeyboard == null ? false : mKeyboard.isShiftedOrShiftLocked())) {
            mMiniKeyboardView.invalidateAllKeys();
        }
        // Mini keyboard needs no pop-up key preview displayed.
        mMiniKeyboardView.setKeyPreviewEnabled(false);
        mMiniKeyboardWindow.setContentView(container);
        mMiniKeyboardWindow.setWidth(container.getMeasuredWidth());
        mMiniKeyboardWindow.setHeight(container.getMeasuredHeight());
        mMiniKeyboardWindow.showAtLocation(this, Gravity.NO_GRAVITY, x, y);

        // Inject down event on the key to mini keyboard.
        final long eventTime = SystemClock.uptimeMillis();
        mMiniKeyboardDisplayedTime = eventTime;
        final MotionEvent downEvent = generateMiniKeyboardMotionEvent(MotionEvent.ACTION_DOWN,
                pointX, parentKey.mY + parentKey.mHeight / 2, eventTime);
        mMiniKeyboardView.onTouchEvent(downEvent);
        downEvent.recycle();

        invalidateAllKeys();
        return true;
    }

    private MotionEvent generateMiniKeyboardMotionEvent(int action, int x, int y, long eventTime) {
        return MotionEvent.obtain(mMiniKeyboardDisplayedTime, eventTime, action,
                    x - mMiniKeyboardOriginX, y - mMiniKeyboardOriginY, 0);
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
        if (mMiniKeyboardView != null) {
            return mMiniKeyboardView.isInSlidingKeyInput();
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
        if ((!mHasDistinctMultitouch || mIsAccessibilityEnabled)
                && pointerCount > 1 && oldPointerCount > 1) {
            return true;
        }

        // Track the last few movements to look for spurious swipes.
        mSwipeTracker.addMovement(me);

        // Gesture detector must be enabled only when mini-keyboard is not on the screen and
        // accessibility is not enabled.
        // TODO: Reconcile gesture detection and accessibility features.
        if (mMiniKeyboardView == null && !mIsAccessibilityEnabled
                && mGestureDetector != null && mGestureDetector.onTouchEvent(me)) {
            dismissAllKeyPreviews();
            mHandler.cancelKeyTimers();
            return true;
        }

        final long eventTime = me.getEventTime();
        final int index = me.getActionIndex();
        final int id = me.getPointerId(index);
        final int x = (int)me.getX(index);
        final int y = (int)me.getY(index);

        // Needs to be called after the gesture detector gets a turn, as it may have
        // displayed the mini keyboard
        if (mMiniKeyboardView != null) {
            final int miniKeyboardPointerIndex = me.findPointerIndex(mMiniKeyboardTrackerId);
            if (miniKeyboardPointerIndex >= 0 && miniKeyboardPointerIndex < pointerCount) {
                final int miniKeyboardX = (int)me.getX(miniKeyboardPointerIndex);
                final int miniKeyboardY = (int)me.getY(miniKeyboardPointerIndex);
                MotionEvent translated = generateMiniKeyboardMotionEvent(action,
                        miniKeyboardX, miniKeyboardY, eventTime);
                mMiniKeyboardView.onTouchEvent(translated);
                translated.recycle();
            }
            return true;
        }

        if (mHandler.isInKeyRepeat()) {
            // It will keep being in the key repeating mode while the key is being pressed.
            if (action == MotionEvent.ACTION_MOVE) {
                return true;
            }
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
        if (!mHasDistinctMultitouch || mIsAccessibilityEnabled) {
            // Use only main (id=0) pointer tracker.
            PointerTracker tracker = getPointerTracker(0);
            if (pointerCount == 1 && oldPointerCount == 2) {
                // Multi-touch to single touch transition.
                // Send a down event for the latest pointer.
                tracker.onDownEvent(x, y, eventTime, null);
            } else if (pointerCount == 2 && oldPointerCount == 1) {
                // Single-touch to multi-touch transition.
                // Send an up event for the last pointer.
                tracker.onUpEvent(tracker.getLastX(), tracker.getLastY(), eventTime, null);
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
        mMiniKeyboardCache.clear();
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

    private void dismissMiniKeyboard() {
        if (mMiniKeyboardWindow.isShowing()) {
            mMiniKeyboardWindow.dismiss();
            mMiniKeyboardView = null;
            mMiniKeyboardOriginX = 0;
            mMiniKeyboardOriginY = 0;
            invalidateAllKeys();
        }
    }

    public boolean handleBack() {
        if (mMiniKeyboardWindow.isShowing()) {
            dismissMiniKeyboard();
            return true;
        }
        return false;
    }
}
