/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.keyboard;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;

import com.android.inputmethod.keyboard.internal.KeyboardIconsSet;
import com.android.inputmethod.keyboard.internal.KeyboardParser;
import com.android.inputmethod.keyboard.internal.KeyboardShiftState;
import com.android.inputmethod.latin.R;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Loads an XML description of a keyboard and stores the attributes of the keys. A keyboard
 * consists of rows of keys.
 * <p>The layout file for a keyboard contains XML that looks like the following snippet:</p>
 * <pre>
 * &lt;Keyboard
 *         latin:keyWidth="%10p"
 *         latin:keyHeight="50px"
 *         latin:horizontalGap="2px"
 *         latin:verticalGap="2px" &gt;
 *     &lt;Row latin:keyWidth="32px" &gt;
 *         &lt;Key latin:keyLabel="A" /&gt;
 *         ...
 *     &lt;/Row&gt;
 *     ...
 * &lt;/Keyboard&gt;
 * </pre>
 */
public class Keyboard {
    private static final String TAG = Keyboard.class.getSimpleName();

    public static final int EDGE_LEFT = 0x01;
    public static final int EDGE_RIGHT = 0x02;
    public static final int EDGE_TOP = 0x04;
    public static final int EDGE_BOTTOM = 0x08;

    /** Some common keys code.  These should be aligned with values/keycodes.xml */
    public static final int CODE_ENTER = '\n';
    public static final int CODE_TAB = '\t';
    public static final int CODE_SPACE = ' ';
    public static final int CODE_PERIOD = '.';
    public static final int CODE_DASH = '-';
    public static final int CODE_SINGLE_QUOTE = '\'';
    public static final int CODE_DOUBLE_QUOTE = '"';
    // TODO: Check how this should work for right-to-left languages. It seems to stand
    // that for rtl languages, a closing parenthesis is a left parenthesis. Is this
    // managed by the font? Or is it a different char?
    public static final int CODE_CLOSING_PARENTHESIS = ')';
    public static final int CODE_CLOSING_SQUARE_BRACKET = ']';
    public static final int CODE_CLOSING_CURLY_BRACKET = '}';
    public static final int CODE_CLOSING_ANGLE_BRACKET = '>';
    public static final int CODE_DIGIT0 = '0';
    public static final int CODE_PLUS = '+';


    /** Special keys code.  These should be aligned with values/keycodes.xml */
    public static final int CODE_DUMMY = 0;
    public static final int CODE_SHIFT = -1;
    public static final int CODE_SWITCH_ALPHA_SYMBOL = -2;
    public static final int CODE_CAPSLOCK = -3;
    public static final int CODE_CANCEL = -4;
    public static final int CODE_DELETE = -5;
    public static final int CODE_SETTINGS = -6;
    public static final int CODE_SETTINGS_LONGPRESS = -7;
    public static final int CODE_SHORTCUT = -8;
    // Code value representing the code is not specified.
    public static final int CODE_UNSPECIFIED = -99;

    /** Horizontal gap default for all rows */
    private int mDefaultHorizontalGap;

    /** Default key width */
    private int mDefaultWidth;

    /** Default key height */
    private int mDefaultHeight;

    /** Default gap between rows */
    private int mDefaultVerticalGap;

    /** Popup keyboard template */
    private int mPopupKeyboardResId;

    /** Maximum column for popup keyboard */
    private int mMaxPopupColumn;

    /** True if Right-To-Left keyboard */
    private boolean mIsRtlKeyboard;

    /** List of shift keys in this keyboard and its icons and state */
    private final List<Key> mShiftKeys = new ArrayList<Key>();
    private final HashMap<Key, Drawable> mShiftedIcons = new HashMap<Key, Drawable>();
    private final HashMap<Key, Drawable> mUnshiftedIcons = new HashMap<Key, Drawable>();
    private final HashSet<Key> mShiftLockKeys = new HashSet<Key>();
    private final KeyboardShiftState mShiftState = new KeyboardShiftState();

    /** Total height of the keyboard, including the padding and keys */
    private int mTotalHeight;

    /**
     * Total width (minimum width) of the keyboard, including left side gaps and keys, but not any
     * gaps on the right side.
     */
    private int mMinWidth;

    /** List of keys in this keyboard */
    private final List<Key> mKeys = new ArrayList<Key>();

    /** Width of the screen available to fit the keyboard */
    private final int mDisplayWidth;

    /** Height of the screen */
    private final int mDisplayHeight;

    /** Height of keyboard */
    private int mKeyboardHeight;

    private int mMostCommonKeyWidth = 0;

    public final KeyboardId mId;

    public final KeyboardIconsSet mIconsSet = new KeyboardIconsSet();

    // Variables for pre-computing nearest keys.

    // TODO: Change GRID_WIDTH and GRID_HEIGHT to private.
    public final int GRID_WIDTH;
    public final int GRID_HEIGHT;

    private final ProximityInfo mProximityInfo;

    /**
     * Creates a keyboard from the given xml key layout file.
     * @param context the application or service context
     * @param xmlLayoutResId the resource file that contains the keyboard layout and keys.
     * @param id keyboard identifier
     * @param width keyboard width
     */

    public Keyboard(Context context, int xmlLayoutResId, KeyboardId id, int width) {
        final Resources res = context.getResources();
        GRID_WIDTH = res.getInteger(R.integer.config_keyboard_grid_width);
        GRID_HEIGHT = res.getInteger(R.integer.config_keyboard_grid_height);

        final int horizontalEdgesPadding = (int)res.getDimension(
                R.dimen.keyboard_horizontal_edges_padding);
        mDisplayWidth = width - horizontalEdgesPadding * 2;
        // TODO: Adjust the height by referring to the height of area available for drawing as well.
        mDisplayHeight = res.getDisplayMetrics().heightPixels;

        mDefaultHorizontalGap = 0;
        setKeyWidth(mDisplayWidth / 10);
        mDefaultVerticalGap = 0;
        mDefaultHeight = mDefaultWidth;
        mId = id;
        loadKeyboard(context, xmlLayoutResId);
        mProximityInfo = new ProximityInfo(
                GRID_WIDTH, GRID_HEIGHT, getMinWidth(), getHeight(), getKeyWidth(), mKeys);
    }

    public int getProximityInfo() {
        return mProximityInfo.getNativeProximityInfo();
    }

    public List<Key> getKeys() {
        return mKeys;
    }

    public int getHorizontalGap() {
        return mDefaultHorizontalGap;
    }

    public void setHorizontalGap(int gap) {
        mDefaultHorizontalGap = gap;
    }

    public int getVerticalGap() {
        return mDefaultVerticalGap;
    }

    public void setVerticalGap(int gap) {
        mDefaultVerticalGap = gap;
    }

    public int getRowHeight() {
        return mDefaultHeight;
    }

    public void setRowHeight(int height) {
        mDefaultHeight = height;
    }

    public int getKeyWidth() {
        return mDefaultWidth;
    }

    public void setKeyWidth(int width) {
        mDefaultWidth = width;
    }

    /**
     * Returns the total height of the keyboard
     * @return the total height of the keyboard
     */
    public int getHeight() {
        return mTotalHeight;
    }

    public void setHeight(int height) {
        mTotalHeight = height;
    }

    public int getMinWidth() {
        return mMinWidth;
    }

    public void setMinWidth(int minWidth) {
        mMinWidth = minWidth;
    }

    public int getDisplayHeight() {
        return mDisplayHeight;
    }

    public int getDisplayWidth() {
        return mDisplayWidth;
    }

    public int getKeyboardHeight() {
        return mKeyboardHeight;
    }

    public void setKeyboardHeight(int height) {
        mKeyboardHeight = height;
    }

    public boolean isRtlKeyboard() {
        return mIsRtlKeyboard;
    }

    public void setRtlKeyboard(boolean isRtl) {
        mIsRtlKeyboard = isRtl;
    }

    public int getPopupKeyboardResId() {
        return mPopupKeyboardResId;
    }

    public void setPopupKeyboardResId(int resId) {
        mPopupKeyboardResId = resId;
    }

    public int getMaxPopupKeyboardColumn() {
        return mMaxPopupColumn;
    }

    public void setMaxPopupKeyboardColumn(int column) {
        mMaxPopupColumn = column;
    }

    public void addShiftKey(Key key) {
        if (key == null) return;
        mShiftKeys.add(key);
        if (key.mSticky) {
            mShiftLockKeys.add(key);
        }
    }

    public void addShiftedIcon(Key key, Drawable icon) {
        if (key == null) return;
        mUnshiftedIcons.put(key, key.getIcon());
        mShiftedIcons.put(key, icon);
    }

    public boolean hasShiftLockKey() {
        return !mShiftLockKeys.isEmpty();
    }

    public boolean setShiftLocked(boolean newShiftLockState) {
        for (final Key key : mShiftLockKeys) {
            // To represent "shift locked" state. The highlight is handled by background image that
            // might be a StateListDrawable.
            key.setHighlightOn(newShiftLockState);
            // To represent "shifted" state. The key might have a shifted icon.
            if (newShiftLockState && mShiftedIcons.containsKey(key)) {
                key.setIcon(mShiftedIcons.get(key));
            } else {
                key.setIcon(mUnshiftedIcons.get(key));
            }
        }
        mShiftState.setShiftLocked(newShiftLockState);
        return true;
    }

    public boolean isShiftLocked() {
        return mShiftState.isShiftLocked();
    }

    public boolean setShifted(boolean newShiftState) {
        for (final Key key : mShiftKeys) {
            if (!newShiftState && !mShiftState.isShiftLocked()) {
                key.setIcon(mUnshiftedIcons.get(key));
            } else if (newShiftState && !mShiftState.isShiftedOrShiftLocked()) {
                key.setIcon(mShiftedIcons.get(key));
            }
        }
        return mShiftState.setShifted(newShiftState);
    }

    public boolean isShiftedOrShiftLocked() {
        return mShiftState.isShiftedOrShiftLocked();
    }

    public void setAutomaticTemporaryUpperCase() {
        setShifted(true);
        mShiftState.setAutomaticTemporaryUpperCase();
    }

    public boolean isAutomaticTemporaryUpperCase() {
        return isAlphaKeyboard() && mShiftState.isAutomaticTemporaryUpperCase();
    }

    public boolean isManualTemporaryUpperCase() {
        return isAlphaKeyboard() && mShiftState.isManualTemporaryUpperCase();
    }

    public boolean isManualTemporaryUpperCaseFromAuto() {
        return isAlphaKeyboard() && mShiftState.isManualTemporaryUpperCaseFromAuto();
    }

    public KeyboardShiftState getKeyboardShiftState() {
        return mShiftState;
    }

    public boolean isAlphaKeyboard() {
        return mId.isAlphabetKeyboard();
    }

    public boolean isPhoneKeyboard() {
        return mId.isPhoneKeyboard();
    }

    public boolean isNumberKeyboard() {
        return mId.isNumberKeyboard();
    }

    public CharSequence adjustLabelCase(CharSequence label) {
        if (isShiftedOrShiftLocked() && !TextUtils.isEmpty(label) && label.length() < 3
                && Character.isLowerCase(label.charAt(0))) {
            return label.toString().toUpperCase(mId.mLocale);
        }
        return label;
    }

    /**
     * Returns the indices of the keys that are closest to the given point.
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return the array of integer indices for the nearest keys to the given point. If the given
     * point is out of range, then an array of size zero is returned.
     */
    public int[] getNearestKeys(int x, int y) {
        return mProximityInfo.getNearestKeys(x, y);
    }

    /**
     * Compute the most common key width in order to use it as proximity key detection threshold.
     *
     * @return The most common key width in the keyboard
     */
    public int getMostCommonKeyWidth() {
        if (mMostCommonKeyWidth == 0) {
            final HashMap<Integer, Integer> histogram = new HashMap<Integer, Integer>();
            int maxCount = 0;
            int mostCommonWidth = 0;
            for (final Key key : mKeys) {
                final Integer width = key.mWidth + key.mGap;
                Integer count = histogram.get(width);
                if (count == null)
                    count = 0;
                histogram.put(width, ++count);
                if (count > maxCount) {
                    maxCount = count;
                    mostCommonWidth = width;
                }
            }
            mMostCommonKeyWidth = mostCommonWidth;
        }
        return mMostCommonKeyWidth;
    }

    private void loadKeyboard(Context context, int xmlLayoutResId) {
        try {
            KeyboardParser parser = new KeyboardParser(this, context);
            parser.parseKeyboard(xmlLayoutResId);
            // mMinWidth is the width of this keyboard which is maximum width of row.
            mMinWidth = parser.getMaxRowWidth();
            mTotalHeight = parser.getTotalHeight();
        } catch (XmlPullParserException e) {
            Log.w(TAG, "keyboard XML parse error: " + e);
            throw new IllegalArgumentException(e);
        } catch (IOException e) {
            Log.w(TAG, "keyboard XML parse error: " + e);
            throw new RuntimeException(e);
        }
    }
}
