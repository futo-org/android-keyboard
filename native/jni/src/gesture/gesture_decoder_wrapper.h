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

#ifndef LATINIME_GESTURE_DECODER_WRAPPER_H
#define LATINIME_GESTURE_DECODER_WRAPPER_H

#include <stdint.h>
#include "defines.h"
#include "incremental_decoder_interface.h"

namespace latinime {

class UnigramDictionary;
class BigramDictionary;
class ProximityInfo;

class GestureDecoderWrapper : public IncrementalDecoderInterface {
 public:
    GestureDecoderWrapper(const int maxWordLength, const int maxWords)
            : mIncrementalDecoderInterface(getGestureDecoderInstance(maxWordLength, maxWords)) {
    }

    virtual ~GestureDecoderWrapper() {
        delete mIncrementalDecoderInterface;
    }

    int getSuggestions(ProximityInfo *pInfo, void *traverseSession, int *inputXs, int *inputYs,
            int *times, int *pointerIds, int *codes, int inputSize, int commitPoint,
            unsigned short *outWords, int *frequencies, int *outputIndices,
            int *outputTypes) const {
        if (!mIncrementalDecoderInterface) {
            return 0;
        }
        return mIncrementalDecoderInterface->getSuggestions(
                pInfo, traverseSession, inputXs, inputYs, times, pointerIds, codes,
                inputSize, commitPoint, outWords, frequencies, outputIndices, outputTypes);
    }

    static void setGestureDecoderFactoryMethod(
            IncrementalDecoderInterface *(*factoryMethod)(int, int)) {
        sGestureDecoderFactoryMethod = factoryMethod;
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(GestureDecoderWrapper);
    static IncrementalDecoderInterface *getGestureDecoderInstance(int maxWordLength, int maxWords) {
        if (sGestureDecoderFactoryMethod) {
            return sGestureDecoderFactoryMethod(maxWordLength, maxWords);
        }
        return 0;
    }

    static IncrementalDecoderInterface *(*sGestureDecoderFactoryMethod)(int, int);
    IncrementalDecoderInterface *mIncrementalDecoderInterface;
};
} // namespace latinime
#endif // LATINIME_GESTURE_DECODER_WRAPPER_H
