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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;

public class KeyStyles {
    private static final String TAG = "KeyStyles";
    private static final boolean DEBUG = false;

    private final HashMap<String, DeclaredKeyStyle> mStyles =
            new HashMap<String, DeclaredKeyStyle>();
    private static final KeyStyle EMPTY_KEY_STYLE = new EmptyKeyStyle();

    public interface KeyStyle {
        public int[] getIntArray(TypedArray a, int index);
        public CharSequence[] getTextArray(TypedArray a, int index);
        public Drawable getDrawable(TypedArray a, int index);
        public CharSequence getText(TypedArray a, int index);
        public int getInt(TypedArray a, int index, int defaultValue);
        public int getFlag(TypedArray a, int index, int defaultValue);
        public boolean getBoolean(TypedArray a, int index, boolean defaultValue);
    }

    /* package */ static class EmptyKeyStyle implements KeyStyle {
        private EmptyKeyStyle() {
            // Nothing to do.
        }

        @Override
        public int[] getIntArray(TypedArray a, int index) {
            return parseIntArray(a, index);
        }

        @Override
        public CharSequence[] getTextArray(TypedArray a, int index) {
            return parseTextArray(a, index);
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

        protected static CharSequence[] parseTextArray(TypedArray a, int index) {
            if (!a.hasValue(index))
                return null;
            final CharSequence text = a.getText(index);
            return parseCsvText(text);
        }

        /* package */ static CharSequence[] parseCsvText(CharSequence text) {
            final int size = text.length();
            if (size == 0) return null;
            if (size == 1) return new CharSequence[] { text };
            final StringBuilder sb = new StringBuilder();
            ArrayList<CharSequence> list = null;
            int start = 0;
            for (int pos = 0; pos < size; pos++) {
                final char c = text.charAt(pos);
                if (c == ',') {
                    if (list == null) list = new ArrayList<CharSequence>();
                    if (sb.length() == 0) {
                        list.add(text.subSequence(start, pos));
                    } else {
                        list.add(sb.toString());
                        sb.setLength(0);
                    }
                    start = pos + 1;
                    continue;
                } else if (c == '\\') {
                    if (start == pos) {
                        // Skip escape character at the beginning of the value.
                        start++;
                        pos++;
                    } else {
                        if (start < pos && sb.length() == 0)
                            sb.append(text.subSequence(start, pos));
                        pos++;
                        if (pos < size)
                            sb.append(text.charAt(pos));
                    }
                } else if (sb.length() > 0) {
                    sb.append(c);
                }
            }
            if (list == null) {
                return new CharSequence[] { sb.length() > 0 ? sb : text.subSequence(start, size) };
            } else {
                list.add(sb.length() > 0 ? sb : text.subSequence(start, size));
                return list.toArray(new CharSequence[list.size()]);
            }
        }

        protected static int[] parseIntArray(TypedArray a, int index) {
            if (!a.hasValue(index))
                return null;
            TypedValue v = new TypedValue();
            a.getValue(index, v);
            if (v.type == TypedValue.TYPE_INT_DEC || v.type == TypedValue.TYPE_INT_HEX) {
                return new int[] { v.data };
            } else if (v.type == TypedValue.TYPE_STRING) {
                return parseCsvInt(v.string.toString());
            } else {
                return null;
            }
        }

        /* package */ static int[] parseCsvInt(String value) {
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
                values[count++] = Integer.parseInt(st.nextToken());
            }
            return values;
        }
    }

    private static class DeclaredKeyStyle extends EmptyKeyStyle {
        private final HashMap<Integer, Object> mAttributes = new HashMap<Integer, Object>();

        @Override
        public int[] getIntArray(TypedArray a, int index) {
            return a.hasValue(index)
                    ? super.getIntArray(a, index) : (int[])mAttributes.get(index);
        }

        @Override
        public CharSequence[] getTextArray(TypedArray a, int index) {
            return a.hasValue(index)
                    ? super.getTextArray(a, index) : (CharSequence[])mAttributes.get(index);
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
        public int getInt(TypedArray a, int index, int defaultValue) {
            final Integer value = (Integer)mAttributes.get(index);
            return super.getInt(a, index, (value != null) ? value : defaultValue);
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

        private void parseKeyStyleAttributes(TypedArray keyAttr) {
            // TODO: Currently not all Key attributes can be declared as style.
            readIntArray(keyAttr, R.styleable.Keyboard_Key_codes);
            readText(keyAttr, R.styleable.Keyboard_Key_keyLabel);
            readFlag(keyAttr, R.styleable.Keyboard_Key_keyLabelOption);
            readTextArray(keyAttr, R.styleable.Keyboard_Key_popupCharacters);
            readInt(keyAttr, R.styleable.Keyboard_Key_maxPopupKeyboardColumn);
            readText(keyAttr, R.styleable.Keyboard_Key_keyOutputText);
            readDrawable(keyAttr, R.styleable.Keyboard_Key_keyIcon);
            readDrawable(keyAttr, R.styleable.Keyboard_Key_iconPreview);
            readDrawable(keyAttr, R.styleable.Keyboard_Key_keyHintIcon);
            readDrawable(keyAttr, R.styleable.Keyboard_Key_shiftedIcon);
            readBoolean(keyAttr, R.styleable.Keyboard_Key_isModifier);
            readBoolean(keyAttr, R.styleable.Keyboard_Key_isSticky);
            readBoolean(keyAttr, R.styleable.Keyboard_Key_isRepeatable);
        }

        private void readDrawable(TypedArray a, int index) {
            if (a.hasValue(index))
                mAttributes.put(index, a.getDrawable(index));
        }

        private void readText(TypedArray a, int index) {
            if (a.hasValue(index))
                mAttributes.put(index, a.getText(index));
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

        private void readBoolean(TypedArray a, int index) {
            if (a.hasValue(index))
                mAttributes.put(index, a.getBoolean(index, false));
        }

        private void readIntArray(TypedArray a, int index) {
            final int[] value = parseIntArray(a, index);
            if (value != null)
                mAttributes.put(index, value);
        }

        private void readTextArray(TypedArray a, int index) {
            final CharSequence[] value = parseTextArray(a, index);
            if (value != null)
                mAttributes.put(index, value);
        }

        private void addParent(DeclaredKeyStyle parentStyle) {
            mAttributes.putAll(parentStyle.mAttributes);
        }
    }

    public void parseKeyStyleAttributes(TypedArray keyStyleAttr, TypedArray keyAttrs,
            XmlResourceParser parser) {
        String styleName = keyStyleAttr.getString(R.styleable.Keyboard_KeyStyle_styleName);
        if (DEBUG) Log.d(TAG, String.format("<%s styleName=%s />",
                KeyboardParser.TAG_KEY_STYLE, styleName));
        if (mStyles.containsKey(styleName))
            throw new ParseException("duplicate key style declared: " + styleName, parser);

        final DeclaredKeyStyle style = new DeclaredKeyStyle();
        if (keyStyleAttr.hasValue(R.styleable.Keyboard_KeyStyle_parentStyle)) {
            String parentStyle = keyStyleAttr.getString(
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
