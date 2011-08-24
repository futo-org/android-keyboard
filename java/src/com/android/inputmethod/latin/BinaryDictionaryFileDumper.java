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

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Group class for static methods to help with creation and getting of the binary dictionary
 * file from the dictionary provider
 */
public class BinaryDictionaryFileDumper {
    private static final String TAG = BinaryDictionaryFileDumper.class.getSimpleName();

    /**
     * The size of the temporary buffer to copy files.
     */
    static final int FILE_READ_BUFFER_SIZE = 1024;

    private static final String DICTIONARY_PROJECTION[] = { "id" };

    // Prevents this class to be accidentally instantiated.
    private BinaryDictionaryFileDumper() {
    }

    /**
     * Return for a given locale or dictionary id the provider URI to get the dictionary.
     */
    private static Uri getProviderUri(String path) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(BinaryDictionary.DICTIONARY_PACK_AUTHORITY).appendPath(
                        path).build();
    }

    /**
     * Queries a content provider for the list of word lists for a specific locale
     * available to copy into Latin IME.
     */
    private static List<String> getWordListIds(final Locale locale, final Context context) {
        final ContentResolver resolver = context.getContentResolver();
        final Uri dictionaryPackUri = getProviderUri(locale.toString());

        final Cursor c = resolver.query(dictionaryPackUri, DICTIONARY_PROJECTION, null, null, null);
        if (null == c) return Collections.<String>emptyList();
        if (c.getCount() <= 0 || !c.moveToFirst()) {
            c.close();
            return Collections.<String>emptyList();
        }

        final List<String> list = new ArrayList<String>();
        do {
            final String id = c.getString(0);
            if (TextUtils.isEmpty(id)) continue;
            list.add(id);
        } while (c.moveToNext());
        c.close();
        return list;
    }

    /**
     * Caches a word list the id of which is passed as an argument.
     */
    private static AssetFileAddress cacheWordList(final String id, final Locale locale,
            final ContentResolver resolver, final Context context) {
        final Uri wordListUri = getProviderUri(id);
        try {
            final AssetFileDescriptor afd = resolver.openAssetFileDescriptor(wordListUri, "r");
            if (null == afd) return null;
            final String fileName = copyFileTo(afd.createInputStream(),
                    BinaryDictionaryGetter.getCacheFileName(id, locale, context));
            afd.close();
            if (0 >= resolver.delete(wordListUri, null, null)) {
                // I'd rather not print the word list ID to the log out of security concerns
                Log.e(TAG, "Could not have the dictionary pack delete a word list");
            }
            return AssetFileAddress.makeFromFileName(fileName);
        } catch (FileNotFoundException e) {
            // This may only come from openAssetFileDescriptor
            return null;
        } catch (IOException e) {
            // Can't read the file for some reason.
            Log.e(TAG, "Cannot read a word list from the dictionary pack : " + e);
        }
        return null;
    }

    /**
     * Queries a content provider for word list data for some locale and cache the returned files
     *
     * This will query a content provider for word list data for a given locale, and copy the
     * files locally so that they can be mmap'ed. This may overwrite previously cached word lists
     * with newer versions if a newer version is made available by the content provider.
     * @returns the addresses of the word list files, or null if no data could be obtained.
     * @throw FileNotFoundException if the provider returns non-existent data.
     * @throw IOException if the provider-returned data could not be read.
     */
    public static List<AssetFileAddress> cacheWordListsFromContentProvider(final Locale locale,
            final Context context) {
        final ContentResolver resolver = context.getContentResolver();
        final List<String> idList = getWordListIds(locale, context);
        final List<AssetFileAddress> fileAddressList = new ArrayList<AssetFileAddress>();
        for (String id : idList) {
            final AssetFileAddress afd = cacheWordList(id, locale, resolver, context);
            if (null != afd) {
                fileAddressList.add(afd);
            }
        }
        return fileAddressList;
    }

    /**
     * Accepts a resource number as dictionary data for some locale and returns the name of a file.
     *
     * This will make the resource the cached dictionary for this locale, overwriting any previous
     * cached data.
     */
    public static String getDictionaryFileFromResource(int resource, Locale locale,
            Context context) throws FileNotFoundException, IOException {
        final Resources res = context.getResources();
        final Locale savedLocale = Utils.setSystemLocale(res, locale);
        final InputStream stream = res.openRawResource(resource);
        Utils.setSystemLocale(res, savedLocale);
        return copyFileTo(stream,
                BinaryDictionaryGetter.getCacheFileName(Integer.toString(resource),
                        locale, context));
    }

    /**
     * Copies the data in an input stream to a target file, creating the file if necessary and
     * overwriting it if it already exists.
     * @param input the stream to be copied.
     * @param outputFileName the name of a file to copy the data to. It is created if necessary.
     */
    private static String copyFileTo(final InputStream input, final String outputFileName)
            throws FileNotFoundException, IOException {
        final byte[] buffer = new byte[FILE_READ_BUFFER_SIZE];
        final FileOutputStream output = new FileOutputStream(outputFileName);
        for (int readBytes = input.read(buffer); readBytes >= 0; readBytes = input.read(buffer))
            output.write(buffer, 0, readBytes);
        input.close();
        return outputFileName;
    }
}
