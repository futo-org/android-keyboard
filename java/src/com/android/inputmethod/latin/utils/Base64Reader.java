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

import com.android.inputmethod.annotations.UsedForTesting;

import java.io.EOFException;
import java.io.IOException;
import java.io.LineNumberReader;

@UsedForTesting
public class Base64Reader {
    private final LineNumberReader mReader;

    private String mLine;
    private int mCharPos;
    private int mByteCount;

    @UsedForTesting
    public Base64Reader(final LineNumberReader reader) {
        mReader = reader;
        reset();
    }

    @UsedForTesting
    public void reset() {
        mLine = null;
        mCharPos = 0;
        mByteCount = 0;
    }

    @UsedForTesting
    public int getLineNumber() {
        return mReader.getLineNumber();
    }

    @UsedForTesting
    public int getByteCount() {
        return mByteCount;
    }

    private void fillBuffer() throws IOException {
        if (mLine == null || mCharPos >= mLine.length()) {
            mLine = mReader.readLine();
            mCharPos = 0;
        }
        if (mLine == null) {
            throw new EOFException();
        }
    }

    private int peekUint8() throws IOException {
        fillBuffer();
        final char c = mLine.charAt(mCharPos);
        if (c >= 'A' && c <= 'Z')
            return c - 'A' + 0;
        if (c >= 'a' && c <= 'z')
            return c - 'a' + 26;
        if (c >= '0' && c <= '9')
            return c - '0' + 52;
        if (c == '+')
            return 62;
        if (c == '/')
            return 63;
        if (c == '=')
            return 0;
        throw new RuntimeException("Unknown character '" + c + "' in base64 at line "
                + mReader.getLineNumber());
    }

    private int getUint8() throws IOException {
        final int value = peekUint8();
        mCharPos++;
        return value;
    }

    @UsedForTesting
    public int readUint8() throws IOException {
        final int value1, value2;
        switch (mByteCount % 3) {
        case 0:
            value1 = getUint8() << 2;
            value2 = value1 | (peekUint8() >> 4);
            break;
        case 1:
            value1 = (getUint8() & 0x0f) << 4;
            value2 = value1 | (peekUint8() >> 2);
            break;
        default:
            value1 = (getUint8() & 0x03) << 6;
            value2 = value1 | getUint8();
            break;
        }
        mByteCount++;
        return value2;
    }

    @UsedForTesting
    public short readInt16() throws IOException {
        final int data = readUint8() << 8;
        return (short)(data | readUint8());
    }
}
