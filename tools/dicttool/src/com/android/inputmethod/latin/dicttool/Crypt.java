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

import java.io.InputStream;
import java.io.OutputStream;

public class Crypt {
    private Crypt() {
        // This container class is not publicly instantiable.
    }

    public static OutputStream getCryptedStream(final OutputStream out) {
        // Encryption is not supported
        return out;
    }

    public static InputStream getDecryptedStream(final InputStream in) {
        // Decryption is not supported
        return in;
    }

    static public class Encrypter extends Dicttool.Command {
        public static final String COMMAND = "encrypt";

        public Encrypter() {
        }

        @Override
        public String getHelp() {
            return COMMAND + " <src_filename> <dst_filename>: Encrypts a file";
        }

        @Override
        public void run() {
            throw new UnsupportedOperationException();
        }
    }

    static public class Decrypter extends Dicttool.Command {
        public static final String COMMAND = "decrypt";

        public Decrypter() {
        }

        @Override
        public String getHelp() {
            return COMMAND + " <src_filename> <dst_filename>: Decrypts a file";
        }

        @Override
        public void run() {
            throw new UnsupportedOperationException();
        }
    }
}
