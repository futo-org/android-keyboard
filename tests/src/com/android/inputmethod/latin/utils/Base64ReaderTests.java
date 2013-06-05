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

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.io.EOFException;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;

@SmallTest
public class Base64ReaderTests extends AndroidTestCase {
    private static final String EMPTY_STRING = "";
    private static final String INCOMPLETE_CHAR1 = "Q";
    // Encode 'A'.
    private static final String INCOMPLETE_CHAR2 = "QQ";
    // Encode 'A', 'B'
    private static final String INCOMPLETE_CHAR3 = "QUI";
    // Encode 'A', 'B', 'C'
    private static final String COMPLETE_CHAR4 = "QUJD";
    private static final String ALL_BYTE_PATTERN =
            "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gISIj\n"
            + "JCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+P0BBQkNERUZH\n"
            + "SElKS0xNTk9QUVJTVFVWV1hZWltcXV5fYGFiY2RlZmdoaWpr\n"
            + "bG1ub3BxcnN0dXZ3eHl6e3x9fn+AgYKDhIWGh4iJiouMjY6P\n"
            + "kJGSk5SVlpeYmZqbnJ2en6ChoqOkpaanqKmqq6ytrq+wsbKz\n"
            + "tLW2t7i5uru8vb6/wMHCw8TFxsfIycrLzM3Oz9DR0tPU1dbX\n"
            + "2Nna29zd3t/g4eLj5OXm5+jp6uvs7e7v8PHy8/T19vf4+fr7\n"
            + "/P3+/w==";

    public void test0CharInt8() {
        final Base64Reader reader = new Base64Reader(
                new LineNumberReader(new StringReader(EMPTY_STRING)));
        try {
            reader.readUint8();
            fail("0 char");
        } catch (final EOFException e) {
            assertEquals("0 char", 0, reader.getByteCount());
        } catch (final IOException e) {
            fail("IOException: " + e);
        }
    }

    public void test1CharInt8() {
        final Base64Reader reader = new Base64Reader(
                new LineNumberReader(new StringReader(INCOMPLETE_CHAR1)));
        try {
            reader.readUint8();
            fail("1 char");
        } catch (final EOFException e) {
            assertEquals("1 char", 0, reader.getByteCount());
        } catch (final IOException e) {
            fail("IOException: " + e);
        }
    }

    public void test2CharsInt8() {
        final Base64Reader reader = new Base64Reader(
                new LineNumberReader(new StringReader(INCOMPLETE_CHAR2)));
        try {
            final int v1 = reader.readUint8();
            assertEquals("2 chars pos 0", 'A', v1);
            reader.readUint8();
            fail("2 chars");
        } catch (final EOFException e) {
            assertEquals("2 chars", 1, reader.getByteCount());
        } catch (final IOException e) {
            fail("IOException: " + e);
        }
    }

    public void test3CharsInt8() {
        final Base64Reader reader = new Base64Reader(
                new LineNumberReader(new StringReader(INCOMPLETE_CHAR3)));
        try {
            final int v1 = reader.readUint8();
            assertEquals("3 chars pos 0", 'A', v1);
            final int v2 = reader.readUint8();
            assertEquals("3 chars pos 1", 'B', v2);
            reader.readUint8();
            fail("3 chars");
        } catch (final EOFException e) {
            assertEquals("3 chars", 2, reader.getByteCount());
        } catch (final IOException e) {
            fail("IOException: " + e);
        }
    }

    public void test4CharsInt8() {
        final Base64Reader reader = new Base64Reader(
                new LineNumberReader(new StringReader(COMPLETE_CHAR4)));
        try {
            final int v1 = reader.readUint8();
            assertEquals("4 chars pos 0", 'A', v1);
            final int v2 = reader.readUint8();
            assertEquals("4 chars pos 1", 'B', v2);
            final int v3 = reader.readUint8();
            assertEquals("4 chars pos 2", 'C', v3);
            reader.readUint8();
            fail("4 chars");
        } catch (final EOFException e) {
            assertEquals("4 chars", 3, reader.getByteCount());
        } catch (final IOException e) {
            fail("IOException: " + e);
        }
    }

    public void testAllBytePatternInt8() {
        final Base64Reader reader = new Base64Reader(
                new LineNumberReader(new StringReader(ALL_BYTE_PATTERN)));
        try {
            for (int i = 0; i <= 0xff; i++) {
                final int v = reader.readUint8();
                assertEquals("value: all byte pattern: pos " + i, i, v);
                assertEquals("count: all byte pattern: pos " + i, i + 1, reader.getByteCount());
            }
        } catch (final EOFException e) {
            assertEquals("all byte pattern", 256, reader.getByteCount());
        } catch (final IOException e) {
            fail("IOException: " + e);
        }
    }

    public void test0CharInt16() {
        final Base64Reader reader = new Base64Reader(
                new LineNumberReader(new StringReader(EMPTY_STRING)));
        try {
            reader.readInt16();
            fail("0 char");
        } catch (final EOFException e) {
            assertEquals("0 char", 0, reader.getByteCount());
        } catch (final IOException e) {
            fail("IOException: " + e);
        }
    }

    public void test1CharInt16() {
        final Base64Reader reader = new Base64Reader(
                new LineNumberReader(new StringReader(INCOMPLETE_CHAR1)));
        try {
            reader.readInt16();
            fail("1 char");
        } catch (final EOFException e) {
            assertEquals("1 char", 0, reader.getByteCount());
        } catch (final IOException e) {
            fail("IOException: " + e);
        }
    }

    public void test2CharsInt16() {
        final Base64Reader reader = new Base64Reader(
                new LineNumberReader(new StringReader(INCOMPLETE_CHAR2)));
        try {
            reader.readInt16();
            fail("2 chars");
        } catch (final EOFException e) {
            assertEquals("2 chars", 1, reader.getByteCount());
        } catch (final IOException e) {
            fail("IOException: " + e);
        }
    }

    public void test3CharsInt16() {
        final Base64Reader reader = new Base64Reader(
                new LineNumberReader(new StringReader(INCOMPLETE_CHAR3)));
        try {
            final short v1 = reader.readInt16();
            assertEquals("3 chars pos 0", 'A' << 8 | 'B', v1);
            reader.readInt16();
            fail("3 chars");
        } catch (final EOFException e) {
            assertEquals("3 chars", 2, reader.getByteCount());
        } catch (final IOException e) {
            fail("IOException: " + e);
        }
    }

    public void test4CharsInt16() {
        final Base64Reader reader = new Base64Reader(
                new LineNumberReader(new StringReader(COMPLETE_CHAR4)));
        try {
            final short v1 = reader.readInt16();
            assertEquals("4 chars pos 0", 'A' << 8 | 'B', v1);
            reader.readInt16();
            fail("4 chars");
        } catch (final EOFException e) {
            assertEquals("4 chars", 3, reader.getByteCount());
        } catch (final IOException e) {
            fail("IOException: " + e);
        }
    }

    public void testAllBytePatternInt16() {
        final Base64Reader reader = new Base64Reader(
                new LineNumberReader(new StringReader(ALL_BYTE_PATTERN)));
        try {
            for (int i = 0; i <= 0xff; i += 2) {
                final short v = reader.readInt16();
                final short expected = (short)(i << 8 | (i + 1));
                assertEquals("value: all byte pattern: pos " + i, expected, v);
                assertEquals("count: all byte pattern: pos " + i, i + 2, reader.getByteCount());
            }
        } catch (final EOFException e) {
            assertEquals("all byte pattern", 256, reader.getByteCount());
        } catch (final IOException e) {
            fail("IOException: " + e);
        }
    }
}
