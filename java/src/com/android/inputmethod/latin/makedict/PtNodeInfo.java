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

package com.android.inputmethod.latin.makedict;

import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;

import java.util.ArrayList;

/**
 * Raw PtNode info straight out of a file. This will contain numbers for addresses.
 */
public final class PtNodeInfo {

    public final int mOriginalAddress;
    public final int mEndAddress;
    public final int mFlags;
    public final int[] mCharacters;
    public final int mFrequency;
    public final int mChildrenAddress;
    public final int mParentAddress;
    public final ArrayList<WeightedString> mShortcutTargets;
    public final ArrayList<PendingAttribute> mBigrams;

    public PtNodeInfo(final int originalAddress, final int endAddress, final int flags,
            final int[] characters, final int frequency, final int parentAddress,
            final int childrenAddress, final ArrayList<WeightedString> shortcutTargets,
            final ArrayList<PendingAttribute> bigrams) {
        mOriginalAddress = originalAddress;
        mEndAddress = endAddress;
        mFlags = flags;
        mCharacters = characters;
        mFrequency = frequency;
        mParentAddress = parentAddress;
        mChildrenAddress = childrenAddress;
        mShortcutTargets = shortcutTargets;
        mBigrams = bigrams;
    }
}
