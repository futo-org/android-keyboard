/**
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Compress {

    private static OutputStream getCompressedStream(final OutputStream out)
        throws java.io.IOException {
        return new GZIPOutputStream(out);
    }

    private static InputStream getUncompressedStream(final InputStream in) throws IOException {
        return new GZIPInputStream(in);
    }

    public static void copy(final InputStream input, final OutputStream output) throws IOException {
        final byte[] buffer = new byte[1000];
        for (int readBytes = input.read(buffer); readBytes >= 0; readBytes = input.read(buffer))
            output.write(buffer, 0, readBytes);
        input.close();
        output.close();
    }

    static public class Compressor extends Dicttool.Command {
        public static final String COMMAND = "compress";
        private static final String SUFFIX = ".compressed";

        public Compressor() {
        }

        public String getHelp() {
            return "compress <filename>: Compresses a file using gzip compression";
        }

        public int getArity() {
            return 1;
        }

        public void run() throws IOException {
            final String inFilename = mArgs[0];
            final String outFilename = inFilename + SUFFIX;
            final FileInputStream input = new FileInputStream(new File(inFilename));
            final FileOutputStream output = new FileOutputStream(new File(outFilename));
            copy(input, new GZIPOutputStream(output));
        }
    }

    static public class Uncompressor extends Dicttool.Command {
        public static final String COMMAND = "uncompress";
        private static final String SUFFIX = ".uncompressed";

        public Uncompressor() {
        }

        public String getHelp() {
            return "uncompress <filename>: Uncompresses a file compressed with gzip compression";
        }

        public int getArity() {
            return 1;
        }

        public void run() throws IOException {
            final String inFilename = mArgs[0];
            final String outFilename = inFilename + SUFFIX;
            final FileInputStream input = new FileInputStream(new File(inFilename));
            final FileOutputStream output = new FileOutputStream(new File(outFilename));
            copy(new GZIPInputStream(input), output);
        }
    }
}
