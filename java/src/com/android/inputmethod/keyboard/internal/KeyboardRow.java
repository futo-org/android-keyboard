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

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.Xml;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.ResourceUtils;

import org.xmlpull.v1.XmlPullParser;

/**
 * Container for keys in the keyboard. All keys in a row are at the same Y-coordinate.
 * Some of the key size defaults can be overridden per row from what the {@link Keyboard}
 * defines.
 */
public final class KeyboardRow {
    // keyWidth enum constants
    private static final int KEYWIDTH_NOT_ENUM = 0;
    private static final int KEYWIDTH_FILL_RIGHT = -1;

    private final KeyboardParams mParams;
    /** Default width of a key in this row. */
    private float mDefaultKeyWidth;
    /** Default height of a key in this row. */
    public final int mRowHeight;
    /** Default keyLabelFlags in this row. */
    private int mDefaultKeyLabelFlags;
    /** Default backgroundType for this row */
    private int mDefaultBackgroundType;

    private final int mCurrentY;
    // Will be updated by {@link Key}'s constructor.
    private float mCurrentX;

    public KeyboardRow(final Resources res, final KeyboardParams params, final XmlPullParser parser,
            final int y) {
        mParams = params;
        final TypedArray keyboardAttr = res.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.Keyboard);
        mRowHeight = (int)ResourceUtils.getDimensionOrFraction(keyboardAttr,
                R.styleable.Keyboard_rowHeight,
                params.mBaseHeight, params.mDefaultRowHeight);
        keyboardAttr.recycle();
        final TypedArray keyAttr = res.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.Keyboard_Key);
        mDefaultKeyWidth = keyAttr.getFraction(R.styleable.Keyboard_Key_keyWidth,
                params.mBaseWidth, params.mBaseWidth, params.mDefaultKeyWidth);
        mDefaultBackgroundType = keyAttr.getInt(R.styleable.Keyboard_Key_backgroundType,
                Key.BACKGROUND_TYPE_NORMAL);
        keyAttr.recycle();

        // TODO: Initialize this with <Row> attribute as backgroundType is done.
        mDefaultKeyLabelFlags = 0;
        mCurrentY = y;
        mCurrentX = 0.0f;
    }

    public float getDefaultKeyWidth() {
        return mDefaultKeyWidth;
    }

    public void setDefaultKeyWidth(final float defaultKeyWidth) {
        mDefaultKeyWidth = defaultKeyWidth;
    }

    public int getDefaultKeyLabelFlags() {
        return mDefaultKeyLabelFlags;
    }

    public void setDefaultKeyLabelFlags(final int keyLabelFlags) {
        mDefaultKeyLabelFlags = keyLabelFlags;
    }

    public int getDefaultBackgroundType() {
        return mDefaultBackgroundType;
    }

    public void setDefaultBackgroundType(final int backgroundType) {
        mDefaultBackgroundType = backgroundType;
    }

    public void setXPos(final float keyXPos) {
        mCurrentX = keyXPos;
    }

    public void advanceXPos(final float width) {
        mCurrentX += width;
    }

    public int getKeyY() {
        return mCurrentY;
    }

    public float getKeyX(final TypedArray keyAttr) {
        if (keyAttr.hasValue(R.styleable.Keyboard_Key_keyXPos)) {
            final float keyXPos = keyAttr.getFraction(R.styleable.Keyboard_Key_keyXPos,
                    mParams.mBaseWidth, mParams.mBaseWidth, 0);
            if (keyXPos < 0) {
                // If keyXPos is negative, the actual x-coordinate will be
                // keyboardWidth + keyXPos.
                // keyXPos shouldn't be less than mCurrentX because drawable area for this
                // key starts at mCurrentX. Or, this key will overlaps the adjacent key on
                // its left hand side.
                final int keyboardRightEdge = mParams.mOccupiedWidth - mParams.mRightPadding;
                return Math.max(keyXPos + keyboardRightEdge, mCurrentX);
            } else {
                return keyXPos + mParams.mLeftPadding;
            }
        }
        return mCurrentX;
    }

    public float getKeyWidth(final TypedArray keyAttr) {
        return getKeyWidth(keyAttr, mCurrentX);
    }

    public float getKeyWidth(final TypedArray keyAttr, final float keyXPos) {
        final int widthType = ResourceUtils.getEnumValue(keyAttr,
                R.styleable.Keyboard_Key_keyWidth, KEYWIDTH_NOT_ENUM);
        switch (widthType) {
        case KEYWIDTH_FILL_RIGHT:
            // If keyWidth is fillRight, the actual key width will be determined to fill
            // out the area up to the right edge of the keyboard.
            final int keyboardRightEdge = mParams.mOccupiedWidth - mParams.mRightPadding;
            return keyboardRightEdge - keyXPos;
        default: // KEYWIDTH_NOT_ENUM
            return keyAttr.getFraction(R.styleable.Keyboard_Key_keyWidth,
                    mParams.mBaseWidth, mParams.mBaseWidth, mDefaultKeyWidth);
        }
    }
}
