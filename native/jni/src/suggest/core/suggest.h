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

#ifndef LATINIME_SUGGEST_IMPL_H
#define LATINIME_SUGGEST_IMPL_H

#include "defines.h"
#include "suggest_interface.h"
#include "suggest_policy.h"

namespace latinime {

class DicNode;
class DicTraverseSession;
class ProximityInfo;
class Scoring;
class Traversal;
class Weighting;

class Suggest : public SuggestInterface {
 public:
    AK_FORCE_INLINE Suggest(const SuggestPolicy *const suggestPolicy)
            : TRAVERSAL(suggestPolicy->getTraversal()),
              SCORING(suggestPolicy->getScoring()), WEIGHTING(suggestPolicy->getWeighting()) {}
    AK_FORCE_INLINE virtual ~Suggest() {}
    int getSuggestions(ProximityInfo *pInfo, void *traverseSession, int *inputXs, int *inputYs,
            int *times, int *pointerIds, int *inputCodePoints, int inputSize, int commitPoint,
            int *outWords, int *frequencies, int *outputIndices, int *outputTypes) const;

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(Suggest);
    void createNextWordDicNode(DicTraverseSession *traverseSession, DicNode *dicNode,
            const bool spaceSubstitution) const;
    int outputSuggestions(DicTraverseSession *traverseSession, int *frequencies,
            int *outputCodePoints, int *outputIndices, int *outputTypes) const;
    void initializeSearch(DicTraverseSession *traverseSession, int commitPoint) const;
    void expandCurrentDicNodes(DicTraverseSession *traverseSession) const;
    void processTerminalDicNode(DicTraverseSession *traverseSession, DicNode *dicNode) const;
    void processExpandedDicNode(DicTraverseSession *traverseSession, DicNode *dicNode) const;
    void weightChildNode(DicTraverseSession *traverseSession, DicNode *dicNode) const;
    float getAutocorrectScore(DicTraverseSession *traverseSession, DicNode *dicNode) const;
    void generateFeatures(
            DicTraverseSession *traverseSession, DicNode *dicNode, float *features) const;
    void processDicNodeAsOmission(DicTraverseSession *traverseSession, DicNode *dicNode) const;
    void processDicNodeAsTransposition(DicTraverseSession *traverseSession,
            DicNode *dicNode) const;
    void processDicNodeAsInsertion(DicTraverseSession *traverseSession, DicNode *dicNode) const;
    void processDicNodeAsAdditionalProximityChar(DicTraverseSession *traverseSession,
            DicNode *dicNode, DicNode *childDicNode) const;
    void processDicNodeAsSubstitution(DicTraverseSession *traverseSession, DicNode *dicNode,
            DicNode *childDicNode) const;
    void processDicNodeAsMatch(DicTraverseSession *traverseSession,
            DicNode *childDicNode) const;

    // Dic nodes cache size for lookahead (autocompletion)
    static const int LOOKAHEAD_DIC_NODES_CACHE_SIZE;
    // Max characters to lookahead
    static const int MAX_LOOKAHEAD;
    // Inputs longer than this will autocorrect if the suggestion is multi-word
    static const int MIN_LEN_FOR_MULTI_WORD_AUTOCORRECT;
    static const int MIN_CONTINUOUS_SUGGESTION_INPUT_SIZE;
    // Base value for converting costs into scores (low so will not autocorrect without classifier)
    static const float BASE_OUTPUT_SCORE;

    // Threshold for autocorrection classifier
    static const float AUTOCORRECT_CLASSIFICATION_THRESHOLD;
    // Threshold for computing the language model feature for autocorrect classification
    static const float AUTOCORRECT_LANGUAGE_FEATURE_THRESHOLD;

    // Typing error correction settings
    static const bool CORRECT_SPACE_OMISSION;
    static const bool CORRECT_TRANSPOSITION;
    static const bool CORRECT_INSERTION;

    const Traversal *const TRAVERSAL;
    const Scoring *const SCORING;
    const Weighting *const WEIGHTING;

    static const bool CORRECT_OMISSION_G;
};
} // namespace latinime
#endif // LATINIME_SUGGEST_IMPL_H
