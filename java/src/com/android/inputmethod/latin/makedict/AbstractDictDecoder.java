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

package com.android.inputmethod.latin.makedict;

import com.android.inputmethod.annotations.UsedForTesting;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.TreeMap;

/**
 * A base class of the binary dictionary decoder.
 */
public abstract class AbstractDictDecoder implements DictDecoder {
    private static final int SUCCESS = 0;
    private static final int ERROR_CANNOT_READ = 1;
    private static final int ERROR_WRONG_FORMAT = 2;

    @Override @UsedForTesting
    public int getTerminalPosition(final String word)
            throws IOException, UnsupportedFormatException {
        if (!isDictBufferOpen()) {
            openDictBuffer();
        }
        return BinaryDictIOUtils.getTerminalPosition(this, word);
    }

    @Override @UsedForTesting
    public void readUnigramsAndBigramsBinary(final TreeMap<Integer, String> words,
            final TreeMap<Integer, Integer> frequencies,
            final TreeMap<Integer, ArrayList<PendingAttribute>> bigrams)
            throws IOException, UnsupportedFormatException {
        if (!isDictBufferOpen()) {
            openDictBuffer();
        }
        BinaryDictIOUtils.readUnigramsAndBigramsBinary(this, words, frequencies, bigrams);
    }

    /**
     * Check whether the header contains the expected information. This is a no-error method,
     * that will return an error code and never throw a checked exception.
     * @return an error code, either ERROR_* or SUCCESS.
     */
    private int checkHeader() {
        try {
            readHeader();
        } catch (IOException e) {
            return ERROR_CANNOT_READ;
        } catch (UnsupportedFormatException e) {
            return ERROR_WRONG_FORMAT;
        }
        return SUCCESS;
    }

    @Override
    public boolean hasValidRawBinaryDictionary() {
        return checkHeader() == SUCCESS;
    }

    // Placeholder implementations below. These are actually unused.
    @Override
    public void openDictBuffer() throws FileNotFoundException, IOException,
            UnsupportedFormatException {
    }

    @Override
    public boolean isDictBufferOpen() {
        return false;
    }

    @Override
    public PtNodeInfo readPtNode(final int ptNodePos) {
        return null;
    }

    @Override
    public void setPosition(int newPos) {
    }

    @Override
    public int getPosition() {
        return 0;
    }

    @Override
    public int readPtNodeCount() {
        return 0;
    }
}
