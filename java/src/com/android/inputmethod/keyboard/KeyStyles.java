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
import com.android.inputmethod.latin.R;

import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.TypedValue;

import java.util.HashMap;
import java.util.StringTokenizer;

public class KeyStyles {
    private static final String TAG = "KeyStyles";

    private final HashMap<String, DeclaredKeyStyle> mStyles =
            new HashMap<String, DeclaredKeyStyle>();
    private static final KeyStyle EMPTY_KEY_STYLE = new EmptyKeyStyle();

    public interface KeyStyle {
        public int[] getIntArray(TypedArray a, int index);
        public Drawable getDrawable(TypedArray a, int index);
        public CharSequence getText(TypedArray a, int index);
        public int getResourceId(TypedArray a, int index, int defaultValue);
        public int getInt(TypedArray a, int index, int defaultValue);
        public int getFlag(TypedArray a, int index, int defaultValue);
        public boolean getBoolean(TypedArray a, int index, boolean defaultValue);
    }

    public static class EmptyKeyStyle implements KeyStyle {
        private EmptyKeyStyle() {
            // Nothing to do.
        }

        @Override
        public int[] getIntArray(TypedArray a, int index) {
            return parseIntArray(a, index);
        }

        @Override
        public Drawable getDrawable(TypedArray a, int index) {
            return a.getDrawable(index);
        }

        @Override
        public CharSequence getText(TypedArray a, int index) {
            return a.getText(index);
        }

        @Override
        public int getResourceId(TypedArray a, int index, int defaultValue) {
            return a.getResourceId(index, defaultValue);
        }

        @Override
        public int getInt(TypedArray a, int index, int defaultValue) {
            return a.getInt(index, defaultValue);
        }

        @Override
        public int getFlag(TypedArray a, int index, int defaultValue) {
            return a.getInt(index, defaultValue);
        }

        @Override
        public boolean getBoolean(TypedArray a, int index, boolean defaultValue) {
            return a.getBoolean(index, defaultValue);
        }

        protected static int[] parseIntArray(TypedArray a, int index) {
            TypedValue v = new TypedValue();
            a.getValue(index, v);
            if (v.type == TypedValue.TYPE_INT_DEC || v.type == TypedValue.TYPE_INT_HEX) {
                return new int[] { v.data };
            } else if (v.type == TypedValue.TYPE_STRING) {
                return parseCSV(v.string.toString());
            } else {
                return null;
            }
        }

        private static int[] parseCSV(String value) {
            int count = 0;
            int lastIndex = 0;
            if (value.length() > 0) {
                count++;
                while ((lastIndex = value.indexOf(",", lastIndex + 1)) > 0) {
                    count++;
                }
            }
            int[] values = new int[count];
            count = 0;
            StringTokenizer st = new StringTokenizer(value, ",");
            while (st.hasMoreTokens()) {
                try {
                    values[count++] = Integer.parseInt(st.nextToken());
                } catch (NumberFormatException nfe) {
                    Log.w(TAG, "Error parsing integer CSV " + value);
                }
            }
            return values;
        }
    }

    public static class DeclaredKeyStyle extends EmptyKeyStyle {
        private final HashMap<Integer, Object> mAttributes = new HashMap<Integer, Object>();

        @Override
        public int[] getIntArray(TypedArray a, int index) {
            return a.hasValue(index)
                    ? super.getIntArray(a, index) : (int[])mAttributes.get(index);
        }

        @Override
        public Drawable getDrawable(TypedArray a, int index) {
            return a.hasValue(index)
                    ? super.getDrawable(a, index) : (Drawable)mAttributes.get(index);
        }

        @Override
        public CharSequence getText(TypedArray a, int index) {
            return a.hasValue(index)
                    ? super.getText(a, index) : (CharSequence)mAttributes.get(index);
        }

        @Override
        public int getResourceId(TypedArray a, int index, int defaultValue) {
            final Integer value = (Integer)mAttributes.get(index);
            return super.getResourceId(a, index, (value != null) ? value : defaultValue);
        }

        @Override
        public int getFlag(TypedArray a, int index, int defaultValue) {
            final Integer value = (Integer)mAttributes.get(index);
            return super.getFlag(a, index, defaultValue) | (value != null ? value : 0);
        }

        @Override
        public boolean getBoolean(TypedArray a, int index, boolean defaultValue) {
            final Boolean value = (Boolean)mAttributes.get(index);
            return super.getBoolean(a, index, (value != null) ? value : defaultValue);
        }

        private DeclaredKeyStyle() {
            super();
        }

        private void parseKeyStyleAttributes(TypedArray a) {
            // TODO: Currently not all Key attributes can be declared as style.
            readIntArray(a, R.styleable.Keyboard_Key_codes);
            readText(a, R.styleable.Keyboard_Key_keyLabel);
            readFlag(a, R.styleable.Keyboard_Key_keyLabelOption);
            readText(a, R.styleable.Keyboard_Key_keyOutputText);
            readDrawable(a, R.styleable.Keyboard_Key_keyIcon);
            readDrawable(a, R.styleable.Keyboard_Key_iconPreview);
            readDrawable(a, R.styleable.Keyboard_Key_keyHintIcon);
            readDrawable(a, R.styleable.Keyboard_Key_shiftedIcon);
            readResourceId(a, R.styleable.Keyboard_Key_popupKeyboard);
            readBoolean(a, R.styleable.Keyboard_Key_isModifier);
            readBoolean(a, R.styleable.Keyboard_Key_isSticky);
            readBoolean(a, R.styleable.Keyboard_Key_isRepeatable);
        }

        private void readDrawable(TypedArray a, int index) {
            if (a.hasValue(index))
                mAttributes.put(index, a.getDrawable(index));
        }

        private void readText(TypedArray a, int index) {
            if (a.hasValue(index))
                mAttributes.put(index, a.getText(index));
        }

        private void readResourceId(TypedArray a, int index) {
            if (a.hasValue(index))
                mAttributes.put(index, a.getResourceId(index, 0));
        }

        private void readFlag(TypedArray a, int index) {
            final Integer value = (Integer)mAttributes.get(index);
            if (a.hasValue(index))
                mAttributes.put(index, a.getInt(index, 0) | (value != null ? value : 0));
        }

        private void readBoolean(TypedArray a, int index) {
            if (a.hasValue(index))
                mAttributes.put(index, a.getBoolean(index, false));
        }

        private void readIntArray(TypedArray a, int index) {
            if (a.hasValue(index)) {
                final int[] value = parseIntArray(a, index);
                if (value != null)
                    mAttributes.put(index, value);
            }
        }

        private void addParent(DeclaredKeyStyle parentStyle) {
            mAttributes.putAll(parentStyle.mAttributes);
        }
    }

    public void parseKeyStyleAttributes(TypedArray a, TypedArray keyAttrs,
            XmlResourceParser parser) {
        String styleName = a.getString(R.styleable.Keyboard_KeyStyle_styleName);
        if (mStyles.containsKey(styleName))
            throw new ParseException("duplicate key style declared: " + styleName, parser);

        final DeclaredKeyStyle style = new DeclaredKeyStyle();
        if (a.hasValue(R.styleable.Keyboard_KeyStyle_parentStyle)) {
            String parentStyle = a.getString(
                    R.styleable.Keyboard_KeyStyle_parentStyle);
            final DeclaredKeyStyle parent = mStyles.get(parentStyle);
            if (parent == null)
                throw new ParseException("Unknown parentStyle " + parent, parser);
            style.addParent(parent);
        }
        style.parseKeyStyleAttributes(keyAttrs);
        mStyles.put(styleName, style);
    }

    public KeyStyle getKeyStyle(String styleName) {
        return mStyles.get(styleName);
    }

    public KeyStyle getEmptyKeyStyle() {
        return EMPTY_KEY_STYLE;
    }
}
