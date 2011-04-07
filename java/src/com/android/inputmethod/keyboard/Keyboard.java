/*
 * Copyright (C) 2010 Google Inc.
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

import com.android.inputmethod.latin.R;

import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

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
    private static final String TAG = "Keyboard";

    public static final int EDGE_LEFT = 0x01;
    public static final int EDGE_RIGHT = 0x02;
    public static final int EDGE_TOP = 0x04;
    public static final int EDGE_BOTTOM = 0x08;

    /** Some common keys code.  These should be aligned with values/keycodes.xml */
    public static final int CODE_ENTER = '\n';
    public static final int CODE_TAB = '\t';
    public static final int CODE_SPACE = ' ';
    public static final int CODE_PERIOD = '.';

    /** Special keys code.  These should be aligned with values/keycodes.xml */
    public static final int CODE_DUMMY = 0;
    public static final int CODE_SHIFT = -1;
    public static final int CODE_SWITCH_ALPHA_SYMBOL = -2;
    public static final int CODE_CANCEL = -3;
    public static final int CODE_DONE = -4;
    public static final int CODE_DELETE = -5;
    public static final int CODE_ALT = -6;
    // Code value representing the code is not specified.
    public static final int CODE_UNSPECIFIED = -99;
    public static final int CODE_SETTINGS = -100;
    public static final int CODE_SETTINGS_LONGPRESS = -101;
    // TODO: remove this once LatinIME stops referring to this.
    public static final int CODE_VOICE = -102;
    public static final int CODE_CAPSLOCK = -103;
    public static final int CODE_NEXT_LANGUAGE = -104;
    public static final int CODE_PREV_LANGUAGE = -105;

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

    /** List of shift keys in this keyboard and its icons and state */
    private final List<Key> mShiftKeys = new ArrayList<Key>();
    private final HashMap<Key, Drawable> mShiftedIcons = new HashMap<Key, Drawable>();
    private final HashMap<Key, Drawable> mNormalShiftIcons = new HashMap<Key, Drawable>();
    private final HashSet<Key> mShiftLockEnabled = new HashSet<Key>();
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

    public final KeyboardId mId;

    // Variables for pre-computing nearest keys.

    // TODO: Change GRID_WIDTH and GRID_HEIGHT to private.
    public final int GRID_WIDTH;
    public final int GRID_HEIGHT;
    private final int GRID_SIZE;
    private int mCellWidth;
    private int mCellHeight;
    private int[][] mGridNeighbors;
    private int mProximityThreshold;
    private static int[] EMPTY_INT_ARRAY = new int[0];
    /** Number of key widths from current touch point to search for nearest keys. */
    private static float SEARCH_DISTANCE = 1.2f;

    private final ProximityInfo mProximityInfo;

    /**
     * Creates a keyboard from the given xml key layout file.
     * @param context the application or service context
     * @param xmlLayoutResId the resource file that contains the keyboard layout and keys.
     * @param id keyboard identifier
     */
    public Keyboard(Context context, int xmlLayoutResId, KeyboardId id) {
        this(context, xmlLayoutResId, id,
                context.getResources().getDisplayMetrics().widthPixels,
                context.getResources().getDisplayMetrics().heightPixels);
    }

    private Keyboard(Context context, int xmlLayoutResId, KeyboardId id, int width,
            int height) {
        Resources res = context.getResources();
        GRID_WIDTH = res.getInteger(R.integer.config_keyboard_grid_width);
        GRID_HEIGHT = res.getInteger(R.integer.config_keyboard_grid_height);
        GRID_SIZE = GRID_WIDTH * GRID_HEIGHT;

        mDisplayWidth = width;
        mDisplayHeight = height;

        mDefaultHorizontalGap = 0;
        setKeyWidth(mDisplayWidth / 10);
        mDefaultVerticalGap = 0;
        mDefaultHeight = mDefaultWidth;
        mId = id;
        loadKeyboard(context, xmlLayoutResId);
        mProximityInfo = new ProximityInfo(GRID_WIDTH, GRID_HEIGHT);
    }

    public int getProximityInfo() {
        return mProximityInfo.getNativeProximityInfo(this);
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
        final int threshold = (int) (width * SEARCH_DISTANCE);
        mProximityThreshold = threshold * threshold;
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

    public List<Key> getShiftKeys() {
        return mShiftKeys;
    }

    public Map<Key, Drawable> getShiftedIcons() {
        return mShiftedIcons;
    }

    public void enableShiftLock() {
        for (final Key key : getShiftKeys()) {
            mShiftLockEnabled.add(key);
            mNormalShiftIcons.put(key, key.getIcon());
        }
    }

    public boolean isShiftLockEnabled(Key key) {
        return mShiftLockEnabled.contains(key);
    }

    public boolean setShiftLocked(boolean newShiftLockState) {
        final Map<Key, Drawable> shiftedIcons = getShiftedIcons();
        for (final Key key : getShiftKeys()) {
            key.mHighlightOn = newShiftLockState;
            key.setIcon(newShiftLockState ? shiftedIcons.get(key) : mNormalShiftIcons.get(key));
        }
        mShiftState.setShiftLocked(newShiftLockState);
        return true;
    }

    public boolean isShiftLocked() {
        return mShiftState.isShiftLocked();
    }

    public boolean setShifted(boolean newShiftState) {
        final Map<Key, Drawable> shiftedIcons = getShiftedIcons();
        for (final Key key : getShiftKeys()) {
            if (!newShiftState && !mShiftState.isShiftLocked()) {
                key.setIcon(mNormalShiftIcons.get(key));
            } else if (newShiftState && !mShiftState.isShiftedOrShiftLocked()) {
                key.setIcon(shiftedIcons.get(key));
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
        return mId != null && mId.isAlphabetKeyboard();
    }

    public boolean isPhoneKeyboard() {
        return mId != null && mId.isPhoneKeyboard();
    }

    public boolean isNumberKeyboard() {
        return mId != null && mId.isNumberKeyboard();
    }

    // TODO: Move this function to ProximityInfo and make this private.
    public void computeNearestNeighbors() {
        // Round-up so we don't have any pixels outside the grid
        mCellWidth = (getMinWidth() + GRID_WIDTH - 1) / GRID_WIDTH;
        mCellHeight = (getHeight() + GRID_HEIGHT - 1) / GRID_HEIGHT;
        mGridNeighbors = new int[GRID_SIZE][];
        final int[] indices = new int[mKeys.size()];
        final int gridWidth = GRID_WIDTH * mCellWidth;
        final int gridHeight = GRID_HEIGHT * mCellHeight;
        final int threshold = mProximityThreshold;
        for (int x = 0; x < gridWidth; x += mCellWidth) {
            for (int y = 0; y < gridHeight; y += mCellHeight) {
                final int centerX = x + mCellWidth / 2;
                final int centerY = y + mCellHeight / 2;
                int count = 0;
                for (int i = 0; i < mKeys.size(); i++) {
                    final Key key = mKeys.get(i);
                    if (key.squaredDistanceToEdge(centerX, centerY) < threshold)
                        indices[count++] = i;
                }
                final int[] cell = new int[count];
                System.arraycopy(indices, 0, cell, 0, count);
                mGridNeighbors[(y / mCellHeight) * GRID_WIDTH + (x / mCellWidth)] = cell;
            }
        }
        mProximityInfo.setProximityInfo(mGridNeighbors, getMinWidth(), getHeight(), mKeys);
    }

    public boolean isInside(Key key, int x, int y) {
        return key.isOnKey(x, y);
    }

    /**
     * Returns the indices of the keys that are closest to the given point.
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return the array of integer indices for the nearest keys to the given point. If the given
     * point is out of range, then an array of size zero is returned.
     */
    public int[] getNearestKeys(int x, int y) {
        if (mGridNeighbors == null) computeNearestNeighbors();
        if (x >= 0 && x < getMinWidth() && y >= 0 && y < getHeight()) {
            int index = (y / mCellHeight) * GRID_WIDTH + (x / mCellWidth);
            if (index < GRID_SIZE) {
                return mGridNeighbors[index];
            }
        }
        return EMPTY_INT_ARRAY;
    }

    private void loadKeyboard(Context context, int xmlLayoutResId) {
        try {
            KeyboardParser parser = new KeyboardParser(this, context.getResources());
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

    protected static void setDefaultBounds(Drawable drawable)  {
        if (drawable != null)
            drawable.setBounds(0, 0, drawable.getIntrinsicWidth(),
                    drawable.getIntrinsicHeight());
    }
}
