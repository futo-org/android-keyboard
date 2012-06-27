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

#ifndef LATINIME_GESTURE_DECODER_IMPL_H
#define LATINIME_GESTURE_DECODER_IMPL_H

#include "defines.h"
#include "incremental_decoder.h"

namespace latinime {

class GestureDecoderImpl : public IncrementalDecoder {

 public:
    GestureDecoderImpl(int maxWordLength, int maxWords) :
            IncrementalDecoder(maxWordLength, maxWords) {
    }

    int getSuggestions(ProximityInfo *pInfo, int *inputXs, int *inputYs, int *times,
            int *pointerIds, int *codes, int inputSize, int commitPoint, bool isMainDict,
            unsigned short *outWords, int *frequencies, int *outputIndices) {
        return 0;
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(GestureDecoderImpl);
};
} // namespace latinime

#endif // LATINIME_GESTURE_DECODER_IMPL_H
