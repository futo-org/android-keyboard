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

#ifndef LATINIME_INCREMENTAL_DECODER_INTERFACE_H
#define LATINIME_INCREMENTAL_DECODER_INTERFACE_H

#include <stdint.h>
#include "defines.h"

namespace latinime {

class UnigramDictionary;
class BigramDictionary;
class ProximityInfo;

class IncrementalDecoderInterface {
 public:
    virtual int getSuggestions(ProximityInfo *pInfo, void *traverseSession,
            int *inputXs, int *inputYs, int *times, int *pointerIds, int *codes,
            int inputSize, int commitPoint, unsigned short *outWords, int *frequencies,
            int *outputIndices, int *outputTypes) const = 0;
    IncrementalDecoderInterface() { };
    virtual ~IncrementalDecoderInterface() { };
 private:
    DISALLOW_COPY_AND_ASSIGN(IncrementalDecoderInterface);
};
} // namespace latinime
#endif // LATINIME_INCREMENTAL_DECODER_INTERFACE_H
