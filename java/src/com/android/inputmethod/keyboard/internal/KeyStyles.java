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

import android.content.res.TypedArray;
import android.util.Log;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.XmlParseUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.util.HashMap;

public class KeyStyles {
    private static final String TAG = KeyStyles.class.getSimpleName();
    private static final boolean DEBUG = false;

    private final HashMap<String, DeclaredKeyStyle> mStyles =
            new HashMap<String, DeclaredKeyStyle>();

    private final KeyboardTextsSet mTextsSet;
    private final KeyStyle mEmptyKeyStyle;

    public KeyStyles(KeyboardTextsSet textsSet) {
        mTextsSet = textsSet;
        mEmptyKeyStyle = new EmptyKeyStyle(textsSet);
    }

    public static abstract class KeyStyle {
        protected final KeyboardTextsSet mTextsSet;

        public KeyStyle(KeyboardTextsSet textsSet) {
            mTextsSet = textsSet;
        }

        public abstract String[] getStringArray(TypedArray a, int index);
        public abstract String getString(TypedArray a, int index);
        public abstract int getInt(TypedArray a, int index, int defaultValue);
        public abstract int getFlag(TypedArray a, int index);

        protected String parseString(TypedArray a, int index) {
            if (a.hasValue(index)) {
                return KeySpecParser.resolveTextReference(a.getString(index), mTextsSet);
            }
            return null;
        }

        protected String[] parseStringArray(TypedArray a, int index) {
            if (a.hasValue(index)) {
                return KeySpecParser.parseCsvString(a.getString(index), mTextsSet);
            }
            return null;
        }
    }

    private static class EmptyKeyStyle extends KeyStyle {
        public EmptyKeyStyle(KeyboardTextsSet textsSet) {
            super(textsSet);
        }

        @Override
        public String[] getStringArray(TypedArray a, int index) {
            return parseStringArray(a, index);
        }

        @Override
        public String getString(TypedArray a, int index) {
            return parseString(a, index);
        }

        @Override
        public int getInt(TypedArray a, int index, int defaultValue) {
            return a.getInt(index, defaultValue);
        }

        @Override
        public int getFlag(TypedArray a, int index) {
            return a.getInt(index, 0);
        }
    }

    private static class DeclaredKeyStyle extends KeyStyle {
        private final HashMap<Integer, Object> mStyleAttributes = new HashMap<Integer, Object>();

        public DeclaredKeyStyle(KeyboardTextsSet textsSet) {
            super(textsSet);
        }

        @Override
        public String[] getStringArray(TypedArray a, int index) {
            if (a.hasValue(index)) {
                return parseStringArray(a, index);
            }
            return (String[])mStyleAttributes.get(index);
        }

        @Override
        public String getString(TypedArray a, int index) {
            if (a.hasValue(index)) {
                return parseString(a, index);
            }
            return (String)mStyleAttributes.get(index);
        }

        @Override
        public int getInt(TypedArray a, int index, int defaultValue) {
            if (a.hasValue(index)) {
                return a.getInt(index, defaultValue);
            }
            final Integer styleValue = (Integer)mStyleAttributes.get(index);
            return styleValue != null ? styleValue : defaultValue;
        }

        @Override
        public int getFlag(TypedArray a, int index) {
            final int value = a.getInt(index, 0);
            final Integer styleValue = (Integer)mStyleAttributes.get(index);
            return (styleValue != null ? styleValue : 0) | value;
        }

        void readKeyAttributes(TypedArray keyAttr) {
            // TODO: Currently not all Key attributes can be declared as style.
            readString(keyAttr, R.styleable.Keyboard_Key_code);
            readString(keyAttr, R.styleable.Keyboard_Key_altCode);
            readString(keyAttr, R.styleable.Keyboard_Key_keyLabel);
            readString(keyAttr, R.styleable.Keyboard_Key_keyOutputText);
            readString(keyAttr, R.styleable.Keyboard_Key_keyHintLabel);
            readStringArray(keyAttr, R.styleable.Keyboard_Key_moreKeys);
            readStringArray(keyAttr, R.styleable.Keyboard_Key_additionalMoreKeys);
            readFlag(keyAttr, R.styleable.Keyboard_Key_keyLabelFlags);
            readString(keyAttr, R.styleable.Keyboard_Key_keyIcon);
            readString(keyAttr, R.styleable.Keyboard_Key_keyIconDisabled);
            readString(keyAttr, R.styleable.Keyboard_Key_keyIconPreview);
            readInt(keyAttr, R.styleable.Keyboard_Key_maxMoreKeysColumn);
            readInt(keyAttr, R.styleable.Keyboard_Key_backgroundType);
            readFlag(keyAttr, R.styleable.Keyboard_Key_keyActionFlags);
        }

        private void readString(TypedArray a, int index) {
            if (a.hasValue(index)) {
                mStyleAttributes.put(index, parseString(a, index));
            }
        }

        private void readInt(TypedArray a, int index) {
            if (a.hasValue(index)) {
                mStyleAttributes.put(index, a.getInt(index, 0));
            }
        }

        private void readFlag(TypedArray a, int index) {
            if (a.hasValue(index)) {
                final Integer value = (Integer)mStyleAttributes.get(index);
                mStyleAttributes.put(index, a.getInt(index, 0) | (value != null ? value : 0));
            }
        }

        private void readStringArray(TypedArray a, int index) {
            if (a.hasValue(index)) {
                mStyleAttributes.put(index, parseStringArray(a, index));
            }
        }

        void addParentStyleAttributes(DeclaredKeyStyle parentStyle) {
            mStyleAttributes.putAll(parentStyle.mStyleAttributes);
        }
    }

    public void parseKeyStyleAttributes(TypedArray keyStyleAttr, TypedArray keyAttrs,
            XmlPullParser parser) throws XmlPullParserException {
        final String styleName = keyStyleAttr.getString(R.styleable.Keyboard_KeyStyle_styleName);
        if (DEBUG) {
            Log.d(TAG, String.format("<%s styleName=%s />",
                    Keyboard.Builder.TAG_KEY_STYLE, styleName));
            if (mStyles.containsKey(styleName)) {
                Log.d(TAG, "key-style " + styleName + " is overridden at "
                        + parser.getPositionDescription());
            }
        }

        final DeclaredKeyStyle style = new DeclaredKeyStyle(mTextsSet);
        if (keyStyleAttr.hasValue(R.styleable.Keyboard_KeyStyle_parentStyle)) {
            final String parentStyle = keyStyleAttr.getString(
                    R.styleable.Keyboard_KeyStyle_parentStyle);
            final DeclaredKeyStyle parent = mStyles.get(parentStyle);
            if (parent == null) {
                throw new XmlParseUtils.ParseException(
                        "Unknown parentStyle " + parentStyle, parser);
            }
            style.addParentStyleAttributes(parent);
        }
        style.readKeyAttributes(keyAttrs);
        mStyles.put(styleName, style);
    }

    public KeyStyle getKeyStyle(String styleName) {
        return mStyles.get(styleName);
    }

    public KeyStyle getEmptyKeyStyle() {
        return mEmptyKeyStyle;
    }
}
