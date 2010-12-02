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

import com.android.inputmethod.keyboard.KeyboardParser.ParseException;
import com.android.inputmethod.keyboard.KeyStyles.KeyStyle;
import com.android.inputmethod.latin.R;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Xml;

/**
 * Class for describing the position and characteristics of a single key in the keyboard.
 */
public class Key {
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
     * {@link Keyboard#EDGE_LEFT}, {@link Keyboard#EDGE_RIGHT},
     * {@link Keyboard#EDGE_TOP} and {@link Keyboard#EDGE_BOTTOM}.
     */
    public int edgeFlags;
    /** Whether this is a modifier key, such as Shift or Alt */
    public boolean modifier;
    /** The Keyboard that this key belongs to */
    protected final Keyboard keyboard;
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
     * a {@link Keyboard}.
     * @param x the x coordinate of the top-left
     * @param y the y coordinate of the top-left
     * @param parser the XML parser containing the attributes for this key
     */
    public Key(Resources res, Row parent, int x, int y, XmlResourceParser parser,
            KeyStyles keyStyles) {
        this(parent);

        TypedArray a = res.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.Keyboard);
        height = KeyboardParser.getDimensionOrFraction(a,
                R.styleable.Keyboard_keyHeight,
                keyboard.mDisplayHeight, parent.defaultHeight);
        gap = KeyboardParser.getDimensionOrFraction(a,
                R.styleable.Keyboard_horizontalGap,
                keyboard.mDisplayWidth, parent.defaultHorizontalGap);
        width = KeyboardParser.getDimensionOrFraction(a,
                R.styleable.Keyboard_keyWidth,
                keyboard.mDisplayWidth, parent.defaultWidth) - gap;
        a.recycle();

        a = res.obtainAttributes(Xml.asAttributeSet(parser), R.styleable.Keyboard_Key);

        final KeyStyle style;
        if (a.hasValue(R.styleable.Keyboard_Key_keyStyle)) {
            String styleName = a.getString(R.styleable.Keyboard_Key_keyStyle);
            style = keyStyles.getKeyStyle(styleName);
            if (style == null)
                throw new ParseException("Unknown key style: " + styleName, parser);
        } else {
            style = keyStyles.getEmptyKeyStyle();
        }

        // Horizontal gap is divided equally to both sides of the key.
        this.x = x + gap / 2;
        this.y = y;

        codes = style.getIntArray(a, R.styleable.Keyboard_Key_codes);
        iconPreview = style.getDrawable(a, R.styleable.Keyboard_Key_iconPreview);
        Keyboard.setDefaultBounds(iconPreview);
        popupCharacters = style.getText(a, R.styleable.Keyboard_Key_popupCharacters);
        popupResId = style.getResourceId(a, R.styleable.Keyboard_Key_popupKeyboard, 0);
        repeatable = style.getBoolean(a, R.styleable.Keyboard_Key_isRepeatable, false);
        modifier = style.getBoolean(a, R.styleable.Keyboard_Key_isModifier, false);
        sticky = style.getBoolean(a, R.styleable.Keyboard_Key_isSticky, false);
        edgeFlags = style.getFlag(a, R.styleable.Keyboard_Key_keyEdgeFlags, 0);
        edgeFlags |= parent.rowEdgeFlags;

        icon = style.getDrawable(a, R.styleable.Keyboard_Key_keyIcon);
        Keyboard.setDefaultBounds(icon);
        hintIcon = style.getDrawable(a, R.styleable.Keyboard_Key_keyHintIcon);
        Keyboard.setDefaultBounds(hintIcon);
        manualTemporaryUpperCaseHintIcon = style.getDrawable(a,
                R.styleable.Keyboard_Key_manualTemporaryUpperCaseHintIcon);
        Keyboard.setDefaultBounds(manualTemporaryUpperCaseHintIcon);

        label = style.getText(a, R.styleable.Keyboard_Key_keyLabel);
        labelOption = style.getFlag(a, R.styleable.Keyboard_Key_keyLabelOption, 0);
        manualTemporaryUpperCaseCode = style.getInt(a,
                R.styleable.Keyboard_Key_manualTemporaryUpperCaseCode, 0);
        text = style.getText(a, R.styleable.Keyboard_Key_keyOutputText);
        final Drawable shiftedIcon = style.getDrawable(a,
                R.styleable.Keyboard_Key_shiftedIcon);
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
        boolean leftEdge = (edgeFlags & Keyboard.EDGE_LEFT) > 0;
        boolean rightEdge = (edgeFlags & Keyboard.EDGE_RIGHT) > 0;
        boolean topEdge = (edgeFlags & Keyboard.EDGE_TOP) > 0;
        boolean bottomEdge = (edgeFlags & Keyboard.EDGE_BOTTOM) > 0;
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
