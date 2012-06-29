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
    virtual int getSuggestions(ProximityInfo *pInfo, int *inputXs, int *inputYs, int *times,
            int *pointerIds, int *codes, int inputSize, int commitPoint,
            unsigned short *outWords, int *frequencies, int *outputIndices) = 0;
    virtual void reset() = 0;
    virtual void setDict(const UnigramDictionary *dict, const BigramDictionary *bigram,
            const uint8_t *dictRoot, int rootPos) = 0;
    virtual void setPrevWord(const int32_t *prevWord, int prevWordLength) = 0;
    virtual ~IncrementalDecoderInterface() { };

    static IncrementalDecoderInterface *getGestureDecoderInstance(int maxWordLength, int maxWords) {
        if (sGestureDecoderFactoryMethod) {
            return sGestureDecoderFactoryMethod(maxWordLength, maxWords);
        }
        return 0;
    }

    static IncrementalDecoderInterface *getIncrementalDecoderInstance(int maxWordLength,
            int maxWords) {
        if (sIncrementalDecoderFactoryMethod) {
            return sIncrementalDecoderFactoryMethod(maxWordLength, maxWords);
        }
        return 0;
    }

    static void setGestureDecoderFactoryMethod(
            IncrementalDecoderInterface *(*factoryMethod)(int, int)) {
        sGestureDecoderFactoryMethod = factoryMethod;
    }

    static void setIncrementalDecoderFactoryMethod(
            IncrementalDecoderInterface *(*factoryMethod)(int, int)) {
        sIncrementalDecoderFactoryMethod = factoryMethod;
    }

 private:
    static IncrementalDecoderInterface *(*sGestureDecoderFactoryMethod)(int, int);
    static IncrementalDecoderInterface *(*sIncrementalDecoderFactoryMethod)(int, int);
};
} // namespace latinime
#endif // LATINIME_INCREMENTAL_DECODER_INTERFACE_H
