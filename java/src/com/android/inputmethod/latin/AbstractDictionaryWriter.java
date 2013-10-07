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

import com.android.inputmethod.latin.makedict.DictEncoder;
import com.android.inputmethod.latin.makedict.UnsupportedFormatException;
import com.android.inputmethod.latin.makedict.Ver3DictEncoder;

import java.io.File;
import java.io.IOException;
import java.util.Map;

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

    /**
     * Add a unigram with an optional shortcut to the dictionary.
     * @param word The word to add.
     * @param shortcutTarget A shortcut target for this word, or null if none.
     * @param frequency The frequency for this unigram.
     * @param shortcutFreq The frequency of the shortcut (0~15, with 15 = whitelist). Ignored
     *   if shortcutTarget is null.
     * @param isNotAWord true if this is not a word, i.e. shortcut only.
     */
    abstract public void addUnigramWord(final String word, final String shortcutTarget,
            final int frequency, final int shortcutFreq, final boolean isNotAWord);

    // TODO: Remove lastModifiedTime after making binary dictionary support forgetting curve.
    abstract public void addBigramWords(final String word0, final String word1,
            final int frequency, final boolean isValid,
            final long lastModifiedTime);

    abstract public void removeBigramWords(final String word0, final String word1);

    abstract protected void writeDictionary(final DictEncoder dictEncoder,
            final Map<String, String> attributeMap) throws IOException, UnsupportedFormatException;

    public void write(final String fileName, final Map<String, String> attributeMap) {
        final String tempFileName = fileName + ".temp";
        final File file = new File(mContext.getFilesDir(), fileName);
        final File tempFile = new File(mContext.getFilesDir(), tempFileName);
        try {
            final DictEncoder dictEncoder = new Ver3DictEncoder(tempFile);
            writeDictionary(dictEncoder, attributeMap);
            tempFile.renameTo(file);
        } catch (IOException e) {
            Log.e(TAG, "IO exception while writing file", e);
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "Unsupported format", e);
        }
    }
}
