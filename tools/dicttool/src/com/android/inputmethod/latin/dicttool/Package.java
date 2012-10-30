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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

public class Package {
    private Package() {
        // This container class is not publicly instantiable.
    }

    static public class Packager extends Dicttool.Command {
        public static final String COMMAND = "package";

        public Packager() {
        }

        public String getHelp() {
            return COMMAND + " <src_filename> <dst_filename>: Package a file for distribution";
        }

        public void run() {
            // Not implemented yet
            throw new UnsupportedOperationException();
        }
    }

    static public class Unpackager extends Dicttool.Command {
        public static final String COMMAND = "unpackage";

        public Unpackager() {
        }

        public String getHelp() {
            return COMMAND + " <src_filename> <dst_filename>: Detects how a file is packaged and\n"
                    + "decrypts/uncompresses as necessary to produce a raw binary file.";
        }

        public void run() throws FileNotFoundException, IOException {
            if (mArgs.length != 2) {
                throw new RuntimeException("Too many/too few arguments for command " + COMMAND);
            }
            final BinaryDictOffdeviceUtils.DecoderChainSpec decodedSpec =
                    BinaryDictOffdeviceUtils.getRawBinaryDictionaryOrNull(new File(mArgs[0]));
            if (null == decodedSpec) {
                System.out.println(mArgs[0] + " does not seem to be a dictionary");
                return;
            }
            System.out.println("Packaging : " + decodedSpec.describeChain());
            System.out.println("Uncompressed size : " + decodedSpec.mFile.length());
            final FileOutputStream dstStream = new FileOutputStream(new File(mArgs[1]));
            BinaryDictOffdeviceUtils.copy(new BufferedInputStream(
                    new FileInputStream(decodedSpec.mFile)), new BufferedOutputStream(dstStream));
        }
    }
}
