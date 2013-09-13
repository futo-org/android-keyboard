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
import android.os.Build;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TextView;

import com.android.inputmethod.keyboard.internal.RecentsKeyboard;
import com.android.inputmethod.keyboard.internal.ScrollKeyboardView;
import com.android.inputmethod.keyboard.internal.ScrollViewWithNotifier;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SubtypeSwitcher;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.ResourceUtils;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * View class to implement Emoji keyboards.
 * The Emoji keyboard consists of group of views {@link R.layout#emoji_keyboard_view}.
 * <ol>
 * <li> Emoji category tabs.
 * <li> Delete button.
 * <li> Emoji keyboard pages that can be scrolled by swiping horizontally or by selecting a tab.
 * <li> Back to main keyboard button and enter button.
 * </ol>
 * Because of the above reasons, this class doesn't extend {@link KeyboardView}.
 */
public final class EmojiKeyboardView extends LinearLayout implements OnTabChangeListener,
        ViewPager.OnPageChangeListener, View.OnClickListener,
        ScrollKeyboardView.OnKeyClickListener {
    private static final String TAG = EmojiKeyboardView.class.getSimpleName();
    private final int mKeyBackgroundId;
    private final int mEmojiFunctionalKeyBackgroundId;
    private final KeyboardLayoutSet mLayoutSet;
    private final ColorStateList mTabLabelColor;
    private EmojiKeyboardAdapter mEmojiKeyboardAdapter;

    private TabHost mTabHost;
    private ViewPager mEmojiPager;

    private KeyboardActionListener mKeyboardActionListener = KeyboardActionListener.EMPTY_LISTENER;

    private static class EmojiCategory {
        private int mCurrentCategory = CATEGORY_UNSPECIFIED;
        private static final int CATEGORY_UNSPECIFIED = -1;
        private static final int CATEGORY_RECENTS = 0;
        private static final int CATEGORY_PEOPLE = 1;
        private static final int CATEGORY_OBJECTS = 2;
        private static final int CATEGORY_NATURE = 3;
        private static final int CATEGORY_PLACES = 4;
        private static final int CATEGORY_SYMBOLS = 5;
        private static final int CATEGORY_EMOTICONS = 6;
        private static final String[] sCategoryName = {
                "recents",
                "people",
                "objects",
                "nature",
                "places",
                "symbols",
                "emoticons" };
        private static final int[] sCategoryIcon = new int[] {
                R.drawable.ic_emoji_recent_light,
                R.drawable.ic_emoji_people_light,
                R.drawable.ic_emoji_objects_light,
                R.drawable.ic_emoji_nature_light,
                R.drawable.ic_emoji_places_light,
                R.drawable.ic_emoji_symbols_light,
                0 };
        private static final String[] sCategoryLabel =
                { null, null, null, null, null, null, ":-)" };
        private static final int[] sCategoryElementId = {
                KeyboardId.ELEMENT_EMOJI_RECENTS,
                KeyboardId.ELEMENT_EMOJI_CATEGORY1,
                KeyboardId.ELEMENT_EMOJI_CATEGORY2,
                KeyboardId.ELEMENT_EMOJI_CATEGORY3,
                KeyboardId.ELEMENT_EMOJI_CATEGORY4,
                KeyboardId.ELEMENT_EMOJI_CATEGORY5,
                KeyboardId.ELEMENT_EMOJI_CATEGORY6, };
        private final HashMap<String, Integer> mCategoryNameToIdMap = CollectionUtils.newHashMap();
        private final ArrayList<Integer> mShownCategories = new ArrayList<Integer>();

        public EmojiCategory() {
            for (int i = 0; i < sCategoryName.length; ++i) {
                mCategoryNameToIdMap.put(sCategoryName[i], i);
            }
            mShownCategories.add(CATEGORY_RECENTS);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mShownCategories.add(CATEGORY_PEOPLE);
                mShownCategories.add(CATEGORY_OBJECTS);
                mShownCategories.add(CATEGORY_NATURE);
                mShownCategories.add(CATEGORY_PLACES);
                // TODO: Restore last saved category
                mCurrentCategory = CATEGORY_PLACES;
            } else {
                // TODO: Restore last saved category
                mCurrentCategory = CATEGORY_SYMBOLS;
            }
            mShownCategories.add(CATEGORY_SYMBOLS);
            mShownCategories.add(CATEGORY_EMOTICONS);
        }

        public String getCategoryName(int category) {
            return sCategoryName[category];
        }

        public int getCategoryId(String name) {
            return mCategoryNameToIdMap.get(name);
        }

        public int getCategoryIcon(int category) {
            return sCategoryIcon[category];
        }

        public String getCategoryLabel(int category) {
            return sCategoryLabel[category];
        }

        public ArrayList<Integer> getShownCategories() {
            return mShownCategories;
        }

        public int getCurrentCategory() {
            // TODO: Record current category.
            return CATEGORY_PEOPLE;
        }

        public void setCurrentCategory(int category) {
            mCurrentCategory = category;
        }

        public boolean isInRecentTab() {
            return mCurrentCategory == CATEGORY_RECENTS;
        }

        public int getTabIdFromCategory(int category) {
            for (int i = 0; i < mShownCategories.size(); ++i) {
                if (mShownCategories.get(i) == category) {
                    return i;
                }
            }
            Log.w(TAG, "category not found: " + category);
            return 0;
        }

        public int getRecentTabId() {
            return getTabIdFromCategory(CATEGORY_RECENTS);
        }

        public int getCategoryFromTabId(int tabId) {
            return mShownCategories.get(tabId);
        }

        public int getElementIdFromTabId(int tabId) {
            return sCategoryElementId[getCategoryFromTabId(tabId)];
        }
    }

    private final EmojiCategory mEmojiCategory = new EmojiCategory();

    public EmojiKeyboardView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.emojiKeyboardViewStyle);
    }

    public EmojiKeyboardView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        final TypedArray keyboardViewAttr = context.obtainStyledAttributes(attrs,
                R.styleable.KeyboardView, defStyle, R.style.KeyboardView);
        mKeyBackgroundId = keyboardViewAttr.getResourceId(
                R.styleable.KeyboardView_keyBackground, 0);
        mEmojiFunctionalKeyBackgroundId = keyboardViewAttr.getResourceId(
                R.styleable.KeyboardView_keyBackgroundEmojiFunctional, 0);
        keyboardViewAttr.recycle();
        final TypedArray emojiKeyboardViewAttr = context.obtainStyledAttributes(attrs,
                R.styleable.EmojiKeyboardView, defStyle, R.style.EmojiKeyboardView);
        mTabLabelColor = emojiKeyboardViewAttr.getColorStateList(
                R.styleable.EmojiKeyboardView_emojiTabLabelColor);
        emojiKeyboardViewAttr.recycle();
        final KeyboardLayoutSet.Builder builder = new KeyboardLayoutSet.Builder(
                context, null /* editorInfo */);
        final Resources res = context.getResources();
        builder.setSubtype(SubtypeSwitcher.getInstance().getEmojiSubtype());
        builder.setKeyboardGeometry(ResourceUtils.getDefaultKeyboardWidth(res),
                (int)ResourceUtils.getDefaultKeyboardHeight(res)
                        + res.getDimensionPixelSize(R.dimen.suggestions_strip_height));
        builder.setOptions(false, false, false /* lanuageSwitchKeyEnabled */);
        mLayoutSet = builder.build();
        // TODO: Save/restore recent keys from/to preferences.
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        final Resources res = getContext().getResources();
        // The main keyboard expands to the entire this {@link KeyboardView}.
        final int width = ResourceUtils.getDefaultKeyboardWidth(res)
                + getPaddingLeft() + getPaddingRight();
        final int height = ResourceUtils.getDefaultKeyboardHeight(res)
                + res.getDimensionPixelSize(R.dimen.suggestions_strip_height)
                + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(width, height);
    }

    private void addTab(final TabHost host, final int category) {
        final String tabId = mEmojiCategory.getCategoryName(category);
        final TabHost.TabSpec tspec = host.newTabSpec(tabId);
        tspec.setContent(R.id.emoji_keyboard_dummy);
        if (mEmojiCategory.getCategoryIcon(category) != 0) {
            final ImageView iconView = (ImageView)LayoutInflater.from(getContext()).inflate(
                    R.layout.emoji_keyboard_tab_icon, null);
            iconView.setImageResource(mEmojiCategory.getCategoryIcon(category));
            tspec.setIndicator(iconView);
        }
        if (mEmojiCategory.getCategoryLabel(category) != null) {
            final TextView textView = (TextView)LayoutInflater.from(getContext()).inflate(
                    R.layout.emoji_keyboard_tab_label, null);
            textView.setText(mEmojiCategory.getCategoryLabel(category));
            textView.setTextColor(mTabLabelColor);
            tspec.setIndicator(textView);
        }
        host.addTab(tspec);
    }

    @Override
    protected void onFinishInflate() {
        mTabHost = (TabHost)findViewById(R.id.emoji_category_tabhost);
        mTabHost.setup();
        for (final int i : mEmojiCategory.getShownCategories()) {
            addTab(mTabHost, i);
        }
        mTabHost.setOnTabChangedListener(this);
        mTabHost.getTabWidget().setStripEnabled(true);

        mEmojiKeyboardAdapter = new EmojiKeyboardAdapter(mEmojiCategory, mLayoutSet, this);

        mEmojiPager = (ViewPager)findViewById(R.id.emoji_keyboard_pager);
        mEmojiPager.setAdapter(mEmojiKeyboardAdapter);
        mEmojiPager.setOnPageChangeListener(this);
        mEmojiPager.setOffscreenPageLimit(0);
        final Resources res = getResources();
        final EmojiLayoutParams emojiLp = new EmojiLayoutParams(res);
        emojiLp.setPagerProps(mEmojiPager);

        setCurrentCategory(mEmojiCategory.getCurrentCategory(), true /* force */);

        final LinearLayout actionBar = (LinearLayout)findViewById(R.id.emoji_action_bar);
        emojiLp.setActionBarProps(actionBar);

        // TODO: Implement auto repeat, using View.OnTouchListener?
        final ImageView deleteKey = (ImageView)findViewById(R.id.emoji_keyboard_delete);
        deleteKey.setBackgroundResource(mEmojiFunctionalKeyBackgroundId);
        deleteKey.setTag(Constants.CODE_DELETE);
        deleteKey.setOnClickListener(this);
        final ImageView alphabetKey = (ImageView)findViewById(R.id.emoji_keyboard_alphabet);
        alphabetKey.setBackgroundResource(mEmojiFunctionalKeyBackgroundId);
        alphabetKey.setTag(Constants.CODE_SWITCH_ALPHA_SYMBOL);
        alphabetKey.setOnClickListener(this);
        final ImageView spaceKey = (ImageView)findViewById(R.id.emoji_keyboard_space);
        spaceKey.setBackgroundResource(mKeyBackgroundId);
        spaceKey.setTag(Constants.CODE_SPACE);
        spaceKey.setOnClickListener(this);
        emojiLp.setKeyProps(spaceKey);
        final ImageView sendKey = (ImageView)findViewById(R.id.emoji_keyboard_send);
        sendKey.setBackgroundResource(mEmojiFunctionalKeyBackgroundId);
        sendKey.setTag(Constants.CODE_ENTER);
        sendKey.setOnClickListener(this);
    }

    @Override
    public void onTabChanged(final String tabId) {
        final int category = mEmojiCategory.getCategoryId(tabId);
        setCurrentCategory(category, false /* force */);
    }


    @Override
    public void onPageSelected(final int position) {
        setCurrentCategory(mEmojiCategory.getCategoryFromTabId(position), false /* force */);
    }

    @Override
    public void onPageScrollStateChanged(final int state) {
        // Ignore this message. Only want the actual page selected.
    }

    @Override
    public void onPageScrolled(final int position, final float positionOffset,
            final int positionOffsetPixels) {
        // Ignore this message. Only want the actual page selected.
    }

    @Override
    public void onClick(final View v) {
        if (v.getTag() instanceof Integer) {
            final int code = (Integer)v.getTag();
            registerCode(code);
            return;
        }
    }

    private void registerCode(final int code) {
        mKeyboardActionListener.onPressKey(code, 0 /* repeatCount */, true /* isSinglePointer */);
        mKeyboardActionListener.onCodeInput(code, NOT_A_COORDINATE, NOT_A_COORDINATE);
        mKeyboardActionListener.onReleaseKey(code, false /* withSliding */);
    }

    @Override
    public void onKeyClick(final Key key) {
        mEmojiKeyboardAdapter.addRecentKey(key);
        final int code = key.getCode();
        if (code == Constants.CODE_OUTPUT_TEXT) {
            mKeyboardActionListener.onTextInput(key.getOutputText());
            return;
        }
        registerCode(code);
    }

    public void setHardwareAcceleratedDrawingEnabled(final boolean enabled) {
        // TODO:
    }

    public void setKeyboardActionListener(final KeyboardActionListener listener) {
        mKeyboardActionListener = listener;
    }

    private void setCurrentCategory(final int category, final boolean force) {
        if (mEmojiCategory.getCurrentCategory() == category && !force) {
            return;
        }

        mEmojiCategory.setCurrentCategory(category);
        final int newTabId = mEmojiCategory.getTabIdFromCategory(category);
        if (force || mEmojiPager.getCurrentItem() != newTabId) {
            mEmojiPager.setCurrentItem(newTabId, true /* smoothScroll */);
        }
        if (force || mTabHost.getCurrentTab() != newTabId) {
            mTabHost.setCurrentTab(newTabId);
        }
        // TODO: Record current category
    }

    private static class EmojiKeyboardAdapter extends PagerAdapter {
        private final ScrollKeyboardView.OnKeyClickListener mListener;
        private final KeyboardLayoutSet mLayoutSet;
        private final RecentsKeyboard mRecentsKeyboard;
        private final SparseArray<ScrollKeyboardView> mActiveKeyboardView =
                CollectionUtils.newSparseArray();
        private final EmojiCategory mEmojiCategory;
        private int mActivePosition = 0;

        public EmojiKeyboardAdapter(final EmojiCategory emojiCategory,
                final KeyboardLayoutSet layoutSet,
                final ScrollKeyboardView.OnKeyClickListener listener) {
            mEmojiCategory = emojiCategory;
            mListener = listener;
            mLayoutSet = layoutSet;
            mRecentsKeyboard = new RecentsKeyboard(
                    layoutSet.getKeyboard(KeyboardId.ELEMENT_EMOJI_RECENTS));
        }

        public void addRecentKey(final Key key) {
            if (mEmojiCategory.isInRecentTab()) {
                return;
            }
            mRecentsKeyboard.addRecentKey(key);
            final KeyboardView recentKeyboardView =
                    mActiveKeyboardView.get(mEmojiCategory.getRecentTabId());
            if (recentKeyboardView != null) {
                recentKeyboardView.invalidateAllKeys();
            }
        }

        @Override
        public int getCount() {
            return mEmojiCategory.getShownCategories().size();
        }

        @Override
        public void setPrimaryItem(final View container, final int position, final Object object) {
            if (mActivePosition == position) {
                return;
            }
            final ScrollKeyboardView oldKeyboardView = mActiveKeyboardView.get(mActivePosition);
            if (oldKeyboardView != null) {
                oldKeyboardView.releaseCurrentKey();
                oldKeyboardView.deallocateMemory();
            }
            mActivePosition = position;
        }

        @Override
        public Object instantiateItem(final ViewGroup container, final int position) {
            final int elementId = mEmojiCategory.getElementIdFromTabId(position);
            final Keyboard keyboard = (elementId == KeyboardId.ELEMENT_EMOJI_RECENTS)
                    ? mRecentsKeyboard : mLayoutSet.getKeyboard(elementId);
            final LayoutInflater inflater = LayoutInflater.from(container.getContext());
            final View view = inflater.inflate(
                    R.layout.emoji_keyboard_page, container, false /* attachToRoot */);
            final ScrollKeyboardView keyboardView = (ScrollKeyboardView)view.findViewById(
                    R.id.emoji_keyboard_page);
            keyboardView.setKeyboard(keyboard);
            keyboardView.setOnKeyClickListener(mListener);
            final ScrollViewWithNotifier scrollView = (ScrollViewWithNotifier)view.findViewById(
                    R.id.emoji_keyboard_scroller);
            keyboardView.setScrollView(scrollView);
            container.addView(view);
            mActiveKeyboardView.put(position, keyboardView);
            return view;
        }

        @Override
        public boolean isViewFromObject(final View view, final Object object) {
            return view == object;
        }

        @Override
        public void destroyItem(final ViewGroup container, final int position,
                final Object object) {
            final ScrollKeyboardView keyboardView = mActiveKeyboardView.get(position);
            if (keyboardView != null) {
                keyboardView.deallocateMemory();
                mActiveKeyboardView.remove(position);
            }
            container.removeView(keyboardView);
        }
    }
}
