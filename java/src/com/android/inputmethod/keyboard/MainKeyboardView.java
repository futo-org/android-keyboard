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
import com.android.inputmethod.keyboard.internal.KeyDrawParams;
import com.android.inputmethod.keyboard.internal.SuddenJumpingTouchEventHandler;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.ResourceUtils;
import com.android.inputmethod.latin.StaticInnerHandlerWrapper;
import com.android.inputmethod.latin.StringUtils;
import com.android.inputmethod.latin.SubtypeLocale;
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
 * @attr ref R.styleable#MainKeyboardView_showMoreKeysKeyboardAtTouchPoint
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
        SuddenJumpingTouchEventHandler.ProcessMotionEvent {
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

    // More keys keyboard
    private PopupWindow mMoreKeysWindow;
    private MoreKeysPanel mMoreKeysPanel;
    private int mMoreKeysPanelPointerTrackerId;
    private final WeakHashMap<Key, MoreKeysPanel> mMoreKeysPanelCache =
            new WeakHashMap<Key, MoreKeysPanel>();
    private final boolean mConfigShowMoreKeysKeyboardAtTouchedPoint;

    private final SuddenJumpingTouchEventHandler mTouchScreenRegulator;

    protected KeyDetector mKeyDetector;
    private boolean mHasDistinctMultitouch;
    private int mOldPointerCount = 1;
    private Key mOldKey;

    private final KeyTimerHandler mKeyTimerHandler;

    private static final class KeyTimerHandler extends StaticInnerHandlerWrapper<MainKeyboardView>
            implements TimerProxy {
        private static final int MSG_TYPING_STATE_EXPIRED = 0;
        private static final int MSG_REPEAT_KEY = 1;
        private static final int MSG_LONGPRESS_KEY = 2;
        private static final int MSG_DOUBLE_TAP = 3;

        private final int mKeyRepeatStartTimeout;
        private final int mKeyRepeatInterval;
        private final int mLongPressKeyTimeout;
        private final int mLongPressShiftKeyTimeout;
        private final int mIgnoreAltCodeKeyTimeout;

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
            }
        }

        private void startKeyRepeatTimer(final PointerTracker tracker, final long delay) {
            final Key key = tracker.getKey();
            if (key == null) return;
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
            case Keyboard.CODE_SHIFT:
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
            case Keyboard.CODE_SHIFT:
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
            if (typedCode == Keyboard.CODE_SPACE || typedCode == Keyboard.CODE_ENTER) {
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

        public void cancelAllMessages() {
            cancelKeyTimers();
        }
    }

    public MainKeyboardView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.mainKeyboardViewStyle);
    }

    public MainKeyboardView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        mTouchScreenRegulator = new SuddenJumpingTouchEventHandler(getContext(), this);

        mHasDistinctMultitouch = context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT);
        final Resources res = getResources();
        final boolean needsPhantomSuddenMoveEventHack = Boolean.parseBoolean(
                ResourceUtils.getDeviceOverrideValue(res,
                        R.array.phantom_sudden_move_event_device_list, "false"));
        PointerTracker.init(mHasDistinctMultitouch, needsPhantomSuddenMoveEventHack);

        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.MainKeyboardView, defStyle, R.style.MainKeyboardView);
        mAutoCorrectionSpacebarLedEnabled = a.getBoolean(
                R.styleable.MainKeyboardView_autoCorrectionSpacebarLedEnabled, false);
        mAutoCorrectionSpacebarLedIcon = a.getDrawable(
                R.styleable.MainKeyboardView_autoCorrectionSpacebarLedIcon);
        mSpacebarTextRatio = a.getFraction(
                R.styleable.MainKeyboardView_spacebarTextRatio, 1, 1, 1.0f);
        mSpacebarTextColor = a.getColor(R.styleable.MainKeyboardView_spacebarTextColor, 0);
        mSpacebarTextShadowColor = a.getColor(
                R.styleable.MainKeyboardView_spacebarTextShadowColor, 0);
        mLanguageOnSpacebarFinalAlpha = a.getInt(
                R.styleable.MainKeyboardView_languageOnSpacebarFinalAlpha,
                Constants.Color.ALPHA_OPAQUE);
        final int languageOnSpacebarFadeoutAnimatorResId = a.getResourceId(
                R.styleable.MainKeyboardView_languageOnSpacebarFadeoutAnimator, 0);
        final int altCodeKeyWhileTypingFadeoutAnimatorResId = a.getResourceId(
                R.styleable.MainKeyboardView_altCodeKeyWhileTypingFadeoutAnimator, 0);
        final int altCodeKeyWhileTypingFadeinAnimatorResId = a.getResourceId(
                R.styleable.MainKeyboardView_altCodeKeyWhileTypingFadeinAnimator, 0);

        final float keyHysteresisDistance = a.getDimension(
                R.styleable.MainKeyboardView_keyHysteresisDistance, 0);
        final float keyHysteresisDistanceForSlidingModifier = a.getDimension(
                R.styleable.MainKeyboardView_keyHysteresisDistanceForSlidingModifier, 0);
        mKeyDetector = new KeyDetector(
                keyHysteresisDistance, keyHysteresisDistanceForSlidingModifier);
        mKeyTimerHandler = new KeyTimerHandler(this, a);
        mConfigShowMoreKeysKeyboardAtTouchedPoint = a.getBoolean(
                R.styleable.MainKeyboardView_showMoreKeysKeyboardAtTouchedPoint, false);
        PointerTracker.setParameters(a);
        a.recycle();

        mLanguageOnSpacebarFadeoutAnimator = loadObjectAnimator(
                languageOnSpacebarFadeoutAnimatorResId, this);
        mAltCodeKeyWhileTypingFadeoutAnimator = loadObjectAnimator(
                altCodeKeyWhileTypingFadeoutAnimatorResId, this);
        mAltCodeKeyWhileTypingFadeinAnimator = loadObjectAnimator(
                altCodeKeyWhileTypingFadeinAnimatorResId, this);
    }

    private ObjectAnimator loadObjectAnimator(final int resId, final Object target) {
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

    public void setLanguageOnSpacebarAnimAlpha(final int alpha) {
        mLanguageOnSpacebarAnimAlpha = alpha;
        invalidateKey(mSpaceKey);
    }

    public int getAltCodeKeyWhileTypingAnimAlpha() {
        return mAltCodeKeyWhileTypingAnimAlpha;
    }

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
        mTouchScreenRegulator.setKeyboard(keyboard);
        mMoreKeysPanelCache.clear();

        mSpaceKey = keyboard.getKey(Keyboard.CODE_SPACE);
        mSpaceIcon = (mSpaceKey != null)
                ? mSpaceKey.getIcon(keyboard.mIconsSet, Constants.Color.ALPHA_OPAQUE) : null;
        final int keyHeight = keyboard.mMostCommonKeyHeight - keyboard.mVerticalGap;
        mSpacebarTextSize = keyHeight * mSpacebarTextRatio;
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.mainKeyboardView_setKeyboard(keyboard);
        }

        // This always needs to be set since the accessibility state can
        // potentially change without the keyboard being set again.
        AccessibleKeyboardViewProxy.getInstance().setKeyboard(keyboard);
    }

    // Note that this method is called from a non-UI thread.
    public void setMainDictionaryAvailability(final boolean mainDictionaryAvailable) {
        PointerTracker.setMainDictionaryAvailability(mainDictionaryAvailable);
    }

    public void setGestureHandlingEnabledByUser(final boolean gestureHandlingEnabledByUser) {
        PointerTracker.setGestureHandlingEnabledByUser(gestureHandlingEnabledByUser);
    }

    /**
     * Returns whether the device has distinct multi-touch panel.
     * @return true if the device has distinct multi-touch panel.
     */
    public boolean hasDistinctMultitouch() {
        return mHasDistinctMultitouch;
    }

    public void setDistinctMultitouch(final boolean hasDistinctMultitouch) {
        mHasDistinctMultitouch = hasDistinctMultitouch;
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
        // Notify the research logger that the keyboard view has been detached.  This is needed
        // to invalidate the reference of {@link MainKeyboardView} to null.
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.getInstance().mainKeyboardView_onDetachedFromWindow();
        }
    }

    @Override
    public void cancelAllMessages() {
        mKeyTimerHandler.cancelAllMessages();
        super.cancelAllMessages();
    }

    private boolean openMoreKeysKeyboardIfRequired(final Key parentKey,
            final PointerTracker tracker) {
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
    protected MoreKeysPanel onCreateMoreKeysPanel(final Key parentKey) {
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
    protected boolean onLongPress(final Key parentKey, final PointerTracker tracker) {
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
        // {@code mPreviewVisibleOffset} has been set appropriately in
        // {@link KeyboardView#showKeyPreview(PointerTracker)}.
        final int pointY = parentKey.mY + mKeyPreviewDrawParams.mPreviewVisibleOffset;
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
            ResearchLogger.mainKeyboardView_processMotionEvent(me, action, eventTime, index, id,
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
                final MotionEvent motionEvent;
                if (mMoreKeysPanel != null
                        && tracker.mPointerId == mMoreKeysPanelPointerTrackerId) {
                    px = mMoreKeysPanel.translateX((int)me.getX(i));
                    py = mMoreKeysPanel.translateY((int)me.getY(i));
                    motionEvent = null;
                } else {
                    px = (int)me.getX(i);
                    py = (int)me.getY(i);
                    motionEvent = me;
                }
                tracker.onMoveEvent(px, py, eventTime, motionEvent);
                if (ENABLE_USABILITY_STUDY_LOG) {
                    final float pointerSize = me.getSize(i);
                    final float pointerPressure = me.getPressure(i);
                    UsabilityStudyLogUtils.getInstance().write("[Move]"  + eventTime + ","
                            + pointerId + "," + px + "," + py + ","
                            + pointerSize + "," + pointerPressure);
                }
                if (ProductionFlag.IS_EXPERIMENTAL) {
                    ResearchLogger.mainKeyboardView_processMotionEvent(me, action, eventTime,
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
        if (!mAutoCorrectionSpacebarLedEnabled) return;
        mAutoCorrectionSpacebarLedOn = isAutoCorrection;
        invalidateKey(mSpaceKey);
    }

    @Override
    protected void onDrawKeyTopVisuals(final Key key, final Canvas canvas, final Paint paint,
            final KeyDrawParams params) {
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

    private boolean fitsTextIntoWidth(final int width, final String text, final Paint paint) {
        paint.setTextScaleX(1.0f);
        final float textWidth = getLabelWidth(text, paint);
        if (textWidth < width) return true;

        final float scaleX = width / textWidth;
        if (scaleX < MINIMUM_XSCALE_OF_LANGUAGE_NAME) return false;

        paint.setTextScaleX(scaleX);
        return getLabelWidth(text, paint) < width;
    }

    // Layout language name on spacebar.
    private String layoutLanguageOnSpacebar(final Paint paint, final InputMethodSubtype subtype,
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
