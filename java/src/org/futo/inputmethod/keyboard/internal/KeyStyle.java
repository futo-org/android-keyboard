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

package org.futo.inputmethod.keyboard.internal;

import android.content.res.TypedArray;

import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class KeyStyle {
    private final KeyboardTextsSet mTextsSet;

    public abstract @Nullable String[] getStringArray(TypedArray a, int index, Function<String, String> stringMutator);
    public abstract @Nullable String getString(TypedArray a, int index, Function<String, String> stringMutator);
    public abstract int getInt(TypedArray a, int index, int defaultValue);
    public abstract int getFlags(TypedArray a, int index);

    protected KeyStyle(@Nonnull final KeyboardTextsSet textsSet) {
        mTextsSet = textsSet;
    }

    @Nullable
    protected String parseString(final TypedArray a, final int index, Function<String, String> stringMutator) {
        if (a.hasValue(index)) {
            String s = a.getString(index);
            if(stringMutator != null) s = stringMutator.apply(s);

            return mTextsSet.resolveTextReference(s);
        }
        return null;
    }

    @Nullable
    protected String[] parseStringArray(final TypedArray a, final int index, Function<String, String> stringMutator) {
        if (a.hasValue(index)) {
            String s = a.getString(index);
            if(stringMutator != null) s = stringMutator.apply(s);

            final String text = mTextsSet.resolveTextReference(s);
            return MoreKeySpec.splitKeySpecs(text);
        }
        return null;
    }
}
