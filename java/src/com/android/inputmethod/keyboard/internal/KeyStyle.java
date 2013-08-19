/*
 * Copyright (C) 2012 The Android Open Source Project
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

public abstract class KeyStyle {
    private final KeyboardTextsSet mTextsSet;

    public abstract String[] getStringArray(TypedArray a, int index);
    public abstract String getString(TypedArray a, int index);
    public abstract int getInt(TypedArray a, int index, int defaultValue);
    public abstract int getFlags(TypedArray a, int index);

    protected KeyStyle(final KeyboardTextsSet textsSet) {
        mTextsSet = textsSet;
    }

    protected String parseString(final TypedArray a, final int index) {
        if (a.hasValue(index)) {
            return KeySpecParser.resolveTextReference(a.getString(index), mTextsSet);
        }
        return null;
    }

    protected String[] parseStringArray(final TypedArray a, final int index) {
        if (a.hasValue(index)) {
            final String text = KeySpecParser.resolveTextReference(a.getString(index), mTextsSet);
            return KeySpecParser.splitKeySpecs(text);
        }
        return null;
    }
}
