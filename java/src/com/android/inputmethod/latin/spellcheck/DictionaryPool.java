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

package com.android.inputmethod.latin.spellcheck;

import android.util.Log;

import com.android.inputmethod.keyboard.ProximityInfo;
import com.android.inputmethod.latin.CollectionUtils;
import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.WordComposer;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A blocking queue that creates dictionaries up to a certain limit as necessary.
 * As a deadlock-detecting device, if waiting for more than TIMEOUT = 3 seconds, we
 * will clear the queue and generate its contents again. This is transparent for
 * the client code, but may help with sloppy clients.
 */
@SuppressWarnings("serial")
public final class DictionaryPool extends LinkedBlockingQueue<DictAndProximity> {
    private final static String TAG = DictionaryPool.class.getSimpleName();
    // How many seconds we wait for a dictionary to become available. Past this delay, we give up in
    // fear some bug caused a deadlock, and reset the whole pool.
    private final static int TIMEOUT = 3;
    private final AndroidSpellCheckerService mService;
    private final int mMaxSize;
    private final Locale mLocale;
    private int mSize;
    private volatile boolean mClosed;
    final static ArrayList<SuggestedWordInfo> noSuggestions = CollectionUtils.newArrayList();
    private final static DictAndProximity dummyDict = new DictAndProximity(
            new Dictionary(Dictionary.TYPE_MAIN) {
                @Override
                public ArrayList<SuggestedWordInfo> getSuggestions(final WordComposer composer,
                        final CharSequence prevWord, final ProximityInfo proximityInfo) {
                    return noSuggestions;
                }
                @Override
                public boolean isValidWord(CharSequence word) {
                    // This is never called. However if for some strange reason it ever gets
                    // called, returning true is less destructive (it will not underline the
                    // word in red).
                    return true;
                }
            }, null);

    static public boolean isAValidDictionary(final DictAndProximity dictInfo) {
        return null != dictInfo && dummyDict != dictInfo;
    }

    public DictionaryPool(final int maxSize, final AndroidSpellCheckerService service,
            final Locale locale) {
        super();
        mMaxSize = maxSize;
        mService = service;
        mLocale = locale;
        mSize = 0;
        mClosed = false;
    }

    @Override
    public DictAndProximity poll(final long timeout, final TimeUnit unit)
            throws InterruptedException {
        final DictAndProximity dict = poll();
        if (null != dict) return dict;
        synchronized(this) {
            if (mSize >= mMaxSize) {
                // Our pool is already full. Wait until some dictionary is ready, or TIMEOUT
                // expires to avoid a deadlock.
                final DictAndProximity result = super.poll(timeout, unit);
                if (null == result) {
                    Log.e(TAG, "Deadlock detected ! Resetting dictionary pool");
                    clear();
                    mSize = 1;
                    return mService.createDictAndProximity(mLocale);
                } else {
                    return result;
                }
            } else {
                ++mSize;
                return mService.createDictAndProximity(mLocale);
            }
        }
    }

    // Convenience method
    public DictAndProximity pollWithDefaultTimeout() {
        try {
            return poll(TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return null;
        }
    }

    public void close() {
        synchronized(this) {
            mClosed = true;
            for (DictAndProximity dict : this) {
                dict.mDictionary.close();
            }
            clear();
        }
    }

    @Override
    public boolean offer(final DictAndProximity dict) {
        if (mClosed) {
            dict.mDictionary.close();
            return super.offer(dummyDict);
        } else {
            return super.offer(dict);
        }
    }
}
