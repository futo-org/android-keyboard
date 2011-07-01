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
import android.view.accessibility.AccessibilityEvent;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.android.inputmethod.accessibility.AccessibilityUtils;
import com.android.inputmethod.accessibility.AccessibleKeyboardViewProxy;
import com.android.inputmethod.compat.FrameLayoutCompatUtils;
import com.android.inputmethod.keyboard.internal.MiniKeyboardBuilder;
import com.android.inputmethod.keyboard.internal.PointerTrackerQueue;
import com.android.inputmethod.keyboard.internal.SwipeTracker;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.StaticInnerHandlerWrapper;

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
 * @attr ref R.styleable#KeyboardView_keyLargeLetterRatio
 * @attr ref R.styleable#KeyboardView_keyLabelRatio
 * @attr ref R.styleable#KeyboardView_keyHintLetterRatio
 * @attr ref R.styleable#KeyboardView_keyUppercaseLetterRatio
 * @attr ref R.styleable#KeyboardView_keyHintLabelRatio
 * @attr ref R.styleable#KeyboardView_keyLabelHorizontalPadding
 * @attr ref R.styleable#KeyboardView_keyHintLetterPadding
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
 * @attr ref R.styleable#KeyboardView_verticalCorrection
 * @attr ref R.styleable#KeyboardView_popupLayout
 * @attr ref R.styleable#KeyboardView_shadowColor
 * @attr ref R.styleable#KeyboardView_shadowRadius
 */
public class KeyboardView extends View implements PointerTracker.UIProxy {
    private static final String TAG = KeyboardView.class.getSimpleName();
    private static final boolean DEBUG_KEYBOARD_GRID = false;

    private static final boolean ENABLE_CAPSLOCK_BY_LONGPRESS = true;
    private static final boolean ENABLE_CAPSLOCK_BY_DOUBLETAP = true;

    // Timing constants
    private final int mKeyRepeatInterval;

    // Miscellaneous constants
    private static final int[] LONG_PRESSABLE_STATE_SET = { android.R.attr.state_long_pressable };

    // XML attribute
    private final float mBackgroundDimAmount;
    private final float mKeyHysteresisDistance;
    private final float mVerticalCorrection;
    private final int mPopupLayout;

    // HORIZONTAL ELLIPSIS "...", character for popup hint.
    private static final String POPUP_HINT_CHAR = "\u2026";

    // Main keyboard
    private Keyboard mKeyboard;
    private final KeyDrawParams mKeyDrawParams;

    // Key preview
    private final KeyPreviewDrawParams mKeyPreviewDrawParams;
    private final TextView mPreviewText;
    private boolean mShowKeyPreviewPopup = true;
    private final int mDelayBeforePreview;
    private int mDelayAfterPreview;
    private ViewGroup mPreviewPlacer;

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
    // This map caches key label text height in pixel as value and key label text size as map key.
    private static final HashMap<Integer, Float> sTextHeightCache =
            new HashMap<Integer, Float>();
    // This map caches key label text width in pixel as value and key label text size as map key.
    private static final HashMap<Integer, Float> sTextWidthCache =
            new HashMap<Integer, Float>();
    private static final String KEY_LABEL_REFERENCE_CHAR = "M";

    private static final int MEASURESPEC_UNSPECIFIED = MeasureSpec.makeMeasureSpec(
            0, MeasureSpec.UNSPECIFIED);

    private final UIHandler mHandler = new UIHandler(this);

    public static class UIHandler extends StaticInnerHandlerWrapper<KeyboardView> {
        private static final int MSG_SHOW_KEY_PREVIEW = 1;
        private static final int MSG_DISMISS_KEY_PREVIEW = 2;
        private static final int MSG_REPEAT_KEY = 3;
        private static final int MSG_LONGPRESS_KEY = 4;
        private static final int MSG_LONGPRESS_SHIFT_KEY = 5;
        private static final int MSG_IGNORE_DOUBLE_TAP = 6;

        private boolean mInKeyRepeat;

        public UIHandler(KeyboardView outerInstance) {
            super(outerInstance);
        }

        @Override
        public void handleMessage(Message msg) {
            final KeyboardView keyboardView = getOuterInstance();
            final PointerTracker tracker = (PointerTracker) msg.obj;
            switch (msg.what) {
            case MSG_SHOW_KEY_PREVIEW:
                keyboardView.showKey(msg.arg1, tracker);
                break;
            case MSG_DISMISS_KEY_PREVIEW:
                keyboardView.mPreviewText.setVisibility(View.INVISIBLE);
                break;
            case MSG_REPEAT_KEY:
                tracker.onRepeatKey(msg.arg1);
                startKeyRepeatTimer(keyboardView.mKeyRepeatInterval, msg.arg1, tracker);
                break;
            case MSG_LONGPRESS_KEY:
                keyboardView.openMiniKeyboardIfRequired(msg.arg1, tracker);
                break;
            case MSG_LONGPRESS_SHIFT_KEY:
                keyboardView.onLongPressShiftKey(tracker);
                break;
            }
        }

        public void showKeyPreview(long delay, int keyIndex, PointerTracker tracker) {
            final KeyboardView keyboardView = getOuterInstance();
            removeMessages(MSG_SHOW_KEY_PREVIEW);
            if (keyboardView.mPreviewText.getVisibility() == VISIBLE || delay == 0) {
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

    private static class KeyDrawParams {
        // XML attributes
        public final int mKeyTextColor;
        public final int mKeyTextInactivatedColor;
        public final Typeface mKeyTextStyle;
        public final float mKeyLabelHorizontalPadding;
        public final float mKeyHintLetterPadding;
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

        public final Rect mPadding = new Rect();
        public int mKeyLetterSize;
        public int mKeyLargeLetterSize;
        public int mKeyLabelSize;
        public int mKeyHintLetterSize;
        public int mKeyUppercaseLetterSize;
        public int mKeyHintLabelSize;

        public KeyDrawParams(TypedArray a) {
            mKeyBackground = a.getDrawable(R.styleable.KeyboardView_keyBackground);
            mKeyLetterRatio = getRatio(a, R.styleable.KeyboardView_keyLetterRatio);
            mKeyLargeLetterRatio = getRatio(a, R.styleable.KeyboardView_keyLargeLetterRatio);
            mKeyLabelRatio = getRatio(a, R.styleable.KeyboardView_keyLabelRatio);
            mKeyHintLetterRatio = getRatio(a, R.styleable.KeyboardView_keyHintLetterRatio);
            mKeyUppercaseLetterRatio = getRatio(a,
                    R.styleable.KeyboardView_keyUppercaseLetterRatio);
            mKeyHintLabelRatio = getRatio(a, R.styleable.KeyboardView_keyHintLabelRatio);
            mKeyLabelHorizontalPadding = a.getDimension(
                    R.styleable.KeyboardView_keyLabelHorizontalPadding, 0);
            mKeyHintLetterPadding = a.getDimension(
                    R.styleable.KeyboardView_keyHintLetterPadding, 0);
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
            mKeyLetterSize = (int)(keyHeight * mKeyLetterRatio);
            mKeyLargeLetterSize = (int)(keyHeight * mKeyLargeLetterRatio);
            mKeyLabelSize = (int)(keyHeight * mKeyLabelRatio);
            mKeyHintLetterSize = (int)(keyHeight * mKeyHintLetterRatio);
            mKeyUppercaseLetterSize = (int)(keyHeight * mKeyUppercaseLetterRatio);
            mKeyHintLabelSize = (int)(keyHeight * mKeyHintLabelRatio);
        }
    }

    private static class KeyPreviewDrawParams {
        // XML attributes.
        public final Drawable mPreviewBackground;
        public final Drawable mPreviewLeftBackground;
        public final Drawable mPreviewRightBackground;
        public final Drawable mPreviewSpacebarBackground;
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
            mPreviewSpacebarBackground = a.getDrawable(
                    R.styleable.KeyboardView_keyPreviewSpacebarBackground);
            setAlpha(mPreviewBackground, PREVIEW_ALPHA);
            setAlpha(mPreviewLeftBackground, PREVIEW_ALPHA);
            setAlpha(mPreviewRightBackground, PREVIEW_ALPHA);
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
        mKeyHysteresisDistance = a.getDimensionPixelOffset(
                R.styleable.KeyboardView_keyHysteresisDistance, 0);
        mVerticalCorrection = a.getDimensionPixelOffset(
                R.styleable.KeyboardView_verticalCorrection, 0);
        final int previewLayout = a.getResourceId(R.styleable.KeyboardView_keyPreviewLayout, 0);
        if (previewLayout != 0) {
            mPreviewText = (TextView) LayoutInflater.from(context).inflate(previewLayout, null);
        } else {
            mPreviewText = null;
            mShowKeyPreviewPopup = false;
        }
        mPopupLayout = a.getResourceId(R.styleable.KeyboardView_popupLayout, 0);
        // TODO: Use Theme (android.R.styleable.Theme_backgroundDimAmount)
        mBackgroundDimAmount = a.getFloat(R.styleable.KeyboardView_backgroundDimAmount, 0.5f);
        a.recycle();

        final Resources res = getResources();

        mDelayBeforePreview = res.getInteger(R.integer.config_delay_before_preview);
        mDelayAfterPreview = res.getInteger(R.integer.config_delay_after_preview);

        mPaint.setAntiAlias(true);
        mPaint.setTextAlign(Align.CENTER);
        mPaint.setAlpha(255);

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

        final boolean isManualTemporaryUpperCase = mKeyboard.isManualTemporaryUpperCase();
        final KeyDrawParams params = mKeyDrawParams;
        if (mInvalidatedKey != null && mInvalidatedKeyRect.contains(mDirtyRect)) {
            // Draw a single key.
            final int keyDrawX = mInvalidatedKey.mX + mInvalidatedKey.mVisualInsetsLeft
                    + getPaddingLeft();
            final int keyDrawY = mInvalidatedKey.mY + getPaddingTop();
            canvas.translate(keyDrawX, keyDrawY);
            onBufferDrawKey(mInvalidatedKey, canvas, mPaint, params, isManualTemporaryUpperCase);
            canvas.translate(-keyDrawX, -keyDrawY);
        } else {
            // Draw all keys.
            for (final Key key : mKeyboard.getKeys()) {
                final int keyDrawX = key.mX + key.mVisualInsetsLeft + getPaddingLeft();
                final int keyDrawY = key.mY + getPaddingTop();
                canvas.translate(keyDrawX, keyDrawY);
                onBufferDrawKey(key, canvas, mPaint, params, isManualTemporaryUpperCase);
                canvas.translate(-keyDrawX, -keyDrawY);
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

    private static void onBufferDrawKey(final Key key, final Canvas canvas, Paint paint,
            KeyDrawParams params, boolean isManualTemporaryUpperCase) {
        final boolean debugShowAlign = LatinImeLogger.sVISUALDEBUG;
        // Draw key background.
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

        // Draw key top visuals.
        final int keyWidth = key.mWidth;
        final int keyHeight = key.mHeight;
        final float centerX = keyWidth * 0.5f;
        final float centerY = keyHeight * 0.5f;

        if (debugShowAlign) {
            drawRectangle(canvas, 0, 0, keyWidth, keyHeight, 0x800000c0, new Paint());
        }

        // Draw key label.
        float positionX = centerX;
        if (key.mLabel != null) {
            // Switch the character to uppercase if shift is pressed
            final CharSequence label = key.getCaseAdjustedLabel();
            // For characters, use large font. For labels like "Done", use smaller font.
            paint.setTypeface(key.selectTypeface(params.mKeyTextStyle));
            final int labelSize = key.selectTextSize(params.mKeyLetterSize,
                    params.mKeyLargeLetterSize, params.mKeyLabelSize, params.mKeyHintLabelSize);
            paint.setTextSize(labelSize);
            final float labelCharHeight = getCharHeight(paint);
            final float labelCharWidth = getCharWidth(paint);

            // Vertical label text alignment.
            final float baseline = centerY + labelCharHeight / 2;

            // Horizontal label text alignment
            if ((key.mLabelOption & Key.LABEL_OPTION_ALIGN_LEFT) != 0) {
                positionX = (int)params.mKeyLabelHorizontalPadding;
                paint.setTextAlign(Align.LEFT);
            } else if ((key.mLabelOption & Key.LABEL_OPTION_ALIGN_RIGHT) != 0) {
                positionX = keyWidth - (int)params.mKeyLabelHorizontalPadding;
                paint.setTextAlign(Align.RIGHT);
            } else if ((key.mLabelOption & Key.LABEL_OPTION_ALIGN_LEFT_OF_CENTER) != 0) {
                // TODO: Parameterise this?
                positionX = centerX - labelCharWidth * 7 / 4;
                paint.setTextAlign(Align.LEFT);
            } else {
                positionX = centerX;
                paint.setTextAlign(Align.CENTER);
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
            // Turn off drop shadow
            paint.setShadowLayer(0, 0, 0, 0);

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
            final float hintCharWidth = getCharWidth(paint);
            final float hintX, hintY;
            if (key.hasHintLabel()) {
                // TODO: Generalize the following calculations.
                hintX = positionX + hintCharWidth * 2;
                hintY = centerY + getCharHeight(paint) / 2;
                paint.setTextAlign(Align.LEFT);
            } else if (key.hasUppercaseLetter()) {
                hintX = keyWidth - params.mKeyUppercaseLetterPadding - hintCharWidth / 2;
                hintY = -paint.ascent() + params.mKeyUppercaseLetterPadding;
                paint.setTextAlign(Align.CENTER);
            } else { // key.hasHintLetter()
                hintX = keyWidth - params.mKeyHintLetterPadding - hintCharWidth / 2;
                hintY = -paint.ascent() + params.mKeyHintLetterPadding;
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
        final Drawable icon = key.getIcon();
        if (key.mLabel == null && icon != null) {
            final int iconWidth = icon.getIntrinsicWidth();
            final int iconHeight = icon.getIntrinsicHeight();
            final int iconX, alignX;
            final int iconY = (keyHeight - iconHeight) / 2;
            if ((key.mLabelOption & Key.LABEL_OPTION_ALIGN_LEFT) != 0) {
                iconX = (int)params.mKeyLabelHorizontalPadding;
                alignX = iconX;
            } else if ((key.mLabelOption & Key.LABEL_OPTION_ALIGN_RIGHT) != 0) {
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
        if (key.hasPopupHint()) {
            paint.setTextSize(params.mKeyHintLetterSize);
            paint.setColor(params.mKeyHintLabelColor);
            paint.setTextAlign(Align.CENTER);
            final float hintX = keyWidth - params.mKeyHintLetterPadding - getCharWidth(paint) / 2;
            final float hintY = keyHeight - params.mKeyHintLetterPadding;
            canvas.drawText(POPUP_HINT_CHAR, hintX, hintY, paint);

            if (debugShowAlign) {
                final Paint line = new Paint();
                drawHorizontalLine(canvas, (int)hintY, keyWidth, 0xc0808000, line);
                drawVerticalLine(canvas, (int)hintX, keyHeight, 0xc0808000, line);
            }
        }
    }

    // This method is currently being used only by MiniKeyboardBuilder
    public int getDefaultLabelSizeAndSetPaint(Paint paint) {
        // For characters, use large font. For labels like "Done", use small font.
        final int labelSize = mKeyDrawParams.mKeyLabelSize;
        paint.setTextSize(labelSize);
        paint.setTypeface(mKeyDrawParams.mKeyTextStyle);
        return labelSize;
    }

    private static final Rect sTextBounds = new Rect();

    private static float getCharHeight(Paint paint) {
        final int labelSize = (int)paint.getTextSize();
        final Float cachedValue = sTextHeightCache.get(labelSize);
        if (cachedValue != null)
            return cachedValue;

        paint.getTextBounds(KEY_LABEL_REFERENCE_CHAR, 0, 1, sTextBounds);
        final float height = sTextBounds.height();
        sTextHeightCache.put(labelSize, height);
        return height;
    }

    private static float getCharWidth(Paint paint) {
        final int labelSize = (int)paint.getTextSize();
        final Typeface face = paint.getTypeface();
        final Integer key;
        if (face == Typeface.DEFAULT) {
            key = labelSize;
        } else if (face == Typeface.DEFAULT_BOLD) {
            key = labelSize + 1000;
        } else if (face == Typeface.MONOSPACE) {
            key = labelSize + 2000;
        } else {
            key = labelSize;
        }

        final Float cached = sTextWidthCache.get(key);
        if (cached != null)
            return cached;

        paint.getTextBounds(KEY_LABEL_REFERENCE_CHAR, 0, 1, sTextBounds);
        final float width = sTextBounds.width();
        sTextWidthCache.put(key, width);
        return width;
    }

    private static void drawIcon(Canvas canvas, Drawable icon, int x, int y, int width,
            int height) {
        canvas.translate(x, y);
        icon.setBounds(0, 0, width, height);
        icon.draw(canvas);
        canvas.translate(-x, -y);
    }

    private static void drawHorizontalLine(Canvas canvas, float y, float w, int color, Paint paint) {
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

    // TODO: clean up this method.
    private void dismissAllKeyPreviews() {
        for (PointerTracker tracker : mPointerTrackers) {
            tracker.setReleasedKeyGraphics();
            dismissKeyPreview(tracker);
        }
    }

    public void cancelAllMessage() {
        mHandler.cancelAllMessages();
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
        if (key == null)
            return;

        mHandler.cancelAllDismissKeyPreviews();
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
            previewText.setText(key.getCaseAdjustedLabel());
        } else {
            final Drawable previewIcon = key.getPreviewIcon();
            previewText.setCompoundDrawables(null, null, null,
                   previewIcon != null ? previewIcon : key.getIcon());
            previewText.setText(null);
        }
        if (key.mCode == Keyboard.CODE_SPACE) {
            previewText.setBackgroundDrawable(params.mPreviewSpacebarBackground);
        } else {
            previewText.setBackgroundDrawable(params.mPreviewBackground);
        }

        previewText.measure(MEASURESPEC_UNSPECIFIED, MEASURESPEC_UNSPECIFIED);
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
                key.mPopupCharacters != null ? LONG_PRESSABLE_STATE_SET : EMPTY_STATE_SET);
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
                MEASURESPEC_UNSPECIFIED);

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
        popupPanel.showPanel(this, parentKey, tracker, mPopupWindow);

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
