/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.inputmethod.keyboard.internal;

import android.content.res.TypedArray;
import android.util.Log;
import android.util.SparseArray;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.XmlParseUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.util.HashMap;

public final class KeyStylesSet {
    private static final String TAG = KeyStylesSet.class.getSimpleName();
    private static final boolean DEBUG = false;

    private final HashMap<String, KeyStyle> mStyles = CollectionUtils.newHashMap();

    private final KeyboardTextsSet mTextsSet;
    private final KeyStyle mEmptyKeyStyle;
    private static final String EMPTY_STYLE_NAME = "<empty>";

    public KeyStylesSet(final KeyboardTextsSet textsSet) {
        mTextsSet = textsSet;
        mEmptyKeyStyle = new EmptyKeyStyle(textsSet);
        mStyles.put(EMPTY_STYLE_NAME, mEmptyKeyStyle);
    }

    private static final class EmptyKeyStyle extends KeyStyle {
        EmptyKeyStyle(final KeyboardTextsSet textsSet) {
            super(textsSet);
        }

        @Override
        public String[] getStringArray(final TypedArray a, final int index) {
            return parseStringArray(a, index);
        }

        @Override
        public String getString(final TypedArray a, final int index) {
            return parseString(a, index);
        }

        @Override
        public int getInt(final TypedArray a, final int index, final int defaultValue) {
            return a.getInt(index, defaultValue);
        }

        @Override
        public int getFlags(final TypedArray a, final int index) {
            return a.getInt(index, 0);
        }
    }

    private static final class DeclaredKeyStyle extends KeyStyle {
        private final HashMap<String, KeyStyle> mStyles;
        private final String mParentStyleName;
        private final SparseArray<Object> mStyleAttributes = CollectionUtils.newSparseArray();

        public DeclaredKeyStyle(final String parentStyleName, final KeyboardTextsSet textsSet,
                final HashMap<String, KeyStyle> styles) {
            super(textsSet);
            mParentStyleName = parentStyleName;
            mStyles = styles;
        }

        @Override
        public String[] getStringArray(final TypedArray a, final int index) {
            if (a.hasValue(index)) {
                return parseStringArray(a, index);
            }
            final Object value = mStyleAttributes.get(index);
            if (value != null) {
                return (String[])value;
            }
            final KeyStyle parentStyle = mStyles.get(mParentStyleName);
            return parentStyle.getStringArray(a, index);
        }

        @Override
        public String getString(final TypedArray a, final int index) {
            if (a.hasValue(index)) {
                return parseString(a, index);
            }
            final Object value = mStyleAttributes.get(index);
            if (value != null) {
                return (String)value;
            }
            final KeyStyle parentStyle = mStyles.get(mParentStyleName);
            return parentStyle.getString(a, index);
        }

        @Override
        public int getInt(final TypedArray a, final int index, final int defaultValue) {
            if (a.hasValue(index)) {
                return a.getInt(index, defaultValue);
            }
            final Object value = mStyleAttributes.get(index);
            if (value != null) {
                return (Integer)value;
            }
            final KeyStyle parentStyle = mStyles.get(mParentStyleName);
            return parentStyle.getInt(a, index, defaultValue);
        }

        @Override
        public int getFlags(final TypedArray a, final int index) {
            final int parentFlags = mStyles.get(mParentStyleName).getFlags(a, index);
            final Integer value = (Integer)mStyleAttributes.get(index);
            final int styleFlags = (value != null) ? value : 0;
            final int flags = a.getInt(index, 0);
            return flags | styleFlags | parentFlags;
        }

        public void readKeyAttributes(final TypedArray keyAttr) {
            // TODO: Currently not all Key attributes can be declared as style.
            readString(keyAttr, R.styleable.Keyboard_Key_code);
            readString(keyAttr, R.styleable.Keyboard_Key_altCode);
            readString(keyAttr, R.styleable.Keyboard_Key_keyLabel);
            readString(keyAttr, R.styleable.Keyboard_Key_keyOutputText);
            readString(keyAttr, R.styleable.Keyboard_Key_keyHintLabel);
            readStringArray(keyAttr, R.styleable.Keyboard_Key_moreKeys);
            readStringArray(keyAttr, R.styleable.Keyboard_Key_additionalMoreKeys);
            readFlags(keyAttr, R.styleable.Keyboard_Key_keyLabelFlags);
            readString(keyAttr, R.styleable.Keyboard_Key_keyIcon);
            readString(keyAttr, R.styleable.Keyboard_Key_keyIconDisabled);
            readString(keyAttr, R.styleable.Keyboard_Key_keyIconPreview);
            readInt(keyAttr, R.styleable.Keyboard_Key_maxMoreKeysColumn);
            readInt(keyAttr, R.styleable.Keyboard_Key_backgroundType);
            readFlags(keyAttr, R.styleable.Keyboard_Key_keyActionFlags);
        }

        private void readString(final TypedArray a, final int index) {
            if (a.hasValue(index)) {
                mStyleAttributes.put(index, parseString(a, index));
            }
        }

        private void readInt(final TypedArray a, final int index) {
            if (a.hasValue(index)) {
                mStyleAttributes.put(index, a.getInt(index, 0));
            }
        }

        private void readFlags(final TypedArray a, final int index) {
            if (a.hasValue(index)) {
                final Integer value = (Integer)mStyleAttributes.get(index);
                final int styleFlags = value != null ? value : 0;
                mStyleAttributes.put(index, a.getInt(index, 0) | styleFlags);
            }
        }

        private void readStringArray(final TypedArray a, final int index) {
            if (a.hasValue(index)) {
                mStyleAttributes.put(index, parseStringArray(a, index));
            }
        }
    }

    public void parseKeyStyleAttributes(final TypedArray keyStyleAttr, final TypedArray keyAttrs,
            final XmlPullParser parser) throws XmlPullParserException {
        final String styleName = keyStyleAttr.getString(R.styleable.Keyboard_KeyStyle_styleName);
        if (DEBUG) {
            Log.d(TAG, String.format("<%s styleName=%s />",
                    KeyboardBuilder.TAG_KEY_STYLE, styleName));
            if (mStyles.containsKey(styleName)) {
                Log.d(TAG, "key-style " + styleName + " is overridden at "
                        + parser.getPositionDescription());
            }
        }

        String parentStyleName = EMPTY_STYLE_NAME;
        if (keyStyleAttr.hasValue(R.styleable.Keyboard_KeyStyle_parentStyle)) {
            parentStyleName = keyStyleAttr.getString(R.styleable.Keyboard_KeyStyle_parentStyle);
            if (!mStyles.containsKey(parentStyleName)) {
                throw new XmlParseUtils.ParseException(
                        "Unknown parentStyle " + parentStyleName, parser);
            }
        }
        final DeclaredKeyStyle style = new DeclaredKeyStyle(parentStyleName, mTextsSet, mStyles);
        style.readKeyAttributes(keyAttrs);
        mStyles.put(styleName, style);
    }

    public KeyStyle getKeyStyle(final TypedArray keyAttr, final XmlPullParser parser)
            throws XmlParseUtils.ParseException {
        if (!keyAttr.hasValue(R.styleable.Keyboard_Key_keyStyle)) {
            return mEmptyKeyStyle;
        }
        final String styleName = keyAttr.getString(R.styleable.Keyboard_Key_keyStyle);
        if (!mStyles.containsKey(styleName)) {
            throw new XmlParseUtils.ParseException("Unknown key style: " + styleName, parser);
        }
        return mStyles.get(styleName);
    }
}
