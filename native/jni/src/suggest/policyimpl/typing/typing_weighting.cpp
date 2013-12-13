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

#include "suggest/policyimpl/typing/typing_weighting.h"

#include "suggest/core/dicnode/dic_node.h"
#include "suggest/policyimpl/typing/scoring_params.h"

namespace latinime {

const TypingWeighting TypingWeighting::sInstance;

ErrorTypeUtils::ErrorType TypingWeighting::getErrorType(const CorrectionType correctionType,
        const DicTraverseSession *const traverseSession, const DicNode *const parentDicNode,
        const DicNode *const dicNode) const {
    switch (correctionType) {
        case CT_MATCH:
            if (isProximityDicNode(traverseSession, dicNode)) {
                return ErrorTypeUtils::PROXIMITY_CORRECTION;
            } else if (dicNode->isInDigraph()) {
                return ErrorTypeUtils::MATCH_WITH_DIGRAPH;
            } else {
                // Compare the node code point with original primary code point on the keyboard.
                const ProximityInfoState *const pInfoState =
                        traverseSession->getProximityInfoState(0);
                const int primaryOriginalCodePoint = pInfoState->getPrimaryOriginalCodePointAt(
                        dicNode->getInputIndex(0));
                const int nodeCodePoint = dicNode->getNodeCodePoint();
                if (primaryOriginalCodePoint == nodeCodePoint) {
                    // Node code point is same as original code point on the keyboard.
                    return ErrorTypeUtils::NOT_AN_ERROR;
                } else if (CharUtils::toLowerCase(primaryOriginalCodePoint) ==
                        CharUtils::toLowerCase(nodeCodePoint)) {
                    // Only cases of the code points are different.
                    return ErrorTypeUtils::MATCH_WITH_CASE_ERROR;
                } else if (CharUtils::toBaseCodePoint(primaryOriginalCodePoint) ==
                        CharUtils::toBaseCodePoint(nodeCodePoint)) {
                    // Node code point is a variant of original code point.
                    return ErrorTypeUtils::MATCH_WITH_ACCENT_ERROR;
                } else {
                    // Node code point is a variant of original code point and the cases are also
                    // different.
                    return ErrorTypeUtils::MATCH_WITH_ACCENT_ERROR
                            | ErrorTypeUtils::MATCH_WITH_CASE_ERROR;
                }
            }
            break;
        case CT_ADDITIONAL_PROXIMITY:
            return  ErrorTypeUtils::PROXIMITY_CORRECTION;
        case CT_OMISSION:
            if (parentDicNode->canBeIntentionalOmission()) {
                return ErrorTypeUtils::INTENTIONAL_OMISSION;
            } else {
                return ErrorTypeUtils::EDIT_CORRECTION;
            }
            break;
        case CT_SUBSTITUTION:
        case CT_INSERTION:
        case CT_TERMINAL_INSERTION:
        case CT_TRANSPOSITION:
            return ErrorTypeUtils::EDIT_CORRECTION;
        case CT_NEW_WORD_SPACE_OMISSION:
        case CT_NEW_WORD_SPACE_SUBSTITUTION:
            return ErrorTypeUtils::NEW_WORD;
        case CT_TERMINAL:
            return ErrorTypeUtils::NOT_AN_ERROR;
        case CT_COMPLETION:
            return ErrorTypeUtils::COMPLETION;
        default:
            return ErrorTypeUtils::NOT_AN_ERROR;
    }
}
}  // namespace latinime
