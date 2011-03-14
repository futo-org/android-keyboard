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

package com.android.inputmethod.latin;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;

/**
 * Helper class to get the address of a mmap'able dictionary file.
 */
class BinaryDictionaryGetter {

    /**
     * Used for Log actions from this class
     */
    private static final String TAG = BinaryDictionaryGetter.class.getSimpleName();

    // Prevents this from being instantiated
    private BinaryDictionaryGetter() {}

    /**
     * Returns a file address from a resource, or null if it cannot be opened.
     */
    private static AssetFileAddress loadFallbackResource(Context context, int fallbackResId) {
        final AssetFileDescriptor afd = context.getResources().openRawResourceFd(fallbackResId);
        if (afd == null) {
            Log.e(TAG, "Found the resource but cannot read it. Is it compressed? resId="
                    + fallbackResId);
            return null;
        }
        return AssetFileAddress.makeFromFileNameAndOffset(
                context.getApplicationInfo().sourceDir, afd.getStartOffset(), afd.getLength());
    }

    /**
     * Returns a file address for a given locale, trying relevant methods in order.
     *
     * Tries to get a binary dictionary from various sources, in order:
     * - Uses a private method of getting a private dictionary, as implemented by the
     *   PrivateBinaryDictionaryGetter class.
     * If that fails:
     * - Uses a content provider to get a public dictionary, as per the protocol described
     *   in BinaryDictionaryFileDumper.
     * If that fails:
     * - Gets a file name from the fallback resource passed as an argument.
     * If that fails:
     * - Returns null.
     * @return The address of a valid file, or null.
     * @throws FileNotFoundException if a dictionary provider returned a file name, but the
     *                               file cannot be found.
     * @throws IOException if there was an I/O problem reading or copying a file.
     */
    public static AssetFileAddress getDictionaryFile(Locale locale, Context context,
            int fallbackResId) {
        // Try first to query a private file signed the same way.
        final AssetFileAddress privateFile =
                PrivateBinaryDictionaryGetter.getDictionaryFile(locale, context);
        if (null != privateFile) {
            return privateFile;
        } else {
            try {
                // If that was no-go, try to find a publicly exported dictionary.
                final String fileName = BinaryDictionaryFileDumper.
                        getDictionaryFileFromContentProvider(locale, context);
                return AssetFileAddress.makeFromFileName(fileName);
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Unable to create dictionary file from provider for locale "
                        + locale.toString() + ": falling back to internal dictionary");
                return loadFallbackResource(context, fallbackResId);
            } catch (IOException e) {
                Log.e(TAG, "Unable to read source data for locale "
                        + locale.toString() + ": falling back to internal dictionary");
                return loadFallbackResource(context, fallbackResId);
            }
        }
    }
}
