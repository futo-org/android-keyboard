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
    private static final KeyStyle EMPTY_KEY_STYLE = new EmptyKeyStyle();

    public interface KeyStyle {
        public String[] getStringArray(TypedArray a, int index);
        public String getString(TypedArray a, int index);
        public int getInt(TypedArray a, int index, int defaultValue);
        public int getFlag(TypedArray a, int index);
    }

    static class EmptyKeyStyle implements KeyStyle {
        @Override
        public String[] getStringArray(TypedArray a, int index) {
            return KeyStyles.parseStringArray(a, index);
        }

        @Override
        public String getString(TypedArray a, int index) {
            return a.getString(index);
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

    static class DeclaredKeyStyle implements KeyStyle {
        private final HashMap<Integer, Object> mStyleAttributes = new HashMap<Integer, Object>();

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
                return a.getString(index);
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
            readInt(keyAttr, R.styleable.Keyboard_Key_code);
            readInt(keyAttr, R.styleable.Keyboard_Key_altCode);
            readString(keyAttr, R.styleable.Keyboard_Key_keyLabel);
            readString(keyAttr, R.styleable.Keyboard_Key_keyOutputText);
            readString(keyAttr, R.styleable.Keyboard_Key_keyHintLabel);
            readStringArray(keyAttr, R.styleable.Keyboard_Key_moreKeys);
            readStringArray(keyAttr, R.styleable.Keyboard_Key_additionalMoreKeys);
            readFlag(keyAttr, R.styleable.Keyboard_Key_keyLabelFlags);
            readInt(keyAttr, R.styleable.Keyboard_Key_keyIcon);
            readInt(keyAttr, R.styleable.Keyboard_Key_keyIconDisabled);
            readInt(keyAttr, R.styleable.Keyboard_Key_keyIconPreview);
            readInt(keyAttr, R.styleable.Keyboard_Key_maxMoreKeysColumn);
            readInt(keyAttr, R.styleable.Keyboard_Key_backgroundType);
            readFlag(keyAttr, R.styleable.Keyboard_Key_keyActionFlags);
        }

        private void readString(TypedArray a, int index) {
            if (a.hasValue(index)) {
                mStyleAttributes.put(index, a.getString(index));
            }
        }

        private void readInt(TypedArray a, int index) {
            if (a.hasValue(index)) {
                mStyleAttributes.put(index, a.getInt(index, 0));
            }
        }

        private void readFlag(TypedArray a, int index) {
            final Integer value = (Integer)mStyleAttributes.get(index);
            if (a.hasValue(index)) {
                mStyleAttributes.put(index, a.getInt(index, 0) | (value != null ? value : 0));
            }
        }

        private void readStringArray(TypedArray a, int index) {
            final String[] value = parseStringArray(a, index);
            if (value != null) {
                mStyleAttributes.put(index, value);
            }
        }

        void addParentStyleAttributes(DeclaredKeyStyle parentStyle) {
            mStyleAttributes.putAll(parentStyle.mStyleAttributes);
        }
    }

    static String[] parseStringArray(TypedArray a, int index) {
        if (a.hasValue(index)) {
            return KeySpecParser.parseCsvString(
                    a.getString(index), a.getResources(), R.string.english_ime_name);
        }
        return null;
    }

    public void parseKeyStyleAttributes(TypedArray keyStyleAttr, TypedArray keyAttrs,
            XmlPullParser parser) throws XmlPullParserException {
        final String styleName = keyStyleAttr.getString(R.styleable.Keyboard_KeyStyle_styleName);
        if (DEBUG) {
            Log.d(TAG, String.format("<%s styleName=%s />",
                    Keyboard.Builder.TAG_KEY_STYLE, styleName));
        }
        if (mStyles.containsKey(styleName)) {
            throw new XmlParseUtils.ParseException(
                    "duplicate key style declared: " + styleName, parser);
        }

        final DeclaredKeyStyle style = new DeclaredKeyStyle();
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

    public static KeyStyle getEmptyKeyStyle() {
        return EMPTY_KEY_STYLE;
    }
}
