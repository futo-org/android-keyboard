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

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.Log;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.Utils;
import com.android.inputmethod.latin.XmlParseUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.util.ArrayList;
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
        public int getFlag(TypedArray a, int index, int defaultValue);
    }

    private static class EmptyKeyStyle implements KeyStyle {
        EmptyKeyStyle() {
            // Nothing to do.
        }

        @Override
        public String[] getStringArray(TypedArray a, int index) {
            return parseStringArray(a, index);
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
        public int getFlag(TypedArray a, int index, int defaultValue) {
            return a.getInt(index, defaultValue);
        }

        protected static String[] parseStringArray(TypedArray a, int index) {
            if (!a.hasValue(index))
                return null;
            return parseCsvString(a.getString(index), a.getResources(), R.string.english_ime_name);
        }
    }

    /* package for test */
    static String[] parseCsvString(String rawText, Resources res, int packageNameResId) {
        final String text = Utils.resolveStringResource(rawText, res, packageNameResId);
        final int size = text.length();
        if (size == 0) {
            return null;
        }
        if (size == 1) {
            return new String[] { text };
        }

        final StringBuilder sb = new StringBuilder();
        ArrayList<String> list = null;
        int start = 0;
        for (int pos = 0; pos < size; pos++) {
            final char c = text.charAt(pos);
            if (c == ',') {
                if (list == null) {
                    list = new ArrayList<String>();
                }
                if (sb.length() == 0) {
                    list.add(text.substring(start, pos));
                } else {
                    list.add(sb.toString());
                    sb.setLength(0);
                }
                start = pos + 1;
                continue;
            } else if (c == Utils.ESCAPE_CHAR) {
                if (start == pos) {
                    // Skip escape character at the beginning of the value.
                    start++;
                    pos++;
                } else {
                    if (start < pos && sb.length() == 0) {
                        sb.append(text.subSequence(start, pos));
                    }
                    pos++;
                    if (pos < size) {
                        sb.append(text.charAt(pos));
                    }
                }
            } else if (sb.length() > 0) {
                sb.append(c);
            }
        }
        if (list == null) {
            return new String[] {
                    sb.length() > 0 ? sb.toString() : text.substring(start)
            };
        } else {
            list.add(sb.length() > 0 ? sb.toString() : text.substring(start));
            return list.toArray(new String[list.size()]);
        }
    }

    private static class DeclaredKeyStyle extends EmptyKeyStyle {
        private final HashMap<Integer, Object> mAttributes = new HashMap<Integer, Object>();

        @Override
        public String[] getStringArray(TypedArray a, int index) {
            return a.hasValue(index)
                    ? super.getStringArray(a, index) : (String[])mAttributes.get(index);
        }

        @Override
        public String getString(TypedArray a, int index) {
            return a.hasValue(index)
                    ? super.getString(a, index) : (String)mAttributes.get(index);
        }

        @Override
        public int getInt(TypedArray a, int index, int defaultValue) {
            final Integer value = (Integer)mAttributes.get(index);
            return super.getInt(a, index, (value != null) ? value : defaultValue);
        }

        @Override
        public int getFlag(TypedArray a, int index, int defaultValue) {
            final Integer value = (Integer)mAttributes.get(index);
            return super.getFlag(a, index, defaultValue) | (value != null ? value : 0);
        }

        DeclaredKeyStyle() {
            super();
        }

        void parseKeyStyleAttributes(TypedArray keyAttr) {
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
            if (a.hasValue(index))
                mAttributes.put(index, a.getString(index));
        }

        private void readInt(TypedArray a, int index) {
            if (a.hasValue(index))
                mAttributes.put(index, a.getInt(index, 0));
        }

        private void readFlag(TypedArray a, int index) {
            final Integer value = (Integer)mAttributes.get(index);
            if (a.hasValue(index))
                mAttributes.put(index, a.getInt(index, 0) | (value != null ? value : 0));
        }

        private void readStringArray(TypedArray a, int index) {
            final String[] value = parseStringArray(a, index);
            if (value != null)
                mAttributes.put(index, value);
        }

        void addParent(DeclaredKeyStyle parentStyle) {
            mAttributes.putAll(parentStyle.mAttributes);
        }
    }

    public void parseKeyStyleAttributes(TypedArray keyStyleAttr, TypedArray keyAttrs,
            XmlPullParser parser) throws XmlPullParserException {
        final String styleName = keyStyleAttr.getString(R.styleable.Keyboard_KeyStyle_styleName);
        if (DEBUG) Log.d(TAG, String.format("<%s styleName=%s />",
                Keyboard.Builder.TAG_KEY_STYLE, styleName));
        if (mStyles.containsKey(styleName))
            throw new XmlParseUtils.ParseException(
                    "duplicate key style declared: " + styleName, parser);

        final DeclaredKeyStyle style = new DeclaredKeyStyle();
        if (keyStyleAttr.hasValue(R.styleable.Keyboard_KeyStyle_parentStyle)) {
            final String parentStyle = keyStyleAttr.getString(
                    R.styleable.Keyboard_KeyStyle_parentStyle);
            final DeclaredKeyStyle parent = mStyles.get(parentStyle);
            if (parent == null)
                throw new XmlParseUtils.ParseException(
                        "Unknown parentStyle " + parentStyle, parser);
            style.addParent(parent);
        }
        style.parseKeyStyleAttributes(keyAttrs);
        mStyles.put(styleName, style);
    }

    public KeyStyle getKeyStyle(String styleName) {
        return mStyles.get(styleName);
    }

    public static KeyStyle getEmptyKeyStyle() {
        return EMPTY_KEY_STYLE;
    }
}
