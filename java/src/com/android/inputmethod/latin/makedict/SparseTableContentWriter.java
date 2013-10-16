/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.latin.makedict;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * An auxiliary class for writing data associated with SparseTable to files.
 */
public class SparseTableContentWriter {
    public interface SparseTableContentWriterInterface {
        public void write(final OutputStream outStream) throws IOException;
    }

    private final int mContentCount;
    private final SparseTable mSparseTable;
    private final File mLookupTableFile;
    protected final File mBaseDir;
    private final File[] mAddressTableFiles;
    private final File[] mContentFiles;
    protected final OutputStream[] mContentOutStreams;

    /**
     * Sole constructor of SparseTableContentWriter.
     *
     * @param name the name of SparseTable.
     * @param initialCapacity the initial capacity of SparseTable.
     * @param blockSize the block size of the content table.
     * @param baseDir the directory which contains the files of the content table.
     * @param contentFilenames the file names of content files.
     * @param contentIds the ids of contents. These ids are used for a suffix of a name of address
     * files and content files.
     */
    public SparseTableContentWriter(final String name, final int initialCapacity,
            final int blockSize, final File baseDir, final String[] contentFilenames,
            final String[] contentIds) {
        if (contentFilenames.length != contentIds.length) {
            throw new RuntimeException("The length of contentFilenames and the length of"
                    + " contentIds are different " + contentFilenames.length + ", "
                    + contentIds.length);
        }
        mContentCount = contentFilenames.length;
        mSparseTable = new SparseTable(initialCapacity, blockSize, mContentCount);
        mLookupTableFile = new File(baseDir, name + FormatSpec.LOOKUP_TABLE_FILE_SUFFIX);
        mAddressTableFiles = new File[mContentCount];
        mContentFiles = new File[mContentCount];
        mBaseDir = baseDir;
        for (int i = 0; i < mContentCount; ++i) {
            mAddressTableFiles[i] = new File(mBaseDir,
                    name + FormatSpec.CONTENT_TABLE_FILE_SUFFIX + contentIds[i]);
            mContentFiles[i] = new File(mBaseDir, contentFilenames[i] + contentIds[i]);
        }
        mContentOutStreams = new OutputStream[mContentCount];
    }

    public void openStreams() throws FileNotFoundException {
        for (int i = 0; i < mContentCount; ++i) {
            mContentOutStreams[i] = new FileOutputStream(mContentFiles[i]);
        }
    }

    protected void write(final int contentIndex, final int index,
            final SparseTableContentWriterInterface writer) throws IOException {
        mSparseTable.set(contentIndex, index, (int) mContentFiles[contentIndex].length());
        writer.write(mContentOutStreams[contentIndex]);
        mContentOutStreams[contentIndex].flush();
    }

    public void closeStreams() throws IOException {
        mSparseTable.writeToFiles(mLookupTableFile, mAddressTableFiles);
        for (int i = 0; i < mContentCount; ++i) {
            mContentOutStreams[i].close();
        }
    }
}