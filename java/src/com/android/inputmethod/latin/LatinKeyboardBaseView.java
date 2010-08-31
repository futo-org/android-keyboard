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

package com.android.inputmethod.latin;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Region.Op;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.Keyboard.Key;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup.LayoutParams;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A view that renders a virtual {@link LatinKeyboard}. It handles rendering of keys and
 * detecting key presses and touch movements.
 *
 * @attr ref R.styleable#LatinKeyboardBaseView_keyBackground
 * @attr ref R.styleable#LatinKeyboardBaseView_keyPreviewLayout
 * @attr ref R.styleable#LatinKeyboardBaseView_keyPreviewOffset
 * @attr ref R.styleable#LatinKeyboardBaseView_labelTextSize
 * @attr ref R.styleable#LatinKeyboardBaseView_keyTextSize
 * @attr ref R.styleable#LatinKeyboardBaseView_keyTextColor
 * @attr ref R.styleable#LatinKeyboardBaseView_verticalCorrection
 * @attr ref R.styleable#LatinKeyboardBaseView_popupLayout
 */
public class LatinKeyboardBaseView extends View implements View.OnClickListener {
    private static final boolean DEBUG = false;

    public static final int NOT_A_TOUCH_COORDINATE = -1;

    public interface OnKeyboardActionListener {

        /**
         * Called when the user presses a key. This is sent before the
         * {@link #onKey} is called. For keys that repeat, this is only
         * called once.
         *
         * @param primaryCode
         *            the unicode of the key being pressed. If the touch is
         *            not on a valid key, the value will be zero.
         */
        void onPress(int primaryCode);

        /**
         * Called when the user releases a key. This is sent after the
         * {@link #onKey} is called. For keys that repeat, this is only
         * called once.
         *
         * @param primaryCode
         *            the code of the key that was released
         */
        void onRelease(int primaryCode);

        /**
         * Send a key press to the listener.
         *
         * @param primaryCode
         *            this is the key that was pressed
         * @param keyCodes
         *            the codes for all the possible alternative keys with
         *            the primary code being the first. If the primary key
         *            code is a single character such as an alphabet or
         *            number or symbol, the alternatives will include other
         *            characters that may be on the same key or adjacent
         *            keys. These codes are useful to correct for
         *            accidental presses of a key adjacent to the intended
         *            key.
         * @param x
         *            x-coordinate pixel of touched event. If onKey is not called by onTouchEvent,
         *            the value should be NOT_A_TOUCH_COORDINATE.
         * @param y
         *            y-coordinate pixel of touched event. If onKey is not called by onTouchEvent,
         *            the value should be NOT_A_TOUCH_COORDINATE.
         */
        void onKey(int primaryCode, int[] keyCodes, int x, int y);

        /**
         * Sends a sequence of characters to the listener.
         *
         * @param text
         *            the sequence of characters to be displayed.
         */
        void onText(CharSequence text);

        /**
         * Called when the user quickly moves the finger from right to
         * left.
         */
        void swipeLeft();

        /**
         * Called when the user quickly moves the finger from left to
         * right.
         */
        void swipeRight();

        /**
         * Called when the user quickly moves the finger from up to down.
         */
        void swipeDown();

        /**
         * Called when the user quickly moves the finger from down to up.
         */
        void swipeUp();
    }

    // Timing constants
    private static final int DELAY_BEFORE_PREVIEW = 0;
    private static final int DELAY_AFTER_PREVIEW = 70;
    private static final int REPEAT_INTERVAL = 50; // ~20 keys per second
    private static final int REPEAT_START_DELAY = 400;
    private static final int LONGPRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();
    private static final int MULTITAP_INTERVAL = 800; // milliseconds
    private static final int KEY_DEBOUNCE_TIME = 70;

    // Miscellaneous constants
    static final int NOT_A_KEY = -1;
    private static final int[] KEY_DELETE = { Keyboard.KEYCODE_DELETE };
    private static final int[] LONG_PRESSABLE_STATE_SET = { android.R.attr.state_long_pressable };

    // XML attribute
    private int mKeyTextSize;
    private int mKeyTextColor;
    private Typeface mKeyTextStyle = Typeface.DEFAULT;
    private int mLabelTextSize;
    private int mSymbolColorScheme = 0;
    private int mShadowColor;
    private float mShadowRadius;
    private Drawable mKeyBackground;
    private float mBackgroundDimAmount;
    private int mVerticalCorrection;
    private int mPreviewOffset;
    private int mPreviewHeight;
    private int mPopupLayout;

    // Main keyboard
    private Keyboard mKeyboard;
    private Key[] mKeys;

    // Key preview popup
    private final static boolean PREVIEW_CENTERED = false;
    private TextView mPreviewText;
    private PopupWindow mPreviewPopup;
    private int mPreviewTextSizeLarge;
    private int[] mOffsetInWindow;
    private int mOldPreviewKeyIndex = NOT_A_KEY;
    private boolean mShowPreview = true;
    private boolean mShowTouchPoints = true;
    private int mPopupPreviewX;
    private int mPopupPreviewY;
    private int mWindowY;

    // Popup mini keyboard
    private PopupWindow mPopupKeyboard;
    private View mMiniKeyboardContainer;
    private LatinKeyboardBaseView mMiniKeyboard;
    private boolean mMiniKeyboardOnScreen;
    private View mPopupParent;
    private int mMiniKeyboardOffsetX;
    private int mMiniKeyboardOffsetY;
    private Map<Key,View> mMiniKeyboardCache;
    private int[] mWindowOffset;

    /** Listener for {@link OnKeyboardActionListener}. */
    private OnKeyboardActionListener mKeyboardActionListener;

    private final KeyDebouncer mDebouncer;
    private final float mDebounceHysteresis;

    private final ProximityKeyDetector mProximityKeyDetector = new ProximityKeyDetector();

    // Variables for dealing with multiple pointers
    private int mOldPointerCount = 1;
    private int mOldPointerX;
    private int mOldPointerY;

    // Swipe gesture detector
    private final GestureDetector mGestureDetector;
    private final SwipeTracker mSwipeTracker = new SwipeTracker();
    private final int mSwipeThreshold;
    private final boolean mDisambiguateSwipe;

    // Drawing
    /** Whether the keyboard bitmap needs to be redrawn before it's blitted. **/
    private boolean mDrawPending;
    /** The dirty region in the keyboard bitmap */
    private final Rect mDirtyRect = new Rect();
    /** The keyboard bitmap for faster updates */
    private Bitmap mBuffer;
    /** Notes if the keyboard just changed, so that we could possibly reallocate the mBuffer. */
    private boolean mKeyboardChanged;
    private Key mInvalidatedKey;
    /** The canvas for the above mutable keyboard bitmap */
    private Canvas mCanvas;
    private final Paint mPaint;
    private final Rect mPadding;
    private final Rect mClipRegion = new Rect(0, 0, 0, 0);

    private final UIHandler mHandler = new UIHandler();

    class UIHandler extends Handler {
        private static final int MSG_POPUP_PREVIEW = 1;
        private static final int MSG_DISMISS_PREVIEW = 2;
        private static final int MSG_REPEAT_KEY = 3;
        private static final int MSG_LONGPRESS_KEY = 4;

        private boolean mInKeyRepeat;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_POPUP_PREVIEW:
                    showKey(msg.arg1, (KeyDebouncer)msg.obj);
                    break;
                case MSG_DISMISS_PREVIEW:
                    mPreviewText.setVisibility(INVISIBLE);
                    break;
                case MSG_REPEAT_KEY: {
                    final KeyDebouncer debouncer = (KeyDebouncer)msg.obj;
                    debouncer.repeatKey(msg.arg1);
                    startKeyRepeatTimer(REPEAT_INTERVAL, msg.arg1, debouncer);
                    break;
                }
                case MSG_LONGPRESS_KEY:
                    openPopupIfRequired(msg.arg1);
                    break;
            }
        }

        public void popupPreview(long delay, int keyIndex, KeyDebouncer debouncer) {
            removeMessages(MSG_POPUP_PREVIEW);
            if (mPreviewPopup.isShowing() && mPreviewText.getVisibility() == VISIBLE) {
                // Show right away, if it's already visible and finger is moving around
                showKey(keyIndex, debouncer);
            } else {
                sendMessageDelayed(obtainMessage(MSG_POPUP_PREVIEW, keyIndex, 0, debouncer),
                        delay);
            }
        }

        public void cancelPopupPreview() {
            removeMessages(MSG_POPUP_PREVIEW);
        }

        public void dismissPreview(long delay) {
            if (mPreviewPopup.isShowing()) {
                sendMessageDelayed(obtainMessage(MSG_DISMISS_PREVIEW), delay);
            }
        }

        public void cancelDismissPreview() {
            removeMessages(MSG_DISMISS_PREVIEW);
        }

        public void startKeyRepeatTimer(long delay, int keyIndex, KeyDebouncer debouncer) {
            mInKeyRepeat = true;
            sendMessageDelayed(obtainMessage(MSG_REPEAT_KEY, keyIndex, 0, debouncer), delay);
        }

        public void cancelKeyRepeatTimer() {
            mInKeyRepeat = false;
            removeMessages(MSG_REPEAT_KEY);
        }

        public boolean isInKeyRepeat() {
            return mInKeyRepeat;
        }

        public void startLongPressTimer(int keyIndex, long delay) {
            removeMessages(MSG_LONGPRESS_KEY);
            sendMessageDelayed(obtainMessage(MSG_LONGPRESS_KEY, keyIndex, 0), delay);
        }

        public void cancelLongPressTimer() {
            removeMessages(MSG_LONGPRESS_KEY);
        }

        public void cancelKeyTimers() {
            cancelKeyRepeatTimer();
            cancelLongPressTimer();
        }

        public void cancelAllMessages() {
            cancelKeyTimers();
            cancelPopupPreview();
            cancelDismissPreview();
        }
    };

    // TODO:nn Rename this class to PointerTracker when this becomes a top-level class.
    public static class KeyDebouncer {
        public interface UIProxy {
            public void invalidateKey(Key key);
            public void showPreview(int keyIndex, KeyDebouncer debouncer);
            // TODO: These methods might be temporary.
            public void dismissPopupKeyboard();
            public boolean isMiniKeyboardOnScreen();
        }

        private final UIProxy mProxy;
        private final UIHandler mHandler;
        private final ProximityKeyDetector mKeyDetector;
        private OnKeyboardActionListener mListener;

        private Key[] mKeys;
        private int mKeyDebounceThresholdSquared = -1;

        private int mCurrentKey = NOT_A_KEY;
        private int mStartX;
        private int mStartY;

        // for move de-bouncing
        private int mLastCodeX;
        private int mLastCodeY;
        private int mLastX;
        private int mLastY;

        // for time de-bouncing
        private int mLastKey;
        private long mLastKeyTime;
        private long mLastMoveTime;
        private long mCurrentKeyTime;

        // For multi-tap
        private int mLastSentIndex;
        private int mTapCount;
        private long mLastTapTime;
        private boolean mInMultiTap;
        private final StringBuilder mPreviewLabel = new StringBuilder(1);

        // pressed key
        private int mPreviousKey;

        public KeyDebouncer(UIHandler handler, ProximityKeyDetector keyDetector, UIProxy proxy) {
            if (proxy == null || handler == null || keyDetector == null)
                throw new NullPointerException();
            mProxy = proxy;
            mHandler = handler;
            mKeyDetector = keyDetector;
            resetMultiTap();
        }

        public void setOnKeyboardActionListener(OnKeyboardActionListener listener) {
            mListener = listener;
        }

        public void setKeyboard(Key[] keys, float hysteresisPixel) {
            if (keys == null || hysteresisPixel < 1.0f)
                throw new IllegalArgumentException();
            mKeys = keys;
            mKeyDebounceThresholdSquared = (int)(hysteresisPixel * hysteresisPixel);
        }

        public Key getKey(int keyIndex) {
            return (keyIndex >= 0 && keyIndex < mKeys.length) ? mKeys[keyIndex] : null;
        }

        public void updateKey(int keyIndex) {
            int oldKeyIndex = mPreviousKey;
            mPreviousKey = keyIndex;
            if (keyIndex != oldKeyIndex) {
                if (oldKeyIndex != NOT_A_KEY && oldKeyIndex < mKeys.length) {
                    // if new key index is not a key, old key was just released inside of the key.
                    final boolean inside = (keyIndex == NOT_A_KEY);
                    mKeys[oldKeyIndex].onReleased(inside);
                    mProxy.invalidateKey(mKeys[oldKeyIndex]);
                }
                if (keyIndex != NOT_A_KEY && keyIndex < mKeys.length) {
                    mKeys[keyIndex].onPressed();
                    mProxy.invalidateKey(mKeys[keyIndex]);
                }
            }
        }

        public void onModifiedTouchEvent(int action, int touchX, int touchY, long eventTime) {
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    onDownEvent(touchX, touchY, eventTime);
                    break;
                case MotionEvent.ACTION_MOVE:
                    onMoveEvent(touchX, touchY, eventTime);
                    break;
                case MotionEvent.ACTION_UP:
                    onUpEvent(touchX, touchY, eventTime);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    onCancelEvent(touchX, touchY, eventTime);
                    break;
            }
        }

        public void onDownEvent(int touchX, int touchY, long eventTime) {
            int keyIndex = mKeyDetector.getKeyIndexAndNearbyCodes(touchX, touchY, null);
            mCurrentKey = keyIndex;
            mStartX = touchX;
            mStartY = touchY;
            startMoveDebouncing(touchX, touchY);
            startTimeDebouncing(eventTime);
            checkMultiTap(eventTime, keyIndex);
            if (mListener != null)
                mListener.onPress(keyIndex != NOT_A_KEY ? mKeys[keyIndex].codes[0] : 0);
            if (keyIndex >= 0 && mKeys[keyIndex].repeatable) {
                repeatKey(keyIndex);
                mHandler.startKeyRepeatTimer(REPEAT_START_DELAY, keyIndex, this);
            }
            if (keyIndex != NOT_A_KEY) {
                mHandler.startLongPressTimer(keyIndex, LONGPRESS_TIMEOUT);
            }
            showKeyPreviewAndUpdateKey(keyIndex);
            updateMoveDebouncing(touchX, touchY);
        }

        public void onMoveEvent(int touchX, int touchY, long eventTime) {
            int keyIndex = mKeyDetector.getKeyIndexAndNearbyCodes(touchX, touchY, null);
            if (keyIndex != NOT_A_KEY) {
                if (mCurrentKey == NOT_A_KEY) {
                    updateTimeDebouncing(eventTime);
                    mCurrentKey = keyIndex;
                    mHandler.startLongPressTimer(keyIndex, LONGPRESS_TIMEOUT);
                } else if (isMinorMoveBounce(touchX, touchY, keyIndex, mCurrentKey)) {
                    updateTimeDebouncing(eventTime);
                } else {
                    resetMultiTap();
                    resetTimeDebouncing(eventTime, mCurrentKey);
                    resetMoveDebouncing();
                    mCurrentKey = keyIndex;
                    mHandler.startLongPressTimer(keyIndex, LONGPRESS_TIMEOUT);
                }
            } else {
                mHandler.cancelLongPressTimer();
            }
            /*
             * While time debouncing is in effect, mCurrentKey holds the new key and mDebouncer
             * holds the last key.  At ACTION_UP event if time debouncing will be in effect
             * eventually, the last key should be sent as the result.  In such case mCurrentKey
             * should not be showed as popup preview.
             */
            showKeyPreviewAndUpdateKey(isMinorTimeBounce() ? mLastKey : mCurrentKey);
            updateMoveDebouncing(touchX, touchY);
        }

        public void onUpEvent(int touchX, int touchY, long eventTime) {
            int keyIndex = mKeyDetector.getKeyIndexAndNearbyCodes(touchX, touchY, null);
            boolean wasInKeyRepeat = mHandler.isInKeyRepeat();
            mHandler.cancelKeyTimers();
            mHandler.cancelPopupPreview();
            if (isMinorMoveBounce(touchX, touchY, keyIndex, mCurrentKey)) {
                updateTimeDebouncing(eventTime);
            } else {
                resetMultiTap();
                resetTimeDebouncing(eventTime, mCurrentKey);
                mCurrentKey = keyIndex;
            }
            if (isMinorTimeBounce()) {
                mCurrentKey = mLastKey;
                touchX = mLastCodeX;
                touchY = mLastCodeY;
            }
            showKeyPreviewAndUpdateKey(NOT_A_KEY);
            // If we're not on a repeating key (which sends on a DOWN event)
            if (!wasInKeyRepeat && !mProxy.isMiniKeyboardOnScreen()) {
                detectAndSendKey(mCurrentKey, touchX, touchY, eventTime);
            }
            if (keyIndex != NOT_A_KEY)
                mProxy.invalidateKey(mKeys[keyIndex]);
        }

        public void onCancelEvent(int touchX, int touchY, long eventTime) {
            mHandler.cancelKeyTimers();
            mHandler.cancelPopupPreview();
            mProxy.dismissPopupKeyboard();
            showKeyPreviewAndUpdateKey(NOT_A_KEY);
            if (mCurrentKey != NOT_A_KEY)
               mProxy.invalidateKey(mKeys[mCurrentKey]);
        }

        public void repeatKey(int keyIndex) {
            Key key = mKeys[keyIndex];
            // While key is repeating, because there is no need to handle multi-tap key, we can pass
            // -1 as eventTime argument.
            detectAndSendKey(keyIndex, key.x, key.y, -1);
        }

        // These package scope methods are only for debugging purpose.
        /* package */ int getStartX() {
            return mStartX;
        }

        /* package */ int getStartY() {
            return mStartY;
        }

        /* package */ int getLastX() {
            return mLastX;
        }

        /* package */ int getLastY() {
            return mLastY;
        }

        private void startMoveDebouncing(int x, int y) {
            mLastCodeX = x;
            mLastCodeY = y;
        }

        private void updateMoveDebouncing(int x, int y) {
            mLastX = x;
            mLastY = y;
        }

        private void resetMoveDebouncing() {
            mLastCodeX = mLastX;
            mLastCodeY = mLastY;
        }

        private boolean isMinorMoveBounce(int x, int y, int newKey, int curKey) {
            if (mKeys == null || mKeyDebounceThresholdSquared < 0)
                throw new IllegalStateException("keyboard and/or hysteresis not set");
            if (newKey == curKey) {
                return true;
            } else if (curKey >= 0 && curKey < mKeys.length) {
                return getSquareDistanceToKeyEdge(x, y, mKeys[curKey])
                        < mKeyDebounceThresholdSquared;
            } else {
                return false;
            }
        }

        private static int getSquareDistanceToKeyEdge(int x, int y, Key key) {
            final int left = key.x;
            final int right = key.x + key.width;
            final int top = key.y;
            final int bottom = key.y + key.height;
            final int edgeX = x < left ? left : (x > right ? right : x);
            final int edgeY = y < top ? top : (y > bottom ? bottom : y);
            final int dx = x - edgeX;
            final int dy = y - edgeY;
            return dx * dx + dy * dy;
        }

        private void startTimeDebouncing(long eventTime) {
            mLastKey = NOT_A_KEY;
            mLastKeyTime = 0;
            mCurrentKeyTime = 0;
            mLastMoveTime = eventTime;
        }

        private void updateTimeDebouncing(long eventTime) {
            mCurrentKeyTime += eventTime - mLastMoveTime;
            mLastMoveTime = eventTime;
        }

        private void resetTimeDebouncing(long eventTime, int currentKey) {
            mLastKey = currentKey;
            mLastKeyTime = mCurrentKeyTime + eventTime - mLastMoveTime;
            mCurrentKeyTime = 0;
            mLastMoveTime = eventTime;
        }

        private boolean isMinorTimeBounce() {
            return mCurrentKeyTime < mLastKeyTime && mCurrentKeyTime < KEY_DEBOUNCE_TIME
                && mLastKey != NOT_A_KEY;
        }

        private void showKeyPreviewAndUpdateKey(int keyIndex) {
            updateKey(keyIndex);
            mProxy.showPreview(keyIndex, this);
        }

        private void detectAndSendKey(int index, int x, int y, long eventTime) {
            if (index != NOT_A_KEY && index < mKeys.length) {
                final Key key = mKeys[index];
                OnKeyboardActionListener listener = mListener;
                if (key.text != null) {
                    if (listener != null) {
                        listener.onText(key.text);
                        listener.onRelease(NOT_A_KEY);
                    }
                } else {
                    int code = key.codes[0];
                    //TextEntryState.keyPressedAt(key, x, y);
                    int[] codes = mKeyDetector.newCodeArray();
                    mKeyDetector.getKeyIndexAndNearbyCodes(x, y, codes);
                    // Multi-tap
                    if (mInMultiTap) {
                        if (mTapCount != -1) {
                            mListener.onKey(Keyboard.KEYCODE_DELETE, KEY_DELETE, x, y);
                        } else {
                            mTapCount = 0;
                        }
                        code = key.codes[mTapCount];
                    }
                    /*
                     * Swap the first and second values in the codes array if the primary code is not
                     * the first value but the second value in the array. This happens when key
                     * debouncing is in effect.
                     */
                    if (codes.length >= 2 && codes[0] != code && codes[1] == code) {
                        codes[1] = codes[0];
                        codes[0] = code;
                    }
                    if (listener != null) {
                        listener.onKey(code, codes, x, y);
                        listener.onRelease(code);
                    }
                }
                mLastSentIndex = index;
                mLastTapTime = eventTime;
            }
        }

        /**
         * Handle multi-tap keys by producing the key label for the current multi-tap state.
         */
        public CharSequence getPreviewText(Key key) {
            if (mInMultiTap) {
                // Multi-tap
                mPreviewLabel.setLength(0);
                mPreviewLabel.append((char) key.codes[mTapCount < 0 ? 0 : mTapCount]);
                return mPreviewLabel;
            } else {
                return key.label;
            }
        }

        private void resetMultiTap() {
            mLastSentIndex = NOT_A_KEY;
            mTapCount = 0;
            mLastTapTime = -1;
            mInMultiTap = false;
        }

        private void checkMultiTap(long eventTime, int keyIndex) {
            if (keyIndex == NOT_A_KEY) return;
            Key key = mKeys[keyIndex];
            if (key.codes.length > 1) {
                mInMultiTap = true;
                if (eventTime < mLastTapTime + MULTITAP_INTERVAL && keyIndex == mLastSentIndex) {
                    mTapCount = (mTapCount + 1) % key.codes.length;
                    return;
                } else {
                    mTapCount = -1;
                    return;
                }
            }
            if (eventTime > mLastTapTime + MULTITAP_INTERVAL || keyIndex != mLastSentIndex) {
                resetMultiTap();
            }
        }
    }

    public LatinKeyboardBaseView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.keyboardViewStyle);
    }

    public LatinKeyboardBaseView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.LatinKeyboardBaseView, defStyle, R.style.LatinKeyboardBaseView);
        LayoutInflater inflate =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        int previewLayout = 0;
        int keyTextSize = 0;

        int n = a.getIndexCount();

        for (int i = 0; i < n; i++) {
            int attr = a.getIndex(i);

            switch (attr) {
            case R.styleable.LatinKeyboardBaseView_keyBackground:
                mKeyBackground = a.getDrawable(attr);
                break;
            case R.styleable.LatinKeyboardBaseView_verticalCorrection:
                mVerticalCorrection = a.getDimensionPixelOffset(attr, 0);
                break;
            case R.styleable.LatinKeyboardBaseView_keyPreviewLayout:
                previewLayout = a.getResourceId(attr, 0);
                break;
            case R.styleable.LatinKeyboardBaseView_keyPreviewOffset:
                mPreviewOffset = a.getDimensionPixelOffset(attr, 0);
                break;
            case R.styleable.LatinKeyboardBaseView_keyPreviewHeight:
                mPreviewHeight = a.getDimensionPixelSize(attr, 80);
                break;
            case R.styleable.LatinKeyboardBaseView_keyTextSize:
                mKeyTextSize = a.getDimensionPixelSize(attr, 18);
                break;
            case R.styleable.LatinKeyboardBaseView_keyTextColor:
                mKeyTextColor = a.getColor(attr, 0xFF000000);
                break;
            case R.styleable.LatinKeyboardBaseView_labelTextSize:
                mLabelTextSize = a.getDimensionPixelSize(attr, 14);
                break;
            case R.styleable.LatinKeyboardBaseView_popupLayout:
                mPopupLayout = a.getResourceId(attr, 0);
                break;
            case R.styleable.LatinKeyboardBaseView_shadowColor:
                mShadowColor = a.getColor(attr, 0);
                break;
            case R.styleable.LatinKeyboardBaseView_shadowRadius:
                mShadowRadius = a.getFloat(attr, 0f);
                break;
            // TODO: Use Theme (android.R.styleable.Theme_backgroundDimAmount)
            case R.styleable.LatinKeyboardBaseView_backgroundDimAmount:
                mBackgroundDimAmount = a.getFloat(attr, 0.5f);
                break;
            //case android.R.styleable.
            case R.styleable.LatinKeyboardBaseView_keyTextStyle:
                int textStyle = a.getInt(attr, 0);
                switch (textStyle) {
                    case 0:
                        mKeyTextStyle = Typeface.DEFAULT;
                        break;
                    case 1:
                        mKeyTextStyle = Typeface.DEFAULT_BOLD;
                        break;
                    default:
                        mKeyTextStyle = Typeface.defaultFromStyle(textStyle);
                        break;
                }
                break;
            case R.styleable.LatinKeyboardBaseView_symbolColorScheme:
                mSymbolColorScheme = a.getInt(attr, 0);
                break;
            }
        }

        mPreviewPopup = new PopupWindow(context);
        if (previewLayout != 0) {
            mPreviewText = (TextView) inflate.inflate(previewLayout, null);
            mPreviewTextSizeLarge = (int) mPreviewText.getTextSize();
            mPreviewPopup.setContentView(mPreviewText);
            mPreviewPopup.setBackgroundDrawable(null);
        } else {
            mShowPreview = false;
        }
        mPreviewPopup.setTouchable(false);
        mPopupParent = this;

        mPopupKeyboard = new PopupWindow(context);
        mPopupKeyboard.setBackgroundDrawable(null);

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setTextSize(keyTextSize);
        mPaint.setTextAlign(Align.CENTER);
        mPaint.setAlpha(255);

        mPadding = new Rect(0, 0, 0, 0);
        mMiniKeyboardCache = new HashMap<Key,View>();
        mKeyBackground.getPadding(mPadding);

        final Resources res = getResources();
        mSwipeThreshold = (int) (500 * res.getDisplayMetrics().density);
        // TODO: Refer frameworks/base/core/res/res/values/config.xml
        mDisambiguateSwipe = res.getBoolean(R.bool.config_swipeDisambiguation);
        mDebounceHysteresis = res.getDimension(R.dimen.key_debounce_hysteresis_distance);

        GestureDetector.SimpleOnGestureListener listener =
                new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent me1, MotionEvent me2, float velocityX,
                    float velocityY) {
                final float absX = Math.abs(velocityX);
                final float absY = Math.abs(velocityY);
                float deltaX = me2.getX() - me1.getX();
                float deltaY = me2.getY() - me1.getY();
                int travelX = getWidth() / 2; // Half the keyboard width
                int travelY = getHeight() / 2; // Half the keyboard height
                mSwipeTracker.computeCurrentVelocity(1000);
                final float endingVelocityX = mSwipeTracker.getXVelocity();
                final float endingVelocityY = mSwipeTracker.getYVelocity();
                if (velocityX > mSwipeThreshold && absY < absX && deltaX > travelX) {
                    if (mDisambiguateSwipe && endingVelocityX >= velocityX / 4) {
                        swipeRight();
                        return true;
                    }
                } else if (velocityX < -mSwipeThreshold && absY < absX && deltaX < -travelX) {
                    if (mDisambiguateSwipe && endingVelocityX <= velocityX / 4) {
                        swipeLeft();
                        return true;
                    }
                } else if (velocityY < -mSwipeThreshold && absX < absY && deltaY < -travelY) {
                    if (mDisambiguateSwipe && endingVelocityY <= velocityY / 4) {
                        swipeUp();
                        return true;
                    }
                } else if (velocityY > mSwipeThreshold && absX < absY / 2 && deltaY > travelY) {
                    if (mDisambiguateSwipe && endingVelocityY >= velocityY / 4) {
                        swipeDown();
                        return true;
                    }
                }
                return false;
            }
        };

        final boolean ignoreMultitouch = true;
        mGestureDetector = new GestureDetector(getContext(), listener, null, ignoreMultitouch);
        mGestureDetector.setIsLongpressEnabled(false);

        // TODO: This anonymous interface is temporary until KeyDebouncer becomes top-level class.
        // In the future LatinKeyboardBaseView class will implement UIProxy.
        mDebouncer = new KeyDebouncer(mHandler, mProximityKeyDetector, new KeyDebouncer.UIProxy() {
            public void invalidateKey(Key key) {
                LatinKeyboardBaseView.this.invalidateKey(key);
            }

            public void showPreview(int keyIndex, KeyDebouncer debouncer) {
                LatinKeyboardBaseView.this.showPreview(keyIndex, debouncer);
            }

            public void dismissPopupKeyboard() {
                LatinKeyboardBaseView.this.dismissPopupKeyboard();
            }

            public boolean isMiniKeyboardOnScreen() {
                return LatinKeyboardBaseView.this.mMiniKeyboardOnScreen;
            }
        });
    }

    public void setOnKeyboardActionListener(OnKeyboardActionListener listener) {
        mKeyboardActionListener = listener;
        mDebouncer.setOnKeyboardActionListener(listener);
    }

    /**
     * Returns the {@link OnKeyboardActionListener} object.
     * @return the listener attached to this keyboard
     */
    protected OnKeyboardActionListener getOnKeyboardActionListener() {
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
            dismissKeyPreview();
        }
        // Remove any pending messages, except dismissing preview
        mHandler.cancelKeyTimers();
        mHandler.cancelPopupPreview();
        mKeyboard = keyboard;
        LatinImeLogger.onSetKeyboard(mKeyboard);
        List<Key> keys = mKeyboard.getKeys();
        mKeys = keys.toArray(new Key[keys.size()]);
        mProximityKeyDetector.setKeyboard(keyboard, mKeys);
        mDebouncer.setKeyboard(mKeys, mDebounceHysteresis);
        requestLayout();
        // Hint to reallocate the buffer if the size changed
        mKeyboardChanged = true;
        invalidateAllKeys();
        computeProximityThreshold(keyboard);
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
     * Sets the state of the shift key of the keyboard, if any.
     * @param shifted whether or not to enable the state of the shift key
     * @return true if the shift key state changed, false if there was no change
     */
    public boolean setShifted(boolean shifted) {
        if (mKeyboard != null) {
            if (mKeyboard.setShifted(shifted)) {
                // The whole keyboard probably needs to be redrawn
                invalidateAllKeys();
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the state of the shift key of the keyboard, if any.
     * @return true if the shift is in a pressed state, false otherwise. If there is
     * no shift key on the keyboard or there is no keyboard attached, it returns false.
     */
    public boolean isShifted() {
        if (mKeyboard != null) {
            return mKeyboard.isShifted();
        }
        return false;
    }

    /**
     * Enables or disables the key feedback popup. This is a popup that shows a magnified
     * version of the depressed key. By default the preview is enabled.
     * @param previewEnabled whether or not to enable the key feedback popup
     * @see #isPreviewEnabled()
     */
    public void setPreviewEnabled(boolean previewEnabled) {
        mShowPreview = previewEnabled;
    }

    /**
     * Returns the enabled state of the key feedback popup.
     * @return whether or not the key feedback popup is enabled
     * @see #setPreviewEnabled(boolean)
     */
    public boolean isPreviewEnabled() {
        return mShowPreview;
    }

    public int getSymbolColorSheme() {
        return mSymbolColorScheme;
    }

    public void setVerticalCorrection(int verticalOffset) {
    }

    public void setPopupParent(View v) {
        mPopupParent = v;
    }

    public void setPopupOffset(int x, int y) {
        mMiniKeyboardOffsetX = x;
        mMiniKeyboardOffsetY = y;
        if (mPreviewPopup.isShowing()) {
            mPreviewPopup.dismiss();
        }
    }

    /**
     * When enabled, calls to {@link OnKeyboardActionListener#onKey} will include key
     * codes for adjacent keys.  When disabled, only the primary key code will be
     * reported.
     * @param enabled whether or not the proximity correction is enabled
     */
    public void setProximityCorrectionEnabled(boolean enabled) {
        mProximityKeyDetector.setProximityCorrectionEnabled(enabled);
    }

    /**
     * Returns true if proximity correction is enabled.
     */
    public boolean isProximityCorrectionEnabled() {
        return mProximityKeyDetector.isProximityCorrectionEnabled();
    }

    /**
     * Popup keyboard close button clicked.
     * @hide
     */
    public void onClick(View v) {
        dismissPopupKeyboard();
    }

    protected CharSequence adjustCase(CharSequence label) {
        if (mKeyboard.isShifted() && label != null && label.length() < 3
                && Character.isLowerCase(label.charAt(0))) {
            label = label.toString().toUpperCase();
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

    /**
     * Compute the average distance between adjacent keys (horizontally and vertically)
     * and square it to get the proximity threshold. We use a square here and in computing
     * the touch distance from a key's center to avoid taking a square root.
     * @param keyboard
     */
    private void computeProximityThreshold(Keyboard keyboard) {
        if (keyboard == null) return;
        final Key[] keys = mKeys;
        if (keys == null) return;
        int length = keys.length;
        int dimensionSum = 0;
        for (int i = 0; i < length; i++) {
            Key key = keys[i];
            dimensionSum += Math.min(key.width, key.height) + key.gap;
        }
        if (dimensionSum < 0 || length == 0) return;
        mProximityKeyDetector.setProximityThreshold((int) (dimensionSum * 1.4f / length));
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Release the buffer, if any and it will be reallocated on the next draw
        mBuffer = null;
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
        if (mBuffer == null || mKeyboardChanged) {
            if (mBuffer == null || mKeyboardChanged &&
                    (mBuffer.getWidth() != getWidth() || mBuffer.getHeight() != getHeight())) {
                // Make sure our bitmap is at least 1x1
                final int width = Math.max(1, getWidth());
                final int height = Math.max(1, getHeight());
                mBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                mCanvas = new Canvas(mBuffer);
            }
            invalidateAllKeys();
            mKeyboardChanged = false;
        }
        final Canvas canvas = mCanvas;
        canvas.clipRect(mDirtyRect, Op.REPLACE);

        if (mKeyboard == null) return;

        final Paint paint = mPaint;
        final Drawable keyBackground = mKeyBackground;
        final Rect clipRegion = mClipRegion;
        final Rect padding = mPadding;
        final int kbdPaddingLeft = getPaddingLeft();
        final int kbdPaddingTop = getPaddingTop();
        final Key[] keys = mKeys;
        final Key invalidKey = mInvalidatedKey;

        paint.setColor(mKeyTextColor);
        boolean drawSingleKey = false;
        if (invalidKey != null && canvas.getClipBounds(clipRegion)) {
          // Is clipRegion completely contained within the invalidated key?
          if (invalidKey.x + kbdPaddingLeft - 1 <= clipRegion.left &&
                  invalidKey.y + kbdPaddingTop - 1 <= clipRegion.top &&
                  invalidKey.x + invalidKey.width + kbdPaddingLeft + 1 >= clipRegion.right &&
                  invalidKey.y + invalidKey.height + kbdPaddingTop + 1 >= clipRegion.bottom) {
              drawSingleKey = true;
          }
        }
        canvas.drawColor(0x00000000, PorterDuff.Mode.CLEAR);
        final int keyCount = keys.length;
        for (int i = 0; i < keyCount; i++) {
            final Key key = keys[i];
            if (drawSingleKey && invalidKey != key) {
                continue;
            }
            int[] drawableState = key.getCurrentDrawableState();
            keyBackground.setState(drawableState);

            // Switch the character to uppercase if shift is pressed
            String label = key.label == null? null : adjustCase(key.label).toString();

            final Rect bounds = keyBackground.getBounds();
            if (key.width != bounds.right ||
                    key.height != bounds.bottom) {
                keyBackground.setBounds(0, 0, key.width, key.height);
            }
            canvas.translate(key.x + kbdPaddingLeft, key.y + kbdPaddingTop);
            keyBackground.draw(canvas);

            if (label != null) {
                // For characters, use large font. For labels like "Done", use small font.
                if (label.length() > 1 && key.codes.length < 2) {
                    paint.setTextSize(mLabelTextSize);
                    paint.setTypeface(Typeface.DEFAULT_BOLD);
                } else {
                    paint.setTextSize(mKeyTextSize);
                    paint.setTypeface(mKeyTextStyle);
                }
                // Draw a drop shadow for the text
                paint.setShadowLayer(mShadowRadius, 0, 0, mShadowColor);
                // Draw the text
                canvas.drawText(label,
                    (key.width - padding.left - padding.right) / 2
                            + padding.left,
                    (key.height - padding.top - padding.bottom) / 2
                            + (paint.getTextSize() - paint.descent()) / 2 + padding.top,
                    paint);
                // Turn off drop shadow
                paint.setShadowLayer(0, 0, 0, 0);
            } else if (key.icon != null) {
                final int drawableX = (key.width - padding.left - padding.right
                                - key.icon.getIntrinsicWidth()) / 2 + padding.left;
                final int drawableY = (key.height - padding.top - padding.bottom
                        - key.icon.getIntrinsicHeight()) / 2 + padding.top;
                canvas.translate(drawableX, drawableY);
                key.icon.setBounds(0, 0,
                        key.icon.getIntrinsicWidth(), key.icon.getIntrinsicHeight());
                key.icon.draw(canvas);
                canvas.translate(-drawableX, -drawableY);
            }
            canvas.translate(-key.x - kbdPaddingLeft, -key.y - kbdPaddingTop);
        }
        mInvalidatedKey = null;
        // Overlay a dark rectangle to dim the keyboard
        if (mMiniKeyboardOnScreen) {
            paint.setColor((int) (mBackgroundDimAmount * 0xFF) << 24);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        }

        if (DEBUG) {
            if (mShowTouchPoints) {
                KeyDebouncer debouncer = mDebouncer;
                int startX = debouncer.getStartX();
                int startY = debouncer.getStartY();
                int lastX = debouncer.getLastX();
                int lastY = debouncer.getLastY();
                paint.setAlpha(128);
                paint.setColor(0xFFFF0000);
                canvas.drawCircle(startX, startY, 3, paint);
                canvas.drawLine(startX, startY, lastX, lastY, paint);
                paint.setColor(0xFF0000FF);
                canvas.drawCircle(lastX, lastY, 3, paint);
                paint.setColor(0xFF00FF00);
                canvas.drawCircle((startX + lastX) / 2, (startY + lastY) / 2, 2, paint);
            }
        }

        mDrawPending = false;
        mDirtyRect.setEmpty();
    }

    // TODO: clean up this when KeyDebouncer class becomes top-level class. 
    private void dismissKeyPreview() {
        mDebouncer.updateKey(NOT_A_KEY);
        showPreview(NOT_A_KEY, mDebouncer);
    }

    private void showPreview(int keyIndex, KeyDebouncer debouncer) {
        int oldKeyIndex = mOldPreviewKeyIndex;
        mOldPreviewKeyIndex = keyIndex;
        // If key changed and preview is on ...
        if (oldKeyIndex != keyIndex && mShowPreview) {
            if (keyIndex == NOT_A_KEY) {
                mHandler.cancelPopupPreview();
                mHandler.dismissPreview(DELAY_AFTER_PREVIEW);
            } else {
                mHandler.popupPreview(DELAY_BEFORE_PREVIEW, keyIndex, debouncer);
            }
        }
    }

    private void showKey(final int keyIndex, KeyDebouncer debouncer) {
        Key key = debouncer.getKey(keyIndex);
        if (key == null)
            return;
        final PopupWindow previewPopup = mPreviewPopup;
        if (key.icon != null) {
            mPreviewText.setCompoundDrawables(null, null, null,
                    key.iconPreview != null ? key.iconPreview : key.icon);
            mPreviewText.setText(null);
        } else {
            mPreviewText.setCompoundDrawables(null, null, null, null);
            mPreviewText.setText(adjustCase(debouncer.getPreviewText(key)));
            if (key.label.length() > 1 && key.codes.length < 2) {
                mPreviewText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mKeyTextSize);
                mPreviewText.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                mPreviewText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mPreviewTextSizeLarge);
                mPreviewText.setTypeface(mKeyTextStyle);
            }
        }
        mPreviewText.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int popupWidth = Math.max(mPreviewText.getMeasuredWidth(), key.width
                + mPreviewText.getPaddingLeft() + mPreviewText.getPaddingRight());
        final int popupHeight = mPreviewHeight;
        LayoutParams lp = mPreviewText.getLayoutParams();
        if (lp != null) {
            lp.width = popupWidth;
            lp.height = popupHeight;
        }
        if (PREVIEW_CENTERED) {
            // TODO: Fix this if centering is brought back
            mPopupPreviewX = 160 - mPreviewText.getMeasuredWidth() / 2;
            mPopupPreviewY = - mPreviewText.getMeasuredHeight();
        } else {
            mPopupPreviewX = key.x - mPreviewText.getPaddingLeft() + getPaddingLeft();
            mPopupPreviewY = key.y - popupHeight + mPreviewOffset;
        }
        mHandler.cancelDismissPreview();
        if (mOffsetInWindow == null) {
            mOffsetInWindow = new int[2];
            getLocationInWindow(mOffsetInWindow);
            mOffsetInWindow[0] += mMiniKeyboardOffsetX; // Offset may be zero
            mOffsetInWindow[1] += mMiniKeyboardOffsetY; // Offset may be zero
            int[] mWindowLocation = new int[2];
            getLocationOnScreen(mWindowLocation);
            mWindowY = mWindowLocation[1];
        }
        // Set the preview background state
        mPreviewText.getBackground().setState(
                key.popupResId != 0 ? LONG_PRESSABLE_STATE_SET : EMPTY_STATE_SET);
        mPopupPreviewX += mOffsetInWindow[0];
        mPopupPreviewY += mOffsetInWindow[1];

        // If the popup cannot be shown above the key, put it on the side
        if (mPopupPreviewY + mWindowY < 0) {
            // If the key you're pressing is on the left side of the keyboard, show the popup on
            // the right, offset by enough to see at least one key to the left/right.
            if (key.x + key.width <= getWidth() / 2) {
                mPopupPreviewX += (int) (key.width * 2.5);
            } else {
                mPopupPreviewX -= (int) (key.width * 2.5);
            }
            mPopupPreviewY += popupHeight;
        }

        if (previewPopup.isShowing()) {
            previewPopup.update(mPopupPreviewX, mPopupPreviewY,
                    popupWidth, popupHeight);
        } else {
            previewPopup.setWidth(popupWidth);
            previewPopup.setHeight(popupHeight);
            previewPopup.showAtLocation(mPopupParent, Gravity.NO_GRAVITY,
                    mPopupPreviewX, mPopupPreviewY);
        }
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
    public void invalidateKey(Key key) {
        if (key == null)
            return;
        mInvalidatedKey = key;
        mDirtyRect.union(key.x + getPaddingLeft(), key.y + getPaddingTop(),
                key.x + key.width + getPaddingLeft(), key.y + key.height + getPaddingTop());
        onBufferDraw();
        invalidate(key.x + getPaddingLeft(), key.y + getPaddingTop(),
                key.x + key.width + getPaddingLeft(), key.y + key.height + getPaddingTop());
    }

    private boolean openPopupIfRequired(int keyIndex) {
        // Check if we have a popup layout specified first.
        if (mPopupLayout == 0) {
            return false;
        }
        if (keyIndex < 0 || keyIndex >= mKeys.length) {
            return false;
        }

        Key popupKey = mKeys[keyIndex];
        boolean result = onLongPress(popupKey);
        if (result) {
            dismissKeyPreview();
        }
        return result;
    }

    /**
     * Called when a key is long pressed. By default this will open any popup keyboard associated
     * with this key through the attributes popupLayout and popupCharacters.
     * @param popupKey the key that was long pressed
     * @return true if the long press is handled, false otherwise. Subclasses should call the
     * method on the base class if the subclass doesn't wish to handle the call.
     */
    protected boolean onLongPress(Key popupKey) {
        int popupKeyboardId = popupKey.popupResId;

        if (popupKeyboardId != 0) {
            mMiniKeyboardContainer = mMiniKeyboardCache.get(popupKey);
            if (mMiniKeyboardContainer == null) {
                LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE);
                mMiniKeyboardContainer = inflater.inflate(mPopupLayout, null);
                mMiniKeyboard = (LatinKeyboardBaseView) mMiniKeyboardContainer.findViewById(
                       R.id.LatinKeyboardBaseView);
                View closeButton = mMiniKeyboardContainer.findViewById(R.id.closeButton);
                if (closeButton != null) closeButton.setOnClickListener(this);
                mMiniKeyboard.setOnKeyboardActionListener(new OnKeyboardActionListener() {
                    public void onKey(int primaryCode, int[] keyCodes, int x, int y) {
                        mKeyboardActionListener.onKey(primaryCode, keyCodes, x, y);
                        dismissPopupKeyboard();
                    }

                    public void onText(CharSequence text) {
                        mKeyboardActionListener.onText(text);
                        dismissPopupKeyboard();
                    }

                    public void swipeLeft() { }
                    public void swipeRight() { }
                    public void swipeUp() { }
                    public void swipeDown() { }
                    public void onPress(int primaryCode) {
                        mKeyboardActionListener.onPress(primaryCode);
                    }
                    public void onRelease(int primaryCode) {
                        mKeyboardActionListener.onRelease(primaryCode);
                    }
                });
                //mInputView.setSuggest(mSuggest);
                Keyboard keyboard;
                if (popupKey.popupCharacters != null) {
                    keyboard = new Keyboard(getContext(), popupKeyboardId,
                            popupKey.popupCharacters, -1, getPaddingLeft() + getPaddingRight());
                } else {
                    keyboard = new Keyboard(getContext(), popupKeyboardId);
                }
                mMiniKeyboard.setKeyboard(keyboard);
                mMiniKeyboard.setPopupParent(this);
                mMiniKeyboardContainer.measure(
                        MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.AT_MOST));

                mMiniKeyboardCache.put(popupKey, mMiniKeyboardContainer);
            } else {
                mMiniKeyboard = (LatinKeyboardBaseView) mMiniKeyboardContainer.findViewById(
                        R.id.LatinKeyboardBaseView);
            }
            if (mWindowOffset == null) {
                mWindowOffset = new int[2];
                getLocationInWindow(mWindowOffset);
            }
            int popupX = popupKey.x + getPaddingLeft();
            int popupY = popupKey.y + getPaddingTop();
            popupX = popupX + popupKey.width - mMiniKeyboardContainer.getMeasuredWidth();
            popupY = popupY - mMiniKeyboardContainer.getMeasuredHeight();
            final int x = popupX + mMiniKeyboardContainer.getPaddingRight() + mWindowOffset[0];
            final int y = popupY + mMiniKeyboardContainer.getPaddingBottom() + mWindowOffset[1];
            mMiniKeyboard.setPopupOffset(x < 0 ? 0 : x, y);
            mMiniKeyboard.setShifted(isShifted());
            mPopupKeyboard.setContentView(mMiniKeyboardContainer);
            mPopupKeyboard.setWidth(mMiniKeyboardContainer.getMeasuredWidth());
            mPopupKeyboard.setHeight(mMiniKeyboardContainer.getMeasuredHeight());
            mPopupKeyboard.showAtLocation(this, Gravity.NO_GRAVITY, x, y);
            mMiniKeyboardOnScreen = true;
            //mMiniKeyboard.onTouchEvent(getTranslatedEvent(me));
            invalidateAllKeys();
            return true;
        }
        return false;
    }

    private int getTouchX(float x) {
        return (int)x - getPaddingLeft();
    }

    private int getTouchY(float y) {
        return (int)y + mVerticalCorrection - getPaddingTop();
    }

    @Override
    public boolean onTouchEvent(MotionEvent me) {
        // Convert multi-pointer up/down events to single up/down events to
        // deal with the typical multi-pointer behavior of two-thumb typing
        final int pointerCount = me.getPointerCount();
        final int action = me.getAction();
        final long eventTime = me.getEventTime();

        if (pointerCount > 1 && mOldPointerCount > 1) {
            // Don't do anything when 2 or more pointers are down and moving.
            return true;
        }

        // Track the last few movements to look for spurious swipes.
        mSwipeTracker.addMovement(me);

        // We must disable gesture detector while mini-keyboard is on the screen.
        if (!mMiniKeyboardOnScreen && mGestureDetector.onTouchEvent(me)) {
            dismissKeyPreview();
            mHandler.cancelKeyTimers();
            return true;
        }

        // Needs to be called after the gesture detector gets a turn, as it may have
        // displayed the mini keyboard
        if (mMiniKeyboardOnScreen && action != MotionEvent.ACTION_CANCEL) {
            return true;
        }

        if (mHandler.isInKeyRepeat()) {
            // It'll be canceled if 2 or more keys are in action. Otherwise it will keep being in
            // the key repeating mode while the key is being pressed.
            if (pointerCount > 1) {
                mHandler.cancelKeyRepeatTimer();
            } else if (action == MotionEvent.ACTION_MOVE) {
                return true;
            }
            // Up event will pass through.
        }

        int touchX = getTouchX(me.getX());
        int touchY = getTouchY(me.getY());
        if (pointerCount != mOldPointerCount) {
            if (pointerCount == 1) {
                // Send a down event for the latest pointer
                mDebouncer.onDownEvent(touchX, touchY, eventTime);
                // If it's an up action, then deliver the up as well.
                if (action == MotionEvent.ACTION_UP) {
                    mDebouncer.onUpEvent(touchX, touchY, eventTime);
                }
            } else {
                // Send an up event for the last pointer
                mDebouncer.onUpEvent(mOldPointerX, mOldPointerY, eventTime);
            }
            mOldPointerCount = pointerCount;
            return true;
        } else {
            if (pointerCount == 1) {
                mDebouncer.onModifiedTouchEvent(action, touchX, touchY, eventTime);
                mOldPointerX = touchX;
                mOldPointerY = touchY;
                return true;
            }
        }

        return false;
    }

    protected void swipeRight() {
        mKeyboardActionListener.swipeRight();
    }

    protected void swipeLeft() {
        mKeyboardActionListener.swipeLeft();
    }

    protected void swipeUp() {
        mKeyboardActionListener.swipeUp();
    }

    protected void swipeDown() {
        mKeyboardActionListener.swipeDown();
    }

    public void closing() {
        if (mPreviewPopup.isShowing()) {
            mPreviewPopup.dismiss();
        }
        mHandler.cancelAllMessages();

        dismissPopupKeyboard();
        mBuffer = null;
        mCanvas = null;
        mMiniKeyboardCache.clear();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        closing();
    }

    private void dismissPopupKeyboard() {
        if (mPopupKeyboard.isShowing()) {
            mPopupKeyboard.dismiss();
            mMiniKeyboardOnScreen = false;
            invalidateAllKeys();
        }
    }

    public boolean handleBack() {
        if (mPopupKeyboard.isShowing()) {
            dismissPopupKeyboard();
            return true;
        }
        return false;
    }
}
