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
import android.net.Uri;
import android.text.TextUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Group class for static methods to help with creation and getting of the binary dictionary
 * file from the dictionary provider
 */
public class BinaryDictionaryFileDumper {
    /**
     * The size of the temporary buffer to copy files.
     */
    static final int FILE_READ_BUFFER_SIZE = 1024;

    // Prevents this class to be accidentally instantiated.
    private BinaryDictionaryFileDumper() {
    }

    /**
     * Generates a file name that matches the locale passed as an argument.
     * The file name is basically the result of the .toString() method, except we replace
     * any @File.separator with an underscore to avoid generating a file name that may not
     * be created.
     * @param locale the locale for which to get the file name
     * @param context the context to use for getting the directory
     * @return the name of the file to be created
     */
    private static String getCacheFileNameForLocale(Locale locale, Context context) {
        // The following assumes two things :
        // 1. That File.separator is not the same character as "_"
        //    I don't think any android system will ever use "_" as a path separator
        // 2. That no two locales differ by only a File.separator versus a "_"
        //    Since "_" can't be part of locale components this should be safe.
        // Examples:
        // en -> en
        // en_US_POSIX -> en_US_POSIX
        // en__foo/bar -> en__foo_bar
        final String[] separator = { File.separator };
        final String[] empty = { "_" };
        final CharSequence basename = TextUtils.replace(locale.toString(), separator, empty);
        return context.getFilesDir() + File.separator + basename;
    }

    /**
     * Return for a given locale the provider URI to query to get the dictionary.
     */
    public static Uri getProviderUri(Locale locale) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(BinaryDictionary.DICTIONARY_PACK_AUTHORITY).appendPath(
                        locale.toString()).build();
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
    public static List<AssetFileAddress> getDictSetFromContentProvider(Locale locale,
            Context context) throws FileNotFoundException, IOException {
        // TODO: check whether the dictionary is the same or not and if it is, return the cached
        // file.
        // TODO: This should be able to read a number of files from the dictionary pack, copy
        // them all and return them.
        final ContentResolver resolver = context.getContentResolver();
        final Uri dictionaryPackUri = getProviderUri(locale);
        final AssetFileDescriptor afd = resolver.openAssetFileDescriptor(dictionaryPackUri, "r");
        if (null == afd) return null;
        final String fileName =
                copyFileTo(afd.createInputStream(), getCacheFileNameForLocale(locale, context));
        return Arrays.asList(AssetFileAddress.makeFromFileName(fileName));
    }

    /**
     * Accepts a file as dictionary data for some locale and returns the name of a file.
     *
     * This will make the data in the input file the cached dictionary for this locale, overwriting
     * any previous cached data.
     */
    public static String getDictionaryFileFromFile(String fileName, Locale locale,
            Context context) throws FileNotFoundException, IOException {
        return copyFileTo(new FileInputStream(fileName), getCacheFileNameForLocale(locale,
                context));
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
        return copyFileTo(stream, getCacheFileNameForLocale(locale, context));
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
