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

import com.android.inputmethod.latin.makedict.FormatSpec.FormatOptions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * An implementation of DictEncoder for version 3 binary dictionary.
 */
public class Ver3DictEncoder implements DictEncoder {

    private final File mDictFile;
    private OutputStream mOutStream;

    public Ver3DictEncoder(final File dictFile) {
        mDictFile = dictFile;
        mOutStream = null;
    }

    // This constructor is used only by BinaryDictOffdeviceUtilsTests.
    // If you want to use this in the production code, you should consider keeping consistency of
    // the interface of Ver3DictDecoder by using factory.
    public Ver3DictEncoder(final OutputStream outStream) {
        mDictFile = null;
        mOutStream = outStream;
    }

    private void openStream() throws FileNotFoundException {
        mOutStream = new FileOutputStream(mDictFile);
    }

    private void close() throws IOException {
        if (mOutStream != null) {
            mOutStream.close();
            mOutStream = null;
        }
    }

    @Override
    public void writeDictionary(FusionDictionary dict, FormatOptions formatOptions)
            throws IOException, UnsupportedFormatException {
        if (mOutStream == null) {
            openStream();
        }
        BinaryDictEncoderUtils.writeDictionaryHeader(mOutStream, dict, formatOptions);
        BinaryDictEncoderUtils.writeDictionaryBody(mOutStream, dict, formatOptions);
        close();
    }
}
