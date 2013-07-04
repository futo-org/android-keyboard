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

package com.android.inputmethod.latin;

import android.content.Context;
import android.util.Log;

import com.android.inputmethod.latin.makedict.UnsupportedFormatException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

// TODO: Quit extending Dictionary after implementing dynamic binary dictionary.
abstract public class AbstractDictionaryWriter extends Dictionary {
    /** Used for Log actions from this class */
    private static final String TAG = AbstractDictionaryWriter.class.getSimpleName();

    private final Context mContext;

    public AbstractDictionaryWriter(final Context context, final String dictType) {
        super(dictType);
        mContext = context;
    }

    abstract public void clear();

    abstract public void addUnigramWord(final String word, final String shortcutTarget,
            final int frequency, final boolean isNotAWord);

    abstract public void addBigramWords(final String word0, final String word1,
            final int frequency, final boolean isValid);

    abstract public void removeBigramWords(final String word0, final String word1);

    abstract protected void writeBinaryDictionary(final FileOutputStream out)
            throws IOException, UnsupportedFormatException;

    public void write(final String fileName) {
        final String tempFileName = fileName + ".temp";
        final File file = new File(mContext.getFilesDir(), fileName);
        final File tempFile = new File(mContext.getFilesDir(), tempFileName);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(tempFile);
            writeBinaryDictionary(out);
            out.flush();
            out.close();
            tempFile.renameTo(file);
        } catch (IOException e) {
            Log.e(TAG, "IO exception while writing file", e);
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "Unsupported format", e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }
}
