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

package com.android.inputmethod.keyboard.tools;

import java.io.PrintStream;

public class ArrayInitializerFormatter {
    private final PrintStream mOut;
    private final int mMaxWidth;
    private final String mIndent;

    private int mCurrentIndex = 0;
    private String mFixedElement;
    private final StringBuilder mBuffer = new StringBuilder();
    private int mBufferedLen;
    private int mBufferedIndex = Integer.MIN_VALUE;

    public ArrayInitializerFormatter(PrintStream out, int width, String indent) {
        mOut = out;
        mMaxWidth = width - indent.length();
        mIndent = indent;
    }

    public void flush() {
        if (mBuffer.length() == 0) {
            return;
        }
        final int lastIndex = mCurrentIndex - 1;
        if (mBufferedIndex == lastIndex) {
            mOut.format("%s/* %d */ %s\n", mIndent, mBufferedIndex, mBuffer);
        } else if (mBufferedIndex == lastIndex - 1) {
            final String[] elements = mBuffer.toString().split(" ");
            mOut.format("%s/* %d */ %s\n"
                    + "%s/* %d */ %s\n",
                    mIndent, mBufferedIndex, elements[0],
                    mIndent, lastIndex, elements[1]);
        } else {
            mOut.format("%s/* %d~ */\n"
                    + "%s%s\n"
                    + "%s/* ~%d */\n", mIndent, mBufferedIndex,
                    mIndent, mBuffer,
                    mIndent, lastIndex);
        }
        mBuffer.setLength(0);
        mBufferedLen = 0;
    }

    public void outCommentLines(String lines) {
        flush();
        mOut.print(lines);
        mFixedElement = null;
    }

    public void outElement(String element) {
        if (!element.equals(mFixedElement)) {
            flush();
            mBufferedIndex = mCurrentIndex;
        }
        final int nextLen = mBufferedLen + " ".length() + element.length();
        if (mBufferedLen != 0 && nextLen < mMaxWidth) {
            mBuffer.append(' ');
            mBuffer.append(element);
            mBufferedLen = nextLen;
        } else {
            if (mBufferedLen != 0) {
                mBuffer.append('\n');
                mBuffer.append(mIndent);
            }
            mBuffer.append(element);
            mBufferedLen = element.length();
        }
        mCurrentIndex++;
        mFixedElement = element;
    }
}
