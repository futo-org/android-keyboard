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

package com.android.inputmethod.keyboard.internal;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.util.Log;
import android.util.TypedValue;
import android.util.Xml;
import android.view.InflateException;

import com.android.inputmethod.compat.EditorInfoCompatUtils;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.latin.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Parser for BaseKeyboard.
 *
 * This class parses Keyboard XML file and fill out keys in Keyboard.
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
 * The XML file which is included in other file must have &gt;merge&lt; as root element, such as:
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
 * You can also use switch-case-default tags to select Rows and Keys.
 * <pre>
 *   &gt;switch&lt;
 *     &gt;case case_attribute*&lt;
 *       &gt;!-- Any valid tags at switch position --&lt;
 *     &gt;/case&lt;
 *     ...
 *     &gt;default&lt;
 *       &gt;!-- Any valid tags at switch position --&lt;
 *     &gt;/default&lt;
 *   &gt;/switch&lt;
 * </pre>
 * You can declare Key style and specify styles within Key tags.
 * <pre>
 *     &gt;switch&lt;
 *       &gt;case mode="email"&lt;
 *         &gt;key-style styleName="f1-key" parentStyle="modifier-key"
 *           keyLabel=".com"
 *         /&lt;
 *       &gt;/case&lt;
 *       &gt;case mode="url"&lt;
 *         &gt;key-style styleName="f1-key" parentStyle="modifier-key"
 *           keyLabel="http://"
 *         /&lt;
 *       &gt;/case&lt;
 *     &gt;/switch&lt;
 *     ...
 *     &gt;Key keyStyle="shift-key" ... /&lt;
 * </pre>
 */

public class KeyboardParser {
    private static final String TAG = KeyboardParser.class.getSimpleName();
    private static final boolean DEBUG = false;

    // Keyboard XML Tags
    private static final String TAG_KEYBOARD = "Keyboard";
    private static final String TAG_ROW = "Row";
    private static final String TAG_KEY = "Key";
    private static final String TAG_SPACER = "Spacer";
    private static final String TAG_INCLUDE = "include";
    private static final String TAG_MERGE = "merge";
    private static final String TAG_SWITCH = "switch";
    private static final String TAG_CASE = "case";
    private static final String TAG_DEFAULT = "default";
    public static final String TAG_KEY_STYLE = "key-style";

    private final Keyboard mKeyboard;
    private final Context mContext;
    private final Resources mResources;

    private int mKeyboardTopPadding;
    private int mKeyboardBottomPadding;
    private int mHorizontalEdgesPadding;
    private int mCurrentX = 0;
    private int mCurrentY = 0;
    private int mMaxRowWidth = 0;
    private int mTotalHeight = 0;
    private Row mCurrentRow = null;
    private final KeyStyles mKeyStyles = new KeyStyles();

    public KeyboardParser(Keyboard keyboard, Context context) {
        mKeyboard = keyboard;
        mContext = context;
        final Resources res = context.getResources();
        mResources = res;
        mHorizontalEdgesPadding = (int)res.getDimension(R.dimen.keyboard_horizontal_edges_padding);
    }

    public int getMaxRowWidth() {
        return mMaxRowWidth;
    }

    public int getTotalHeight() {
        return mTotalHeight;
    }

    public void parseKeyboard(int resId) throws XmlPullParserException, IOException {
        if (DEBUG) Log.d(TAG, String.format("<%s> %s", TAG_KEYBOARD, mKeyboard.mId));
        final XmlResourceParser parser = mResources.getXml(resId);
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                final String tag = parser.getName();
                if (TAG_KEYBOARD.equals(tag)) {
                    parseKeyboardAttributes(parser);
                    startKeyboard();
                    parseKeyboardContent(parser, mKeyboard.getKeys());
                    break;
                } else {
                    throw new IllegalStartTag(parser, TAG_KEYBOARD);
                }
            }
        }
    }

    public static String parseKeyboardLocale(
            Context context, int resId) throws XmlPullParserException, IOException {
        final Resources res = context.getResources();
        final XmlResourceParser parser = res.getXml(resId);
        if (parser == null) return "";
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                final String tag = parser.getName();
                if (TAG_KEYBOARD.equals(tag)) {
                    final TypedArray keyboardAttr = res.obtainAttributes(Xml.asAttributeSet(parser),
                            R.styleable.Keyboard);
                    return keyboardAttr.getString(R.styleable.Keyboard_keyboardLocale);
                } else {
                    throw new IllegalStartTag(parser, TAG_KEYBOARD);
                }
            }
        }
        return "";
    }

    private void parseKeyboardAttributes(XmlResourceParser parser) {
        final Keyboard keyboard = mKeyboard;
        final TypedArray keyboardAttr = mContext.obtainStyledAttributes(
                Xml.asAttributeSet(parser), R.styleable.Keyboard, R.attr.keyboardStyle,
                R.style.Keyboard);
        final TypedArray keyAttr = mResources.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.Keyboard_Key);
        try {
            final int displayHeight = keyboard.getDisplayHeight();
            final int keyboardHeight = (int)keyboardAttr.getDimension(
                    R.styleable.Keyboard_keyboardHeight, displayHeight / 2);
            final int maxKeyboardHeight = getDimensionOrFraction(keyboardAttr,
                    R.styleable.Keyboard_maxKeyboardHeight, displayHeight, displayHeight / 2);
            int minKeyboardHeight = getDimensionOrFraction(keyboardAttr,
                    R.styleable.Keyboard_minKeyboardHeight, displayHeight, displayHeight / 2);
            if (minKeyboardHeight < 0) {
                // Specified fraction was negative, so it should be calculated against display
                // width.
                final int displayWidth = keyboard.getDisplayWidth();
                minKeyboardHeight = -getDimensionOrFraction(keyboardAttr,
                        R.styleable.Keyboard_minKeyboardHeight, displayWidth, displayWidth / 2);
            }
            // Keyboard height will not exceed maxKeyboardHeight and will not be less than
            // minKeyboardHeight.
            final int height = Math.max(
                    Math.min(keyboardHeight, maxKeyboardHeight), minKeyboardHeight);
            final int width = keyboard.getDisplayWidth();

            keyboard.setKeyboardHeight(height);
            keyboard.setKeyWidth(getDimensionOrFraction(keyboardAttr,
                    R.styleable.Keyboard_keyWidth, width, width / 10));
            keyboard.setRowHeight(getDimensionOrFraction(keyboardAttr,
                    R.styleable.Keyboard_rowHeight, height, 50));
            keyboard.setHorizontalGap(getDimensionOrFraction(keyboardAttr,
                    R.styleable.Keyboard_horizontalGap, width, 0));
            keyboard.setVerticalGap(getDimensionOrFraction(keyboardAttr,
                    R.styleable.Keyboard_verticalGap, height, 0));
            keyboard.setPopupKeyboardResId(keyboardAttr.getResourceId(
                    R.styleable.Keyboard_popupKeyboardTemplate, 0));

            keyboard.setMaxPopupKeyboardColumn(keyAttr.getInt(
                    R.styleable.Keyboard_Key_maxPopupKeyboardColumn, 5));

            mKeyboard.mIconsSet.loadIcons(keyboardAttr);
            mKeyboardTopPadding = keyboardAttr.getDimensionPixelSize(
                    R.styleable.Keyboard_keyboardTopPadding, 0);
            mKeyboardBottomPadding = keyboardAttr.getDimensionPixelSize(
                    R.styleable.Keyboard_keyboardBottomPadding, 0);
        } finally {
            keyAttr.recycle();
            keyboardAttr.recycle();
        }
    }

    private void parseKeyboardContent(XmlResourceParser parser, List<Key> keys)
            throws XmlPullParserException, IOException {
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                final String tag = parser.getName();
                if (TAG_ROW.equals(tag)) {
                    Row row = new Row(mResources, mKeyboard, parser);
                    if (DEBUG) Log.d(TAG, String.format("<%s>", TAG_ROW));
                    if (keys != null)
                        startRow(row);
                    parseRowContent(parser, row, keys);
                } else if (TAG_INCLUDE.equals(tag)) {
                    parseIncludeKeyboardContent(parser, keys);
                } else if (TAG_SWITCH.equals(tag)) {
                    parseSwitchKeyboardContent(parser, keys);
                } else if (TAG_KEY_STYLE.equals(tag)) {
                    parseKeyStyle(parser, keys);
                } else {
                    throw new IllegalStartTag(parser, TAG_ROW);
                }
            } else if (event == XmlPullParser.END_TAG) {
                final String tag = parser.getName();
                if (TAG_KEYBOARD.equals(tag)) {
                    endKeyboard(mKeyboard.getVerticalGap());
                    break;
                } else if (TAG_CASE.equals(tag) || TAG_DEFAULT.equals(tag)
                        || TAG_MERGE.equals(tag)) {
                    if (DEBUG) Log.d(TAG, String.format("</%s>", tag));
                    break;
                } else if (TAG_KEY_STYLE.equals(tag)) {
                    continue;
                } else {
                    throw new IllegalEndTag(parser, TAG_ROW);
                }
            }
        }
    }

    private void parseRowContent(XmlResourceParser parser, Row row, List<Key> keys)
            throws XmlPullParserException, IOException {
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                final String tag = parser.getName();
                if (TAG_KEY.equals(tag)) {
                    parseKey(parser, row, keys);
                } else if (TAG_SPACER.equals(tag)) {
                    parseSpacer(parser, row, keys);
                } else if (TAG_INCLUDE.equals(tag)) {
                    parseIncludeRowContent(parser, row, keys);
                } else if (TAG_SWITCH.equals(tag)) {
                    parseSwitchRowContent(parser, row, keys);
                } else if (TAG_KEY_STYLE.equals(tag)) {
                    parseKeyStyle(parser, keys);
                } else {
                    throw new IllegalStartTag(parser, TAG_KEY);
                }
            } else if (event == XmlPullParser.END_TAG) {
                final String tag = parser.getName();
                if (TAG_ROW.equals(tag)) {
                    if (DEBUG) Log.d(TAG, String.format("</%s>", TAG_ROW));
                    if (keys != null)
                        endRow();
                    break;
                } else if (TAG_CASE.equals(tag) || TAG_DEFAULT.equals(tag)
                        || TAG_MERGE.equals(tag)) {
                    if (DEBUG) Log.d(TAG, String.format("</%s>", tag));
                    break;
                } else if (TAG_KEY_STYLE.equals(tag)) {
                    continue;
                } else {
                    throw new IllegalEndTag(parser, TAG_KEY);
                }
            }
        }
    }

    private void parseKey(XmlResourceParser parser, Row row, List<Key> keys)
            throws XmlPullParserException, IOException {
        if (keys == null) {
            checkEndTag(TAG_KEY, parser);
        } else {
            Key key = new Key(mResources, row, mCurrentX, mCurrentY, parser, mKeyStyles);
            if (DEBUG) Log.d(TAG, String.format("<%s%s keyLabel=%s code=%d popupCharacters=%s />",
                    TAG_KEY, (key.mEnabled ? "" : " disabled"), key.mLabel, key.mCode,
                    Arrays.toString(key.mPopupCharacters)));
            checkEndTag(TAG_KEY, parser);
            keys.add(key);
            if (key.mCode == Keyboard.CODE_SHIFT)
                mKeyboard.getShiftKeys().add(key);
            endKey(key);
        }
    }

    private void parseSpacer(XmlResourceParser parser, Row row, List<Key> keys)
            throws XmlPullParserException, IOException {
        if (keys == null) {
            checkEndTag(TAG_SPACER, parser);
        } else {
            if (DEBUG) Log.d(TAG, String.format("<%s />", TAG_SPACER));
            final TypedArray keyboardAttr = mResources.obtainAttributes(Xml.asAttributeSet(parser),
                    R.styleable.Keyboard);
            if (keyboardAttr.hasValue(R.styleable.Keyboard_horizontalGap))
                throw new IllegalAttribute(parser, "horizontalGap");
            final int defaultWidth = (row != null) ? row.mDefaultWidth : 0;
            final int keyWidth = getDimensionOrFraction(keyboardAttr, R.styleable.Keyboard_keyWidth,
                    mKeyboard.getDisplayWidth(), defaultWidth);
            keyboardAttr.recycle();

            final TypedArray keyAttr = mResources.obtainAttributes(Xml.asAttributeSet(parser),
                    R.styleable.Keyboard_Key);
            int keyXPos = KeyboardParser.getDimensionOrFraction(keyAttr,
                    R.styleable.Keyboard_Key_keyXPos, mKeyboard.getDisplayWidth(), mCurrentX);
            if (keyXPos < 0) {
                // If keyXPos is negative, the actual x-coordinate will be display_width + keyXPos.
                keyXPos += mKeyboard.getDisplayWidth();
            }

            checkEndTag(TAG_SPACER, parser);
            setSpacer(keyXPos, keyWidth);
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
        if (keys == null) {
            checkEndTag(TAG_INCLUDE, parser);
        } else {
            final TypedArray a = mResources.obtainAttributes(Xml.asAttributeSet(parser),
                    R.styleable.Keyboard_Include);
            final int keyboardLayout = a.getResourceId(
                    R.styleable.Keyboard_Include_keyboardLayout, 0);
            a.recycle();

            checkEndTag(TAG_INCLUDE, parser);
            if (keyboardLayout == 0)
                throw new ParseException("No keyboardLayout attribute in <include/>", parser);
            if (DEBUG) Log.d(TAG, String.format("<%s keyboardLayout=%s />",
                    TAG_INCLUDE, mResources.getResourceEntryName(keyboardLayout)));
            parseMerge(mResources.getLayout(keyboardLayout), row, keys);
        }
    }

    private void parseMerge(XmlResourceParser parser, Row row, List<Key> keys)
            throws XmlPullParserException, IOException {
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                final String tag = parser.getName();
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
    }

    private void parseSwitchKeyboardContent(XmlResourceParser parser, List<Key> keys)
            throws XmlPullParserException, IOException {
        parseSwitchInternal(parser, null, keys);
    }

    private void parseSwitchRowContent(XmlResourceParser parser, Row row, List<Key> keys)
            throws XmlPullParserException, IOException {
        parseSwitchInternal(parser, row, keys);
    }

    private void parseSwitchInternal(XmlResourceParser parser, Row row, List<Key> keys)
            throws XmlPullParserException, IOException {
        if (DEBUG) Log.d(TAG, String.format("<%s> %s", TAG_SWITCH, mKeyboard.mId));
        boolean selected = false;
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                final String tag = parser.getName();
                if (TAG_CASE.equals(tag)) {
                    selected |= parseCase(parser, row, selected ? null : keys);
                } else if (TAG_DEFAULT.equals(tag)) {
                    selected |= parseDefault(parser, row, selected ? null : keys);
                } else {
                    throw new IllegalStartTag(parser, TAG_KEY);
                }
            } else if (event == XmlPullParser.END_TAG) {
                final String tag = parser.getName();
                if (TAG_SWITCH.equals(tag)) {
                    if (DEBUG) Log.d(TAG, String.format("</%s>", TAG_SWITCH));
                    break;
                } else {
                    throw new IllegalEndTag(parser, TAG_KEY);
                }
            }
        }
    }

    private boolean parseCase(XmlResourceParser parser, Row row, List<Key> keys)
            throws XmlPullParserException, IOException {
        final boolean selected = parseCaseCondition(parser);
        if (row == null) {
            // Processing Rows.
            parseKeyboardContent(parser, selected ? keys : null);
        } else {
            // Processing Keys.
            parseRowContent(parser, row, selected ? keys : null);
        }
        return selected;
    }

    private boolean parseCaseCondition(XmlResourceParser parser) {
        final KeyboardId id = mKeyboard.mId;
        if (id == null)
            return true;

        final TypedArray a = mResources.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.Keyboard_Case);
        final TypedArray viewAttr = mResources.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.KeyboardView);
        try {
            final boolean modeMatched = matchTypedValue(a,
                    R.styleable.Keyboard_Case_mode, id.mMode, KeyboardId.modeName(id.mMode));
            final boolean navigateActionMatched = matchBoolean(a,
                    R.styleable.Keyboard_Case_navigateAction, id.mNavigateAction);
            final boolean passwordInputMatched = matchBoolean(a,
                    R.styleable.Keyboard_Case_passwordInput, id.mPasswordInput);
            final boolean hasSettingsKeyMatched = matchBoolean(a,
                    R.styleable.Keyboard_Case_hasSettingsKey, id.mHasSettingsKey);
            final boolean f2KeyModeMatched = matchInteger(a,
                    R.styleable.Keyboard_Case_f2KeyMode, id.mF2KeyMode);
            final boolean clobberSettingsKeyMatched = matchBoolean(a,
                    R.styleable.Keyboard_Case_clobberSettingsKey, id.mClobberSettingsKey);
            final boolean voiceEnabledMatched = matchBoolean(a,
                    R.styleable.Keyboard_Case_voiceKeyEnabled, id.mVoiceKeyEnabled);
            final boolean voiceKeyMatched = matchBoolean(a,
                    R.styleable.Keyboard_Case_hasVoiceKey, id.mHasVoiceKey);
            // As noted at {@link KeyboardId} class, we are interested only in enum value masked by
            // {@link android.view.inputmethod.EditorInfo#IME_MASK_ACTION} and
            // {@link android.view.inputmethod.EditorInfo#IME_FLAG_NO_ENTER_ACTION}. So matching
            // this attribute with id.mImeOptions as integer value is enough for our purpose.
            final boolean imeActionMatched = matchInteger(a,
                    R.styleable.Keyboard_Case_imeAction, id.mImeAction);
            final boolean localeCodeMatched = matchString(a,
                    R.styleable.Keyboard_Case_localeCode, id.mLocale.toString());
            final boolean languageCodeMatched = matchString(a,
                    R.styleable.Keyboard_Case_languageCode, id.mLocale.getLanguage());
            final boolean countryCodeMatched = matchString(a,
                    R.styleable.Keyboard_Case_countryCode, id.mLocale.getCountry());
            final boolean selected = modeMatched && navigateActionMatched && passwordInputMatched
                    && hasSettingsKeyMatched && f2KeyModeMatched && clobberSettingsKeyMatched
                    && voiceEnabledMatched && voiceKeyMatched && imeActionMatched &&
                    localeCodeMatched && languageCodeMatched && countryCodeMatched;

            if (DEBUG) Log.d(TAG, String.format("<%s%s%s%s%s%s%s%s%s%s%s%s%s> %s", TAG_CASE,
                    textAttr(a.getString(R.styleable.Keyboard_Case_mode), "mode"),
                    booleanAttr(a, R.styleable.Keyboard_Case_navigateAction, "navigateAction"),
                    booleanAttr(a, R.styleable.Keyboard_Case_passwordInput, "passwordInput"),
                    booleanAttr(a, R.styleable.Keyboard_Case_hasSettingsKey, "hasSettingsKey"),
                    textAttr(KeyboardId.f2KeyModeName(
                            a.getInt(R.styleable.Keyboard_Case_f2KeyMode, -1)), "f2KeyMode"),
                    booleanAttr(a, R.styleable.Keyboard_Case_clobberSettingsKey,
                            "clobberSettingsKey"),
                    booleanAttr(a, R.styleable.Keyboard_Case_voiceKeyEnabled, "voiceKeyEnabled"),
                    booleanAttr(a, R.styleable.Keyboard_Case_hasVoiceKey, "hasVoiceKey"),
                    textAttr(EditorInfoCompatUtils.imeOptionsName(
                            a.getInt(R.styleable.Keyboard_Case_imeAction, -1)), "imeAction"),
                    textAttr(a.getString(R.styleable.Keyboard_Case_localeCode), "localeCode"),
                    textAttr(a.getString(R.styleable.Keyboard_Case_languageCode), "languageCode"),
                    textAttr(a.getString(R.styleable.Keyboard_Case_countryCode), "countryCode"),
                    Boolean.toString(selected)));

            return selected;
        } finally {
            a.recycle();
            viewAttr.recycle();
        }
    }

    private static boolean matchInteger(TypedArray a, int index, int value) {
        // If <case> does not have "index" attribute, that means this <case> is wild-card for the
        // attribute.
        return !a.hasValue(index) || a.getInt(index, 0) == value;
    }

    private static boolean matchBoolean(TypedArray a, int index, boolean value) {
        // If <case> does not have "index" attribute, that means this <case> is wild-card for the
        // attribute.
        return !a.hasValue(index) || a.getBoolean(index, false) == value;
    }

    private static boolean matchString(TypedArray a, int index, String value) {
        // If <case> does not have "index" attribute, that means this <case> is wild-card for the
        // attribute.
        return !a.hasValue(index) || stringArrayContains(a.getString(index).split("\\|"), value);
    }

    private static boolean matchTypedValue(TypedArray a, int index, int intValue, String strValue) {
        // If <case> does not have "index" attribute, that means this <case> is wild-card for the
        // attribute.
        final TypedValue v = a.peekValue(index);
        if (v == null)
            return true;

        if (isIntegerValue(v)) {
            return intValue == a.getInt(index, 0);
        } else if (isStringValue(v)) {
            return stringArrayContains(a.getString(index).split("\\|"), strValue);
        }
        return false;
    }

    private static boolean stringArrayContains(String[] array, String value) {
        for (final String elem : array) {
            if (elem.equals(value))
                return true;
        }
        return false;
    }

    private boolean parseDefault(XmlResourceParser parser, Row row, List<Key> keys)
            throws XmlPullParserException, IOException {
        if (DEBUG) Log.d(TAG, String.format("<%s>", TAG_DEFAULT));
        if (row == null) {
            parseKeyboardContent(parser, keys);
        } else {
            parseRowContent(parser, row, keys);
        }
        return true;
    }

    private void parseKeyStyle(XmlResourceParser parser, List<Key> keys) {
        TypedArray keyStyleAttr = mResources.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.Keyboard_KeyStyle);
        TypedArray keyAttrs = mResources.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.Keyboard_Key);
        try {
            if (!keyStyleAttr.hasValue(R.styleable.Keyboard_KeyStyle_styleName))
                throw new ParseException("<" + TAG_KEY_STYLE
                        + "/> needs styleName attribute", parser);
            if (keys != null)
                mKeyStyles.parseKeyStyleAttributes(keyStyleAttr, keyAttrs, parser);
        } finally {
            keyStyleAttr.recycle();
            keyAttrs.recycle();
        }
    }

    private static void checkEndTag(String tag, XmlResourceParser parser)
            throws XmlPullParserException, IOException {
        if (parser.next() == XmlPullParser.END_TAG && tag.equals(parser.getName()))
            return;
        throw new NonEmptyTag(tag, parser);
    }

    private void startKeyboard() {
        mCurrentY += mKeyboardTopPadding;
    }

    private void startRow(Row row) {
        mCurrentX = 0;
        setSpacer(mCurrentX, mHorizontalEdgesPadding);
        mCurrentRow = row;
    }

    private void endRow() {
        if (mCurrentRow == null)
            throw new InflateException("orphant end row tag");
        setSpacer(mCurrentX, mHorizontalEdgesPadding);
        if (mCurrentX > mMaxRowWidth)
            mMaxRowWidth = mCurrentX;
        mCurrentY += mCurrentRow.mDefaultHeight;
        mCurrentRow = null;
    }

    private void endKey(Key key) {
        mCurrentX = key.mX - key.mGap / 2 + key.mWidth + key.mGap;
    }

    private void endKeyboard(int defaultVerticalGap) {
        mCurrentY += mKeyboardBottomPadding;
        mTotalHeight = mCurrentY - defaultVerticalGap;
    }

    private void setSpacer(int keyXPos, int width) {
        mCurrentX = keyXPos + width;
    }

    public static int getDimensionOrFraction(TypedArray a, int index, int base, int defValue) {
        final TypedValue value = a.peekValue(index);
        if (value == null)
            return defValue;
        if (isFractionValue(value)) {
            // Round it to avoid values like 47.9999 from getting truncated
            return Math.round(a.getFraction(index, base, base, defValue));
        } else if (isDimensionValue(value)) {
            return a.getDimensionPixelOffset(index, defValue);
        } else if (isIntegerValue(value)) {
            // For enum value.
            return a.getInt(index, defValue);
        }
        return defValue;
    }

    private static boolean isFractionValue(TypedValue v) {
        return v.type == TypedValue.TYPE_FRACTION;
    }

    private static boolean isDimensionValue(TypedValue v) {
        return v.type == TypedValue.TYPE_DIMENSION;
    }

    private static boolean isIntegerValue(TypedValue v) {
        return v.type >= TypedValue.TYPE_FIRST_INT && v.type <= TypedValue.TYPE_LAST_INT;
    }

    private static boolean isStringValue(TypedValue v) {
        return v.type == TypedValue.TYPE_STRING;
    }

    @SuppressWarnings("serial")
    public static class ParseException extends InflateException {
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
    private static class IllegalAttribute extends ParseException {
        public IllegalAttribute(XmlResourceParser parser, String attribute) {
            super("Tag " + parser.getName() + " has illegal attribute " + attribute, parser);
        }
    }

    @SuppressWarnings("serial")
    private static class NonEmptyTag extends ParseException {
        public NonEmptyTag(String tag, XmlResourceParser parser) {
            super(tag + " must be empty tag", parser);
        }
    }

    private static String textAttr(String value, String name) {
        return value != null ? String.format(" %s=%s", name, value) : "";
    }

    private static String booleanAttr(TypedArray a, int index, String name) {
        return a.hasValue(index) ? String.format(" %s=%s", name, a.getBoolean(index, false)) : "";
    }
}
