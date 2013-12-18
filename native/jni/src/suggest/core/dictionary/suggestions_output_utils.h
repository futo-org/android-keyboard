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

#ifndef LATINIME_SUGGESTIONS_OUTPUT_UTILS
#define LATINIME_SUGGESTIONS_OUTPUT_UTILS

#include "defines.h"

namespace latinime {

class BinaryDictionaryShortcutIterator;
class DicNode;
class DicTraverseSession;
class Scoring;

class SuggestionsOutputUtils {
 public:
    /**
     * Outputs the final list of suggestions (i.e., terminal nodes).
     */
    static int outputSuggestions(const Scoring *const scoringPolicy,
            DicTraverseSession *traverseSession, int *frequencies, int *outputCodePoints,
            int *outputIndicesToPartialCommit, int *outputTypes,
            int *outputAutoCommitFirstWordConfidence);

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(SuggestionsOutputUtils);

    // Inputs longer than this will autocorrect if the suggestion is multi-word
    static const int MIN_LEN_FOR_MULTI_WORD_AUTOCORRECT;

    static int computeFirstWordConfidence(const DicNode *const terminalDicNode);

    static int outputShortcuts(BinaryDictionaryShortcutIterator *const shortcutIt,
            int outputWordIndex, const int finalScore, int *const outputCodePoints,
            int *const frequencies, int *const outputTypes, const bool sameAsTyped);
};
} // namespace latinime
#endif // LATINIME_SUGGESTIONS_OUTPUT_UTILS
