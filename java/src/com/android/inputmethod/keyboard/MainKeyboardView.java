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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.TextView;

import com.android.inputmethod.accessibility.AccessibilityUtils;
import com.android.inputmethod.accessibility.AccessibleKeyboardViewProxy;
import com.android.inputmethod.annotations.ExternallyReferenced;
import com.android.inputmethod.keyboard.PointerTracker.DrawingProxy;
import com.android.inputmethod.keyboard.PointerTracker.TimerProxy;
import com.android.inputmethod.keyboard.internal.KeyDrawParams;
import com.android.inputmethod.keyboard.internal.KeyPreviewDrawParams;
import com.android.inputmethod.keyboard.internal.PreviewPlacerView;
import com.android.inputmethod.keyboard.internal.TouchScreenRegulator;
import com.android.inputmethod.latin.CollectionUtils;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.CoordinateUtils;
import com.android.inputmethod.latin.DebugSettings;
import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.ResourceUtils;
import com.android.inputmethod.latin.StaticInnerHandlerWrapper;
import com.android.inputmethod.latin.StringUtils;
import com.android.inputmethod.latin.SubtypeLocale;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.Utils.UsabilityStudyLogUtils;
import com.android.inputmethod.latin.define.ProductionFlag;
import com.android.inputmethod.research.ResearchLogger;

import java.util.Locale;
import java.util.WeakHashMap;

/**
 * A view that is responsible for detecting key presses and touch movements.
 *
 * @attr ref R.styleable#MainKeyboardView_autoCorrectionSpacebarLedEnabled
 * @attr ref R.styleable#MainKeyboardView_autoCorrectionSpacebarLedIcon
 * @attr ref R.styleable#MainKeyboardView_spacebarTextRatio
 * @attr ref R.styleable#MainKeyboardView_spacebarTextColor
 * @attr ref R.styleable#MainKeyboardView_spacebarTextShadowColor
 * @attr ref R.styleable#MainKeyboardView_languageOnSpacebarFinalAlpha
 * @attr ref R.styleable#MainKeyboardView_languageOnSpacebarFadeoutAnimator
 * @attr ref R.styleable#MainKeyboardView_altCodeKeyWhileTypingFadeoutAnimator
 * @attr ref R.styleable#MainKeyboardView_altCodeKeyWhileTypingFadeinAnimator
 * @attr ref R.styleable#MainKeyboardView_keyHysteresisDistance
 * @attr ref R.styleable#MainKeyboardView_touchNoiseThresholdTime
 * @attr ref R.styleable#MainKeyboardView_touchNoiseThresholdDistance
 * @attr ref R.styleable#MainKeyboardView_slidingKeyInputEnable
 * @attr ref R.styleable#MainKeyboardView_keyRepeatStartTimeout
 * @attr ref R.styleable#MainKeyboardView_keyRepeatInterval
 * @attr ref R.styleable#MainKeyboardView_longPressKeyTimeout
 * @attr ref R.styleable#MainKeyboardView_longPressShiftKeyTimeout
 * @attr ref R.styleable#MainKeyboardView_ignoreAltCodeKeyTimeout
 * @attr ref R.styleable#MainKeyboardView_keyPreviewLayout
 * @attr ref R.styleable#MainKeyboardView_keyPreviewOffset
 * @attr ref R.styleable#MainKeyboardView_keyPreviewHeight
 * @attr ref R.styleable#MainKeyboardView_keyPreviewLingerTimeout
 * @attr ref R.styleable#MainKeyboardView_moreKeysKeyboardLayout
 * @attr ref R.styleable#MainKeyboardView_backgroundDimAlpha
 * @attr ref R.styleable#MainKeyboardView_showMoreKeysKeyboardAtTouchPoint
 * @attr ref R.styleable#MainKeyboardView_gestureFloatingPreviewTextLingerTimeout
 * @attr ref R.styleable#MainKeyboardView_gestureStaticTimeThresholdAfterFastTyping
 * @attr ref R.styleable#MainKeyboardView_gestureDetectFastMoveSpeedThreshold
 * @attr ref R.styleable#MainKeyboardView_gestureDynamicThresholdDecayDuration
 * @attr ref R.styleable#MainKeyboardView_gestureDynamicTimeThresholdFrom
 * @attr ref R.styleable#MainKeyboardView_gestureDynamicTimeThresholdTo
 * @attr ref R.styleable#MainKeyboardView_gestureDynamicDistanceThresholdFrom
 * @attr ref R.styleable#MainKeyboardView_gestureDynamicDistanceThresholdTo
 * @attr ref R.styleable#MainKeyboardView_gestureSamplingMinimumDistance
 * @attr ref R.styleable#MainKeyboardView_gestureRecognitionMinimumTime
 * @attr ref R.styleable#MainKeyboardView_gestureRecognitionSpeedThreshold
 * @attr ref R.styleable#MainKeyboardView_suppressKeyPreviewAfterBatchInputDuration
 */
public final class MainKeyboardView extends KeyboardView implements PointerTracker.KeyEventHandler,
        PointerTracker.DrawingProxy, MoreKeysPanel.Controller,
        TouchScreenRegulator.ProcessMotionEvent {
    private static final String TAG = MainKeyboardView.class.getSimpleName();

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
    private boolean mNeedsToDisplayLanguage;
    private boolean mHasMultipleEnabledIMEsOrSubtypes;
    private int mLanguageOnSpacebarAnimAlpha = Constants.Color.ALPHA_OPAQUE;
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
    private int mAltCodeKeyWhileTypingAnimAlpha = Constants.Color.ALPHA_OPAQUE;

    // Preview placer view
    private final PreviewPlacerView mPreviewPlacerView;
    private final int[] mOriginCoords = CoordinateUtils.newInstance();

    // Key preview
    private static final int PREVIEW_ALPHA = 240;
    private final int mKeyPreviewLayoutId;
    private final int mKeyPreviewOffset;
    private final int mKeyPreviewHeight;
    private final SparseArray<TextView> mKeyPreviewTexts = CollectionUtils.newSparseArray();
    private final KeyPreviewDrawParams mKeyPreviewDrawParams = new KeyPreviewDrawParams();
    private boolean mShowKeyPreviewPopup = true;
    private int mKeyPreviewLingerTimeout;

    // More keys keyboard
    private final Paint mBackgroundDimAlphaPaint = new Paint();
    private boolean mNeedsToDimEntireKeyboard;
    private final WeakHashMap<Key, MoreKeysPanel> mMoreKeysPanelCache =
            new WeakHashMap<Key, MoreKeysPanel>();
    private final int mMoreKeysLayout;
    private final boolean mConfigShowMoreKeysKeyboardAtTouchedPoint;
    // More keys panel (used by both more keys keyboard and more suggestions view)
    // TODO: Consider extending to support multiple more keys panels
    private MoreKeysPanel mMoreKeysPanel;

    // Gesture floating preview text
    // TODO: Make this parameter customizable by user via settings.
    private int mGestureFloatingPreviewTextLingerTimeout;

    private final TouchScreenRegulator mTouchScreenRegulator;

    private KeyDetector mKeyDetector;
    private final boolean mHasDistinctMultitouch;
    private int mOldPointerCount = 1;
    private Key mOldKey;

    private final KeyTimerHandler mKeyTimerHandler;

    private static final class KeyTimerHandler extends StaticInnerHandlerWrapper<MainKeyboardView>
            implements TimerProxy {
        private static final int MSG_TYPING_STATE_EXPIRED = 0;
        private static final int MSG_REPEAT_KEY = 1;
        private static final int MSG_LONGPRESS_KEY = 2;
        private static final int MSG_DOUBLE_TAP = 3;
        private static final int MSG_UPDATE_BATCH_INPUT = 4;

        private final int mKeyRepeatStartTimeout;
        private final int mKeyRepeatInterval;
        private final int mLongPressKeyTimeout;
        private final int mLongPressShiftKeyTimeout;
        private final int mIgnoreAltCodeKeyTimeout;
        private final int mGestureRecognitionUpdateTime;

        public KeyTimerHandler(final MainKeyboardView outerInstance,
                final TypedArray mainKeyboardViewAttr) {
            super(outerInstance);

            mKeyRepeatStartTimeout = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_keyRepeatStartTimeout, 0);
            mKeyRepeatInterval = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_keyRepeatInterval, 0);
            mLongPressKeyTimeout = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_longPressKeyTimeout, 0);
            mLongPressShiftKeyTimeout = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_longPressShiftKeyTimeout, 0);
            mIgnoreAltCodeKeyTimeout = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_ignoreAltCodeKeyTimeout, 0);
            mGestureRecognitionUpdateTime = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_gestureRecognitionUpdateTime, 0);
        }

        @Override
        public void handleMessage(final Message msg) {
            final MainKeyboardView keyboardView = getOuterInstance();
            final PointerTracker tracker = (PointerTracker) msg.obj;
            switch (msg.what) {
            case MSG_TYPING_STATE_EXPIRED:
                startWhileTypingFadeinAnimation(keyboardView);
                break;
            case MSG_REPEAT_KEY:
                final Key currentKey = tracker.getKey();
                if (currentKey != null && currentKey.mCode == msg.arg1) {
                    tracker.onRegisterKey(currentKey);
                    startKeyRepeatTimer(tracker, mKeyRepeatInterval);
                }
                break;
            case MSG_LONGPRESS_KEY:
                if (tracker != null) {
                    keyboardView.openMoreKeysKeyboardIfRequired(tracker.getKey(), tracker);
                } else {
                    KeyboardSwitcher.getInstance().onLongPressTimeout(msg.arg1);
                }
                break;
            case MSG_UPDATE_BATCH_INPUT:
                tracker.updateBatchInputByTimer(SystemClock.uptimeMillis());
                startUpdateBatchInputTimer(tracker);
                break;
            }
        }

        private void startKeyRepeatTimer(final PointerTracker tracker, final long delay) {
            final Key key = tracker.getKey();
            if (key == null) {
                return;
            }
            sendMessageDelayed(obtainMessage(MSG_REPEAT_KEY, key.mCode, 0, tracker), delay);
        }

        @Override
        public void startKeyRepeatTimer(final PointerTracker tracker) {
            startKeyRepeatTimer(tracker, mKeyRepeatStartTimeout);
        }

        public void cancelKeyRepeatTimer() {
            removeMessages(MSG_REPEAT_KEY);
        }

        // TODO: Suppress layout changes in key repeat mode
        public boolean isInKeyRepeat() {
            return hasMessages(MSG_REPEAT_KEY);
        }

        @Override
        public void startLongPressTimer(final int code) {
            cancelLongPressTimer();
            final int delay;
            switch (code) {
            case Constants.CODE_SHIFT:
                delay = mLongPressShiftKeyTimeout;
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
        public void startLongPressTimer(final PointerTracker tracker) {
            cancelLongPressTimer();
            if (tracker == null) {
                return;
            }
            final Key key = tracker.getKey();
            final int delay;
            switch (key.mCode) {
            case Constants.CODE_SHIFT:
                delay = mLongPressShiftKeyTimeout;
                break;
            default:
                if (KeyboardSwitcher.getInstance().isInMomentarySwitchState()) {
                    // We use longer timeout for sliding finger input started from the symbols
                    // mode key.
                    delay = mLongPressKeyTimeout * 3;
                } else {
                    delay = mLongPressKeyTimeout;
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

        private static void cancelAndStartAnimators(final ObjectAnimator animatorToCancel,
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

        private static void startWhileTypingFadeinAnimation(final MainKeyboardView keyboardView) {
            cancelAndStartAnimators(keyboardView.mAltCodeKeyWhileTypingFadeoutAnimator,
                    keyboardView.mAltCodeKeyWhileTypingFadeinAnimator);
        }

        private static void startWhileTypingFadeoutAnimation(final MainKeyboardView keyboardView) {
            cancelAndStartAnimators(keyboardView.mAltCodeKeyWhileTypingFadeinAnimator,
                    keyboardView.mAltCodeKeyWhileTypingFadeoutAnimator);
        }

        @Override
        public void startTypingStateTimer(final Key typedKey) {
            if (typedKey.isModifier() || typedKey.altCodeWhileTyping()) {
                return;
            }

            final boolean isTyping = isTypingState();
            removeMessages(MSG_TYPING_STATE_EXPIRED);
            final MainKeyboardView keyboardView = getOuterInstance();

            // When user hits the space or the enter key, just cancel the while-typing timer.
            final int typedCode = typedKey.mCode;
            if (typedCode == Constants.CODE_SPACE || typedCode == Constants.CODE_ENTER) {
                startWhileTypingFadeinAnimation(keyboardView);
                return;
            }

            sendMessageDelayed(
                    obtainMessage(MSG_TYPING_STATE_EXPIRED), mIgnoreAltCodeKeyTimeout);
            if (isTyping) {
                return;
            }
            startWhileTypingFadeoutAnimation(keyboardView);
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

        @Override
        public void startUpdateBatchInputTimer(final PointerTracker tracker) {
            if (mGestureRecognitionUpdateTime <= 0) {
                return;
            }
            removeMessages(MSG_UPDATE_BATCH_INPUT, tracker);
            sendMessageDelayed(obtainMessage(MSG_UPDATE_BATCH_INPUT, tracker),
                    mGestureRecognitionUpdateTime);
        }

        @Override
        public void cancelUpdateBatchInputTimer(final PointerTracker tracker) {
            removeMessages(MSG_UPDATE_BATCH_INPUT, tracker);
        }

        @Override
        public void cancelAllUpdateBatchInputTimers() {
            removeMessages(MSG_UPDATE_BATCH_INPUT);
        }

        public void cancelAllMessages() {
            cancelKeyTimers();
            cancelAllUpdateBatchInputTimers();
        }
    }

    private final DrawingHandler mDrawingHandler = new DrawingHandler(this);

    public static class DrawingHandler extends StaticInnerHandlerWrapper<MainKeyboardView> {
        private static final int MSG_DISMISS_KEY_PREVIEW = 0;
        private static final int MSG_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT = 1;

        public DrawingHandler(final MainKeyboardView outerInstance) {
            super(outerInstance);
        }

        @Override
        public void handleMessage(final Message msg) {
            final MainKeyboardView mainKeyboardView = getOuterInstance();
            if (mainKeyboardView == null) return;
            final PointerTracker tracker = (PointerTracker) msg.obj;
            switch (msg.what) {
            case MSG_DISMISS_KEY_PREVIEW:
                final TextView previewText = mainKeyboardView.mKeyPreviewTexts.get(
                        tracker.mPointerId);
                if (previewText != null) {
                    previewText.setVisibility(INVISIBLE);
                }
                break;
            case MSG_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT:
                mainKeyboardView.mPreviewPlacerView.setGestureFloatingPreviewText(
                        SuggestedWords.EMPTY);
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

        public void dismissGestureFloatingPreviewText(final long delay) {
            sendMessageDelayed(obtainMessage(MSG_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT), delay);
        }

        public void cancelAllMessages() {
            cancelAllDismissKeyPreviews();
        }
    }

    public MainKeyboardView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.mainKeyboardViewStyle);
    }

    public MainKeyboardView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        mTouchScreenRegulator = new TouchScreenRegulator(context, this);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean forceNonDistinctMultitouch = prefs.getBoolean(
                DebugSettings.PREF_FORCE_NON_DISTINCT_MULTITOUCH, false);
        final boolean hasDistinctMultitouch = context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT);
        mHasDistinctMultitouch = hasDistinctMultitouch && !forceNonDistinctMultitouch;
        final Resources res = getResources();
        final boolean needsPhantomSuddenMoveEventHack = Boolean.parseBoolean(
                ResourceUtils.getDeviceOverrideValue(
                        res, R.array.phantom_sudden_move_event_device_list));
        PointerTracker.init(needsPhantomSuddenMoveEventHack);

        final TypedArray mainKeyboardViewAttr = context.obtainStyledAttributes(
                attrs, R.styleable.MainKeyboardView, defStyle, R.style.MainKeyboardView);
        final int backgroundDimAlpha = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_backgroundDimAlpha, 0);
        mBackgroundDimAlphaPaint.setColor(Color.BLACK);
        mBackgroundDimAlphaPaint.setAlpha(backgroundDimAlpha);
        mAutoCorrectionSpacebarLedEnabled = mainKeyboardViewAttr.getBoolean(
                R.styleable.MainKeyboardView_autoCorrectionSpacebarLedEnabled, false);
        mAutoCorrectionSpacebarLedIcon = mainKeyboardViewAttr.getDrawable(
                R.styleable.MainKeyboardView_autoCorrectionSpacebarLedIcon);
        mSpacebarTextRatio = mainKeyboardViewAttr.getFraction(
                R.styleable.MainKeyboardView_spacebarTextRatio, 1, 1, 1.0f);
        mSpacebarTextColor = mainKeyboardViewAttr.getColor(
                R.styleable.MainKeyboardView_spacebarTextColor, 0);
        mSpacebarTextShadowColor = mainKeyboardViewAttr.getColor(
                R.styleable.MainKeyboardView_spacebarTextShadowColor, 0);
        mLanguageOnSpacebarFinalAlpha = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_languageOnSpacebarFinalAlpha,
                Constants.Color.ALPHA_OPAQUE);
        final int languageOnSpacebarFadeoutAnimatorResId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_languageOnSpacebarFadeoutAnimator, 0);
        final int altCodeKeyWhileTypingFadeoutAnimatorResId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_altCodeKeyWhileTypingFadeoutAnimator, 0);
        final int altCodeKeyWhileTypingFadeinAnimatorResId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_altCodeKeyWhileTypingFadeinAnimator, 0);

        final float keyHysteresisDistance = mainKeyboardViewAttr.getDimension(
                R.styleable.MainKeyboardView_keyHysteresisDistance, 0);
        final float keyHysteresisDistanceForSlidingModifier = mainKeyboardViewAttr.getDimension(
                R.styleable.MainKeyboardView_keyHysteresisDistanceForSlidingModifier, 0);
        mKeyDetector = new KeyDetector(
                keyHysteresisDistance, keyHysteresisDistanceForSlidingModifier);
        mKeyTimerHandler = new KeyTimerHandler(this, mainKeyboardViewAttr);
        mKeyPreviewOffset = mainKeyboardViewAttr.getDimensionPixelOffset(
                R.styleable.MainKeyboardView_keyPreviewOffset, 0);
        mKeyPreviewHeight = mainKeyboardViewAttr.getDimensionPixelSize(
                R.styleable.MainKeyboardView_keyPreviewHeight, 0);
        mKeyPreviewLingerTimeout = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_keyPreviewLingerTimeout, 0);
        mKeyPreviewLayoutId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_keyPreviewLayout, 0);
        if (mKeyPreviewLayoutId == 0) {
            mShowKeyPreviewPopup = false;
        }
        mMoreKeysLayout = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_moreKeysKeyboardLayout, 0);
        mConfigShowMoreKeysKeyboardAtTouchedPoint = mainKeyboardViewAttr.getBoolean(
                R.styleable.MainKeyboardView_showMoreKeysKeyboardAtTouchedPoint, false);

        mGestureFloatingPreviewTextLingerTimeout = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_gestureFloatingPreviewTextLingerTimeout, 0);
        PointerTracker.setParameters(mainKeyboardViewAttr);
        mainKeyboardViewAttr.recycle();

        mLanguageOnSpacebarFadeoutAnimator = loadObjectAnimator(
                languageOnSpacebarFadeoutAnimatorResId, this);
        mAltCodeKeyWhileTypingFadeoutAnimator = loadObjectAnimator(
                altCodeKeyWhileTypingFadeoutAnimatorResId, this);
        mAltCodeKeyWhileTypingFadeinAnimator = loadObjectAnimator(
                altCodeKeyWhileTypingFadeinAnimatorResId, this);

        mPreviewPlacerView = new PreviewPlacerView(context, attrs);
    }

    private ObjectAnimator loadObjectAnimator(final int resId, final Object target) {
        if (resId == 0) {
            return null;
        }
        final ObjectAnimator animator = (ObjectAnimator)AnimatorInflater.loadAnimator(
                getContext(), resId);
        if (animator != null) {
            animator.setTarget(target);
        }
        return animator;
    }

    @ExternallyReferenced
    public int getLanguageOnSpacebarAnimAlpha() {
        return mLanguageOnSpacebarAnimAlpha;
    }

    @ExternallyReferenced
    public void setLanguageOnSpacebarAnimAlpha(final int alpha) {
        mLanguageOnSpacebarAnimAlpha = alpha;
        invalidateKey(mSpaceKey);
    }

    @ExternallyReferenced
    public int getAltCodeKeyWhileTypingAnimAlpha() {
        return mAltCodeKeyWhileTypingAnimAlpha;
    }

    @ExternallyReferenced
    public void setAltCodeKeyWhileTypingAnimAlpha(final int alpha) {
        mAltCodeKeyWhileTypingAnimAlpha = alpha;
        updateAltCodeKeyWhileTyping();
    }

    public void setKeyboardActionListener(final KeyboardActionListener listener) {
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
    public void setKeyboard(final Keyboard keyboard) {
        // Remove any pending messages, except dismissing preview and key repeat.
        mKeyTimerHandler.cancelLongPressTimer();
        super.setKeyboard(keyboard);
        mKeyDetector.setKeyboard(
                keyboard, -getPaddingLeft(), -getPaddingTop() + mVerticalCorrection);
        PointerTracker.setKeyDetector(mKeyDetector);
        mTouchScreenRegulator.setKeyboardGeometry(keyboard.mOccupiedWidth);
        mMoreKeysPanelCache.clear();

        mSpaceKey = keyboard.getKey(Constants.CODE_SPACE);
        mSpaceIcon = (mSpaceKey != null)
                ? mSpaceKey.getIcon(keyboard.mIconsSet, Constants.Color.ALPHA_OPAQUE) : null;
        final int keyHeight = keyboard.mMostCommonKeyHeight - keyboard.mVerticalGap;
        mSpacebarTextSize = keyHeight * mSpacebarTextRatio;
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.mainKeyboardView_setKeyboard(keyboard);
        }

        // This always needs to be set since the accessibility state can
        // potentially change without the keyboard being set again.
        AccessibleKeyboardViewProxy.getInstance().setKeyboard();
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
        mKeyPreviewLingerTimeout = delay;
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
        getLocationInWindow(mOriginCoords);
        final DisplayMetrics dm = getResources().getDisplayMetrics();
        if (CoordinateUtils.y(mOriginCoords) < dm.heightPixels / 4) {
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
            mPreviewPlacerView.setKeyboardViewGeometry(mOriginCoords, width, height);
        }
    }

    /**
     * Returns the enabled state of the key feedback preview
     * @return whether or not the key feedback preview is enabled
     * @see #setKeyPreviewPopupEnabled(boolean, int)
     */
    public boolean isKeyPreviewPopupEnabled() {
        return mShowKeyPreviewPopup;
    }

    private void addKeyPreview(final TextView keyPreview) {
        locatePreviewPlacerView();
        mPreviewPlacerView.addView(
                keyPreview, ViewLayoutUtils.newLayoutParam(mPreviewPlacerView, 0, 0));
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

    @Override
    public void showKeyPreview(final PointerTracker tracker) {
        final KeyPreviewDrawParams previewParams = mKeyPreviewDrawParams;
        final Keyboard keyboard = getKeyboard();
        if (!mShowKeyPreviewPopup) {
            previewParams.mPreviewVisibleOffset = -keyboard.mVerticalGap;
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
                    key.getPreviewIcon(keyboard.mIconsSet));
            previewText.setText(null);
        }

        previewText.measure(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        final int keyDrawWidth = key.getDrawWidth();
        final int previewWidth = previewText.getMeasuredWidth();
        final int previewHeight = mKeyPreviewHeight;
        // The width and height of visible part of the key preview background. The content marker
        // of the background 9-patch have to cover the visible part of the background.
        previewParams.mPreviewVisibleWidth = previewWidth - previewText.getPaddingLeft()
                - previewText.getPaddingRight();
        previewParams.mPreviewVisibleHeight = previewHeight - previewText.getPaddingTop()
                - previewText.getPaddingBottom();
        // The distance between the top edge of the parent key and the bottom of the visible part
        // of the key preview background.
        previewParams.mPreviewVisibleOffset = mKeyPreviewOffset - previewText.getPaddingBottom();
        getLocationInWindow(mOriginCoords);
        // The key preview is horizontally aligned with the center of the visible part of the
        // parent key. If it doesn't fit in this {@link KeyboardView}, it is moved inward to fit and
        // the left/right background is used if such background is specified.
        final int statePosition;
        int previewX = key.getDrawX() - (previewWidth - keyDrawWidth) / 2
                + CoordinateUtils.x(mOriginCoords);
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
        final int previewY = key.mY - previewHeight + mKeyPreviewOffset
                + CoordinateUtils.y(mOriginCoords);

        if (background != null) {
            final int hasMoreKeys = (key.mMoreKeys != null) ? STATE_HAS_MOREKEYS : STATE_NORMAL;
            background.setState(KEY_PREVIEW_BACKGROUND_STATE_TABLE[statePosition][hasMoreKeys]);
        }
        ViewLayoutUtils.placeViewAt(
                previewText, previewX, previewY, previewWidth, previewHeight);
        previewText.setVisibility(VISIBLE);
    }

    @Override
    public void dismissKeyPreview(final PointerTracker tracker) {
        mDrawingHandler.dismissKeyPreview(mKeyPreviewLingerTimeout, tracker);
    }

    @Override
    public void showSlidingKeyInputPreview(final PointerTracker tracker) {
        locatePreviewPlacerView();
        mPreviewPlacerView.showSlidingKeyInputPreview(tracker);
    }

    @Override
    public void dismissSlidingKeyInputPreview() {
        mPreviewPlacerView.dismissSlidingKeyInputPreview();
    }

    public void setGesturePreviewMode(final boolean drawsGesturePreviewTrail,
            final boolean drawsGestureFloatingPreviewText) {
        mPreviewPlacerView.setGesturePreviewMode(
                drawsGesturePreviewTrail, drawsGestureFloatingPreviewText);
    }

    public void showGestureFloatingPreviewText(final SuggestedWords suggestedWords) {
        locatePreviewPlacerView();
        mPreviewPlacerView.setGestureFloatingPreviewText(suggestedWords);
    }

    public void dismissGestureFloatingPreviewText() {
        locatePreviewPlacerView();
        mDrawingHandler.dismissGestureFloatingPreviewText(mGestureFloatingPreviewTextLingerTimeout);
    }

    public void showGesturePreviewTrail(final PointerTracker tracker) {
        locatePreviewPlacerView();
        mPreviewPlacerView.invalidatePointer(tracker);
    }

    // Note that this method is called from a non-UI thread.
    public void setMainDictionaryAvailability(final boolean mainDictionaryAvailable) {
        PointerTracker.setMainDictionaryAvailability(mainDictionaryAvailable);
    }

    public void setGestureHandlingEnabledByUser(final boolean gestureHandlingEnabledByUser) {
        PointerTracker.setGestureHandlingEnabledByUser(gestureHandlingEnabledByUser);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Notify the research logger that the keyboard view has been attached.  This is needed
        // to properly show the splash screen, which requires that the window token of the
        // KeyboardView be non-null.
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.getInstance().mainKeyboardView_onAttachedToWindow(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mPreviewPlacerView.removeAllViews();
        // Notify the research logger that the keyboard view has been detached.  This is needed
        // to invalidate the reference of {@link MainKeyboardView} to null.
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.getInstance().mainKeyboardView_onDetachedFromWindow();
        }
    }

    private boolean openMoreKeysKeyboardIfRequired(final Key parentKey,
            final PointerTracker tracker) {
        // Check if we have a popup layout specified first.
        if (mMoreKeysLayout == 0) {
            return false;
        }

        // Check if we are already displaying popup panel.
        if (mMoreKeysPanel != null) {
            return false;
        }
        if (parentKey == null) {
            return false;
        }
        return onLongPress(parentKey, tracker);
    }

    private MoreKeysPanel onCreateMoreKeysPanel(final Key parentKey) {
        if (parentKey.mMoreKeys == null) {
            return null;
        }

        final View container = LayoutInflater.from(getContext()).inflate(mMoreKeysLayout, null);
        if (container == null) {
            throw new NullPointerException();
        }

        final MoreKeysKeyboardView moreKeysKeyboardView =
                (MoreKeysKeyboardView)container.findViewById(R.id.more_keys_keyboard_view);
        final Keyboard moreKeysKeyboard = new MoreKeysKeyboard.Builder(
                container, parentKey, this, mKeyPreviewDrawParams)
                .build();
        moreKeysKeyboardView.setKeyboard(moreKeysKeyboard);
        container.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        return moreKeysKeyboardView;
    }

    /**
     * Called when a key is long pressed.
     * @param parentKey the key that was long pressed
     * @param tracker the pointer tracker which pressed the parent key
     * @return true if the long press is handled, false otherwise. Subclasses should call the
     * method on the base class if the subclass doesn't wish to handle the call.
     */
    private boolean onLongPress(final Key parentKey, final PointerTracker tracker) {
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.mainKeyboardView_onLongPress();
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
        if (primaryCode == Constants.CODE_SPACE || primaryCode == Constants.CODE_LANGUAGE_SWITCH) {
            // Long pressing the space key invokes IME switcher dialog.
            if (invokeCustomRequest(LatinIME.CODE_SHOW_INPUT_METHOD_PICKER)) {
                tracker.onLongPressed();
                invokeReleaseKey(primaryCode);
                return true;
            }
        }
        return openMoreKeysPanel(parentKey, tracker);
    }

    private boolean invokeCustomRequest(final int code) {
        return mKeyboardActionListener.onCustomRequest(code);
    }

    private void invokeCodeInput(final int primaryCode) {
        mKeyboardActionListener.onCodeInput(
                primaryCode, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE);
    }

    private void invokeReleaseKey(final int primaryCode) {
        mKeyboardActionListener.onReleaseKey(primaryCode, false);
    }

    private boolean openMoreKeysPanel(final Key parentKey, final PointerTracker tracker) {
        MoreKeysPanel moreKeysPanel = mMoreKeysPanelCache.get(parentKey);
        if (moreKeysPanel == null) {
            moreKeysPanel = onCreateMoreKeysPanel(parentKey);
            if (moreKeysPanel == null) {
                return false;
            }
            mMoreKeysPanelCache.put(parentKey, moreKeysPanel);
        }

        final int[] lastCoords = CoordinateUtils.newInstance();
        tracker.getLastCoordinates(lastCoords);
        final boolean keyPreviewEnabled = isKeyPreviewPopupEnabled() && !parentKey.noKeyPreview();
        // The more keys keyboard is usually horizontally aligned with the center of the parent key.
        // If showMoreKeysKeyboardAtTouchedPoint is true and the key preview is disabled, the more
        // keys keyboard is placed at the touch point of the parent key.
        final int pointX = (mConfigShowMoreKeysKeyboardAtTouchedPoint && !keyPreviewEnabled)
                ? CoordinateUtils.x(lastCoords)
                : parentKey.mX + parentKey.mWidth / 2;
        // The more keys keyboard is usually vertically aligned with the top edge of the parent key
        // (plus vertical gap). If the key preview is enabled, the more keys keyboard is vertically
        // aligned with the bottom edge of the visible part of the key preview.
        // {@code mPreviewVisibleOffset} has been set appropriately in
        // {@link KeyboardView#showKeyPreview(PointerTracker)}.
        final int pointY = parentKey.mY + mKeyPreviewDrawParams.mPreviewVisibleOffset;
        moreKeysPanel.showMoreKeysPanel(this, this, pointX, pointY, mKeyboardActionListener);
        final int translatedX = moreKeysPanel.translateX(CoordinateUtils.x(lastCoords));
        final int translatedY = moreKeysPanel.translateY(CoordinateUtils.y(lastCoords));
        tracker.onShowMoreKeysPanel(translatedX, translatedY, moreKeysPanel);
        dimEntireKeyboard(true /* dimmed */);
        return true;
    }

    public boolean isInSlidingKeyInput() {
        if (mMoreKeysPanel != null) {
            return true;
        }
        return PointerTracker.isAnyInSlidingKeyInput();
    }

    @Override
    public void onShowMoreKeysPanel(final MoreKeysPanel panel) {
        if (isShowingMoreKeysPanel()) {
            onDismissMoreKeysPanel();
        }
        mMoreKeysPanel = panel;
        mPreviewPlacerView.addView(mMoreKeysPanel.getContainerView());
    }

    public boolean isShowingMoreKeysPanel() {
        return (mMoreKeysPanel != null);
    }

    @Override
    public void onCancelMoreKeysPanel() {
        if (isShowingMoreKeysPanel()) {
            mMoreKeysPanel.dismissMoreKeysPanel();
        }
        PointerTracker.dismissAllMoreKeysPanels();
    }

    @Override
    public boolean onDismissMoreKeysPanel() {
        dimEntireKeyboard(false /* dimmed */);
        if (isShowingMoreKeysPanel()) {
            mPreviewPlacerView.removeView(mMoreKeysPanel.getContainerView());
            mMoreKeysPanel = null;
            return true;
        }
        return false;
    }

    public int getPointerCount() {
        return mOldPointerCount;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (AccessibilityUtils.getInstance().isTouchExplorationEnabled()) {
            return AccessibleKeyboardViewProxy.getInstance().dispatchTouchEvent(event);
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(final MotionEvent me) {
        if (getKeyboard() == null) {
            return false;
        }
        return mTouchScreenRegulator.onTouchEvent(me);
    }

    @Override
    public boolean processMotionEvent(final MotionEvent me) {
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
        final int x = (int)me.getX(index);
        final int y = (int)me.getY(index);

        // TODO: This might be moved to the tracker.processMotionEvent() call below.
        if (ENABLE_USABILITY_STUDY_LOG && action != MotionEvent.ACTION_MOVE) {
            writeUsabilityStudyLog(me, action, eventTime, index, id, x, y);
        }
        // TODO: This should be moved to the tracker.processMotionEvent() call below.
        // Currently the same "move" event is being logged twice.
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.mainKeyboardView_processMotionEvent(
                    me, action, eventTime, index, id, x, y);
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
                    if (action == MotionEvent.ACTION_UP) {
                        tracker.onUpEvent(x, y, eventTime);
                    }
                }
            } else if (pointerCount == 2 && oldPointerCount == 1) {
                // Single-touch to multi-touch transition.
                // Send an up event for the last pointer.
                final int[] lastCoords = CoordinateUtils.newInstance();
                mOldKey = tracker.getKeyOn(
                        CoordinateUtils.x(lastCoords), CoordinateUtils.y(lastCoords));
                tracker.onUpEvent(
                        CoordinateUtils.x(lastCoords), CoordinateUtils.y(lastCoords), eventTime);
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
                final int px = (int)me.getX(i);
                final int py = (int)me.getY(i);
                tracker.onMoveEvent(px, py, eventTime, me);
                if (ENABLE_USABILITY_STUDY_LOG) {
                    writeUsabilityStudyLog(me, action, eventTime, i, pointerId, px, py);
                }
                if (ProductionFlag.IS_EXPERIMENTAL) {
                    ResearchLogger.mainKeyboardView_processMotionEvent(
                            me, action, eventTime, i, pointerId, px, py);
                }
            }
        } else {
            final PointerTracker tracker = PointerTracker.getPointerTracker(id, this);
            tracker.processMotionEvent(action, x, y, eventTime, this);
        }

        return true;
    }

    private static void writeUsabilityStudyLog(final MotionEvent me, final int action,
            final long eventTime, final int index, final int id, final int x, final int y) {
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
        case MotionEvent.ACTION_MOVE:
            eventTag = "[Move]";
            break;
        default:
            eventTag = "[Action" + action + "]";
            break;
        }
        final float size = me.getSize(index);
        final float pressure = me.getPressure(index);
        UsabilityStudyLogUtils.getInstance().write(
                eventTag + eventTime + "," + id + "," + x + "," + y + "," + size + "," + pressure);
    }

    public void cancelAllMessages() {
        mKeyTimerHandler.cancelAllMessages();
        mDrawingHandler.cancelAllMessages();
    }

    @Override
    public void closing() {
        dismissAllKeyPreviews();
        cancelAllMessages();
        super.closing();
        onCancelMoreKeysPanel();
        mMoreKeysPanelCache.clear();
    }

    /**
     * Receives hover events from the input framework.
     *
     * @param event The motion event to be dispatched.
     * @return {@code true} if the event was handled by the view, {@code false}
     *         otherwise
     */
    @Override
    public boolean dispatchHoverEvent(final MotionEvent event) {
        if (AccessibilityUtils.getInstance().isTouchExplorationEnabled()) {
            final PointerTracker tracker = PointerTracker.getPointerTracker(0, this);
            return AccessibleKeyboardViewProxy.getInstance().dispatchHoverEvent(event, tracker);
        }

        // Reflection doesn't support calling superclass methods.
        return false;
    }

    public void updateShortcutKey(final boolean available) {
        final Keyboard keyboard = getKeyboard();
        if (keyboard == null) {
            return;
        }
        final Key shortcutKey = keyboard.getKey(Constants.CODE_SHORTCUT);
        if (shortcutKey == null) {
            return;
        }
        shortcutKey.setEnabled(available);
        invalidateKey(shortcutKey);
    }

    private void updateAltCodeKeyWhileTyping() {
        final Keyboard keyboard = getKeyboard();
        if (keyboard == null) {
            return;
        }
        for (final Key key : keyboard.mAltCodeKeysWhileTyping) {
            invalidateKey(key);
        }
    }

    public void startDisplayLanguageOnSpacebar(final boolean subtypeChanged,
            final boolean needsToDisplayLanguage, final boolean hasMultipleEnabledIMEsOrSubtypes) {
        mNeedsToDisplayLanguage = needsToDisplayLanguage;
        mHasMultipleEnabledIMEsOrSubtypes = hasMultipleEnabledIMEsOrSubtypes;
        final ObjectAnimator animator = mLanguageOnSpacebarFadeoutAnimator;
        if (animator == null) {
            mNeedsToDisplayLanguage = false;
        } else {
            if (subtypeChanged && needsToDisplayLanguage) {
                setLanguageOnSpacebarAnimAlpha(Constants.Color.ALPHA_OPAQUE);
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

    public void updateAutoCorrectionState(final boolean isAutoCorrection) {
        if (!mAutoCorrectionSpacebarLedEnabled) {
            return;
        }
        mAutoCorrectionSpacebarLedOn = isAutoCorrection;
        invalidateKey(mSpaceKey);
    }

    public void dimEntireKeyboard(final boolean dimmed) {
        final boolean needsRedrawing = mNeedsToDimEntireKeyboard != dimmed;
        mNeedsToDimEntireKeyboard = dimmed;
        if (needsRedrawing) {
            invalidateAllKeys();
        }
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);

        // Overlay a dark rectangle to dim.
        if (mNeedsToDimEntireKeyboard) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), mBackgroundDimAlphaPaint);
        }
    }

    @Override
    protected void onDrawKeyTopVisuals(final Key key, final Canvas canvas, final Paint paint,
            final KeyDrawParams params) {
        if (key.altCodeWhileTyping() && key.isEnabled()) {
            params.mAnimAlpha = mAltCodeKeyWhileTypingAnimAlpha;
        }
        if (key.mCode == Constants.CODE_SPACE) {
            drawSpacebar(key, canvas, paint);
            // Whether space key needs to show the "..." popup hint for special purposes
            if (key.isLongPressEnabled() && mHasMultipleEnabledIMEsOrSubtypes) {
                drawKeyPopupHint(key, canvas, paint, params);
            }
        } else if (key.mCode == Constants.CODE_LANGUAGE_SWITCH) {
            super.onDrawKeyTopVisuals(key, canvas, paint, params);
            drawKeyPopupHint(key, canvas, paint, params);
        } else {
            super.onDrawKeyTopVisuals(key, canvas, paint, params);
        }
    }

    private boolean fitsTextIntoWidth(final int width, final String text, final Paint paint) {
        paint.setTextScaleX(1.0f);
        final float textWidth = getLabelWidth(text, paint);
        if (textWidth < width) {
            return true;
        }

        final float scaleX = width / textWidth;
        if (scaleX < MINIMUM_XSCALE_OF_LANGUAGE_NAME) {
            return false;
        }

        paint.setTextScaleX(scaleX);
        return getLabelWidth(text, paint) < width;
    }

    // Layout language name on spacebar.
    private String layoutLanguageOnSpacebar(final Paint paint, final InputMethodSubtype subtype,
            final int width) {
        // Choose appropriate language name to fit into the width.
        final String fullText = getFullDisplayName(subtype, getResources());
        if (fitsTextIntoWidth(width, fullText, paint)) {
            return fullText;
        }

        final String middleText = getMiddleDisplayName(subtype);
        if (fitsTextIntoWidth(width, middleText, paint)) {
            return middleText;
        }

        final String shortText = getShortDisplayName(subtype);
        if (fitsTextIntoWidth(width, shortText, paint)) {
            return shortText;
        }

        return "";
    }

    private void drawSpacebar(final Key key, final Canvas canvas, final Paint paint) {
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
    // locale layout  | Short  Middle      Full
    // ------ ------- - ---- --------- ----------------------
    //  en_US qwerty  F  En  English   English (US)           exception
    //  en_GB qwerty  F  En  English   English (UK)           exception
    //  es_US spanish F  Es  Español   Español (EE.UU.)       exception
    //  fr    azerty  F  Fr  Français  Français
    //  fr_CA qwerty  F  Fr  Français  Français (Canada)
    //  de    qwertz  F  De  Deutsch   Deutsch
    //  zz    qwerty  F      QWERTY    QWERTY
    //  fr    qwertz  T  Fr  Français  Français (QWERTZ)
    //  de    qwerty  T  De  Deutsch   Deutsch (QWERTY)
    //  en_US azerty  T  En  English   English (US) (AZERTY)
    //  zz    azerty  T      AZERTY    AZERTY

    // Get InputMethodSubtype's full display name in its locale.
    static String getFullDisplayName(final InputMethodSubtype subtype, final Resources res) {
        if (SubtypeLocale.isNoLanguage(subtype)) {
            return SubtypeLocale.getKeyboardLayoutSetDisplayName(subtype);
        }

        return SubtypeLocale.getSubtypeDisplayName(subtype, res);
    }

    // Get InputMethodSubtype's short display name in its locale.
    static String getShortDisplayName(final InputMethodSubtype subtype) {
        if (SubtypeLocale.isNoLanguage(subtype)) {
            return "";
        }
        final Locale locale = SubtypeLocale.getSubtypeLocale(subtype);
        return StringUtils.toTitleCase(locale.getLanguage(), locale);
    }

    // Get InputMethodSubtype's middle display name in its locale.
    static String getMiddleDisplayName(final InputMethodSubtype subtype) {
        if (SubtypeLocale.isNoLanguage(subtype)) {
            return SubtypeLocale.getKeyboardLayoutSetDisplayName(subtype);
        }
        final Locale locale = SubtypeLocale.getSubtypeLocale(subtype);
        return StringUtils.toTitleCase(locale.getDisplayLanguage(locale), locale);
    }
}
