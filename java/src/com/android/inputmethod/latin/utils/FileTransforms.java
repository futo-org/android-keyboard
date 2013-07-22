/*
 * Copyright (C) 2011 The Android Open Source Project
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;

public final class FileTransforms {
    public static OutputStream getCryptedStream(OutputStream out) {
        // Crypt the stream.
        return out;
    }

    public static InputStream getDecryptedStream(InputStream in) {
        // Decrypt the stream.
        return in;
    }

    public static InputStream getUncompressedStream(InputStream in) throws IOException {
        return new GZIPInputStream(in);
    }
}
