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
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TextView;

import com.android.inputmethod.keyboard.internal.DynamicGridKeyboard;
import com.android.inputmethod.keyboard.internal.EmojiLayoutParams;
import com.android.inputmethod.keyboard.internal.EmojiPageKeyboardView;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SubtypeSwitcher;
import com.android.inputmethod.latin.settings.Settings;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.ResourceUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
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
public final class EmojiPalettesView extends LinearLayout implements OnTabChangeListener,
        ViewPager.OnPageChangeListener, View.OnTouchListener,
        EmojiPageKeyboardView.OnKeyEventListener {
    static final String TAG = EmojiPalettesView.class.getSimpleName();
    private static final boolean DEBUG_PAGER = false;
    private final int mKeyBackgroundId;
    private final int mEmojiFunctionalKeyBackgroundId;
    private final ColorStateList mTabLabelColor;
    private final DeleteKeyOnTouchListener mDeleteKeyOnTouchListener;
    private EmojiPalettesAdapter mEmojiPalettesAdapter;
    private final EmojiLayoutParams mEmojiLayoutParams;

    private TabHost mTabHost;
    private ViewPager mEmojiPager;
    private int mCurrentPagerPosition = 0;
    private EmojiCategoryPageIndicatorView mEmojiCategoryPageIndicatorView;

    private KeyboardActionListener mKeyboardActionListener = KeyboardActionListener.EMPTY_LISTENER;

    private static final int CATEGORY_ID_UNSPECIFIED = -1;
    public static final int CATEGORY_ID_RECENTS = 0;
    public static final int CATEGORY_ID_PEOPLE = 1;
    public static final int CATEGORY_ID_OBJECTS = 2;
    public static final int CATEGORY_ID_NATURE = 3;
    public static final int CATEGORY_ID_PLACES = 4;
    public static final int CATEGORY_ID_SYMBOLS = 5;
    public static final int CATEGORY_ID_EMOTICONS = 6;

    private static class CategoryProperties {
        public int mCategoryId;
        public int mPageCount;
        public CategoryProperties(final int categoryId, final int pageCount) {
            mCategoryId = categoryId;
            mPageCount = pageCount;
        }
    }

    private static class EmojiCategory {
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
                KeyboardId.ELEMENT_EMOJI_CATEGORY6 };
        private final SharedPreferences mPrefs;
        private final int mMaxPageKeyCount;
        private final KeyboardLayoutSet mLayoutSet;
        private final HashMap<String, Integer> mCategoryNameToIdMap = CollectionUtils.newHashMap();
        private final ArrayList<CategoryProperties> mShownCategories =
                CollectionUtils.newArrayList();
        private final ConcurrentHashMap<Long, DynamicGridKeyboard>
                mCategoryKeyboardMap = new ConcurrentHashMap<Long, DynamicGridKeyboard>();

        private int mCurrentCategoryId = CATEGORY_ID_UNSPECIFIED;
        private int mCurrentCategoryPageId = 0;

        public EmojiCategory(final SharedPreferences prefs, final Resources res,
                final KeyboardLayoutSet layoutSet) {
            mPrefs = prefs;
            mMaxPageKeyCount = res.getInteger(R.integer.config_emoji_keyboard_max_page_key_count);
            mLayoutSet = layoutSet;
            for (int i = 0; i < sCategoryName.length; ++i) {
                mCategoryNameToIdMap.put(sCategoryName[i], i);
            }
            addShownCategoryId(CATEGORY_ID_RECENTS);
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2
                    || android.os.Build.VERSION.CODENAME.equalsIgnoreCase("KeyLimePie")
                    || android.os.Build.VERSION.CODENAME.equalsIgnoreCase("KitKat")) {
                addShownCategoryId(CATEGORY_ID_PEOPLE);
                addShownCategoryId(CATEGORY_ID_OBJECTS);
                addShownCategoryId(CATEGORY_ID_NATURE);
                addShownCategoryId(CATEGORY_ID_PLACES);
                mCurrentCategoryId =
                        Settings.readLastShownEmojiCategoryId(mPrefs, CATEGORY_ID_PEOPLE);
            } else {
                mCurrentCategoryId =
                        Settings.readLastShownEmojiCategoryId(mPrefs, CATEGORY_ID_SYMBOLS);
            }
            addShownCategoryId(CATEGORY_ID_SYMBOLS);
            addShownCategoryId(CATEGORY_ID_EMOTICONS);
            getKeyboard(CATEGORY_ID_RECENTS, 0 /* cagetoryPageId */)
                    .loadRecentKeys(mCategoryKeyboardMap.values());
        }

        private void addShownCategoryId(final int categoryId) {
            // Load a keyboard of categoryId
            getKeyboard(categoryId, 0 /* cagetoryPageId */);
            final CategoryProperties properties =
                    new CategoryProperties(categoryId, getCategoryPageCount(categoryId));
            mShownCategories.add(properties);
        }

        public String getCategoryName(final int categoryId, final int categoryPageId) {
            return sCategoryName[categoryId] + "-" + categoryPageId;
        }

        public int getCategoryId(final String name) {
            final String[] strings = name.split("-");
            return mCategoryNameToIdMap.get(strings[0]);
        }

        public int getCategoryIcon(final int categoryId) {
            return sCategoryIcon[categoryId];
        }

        public String getCategoryLabel(final int categoryId) {
            return sCategoryLabel[categoryId];
        }

        public ArrayList<CategoryProperties> getShownCategories() {
            return mShownCategories;
        }

        public int getCurrentCategoryId() {
            return mCurrentCategoryId;
        }

        public int getCurrentCategoryPageSize() {
            return getCategoryPageSize(mCurrentCategoryId);
        }

        public int getCategoryPageSize(final int categoryId) {
            for (final CategoryProperties prop : mShownCategories) {
                if (prop.mCategoryId == categoryId) {
                    return prop.mPageCount;
                }
            }
            Log.w(TAG, "Invalid category id: " + categoryId);
            // Should not reach here.
            return 0;
        }

        public void setCurrentCategoryId(final int categoryId) {
            mCurrentCategoryId = categoryId;
            Settings.writeLastShownEmojiCategoryId(mPrefs, categoryId);
        }

        public void setCurrentCategoryPageId(final int id) {
            mCurrentCategoryPageId = id;
        }

        public int getCurrentCategoryPageId() {
            return mCurrentCategoryPageId;
        }

        public void saveLastTypedCategoryPage() {
            Settings.writeLastTypedEmojiCategoryPageId(
                    mPrefs, mCurrentCategoryId, mCurrentCategoryPageId);
        }

        public boolean isInRecentTab() {
            return mCurrentCategoryId == CATEGORY_ID_RECENTS;
        }

        public int getTabIdFromCategoryId(final int categoryId) {
            for (int i = 0; i < mShownCategories.size(); ++i) {
                if (mShownCategories.get(i).mCategoryId == categoryId) {
                    return i;
                }
            }
            Log.w(TAG, "categoryId not found: " + categoryId);
            return 0;
        }

        // Returns the view pager's page position for the categoryId
        public int getPageIdFromCategoryId(final int categoryId) {
            final int lastSavedCategoryPageId =
                    Settings.readLastTypedEmojiCategoryPageId(mPrefs, categoryId);
            int sum = 0;
            for (int i = 0; i < mShownCategories.size(); ++i) {
                final CategoryProperties props = mShownCategories.get(i);
                if (props.mCategoryId == categoryId) {
                    return sum + lastSavedCategoryPageId;
                }
                sum += props.mPageCount;
            }
            Log.w(TAG, "categoryId not found: " + categoryId);
            return 0;
        }

        public int getRecentTabId() {
            return getTabIdFromCategoryId(CATEGORY_ID_RECENTS);
        }

        private int getCategoryPageCount(final int categoryId) {
            final Keyboard keyboard = mLayoutSet.getKeyboard(sCategoryElementId[categoryId]);
            return (keyboard.getKeys().length - 1) / mMaxPageKeyCount + 1;
        }

        // Returns a pair of the category id and the category page id from the view pager's page
        // position. The category page id is numbered in each category. And the view page position
        // is the position of the current shown page in the view pager which contains all pages of
        // all categories.
        public Pair<Integer, Integer> getCategoryIdAndPageIdFromPagePosition(final int position) {
            int sum = 0;
            for (final CategoryProperties properties : mShownCategories) {
                final int temp = sum;
                sum += properties.mPageCount;
                if (sum > position) {
                    return new Pair<Integer, Integer>(properties.mCategoryId, position - temp);
                }
            }
            return null;
        }

        // Returns a keyboard from the view pager's page position.
        public DynamicGridKeyboard getKeyboardFromPagePosition(final int position) {
            final Pair<Integer, Integer> categoryAndId =
                    getCategoryIdAndPageIdFromPagePosition(position);
            if (categoryAndId != null) {
                return getKeyboard(categoryAndId.first, categoryAndId.second);
            }
            return null;
        }

        private static final Long getCategoryKeyboardMapKey(final int categoryId, final int id) {
            return (((long) categoryId) << Constants.MAX_INT_BIT_COUNT) | id;
        }

        public DynamicGridKeyboard getKeyboard(final int categoryId, final int id) {
            synchronized (mCategoryKeyboardMap) {
                final Long categotyKeyboardMapKey = getCategoryKeyboardMapKey(categoryId, id);
                if (mCategoryKeyboardMap.containsKey(categotyKeyboardMapKey)) {
                    return mCategoryKeyboardMap.get(categotyKeyboardMapKey);
                }

                if (categoryId == CATEGORY_ID_RECENTS) {
                    final DynamicGridKeyboard kbd = new DynamicGridKeyboard(mPrefs,
                            mLayoutSet.getKeyboard(KeyboardId.ELEMENT_EMOJI_RECENTS),
                            mMaxPageKeyCount, categoryId);
                    mCategoryKeyboardMap.put(categotyKeyboardMapKey, kbd);
                    return kbd;
                }

                final Keyboard keyboard = mLayoutSet.getKeyboard(sCategoryElementId[categoryId]);
                final Key[][] sortedKeys = sortKeysIntoPages(keyboard.getKeys(), mMaxPageKeyCount);
                for (int pageId = 0; pageId < sortedKeys.length; ++pageId) {
                    final DynamicGridKeyboard tempKeyboard = new DynamicGridKeyboard(mPrefs,
                            mLayoutSet.getKeyboard(KeyboardId.ELEMENT_EMOJI_RECENTS),
                            mMaxPageKeyCount, categoryId);
                    for (final Key emojiKey : sortedKeys[pageId]) {
                        if (emojiKey == null) {
                            break;
                        }
                        tempKeyboard.addKeyLast(emojiKey);
                    }
                    mCategoryKeyboardMap.put(
                            getCategoryKeyboardMapKey(categoryId, pageId), tempKeyboard);
                }
                return mCategoryKeyboardMap.get(categotyKeyboardMapKey);
            }
        }

        public int getTotalPageCountOfAllCategories() {
            int sum = 0;
            for (CategoryProperties properties : mShownCategories) {
                sum += properties.mPageCount;
            }
            return sum;
        }

        private static Comparator<Key> EMOJI_KEY_COMPARATOR = new Comparator<Key>() {
            @Override
            public int compare(final Key lhs, final Key rhs) {
                final Rect lHitBox = lhs.getHitBox();
                final Rect rHitBox = rhs.getHitBox();
                if (lHitBox.top < rHitBox.top) {
                    return -1;
                } else if (lHitBox.top > rHitBox.top) {
                    return 1;
                }
                if (lHitBox.left < rHitBox.left) {
                    return -1;
                } else if (lHitBox.left > rHitBox.left) {
                    return 1;
                }
                if (lhs.getCode() == rhs.getCode()) {
                    return 0;
                }
                return lhs.getCode() < rhs.getCode() ? -1 : 1;
            }
        };

        private static Key[][] sortKeysIntoPages(final Key[] inKeys, final int maxPageCount) {
            final Key[] keys = Arrays.copyOf(inKeys, inKeys.length);
            Arrays.sort(keys, 0, keys.length, EMOJI_KEY_COMPARATOR);
            final int pageCount = (keys.length - 1) / maxPageCount + 1;
            final Key[][] retval = new Key[pageCount][maxPageCount];
            for (int i = 0; i < keys.length; ++i) {
                retval[i / maxPageCount][i % maxPageCount] = keys[i];
            }
            return retval;
        }
    }

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
            tspec.setIndicator(iconView);
        }
        if (mEmojiCategory.getCategoryLabel(categoryId) != null) {
            final TextView textView = (TextView)LayoutInflater.from(getContext()).inflate(
                    R.layout.emoji_keyboard_tab_label, null);
            textView.setText(mEmojiCategory.getCategoryLabel(categoryId));
            textView.setTextColor(mTabLabelColor);
            tspec.setIndicator(textView);
        }
        host.addTab(tspec);
    }

    @Override
    protected void onFinishInflate() {
        mTabHost = (TabHost)findViewById(R.id.emoji_category_tabhost);
        mTabHost.setup();
        for (final CategoryProperties properties : mEmojiCategory.getShownCategories()) {
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

        final ImageView deleteKey = (ImageView)findViewById(R.id.emoji_keyboard_delete);
        deleteKey.setTag(Constants.CODE_DELETE);
        deleteKey.setOnTouchListener(mDeleteKeyOnTouchListener);
        final ImageView alphabetKey = (ImageView)findViewById(R.id.emoji_keyboard_alphabet);
        alphabetKey.setBackgroundResource(mEmojiFunctionalKeyBackgroundId);
        alphabetKey.setTag(Constants.CODE_SWITCH_ALPHA_SYMBOL);
        alphabetKey.setOnTouchListener(this);
        final ImageView spaceKey = (ImageView)findViewById(R.id.emoji_keyboard_space);
        spaceKey.setBackgroundResource(mKeyBackgroundId);
        spaceKey.setTag(Constants.CODE_SPACE);
        spaceKey.setOnTouchListener(this);
        mEmojiLayoutParams.setKeyProperties(spaceKey);
        final ImageView alphabetKey2 = (ImageView)findViewById(R.id.emoji_keyboard_alphabet2);
        alphabetKey2.setBackgroundResource(mEmojiFunctionalKeyBackgroundId);
        alphabetKey2.setTag(Constants.CODE_SWITCH_ALPHA_SYMBOL);
        alphabetKey2.setOnTouchListener(this);
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

    // Called from {@link EmojiPageKeyboardView} through {@link View.OnTouchListener} interface to
    // handle touch events from View-based elements such as the space bar.
    @Override
    public boolean onTouch(final View v, final MotionEvent event) {
        final Object tag = v.getTag();
        if (!(tag instanceof Integer)) {
            return false;
        }
        final int code = (Integer) tag;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mKeyboardActionListener.onPressKey(
                        code, 0 /* repeatCount */, true /* isSinglePointer */);
                break;
            case MotionEvent.ACTION_UP:
                mKeyboardActionListener.onCodeInput(code, NOT_A_COORDINATE, NOT_A_COORDINATE);
                mKeyboardActionListener.onReleaseKey(code, false /* withSliding */);
                break;
        }
        return false;
    }

    // Called from {@link EmojiPageKeyboardView} through
    // {@link EmojiPageKeyboardView.OnKeyEventListener} interface to handle touch events from
    // non-View-based elements like typical Emoji characters.
    @Override
    public void onPressKey(final Key key) {
        final int code = key.getCode();
        mKeyboardActionListener.onPressKey(code, 0 /* repeatCount */, true /* isSinglePointer */);
    }

    // Called from {@link EmojiPageKeyboardView} through
    // {@link EmojiPageKeyboardView.OnKeyEventListener} interface to handle touch events from
    // non-View-based elements like typical Emoji characters.
    @Override
    public void onReleaseKey(final Key key) {
        mEmojiPalettesAdapter.addRecentKey(key);
        mEmojiCategory.saveLastTypedCategoryPage();
        final int code = key.getCode();
        if (code == Constants.CODE_OUTPUT_TEXT) {
            mKeyboardActionListener.onTextInput(key.getOutputText());
        } else {
            mKeyboardActionListener.onCodeInput(code, NOT_A_COORDINATE, NOT_A_COORDINATE);
        }
        mKeyboardActionListener.onReleaseKey(code, false /* withSliding */);
    }

    public void setHardwareAcceleratedDrawingEnabled(final boolean enabled) {
        // TODO:
    }

    public void startEmojiPalettes() {
        if (DEBUG_PAGER) {
            Log.d(TAG, "allocate emoji palettes memory " + mCurrentPagerPosition);
        }
        mEmojiPager.setAdapter(mEmojiPalettesAdapter);
        mEmojiPager.setCurrentItem(mCurrentPagerPosition);
    }

    public void stopEmojiPalettes() {
        if (DEBUG_PAGER) {
            Log.d(TAG, "deallocate emoji palettes memory");
        }
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

        if (oldCategoryId == CATEGORY_ID_RECENTS) {
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

    private static class EmojiPalettesAdapter extends PagerAdapter {
        private final EmojiPageKeyboardView.OnKeyEventListener mListener;
        private final DynamicGridKeyboard mRecentsKeyboard;
        private final SparseArray<EmojiPageKeyboardView> mActiveKeyboardViews =
                CollectionUtils.newSparseArray();
        private final EmojiCategory mEmojiCategory;
        private int mActivePosition = 0;

        public EmojiPalettesAdapter(final EmojiCategory emojiCategory,
                final EmojiPageKeyboardView.OnKeyEventListener listener) {
            mEmojiCategory = emojiCategory;
            mListener = listener;
            mRecentsKeyboard = mEmojiCategory.getKeyboard(CATEGORY_ID_RECENTS, 0);
        }

        public void flushPendingRecentKeys() {
            mRecentsKeyboard.flushPendingRecentKeys();
            final KeyboardView recentKeyboardView =
                    mActiveKeyboardViews.get(mEmojiCategory.getRecentTabId());
            if (recentKeyboardView != null) {
                recentKeyboardView.invalidateAllKeys();
            }
        }

        public void addRecentKey(final Key key) {
            if (mEmojiCategory.isInRecentTab()) {
                mRecentsKeyboard.addPendingKey(key);
                return;
            }
            mRecentsKeyboard.addKeyFirst(key);
            final KeyboardView recentKeyboardView =
                    mActiveKeyboardViews.get(mEmojiCategory.getRecentTabId());
            if (recentKeyboardView != null) {
                recentKeyboardView.invalidateAllKeys();
            }
        }

        @Override
        public int getCount() {
            return mEmojiCategory.getTotalPageCountOfAllCategories();
        }

        @Override
        public void setPrimaryItem(final ViewGroup container, final int position,
                final Object object) {
            if (mActivePosition == position) {
                return;
            }
            final EmojiPageKeyboardView oldKeyboardView = mActiveKeyboardViews.get(mActivePosition);
            if (oldKeyboardView != null) {
                oldKeyboardView.releaseCurrentKey();
                oldKeyboardView.deallocateMemory();
            }
            mActivePosition = position;
        }

        @Override
        public Object instantiateItem(final ViewGroup container, final int position) {
            if (DEBUG_PAGER) {
                Log.d(TAG, "instantiate item: " + position);
            }
            final EmojiPageKeyboardView oldKeyboardView = mActiveKeyboardViews.get(position);
            if (oldKeyboardView != null) {
                oldKeyboardView.deallocateMemory();
                // This may be redundant but wanted to be safer..
                mActiveKeyboardViews.remove(position);
            }
            final Keyboard keyboard =
                    mEmojiCategory.getKeyboardFromPagePosition(position);
            final LayoutInflater inflater = LayoutInflater.from(container.getContext());
            final EmojiPageKeyboardView keyboardView = (EmojiPageKeyboardView)inflater.inflate(
                    R.layout.emoji_keyboard_page, container, false /* attachToRoot */);
            keyboardView.setKeyboard(keyboard);
            keyboardView.setOnKeyEventListener(mListener);
            container.addView(keyboardView);
            mActiveKeyboardViews.put(position, keyboardView);
            return keyboardView;
        }

        @Override
        public boolean isViewFromObject(final View view, final Object object) {
            return view == object;
        }

        @Override
        public void destroyItem(final ViewGroup container, final int position,
                final Object object) {
            if (DEBUG_PAGER) {
                Log.d(TAG, "destroy item: " + position + ", " + object.getClass().getSimpleName());
            }
            final EmojiPageKeyboardView keyboardView = mActiveKeyboardViews.get(position);
            if (keyboardView != null) {
                keyboardView.deallocateMemory();
                mActiveKeyboardViews.remove(position);
            }
            if (object instanceof View) {
                container.removeView((View)object);
            } else {
                Log.w(TAG, "Warning!!! Emoji palette may be leaking. " + object);
            }
        }
    }

    private static class DeleteKeyOnTouchListener implements OnTouchListener {
        private static final long MAX_REPEAT_COUNT_TIME = TimeUnit.SECONDS.toMillis(30);
        private final int mDeleteKeyPressedBackgroundColor;
        private final long mKeyRepeatStartTimeout;
        private final long mKeyRepeatInterval;

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
            mKeyboardActionListener.onCodeInput(
                    Constants.CODE_DELETE, NOT_A_COORDINATE, NOT_A_COORDINATE);
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
        private void onKeyRepeat() {
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
