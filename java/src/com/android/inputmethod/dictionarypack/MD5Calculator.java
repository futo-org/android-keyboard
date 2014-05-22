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

package com.android.inputmethod.dictionarypack;

import java.io.InputStream;
import java.io.IOException;
import java.security.MessageDigest;

public final class MD5Calculator {
    private MD5Calculator() {} // This helper class is not instantiable

    public static String checksum(final InputStream in) throws IOException {
        // This code from the Android documentation for MessageDigest. Nearly verbatim.
        MessageDigest digester;
        try {
            digester = MessageDigest.getInstance("MD5");
        } catch (java.security.NoSuchAlgorithmException e) {
            return null; // Platform does not support MD5 : can't check, so return null
        }
        final byte[] bytes = new byte[8192];
        int byteCount;
        while ((byteCount = in.read(bytes)) > 0) {
            digester.update(bytes, 0, byteCount);
        }
        final byte[] digest = digester.digest();
        final StringBuilder s = new StringBuilder();
        for (int i = 0; i < digest.length; ++i) {
            s.append(String.format("%1$02x", digest[i]));
        }
        return s.toString();
    }
}
