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

#ifndef LATINIME_GESTURE_SUGGEST_H
#define LATINIME_GESTURE_SUGGEST_H

#include "defines.h"
#include "suggest_interface.h"

namespace latinime {

class ProximityInfo;

class GestureSuggest : public SuggestInterface {
 public:
    GestureSuggest() : mSuggestInterface(getGestureSuggestInstance()) {}

    virtual ~GestureSuggest();

    int getSuggestions(ProximityInfo *pInfo, void *traverseSession, int *inputXs, int *inputYs,
            int *times, int *pointerIds, int *codes, int inputSize, int commitPoint, int *outWords,
            int *frequencies, int *outputIndices, int *outputTypes) const {
        if (!mSuggestInterface) {
            return 0;
        }
        return mSuggestInterface->getSuggestions(pInfo, traverseSession, inputXs, inputYs, times,
                pointerIds, codes, inputSize, commitPoint, outWords, frequencies, outputIndices,
                outputTypes);
    }

    static void setGestureSuggestFactoryMethod(SuggestInterface *(*factoryMethod)()) {
        sGestureSuggestFactoryMethod = factoryMethod;
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(GestureSuggest);
    static SuggestInterface *getGestureSuggestInstance() {
        if (!sGestureSuggestFactoryMethod) {
            return 0;
        }
        return sGestureSuggestFactoryMethod();
    }

    static SuggestInterface *(*sGestureSuggestFactoryMethod)();
    SuggestInterface *mSuggestInterface;
};
} // namespace latinime
#endif // LATINIME_GESTURE_SUGGEST_H
