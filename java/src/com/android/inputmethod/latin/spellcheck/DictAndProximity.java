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

import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.keyboard.ProximityInfo;

/**
 * A simple container for both a Dictionary and a ProximityInfo.
 */
public class DictAndProximity {
    public final Dictionary mDictionary;
    public final ProximityInfo mProximityInfo;
    public DictAndProximity(final Dictionary dictionary, final ProximityInfo proximityInfo) {
        mDictionary = dictionary;
        mProximityInfo = proximityInfo;
    }
}
