/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.keyboard.internal;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.latin.utils.CollectionUtils;

import java.util.HashMap;

public final class KeysCache {
    private final HashMap<Key, Key> mMap = CollectionUtils.newHashMap();

    public void clear() {
        mMap.clear();
    }

    public Key get(final Key key) {
        final Key existingKey = mMap.get(key);
        if (existingKey != null) {
            // Reuse the existing element that equals to "key" without adding "key" to the map.
            return existingKey;
        }
        mMap.put(key, key);
        return key;
    }
}
