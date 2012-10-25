/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.latin.dicttool;

import com.android.inputmethod.latin.makedict.BinaryDictInputOutput;

import java.io.File;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * Class grouping utilities for offline dictionary making.
 *
 * Those should not be used on-device, essentially because they are quite
 * liberal about I/O and performance.
 */
public final class BinaryDictOffdeviceUtils {
    // Prefix and suffix are arbitrary, the values do not really matter
    private final static String PREFIX = "dicttool";
    private final static String SUFFIX = ".tmp";

    public final static String COMPRESSION = "compression";
    public final static String ENCRYPTION = "encryption";

    public static class DecoderChainSpec {
        ArrayList<String> mDecoderSpec = new ArrayList<String>();
        File mFile;
        public DecoderChainSpec addStep(final String stepDescription) {
            mDecoderSpec.add(stepDescription);
            return this;
        }
    }

    public static void copy(final InputStream input, final OutputStream output) throws IOException {
        final byte[] buffer = new byte[1000];
        final BufferedInputStream in = new BufferedInputStream(input);
        final BufferedOutputStream out = new BufferedOutputStream(output);
        for (int readBytes = in.read(buffer); readBytes >= 0; readBytes = in.read(buffer))
            output.write(buffer, 0, readBytes);
        in.close();
        out.close();
    }

    /**
     * Returns a decrypted/uncompressed binary dictionary.
     *
     * This will decrypt/uncompress any number of times as necessary until it finds the binary
     * dictionary signature, and copy the decoded file to a temporary place.
     * If this is not a binary dictionary, the method returns null.
     */
    public static DecoderChainSpec getRawBinaryDictionaryOrNull(final File src) {
        return getRawBinaryDictionaryOrNullInternal(new DecoderChainSpec(), src);
    }

    private static DecoderChainSpec getRawBinaryDictionaryOrNullInternal(
            final DecoderChainSpec spec, final File src) {
        // TODO: arrange for the intermediary files to be deleted
        if (BinaryDictInputOutput.isBinaryDictionary(src)) {
            spec.mFile = src;
            return spec;
        }
        // It's not a raw dictionary - try to see if it's compressed.
        final File uncompressedFile = tryGetUncompressedFile(src);
        if (null != uncompressedFile) {
            final DecoderChainSpec newSpec =
                    getRawBinaryDictionaryOrNullInternal(spec, uncompressedFile);
            if (null == newSpec) return null;
            return newSpec.addStep(COMPRESSION);
        }
        // It's not a compressed either - try to see if it's crypted.
        final File decryptedFile = tryGetDecryptedFile(src);
        if (null != decryptedFile) {
            final DecoderChainSpec newSpec =
                    getRawBinaryDictionaryOrNullInternal(spec, decryptedFile);
            if (null == newSpec) return null;
            return newSpec.addStep(ENCRYPTION);
        }
        return null;
    }

    /* Try to uncompress the file passed as an argument.
     *
     * If the file can be uncompressed, the uncompressed version is returned. Otherwise, null
     * is returned.
     */
    private static File tryGetUncompressedFile(final File src) {
        try {
            final File dst = File.createTempFile(PREFIX, SUFFIX);
            final FileOutputStream dstStream = new FileOutputStream(dst);
            copy(Compress.getUncompressedStream(new BufferedInputStream(new FileInputStream(src))),
                    new BufferedOutputStream(dstStream)); // #copy() closes the streams
            return dst;
        } catch (IOException e) {
            // Could not uncompress the file: presumably the file is simply not a compressed file
            return null;
        }
    }

    /* Try to decrypt the file passed as an argument.
     *
     * If the file can be decrypted, the decrypted version is returned. Otherwise, null
     * is returned.
     */
    private static File tryGetDecryptedFile(final File src) {
        try {
            final File dst = File.createTempFile(PREFIX, SUFFIX);
            final FileOutputStream dstStream = new FileOutputStream(dst);
            copy(Crypt.getDecryptedStream(new BufferedInputStream(new FileInputStream(src))),
                    dstStream); // #copy() closes the streams
            return dst;
        } catch (IOException e) {
            // Could not uncompress the file: presumably the file is simply not a compressed file
            return null;
        }
    }
}
