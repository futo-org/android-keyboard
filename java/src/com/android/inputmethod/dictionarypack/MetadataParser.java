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

import android.text.TextUtils;
import android.util.JsonReader;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

/**
 * Helper class containing functions to parse the dictionary metadata.
 */
public class MetadataParser {

    // Name of the fields in the JSON-formatted file.
    private static final String ID_FIELD_NAME = MetadataDbHelper.WORDLISTID_COLUMN;
    private static final String LOCALE_FIELD_NAME = "locale";
    private static final String DESCRIPTION_FIELD_NAME = MetadataDbHelper.DESCRIPTION_COLUMN;
    private static final String UPDATE_FIELD_NAME = "update";
    private static final String FILESIZE_FIELD_NAME = MetadataDbHelper.FILESIZE_COLUMN;
    private static final String RAW_CHECKSUM_FIELD_NAME = MetadataDbHelper.RAW_CHECKSUM_COLUMN;
    private static final String CHECKSUM_FIELD_NAME = MetadataDbHelper.CHECKSUM_COLUMN;
    private static final String REMOTE_FILENAME_FIELD_NAME =
            MetadataDbHelper.REMOTE_FILENAME_COLUMN;
    private static final String VERSION_FIELD_NAME = MetadataDbHelper.VERSION_COLUMN;
    private static final String FORMATVERSION_FIELD_NAME = MetadataDbHelper.FORMATVERSION_COLUMN;

    /**
     * Parse one JSON-formatted word list metadata.
     * @param reader the reader containing the data.
     * @return a WordListMetadata object from the parsed data.
     * @throws IOException if the underlying reader throws IOException during reading.
     */
    private static WordListMetadata parseOneWordList(final JsonReader reader)
            throws IOException, BadFormatException {
        final TreeMap<String, String> arguments = new TreeMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            final String name = reader.nextName();
            if (!TextUtils.isEmpty(name)) {
                arguments.put(name, reader.nextString());
            }
        }
        reader.endObject();
        if (TextUtils.isEmpty(arguments.get(ID_FIELD_NAME))
                || TextUtils.isEmpty(arguments.get(LOCALE_FIELD_NAME))
                || TextUtils.isEmpty(arguments.get(DESCRIPTION_FIELD_NAME))
                || TextUtils.isEmpty(arguments.get(UPDATE_FIELD_NAME))
                || TextUtils.isEmpty(arguments.get(FILESIZE_FIELD_NAME))
                || TextUtils.isEmpty(arguments.get(CHECKSUM_FIELD_NAME))
                || TextUtils.isEmpty(arguments.get(REMOTE_FILENAME_FIELD_NAME))
                || TextUtils.isEmpty(arguments.get(VERSION_FIELD_NAME))
                || TextUtils.isEmpty(arguments.get(FORMATVERSION_FIELD_NAME))) {
            throw new BadFormatException(arguments.toString());
        }
        // TODO: need to find out whether it's bulk or update
        // The null argument is the local file name, which is not known at this time and will
        // be decided later.
        return new WordListMetadata(
                arguments.get(ID_FIELD_NAME),
                MetadataDbHelper.TYPE_BULK,
                arguments.get(DESCRIPTION_FIELD_NAME),
                Long.parseLong(arguments.get(UPDATE_FIELD_NAME)),
                Long.parseLong(arguments.get(FILESIZE_FIELD_NAME)),
                arguments.get(RAW_CHECKSUM_FIELD_NAME),
                arguments.get(CHECKSUM_FIELD_NAME),
                MetadataDbHelper.DICTIONARY_RETRY_THRESHOLD /* retryCount */,
                null,
                arguments.get(REMOTE_FILENAME_FIELD_NAME),
                Integer.parseInt(arguments.get(VERSION_FIELD_NAME)),
                Integer.parseInt(arguments.get(FORMATVERSION_FIELD_NAME)),
                0, arguments.get(LOCALE_FIELD_NAME));
    }

    /**
     * Parses metadata in the JSON format.
     * @param input a stream reader expected to contain JSON formatted metadata.
     * @return dictionary metadata, as an array of WordListMetadata objects.
     * @throws IOException if the underlying reader throws IOException during reading.
     * @throws BadFormatException if the data was not in the expected format.
     */
    public static List<WordListMetadata> parseMetadata(final InputStreamReader input)
            throws IOException, BadFormatException {
        JsonReader reader = new JsonReader(input);
        final ArrayList<WordListMetadata> readInfo = new ArrayList<>();
        reader.beginArray();
        while (reader.hasNext()) {
            final WordListMetadata thisMetadata = parseOneWordList(reader);
            if (!TextUtils.isEmpty(thisMetadata.mLocale))
                readInfo.add(thisMetadata);
        }
        return Collections.unmodifiableList(readInfo);
    }

}
