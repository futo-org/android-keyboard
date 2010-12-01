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

package com.android.inputmethod.latin;

import com.android.inputmethod.latin.BaseKeyboardParser.ParseException;
import com.android.inputmethod.latin.KeyStyles.KeyStyle;
import com.android.inputmethod.latin.KeyboardSwitcher.KeyboardId;

import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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
public class BaseKeyboard {

    static final String TAG = "BaseKeyboard";

    public static final int EDGE_LEFT = 0x01;
    public static final int EDGE_RIGHT = 0x02;
    public static final int EDGE_TOP = 0x04;
    public static final int EDGE_BOTTOM = 0x08;

    public static final int KEYCODE_SHIFT = -1;
    public static final int KEYCODE_MODE_CHANGE = -2;
    public static final int KEYCODE_CANCEL = -3;
    public static final int KEYCODE_DONE = -4;
    public static final int KEYCODE_DELETE = -5;
    public static final int KEYCODE_ALT = -6;

    /** Horizontal gap default for all rows */
    private int mDefaultHorizontalGap;

    /** Default key width */
    private int mDefaultWidth;

    /** Default key height */
    private int mDefaultHeight;

    /** Default gap between rows */
    private int mDefaultVerticalGap;

    /** Is the keyboard in the shifted state */
    private boolean mShifted;

    /** List of shift keys in this keyboard */
    private final List<Key> mShiftKeys = new ArrayList<Key>();

    /** List of shift keys and its shifted state icon */
    private final HashMap<Key, Drawable> mShiftedIcons = new HashMap<Key, Drawable>();

    /** Total height of the keyboard, including the padding and keys */
    private int mTotalHeight;

    /**
     * Total width of the keyboard, including left side gaps and keys, but not any gaps on the
     * right side.
     */
    private int mTotalWidth;

    /** List of keys in this keyboard */
    private final List<Key> mKeys = new ArrayList<Key>();

    /** Width of the screen available to fit the keyboard */
    private final int mDisplayWidth;

    /** Height of the screen */
    private final int mDisplayHeight;

    protected final KeyboardId mId;

    // Variables for pre-computing nearest keys.

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

    /**
     * Container for keys in the keyboard. All keys in a row are at the same Y-coordinate.
     * Some of the key size defaults can be overridden per row from what the {@link BaseKeyboard}
     * defines.
     */
    public static class Row {
        /** Default width of a key in this row. */
        public int defaultWidth;
        /** Default height of a key in this row. */
        public int defaultHeight;
        /** Default horizontal gap between keys in this row. */
        public int defaultHorizontalGap;
        /** Vertical gap following this row. */
        public int verticalGap;
        /**
         * Edge flags for this row of keys. Possible values that can be assigned are
         * {@link BaseKeyboard#EDGE_TOP EDGE_TOP} and {@link BaseKeyboard#EDGE_BOTTOM EDGE_BOTTOM}
         */
        public int rowEdgeFlags;

        private final BaseKeyboard parent;

        private Row(BaseKeyboard parent) {
            this.parent = parent;
        }

        public Row(Resources res, BaseKeyboard parent, XmlResourceParser parser) {
            this.parent = parent;
            TypedArray a = res.obtainAttributes(Xml.asAttributeSet(parser),
                    R.styleable.BaseKeyboard);
            defaultWidth = BaseKeyboardParser.getDimensionOrFraction(a,
                    R.styleable.BaseKeyboard_keyWidth,
                    parent.mDisplayWidth, parent.mDefaultWidth);
            defaultHeight = BaseKeyboardParser.getDimensionOrFraction(a,
                    R.styleable.BaseKeyboard_keyHeight,
                    parent.mDisplayHeight, parent.mDefaultHeight);
            defaultHorizontalGap = BaseKeyboardParser.getDimensionOrFraction(a,
                    R.styleable.BaseKeyboard_horizontalGap,
                    parent.mDisplayWidth, parent.mDefaultHorizontalGap);
            verticalGap = BaseKeyboardParser.getDimensionOrFraction(a,
                    R.styleable.BaseKeyboard_verticalGap,
                    parent.mDisplayHeight, parent.mDefaultVerticalGap);
            a.recycle();
            a = res.obtainAttributes(Xml.asAttributeSet(parser),
                    R.styleable.BaseKeyboard_Row);
            rowEdgeFlags = a.getInt(R.styleable.BaseKeyboard_Row_rowEdgeFlags, 0);
        }
    }

    /**
     * Class for describing the position and characteristics of a single key in the keyboard.
     */
    public static class Key {
        /**
         * All the key codes (unicode or custom code) that this key could generate, zero'th
         * being the most important.
         */
        public int[] codes;
        /** The unicode that this key generates in manual temporary upper case mode. */
        public int manualTemporaryUpperCaseCode;

        /** Label to display */
        public CharSequence label;
        /** Option of the label */
        public int labelOption;

        /** Icon to display instead of a label. Icon takes precedence over a label */
        public Drawable icon;
        /** Hint icon to display on the key in conjunction with the label */
        public Drawable hintIcon;
        /** Preview version of the icon, for the preview popup */
        /**
         * The hint icon to display on the key when keyboard is in manual temporary upper case
         * mode.
         */
        public Drawable manualTemporaryUpperCaseHintIcon;

        public Drawable iconPreview;
        /** Width of the key, not including the gap */
        public int width;
        /** Height of the key, not including the gap */
        public int height;
        /** The horizontal gap before this key */
        public int gap;
        /** Whether this key is sticky, i.e., a toggle key */
        public boolean sticky;
        /** X coordinate of the key in the keyboard layout */
        public int x;
        /** Y coordinate of the key in the keyboard layout */
        public int y;
        /** The current pressed state of this key */
        public boolean pressed;
        /** If this is a sticky key, is it on? */
        public boolean on;
        /** Text to output when pressed. This can be multiple characters, like ".com" */
        public CharSequence text;
        /** Popup characters */
        public CharSequence popupCharacters;

        /**
         * Flags that specify the anchoring to edges of the keyboard for detecting touch events
         * that are just out of the boundary of the key. This is a bit mask of
         * {@link BaseKeyboard#EDGE_LEFT}, {@link BaseKeyboard#EDGE_RIGHT},
         * {@link BaseKeyboard#EDGE_TOP} and {@link BaseKeyboard#EDGE_BOTTOM}.
         */
        public int edgeFlags;
        /** Whether this is a modifier key, such as Shift or Alt */
        public boolean modifier;
        /** The BaseKeyboard that this key belongs to */
        protected final BaseKeyboard keyboard;
        /**
         * If this key pops up a mini keyboard, this is the resource id for the XML layout for that
         * keyboard.
         */
        public int popupResId;
        /** Whether this key repeats itself when held down */
        public boolean repeatable;


        private final static int[] KEY_STATE_NORMAL_ON = {
            android.R.attr.state_checkable,
            android.R.attr.state_checked
        };

        private final static int[] KEY_STATE_PRESSED_ON = {
            android.R.attr.state_pressed,
            android.R.attr.state_checkable,
            android.R.attr.state_checked
        };

        private final static int[] KEY_STATE_NORMAL_OFF = {
            android.R.attr.state_checkable
        };

        private final static int[] KEY_STATE_PRESSED_OFF = {
            android.R.attr.state_pressed,
            android.R.attr.state_checkable
        };

        private final static int[] KEY_STATE_NORMAL = {
        };

        private final static int[] KEY_STATE_PRESSED = {
            android.R.attr.state_pressed
        };

        /** Create an empty key with no attributes. */
        public Key(Row parent) {
            keyboard = parent.parent;
            height = parent.defaultHeight;
            gap = parent.defaultHorizontalGap;
            width = parent.defaultWidth - gap;
            edgeFlags = parent.rowEdgeFlags;
        }

        /** Create a key with the given top-left coordinate and extract its attributes from
         * the XML parser.
         * @param res resources associated with the caller's context
         * @param parent the row that this key belongs to. The row must already be attached to
         * a {@link BaseKeyboard}.
         * @param x the x coordinate of the top-left
         * @param y the y coordinate of the top-left
         * @param parser the XML parser containing the attributes for this key
         */
        public Key(Resources res, Row parent, int x, int y, XmlResourceParser parser,
                KeyStyles keyStyles) {
            this(parent);

            TypedArray a = res.obtainAttributes(Xml.asAttributeSet(parser),
                    R.styleable.BaseKeyboard);
            height = BaseKeyboardParser.getDimensionOrFraction(a,
                    R.styleable.BaseKeyboard_keyHeight,
                    keyboard.mDisplayHeight, parent.defaultHeight);
            gap = BaseKeyboardParser.getDimensionOrFraction(a,
                    R.styleable.BaseKeyboard_horizontalGap,
                    keyboard.mDisplayWidth, parent.defaultHorizontalGap);
            width = BaseKeyboardParser.getDimensionOrFraction(a,
                    R.styleable.BaseKeyboard_keyWidth,
                    keyboard.mDisplayWidth, parent.defaultWidth) - gap;
            a.recycle();

            a = res.obtainAttributes(Xml.asAttributeSet(parser), R.styleable.BaseKeyboard_Key);

            final KeyStyle style;
            if (a.hasValue(R.styleable.BaseKeyboard_Key_keyStyle)) {
                String styleName = a.getString(R.styleable.BaseKeyboard_Key_keyStyle);
                style = keyStyles.getKeyStyle(styleName);
                if (style == null)
                    throw new ParseException("Unknown key style: " + styleName, parser);
            } else {
                style = keyStyles.getEmptyKeyStyle();
            }

            // Horizontal gap is divided equally to both sides of the key.
            this.x = x + gap / 2;
            this.y = y;

            codes = style.getIntArray(a, R.styleable.BaseKeyboard_Key_codes);
            iconPreview = style.getDrawable(a, R.styleable.BaseKeyboard_Key_iconPreview);
            setDefaultBounds(iconPreview);
            popupCharacters = style.getText(a, R.styleable.BaseKeyboard_Key_popupCharacters);
            popupResId = style.getResourceId(a, R.styleable.BaseKeyboard_Key_popupKeyboard, 0);
            repeatable = style.getBoolean(a, R.styleable.BaseKeyboard_Key_isRepeatable, false);
            modifier = style.getBoolean(a, R.styleable.BaseKeyboard_Key_isModifier, false);
            sticky = style.getBoolean(a, R.styleable.BaseKeyboard_Key_isSticky, false);
            edgeFlags = style.getFlag(a, R.styleable.BaseKeyboard_Key_keyEdgeFlags, 0);
            edgeFlags |= parent.rowEdgeFlags;

            icon = style.getDrawable(a, R.styleable.BaseKeyboard_Key_keyIcon);
            setDefaultBounds(icon);
            hintIcon = style.getDrawable(a, R.styleable.BaseKeyboard_Key_keyHintIcon);
            setDefaultBounds(hintIcon);
            manualTemporaryUpperCaseHintIcon = style.getDrawable(a,
                    R.styleable.BaseKeyboard_Key_manualTemporaryUpperCaseHintIcon);
            setDefaultBounds(manualTemporaryUpperCaseHintIcon);

            label = style.getText(a, R.styleable.BaseKeyboard_Key_keyLabel);
            labelOption = style.getFlag(a, R.styleable.BaseKeyboard_Key_keyLabelOption, 0);
            manualTemporaryUpperCaseCode = style.getInt(a,
                    R.styleable.BaseKeyboard_Key_manualTemporaryUpperCaseCode, 0);
            text = style.getText(a, R.styleable.BaseKeyboard_Key_keyOutputText);
            final Drawable shiftedIcon = style.getDrawable(a,
                    R.styleable.BaseKeyboard_Key_shiftedIcon);
            if (shiftedIcon != null)
                keyboard.getShiftedIcons().put(this, shiftedIcon);

            if (codes == null && !TextUtils.isEmpty(label)) {
                codes = new int[] { label.charAt(0) };
            }
            a.recycle();
        }

        /**
         * Informs the key that it has been pressed, in case it needs to change its appearance or
         * state.
         * @see #onReleased(boolean)
         */
        public void onPressed() {
            pressed = !pressed;
        }

        /**
         * Changes the pressed state of the key. If it is a sticky key, it will also change the
         * toggled state of the key if the finger was release inside.
         * @param inside whether the finger was released inside the key
         * @see #onPressed()
         */
        public void onReleased(boolean inside) {
            pressed = !pressed;
            if (sticky) {
                on = !on;
            }
        }

        /**
         * Detects if a point falls inside this key.
         * @param x the x-coordinate of the point
         * @param y the y-coordinate of the point
         * @return whether or not the point falls inside the key. If the key is attached to an
         * edge, it will assume that all points between the key and the edge are considered to be
         * inside the key.
         */
        public boolean isInside(int x, int y) {
            boolean leftEdge = (edgeFlags & EDGE_LEFT) > 0;
            boolean rightEdge = (edgeFlags & EDGE_RIGHT) > 0;
            boolean topEdge = (edgeFlags & EDGE_TOP) > 0;
            boolean bottomEdge = (edgeFlags & EDGE_BOTTOM) > 0;
            if ((x >= this.x || (leftEdge && x <= this.x + this.width))
                    && (x < this.x + this.width || (rightEdge && x >= this.x))
                    && (y >= this.y || (topEdge && y <= this.y + this.height))
                    && (y < this.y + this.height || (bottomEdge && y >= this.y))) {
                return true;
            } else {
                return false;
            }
        }

        /**
         * Returns the square of the distance to the nearest edge of the key and the given point.
         * @param x the x-coordinate of the point
         * @param y the y-coordinate of the point
         * @return the square of the distance of the point from the nearest edge of the key
         */
        public int squaredDistanceToEdge(int x, int y) {
            final int left = this.x;
            final int right = left + this.width;
            final int top = this.y;
            final int bottom = top + this.height;
            final int edgeX = x < left ? left : (x > right ? right : x);
            final int edgeY = y < top ? top : (y > bottom ? bottom : y);
            final int dx = x - edgeX;
            final int dy = y - edgeY;
            return dx * dx + dy * dy;
        }

        /**
         * Returns the drawable state for the key, based on the current state and type of the key.
         * @return the drawable state of the key.
         * @see android.graphics.drawable.StateListDrawable#setState(int[])
         */
        public int[] getCurrentDrawableState() {
            int[] states = KEY_STATE_NORMAL;

            if (on) {
                if (pressed) {
                    states = KEY_STATE_PRESSED_ON;
                } else {
                    states = KEY_STATE_NORMAL_ON;
                }
            } else {
                if (sticky) {
                    if (pressed) {
                        states = KEY_STATE_PRESSED_OFF;
                    } else {
                        states = KEY_STATE_NORMAL_OFF;
                    }
                } else {
                    if (pressed) {
                        states = KEY_STATE_PRESSED;
                    }
                }
            }
            return states;
        }
    }

    /**
     * Creates a keyboard from the given xml key layout file.
     * @param context the application or service context
     * @param xmlLayoutResId the resource file that contains the keyboard layout and keys.
     */
    public BaseKeyboard(Context context, int xmlLayoutResId) {
        this(context, xmlLayoutResId, null);
    }

    /**
     * Creates a keyboard from the given keyboard identifier.
     * @param context the application or service context
     * @param id keyboard identifier
     */
    public BaseKeyboard(Context context, KeyboardId id) {
        this(context, id.getXmlId(), id);
    }

    /**
     * Creates a keyboard from the given xml key layout file.
     * @param context the application or service context
     * @param xmlLayoutResId the resource file that contains the keyboard layout and keys.
     * @param id keyboard identifier
     */
    private BaseKeyboard(Context context, int xmlLayoutResId, KeyboardId id) {
        this(context, xmlLayoutResId, id,
                context.getResources().getDisplayMetrics().widthPixels,
                context.getResources().getDisplayMetrics().heightPixels);
    }

    private BaseKeyboard(Context context, int xmlLayoutResId, KeyboardId id, int width,
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
    }

    /**
     * <p>Creates a blank keyboard from the given resource file and populates it with the specified
     * characters in left-to-right, top-to-bottom fashion, using the specified number of columns.
     * </p>
     * <p>If the specified number of columns is -1, then the keyboard will fit as many keys as
     * possible in each row.</p>
     * @param context the application or service context
     * @param layoutTemplateResId the layout template file, containing no keys.
     * @param characters the list of characters to display on the keyboard. One key will be created
     * for each character.
     * @param columns the number of columns of keys to display. If this number is greater than the
     * number of keys that can fit in a row, it will be ignored. If this number is -1, the
     * keyboard will fit as many keys as possible in each row.
     */
    public BaseKeyboard(Context context, int layoutTemplateResId,
            CharSequence characters, int columns, int horizontalPadding) {
        this(context, layoutTemplateResId);
        int x = 0;
        int y = 0;
        int column = 0;
        mTotalWidth = 0;

        Row row = new Row(this);
        row.defaultHeight = mDefaultHeight;
        row.defaultWidth = mDefaultWidth;
        row.defaultHorizontalGap = mDefaultHorizontalGap;
        row.verticalGap = mDefaultVerticalGap;
        row.rowEdgeFlags = EDGE_TOP | EDGE_BOTTOM;
        final int maxColumns = columns == -1 ? Integer.MAX_VALUE : columns;
        for (int i = 0; i < characters.length(); i++) {
            char c = characters.charAt(i);
            if (column >= maxColumns
                    || x + mDefaultWidth + horizontalPadding > mDisplayWidth) {
                x = 0;
                y += mDefaultVerticalGap + mDefaultHeight;
                column = 0;
            }
            final Key key = new Key(row);
            // Horizontal gap is divided equally to both sides of the key.
            key.x = x + key.gap / 2;
            key.y = y;
            key.label = String.valueOf(c);
            key.codes = new int[] { c };
            column++;
            x += key.width + key.gap;
            mKeys.add(key);
            if (x > mTotalWidth) {
                mTotalWidth = x;
            }
        }
        mTotalHeight = y + mDefaultHeight;
    }

    public KeyboardId getKeyboardId() {
        return mId;
    }

    public List<Key> getKeys() {
        return mKeys;
    }

    protected int getHorizontalGap() {
        return mDefaultHorizontalGap;
    }

    protected void setHorizontalGap(int gap) {
        mDefaultHorizontalGap = gap;
    }

    protected int getVerticalGap() {
        return mDefaultVerticalGap;
    }

    protected void setVerticalGap(int gap) {
        mDefaultVerticalGap = gap;
    }

    protected int getKeyHeight() {
        return mDefaultHeight;
    }

    protected void setKeyHeight(int height) {
        mDefaultHeight = height;
    }

    protected int getKeyWidth() {
        return mDefaultWidth;
    }

    protected void setKeyWidth(int width) {
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

    public int getMinWidth() {
        return mTotalWidth;
    }

    public int getKeyboardHeight() {
        return mDisplayHeight;
    }

    public int getKeyboardWidth() {
        return mDisplayWidth;
    }

    public boolean setShifted(boolean shiftState) {
        for (final Key key : mShiftKeys) {
            key.on = shiftState;
        }
        if (mShifted != shiftState) {
            mShifted = shiftState;
            return true;
        }
        return false;
    }

    public boolean isShiftedOrShiftLocked() {
        return mShifted;
    }

    public List<Key> getShiftKeys() {
        return mShiftKeys;
    }

    public Map<Key, Drawable> getShiftedIcons() {
        return mShiftedIcons;
    }

    private void computeNearestNeighbors() {
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

    // TODO should be private
    protected BaseKeyboard.Row createRowFromXml(Resources res, XmlResourceParser parser) {
        return new BaseKeyboard.Row(res, this, parser);
    }

    // TODO should be private
    protected BaseKeyboard.Key createKeyFromXml(Resources res, Row parent, int x, int y,
            XmlResourceParser parser, KeyStyles keyStyles) {
        return new BaseKeyboard.Key(res, parent, x, y, parser, keyStyles);
    }

    private void loadKeyboard(Context context, int xmlLayoutResId) {
        try {
            final Resources res = context.getResources();
            BaseKeyboardParser parser = new BaseKeyboardParser(this, res);
            parser.parseKeyboard(res.getXml(xmlLayoutResId));
            // mTotalWidth is the width of this keyboard which is maximum width of row.
            mTotalWidth = parser.getMaxRowWidth();
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
