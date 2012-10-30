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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Compress {
    private Compress() {
        // This container class is not publicly instantiable.
    }

    public static OutputStream getCompressedStream(final OutputStream out)
        throws java.io.IOException {
        return new GZIPOutputStream(out);
    }

    public static InputStream getUncompressedStream(final InputStream in) throws IOException {
        return new GZIPInputStream(in);
    }

    static public class Compressor extends Dicttool.Command {
        public static final String COMMAND = "compress";
        public static final String STDIN_OR_STDOUT = "-";

        public Compressor() {
        }

        @Override
        public String getHelp() {
            return COMMAND + " <src_filename> <dst_filename>: "
                    + "Compresses a file using gzip compression";
        }

        @Override
        public void run() throws IOException {
            if (mArgs.length > 2) {
                throw new RuntimeException("Too many arguments for command " + COMMAND);
            }
            final String inFilename = mArgs.length >= 1 ? mArgs[0] : STDIN_OR_STDOUT;
            final String outFilename = mArgs.length >= 2 ? mArgs[1] : STDIN_OR_STDOUT;
            final InputStream input = inFilename.equals(STDIN_OR_STDOUT) ? System.in
                    : new BufferedInputStream(new FileInputStream(new File(inFilename)));
            final OutputStream output = outFilename.equals(STDIN_OR_STDOUT) ? System.out
                    : new BufferedOutputStream(new FileOutputStream(new File(outFilename)));
            BinaryDictOffdeviceUtils.copy(input, new GZIPOutputStream(output));
        }
    }

    static public class Uncompressor extends Dicttool.Command {
        public static final String COMMAND = "uncompress";
        public static final String STDIN_OR_STDOUT = "-";

        public Uncompressor() {
        }

        @Override
        public String getHelp() {
            return COMMAND + " <src_filename> <dst_filename>: "
                    + "Uncompresses a file compressed with gzip compression";
        }

        @Override
        public void run() throws IOException {
            if (mArgs.length > 2) {
                throw new RuntimeException("Too many arguments for command " + COMMAND);
            }
            final String inFilename = mArgs.length >= 1 ? mArgs[0] : STDIN_OR_STDOUT;
            final String outFilename = mArgs.length >= 2 ? mArgs[1] : STDIN_OR_STDOUT;
            final InputStream input = inFilename.equals(STDIN_OR_STDOUT) ? System.in
                    : new BufferedInputStream(new FileInputStream(new File(inFilename)));
            final OutputStream output = outFilename.equals(STDIN_OR_STDOUT) ? System.out
                    : new BufferedOutputStream(new FileOutputStream(new File(outFilename)));
            BinaryDictOffdeviceUtils.copy(new GZIPInputStream(input), output);
        }
    }
}
