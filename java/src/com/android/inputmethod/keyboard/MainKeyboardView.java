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
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.TextView;

import com.android.inputmethod.accessibility.AccessibilityUtils;
import com.android.inputmethod.accessibility.AccessibleKeyboardViewProxy;
import com.android.inputmethod.annotations.ExternallyReferenced;
import com.android.inputmethod.keyboard.internal.DrawingHandler;
import com.android.inputmethod.keyboard.internal.DrawingPreviewPlacerView;
import com.android.inputmethod.keyboard.internal.GestureFloatingTextDrawingPreview;
import com.android.inputmethod.keyboard.internal.GestureTrailsDrawingPreview;
import com.android.inputmethod.keyboard.internal.KeyDrawParams;
import com.android.inputmethod.keyboard.internal.KeyPreviewChoreographer;
import com.android.inputmethod.keyboard.internal.KeyPreviewDrawParams;
import com.android.inputmethod.keyboard.internal.NonDistinctMultitouchHelper;
import com.android.inputmethod.keyboard.internal.SlidingKeyInputDrawingPreview;
import com.android.inputmethod.keyboard.internal.TimerHandler;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.define.ProductionFlag;
import com.android.inputmethod.latin.settings.DebugSettings;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.CoordinateUtils;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;
import com.android.inputmethod.latin.utils.TypefaceUtils;
import com.android.inputmethod.latin.utils.UsabilityStudyLogUtils;
import com.android.inputmethod.latin.utils.ViewLayoutUtils;
import com.android.inputmethod.research.ResearchLogger;

import java.util.WeakHashMap;

/**
 * A view that is responsible for detecting key presses and touch movements.
 *
 * @attr ref R.styleable#MainKeyboardView_autoCorrectionSpacebarLedEnabled
 * @attr ref R.styleable#MainKeyboardView_autoCorrectionSpacebarLedIcon
 * @attr ref R.styleable#MainKeyboardView_languageOnSpacebarTextRatio
 * @attr ref R.styleable#MainKeyboardView_languageOnSpacebarTextColor
 * @attr ref R.styleable#MainKeyboardView_languageOnSpacebarTextShadowColor
 * @attr ref R.styleable#MainKeyboardView_spacebarBackground
 * @attr ref R.styleable#MainKeyboardView_languageOnSpacebarFinalAlpha
 * @attr ref R.styleable#MainKeyboardView_languageOnSpacebarFadeoutAnimator
 * @attr ref R.styleable#MainKeyboardView_altCodeKeyWhileTypingFadeoutAnimator
 * @attr ref R.styleable#MainKeyboardView_altCodeKeyWhileTypingFadeinAnimator
 * @attr ref R.styleable#MainKeyboardView_keyHysteresisDistance
 * @attr ref R.styleable#MainKeyboardView_touchNoiseThresholdTime
 * @attr ref R.styleable#MainKeyboardView_touchNoiseThresholdDistance
 * @attr ref R.styleable#MainKeyboardView_keySelectionByDraggingFinger
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
public final class MainKeyboardView extends KeyboardView implements PointerTracker.DrawingProxy,
        MoreKeysPanel.Controller, DrawingHandler.Callbacks, TimerHandler.Callbacks {
    private static final String TAG = MainKeyboardView.class.getSimpleName();

    /** Listener for {@link KeyboardActionListener}. */
    private KeyboardActionListener mKeyboardActionListener;

    /* Space key and its icon and background. */
    private Key mSpaceKey;
    private Drawable mSpacebarIcon;
    private final Drawable mSpacebarBackground;
    // Stuff to draw language name on spacebar.
    private final int mLanguageOnSpacebarFinalAlpha;
    private ObjectAnimator mLanguageOnSpacebarFadeoutAnimator;
    private boolean mNeedsToDisplayLanguage;
    private boolean mHasMultipleEnabledIMEsOrSubtypes;
    private int mLanguageOnSpacebarAnimAlpha = Constants.Color.ALPHA_OPAQUE;
    private final float mLanguageOnSpacebarTextRatio;
    private float mLanguageOnSpacebarTextSize;
    private final int mLanguageOnSpacebarTextColor;
    private final int mLanguageOnSpacebarTextShadowColor;
    // The minimum x-scale to fit the language name on spacebar.
    private static final float MINIMUM_XSCALE_OF_LANGUAGE_NAME = 0.8f;
    // Stuff to draw auto correction LED on spacebar.
    private boolean mAutoCorrectionSpacebarLedOn;
    private final boolean mAutoCorrectionSpacebarLedEnabled;
    private final Drawable mAutoCorrectionSpacebarLedIcon;
    private static final int SPACE_LED_LENGTH_PERCENT = 80;

    // Stuff to draw altCodeWhileTyping keys.
    private final ObjectAnimator mAltCodeKeyWhileTypingFadeoutAnimator;
    private final ObjectAnimator mAltCodeKeyWhileTypingFadeinAnimator;
    private int mAltCodeKeyWhileTypingAnimAlpha = Constants.Color.ALPHA_OPAQUE;

    // Drawing preview placer view
    private final DrawingPreviewPlacerView mDrawingPreviewPlacerView;
    private final int[] mOriginCoords = CoordinateUtils.newInstance();
    private final GestureFloatingTextDrawingPreview mGestureFloatingTextDrawingPreview;
    private final GestureTrailsDrawingPreview mGestureTrailsDrawingPreview;
    private final SlidingKeyInputDrawingPreview mSlidingKeyInputDrawingPreview;

    // Key preview
    private static final boolean FADE_OUT_KEY_TOP_LETTER_WHEN_KEY_IS_PRESSED = false;
    private final KeyPreviewDrawParams mKeyPreviewDrawParams;
    private final KeyPreviewChoreographer mKeyPreviewChoreographer;

    // More keys keyboard
    private final Paint mBackgroundDimAlphaPaint = new Paint();
    private boolean mNeedsToDimEntireKeyboard;
    private final View mMoreKeysKeyboardContainer;
    private final WeakHashMap<Key, Keyboard> mMoreKeysKeyboardCache =
            CollectionUtils.newWeakHashMap();
    private final boolean mConfigShowMoreKeysKeyboardAtTouchedPoint;
    // More keys panel (used by both more keys keyboard and more suggestions view)
    // TODO: Consider extending to support multiple more keys panels
    private MoreKeysPanel mMoreKeysPanel;

    // Gesture floating preview text
    // TODO: Make this parameter customizable by user via settings.
    private int mGestureFloatingPreviewTextLingerTimeout;

    private final KeyDetector mKeyDetector;
    private final NonDistinctMultitouchHelper mNonDistinctMultitouchHelper;

    private final TimerHandler mKeyTimerHandler;
    private final int mLanguageOnSpacebarHorizontalMargin;

    private final DrawingHandler mDrawingHandler =
            new DrawingHandler(this);

    public MainKeyboardView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.mainKeyboardViewStyle);
    }

    public MainKeyboardView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        mDrawingPreviewPlacerView = new DrawingPreviewPlacerView(context, attrs);

        final TypedArray mainKeyboardViewAttr = context.obtainStyledAttributes(
                attrs, R.styleable.MainKeyboardView, defStyle, R.style.MainKeyboardView);
        final int ignoreAltCodeKeyTimeout = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_ignoreAltCodeKeyTimeout, 0);
        final int gestureRecognitionUpdateTime = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_gestureRecognitionUpdateTime, 0);
        mKeyTimerHandler = new TimerHandler(
                this, ignoreAltCodeKeyTimeout, gestureRecognitionUpdateTime);

        final float keyHysteresisDistance = mainKeyboardViewAttr.getDimension(
                R.styleable.MainKeyboardView_keyHysteresisDistance, 0.0f);
        final float keyHysteresisDistanceForSlidingModifier = mainKeyboardViewAttr.getDimension(
                R.styleable.MainKeyboardView_keyHysteresisDistanceForSlidingModifier, 0.0f);
        mKeyDetector = new KeyDetector(
                keyHysteresisDistance, keyHysteresisDistanceForSlidingModifier);

        PointerTracker.init(mainKeyboardViewAttr, mKeyTimerHandler, this /* DrawingProxy */);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean forceNonDistinctMultitouch = prefs.getBoolean(
                DebugSettings.PREF_FORCE_NON_DISTINCT_MULTITOUCH, false);
        final boolean hasDistinctMultitouch = context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT)
                && !forceNonDistinctMultitouch;
        mNonDistinctMultitouchHelper = hasDistinctMultitouch ? null
                : new NonDistinctMultitouchHelper();

        final int backgroundDimAlpha = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_backgroundDimAlpha, 0);
        mBackgroundDimAlphaPaint.setColor(Color.BLACK);
        mBackgroundDimAlphaPaint.setAlpha(backgroundDimAlpha);
        mSpacebarBackground = mainKeyboardViewAttr.getDrawable(
                R.styleable.MainKeyboardView_spacebarBackground);
        mAutoCorrectionSpacebarLedEnabled = mainKeyboardViewAttr.getBoolean(
                R.styleable.MainKeyboardView_autoCorrectionSpacebarLedEnabled, false);
        mAutoCorrectionSpacebarLedIcon = mainKeyboardViewAttr.getDrawable(
                R.styleable.MainKeyboardView_autoCorrectionSpacebarLedIcon);
        mLanguageOnSpacebarTextRatio = mainKeyboardViewAttr.getFraction(
                R.styleable.MainKeyboardView_languageOnSpacebarTextRatio, 1, 1, 1.0f);
        mLanguageOnSpacebarTextColor = mainKeyboardViewAttr.getColor(
                R.styleable.MainKeyboardView_languageOnSpacebarTextColor, 0);
        mLanguageOnSpacebarTextShadowColor = mainKeyboardViewAttr.getColor(
                R.styleable.MainKeyboardView_languageOnSpacebarTextShadowColor, 0);
        mLanguageOnSpacebarFinalAlpha = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_languageOnSpacebarFinalAlpha,
                Constants.Color.ALPHA_OPAQUE);
        final int languageOnSpacebarFadeoutAnimatorResId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_languageOnSpacebarFadeoutAnimator, 0);
        final int altCodeKeyWhileTypingFadeoutAnimatorResId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_altCodeKeyWhileTypingFadeoutAnimator, 0);
        final int altCodeKeyWhileTypingFadeinAnimatorResId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_altCodeKeyWhileTypingFadeinAnimator, 0);

        mKeyPreviewDrawParams = new KeyPreviewDrawParams(mainKeyboardViewAttr);
        mKeyPreviewChoreographer = new KeyPreviewChoreographer(mKeyPreviewDrawParams);

        final int moreKeysKeyboardLayoutId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_moreKeysKeyboardLayout, 0);
        mConfigShowMoreKeysKeyboardAtTouchedPoint = mainKeyboardViewAttr.getBoolean(
                R.styleable.MainKeyboardView_showMoreKeysKeyboardAtTouchedPoint, false);

        mGestureFloatingPreviewTextLingerTimeout = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_gestureFloatingPreviewTextLingerTimeout, 0);

        mGestureFloatingTextDrawingPreview = new GestureFloatingTextDrawingPreview(
                mDrawingPreviewPlacerView, mainKeyboardViewAttr);
        mDrawingPreviewPlacerView.addPreview(mGestureFloatingTextDrawingPreview);

        mGestureTrailsDrawingPreview = new GestureTrailsDrawingPreview(
                mDrawingPreviewPlacerView, mainKeyboardViewAttr);
        mDrawingPreviewPlacerView.addPreview(mGestureTrailsDrawingPreview);

        mSlidingKeyInputDrawingPreview = new SlidingKeyInputDrawingPreview(
                mDrawingPreviewPlacerView, mainKeyboardViewAttr);
        mDrawingPreviewPlacerView.addPreview(mSlidingKeyInputDrawingPreview);
        mainKeyboardViewAttr.recycle();

        mMoreKeysKeyboardContainer = LayoutInflater.from(getContext())
                .inflate(moreKeysKeyboardLayoutId, null);
        mLanguageOnSpacebarFadeoutAnimator = loadObjectAnimator(
                languageOnSpacebarFadeoutAnimatorResId, this);
        mAltCodeKeyWhileTypingFadeoutAnimator = loadObjectAnimator(
                altCodeKeyWhileTypingFadeoutAnimatorResId, this);
        mAltCodeKeyWhileTypingFadeinAnimator = loadObjectAnimator(
                altCodeKeyWhileTypingFadeinAnimatorResId, this);

        mKeyboardActionListener = KeyboardActionListener.EMPTY_LISTENER;

        mLanguageOnSpacebarHorizontalMargin = (int)getResources().getDimension(
                R.dimen.config_language_on_spacebar_horizontal_margin);
    }

    @Override
    public void setHardwareAcceleratedDrawingEnabled(final boolean enabled) {
        super.setHardwareAcceleratedDrawingEnabled(enabled);
        mDrawingPreviewPlacerView.setHardwareAcceleratedDrawingEnabled(enabled);
    }

    private ObjectAnimator loadObjectAnimator(final int resId, final Object target) {
        if (resId == 0) {
            // TODO: Stop returning null.
            return null;
        }
        final ObjectAnimator animator = (ObjectAnimator)AnimatorInflater.loadAnimator(
                getContext(), resId);
        if (animator != null) {
            animator.setTarget(target);
        }
        return animator;
    }

    private static void cancelAndStartAnimators(final ObjectAnimator animatorToCancel,
            final ObjectAnimator animatorToStart) {
        if (animatorToCancel == null || animatorToStart == null) {
            // TODO: Stop using null as a no-operation animator.
            return;
        }
        float startFraction = 0.0f;
        if (animatorToCancel.isStarted()) {
            animatorToCancel.cancel();
            startFraction = 1.0f - animatorToCancel.getAnimatedFraction();
        }
        final long startTime = (long)(animatorToStart.getDuration() * startFraction);
        animatorToStart.start();
        animatorToStart.setCurrentPlayTime(startTime);
    }

    // Implements {@link TimerHander.Callbacks} method.
    @Override
    public void startWhileTypingFadeinAnimation() {
        cancelAndStartAnimators(
                mAltCodeKeyWhileTypingFadeoutAnimator, mAltCodeKeyWhileTypingFadeinAnimator);
    }

    @Override
    public void startWhileTypingFadeoutAnimation() {
        cancelAndStartAnimators(
                mAltCodeKeyWhileTypingFadeinAnimator, mAltCodeKeyWhileTypingFadeoutAnimator);
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
        if (mAltCodeKeyWhileTypingAnimAlpha == alpha) {
            return;
        }
        // Update the visual of alt-code-key-while-typing.
        mAltCodeKeyWhileTypingAnimAlpha = alpha;
        final Keyboard keyboard = getKeyboard();
        if (keyboard == null) {
            return;
        }
        for (final Key key : keyboard.mAltCodeKeysWhileTyping) {
            invalidateKey(key);
        }
    }

    public void setKeyboardActionListener(final KeyboardActionListener listener) {
        mKeyboardActionListener = listener;
        PointerTracker.setKeyboardActionListener(listener);
    }

    // TODO: We should reconsider which coordinate system should be used to represent keyboard
    // event.
    public int getKeyX(final int x) {
        return Constants.isValidCoordinate(x) ? mKeyDetector.getTouchX(x) : x;
    }

    // TODO: We should reconsider which coordinate system should be used to represent keyboard
    // event.
    public int getKeyY(final int y) {
        return Constants.isValidCoordinate(y) ? mKeyDetector.getTouchY(y) : y;
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
        mKeyTimerHandler.cancelLongPressTimers();
        super.setKeyboard(keyboard);
        mKeyDetector.setKeyboard(
                keyboard, -getPaddingLeft(), -getPaddingTop() + getVerticalCorrection());
        PointerTracker.setKeyDetector(mKeyDetector);
        mMoreKeysKeyboardCache.clear();

        mSpaceKey = keyboard.getKey(Constants.CODE_SPACE);
        mSpacebarIcon = (mSpaceKey != null)
                ? mSpaceKey.getIcon(keyboard.mIconsSet, Constants.Color.ALPHA_OPAQUE) : null;
        final int keyHeight = keyboard.mMostCommonKeyHeight - keyboard.mVerticalGap;
        mLanguageOnSpacebarTextSize = keyHeight * mLanguageOnSpacebarTextRatio;
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            final int orientation = getContext().getResources().getConfiguration().orientation;
            ResearchLogger.mainKeyboardView_setKeyboard(keyboard, orientation);
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
        mKeyPreviewDrawParams.setPopupEnabled(previewEnabled, delay);
    }

    private void locatePreviewPlacerView() {
        if (mDrawingPreviewPlacerView.getParent() != null) {
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
            Log.w(TAG, "Cannot find android.R.id.content view to add DrawingPreviewPlacerView");
        } else {
            windowContentView.addView(mDrawingPreviewPlacerView);
            mDrawingPreviewPlacerView.setKeyboardViewGeometry(mOriginCoords, width, height);
        }
    }

    /**
     * Returns the enabled state of the key feedback preview
     * @return whether or not the key feedback preview is enabled
     * @see #setKeyPreviewPopupEnabled(boolean, int)
     */
    public boolean isKeyPreviewPopupEnabled() {
        return mKeyPreviewDrawParams.isPopupEnabled();
    }

    // Implements {@link DrawingHandler.Callbacks} method.
    @Override
    public void dismissAllKeyPreviews() {
        mKeyPreviewChoreographer.dismissAllKeyPreviews();
        PointerTracker.setReleasedKeyGraphicsToAllKeys();
    }

    @Override
    public void showKeyPreview(final Key key) {
        // If key is invalid or IME is already closed, we must not show key preview.
        // Trying to show key preview while root window is closed causes
        // WindowManager.BadTokenException.
        if (key == null) {
            return;
        }

        final KeyPreviewDrawParams previewParams = mKeyPreviewDrawParams;
        final Keyboard keyboard = getKeyboard();
        if (!previewParams.isPopupEnabled()) {
            previewParams.setVisibleOffset(-keyboard.mVerticalGap);
            return;
        }

        final TextView previewTextView = mKeyPreviewChoreographer.getKeyPreviewTextView(
                key, getContext());
        locatePreviewPlacerView();
        mDrawingPreviewPlacerView.addView(
                previewTextView, ViewLayoutUtils.newLayoutParam(mDrawingPreviewPlacerView, 0, 0));
        getLocationInWindow(mOriginCoords);
        mKeyPreviewChoreographer.placeKeyPreview(key, previewTextView, keyboard.mIconsSet,
                mKeyDrawParams, getWidth(), mOriginCoords);
        mKeyPreviewChoreographer.showKeyPreview(key, previewTextView, isHardwareAccelerated());
    }

    // Implements {@link TimerHandler.Callbacks} method.
    @Override
    public void dismissKeyPreviewWithoutDelay(final Key key) {
        mKeyPreviewChoreographer.dismissKeyPreview(key, false /* withAnimation */);
        // To redraw key top letter.
        invalidateKey(key);
    }

    @Override
    public void dismissKeyPreview(final Key key) {
        if (!isHardwareAccelerated()) {
            // TODO: Implement preference option to control key preview method and duration.
            mDrawingHandler.dismissKeyPreview(mKeyPreviewDrawParams.getLingerTimeout(), key);
            return;
        }
        mKeyPreviewChoreographer.dismissKeyPreview(key, true /* withAnimation */);
    }

    public void setSlidingKeyInputPreviewEnabled(final boolean enabled) {
        mSlidingKeyInputDrawingPreview.setPreviewEnabled(enabled);
    }

    @Override
    public void showSlidingKeyInputPreview(final PointerTracker tracker) {
        locatePreviewPlacerView();
        mSlidingKeyInputDrawingPreview.setPreviewPosition(tracker);
    }

    @Override
    public void dismissSlidingKeyInputPreview() {
        mSlidingKeyInputDrawingPreview.dismissSlidingKeyInputPreview();
    }

    private void setGesturePreviewMode(final boolean isGestureTrailEnabled,
            final boolean isGestureFloatingPreviewTextEnabled) {
        mGestureFloatingTextDrawingPreview.setPreviewEnabled(isGestureFloatingPreviewTextEnabled);
        mGestureTrailsDrawingPreview.setPreviewEnabled(isGestureTrailEnabled);
    }

    // Implements {@link DrawingHandler.Callbacks} method.
    @Override
    public void showGestureFloatingPreviewText(final SuggestedWords suggestedWords) {
        locatePreviewPlacerView();
        mGestureFloatingTextDrawingPreview.setSuggetedWords(suggestedWords);
    }

    public void dismissGestureFloatingPreviewText() {
        locatePreviewPlacerView();
        mDrawingHandler.dismissGestureFloatingPreviewText(mGestureFloatingPreviewTextLingerTimeout);
    }

    @Override
    public void showGestureTrail(final PointerTracker tracker,
            final boolean showsFloatingPreviewText) {
        locatePreviewPlacerView();
        if (showsFloatingPreviewText) {
            mGestureFloatingTextDrawingPreview.setPreviewPosition(tracker);
        }
        mGestureTrailsDrawingPreview.setPreviewPosition(tracker);
    }

    // Note that this method is called from a non-UI thread.
    public void setMainDictionaryAvailability(final boolean mainDictionaryAvailable) {
        PointerTracker.setMainDictionaryAvailability(mainDictionaryAvailable);
    }

    public void setGestureHandlingEnabledByUser(final boolean isGestureHandlingEnabledByUser,
            final boolean isGestureTrailEnabled,
            final boolean isGestureFloatingPreviewTextEnabled) {
        PointerTracker.setGestureHandlingEnabledByUser(isGestureHandlingEnabledByUser);
        setGesturePreviewMode(isGestureHandlingEnabledByUser && isGestureTrailEnabled,
                isGestureHandlingEnabledByUser && isGestureFloatingPreviewTextEnabled);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Notify the ResearchLogger (development only diagnostics) that the keyboard view has
        // been attached.  This is needed to properly show the splash screen, which requires that
        // the window token of the KeyboardView be non-null.
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.getInstance().mainKeyboardView_onAttachedToWindow(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mDrawingPreviewPlacerView.removeAllViews();
        // Notify the ResearchLogger (development only diagnostics) that the keyboard view has
        // been detached.  This is needed to invalidate the reference of {@link MainKeyboardView}
        // to null.
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.getInstance().mainKeyboardView_onDetachedFromWindow();
        }
    }

    private MoreKeysPanel onCreateMoreKeysPanel(final Key key, final Context context) {
        if (key.getMoreKeys() == null) {
            return null;
        }
        Keyboard moreKeysKeyboard = mMoreKeysKeyboardCache.get(key);
        if (moreKeysKeyboard == null) {
            moreKeysKeyboard = new MoreKeysKeyboard.Builder(
                    context, key, this, mKeyPreviewDrawParams).build();
            mMoreKeysKeyboardCache.put(key, moreKeysKeyboard);
        }

        final View container = mMoreKeysKeyboardContainer;
        final MoreKeysKeyboardView moreKeysKeyboardView =
                (MoreKeysKeyboardView)container.findViewById(R.id.more_keys_keyboard_view);
        moreKeysKeyboardView.setKeyboard(moreKeysKeyboard);
        container.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        return moreKeysKeyboardView;
    }

    // Implements {@link TimerHandler.Callbacks} method.
    /**
     * Called when a key is long pressed.
     * @param tracker the pointer tracker which pressed the parent key
     */
    @Override
    public void onLongPress(final PointerTracker tracker) {
        if (isShowingMoreKeysPanel()) {
            return;
        }
        final Key key = tracker.getKey();
        if (key == null) {
            return;
        }
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.mainKeyboardView_onLongPress();
        }
        final KeyboardActionListener listener = mKeyboardActionListener;
        if (key.hasNoPanelAutoMoreKey()) {
            final int moreKeyCode = key.getMoreKeys()[0].mCode;
            tracker.onLongPressed();
            listener.onPressKey(moreKeyCode, 0 /* repeatCount */, true /* isSinglePointer */);
            listener.onCodeInput(moreKeyCode,
                    Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE);
            listener.onReleaseKey(moreKeyCode, false /* withSliding */);
            return;
        }
        final int code = key.getCode();
        if (code == Constants.CODE_SPACE || code == Constants.CODE_LANGUAGE_SWITCH) {
            // Long pressing the space key invokes IME switcher dialog.
            if (listener.onCustomRequest(Constants.CUSTOM_CODE_SHOW_INPUT_METHOD_PICKER)) {
                tracker.onLongPressed();
                listener.onReleaseKey(code, false /* withSliding */);
                return;
            }
        }
        openMoreKeysPanel(key, tracker);
    }

    private void openMoreKeysPanel(final Key key, final PointerTracker tracker) {
        final MoreKeysPanel moreKeysPanel = onCreateMoreKeysPanel(key, getContext());
        if (moreKeysPanel == null) {
            return;
        }

        final int[] lastCoords = CoordinateUtils.newInstance();
        tracker.getLastCoordinates(lastCoords);
        final boolean keyPreviewEnabled = isKeyPreviewPopupEnabled() && !key.noKeyPreview();
        // The more keys keyboard is usually horizontally aligned with the center of the parent key.
        // If showMoreKeysKeyboardAtTouchedPoint is true and the key preview is disabled, the more
        // keys keyboard is placed at the touch point of the parent key.
        final int pointX = (mConfigShowMoreKeysKeyboardAtTouchedPoint && !keyPreviewEnabled)
                ? CoordinateUtils.x(lastCoords)
                : key.getX() + key.getWidth() / 2;
        // The more keys keyboard is usually vertically aligned with the top edge of the parent key
        // (plus vertical gap). If the key preview is enabled, the more keys keyboard is vertically
        // aligned with the bottom edge of the visible part of the key preview.
        // {@code mPreviewVisibleOffset} has been set appropriately in
        // {@link KeyboardView#showKeyPreview(PointerTracker)}.
        final int pointY = key.getY() + mKeyPreviewDrawParams.getVisibleOffset();
        moreKeysPanel.showMoreKeysPanel(this, this, pointX, pointY, mKeyboardActionListener);
        tracker.onShowMoreKeysPanel(moreKeysPanel);
        // TODO: Implement zoom in animation of more keys panel.
        dismissKeyPreviewWithoutDelay(key);
    }

    public boolean isInDraggingFinger() {
        if (isShowingMoreKeysPanel()) {
            return true;
        }
        return PointerTracker.isAnyInDraggingFinger();
    }

    @Override
    public void onShowMoreKeysPanel(final MoreKeysPanel panel) {
        locatePreviewPlacerView();
        panel.showInParent(mDrawingPreviewPlacerView);
        mMoreKeysPanel = panel;
        dimEntireKeyboard(true /* dimmed */);
    }

    public boolean isShowingMoreKeysPanel() {
        return mMoreKeysPanel != null && mMoreKeysPanel.isShowingInParent();
    }

    @Override
    public void onCancelMoreKeysPanel(final MoreKeysPanel panel) {
        PointerTracker.dismissAllMoreKeysPanels();
    }

    @Override
    public void onDismissMoreKeysPanel(final MoreKeysPanel panel) {
        dimEntireKeyboard(false /* dimmed */);
        if (isShowingMoreKeysPanel()) {
            mMoreKeysPanel.removeFromParent();
            mMoreKeysPanel = null;
        }
    }

    public void startDoubleTapShiftKeyTimer() {
        mKeyTimerHandler.startDoubleTapShiftKeyTimer();
    }

    public void cancelDoubleTapShiftKeyTimer() {
        mKeyTimerHandler.cancelDoubleTapShiftKeyTimer();
    }

    public boolean isInDoubleTapShiftKeyTimeout() {
        return mKeyTimerHandler.isInDoubleTapShiftKeyTimeout();
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
        if (mNonDistinctMultitouchHelper != null) {
            if (me.getPointerCount() > 1 && mKeyTimerHandler.isInKeyRepeat()) {
                // Key repeating timer will be canceled if 2 or more keys are in action.
                mKeyTimerHandler.cancelKeyRepeatTimers();
            }
            // Non distinct multitouch screen support
            mNonDistinctMultitouchHelper.processMotionEvent(me, mKeyDetector);
            return true;
        }
        return processMotionEvent(me);
    }

    public boolean processMotionEvent(final MotionEvent me) {
        if (LatinImeLogger.sUsabilityStudy) {
            UsabilityStudyLogUtils.writeMotionEvent(me);
        }
        // Currently the same "move" event is being logged twice.
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.mainKeyboardView_processMotionEvent(me);
        }

        final int index = me.getActionIndex();
        final int id = me.getPointerId(index);
        final PointerTracker tracker = PointerTracker.getPointerTracker(id);
        // When a more keys panel is showing, we should ignore other fingers' single touch events
        // other than the finger that is showing the more keys panel.
        if (isShowingMoreKeysPanel() && !tracker.isShowingMoreKeysPanel()
                && PointerTracker.getActivePointerTrackerCount() == 1) {
            return true;
        }
        tracker.processMotionEvent(me, mKeyDetector);
        return true;
    }

    public void cancelAllOngoingEvents() {
        mKeyTimerHandler.cancelAllMessages();
        mDrawingHandler.cancelAllMessages();
        dismissAllKeyPreviews();
        dismissGestureFloatingPreviewText();
        dismissSlidingKeyInputPreview();
        PointerTracker.dismissAllMoreKeysPanels();
        PointerTracker.cancelAllPointerTrackers();
    }

    public void closing() {
        cancelAllOngoingEvents();
        mMoreKeysKeyboardCache.clear();
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
            return AccessibleKeyboardViewProxy.getInstance().dispatchHoverEvent(
                    event, mKeyDetector);
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

    private void dimEntireKeyboard(final boolean dimmed) {
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
            canvas.drawRect(0.0f, 0.0f, getWidth(), getHeight(), mBackgroundDimAlphaPaint);
        }
    }

    // Draw key background.
    @Override
    protected void onDrawKeyBackground(final Key key, final Canvas canvas,
            final Drawable background) {
        if (key.getCode() == Constants.CODE_SPACE) {
            super.onDrawKeyBackground(key, canvas, mSpacebarBackground);
            return;
        }
        super.onDrawKeyBackground(key, canvas, background);
    }

    @Override
    protected void onDrawKeyTopVisuals(final Key key, final Canvas canvas, final Paint paint,
            final KeyDrawParams params) {
        if (key.altCodeWhileTyping() && key.isEnabled()) {
            params.mAnimAlpha = mAltCodeKeyWhileTypingAnimAlpha;
        }
        // Don't draw key top letter when key preview is showing.
        if (FADE_OUT_KEY_TOP_LETTER_WHEN_KEY_IS_PRESSED
                && mKeyPreviewChoreographer.isShowingKeyPreview(key)) {
            // TODO: Fade out animation for the key top letter, and fade in animation for the key
            // background color when the user presses the key.
            return;
        }
        final int code = key.getCode();
        if (code == Constants.CODE_SPACE) {
            drawSpacebar(key, canvas, paint);
            // Whether space key needs to show the "..." popup hint for special purposes
            if (key.isLongPressEnabled() && mHasMultipleEnabledIMEsOrSubtypes) {
                drawKeyPopupHint(key, canvas, paint, params);
            }
        } else if (code == Constants.CODE_LANGUAGE_SWITCH) {
            super.onDrawKeyTopVisuals(key, canvas, paint, params);
            drawKeyPopupHint(key, canvas, paint, params);
        } else {
            super.onDrawKeyTopVisuals(key, canvas, paint, params);
        }
    }

    private boolean fitsTextIntoWidth(final int width, final String text, final Paint paint) {
        final int maxTextWidth = width - mLanguageOnSpacebarHorizontalMargin * 2;
        paint.setTextScaleX(1.0f);
        final float textWidth = TypefaceUtils.getStringWidth(text, paint);
        if (textWidth < width) {
            return true;
        }

        final float scaleX = maxTextWidth / textWidth;
        if (scaleX < MINIMUM_XSCALE_OF_LANGUAGE_NAME) {
            return false;
        }

        paint.setTextScaleX(scaleX);
        return TypefaceUtils.getStringWidth(text, paint) < maxTextWidth;
    }

    // Layout language name on spacebar.
    private String layoutLanguageOnSpacebar(final Paint paint,
            final InputMethodSubtype subtype, final int width) {

        // Choose appropriate language name to fit into the width.
        final String fullText = SubtypeLocaleUtils.getFullDisplayName(subtype);
        if (fitsTextIntoWidth(width, fullText, paint)) {
            return fullText;
        }

        final String middleText = SubtypeLocaleUtils.getMiddleDisplayName(subtype);
        if (fitsTextIntoWidth(width, middleText, paint)) {
            return middleText;
        }

        final String shortText = SubtypeLocaleUtils.getShortDisplayName(subtype);
        if (fitsTextIntoWidth(width, shortText, paint)) {
            return shortText;
        }

        return "";
    }

    private void drawSpacebar(final Key key, final Canvas canvas, final Paint paint) {
        final int width = key.getWidth();
        final int height = key.getHeight();

        // If input language are explicitly selected.
        if (mNeedsToDisplayLanguage) {
            paint.setTextAlign(Align.CENTER);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(mLanguageOnSpacebarTextSize);
            final InputMethodSubtype subtype = getKeyboard().mId.mSubtype;
            final String language = layoutLanguageOnSpacebar(paint, subtype, width);
            // Draw language text with shadow
            final float descent = paint.descent();
            final float textHeight = -paint.ascent() + descent;
            final float baseline = height / 2 + textHeight / 2;
            paint.setColor(mLanguageOnSpacebarTextShadowColor);
            paint.setAlpha(mLanguageOnSpacebarAnimAlpha);
            canvas.drawText(language, width / 2, baseline - descent - 1, paint);
            paint.setColor(mLanguageOnSpacebarTextColor);
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
        } else if (mSpacebarIcon != null) {
            final int iconWidth = mSpacebarIcon.getIntrinsicWidth();
            final int iconHeight = mSpacebarIcon.getIntrinsicHeight();
            int x = (width - iconWidth) / 2;
            int y = height - iconHeight;
            drawIcon(canvas, mSpacebarIcon, x, y, iconWidth, iconHeight);
        }
    }

    @Override
    public void deallocateMemory() {
        super.deallocateMemory();
        mDrawingPreviewPlacerView.deallocateMemory();
    }
}
