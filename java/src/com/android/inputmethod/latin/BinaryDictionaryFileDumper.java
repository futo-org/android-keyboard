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

import java.io.File;
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
     * Escapes a string for any characters that may be suspicious for a file or directory name.
     *
     * Concretely this does a sort of URL-encoding except it will encode everything that's not
     * alphanumeric or underscore. (true URL-encoding leaves alone characters like '*', which
     * we cannot allow here)
     */
    // TODO: create a unit test for this method
    private static String replaceFileNameDangerousCharacters(String name) {
        // This assumes '%' is fully available as a non-separator, normal
        // character in a file name. This is probably true for all file systems.
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); ++i) {
            final int codePoint = name.codePointAt(i);
            if (Character.isLetterOrDigit(codePoint) || '_' == codePoint) {
                sb.appendCodePoint(codePoint);
            } else {
                sb.append('%');
                sb.append(Integer.toHexString(codePoint));
            }
        }
        return sb.toString();
    }

    /**
     * Find out the cache directory associated with a specific locale.
     */
    private static String getCacheDirectoryForLocale(Locale locale, Context context) {
        final String relativeDirectoryName = replaceFileNameDangerousCharacters(locale.toString());
        final String absoluteDirectoryName = context.getFilesDir() + File.separator
                + relativeDirectoryName;
        final File directory = new File(absoluteDirectoryName);
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                Log.e(TAG, "Could not create the directory for locale" + locale);
            }
        }
        return absoluteDirectoryName;
    }

    /**
     * Generates a file name for the id and locale passed as an argument.
     *
     * In the current implementation the file name returned will always be unique for
     * any id/locale pair, but please do not expect that the id can be the same for
     * different dictionaries with different locales. An id should be unique for any
     * dictionary.
     * The file name is pretty much an URL-encoded version of the id inside a directory
     * named like the locale, except it will also escape characters that look dangerous
     * to some file systems.
     * @param id the id of the dictionary for which to get a file name
     * @param locale the locale for which to get the file name
     * @param context the context to use for getting the directory
     * @return the name of the file to be created
     */
    private static String getCacheFileName(String id, Locale locale, Context context) {
        final String fileName = replaceFileNameDangerousCharacters(id);
        return getCacheDirectoryForLocale(locale, context) + File.separator + fileName;
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
     * Queries a content provider for the list of dictionaries for a specific locale
     * available to copy into Latin IME.
     */
    private static List<String> getDictIdList(final Locale locale, final Context context) {
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
     * Queries a content provider for dictionary data for some locale and returns the file addresses
     *
     * This will query a content provider for dictionary data for a given locale, and return
     * the addresses of a file set the members of which are suitable to be mmap'ed. It will copy
     * them to local storage if needed.
     * It should also check the dictionary versions to avoid unnecessary copies but this is
     * still in TODO state.
     * This will make the data from the content provider the cached dictionary for this locale,
     * overwriting any previous cached data.
     * @returns the addresses of the files, or null if no data could be obtained.
     * @throw FileNotFoundException if the provider returns non-existent data.
     * @throw IOException if the provider-returned data could not be read.
     */
    public static List<AssetFileAddress> getDictSetFromContentProvider(final Locale locale,
            final Context context) throws FileNotFoundException, IOException {
        final ContentResolver resolver = context.getContentResolver();
        final List<String> idList = getDictIdList(locale, context);
        final List<AssetFileAddress> fileAddressList = new ArrayList<AssetFileAddress>();
        for (String id : idList) {
            final Uri dictionaryPackUri = getProviderUri(id);
            final AssetFileDescriptor afd =
                    resolver.openAssetFileDescriptor(dictionaryPackUri, "r");
            if (null == afd) continue;
            final String fileName = copyFileTo(afd.createInputStream(),
                    getCacheFileName(id, locale, context));
            afd.close();
            fileAddressList.add(AssetFileAddress.makeFromFileName(fileName));
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
        return copyFileTo(stream, getCacheFileName(Integer.toString(resource), locale, context));
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
