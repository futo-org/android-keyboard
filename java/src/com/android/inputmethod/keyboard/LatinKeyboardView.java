/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.animation.AnimatorInflater;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Message;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.PopupWindow;

import com.android.inputmethod.accessibility.AccessibilityUtils;
import com.android.inputmethod.accessibility.AccessibleKeyboardViewProxy;
import com.android.inputmethod.keyboard.PointerTracker.DrawingProxy;
import com.android.inputmethod.keyboard.PointerTracker.TimerProxy;
import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.ResearchLogger;
import com.android.inputmethod.latin.StaticInnerHandlerWrapper;
import com.android.inputmethod.latin.StringUtils;
import com.android.inputmethod.latin.SubtypeLocale;
import com.android.inputmethod.latin.Utils;
import com.android.inputmethod.latin.Utils.UsabilityStudyLogUtils;
import com.android.inputmethod.latin.define.ProductionFlag;

import java.util.Locale;
import java.util.WeakHashMap;

/**
 * A view that is responsible for detecting key presses and touch movements.
 *
 * @attr ref R.styleable#KeyboardView_keyHysteresisDistance
 * @attr ref R.styleable#KeyboardView_verticalCorrection
 * @attr ref R.styleable#KeyboardView_popupLayout
 */
public class LatinKeyboardView extends KeyboardView implements PointerTracker.KeyEventHandler,
        SuddenJumpingTouchEventHandler.ProcessMotionEvent {
    private static final String TAG = LatinKeyboardView.class.getSimpleName();

    // TODO: Kill process when the usability study mode was changed.
    private static final boolean ENABLE_USABILITY_STUDY_LOG = LatinImeLogger.sUsabilityStudy;

    /** Listener for {@link KeyboardActionListener}. */
    private KeyboardActionListener mKeyboardActionListener;

    /* Space key and its icons */
    private Key mSpaceKey;
    private Drawable mSpaceIcon;
    // Stuff to draw language name on spacebar.
    private final int mLanguageOnSpacebarFinalAlpha;
    private ObjectAnimator mLanguageOnSpacebarFadeoutAnimator;
    private static final int ALPHA_OPAQUE = 255;
    private boolean mNeedsToDisplayLanguage;
    private boolean mHasMultipleEnabledIMEsOrSubtypes;
    private int mLanguageOnSpacebarAnimAlpha = ALPHA_OPAQUE;
    private final float mSpacebarTextRatio;
    private float mSpacebarTextSize;
    private final int mSpacebarTextColor;
    private final int mSpacebarTextShadowColor;
    // The minimum x-scale to fit the language name on spacebar.
    private static final float MINIMUM_XSCALE_OF_LANGUAGE_NAME = 0.8f;
    // Stuff to draw auto correction LED on spacebar.
    private boolean mAutoCorrectionSpacebarLedOn;
    private final boolean mAutoCorrectionSpacebarLedEnabled;
    private final Drawable mAutoCorrectionSpacebarLedIcon;
    private static final int SPACE_LED_LENGTH_PERCENT = 80;

    // Stuff to draw altCodeWhileTyping keys.
    private ObjectAnimator mAltCodeKeyWhileTypingFadeoutAnimator;
    private ObjectAnimator mAltCodeKeyWhileTypingFadeinAnimator;
    private int mAltCodeKeyWhileTypingAnimAlpha = ALPHA_OPAQUE;

    // More keys keyboard
    private PopupWindow mMoreKeysWindow;
    private MoreKeysPanel mMoreKeysPanel;
    private int mMoreKeysPanelPointerTrackerId;
    private final WeakHashMap<Key, MoreKeysPanel> mMoreKeysPanelCache =
            new WeakHashMap<Key, MoreKeysPanel>();
    private final boolean mConfigShowMoreKeysKeyboardAtTouchedPoint;

    private final PointerTrackerParams mPointerTrackerParams;
    private final SuddenJumpingTouchEventHandler mTouchScreenRegulator;

    protected KeyDetector mKeyDetector;
    private boolean mHasDistinctMultitouch;
    private int mOldPointerCount = 1;
    private Key mOldKey;

    private final KeyTimerHandler mKeyTimerHandler;

    private static class KeyTimerHandler extends StaticInnerHandlerWrapper<LatinKeyboardView>
            implements TimerProxy {
        private static final int MSG_REPEAT_KEY = 1;
        private static final int MSG_LONGPRESS_KEY = 2;
        private static final int MSG_DOUBLE_TAP = 3;
        private static final int MSG_TYPING_STATE_EXPIRED = 4;

        private final KeyTimerParams mParams;
        private boolean mInKeyRepeat;

        public KeyTimerHandler(LatinKeyboardView outerInstance, KeyTimerParams params) {
            super(outerInstance);
            mParams = params;
        }

        @Override
        public void handleMessage(Message msg) {
            final LatinKeyboardView keyboardView = getOuterInstance();
            final PointerTracker tracker = (PointerTracker) msg.obj;
            switch (msg.what) {
            case MSG_REPEAT_KEY:
                tracker.onRegisterKey(tracker.getKey());
                startKeyRepeatTimer(tracker, mParams.mKeyRepeatInterval);
                break;
            case MSG_LONGPRESS_KEY:
                if (tracker != null) {
                    keyboardView.openMoreKeysKeyboardIfRequired(tracker.getKey(), tracker);
                } else {
                    KeyboardSwitcher.getInstance().onLongPressTimeout(msg.arg1);
                }
                break;
            case MSG_TYPING_STATE_EXPIRED:
                cancelAndStartAnimators(keyboardView.mAltCodeKeyWhileTypingFadeoutAnimator,
                        keyboardView.mAltCodeKeyWhileTypingFadeinAnimator);
                break;
            }
        }

        private void startKeyRepeatTimer(PointerTracker tracker, long delay) {
            sendMessageDelayed(obtainMessage(MSG_REPEAT_KEY, tracker), delay);
        }

        @Override
        public void startKeyRepeatTimer(PointerTracker tracker) {
            mInKeyRepeat = true;
            startKeyRepeatTimer(tracker, mParams.mKeyRepeatStartTimeout);
        }

        public void cancelKeyRepeatTimer() {
            mInKeyRepeat = false;
            removeMessages(MSG_REPEAT_KEY);
        }

        public boolean isInKeyRepeat() {
            return mInKeyRepeat;
        }

        @Override
        public void startLongPressTimer(int code) {
            cancelLongPressTimer();
            final int delay;
            switch (code) {
            case Keyboard.CODE_SHIFT:
                delay = mParams.mLongPressShiftKeyTimeout;
                break;
            default:
                delay = 0;
                break;
            }
            if (delay > 0) {
                sendMessageDelayed(obtainMessage(MSG_LONGPRESS_KEY, code, 0), delay);
            }
        }

        @Override
        public void startLongPressTimer(PointerTracker tracker) {
            cancelLongPressTimer();
            if (tracker == null) {
                return;
            }
            final Key key = tracker.getKey();
            final int delay;
            switch (key.mCode) {
            case Keyboard.CODE_SHIFT:
                delay = mParams.mLongPressShiftKeyTimeout;
                break;
            default:
                if (KeyboardSwitcher.getInstance().isInMomentarySwitchState()) {
                    // We use longer timeout for sliding finger input started from the symbols
                    // mode key.
                    delay = mParams.mLongPressKeyTimeout * 3;
                } else {
                    delay = mParams.mLongPressKeyTimeout;
                }
                break;
            }
            if (delay > 0) {
                sendMessageDelayed(obtainMessage(MSG_LONGPRESS_KEY, tracker), delay);
            }
        }

        @Override
        public void cancelLongPressTimer() {
            removeMessages(MSG_LONGPRESS_KEY);
        }

        public static void cancelAndStartAnimators(final ObjectAnimator animatorToCancel,
                final ObjectAnimator animatorToStart) {
            float startFraction = 0.0f;
            if (animatorToCancel.isStarted()) {
                animatorToCancel.cancel();
                startFraction = 1.0f - animatorToCancel.getAnimatedFraction();
            }
            final long startTime = (long)(animatorToStart.getDuration() * startFraction);
            animatorToStart.start();
            animatorToStart.setCurrentPlayTime(startTime);
        }

        @Override
        public void startTypingStateTimer() {
            final boolean isTyping = isTypingState();
            removeMessages(MSG_TYPING_STATE_EXPIRED);
            sendMessageDelayed(
                    obtainMessage(MSG_TYPING_STATE_EXPIRED), mParams.mIgnoreAltCodeKeyTimeout);
            if (isTyping) {
                return;
            }
            final LatinKeyboardView keyboardView = getOuterInstance();
            cancelAndStartAnimators(keyboardView.mAltCodeKeyWhileTypingFadeinAnimator,
                    keyboardView.mAltCodeKeyWhileTypingFadeoutAnimator);
        }

        @Override
        public boolean isTypingState() {
            return hasMessages(MSG_TYPING_STATE_EXPIRED);
        }

        @Override
        public void startDoubleTapTimer() {
            sendMessageDelayed(obtainMessage(MSG_DOUBLE_TAP),
                    ViewConfiguration.getDoubleTapTimeout());
        }

        @Override
        public void cancelDoubleTapTimer() {
            removeMessages(MSG_DOUBLE_TAP);
        }

        @Override
        public boolean isInDoubleTapTimeout() {
            return hasMessages(MSG_DOUBLE_TAP);
        }

        @Override
        public void cancelKeyTimers() {
            cancelKeyRepeatTimer();
            cancelLongPressTimer();
        }

        public void cancelAllMessages() {
            cancelKeyTimers();
        }
    }

    public static class PointerTrackerParams {
        public final boolean mSlidingKeyInputEnabled;
        public final int mTouchNoiseThresholdTime;
        public final float mTouchNoiseThresholdDistance;

        public static final PointerTrackerParams DEFAULT = new PointerTrackerParams();

        private PointerTrackerParams() {
            mSlidingKeyInputEnabled = false;
            mTouchNoiseThresholdTime =0;
            mTouchNoiseThresholdDistance = 0;
        }

        public PointerTrackerParams(TypedArray latinKeyboardViewAttr) {
            mSlidingKeyInputEnabled = latinKeyboardViewAttr.getBoolean(
                    R.styleable.LatinKeyboardView_slidingKeyInputEnable, false);
            mTouchNoiseThresholdTime = latinKeyboardViewAttr.getInt(
                    R.styleable.LatinKeyboardView_touchNoiseThresholdTime, 0);
            mTouchNoiseThresholdDistance = latinKeyboardViewAttr.getDimension(
                    R.styleable.LatinKeyboardView_touchNoiseThresholdDistance, 0);
        }
    }

    static class KeyTimerParams {
        public final int mKeyRepeatStartTimeout;
        public final int mKeyRepeatInterval;
        public final int mLongPressKeyTimeout;
        public final int mLongPressShiftKeyTimeout;
        public final int mIgnoreAltCodeKeyTimeout;

        public KeyTimerParams(TypedArray latinKeyboardViewAttr) {
            mKeyRepeatStartTimeout = latinKeyboardViewAttr.getInt(
                    R.styleable.LatinKeyboardView_keyRepeatStartTimeout, 0);
            mKeyRepeatInterval = latinKeyboardViewAttr.getInt(
                    R.styleable.LatinKeyboardView_keyRepeatInterval, 0);
            mLongPressKeyTimeout = latinKeyboardViewAttr.getInt(
                    R.styleable.LatinKeyboardView_longPressKeyTimeout, 0);
            mLongPressShiftKeyTimeout = latinKeyboardViewAttr.getInt(
                    R.styleable.LatinKeyboardView_longPressShiftKeyTimeout, 0);
            mIgnoreAltCodeKeyTimeout = latinKeyboardViewAttr.getInt(
                    R.styleable.LatinKeyboardView_ignoreAltCodeKeyTimeout, 0);
        }
    }

    public LatinKeyboardView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.latinKeyboardViewStyle);
    }

    public LatinKeyboardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mTouchScreenRegulator = new SuddenJumpingTouchEventHandler(getContext(), this);

        mHasDistinctMultitouch = context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT);
        final boolean needsPhantomSuddenMoveEventHack = Boolean.parseBoolean(
                Utils.getDeviceOverrideValue(context.getResources(),
                        R.array.phantom_sudden_move_event_device_list, "false"));
        PointerTracker.init(mHasDistinctMultitouch, needsPhantomSuddenMoveEventHack);

        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.LatinKeyboardView, defStyle, R.style.LatinKeyboardView);
        mAutoCorrectionSpacebarLedEnabled = a.getBoolean(
                R.styleable.LatinKeyboardView_autoCorrectionSpacebarLedEnabled, false);
        mAutoCorrectionSpacebarLedIcon = a.getDrawable(
                R.styleable.LatinKeyboardView_autoCorrectionSpacebarLedIcon);
        mSpacebarTextRatio = a.getFraction(R.styleable.LatinKeyboardView_spacebarTextRatio,
                1000, 1000, 1) / 1000.0f;
        mSpacebarTextColor = a.getColor(R.styleable.LatinKeyboardView_spacebarTextColor, 0);
        mSpacebarTextShadowColor = a.getColor(
                R.styleable.LatinKeyboardView_spacebarTextShadowColor, 0);
        mLanguageOnSpacebarFinalAlpha = a.getInt(
                R.styleable.LatinKeyboardView_languageOnSpacebarFinalAlpha, ALPHA_OPAQUE);
        final int languageOnSpacebarFadeoutAnimatorResId = a.getResourceId(
                R.styleable.LatinKeyboardView_languageOnSpacebarFadeoutAnimator, 0);
        final int altCodeKeyWhileTypingFadeoutAnimatorResId = a.getResourceId(
                R.styleable.LatinKeyboardView_altCodeKeyWhileTypingFadeoutAnimator, 0);
        final int altCodeKeyWhileTypingFadeinAnimatorResId = a.getResourceId(
                R.styleable.LatinKeyboardView_altCodeKeyWhileTypingFadeinAnimator, 0);

        final KeyTimerParams keyTimerParams = new KeyTimerParams(a);
        mPointerTrackerParams = new PointerTrackerParams(a);

        final float keyHysteresisDistance = a.getDimension(
                R.styleable.LatinKeyboardView_keyHysteresisDistance, 0);
        mKeyDetector = new KeyDetector(keyHysteresisDistance);
        mKeyTimerHandler = new KeyTimerHandler(this, keyTimerParams);
        mConfigShowMoreKeysKeyboardAtTouchedPoint = a.getBoolean(
                R.styleable.LatinKeyboardView_showMoreKeysKeyboardAtTouchedPoint, false);
        a.recycle();

        PointerTracker.setParameters(mPointerTrackerParams);

        mLanguageOnSpacebarFadeoutAnimator = loadObjectAnimator(
                languageOnSpacebarFadeoutAnimatorResId, this);
        mAltCodeKeyWhileTypingFadeoutAnimator = loadObjectAnimator(
                altCodeKeyWhileTypingFadeoutAnimatorResId, this);
        mAltCodeKeyWhileTypingFadeinAnimator = loadObjectAnimator(
                altCodeKeyWhileTypingFadeinAnimatorResId, this);
    }

    private ObjectAnimator loadObjectAnimator(int resId, Object target) {
        if (resId == 0) return null;
        final ObjectAnimator animator = (ObjectAnimator)AnimatorInflater.loadAnimator(
                getContext(), resId);
        if (animator != null) {
            animator.setTarget(target);
        }
        return animator;
    }

    // Getter/setter methods for {@link ObjectAnimator}.
    public int getLanguageOnSpacebarAnimAlpha() {
        return mLanguageOnSpacebarAnimAlpha;
    }

    public void setLanguageOnSpacebarAnimAlpha(int alpha) {
        mLanguageOnSpacebarAnimAlpha = alpha;
        invalidateKey(mSpaceKey);
    }

    public int getAltCodeKeyWhileTypingAnimAlpha() {
        return mAltCodeKeyWhileTypingAnimAlpha;
    }

    public void setAltCodeKeyWhileTypingAnimAlpha(int alpha) {
        mAltCodeKeyWhileTypingAnimAlpha = alpha;
        updateAltCodeKeyWhileTyping();
    }

    public void setKeyboardActionListener(KeyboardActionListener listener) {
        mKeyboardActionListener = listener;
        PointerTracker.setKeyboardActionListener(listener);
    }

    /**
     * Returns the {@link KeyboardActionListener} object.
     * @return the listener attached to this keyboard
     */
    @Override
    public KeyboardActionListener getKeyboardActionListener() {
        return mKeyboardActionListener;
    }

    @Override
    public KeyDetector getKeyDetector() {
        return mKeyDetector;
    }

    @Override
    public DrawingProxy getDrawingProxy() {
        return this;
    }

    @Override
    public TimerProxy getTimerProxy() {
        return mKeyTimerHandler;
    }

    /**
     * Attaches a keyboard to this view. The keyboard can be switched at any time and the
     * view will re-layout itself to accommodate the keyboard.
     * @see Keyboard
     * @see #getKeyboard()
     * @param keyboard the keyboard to display in this view
     */
    @Override
    public void setKeyboard(Keyboard keyboard) {
        // Remove any pending messages, except dismissing preview
        mKeyTimerHandler.cancelKeyTimers();
        super.setKeyboard(keyboard);
        mKeyDetector.setKeyboard(
                keyboard, -getPaddingLeft(), -getPaddingTop() + mVerticalCorrection);
        PointerTracker.setKeyDetector(mKeyDetector);
        mTouchScreenRegulator.setKeyboard(keyboard);
        mMoreKeysPanelCache.clear();

        mSpaceKey = keyboard.getKey(Keyboard.CODE_SPACE);
        mSpaceIcon = (mSpaceKey != null)
                ? mSpaceKey.getIcon(keyboard.mIconsSet, ALPHA_OPAQUE) : null;
        final int keyHeight = keyboard.mMostCommonKeyHeight - keyboard.mVerticalGap;
        mSpacebarTextSize = keyHeight * mSpacebarTextRatio;
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.latinKeyboardView_setKeyboard(keyboard);
        }

        // This always needs to be set since the accessibility state can
        // potentially change without the keyboard being set again.
        AccessibleKeyboardViewProxy.getInstance().setKeyboard(keyboard);
    }

    /**
     * Returns whether the device has distinct multi-touch panel.
     * @return true if the device has distinct multi-touch panel.
     */
    public boolean hasDistinctMultitouch() {
        return mHasDistinctMultitouch;
    }

    public void setDistinctMultitouch(boolean hasDistinctMultitouch) {
        mHasDistinctMultitouch = hasDistinctMultitouch;
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
    public void cancelAllMessages() {
        mKeyTimerHandler.cancelAllMessages();
        super.cancelAllMessages();
    }

    private boolean openMoreKeysKeyboardIfRequired(Key parentKey, PointerTracker tracker) {
        // Check if we have a popup layout specified first.
        if (mMoreKeysLayout == 0) {
            return false;
        }

        // Check if we are already displaying popup panel.
        if (mMoreKeysPanel != null)
            return false;
        if (parentKey == null)
            return false;
        return onLongPress(parentKey, tracker);
    }

    // This default implementation returns a more keys panel.
    protected MoreKeysPanel onCreateMoreKeysPanel(Key parentKey) {
        if (parentKey.mMoreKeys == null)
            return null;

        final View container = LayoutInflater.from(getContext()).inflate(mMoreKeysLayout, null);
        if (container == null)
            throw new NullPointerException();

        final MoreKeysKeyboardView moreKeysKeyboardView =
                (MoreKeysKeyboardView)container.findViewById(R.id.more_keys_keyboard_view);
        final Keyboard moreKeysKeyboard = new MoreKeysKeyboard.Builder(container, parentKey, this)
                .build();
        moreKeysKeyboardView.setKeyboard(moreKeysKeyboard);
        container.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        return moreKeysKeyboardView;
    }

    /**
     * Called when a key is long pressed. By default this will open more keys keyboard associated
     * with this key.
     * @param parentKey the key that was long pressed
     * @param tracker the pointer tracker which pressed the parent key
     * @return true if the long press is handled, false otherwise. Subclasses should call the
     * method on the base class if the subclass doesn't wish to handle the call.
     */
    protected boolean onLongPress(Key parentKey, PointerTracker tracker) {
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.latinKeyboardView_onLongPress();
        }
        final int primaryCode = parentKey.mCode;
        if (parentKey.hasEmbeddedMoreKey()) {
            final int embeddedCode = parentKey.mMoreKeys[0].mCode;
            tracker.onLongPressed();
            invokeCodeInput(embeddedCode);
            invokeReleaseKey(primaryCode);
            KeyboardSwitcher.getInstance().hapticAndAudioFeedback(primaryCode);
            return true;
        }
        if (primaryCode == Keyboard.CODE_SPACE || primaryCode == Keyboard.CODE_LANGUAGE_SWITCH) {
            // Long pressing the space key invokes IME switcher dialog.
            if (invokeCustomRequest(LatinIME.CODE_SHOW_INPUT_METHOD_PICKER)) {
                tracker.onLongPressed();
                invokeReleaseKey(primaryCode);
                return true;
            }
        }
        return openMoreKeysPanel(parentKey, tracker);
    }

    private boolean invokeCustomRequest(int code) {
        return mKeyboardActionListener.onCustomRequest(code);
    }

    private void invokeCodeInput(int primaryCode) {
        mKeyboardActionListener.onCodeInput(primaryCode,
                KeyboardActionListener.NOT_A_TOUCH_COORDINATE,
                KeyboardActionListener.NOT_A_TOUCH_COORDINATE);
    }

    private void invokeReleaseKey(int primaryCode) {
        mKeyboardActionListener.onReleaseKey(primaryCode, false);
    }

    private boolean openMoreKeysPanel(Key parentKey, PointerTracker tracker) {
        MoreKeysPanel moreKeysPanel = mMoreKeysPanelCache.get(parentKey);
        if (moreKeysPanel == null) {
            moreKeysPanel = onCreateMoreKeysPanel(parentKey);
            if (moreKeysPanel == null)
                return false;
            mMoreKeysPanelCache.put(parentKey, moreKeysPanel);
        }
        if (mMoreKeysWindow == null) {
            mMoreKeysWindow = new PopupWindow(getContext());
            mMoreKeysWindow.setBackgroundDrawable(null);
            mMoreKeysWindow.setAnimationStyle(R.style.MoreKeysKeyboardAnimation);
        }
        mMoreKeysPanel = moreKeysPanel;
        mMoreKeysPanelPointerTrackerId = tracker.mPointerId;

        final boolean keyPreviewEnabled = isKeyPreviewPopupEnabled() && !parentKey.noKeyPreview();
        // The more keys keyboard is usually horizontally aligned with the center of the parent key.
        // If showMoreKeysKeyboardAtTouchedPoint is true and the key preview is disabled, the more
        // keys keyboard is placed at the touch point of the parent key.
        final int pointX = (mConfigShowMoreKeysKeyboardAtTouchedPoint && !keyPreviewEnabled)
                ? tracker.getLastX()
                : parentKey.mX + parentKey.mWidth / 2;
        // The more keys keyboard is usually vertically aligned with the top edge of the parent key
        // (plus vertical gap). If the key preview is enabled, the more keys keyboard is vertically
        // aligned with the bottom edge of the visible part of the key preview.
        final int pointY = parentKey.mY + (keyPreviewEnabled
                ? mKeyPreviewDrawParams.mPreviewVisibleOffset
                : -parentKey.mVerticalGap);
        moreKeysPanel.showMoreKeysPanel(
                this, this, pointX, pointY, mMoreKeysWindow, mKeyboardActionListener);
        final int translatedX = moreKeysPanel.translateX(tracker.getLastX());
        final int translatedY = moreKeysPanel.translateY(tracker.getLastY());
        tracker.onShowMoreKeysPanel(translatedX, translatedY, moreKeysPanel);
        dimEntireKeyboard(true);
        return true;
    }

    public boolean isInSlidingKeyInput() {
        if (mMoreKeysPanel != null) {
            return true;
        } else {
            return PointerTracker.isAnyInSlidingKeyInput();
        }
    }

    public int getPointerCount() {
        return mOldPointerCount;
    }

    @Override
    public boolean onTouchEvent(MotionEvent me) {
        if (getKeyboard() == null) {
            return false;
        }
        return mTouchScreenRegulator.onTouchEvent(me);
    }

    @Override
    public boolean processMotionEvent(MotionEvent me) {
        final boolean nonDistinctMultitouch = !mHasDistinctMultitouch;
        final int action = me.getActionMasked();
        final int pointerCount = me.getPointerCount();
        final int oldPointerCount = mOldPointerCount;
        mOldPointerCount = pointerCount;

        // TODO: cleanup this code into a multi-touch to single-touch event converter class?
        // If the device does not have distinct multi-touch support panel, ignore all multi-touch
        // events except a transition from/to single-touch.
        if (nonDistinctMultitouch && pointerCount > 1 && oldPointerCount > 1) {
            return true;
        }

        final long eventTime = me.getEventTime();
        final int index = me.getActionIndex();
        final int id = me.getPointerId(index);
        final int x, y;
        if (mMoreKeysPanel != null && id == mMoreKeysPanelPointerTrackerId) {
            x = mMoreKeysPanel.translateX((int)me.getX(index));
            y = mMoreKeysPanel.translateY((int)me.getY(index));
        } else {
            x = (int)me.getX(index);
            y = (int)me.getY(index);
        }
        if (ENABLE_USABILITY_STUDY_LOG) {
            final String eventTag;
            switch (action) {
                case MotionEvent.ACTION_UP:
                    eventTag = "[Up]";
                    break;
                case MotionEvent.ACTION_DOWN:
                    eventTag = "[Down]";
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    eventTag = "[PointerUp]";
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    eventTag = "[PointerDown]";
                    break;
                case MotionEvent.ACTION_MOVE: // Skip this as being logged below
                    eventTag = "";
                    break;
                default:
                    eventTag = "[Action" + action + "]";
                    break;
            }
            if (!TextUtils.isEmpty(eventTag)) {
                final float size = me.getSize(index);
                final float pressure = me.getPressure(index);
                UsabilityStudyLogUtils.getInstance().write(
                        eventTag + eventTime + "," + id + "," + x + "," + y + ","
                        + size + "," + pressure);
            }
        }
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.latinKeyboardView_processMotionEvent(me, action, eventTime, index, id,
                    x, y);
        }

        if (mKeyTimerHandler.isInKeyRepeat()) {
            final PointerTracker tracker = PointerTracker.getPointerTracker(id, this);
            // Key repeating timer will be canceled if 2 or more keys are in action, and current
            // event (UP or DOWN) is non-modifier key.
            if (pointerCount > 1 && !tracker.isModifier()) {
                mKeyTimerHandler.cancelKeyRepeatTimer();
            }
            // Up event will pass through.
        }

        // TODO: cleanup this code into a multi-touch to single-touch event converter class?
        // Translate mutli-touch event to single-touch events on the device that has no distinct
        // multi-touch panel.
        if (nonDistinctMultitouch) {
            // Use only main (id=0) pointer tracker.
            final PointerTracker tracker = PointerTracker.getPointerTracker(0, this);
            if (pointerCount == 1 && oldPointerCount == 2) {
                // Multi-touch to single touch transition.
                // Send a down event for the latest pointer if the key is different from the
                // previous key.
                final Key newKey = tracker.getKeyOn(x, y);
                if (mOldKey != newKey) {
                    tracker.onDownEvent(x, y, eventTime, this);
                    if (action == MotionEvent.ACTION_UP)
                        tracker.onUpEvent(x, y, eventTime);
                }
            } else if (pointerCount == 2 && oldPointerCount == 1) {
                // Single-touch to multi-touch transition.
                // Send an up event for the last pointer.
                final int lastX = tracker.getLastX();
                final int lastY = tracker.getLastY();
                mOldKey = tracker.getKeyOn(lastX, lastY);
                tracker.onUpEvent(lastX, lastY, eventTime);
            } else if (pointerCount == 1 && oldPointerCount == 1) {
                tracker.processMotionEvent(action, x, y, eventTime, this);
            } else {
                Log.w(TAG, "Unknown touch panel behavior: pointer count is " + pointerCount
                        + " (old " + oldPointerCount + ")");
            }
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < pointerCount; i++) {
                final int pointerId = me.getPointerId(i);
                final PointerTracker tracker = PointerTracker.getPointerTracker(
                        pointerId, this);
                final int px, py;
                if (mMoreKeysPanel != null
                        && tracker.mPointerId == mMoreKeysPanelPointerTrackerId) {
                    px = mMoreKeysPanel.translateX((int)me.getX(i));
                    py = mMoreKeysPanel.translateY((int)me.getY(i));
                } else {
                    px = (int)me.getX(i);
                    py = (int)me.getY(i);
                }
                tracker.onMoveEvent(px, py, eventTime);
                if (ENABLE_USABILITY_STUDY_LOG) {
                    final float pointerSize = me.getSize(i);
                    final float pointerPressure = me.getPressure(i);
                    UsabilityStudyLogUtils.getInstance().write("[Move]"  + eventTime + ","
                            + pointerId + "," + px + "," + py + ","
                            + pointerSize + "," + pointerPressure);
                }
                if (ProductionFlag.IS_EXPERIMENTAL) {
                    ResearchLogger.latinKeyboardView_processMotionEvent(me, action, eventTime,
                            i, pointerId, px, py);
                }
            }
        } else {
            final PointerTracker tracker = PointerTracker.getPointerTracker(id, this);
            tracker.processMotionEvent(action, x, y, eventTime, this);
        }

        return true;
    }

    @Override
    public void closing() {
        super.closing();
        dismissMoreKeysPanel();
        mMoreKeysPanelCache.clear();
    }

    @Override
    public boolean dismissMoreKeysPanel() {
        if (mMoreKeysWindow != null && mMoreKeysWindow.isShowing()) {
            mMoreKeysWindow.dismiss();
            mMoreKeysPanel = null;
            mMoreKeysPanelPointerTrackerId = -1;
            dimEntireKeyboard(false);
            return true;
        }
        return false;
    }

    @Override
    public void draw(Canvas c) {
        Utils.GCUtils.getInstance().reset();
        boolean tryGC = true;
        for (int i = 0; i < Utils.GCUtils.GC_TRY_LOOP_MAX && tryGC; ++i) {
            try {
                super.draw(c);
                tryGC = false;
            } catch (OutOfMemoryError e) {
                tryGC = Utils.GCUtils.getInstance().tryGCOrWait(TAG, e);
            }
        }
    }

    /**
     * Receives hover events from the input framework.
     *
     * @param event The motion event to be dispatched.
     * @return {@code true} if the event was handled by the view, {@code false}
     *         otherwise
     */
    @Override
    public boolean dispatchHoverEvent(MotionEvent event) {
        if (AccessibilityUtils.getInstance().isTouchExplorationEnabled()) {
            final PointerTracker tracker = PointerTracker.getPointerTracker(0, this);
            return AccessibleKeyboardViewProxy.getInstance().dispatchHoverEvent(event, tracker);
        }

        // Reflection doesn't support calling superclass methods.
        return false;
    }

    public void updateShortcutKey(boolean available) {
        final Keyboard keyboard = getKeyboard();
        if (keyboard == null) return;
        final Key shortcutKey = keyboard.getKey(Keyboard.CODE_SHORTCUT);
        if (shortcutKey == null) return;
        shortcutKey.setEnabled(available);
        invalidateKey(shortcutKey);
    }

    private void updateAltCodeKeyWhileTyping() {
        final Keyboard keyboard = getKeyboard();
        if (keyboard == null) return;
        for (final Key key : keyboard.mAltCodeKeysWhileTyping) {
            invalidateKey(key);
        }
    }

    public void startDisplayLanguageOnSpacebar(boolean subtypeChanged,
            boolean needsToDisplayLanguage, boolean hasMultipleEnabledIMEsOrSubtypes) {
        mNeedsToDisplayLanguage = needsToDisplayLanguage;
        mHasMultipleEnabledIMEsOrSubtypes = hasMultipleEnabledIMEsOrSubtypes;
        final ObjectAnimator animator = mLanguageOnSpacebarFadeoutAnimator;
        if (animator == null) {
            mNeedsToDisplayLanguage = false;
        } else {
            if (subtypeChanged && needsToDisplayLanguage) {
                setLanguageOnSpacebarAnimAlpha(ALPHA_OPAQUE);
                if (animator.isStarted()) {
                    animator.cancel();
                }
                animator.start();
            } else {
                if (!animator.isStarted()) {
                    mLanguageOnSpacebarAnimAlpha = mLanguageOnSpacebarFinalAlpha;
                }
            }
        }
        invalidateKey(mSpaceKey);
    }

    public void updateAutoCorrectionState(boolean isAutoCorrection) {
        if (!mAutoCorrectionSpacebarLedEnabled) return;
        mAutoCorrectionSpacebarLedOn = isAutoCorrection;
        invalidateKey(mSpaceKey);
    }

    @Override
    protected void onDrawKeyTopVisuals(Key key, Canvas canvas, Paint paint, KeyDrawParams params) {
        if (key.altCodeWhileTyping() && key.isEnabled()) {
            params.mAnimAlpha = mAltCodeKeyWhileTypingAnimAlpha;
        }
        if (key.mCode == Keyboard.CODE_SPACE) {
            drawSpacebar(key, canvas, paint);
            // Whether space key needs to show the "..." popup hint for special purposes
            if (key.isLongPressEnabled() && mHasMultipleEnabledIMEsOrSubtypes) {
                drawKeyPopupHint(key, canvas, paint, params);
            }
        } else if (key.mCode == Keyboard.CODE_LANGUAGE_SWITCH) {
            super.onDrawKeyTopVisuals(key, canvas, paint, params);
            drawKeyPopupHint(key, canvas, paint, params);
        } else {
            super.onDrawKeyTopVisuals(key, canvas, paint, params);
        }
    }

    private boolean fitsTextIntoWidth(final int width, String text, Paint paint) {
        paint.setTextScaleX(1.0f);
        final float textWidth = getLabelWidth(text, paint);
        if (textWidth < width) return true;

        final float scaleX = width / textWidth;
        if (scaleX < MINIMUM_XSCALE_OF_LANGUAGE_NAME) return false;

        paint.setTextScaleX(scaleX);
        return getLabelWidth(text, paint) < width;
    }

    // Layout language name on spacebar.
    private String layoutLanguageOnSpacebar(Paint paint, InputMethodSubtype subtype,
            final int width) {
        // Choose appropriate language name to fit into the width.
        String text = getFullDisplayName(subtype, getResources());
        if (fitsTextIntoWidth(width, text, paint)) {
            return text;
        }

        text = getMiddleDisplayName(subtype);
        if (fitsTextIntoWidth(width, text, paint)) {
            return text;
        }

        text = getShortDisplayName(subtype);
        if (fitsTextIntoWidth(width, text, paint)) {
            return text;
        }

        return "";
    }

    private void drawSpacebar(Key key, Canvas canvas, Paint paint) {
        final int width = key.mWidth;
        final int height = key.mHeight;

        // If input language are explicitly selected.
        if (mNeedsToDisplayLanguage) {
            paint.setTextAlign(Align.CENTER);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(mSpacebarTextSize);
            final InputMethodSubtype subtype = getKeyboard().mId.mSubtype;
            final String language = layoutLanguageOnSpacebar(paint, subtype, width);
            // Draw language text with shadow
            final float descent = paint.descent();
            final float textHeight = -paint.ascent() + descent;
            final float baseline = height / 2 + textHeight / 2;
            paint.setColor(mSpacebarTextShadowColor);
            paint.setAlpha(mLanguageOnSpacebarAnimAlpha);
            canvas.drawText(language, width / 2, baseline - descent - 1, paint);
            paint.setColor(mSpacebarTextColor);
            paint.setAlpha(mLanguageOnSpacebarAnimAlpha);
            canvas.drawText(language, width / 2, baseline - descent, paint);
        }

        // Draw the spacebar icon at the bottom
        if (mAutoCorrectionSpacebarLedOn) {
            final int iconWidth = width * SPACE_LED_LENGTH_PERCENT / 100;
            final int iconHeight = mAutoCorrectionSpacebarLedIcon.getIntrinsicHeight();
            int x = (width - iconWidth) / 2;
            int y = height - iconHeight;
            drawIcon(canvas, mAutoCorrectionSpacebarLedIcon, x, y, iconWidth, iconHeight);
        } else if (mSpaceIcon != null) {
            final int iconWidth = mSpaceIcon.getIntrinsicWidth();
            final int iconHeight = mSpaceIcon.getIntrinsicHeight();
            int x = (width - iconWidth) / 2;
            int y = height - iconHeight;
            drawIcon(canvas, mSpaceIcon, x, y, iconWidth, iconHeight);
        }
    }

    // InputMethodSubtype's display name for spacebar text in its locale.
    //        isAdditionalSubtype (T=true, F=false)
    // locale layout | Short  Middle      Full
    // ------ ------ - ---- --------- ----------------------
    //  en_US qwerty F  En  English   English (US)           exception
    //  en_GB qwerty F  En  English   English (UK)           exception
    //  fr    azerty F  Fr  Français  Français
    //  fr_CA qwerty F  Fr  Français  Français (Canada)
    //  de    qwertz F  De  Deutsch   Deutsch
    //  zz    qwerty F      QWERTY    QWERTY
    //  fr    qwertz T  Fr  Français  Français (QWERTZ)
    //  de    qwerty T  De  Deutsch   Deutsch (QWERTY)
    //  en_US azerty T  En  English   English (US) (AZERTY)
    //  zz    azerty T      AZERTY    AZERTY

    // Get InputMethodSubtype's full display name in its locale.
    static String getFullDisplayName(InputMethodSubtype subtype, Resources res) {
        if (SubtypeLocale.isNoLanguage(subtype)) {
            return SubtypeLocale.getKeyboardLayoutSetDisplayName(subtype);
        }

        return SubtypeLocale.getSubtypeDisplayName(subtype, res);
    }

    // Get InputMethodSubtype's short display name in its locale.
    static String getShortDisplayName(InputMethodSubtype subtype) {
        if (SubtypeLocale.isNoLanguage(subtype)) {
            return "";
        }
        final Locale locale = SubtypeLocale.getSubtypeLocale(subtype);
        return StringUtils.toTitleCase(locale.getLanguage(), locale);
    }

    // Get InputMethodSubtype's middle display name in its locale.
    static String getMiddleDisplayName(InputMethodSubtype subtype) {
        if (SubtypeLocale.isNoLanguage(subtype)) {
            return SubtypeLocale.getKeyboardLayoutSetDisplayName(subtype);
        }
        final Locale locale = SubtypeLocale.getSubtypeLocale(subtype);
        return StringUtils.toTitleCase(locale.getDisplayLanguage(locale), locale);
    }
}
