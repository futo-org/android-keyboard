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

import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
* Class grouping utilities for offline dictionary making.
*
* Those should not be used on-device, essentially because they are quite
* liberal about I/O and performance.
*/
public class BinaryDictOffdeviceUtils {
    public static void copy(final InputStream input, final OutputStream output) throws IOException {
        final byte[] buffer = new byte[1000];
        final BufferedInputStream in = new BufferedInputStream(input);
        final BufferedOutputStream out = new BufferedOutputStream(output);
        for (int readBytes = in.read(buffer); readBytes >= 0; readBytes = in.read(buffer))
            output.write(buffer, 0, readBytes);
        in.close();
        out.close();
    }
}
