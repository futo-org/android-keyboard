/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.inputmethod.keyboard.emoji;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.util.Pair;

import com.android.inputmethod.compat.BuildCompatUtils;
import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.KeyboardLayoutSet;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.settings.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

final class EmojiCategory {
    private final String TAG = EmojiCategory.class.getSimpleName();

    private static final int ID_UNSPECIFIED = -1;
    public static final int ID_RECENTS = 0;
    private static final int ID_PEOPLE = 1;
    private static final int ID_OBJECTS = 2;
    private static final int ID_NATURE = 3;
    private static final int ID_PLACES = 4;
    private static final int ID_SYMBOLS = 5;
    private static final int ID_EMOTICONS = 6;
    private static final int ID_FLAGS = 7;
    private static final int ID_EIGHT_SMILEY_PEOPLE = 8;
    private static final int ID_EIGHT_ANIMALS_NATURE = 9;
    private static final int ID_EIGHT_FOOD_DRINK = 10;
    private static final int ID_EIGHT_TRAVEL_PLACES = 11;
    private static final int ID_EIGHT_ACTIVITY = 12;
    private static final int ID_EIGHT_OBJECTS = 13;
    private static final int ID_EIGHT_SYMBOLS = 14;
    private static final int ID_EIGHT_FLAGS = 15;
    private static final int ID_EIGHT_SMILEY_PEOPLE_BORING = 16;

    public final class CategoryProperties {
        public final int mCategoryId;
        public final int mPageCount;
        public CategoryProperties(final int categoryId, final int pageCount) {
            mCategoryId = categoryId;
            mPageCount = pageCount;
        }
    }

    private static final String[] sCategoryName = {
            "recents",
            "people",
            "objects",
            "nature",
            "places",
            "symbols",
            "emoticons",
            "flags",
            "smiley & people",
            "animals & nature",
            "food & drink",
            "travel & places",
            "activity",
            "objects2",
            "symbols2",
            "flags2",
            "smiley & people2" };

    private static final int[] sCategoryTabIconAttr = {
            R.styleable.EmojiPalettesView_iconEmojiRecentsTab,
            R.styleable.EmojiPalettesView_iconEmojiCategory1Tab,
            R.styleable.EmojiPalettesView_iconEmojiCategory2Tab,
            R.styleable.EmojiPalettesView_iconEmojiCategory3Tab,
            R.styleable.EmojiPalettesView_iconEmojiCategory4Tab,
            R.styleable.EmojiPalettesView_iconEmojiCategory5Tab,
            R.styleable.EmojiPalettesView_iconEmojiCategory6Tab,
            R.styleable.EmojiPalettesView_iconEmojiCategory7Tab,
            R.styleable.EmojiPalettesView_iconEmojiCategory8Tab,
            R.styleable.EmojiPalettesView_iconEmojiCategory9Tab,
            R.styleable.EmojiPalettesView_iconEmojiCategory10Tab,
            R.styleable.EmojiPalettesView_iconEmojiCategory11Tab,
            R.styleable.EmojiPalettesView_iconEmojiCategory12Tab,
            R.styleable.EmojiPalettesView_iconEmojiCategory13Tab,
            R.styleable.EmojiPalettesView_iconEmojiCategory14Tab,
            R.styleable.EmojiPalettesView_iconEmojiCategory15Tab,
            R.styleable.EmojiPalettesView_iconEmojiCategory16Tab };

    private static final int[] sAccessibilityDescriptionResourceIdsForCategories = {
            R.string.spoken_descrption_emoji_category_recents,
            R.string.spoken_descrption_emoji_category_people,
            R.string.spoken_descrption_emoji_category_objects,
            R.string.spoken_descrption_emoji_category_nature,
            R.string.spoken_descrption_emoji_category_places,
            R.string.spoken_descrption_emoji_category_symbols,
            R.string.spoken_descrption_emoji_category_emoticons,
            R.string.spoken_descrption_emoji_category_flags,
            R.string.spoken_descrption_emoji_category_eight_smiley_people,
            R.string.spoken_descrption_emoji_category_eight_animals_nature,
            R.string.spoken_descrption_emoji_category_eight_food_drink,
            R.string.spoken_descrption_emoji_category_eight_travel_places,
            R.string.spoken_descrption_emoji_category_eight_activity,
            R.string.spoken_descrption_emoji_category_objects,
            R.string.spoken_descrption_emoji_category_symbols,
            R.string.spoken_descrption_emoji_category_flags,
            R.string.spoken_descrption_emoji_category_eight_smiley_people };

    private static final int[] sCategoryElementId = {
            KeyboardId.ELEMENT_EMOJI_RECENTS,
            KeyboardId.ELEMENT_EMOJI_CATEGORY1,
            KeyboardId.ELEMENT_EMOJI_CATEGORY2,
            KeyboardId.ELEMENT_EMOJI_CATEGORY3,
            KeyboardId.ELEMENT_EMOJI_CATEGORY4,
            KeyboardId.ELEMENT_EMOJI_CATEGORY5,
            KeyboardId.ELEMENT_EMOJI_CATEGORY6,
            KeyboardId.ELEMENT_EMOJI_CATEGORY7,
            KeyboardId.ELEMENT_EMOJI_CATEGORY8,
            KeyboardId.ELEMENT_EMOJI_CATEGORY9,
            KeyboardId.ELEMENT_EMOJI_CATEGORY10,
            KeyboardId.ELEMENT_EMOJI_CATEGORY11,
            KeyboardId.ELEMENT_EMOJI_CATEGORY12,
            KeyboardId.ELEMENT_EMOJI_CATEGORY13,
            KeyboardId.ELEMENT_EMOJI_CATEGORY14,
            KeyboardId.ELEMENT_EMOJI_CATEGORY15,
            KeyboardId.ELEMENT_EMOJI_CATEGORY16 };

    private final SharedPreferences mPrefs;
    private final Resources mRes;
    private final int mMaxPageKeyCount;
    private final KeyboardLayoutSet mLayoutSet;
    private final HashMap<String, Integer> mCategoryNameToIdMap = new HashMap<>();
    private final int[] mCategoryTabIconId = new int[sCategoryName.length];
    private final ArrayList<CategoryProperties> mShownCategories = new ArrayList<>();
    private final ConcurrentHashMap<Long, DynamicGridKeyboard> mCategoryKeyboardMap =
            new ConcurrentHashMap<>();

    private int mCurrentCategoryId = EmojiCategory.ID_UNSPECIFIED;
    private int mCurrentCategoryPageId = 0;

    public EmojiCategory(final SharedPreferences prefs, final Resources res,
            final KeyboardLayoutSet layoutSet, final TypedArray emojiPaletteViewAttr) {
        mPrefs = prefs;
        mRes = res;
        mMaxPageKeyCount = res.getInteger(R.integer.config_emoji_keyboard_max_page_key_count);
        mLayoutSet = layoutSet;
        for (int i = 0; i < sCategoryName.length; ++i) {
            mCategoryNameToIdMap.put(sCategoryName[i], i);
            mCategoryTabIconId[i] = emojiPaletteViewAttr.getResourceId(
                    sCategoryTabIconAttr[i], 0);
        }

        int defaultCategoryId = EmojiCategory.ID_SYMBOLS;
        addShownCategoryId(EmojiCategory.ID_RECENTS);
        if (BuildCompatUtils.EFFECTIVE_SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (canShowUnicodeEightEmoji()) {
                defaultCategoryId = EmojiCategory.ID_EIGHT_SMILEY_PEOPLE;
                addShownCategoryId(EmojiCategory.ID_EIGHT_SMILEY_PEOPLE);
                addShownCategoryId(EmojiCategory.ID_EIGHT_ANIMALS_NATURE);
                addShownCategoryId(EmojiCategory.ID_EIGHT_FOOD_DRINK);
                addShownCategoryId(EmojiCategory.ID_EIGHT_TRAVEL_PLACES);
                addShownCategoryId(EmojiCategory.ID_EIGHT_ACTIVITY);
                addShownCategoryId(EmojiCategory.ID_EIGHT_OBJECTS);
                addShownCategoryId(EmojiCategory.ID_EIGHT_SYMBOLS);
                addShownCategoryId(EmojiCategory.ID_FLAGS); // Exclude combinations without glyphs.
            } else {
                defaultCategoryId = EmojiCategory.ID_PEOPLE;
                addShownCategoryId(EmojiCategory.ID_PEOPLE);
                addShownCategoryId(EmojiCategory.ID_OBJECTS);
                addShownCategoryId(EmojiCategory.ID_NATURE);
                addShownCategoryId(EmojiCategory.ID_PLACES);
                addShownCategoryId(EmojiCategory.ID_SYMBOLS);
                if (canShowFlagEmoji()) {
                    addShownCategoryId(EmojiCategory.ID_FLAGS);
                }
            }
        } else {
            addShownCategoryId(EmojiCategory.ID_SYMBOLS);
        }
        addShownCategoryId(EmojiCategory.ID_EMOTICONS);

        DynamicGridKeyboard recentsKbd =
                getKeyboard(EmojiCategory.ID_RECENTS, 0 /* categoryPageId */);
        recentsKbd.loadRecentKeys(mCategoryKeyboardMap.values());

        mCurrentCategoryId = Settings.readLastShownEmojiCategoryId(mPrefs, defaultCategoryId);
        Log.i(TAG, "Last Emoji category id is " + mCurrentCategoryId);
        if (!isShownCategoryId(mCurrentCategoryId)) {
            Log.i(TAG, "Last emoji category " + mCurrentCategoryId +
                    " is invalid, starting in " + defaultCategoryId);
            mCurrentCategoryId = defaultCategoryId;
        } else if (mCurrentCategoryId == EmojiCategory.ID_RECENTS &&
                recentsKbd.getSortedKeys().isEmpty()) {
            Log.i(TAG, "No recent emojis found, starting in category " + defaultCategoryId);
            mCurrentCategoryId = defaultCategoryId;
        }
    }

    private void addShownCategoryId(final int categoryId) {
        // Load a keyboard of categoryId
        getKeyboard(categoryId, 0 /* categoryPageId */);
        final CategoryProperties properties =
                new CategoryProperties(categoryId, getCategoryPageCount(categoryId));
        mShownCategories.add(properties);
    }

    private boolean isShownCategoryId(final int categoryId) {
        for (final CategoryProperties prop : mShownCategories) {
            if (prop.mCategoryId == categoryId) {
                return true;
            }
        }
        return false;
    }

    public static String getCategoryName(final int categoryId, final int categoryPageId) {
        return sCategoryName[categoryId] + "-" + categoryPageId;
    }

    public int getCategoryId(final String name) {
        final String[] strings = name.split("-");
        return mCategoryNameToIdMap.get(strings[0]);
    }

    public int getCategoryTabIcon(final int categoryId) {
        return mCategoryTabIconId[categoryId];
    }

    public String getAccessibilityDescription(final int categoryId) {
        return mRes.getString(sAccessibilityDescriptionResourceIdsForCategories[categoryId]);
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
        return mCurrentCategoryId == EmojiCategory.ID_RECENTS;
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
        return getTabIdFromCategoryId(EmojiCategory.ID_RECENTS);
    }

    private int getCategoryPageCount(final int categoryId) {
        final Keyboard keyboard = mLayoutSet.getKeyboard(sCategoryElementId[categoryId]);
        return (keyboard.getSortedKeys().size() - 1) / mMaxPageKeyCount + 1;
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
                return new Pair<>(properties.mCategoryId, position - temp);
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
        return (((long) categoryId) << Integer.SIZE) | id;
    }

    public DynamicGridKeyboard getKeyboard(final int categoryId, final int id) {
        synchronized (mCategoryKeyboardMap) {
            final Long categoryKeyboardMapKey = getCategoryKeyboardMapKey(categoryId, id);
            if (mCategoryKeyboardMap.containsKey(categoryKeyboardMapKey)) {
                return mCategoryKeyboardMap.get(categoryKeyboardMapKey);
            }

            if (categoryId == EmojiCategory.ID_RECENTS) {
                final DynamicGridKeyboard kbd = new DynamicGridKeyboard(mPrefs,
                        mLayoutSet.getKeyboard(KeyboardId.ELEMENT_EMOJI_RECENTS),
                        mMaxPageKeyCount, categoryId);
                mCategoryKeyboardMap.put(categoryKeyboardMapKey, kbd);
                return kbd;
            }

            final Keyboard keyboard = mLayoutSet.getKeyboard(sCategoryElementId[categoryId]);
            final Key[][] sortedKeys = sortKeysIntoPages(
                    keyboard.getSortedKeys(), mMaxPageKeyCount);
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
            return mCategoryKeyboardMap.get(categoryKeyboardMapKey);
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

    private static Key[][] sortKeysIntoPages(final List<Key> inKeys, final int maxPageCount) {
        final ArrayList<Key> keys = new ArrayList<>(inKeys);
        Collections.sort(keys, EMOJI_KEY_COMPARATOR);
        final int pageCount = (keys.size() - 1) / maxPageCount + 1;
        final Key[][] retval = new Key[pageCount][maxPageCount];
        for (int i = 0; i < keys.size(); ++i) {
            retval[i / maxPageCount][i % maxPageCount] = keys.get(i);
        }
        return retval;
    }

    private static boolean canShowFlagEmoji() {
        Paint paint = new Paint();
        String switzerland = "\uD83C\uDDE8\uD83C\uDDED"; //  U+1F1E8 U+1F1ED Flag for Switzerland
        try {
            return paint.hasGlyph(switzerland);
        } catch (NoSuchMethodError e) {
            // Compare display width of single-codepoint emoji to width of flag emoji to determine
            // whether flag is rendered as single glyph or two adjacent regional indicator symbols.
            float flagWidth = paint.measureText(switzerland);
            float standardWidth = paint.measureText("\uD83D\uDC27"); //  U+1F427 Penguin
            return flagWidth < standardWidth * 1.25;
            // This assumes that a valid glyph for the flag emoji must be less than 1.25 times
            // the width of the penguin.
        }
    }

    private static boolean canShowUnicodeEightEmoji() {
        Paint paint = new Paint();
        String cheese = "\uD83E\uDDC0"; //  U+1F9C0 Cheese wedge
        try {
            return paint.hasGlyph(cheese);
        } catch (NoSuchMethodError e) {
            float cheeseWidth = paint.measureText(cheese);
            float tofuWidth = paint.measureText("\uFFFE");
            return cheeseWidth > tofuWidth;
            // This assumes that a valid glyph for the cheese wedge must be greater than the width
            // of the noncharacter.
        }
    }
}
