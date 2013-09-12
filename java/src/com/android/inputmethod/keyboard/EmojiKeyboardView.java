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
    private final int mKeyBackgroundId;
    private final int mEmojiFunctionalKeyBackgroundId;
    private final ColorStateList mTabLabelColor;
    private final EmojiKeyboardAdapter mEmojiKeyboardAdapter;

    private TabHost mTabHost;
    private ViewPager mEmojiPager;

    private KeyboardActionListener mKeyboardActionListener = KeyboardActionListener.EMPTY_LISTENER;

    private int mCurrentCategory = CATEGORY_UNSPECIFIED;
    private static final int CATEGORY_UNSPECIFIED = -1;
    private static final int CATEGORY_RECENTS = 0;
    private static final int CATEGORY_PEOPLE = 1;
    private static final int CATEGORY_OBJECTS = 2;
    private static final int CATEGORY_NATURE = 3;
    private static final int CATEGORY_PLACES = 4;
    private static final int CATEGORY_SYMBOLS = 5;
    private static final int CATEGORY_EMOTICONS = 6;
    private static final HashMap<String, Integer> sCategoryNameToIdMap =
            CollectionUtils.newHashMap();
    private static final String[] sCategoryName = {
        "recents", "people", "objects", "nature", "places", "symbols", "emoticons"
    };
    private static final int[] sCategoryIcon = new int[] {
        R.drawable.ic_emoji_recent_light,
        R.drawable.ic_emoji_people_light,
        R.drawable.ic_emoji_objects_light,
        R.drawable.ic_emoji_nature_light,
        R.drawable.ic_emoji_places_light,
        R.drawable.ic_emoji_symbols_light,
        0
    };
    private static final String[] sCategoryLabel = {
        null, null, null, null, null, null,
        ":-)"
    };
    private static final int[] sCategoryElementId = {
        KeyboardId.ELEMENT_EMOJI_RECENTS,
        KeyboardId.ELEMENT_EMOJI_CATEGORY1,
        KeyboardId.ELEMENT_EMOJI_CATEGORY2,
        KeyboardId.ELEMENT_EMOJI_CATEGORY3,
        KeyboardId.ELEMENT_EMOJI_CATEGORY4,
        KeyboardId.ELEMENT_EMOJI_CATEGORY5,
        KeyboardId.ELEMENT_EMOJI_CATEGORY6,
    };

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
        final KeyboardLayoutSet layoutSet = builder.build();
        mEmojiKeyboardAdapter = new EmojiKeyboardAdapter(layoutSet, this);
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
        final String tabId = sCategoryName[category];
        sCategoryNameToIdMap.put(tabId, category);
        final TabHost.TabSpec tspec = host.newTabSpec(tabId);
        tspec.setContent(R.id.emoji_keyboard_dummy);
        if (sCategoryIcon[category] != 0) {
            final ImageView iconView = (ImageView)LayoutInflater.from(getContext()).inflate(
                    R.layout.emoji_keyboard_tab_icon, null);
            iconView.setImageResource(sCategoryIcon[category]);
            tspec.setIndicator(iconView);
        }
        if (sCategoryLabel[category] != null) {
            final TextView textView = (TextView)LayoutInflater.from(getContext()).inflate(
                    R.layout.emoji_keyboard_tab_label, null);
            textView.setText(sCategoryLabel[category]);
            textView.setTextColor(mTabLabelColor);
            tspec.setIndicator(textView);
        }
        host.addTab(tspec);
    }

    @Override
    protected void onFinishInflate() {
        mTabHost = (TabHost)findViewById(R.id.emoji_category_tabhost);
        mTabHost.setup();
        addTab(mTabHost, CATEGORY_RECENTS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            addTab(mTabHost, CATEGORY_PEOPLE);
            addTab(mTabHost, CATEGORY_OBJECTS);
            addTab(mTabHost, CATEGORY_NATURE);
            addTab(mTabHost, CATEGORY_PLACES);
        }
        addTab(mTabHost, CATEGORY_SYMBOLS);
        addTab(mTabHost, CATEGORY_EMOTICONS);
        mTabHost.setOnTabChangedListener(this);
        mTabHost.getTabWidget().setStripEnabled(true);

        mEmojiPager = (ViewPager)findViewById(R.id.emoji_keyboard_pager);
        mEmojiPager.setAdapter(mEmojiKeyboardAdapter);
        mEmojiPager.setOnPageChangeListener(this);
        mEmojiPager.setOffscreenPageLimit(0);
        final Resources res = getResources();
        final EmojiLayoutParams emojiLp = new EmojiLayoutParams(res);
        emojiLp.setPagerProps(mEmojiPager);

        // TODO: Record current category.
        final int category = CATEGORY_PEOPLE;
        setCurrentCategory(category, true /* force */);

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
        final int category = sCategoryNameToIdMap.get(tabId);
        setCurrentCategory(category, false /* force */);
    }


    @Override
    public void onPageSelected(final int position) {
        setCurrentCategory(position, false /* force */);
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
        if (mCurrentCategory == category && !force) {
            return;
        }

        mCurrentCategory = category;
        if (force || mEmojiPager.getCurrentItem() != category) {
            mEmojiPager.setCurrentItem(category, true /* smoothScroll */);
        }
        if (force || mTabHost.getCurrentTab() != category) {
            mTabHost.setCurrentTab(category);
        }
        // TODO: Record current category
    }

    private static class EmojiKeyboardAdapter extends PagerAdapter {
        private final ScrollKeyboardView.OnKeyClickListener mListener;
        private final KeyboardLayoutSet mLayoutSet;
        private final RecentsKeyboard mRecentsKeyboard;
        private final SparseArray<ScrollKeyboardView> mActiveKeyboardView =
                CollectionUtils.newSparseArray();
        private int mActivePosition = CATEGORY_UNSPECIFIED;

        public EmojiKeyboardAdapter(final KeyboardLayoutSet layoutSet,
                final ScrollKeyboardView.OnKeyClickListener listener) {
            mListener = listener;
            mLayoutSet = layoutSet;
            mRecentsKeyboard = new RecentsKeyboard(
                    layoutSet.getKeyboard(KeyboardId.ELEMENT_EMOJI_RECENTS));
        }

        public void addRecentKey(final Key key) {
            if (mActivePosition == CATEGORY_RECENTS) {
                return;
            }
            mRecentsKeyboard.addRecentKey(key);
            final KeyboardView recentKeyboardView = mActiveKeyboardView.get(CATEGORY_RECENTS);
            if (recentKeyboardView != null) {
                recentKeyboardView.invalidateAllKeys();
            }
        }

        @Override
        public int getCount() {
            return sCategoryName.length;
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
            final int elementId = sCategoryElementId[position];
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
