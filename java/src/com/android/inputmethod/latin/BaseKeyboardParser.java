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

import com.android.inputmethod.latin.BaseKeyboard.Key;
import com.android.inputmethod.latin.BaseKeyboard.Row;

import org.xmlpull.v1.XmlPullParserException;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.util.TypedValue;
import android.util.Xml;
import android.view.InflateException;

import java.io.IOException;

public class BaseKeyboardParser {
    // Keyboard XML Tags
    private static final String TAG_KEYBOARD = "Keyboard";
    private static final String TAG_ROW = "Row";
    private static final String TAG_KEY = "Key";
    private static final String TAG_SPACER = "Spacer";
    private static final String TAG_INCLUDE = "include";
    private static final String TAG_MERGE = "merge";

    private final BaseKeyboard mKeyboard;
    private final Resources mResources;

    private int mCurrentX = 0;
    private int mCurrentY = 0;
    private int mMaxRowWidth = 0;
    private int mTotalHeight = 0;
    private Row mCurrentRow = null;

    public BaseKeyboardParser(BaseKeyboard keyboard, Resources res) {
        mKeyboard = keyboard;
        mResources = res;
    }

    public int getMaxRowWidth() {
        return mMaxRowWidth;
    }

    public int getTotalHeight() {
        return mTotalHeight;
    }

    public void parseKeyboard(XmlResourceParser parser)
            throws XmlPullParserException, IOException {
        final BaseKeyboard keyboard = mKeyboard;
        Key key = null;

        int event;
        while ((event = parser.next()) != XmlResourceParser.END_DOCUMENT) {
            if (event == XmlResourceParser.START_TAG) {
                String tag = parser.getName();
                if (TAG_ROW.equals(tag)) {
                    // TODO createRowFromXml should not be called from
                    // BaseKeyboard constructor.
                    Row row = keyboard.createRowFromXml(mResources, parser);
                    if (!startRow(row))
                        skipToEndOfRow(parser);
                } else if (TAG_KEY.equals(tag)) {
                    // TODO createKeyFromXml should not be called from
                    // BaseKeyboard constructor.
                    key = keyboard.createKeyFromXml(mResources, mCurrentRow, mCurrentX, mCurrentY,
                            parser);
                    keyboard.getKeys().add(key);
                    if (key.codes[0] == BaseKeyboard.KEYCODE_SHIFT)
                        keyboard.getShiftKeys().add(key);
                } else if (TAG_SPACER.equals(tag)) {
                    parseSpacerAttribute(parser);
                } else if (TAG_KEYBOARD.equals(tag)) {
                    parseKeyboardAttributes(parser);
                } else if (TAG_INCLUDE.equals(tag)) {
                    if (parser.getDepth() == 0)
                        throw new InflateException("<include /> cannot be the root element");
                    parseInclude(parser);
                } else if (TAG_MERGE.equals(tag)) {
                    throw new InflateException(
                            "<merge> must not be appeared in keyboard XML file");
                } else {
                    throw new InflateException("unknown start tag: " + tag);
                }
            } else if (event == XmlResourceParser.END_TAG) {
                String tag = parser.getName();
                if (TAG_KEY.equals(tag)) {
                    endKey(key);
                } else if (TAG_ROW.equals(tag)) {
                    endRow();
                } else if (TAG_SPACER.equals(tag)) {
                    ;
                } else if (TAG_KEYBOARD.equals(tag)) {
                    endKeyboard(mKeyboard.getVerticalGap());
                } else if (TAG_INCLUDE.equals(tag)) {
                    ;
                } else if (TAG_MERGE.equals(tag)) {
                    return;
                } else {
                    throw new InflateException("unknown end tag: " + tag);
                }
            }
        }
    }

    // return true if the row is valid for this keyboard mode
    private boolean startRow(Row row) {
        mCurrentX = 0;
        mCurrentRow = row;
        return row.mode == 0 || row.mode == mKeyboard.getKeyboardMode();
    }

    private void skipRow() {
        mCurrentRow = null;
    }

    private void endRow() {
        if (mCurrentRow == null)
            throw new InflateException("orphant end row tag");
        mCurrentY += mCurrentRow.verticalGap + mCurrentRow.defaultHeight;
        mCurrentRow = null;
    }

    private void endKey(Key key) {
        mCurrentX += key.gap + key.width;
        if (mCurrentX > mMaxRowWidth)
            mMaxRowWidth = mCurrentX;
    }

    private void endKeyboard(int defaultVerticalGap) {
        mTotalHeight = mCurrentY - defaultVerticalGap;
    }

    private void setSpacer(int gap) {
        mCurrentX += gap;
    }

    private void parseSpacerAttribute(XmlResourceParser parser) {
        final TypedArray a = mResources.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.BaseKeyboard);
        int gap = getDimensionOrFraction(a, R.styleable.BaseKeyboard_horizontalGap,
                mKeyboard.getKeyboardWidth(), 0);
        a.recycle();
        setSpacer(gap);
    }

    private void parseInclude(XmlResourceParser parent)
            throws XmlPullParserException, IOException {
        final TypedArray a = mResources.obtainAttributes(Xml.asAttributeSet(parent),
                R.styleable.BaseKeyboard_Include);
        final int keyboardLayout = a.getResourceId(
                R.styleable.BaseKeyboard_Include_keyboardLayout, 0);
        a.recycle();
        if (keyboardLayout == 0)
            throw new InflateException("<include /> must have keyboardLayout attribute");
        final XmlResourceParser parser = mResources.getLayout(keyboardLayout);

        int event;
        while ((event = parser.next()) != XmlResourceParser.END_DOCUMENT) {
            if (event == XmlResourceParser.START_TAG) {
                String name = parser.getName();
                if (TAG_MERGE.equals(name)) {
                    parseKeyboard(parser);
                    return;
                } else {
                    throw new InflateException(
                            "include keyboard layout must have <merge> root element");
                }
            }
        }
    }

    private void skipToEndOfRow(XmlResourceParser parser)
            throws XmlPullParserException, IOException {
        int event;
        while ((event = parser.next()) != XmlResourceParser.END_DOCUMENT) {
            if (event == XmlResourceParser.END_TAG) {
                String tag = parser.getName();
                if (TAG_ROW.equals(tag)) {
                    skipRow();
                    return;
                }
            }
        }
        throw new InflateException("can not find </Row>");
    }

    private void parseKeyboardAttributes(XmlResourceParser parser) {
        final BaseKeyboard keyboard = mKeyboard;
        final TypedArray a = mResources.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.BaseKeyboard);
        final int width = keyboard.getKeyboardWidth();
        final int height = keyboard.getKeyboardHeight();
        keyboard.setKeyWidth(getDimensionOrFraction(a,
                R.styleable.BaseKeyboard_keyWidth, width, width / 10));
        keyboard.setKeyHeight(getDimensionOrFraction(a,
                R.styleable.BaseKeyboard_keyHeight, height, 50));
        keyboard.setHorizontalGap(getDimensionOrFraction(a,
                R.styleable.BaseKeyboard_horizontalGap, width, 0));
        keyboard.setVerticalGap(getDimensionOrFraction(a,
                R.styleable.BaseKeyboard_verticalGap, height, 0));
        a.recycle();
    }

    public static int getDimensionOrFraction(TypedArray a, int index, int base, int defValue) {
        final TypedValue value = a.peekValue(index);
        if (value == null)
            return defValue;
        if (value.type == TypedValue.TYPE_DIMENSION) {
            return a.getDimensionPixelOffset(index, defValue);
        } else if (value.type == TypedValue.TYPE_FRACTION) {
            // Round it to avoid values like 47.9999 from getting truncated
            return Math.round(a.getFraction(index, base, base, defValue));
        }
        return defValue;
    }
}
