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

package com.android.inputmethod.latin;

import android.content.Context;
import android.content.res.Resources;

public class Flag {
    public final String mName;
    public final int mResource;
    public final int mMask;
    public final int mSource;

    private static final int SOURCE_CONFIG = 1;
    private static final int SOURCE_EXTRAVALUE = 2;
    private static final int SOURCE_PARAM = 3;

    public Flag(int resourceId, int mask) {
        mName = null;
        mResource = resourceId;
        mSource = SOURCE_CONFIG;
        mMask = mask;
    }

    public Flag(String name, int mask) {
        mName = name;
        mResource = 0;
        mSource = SOURCE_EXTRAVALUE;
        mMask = mask;
    }

    public Flag(int mask) {
        mName = null;
        mResource = 0;
        mSource = SOURCE_PARAM;
        mMask = mask;
    }

    // If context/switcher are null, set all related flags in flagArray to on.
    public static int initFlags(Flag[] flagArray, Context context, SubtypeSwitcher switcher) {
        int flags = 0;
        final Resources res = null == context ? null : context.getResources();
        for (Flag entry : flagArray) {
            switch (entry.mSource) {
                case Flag.SOURCE_CONFIG:
                    if (res == null || res.getBoolean(entry.mResource))
                        flags |= entry.mMask;
                    break;
                case Flag.SOURCE_EXTRAVALUE:
                    if (switcher == null ||
                            switcher.currentSubtypeContainsExtraValueKey(entry.mName))
                        flags |= entry.mMask;
                    break;
                case Flag.SOURCE_PARAM:
                    flags |= entry.mMask;
                    break;
            }
        }
        return flags;
    }
}
