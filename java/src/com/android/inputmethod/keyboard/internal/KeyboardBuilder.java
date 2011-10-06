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
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.util.Xml;
import android.view.InflateException;

import com.android.inputmethod.compat.EditorInfoCompatUtils;
import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Arrays;

/**
 * Keyboard Building helper.
 *
 * This class parses Keyboard XML file and eventually build a Keyboard.
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

public class KeyboardBuilder<KP extends KeyboardParams> {
    private static final String TAG = KeyboardBuilder.class.getSimpleName();
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

    private static final int DEFAULT_KEYBOARD_COLUMNS = 10;
    private static final int DEFAULT_KEYBOARD_ROWS = 4;

    protected final KP mParams;
    protected final Context mContext;
    protected final Resources mResources;
    private final DisplayMetrics mDisplayMetrics;

    private int mCurrentY = 0;
    private Row mCurrentRow = null;
    private boolean mLeftEdge;
    private boolean mTopEdge;
    private Key mRightEdgeKey = null;
    private final KeyStyles mKeyStyles = new KeyStyles();

    /**
     * Container for keys in the keyboard. All keys in a row are at the same Y-coordinate.
     * Some of the key size defaults can be overridden per row from what the {@link Keyboard}
     * defines.
     */
    public static class Row {
        // keyWidth enum constants
        private static final int KEYWIDTH_NOT_ENUM = 0;
        private static final int KEYWIDTH_FILL_RIGHT = -1;
        private static final int KEYWIDTH_FILL_BOTH = -2;

        private final KeyboardParams mParams;
        /** Default width of a key in this row. */
        public final float mDefaultKeyWidth;
        /** Default height of a key in this row. */
        public final int mRowHeight;

        private final int mCurrentY;
        // Will be updated by {@link Key}'s constructor.
        private float mCurrentX;

        public Row(Resources res, KeyboardParams params, XmlPullParser parser, int y) {
            mParams = params;
            TypedArray keyboardAttr = res.obtainAttributes(Xml.asAttributeSet(parser),
                    R.styleable.Keyboard);
            mRowHeight = (int)KeyboardBuilder.getDimensionOrFraction(keyboardAttr,
                    R.styleable.Keyboard_rowHeight, params.mBaseHeight, params.mDefaultRowHeight);
            keyboardAttr.recycle();
            TypedArray keyAttr = res.obtainAttributes(Xml.asAttributeSet(parser),
                    R.styleable.Keyboard_Key);
            mDefaultKeyWidth = KeyboardBuilder.getDimensionOrFraction(keyboardAttr,
                    R.styleable.Keyboard_Key_keyWidth, params.mBaseWidth, params.mDefaultKeyWidth);
            keyAttr.recycle();

            mCurrentY = y;
            mCurrentX = 0.0f;
        }

        public void setXPos(float keyXPos) {
            mCurrentX = keyXPos;
        }

        public void advanceXPos(float width) {
            mCurrentX += width;
        }

        public int getKeyY() {
            return mCurrentY;
        }

        public float getKeyX(TypedArray keyAttr) {
            final int widthType = KeyboardBuilder.getEnumValue(keyAttr,
                    R.styleable.Keyboard_Key_keyWidth, KEYWIDTH_NOT_ENUM);
            if (widthType == KEYWIDTH_FILL_BOTH) {
                // If keyWidth is fillBoth, the key width should start right after the nearest key
                // on the left hand side.
                return mCurrentX;
            }

            final int keyboardRightEdge = mParams.mOccupiedWidth - mParams.mHorizontalEdgesPadding;
            if (keyAttr.hasValue(R.styleable.Keyboard_Key_keyXPos)) {
                final float keyXPos = KeyboardBuilder.getDimensionOrFraction(keyAttr,
                        R.styleable.Keyboard_Key_keyXPos, mParams.mBaseWidth, 0);
                if (keyXPos < 0) {
                    // If keyXPos is negative, the actual x-coordinate will be
                    // keyboardWidth + keyXPos.
                    // keyXPos shouldn't be less than mCurrentX because drawable area for this key
                    // starts at mCurrentX. Or, this key will overlaps the adjacent key on its left
                    // hand side.
                    return Math.max(keyXPos + keyboardRightEdge, mCurrentX);
                } else {
                    return keyXPos + mParams.mHorizontalEdgesPadding;
                }
            }
            return mCurrentX;
        }

        public float getKeyWidth(TypedArray keyAttr, float keyXPos) {
            final int widthType = KeyboardBuilder.getEnumValue(keyAttr,
                    R.styleable.Keyboard_Key_keyWidth, KEYWIDTH_NOT_ENUM);
            switch (widthType) {
            case KEYWIDTH_FILL_RIGHT:
            case KEYWIDTH_FILL_BOTH:
                final int keyboardRightEdge =
                        mParams.mOccupiedWidth - mParams.mHorizontalEdgesPadding;
                // If keyWidth is fillRight, the actual key width will be determined to fill out the
                // area up to the right edge of the keyboard.
                // If keyWidth is fillBoth, the actual key width will be determined to fill out the
                // area between the nearest key on the left hand side and the right edge of the
                // keyboard.
                return keyboardRightEdge - keyXPos;
            default: // KEYWIDTH_NOT_ENUM
                return KeyboardBuilder.getDimensionOrFraction(keyAttr,
                        R.styleable.Keyboard_Key_keyWidth, mParams.mBaseWidth, mDefaultKeyWidth);
            }
        }
    }

    public KeyboardBuilder(Context context, KP params) {
        mContext = context;
        final Resources res = context.getResources();
        mResources = res;
        mDisplayMetrics = res.getDisplayMetrics();

        mParams = params;

        setTouchPositionCorrectionData(context, params);

        params.GRID_WIDTH = res.getInteger(R.integer.config_keyboard_grid_width);
        params.GRID_HEIGHT = res.getInteger(R.integer.config_keyboard_grid_height);
    }

    private static void setTouchPositionCorrectionData(Context context, KeyboardParams params) {
        final TypedArray a = context.obtainStyledAttributes(
                null, R.styleable.Keyboard, R.attr.keyboardStyle, 0);
        params.mThemeId = a.getInt(R.styleable.Keyboard_themeId, 0);
        final int resourceId = a.getResourceId(R.styleable.Keyboard_touchPositionCorrectionData, 0);
        a.recycle();
        if (resourceId == 0) {
            if (LatinImeLogger.sDBG)
                throw new RuntimeException("touchPositionCorrectionData is not defined");
            return;
        }

        final String[] data = context.getResources().getStringArray(resourceId);
        params.mTouchPositionCorrection.load(data);
    }

    public KeyboardBuilder<KP> load(KeyboardId id) {
        mParams.mId = id;
        try {
            parseKeyboard(id.getXmlId());
        } catch (XmlPullParserException e) {
            Log.w(TAG, "keyboard XML parse error: " + e);
            throw new IllegalArgumentException(e);
        } catch (IOException e) {
            Log.w(TAG, "keyboard XML parse error: " + e);
            throw new RuntimeException(e);
        }
        return this;
    }

    public void setTouchPositionCorrectionEnabled(boolean enabled) {
        mParams.mTouchPositionCorrection.setEnabled(enabled);
    }

    public Keyboard build() {
        return new Keyboard(mParams);
    }

    private void parseKeyboard(int resId) throws XmlPullParserException, IOException {
        if (DEBUG) Log.d(TAG, String.format("<%s> %s", TAG_KEYBOARD, mParams.mId));
        final XmlPullParser parser = mResources.getXml(resId);
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                final String tag = parser.getName();
                if (TAG_KEYBOARD.equals(tag)) {
                    parseKeyboardAttributes(parser);
                    startKeyboard();
                    parseKeyboardContent(parser, false);
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
        final XmlPullParser parser = res.getXml(resId);
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

    private void parseKeyboardAttributes(XmlPullParser parser) {
        final int displayWidth = mDisplayMetrics.widthPixels;
        final TypedArray keyboardAttr = mContext.obtainStyledAttributes(
                Xml.asAttributeSet(parser), R.styleable.Keyboard, R.attr.keyboardStyle,
                R.style.Keyboard);
        final TypedArray keyAttr = mResources.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.Keyboard_Key);
        try {
            final int displayHeight = mDisplayMetrics.heightPixels;
            final int keyboardHeight = (int)keyboardAttr.getDimension(
                    R.styleable.Keyboard_keyboardHeight, displayHeight / 2);
            final int maxKeyboardHeight = (int)getDimensionOrFraction(keyboardAttr,
                    R.styleable.Keyboard_maxKeyboardHeight, displayHeight, displayHeight / 2);
            int minKeyboardHeight = (int)getDimensionOrFraction(keyboardAttr,
                    R.styleable.Keyboard_minKeyboardHeight, displayHeight, displayHeight / 2);
            if (minKeyboardHeight < 0) {
                // Specified fraction was negative, so it should be calculated against display
                // width.
                minKeyboardHeight = -(int)getDimensionOrFraction(keyboardAttr,
                        R.styleable.Keyboard_minKeyboardHeight, displayWidth, displayWidth / 2);
            }
            final KeyboardParams params = mParams;
            // Keyboard height will not exceed maxKeyboardHeight and will not be less than
            // minKeyboardHeight.
            params.mOccupiedHeight = Math.max(
                    Math.min(keyboardHeight, maxKeyboardHeight), minKeyboardHeight);
            params.mOccupiedWidth = params.mId.mWidth;
            params.mTopPadding = (int)getDimensionOrFraction(keyboardAttr,
                    R.styleable.Keyboard_keyboardTopPadding, params.mOccupiedHeight, 0);
            params.mBottomPadding = (int)getDimensionOrFraction(keyboardAttr,
                    R.styleable.Keyboard_keyboardBottomPadding, params.mOccupiedHeight, 0);
            params.mHorizontalEdgesPadding = (int)getDimensionOrFraction(keyboardAttr,
                    R.styleable.Keyboard_keyboardHorizontalEdgesPadding, mParams.mOccupiedWidth, 0);

            params.mBaseWidth = params.mOccupiedWidth - params.mHorizontalEdgesPadding * 2
                    - params.mHorizontalCenterPadding;
            params.mDefaultKeyWidth = (int)getDimensionOrFraction(keyAttr,
                    R.styleable.Keyboard_Key_keyWidth, params.mBaseWidth,
                    params.mBaseWidth / DEFAULT_KEYBOARD_COLUMNS);
            params.mHorizontalGap = (int)getDimensionOrFraction(keyboardAttr,
                    R.styleable.Keyboard_horizontalGap, params.mBaseWidth, 0);
            params.mVerticalGap = (int)getDimensionOrFraction(keyboardAttr,
                    R.styleable.Keyboard_verticalGap, params.mOccupiedHeight, 0);
            params.mBaseHeight = params.mOccupiedHeight - params.mTopPadding
                    - params.mBottomPadding + params.mVerticalGap;
            params.mDefaultRowHeight = (int)getDimensionOrFraction(keyboardAttr,
                    R.styleable.Keyboard_rowHeight, params.mBaseHeight,
                    params.mBaseHeight / DEFAULT_KEYBOARD_ROWS);

            params.mIsRtlKeyboard = keyboardAttr.getBoolean(
                    R.styleable.Keyboard_isRtlKeyboard, false);
            params.mMoreKeysTemplate = keyboardAttr.getResourceId(
                    R.styleable.Keyboard_moreKeysTemplate, 0);
            params.mMaxMiniKeyboardColumn = keyAttr.getInt(
                    R.styleable.Keyboard_Key_maxMoreKeysColumn, 5);

            params.mIconsSet.loadIcons(keyboardAttr);
        } finally {
            keyAttr.recycle();
            keyboardAttr.recycle();
        }
    }

    private void parseKeyboardContent(XmlPullParser parser, boolean skip)
            throws XmlPullParserException, IOException {
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                final String tag = parser.getName();
                if (TAG_ROW.equals(tag)) {
                    Row row = parseRowAttributes(parser);
                    if (DEBUG) Log.d(TAG, String.format("<%s>", TAG_ROW));
                    if (!skip)
                        startRow(row);
                    parseRowContent(parser, row, skip);
                } else if (TAG_INCLUDE.equals(tag)) {
                    parseIncludeKeyboardContent(parser, skip);
                } else if (TAG_SWITCH.equals(tag)) {
                    parseSwitchKeyboardContent(parser, skip);
                } else if (TAG_KEY_STYLE.equals(tag)) {
                    parseKeyStyle(parser, skip);
                } else {
                    throw new IllegalStartTag(parser, TAG_ROW);
                }
            } else if (event == XmlPullParser.END_TAG) {
                final String tag = parser.getName();
                if (TAG_KEYBOARD.equals(tag)) {
                    endKeyboard();
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

    private Row parseRowAttributes(XmlPullParser parser) {
        final TypedArray a = mResources.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.Keyboard);
        try {
            if (a.hasValue(R.styleable.Keyboard_horizontalGap))
                throw new IllegalAttribute(parser, "horizontalGap");
            if (a.hasValue(R.styleable.Keyboard_verticalGap))
                throw new IllegalAttribute(parser, "verticalGap");
            return new Row(mResources, mParams, parser, mCurrentY);
        } finally {
            a.recycle();
        }
    }

    private void parseRowContent(XmlPullParser parser, Row row, boolean skip)
            throws XmlPullParserException, IOException {
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                final String tag = parser.getName();
                if (TAG_KEY.equals(tag)) {
                    parseKey(parser, row, skip);
                } else if (TAG_SPACER.equals(tag)) {
                    parseSpacer(parser, row, skip);
                } else if (TAG_INCLUDE.equals(tag)) {
                    parseIncludeRowContent(parser, row, skip);
                } else if (TAG_SWITCH.equals(tag)) {
                    parseSwitchRowContent(parser, row, skip);
                } else if (TAG_KEY_STYLE.equals(tag)) {
                    parseKeyStyle(parser, skip);
                } else {
                    throw new IllegalStartTag(parser, TAG_KEY);
                }
            } else if (event == XmlPullParser.END_TAG) {
                final String tag = parser.getName();
                if (TAG_ROW.equals(tag)) {
                    if (DEBUG) Log.d(TAG, String.format("</%s>", TAG_ROW));
                    if (!skip)
                        endRow(row);
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

    private void parseKey(XmlPullParser parser, Row row, boolean skip)
            throws XmlPullParserException, IOException {
        if (skip) {
            checkEndTag(TAG_KEY, parser);
        } else {
            final Key key = new Key(mResources, mParams, row, parser, mKeyStyles);
            if (DEBUG) Log.d(TAG, String.format("<%s%s keyLabel=%s code=%d moreKeys=%s />",
                    TAG_KEY, (key.isEnabled() ? "" : " disabled"), key.mLabel, key.mCode,
                    Arrays.toString(key.mMoreKeys)));
            checkEndTag(TAG_KEY, parser);
            endKey(key);
        }
    }

    private void parseSpacer(XmlPullParser parser, Row row, boolean skip)
            throws XmlPullParserException, IOException {
        if (skip) {
            checkEndTag(TAG_SPACER, parser);
        } else {
            final Key.Spacer spacer = new Key.Spacer(mResources, mParams, row, parser, mKeyStyles);
            if (DEBUG) Log.d(TAG, String.format("<%s />", TAG_SPACER));
            checkEndTag(TAG_SPACER, parser);
            endKey(spacer);
        }
    }

    private void parseIncludeKeyboardContent(XmlPullParser parser, boolean skip)
            throws XmlPullParserException, IOException {
        parseIncludeInternal(parser, null, skip);
    }

    private void parseIncludeRowContent(XmlPullParser parser, Row row, boolean skip)
            throws XmlPullParserException, IOException {
        parseIncludeInternal(parser, row, skip);
    }

    private void parseIncludeInternal(XmlPullParser parser, Row row, boolean skip)
            throws XmlPullParserException, IOException {
        if (skip) {
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
            parseMerge(mResources.getLayout(keyboardLayout), row, skip);
        }
    }

    private void parseMerge(XmlPullParser parser, Row row, boolean skip)
            throws XmlPullParserException, IOException {
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                final String tag = parser.getName();
                if (TAG_MERGE.equals(tag)) {
                    if (row == null) {
                        parseKeyboardContent(parser, skip);
                    } else {
                        parseRowContent(parser, row, skip);
                    }
                    break;
                } else {
                    throw new ParseException(
                            "Included keyboard layout must have <merge> root element", parser);
                }
            }
        }
    }

    private void parseSwitchKeyboardContent(XmlPullParser parser, boolean skip)
            throws XmlPullParserException, IOException {
        parseSwitchInternal(parser, null, skip);
    }

    private void parseSwitchRowContent(XmlPullParser parser, Row row, boolean skip)
            throws XmlPullParserException, IOException {
        parseSwitchInternal(parser, row, skip);
    }

    private void parseSwitchInternal(XmlPullParser parser, Row row, boolean skip)
            throws XmlPullParserException, IOException {
        if (DEBUG) Log.d(TAG, String.format("<%s> %s", TAG_SWITCH, mParams.mId));
        boolean selected = false;
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                final String tag = parser.getName();
                if (TAG_CASE.equals(tag)) {
                    selected |= parseCase(parser, row, selected ? true : skip);
                } else if (TAG_DEFAULT.equals(tag)) {
                    selected |= parseDefault(parser, row, selected ? true : skip);
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

    private boolean parseCase(XmlPullParser parser, Row row, boolean skip)
            throws XmlPullParserException, IOException {
        final boolean selected = parseCaseCondition(parser);
        if (row == null) {
            // Processing Rows.
            parseKeyboardContent(parser, selected ? skip : true);
        } else {
            // Processing Keys.
            parseRowContent(parser, row, selected ? skip : true);
        }
        return selected;
    }

    private boolean parseCaseCondition(XmlPullParser parser) {
        final KeyboardId id = mParams.mId;
        if (id == null)
            return true;

        final TypedArray a = mResources.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.Keyboard_Case);
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
            final boolean shortcutKeyEnabledMatched = matchBoolean(a,
                    R.styleable.Keyboard_Case_shortcutKeyEnabled, id.mShortcutKeyEnabled);
            final boolean hasShortcutKeyMatched = matchBoolean(a,
                    R.styleable.Keyboard_Case_hasShortcutKey, id.mHasShortcutKey);
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
                    && shortcutKeyEnabledMatched && hasShortcutKeyMatched && imeActionMatched &&
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
                    booleanAttr(
                            a, R.styleable.Keyboard_Case_shortcutKeyEnabled, "shortcutKeyEnabled"),
                    booleanAttr(a, R.styleable.Keyboard_Case_hasShortcutKey, "hasShortcutKey"),
                    textAttr(EditorInfoCompatUtils.imeOptionsName(
                            a.getInt(R.styleable.Keyboard_Case_imeAction, -1)), "imeAction"),
                    textAttr(a.getString(R.styleable.Keyboard_Case_localeCode), "localeCode"),
                    textAttr(a.getString(R.styleable.Keyboard_Case_languageCode), "languageCode"),
                    textAttr(a.getString(R.styleable.Keyboard_Case_countryCode), "countryCode"),
                    Boolean.toString(selected)));

            return selected;
        } finally {
            a.recycle();
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

    private boolean parseDefault(XmlPullParser parser, Row row, boolean skip)
            throws XmlPullParserException, IOException {
        if (DEBUG) Log.d(TAG, String.format("<%s>", TAG_DEFAULT));
        if (row == null) {
            parseKeyboardContent(parser, skip);
        } else {
            parseRowContent(parser, row, skip);
        }
        return true;
    }

    private void parseKeyStyle(XmlPullParser parser, boolean skip) {
        TypedArray keyStyleAttr = mResources.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.Keyboard_KeyStyle);
        TypedArray keyAttrs = mResources.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.Keyboard_Key);
        try {
            if (!keyStyleAttr.hasValue(R.styleable.Keyboard_KeyStyle_styleName))
                throw new ParseException("<" + TAG_KEY_STYLE
                        + "/> needs styleName attribute", parser);
            if (!skip)
                mKeyStyles.parseKeyStyleAttributes(keyStyleAttr, keyAttrs, parser);
        } finally {
            keyStyleAttr.recycle();
            keyAttrs.recycle();
        }
    }

    private static void checkEndTag(String tag, XmlPullParser parser)
            throws XmlPullParserException, IOException {
        if (parser.next() == XmlPullParser.END_TAG && tag.equals(parser.getName()))
            return;
        throw new NonEmptyTag(tag, parser);
    }

    private void startKeyboard() {
        mCurrentY += mParams.mTopPadding;
        mTopEdge = true;
    }

    private void startRow(Row row) {
        addEdgeSpace(mParams.mHorizontalEdgesPadding, row);
        mCurrentRow = row;
        mLeftEdge = true;
        mRightEdgeKey = null;
    }

    private void endRow(Row row) {
        if (mCurrentRow == null)
            throw new InflateException("orphant end row tag");
        if (mRightEdgeKey != null) {
            mRightEdgeKey.markAsRightEdge(mParams);
            mRightEdgeKey = null;
        }
        addEdgeSpace(mParams.mHorizontalEdgesPadding, row);
        mCurrentY += row.mRowHeight;
        mCurrentRow = null;
        mTopEdge = false;
    }

    private void endKey(Key key) {
        mParams.onAddKey(key);
        if (mLeftEdge) {
            key.markAsLeftEdge(mParams);
            mLeftEdge = false;
        }
        if (mTopEdge) {
            key.markAsTopEdge(mParams);
        }
        mRightEdgeKey = key;
    }

    private void endKeyboard() {
    }

    private void addEdgeSpace(float width, Row row) {
        row.advanceXPos(width);
        mLeftEdge = false;
        mRightEdgeKey = null;
    }

    public static float getDimensionOrFraction(TypedArray a, int index, int base, float defValue) {
        final TypedValue value = a.peekValue(index);
        if (value == null)
            return defValue;
        if (isFractionValue(value)) {
            return a.getFraction(index, base, base, defValue);
        } else if (isDimensionValue(value)) {
            return a.getDimension(index, defValue);
        }
        return defValue;
    }

    public static int getEnumValue(TypedArray a, int index, int defValue) {
        final TypedValue value = a.peekValue(index);
        if (value == null)
            return defValue;
        if (isIntegerValue(value)) {
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
        public ParseException(String msg, XmlPullParser parser) {
            super(msg + " at line " + parser.getLineNumber());
        }
    }

    @SuppressWarnings("serial")
    private static class IllegalStartTag extends ParseException {
        public IllegalStartTag(XmlPullParser parser, String parent) {
            super("Illegal start tag " + parser.getName() + " in " + parent, parser);
        }
    }

    @SuppressWarnings("serial")
    private static class IllegalEndTag extends ParseException {
        public IllegalEndTag(XmlPullParser parser, String parent) {
            super("Illegal end tag " + parser.getName() + " in " + parent, parser);
        }
    }

    @SuppressWarnings("serial")
    private static class IllegalAttribute extends ParseException {
        public IllegalAttribute(XmlPullParser parser, String attribute) {
            super("Tag " + parser.getName() + " has illegal attribute " + attribute, parser);
        }
    }

    @SuppressWarnings("serial")
    private static class NonEmptyTag extends ParseException {
        public NonEmptyTag(String tag, XmlPullParser parser) {
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
