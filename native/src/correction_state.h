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

class ProximityInfo;

class CorrectionState {
public:
    CorrectionState();
    void setCorrectionParams(const ProximityInfo *pi, const int inputLength, const int skipPos,
        const int excessivePos, const int transposedPos);
    void checkState();
    virtual ~CorrectionState();
    int getSkipPos() const {
        return mSkipPos;
    }
    int getExcessivePos() const {
        return mExcessivePos;
    }
    int getTransposedPos() const {
        return mTransposedPos;
    }
private:
    const ProximityInfo *mProximityInfo;
    int mInputLength;
    int mSkipPos;
    int mExcessivePos;
    int mTransposedPos;
};
} // namespace latinime
#endif // LATINIME_CORRECTION_INFO_H
