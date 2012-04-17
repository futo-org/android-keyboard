/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.inputmethod.keyboard.ProximityInfo;

public class SynchronouslyLoadedContactsDictionary extends ContactsDictionary {
    private boolean mClosed;

    public SynchronouslyLoadedContactsDictionary(final Context context) {
        super(context, Suggest.DIC_CONTACTS);
        mClosed = false;
    }

    @Override
    public synchronized void getWords(final WordComposer codes,
            final CharSequence prevWordForBigrams, final WordCallback callback,
            final ProximityInfo proximityInfo) {
        blockingReloadDictionaryIfRequired();
        getWordsInner(codes, prevWordForBigrams, callback, proximityInfo);
    }

    @Override
    public synchronized boolean isValidWord(CharSequence word) {
        blockingReloadDictionaryIfRequired();
        return getWordFrequency(word) > -1;
    }

    // Protect against multiple closing
    @Override
    public synchronized void close() {
        // Actually with the current implementation of ContactsDictionary it's safe to close
        // several times, so the following protection is really only for foolproofing
        if (mClosed) return;
        mClosed = true;
        super.close();
    }
}
