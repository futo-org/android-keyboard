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

#ifndef LATINIME_TYPING_SUGGEST_H
#define LATINIME_TYPING_SUGGEST_H

#include "defines.h"
#include "suggest_interface.h"

namespace latinime {

class ProximityInfo;

class TypingSuggest : public SuggestInterface {
 public:
    TypingSuggest(const int maxWordLength, const int maxWords)
            : mSuggestInterface(getTypingSuggestInstance(maxWordLength, maxWords)) {
    }

    virtual ~TypingSuggest();

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

    static void setTypingSuggestFactoryMethod(SuggestInterface *(*factoryMethod)(int, int)) {
        sTypingSuggestFactoryMethod = factoryMethod;
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(TypingSuggest);
    static SuggestInterface *getTypingSuggestInstance(int maxWordLength, int maxWords) {
        if (!sTypingSuggestFactoryMethod) {
            return 0;
        }
        return sTypingSuggestFactoryMethod(maxWordLength, maxWords);
    }

    static SuggestInterface *(*sTypingSuggestFactoryMethod)(int, int);
    SuggestInterface *mSuggestInterface;
};
} // namespace latinime
#endif // LATINIME_TYPING_SUGGEST_H
