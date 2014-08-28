/*
 * Copyright (C) 2014 The Android Open Source Project
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

import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import com.android.inputmethod.annotations.UsedForTesting;

import android.content.Context;
import android.util.Log;
import android.util.LruCache;

/**
 * Cache for dictionary facilitators of multiple locales.
 * This class automatically creates and releases facilitator instances using LRU policy.
 */
public class DictionaryFacilitatorLruCache {
    private static final String TAG = DictionaryFacilitatorLruCache.class.getSimpleName();
    private static final int WAIT_FOR_LOADING_MAIN_DICT_IN_MILLISECONDS = 1000;
    private static final int MAX_RETRY_COUNT_FOR_WAITING_FOR_LOADING_DICT = 5;

    /**
     * Class extends LruCache. This class tracks cached locales and closes evicted dictionaries by
     * overriding entryRemoved.
     */
    private static class DictionaryFacilitatorLruCacheInner extends
            LruCache<Locale, DictionaryFacilitator> {
        private final HashSet<Locale> mCachedLocales;
        public DictionaryFacilitatorLruCacheInner(final HashSet<Locale> cachedLocales,
                final int maxSize) {
            super(maxSize);
            mCachedLocales = cachedLocales;
        }

        @Override
        protected void entryRemoved(boolean evicted, Locale key,
                DictionaryFacilitator oldValue, DictionaryFacilitator newValue) {
            if (oldValue != null && oldValue != newValue) {
                oldValue.closeDictionaries();
            }
            if (key != null && newValue == null) {
                // Remove locale from the cache when the dictionary facilitator for the locale is
                // evicted and new facilitator is not set for the locale.
                mCachedLocales.remove(key);
                if (size() >= maxSize()) {
                    Log.w(TAG, "DictionaryFacilitator for " + key.toString()
                            + " has been evicted due to cache size limit."
                            + " size: " + size() + ", maxSize: " + maxSize());
                }
            }
        }
    }

    private final Context mContext;
    private final HashSet<Locale> mCachedLocales = new HashSet<>();
    private final String mDictionaryNamePrefix;
    private final DictionaryFacilitatorLruCacheInner mLruCache;
    private final Object mLock = new Object();
    private boolean mUseContactsDictionary = false;

    public DictionaryFacilitatorLruCache(final Context context, final int maxSize,
            final String dictionaryNamePrefix) {
        mContext = context;
        mLruCache = new DictionaryFacilitatorLruCacheInner(mCachedLocales, maxSize);
        mDictionaryNamePrefix = dictionaryNamePrefix;
    }

    private void waitForLoadingMainDictionary(final DictionaryFacilitator dictionaryFacilitator) {
        for (int i = 0; i < MAX_RETRY_COUNT_FOR_WAITING_FOR_LOADING_DICT; i++) {
            try {
                dictionaryFacilitator.waitForLoadingMainDictionary(
                        WAIT_FOR_LOADING_MAIN_DICT_IN_MILLISECONDS, TimeUnit.MILLISECONDS);
                return;
            } catch (final InterruptedException e) {
                Log.i(TAG, "Interrupted during waiting for loading main dictionary.", e);
                if (i < MAX_RETRY_COUNT_FOR_WAITING_FOR_LOADING_DICT - 1) {
                    Log.i(TAG, "Retry", e);
                } else {
                    Log.w(TAG, "Give up retrying. Retried "
                            + MAX_RETRY_COUNT_FOR_WAITING_FOR_LOADING_DICT + " times.", e);
                }
            }
        }
    }

    private void resetDictionariesForLocaleLocked(final DictionaryFacilitator dictionaryFacilitator,
            final Locale locale) {
        dictionaryFacilitator.resetDictionariesWithDictNamePrefix(mContext, locale,
                mUseContactsDictionary, false /* usePersonalizedDicts */,
                false /* forceReloadMainDictionary */, null /* listener */,
                mDictionaryNamePrefix);
    }

    public void setUseContactsDictionary(final boolean useContectsDictionary) {
        if (mUseContactsDictionary == useContectsDictionary) {
            // The value has not been changed.
            return;
        }
        synchronized (mLock) {
            mUseContactsDictionary = useContectsDictionary;
            for (final Locale locale : mCachedLocales) {
                final DictionaryFacilitator dictionaryFacilitator = mLruCache.get(locale);
                resetDictionariesForLocaleLocked(dictionaryFacilitator, locale);
                waitForLoadingMainDictionary(dictionaryFacilitator);
            }
        }
    }

    public DictionaryFacilitator get(final Locale locale) {
        DictionaryFacilitator dictionaryFacilitator = mLruCache.get(locale);
        if (dictionaryFacilitator != null) {
            // dictionary falicitator for the locale is in the cache.
            return dictionaryFacilitator;
        }
        synchronized (mLock) {
            dictionaryFacilitator = mLruCache.get(locale);
            if (dictionaryFacilitator != null) {
                return dictionaryFacilitator;
            }
            dictionaryFacilitator = new DictionaryFacilitator();
            resetDictionariesForLocaleLocked(dictionaryFacilitator, locale);
            waitForLoadingMainDictionary(dictionaryFacilitator);
            mLruCache.put(locale, dictionaryFacilitator);
            mCachedLocales.add(locale);
            return dictionaryFacilitator;
        }
    }

    public void evictAll() {
        synchronized (mLock) {
            mLruCache.evictAll();
            mCachedLocales.clear();
        }
    }

    @UsedForTesting
    HashSet<Locale> getCachedLocalesForTesting() {
        return mCachedLocales;
    }
}
