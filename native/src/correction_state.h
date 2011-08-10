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

#ifndef LATINIME_CORRECTION_STATE_H
#define LATINIME_CORRECTION_STATE_H

#include <stdint.h>

#include "defines.h"

namespace latinime {

class CorrectionState {
public:
    int mParentIndex;
    int mMatchedCount;
    int mChildCount;
    int mInputIndex;
    int mDiffs;
    int mSiblingPos;
    bool mTraverseAll;

    inline void init(const int rootPos, const int childCount, const bool traverseAll) {
        set(-1, 0, childCount, 0, 0, rootPos, traverseAll);
    }

private:
    inline void set(const int parentIndex, const int matchedCount, const int childCount,
            const int inputIndex, const int diffs, const int siblingPos,
            const bool traverseAll) {
        mParentIndex = parentIndex;
        mMatchedCount = matchedCount;
        mChildCount = childCount;
        mInputIndex = inputIndex;
        mDiffs = diffs;
        mSiblingPos = siblingPos;
        mTraverseAll = traverseAll;
    }
};
} // namespace latinime
#endif // LATINIME_CORRECTION_STATE_H
