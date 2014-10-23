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

import android.annotation.TargetApi;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Build;
import android.view.inputmethod.CursorAnchorInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A wrapper for {@link CursorAnchorInfo}, which has been introduced in API Level 21. You can use
 * this wrapper to avoid direct dependency on newly introduced types.
 */
public class CursorAnchorInfoCompatWrapper {

    /**
     * The insertion marker or character bounds have at least one visible region.
     */
    public static final int FLAG_HAS_VISIBLE_REGION = 0x01;

    /**
     * The insertion marker or character bounds have at least one invisible (clipped) region.
     */
    public static final int FLAG_HAS_INVISIBLE_REGION = 0x02;

    /**
     * The insertion marker or character bounds is placed at right-to-left (RTL) character.
     */
    public static final int FLAG_IS_RTL = 0x04;

    CursorAnchorInfoCompatWrapper() {
        // This class is not publicly instantiable.
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Nullable
    public static CursorAnchorInfoCompatWrapper wrap(@Nullable final CursorAnchorInfo instance) {
        if (BuildCompatUtils.EFFECTIVE_SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return null;
        }
        if (instance == null) {
            return null;
        }
        return new RealWrapper(instance);
    }

    public int getSelectionStart() {
        throw new UnsupportedOperationException("not supported.");
    }

    public int getSelectionEnd() {
        throw new UnsupportedOperationException("not supported.");
    }

    public CharSequence getComposingText() {
        throw new UnsupportedOperationException("not supported.");
    }

    public int getComposingTextStart() {
        throw new UnsupportedOperationException("not supported.");
    }

    public Matrix getMatrix() {
        throw new UnsupportedOperationException("not supported.");
    }

    @SuppressWarnings("unused")
    public RectF getCharacterBounds(final int index) {
        throw new UnsupportedOperationException("not supported.");
    }

    @SuppressWarnings("unused")
    public int getCharacterBoundsFlags(final int index) {
        throw new UnsupportedOperationException("not supported.");
    }

    public float getInsertionMarkerBaseline() {
        throw new UnsupportedOperationException("not supported.");
    }

    public float getInsertionMarkerBottom() {
        throw new UnsupportedOperationException("not supported.");
    }

    public float getInsertionMarkerHorizontal() {
        throw new UnsupportedOperationException("not supported.");
    }

    public float getInsertionMarkerTop() {
        throw new UnsupportedOperationException("not supported.");
    }

    public int getInsertionMarkerFlags() {
        throw new UnsupportedOperationException("not supported.");
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static final class RealWrapper extends CursorAnchorInfoCompatWrapper {

        @Nonnull
        private final CursorAnchorInfo mInstance;

        public RealWrapper(@Nonnull final CursorAnchorInfo info) {
            mInstance = info;
        }

        @Override
        public int getSelectionStart() {
            return mInstance.getSelectionStart();
        }

        @Override
        public int getSelectionEnd() {
            return mInstance.getSelectionEnd();
        }

        @Override
        public CharSequence getComposingText() {
            return mInstance.getComposingText();
        }

        @Override
        public int getComposingTextStart() {
            return mInstance.getComposingTextStart();
        }

        @Override
        public Matrix getMatrix() {
            return mInstance.getMatrix();
        }

        @Override
        public RectF getCharacterBounds(final int index) {
            return mInstance.getCharacterBounds(index);
        }

        @Override
        public int getCharacterBoundsFlags(final int index) {
            return mInstance.getCharacterBoundsFlags(index);
        }

        @Override
        public float getInsertionMarkerBaseline() {
            return mInstance.getInsertionMarkerBaseline();
        }

        @Override
        public float getInsertionMarkerBottom() {
            return mInstance.getInsertionMarkerBottom();
        }

        @Override
        public float getInsertionMarkerHorizontal() {
            return mInstance.getInsertionMarkerHorizontal();
        }

        @Override
        public float getInsertionMarkerTop() {
            return mInstance.getInsertionMarkerTop();
        }

        @Override
        public int getInsertionMarkerFlags() {
            return mInstance.getInsertionMarkerFlags();
        }
    }
}
