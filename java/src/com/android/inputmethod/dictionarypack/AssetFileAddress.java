/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.dictionarypack;

import java.io.File;

/**
 * Immutable class to hold the address of an asset.
 * As opposed to a normal file, an asset is usually represented as a contiguous byte array in
 * the package file. Open it correctly thus requires the name of the package it is in, but
 * also the offset in the file and the length of this data. This class encapsulates these three.
 */
final class AssetFileAddress {
    public final String mFilename;
    public final long mOffset;
    public final long mLength;

    public AssetFileAddress(final String filename, final long offset, final long length) {
        mFilename = filename;
        mOffset = offset;
        mLength = length;
    }

    /**
     * Makes an AssetFileAddress. This may return null.
     *
     * @param filename the filename.
     * @return the address, or null if the file does not exist or the parameters are not valid.
     */
    public static AssetFileAddress makeFromFileName(final String filename) {
        if (null == filename) return null;
        final File f = new File(filename);
        if (!f.isFile()) return null;
        return new AssetFileAddress(filename, 0l, f.length());
    }

    /**
     * Makes an AssetFileAddress. This may return null.
     *
     * @param filename the filename.
     * @param offset the offset.
     * @param length the length.
     * @return the address, or null if the file does not exist or the parameters are not valid.
     */
    public static AssetFileAddress makeFromFileNameAndOffset(final String filename,
            final long offset, final long length) {
        if (null == filename) return null;
        final File f = new File(filename);
        if (!f.isFile()) return null;
        return new AssetFileAddress(filename, offset, length);
    }
}
