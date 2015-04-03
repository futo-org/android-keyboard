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

package com.android.inputmethod.dictionarypack;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to easy up manipulation of dictionary pack metadata.
 */
public class MetadataHandler {

    public static final String TAG = MetadataHandler.class.getSimpleName();

    // The canonical file name for metadata. This is not the name of a real file on the
    // device, but a symbolic name used in the database and in metadata handling. It is never
    // tested against, only used for human-readability as the file name for the metadata.
    public static final String METADATA_FILENAME = "metadata.json";

    /**
     * Reads the data from the cursor and store it in metadata objects.
     * @param results the cursor to read data from.
     * @return the constructed list of wordlist metadata.
     */
    private static List<WordListMetadata> makeMetadataObject(final Cursor results) {
        final ArrayList<WordListMetadata> buildingMetadata = new ArrayList<>();
        if (null != results && results.moveToFirst()) {
            final int localeColumn = results.getColumnIndex(MetadataDbHelper.LOCALE_COLUMN);
            final int typeColumn = results.getColumnIndex(MetadataDbHelper.TYPE_COLUMN);
            final int descriptionColumn =
                    results.getColumnIndex(MetadataDbHelper.DESCRIPTION_COLUMN);
            final int idIndex = results.getColumnIndex(MetadataDbHelper.WORDLISTID_COLUMN);
            final int updateIndex = results.getColumnIndex(MetadataDbHelper.DATE_COLUMN);
            final int fileSizeIndex = results.getColumnIndex(MetadataDbHelper.FILESIZE_COLUMN);
            final int rawChecksumIndex =
                    results.getColumnIndex(MetadataDbHelper.RAW_CHECKSUM_COLUMN);
            final int checksumIndex = results.getColumnIndex(MetadataDbHelper.CHECKSUM_COLUMN);
            final int retryCountIndex = results.getColumnIndex(MetadataDbHelper.RETRY_COUNT_COLUMN);
            final int localFilenameIndex =
                    results.getColumnIndex(MetadataDbHelper.LOCAL_FILENAME_COLUMN);
            final int remoteFilenameIndex =
                    results.getColumnIndex(MetadataDbHelper.REMOTE_FILENAME_COLUMN);
            final int versionIndex = results.getColumnIndex(MetadataDbHelper.VERSION_COLUMN);
            final int formatVersionIndex =
                    results.getColumnIndex(MetadataDbHelper.FORMATVERSION_COLUMN);
            do {
                buildingMetadata.add(new WordListMetadata(results.getString(idIndex),
                        results.getInt(typeColumn),
                        results.getString(descriptionColumn),
                        results.getLong(updateIndex),
                        results.getLong(fileSizeIndex),
                        results.getString(rawChecksumIndex),
                        results.getString(checksumIndex),
                        results.getInt(retryCountIndex),
                        results.getString(localFilenameIndex),
                        results.getString(remoteFilenameIndex),
                        results.getInt(versionIndex),
                        results.getInt(formatVersionIndex),
                        0, results.getString(localeColumn)));
            } while (results.moveToNext());
        }
        return Collections.unmodifiableList(buildingMetadata);
    }

    /**
     * Gets the whole metadata, for installed and not installed dictionaries.
     * @param context The context to open files over.
     * @param clientId the client id for retrieving the database. null for default (deprecated)
     * @return The current metadata.
     */
    public static List<WordListMetadata> getCurrentMetadata(final Context context,
            final String clientId) {
        // If clientId is null, we get a cursor on the default database (see
        // MetadataDbHelper#getInstance() for more on this)
        final Cursor results = MetadataDbHelper.queryCurrentMetadata(context, clientId);
        // If null, we should return makeMetadataObject(null), so we go through.
        try {
            return makeMetadataObject(results);
        } finally {
            if (null != results) {
                results.close();
            }
        }
    }

    /**
     * Gets the metadata, for a specific dictionary.
     *
     * @param context The context to open files over.
     * @param clientId the client id for retrieving the database. null for default (deprecated).
     * @param wordListId the word list ID.
     * @param version the word list version.
     * @return the current metaData
     */
    public static WordListMetadata getCurrentMetadataForWordList(final Context context,
            final String clientId, final String wordListId, final int version) {
        final ContentValues contentValues = MetadataDbHelper.getContentValuesByWordListId(
                MetadataDbHelper.getDb(context, clientId), wordListId, version);
        if (contentValues == null) {
            // TODO: Figure out why this would happen.
            // Check if this happens when the metadata gets updated in the background.
            Log.e(TAG, String.format( "Unable to find the current metadata for wordlist "
                            + "(clientId=%s, wordListId=%s, version=%d) on the database",
                    clientId, wordListId, version));
            return null;
        }
        return WordListMetadata.createFromContentValues(contentValues);
    }

    /**
     * Read metadata from a stream.
     * @param input The stream to read from.
     * @return The read metadata.
     * @throws IOException if the input stream cannot be read
     * @throws BadFormatException if the stream is not in a known format
     */
    public static List<WordListMetadata> readMetadata(final InputStreamReader input)
            throws IOException, BadFormatException {
        return MetadataParser.parseMetadata(input);
    }

    /**
     * Finds a single WordListMetadata inside a whole metadata chunk.
     *
     * Searches through the whole passed metadata for the first WordListMetadata associated
     * with the passed ID. If several metadata chunks with the same id are found, it will
     * always return the one with the bigger FormatVersion that is less or equal than the
     * maximum supported format version (as listed in UpdateHandler).
     * This will NEVER return the metadata with a FormatVersion bigger than what is supported,
     * even if it is the only word list with this ID.
     *
     * @param metadata the metadata to search into.
     * @param id the word list ID of the metadata to find.
     * @return the associated metadata, or null if not found.
     */
    public static WordListMetadata findWordListById(final List<WordListMetadata> metadata,
            final String id) {
        WordListMetadata bestWordList = null;
        int bestFormatVersion = Integer.MIN_VALUE; // To be sure we can't be inadvertently smaller
        for (WordListMetadata wordList : metadata) {
            if (id.equals(wordList.mId)
                    && wordList.mFormatVersion <= UpdateHandler.MAXIMUM_SUPPORTED_FORMAT_VERSION
                    && wordList.mFormatVersion > bestFormatVersion) {
                bestWordList = wordList;
                bestFormatVersion = wordList.mFormatVersion;
            }
        }
        // If we didn't find any match we'll return null.
        return bestWordList;
    }
}
