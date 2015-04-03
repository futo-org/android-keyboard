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

import javax.annotation.Nonnull;

/**
 * The metadata for a single word list.
 *
 * Instances of this class are always immutable.
 */
public class WordListMetadata {

    public final String mId;
    public final int mType; // Type, as of MetadataDbHelper#TYPE_*
    public final String mDescription;
    public final long mLastUpdate;
    public final long mFileSize;
    public final String mRawChecksum;
    public final String mChecksum;
    public final String mLocalFilename;
    public final String mRemoteFilename;
    public final int mVersion; // version of this word list
    public final int mFlags; // Always 0 in this version, reserved for future use
    public int mRetryCount;

    // The locale is matched against the locale requested by the client. The matching algorithm
    // is a standard locale matching with fallback; it is implemented in
    // DictionaryProvider#getDictionaryFileForContentUri.
    public final String mLocale;


    // Version number of the format.
    // This implementation of the DictionaryDataService knows how to handle format 1 only.
    // This is only for forward compatibility, to be able to upgrade the format without
    // breaking old implementations.
    public final int mFormatVersion;

    public WordListMetadata(final String id, final int type,
            final String description, final long lastUpdate, final long fileSize,
            final String rawChecksum, final String checksum, final int retryCount,
            final String localFilename, final String remoteFilename,
            final int version, final int formatVersion,
            final int flags, final String locale) {
        mId = id;
        mType = type;
        mDescription = description;
        mLastUpdate = lastUpdate; // In milliseconds
        mFileSize = fileSize;
        mRawChecksum = rawChecksum;
        mChecksum = checksum;
        mRetryCount = retryCount;
        mLocalFilename = localFilename;
        mRemoteFilename = remoteFilename;
        mVersion = version;
        mFormatVersion = formatVersion;
        mFlags = flags;
        mLocale = locale;
    }

    /**
     * Create a WordListMetadata from the contents of a ContentValues.
     *
     * If this lacks any required field, IllegalArgumentException is thrown.
     */
    public static WordListMetadata createFromContentValues(@Nonnull final ContentValues values) {
        final String id = values.getAsString(MetadataDbHelper.WORDLISTID_COLUMN);
        final Integer type = values.getAsInteger(MetadataDbHelper.TYPE_COLUMN);
        final String description = values.getAsString(MetadataDbHelper.DESCRIPTION_COLUMN);
        final Long lastUpdate = values.getAsLong(MetadataDbHelper.DATE_COLUMN);
        final Long fileSize = values.getAsLong(MetadataDbHelper.FILESIZE_COLUMN);
        final String rawChecksum = values.getAsString(MetadataDbHelper.RAW_CHECKSUM_COLUMN);
        final String checksum = values.getAsString(MetadataDbHelper.CHECKSUM_COLUMN);
        final int retryCount = values.getAsInteger(MetadataDbHelper.RETRY_COUNT_COLUMN);
        final String localFilename = values.getAsString(MetadataDbHelper.LOCAL_FILENAME_COLUMN);
        final String remoteFilename = values.getAsString(MetadataDbHelper.REMOTE_FILENAME_COLUMN);
        final Integer version = values.getAsInteger(MetadataDbHelper.VERSION_COLUMN);
        final Integer formatVersion = values.getAsInteger(MetadataDbHelper.FORMATVERSION_COLUMN);
        final Integer flags = values.getAsInteger(MetadataDbHelper.FLAGS_COLUMN);
        final String locale = values.getAsString(MetadataDbHelper.LOCALE_COLUMN);
        if (null == id
                || null == type
                || null == description
                || null == lastUpdate
                || null == fileSize
                || null == checksum
                || null == localFilename
                || null == remoteFilename
                || null == version
                || null == formatVersion
                || null == flags
                || null == locale) {
            throw new IllegalArgumentException();
        }
        return new WordListMetadata(id, type, description, lastUpdate, fileSize, rawChecksum,
                checksum, retryCount, localFilename, remoteFilename, version, formatVersion,
                flags, locale);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(WordListMetadata.class.getSimpleName());
        sb.append(" : ").append(mId);
        sb.append("\nType : ").append(mType);
        sb.append("\nDescription : ").append(mDescription);
        sb.append("\nLastUpdate : ").append(mLastUpdate);
        sb.append("\nFileSize : ").append(mFileSize);
        sb.append("\nRawChecksum : ").append(mRawChecksum);
        sb.append("\nChecksum : ").append(mChecksum);
        sb.append("\nRetryCount: ").append(mRetryCount);
        sb.append("\nLocalFilename : ").append(mLocalFilename);
        sb.append("\nRemoteFilename : ").append(mRemoteFilename);
        sb.append("\nVersion : ").append(mVersion);
        sb.append("\nFormatVersion : ").append(mFormatVersion);
        sb.append("\nFlags : ").append(mFlags);
        sb.append("\nLocale : ").append(mLocale);
        return sb.toString();
    }
}
