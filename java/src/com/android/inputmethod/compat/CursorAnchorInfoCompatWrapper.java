/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.inputmethod.compat;

import android.graphics.Matrix;
import android.graphics.RectF;

import com.android.inputmethod.annotations.UsedForTesting;

import java.lang.reflect.Method;

@UsedForTesting
public final class CursorAnchorInfoCompatWrapper {
    // Note that CursorAnchorInfo has been introduced in API level XX (Build.VERSION_CODE.LXX).
    private static Class<?> getCursorAnchorInfoClass() {
        try {
            return Class.forName("android.view.inputmethod.CursorAnchorInfo");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
    private static final Class<?> CLASS;
    private static final Method METHOD_GET_CHARACTER_RECT;
    private static final Method METHOD_GET_CHARACTER_RECT_FLAGS;
    private static final Method METHOD_GET_COMPOSING_TEXT;
    private static final Method METHOD_GET_COMPOSING_TEXT_START;
    private static final Method METHOD_GET_MATRIX;
    static {
        CLASS = getCursorAnchorInfoClass();
        METHOD_GET_CHARACTER_RECT = CompatUtils.getMethod(CLASS, "getCharacterRect", int.class);
        METHOD_GET_CHARACTER_RECT_FLAGS = CompatUtils.getMethod(CLASS, "getCharacterRectFlags",
                int.class);
        METHOD_GET_COMPOSING_TEXT = CompatUtils.getMethod(CLASS, "getComposingText");
        METHOD_GET_COMPOSING_TEXT_START = CompatUtils.getMethod(CLASS, "getComposingTextStart");
        METHOD_GET_MATRIX = CompatUtils.getMethod(CLASS, "getMatrix");
    }

    @UsedForTesting
    public static boolean isAvailable() {
        return CLASS != null;
    }

    public static final int CHARACTER_RECT_TYPE_MASK = 0x0f;

    /**
     * Type for {@link #CHARACTER_RECT_TYPE_MASK}: the editor did not specify any type of this
     * character. Editor authors should not use this flag.
     */
    public static final int CHARACTER_RECT_TYPE_UNSPECIFIED = 0;

    /**
     * Type for {@link #CHARACTER_RECT_TYPE_MASK}: the character is entirely visible.
     */
    public static final int CHARACTER_RECT_TYPE_FULLY_VISIBLE = 1;

    /**
     * Type for {@link #CHARACTER_RECT_TYPE_MASK}: some area of the character is invisible.
     */
    public static final int CHARACTER_RECT_TYPE_PARTIALLY_VISIBLE = 2;

    /**
     * Type for {@link #CHARACTER_RECT_TYPE_MASK}: the character is entirely invisible.
     */
    public static final int CHARACTER_RECT_TYPE_INVISIBLE = 3;

    /**
     * Type for {@link #CHARACTER_RECT_TYPE_MASK}: the editor gave up to calculate the rectangle
     * for this character. Input method authors should ignore the returned rectangle.
     */
    public static final int CHARACTER_RECT_TYPE_NOT_FEASIBLE = 4;

    private Object mInstance;

    private CursorAnchorInfoCompatWrapper(final Object instance) {
        mInstance = instance;
    }

    @UsedForTesting
    public static CursorAnchorInfoCompatWrapper fromObject(final Object instance) {
        if (!isAvailable()) {
            return new CursorAnchorInfoCompatWrapper(null);
        }
        return new CursorAnchorInfoCompatWrapper(instance);
    }

    private static final class FakeHolder {
        static CursorAnchorInfoCompatWrapper sInstance = new CursorAnchorInfoCompatWrapper(null);
    }

    @UsedForTesting
    public static CursorAnchorInfoCompatWrapper getFake() {
        return FakeHolder.sInstance;
    }

    public CharSequence getComposingText() {
        return (CharSequence) CompatUtils.invoke(mInstance, null, METHOD_GET_COMPOSING_TEXT);
    }

    private static int COMPOSING_TEXT_START_DEFAULT = -1;
    public int getComposingTextStart() {
        if (mInstance == null || METHOD_GET_COMPOSING_TEXT_START == null) {
            return COMPOSING_TEXT_START_DEFAULT;
        }
        return (int) CompatUtils.invoke(mInstance, null, METHOD_GET_COMPOSING_TEXT_START);
    }

    public Matrix getMatrix() {
        return (Matrix) CompatUtils.invoke(mInstance, null, METHOD_GET_MATRIX);
    }

    public RectF getCharacterRect(final int index) {
        return (RectF) CompatUtils.invoke(mInstance, null, METHOD_GET_CHARACTER_RECT, index);
    }

    public int getCharacterRectFlags(final int index) {
        if (mInstance == null || METHOD_GET_CHARACTER_RECT_FLAGS == null) {
            return CHARACTER_RECT_TYPE_UNSPECIFIED;
        }
        return (int) CompatUtils.invoke(mInstance, null, METHOD_GET_CHARACTER_RECT_FLAGS, index);
    }
}
