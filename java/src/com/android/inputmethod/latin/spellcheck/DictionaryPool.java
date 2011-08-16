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

import android.content.Context;

import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A blocking queue that creates dictionaries up to a certain limit as necessary.
 */
public class DictionaryPool extends LinkedBlockingQueue<DictAndProximity> {
    private final AndroidSpellCheckerService mService;
    private final int mMaxSize;
    private final Locale mLocale;
    private int mSize = 0;

    public DictionaryPool(final int maxSize, final AndroidSpellCheckerService service,
            final Locale locale) {
        super();
        mMaxSize = maxSize;
        mService = service;
        mLocale = locale;
    }

    @Override
    public DictAndProximity take() throws InterruptedException {
        final DictAndProximity dict = poll();
        if (null != dict) return dict;
        synchronized(this) {
            if (mSize >= mMaxSize) {
                // Our pool is already full. Wait until some dictionary is ready.
                return super.take();
            } else {
                ++mSize;
                return mService.createDictAndProximity(mLocale);
            }
        }
    }
}
