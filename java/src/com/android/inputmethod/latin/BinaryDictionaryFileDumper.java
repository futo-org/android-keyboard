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
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Group class for static methods to help with creation and getting of the binary dictionary
 * file from the dictionary provider
 */
public class BinaryDictionaryFileDumper {
    private static final String TAG = BinaryDictionaryFileDumper.class.getSimpleName();
    private static final boolean DEBUG = false;

    /**
     * The size of the temporary buffer to copy files.
     */
    private static final int FILE_READ_BUFFER_SIZE = 1024;
    // TODO: make the following data common with the native code
    private static final byte[] MAGIC_NUMBER_VERSION_1 =
            new byte[] { (byte)0x78, (byte)0xB1, (byte)0x00, (byte)0x00 };
    private static final byte[] MAGIC_NUMBER_VERSION_2 =
            new byte[] { (byte)0x9B, (byte)0xC1, (byte)0x3A, (byte)0xFE };

    private static final String DICTIONARY_PROJECTION[] = { "id" };

    public static final String QUERY_PARAMETER_MAY_PROMPT_USER = "mayPrompt";
    public static final String QUERY_PARAMETER_TRUE = "true";
    public static final String QUERY_PARAMETER_DELETE_RESULT = "result";
    public static final String QUERY_PARAMETER_SUCCESS = "success";
    public static final String QUERY_PARAMETER_FAILURE = "failure";

    // Prevents this class to be accidentally instantiated.
    private BinaryDictionaryFileDumper() {
    }

    /**
     * Returns a URI builder pointing to the dictionary pack.
     *
     * This creates a URI builder able to build a URI pointing to the dictionary
     * pack content provider for a specific dictionary id.
     */
    private static Uri.Builder getProviderUriBuilder(final String path) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(BinaryDictionary.DICTIONARY_PACK_AUTHORITY).appendPath(
                        path);
    }

    /**
     * Queries a content provider for the list of word lists for a specific locale
     * available to copy into Latin IME.
     */
    private static List<WordListInfo> getWordListWordListInfos(final Locale locale,
            final Context context, final boolean hasDefaultWordList) {
        final ContentResolver resolver = context.getContentResolver();
        final Uri.Builder builder = getProviderUriBuilder(locale.toString());
        if (!hasDefaultWordList) {
            builder.appendQueryParameter(QUERY_PARAMETER_MAY_PROMPT_USER, QUERY_PARAMETER_TRUE);
        }
        final Uri dictionaryPackUri = builder.build();

        final Cursor c = resolver.query(dictionaryPackUri, DICTIONARY_PROJECTION, null, null, null);
        if (null == c) return Collections.<WordListInfo>emptyList();
        if (c.getCount() <= 0 || !c.moveToFirst()) {
            c.close();
            return Collections.<WordListInfo>emptyList();
        }

        try {
            final List<WordListInfo> list = new ArrayList<WordListInfo>();
            do {
                final String wordListId = c.getString(0);
                final String wordListLocale = c.getString(1);
                if (TextUtils.isEmpty(wordListId)) continue;
                list.add(new WordListInfo(wordListId, wordListLocale));
            } while (c.moveToNext());
            c.close();
            return list;
        } catch (Exception e) {
            // Just in case we hit a problem in communication with the dictionary pack.
            // We don't want to die.
            Log.e(TAG, "Exception communicating with the dictionary pack : " + e);
            return Collections.<WordListInfo>emptyList();
        }
    }


    /**
     * Helper method to encapsulate exception handling.
     */
    private static AssetFileDescriptor openAssetFileDescriptor(final ContentResolver resolver,
            final Uri uri) {
        try {
            return resolver.openAssetFileDescriptor(uri, "r");
        } catch (FileNotFoundException e) {
            // I don't want to log the word list URI here for security concerns
            Log.e(TAG, "Could not find a word list from the dictionary provider.");
            return null;
        }
    }

    /**
     * Caches a word list the id of which is passed as an argument. This will write the file
     * to the cache file name designated by its id and locale, overwriting it if already present
     * and creating it (and its containing directory) if necessary.
     */
    private static AssetFileAddress cacheWordList(final String id, final String locale,
            final ContentResolver resolver, final Context context) {

        final int COMPRESSED_CRYPTED_COMPRESSED = 0;
        final int CRYPTED_COMPRESSED = 1;
        final int COMPRESSED_CRYPTED = 2;
        final int COMPRESSED_ONLY = 3;
        final int CRYPTED_ONLY = 4;
        final int NONE = 5;
        final int MODE_MIN = COMPRESSED_CRYPTED_COMPRESSED;
        final int MODE_MAX = NONE;

        final Uri.Builder wordListUriBuilder = getProviderUriBuilder(id);
        final String finalFileName = BinaryDictionaryGetter.getCacheFileName(id, locale, context);
        final String tempFileName = finalFileName + ".tmp";

        for (int mode = MODE_MIN; mode <= MODE_MAX; ++mode) {
            InputStream originalSourceStream = null;
            InputStream inputStream = null;
            File outputFile = null;
            FileOutputStream outputStream = null;
            AssetFileDescriptor afd = null;
            final Uri wordListUri = wordListUriBuilder.build();
            try {
                // Open input.
                afd = openAssetFileDescriptor(resolver, wordListUri);
                // If we can't open it at all, don't even try a number of times.
                if (null == afd) return null;
                originalSourceStream = afd.createInputStream();
                // Open output.
                outputFile = new File(tempFileName);
                // Just to be sure, delete the file. This may fail silently, and return false: this
                // is the right thing to do, as we just want to continue anyway.
                outputFile.delete();
                outputStream = new FileOutputStream(outputFile);
                // Get the appropriate decryption method for this try
                switch (mode) {
                    case COMPRESSED_CRYPTED_COMPRESSED:
                        inputStream = FileTransforms.getUncompressedStream(
                                FileTransforms.getDecryptedStream(
                                        FileTransforms.getUncompressedStream(
                                                originalSourceStream)));
                        break;
                    case CRYPTED_COMPRESSED:
                        inputStream = FileTransforms.getUncompressedStream(
                                FileTransforms.getDecryptedStream(originalSourceStream));
                        break;
                    case COMPRESSED_CRYPTED:
                        inputStream = FileTransforms.getDecryptedStream(
                                FileTransforms.getUncompressedStream(originalSourceStream));
                        break;
                    case COMPRESSED_ONLY:
                        inputStream = FileTransforms.getUncompressedStream(originalSourceStream);
                        break;
                    case CRYPTED_ONLY:
                        inputStream = FileTransforms.getDecryptedStream(originalSourceStream);
                        break;
                    case NONE:
                        inputStream = originalSourceStream;
                        break;
                    }
                checkMagicAndCopyFileTo(new BufferedInputStream(inputStream), outputStream);
                outputStream.flush();
                outputStream.close();
                final File finalFile = new File(finalFileName);
                if (!outputFile.renameTo(finalFile)) {
                    throw new IOException("Can't move the file to its final name");
                }
                wordListUriBuilder.appendQueryParameter(QUERY_PARAMETER_DELETE_RESULT,
                        QUERY_PARAMETER_SUCCESS);
                if (0 >= resolver.delete(wordListUriBuilder.build(), null, null)) {
                    Log.e(TAG, "Could not have the dictionary pack delete a word list");
                }
                BinaryDictionaryGetter.removeFilesWithIdExcept(context, id, finalFile);
                // Success! Close files (through the finally{} clause) and return.
                return AssetFileAddress.makeFromFileName(finalFileName);
            } catch (Exception e) {
                if (DEBUG) {
                    Log.i(TAG, "Can't open word list in mode " + mode + " : " + e);
                }
                if (null != outputFile) {
                    // This may or may not fail. The file may not have been created if the
                    // exception was thrown before it could be. Hence, both failure and
                    // success are expected outcomes, so we don't check the return value.
                    outputFile.delete();
                }
                // Try the next method.
            } finally {
                // Ignore exceptions while closing files.
                try {
                    // inputStream.close() will close afd, we should not call afd.close().
                    if (null != inputStream) inputStream.close();
                } catch (Exception e) {
                    Log.e(TAG, "Exception while closing a cross-process file descriptor : " + e);
                }
                try {
                    if (null != outputStream) outputStream.close();
                } catch (Exception e) {
                    Log.e(TAG, "Exception while closing a file : " + e);
                }
            }
        }

        // We could not copy the file at all. This is very unexpected.
        // I'd rather not print the word list ID to the log out of security concerns
        Log.e(TAG, "Could not copy a word list. Will not be able to use it.");
        // If we can't copy it we should warn the dictionary provider so that it can mark it
        // as invalid.
        wordListUriBuilder.appendQueryParameter(QUERY_PARAMETER_DELETE_RESULT,
                QUERY_PARAMETER_FAILURE);
        if (0 >= resolver.delete(wordListUriBuilder.build(), null, null)) {
            Log.e(TAG, "In addition, we were unable to delete it.");
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
            final Context context, final boolean hasDefaultWordList) {
        final ContentResolver resolver = context.getContentResolver();
        final List<WordListInfo> idList = getWordListWordListInfos(locale, context,
                hasDefaultWordList);
        final List<AssetFileAddress> fileAddressList = new ArrayList<AssetFileAddress>();
        for (WordListInfo id : idList) {
            final AssetFileAddress afd = cacheWordList(id.mId, id.mLocale, resolver, context);
            if (null != afd) {
                fileAddressList.add(afd);
            }
        }
        return fileAddressList;
    }

    /**
     * Copies the data in an input stream to a target file if the magic number matches.
     *
     * If the magic number does not match the expected value, this method throws an
     * IOException. Other usual conditions for IOException or FileNotFoundException
     * also apply.
     *
     * @param input the stream to be copied.
     * @param output an output stream to copy the data to.
     */
    private static void checkMagicAndCopyFileTo(final BufferedInputStream input,
            final FileOutputStream output) throws FileNotFoundException, IOException {
        // Check the magic number
        final int length = MAGIC_NUMBER_VERSION_2.length;
        final byte[] magicNumberBuffer = new byte[length];
        final int readMagicNumberSize = input.read(magicNumberBuffer, 0, length);
        if (readMagicNumberSize < length) {
            throw new IOException("Less bytes to read than the magic number length");
        }
        if (!Arrays.equals(MAGIC_NUMBER_VERSION_2, magicNumberBuffer)) {
            if (!Arrays.equals(MAGIC_NUMBER_VERSION_1, magicNumberBuffer)) {
                throw new IOException("Wrong magic number for downloaded file");
            }
        }
        output.write(magicNumberBuffer);

        // Actually copy the file
        final byte[] buffer = new byte[FILE_READ_BUFFER_SIZE];
        for (int readBytes = input.read(buffer); readBytes >= 0; readBytes = input.read(buffer))
            output.write(buffer, 0, readBytes);
        input.close();
    }
}
