/*
 * Copyright (C) 2013 The Android Open Source Project
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

import static com.android.inputmethod.latin.Constants.NOT_A_COORDINATE;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.util.Pair;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TextView;

import com.android.inputmethod.keyboard.internal.EmojiCategory;
import com.android.inputmethod.keyboard.internal.EmojiLayoutParams;
import com.android.inputmethod.keyboard.internal.EmojiPageKeyboardView;
import com.android.inputmethod.keyboard.internal.EmojiPalettesAdapter;
import com.android.inputmethod.keyboard.internal.KeyDrawParams;
import com.android.inputmethod.keyboard.internal.KeyVisualAttributes;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SubtypeSwitcher;
import com.android.inputmethod.latin.utils.ResourceUtils;

import java.util.concurrent.TimeUnit;

/**
 * View class to implement Emoji palettes.
 * The Emoji keyboard consists of group of views {@link R.layout#emoji_palettes_view}.
 * <ol>
 * <li> Emoji category tabs.
 * <li> Delete button.
 * <li> Emoji keyboard pages that can be scrolled by swiping horizontally or by selecting a tab.
 * <li> Back to main keyboard button and enter button.
 * </ol>
 * Because of the above reasons, this class doesn't extend {@link KeyboardView}.
 */
// TODO: Move this class to com.android.inputmethod.emoji package.
public final class EmojiPalettesView extends LinearLayout implements OnTabChangeListener,
        ViewPager.OnPageChangeListener, View.OnClickListener, View.OnTouchListener,
        EmojiPageKeyboardView.OnKeyEventListener {
    private final int mKeyBackgroundId;
    private final int mEmojiFunctionalKeyBackgroundId;
    private final ColorStateList mTabLabelColor;
    private final DeleteKeyOnTouchListener mDeleteKeyOnTouchListener;
    private EmojiPalettesAdapter mEmojiPalettesAdapter;
    private final EmojiLayoutParams mEmojiLayoutParams;

    private TextView mAlphabetKeyLeft;
    private TextView mAlphabetKeyRight;
    private TabHost mTabHost;
    private ViewPager mEmojiPager;
    private int mCurrentPagerPosition = 0;
    private EmojiCategoryPageIndicatorView mEmojiCategoryPageIndicatorView;

    private KeyboardActionListener mKeyboardActionListener = KeyboardActionListener.EMPTY_LISTENER;

    private final EmojiCategory mEmojiCategory;

    public EmojiPalettesView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.emojiPalettesViewStyle);
    }

    public EmojiPalettesView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        final TypedArray keyboardViewAttr = context.obtainStyledAttributes(attrs,
                R.styleable.KeyboardView, defStyle, R.style.KeyboardView);
        mKeyBackgroundId = keyboardViewAttr.getResourceId(
                R.styleable.KeyboardView_keyBackground, 0);
        mEmojiFunctionalKeyBackgroundId = keyboardViewAttr.getResourceId(
                R.styleable.KeyboardView_keyBackgroundEmojiFunctional, 0);
        keyboardViewAttr.recycle();
        final TypedArray emojiPalettesViewAttr = context.obtainStyledAttributes(attrs,
                R.styleable.EmojiPalettesView, defStyle, R.style.EmojiPalettesView);
        mTabLabelColor = emojiPalettesViewAttr.getColorStateList(
                R.styleable.EmojiPalettesView_emojiTabLabelColor);
        emojiPalettesViewAttr.recycle();
        final KeyboardLayoutSet.Builder builder = new KeyboardLayoutSet.Builder(
                context, null /* editorInfo */);
        final Resources res = context.getResources();
        mEmojiLayoutParams = new EmojiLayoutParams(res);
        builder.setSubtype(SubtypeSwitcher.getInstance().getEmojiSubtype());
        builder.setKeyboardGeometry(ResourceUtils.getDefaultKeyboardWidth(res),
                mEmojiLayoutParams.mEmojiKeyboardHeight);
        builder.setOptions(false /* shortcutImeEnabled */, false /* showsVoiceInputKey */,
                false /* languageSwitchKeyEnabled */);
        mEmojiCategory = new EmojiCategory(PreferenceManager.getDefaultSharedPreferences(context),
                context.getResources(), builder.build());
        mDeleteKeyOnTouchListener = new DeleteKeyOnTouchListener(context);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        final Resources res = getContext().getResources();
        // The main keyboard expands to the entire this {@link KeyboardView}.
        final int width = ResourceUtils.getDefaultKeyboardWidth(res)
                + getPaddingLeft() + getPaddingRight();
        final int height = ResourceUtils.getDefaultKeyboardHeight(res)
                + res.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
                + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(width, height);
    }

    private void addTab(final TabHost host, final int categoryId) {
        final String tabId = mEmojiCategory.getCategoryName(categoryId, 0 /* categoryPageId */);
        final TabHost.TabSpec tspec = host.newTabSpec(tabId);
        tspec.setContent(R.id.emoji_keyboard_dummy);
        if (mEmojiCategory.getCategoryIcon(categoryId) != 0) {
            final ImageView iconView = (ImageView)LayoutInflater.from(getContext()).inflate(
                    R.layout.emoji_keyboard_tab_icon, null);
            iconView.setImageResource(mEmojiCategory.getCategoryIcon(categoryId));
            iconView.setContentDescription(mEmojiCategory.getAccessibilityDescription(categoryId));
            tspec.setIndicator(iconView);
        }
        if (mEmojiCategory.getCategoryLabel(categoryId) != null) {
            final TextView textView = (TextView)LayoutInflater.from(getContext()).inflate(
                    R.layout.emoji_keyboard_tab_label, null);
            textView.setText(mEmojiCategory.getCategoryLabel(categoryId));
            textView.setContentDescription(mEmojiCategory.getAccessibilityDescription(categoryId));
            textView.setTextColor(mTabLabelColor);
            tspec.setIndicator(textView);
        }
        host.addTab(tspec);
    }

    @Override
    protected void onFinishInflate() {
        mTabHost = (TabHost)findViewById(R.id.emoji_category_tabhost);
        mTabHost.setup();
        for (final EmojiCategory.CategoryProperties properties
                : mEmojiCategory.getShownCategories()) {
            addTab(mTabHost, properties.mCategoryId);
        }
        mTabHost.setOnTabChangedListener(this);
        mTabHost.getTabWidget().setStripEnabled(true);

        mEmojiPalettesAdapter = new EmojiPalettesAdapter(mEmojiCategory, this);

        mEmojiPager = (ViewPager)findViewById(R.id.emoji_keyboard_pager);
        mEmojiPager.setAdapter(mEmojiPalettesAdapter);
        mEmojiPager.setOnPageChangeListener(this);
        mEmojiPager.setOffscreenPageLimit(0);
        mEmojiPager.setPersistentDrawingCache(PERSISTENT_NO_CACHE);
        mEmojiLayoutParams.setPagerProperties(mEmojiPager);

        mEmojiCategoryPageIndicatorView =
                (EmojiCategoryPageIndicatorView)findViewById(R.id.emoji_category_page_id_view);
        mEmojiLayoutParams.setCategoryPageIdViewProperties(mEmojiCategoryPageIndicatorView);

        setCurrentCategoryId(mEmojiCategory.getCurrentCategoryId(), true /* force */);

        final LinearLayout actionBar = (LinearLayout)findViewById(R.id.emoji_action_bar);
        mEmojiLayoutParams.setActionBarProperties(actionBar);

        // deleteKey depends only on OnTouchListener.
        final ImageView deleteKey = (ImageView)findViewById(R.id.emoji_keyboard_delete);
        deleteKey.setTag(Constants.CODE_DELETE);
        deleteKey.setOnTouchListener(mDeleteKeyOnTouchListener);

        // {@link #mAlphabetKeyLeft}, {@link #mAlphabetKeyRight, and spaceKey depend on
        // {@link View.OnClickListener} as well as {@link View.OnTouchListener}.
        // {@link View.OnTouchListener} is used as the trigger of key-press, while
        // {@link View.OnClickListener} is used as the trigger of key-release which does not occur
        // if the event is canceled by moving off the finger from the view.
        // The text on alphabet keys are set at
        // {@link #startEmojiPalettes(String,int,float,Typeface)}.
        mAlphabetKeyLeft = (TextView)findViewById(R.id.emoji_keyboard_alphabet_left);
        mAlphabetKeyLeft.setBackgroundResource(mEmojiFunctionalKeyBackgroundId);
        mAlphabetKeyLeft.setTag(Constants.CODE_ALPHA_FROM_EMOJI);
        mAlphabetKeyLeft.setOnTouchListener(this);
        mAlphabetKeyLeft.setOnClickListener(this);
        mAlphabetKeyRight = (TextView)findViewById(R.id.emoji_keyboard_alphabet_right);
        mAlphabetKeyRight.setBackgroundResource(mEmojiFunctionalKeyBackgroundId);
        mAlphabetKeyRight.setTag(Constants.CODE_ALPHA_FROM_EMOJI);
        mAlphabetKeyRight.setOnTouchListener(this);
        mAlphabetKeyRight.setOnClickListener(this);
        final ImageView spaceKey = (ImageView)findViewById(R.id.emoji_keyboard_space);
        spaceKey.setBackgroundResource(mKeyBackgroundId);
        spaceKey.setTag(Constants.CODE_SPACE);
        spaceKey.setOnTouchListener(this);
        spaceKey.setOnClickListener(this);
        mEmojiLayoutParams.setKeyProperties(spaceKey);
    }

    @Override
    public boolean dispatchTouchEvent(final MotionEvent ev) {
        // Add here to the stack trace to nail down the {@link IllegalArgumentException} exception
        // in MotionEvent that sporadically happens.
        // TODO: Remove this override method once the issue has been addressed.
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onTabChanged(final String tabId) {
        final int categoryId = mEmojiCategory.getCategoryId(tabId);
        setCurrentCategoryId(categoryId, false /* force */);
        updateEmojiCategoryPageIdView();
    }

    @Override
    public void onPageSelected(final int position) {
        final Pair<Integer, Integer> newPos =
                mEmojiCategory.getCategoryIdAndPageIdFromPagePosition(position);
        setCurrentCategoryId(newPos.first /* categoryId */, false /* force */);
        mEmojiCategory.setCurrentCategoryPageId(newPos.second /* categoryPageId */);
        updateEmojiCategoryPageIdView();
        mCurrentPagerPosition = position;
    }

    @Override
    public void onPageScrollStateChanged(final int state) {
        // Ignore this message. Only want the actual page selected.
    }

    @Override
    public void onPageScrolled(final int position, final float positionOffset,
            final int positionOffsetPixels) {
        mEmojiPalettesAdapter.onPageScrolled();
        final Pair<Integer, Integer> newPos =
                mEmojiCategory.getCategoryIdAndPageIdFromPagePosition(position);
        final int newCategoryId = newPos.first;
        final int newCategorySize = mEmojiCategory.getCategoryPageSize(newCategoryId);
        final int currentCategoryId = mEmojiCategory.getCurrentCategoryId();
        final int currentCategoryPageId = mEmojiCategory.getCurrentCategoryPageId();
        final int currentCategorySize = mEmojiCategory.getCurrentCategoryPageSize();
        if (newCategoryId == currentCategoryId) {
            mEmojiCategoryPageIndicatorView.setCategoryPageId(
                    newCategorySize, newPos.second, positionOffset);
        } else if (newCategoryId > currentCategoryId) {
            mEmojiCategoryPageIndicatorView.setCategoryPageId(
                    currentCategorySize, currentCategoryPageId, positionOffset);
        } else if (newCategoryId < currentCategoryId) {
            mEmojiCategoryPageIndicatorView.setCategoryPageId(
                    currentCategorySize, currentCategoryPageId, positionOffset - 1);
        }
    }

    /**
     * Called from {@link EmojiPageKeyboardView} through {@link android.view.View.OnTouchListener}
     * interface to handle touch events from View-based elements such as the space bar.
     * Note that this method is used only for observing {@link MotionEvent#ACTION_DOWN} to trigger
     * {@link KeyboardActionListener#onPressKey}. {@link KeyboardActionListener#onReleaseKey} will
     * be covered by {@link #onClick} as long as the event is not canceled.
     */
    @Override
    public boolean onTouch(final View v, final MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN) {
            return false;
        }
        final Object tag = v.getTag();
        if (!(tag instanceof Integer)) {
            return false;
        }
        final int code = (Integer) tag;
        mKeyboardActionListener.onPressKey(
                code, 0 /* repeatCount */, true /* isSinglePointer */);
        // It's important to return false here. Otherwise, {@link #onClick} and touch-down visual
        // feedback stop working.
        return false;
    }

    /**
     * Called from {@link EmojiPageKeyboardView} through {@link android.view.View.OnClickListener}
     * interface to handle non-canceled touch-up events from View-based elements such as the space
     * bar.
     */
    @Override
    public void onClick(View v) {
        final Object tag = v.getTag();
        if (!(tag instanceof Integer)) {
            return;
        }
        final int code = (Integer) tag;
        mKeyboardActionListener.onCodeInput(code, NOT_A_COORDINATE, NOT_A_COORDINATE,
                false /* isKeyRepeat */);
        mKeyboardActionListener.onReleaseKey(code, false /* withSliding */);
    }

    /**
     * Called from {@link EmojiPageKeyboardView} through
     * {@link com.android.inputmethod.keyboard.internal.EmojiPageKeyboardView.OnKeyEventListener}
     * interface to handle touch events from non-View-based elements such as Emoji buttons.
     */
    @Override
    public void onPressKey(final Key key) {
        final int code = key.getCode();
        mKeyboardActionListener.onPressKey(code, 0 /* repeatCount */, true /* isSinglePointer */);
    }

    /**
     * Called from {@link EmojiPageKeyboardView} through
     * {@link com.android.inputmethod.keyboard.internal.EmojiPageKeyboardView.OnKeyEventListener}
     * interface to handle touch events from non-View-based elements such as Emoji buttons.
     */
    @Override
    public void onReleaseKey(final Key key) {
        mEmojiPalettesAdapter.addRecentKey(key);
        mEmojiCategory.saveLastTypedCategoryPage();
        final int code = key.getCode();
        if (code == Constants.CODE_OUTPUT_TEXT) {
            mKeyboardActionListener.onTextInput(key.getOutputText());
        } else {
            mKeyboardActionListener.onCodeInput(code, NOT_A_COORDINATE, NOT_A_COORDINATE,
                    false /* isKeyRepeat */);
        }
        mKeyboardActionListener.onReleaseKey(code, false /* withSliding */);
    }

    public void setHardwareAcceleratedDrawingEnabled(final boolean enabled) {
        if (!enabled) return;
        // TODO: Should use LAYER_TYPE_SOFTWARE when hardware acceleration is off?
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    private static void setupAlphabetKey(final TextView alphabetKey, final String label,
            final KeyDrawParams params) {
        alphabetKey.setText(label);
        alphabetKey.setTextColor(params.mTextColor);
        alphabetKey.setTextSize(TypedValue.COMPLEX_UNIT_PX, params.mLabelSize);
        alphabetKey.setTypeface(params.mTypeface);
    }

    public void startEmojiPalettes(final String switchToAlphaLabel,
            final KeyVisualAttributes keyVisualAttr) {
        final KeyDrawParams params = new KeyDrawParams();
        params.updateParams(mEmojiLayoutParams.getActionBarHeight(), keyVisualAttr);
        setupAlphabetKey(mAlphabetKeyLeft, switchToAlphaLabel, params);
        setupAlphabetKey(mAlphabetKeyRight, switchToAlphaLabel, params);
        mEmojiPager.setAdapter(mEmojiPalettesAdapter);
        mEmojiPager.setCurrentItem(mCurrentPagerPosition);
    }

    public void stopEmojiPalettes() {
        mEmojiPalettesAdapter.flushPendingRecentKeys();
        mEmojiPager.setAdapter(null);
    }

    public void setKeyboardActionListener(final KeyboardActionListener listener) {
        mKeyboardActionListener = listener;
        mDeleteKeyOnTouchListener.setKeyboardActionListener(mKeyboardActionListener);
    }

    private void updateEmojiCategoryPageIdView() {
        if (mEmojiCategoryPageIndicatorView == null) {
            return;
        }
        mEmojiCategoryPageIndicatorView.setCategoryPageId(
                mEmojiCategory.getCurrentCategoryPageSize(),
                mEmojiCategory.getCurrentCategoryPageId(), 0.0f /* offset */);
    }

    private void setCurrentCategoryId(final int categoryId, final boolean force) {
        final int oldCategoryId = mEmojiCategory.getCurrentCategoryId();
        if (oldCategoryId == categoryId && !force) {
            return;
        }

        if (oldCategoryId == EmojiCategory.ID_RECENTS) {
            // Needs to save pending updates for recent keys when we get out of the recents
            // category because we don't want to move the recent emojis around while the user
            // is in the recents category.
            mEmojiPalettesAdapter.flushPendingRecentKeys();
        }

        mEmojiCategory.setCurrentCategoryId(categoryId);
        final int newTabId = mEmojiCategory.getTabIdFromCategoryId(categoryId);
        final int newCategoryPageId = mEmojiCategory.getPageIdFromCategoryId(categoryId);
        if (force || mEmojiCategory.getCategoryIdAndPageIdFromPagePosition(
                mEmojiPager.getCurrentItem()).first != categoryId) {
            mEmojiPager.setCurrentItem(newCategoryPageId, false /* smoothScroll */);
        }
        if (force || mTabHost.getCurrentTab() != newTabId) {
            mTabHost.setCurrentTab(newTabId);
        }
    }

    private static class DeleteKeyOnTouchListener implements OnTouchListener {
        static final long MAX_REPEAT_COUNT_TIME = TimeUnit.SECONDS.toMillis(30);
        final int mDeleteKeyPressedBackgroundColor;
        final long mKeyRepeatStartTimeout;
        final long mKeyRepeatInterval;

        public DeleteKeyOnTouchListener(Context context) {
            final Resources res = context.getResources();
            mDeleteKeyPressedBackgroundColor =
                    res.getColor(R.color.emoji_key_pressed_background_color);
            mKeyRepeatStartTimeout = res.getInteger(R.integer.config_key_repeat_start_timeout);
            mKeyRepeatInterval = res.getInteger(R.integer.config_key_repeat_interval);
            mTimer = new CountDownTimer(MAX_REPEAT_COUNT_TIME, mKeyRepeatInterval) {
                @Override
                public void onTick(long millisUntilFinished) {
                    final long elapsed = MAX_REPEAT_COUNT_TIME - millisUntilFinished;
                    if (elapsed < mKeyRepeatStartTimeout) {
                        return;
                    }
                    onKeyRepeat();
                }
                @Override
                public void onFinish() {
                    onKeyRepeat();
                }
            };
        }

        /** Key-repeat state. */
        private static final int KEY_REPEAT_STATE_INITIALIZED = 0;
        // The key is touched but auto key-repeat is not started yet.
        private static final int KEY_REPEAT_STATE_KEY_DOWN = 1;
        // At least one key-repeat event has already been triggered and the key is not released.
        private static final int KEY_REPEAT_STATE_KEY_REPEAT = 2;

        private KeyboardActionListener mKeyboardActionListener =
                KeyboardActionListener.EMPTY_LISTENER;

        // TODO: Do the same things done in PointerTracker
        private final CountDownTimer mTimer;
        private int mState = KEY_REPEAT_STATE_INITIALIZED;
        private int mRepeatCount = 0;

        public void setKeyboardActionListener(final KeyboardActionListener listener) {
            mKeyboardActionListener = listener;
        }

        @Override
        public boolean onTouch(final View v, final MotionEvent event) {
            switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                onTouchDown(v);
                return true;
            case MotionEvent.ACTION_MOVE:
                final float x = event.getX();
                final float y = event.getY();
                if (x < 0.0f || v.getWidth() < x || y < 0.0f || v.getHeight() < y) {
                    // Stop generating key events once the finger moves away from the view area.
                    onTouchCanceled(v);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                onTouchUp(v);
                return true;
            }
            return false;
        }

        private void handleKeyDown() {
            mKeyboardActionListener.onPressKey(
                    Constants.CODE_DELETE, mRepeatCount, true /* isSinglePointer */);
        }

        private void handleKeyUp() {
            mKeyboardActionListener.onCodeInput(Constants.CODE_DELETE,
                    NOT_A_COORDINATE, NOT_A_COORDINATE, false /* isKeyRepeat */);
            mKeyboardActionListener.onReleaseKey(
                    Constants.CODE_DELETE, false /* withSliding */);
            ++mRepeatCount;
        }

        private void onTouchDown(final View v) {
            mTimer.cancel();
            mRepeatCount = 0;
            handleKeyDown();
            v.setBackgroundColor(mDeleteKeyPressedBackgroundColor);
            mState = KEY_REPEAT_STATE_KEY_DOWN;
            mTimer.start();
        }

        private void onTouchUp(final View v) {
            mTimer.cancel();
            if (mState == KEY_REPEAT_STATE_KEY_DOWN) {
                handleKeyUp();
            }
            v.setBackgroundColor(Color.TRANSPARENT);
            mState = KEY_REPEAT_STATE_INITIALIZED;
        }

        private void onTouchCanceled(final View v) {
            mTimer.cancel();
            v.setBackgroundColor(Color.TRANSPARENT);
            mState = KEY_REPEAT_STATE_INITIALIZED;
        }

        // Called by {@link #mTimer} in the UI thread as an auto key-repeat signal.
        void onKeyRepeat() {
            switch (mState) {
            case KEY_REPEAT_STATE_INITIALIZED:
                // Basically this should not happen.
                break;
            case KEY_REPEAT_STATE_KEY_DOWN:
                // Do not call {@link #handleKeyDown} here because it has already been called
                // in {@link #onTouchDown}.
                handleKeyUp();
                mState = KEY_REPEAT_STATE_KEY_REPEAT;
                break;
            case KEY_REPEAT_STATE_KEY_REPEAT:
                handleKeyDown();
                handleKeyUp();
                break;
            }
        }
    }
}
