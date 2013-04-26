/*
 * Copyright (C) 2013 The Android Open Source Project
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

#ifndef LATINIME_SCORING_H
#define LATINIME_SCORING_H

#include "defines.h"

namespace latinime {

class DicNode;
class DicTraverseSession;

// This class basically tweaks suggestions and distances apart from CompoundDistance
class Scoring {
 public:
    virtual int calculateFinalScore(const float compoundDistance, const int inputSize,
            const bool forceCommit) const = 0;
    virtual bool getMostProbableString(const DicTraverseSession *const traverseSession,
            const int terminalSize, const float languageWeight, int *const outputCodePoints,
            int *const type, int *const freq) const = 0;
    virtual void safetyNetForMostProbableString(const int terminalSize,
            const int maxScore, int *const outputCodePoints, int *const frequencies) const = 0;
    // TODO: Make more generic
    virtual void searchWordWithDoubleLetter(DicNode *terminals, const int terminalSize,
            int *doubleLetterTerminalIndex, DoubleLetterLevel *doubleLetterLevel) const = 0;
    virtual float getAdjustedLanguageWeight(DicTraverseSession *const traverseSession,
            DicNode *const terminals, const int size) const = 0;
    virtual float getDoubleLetterDemotionDistanceCost(const int terminalIndex,
            const int doubleLetterTerminalIndex,
            const DoubleLetterLevel doubleLetterLevel) const = 0;
    virtual bool doesAutoCorrectValidWord() const = 0;

 protected:
    Scoring() {}
    virtual ~Scoring() {}

 private:
    DISALLOW_COPY_AND_ASSIGN(Scoring);
};
} // namespace latinime
#endif // LATINIME_SCORING_H
