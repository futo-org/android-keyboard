/**
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.android.inputmethod.dictionarypack;

import android.view.View;

import com.android.inputmethod.latin.utils.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Helper class to maintain the interface state of word list preferences.
 *
 * This is necessary because the views are created on-demand by calling code. There are many
 * situations where views are renewed with little relation with user interaction. For example,
 * when scrolling, the view is reused so it doesn't keep its state, which means we need to keep
 * it separately. Also whenever the underlying dictionary list undergoes a change (for example,
 * update the metadata, or finish downloading) the whole list has to be thrown out and recreated
 * in case some dictionaries appeared, disappeared, changed states etc.
 */
public class DictionaryListInterfaceState {
    private static class State {
        public boolean mOpen = false;
        public int mStatus = MetadataDbHelper.STATUS_UNKNOWN;
    }

    private HashMap<String, State> mWordlistToState = CollectionUtils.newHashMap();
    private ArrayList<View> mViewCache = CollectionUtils.newArrayList();

    public boolean isOpen(final String wordlistId) {
        final State state = mWordlistToState.get(wordlistId);
        if (null == state) return false;
        return state.mOpen;
    }

    public int getStatus(final String wordlistId) {
        final State state = mWordlistToState.get(wordlistId);
        if (null == state) return MetadataDbHelper.STATUS_UNKNOWN;
        return state.mStatus;
    }

    public void setOpen(final String wordlistId, final int status) {
        final State newState;
        final State state = mWordlistToState.get(wordlistId);
        newState = null == state ? new State() : state;
        newState.mOpen = true;
        newState.mStatus = status;
        mWordlistToState.put(wordlistId, newState);
    }

    public void closeAll() {
        for (final State state : mWordlistToState.values()) {
            state.mOpen = false;
        }
    }

    public View findFirstOrphanedView() {
        for (final View v : mViewCache) {
            if (null == v.getParent()) return v;
        }
        return null;
    }

    public View addToCacheAndReturnView(final View view) {
        mViewCache.add(view);
        return view;
    }

    public void removeFromCache(final View view) {
        mViewCache.remove(view);
    }
}
