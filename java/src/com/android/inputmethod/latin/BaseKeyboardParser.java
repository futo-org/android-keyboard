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
import android.util.Log;
import android.util.TypedValue;
import android.util.Xml;
import android.view.InflateException;

import java.io.IOException;
import java.util.List;

/**
 * Parser for BaseKeyboard.
 *
 * This class parses Keyboard XML file and fill out keys in BaseKeyboard.
 * The Keyboard XML file looks like:
 * <pre>
 *   &gt;!-- xml/keyboard.xml --&lt;
 *   &gt;Keyboard keyboard_attributes*&lt;
 *     &gt;!-- Keyboard Content --&lt;
 *     &gt;Row row_attributes*&lt;
 *       &gt;!-- Row Content --&lt;
 *       &gt;Key key_attributes* /&lt;
 *       &gt;Spacer horizontalGap="0.2in" /&lt;
 *       &gt;include keyboardLayout="@xml/other_keys"&lt;
 *       ...
 *     &gt;/Row&lt;
 *     &gt;include keyboardLayout="@xml/other_rows"&lt;
 *     ...
 *   &gt;/Keyboard&lt;
 * </pre>
 * The xml file which is included in other file must have &gt;merge&lt; as root element, such as:
 * <pre>
 *   &gt;!-- xml/other_keys.xml --&lt;
 *   &gt;merge&lt;
 *     &gt;Key key_attributes* /&lt;
 *     ...
 *   &gt;/merge&lt;
 * </pre>
 * and
 * <pre>
 *   &gt;!-- xml/other_rows.xml --&lt;
 *   &gt;merge&lt;
 *     &gt;Row row_attributes*&lt;
 *       &gt;Key key_attributes* /&lt;
 *     &gt;/Row&lt;
 *     ...
 *   &gt;/merge&lt;
 * </pre>
 */
public class BaseKeyboardParser {
    private static final String TAG = "BaseKeyboardParser";
    private static final boolean DEBUG_TAG = false;
    private static final boolean DEBUG_PARSER = false;

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
        if (DEBUG_PARSER) debugEnterMethod("parseKeyboard", false);
        int event;
        while ((event = parser.next()) != XmlResourceParser.END_DOCUMENT) {
            if (event == XmlResourceParser.START_TAG) {
                final String tag = parser.getName();
                if (DEBUG_TAG) debugStartTag("parseKeyboard", tag, false);
                if (TAG_KEYBOARD.equals(tag)) {
                    parseKeyboardAttributes(parser);
                    parseKeyboardContent(parser, mKeyboard.getKeys());
                    break;
                } else {
                    throw new IllegalStartTag(parser, TAG_KEYBOARD);
                }
            }
        }
        if (DEBUG_PARSER) debugLeaveMethod("parseKeyboard", false);
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

    private void parseKeyboardContent(XmlResourceParser parser, final List<Key> keys)
            throws XmlPullParserException, IOException {
        if (DEBUG_PARSER) debugEnterMethod("parseKeyboardContent", keys == null);
        int event;
        while ((event = parser.next()) != XmlResourceParser.END_DOCUMENT) {
            if (event == XmlResourceParser.START_TAG) {
                final String tag = parser.getName();
                if (DEBUG_TAG) debugStartTag("parseKeyboardContent", tag, keys == null);
                if (TAG_ROW.equals(tag)) {
                    Row row = mKeyboard.createRowFromXml(mResources, parser);
                    if (keys != null && maybeStartRow(row)) {
                        parseRowContent(parser, row, keys);
                    } else {
                        // Skip entire <Row></Row>
                        parseRowContent(parser, row, null);
                    }
                } else if (TAG_INCLUDE.equals(tag)) {
                    parseIncludeKeyboardContent(parser, keys);
                } else {
                    throw new IllegalStartTag(parser, TAG_ROW);
                }
            } else if (event == XmlResourceParser.END_TAG) {
                final String tag = parser.getName();
                if (DEBUG_TAG) debugEndTag("parseKeyboardContent", tag, keys == null);
                if (TAG_KEYBOARD.equals(tag)) {
                    endKeyboard(mKeyboard.getVerticalGap());
                    break;
                } else if (TAG_MERGE.equals(tag)) {
                    break;
                } else {
                    throw new IllegalEndTag(parser, TAG_ROW);
                }
            }
        }
        if (DEBUG_PARSER) debugLeaveMethod("parseKeyboardContent", keys == null);
    }

    private void parseRowContent(XmlResourceParser parser, Row row, List<Key> keys)
            throws XmlPullParserException, IOException {
        if (DEBUG_PARSER) debugEnterMethod("parseRowContent", keys == null);
        int event;
        while ((event = parser.next()) != XmlResourceParser.END_DOCUMENT) {
            if (event == XmlResourceParser.START_TAG) {
                final String tag = parser.getName();
                if (DEBUG_TAG) debugStartTag("parseRowContent", tag, keys == null);
                if (TAG_KEY.equals(tag)) {
                    parseKey(parser, row, keys);
                } else if (TAG_SPACER.equals(tag)) {
                    parseSpacer(parser, keys);
                } else if (TAG_INCLUDE.equals(tag)) {
                    parseIncludeRowContent(parser, row, keys);
                } else {
                    throw new IllegalStartTag(parser, TAG_KEY);
                }
            } else if (event == XmlResourceParser.END_TAG) {
                final String tag = parser.getName();
                if (DEBUG_TAG) debugEndTag("parseRowContent", tag, keys == null);
                if (TAG_ROW.equals(tag)) {
                    if (keys != null)
                        endRow();
                    break;
                } else if (TAG_MERGE.equals(tag)) {
                    break;
                } else {
                    throw new IllegalEndTag(parser, TAG_KEY);
                }
            }
        }
        if (DEBUG_PARSER) debugLeaveMethod("parseRowContent", keys == null);
    }

    private void parseKey(XmlResourceParser parser, Row row, List<Key> keys)
            throws XmlPullParserException, IOException {
        if (DEBUG_PARSER) debugEnterMethod("parseKey", keys == null);
        if (keys == null) {
            checkEndTag(TAG_KEY, parser);
        } else {
            Key key = mKeyboard.createKeyFromXml(mResources, row, mCurrentX, mCurrentY, parser);
            checkEndTag(TAG_KEY, parser);
            keys.add(key);
            if (key.codes[0] == BaseKeyboard.KEYCODE_SHIFT)
                mKeyboard.getShiftKeys().add(key);
            endKey(key);
        }
    }

    private void parseSpacer(XmlResourceParser parser, List<Key> keys)
            throws XmlPullParserException, IOException {
        if (DEBUG_PARSER) debugEnterMethod("parseSpacer", keys == null);
        if (keys == null) {
            checkEndTag(TAG_SPACER, parser);
        } else {
            final TypedArray a = mResources.obtainAttributes(Xml.asAttributeSet(parser),
                    R.styleable.BaseKeyboard);
            int gap = getDimensionOrFraction(a, R.styleable.BaseKeyboard_horizontalGap,
                    mKeyboard.getKeyboardWidth(), 0);
            a.recycle();
            checkEndTag(TAG_SPACER, parser);
            setSpacer(gap);
        }
    }

    private void parseIncludeKeyboardContent(XmlResourceParser parser, List<Key> keys)
            throws XmlPullParserException, IOException {
        parseIncludeInternal(parser, null, keys);
    }

    private void parseIncludeRowContent(XmlResourceParser parser, Row row, List<Key> keys)
            throws XmlPullParserException, IOException {
        parseIncludeInternal(parser, row, keys);
    }

    private void parseIncludeInternal(XmlResourceParser parser, Row row, List<Key> keys)
            throws XmlPullParserException, IOException {
        if (DEBUG_PARSER) debugEnterMethod("parseInclude", keys == null);
        if (keys == null) {
            checkEndTag(TAG_INCLUDE, parser);
        } else {
            final TypedArray a = mResources.obtainAttributes(Xml.asAttributeSet(parser),
                    R.styleable.BaseKeyboard_Include);
            final int keyboardLayout = a.getResourceId(
                    R.styleable.BaseKeyboard_Include_keyboardLayout, 0);
            a.recycle();

            checkEndTag(TAG_INCLUDE, parser);
            if (keyboardLayout == 0)
                throw new ParseException("No keyboardLayout attribute in <include/>", parser);
            parseMerge(mResources.getLayout(keyboardLayout), row, keys);
        }
        if (DEBUG_PARSER) debugLeaveMethod("parseInclude", keys == null);
    }

    private void parseMerge(XmlResourceParser parser, Row row, List<Key> keys)
            throws XmlPullParserException, IOException {
        if (DEBUG_PARSER) debugEnterMethod("parseMerge", keys == null);
        int event;
        while ((event = parser.next()) != XmlResourceParser.END_DOCUMENT) {
            if (event == XmlResourceParser.START_TAG) {
                String tag = parser.getName();
                if (DEBUG_TAG) debugStartTag("parseMerge", tag, keys == null);
                if (TAG_MERGE.equals(tag)) {
                    if (row == null) {
                        parseKeyboardContent(parser, keys);
                    } else {
                        parseRowContent(parser, row, keys);
                    }
                    break;
                } else {
                    throw new ParseException(
                            "Included keyboard layout must have <merge> root element", parser);
                }
            }
        }
        if (DEBUG_PARSER) debugLeaveMethod("parseMerge", keys == null);
    }

    private static void checkEndTag(String tag, XmlResourceParser parser)
            throws XmlPullParserException, IOException {
        if (parser.next() == XmlResourceParser.END_TAG && tag.equals(parser.getName()))
            return;
        throw new NonEmptyTag(tag, parser);
    }

    // return true if the row is valid for this keyboard mode
    private boolean maybeStartRow(Row row) {
        if (DEBUG_TAG)
            Log.d(TAG, String.format("startRow: mode=0x%08x keyboardMode=0x%08x",
                    row.mode, mKeyboard.getKeyboardMode()));
        if (row.mode == 0 || row.mode == mKeyboard.getKeyboardMode()) {
            mCurrentX = 0;
            mCurrentRow = row;
            return true;
        } else {
            return false;
        }
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

    @SuppressWarnings("serial")
    private static class ParseException extends InflateException {
        public ParseException(String msg, XmlResourceParser parser) {
            super(msg + " at line " + parser.getLineNumber());
        }
    }

    @SuppressWarnings("serial")
    private static class IllegalStartTag extends ParseException {
        public IllegalStartTag(XmlResourceParser parser, String parent) {
            super("Illegal start tag " + parser.getName() + " in " + parent, parser);
        }
    }

    @SuppressWarnings("serial")
    private static class IllegalEndTag extends ParseException {
        public IllegalEndTag(XmlResourceParser parser, String parent) {
            super("Illegal end tag " + parser.getName() + " in " + parent, parser);
        }
    }

    @SuppressWarnings("serial")
    private static class NonEmptyTag extends ParseException {
        public NonEmptyTag(String tag, XmlResourceParser parser) {
            super(tag + " must be empty tag", parser);
        }
    }

    private static void debugEnterMethod(String title, boolean skip) {
        Log.d(TAG, title + ": enter" + (skip ? " skip" : ""));
    }

    private static void debugLeaveMethod(String title, boolean skip) {
        Log.d(TAG, title + ": leave" + (skip ? " skip" : ""));
    }

    private static void debugStartTag(String title, String tag, boolean skip) {
        Log.d(TAG, title + ": <" + tag + ">" + (skip ? " skip" : ""));
    }

    private static void debugEndTag(String title, String tag, boolean skip) {
        Log.d(TAG, title + ": </" + tag + ">" + (skip ? " skip" : ""));
    }
 }
