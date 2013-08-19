/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.inputmethod.latin.utils;

import com.android.inputmethod.latin.makedict.BinaryDictDecoderUtils.DictBuffer;

/**
 * This class provides an implementation for the FusionDictionary buffer interface that is backed
 * by a simpled byte array. It allows to create a binary dictionary in memory.
 */
public final class ByteArrayDictBuffer implements DictBuffer {
    private byte[] mBuffer;
    private int mPosition;

    public ByteArrayDictBuffer(final byte[] buffer) {
        mBuffer = buffer;
        mPosition = 0;
    }

    @Override
    public int readUnsignedByte() {
        return mBuffer[mPosition++] & 0xFF;
    }

    @Override
    public int readUnsignedShort() {
        final int retval = readUnsignedByte();
        return (retval << 8) + readUnsignedByte();
    }

    @Override
    public int readUnsignedInt24() {
        final int retval = readUnsignedShort();
        return (retval << 8) + readUnsignedByte();
    }

    @Override
    public int readInt() {
        final int retval = readUnsignedShort();
        return (retval << 16) + readUnsignedShort();
    }

    @Override
    public int position() {
        return mPosition;
    }

    @Override
    public void position(int position) {
        mPosition = position;
    }

    @Override
    public void put(final byte b) {
        mBuffer[mPosition++] = b;
    }

    @Override
    public int limit() {
        return mBuffer.length - 1;
    }

    @Override
    public int capacity() {
        return mBuffer.length;
    }
}
